package driemondglas.nl.zorba

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.MenuItem
import android.widget.ArrayAdapter
import kotlinx.android.synthetic.main.activity_groep_woordsoort.*
import java.util.ArrayList

class GroepWoordsoort : AppCompatActivity() {
    private val zorbaDBHelper: ZorbaDBHelper = ZorbaDBHelper(this)
    private val queryManager: QueryManager = QueryManager.getInstance()

    private val alleGroepen = ArrayList<String>()
    private val alleSoorten = ArrayList<String>()

    /* save initial values to restore when cancel is pressed */
    private val oldGroep=queryManager.wordGroup
    private val oldSoort=queryManager.wordType

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_groep_woordsoort)

        val myBar = supportActionBar
        if (myBar != null) {
            /* This next line shows the home/back button.
             * The functionality is handled by the android system as long as a parent activity
             * is specified in the manifest.xls file
             */
            myBar.setDisplayHomeAsUpEnabled(true)
            myBar.title = "ZORBA"
            myBar.subtitle = "Selecteer woordsoort en/of woordgroep"
        }

        btn_select.setOnClickListener { selectAndFinish() }
        btn_cancel.setOnClickListener { cancelChanges() }
        btn_default.setOnClickListener { clearSelections() }

        populateGroepen()
        populateWoordsoort()

        lst_groep.setOnItemClickListener { _, _, position, _ ->
            val selGroep = alleGroepen[position]
            queryManager.wordGroup = if (selGroep == "*") "" else  selGroep.substringBeforeLast(" (")
        }

        lst_woordsoort.setOnItemClickListener { _, _, position, _ ->
            val selWoordsoort = alleSoorten[position]
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
        queryManager.wordGroup=""
        queryManager.wordType=""
        lst_groep.setItemChecked(0, true)
        lst_woordsoort.setItemChecked(0, true)
    }

    private fun cancelChanges(){
        /* restore initial values */
        queryManager.wordType = oldSoort
        queryManager.wordGroup = oldGroep
        // go back to calling activity
        val myIntent = Intent()
        myIntent.putExtra("result", "cancel")
        setResult(RESULT_OK, myIntent)
        finish()
    }

    private fun populateGroepen() {
        var oldPosition=0
        var thisPosition=0
        // prepopulate arrayList with '*' (all)
        alleGroepen.add("*")

        // this query returns all distinct wordgroups (column "groep") and
        // the number of records per wordgroup (column "groeptotaal")
        val query = queryManager.queryAlleGroepen
        val db = zorbaDBHelper.readableDatabase
        val myCursor = db.rawQuery(query, null)

        // step through the records (wordgroups)
        while (myCursor.moveToNext()) {
            val groep = myCursor.getString(myCursor.getColumnIndex("groep"))
            val groeptotaal: Int = myCursor.getInt(myCursor.getColumnIndex("groeptotaal"))
            alleGroepen.add("$groep ($groeptotaal)")
            thisPosition++
            if (groep == oldGroep) oldPosition = thisPosition
        }
        myCursor.close()
        db.close()

        // attach data to listbox via adapter
        val myAdapter = ArrayAdapter<String>(this,android.R.layout.simple_list_item_checked, alleGroepen)
        lst_groep.adapter = myAdapter
        lst_groep.setItemChecked(oldPosition, true)
    }

    private fun populateWoordsoort() {
        var oldPosition=0
        var thisPosition=0
        // prepopulate arrayList with '*' (all)
        alleSoorten.add("*")

        // this query returns all distinct wordtypes (column "woordsoort") and
        // the number of records per wordtype (column "soorttotaal")
        val query= queryManager.queryAlleWoordSoorten
        val db = zorbaDBHelper.readableDatabase
        val myCursor = db.rawQuery(query, null)

        // step through the records (wordtypes)
        while (myCursor.moveToNext()) {
            val woordsoort = myCursor.getString(myCursor.getColumnIndex("woordsoort"))
            val soorttotaal = myCursor.getInt(myCursor.getColumnIndex("soorttotaal"))
            alleSoorten.add("$woordsoort ($soorttotaal)")
            thisPosition++
            if (woordsoort==oldSoort) oldPosition = thisPosition
        }

        myCursor.close()
        db.close()
        // attach data to listbox via adapter
        val myAdapter = ArrayAdapter<String>(this,android.R.layout.simple_list_item_checked, alleSoorten)
        lst_woordsoort.adapter = myAdapter
        lst_woordsoort.setItemChecked(oldPosition, true)
    }
}
