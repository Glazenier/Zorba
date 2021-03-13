package driemondglas.nl.zorba

import android.app.AlertDialog
import android.content.*
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.text.*
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import driemondglas.nl.zorba.QueryManager.selectedCount
import driemondglas.nl.zorba.QueryManager.verbGameCount
import driemondglas.nl.zorba.databinding.ActivityMainBinding
import java.util.*


/* Top-level Properties: */
const val DATABASE_URI = "https://driemondglas.nl/RESTgrieks_v4.php"
const val TAG = "hvr"
const val THEME_WORDTYPE = 3208
const val DETAILS = 3308
const val FLASHCARDS = 3408
//const val LISTEN = 3508

/* 'global' variables holding configuration data.
 *  Initial values are retrieved from shared preferences, including the default values */
var blockSize = 0         // lemma's can be grouped into a blocks to focus studying, nr of lemma's per block
var useBlocks = true      // turn off usage of blocks entirely. Database is then in fact one giant block
var thema = ""            // currently selected theme (lemmas with same subject)
var wordType = ""         // currently selected word type (noun, verb, article, etc.)
var levelBasic = true     // filter lemma's  by (difficulty/usage) level
var levelAdvanced = true  // ...
var levelBallast = true   // ...
var useLength = false     // filter lemma's on length on/off
var pureLemmaLength = 0   // set lemma length to filter
var initial = ""          // set filter for lemma's to start with this character
var orderbyTag = ""       // initial lemma order is by index in table
var orderDescending = true// highest index on top (newest first)
var search = ""           // search text can be lemma (Greek) or meaning (Dutch)
var flashed = false       // flag to signal to show only flashed lemma's
var speechRate = 1f       // speech speed

/* Jumpers:
 * Jumpers are lemmas having a count of correct answers above the set threshold. "They jumped the threshold"
 * The idea is to hide those lemmas in further selections to focus on remaining lemmas */
var jumpThreshold = 2
var hideJumpers = false  // do not move jumpers completely out of sight

/*  Initialise the DATABASE HELPER class. */
lateinit var zorbaDBHelper: ZorbaDBHelper

/* text to SPEECH OBJECT used throughout the application*/
lateinit var zorbaSpeaks: TextToSpeech

/* SPEECH on or off */
var useSpeech = true

/* SHARED PREFERENCES to keep configuration as well as certain progress ans score values */
lateinit var zorbaPreferences: SharedPreferences


