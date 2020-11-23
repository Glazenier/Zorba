package driemondglas.nl.zorba

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.CheckBox
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import driemondglas.nl.zorba.Utils.enabled
import driemondglas.nl.zorba.Utils.toggleVisibility
import kotlinx.android.synthetic.main.verb_game.*

@SuppressLint("SetTextI18n")
class VerbGame : AppCompatActivity() {

    private lateinit var db: SQLiteDatabase // shall be reference to the local sqlite database
    private lateinit var gameCursor: Cursor // shall contain the records selected by the "verbgamequery" in the QueryManager

    private var theIdx = 0           // indexnumber of current lemma
    private var theGreek = ""        // the entire greek field in the db table
    private var theVerb = ""         // holds the un-conjugated Enestotas (present)
    private var theMeaning = ""      // holds the NL translation of the verb
    private var theTense = ""        // holds the randomly choosen tense
    private var theConjugation = ""  // holds the randomly selected conjugation in the choosen tense
    private var thePersonGR = ""     // keeps greek personal pronoun in sync with choosen conjugation
    private var theDashes = ""       // creates baseline of dashes
    // short description of the possible person & number combinations
    private val shortPerson = listOf("1ste pers. enkelv.", "2-de pers. enkelv.", "3de pers. enkelv.", "1ste pers. meerv.", "2de pers. meerv.", "3de pers. meerv.")
    // actually picked(randomly) person & number
    private var theShort = ""

