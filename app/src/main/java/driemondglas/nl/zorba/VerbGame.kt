package driemondglas.nl.zorba

import android.annotation.SuppressLint
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import kotlinx.android.synthetic.main.verb_game.*

@SuppressLint("SetTextI18n")
class VerbGame : AppCompatActivity() {
    private val queryManager: QueryManager = QueryManager.getInstance()
    private lateinit var mainCursor: Cursor
    private lateinit var db: SQLiteDatabase

    private var theVerb = ""       // holds the Enestotas (present)
    private var theMeaning = ""    // holds the NL translation of the verb
    private var tijdvorm = ""      // holds the randomly choosen tense
    private var theConjurgation="" // holds the randomly selected conjugation in the choosen tense
    private var thePersonGR=""     // keeps greek personal pronoun in sync with choosen conjugation

    private lateinit var conjugationParts: List<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.verb_game)

        /* get a reference to the database */
        db = zorbaDBHelper.readableDatabase

        /* get the specific selection query from the Query manager  */
        val query = queryManager.verbGameQuery
        mainCursor = db.rawQuery(query, null)

        btn_go.setOnClickListener {if (mainCursor.moveToNext()) setupVerb() else Utils.colorToast(this, "Thats all")}
        btn_reveal.setOnClickListener { reveal() }
        sw_mode.setOnClickListener { onModeChange() }
        /* if there is indeed a record returned...
         * ... setup the game
         */
        if (mainCursor.moveToNext()) setupVerb()
    }

    private fun setupVerb(){
        /* Setup the texts for ask and reveal */
        theVerb = mainCursor.getString(mainCursor.getColumnIndex("PureLemma"))
        val textGR = mainCursor.getString(mainCursor.getColumnIndex("GR"))
        theMeaning = mainCursor.getString(mainCursor.getColumnIndex("NL"))

        /* check which tenses are available in the Greek text field */
        var tenseIsAvailable = "e"  // present is always there, or at least an expression
        if (hasMellontas(textGR)) tenseIsAvailable += "m"
        if (hasAorist(textGR)) tenseIsAvailable += "a"
        if (hasParatatikos(textGR)) tenseIsAvailable += "p"

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
            else -> "unknown"
        }

        conjugationParts = sixConjugations.split(",")
        if (conjugationParts[0].trim() == "Werkwoordtype onbekend") {
            theConjurgation = theVerb
            tijdvorm = ""
            thePersonGR = ""

        } else {
            /* choose one of the 6 conjugations */
            val conjureCount = conjugationParts.size
            val pickOne = (0 until conjureCount).shuffled().last()
            theConjurgation = conjugationParts[pickOne]
            val persoonsvormGR = listOf("Εγώ", "Εσύ", listOf("Αυτός", "Αυτή", "Αυτό").random(), "Εμείς", "Εσείς", listOf("Αυτοί", "Αυτές", "Αυτά").random())
            thePersonGR = persoonsvormGR[pickOne]
            showByMode()
        }
    }

    private fun showByMode(){
        if (sw_mode.isChecked) {
            text_ask.text = theConjurgation
            text_reveal.text = "Komt van: $theVerb\n$theMeaning\n\n$tijdvorm"
        } else {
            text_ask.text = theVerb
            text_reveal.text = "$thePersonGR $tijdvorm"
        }

        text_reveal.visibility = if (sw_mode.isChecked) View.INVISIBLE else View.VISIBLE
    }

    private fun reveal(){
        text_reveal.visibility = View.VISIBLE
        if (sw_mode.isChecked) {
            text_ask.text = "$thePersonGR $theConjurgation"
        } else {
            text_ask.text = "$thePersonGR $theConjurgation"
            text_reveal.text = "Komt van: $theVerb\n$theMeaning\n\n$tijdvorm"
        }
    }

    private fun onModeChange(){
        showByMode()
    }

    override fun onStop() {
        /* make sure the db etc get properly closed */
        mainCursor.close()
        db.close()
        super.onStop()
    }
}
