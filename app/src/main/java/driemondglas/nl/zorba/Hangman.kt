package driemondglas.nl.zorba

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.graphics.*
import android.os.Bundle
import android.util.AttributeSet
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import driemondglas.nl.zorba.Utils.normalize
import driemondglas.nl.zorba.databinding.HangmanBinding
import java.util.*

/* This file contains 2 classes related to the hangman game activity:
 *   Hangman        Activity that gets called from main activity.
 *   HangmanCanvas  Custom view to draw graphics for the gallow and the increasing number of body parts
 *                  Note: this custom view is used in the hangman.xml flile as
 *                  type: driemondglas.nl.zorba.HangmanCanvas and
 *                  id: "@+id/gallow"
 */
class Hangman : AppCompatActivity() {
    private lateinit var mainCursor: Cursor
    private lateinit var db: SQLiteDatabase
    private lateinit var binding: HangmanBinding  // replaces synthetic binding

    private var theSecretWord = ""            // random lemma from database
    private var theTranslation = ""           // to be shown at the end of the turn
    private var normalizedSecret = ""         // normalized secret word (the accented characters) and the final sigma(ς) replaced by sigma(σ))
    private var guessed = ""                  // guessed character
    private var reveal = ""                   // displays guessed (accented) characters (and correct final sigma)
    private var alreadyGuessedButWrong = ""   // string holding the faulty guesses
    private var aantalFout = 0                // error counter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = HangmanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.title_activity)
            subtitle = getString(R.string.subtitle_hangman)
        }

        /* get a reference to the database */
        db = zorbaDBHelper.readableDatabase

        /* get the specific 'hangman' selection query from the Query manager (ex. not larger than 16 characters)
         * and put the resulting records in the cursor */
        mainCursor = db.rawQuery(QueryManager.hangmanQuery(), null)

        binding.btnAgain.setOnClickListener {
            /* if any more secret words are available, setup the next turn */
            if (mainCursor.moveToNext()) setupSecret() else colorToast(context = this, msg = getString(R.string.msg_thats_all))
        }

        /* if there is indeed a record returned...
         * ... setup the game  */
        if (mainCursor.moveToNext()) setupSecret()
    }

    private fun setupSecret() {
        /* all new turns start from here */

        /* retrieve the greek lemma and the translation from the current cursor-record */
        theSecretWord = mainCursor.getString(mainCursor.getColumnIndex("PureLemma")).toLowerCase(Locale.getDefault())
        theTranslation = mainCursor.getString(mainCursor.getColumnIndex("NL"))

        /* We will accept unaccented characters to match the accented original
         * Also we will accept a normal sigma(σ) to match the 'end'-sigma(ς)
         * For this purpose we use a normalized version of the secret word to match the guesses against. */
        normalizedSecret = theSecretWord.normalize()

        /* create the initial displayed word with as many underscores as the length of the secret word */
        val theLength = normalizedSecret.length
        reveal = "_".repeat(theLength)
        binding.textDashed.text = reveal

        /* initialize the incorrect guesses */
        alreadyGuessedButWrong = ""
        binding.textFaulty.text = alreadyGuessedButWrong

        /* init the error counter */
        aantalFout = 0

        /* don't show the translation yet/anymore */
        binding.textNl.text = ""

        /* trigger initial drawing */
        binding.gallow.applyFault(0)
    }

    private fun checkIt() {
        /* match the guessed character to the normalized secret word */
        var matchPos = normalizedSecret.indexOf(guessed)

        /* The result will be -1 for no match or the index of the matched character in the string*/
        if (matchPos == -1) {
            /* no match so the guess was wrong */

            /* add guess to the string of faulty characters and display it to the user */
            if (guessed !in alreadyGuessedButWrong) alreadyGuessedButWrong += guessed
            binding.textFaulty.text = alreadyGuessedButWrong

            /* increase the error count */
            aantalFout++

            /* draw the body parts for that error count */
            binding.gallow.applyFault(aantalFout)

            /* check if already dead */
            if (aantalFout > 7) {
                colorToast(context = this, msg = getString(R.string.msg_you_hang), bgColor = Color.RED)
                binding.textDashed.text = theSecretWord
                binding.textNl.text = getString(R.string.hangman_meaning, theTranslation)
            }
        } else {
            /* match found, handle all matches for this character */
            while (matchPos != -1) {
                /* replace the underscore in the revealed string with the original (unnormalized) character. */
                reveal = reveal.replaceRange(matchPos, matchPos + 1, theSecretWord.substring(matchPos, matchPos + 1))
                /* find next match */
                matchPos = normalizedSecret.indexOf(guessed, matchPos + 1)
            }

            /* show the current state of the guesses to the player */
            binding.textDashed.text = reveal

            /* check if the game is over (when the revealed word equals the secret word) */
            if (reveal == theSecretWord) {
                /* display congratulations  and show the translation */
                colorToast(context = this, msg = getString(R.string.msg_got_it), bgColor = Color.GREEN, fgColor = Color.BLACK)
                binding.textNl.text = getString(R.string.hangman_meaning, theTranslation)
            }
        }
    }

    fun onGreekLetter(view: View) {
        /* Key presses on each greek character, all come here for handling
         * Each character button has the character itself as tag property.
         * No need to identify the button, just use the tag to know the character pressed */
        guessed = view.tag.toString()
        /* spaces are allowed in the secret word (lemma); they have tag underscore(_) */
        if (guessed == "_") guessed = " "
        checkIt()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        /* Handle action bar item clicks here. */
        if (item.itemId == android.R.id.home) finish() else super.onOptionsItemSelected(item)
        return true
    }

    override fun finish() {
        mainCursor.close()
        db.close()
        super.finish()
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)    }
}

