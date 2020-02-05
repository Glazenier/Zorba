package driemondglas.nl.zorba

import android.annotation.SuppressLint
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.graphics.Color
import android.graphics.Rect
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import kotlinx.android.synthetic.main.verb_game.*

@SuppressLint("SetTextI18n")
class VerbGame : AppCompatActivity() {
    private val queryManager: QueryManager = QueryManager.getInstance()
    private lateinit var mainCursor: Cursor
    private lateinit var db: SQLiteDatabase

    private var theVerb = ""         // holds the unconjured Enestotas (present)
    private var theMeaning = ""      // holds the NL translation of the verb
    private var tijdvorm = ""        // holds the randomly choosen tense
    private var theConjugation = ""  // holds the randomly selected conjugation in the choosen tense
    private var thePersonGR = ""     // keeps greek personal pronoun in sync with choosen conjugation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.verb_game)

        val myBar = supportActionBar
        if (myBar != null) {
            /* This next line shows the home/back button.
             * The functionality is handled by the android system as long as a parent activity
             * is specified in the manifest.xls file
             */
            myBar.setDisplayHomeAsUpEnabled(true)
            myBar.title = "ZORBA Verb Game"
            myBar.subtitle = "by Herman"
        }

        /* get a reference to the database */
        db = zorbaDBHelper.readableDatabase

        /* get the specific selection query from the Query manager  */
        val query = queryManager.verbGameQuery()
        mainCursor = db.rawQuery(query, null)


        btn_go.setOnTouchListener { v: View, m: MotionEvent -> touche(v, m); true }
        btn_reveal.setOnClickListener { txt_conjugations.visibility=if(txt_conjugations.visibility==View.VISIBLE) View.INVISIBLE else View.VISIBLE }
        sw_mode.setOnClickListener { showByMode() }

        /* if indeed a record is returned...
           ... setup the game */
        forwardToNextMatch()
    }

    private fun setupVerb(): Boolean {
        /* Setup the textstrings for ask and reveal */

        theVerb = mainCursor.getString(mainCursor.getColumnIndex("PureLemma"))
        theMeaning = mainCursor.getString(mainCursor.getColumnIndex("NL"))
        val textGR = mainCursor.getString(mainCursor.getColumnIndex("GR"))

        /* check which tenses are available in the Greek text field */
        var tenseIsAvailable = if (chk_enestotas.isChecked) "e" else ""
        if (hasMellontas(textGR) && chk_mellontas.isChecked) tenseIsAvailable += "m"
        if (hasAorist(textGR) && chk_aoristos.isChecked) tenseIsAvailable += "a"
        if (hasParatatikos(textGR) && chk_paratatikos.isChecked) tenseIsAvailable += "p"

        if (tenseIsAvailable.isNotEmpty()) {
            /* pick a random position in the string with available tenses */
            val sixConjugations = when (tenseIsAvailable.random()) {
                'e' -> {
                    tijdvorm = "Tegenwoordige tijd"
                    conjureEnestotas(textGR)
                }
                'm' -> {
                    tijdvorm = "Toekomende tijd"
                    conjureMellontas(textGR)
                }
                'a' -> {
                    tijdvorm = "Aorist"
                    conjureAorist(textGR)
                }
                'p' -> {
                    tijdvorm = "Onvoltooid verleden tijd"
                    conjureParatatikos(textGR)
                }
                else -> UNKNOWN_VERB
            }

            val selectedPersons = mutableSetOf<Int>()
            if (chk_1.isChecked) selectedPersons.add(0)
            if (chk_2.isChecked) selectedPersons.add(1)
            if (chk_3.isChecked) selectedPersons.add(2)
            if (chk_4.isChecked) selectedPersons.add(3)
            if (chk_5.isChecked) selectedPersons.add(4)
            if (chk_6.isChecked) selectedPersons.add(5)

            if (selectedPersons.size != 0) {
                txt_conjugations.text = sixConjugations.replace(", ".toRegex(), "\n")
                val conjugationParts = sixConjugations.split(",")
                if (conjugationParts.size == 1) {
                    theConjugation = theVerb
                    tijdvorm = ""
                    thePersonGR = ""
                } else {
                    /* pick one of the choosen conjugations */
                    val pickOne = selectedPersons.shuffled().last()
                    theConjugation = conjugationParts[pickOne]
                    val persoonsvormGR = listOf("Εγώ", "Εσύ", listOf("Αυτός", "Αυτή", "Αυτό").random(), "Εμείς", "Εσείς", listOf("Αυτοί", "Αυτές", "Αυτά").random())
                    thePersonGR = persoonsvormGR[pickOne]
                    showByMode()
                }
                return true
            } else return false
        } else return false
    }

    private fun showByMode() {
        if (sw_mode.isChecked) {
            text_ask.text = theConjugation
            text_reveal.text = "Komt van: $theVerb\n$theMeaning\n\n$tijdvorm"
        } else {
            val dashes = "⎽ ".repeat(theConjugation.length)
            text_ask.text = "$thePersonGR $dashes ($theVerb)"
            text_reveal.text = "              $tijdvorm"
        }

        text_reveal.visibility = if (sw_mode.isChecked) View.INVISIBLE else View.VISIBLE
    }

    private fun reveal() {
        text_reveal.visibility = View.VISIBLE
        if (sw_mode.isChecked) {
            text_ask.text = "$thePersonGR $theConjugation"
        } else {
            text_ask.text = "$thePersonGR $theConjugation ($theVerb)"
            text_reveal.text = "              $tijdvorm\n\nBetekenis: $theMeaning"
        }
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        /* Handle action bar item clicks here. */
        if (item.itemId == android.R.id.home) finishIntent() else super.onOptionsItemSelected(item)
        return true
    }

    /* finish intent and return to main activity */
    private fun finishIntent() {
        mainCursor.close()
        db.close()
        finish()
    }

    /* Actions needed when touching and releasing buttons of text field, emulating key-down / key-up events
    * Checks if release is inside or outside original view boundaries */
    private fun touche(v: View, m: MotionEvent) {
        when (m.actionMasked) {
            MotionEvent.ACTION_DOWN -> reveal()
            MotionEvent.ACTION_UP -> {
                /* check if release is  within the source view */
                val boundaries = Rect(0, 0, v.width, v.height)
                /* correct false negative/positive count */
                if (boundaries.contains(m.x.toInt(), m.y.toInt())) forwardToNextMatch()
            }
        }
    }

    private fun forwardToNextMatch() {
        txt_conjugations.visibility=View.INVISIBLE
        while (mainCursor.moveToNext()) {
            if (setupVerb()) break
        }

        if (mainCursor.isAfterLast) Utils.colorToast(this, "Thats all", Color.BLUE)
    }
}
