package driemondglas.nl.zorba

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.text.Spannable
import android.text.SpannableString
import android.text.format.DateUtils
import android.text.style.*
import android.view.*
import android.webkit.WebView
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import driemondglas.nl.zorba.Utils.enable
import driemondglas.nl.zorba.Utils.toggleVisibility
import driemondglas.nl.zorba.databinding.VerbGameBinding

class VerbGame : AppCompatActivity() {

    private lateinit var db: SQLiteDatabase        // shall be reference to the local sqlite database
    private lateinit var gameCursor: Cursor        // shall contain the records selected by the "verbgamequery" in the QueryManager
    private lateinit var binding: VerbGameBinding  // replaces synthetic binding

    private var theIdx = 0           // indexnumber of current lemma
    private var theGreek = ""        // the entire greek field in the db table
    private var theVerb = ""         // holds the un-conjugated Enestotas (present)
    private var theMeaning = ""      // holds the NL translation of the verb
    private var theTense = ""        // holds the randomly choosen tense
    private var theConjugation = ""  // holds the randomly selected conjugation in the choosen tense
    private var thePersonGR = ""     // keeps greek personal pronoun in sync with choosen conjugation
    private var theDashes = ""       // creates baseline of dashes

    // persoonlijke voornaamwoorden
    private val pronouns = listOf("εγώ", "εσύ", "αυτός,-ή,-ό", "εμείς", "εσείς", "αυτοί,-ές,-ά")

    // short description of the possible person & number combinations
    private val shortPerson = listOf("1ste pers. enkelv.", "2de pers. enkelv.", "3de pers. enkelv.", "1ste pers. meerv.", "2de pers. meerv.", "3de pers. meerv.")
    private var  pickOne = 0
    // actually picked person & number (randomly)
    private var theShort = ""

