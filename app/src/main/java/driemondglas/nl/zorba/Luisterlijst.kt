package driemondglas.nl.zorba

import android.annotation.SuppressLint
import android.content.Intent
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.Spannable
import android.text.SpannableString
import android.text.method.ScrollingMovementMethod
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.TypefaceSpan
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import driemondglas.nl.zorba.Utils.enabled
import kotlinx.android.synthetic.main.luisterlijst.*
import java.util.*
import kotlin.concurrent.thread
import kotlin.math.pow
import kotlin.properties.Delegates

const val BRAINTIME =  1000L
const val SHOWTIME =  1500L

@SuppressLint("SetTextI18n")

class Luisterlijst : AppCompatActivity(), TextToSpeech.OnInitListener {
    /* text to speech object used in this activity */
    private lateinit var spreker: TextToSpeech

    /* initialize reference to the Zorba database */
    private lateinit var db: SQLiteDatabase

    /* declare a cursor to hold records from main selection query result */
    private lateinit var thisCursor: Cursor

    private var blockOffset: Int by Delegates.observable(0) { _, _, _ -> showProgress() }       // position of current block in cursor + action when changed
    private var positionInBlock: Int by Delegates.observable(0) { _, _, _ -> showProgress() }   // relative position of current lemma in the block + action when changed

    private var stopTalking = true   // flags stop as speech is run in separate thread
    private var hasStarted = false   // checks state of speech from utterance thread
    private var isDone = true        // checks state of speech from utterance thread

    private var history = ""

