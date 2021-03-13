package driemondglas.nl.zorba

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.*
import android.content.pm.ActivityInfo
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.text.Spannable
import android.text.SpannableString
import android.text.style.*
import android.view.*
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import driemondglas.nl.zorba.Utils.enable
import driemondglas.nl.zorba.Utils.toggleVisibility
import driemondglas.nl.zorba.Utils.visible
import driemondglas.nl.zorba.databinding.FlashcardBinding


class FlashCard : AppCompatActivity() {

    private lateinit var binding: FlashcardBinding  // replaces synthetic view binding

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
    private var thisThema = ""
    private var thisLevel = 1
    private var thisActivePassiveXref = 0

    private var blockOffset = 0
    private var maxBlockOffset = 0
    private var positionInBlock = 0
    private var maxPositionInBlock = 0
    private var idxRequested = 0L
    private var singleLemma = false

    private var startTime = 0L
    private var configChanged = false

//    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = FlashcardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // initialise the ZORBA Action Bar
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.title_activity)
            subtitle = getString(R.string.subtitle_flash)
        }
        /* Determine if only a single card is to be displayed
         *  If so, several views can be hidden  (next previous score, etc.) */
        singleLemma = intent.getBooleanExtra("singlecard", false)
        idxRequested = intent.getLongExtra("idx", 0)

        // init database
        db = zorbaDBHelper.readableDatabase

        with(binding) {
            /* initialize the buttons' listeners
             * NOTE that the text fields also double as (large) buttons:
             * Greek text field: go to previous card
             * Dutch text field: wrong answer, go to next card
             * Note text field:  correct answer, go to next card
             * Touch DOWN shows hidden fields and Score
             * Touch UP  moves to next record
             * Move outside the view's boundaries while touch DOWN, prevents goto next and restores the score
             */

            enableTouch(!singleLemma)

            btnReveal.setOnClickListener {
                textGrieks.toggleVisibility()
                textNote.toggleVisibility()
            }
            btnNextBlock.setOnClickListener { nextBlock() }
            btnPrevBlock.setOnClickListener { prevBlock() }
            btnPrevBlock.setOnLongClickListener { goToFirstBlock(); true }

            btnOTT.setOnClickListener { showVerb(conjugateEnestotas(thisGreekText), getString(R.string.title_verb_present, thisPureLemma)) }
            btnOTTT.setOnClickListener { showVerb(conjugateMellontas(thisGreekText), getString(R.string.title_verb_future, thisPureLemma)) }
            btnVVT.setOnClickListener { showVerb(conjugateAoristos(thisGreekText), getString(R.string.title_verb_past, thisPureLemma)) }
            btnOVT.setOnClickListener { showVerb(conjugateParatatikos(thisGreekText), getString(R.string.title_verb_paratatikos, thisPureLemma)) }
            btnGW.setOnClickListener { showVerb(createProstaktiki(thisGreekText), getString(R.string.title_verb_imperative, thisPureLemma)) }
            btnSpeak.setOnClickListener { cleanSpeech(thisGreekText, thisWordType) }
            lblJumper.setOnClickListener { unJump() }
            btnShowAll.setOnClickListener { alleRijtjes() }
            flashChk.setOnClickListener { onFlashChange() }
            btnActivePassive.setOnClickListener { swichActivePassive() }

            // hide irrelevant views when single lemma
            btnReveal.visible(!singleLemma)
            btnNextBlock.visible(!singleLemma)
            btnPrevBlock.visible(!singleLemma)
            lblCorrect.visible(!singleLemma)
            lblWrong.visible(!singleLemma)
            lblPrev.visible(!singleLemma)
            txtProgress.visible(!singleLemma)
            textScore.visible(!singleLemma)
            blockProgressBar.visible(!singleLemma)

            if (singleLemma) {
                mainCursor = db.rawQuery("SELECT * FROM woorden WHERE idx=$idxRequested", null)
                mainCursor.moveToFirst()
                textGrieks.visibility = View.VISIBLE
                textNote.visibility = View.VISIBLE
            } else {
                // fetch the data
                reQuery()
                // retrieve last viewed block/position from shared preferences
                blockOffset = minOf(zorbaPreferences.getInt("last_viewed_block", 0), maxBlockOffset)
                positionInBlock = minOf(zorbaPreferences.getInt("last_viewed_position", 0), maxPositionInBlock)
                if (mainCursor.moveToPosition(blockOffset * blockSize + positionInBlock)) {
                    startTime = SystemClock.elapsedRealtime()
                    showProgress()
                    // and initial scores
                    textScore.text = ScoreBoard.getSessionScore()
                } else {
                    mainCursor.moveToFirst()
                }
            }
            populateFields()
        }
    }

    /* Actions needed when touching and releasing text fields, emulating key-down / key-up events
     * Checks if release is inside or outside original field boundaries */
    private fun IamTouched(v: View, m: MotionEvent, isCorrect: Boolean) {
        when (m.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                binding.textGrieks.visibility = View.VISIBLE
                binding.textNote.visibility = View.VISIBLE
                if (!ScoreBoard.allAreCorrect()) ScoreBoard.setScore(thisIdx, isCorrect)
            }
            MotionEvent.ACTION_UP -> {
                // check if touch-release is  within the control boundaries
                val boundaries = Rect(0, 0, v.width, v.height)

                if (boundaries.contains(m.getX().toInt(), m.getY().toInt())) {
                    binding.textGrieks.visibility = View.INVISIBLE
                    binding.textNote.visibility = View.INVISIBLE
                    next()
                } else {
                    // correct false negative/positive count
                    if (!ScoreBoard.allAreCorrect()) ScoreBoard.undoLastScore()
                }
            }
        }
        showScores()
    }

    /*  If the setting for using blocks is off then disable the [previous block] and [next block]-buttons
     *  Function lives in onResume() because the value can have changed by other activities */
    override fun onResume() {
        binding.btnPrevBlock.enable(!singleLemma)
        binding.btnNextBlock.enable(useBlocks && !singleLemma)
        db = zorbaDBHelper.readableDatabase
        super.onResume()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        /* Inflate the menu; this adds menu items to the zorba action bar. */
        menuInflater.inflate(R.menu.menu_flashcard, menu)
        with (menu) {
            findItem(R.id.menu_card_speech).isChecked = useSpeech
            findItem(R.id.menu_set_theme_wordtype).isVisible = !singleLemma
            findItem(R.id.menu_set_details_sort).isVisible = !singleLemma
            findItem(R.id.menu_clear_selects).isVisible = !singleLemma
            findItem(R.id.menu_reset_score).isVisible = !singleLemma
            findItem(R.id.menu_copy).isVisible = !singleLemma
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        /* Handle action bar item clicks here. */
        when (item.itemId) {
            R.id.menu_set_theme_wordtype -> {
                // launch the wordgroup/wordtype selection activity
                val myIntent = Intent(this, ThemeAndWordType::class.java)
                startActivityForResult(myIntent, THEME_WORDTYPE)
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            }

            R.id.menu_set_details_sort -> {
                // launch the detail selections and order-by activity
                val myIntent = Intent(this, FilterAndSort::class.java)
                startActivityForResult(myIntent, DETAILS)
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            }

            /* menu wis alle selecties */
            R.id.menu_clear_selects -> {
                clearAllSelections()
                configChanged = true
            }

            R.id.menu_reset_score -> {
                ScoreBoard.resetAllScores()
                showScores()
            }

            /* speech on/off */
            R.id.menu_card_speech -> {
                useSpeech = !item.isChecked
                item.isChecked = !item.isChecked
                binding.btnSpeak.enable(useSpeech)
            }

            R.id.menu_mail_lemma -> mailLemma()

            R.id.menu_copy -> {
                // Checking this menu item disables the onTouchlistener on the greek text fields
                // This re-enables the standard cut/paste/share actions on the textfield.
                item.isChecked = !item.isChecked
                binding.lblCorrect.visible(!item.isChecked)
                binding.lblWrong.visible(!item.isChecked)
                binding.lblPrev.visible(!item.isChecked)
                enableTouch(!item.isChecked)
            }

            R.id.menu_raw_view -> buidHTMLbox()

            R.id.menu_wordreference -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("""https://www.wordreference.com/gren/""" + thisPureLemma))
                startActivity(intent)
            }
            R.id.menu_neurolingo -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("""http://www.neurolingo.gr/en/online_tools/lexiscope.htm?term=""" + thisPureLemma))
                startActivity(intent)
            }

            /* home button (left arrow in app bar) pressed */
            android.R.id.home -> finish()

            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    /* when configuration changing activities are finished, they return here.
     * if result is a possible selection change, requery the data */
    override fun onActivityResult(requestCode: Int, resultCode: Int, myIntent: Intent?) {
        if (resultCode == RESULT_OK) {
            if (requestCode in setOf(THEME_WORDTYPE, DETAILS, FLASHCARDS)) {
                if (myIntent?.getStringExtra("result") == "changed") {
                    configChanged = true
                    reQuery()
                    if (mainCursor.count > 0) populateFields()
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, myIntent)
    }

    /* re-execute the main query and get new set of data from database table */
    private fun reQuery() {

        mainCursor = db.rawQuery(QueryManager.mainQuery(), null)

        if (mainCursor.moveToFirst()) {
            // determine how many block are possible
            if (blockSize == 0 || !useBlocks) {
                maxBlockOffset = 0
                maxPositionInBlock = mainCursor.count - 1
            }

            if (blockSize > 0 && useBlocks) {
                maxBlockOffset = (mainCursor.count - 1) / blockSize
                maxPositionInBlock = minOf(blockSize, mainCursor.count) - 1
            }

            // not sure that old positions exist in new cursor, so go back to start
            blockOffset = 0
            positionInBlock = 0

            showProgress()

            /* initialize progress bar */
            binding.blockProgressBar.max = maxPositionInBlock * (jumpThreshold + 1)
            binding.blockProgressBar.progress = 0
            ScoreBoard.resetScoreMap()
        }
    }

    override fun finish() {
        mainCursor.close()
        db.close()

        val myIntent = Intent()
        myIntent.putExtra("result", if (configChanged) "changed" else "unchanged")
        setResult(RESULT_OK, myIntent)
        super.finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    private fun unJump() {
        /* create attention before removing from jumper table */
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.alert_lemma_unjump_title))
            .setMessage(getString(R.string.alert_lemma_unjump_message))
            .setNegativeButton(getString(R.string.alert_lemma_unjump_neg), null)
            .setPositiveButton(getString(R.string.alert_lemma_unjump_pos)) { _, _ ->
                db.execSQL("DELETE FROM jumpers WHERE idx=$thisIdx")
                populateFields()
            }
            .show()
    }

    /* see if record is a jumper? (record has been added to the table of jumpers) */
    private fun isJumper(index: Int) = DatabaseUtils.queryNumEntries(db, "jumpers", "idx=$index") != 0L

    private fun onFlashChange() = if (binding.flashChk.isChecked) flashLocally() else unFlash()

    /* see if record is flashed (record idx present in the flashed table) */
    private fun isFlashed(index: Int) = DatabaseUtils.queryNumEntries(db, "flashedlocal", "idx=$index") != 0L

    private fun flashLocally() {
        if (thisIdx > 0) db.execSQL("INSERT INTO flashedlocal (idx,flashvalue) VALUES($thisIdx,1);")
    }

    private fun unFlash() = db.execSQL("DELETE FROM flashedlocal WHERE idx=$thisIdx;")

    /************ NEXT LEMMA **************/
    private fun next() {
        if (ScoreBoard.strike()) {
            prepareNextBlock()
        } else if (ScoreBoard.allAreCorrect()) {
            prepareNextBlock()
            /* add all jumpers( correct > threshold ) to jumper table EXCEPT when only 1 lemma was loaded */
            db.execSQL("INSERT OR REPLACE INTO jumpers VALUES ${ScoreBoard.scoreMapToString()};")
        } else {
            while (true) {
                /* move to next record or recycle to first record */
                if (positionInBlock >= maxPositionInBlock || mainCursor.isLast()) {
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
            binding.textGrieks.visibility = View.INVISIBLE
            binding.textNote.visibility = View.INVISIBLE
            showProgress()
            // maintain progress in shared preferences
            zorbaPreferences.edit().putInt("last_viewed_position", positionInBlock).apply()
        }
    }

    /* move to previous record or recycle to last record  in block */
    private fun previous() {
        if (positionInBlock == 0 || mainCursor.isFirst) {
            positionInBlock = maxPositionInBlock
            mainCursor.move(maxPositionInBlock)
        } else {
            positionInBlock--
            mainCursor.moveToPrevious()
        }
        ScoreBoard.undoLastScore()
        showScores()
        populateFields()
        showProgress()
        // maintain progress in shared preferences
        zorbaPreferences.edit().putInt("last_viewed_position", positionInBlock).apply()
    }

    /* copy field content from cursor and enable/disable appropriate buttons */
    private fun populateFields() {
        with(mainCursor) {
            thisIdx = getInt(getColumnIndex("idx"))
            thisGreekText = getString(getColumnIndex("GR"))
            thisDutchText = getString(getColumnIndex("NL"))
            thisRemark = getString(getColumnIndex("Opm"))
            thisWordType = getString(getColumnIndex("Woordsoort"))
            thisThema = getString(getColumnIndex("Thema"))
            thisPureLemma = getString(getColumnIndex("PureLemma"))
            thisLevel = getInt(getColumnIndex("Level"))
            thisActivePassiveXref = getInt(getColumnIndex("ActivePassiveRef"))
        }
        with(binding) {
            textGrieks.text = thisGreekText
            textNederlands.text = thisDutchText
            textNote.text = thisRemark

            // enable/disable buttons when applicable
            if (thisWordType != "werkwoord") {
                btnOTT.visibility = View.GONE
                btnOTTT.visibility = View.GONE
                btnVVT.visibility = View.GONE
                btnOVT.visibility = View.GONE
                btnGW.visibility = View.GONE
                btnShowAll.visibility = View.GONE
                btnActivePassive.visibility = View.GONE
            } else {
                btnOTT.visible(true)
                btnOTTT.visible(hasMellontas(thisGreekText))
                btnVVT.visible(hasAorist(thisGreekText))
                btnOVT.visible(hasParatatikos(thisGreekText))
                btnGW.visible(hasMellontas(thisGreekText))      //Gebiedende wijs is afgeleid van toekomende tijd
                btnShowAll.visible(true)
                btnActivePassive.visible(thisActivePassiveXref != 0)
            }
            btnSpeak.enable(useSpeech)
            lblJumper.visible(isJumper(thisIdx))
            flashChk.isChecked = isFlashed(thisIdx)

            // show correct-incorrect count using a series of green or red rectangles
            textSaldo.text = ScoreBoard.getLemmaScore(applicationContext, thisIdx)
        }
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
        zorbaPreferences.edit().putInt("last_viewed_position", positionInBlock).apply()

        mainCursor.moveToPosition(blockOffset * blockSize)
        populateFields()

        startTime = SystemClock.elapsedRealtime()

        binding.textGrieks.visibility = View.INVISIBLE
        binding.textNote.visibility = View.INVISIBLE
        binding.blockProgressBar.progress = 0
        showProgress()
    }

    private fun goToFirstBlock() {
        /* reset correct-incorrect counter */
        ScoreBoard.resetScoreMap()
        blockOffset = 0
        positionInBlock = 0
        zorbaPreferences.edit()
            .putInt("last_viewed_block", 0)
            .putInt("last_viewed_position", 0)
            .apply()
        mainCursor.moveToFirst()
        populateFields()

        binding.textGrieks.visibility = View.INVISIBLE
        binding.textNote.visibility = View.INVISIBLE
        binding.blockProgressBar.progress = 0
        showProgress()
    }

    /* move one block (set of lemmas) back */
    private fun prevBlock() {
        /* reset correct-incorrect counter */
        ScoreBoard.resetScoreMap()
        /* decrease the offset to go to the previous block */
        if (blockOffset > 0 && positionInBlock == 0) {
            blockOffset--
            // maintain progress in shared preferences
            zorbaPreferences.edit().putInt("last_viewed_block", blockOffset).apply()
        }
        positionInBlock = 0
        zorbaPreferences.edit().putInt("last_viewed_position", positionInBlock).apply()
        mainCursor.moveToPosition(blockOffset * blockSize)
        populateFields()

        binding.textGrieks.visibility = View.INVISIBLE
        binding.textNote.visibility = View.INVISIBLE
        binding.blockProgressBar.progress = 0
        showProgress()
    }

    /* update and show scores for current lemma, current block and  total session */
    private fun showScores() {
        binding.textScore.text = ScoreBoard.getSessionScore()
        binding.textSaldo.text = ScoreBoard.getLemmaScore(this, thisIdx)
        binding.blockProgressBar.progress = ScoreBoard.getBlockScore()
    }

    /* create attention after all correct, before moving to next block */
    private fun prepareNextBlock() {
        val elapsedSeconds = (SystemClock.elapsedRealtime() - startTime) / 1000
        var minutes = (elapsedSeconds / 60).toInt()
        var seconds = elapsedSeconds - (60 * minutes)
        var msg = if (ScoreBoard.strike())
            getString(R.string.clean_sweep, minutes, seconds) else
            getString(R.string.all_correct, jumpThreshold + 1, minutes, seconds)
        val bob = AlertDialog.Builder(this)
            .setTitle(getString(R.string.alert_endofblock_title))
            .setMessage(msg)
            .setPositiveButton(R.string.btn_caption_next_block) { _, _ -> nextBlock() }
        val alert = bob.show()
        alert.show()
        alert.getButton(DialogInterface.BUTTON_POSITIVE).textSize = 36f
    }

    /* show verb conjugations */
    private fun showVerb(conjugations: List<String>, title: String) {
        if (conjugations.size != 2 && conjugations.size != 6) return
        var ladder = conjugations.joinToString("\n")
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
            .setPositiveButton(R.string.btn_caption_ok, null)
            .setNeutralButton(R.string.btn_caption_speak, null)  // listener gets overridden below

        /* create the dialog from the builder (we need the reference to change text size) */
        val alertDialog = bob.create()
        alertDialog.show()

        var sayThis = ""
        /* change properties of the alert dialog internal views */
        (alertDialog.findViewById(android.R.id.message) as TextView).textSize = 20f
        when (conjugations.size) {
            6 -> {
                val persoonsvormGR = listOf("Εγώ", "Εσύ", setOf("Αυτός", "Αυτή", "Αυτό").random(), "Εμείς", "Εσείς", setOf("Αυτοί", "Αυτές", "Αυτά").random())
                sayThis = persoonsvormGR.mapIndexed { i, pronoun -> pronoun + " " + conjugations[i] }.joinToString(",")
            }
            2 -> sayThis = conjugations[0] + ", " + conjugations[1] + "."
        }

        with(alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL)) {
            textSize = 28f
            setOnClickListener { cleanSpeech(sayThis, "standaard") }
            enable(useSpeech)
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
              "Thema: $thisThema\n\n" +
              "Level: $thisLevel\n\n" +
              "A/P xref: $thisActivePassiveXref"

        val emailIntent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", getString(R.string.app_contact_email), null))
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, body)
        startActivity(Intent.createChooser(emailIntent, "Send lemma by email..."))
    }

    private fun showProgress() {
        binding.txtProgress.text = getString(R.string.lbl_position, 1 + blockOffset, 1 + positionInBlock)   // users count from 1
    }

    /* build the Raw Record View invoked through the menu */
    fun buidHTMLbox() {
        val webView = WebView(applicationContext)
        val record =
            """<html>
                <head>
                    <style>
                        table{border: 1px solid black;border-collapse: collapse; background-color: gold;}
                        td{border: 1px solid black; vertical-align:top;}
                    </style>"
                </head>
                <body>
                    <table>
                        <tr><td>index:</td><td>$thisIdx</td></tr>
                        <tr><td>pure lemma:</td><td>$thisPureLemma</td></tr>
                        <tr><td>grieks:</td><td>${thisGreekText.replace("\n", "<br>")}</td></tr>
                        <tr><td>nederlands:</td><td>${thisDutchText.replace("\n", "<br>")}</td></tr>
                        <tr><td>noot:</td><td>${thisRemark.replace("\n", "<br>")}</td></tr>
                        <tr><td>woordsoort:</td><td>$thisWordType</td></tr>
                        <tr><td>thema:</td><td>$thisThema</td></tr>
                        <tr><td>niveau:</td><td>$thisLevel</td></tr>
                        <tr><td>a/p ref:</td><td>$thisActivePassiveXref</td></tr>
                    </table>
                </body>
            </html>"""
        webView.loadData(record, "text/html", "UTF-8")

        val bob = AlertDialog.Builder(this)
            .setTitle(getString(R.string.alert_baserecord_title))
            .setView(webView)
            .setPositiveButton(R.string.btn_caption_ok, null)
        val alertDialog = bob.create()
        alertDialog.show()
        alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).textSize = 28f
    }

    /* build the box with all conjugations */
    fun alleRijtjes() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        val webView = WebView(applicationContext)

        val table = buildHTMLtable(thisGreekText)
        webView.loadData(table, "text/html", "UTF-8")
        val settings: WebSettings = webView.getSettings()
        settings.textZoom = 85 // where 50 is 50%; default value is ... 100

        val bob = AlertDialog.Builder(this)
            .setTitle(thisPureLemma)
            .setView(webView)
            .setPositiveButton(R.string.btn_caption_ok) { _, _ -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        val alertDialog = bob.create()
        alertDialog.show()
        alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).textSize = 24f
    }

    private fun swichActivePassive() {
        // if current activity was created using active/passive switchover, just finish it
        if (intent.getStringExtra("origin") == "apSwitch") {
            finish()
            // otherwise create new activity using cross referenced active/passive counterpart
        } else {
            val myIntent = Intent(this, FlashCard::class.java)
                .putExtra("idx", thisActivePassiveXref.toLong())
                .putExtra("singlecard", true)
                .putExtra("origin", "apSwitch")
            startActivity(myIntent)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun enableTouch(enable: Boolean) {
        // Text field click/touch behaviour
        // Disabled click/touch restore standard copy/share/... functionality
        with(binding) {
            if (enable) {
                textNederlands.setOnClickListener { previous() }
                shadowGrieks.setOnTouchListener { v: View, m: MotionEvent -> IamTouched(v, m, true);true }
                textGrieks.setOnTouchListener { v: View, m: MotionEvent -> IamTouched(v, m, true); true }
                shadowNote.setOnTouchListener { v: View, m: MotionEvent -> IamTouched(v, m, false); true }
                textNote.setOnTouchListener { v: View, m: MotionEvent -> IamTouched(v, m, false); true; }
            } else {
                textNederlands.setOnClickListener(null)
                shadowGrieks.setOnTouchListener(null)
                textGrieks.setOnTouchListener(null)
                shadowNote.setOnTouchListener(null)
                textNote.setOnTouchListener(null)
            }
        }
    }
}