/* MAIN ACTIVITY CLASS implements TextToSpeech.OnInitListener */
class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    // view binding replaces synthetic binding
    private lateinit var binding: ActivityMainBinding
    
    // initialise the list containing all the Lemma data items that attaches to the recycler through the adapter
    private val lemmaArrayList: ArrayList<LemmaItem> = ArrayList()

    // attach to view
    private val recyclerViewAdapter = LemmaRecyclerAdapter(lemmaArrayList)

    private val onItemClickListener = View.OnClickListener { view ->
        //  The viewholder is attached as tag to the view
        //  This viewHolder will contain the values needed to create the item.
        val viewHolder = view.tag as RecyclerView.ViewHolder

        // retrieve position (index) of the clicked item
        val position = viewHolder.adapterPosition

        // display the item in a single flashcard
        val myIntent = Intent(this, FlashCard::class.java)
        myIntent.putExtra("idx", lemmaArrayList[position].idx)
        myIntent.putExtra("singlecard", true)
        startActivity(myIntent)
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // FIRST launch the Splash Activity , note: done before  inflation of the main activity layout
        val splashIntent = Intent(this, Splash::class.java)
        startActivity(splashIntent)

        binding = ActivityMainBinding.inflate(layoutInflater)
        //  inflate the layout file for this activity
        setContentView(binding.root)

        zorbaDBHelper = ZorbaDBHelper(applicationContext)

        zorbaPreferences = applicationContext.getSharedPreferences("zorbaPrefKey", Context.MODE_PRIVATE)

        // retrieve config from shared preferences
        with(zorbaPreferences) {
            useBlocks = getBoolean("useblocks", true)
            blockSize = getInt("blocksize", 5)
            thema = getString("theme", "") ?: ""
            wordType = getString("wordtype", "") ?: ""
            levelBasic = getBoolean("levelbasic", true)
            levelAdvanced = getBoolean("leveladvanced", true)
            levelBallast = getBoolean("levelballast", true)
            useLength = getBoolean("uselength", false)
            pureLemmaLength = getInt("purelemmalenght", 6)
            initial = getString("initial", "") ?: ""
            orderbyTag = getString("orderbytag", "index") ?: "index"
            orderDescending = getBoolean("orderdescending", true)
            jumpThreshold = getInt("jumpthreshold", 2)
            hideJumpers = getBoolean("hidejumpers", false)
            flashed = getBoolean("flashed", false)
            speechRate = getFloat("speechrate", 1.0f)
            search = getString("search", "") ?: ""
        }

        // create action bar on top
        supportActionBar?.apply {
            setDisplayShowHomeEnabled(true)
            title = SpannableString(getString(R.string.title_main)).apply {
                setSpan(StyleSpan(Typeface.BOLD_ITALIC), 0, 4, 0)
                setSpan(StyleSpan(Typeface.NORMAL), 5, 15, 0)
                setSpan(RelativeSizeSpan(0.75f), 5, 15, 0)
            }
            setIcon(R.drawable.greneth)
        }

        // Init reference to tts
        zorbaSpeaks = TextToSpeech(applicationContext, this)

        // set listener for changes in search field
        binding.textSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(searchWhat: Editable) = onSearch(searchWhat)
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        })

        binding.textSearch.setText(search)

        // create jumpers table if it does not exist yet
        zorbaDBHelper.assessJumperTable()

        // create local flashed table if it does not exist yet
        zorbaDBHelper.assessFlashTable()

        // initialise the recyclerView showing the lemma's on the front page (the main activity)
        val recyclerView = findViewById<RecyclerView>(R.id.recycler_view)

        // fill her up with lemma's
        refreshData()

        // declare the adapter that attaches the data to the view
        // this also initialises the click listener
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = recyclerViewAdapter

        // create and set OnItemClickListener to the adapter, that in turn sets it to the viewholder
        recyclerViewAdapter.setOnItemClickListener(onItemClickListener)

        // attach listeners to the front page buttons:

        /*** to launch FLASHCARDS activity ***/
        binding.btnOpenDeck.setOnClickListener {

            if (selectedCount() > 0 ) {
                val myIntent = Intent(this, FlashCard::class.java)
                myIntent.putExtra("singlecard", false)
                startActivityForResult(myIntent, FLASHCARDS)
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            } else {
                colorToast(applicationContext, getString(R.string.warning_empty_cursor))
            }
        }

        /*** to launch LISTENING excercise ***/
        binding.btnLuister.setOnClickListener {
            // launch the luisterlijst activity only if selection contains lemma's
            if (selectedCount() > 0 ) {
                val myIntent = Intent(this, Luisterlijst::class.java)
                startActivity(myIntent)
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            } else {
                colorToast(applicationContext, getString(R.string.warning_empty_cursor))
            }
        }

        /*** to launch the VERB game activity ***/
        binding.btnVerbGame.setOnClickListener {
            /* launch the verb game activity if selection contains any verbs */
            if (verbGameCount() > 0 ) {
                val myIntent = Intent(this, VerbGame::class.java)
                startActivity(myIntent)
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            } else {
                colorToast(applicationContext, getString(R.string.warning_empty_cursor))
            }
        }

        /*** launch the HANGMAN game ***/
        binding.btnHangman.setOnClickListener {
            val myIntent = Intent(this, Hangman::class.java)
            startActivity(myIntent)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }

        //  pressing the Greek flag toggles the Greek text on/off
        binding.btnFlagGreek.setOnClickListener {
            if (recyclerViewAdapter.showDutch) recyclerViewAdapter.showGreek = !recyclerViewAdapter.showGreek
            recyclerViewAdapter.notifyDataSetChanged()
        }

        //  pressing the Dutch flag toggles the Dutch text on/off
        binding.flagNlBtn.setOnClickListener {
            if (recyclerViewAdapter.showGreek) recyclerViewAdapter.showDutch = !recyclerViewAdapter.showDutch
            recyclerViewAdapter.notifyDataSetChanged()
        }

        binding.btnClearSearch.setOnClickListener {
            binding.textSearch.setText("")
            val inputManager: InputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
            inputManager.hideSoftInputFromWindow(currentFocus?.windowToken, InputMethodManager.SHOW_FORCED)
        }
    }

    /* overriding onInit() is needed for the TextToSpeech.OnInitListener */
    override fun onInit(status: Int) {
        // set Greek as language for text to speech object
        if (status == TextToSpeech.SUCCESS) {
            zorbaSpeaks.language = Locale("el_GR")
            zorbaSpeaks.setSpeechRate(speechRate)
        } else {
            Log.d(TAG, getString(R.string.warning_speech_init))
            colorToast(context = this, msg = getString(R.string.warning_speech_init), fgColor = Color.RED)
        }
    }

    override fun onResume() {
        super.onResume()
        // runs when focus returns from config changes or other activity
        zorbaSpeaks.language = Locale("el_GR")
    }

    override fun onDestroy() {
        // Shutdown TTS
        zorbaSpeaks.stop()
        zorbaSpeaks.shutdown()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        /*  Inflate the menu from the layout file
         *  this adds menu items to the action bar if it is present. */
        menuInflater.inflate(R.menu.menu_main, menu)
        menu.findItem(R.id.menu_speech).isChecked = useSpeech
        return true
    }

    /* Handle action bar (menu) item clicks here. */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when (item.itemId) {
            /* menu set theme/wordtype */
            R.id.menu_set_theme_wordtype -> {
                /* launch the Thema/Woordsoort Selection Activity */
                val myIntent = Intent(this, ThemeAndWordType::class.java)
                startActivityForResult(myIntent, THEME_WORDTYPE)
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            }

            /*  menu detail selecties */
            R.id.menu_set_details_sort -> {
                /* launch the Selecties Activity */
                val myIntent = Intent(this, FilterAndSort::class.java)
                startActivityForResult(myIntent, DETAILS)
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            }

            /* menu wis alle selecties */
            R.id.menu_clear_selects -> {
                clearAllSelections()
                binding.textSearch.setText("")
                refreshData()
                recyclerViewAdapter.notifyDataSetChanged()
            }

            /* importeer de database (opnieuw?) */
            R.id.action_import -> importTable()

            /* speech on/off */
            R.id.menu_speech -> {
                item.isChecked = !item.isChecked
                useSpeech = item.isChecked
                recyclerViewAdapter.notifyDataSetChanged()
            }

            /*  about applicattion */
            R.id.menu_about -> about()

            /* perform any other actions triggered from parent or beyond */
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, myIntent: Intent?) {
        super.onActivityResult(requestCode, resultCode, myIntent)
        /* when configuration changing activities are finished, they return here.
         * if result is a possible selection change, requery the data */
        if (resultCode== RESULT_OK) {
            if (requestCode in setOf(THEME_WORDTYPE, DETAILS, FLASHCARDS)) {
                if (myIntent?.getStringExtra("result") == "changed") {
                    refreshData()
                    recyclerViewAdapter.notifyDataSetChanged()
                }
            }
        }
    }

    /* method clears the existing array list and refills it with fresh data from the local database */
    private fun refreshData() {
        // remove existing content
        lemmaArrayList.clear()

        val db = zorbaDBHelper.readableDatabase

        db.rawQuery(QueryManager.mainQuery(), null).apply {
            val col0 = getColumnIndex("PureLemma")
            val col1 = getColumnIndex("GR")
            val col2 = getColumnIndex("NL")
            val col3 = getColumnIndex("Woordsoort")
            val col4 = getColumnIndex("idx")
            while (moveToNext()) {
                val lemmaItem = LemmaItem(
                    getString(col0),
                    getString(col1),
                    getString(col2),
                    getString(col3),
                    getLong(col4))
                lemmaArrayList.add(lemmaItem)
            }
            close()
        }
        db.close()
    }

    /* Implement the 'about...' method invoked through the main menu */
    private fun about() {
        val webView = WebView(applicationContext)
        val about = """
    <html>
        <head>
            <style>
                table{border: 1px solid black;border-collapse: collapse; background-color: gold;}
                td{border: 1px solid black;vertical-align:top;}
            </style>
        </head>
        <body>
            <table>
                <tr><td>&nbsp;</td><td>${getString(R.string.app_description)}</td></tr>
                <tr><td>Author:</td><td>${getString(R.string.app_author)}</td></tr>

                <tr><td>Change:</td><td>${getString(R.string.app_versioninfo)}</td></tr>
                <tr><td>Lemmas:</td><td>${zorbaDBHelper.lemmaCount()}</td></tr>
                <tr><td>Selected:</td><td>${selectedCount()}</td></tr>
                <tr><td>Flashed:</td><td>${zorbaDBHelper.flashCount()}</td></tr>
                <tr><td>Jumpers:</td><td>${zorbaDBHelper.countJumpers()}</td></tr>
            </table>
        </body>
    </html>"""
        webView.loadData(about, "text/html", "UTF-8")

        val bob = AlertDialog.Builder(this)
            .setTitle(getString(R.string.alert_about_title))
            .setView(webView)
            .setPositiveButton(R.string.btn_caption_ok, null)

        /* create the dialog from the builder */
        val alertDialog = bob.create()
        alertDialog.show()
        alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).textSize = 28f
    }

    /* create attention before importing new woorden table */
    private fun importTable() {
        val bob = AlertDialog.Builder(this)
            .setTitle(getString(R.string.msg_import_title))
            .setMessage(getString(R.string.msg_import_db))
            .setPositiveButton(R.string.btn_caption_ok) { _, _ -> importJson() }
            .setNegativeButton(R.string.btn_caption_notok, null)
        val alert = bob.show()
        alert.show()
        alert.getButton(DialogInterface.BUTTON_POSITIVE).textSize = 28f
        alert.getButton(DialogInterface.BUTTON_NEGATIVE).textSize = 28f
    }

    /* import function moved here in stead of DbHelper class to address the progressBar in this context.
     * also, all synchronous code must be in the callback lambda */
    private fun importJson() {

        binding.progressBar.visibility = View.VISIBLE

        /* initialise the Volley Request Queue */
        val queue = Volley.newRequestQueue(this)

        /* Request a string response from the URL */
        val stringRequest = StringRequest( Request.Method.GET, DATABASE_URI,
            { response ->
                binding.progressBar.visibility = View.INVISIBLE
                zorbaDBHelper.jsonToSqlite(response)
                refreshData()
                recyclerViewAdapter.notifyDataSetChanged()
            },
            { error -> Log.d(TAG, getString(R.string.error_generic, error)) })

        /* Add the requests to the RequestQueue. */
        queue.add(stringRequest)
    }

    private fun onSearch(searchText: CharSequence) {
        // set global variable
        search = searchText.toString()

        // save in preferences so we can restart from where we left off
        zorbaPreferences.edit().putString("search", search).apply()

        // show in recycler list
        refreshData()
        recyclerViewAdapter.notifyDataSetChanged()
    }
}

/* data class minimal definition: holds the lemma item data GR and NL */
data class LemmaItem(
    val pureLemma: String,
    val textGreek: String,
    val meaningNL: String,
    val woordsoort: String,
    val idx: Long
)