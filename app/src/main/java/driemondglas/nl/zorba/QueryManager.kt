package driemondglas.nl.zorba

import android.util.Log

/** This must be a singleton class so all classes can use the same instance. No class can call "new QueryManager"
 *  It is enforced by making the constructor of the class private,
 *  and provide a getInstance() method in companion object
 */
class QueryManager private constructor() {
    /** other classes must be able to get to the single instance to use it's methods
     *  they will call the QueryManager.getInstance() method
     */
    companion object {
        /* create one and only instance */
        private val QueryManagerInstance = QueryManager()

        /* getInstance presents this instance to the world */
        fun getInstance(): QueryManager {
            return QueryManagerInstance
        }

        /* constants */
        const val DEFAULT_BLOCKSIZE = 20
        const val DEFAULT_LENGTH = 0
        const val DEFAULT_THRESHOLD = 2    // 3 goede antwoorden: JUMP
        const val DEFAULT_ORDERBY = "index"
    }


    /*  public vals can be read from outside, needs no getter   */
    val queryAlleWoordSoorten =
        "SELECT Woordsoort AS woordsoort, Count(*) AS soorttotaal FROM woorden GROUP BY Woordsoort ORDER BY Woordsoort;"
    val queryAlleGroepen =
        "SELECT Groep AS groep, Count(*) AS groeptotaal FROM woorden WHERE woordsoort != 'liedtekst' GROUP BY Groep ORDER BY Groep;"


    /* public vars can be written to and read from outside  (implied getter / setter) */
    var wordGroup = ""
    var wordType = ""
    var levelBasic = true
    var levelAdvanced = true
    var levelBallast = true
    var useLength = false
    var pureLemmaLength = DEFAULT_LENGTH
    var initial = ""
    var search = ""

    /* present the selected lemmas in blocks of [blocksize] (true by default) */
    var useBlocks = true
    /* and how big is this block? */
    var blockSize = DEFAULT_BLOCKSIZE

    var orderbyTag = DEFAULT_ORDERBY

    /* reverse sort order */
    var orderDecending = true

    /* Jumpers:
     * Jumpers are lemmas that have been answered correctly above the set threshold. "They jumped the threshold"
     * The idea is to hide those lemmas in further selections to focus on remaining lemmas
     */
    var jumpThreshold = DEFAULT_THRESHOLD
    var hideJumpers = false


    /* private variables, not visible to the outside world*/

    /* translate lowercase greek letter to corresponding SQL WHERE clause that also contains accented variants of the same letter */
    private val initialToClause: Map<String, String> = mapOf(
        "α" to "(GR LIKE 'α%' OR GR LIKE 'ά%')",
        "β" to "(GR LIKE 'β%')",
        "γ" to "(GR LIKE 'γ%')",
        "δ" to "(GR LIKE 'δ%')",
        "ε" to "(GR LIKE 'ε%' OR GR LIKE 'έ%')",
        "ζ" to "(GR LIKE 'ζ%')",
        "η" to "(GR LIKE 'η%' OR GR LIKE 'ή%')",
        "θ" to "(GR LIKE 'θ%')",
        "ι" to "(GR LIKE 'ι%' OR GR LIKE 'ί%' OR GR LIKE 'ϊ%' OR GR LIKE 'ΐ%')",
        "κ" to "(GR LIKE 'κ%')",
        "λ" to "(GR LIKE 'λ%')",
        "μ" to "(GR LIKE 'μ%')",
        "ν" to "(GR LIKE 'ν%')",
        "ξ" to "(GR LIKE 'ξ%')",
        "ο" to "(GR LIKE 'ο%' OR GR LIKE 'ό%')",
        "π" to "(GR LIKE 'π%')",
        "ρ" to "(GR LIKE 'ρ%')",
        "σ" to "(GR LIKE 'σ%')",
        "τ" to "(GR LIKE 'τ%')",
        "υ" to "(GR LIKE 'υ%' OR GR LIKE 'ύ%' OR GR LIKE 'ϋ%' OR GR LIKE 'ΰ%')",
        "φ" to "(GR LIKE 'φ%')",
        "χ" to "(GR LIKE 'χ%')",
        "ψ" to "(GR LIKE 'ψ%')",
        "ω" to "(GR LIKE 'ω%' OR GR LIKE 'ώ%')"
    )

