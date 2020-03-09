package driemondglas.nl.zorba

import android.app.AlertDialog
import android.content.*
import android.database.*
import android.database.sqlite.SQLiteDatabase
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.*
import android.widget.TextView
import driemondglas.nl.zorba.Utils.enabled
import driemondglas.nl.zorba.Utils.visible
import driemondglas.nl.zorba.Utils.toggleVisibility
import driemondglas.nl.zorba.ScoreBoard.undoLastScore
import kotlinx.android.synthetic.main.flashcard.*

class FlashCard : AppCompatActivity() {
    /* declare references to database helper class ans query manager */
    private val zorbaDBHelper = ZorbaDBHelper(this)
    private val queryManager: QueryManager = QueryManager.getInstance()

    /* initialize reference to the Zorba database */
    private lateinit var db: SQLiteDatabase

    /* declare a cursor to hold records from main selection query result */
    private lateinit var mainCursor: Cursor

    /* declare and initialise local vars */
    private var thisIdx = 0
    private var thisGreekText = ""
    private var thisPureLemma = ""
    private var thisWoordsoort = ""
    private var blockSize = 0
    private var blockOffset = 0
    private var positionInBlock = 0
    private var idxRequested = 0L
    private var singleLemma = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.flashcard)

        /* initialise the ZORBA Action Bar */
        val zorbaActionBar = supportActionBar
        if (zorbaActionBar != null) {
            /* This next line shows the home/back button.
             * The functionality is handled by the android system as long as a parent activity
             * is specified in the manifest.xls file
             * We OVERRIDE this functionality with our own goBack() function to cleanly close cursor and database
             */
            zorbaActionBar.setDisplayHomeAsUpEnabled(true)
            zorbaActionBar.title = "ZORBA"
            zorbaActionBar.subtitle = "Flashcards Nederlands-Grieks"
        }

        shadow_grieks.setOnTouchListener { v: View, m: MotionEvent -> if (!singleLemma) touche(v, m, false); true }
        text_grieks.setOnTouchListener { v: View, m: MotionEvent -> if (!singleLemma) touche(v, m, false); true }
        shadow_note.setOnTouchListener { v: View, m: MotionEvent -> if (!singleLemma) touche(v, m, true); true }
        text_note.setOnTouchListener { v: View, m: MotionEvent -> if (!singleLemma) touche(v, m, true); true }

        /* enable scrolling for long texts */
        text_grieks.movementMethod = ScrollingMovementMethod()
        text_nederlands.movementMethod = ScrollingMovementMethod()
        text_note.movementMethod = ScrollingMovementMethod()

        /* initialize the buttons' listeners
         * NOTE that the text fields also double as (large) buttons:
         * Greek text field: go to previous card
         * Dutch text field: wrong answer, go to next card
         * Note text field:  correct answer, go to next card
         * Touch DOWN shows hidden fields and Score
         * Touch UP  moves to next record
         * Move outside the view's boundaries while touch DOWN, prevents goto next and sets back the score
         */
        btn_reveal_answer.setOnClickListener { text_grieks.toggleVisibility() }
        btn_reveal_note.setOnClickListener { text_note.toggleVisibility() }
        btn_next_block.setOnClickListener { nextBlock() }
        btn_prev_block.setOnClickListener { prevBlock() }
        text_nederlands.setOnClickListener { if (!singleLemma) previous() }
        btn_OTT.setOnClickListener { showVerb(conjugateEnestotas(thisGreekText), "Tegenwoordige tijd van $thisPureLemma") }
        btn_OTTT.setOnClickListener { showVerb(conjugateMellontas(thisGreekText), "Toekomende tijd van $thisPureLemma") }
        btn_VVT.setOnClickListener { showVerb(conjugateAorist(thisGreekText), "Verleden tijd van $thisPureLemma") }
        btn_OVT.setOnClickListener { showVerb(conjugateParatatikos(thisGreekText), "Paratatikos van $thisPureLemma") }
        btn_GW.setOnClickListener { showVerb(createProstaktiki(thisGreekText), "Gebiedende wijs (ev, mv) van $thisPureLemma") }
        btn_speak.setOnClickListener { cleanSpeech(thisGreekText, thisWoordsoort) }
        lbl_jumper.setOnClickListener { unJump() }


        /* init database */
        db = zorbaDBHelper.readableDatabase

        /* configured blockSize is kept in the query manager instance */
        blockSize = queryManager.blockSize

        if (intent != null) {
            idxRequested = intent.getLongExtra("idx", 0)
            singleLemma = idxRequested != 0L
        }

        /* disable buttons when single lemma */
        btn_reveal_answer.enabled(!singleLemma)
        btn_reveal_note.enabled(!singleLemma)
        btn_next_block.enabled(!singleLemma)
        btn_prev_block.enabled(!singleLemma)

        /* hide action labels */
        lbl_correct.visible(!singleLemma)
        lbl_wrong.visible(!singleLemma)
        lbl_prev.visible(!singleLemma)

        /* populate fields with current data  */
        reQuery()

        /* and initial scores */
        text_score.text = if (singleLemma) "" else ScoreBoard.getSessionScore()
    }

    /* Actions needed when touching and releasing text fields, emulating key-down / key-up events
     * Checks if release is inside or outside original field boundaries */
    private fun touche(v: View, m: MotionEvent, isCorrect: Boolean) {
        val action = m.actionMasked
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                text_grieks.visibility = View.VISIBLE
                text_note.visibility = View.VISIBLE
                if (!ScoreBoard.allAreCorrect()) ScoreBoard.updateCounters(thisIdx, isCorrect)
            }
            MotionEvent.ACTION_UP -> {
                /* check if release is  within the source view */
                val boundaries = Rect(0, 0, v.width, v.height)
                /* correct false negative/positive count */
                if (boundaries.contains(m.getX().toInt(), m.getY().toInt())) {
                    text_grieks.visibility = View.INVISIBLE
                    text_note.visibility = View.INVISIBLE
                    next()
                } else if (!ScoreBoard.allAreCorrect()) ScoreBoard.undoLastScore()
            }
        }
        showScores()
    }

    /*  If the setting for using blocks is off then disable the [previous block] and [next block]-buttons
     *  Function lives in onResume() because the value can have changed by other activities
     */
    override fun onResume() {
        /* when blocks (sets of limited number of cards) are used, enable the previous/next block buttons */
        btn_prev_block.enabled(queryManager.useBlocks && !singleLemma)
        btn_next_block.enabled(queryManager.useBlocks && !singleLemma)
        super.onResume()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        /* Inflate the menu; this adds menu items to the zorba action bar. */
        menuInflater.inflate(R.menu.menu_flashcard, menu)
        menu.findItem(R.id.menu_card_speech).isChecked = useSpeech
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        /* Handle action bar item clicks here. */
        when (item.itemId) {
            R.id.menu_set_groep_soort -> {
                // launch the wordgroup/wordtype selection activity
                val myIntent = Intent(this, WordTypeAndGroup::class.java)
                startActivityForResult(myIntent, GROEP_SOORT_CODE)
            }
            R.id.menu_set_block_sort -> {
                // launch the detail selections and order-by activity
                val myIntent = Intent(this, FilterAndSort::class.java)
                startActivityForResult(myIntent, SELECTIES_CODE)
            }

            /* menu wis alle selecties */
            R.id.menu_clear_selects -> {
                if (!singleLemma) {
                    queryManager.clearAll()
                    reQuery()
                }
            }

            R.id.menu_reset_score -> {
                if (!singleLemma) {
                    ScoreBoard.resetScores()
                    showScores()
                }
            }

            /* speech on/off */
            R.id.menu_card_speech -> {
                useSpeech = !item.isChecked
                item.isChecked = !item.isChecked
                btn_speak.enabled(useSpeech)
            }

            R.id.menu_mail_lemma -> {
                mailLemma()
            }
            R.id.menu_wordreference -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("""https://www.wordreference.com/gren/""" + thisPureLemma))
                startActivity(intent)
            }
            R.id.menu_neurolingo -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("""http://www.neurolingo.gr/en/online_tools/lexiscope.htm?term=""" + thisPureLemma))
                startActivity(intent)
            }

            /* home button (left arrow in app bar) pressed */
            android.R.id.home -> {
                finishIntent()
            }
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, myIntent: Intent?) {
        /* when configuration changing activities are finished, they return here.
         * if result is a possible selection change, requery the data
         */
        if (requestCode == GROEP_SOORT_CODE || requestCode == SELECTIES_CODE) {
            if (myIntent?.getStringExtra("result") == "selected") {
                reQuery()
            }
        }
        super.onActivityResult(requestCode, resultCode, myIntent)
    }

    /* re-execute the main query and get new set of data from database table */
    private fun reQuery() {
        mainCursor = if (singleLemma) {
            db.rawQuery("SELECT * FROM woorden WHERE idx=$idxRequested", null)
        } else {
            db.rawQuery(queryManager.mainQuery(), null)
        }
        if (mainCursor.moveToFirst()) {
            /* initialize progress bar for the block or entire selection */
            blockSize = if (queryManager.useBlocks) queryManager.blockSize else mainCursor.count
            block_progress_bar.max = blockSize * (queryManager.jumpThreshold + 1)
            block_progress_bar.progress = 0
            ScoreBoard.resetScoreMap()
            populateFields()
            if (singleLemma) {
                text_grieks.visibility = View.VISIBLE
                text_note.visibility = View.VISIBLE
            }
        }
    }

    /* finish intent and return to main activity */
    private fun finishIntent() {
        mainCursor.close()
        db.close()

        val myIntent = Intent()
        myIntent.putExtra("action", "stop")
        setResult(RESULT_OK, myIntent)
        finish()
    }

    private fun unJump() {
        /* create attention before removing from jumper table */
        AlertDialog.Builder(this)
              .setTitle("Jumper")
              .setMessage("Lemma vaak genoeg goed beantwoord.\nVerwijder deze status voor dit lemma?")
              .setNegativeButton("Nee, laat maar zo.", null)
              .setPositiveButton("Ja, verwijder") { _, _ ->
                  db.execSQL("DELETE FROM jumpers WHERE idx=$thisIdx")
                  populateFields()
              }
              .show()
    }

    /* see if record is jumper (moved to table of jumpers) */
    private fun isJumper(): Boolean {
        val countJumpers = DatabaseUtils.queryNumEntries(db, "jumpers","idx=$thisIdx")
        return countJumpers!=0L
    }

    /************ NEXT LEMMA **************/
    private fun next() {
        if (idxRequested == 0L) {
            if (ScoreBoard.allAreCorrect()) {
                snack("Alle lemma's in dit blok ${queryManager.jumpThreshold + 1} keer correct beantwoord.")

                /* add all jumpers( correct > threshold ) to jumper table EXCEPT when only 1 lemma was loaded */
                db.execSQL("INSERT OR REPLACE INTO jumpers VALUES ${ScoreBoard.scoreMapToString()};")

            } else {
                while (true) {
                    /* move to next record or recycle to first record */
                    if (positionInBlock >= blockSize - 1 || mainCursor.isLast()) {
                        mainCursor.moveToPosition(blockOffset * blockSize)
                        positionInBlock = 0
                    } else {
                        mainCursor.moveToNext()
                        positionInBlock++
                    }
                    val nextIdx = mainCursor.getInt(mainCursor.getColumnIndex("idx"))
                    if (ScoreBoard.noJumper(nextIdx)) break
                }

                populateFields()
                text_grieks.visibility = View.INVISIBLE
                text_note.visibility = View.INVISIBLE
            }
        }
    }

    /* move to previous record or recycle to last record  in block */
    private fun previous() {
        if (idxRequested == 0L) {
            if (positionInBlock == 0 || mainCursor.isFirst) {
                positionInBlock = minOf(blockSize, mainCursor.count) - 1
                if (positionInBlock > 0) mainCursor.move(positionInBlock)
            } else {
                positionInBlock--
                mainCursor.moveToPrevious()
            }
            undoLastScore()
            showScores()
            populateFields()
            // do not change visiblity when moving backwards
        }
    }

    /* copy field content from cursor and enable/disable appropriate buttons */
    private fun populateFields() {
        thisIdx = mainCursor.getInt(mainCursor.getColumnIndex("idx"))
        thisGreekText = mainCursor.getString(mainCursor.getColumnIndex("GR"))
        thisWoordsoort = mainCursor.getString(mainCursor.getColumnIndex("Woordsoort"))
        thisPureLemma = mainCursor.getString(mainCursor.getColumnIndex("PureLemma"))
        text_grieks.text = thisGreekText
        text_nederlands.text = mainCursor.getString(mainCursor.getColumnIndex("NL"))
        text_note.text = mainCursor.getString(mainCursor.getColumnIndex("Opm"))
        Log.d("hvr", "Current record: $thisIdx lemma: $thisPureLemma")

        /* enable/disable buttons when applicable */
        val isVerb = (thisWoordsoort == "werkwoord")
        btn_OTT.enabled(isVerb)
        btn_OTTT.enabled(isVerb && hasMellontas(thisGreekText))
        btn_VVT.enabled(isVerb && hasAorist(thisGreekText))
        btn_OVT.enabled(isVerb && hasParatatikos(thisGreekText))
        btn_GW.enabled(isVerb && hasMellontas(thisGreekText))  //Gebiedende wijs is afgeleid van toekomende tijd
        btn_reveal_note.enabled(!text_note.text.isEmpty() && !singleLemma)
        btn_speak.enabled(useSpeech)
        lbl_jumper.visible(isJumper())

        /* show correct-incorrect count using a series of green or red rectangles */
        text_saldo.text = ScoreBoard.showLemmaScore(this, thisIdx)
    }

    /* move one block forward */
    private fun nextBlock() {
        /* reset correct-incorrect counter */
        ScoreBoard.resetScoreMap()
        /* increase the offset to go to the next block */
        val maxOffset = if (blockSize == 0) 0 else mainCursor.count / blockSize
        if (blockOffset < maxOffset) blockOffset++
        positionInBlock = 0
        mainCursor.moveToPosition(blockOffset * blockSize)
        populateFields()

        text_grieks.visibility = View.INVISIBLE
        text_note.visibility = View.INVISIBLE
        block_progress_bar.progress = 0
    }

    /* move one block (set of lemmas) back */
    private fun prevBlock() {
        /* reset correct-incorrect counter */
        ScoreBoard.resetScoreMap()
        /* decrease the offset to go to the previous block */
        if (blockOffset > 0) blockOffset--
        positionInBlock = 0
        mainCursor.moveToPosition(blockOffset * blockSize)
        populateFields()

        text_grieks.visibility = View.INVISIBLE
        text_note.visibility = View.INVISIBLE
        block_progress_bar.progress = 0
    }

    /* update and show scores for current lemma, current block and  total session */
    private fun showScores() {
        text_score.text = ScoreBoard.getSessionScore()
        text_saldo.text = ScoreBoard.showLemmaScore(this, thisIdx)
        block_progress_bar.progress = ScoreBoard.getBlockScore()
    }

    /* build and show custom snackbar */
    private fun snack(snackText: String, ms: Int = 5000) {
        val snackbar = Snackbar.make(getWindow().getDecorView().getRootView(), snackText, ms)
        val textView = snackbar.view.findViewById(com.google.android.material.R.id.snackbar_text) as TextView
        val actionView = snackbar.view.findViewById(com.google.android.material.R.id.snackbar_action) as TextView
        snackbar.view.setBackgroundColor(Color.LTGRAY)

        textView.setTextColor(Color.BLUE)
        textView.textSize = 18f
        actionView.setTextColor(Color.BLACK)
        actionView.textSize = 24f

        snackbar.setAction(getText(R.string.btn_caption_next_block), View.OnClickListener { nextBlock() })
        snackbar.show()
    }

    /* show verb conjugations */
    private fun showVerb(verb: String, title: String) {

        /* make a column by adding newline to each delimiter */
        var ladder = verb.replace(", ".toRegex(), ",\n")
       ladder = ladder.replace(" - ".toRegex(), ", ")

        val bob = AlertDialog.Builder(this)
              .setTitle(title)
              .setMessage(ladder)
              .setPositiveButton(R.string.emoji_ok, null)
              .setNeutralButton(R.string.speak, null)  // gets overridden below

        /* create the dialog from the builder (we need the reference to change text size) */
        val alertDialog = bob.create()
        alertDialog.show()

        /* change properties of the alert dialog internal views */
        val messageView = alertDialog.findViewById(android.R.id.message) as TextView
        messageView.textSize = 20f

        val neutralButton = alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL)
        neutralButton.textSize = 28f
        neutralButton.setOnClickListener { cleanSpeech(verb, "standaard") }
        neutralButton.enabled(useSpeech)

        alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).textSize = 28f
    }

    private fun mailLemma() {
        val subject = "$thisIdx: $thisPureLemma"
        val body = "Idx: $thisIdx\n\n" +
              "Nederlands:\n ${text_nederlands.text.toString()}\n\n" +
              "Grieks:\n $thisGreekText\n\n" +
              "Note:\n ${text_note.text.toString()}"

        val emailIntent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", getString(R.string.contact_email), null))
              .putExtra(Intent.EXTRA_SUBJECT, subject )
              .putExtra(Intent.EXTRA_TEXT, body)
        startActivity(Intent.createChooser(emailIntent, "Send lemma by email..."))
    }
}
