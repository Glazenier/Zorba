package driemondglas.nl.zorba

object QueryManager {

    const val queryAlleWoordSoorten = "SELECT Woordsoort AS woordsoort, Count(*) AS soorttotaal FROM woorden GROUP BY Woordsoort ORDER BY Woordsoort;"
    const val queryAlleThemas = "SELECT Thema, Count(*) AS thematotaal FROM woorden WHERE woordsoort != 'liedtekst' GROUP BY Thema ORDER BY Thema;"

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

    private fun themaClause() = if (thema.isNotEmpty()) " AND thema = '$thema'" else ""
    private fun wordtypeClause() = if (wordType.isNotEmpty()) " AND woordsoort = '$wordType'" else ""
    private fun lengthClause() = if (pureLemmaLength != 0 && useLength) " AND PureLength = $pureLemmaLength" else ""
    private fun initialClause() = if (initial.isNotEmpty()) " AND " + initialToClause[initial].toString() else ""
    private fun searchClause() = if (search.isNotEmpty()) " AND ( GR like '%$search%' OR  NL like '%$search%') " else ""
    private fun thresholdClause() = if (hideJumpers) " AND  woorden.idx NOT IN (SELECT idx FROM jumpers) " else ""

    private fun orderbyClause() =
        when (orderbyTag) {
            "alfa" -> " ORDER BY LOWER(GR) COLLATE UNICODE" + if (orderDescending) " DESC" else ""
            "random" -> " ORDER BY RANDOM()"
            else -> " ORDER BY idx" + if (orderDescending) " DESC" else ""
        }

    /* The 3 boolean level-flags (Basic-Advanced-Ballast) are consolidated into an SQL clause
     * Note: when NO flags are set, or when ALL flags are set, the clause is empty (no filter on level)
     * So we only have to consider a single flag or two flags set
     */
    private fun levelClause(): String {
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

    /* MAIN selection QUERY
     * this function builds the main query that implements the various selections made by the user
     */
    fun mainQuery(): String {
        var sqlMain =
            if (flashed) {
                "SELECT * FROM flashedlocal LEFT JOIN woorden ON woorden.idx = flashedlocal.idx "
            } else {
                "SELECT * FROM woorden "
            }
        sqlMain += themaClause() + wordtypeClause() + levelClause() + lengthClause() + initialClause() + searchClause() + thresholdClause() + orderbyClause() + ";"

        /* replace first 'AND' with 'WHERE' */
        if (sqlMain.contains(" AND ")) sqlMain = sqlMain.replaceFirst(" AND ", " WHERE ")
        return sqlMain
    }

    /*  H A N G M A N  query */
    fun hangmanQuery(): String {
        /* length and order are not determined by the general configuration but specific to the hangman game */
        val lengthClause = if (useLength && pureLemmaLength in 1..15) " AND PureLength = $pureLemmaLength" else " AND PureLength <= 16"

        var sqlHangman = "SELECT PureLemma , NL FROM woorden" +
              themaClause() + wordtypeClause() + levelClause() + lengthClause + " ORDER BY RANDOM() LIMIT 10;"

        if (sqlHangman.contains(" AND ")) sqlHangman = sqlHangman.replaceFirst(" AND ", " WHERE ")
        return sqlHangman
    }

    /*  V E R B   G A M E  query */
    fun verbGameQuery(type1: Boolean = true, type2: Boolean = true, type3: Boolean = true): String {
        var typeClause: String
        if (type1 && type2 && type3) {
            typeClause = ""
        } else {
            val type1Clause = if (type1) """ OR (purelemma LIKE '%ω' AND purelemma NOT LIKE '%άω') """ else ""
            val type2Clause = if (type2) """ OR substr(purelemma,-2)='άω' OR substr(purelemma,-1)='ώ' """ else ""
            val type3Clause = if (type3) """ OR substr(purelemma,-3)='μαι' """ else ""

            typeClause = type1Clause + type2Clause + type3Clause
            if (typeClause.contains(" OR ")) typeClause = typeClause.replaceFirst(" OR ", "")
            typeClause = " AND ($typeClause)"
        }

        return if (flashed) {
            "SELECT woorden.idx, PureLemma, GR, NL FROM flashedlocal LEFT JOIN woorden ON woorden.idx = flashedlocal.idx WHERE woordsoort = 'werkwoord' " + levelClause()
        } else {
            "SELECT idx, PureLemma, GR, NL FROM woorden WHERE woordsoort = 'werkwoord' " + levelClause() + typeClause + " ORDER BY RANDOM()"
        }
    }

    /* function counts how many lemma's are available to verb game using current selections */
    fun verbGameCount(): Int {
        val countSQL = if (flashed) {
            "SELECT COUNT(*) AS verbCount FROM flashedlocal LEFT JOIN woorden ON woorden.idx = flashedlocal.idx WHERE woordsoort = 'werkwoord' " + levelClause()
        } else {
            "SELECT COUNT(*) AS verbCount FROM woorden WHERE woordsoort = 'werkwoord' " + levelClause()
        }
        val countCursor = zorbaDBHelper.readableDatabase.rawQuery(countSQL, null)
        val cnt= if (countCursor.moveToFirst())  countCursor.getInt(countCursor.getColumnIndex("verbCount")) else 0
        countCursor.close()
        return cnt
    }

    /* function counts how many lemma's are selected including locally flashed is so configured */
    fun selectedCount(): Int {
        var countSQL = if (flashed) {
            "SELECT COUNT(*) AS selCount FROM flashedlocal LEFT JOIN woorden ON woorden.idx = flashedlocal.idx "
        } else {
            "SELECT COUNT(*) AS selCount FROM woorden "
        }
        countSQL += themaClause() + wordtypeClause() + levelClause() + lengthClause() + initialClause() + searchClause() + thresholdClause() + ";"
        if (countSQL.contains(" AND ")) countSQL = countSQL.replaceFirst(" AND ", " WHERE ")
        val countCursor = zorbaDBHelper.readableDatabase.rawQuery(countSQL, null)
        val cnt= if (countCursor.moveToFirst())  countCursor.getInt(countCursor.getColumnIndex("selCount")) else 0
        countCursor.close()
        return cnt
    }
}