class HangmanCanvas(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private var fout = 0
    private val myPaint = Paint()
    private var gallowParts = floatArrayOf()
    private var headCoords = floatArrayOf()
    private var bodyParts = floatArrayOf()
    private val STROKEWIDTH = 12f
    private val GRID_UNIT = 30f

    init {
        gallowParts  = (floatArrayOf(GRID_UNIT * 2f, GRID_UNIT * 20f, GRID_UNIT * 8f, GRID_UNIT * 20f)) //base
        gallowParts += (floatArrayOf(GRID_UNIT * 4f, GRID_UNIT * 20f, GRID_UNIT * 4f, GRID_UNIT * 2f))  //pole
        gallowParts += (floatArrayOf(GRID_UNIT * 3f, GRID_UNIT * 2f, GRID_UNIT * 12f, GRID_UNIT * 2f))  //beam
        gallowParts += (floatArrayOf(GRID_UNIT * 4f, GRID_UNIT * 6f, GRID_UNIT * 8f, GRID_UNIT * 2f))   //support
        gallowParts += (floatArrayOf(GRID_UNIT * 10f, GRID_UNIT * 2f, GRID_UNIT * 10f, GRID_UNIT * 6f)) //rope

        /* headCoords are used to set left, top, right, and bottom of the oval making the head */
        headCoords = (floatArrayOf(GRID_UNIT * 9f, GRID_UNIT * 6f, GRID_UNIT * 11f, GRID_UNIT * 8f))

        bodyParts  = (floatArrayOf(GRID_UNIT * 10f, GRID_UNIT * 8f, GRID_UNIT * 10f, GRID_UNIT * 13f))  // neck and body
        bodyParts += (floatArrayOf(GRID_UNIT * 10f, GRID_UNIT * 9f, GRID_UNIT * 6f, GRID_UNIT * 11f))   // left arm
        bodyParts += (floatArrayOf(GRID_UNIT * 10f, GRID_UNIT * 9f, GRID_UNIT * 14f, GRID_UNIT * 11f))  // right arm
        bodyParts += (floatArrayOf(GRID_UNIT * 10f, GRID_UNIT * 13f, GRID_UNIT * 8f, GRID_UNIT * 18f))  // left leg
        bodyParts += (floatArrayOf(GRID_UNIT * 10f, GRID_UNIT * 13f, GRID_UNIT * 12f, GRID_UNIT * 18f)) // right leg
        bodyParts += (floatArrayOf(GRID_UNIT * 8f,  GRID_UNIT * 18f, GRID_UNIT * 7f, GRID_UNIT * 17f))  // left foot
        bodyParts += (floatArrayOf(GRID_UNIT * 12f, GRID_UNIT * 18f, GRID_UNIT * 13f, GRID_UNIT * 17f)) // right foot
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        if (canvas == null) return

        myPaint.color = ContextCompat.getColor(context, R.color.gallow_color)
        myPaint.strokeWidth = STROKEWIDTH * 1.5f
        myPaint.style = Paint.Style.STROKE  // unfilled shapes (the head)

        /* draw the gallow */
        canvas.drawLines(gallowParts, myPaint)

        /* change the line color */
        myPaint.color = ContextCompat.getColor(context, R.color.body_color)
        myPaint.strokeWidth = STROKEWIDTH
        /* draw the head if error count is 1 or more */
        if (fout > 0) canvas.drawOval(headCoords[0], headCoords[1], headCoords[2], headCoords[3], myPaint)

        /* draw the other body parts */
        if (fout > 1) canvas.drawLines(bodyParts, 0, 4 * (fout - 1), myPaint)
    }

    fun applyFault(pfout: Int) {
        /* maximum errors are 8 */
        fout = if (pfout < 8) pfout else 8
        postInvalidate()
    }
}