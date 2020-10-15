package driemondglas.nl.zorba

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.database.DatabaseUtils
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*


/* Top-level Properties:
 * below are the self chosen 'unique' request codes used to start the various activities
 */
const val SHOW_CARDS_CODE = 3108
const val GROEP_SOORT_CODE = 3208
const val SELECTIES_CODE = 3308

const val DATABASE_URI = "https://driemondglas.nl/RESTgrieks_v3.php"
const val TAG = "hvr"

/* 'global' variables holding configuration data */
var blockSize = 20        // number of lemma's to speak in one turn.
var useBlocks = true      // turn off usage of blocks entirely. Database is then in fact one giant block
var wordGroup = ""
var wordType = ""
var levelBasic = true
var levelAdvanced = true
var levelBallast = true
var useLength = false
var pureLemmaLength = 0
var initial = ""
var orderbyTag = "index"
var orderDescending = true
var search = ""

/* Jumpers:
 * Jumpers are lemmas with number of correct answers above the set threshold. "They jumped the threshold"
 * The idea is to hide those lemmas in further selections to focus on remaining lemmas
 */
var jumpThreshold = 2
var hideJumpers = false

/*  Initialise the database helper class. */
lateinit var zorbaDBHelper: ZorbaDBHelper

/* text to speech object used throughout the application*/
lateinit var zorbaSpeaks: TextToSpeech

/* speech on or off */
var useSpeech = true

/* shared preferences to keep configuration as well as certain progress ans score values */
lateinit var zorbaPreferences: SharedPreferences

