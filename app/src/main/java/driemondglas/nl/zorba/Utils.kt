package driemondglas.nl.zorba

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import driemondglas.nl.zorba.Utils.replace
import driemondglas.nl.zorba.Utils.stressLastVowel
import driemondglas.nl.zorba.Utils.stressOneChar
import driemondglas.nl.zorba.Utils.unStress

const val consonants = "βγδζθκλμνξπρσςτφχψ"
val stressedVowels = "άέήίΐόύΰώ".toCharArray()
val unstressedVowels = "αεηιϊουϋω".toCharArray()

val inBracketsRegex = Regex( """\(.*?\)""")


/* function is called from the menu item 'Clear All' to reset all selections made by user. */
fun clearAllSelections() {
    resetThemeAndType()
    resetDetails()
}

fun resetThemeAndType(){
    thema = ""
    wordType = ""
    search = ""
    zorbaPreferences.edit()
        .putString("theme", "")
        .putString("wordtype", "")
        .apply()
}

fun resetDetails() {
    // retrieve default config from shared preferences
    with(zorbaPreferences) {
        useBlocks = getBoolean("defaultuseblocks", true)
        blockSize = getInt("defaultblocksize", 20)
        levelBasic = getBoolean("defaultlevelbasic", true)
        levelAdvanced = getBoolean("defaultleveladvanced", true)
        levelBallast = getBoolean("defaultlevelballast", true)
        useLength = getBoolean("defaultuselength", false)
        pureLemmaLength = getInt("defaultpurelemmalength", 6)
        initial = getString("defaultinitial", "") ?: ""
        orderbyTag = getString("defaultorderbytag", "index") ?: "index"
        orderDescending = getBoolean("defaultorderdescending", true)
        jumpThreshold = getInt("defaultjumpthreshold", 2)
        hideJumpers = getBoolean("defaulthidejumpers", false)
        flashed = getBoolean("defaultflashed", false)
        speechRate = getFloat("defaultspeechrate", 1.0f)
    }
    zorbaPreferences.edit()
          .putBoolean("useblocks", useBlocks)
          .putInt("blocksize", blockSize)
          .putBoolean("levelbasic", levelBasic)
          .putBoolean("leveladvanced", levelAdvanced)
          .putBoolean("levelballast", levelBallast)
          .putBoolean("uselength", useLength)
          .putInt("purelemmalength", pureLemmaLength)
          .putString("initial", initial)
          .putString("orderbytag", orderbyTag)
          .putBoolean("orderdescending", orderDescending)
          .putInt("jumpthreshold", jumpThreshold)
          .putBoolean("hidejumpers", hideJumpers)
          .putBoolean("flashed", flashed)
          .putFloat("speechrate", speechRate)
          .apply()
}

//val articleRegex = Regex("""(.*?),\s(τ?[ηοα]ι?)""")  //match greek articles after a komma+space: , ο, η, το, οι, τα
val articleRegex = Regex("""(.*?),\s([οητ][οι]?)""")  //match greek articles after a komma+space: , ο, η, το, οι, τα
val adjectiveRegex = Regex("""(.*?)(ε?[οηόύήί][ςιί]),\s?-(ε?[αάεέηήιί][αάς]?),\s?-([άαεέίοόύή]ς?)""")   //changed for -είς, -είς, -ή
val eeStartSound = Regex("""\b(([οε]?[ιί])|([ηήυύ]))""")
val oStartSound = Regex("""\b[οόωώ][^ιί]""")

