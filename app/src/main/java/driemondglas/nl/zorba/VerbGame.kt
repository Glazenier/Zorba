package driemondglas.nl.zorba

import android.annotation.SuppressLint
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import kotlinx.android.synthetic.main.verb_game.*

class VerbGame : AppCompatActivity() {
    private val queryManager: QueryManager = QueryManager.getInstance()
    private lateinit var mainCursor: Cursor
    private lateinit var db: SQLiteDatabase
    private val persoonsvormNL = listOf("1ste persoon enkelvoud: ", "2de persoon enkelvoud: ", "3de persoon enkelvoud: ", "1ste persoon meervoud: ", "2de persoon meervoud: ", "3de persoon meervoud: ")
    private val persoonsvormGR = listOf("Εγώ", "Εσύ", "Αυτός, αυτή, αυτό", "Εμείς", "Εσείς", "Αυτοί, αυτές, αυτά")
    private val tijdvorm=listOf("Tegenwoordige tijd","Toekomende tijd","Voltooid Verleden tijd (Aorist)", "Onvoltooid Verleden tijd")
    private var pickOne=0
    private lateinit var parts: List<String>

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.verb_game)

        /* get a reference to the database */
        db = zorbaDBHelper.readableDatabase

        /* get the specific selection query from the Query manager  */
        val query = queryManager.verbGameQuery
        mainCursor = db.rawQuery(query, null)

        btn_go.setOnClickListener {if (mainCursor.moveToNext()) setupVerb() else Utils.colorToast(this, "Thats all")
            text_reveal.visibility= View.INVISIBLE}
        btn_reveal.setOnClickListener {
            text_reveal.visibility= View.VISIBLE
            text_ask.text = "${persoonsvormGR[pickOne]} ${parts[pickOne]}"
        }

        /* if there is indeed a record returned...
         * ... setup the game
         */
        if (mainCursor.moveToNext()) setupVerb()
    }
    @SuppressLint("SetTextI18n")
    private fun setupVerb(){
        val theVerb = mainCursor.getString(mainCursor.getColumnIndex("PureLemma"))
        val theTenses = mainCursor.getString(mainCursor.getColumnIndex("GR"))
        val betekenis = mainCursor.getString(mainCursor.getColumnIndex("NL"))

        var avail = "1"
        if (hasMellontas(theTenses)) avail += "2"
        if (hasAorist(theTenses)) avail += "3"
        if (hasParatatikos(theTenses)) avail += "4"
        val pos = (1..avail.length).shuffled().last()
        val choice = avail[pos-1].toString().toInt()
        val tense = when (choice) {
            1 -> conjureEnestotas(theTenses)
            2 -> conjureMellontas(theTenses)
            3 -> conjureAorist(theTenses)
            4 -> conjureParatatikos(theTenses)
            else -> "unknown"
        }
        parts = tense.split(",")
        if (parts[0]!="Werkwoordtype onbekend") {
        val conjureCount=parts.size
        Log.d("hvr","nr of conjurgations: $conjureCount")
        pickOne=(0 until conjureCount).shuffled().last()
        Log.d("hvr"," picked: $pickOne")
        text_ask.text = parts[pickOne]
        text_reveal.text="Komt van: $theVerb\nBetekent: $betekenis\n\n${tijdvorm[choice-1]}\n${persoonsvormNL[pickOne]}"
        }
    }

    override fun onStop() {
        /* make sure the db etc get properly closed */
        mainCursor.close()
        db.close()
        super.onStop()
    }
}