    private val selectedPersons = mutableSetOf<Int>()  // holds preselected possible persons
    private var savedLevelBasic = true
    private var savedLevelAdvanced = true
    private var savedLevelBallast = true
    private var seconds = 0
    private var scores = mutableListOf<Int>()
    private var fastest = 3000  // keeps track of fastest score

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.verb_game)

        /* setup action bar on top */
        val actionBar = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(true)
        actionBar?.title = "ZORBA Werkwoord Game"

        /* get a reference to the database */
        db = zorbaDBHelper.readableDatabase

        /* setup chronometer/stopwatch */
        chronometer.base = SystemClock.elapsedRealtime()

        /* retrieve 'high score' (fastest time) from shared preferences */
        fastest = zorbaPreferences.getInt("fastest", 3000)  // 5 min is the default value

        /* initialise all onClick listeners here */
        btn_go.setOnTouchListener { v: View, m: MotionEvent ->
            touche(v, m)
            v.performClick()
            true }

        sw_mode.setOnClickListener { switchGameMode() }

        lbl_herken.setOnClickListener {
            sw_mode.isChecked = !sw_mode.isChecked
            switchGameMode()
        }

        sw_level_basis.setOnClickListener { onLevelChange() }
        sw_level_gevorderd.setOnClickListener { onLevelChange() }
        sw_level_ballast.setOnClickListener { onLevelChange() }
        chk_type1.setOnClickListener { onEndingChange() }
        chk_type2.setOnClickListener { onEndingChange() }
        chk_type3.setOnClickListener { onEndingChange() }
        btn_fout.setOnClickListener { startOver() }
        btn_previous.setOnClickListener { backwardToPreviousMatch() }
        btn_verbal.setOnClickListener { cleanSpeech("$thePersonGR  $theConjugation", "anders") }
        btn_show_ladder.setOnClickListener {
            txt_reveal.visibility = if (txt_conjugations.visibility == View.VISIBLE) View.VISIBLE else View.GONE
            txt_conjugations.toggleVisibility()
            txt_persons.toggleVisibility()
        }

        /* save original level values and set initial values for the game */
        savedLevelBasic = levelBasic
        savedLevelAdvanced = levelAdvanced
        savedLevelBallast = levelBallast

        /* for the game start with basic level only */
        sw_level_basis.isChecked = true
        sw_level_gevorderd.isChecked = true
        sw_level_ballast.isChecked = true

        levelBasic = true
        levelAdvanced = true
        levelBallast = true

        /* init selected person list based on default switch settings */
        if (chk_1.isChecked) selectedPersons.add(0)
        if (chk_2.isChecked) selectedPersons.add(1)
        if (chk_3.isChecked) selectedPersons.add(2)
        if (chk_4.isChecked) selectedPersons.add(3)
        if (chk_5.isChecked) selectedPersons.add(4)
        if (chk_6.isChecked) selectedPersons.add(5)


        /* get the specific selection query from the Query Manager */
        gameCursor = db.rawQuery(QueryManager.verbGameQuery(), null)

        /* ... and setup the game ... */
        forwardUntilMatch()
    }

    override fun onStop() {
        /* restore original level values as before the game was started */
        levelBasic = savedLevelBasic
        levelAdvanced = savedLevelAdvanced
        levelBallast = savedLevelBallast
        super.onStop()
    }

    /*  Switch between recognising (determinate) a conjugation and conjugating a given verb */
    private fun switchGameMode() {
        if (sw_mode.isChecked) {
            sw_mode.setTextColor(ContextCompat.getColor(applicationContext, R.color.normal_text_color))
            sw_mode.setTypeface(null, Typeface.NORMAL)
            lbl_herken.setTextColor(ContextCompat.getColor(applicationContext, R.color.colorAccent))
            lbl_herken.setTypeface(null, Typeface.BOLD)
        } else {
            sw_mode.setTextColor(ContextCompat.getColor(applicationContext, R.color.colorAccent))
            sw_mode.setTypeface(null, Typeface.BOLD)
            lbl_herken.setTextColor(ContextCompat.getColor(applicationContext, R.color.normal_text_color))
            lbl_herken.setTypeface(null, Typeface.NORMAL)
        }
        showByMode()
    }

    /* move forward in cursor until a verb is found that contains selected conjugation (ex: Not all have Paratatikos!) */
    private fun forwardUntilMatch() {
        txt_conjugations.visibility = View.INVISIBLE
        txt_persons.visibility = View.INVISIBLE
        text_ask.setTextColor(ContextCompat.getColor(applicationContext, R.color.normal_text_color))

        /* this loops until a matching record is found */
        while (gameCursor.moveToNext()) {
            if (setupVerb()) break
        }
        /*     .    .    .    .    .    .    .    .    */

        if (gameCursor.isAfterLast) colorToast(context = applicationContext, msg = "Thats all", bgColor = Color.BLUE) else showByMode()
    }

    /* move backwards in cursor until a verb is found that contains selected conjugation */
    private fun backwardToPreviousMatch() {
        txt_conjugations.visibility = View.INVISIBLE
        txt_persons.visibility = View.INVISIBLE
        text_ask.setTextColor(ContextCompat.getColor(applicationContext, R.color.normal_text_color))
        while (gameCursor.moveToPrevious()) {
            if (setupVerb()) break
        }
        if (gameCursor.isBeforeFirst) colorToast(context = applicationContext, msg = "Thats all", bgColor = Color.BLUE) else showByMode()
    }

    /* Setup the textstrings for ask and reveal. retruns false if no conjugation matches */
    private fun setupVerb(): Boolean {
        theIdx = gameCursor.getInt(gameCursor.getColumnIndex("idx"))
        theGreek = gameCursor.getString(gameCursor.getColumnIndex("GR"))
        theMeaning = gameCursor.getString(gameCursor.getColumnIndex("NL"))
        theVerb = gameCursor.getString(gameCursor.getColumnIndex("PureLemma"))

        /* check which tenses are available in the Greek text field  AND selected in the UI */
        val availableTenses = mutableSetOf<String>()  // holds each tense available in the Greek text

        if (chk_enestotas.isChecked) availableTenses.add("Tegenwoordige tijd")
        if (hasMellontas(theGreek) && chk_mellontas.isChecked) availableTenses.add("Toekomende tijd")
        if (hasAorist(theGreek) && chk_aoristos.isChecked) availableTenses.add("Verleden tijd")
        if (hasParatatikos(theGreek) && chk_paratatikos.isChecked) availableTenses.add("Onvoltooid verleden tijd")
        if (hasMellontas(theGreek) && chk_prostaktiki.isChecked) availableTenses.add("Gebiedende wijs")  // need mellontas to build gebiedende wijs

        if (availableTenses.size > 0)      {
            /* pick a random tense in the set of available tenses */
            theTense = availableTenses.shuffled().first()

            val conjugations = when (theTense) {
                "Tegenwoordige tijd" -> { conjugateEnestotas(theGreek)  }
                "Toekomende tijd" -> { conjugateMellontas(theGreek) }
                "Verleden tijd" -> { conjugateAoristos(theGreek) }
                "Onvoltooid verleden tijd" -> { conjugateParatatikos(theGreek) }
                "Gebiedende wijs" -> { createProstaktiki(theGreek) }
                else -> listOf("Werkwoordvorm onbekend")
            }

            if (selectedPersons.isNotEmpty()) {
                val persoonList = listOf("εγώ", "εσύ", "αυτός,-ή,-ό", "εμείς", "εσείς", "αυτοί,-ές,-ά")

                when (conjugations.size) {
                    6 -> {
                        txt_conjugations.text = "$theTense:\n" + conjugations.joinToString("\n")
                        txt_persons.text = "\n" + persoonList.joinToString("\n")
                    }
                    2 -> {
                        txt_conjugations.text = "$theTense:\n" + conjugations[0] + "\n" + conjugations[1]
                        txt_persons.text = "\nenkelvoud:\nmeervoud:"
                    }
                    1 -> {
                        txt_conjugations.text = "$theTense:\n" + conjugations[0]
                        txt_persons.text =""
                    }
                  else -> {
                      txt_conjugations.text = "$theTense:\nunexpected!"
                      txt_persons.text =""
                  }
                }

                when(conjugations.size) {
                    6 -> {
                        /* pick one of the choosen conjugations */
                        val pickOne = selectedPersons.shuffled().last()
                        theConjugation = conjugations[pickOne]
                        val persoonsvormGR = listOf("Εγώ", "Εσύ", setOf("Αυτός", "Αυτή", "Αυτό").random(), "Εμείς", "Εσείς", setOf("Αυτοί", "Αυτές", "Αυτά").random())
                        thePersonGR = persoonsvormGR[pickOne]
                        theShort = shortPerson[pickOne]
                    }
                    2 -> {
                        /* gebiedende wijs */
                        theConjugation = conjugations[0] + " - " + conjugations[1]
                        thePersonGR = ""
                    }
                    1 -> {
                        /* warnings mostly */
                        theConjugation = conjugations[0]
                        thePersonGR = ""
                    }
                }
                theDashes = "⎽ ".repeat(theConjugation.length)
                chronometer.base = SystemClock.elapsedRealtime()
                chronometer.start()

                return true
            } else return false
        } else return false
    }

    private fun showByMode() {
        if (!sw_mode.isChecked) {
            text_ask.text = "$thePersonGR $theDashes ($theVerb)"
            txt_reveal.text = " ".repeat(10) + theTense
            txt_reveal.visibility = View.VISIBLE // initially part of the question
        } else {
            text_ask.text = theConjugation
            txt_reveal.text = "$theTense van $theVerb ($theMeaning)"
            txt_reveal.visibility = View.INVISIBLE
        }
    }

    private fun reveal() {
        txt_conjugations.visibility = View.INVISIBLE
        txt_persons.visibility = View.INVISIBLE
        txt_reveal.visibility = View.VISIBLE
        text_ask.setTextColor(ContextCompat.getColor(applicationContext, R.color.blue_text_color))
        text_ask.text = "$thePersonGR $theConjugation"

        val textReveal = SpannableString("$theTense, $theShort van $theVerb ($theMeaning).")
        val spanStart = "$theTense, $theShort van ".length
        val spanEnd = spanStart + theVerb.length

        textReveal.setSpan(StyleSpan(Typeface.BOLD_ITALIC), spanStart, spanEnd, 0)
        textReveal.setSpan(ForegroundColorSpan(ContextCompat.getColor(applicationContext, R.color.blue_text_color)), spanStart, spanEnd, 0)
        txt_reveal.text = textReveal

        chronometer.stop()
        seconds = chronometer.text.take(2).toString().toInt() * 60 + chronometer.text.takeLast(2).toString().toInt()
        scores.add(seconds)

        lbl_total.text = "Totaal:  ${intToTime(scores.sum())}"
        lbl_count.text = "Aantal:  ${scores.size}"
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
                if (boundaries.contains(m.x.toInt(), m.y.toInt())) {
                    if (scores.size == 10)  buidScoreResult() else forwardUntilMatch()
                }
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onPersonClick(view: View) {
        val thisCheck=view as CheckBox
        if (!(chk_1.isChecked || chk_2.isChecked || chk_3.isChecked || chk_4.isChecked || chk_5.isChecked || chk_6.isChecked)) {
            colorToast(context = this, msg = "Need at least one person selected.", bgColor = Color.RED)
            thisCheck.isChecked = true
        } else {
            val tagInt = thisCheck.tag.toString().toInt()
            if (thisCheck.isChecked) selectedPersons.add(tagInt) else selectedPersons.remove(tagInt)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onTenseClick(view: View) {
        val thisCheck=view as CheckBox
        if (!(chk_enestotas.isChecked || chk_mellontas.isChecked || chk_aoristos.isChecked || chk_paratatikos.isChecked || chk_prostaktiki.isChecked )) {
            colorToast(context = this, msg = "Need at least one tense selected.", bgColor = Color.RED)
            thisCheck.isChecked = true
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
        levelBasic = sw_level_basis.isChecked
        levelAdvanced = sw_level_gevorderd.isChecked
        levelBallast = sw_level_ballast.isChecked
        gameCursor = db.rawQuery(QueryManager.verbGameQuery(), null)
        forwardUntilMatch()
    }

    private fun onEndingChange() {
        /* if no ending is selected, select all */
        if (!(chk_type1.isChecked || chk_type2.isChecked || chk_type3.isChecked)) {
            chk_type1.isChecked = true
            chk_type2.isChecked = true
            chk_type3.isChecked = true
        }
        /* forward to query manager */
        gameCursor = db.rawQuery(QueryManager.verbGameQuery(chk_type1.isChecked,chk_type2.isChecked,chk_type3.isChecked), null)
        forwardUntilMatch()
    }
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        /* Inflate the menu; this adds menu items to the zorba action bar. */
        menuInflater.inflate(R.menu.menu_verbgame, menu)
        menu.findItem(R.id.menu_game_speech).isChecked = useSpeech
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        /* Handle action bar item clicks here. */
        when (item.itemId) {

            /* speech on/off */
            R.id.menu_game_speech -> {
                useSpeech = !item.isChecked
                item.isChecked = !item.isChecked
                btn_verbal.enabled(useSpeech)
            }
            R.id.menu_mail_lemma -> mailLemma()

            R.id.menu_reset_hiscore -> {
                /*  reset fastest time */
                zorbaPreferences.edit()
                      .putInt("fastest", 3000)
                      .apply()
                fastest = 3000
            }

            R.id.menu_wordreference -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.wordreference.com/gren/$theVerb"))
                startActivity(intent)
            }
            R.id.menu_neurolingo -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://www.neurolingo.gr/en/online_tools/lexiscope.htm?term=$theVerb"))
                startActivity(intent)
            }
            /* home button (left arrow in app bar) pressed */
            android.R.id.home -> finishIntent()

            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun mailLemma() {
        val subject = "$theIdx: $theVerb"
        val body = "Idx: $theIdx $theVerb\n\n" +
              "Ask:\n$thePersonGR $theConjugation\n\n" +
              "Reveal:\n$theTense van $theVerb ($theMeaning)."

        val emailIntent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", getString(R.string.contact_email), null))
              .putExtra(Intent.EXTRA_SUBJECT, subject)
              .putExtra(Intent.EXTRA_TEXT, body)
        startActivity(Intent.createChooser(emailIntent, "Send lemma by email..."))
    }

    private fun intToTime(seconds: Int): String {
        val wholeMins = (seconds / 60)
        val restSeconds = seconds - (wholeMins * 60)
        return "${("0$wholeMins").takeLast(2)}:${("0$restSeconds").takeLast(2)}"
    }

    private fun intToMinSec(seconds: Int): String {
        val wholeMins = (seconds / 60)
        val restSeconds = seconds - (wholeMins * 60)
        return "$wholeMins minuut $restSeconds sec."
    }

    /* show the score of the answers after X correct */
    private fun buidScoreResult() {
        val aantal = scores.size
        val totaal = scores.sum()
        val min = intToTime(scores.minOrNull()!!)
        val max = intToTime(scores.maxOrNull()!!)
        val gemiddeld = intToTime(scores.average().toInt())
        var bravo=""
        if (totaal < fastest) {
            // maintain 'high score' (fastest) in shared preferences
            zorbaPreferences.edit()
                  .putInt("fastest", totaal)
                  .apply()
            fastest=totaal
            bravo = "Bravo! Dit is de snelste tijd.\n\n"
        }
        val textToDisplay = "$bravo${intToMinSec(totaal)}\n    voor $aantal antwoorden." +
                  "\nGemiddeld: $gemiddeld" +
                  "\nSnelste:   $min" +
                  "\nTraagste:  $max"

        val bob = AlertDialog.Builder(this)
              .setTitle("Goed gedaan.")
              .setMessage(textToDisplay)
              .setPositiveButton(R.string.emoji_ok) { _, _ -> startOver() }

        /* create the dialog from the builder */
        val alertDialog = bob.create()
        alertDialog.show()
        alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).textSize = 28f
        scores.clear()
        lbl_total.text = ""
        lbl_count.text = ""
    }

    private fun startOver() {
        scores.clear()
        lbl_total.text = ""
        lbl_count.text = ""
        forwardUntilMatch()
    }
}
