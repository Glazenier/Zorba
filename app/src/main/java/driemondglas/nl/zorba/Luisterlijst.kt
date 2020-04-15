package driemondglas.nl.zorba

import android.annotation.SuppressLint
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.luisterlijst.*
import java.util.*


@SuppressLint("SetTextI18n")
class Luisterlijst : AppCompatActivity(), TextToSpeech.OnInitListener {


    /* text to speech object used tthis activity */
    private var spreker: TextToSpeech? = null

    /* overriding onInit() is needed for the TextToSpeech.OnInitListener */
    override fun onInit(status: Int) {
        /* set Greek as language for text to speech object */
        if (status == TextToSpeech.SUCCESS) {
            spreker?.language = Locale("el_GR")

        } else Log.d("hvr", "[spreker] speech initilization problem!")
    }

    /* declare references to database helper class and query manager */
    private val zorbaDBHelper = ZorbaDBHelper(this)
    private val queryManager: QueryManager = QueryManager.getInstance()

    /* initialize reference to the Zorba database */
    private lateinit var db: SQLiteDatabase

    /* declare a cursor to hold records from main selection query result */
    private lateinit var mainCursor: Cursor

    private var blockSize = 0
    private var blockOffset = 0
    private var relPos = 0
    private var nuEvenNiet = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.luisterlijst)


        /* Init reference to tts */
        spreker = TextToSpeech(this, this)

        spreker?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onDone(utteranceId: String?) {

                /* this event runs in separate thread from UI thread!
                 * All changes to UI must be run on UI thread or you get unpredictable results.
                 */

                Log.d("hvr", "[spreker] Done $utteranceId")
                Thread.sleep(750)
                runOnUiThread {
                    tv_lemma.text = mainCursor.getString(mainCursor.getColumnIndex("PureLemma"))
                    tv_meaning.text = mainCursor.getString(mainCursor.getColumnIndex("NL"))
                }
                Thread.sleep(1250)
                runOnUiThread {
                    tv_lemma.text = ""
                    tv_meaning.text = ""
                }

                if (!nuEvenNiet ) {
                    if( relPos < blockSize - 1) {
                        relPos++
                        runOnUiThread { tv_blockpos.text = "${1 + blockOffset} - ${1 + relPos}" }
                        mainCursor.moveToPosition(blockOffset * blockSize + relPos)
                        val lemma = mainCursor.getString(mainCursor.getColumnIndex("PureLemma"))
                        spreker?.speak(lemma, TextToSpeech.QUEUE_FLUSH, null, lemma)
                    } else {
                        relPos = 0
                        nuEvenNiet=true
                        runOnUiThread { btn_leeslijst.text = getString(R.string.speak) }
                    }

                }
            }

            override fun onError(utteranceId: String?) {
                Log.e("hvr", "[spreker] Error $utteranceId")
            }

            override fun onStart(utteranceId: String?) {
//                Log.d("hvr","[spreker] Start $utteranceId")
            }
        })

        btn_leeslijst.setOnClickListener { startListening() }

        btn_leeslijst.setOnLongClickListener {
            relPos = 0
            startListening()
            true
        }

        /* init database */
        db = zorbaDBHelper.readableDatabase
        mainCursor = db.rawQuery(queryManager.mainQuery(), null)

        /* configured blockSize is kept in the query manager instance */
        blockSize = queryManager.blockSize


        btn_next_block.setOnClickListener { nextBlock() }
        btn_prev_block.setOnClickListener { prevBlock() }
    }


    private fun startListening() {
        if (nuEvenNiet) {
            /* einde stilte */
            nuEvenNiet = false
            /* stop all ongoing speech */
            spreker?.stop()
            btn_leeslijst.text = getString(R.string.no_speak)

            /* reset relative position in block of lemmas */
//            relPos = 0

            /* position the database cursor in the current block on the relative position */
            mainCursor.moveToPosition(blockOffset * blockSize + relPos)

            /* display this position as progress indicatior to the user*/
            tv_blockpos.text = "${1 + blockOffset} - ${1 + relPos}"  // users count from 1...

            /* get the first pure lemma text to speak */
            val lemma = mainCursor.getString(mainCursor.getColumnIndex("PureLemma"))

            /* say it.
             * All consequent lemma's are handled by the UtteranceProgressListener until block is done
             */

            spreker?.speak(lemma, TextToSpeech.QUEUE_FLUSH, null, lemma)
        } else {
            nuEvenNiet = true
            btn_leeslijst.text = getString(R.string.speak)
        }
    }

    /* move one block forward */
    private fun nextBlock() {
        /* increase the offset to go to the next block */
        val maxOffset = if (blockSize == 0) 0 else mainCursor.count / blockSize
        if (blockOffset < maxOffset) blockOffset++
        relPos = 0
        mainCursor.moveToPosition(blockOffset * blockSize)
        tv_lemma.text = ""
        tv_meaning.text = ""
        tv_blockpos.text = "${blockOffset + 1} - 1"
    }

    /* move one block (set of lemmas) back */
    private fun prevBlock() {
        /* decrease the offset to go to the previous block */
        if (blockOffset > 0) blockOffset--
        relPos = 0
        mainCursor.moveToPosition(blockOffset * blockSize)
        tv_lemma.text = ""
        tv_meaning.text = ""
        tv_blockpos.text = "${blockOffset + 1} - 1"
    }

    override fun onRestart() {
        nuEvenNiet = false
        super.onRestart()
    }

    override fun onStop() {
        nuEvenNiet = true
        spreker?.stop()
        super.onStop()
    }

    override fun onDestroy() {
        spreker?.stop()
        spreker?.shutdown()
        super.onDestroy()
    }
}
