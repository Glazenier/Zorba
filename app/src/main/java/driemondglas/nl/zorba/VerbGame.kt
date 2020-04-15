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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import driemondglas.nl.zorba.Utils.colorToast
import driemondglas.nl.zorba.Utils.enabled
import kotlinx.android.synthetic.main.verb_game.*


@SuppressLint("SetTextI18n")
class VerbGame : AppCompatActivity() {
    private val queryManager: QueryManager = QueryManager.getInstance()
    private lateinit var gameCursor: Cursor
    private lateinit var db: SQLiteDatabase


    private var mode = "conjugate"

    private var theIdx = 0
    private var theGreek = ""        // the entire greek field in the db table
    private var theVerb = ""         // holds the un-conjugated Enestotas (present)
    private var theMeaning = ""      // holds the NL translation of the verb
    private var theTense = ""        // holds the randomly choosen tense
    private var theConjugation = ""  // holds the randomly selected conjugation in the choosen tense
    private var thePersonGR = ""     // keeps greek personal pronoun in sync with choosen conjugation
    private var dashes = ""          // creates baseline of dashes

    private var savedLevelBasic = true
    private var savedLevelAdvanced = true
    private var savedLevelBallast = true
    private var seconds = 0
    private var scores = mutableListOf<Int>()
    private var fastest = 3000

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
        btn_go.setOnTouchListener { v: View, m: MotionEvent -> touche(v, m); true }
        btn_show_ladder.setOnClickListener { txt_conjugations.visibility = if (txt_conjugations.visibility == View.VISIBLE) View.INVISIBLE else View.VISIBLE }
        sw_mode.setOnClickListener { switchGameMode() }
        btn_fout.setOnClickListener { startOver() }
        lbl_herken.setOnClickListener {
            sw_mode.isChecked = !sw_mode.isChecked
            switchGameMode()
        }
        sw_level_basis.setOnClickListener { onLevelChange() }
        sw_level_gevorderd.setOnClickListener { onLevelChange() }
        sw_level_ballast.setOnClickListener { onLevelChange() }
        btn_previous.setOnClickListener { backwardToPreviousMatch() }
        btn_verbal.setOnClickListener { cleanSpeech("$thePersonGR  $theConjugation", "anders") }

        /* save original QueryManager's level values mand set initial values for the game*/
        with (queryManager) {
            savedLevelBasic = levelBasic
            savedLevelAdvanced = levelAdvanced
            savedLevelBallast = levelBallast

            /* for the game start with basic level only */
            sw_level_basis.isChecked = true
            sw_level_gevorderd.isChecked = false
            sw_level_ballast.isChecked = false

            levelBasic = true
            levelAdvanced = false
            levelBallast = false
        }

        /* get the specific selection query from the Query manager  */
        gameCursor = db.rawQuery(queryManager.verbGameQuery(), null)

