package driemondglas.nl.zorba

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.MenuItem
import android.widget.ArrayAdapter
import kotlinx.android.synthetic.main.wordgroup_wordtype.*
import java.util.ArrayList

class WordTypeAndGroup : AppCompatActivity() {
    private val zorbaDBHelper: ZorbaDBHelper = ZorbaDBHelper(this)
    private val queryManager: QueryManager = QueryManager.getInstance()

    private val allGroups = ArrayList<String>()
    private val allTypes = ArrayList<String>()

    /* save initial values to restore when cancel is pressed */
    private val origGroup = queryManager.wordGroup
    private val origType = queryManager.wordType


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.wordgroup_wordtype)

        val myBar = supportActionBar
        if (myBar != null) {
            /* This next line shows the home/back button.
             * The functionality is handled by the android system as long as a parent activity
             * is specified in the manifest.xls file
             */
            myBar.setDisplayHomeAsUpEnabled(true)
            myBar.title = "ZORBA"
            myBar.subtitle = getString(R.string.word_group_type_subtitle)
        }

        btn_select.setOnClickListener { selectAndFinish() }
        btn_cancel.setOnClickListener { cancelChanges() }
        btn_default.setOnClickListener { clearSelections() }

        populateWordGroups()
        populateWordTypes()

        lst_wordgroup.setOnItemClickListener { _, _, position, _ ->
            val selGroep = allGroups[position]
            queryManager.wordGroup = if (selGroep == "*") "" else selGroep.substringBeforeLast(" (")
        }

        lst_wordtype.setOnItemClickListener { _, _, position, _ ->
            val selWoordsoort = allTypes[position]
            queryManager.wordType = if (selWoordsoort == "*") "" else selWoordsoort.substringBeforeLast(" (")
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.decor.
        // We have our own goBack() routine (hvr)
        if (item.itemId == android.R.id.home) selectAndFinish()
        return true
    }

    private fun selectAndFinish() {
        // return to calling activity
        val myIntent = Intent()
        myIntent.putExtra("result", "selected")
        setResult(RESULT_OK, myIntent)
        finish()
    }

    private fun clearSelections() {
        queryManager.wordGroup = ""
        queryManager.wordType = ""
        lst_wordgroup.setItemChecked(0, true)
        lst_wordtype.setItemChecked(0, true)
    }

    private fun cancelChanges() {
        /* restore initial values */
        queryManager.wordType = origType
        queryManager.wordGroup = origGroup
        // go back to calling activity
        val myIntent = Intent()
        myIntent.putExtra("result", "cancel")
        setResult(RESULT_OK, myIntent)
        finish()
    }

    private fun populateWordGroups() {
        var oldPosition = 0
        var thisPosition = 0
        // prepopulate arrayList with '*' (all)
        allGroups.add("*")

        // this query returns all distinct wordgroups (column "groep") and
        // the number of records per wordgroup (column "groeptotaal")
        val query = queryManager.queryAlleGroepen
        val db = zorbaDBHelper.readableDatabase
        val myCursor = db.rawQuery(query, null)

        // step through the records (wordgroups)
        while (myCursor.moveToNext()) {
            val groep = myCursor.getString(myCursor.getColumnIndex("groep"))
            val groeptotaal: Int = myCursor.getInt(myCursor.getColumnIndex("groeptotaal"))
            allGroups.add("$groep ($groeptotaal)")
            thisPosition++
            if (groep == origGroup) oldPosition = thisPosition
        }
        myCursor.close()
        db.close()

        // attach data to listbox via adapter
        val myAdapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_checked, allGroups)
        lst_wordgroup.adapter = myAdapter
        lst_wordgroup.setItemChecked(oldPosition, true)
    }

    private fun populateWordTypes() {
        var oldPosition = 0
        var thisPosition = 0
        // prepopulate arrayList with '*' (all)
        allTypes.add("*")

        // this query returns all distinct wordtypes (column "woordsoort") and
        // the number of records per wordtype (column "soorttotaal")
        val query = queryManager.queryAlleWoordSoorten
        val db = zorbaDBHelper.readableDatabase
        val myCursor = db.rawQuery(query, null)

        // step through the records (wordtypes)
        while (myCursor.moveToNext()) {
            val woordsoort = myCursor.getString(myCursor.getColumnIndex("woordsoort"))
            val soorttotaal = myCursor.getInt(myCursor.getColumnIndex("soorttotaal"))
            allTypes.add("$woordsoort ($soorttotaal)")
            thisPosition++
            if (woordsoort == origType) oldPosition = thisPosition
        }

        myCursor.close()
        db.close()
        // attach data to listbox via adapter
        val myAdapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_checked, allTypes)
        lst_wordtype.adapter = myAdapter
        lst_wordtype.setItemChecked(oldPosition, true)
    }
}