    /* overriding onInit() is needed for the TextToSpeech.OnInitListener */
    override fun onInit(status: Int) {
        /* set Greek as language for text to speech object */
        if (status == TextToSpeech.SUCCESS) {
            spreker.language = Locale("el_GR")
            spreker.setSpeechRate(1f)
        } else {
            Log.d(TAG, "[spreker] speech initilization problem!")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.luisterlijst)

        /* initialise the ZORBA Action Bar */
        val zorbaActionBar = supportActionBar
        zorbaActionBar?.setDisplayHomeAsUpEnabled(true)
        zorbaActionBar?.title = "ZORBA"
        zorbaActionBar?.subtitle = "Luisteroefening"

        /* make text field scrollable */
        farfarAway.movementMethod = ScrollingMovementMethod()
        /* Init tts object  */
        spreker = TextToSpeech(this, this)

        /* trigger for speech progress events */
        spreker.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            /* these events runs in separate thread from UI thread!
             * All changes to UI must be run on UI thread or you get unpredictable results.
             */
            override fun onDone(utteranceId: String?) {
                isDone = true
                //  Log.d(TAG, "[spreker] Done $utteranceId")
            }

            override fun onError(utteranceId: String?) {
                Log.d(TAG, "[spreker] Error $utteranceId")
            }

            override fun onStart(utteranceId: String?) {
                hasStarted = true
                // Log.d(TAG, "[spreker] Start $utteranceId")
            }
        })

        sb_speed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(sb_speed: SeekBar, progress: Int, fromUser: Boolean) {
                val speed = sb_speed.progress
                spreker.setSpeechRate(speed / 10f)
                txt_speed.text = "${speed * 10}%"
            }

            override fun onStartTrackingTouch(sb_speed: SeekBar) {}
            override fun onStopTrackingTouch(sb_speed: SeekBar) {}
        })

        /* (late)init database */
        db = zorbaDBHelper.readableDatabase
        thisCursor = db.rawQuery(QueryManager.mainQuery(), null)

        /* check how many block are possible */
        val maxOffset = if (blockSize == 0 || !useBlocks) 0 else (thisCursor.count - 1) / blockSize
        /* retrieve last_listened_block from shared preferences (if it is not too big!)*/
        blockOffset = minOf(zorbaPreferences.getInt("last_listened_block", 0), maxOffset)

        btn_next_block.setOnClickListener {
            nextBlock()
            startStop()
        }
        btn_prev_block.setOnClickListener {
            prevBlock()
            startStop()
        }
        btn_play.setOnClickListener { startStop() }
        btn_play.enabled(useSpeech)

        btn_rewind.setOnClickListener {
            positionInBlock = 0
            stopTalking = true
            img_thatsall.visibility = View.INVISIBLE
            startStop()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        /*  Inflate the menu from the layout file
         *  this adds menu items to the action bar if it is present. */
        menuInflater.inflate(R.menu.menu_luisterlijst, menu)
        menu.findItem(R.id.menu_speech).isChecked = useSpeech
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        /* Handle action bar (menu) item clicks here. */

        when (item.itemId) {
            /* menu set woordsoort/woordgroep */
            R.id.menu_set_groep_soort -> {
                /* launch the Groep/Woordsoort Selection Activity */
                val myIntent = Intent(this, WordTypeAndGroup::class.java)
                startActivityForResult(myIntent, GROEP_SOORT_CODE)
            }

            /*  menu detail selecties */
            R.id.menu_set_block_sort -> {
                /* launch the Selecties Activity */
                val myIntent = Intent(this, FilterAndSort::class.java)
                startActivityForResult(myIntent, SELECTIES_CODE)
            }

            /* menu wis alle selecties */
            R.id.menu_clear_selects -> {
                clearAll()
                thisCursor = db.rawQuery(QueryManager.mainQuery(), null)
            }

            /* speech on/off */
            R.id.menu_speech -> {
                useSpeech = !item.isChecked
                item.isChecked = !item.isChecked
                btn_play.enabled(useSpeech)
                stopTalking = true
                spreker.stop()
            }

            /* home button (left arrow in app bar) pressed */
            android.R.id.home -> {
                finishIntent()
            }
            /* perform any other actions triggered from parent or beyond */
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, myIntent: Intent?) {
        /*
         * This is the place where the activities/intents return after finishing
         * you can pick up the result from each intent by looking at the request code used */
        if (myIntent != null) {   // to get rid of the Intent? null safety

            /* refresh data in the 'lemmaArrayList' using the changed selections */
            when (requestCode) {

                GROEP_SOORT_CODE -> {
                    if (myIntent.getStringExtra("result") == "selected") {
                        /* refresh data in the mainCursor using the changed selections */
                        thisCursor = db.rawQuery(QueryManager.mainQuery(), null)
                    }
                }
                SELECTIES_CODE -> {
                    if (myIntent.getStringExtra("result") == "selected") {
                        /* refresh data in the mainCursor using the changed selections */
                        thisCursor = db.rawQuery(QueryManager.mainQuery(), null)
                    }
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, myIntent)
    }


    private fun startStop() {
        if (stopTalking) {                 // talking was paused or ended,
            stopTalking = false            // remove this flag

            /* show pause icon  */
            btn_play.text = getString(R.string.btn_caption_pause)

            /* if talking stopped at the end of the block, we need to reset the position */
            if (positionInBlock == blockSize - 1) positionInBlock = 0

            speakWholeBlock()

        } else {                            // talking was active, pause it
            stopTalking = true             // set the flag

            /* stop all ongoing speech */
            spreker.stop()

            /* show play icon */
            btn_play.text = getString(R.string.btn_caption_play)
        }
    }

    private fun speakWholeBlock() {

        thread {
            val maxPosition = if (useBlocks) blockSize else thisCursor.count
            wholeblock@ while ((positionInBlock < maxPosition) && !stopTalking) {

                /* position the database cursor in the current block on the relative position */
                thisCursor.moveToPosition(blockOffset * blockSize + positionInBlock)

                /* if no more lemmas in cursor -> exit loop, when reaching the end of the database, last block */
                if (thisCursor.isAfterLast) {
                    runOnUiThread { img_thatsall.visibility = View.VISIBLE }
                    return@thread
                }

                /* get the pure lemma text to speak, and the meaning to display after delay */
                val lemma = thisCursor.getString(thisCursor.getColumnIndex("PureLemma"))
                var meaning = thisCursor.getString(thisCursor.getColumnIndex("NL"))
                /* only first line */
                meaning = meaning.substringBefore('\n')

                /* say it. */
                hasStarted = false
                isDone = false
                spreker.speak(lemma, TextToSpeech.QUEUE_FLUSH, null, lemma)  // UtteranceProgressListener sets statii hasStarted and isDone
                while (!hasStarted) { /* wait for speech to start */
                    if (stopTalking) break@wholeblock
                }
                while (!isDone) {  /* wait for speech to finish */
                    if (stopTalking) break@wholeblock
                }

                /* pause before showing text */
                if (!stopTalking) Thread.sleep(BRAINTIME)

                /* fade down text */
                runOnUiThread {
                    var runningStart = 0
                    val cleanMeaning = (meaning.substringBefore("\n")).trim()
                    val lemmaLine = "$lemma ~ $cleanMeaning"

                    history = if (history.isEmpty()) lemmaLine else "$history\n$lemmaLine"
                    // limit number of lines by removing the last line
                    if (history.lines().count() > 12) history = history.substringAfter("\n").trim()
                    val spannable = SpannableString(history)

                    val numLines = history.lines().count()
                    val factor = 0.9F
                    val typeface: Typeface? = ResourcesCompat.getFont(applicationContext, R.font.tinos_italic)

                    history.lines().forEachIndexed { idx, line ->
                        val shrinkingFontSize = factor.pow(numLines - idx - 1)
                        val delimiterStart = line.indexOf(" ~ ")
                        val meaningStart = delimiterStart + 3
                        val colorSpan = ForegroundColorSpan(ContextCompat.getColor(applicationContext, R.color.sky_text_greek))

                        with(spannable) {
                            setSpan(RelativeSizeSpan(shrinkingFontSize), runningStart, runningStart + line.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                            setSpan(colorSpan, runningStart, runningStart + delimiterStart, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                            setSpan(TypefaceSpan(typeface!!), runningStart, runningStart + delimiterStart, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                            setSpan(ForegroundColorSpan(Color.BLACK), runningStart + delimiterStart, runningStart + meaningStart, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                        runningStart += line.length + 1
                    }
                    farfarAway.text = spannable
                }
                /* pause before moving on to next lemma */
                if (!stopTalking) Thread.sleep(SHOWTIME)

                positionInBlock++
            }  // end while

            if (!stopTalking) { // so at the end of the block
                positionInBlock = maxPosition - 1
                runOnUiThread { btn_play.text = getString(R.string.btn_caption_restart) }
                stopTalking = true   // setup for restart
            }
        }  // end of thread
    }


    /* move one block forward */
    private fun nextBlock() {

        /* raise stop sign */
        stopTalking = true

        /* check how many block are possible */
        val maxOffset = if (blockSize == 0) 0 else (thisCursor.count - 1) / blockSize

        /* increase the offset to go to the next block */
        if (blockOffset < maxOffset) {
            blockOffset++
            // maintain progress in shared preferences
            zorbaPreferences.edit()
                .putInt("last_listened_block", blockOffset)
                .apply()
        }

        /* start at first lemma in block */
        positionInBlock = 0

        /* move cursor position to this lemma in the set */
        thisCursor.moveToPosition(blockOffset * blockSize)
        img_thatsall.visibility = View.INVISIBLE

        /* show play icon */
        btn_play.text = getString(R.string.btn_caption_play)
    }

    /* move one block (set of lemmas) back */
    private fun prevBlock() {

        /* raise the stop sign */
        stopTalking = true

        /* decrease the offset to go to the previous block */
        if (blockOffset > 0) {
            blockOffset--
            // maintain progress in shared preferences
            zorbaPreferences.edit()
                .putInt("last_listened_block", blockOffset)
                .apply()
        }

        /* start at first lemma in block */
        positionInBlock = 0

        /* move cursor position to this lemma in the set */
        thisCursor.moveToPosition(blockOffset * blockSize)

        /* show the end image */
        img_thatsall.visibility = View.INVISIBLE

        /* show play icon */
        btn_play.text = getString(R.string.btn_caption_play)
    }

    /* finish intent and return to main activity */
    private fun finishIntent() {
        /* close database related */
        thisCursor.close()
        db.close()

        /* release tts resources */
        spreker.stop()
        spreker.shutdown()

        /* finish activity */
        val myIntent = Intent()
        myIntent.putExtra("action", "stop")
        setResult(RESULT_OK, myIntent)
        finish()
    }

    override fun onResume() {
        /* set state to waiting for start */
        stopTalking = true
        /* show play icon */
        btn_play.text = getString(R.string.btn_caption_play)
        super.onResume()
    }

    override fun onPause() {
        stopTalking = true
        spreker.stop()
        super.onPause()
    }

    override fun onDestroy() {
        stopTalking
        spreker.stop()
        spreker.shutdown()
        super.onDestroy()
    }

    private fun showProgress() {
        runOnUiThread { txt_blockpos.text = getString(R.string.positie, 1 + blockOffset, 1 + positionInBlock) }  // users count from 1
    }
}