/* main activity class implements TextToSpeech.OnInitListener */
class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    /* list containing all the Lemma data items that attaches to the recycler through the adapter */
    private val lemmaArrayList: ArrayList<LemmaItem> = ArrayList()

    /* attach to view */
    private val recyclerViewAdapter = LemmaRecyclerAdapter(lemmaArrayList)

    private val onItemClickListener = View.OnClickListener { view ->
        /*  The viewholder is attached as tag to the view
         *  This viewHolder will contain the values needed for the item. */
        val viewHolder = view.tag as RecyclerView.ViewHolder

        /* retrieve position (index) of the clicked item */
        val position = viewHolder.adapterPosition

        /* lookup the item in the ArrayList */
        val thisLemmaItem: LemmaItem = lemmaArrayList[position]

        val thisIdx = thisLemmaItem.idx

        val myIntent = Intent(this, FlashCard::class.java)
        myIntent.putExtra("idx", thisIdx)
        startActivity(myIntent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /* FIRST launch the Splash Activity , note: done before  inflation of the main activity layout*/
        val splashIntent = Intent(this, Splash::class.java)
        startActivity(splashIntent)

        /*  inflate the layout file for this activity */
        setContentView(R.layout.activity_main)

        zorbaDBHelper = ZorbaDBHelper(applicationContext)

        zorbaPreferences = applicationContext.getSharedPreferences("zorbaPrefKey", Context.MODE_PRIVATE)

        /* retrieve config from shared preferences  */
        useBlocks = zorbaPreferences.getBoolean("useblocks", true)
        blockSize = zorbaPreferences.getInt("blocksize", 5)
        wordGroup = zorbaPreferences.getString("wordgroup", "") ?: ""
        wordType = zorbaPreferences.getString("wordtype", "") ?: ""
        levelBasic = zorbaPreferences.getBoolean("levelbasic", true)
        levelAdvanced = zorbaPreferences.getBoolean("leveladvanced", true)
        levelBallast = zorbaPreferences.getBoolean("levelballast", true)
        useLength = zorbaPreferences.getBoolean("uselength", true)
        pureLemmaLength = zorbaPreferences.getInt("purelemmalenght", 6)
        initial = zorbaPreferences.getString("initial", "") ?: ""
        orderbyTag = zorbaPreferences.getString("orderbytag", "index") ?: "index"
        orderDescending = zorbaPreferences.getBoolean("orderdecending", true)
        jumpThreshold = zorbaPreferences.getInt("jumpthreshold", 2)
        hideJumpers = zorbaPreferences.getBoolean("hidejumpers", false)


        val zTitle = SpannableString("ZORBA by Herman")
        with(zTitle) {
            setSpan(StyleSpan(Typeface.BOLD_ITALIC), 0, 4, 0)
            setSpan(StyleSpan(Typeface.NORMAL), 5, 15, 0)
            setSpan(RelativeSizeSpan(0.75f), 5, 15, 0)
        }

        /* create action bar on top */
        with(supportActionBar!!) {
            setDisplayShowHomeEnabled(true)
            title = zTitle
            setIcon(R.drawable.greneth)
        }

        /* Init reference to tts */
        zorbaSpeaks = TextToSpeech(applicationContext, this)

        /* set listener for changes in search field   */
        text_search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                onSearch(s)
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        })

        /* create jumpers table if it does not exist yet */
        zorbaDBHelper.assessJumperTable()

        /* initialise the recyclerView for the lemma's on the front page */
        val recyclerView = findViewById<RecyclerView>(R.id.recycler_view)

        /* fill up with lemma's */
        refreshData()

        /* declare the adapter that attaches the data to the view
         * this also initialises the click listener */
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = recyclerViewAdapter

        /* create and set OnItemClickListener to the adapter, that in turn sets it to the viewholder */
        recyclerViewAdapter.setOnItemClickListener(onItemClickListener)

        /* attach listeners to the front page buttons */
        btn_OpenDeck.setOnClickListener {
            /* launch the FlashCard Activity */
            val myIntent = Intent(this, FlashCard::class.java)
            startActivityForResult(myIntent, SHOW_CARDS_CODE)
        }

        /*  pressing the Greek flag toggles the Greek text on/off */
        img_flag_greek.setOnClickListener {
            if (recyclerViewAdapter.showDutch) recyclerViewAdapter.showGreek = !recyclerViewAdapter.showGreek
            recyclerViewAdapter.notifyDataSetChanged()
        }

        btn_clear.setOnClickListener {
            text_search.setText("")
            val inputManager: InputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
            inputManager.hideSoftInputFromWindow(currentFocus?.windowToken, InputMethodManager.SHOW_FORCED)
        }

        /*  pressing the Dutch flag toggles the Dutch text on/off */
        ibtn_flag_nl.setOnClickListener {
            if (recyclerViewAdapter.showGreek) recyclerViewAdapter.showDutch = !recyclerViewAdapter.showDutch
            recyclerViewAdapter.notifyDataSetChanged()
        }

        btn_hangman.setOnClickListener {
            /* launch the Hangman Activity */
            val myIntent = Intent(this, Hangman::class.java)
            startActivity(myIntent)
        }

        btn_verb_game.setOnClickListener {
            /* launch the Hangman Activity */
            val myIntent = Intent(this, VerbGame::class.java)
            startActivity(myIntent)
        }

        btn_luister.setOnClickListener {
            /* launch the Hangman Activity */
            val myIntent = Intent(this, Luisterlijst::class.java)
            startActivity(myIntent)
        }
    }

    /* overriding onInit() is needed for the TextToSpeech.OnInitListener */
    override fun onInit(status: Int) {
        /* set Greek as language for text to speech object */
        if (status == TextToSpeech.SUCCESS) {
            zorbaSpeaks.language = Locale("el_GR")
        } else {
            Log.d(TAG, "speech initilization problem!")
            colorToast(context = this, msg = "speech initilization problem!", fgColor = Color.RED)
        }
    }

    override fun onResume() {
        super.onResume()
        zorbaSpeaks.language = Locale("el_GR")
    }

    override fun onDestroy() {
        // Shutdown TTS
        zorbaSpeaks.stop()
        zorbaSpeaks.shutdown()

        zorbaPreferences.edit().putInt("last_viewed_block", 0).apply()

        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        /*  Inflate the menu from the layout file
         *  this adds menu items to the action bar if it is present.
         */
        menuInflater.inflate(R.menu.menu_main, menu)
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
                clearAll()
                refreshData()
                recyclerViewAdapter.notifyDataSetChanged()
            }

            /* importeer de database (opnieuw?) */
            R.id.action_import -> importTable()

            /* speech on/off */
            R.id.menu_speech -> {
                useSpeech = !item.isChecked
                item.isChecked = !item.isChecked
                recyclerViewAdapter.notifyDataSetChanged()
            }

            /*  about applicattion */
            R.id.menu_about -> about()

            /* perform any other actions triggered from parent or beyond */
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun refreshData() {
        /* method clears the existing array list and refills it with fresh data from the local database */
        val db = zorbaDBHelper.readableDatabase
        val myCursor = db.rawQuery(QueryManager.mainQuery(), null)

        /* remove existing content */
        lemmaArrayList.clear()

        /* step through the records (wordtypes) */
        while (myCursor.moveToNext()) {
            val lemmaItem = LemmaItem(
                myCursor.getString(myCursor.getColumnIndex("PureLemma")),
                myCursor.getString(myCursor.getColumnIndex("GR")),
                myCursor.getString(myCursor.getColumnIndex("NL")),
                myCursor.getString(myCursor.getColumnIndex("Woordsoort")),
                myCursor.getLong(myCursor.getColumnIndex("idx"))
            )
            lemmaArrayList.add(lemmaItem)
        }
        myCursor.close()
        db.close()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, myIntent: Intent?) {
        /*
         * This is the place where the activities/intents return after finishing
         * you can pick up the result from each intent by looking at the request code used */
        if (myIntent != null) {                      // to get rid of the Intent? null safety/* refresh data in the 'lemmaArrayList' using the changed selections */

            /* refresh data in the 'lemmaArrayList' using the changed selections */
            when (requestCode) {
                SHOW_CARDS_CODE -> {
                    refreshData()
                    recyclerViewAdapter.notifyDataSetChanged()
                }
                GROEP_SOORT_CODE -> {
                    if (myIntent.getStringExtra("result") == "selected") {
                        /* refresh data in the 'lemmaArrayList' using the changed selections */
                        refreshData()
                        recyclerViewAdapter.notifyDataSetChanged()
                    }
                }
                SELECTIES_CODE -> {
                    if (myIntent.getStringExtra("result") == "selected") {
                        /* refresh data in the 'lemmaArrayList' using the changed selections */
                        refreshData()
                        recyclerViewAdapter.notifyDataSetChanged()
                    }
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, myIntent)
    }

    /* Implement the 'about...' method invoked through the main menu */
    private fun about() {
        val webView = WebView(applicationContext)
        val about = "<html><head><style>" +
              "table{border: 1px solid black;border-collapse: collapse; background-color: gold;}" +
              "td{border: 1px solid black;vertical-align:top;}" +
              "</style></head>" +
              "<body>" +
              "<table>" +
              "<tr><td>&nbsp;</td><td>${getString(R.string.description)}</td></tr>" +
              "<tr><td>Author:</td><td>${getString(R.string.author)}</td></tr>" +
              "<tr><td>Version:</td><td>" + BuildConfig.VERSION_NAME + "</td></tr>" +
              "<tr><td>Build:</td><td>" + BuildConfig.VERSION_CODE + "</td></tr>" +
              "<tr><td>Change:</td><td>" + getString(R.string.version_info) + "</td></tr>" +
              "<tr><td>Lemmas:</td><td>" + lemmaCount() + "</td></tr>" +
              "<tr><td>Selected:</td><td>" + selectionCount() + "</td></tr>" +
              "<tr><td>Jumpers:</td><td>" + countJumpers() + "</td></tr>" +
              "</table></body></html>"
        webView.loadData(about, "text/html", "UTF-8")

        val bob = AlertDialog.Builder(this)
            .setTitle("About Zorba")
            .setView(webView)
            .setPositiveButton(R.string.emoji_ok, null)

        /* create the dialog from the builder */
        val alertDialog = bob.create()
        alertDialog.show()
        alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).textSize = 28f
    }

    /* count selected records */
    private fun lemmaCount() = DatabaseUtils.queryNumEntries(zorbaDBHelper.readableDatabase, "woorden")
    private fun selectionCount() = DatabaseUtils.queryNumEntries(zorbaDBHelper.readableDatabase, "woorden", QueryManager.selectionClauseOnly())
    private fun countJumpers() = DatabaseUtils.queryNumEntries(zorbaDBHelper.readableDatabase, "jumpers")

    /* create attention before importing new woorden table */
    private fun importTable() {
        val bob = AlertDialog.Builder(this)
            .setTitle("Import from Server")
            .setMessage("Do you want to re-import the database from the remote SQL server?")
            .setPositiveButton(R.string.emoji_ok) { _, _ -> importJson() }
            .setNegativeButton(R.string.emoji_not_ok, null)
        val alert = bob.show()
        alert.show()
        alert.getButton(DialogInterface.BUTTON_POSITIVE).textSize = 28f
        alert.getButton(DialogInterface.BUTTON_NEGATIVE).textSize = 28f
    }

    /* import function moved here in stead of DbHelper class to address the progressBar in this context.
     * also, all synchronous code must be in the callback lambda */
    private fun importJson() {

        progress_bar.visibility = View.VISIBLE

        /* initialise the Volley Request Queue */
        val queue = Volley.newRequestQueue(this)

        /* Request a string response from the URL */
        val stringRequest = StringRequest(
            Request.Method.GET, DATABASE_URI,
            { response ->
                progress_bar.visibility = View.INVISIBLE
                zorbaDBHelper.jsonToSqlite(response)
                refreshData()
                recyclerViewAdapter.notifyDataSetChanged()
            },
            { error -> Log.d(TAG, "That didn't work: $error") })

        /* Add the requests to the RequestQueue. */
        queue.add(stringRequest)
    }

    private fun onSearch(searchText: CharSequence) {
        search = searchText.toString()
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