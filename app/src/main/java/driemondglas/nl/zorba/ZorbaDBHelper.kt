package driemondglas.nl.zorba

import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray

/** This must be a singleton class so all classes can use the same instance. No class can call "new ZorbaDBHelper"
 *  This behaviour is enforced by making the constructor of the class private,
 *  and provide a getInstance() method in companion object
 */
class ZorbaDBHelper(zorbaContext: Context) : SQLiteOpenHelper(zorbaContext, DATABASE_NAME, null, DATABASE_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_ENTRIES)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        /* easy version: delete the records and reload them from the source */
        db.execSQL(SQL_DELETE_ENTRIES)
        onCreate(db)
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        /* same as on upgrade: delete the records and reload them from the source */
        db.execSQL(SQL_DELETE_ENTRIES)
        onCreate(db)
    }

    companion object {
        private const val DATABASE_VERSION = 1
        private const val DATABASE_NAME = "ZorbaDB"
        private const val SQL_DELETE_ENTRIES = "DROP TABLE IF EXISTS woorden"
        private const val SQL_CREATE_ENTRIES = "CREATE TABLE woorden (" +
              "id INT UNIQUE PRIMARY KEY," +
              "idx INT," +
              "GR TEXT," +
              "NL TEXT," +
              "Opm TEXT," +
              "Thema TEXT," +
              "Flash INT," +
              "Level INT," +
              "Woordsoort TEXT," +
              "PureLemma TEXT," +
              "PureLength INT," +
              "ActivePassiveRef INT)"
    }

    fun assessJumperTable() {
        writableDatabase.execSQL("CREATE TABLE IF NOT EXISTS jumpers (idx INT UNIQUE PRIMARY KEY, threshold INT)")
    }

    fun assessFlashTable() {
        writableDatabase.execSQL("CREATE TABLE IF NOT EXISTS flashedlocal (idx INT UNIQUE PRIMARY KEY, flashvalue INT)")
    }

    fun jsonToSqlite(volleyResponse: String) {
        val json = JSONArray(volleyResponse)

        /*remove existing table*/
        val db = writableDatabase
        db.execSQL("DROP TABLE IF EXISTS woorden")

        /*create new table structure*/
        onCreate(db)
        // From stackoverflow:
        // Unfortunately, JsonArray does not expose an iterator. So you will have to iterate through it using an index range:
        for (i in 0 until (json.length())) {
            json.getJSONObject(i).apply {
                val thisId = getString("id").toInt()
                val thisIdx = getString("idx").toInt()
                val thisGR = getString("GR")
                val thisNL = getString("NL")
                val thisOpm = getString("Opm")
                val thisThema = getString("Groep")
                val thisFlash = getString("Flash").toInt()
                val thisLevel = getString("Level").toInt()
                val thisWoordsoort = getString("Woordsoort")
                val apRef = getString("ActivePassiveRef").toInt()
                val pureLemma = pureLemma(thisGR).trim()
                val pureLength = pureLemma.length

                val query = "INSERT INTO woorden VALUES " +
                      "($thisId, $thisIdx, '$thisGR', '$thisNL', '$thisOpm', '$thisThema', $thisFlash," +
                      " $thisLevel, '$thisWoordsoort', '$pureLemma', $pureLength, $apRef);"
                db.execSQL(query)
            }
        }

        /* Remove records from jumpers if idx is no longer present in reloaded woorden table */
        val deletedJumpSQL =
            "DELETE  FROM jumpers  WHERE jumpers.idx IN " +
            "( SELECT jumpers.idx FROM jumpers LEFT JOIN woorden ON jumpers.idx=woorden.idx WHERE woorden.idx IS NULL );"
        db.execSQL(deletedJumpSQL)

        /* Remove records from flashedlocal if idx is no longer present in reloaded woorden table */
        val deletedFlashSQL =
            "DELETE  FROM flashedlocal  WHERE flashedlocal.idx IN " +
            "( SELECT flashedlocal.idx FROM flashedlocal LEFT JOIN woorden ON flashedlocal.idx=woorden.idx WHERE woorden.idx IS NULL );"
        db.execSQL(deletedFlashSQL)

        db.close()
    }

    private fun pureLemma(greekText: String): String {
        /* regex pattern returns all characters from the start that are not a delimiter
         * ^ beginning of line
         * [^ ]  character class not being one of ,\r(*;!.↔
         * \u2194 = ↔ double headed arrow
         * return result ?: ""   //Elvis: return result, if null return empty string "" */

        //keep exclamation or questionmark with pure lemma
        return Regex("""^[^,.\r(*\u2194]*""").find(greekText)?.value ?: ""
    }

    /* count selected records */
    fun lemmaCount() = DatabaseUtils.queryNumEntries(readableDatabase, "woorden")
    fun flashCount() = DatabaseUtils.queryNumEntries(readableDatabase, "flashedlocal")
    fun countJumpers() = DatabaseUtils.queryNumEntries(readableDatabase, "jumpers")

    /* see if record is flashed (record idx present in the flashed table) */
    fun isFlashed(indx: Int) = DatabaseUtils.queryNumEntries(readableDatabase, "flashedlocal", "idx=$indx") != 0L

}