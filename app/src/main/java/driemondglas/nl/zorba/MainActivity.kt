package driemondglas.nl.zorba

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.database.DatabaseUtils
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import com.koushikdutta.ion.Ion
import kotlinx.android.synthetic.main.activity_main.*
import driemondglas.nl.zorba.Utils.enabled
import java.util.*

/* Top-level Properties:
 * below are the self chosen 'unique' request codes used to start the various activities
 */
const val SHOW_CARDS_CODE = 3108
const val GROEP_SOORT_CODE = 3208
const val SELECTIES_CODE = 3308
const val UNKNOWN_VERB = "Werkwoordvorm onbekend"

/*  Initialise the database helper class. */
lateinit var zorbaDBHelper: ZorbaDBHelper

/* text to speech object used throughout the application*/
lateinit var zorbaSpeaks: TextToSpeech

/* speech on or off */
var useSpeech = true

/* main activity class implements TextToSpeech.OnInitListener */
class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    /* overriding onInit() is needed for the TextToSpeech.OnInitListener */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            /* set Greek as language for text to speech object */
            zorbaSpeaks.setLanguage(Locale("el_GR"))
        } else Log.e("hvr", "peech initilization problem!")
    }

    /* Initialise the Query Manager class.
     * This class builds and manages all queries to the database */
    private var queryManager = QueryManager.getInstance()


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

        val thisIdx=thisLemmaItem.idx

        val myIntent = Intent(this,FlashCard::class.java)
        myIntent.putExtra("idx",thisIdx)
        startActivity(myIntent)


//        /* take the property you need and show to the user */
//        val textToDisplay = thisLemmaItem.textGreek + "\n\n" + thisLemmaItem.meaningNL
//
//        val bob = AlertDialog.Builder(this)
//              .setMessage(textToDisplay)
//              .setIcon(R.drawable.greneth)
//              .setTitle(" ") // needed to display the icon on the title line
//              .setPositiveButton(R.string.emoji_ok, null)  // no action, just close the message
//              .setNeutralButton(R.string.speak, null)  // we write our own custom listener below
//        val alertDialog = bob.create()
//        alertDialog.show()
//
//        val neutralButton = alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL)
//        neutralButton.setOnClickListener { if (useSpeech) cleanSpeech(thisLemmaItem.textGreek, thisLemmaItem.woordsoort) }
//        neutralButton.textSize = 28f
//        neutralButton.enabled(useSpeech)
//
//        alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).textSize = 28f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /* FIRST launch the Splash Activity , note: done before  inflation of the main activity layout*/
        val splashIntent = Intent(this, Splash::class.java)
        startActivity(splashIntent)

        /*  inflate the layout file for this activity */
        setContentView(R.layout.activity_main)

        zorbaDBHelper = ZorbaDBHelper(applicationContext)

        /* create action bar on top */
        val myBar = supportActionBar
        if (myBar != null) {
            myBar.setDisplayShowHomeEnabled(true)
            myBar.title = "  " + getString(R.string.app_name)
            myBar.subtitle = "  " + getString(R.string.app_subtitle)
            myBar.setIcon(R.drawable.greneth)
        }

        /* set listener for changes in search field   */
        text_search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) { onSearch(s) }
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
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        /*  Inflate the menu from the layout file
         *  this adds menu items to the action bar if it is present.
         */
        menuInflater.inflate(R.menu.menu_main, menu)
        menu.findItem(R.id.menu_speech).isChecked = useSpeech
        return true
    }

    override fun onStart() {
        /* initialise (late) the reference to the text to speech object */
        if (!::zorbaSpeaks.isInitialized) zorbaSpeaks = TextToSpeech(this, this)
        super.onStart()
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        /* Handle action bar (menu) item clicks here. */

        when (item.itemId) {
            /* menu set woordsoort/woordgroep */
            R.id.menu_set_groep_soort -> {
                /* launch the Groep/Woordsoort Selection Activity */
                val myIntent = Intent(this, GroepWoordsoort::class.java)
                startActivityForResult(myIntent, GROEP_SOORT_CODE)
            }

            /*  menu detail selecties */
            R.id.menu_set_block_sort -> {
                /* launch the Selecties Activity */
                val myIntent = Intent(this, Selecties::class.java)
                startActivityForResult(myIntent, SELECTIES_CODE)
            }

            /* menu wis alle selecties */
            R.id.menu_clear_selects -> {
                queryManager.clearAll()
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
            R.id.menu_about -> buidAbout()

            /* perform any other actions triggered from parent or beyond */
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

//    override fun onDestroy() {
//        /* Shutdown TTS when app gets destroyed by android */
//        zorbaSpeaks.stop()
//        zorbaSpeaks.shutdown()
//        super.onDestroy()
//    }

    private fun refreshData() {
        /* method clears the existing array list and refills it with fresh data from the database */
        var lemmaItem: LemmaItem
        val db = zorbaDBHelper.readableDatabase
        val myCursor = db.rawQuery(queryManager.mainQuery(), null)

        /* remove existing content */
        lemmaArrayList.clear()

        /* step through the records (wordtypes) */
        while (myCursor.moveToNext()) {
            lemmaItem = LemmaItem(
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
    private fun buidAbout() {
        val textToDisplay =
            getString(R.string.description) +
            "\n" + "Author: " + getString(R.string.author) +
            "\n" + "Version: " + BuildConfig.VERSION_NAME +
            "\n" + "Build: " + BuildConfig.VERSION_CODE +
            "\n" + getString(R.string.version_info) +
            showCount()

        val bob = AlertDialog.Builder(this)
            .setTitle("About Zorba")
            .setMessage(textToDisplay)
            .setPositiveButton(R.string.emoji_ok, null)

        /* create the dialog from the builder */
        val alertDialog = bob.create()
        alertDialog.show()
        alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).textSize = 28f
    }

    /* count selected records */
    private fun showCount(): String {
        val db = zorbaDBHelper.readableDatabase
        val countAll = DatabaseUtils.queryNumEntries(db, "woorden")
        val countSelected = DatabaseUtils.queryNumEntries(db, "woorden", queryManager.selectionClauseOnly())
        val countJumpers = DatabaseUtils.queryNumEntries(db, "jumpers")
        db.close()
        return "\n\n $countSelected selected of $countAll lemmas.\nJumpers: $countJumpers"
    }

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
        Ion.with(this)
        .load(ZorbaDBHelper.DATABASE_URI)
        .asString()
        .setCallback { _, result ->
              zorbaDBHelper.jsonToSqlite(result)
              refreshData()
              recyclerViewAdapter.notifyDataSetChanged()
              progress_bar.visibility = View.INVISIBLE
        }
    }

    private fun onSearch(searchText: CharSequence) {
        queryManager.search = searchText.toString()
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