    private val selectedPersons = mutableSetOf<Int>()  // holds preselected persons
    private var savedLevelBasic = true
    private var savedLevelAdvanced = true
    private var savedLevelBallast = true
    private var seconds = 0
    private var scores = mutableListOf<Int>()
    private var fastest = 3000  // keeps track of fastest score

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = VerbGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        /* setup action bar on top */
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.title_activity)
            subtitle = getString(R.string.subtitle_verbgame)
        }

        /* get a reference to the database */
        db = zorbaDBHelper.readableDatabase

        with(binding) {
            /* setup chronometer/stopwatch */
            chronometer.base = SystemClock.elapsedRealtime()

            /* retrieve 'high score' (fastest time) from shared preferences */
            fastest = zorbaPreferences.getInt("fastest", 3000)  // 5 min is the default value

            /* initialise all onClick listeners here */
            btnGo.setOnTouchListener { v: View, m: MotionEvent ->
                touche(v, m)
                true
            }

            swMode.setOnClickListener { switchGameMode() }

            lblHerken.setOnClickListener {
                swMode.isChecked = !swMode.isChecked
                switchGameMode()
            }
            swLevelBasis.setOnClickListener { onLevelChange() }
            swLevelGevorderd.setOnClickListener { onLevelChange() }
            swLevelBallast.setOnClickListener { onLevelChange() }
            chkType1.setOnClickListener { onEndingChange() }
            chkType2.setOnClickListener { onEndingChange() }
            chkType3.setOnClickListener { onEndingChange() }
            btnFout.setOnClickListener { startOver() }
            btnPrevious.setOnClickListener { backwardToPreviousMatch() }
//            btnVerbal.setOnClickListener { cleanSpeech("$thePersonGR  $theConjugation", "anders") }
            btnVerbal.setOnClickListener { timesPerPerson() }
            btnShowLadder.setOnClickListener {
                txtReveal.visibility = if (binding.txtConjugations.visibility == View.VISIBLE) View.VISIBLE else View.GONE
                txtConjugations.toggleVisibility()
                txtPersons.toggleVisibility()
            }

            /* save original level values and set initial values for the game */
            savedLevelBasic = levelBasic
            savedLevelAdvanced = levelAdvanced
            savedLevelBallast = levelBallast

            /* for the game start with basic level only */
            swLevelBasis.isChecked = true
            swLevelGevorderd.isChecked = true
            swLevelBallast.isChecked = true

            levelBasic = true
            levelAdvanced = true
            levelBallast = true

            /* init selected person list based on default switch settings */
            if (chk1.isChecked) selectedPersons.add(0)
            if (chk2.isChecked) selectedPersons.add(1)
            if (chk3.isChecked) selectedPersons.add(2)
            if (chk4.isChecked) selectedPersons.add(3)
            if (chk5.isChecked) selectedPersons.add(4)
            if (chk6.isChecked) selectedPersons.add(5)
        }

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
        with (binding) {
            if (swMode.isChecked) {
                swMode.setTextColor(ContextCompat.getColor(applicationContext, R.color.normal_text_color))
                swMode.setTypeface(null, Typeface.NORMAL)
                lblHerken.setTextColor(ContextCompat.getColor(applicationContext, R.color.colorAccent))
                lblHerken.setTypeface(null, Typeface.BOLD)
            } else {
                swMode.setTextColor(ContextCompat.getColor(applicationContext, R.color.colorAccent))
                swMode.setTypeface(null, Typeface.BOLD)
                lblHerken.setTextColor(ContextCompat.getColor(applicationContext, R.color.normal_text_color))
                lblHerken.setTypeface(null, Typeface.NORMAL)
            }
        }
        showByMode()
    }

    /* move forward in cursor until a verb is found that contains selected conjugation (ex: Not all have Paratatikos!) */
    private fun forwardUntilMatch() {
        binding.txtConjugations.visibility = View.INVISIBLE
        binding.txtPersons.visibility = View.INVISIBLE
        binding.textAsk.setTextColor(ContextCompat.getColor(applicationContext, R.color.normal_text_color))

        /* this loops until a matching record is found */
        while (gameCursor.moveToNext()) {
            if (setupVerb()) break
        }

        if (gameCursor.isAfterLast) colorToast(context = applicationContext, msg = "Thats all", bgColor = Color.BLUE) else showByMode()
    }

    /* move backwards in cursor until a verb is found that contains selected conjugation */
    private fun backwardToPreviousMatch() {
        binding.txtConjugations.visibility = View.INVISIBLE
        binding.txtPersons.visibility = View.INVISIBLE
        binding.textAsk.setTextColor(ContextCompat.getColor(applicationContext, R.color.normal_text_color))
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

        if (binding.chkEnestotas.isChecked) availableTenses.add("Nu")
        if (hasMellontas(theGreek) && binding.chkMellontas.isChecked) availableTenses.add("Toekomst")
        if (hasAorist(theGreek) && binding.chkAoristos.isChecked) availableTenses.add("Verleden")
        if (hasParatatikos(theGreek) && binding.chkParatatikos.isChecked) availableTenses.add("Παρατατικός")
        if (hasMellontas(theGreek) && binding.chkProstaktiki.isChecked) availableTenses.add("Gebiedend")  // need mellontas to build gebiedende wijs

        if (availableTenses.size > 0) {

            /* pick a random tense in the set of available tenses */
            theTense = availableTenses.shuffled().first()

            val conjugations = when (theTense) {
                "Nu" -> conjugateEnestotas(theGreek)
                "Toekomst" -> conjugateMellontas(theGreek)
                "Verleden" -> conjugateAoristos(theGreek)
                "Παρατατικός" -> conjugateParatatikos(theGreek)
                "Gebiedend" -> createProstaktiki(theGreek)
                else -> listOf("Werkwoordvorm onbekend")
            }

            if (selectedPersons.isNotEmpty()) {
                when (conjugations.size) {
                    6 -> {
                        binding.txtPersons.text = pronouns.joinToString("\n","\n")
                        binding.txtConjugations.text =  conjugations.joinToString("\n","$theTense:\n")
                        /* pick one of the choosen conjugations */
                        pickOne = selectedPersons.shuffled().last()
                        theConjugation = conjugations[pickOne]
                        val persoonsvormGR = listOf("Εγώ", "Εσύ", setOf("Αυτός", "Αυτή", "Αυτό").random(), "Εμείς", "Εσείς", setOf("Αυτοί", "Αυτές", "Αυτά").random())
                        thePersonGR = persoonsvormGR[pickOne]
                        theShort = shortPerson[pickOne]
                    }
                    2 -> {
                        binding.txtPersons.text = getString(R.string.single_plural)
                        binding.txtConjugations.text = conjugations.joinToString("\n","$theTense\n")
                        /* gebiedende wijs */
                        theConjugation = conjugations[0] + " - " + conjugations[1]
                        thePersonGR = ""
                    }
                    else -> return false // do not show if incomplete
                }
                theDashes = "⎽ ".repeat(theConjugation.length)
                binding.chronometer.base = SystemClock.elapsedRealtime()
                binding.chronometer.start()

                return true // valid conjurgation found

            } else return false // no available persons
        } else return false     // no available Tenses
    }

    /* show verb conjugations */
    private fun timesPerPerson() {
        val e = conjugateEnestotas(theGreek)[pickOne]
        val m = conjugateMellontas(theGreek)[pickOne]
        val a = conjugateAoristos(theGreek)[pickOne]
        val p = conjugateParatatikos(theGreek)[pickOne]

        val ladder = "$thePersonGR $e\n$thePersonGR $m\n$thePersonGR $a\n$thePersonGR $p"

        val titleSpan = SpannableString(theVerb)
        val titleLength = theVerb.length
        val greekTextColor = ContextCompat.getColor(applicationContext, R.color.κυανός)
        val thisColorSpan = ForegroundColorSpan(greekTextColor)
        val italicSpan = StyleSpan(Typeface.ITALIC)
        val verbSizeSpan = RelativeSizeSpan(1.3f)

        titleSpan.setSpan(thisColorSpan, 0, titleLength, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        titleSpan.setSpan(verbSizeSpan, 0, titleLength, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        titleSpan.setSpan(italicSpan, 0, titleLength, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

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

        /* change properties of the alert dialog internal views */
        (alertDialog.findViewById(android.R.id.message) as TextView).textSize = 20f

        with(alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL)) {
            textSize = 28f
            setOnClickListener { cleanSpeech("$thePersonGR $e. $thePersonGR $m. $thePersonGR $a. $thePersonGR $p", "standaard") }
            enable(useSpeech)
        }
        alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).textSize = 28f
    }

    private fun showByMode() {
        if (!binding.swMode.isChecked) {
            binding.textAsk.text = getString(R.string.spaced_triple, thePersonGR, theDashes, theVerb)
            binding.txtReveal.text = theTense//.padStart(10)
            binding.txtReveal.visibility = View.VISIBLE // initially part of the question
        } else {
            binding.textAsk.text = theConjugation
            binding.txtReveal.text = getString(R.string.reveal,theTense , theVerb ,theMeaning)
            binding.txtReveal.visibility = View.INVISIBLE
        }
    }

    private fun reveal() {
        with (binding) {
            txtConjugations.visibility = View.INVISIBLE
            txtPersons.visibility = View.INVISIBLE
            txtReveal.visibility = View.VISIBLE
            textAsk.setTextColor(ContextCompat.getColor(applicationContext, R.color.blue_text_color))
            textAsk.text = getString(R.string.spaced_pair, thePersonGR, theConjugation)

            val textReveal = SpannableString("$theTense, $theShort van $theVerb ($theMeaning).")
            val spanStart = "$theTense, $theShort van ".length
            val spanEnd = spanStart + theVerb.length

            textReveal.setSpan(StyleSpan(Typeface.BOLD_ITALIC), spanStart, spanEnd, 0)
            textReveal.setSpan(ForegroundColorSpan(ContextCompat.getColor(applicationContext, R.color.blue_text_color)), spanStart, spanEnd, 0)
            txtReveal.text = textReveal

            chronometer.stop()
            seconds = chronometer.text.take(2).toString().toInt() * 60 + chronometer.text.takeLast(2).toString().toInt()
            scores.add(seconds)

            lblTotal.text = getString(R.string.total_value, secondsToShortTime(scores.sum()))
            lblCount.text = getString(R.string.count_value, scores.size)
        }
        if (useSpeech) cleanSpeech("$thePersonGR  $theConjugation", "anders")
    }

    /* finish intent and return to main activity */
    override fun finish() {
        gameCursor.close()
        db.close()
        super.finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    /* Actions needed when touching and releasing buttons of text field, emulating key-down / key-up events
    * Checks if release is inside or outside original view boundaries */
    private fun touche(textfield: View, motion: MotionEvent) {
        when (motion.actionMasked) {
            MotionEvent.ACTION_DOWN -> reveal()
            MotionEvent.ACTION_UP -> {
                /* check if release is  within the source view */
                val boundaries = Rect(0, 0, textfield.width, textfield.height)
                if (boundaries.contains(motion.x.toInt(), motion.y.toInt())) {
                    if (scores.size > blockSize - 1) {
                        buildScoreResult()
                    } else {
                        forwardUntilMatch()
                    }
                }
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onPersonClick(view: View) {
        val thisCheck = view as CheckBox
        if (!(binding.chk1.isChecked || binding.chk2.isChecked || binding.chk3.isChecked || binding.chk4.isChecked || binding.chk5.isChecked || binding.chk6.isChecked)) {
            colorToast(context = this, msg = "Need at least one person selected.", bgColor = Color.RED)
            thisCheck.isChecked = true
        } else {
            val tagInt = thisCheck.tag.toString().toInt()
            if (thisCheck.isChecked) selectedPersons.add(tagInt) else selectedPersons.remove(tagInt)
        }
        forwardUntilMatch()
    }

    @Suppress("UNUSED_PARAMETER")
    fun onTenseClick(view: View) {
        val thisCheck = view as CheckBox
        if (!(binding.chkEnestotas.isChecked || binding.chkMellontas.isChecked || binding.chkAoristos.isChecked || binding.chkParatatikos.isChecked || binding.chkProstaktiki.isChecked)) {
            colorToast(context = this, msg = "Need at least one tense selected.", bgColor = Color.RED)
            thisCheck.isChecked = true
        }
        forwardUntilMatch()
    }

    private fun onLevelChange() {
        /* if no level is selected, select all */
        if (!(binding.swLevelBasis.isChecked || binding.swLevelGevorderd.isChecked || binding.swLevelBallast.isChecked)) {
            binding.swLevelBasis.isChecked = true
            binding.swLevelGevorderd.isChecked = true
            binding.swLevelBallast.isChecked = true
        }
        /* forward to query manager */
        levelBasic = binding.swLevelBasis.isChecked
        levelAdvanced = binding.swLevelGevorderd.isChecked
        levelBallast = binding.swLevelBallast.isChecked
        gameCursor = db.rawQuery(QueryManager.verbGameQuery(), null)
        forwardUntilMatch()
    }

    private fun onEndingChange() {
        /* if no ending is selected, select all */
        if (!(binding.chkType1.isChecked || binding.chkType2.isChecked || binding.chkType3.isChecked)) {
            binding.chkType1.isChecked = true
            binding.chkType2.isChecked = true
            binding.chkType3.isChecked = true
        }
        /* forward to query manager */
        gameCursor = db.rawQuery(QueryManager.verbGameQuery(binding.chkType1.isChecked, binding.chkType2.isChecked, binding.chkType3.isChecked), null)
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
                binding.btnVerbal.enable(useSpeech)
            }
            R.id.menu_mail_lemma -> mailLemma()

            R.id.menu_reset_hiscore -> {
                /*  reset fastest time */
                zorbaPreferences.edit()
                    .putInt("fastest", 3000)
                    .apply()
                fastest = 3000
            }

            R.id.menu_raw_view -> showBaseRecord()

            R.id.menu_wordreference -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.wordreference.com/gren/$theVerb"))
                startActivity(intent)
            }
            R.id.menu_neurolingo -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://www.neurolingo.gr/en/online_tools/lexiscope.htm?term=$theVerb"))
                startActivity(intent)
            }
            /* home button (left arrow in app bar) pressed */
            android.R.id.home -> finish()

            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun mailLemma() {
        val subject = "$theIdx: $theVerb"
        val body = "Idx: $theIdx $theVerb\n\n" +
              "Ask:\n$thePersonGR $theConjugation\n\n" +
              "Reveal:\n$theTense van $theVerb ($theMeaning)."

        val emailIntent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", getString(R.string.app_contact_email), null))
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, body)
        startActivity(Intent.createChooser(emailIntent, "Send lemma by email..."))
    }

    private fun secondsToShortTime(seconds: Int): String {
        return DateUtils.formatElapsedTime(seconds.toLong())
    }

    private fun secondsToMinSec(seconds: Int): String {
        val wholeMins = (seconds / 60)
        val restSeconds = seconds - (wholeMins * 60)
        return "$wholeMins minuut $restSeconds sec."
    }

    /* show the score of the answers after X correct */
    private fun buildScoreResult() {
        val aantal = scores.size
        val totaal = scores.sum()
        val min = secondsToShortTime(scores.minOrNull()!!)
        val max = secondsToShortTime(scores.maxOrNull()!!)
        val gemiddeld = secondsToShortTime(scores.average().toInt())
        var bravo = ""
        if (totaal < fastest) {
            // maintain 'high score' (fastest) in shared preferences
            zorbaPreferences.edit()
                .putInt("fastest", totaal)
                .apply()
            fastest = totaal
            bravo = "Bravo! Dit is de snelste tijd.\n\n"
        }
        val textToDisplay = "$bravo${secondsToMinSec(totaal)}\n    voor $aantal antwoorden." +
              "\nGemiddeld: $gemiddeld" +
              "\nSnelste:   $min" +
              "\nTraagste:  $max"

        val bob = AlertDialog.Builder(this)
            .setTitle("Goed gedaan.")
            .setMessage(textToDisplay)
            .setPositiveButton(R.string.btn_caption_ok) { _, _ -> startOver() }

        /* create the dialog from the builder */
        val alertDialog = bob.create()
        alertDialog.show()
        alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).textSize = 28f
        scores.clear()
        binding.lblTotal.text = ""
        binding.lblCount.text = ""
    }

    private fun startOver() {
        scores.clear()
        binding.lblTotal.text = ""
        binding.lblCount.text = ""
        forwardUntilMatch()
    }

    /* build the Raw Record View invoked through the menu */
    private fun showBaseRecord() {
        val webView = WebView(applicationContext)
        val record = "<html><head><style>" +
              "  table{border: 1px solid black;border-collapse: collapse; background-color: gold;}" +
              "  td{border: 1px solid black;vertical-align:top;}" +
              "</style></head>" +
              "<body><table>" +
              "  <tr><td>index:</td><td>$theIdx</td></tr>" +
              "  <tr><td>pure lemma:</td><td>$theVerb</td></tr>" +
              "  <tr><td>grieks:</td><td>" + theGreek.replace("\n", "<br>") + "</td></tr>" +
              "  <tr><td>nederlands:</td><td>" + theMeaning.replace("\n", "<br>") + "</td></tr>" +
              "</table></body></html>"
        webView.loadData(record, "text/html", "UTF-8")
        val bob = AlertDialog.Builder(this)
        bob.setTitle("Base Record")
        bob.setView(webView)
        bob.setPositiveButton(R.string.btn_caption_ok, null)

        val alertDialog = bob.create()
        alertDialog.show()
        alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).textSize = 28f
    }

}