        /* ... and setup the game */
        forwardUntilMatch()
    }

    override fun onStop() {
        /* restore original level values as before the game started */
        with (queryManager) {
            levelBasic = savedLevelBasic
            levelAdvanced = savedLevelAdvanced
            levelBallast = savedLevelBallast
        }
        super.onStop()
    }

    /*  Switch between recognising (determinate) a conjugation and conjugating a given verb */
    private fun switchGameMode() {
        if (sw_mode.isChecked) {
            mode = "determinate"
            sw_mode.setTextColor(Color.DKGRAY)
            sw_mode.setTypeface(null, Typeface.NORMAL)
            lbl_herken.setTextColor(ContextCompat.getColor(this, R.color.colorAccent))
            lbl_herken.setTypeface(null, Typeface.BOLD)
        } else {
            mode = "conjugate"
            sw_mode.setTextColor(ContextCompat.getColor(this, R.color.colorAccent))
            sw_mode.setTypeface(null, Typeface.BOLD)
            lbl_herken.setTextColor(Color.DKGRAY)
            lbl_herken.setTypeface(null, Typeface.NORMAL)
        }
        showByMode()
    }

    /* move forward in cursor until a verb is found that contains selected conjugation (ex: Not all have Paratatikos!) */
    private fun forwardUntilMatch() {
        txt_conjugations.visibility = View.INVISIBLE
        text_ask.setTextColor(Color.DKGRAY)
        while (gameCursor.moveToNext()) {
            if (setupVerb()) break
        }
        if (gameCursor.isAfterLast) colorToast(this, "Thats all", Color.BLUE) else showByMode()
    }

    /* move backwards in cursor until a verb is found that contains selected conjugation (ex: Not all have Paratatikos!) */
    private fun backwardToPreviousMatch() {
        txt_conjugations.visibility = View.INVISIBLE
        text_ask.setTextColor(Color.DKGRAY)
        while (gameCursor.moveToPrevious()) {
            if (setupVerb()) break
        }
        if (gameCursor.isBeforeFirst) colorToast(this, "Thats all", Color.BLUE) else showByMode()
    }

    private fun setupVerb(): Boolean {
        /* Setup the textstrings for ask and reveal. retruns false if no conjugation matches */

        theIdx = gameCursor.getInt(gameCursor.getColumnIndex("idx"))
        theVerb = gameCursor.getString(gameCursor.getColumnIndex("PureLemma"))
        theMeaning = gameCursor.getString(gameCursor.getColumnIndex("NL"))
        theGreek = gameCursor.getString(gameCursor.getColumnIndex("GR"))

        /* check which tenses are available in the Greek text field */
        var tenseIsAvailable = if (chk_enestotas.isChecked) "e" else ""
        if (hasMellontas(theGreek) && chk_mellontas.isChecked) tenseIsAvailable += "m"
        if (hasAorist(theGreek) && chk_aoristos.isChecked) tenseIsAvailable += "a"
        if (hasParatatikos(theGreek) && chk_paratatikos.isChecked) tenseIsAvailable += "p"
        if (hasMellontas(theGreek) && chk_prostaktiki.isChecked) tenseIsAvailable += "g"  // need mellondas to build gebiedende wijs

        if (tenseIsAvailable.isNotEmpty()) {
            /* pick a random position in the string with available tenses */
            val sixConjugations = when (tenseIsAvailable.random()) {
                'e' -> {
                    theTense = "Tegenwoordige tijd"
                    conjugateEnestotas(theGreek)
                }
                'm' -> {
                    theTense = "Toekomende tijd"
                    conjugateMellontas(theGreek)
                }
                'a' -> {
                    theTense = "Verleden tijd"
                    conjugateAorist(theGreek)
                }
                'p' -> {
                    theTense = "Onvoltooid verleden tijd"
                    conjugateParatatikos(theGreek)
                }
                'g' -> {
                    theTense = "Gebiedende wijs"
                    createProstaktiki(theGreek)
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
                    theConjugation = conjugationParts[0]
                    thePersonGR = ""
                } else {
                    /* pick one of the choosen conjugations */
                    val pickOne = selectedPersons.shuffled().last()
                    theConjugation = conjugationParts[pickOne].trim()
                    val persoonsvormGR = listOf("Εγώ", "Εσύ", listOf("Αυτός", "Αυτή", "Αυτό").random(), "Εμείς", "Εσείς", listOf("Αυτοί", "Αυτές", "Αυτά").random())
                    thePersonGR = persoonsvormGR[pickOne]
                }
                dashes = "⎽ ".repeat(theConjugation.length)
                chronometer.base = SystemClock.elapsedRealtime()
                chronometer.start()


                return true
            } else return false
        } else return false
    }

    private fun showByMode() {
        if (mode == "conjugate") {
            text_ask.text = "$thePersonGR $dashes ($theVerb)"
            text_reveal.text = " ".repeat(10) + theTense
            text_reveal.visibility = View.VISIBLE // initially part of the question
        } else {
            text_ask.text = theConjugation
            text_reveal.text = "$theTense van $theVerb ($theMeaning)"
            text_reveal.visibility = View.INVISIBLE
        }
    }

    private fun reveal() {
        text_reveal.visibility = View.VISIBLE
        text_ask.setTextColor(ContextCompat.getColor(this, R.color.kobaltblauw))
        text_ask.text = "$thePersonGR $theConjugation"

        val textReveal = SpannableString("$theTense van $theVerb ($theMeaning).")
        val spanStart = "$theTense van ".length
        val spanEnd = spanStart + theVerb.length

        textReveal.setSpan(StyleSpan(Typeface.BOLD_ITALIC), spanStart, spanEnd, 0)
        textReveal.setSpan(ForegroundColorSpan(ContextCompat.getColor(this, R.color.kobaltblauw)), spanStart, spanEnd, 0)
        text_reveal.text = textReveal

        chronometer.stop()
        seconds = chronometer.text.take(2).toString().toInt() * 60 + chronometer.text.takeLast(2).toString().toInt()
        scores.add(seconds)

        lbl_total.text = "Totaaltijd:  ${intToTime(scores.sum())}"
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
        if (!(chk_1.isChecked || chk_2.isChecked || chk_3.isChecked || chk_4.isChecked || chk_5.isChecked || chk_6.isChecked)) {
            colorToast(this, "Need at least one person selected.", Color.RED)
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
        with (queryManager) {
            levelBasic = sw_level_basis.isChecked
            levelAdvanced = sw_level_gevorderd.isChecked
            levelBallast = sw_level_ballast.isChecked
        }
        gameCursor = db.rawQuery(queryManager.verbGameQuery(), null)
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
        val min = intToTime(scores.min()!!)
        val max = intToTime(scores.max()!!)
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
    }

    private fun startOver() {
        scores.clear()
        lbl_total.text = ""
        lbl_count.text = ""
        forwardUntilMatch()
    }
}
