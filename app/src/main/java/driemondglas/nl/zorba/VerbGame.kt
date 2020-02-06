package driemondglas.nl.zorba

import android.annotation.SuppressLint
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import driemondglas.nl.zorba.Utils.colorToast
import kotlinx.android.synthetic.main.verb_game.*
@SuppressLint("SetTextI18n")
class VerbGame : AppCompatActivity() {
    private val queryManager: QueryManager = QueryManager.getInstance()
    private lateinit var gameCursor: Cursor
    private lateinit var db: SQLiteDatabase

    private var theVerb = ""         // holds the un-conjugated Enestotas (present)
    private var theMeaning = ""      // holds the NL translation of the verb
    private var theTense = ""        // holds the randomly choosen tense
    private var theConjugation = ""  // holds the randomly selected conjugation in the choosen tense
    private var thePersonGR = ""     // keeps greek personal pronoun in sync with choosen conjugation
//    private val attentionColor = ContextCompat.getColor(this, R.color.colorAccent)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.verb_game)

        /* setup action bar on top */
        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.title = "ZORBA Werkwoord Game"
        }

        /* get a reference to the database */
        db = zorbaDBHelper.readableDatabase

        /* get the specific selection query from the Query manager  */
        gameCursor = db.rawQuery(queryManager.verbGameQuery(), null)

        btn_go.setOnTouchListener { v: View, m: MotionEvent -> touche(v, m); true }
        btn_show_ladder.setOnClickListener { txt_conjugations.visibility = if (txt_conjugations.visibility == View.VISIBLE) View.INVISIBLE else View.VISIBLE }
        sw_mode.setOnClickListener { showByMode() }
        sw_level_basis.setOnClickListener { onLevelChange() }
        sw_level_gevorderd.setOnClickListener { onLevelChange() }
        sw_level_ballast.setOnClickListener { onLevelChange() }

        /* set level switches according to QueryManager's values */
        sw_level_basis.isChecked = queryManager.levelBasic
        sw_level_gevorderd.isChecked = queryManager.levelAdvanced
        sw_level_ballast.isChecked = queryManager.levelBallast

        /* if indeed a record is returned...
           ... setup the game */
        forwardToNextMatch()
    }

    /* move forward in cursor until a verb is found that contains selected conjugation (ex: Not all have Paratatikos!) */
    private fun forwardToNextMatch() {
        txt_conjugations.visibility = View.INVISIBLE
        text_ask.setTextColor(Color.DKGRAY)
        while (gameCursor.moveToNext()) {
            if (setupVerb()) break
        }
        if (gameCursor.isAfterLast) colorToast(this, "Thats all", Color.BLUE)
    }

    private fun setupVerb(): Boolean {
        /* Setup the textstrings for ask and reveal. retruns false if no conjugation matches */

        theVerb = gameCursor.getString(gameCursor.getColumnIndex("PureLemma"))
        theMeaning = gameCursor.getString(gameCursor.getColumnIndex("NL"))
        val textGR = gameCursor.getString(gameCursor.getColumnIndex("GR"))

        /* check which tenses are available in the Greek text field */
        var tenseIsAvailable = if (chk_enestotas.isChecked) "e" else ""
        if (hasMellontas(textGR) && chk_mellontas.isChecked) tenseIsAvailable += "m"
        if (hasAorist(textGR) && chk_aoristos.isChecked) tenseIsAvailable += "a"
        if (hasParatatikos(textGR) && chk_paratatikos.isChecked) tenseIsAvailable += "p"

        if (tenseIsAvailable.isNotEmpty()) {
            /* pick a random position in the string with available tenses */
            val sixConjugations = when (tenseIsAvailable.random()) {
                'e' -> {
                    theTense = "Tegenwoordige tijd"
                    conjugateEnestotas(textGR)
                }
                'm' -> {
                    theTense = "Toekomende tijd"
                    conjugateMellontas(textGR)
                }
                'a' -> {
                    theTense = "Aorist"
                    conjugateAorist(textGR)
                }
                'p' -> {
                    theTense = "Onvoltooid verleden tijd"
                    conjugateParatatikos(textGR)
                }
                else -> UNKNOWN_VERB
            }

            /* Create set of selected persons: 0 = 1st person singular, 1 = 2nd person singular, ..., 3 = 1st person plural ... etc.
             * questionned person is picked from this set */
            val selectedPersons = mutableSetOf<Int>()
            if (chk_1.isChecked) selectedPersons.add(0)
            if (chk_2.isChecked) selectedPersons.add(1)
            if (chk_3.isChecked) selectedPersons.add(2)
            if (chk_4.isChecked) selectedPersons.add(3)
            if (chk_5.isChecked) selectedPersons.add(4)
            if (chk_6.isChecked) selectedPersons.add(5)

            if (selectedPersons.isNotEmpty()) {
                txt_conjugations.text = sixConjugations.replace(", ".toRegex(), "\n")
                val conjugationParts = sixConjugations.split(",")
                if (conjugationParts.size == 1) {
                    theConjugation = theVerb
                    theTense = ""
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
            sw_mode.setTextColor(Color.DKGRAY)
            sw_mode.setTypeface(null, Typeface.NORMAL)

            lbl_herken.setTextColor(ContextCompat.getColor(this, R.color.colorAccent))
            lbl_herken.setTypeface(null, Typeface.BOLD)

            text_ask.text = theConjugation
            text_reveal.text = "Komt van: $theVerb\n$theMeaning\n\n$theTense"
        } else {
            sw_mode.setTextColor(ContextCompat.getColor(this, R.color.colorAccent))
            sw_mode.setTypeface(null, Typeface.BOLD)

            lbl_herken.setTextColor(Color.DKGRAY)
            lbl_herken.setTypeface(null, Typeface.NORMAL)

            val dashes = "⎽ ".repeat(theConjugation.length)
            text_ask.text = "$thePersonGR $dashes ($theVerb)"
            text_reveal.text = " ".repeat(14) + theTense
        }
        text_reveal.visibility = if (sw_mode.isChecked) View.INVISIBLE else View.VISIBLE
    }

    private fun reveal() {
        text_reveal.visibility = View.VISIBLE
        if (sw_mode.isChecked) {
            text_ask.text = "$thePersonGR $theConjugation"
        } else {
            text_ask.text = "$thePersonGR $theConjugation"
            text_ask.setTextColor(ContextCompat.getColor(this, R.color.colorAccent))
            text_reveal.text = " ".repeat(14) + "$theTense\n\nBetekenis: $theMeaning"
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        /* Handle action bar item clicks here. */
        if (item.itemId == android.R.id.home) finishIntent() else super.onOptionsItemSelected(item)
        return true
    }

    /* finish intent and return to main activity */
    private fun finishIntent() {
        gameCursor.close()
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
                if (boundaries.contains(m.x.toInt(), m.y.toInt())) forwardToNextMatch()
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onPersonClick(view: View) {
        if (!(chk_1.isChecked || chk_2.isChecked || chk_3.isChecked || chk_4.isChecked || chk_5.isChecked || chk_6.isChecked)){
            colorToast(this,"Need at least one person selected.",Color.RED)
            chk_1.isChecked = true
        }
    }

    private fun onLevelChange() {
        /* if no level is selected, select all */
        if (!(sw_level_basis.isChecked || sw_level_gevorderd.isChecked || sw_level_ballast.isChecked)) {
            sw_level_basis.isChecked = true
            sw_level_gevorderd.isChecked = true
            sw_level_ballast.isChecked = true
        }

        /* forward to query manager */
        queryManager.levelBasic = sw_level_basis.isChecked
        queryManager.levelAdvanced = sw_level_gevorderd.isChecked
        queryManager.levelBallast = sw_level_ballast.isChecked

        gameCursor = db.rawQuery(queryManager.verbGameQuery(), null)
        forwardToNextMatch()
    }
}
