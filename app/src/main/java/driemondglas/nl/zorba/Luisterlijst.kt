package driemondglas.nl.zorba

import android.annotation.SuppressLint
import android.content.Intent
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.*
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.luisterlijst.*
import java.util.*
import kotlin.concurrent.thread
import driemondglas.nl.zorba.Utils.enabled

@SuppressLint("SetTextI18n")

class Luisterlijst : AppCompatActivity(), TextToSpeech.OnInitListener {

    /* text to speech object used in this activity */
    private lateinit var spreker: TextToSpeech

    /* declare references to database helper class and query manager */
    private val zorbaDBHelper = ZorbaDBHelper(this)
    private val queryManager: QueryManager = QueryManager.getInstance()

    /* initialize reference to the Zorba database */
    private lateinit var db: SQLiteDatabase

    /* declare a cursor to hold records from main selection query result */
    private lateinit var thisCursor: Cursor

    private var blockSize = 0        // number of lemma's to speak in one turn. This is taken from blocksize in Querymanager
    private var blockOffset = 0      // position of current block in cursor
    private var positionInBlock = 0  // relative position of current lemma in the block
    private var stopTalking = true   // flags stop when speech is run in separate thread
    private var hasStarted = false   // checks state of speech from separate thread
    private var isDone = true        // checks state of speech from separate thread

    /* overriding onInit() is needed for the TextToSpeech.OnInitListener */
    override fun onInit(status: Int) {
        /* set Greek as language for text to speech object */
        if (status == TextToSpeech.SUCCESS) {
            spreker.language = Locale("el_GR")
            spreker.setSpeechRate(1f)
        } else {
            Log.d("hvr", "[spreker] speech initilization problem!")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.luisterlijst)

        /* Init tts object  */
        spreker = TextToSpeech(this, this)

        /* triggers for speech progress events */
        spreker.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            /* these events runs in separate thread from UI thread!
             * All changes to UI must be run on UI thread or you get unpredictable results.
             */
            override fun onDone(utteranceId: String?) {
                isDone = true
//                Log.d("hvr", "[spreker] Done $utteranceId")
            }

            override fun onError(utteranceId: String?) {
                Log.e("hvr", "[spreker] Error $utteranceId")
            }

            override fun onStart(utteranceId: String?) {
                hasStarted = true
//                Log.d("hvr", "[spreker] Start $utteranceId")
            }
        })

        btn_play.setOnClickListener { startListening() }
        btn_play.enabled(useSpeech)

        btn_rewind.setOnClickListener {
            spreker.stop()
            positionInBlock = 0
            hasStarted = false
            isDone = true
            startListening()
        }

        sb_speed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb_speed: SeekBar, progress: Int, fromUser: Boolean) {
                val speed = sb_speed.progress / 10f
                spreker.setSpeechRate(speed)
                txt_speed.text = "${sb_speed.progress * 10}%"
                }
            override fun onStartTrackingTouch(sb_speed: SeekBar) {}
            override fun onStopTrackingTouch(sb_speed: SeekBar) {}
        })

        /* (late)init database */
        db = zorbaDBHelper.readableDatabase
        thisCursor = db.rawQuery(queryManager.mainQuery(), null)

        /* configured blockSize is kept in the query manager instance */
        blockSize = queryManager.blockSize

        btn_next_block.setOnClickListener { nextBlock() }
        btn_prev_block.setOnClickListener { prevBlock() }
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
                queryManager.clearAll()
                thisCursor = db.rawQuery(queryManager.mainQuery(), null)
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
                        thisCursor = db.rawQuery(queryManager.mainQuery(), null)
                    }
                }
                SELECTIES_CODE -> {
                    if (myIntent.getStringExtra("result") == "selected") {
                        /* refresh data in the mainCursor using the changed selections */
                        thisCursor = db.rawQuery(queryManager.mainQuery(), null)
                    }
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, myIntent)
    }

    private fun startListening() {
        if (stopTalking and useSpeech) {

            /* stop all ongoing speech */
            spreker.stop()

            /* remove stop sign */
            stopTalking = false

            /* show pause icon */
            btn_play.text = getString(R.string.btn_caption_pause)

            thread {
                while (positionInBlock < blockSize && !stopTalking) {

                    /* position the database cursor in the current block on the relative position */
                    thisCursor.moveToPosition(blockOffset * blockSize + positionInBlock)

                    /* if no more lemmas in cursor -> exit loop */
                    if (thisCursor.isAfterLast) {
                        runOnUiThread { img_thatsall.visibility = View.VISIBLE }
                        return@thread
                    }

                    /* display this position as progress indicatior to the user*/
                    runOnUiThread { txt_blockpos.text = getString(R.string.positie, 1 + blockOffset, 1 + positionInBlock) }  // users count from 1

                    /* get the first pure lemma text to speak */
                    val lemma = thisCursor.getString(thisCursor.getColumnIndex("PureLemma"))
                    val meaning = thisCursor.getString(thisCursor.getColumnIndex("NL"))

                    /* say it. */
                    hasStarted = false
                    isDone = false
                    spreker.speak(lemma, TextToSpeech.QUEUE_FLUSH, null, lemma)

                    while (!hasStarted) {
                        /* wait for speech to start */
                    }
                    while (!isDone) {
                        /* wait for speech to finish */
                    }
                    /* check if state has changed while speaking */
                    if (stopTalking) return@thread

                    /* pause before showing text */
                    Thread.sleep(750)

                    /* check if state has changed while sleeping */
                    if (stopTalking) return@thread

                    /* display greek and meaning */
                    runOnUiThread {
                        txt_lemma.text = lemma
                        txt_meaning.text = meaning
                    }

                    /* time text remains visible */
                    Thread.sleep(1500)

                    /* clear text */
                    runOnUiThread {
                        txt_lemma.text = ""
                        txt_meaning.text = ""
                    }

                    /* move to next lemma */
                    if (!stopTalking) positionInBlock++

                }  // end while
            }  // end of thread
        } else {
            /* raise stop sign */
            stopTalking = true

            /* show play icon */
            btn_play.text = getString(R.string.btn_caption_play)

            /* hide 'that's all folkes' */
            img_thatsall.visibility = View.INVISIBLE
        }
    }

    /* move one block forward */
    private fun nextBlock() {

        /* raise stop sign */
        stopTalking = true

        /* check how many block are possible */
        val maxOffset = if (blockSize == 0) 0 else (thisCursor.count - 1) / blockSize

        /* increase the offset to go to the next block */
        if (blockOffset < maxOffset) blockOffset++

        /* start at first lemma in block */
        positionInBlock = 0

        /* move cursor position to this lemma in the set */
        thisCursor.moveToPosition(blockOffset * blockSize)

        /* reset the ui fields */
        txt_lemma.text = ""
        txt_meaning.text = ""
        txt_blockpos.text = getString(R.string.positie, 1 + blockOffset, 1)
        img_thatsall.visibility = View.INVISIBLE

        /* show play icon */
        btn_play.text = getString(R.string.btn_caption_play)
    }

    /* move one block (set of lemmas) back */
    private fun prevBlock() {

        /* raise the stop sign */
        stopTalking = true

        /* decrease the offset to go to the previous block */
        if (blockOffset > 0) blockOffset--

        /* start at first lemma in block */
        positionInBlock = 0

        /* move cursor position to this lemma in the set */
        thisCursor.moveToPosition(blockOffset * blockSize)

        /* reset the ui fields */
        txt_lemma.text = ""
        txt_meaning.text = ""
        txt_blockpos.text = getString(R.string.positie, 1 + blockOffset, 1)
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


}
