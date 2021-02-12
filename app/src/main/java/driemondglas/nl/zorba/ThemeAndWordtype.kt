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

    override fun onBackPressed() {
        selectAndFinish()
        super.onBackPressed()
    }

    /* return to calling activity */
    private fun selectAndFinish() {
            // check if selection has changed so the cursor needs to get refreshed in the calling activity
            val result = if (thema == originalTheme && wordType == originalType)  "unchanged" else "changed"
            val myIntent = Intent()
            myIntent.putExtra("result", result)
            setResult(RESULT_OK, myIntent)
            finish()
    }

    override fun finish() {
        if (QueryManager.selectedCount() == 0) {
            colorToast(this, getString(R.string.warning_empty_cursor))
        } else {
            super.finish()
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }
    }

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

    /* restore initial values */
    private fun cancelChanges() {
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
        allThemes.add("★")  // prepopulate arrayList with '★' (all)

        val db = zorbaDBHelper.readableDatabase
        // the query queryAllThemes returns all distinct themes (column "Thema") and
        // the number of records per theme (column "thematotaal")
        db.rawQuery(QueryManager.queryAllThemes, null).apply {
            val colTheme = getColumnIndex("Thema")
            val colCount = getColumnIndex("thematotaal")
            // step through the records (themes)
            while (moveToNext()) {
                val wordTheme = getString(colTheme)
                val themeCount = getInt(colCount)
                allThemes.add("$wordTheme ($themeCount)")
                runningPosition++
                if (wordTheme == originalTheme) originalPosition = runningPosition
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
        allTypes.add("★") // prepopulate arrayList with '★' (all)

        val db = zorbaDBHelper.readableDatabase

        // the query queryAllWordTypes returns all distinct wordtypes (column "woordsoort") and
        // the number of records per wordtype (column "soorttotaal")
        db.rawQuery(QueryManager.queryAllWordTypes, null).apply {
            val colType = getColumnIndex("Woordsoort")
            val colCount = getColumnIndex("soorttotaal")
            // step through the records (wordtypes)
            while (moveToNext()) {
                val wordType = getString(colType)
                val typeCount = getInt(colCount)
                allTypes.add("$wordType ($typeCount)")
                runningPosition++
                if (wordType == originalType) originalPosition = runningPosition
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