fun cleanSpeech(rawText: String, wordType: String) {
    var result = ""
    val takeAbrake= if (zorbaSpeaks.defaultEngine.contains ("acapelagroup")) """\pau=75\""" else ","

    when (wordType) {
        "bijvoeglijk nw", "voornaamwoord", "telwoord" -> {
            /* uses adjectiveRegex to match endings of greek adjectives and similar
             * returns capturing groups:
             *   matchResult.groups[0]: whole match
             *   matchResult.groups[1]: stem
             *   matchResult.groups[2]: male ending
             *   matchResult.groups[3]: female ending
             *   matchResult.groups[4]: neuter ending  */

            /* multiple lines of related adjectives are possible, so assess all lines */
            rawText.lines().forEach {
                val matchResult = adjectiveRegex.find(it)
                result += if (matchResult != null) {
                    /* combine stem with endings */
                    val stem = matchResult.groups[1]?.value
                    val maleEnd = matchResult.groups[2]?.value
                    val femaleEnd = matchResult.groups[3]?.value
                    val neuterEnd = matchResult.groups[4]?.value
                    "$stem$maleEnd, $stem$femaleEnd, $stem$neuterEnd. "
                } else {
                    it.replace(inBracketsRegex, "") + ". "
                }
            }
        }
        "zelfstandig nw" -> {
            /* uses articleRegex to match greek articles: ο, η, το, οι, τα
               returns capturing groups:
                    * matchResult.groups[0]: whole match
                    * matchResult.groups[1]: noun
                    * matchResult.groups[2]: article */

            // multiple lines of similar nouns are possible, so assess all lines
            rawText.lines().forEach {
                val matchResult = articleRegex.find(it)
                if (matchResult != null) {
                    /* put article before noun */
                    val noun = matchResult.groups[1]?.value
                    var article = matchResult.groups[2]?.value
                    if (noun == null || article == null) {
                        result += "$it,"
                    } else {
                        /* if article ends with 'o' and noun begins with 'o', the speech engine combines them into one 'o'
                         *  This is not what we want, so
                         *  an extra ',' is inserted to ensure we hear two separate 'o's */
                        if (article.last() == 'ο' && oStartSound.find(noun)!=null) article += takeAbrake

                        // Same thing for ee-sound (ie-klank)
                        if ( (article == "η" || article == "οι") && eeStartSound.find(noun) != null ) article += takeAbrake
                        result += "$article $noun,"
                    }
                } else {
                    result += "$it,"
                }
            }
        }

        "lidwoord" -> {
            result = rawText.replace("""\s+""".toRegex(), ",") + "."
        }

        "werkwoord" -> {
            result = "ενεστώτας," + getEnestotas(rawText)
            if (hasMellontas(rawText)) result += ",μέλλοντας," +  getMellontas(rawText)
            if (hasAorist(rawText)) result += ",αόριστος," +  getAorist(rawText)
            if (hasParatatikos(rawText)) result += ",παρατατικός," +  getParatatikos(rawText)
            if (hasMellontas(rawText)) result += ", προστακτική," +  createProstaktiki(rawText)
            result += "."
        }
        
        else -> {   // Standard clean-up for other word types:

            /* 1: In speech output we don't want text in brackets */
            result = rawText.replace(inBracketsRegex, "")

            /* 2: Change space dash space to insert pause (gebiedende wijs) */
            result = result.replace(" - ", takeAbrake)

            /* 3: Change equeals sign (=) to insert pause for speech */
            result = result.replace("=", takeAbrake)
        }
    }
    Log.d(TAG, "cleanSpeech: $result")
    zorbaSpeaks.speak(result, TextToSpeech.QUEUE_FLUSH, null, "")
}

/* This function returns all the greek characters from first line of the text until non greek is found */
val firstWordRegex = Regex("""^[\p{InGreek}]*""")
fun getEnestotas(textGreek: String): String = firstWordRegex.find(textGreek)?.value ?: ""

/* This function returns all the greek characters from the second line of the text until non greek is found */
val mellontasRegex = Regex("""^(?:.*\R)(\p{InGREEK}+)""")
fun getMellontas(textGreek: String): String = mellontasRegex.find(textGreek)?.groups?.get(1)?.value ?: ""

/* This function returns all the greek characters from the third line of the text until non greek is found */
val aoristosRegex = Regex("""^(?:.*\R){2}(\p{InGREEK}+)""")
fun getAorist(textGreek: String): String = aoristosRegex.find(textGreek)?.groups?.get(1)?.value ?: ""

/* This function returns all the greek characters from the fourth line of the text until non greek is found */
val paratatikosRegex = Regex("""^(?:.*\R){3}(\p{InGREEK}+)""")
fun getParatatikos(textGreek: String): String = paratatikosRegex.find(textGreek)?.groups?.get(1)?.value ?: ""

//fun hasEnestotas(textGreek: String): Boolean = Regex("""^\p{InGREEK}+""").containsMatchIn(textGreek)

fun hasMellontas(textGreek: String): Boolean = Regex("""^(.*\R)\p{InGREEK}+""").containsMatchIn(textGreek)

fun hasAorist(textGreek: String): Boolean = Regex("""^(.*\R){2}\p{InGREEK}+""").containsMatchIn(textGreek)

fun hasParatatikos(textGreek: String): Boolean = Regex("""^(.*\R){3}\p{InGREEK}+""").containsMatchIn(textGreek)

fun buildHTMLtable(textGreek: String): String {
    val ene: List<String> = conjugateEnestotas(textGreek)
    if (ene.size < 5) return "<p> geen vervoegingen beschikbaar.</p>"
    var mel: List<String> = conjugateMellontas(textGreek)
    var par: List<String> = conjugateParatatikos(textGreek)
    var aor: List<String> = conjugateAoristos(textGreek)
    var pro: List<String> = createProstaktiki(textGreek)

    if (mel.size < 5) mel = listOf("", "", "", "", "", "")
    if (par.size < 5) par = listOf("", "", "", "", "", "")
    if (aor.size < 5) aor = listOf("", "", "", "", "", "")
    if (pro.size < 2) pro = listOf("", "")
    var htmlText = """
<table style="font-size: 11pt;">
  <tr>
    <th style='border: 1px solid black; background-color:gold;'>ENESTOTAS</th>
    <th style='border: 1px solid black; background-color:gold;'>MELLONTAS</th>
    <th style='border: 1px solid black; background-color:gold;'>AORISTOS</th>
    <th style='border: 1px solid black; background-color:gold;'>PARATATIKOS</th>
  </tr>
"""
    // conjugations
    for (i in 0..5) htmlText += "  <tr><td>${ene[i]}</td><td>${mel[i]}</td><td>${aor[i]}</td><td>${par[i]}</td></tr>" + "\n"

    // imperatief προστακτική
    htmlText += """  <tr>
    <th colspan=2 style='border: 1px solid black; background-color:gold;'>PROSTAKTIKI</th>
    <td style='border-top: 1px solid black;'>${pro[0]}</td>
    <td style='border-top: 1px solid black;'>${pro[1]}</td>
  </tr>
</table>"""
    return htmlText
}

fun conjugateEnestotas(textGreek: String): List<String> {
    var stem = ""
    var verbType = ""
    var oneSyllable = false
    val enestotas = getEnestotas(textGreek)
    if (enestotas.isEmpty()) return emptyList()

    when {
        enestotas in setOf("λέω", "πάω", "φταίω", "τρώω") -> {
            stem = enestotas.dropLast(1)
            verbType = "A2"
            oneSyllable = true //needed to remove stress from λές πάς, φταίς, τρώς, κτλ
        }
        enestotas == "ζω" -> {
            stem = "ζ"
            verbType = "B4"
        }
        enestotas.endsWith("ιστώ") -> {
            stem = enestotas.dropLast(1)
            verbType = "B3"
        }
        enestotas.endsWith("άω") -> {
            stem = enestotas.dropLast(2)
            verbType = "B1"
        }
        enestotas.endsWith("ώ") -> {
            stem = enestotas.dropLast(1)
            verbType = "B2"
        }
        enestotas.endsWith("ω") -> {
            stem = enestotas.dropLast(1)
            verbType = when {
                stem.last() in consonants -> "A1"
                stem.endsWith("εύ") -> "A1"
                stem.endsWith("έ") -> "A1"
                else -> "A2"
            }
        }
        enestotas.endsWith("ομαι") -> {
            stem = enestotas.dropLast(4)
            verbType = "Γ1"
        }
        enestotas.endsWith("άμαι") -> {
            stem = enestotas.dropLast(4)
            verbType = "Γ2"
        }
        enestotas.endsWith("ιέμαι") -> {
            stem = enestotas.dropLast(5)
            verbType = "Γ3"
        }
        enestotas.endsWith("ούμαι") -> {
            stem = enestotas.dropLast(5)
            verbType = "Γ4"
        }
        enestotas.endsWith("είμαι") -> {
            stem = enestotas.dropLast(5)
            verbType = "Γ5"
        }
    }

    return when (verbType) {
        "A1" -> listOf("ω",     "εις",   "ει",     "ουμε",      "ετε",  "ουν"     ).map { stem + it }
        "A2" -> listOf("ω",     "ς",     "ει",     "με",        "τε",   "ν(ε)"    ).mapIndexed { idx, it -> (if (oneSyllable && idx == 1) stem.unStress() else stem) + it }
        "B1" -> listOf("άω(ώ)", "άς",    "άει(ά)", "άμε(ούμε)", "άτε",  "άνε(ούν)").map { stem + it }
        "B2" -> listOf("ώ",     "είς",   "εί",     "ούμε",      "είτε",  "ούν"    ).map { stem + it }
        "B3" -> listOf("ώ",     "άς",    "ά",      "ούμε",      "άτε",   "ούν"    ).map { stem + it }
        "B4" -> listOf("ω",     "εις",   "ει",     "ούμε",      "είτε",  "ουν"    ).map { stem + it }
        "Γ1" -> listOf("ομαι",  "εσαι",  "εται",   "όμαστε",    "εστε",  "ονται"  ).mapIndexed { idx, it -> (if (idx == 3) stem.unStress() else stem) + it }
        "Γ2" -> listOf("άμαι",  "άσαι",  "άται",   "όμαστε",    "άστε",  "ούνται" ).map { stem + it }
        "Γ3" -> listOf("ιέμαι", "ιέσαι", "ιέται",  "ιόμαστε",   "ιέστε", "ιούνται").map { stem + it }
        "Γ4" -> listOf("ούμαι", "είσαι", "είται",  "ούμαστε",   "είστε", "ούνται" ).map { stem + it }
        "Γ5" -> listOf("είμαι", "είσαι", "είναι",  "είμαστε",   "είστε", "είναι"  ).map { stem + it }
        else -> emptyList()
    }
}

fun conjugateMellontas(textGreek: String): List<String> {
    val verbType: String
    val mellontas = getMellontas(textGreek)
    if (mellontas.isEmpty()) return emptyList()

    val stem = mellontas.dropLast(1)

    verbType = when {
        mellontas == "είμαι" -> "very irregular"
        mellontas in setOf("φάω", "πάω") -> "irregular2"
        mellontas in setOf("πιω", "δω", "βρω", "πω", "μπω", "βγω") -> "irregular3"
        mellontas.endsWith("ω") -> "regular"
        mellontas.endsWith("ώ") -> "irregular1"
        else -> "Werkwoordvorm onbekend"
    }

    return when (verbType) {
        "regular"        -> listOf("ω",     "εις",   "ει",    "ουμε",    "ετε",   "ουν").map { "θα $stem$it" }
        "irregular1"     -> listOf("ώ",     "είς",   "εί",    "ούμε",    "είτε",  "ούν").map { "θα $stem$it" }
        "irregular2"     -> listOf("ω",     "ς",     "ει",    "με",      "τε",    "νε").mapIndexed { idx, it -> "θα " + (if (idx == 1) stem.unStress() else stem) + it }
        "irregular3"     -> listOf("ω",     "εις",   "ει",    "ούμε",    "είτε",  "ουν").map { "θα $stem$it" }
        "very irregular" -> listOf("είμαι", "είσαι", "είναι", "είμαστε", "είστε", "είναι").map { "θα $it" }
        else -> emptyList()
    }
}

fun conjugateAoristos(textGreek: String): List<String> {
    val mellontas = getMellontas(textGreek)
    val aoristos = getAorist(textGreek)
    var stemPlural:String

    if (aoristos.isEmpty() || mellontas.isEmpty() )  return emptyList()

    //irregular
    if (aoristos.endsWith("ε"))  return listOf("not 1st person","","","","","")
    if (aoristos == "ήμουν")  return listOf("ήμουν", "ήσουν", "ήταν", "ήμασταν", "ήσασταν", "ήταν")

    /* STEM for conjugations EXCEPT for 1st and 2nd person plural*/
    val stemSingle = aoristos.dropLast(1)

    /* STEM for conjugations of 1st and 2nd person PLURAL */
    stemPlural = when {
        // exceptions go here:
        aoristos in listOf("βγήκα", "είδα", "βρήκα", "μπήκα", "ήρθα", "είπα", "πήγα", "ήπια", "υπήρξα") ->  stemSingle
        aoristos.endsWith("είχα") ->  stemSingle // werkwoorden afgeleid van έχω
        aoristos.endsWith("ήλθα") ->  stemSingle // werkwoorden afgeleid van έρχομαι
        aoristos.endsWith("πήρα") ->  stemSingle // werkwoorden afgeleid van παίρνω
        else -> {
            val stemFromFuture = mellontas.dropLast(1)
            if (mellontas.last() == 'ώ') stemFromFuture + "ήκ" else stemFromFuture
        }
    }
    if (stemPlural.last() in("αά") ) stemPlural += 'γ' // φά-γ-αμε
    return listOf("α", "ες", "ε", "αμε", "ατε", "αν").mapIndexed { idx, it -> (if (idx in 3..4) stemPlural else stemSingle) + it }
}

fun conjugateParatatikos(textGreek: String): List<String>{
    val stemSingle: String
    var stemPlural: String
    val enestotas = getEnestotas(textGreek)
    val paratatikos = getParatatikos(textGreek)
    if (paratatikos.isEmpty()) return emptyList()

    return when {
        //irregular
        enestotas == "είμαι"-> listOf("ήμουν","ήσουν","ήταν","ήμασταν","ήσασταν","ήταν")
        enestotas=="πάω" -> listOf("πήγαινα", "πήγαινες", "πήγαινε", "πηγαίναμε", "πηγαίνατε", "πήγαιναν")
        enestotas=="λέω" -> listOf("έλεγα", "έλεγες", "έλεγε", "λέγαμε", "λέγατε", "έλεγαν")
        enestotas=="υπάρχω" -> listOf("υπήρχα", "υπήρχες", "υπήρχε", "υπήρχαμε", "υπήρχατε", "υπήρχαν")

        paratatikos.endsWith("είχα") ||
        paratatikos.endsWith("ούσα") -> {
            stemSingle = paratatikos.dropLast(1)
            listOf("α", "ες", "ε", "αμε", "ατε", "αν").map{stemSingle + it}
        }

        paratatikos.endsWith("όμουν") -> {  //werkwoorden met tegenwoordige tijd op -άμαι, -όμαι, -έμαι, ...
            stemSingle = paratatikos.dropLast(5)
            listOf("όμουν", "όσουν", "όταν", "όμασταν", "όσασταν", "ονταν(ε)").mapIndexed{idx, it -> (if (idx==5) stemSingle.stressLastVowel() else stemSingle) + it}
        }

        enestotas.endsWith('ω') -> {             //werkwoorden op -ω bijvoorbeeld μαγειρεύω
            // stem for the single form is easy from the given paratatikos: μαγείρευα -> μαγείρευ
            stemSingle = paratatikos.dropLast(1)
            stemPlural = enestotas.dropLast(1)
            if (stemPlural.takeLast(2) !in setOf("εύ", "αύ")) {    // no 'γ' after  εύ or αύ
                if (stemPlural.last() in ("αάύώί")) stemPlural += 'γ' // φά-γ-αμε
            }
            listOf("α", "ες", "ε", "αμε", "ατε", "αν").mapIndexed { idx, it -> (if (idx in 3..4) stemPlural else stemSingle) + it }
        }
        else -> emptyList()
    }
}

fun createProstaktiki(textGreek: String): List<String> {
    val enestotas = getEnestotas(textGreek)
    val mellontas = getMellontas(textGreek)
    val aoristos = getAorist(textGreek)
    val stemAorist: String
    val prostaktiki: List<String>
    val prostaktikiSingle: String
    val prostaktikiPlural: String

    /***** EXCEPTIONS *****/
    prostaktiki = when (enestotas) {
        "πηγαίνω" -> listOf("πήγαινε","πηγαίνετε (πάτε)")
        "αφήνω" -> listOf("άσε/άφισε","άστε/αφήστε")
        "είμαι" -> listOf("να είσαι","να είστε")
        "έρχομαι" -> listOf("έλα","ελάτε")
        "ανεβαίνω" -> listOf("ανέβα","ανεβείτε")
        "κατεβαίνω" -> listOf("κατέβα","κατεβείτε")
        "μπαίνω" -> listOf("μπες","μπείτε")
        "βγαίνω" -> listOf("βγες","βγείτε")
        "βρίσκω" -> listOf("βρες","βρείτε")
        "λέω" -> listOf("πες","πείτε")
        "βλέπω" -> listOf("δες","δείτε")
        "πίνω" -> listOf("πιες","πιείτε")
        "επιτρέπομαι" -> listOf("επιτρέψου","επιτραπείτε")
        "κάθομαι" -> listOf("κάθισε/κάτσε","καθίστε")
        "προέρχομαι" -> listOf("πρόελθε","προέλθετε")
        "συνέρχομαι" -> listOf("σύνελθε","συνέλθετε")
        "φαίνομαι" -> listOf("alleen meervoud:","φανείτε")
        "χρωστάω" -> listOf("χρωστά","alleen enkelvoud")
        else -> emptyList()
    }
    if (prostaktiki.isNotEmpty()) return prostaktiki

    /***** PASSIVE FORM *****/
    if (aoristos.isNotEmpty() && aoristos.endsWith("ηκα")) {
        stemAorist = aoristos.dropLast(3)
        prostaktikiPlural = stemAorist.unStress() + "είτε"
        prostaktikiSingle = when {
            stemAorist.endsWith("θ") -> stemAorist.dropLast(1) + "σου"
            stemAorist.endsWith("στ") -> stemAorist.dropLast(2) + "σου"
            stemAorist.endsWith("χτ") -> stemAorist.dropLast(2) + "ξου"
            stemAorist.endsWith("φτ") -> stemAorist.dropLast(2) + "ψου"
            stemAorist.endsWith("υτ") -> stemAorist.dropLast(2) + "ψου"
            stemAorist.endsWith("εύτ") -> stemAorist.dropLast(3) + "έψου"
            stemAorist.endsWith("αύτ") -> stemAorist.dropLast(3) + "άψου"
            else -> "stem Aoristos niet op: θ,στ,χτ,φτ,υτ"
        }
        return listOf(prostaktikiSingle,prostaktikiPlural)
    }

    /***** ACTIVE FORM *****/
    if (mellontas.isEmpty() ) return listOf("Niet genoeg info voor vervoeging")

        // σημειώνω -> σημειώσω
        // χαίρω    -> χαιρώ
    /* remove final 'ω' from mellontas to create the 2nd stem */
    var stem = mellontas.dropLast(1)
         // σημειώσ
         // χαιρ
    /* If no accent in the stem then add accent to last vowel */
    var stressPos = stem.indexOfAny(stressedVowels)
        // σημειώσ
        //      ^---- stressPos = 5
        // χαιρ
        // ----     <- stressPos = -1
    if (stressPos == -1) {
        /* find last vowel */
        stressPos = stem.lastIndexOfAny(unstressedVowels)
            // χαιρ
            //   ^---- vowelPos = 2
        stem = stem.replace(atPosition = stressPos, replacement = stressOneChar(unStressed = stem[stressPos]))
            // χαιρ -> χαίρ
    }

    /* create single Imperative from the stem */
    var single = stem + "ε"
        // σημειώσε
        // χαίρε

    /* create plural Imperative from the stem, suffix depends on last character of the stem: '-ετε' or '-τε'  */
    val plural = if (stem.takeLast(1) in "νγβθχ") "${stem}ετε" else "${stem}τε"
        // σημειώστε
        // χαίρτε
    /* move stress 1 syllable to the left, if possible */
    if (stressPos > 0) {
        val dbl = stem.substring(stressPos - 1..stressPos)
        if (dbl in setOf("αύ", "εύ", "αί", "εί", "οί", "ού")) stressPos--
        val vowelPos = single.take(stressPos).lastIndexOfAny(unstressedVowels)
        if (vowelPos > -1) single = single.unStress().replace(atPosition = vowelPos, replacement = stressOneChar(unStressed = single[vowelPos]))
    }
    /* return Imperative as: 2nd person single - 2nd person plural */
    return listOf(single, plural)
}

fun colorToast(context: Context, msg: String, bgColor: Int = Color.DKGRAY, fgColor: Int = Color.WHITE, duration: Int = 0) {
    // create the normal Toast message
    val myToast = Toast.makeText(context, msg, if (duration == 0) Toast.LENGTH_SHORT else Toast.LENGTH_LONG)

    // create a reference to the view that holds this Toast
    val myView = myToast.view

    // create a reference to the text-view part of the Toast
    val myText = myView.findViewById(android.R.id.message) as TextView

    // change the text color
    myText.setTextColor(fgColor)

    // create background
    val gd = GradientDrawable().apply {
        setColor(bgColor)
        cornerRadius = 30f
        setStroke(5, Color.BLACK)
    }
    // set background to view
    myView.background = gd

    // and  finally ... show this differently colored Toast
    myToast.show()
}

object Utils {

    /* Method stressOneChar
     * input: exactly one(1) unstressed greek character.
     * output: same character with stress (tonos / accent).
     * if input not part o
     * f unstressed vowels it returns original input character. */
    fun stressOneChar(unStressed: Char): Char {
        val position = unstressedVowels.indexOf(unStressed)
        return if (position == -1) unStressed else stressedVowels[position]
    }

    /* Extension function unStressOneChar
     * input: exactly one(1) stressed greek character.
     * output: same character without stress (tonos / accent).
     * if input not part of stressed vowels it returns original input character. */
    fun unStressOneChar(target: Char): Char {
        val matchIndex = stressedVowels.indexOf(target)
        return if (matchIndex >= 0) unstressedVowels[matchIndex] else target
    }

    /* Extension function unStress
     * input: string containing one stressed greek character.
     * output: same string without stress (tonos / accent).
     * if input does not contain a stressed vowel it returns original input string. */
    fun String.unStress(): String {
        val stressPos = this.indexOfAny(stressedVowels)
        return if (stressPos == -1) this else this.replace(atPosition = stressPos, replacement = unStressOneChar(target = this[stressPos]))
    }

    /* Extension function replace character at index position */
    fun String.replace(atPosition: Int, replacement: Char): String {
        return if (atPosition < this.length && atPosition >= 0) this.take(atPosition) + replacement + this.drop(atPosition + 1) else this
    }

    /* Extension function normalize
     * input: string containing any diacritic or accented vowel.
     * output: same string without diacritic or ather marks. */
    private val diacriticVowels  = "άέήίϊΐόύϋΰώ".toCharArray()
    private val normalizedVowels = "αεηιιιουυυω".toCharArray()

    fun String.normalize(): String {
        val diacritics = diacriticVowels + 'ς'     // ς normalized to
        val normalized = normalizedVowels + 'σ'    // σ just for hangman purpose !!!

        return this.map {
            val position = diacritics.indexOf(it)
            if (position == -1) it else normalized[position]
        }.joinToString("")
    }

    fun View.enable(isEnabled: Boolean) {
        alpha = if (isEnabled) 1f else 0.3f   // high transparency looks like greyed out
        isClickable = isEnabled
    }

    fun View.visible(isVisible: Boolean = true) {
        visibility = if (isVisible) View.VISIBLE else View.INVISIBLE
    }

    /* extension function for Views: toggles visibility on/off */
    fun View.toggleVisibility() {
        /*  android ui View visibility is NOT a boolean!
         *  It can be one of 3 values: View.VISIBLE, View.INVISIBLE,  View.GONE
         *  this extension function  toggles between visible and invisible */
        visibility = if (visibility == View.VISIBLE) View.INVISIBLE else View.VISIBLE
    }

    /* extension function puts tonos on the last vowel of a greek stem (or word)
    * input:  string of Greek characters
    * output: same string but with accent on last vowel
    * output if no vowel found: original string. */
    fun String.stressLastVowel(): String {
        // remove all stress
        val unstressed = this.unStress()
        val vowelPos = unstressed.lastIndexOfAny(unstressedVowels)
        return if (vowelPos == -1) this else unstressed.replace(atPosition = vowelPos, replacement = stressOneChar(unstressed[vowelPos]))
    }
}