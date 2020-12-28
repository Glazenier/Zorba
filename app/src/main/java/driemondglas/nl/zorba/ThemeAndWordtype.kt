package driemondglas.nl.zorba

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import driemondglas.nl.zorba.databinding.ThemeWordtypeBinding
import java.util.*

class ThemeAndWordType : AppCompatActivity() {
    private val zorbaDBHelper: ZorbaDBHelper = ZorbaDBHelper(this)

    private val allThemes = ArrayList<String>()
    private val allTypes = ArrayList<String>()

    /* save initial values to restore when cancel is pressed */
    private val originalTheme = thema
    private val originalType = wordType

    private lateinit var binding: ThemeWordtypeBinding  // replaces synthetic binding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ThemeWordtypeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.title_activity)
            subtitle = getString(R.string.subtitle_theme_wordtype)
        }

        binding.btnSelect.setOnClickListener { selectAndFinish() }
        binding.btnCancel.setOnClickListener { cancelChanges() }
        binding.btnDefault.setOnClickListener { clearSelections() }

        populateThemes()
        populateWordTypes()

        binding.lstTheme.setOnItemClickListener { _, _, position, _ ->
            val selectedTheme = allThemes[position]
            thema = if (position==0) "" else selectedTheme.substringBefore(" (")
            zorbaPreferences.edit().putString("theme", thema).apply()
        }

        binding.lstWordtype.setOnItemClickListener { _, _, position, _ ->
            val selWoordsoort = allTypes[position]
            wordType = if (position==0) "" else selWoordsoort.substringBefore(" (")
            zorbaPreferences.edit().putString("wordtype", wordType).apply()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
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

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)    }

    private fun clearSelections() {
        thema = ""
        wordType = ""
        binding.lstTheme.setItemChecked(0, true)
        binding.lstWordtype.setItemChecked(0, true)
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
        allThemes.clear()

        // prepopulate arrayList with '★' (all)
        allThemes.add("★")

        // this query returns all distinct themes (column "Thema") and
        // the number of records per theme (column "thematotaal")
        val db = zorbaDBHelper.readableDatabase
        db.rawQuery(QueryManager.queryAllThemes, null).apply {
            val col0 = getColumnIndex("Thema")
            val col1 = getColumnIndex("thematotaal")
            // step through the records (themes)
            while (moveToNext()) {
                val thema = getString(col0)
                val themaTotaal: Int = getInt(col1)
                allThemes.add("$thema ($themaTotaal)")
                runningPosition++
                if (thema == originalTheme) originalPosition = runningPosition
            }
            close()
        }
        db.close()
        // attach data to listbox via adapter
        val myAdapter = ArrayAdapter(this, R.layout.simple_list_item_checked, allThemes)
        binding.lstTheme.adapter = myAdapter
        binding.lstTheme.setItemChecked(originalPosition, true)
    }

    private fun populateWordTypes() {
        var originalPosition = 0
        var runningPosition = 0
        allTypes.clear()
        // prepopulate arrayList with '★' (all)
        allTypes.add("★")

        // this query returns all distinct wordtypes (column "woordsoort") and
        // the number of records per wordtype (column "soorttotaal")
        val db = zorbaDBHelper.readableDatabase
        db.rawQuery(QueryManager.queryAllWordTypes, null).apply {
            val col0 = getColumnIndex("woordsoort")
            val col1 = getColumnIndex("soorttotaal")
            // step through the records (wordtypes)
            while (moveToNext()) {
                val woordsoort = getString(col0)
                val soorttotaal = getInt(col1)
                allTypes.add("$woordsoort ($soorttotaal)")
                runningPosition++
                if (woordsoort == originalType) originalPosition = runningPosition
            }
            close()
        }
        db.close()
        // attach data to listbox through adapter
        val myAdapter = ArrayAdapter(this, R.layout.simple_list_item_checked, allTypes)
        binding.lstWordtype.adapter = myAdapter
        binding.lstWordtype.setItemChecked(originalPosition, true)
    }
}
