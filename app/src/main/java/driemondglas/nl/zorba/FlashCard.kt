package driemondglas.nl.zorba

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import driemondglas.nl.zorba.ScoreBoard.resetScoreMap
import driemondglas.nl.zorba.ScoreBoard.undoLastScore
import driemondglas.nl.zorba.Utils.enabled
import driemondglas.nl.zorba.Utils.toggleVisibility
import driemondglas.nl.zorba.Utils.visible
import kotlinx.android.synthetic.main.flashcard.*

class FlashCard : AppCompatActivity() {

    /* initialize reference to the Zorba database */
    private lateinit var db: SQLiteDatabase

    /* declare a cursor to hold records from selection query result */
    private lateinit var mainCursor: Cursor

    /* declare and initialise local vars */
    private var thisIdx = 0
    private var thisGreekText = ""
    private var thisDutchText = ""
    private var thisRemark = ""
    private var thisPureLemma = ""
    private var thisWordType = ""
    private var thisWordGroup = ""
    private var thisLevel = 1

    private var blockOffset = 0
    private var maxOffset = 0
    private var maxPosition = 0
    private var positionInBlock = 0
    private var idxRequested = 0L
    private var singleLemma = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.flashcard)

        /* initialise the ZORBA Action Bar */
        val zorbaActionBar = supportActionBar
        zorbaActionBar?.setDisplayHomeAsUpEnabled(true)
        zorbaActionBar?.title = "ZORBA"
        zorbaActionBar?.subtitle = "Flashcards Nederlands-Grieks"

        /* setup the touch areas response to various motions */
        shadow_grieks.setOnTouchListener { v: View, m: MotionEvent ->
            if (!singleLemma) touche(v, m, true)
            true
        }
        text_grieks.setOnTouchListener { v: View, m: MotionEvent ->
            if (!singleLemma) touche(v, m, true)
            true
        }
        shadow_note.setOnTouchListener { v: View, m: MotionEvent ->
            if (!singleLemma) touche(v, m, false)
            true
        }
        text_note.setOnTouchListener { v: View, m: MotionEvent ->
            if (!singleLemma) touche(v, m, false)
            true
        }

        /* enable scrolling for long texts */