    /* this function builds the main select query that implements the various selections made by the user */
    fun mainQuery(): String {
        val wordgroupClause = if (wordGroup.isNotEmpty()) " AND groep = '$wordGroup'" else ""
        val wordtypeClause = if (wordType.isNotEmpty()) " AND woordsoort = '$wordType'" else ""
        val levelClause = buildLevelClause()
        val lengthClause = if (pureLemmaLength != 0 && useLength) " AND PureLength = $pureLemmaLength" else ""
        val initialClause = if (initial.isNotEmpty()) " AND " + initialToClause[initial].toString() else ""
        val searchClause = if (search.isNotEmpty()) " AND ( GR like '%$search%' OR  NL like '%$search%') " else ""
        val thresholdClause = if (hideJumpers) " AND  woorden.idx NOT IN (SELECT idx FROM jumpers) " else ""
        val orderbyClause = when (orderbyTag) {
            "alfa" -> " ORDER BY LOWER(GR) COLLATE UNICODE" + if (orderDecending) " DESC" else ""
            "random" -> " ORDER BY RANDOM()"
            else -> " ORDER BY idx" + if (orderDecending) " DESC" else ""
        }

        var sqlMain = "SELECT * FROM woorden" +
              wordgroupClause +
              wordtypeClause +
              levelClause +
              lengthClause +
              initialClause +
              searchClause +
              thresholdClause +
              orderbyClause +  ";"
        if (sqlMain.contains(" AND ")) sqlMain = sqlMain.replaceFirst(" AND ", " WHERE ")
        Log.d("hvr", "Function: mainQuery(), Query looks like this: $sqlMain")
        return sqlMain
    }

    /* This function returns the selection clause only (without WHERE).
     * It is used to retrieve the record count of the selected records using DatabaseUtils.queryNumEntries
     * Obviously, clauses with no impact on count are not used here
     */
    fun selectionClauseOnly(): String? {
        val wordgroupClause = if (wordGroup.isNotEmpty()) " AND groep = '$wordGroup'" else ""
        val wordtypeClause = if (wordType.isNotEmpty()) " AND woordsoort = '$wordType'" else ""
        val levelClause = buildLevelClause()
        val lengthClause = if (pureLemmaLength != 0 && useLength) " AND PureLength = $pureLemmaLength" else ""
        val initialClause = if (initial.isNotEmpty()) " AND " + initialToClause[initial].toString() else ""
        val searchClause = if (search.isNotEmpty()) " AND ( GR like '%$search%' OR  NL like '%$search%') " else ""
        val thresholdClause = if (hideJumpers) " AND  woorden.idx NOT IN (SELECT idx FROM jumpers) " else ""

        var selection =
            wordgroupClause +
            wordtypeClause +
            levelClause +
            lengthClause +
            initialClause +
            searchClause +
            thresholdClause + ";"
        if (selection.contains(" AND ")) selection = selection.replaceFirst(" AND ", "")
        Log.d("hvr", "Function: selectionClauseOnly(), Clause looks like: $selection")
        /* return null if no selection is made, is required by DatabaseUtils.queryNumEntries. It will count all records in the table if selection is null */
        return if (selection == ";") null else selection
    }

    /*  H A N G M A N  query */
    fun hangmanQuery(): String {
        val wordgroupClause = if (wordGroup.isNotEmpty()) " AND groep = '$wordGroup'" else ""
        val wordtypeClause = if (wordType.isNotEmpty()) " AND woordsoort = '$wordType'" else ""
        val levelClause = buildLevelClause()
        val lengthClause = if (useLength && pureLemmaLength in 1..15) " AND PureLength = $pureLemmaLength" else " AND PureLength <= 16"
        val orderbyClause = " ORDER BY RANDOM() LIMIT 10"

        var sqlHangman =
            "SELECT PureLemma , NL FROM woorden" +
            wordgroupClause +
            wordtypeClause +
            levelClause +
            lengthClause +
            orderbyClause + ";"

        if (sqlHangman.contains(" AND ")) sqlHangman = sqlHangman.replaceFirst(" AND ", " WHERE ")
        Log.d("hvr", "Hangman query is: $sqlHangman")
        return sqlHangman
    }

    fun verbGameQuery(): String{
        val sqlVerbGame =  "SELECT PureLemma, GR, NL FROM woorden WHERE woordsoort = 'werkwoord' " + buildLevelClause() + " ORDER BY RANDOM() LIMIT 100"
        Log.d("hvr", "Game query is: $sqlVerbGame")
        return sqlVerbGame
    }

    /* function is called from the menu item 'Clear All' to reset all selections made by user. */
    fun clearAll() {
        wordGroup = ""
        wordType = ""
        search=""
        clearDetails()
    }

    fun clearDetails() {
        levelBasic = true
        levelAdvanced = true
        levelBallast = true
        useLength = false
        pureLemmaLength = 0
        initial = ""
        useBlocks = true
        blockSize = DEFAULT_BLOCKSIZE
        orderbyTag = DEFAULT_ORDERBY
        jumpThreshold = DEFAULT_THRESHOLD
        hideJumpers = false
    }

    /* The 3 boolean level-flags (Basic-Advanced-Ballast) are consolidated into an SQL clause
     * Note: when NO flags are set, or when ALL flags are set, the clause is empty (no filter on level)
     * So we only have to consider single flag or two flags set
     */
    private fun buildLevelClause(): String {
        var levelString = ""
        if (levelBasic) levelString += "1"
        if (levelAdvanced) levelString += "2"
        if (levelBallast) levelString += "3"
        return when (levelString.length) {
            1 -> " AND level=$levelString"
            2 -> " AND (level=${levelString[0]} OR level=${levelString[1]} )"
            else -> ""
        }
    }
}
