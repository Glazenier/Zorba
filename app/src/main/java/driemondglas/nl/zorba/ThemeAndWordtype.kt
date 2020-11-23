package driemondglas.nl.zorba

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.theme_wordtype.*
import java.util.*

class ThemeAndWordType : AppCompatActivity() {
    private val zorbaDBHelper: ZorbaDBHelper = ZorbaDBHelper(this)

    private val allThemes = ArrayList<String>()
    private val allTypes = ArrayList<String>()

    /* save initial values to restore when cancel is pressed */
    private val originalTheme = thema
    private val originalType = wordType


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.theme_wordtype)

        val myBar = supportActionBar
        if (myBar != null) {
            /* This next line shows the home/back button.
             * The functionality is handled by the android system as long as a parent activity
             * is specified in the manifest.xls file
             */
            myBar.setDisplayHomeAsUpEnabled(true)
            myBar.title = "ZORBA"
            myBar.subtitle = getString(R.string.theme_wordtype_subtitle)
        }

        btn_select.setOnClickListener { selectAndFinish() }
        btn_cancel.setOnClickListener { cancelChanges() }
        btn_default.setOnClickListener { clearSelections() }

        populateThemes()
        populateWordTypes()

        lst_theme.setOnItemClickListener { _, _, position, _ ->
            val selectedTheme = allThemes[position]
            thema = if (position==0) "" else selectedTheme.substringBefore(" (")
            zorbaPreferences.edit().putString("theme", thema).apply()
        }

        lst_wordtype.setOnItemClickListener { _, _, position, _ ->
            val selWoordsoort = allTypes[position]
            wordType = if (position==0) "" else selWoordsoort.substringBefore(" (")
            zorbaPreferences.edit().putString("wordtype", wordType).apply()
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
        thema = ""
        wordType = ""
        lst_theme.setItemChecked(0, true)
        lst_wordtype.setItemChecked(0, true)
        zorbaPreferences.edit()
            .putString("theme", "")
            .putString("wordtype", "")
            .apply()
    }

    private fun cancelChanges() {
        /* restore initial values */
        wordType = originalType
        thema = originalTheme
        zorbaPreferences.edit()
              .putString("theme", thema)
              .putString("wordtype", wordType)
              .apply()
        // go back to calling activity
        val myIntent = Intent()
        myIntent.putExtra("result", "cancel")
        setResult(RESULT_OK, myIntent)
        finish()
    }

    private fun populateThemes() {
        var originalPosition = 0
        var runningPosition = 0

        // prepopulate arrayList with '★' (all)
        allThemes.add("★")

        // this query returns all distinct themes (column "Thema") and
        // the number of records per theme (column "thematotaal")
        val query = QueryManager.queryAlleThemas
        val db = zorbaDBHelper.readableDatabase
        val myCursor = db.rawQuery(query, null)

        // step through the records (themes)
        while (myCursor.moveToNext()) {
            val thema = myCursor.getString(myCursor.getColumnIndex("Thema"))
            val themaTotaal: Int = myCursor.getInt(myCursor.getColumnIndex("thematotaal"))
            allThemes.add("$thema ($themaTotaal)")
            runningPosition++
            if (thema == originalTheme) originalPosition = runningPosition
        }
        myCursor.close()
        db.close()
        // attach data to listbox via adapter
        val myAdapter = ArrayAdapter(this, R.layout.simple_list_item_checked, allThemes)
        lst_theme.adapter = myAdapter
        lst_theme.setItemChecked(originalPosition, true)
    }

    private fun populateWordTypes() {
        var originalPosition = 0
        var runningPosition = 0
        // prepopulate arrayList with '★' (all)
        allTypes.add("★")

        // this query returns all distinct wordtypes (column "woordsoort") and
        // the number of records per wordtype (column "soorttotaal")
        val query = QueryManager.queryAlleWoordSoorten
        val db = zorbaDBHelper.readableDatabase
        val myCursor = db.rawQuery(query, null)

        // step through the records (wordtypes)
        while (myCursor.moveToNext()) {
            val woordsoort = myCursor.getString(myCursor.getColumnIndex("woordsoort"))
            val soorttotaal = myCursor.getInt(myCursor.getColumnIndex("soorttotaal"))
            allTypes.add("$woordsoort ($soorttotaal)")
            runningPosition++
            if (woordsoort == originalType) originalPosition = runningPosition
        }

        myCursor.close()
        db.close()
        // attach data to listbox via adapter
        val myAdapter = ArrayAdapter(this, R.layout.simple_list_item_checked, allTypes)
        lst_wordtype.adapter = myAdapter
        lst_wordtype.setItemChecked(originalPosition, true)
    }
}