//        text_grieks.movementMethod = ScrollingMovementMethod()
//        text_nederlands.movementMethod = ScrollingMovementMethod()
//        text_note.movementMethod = ScrollingMovementMethod()

        /* initialize the buttons' listeners
         * NOTE that the text fields also double as (large) buttons:
         * Greek text field: go to previous card
         * Dutch text field: wrong answer, go to next card
         * Note text field:  correct answer, go to next card
         * Touch DOWN shows hidden fields and Score
         * Touch UP  moves to next record
         * Move outside the view's boundaries while touch DOWN, prevents goto next and restores the score
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
        btn_speak.setOnClickListener { cleanSpeech(thisGreekText, thisWordType) }
        lbl_jumper.setOnClickListener { unJump() }

        /* init database */
        db = zorbaDBHelper.readableDatabase

        /* Determine weather only a single card is to be displayed
         *  If so, several views can be hidden  (next previous score, etc.
         */
        idxRequested = intent.getLongExtra("idx", 0)
        singleLemma = idxRequested != 0L

        /* disable buttons when single lemma */
        btn_reveal_answer.enabled(!singleLemma)
        btn_reveal_note.enabled(!singleLemma)
        btn_next_block.enabled(!singleLemma)
        btn_prev_block.enabled(!singleLemma)

        /* hide action labels */
        lbl_correct.visible(!singleLemma)
        lbl_wrong.visible(!singleLemma)
        lbl_prev.visible(!singleLemma)

        /* fetch the data */
        reQuery()
        /* populate fields with current data  */
        populateFields()



        if (!singleLemma) {
            /* retrieve last viewed block/position from shared preferences */
            blockOffset = minOf(zorbaPreferences.getInt("last_viewed_block", 0), maxOffset)
            positionInBlock = minOf(zorbaPreferences.getInt("last_viewed_position", 0), maxPosition)
            if (mainCursor.moveToPosition(blockOffset * blockSize + positionInBlock)) {
                populateFields()
                showProgress()
                /* and initial scores */
                text_score.text = ScoreBoard.getSessionScore()
            }
        } else {
            text_grieks.visibility = View.VISIBLE
            text_note.visibility = View.VISIBLE
            text_score.text = ""
        }
    }

    /* Actions needed when touching and releasing text fields, emulating key-down / key-up events
     * Checks if release is inside or outside original field boundaries */
    private fun touche(v: View, m: MotionEvent, isCorrect: Boolean) {
        when (m.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                text_grieks.visibility = View.VISIBLE
                text_note.visibility = View.VISIBLE
                if (!ScoreBoard.allAreCorrect()) ScoreBoard.updateCounters(thisIdx, isCorrect)
            }
            MotionEvent.ACTION_UP -> {
                /* check if release is  within the source control boundaries */
                val boundaries = Rect(0, 0, v.width, v.height)

                if (boundaries.contains(m.getX().toInt(), m.getY().toInt())) {
                    text_grieks.visibility = View.INVISIBLE
                    text_note.visibility = View.INVISIBLE
                    next()

                } else if (!ScoreBoard.allAreCorrect()) ScoreBoard.undoLastScore() // correct false negative/positive count
            }
        }
        showScores()
    }

    /*  If the setting for using blocks is off then disable the [previous block] and [next block]-buttons
     *  Function lives in onResume() because the value can have changed by other activities
     */
    override fun onResume() {
        /* when blocks (sets of limited number of cards) are used, enable the previous/next block buttons */
        btn_prev_block.enabled(useBlocks && !singleLemma)
        btn_next_block.enabled(useBlocks && !singleLemma)
        super.onResume()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        /* Inflate the menu; this adds menu items to the zorba action bar. */
        menuInflater.inflate(R.menu.menu_flashcard, menu)
        menu.findItem(R.id.menu_card_speech).isChecked = useSpeech


        menu.findItem(R.id.menu_set_groep_soort).isVisible = !singleLemma
        menu.findItem(R.id.menu_set_block_sort).isVisible = !singleLemma
        menu.findItem(R.id.menu_clear_selects).isVisible = !singleLemma
        menu.findItem(R.id.menu_reset_score).isVisible = !singleLemma

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
            R.id.menu_clear_selects -> clearAll()

            R.id.menu_reset_score -> {
                ScoreBoard.resetScores()
                showScores()
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

            R.id.menu_raw_view -> {
                buidHTMLbox()
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
                populateFields()
            }
        }
        super.onActivityResult(requestCode, resultCode, myIntent)
    }

    /* re-execute the main query and get new set of data from database table */
    private fun reQuery() {
        mainCursor = if (singleLemma) {
            db.rawQuery("SELECT * FROM woorden WHERE idx=$idxRequested", null)
        } else {
            db.rawQuery(QueryManager.mainQuery(), null)
        }
        if (mainCursor.moveToFirst()) {
            /* determine how many block are possible */
            if (blockSize == 0 || !useBlocks) {
                maxOffset = 0
                maxPosition = mainCursor.count - 1
            }

            if (blockSize > 0 && useBlocks) {
                maxOffset = (mainCursor.count - 1) / blockSize
                maxPosition = minOf(blockSize, mainCursor.count) - 1
            }

            /* initialize progress bar */
            block_progress_bar.max = blockSize * (jumpThreshold + 1)
            block_progress_bar.progress = 0
            resetScoreMap()
        }
        Log.d(TAG, "reQuery: max offset $maxOffset")
        Log.d(TAG, "reQuery: max position $maxPosition")
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
            .setMessage("Lemma vaak genoeg goed beantwoord.\nReset deze status voor dit lemma?")
            .setNegativeButton("Nee, is goed zo.", null)
            .setPositiveButton("Ja, reset!") { _, _ ->
                db.execSQL("DELETE FROM jumpers WHERE idx=$thisIdx")
                populateFields()
            }
            .show()
    }

    /* see if record is a jumper? (record has been moved to the table of jumpers) */
    private fun isJumper() = DatabaseUtils.queryNumEntries(db, "jumpers", "idx=$thisIdx") != 0L

    /************ NEXT LEMMA **************/
    private fun next() {
        if (idxRequested == 0L) {
            if (ScoreBoard.allAreCorrect()) {
                snack("Alle lemma's in dit blok ${jumpThreshold + 1} keer correct beantwoord.")

                /* add all jumpers( correct > threshold ) to jumper table EXCEPT when only 1 lemma was loaded */
                db.execSQL("INSERT OR REPLACE INTO jumpers VALUES ${ScoreBoard.scoreMapToString()};")

            } else {
                while (true) {
                    /* move to next record or recycle to first record */
                    if (positionInBlock >= maxPosition || mainCursor.isLast()) {
                        positionInBlock = 0
                        mainCursor.moveToPosition(blockOffset * blockSize)
                    } else {
                        positionInBlock++
                        mainCursor.moveToNext()
                    }
                    val nextIdx = mainCursor.getInt(mainCursor.getColumnIndex("idx"))
                    if (ScoreBoard.noJumper(nextIdx)) break
                }

                populateFields()
                text_grieks.visibility = View.INVISIBLE
                text_note.visibility = View.INVISIBLE
                showProgress()
                // maintain progress in shared preferences
                zorbaPreferences.edit().putInt("last_viewed_position", positionInBlock).apply()
            }
        }
    }

    /* move to previous record or recycle to last record  in block */
    private fun previous() {
        if (positionInBlock == 0 || mainCursor.isFirst) {
            positionInBlock = maxPosition
            mainCursor.move(maxPosition)
        } else {
            positionInBlock--
            mainCursor.moveToPrevious()
        }
        undoLastScore()
        showScores()
        populateFields()
        showProgress()
        // maintain progress in shared preferences
        zorbaPreferences.edit().putInt("last_viewed_position", positionInBlock).apply()
        // do not change visiblity when moving backwards
    }

    /* copy field content from cursor and enable/disable appropriate buttons */
    private fun populateFields() {
        thisIdx = mainCursor.getInt(mainCursor.getColumnIndex("idx"))
        thisGreekText = mainCursor.getString(mainCursor.getColumnIndex("GR"))
        thisDutchText = mainCursor.getString(mainCursor.getColumnIndex("NL"))
        thisRemark = mainCursor.getString(mainCursor.getColumnIndex("Opm"))
        thisWordType = mainCursor.getString(mainCursor.getColumnIndex("Woordsoort"))
        thisWordGroup = mainCursor.getString(mainCursor.getColumnIndex("Groep"))
        thisPureLemma = mainCursor.getString(mainCursor.getColumnIndex("PureLemma"))
        thisLevel = mainCursor.getInt(mainCursor.getColumnIndex("Level"))
        text_grieks.text = thisGreekText
        text_nederlands.text = thisDutchText
        text_note.text = thisRemark
        Log.d(TAG, "Current record: $thisIdx lemma: $thisPureLemma")

        /* enable/disable buttons when applicable */
        val isVerb = (thisWordType == "werkwoord")
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
        if (blockOffset < maxOffset) {
            blockOffset++
            // maintain progress in shared preferences
            zorbaPreferences.edit().putInt("last_viewed_block", blockOffset).apply()
        }
        positionInBlock = 0
        mainCursor.moveToPosition(blockOffset * blockSize)
        populateFields()

        text_grieks.visibility = View.INVISIBLE
        text_note.visibility = View.INVISIBLE
        block_progress_bar.progress = 0
        showProgress()
    }


    /* move one block (set of lemmas) back */
    private fun prevBlock() {
        /* reset correct-incorrect counter */
        ScoreBoard.resetScoreMap()
        /* decrease the offset to go to the previous block */
        if (blockOffset > 0) {
            blockOffset--
            // maintain progress in shared preferences
            zorbaPreferences.edit().putInt("last_viewed_block", blockOffset).apply()
        }
        positionInBlock = 0
        mainCursor.moveToPosition(blockOffset * blockSize)
        populateFields()

        text_grieks.visibility = View.INVISIBLE
        text_note.visibility = View.INVISIBLE
        block_progress_bar.progress = 0
        showProgress()
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
        var ladder = verb.replace(", ".toRegex(), "\n")
        ladder = ladder.replace(" - ".toRegex(), ", ")
        var verbPos = title.lastIndexOf(' ')
        val titleSpan = SpannableString(title)
        val greekTextColor = ContextCompat.getColor(applicationContext, R.color.κυανός)
        val thisColorSpan = ForegroundColorSpan(greekTextColor)
        val italicSpan = StyleSpan(Typeface.ITALIC)
        val verbSizeSpan = RelativeSizeSpan(1.3f)
        titleSpan.setSpan(thisColorSpan, verbPos, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        titleSpan.setSpan(verbSizeSpan, verbPos, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        titleSpan.setSpan(italicSpan, verbPos, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        val ladderSpan = SpannableString(ladder)
        ladderSpan.setSpan(italicSpan, 0, ladder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        ladderSpan.setSpan(thisColorSpan, 0, ladder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        val bob = AlertDialog.Builder(this)
            .setTitle(titleSpan)
            .setMessage(ladderSpan)
            .setPositiveButton(R.string.emoji_ok, null)
            .setNeutralButton(R.string.speak, null)  // listener gets overridden below

        /* create the dialog from the builder (we need the reference to change text size) */
        val alertDialog = bob.create()
        alertDialog.show()

        /* change properties of the alert dialog internal views */
        (alertDialog.findViewById(android.R.id.message) as TextView).textSize = 20f

        with(alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL)) {
            textSize = 28f
            setOnClickListener { cleanSpeech(verb, "standaard") }
            enabled(useSpeech)
        }

        alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).textSize = 28f
    }

    private fun mailLemma() {
        val subject = "$thisIdx: $thisPureLemma"
        val body = "Idx: $thisIdx\n\n" +
              "Grieks: $thisGreekText\n\n" +
              "Nederlands: $thisDutchText\n\n" +
              "Note: $thisRemark\n\n" +
              "Woordsoort: $thisWordType\n\n" +
              "Woordgroep: $thisWordGroup\n\n" +
              "Level: $thisLevel"

        val emailIntent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", getString(R.string.contact_email), null))
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, body)
        startActivity(Intent.createChooser(emailIntent, "Send lemma by email..."))
    }

    private fun showProgress() {
        txt_progress.text = getString(R.string.positie, 1 + blockOffset, 1 + positionInBlock)   // users count from 1
    }

    /* build the Raw Record View invoked through the main menu */
    fun buidHTMLbox() {
        val webView = WebView(applicationContext)
        val record = "<html><head><style>" +
              "  table{border: 1px solid black;border-collapse: collapse; background-color: gold;}" +
              "  td{border: 1px solid black;vertical-align:top;}" +
              "</style></head>" +
              "<body><table>" +
              "  <tr><td>index:</td><td>$thisIdx</td></tr>" +
              "  <tr><td>pure lemma:</td><td>$thisPureLemma</td></tr>" +
              "  <tr><td>grieks:</td><td>" + thisGreekText.replace("\n", "<br>") + "</td></tr>" +
              "  <tr><td>nederlands:</td><td>" + thisDutchText.replace("\n", "<br>") + "</td></tr>" +
              "  <tr><td>noot:</td><td>" + thisRemark.replace("\n", "<br>") + "</td></tr>" +
              "  <tr><td>woordsoort:</td><td>$thisWordType</td></tr>" +
              "  <tr><td>woordgroep:</td><td>$thisWordGroup</td></tr>" +
              "  <tr><td>niveau:</td><td>$thisLevel</td></tr>" +
              "</table></body></html>"
        webView.loadData(record, "text/html", "UTF-8")
        val bob = AlertDialog.Builder(this)
        bob.setTitle("Base Record")
        bob.setView(webView)
        bob.setPositiveButton(R.string.emoji_ok, null)

        val alertDialog = bob.create()
        alertDialog.show()
        alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).textSize = 28f
    }
}
