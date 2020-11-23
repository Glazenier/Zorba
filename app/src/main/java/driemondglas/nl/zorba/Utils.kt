package driemondglas.nl.zorba

import android.content.Context
import android.graphics.Color
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.TextView
import android.widget.Toast
import driemondglas.nl.zorba.Utils.replace
import driemondglas.nl.zorba.Utils.stressLastVowel
import driemondglas.nl.zorba.Utils.stressOneChar
import driemondglas.nl.zorba.Utils.unStress
import driemondglas.nl.zorba.Utils.unStressOneChar

const val allConsonants = "βγδζθκλμνξπρσςτφχψ"
val allStressedVowels = "άέήίΐόύΰώ".toCharArray()
val allUnstressedVowels = "αεηιϊουϋω".toCharArray()

val inBracketsRegex = Regex( """\(.*?\)""")

/* function is called from the menu item 'Clear All' to reset all selections made by user. */
fun clearAll() {
    thema = ""
    wordType = ""
    search = ""
    zorbaPreferences.edit()
          .putString("theme", "")
          .putString("wordtype", "")
          .apply()
    resetDetails()
}

fun resetDetails() {
    useBlocks = true
    blockSize = 20
    levelBasic = true
    levelAdvanced = true
    levelBallast = true
    useLength = false
    pureLemmaLength = 0
    initial = ""
    orderbyTag = "index"
    orderDescending = true
    jumpThreshold = 2
    hideJumpers = false
    flashed = false
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
          .apply()
}

val articleRegex = Regex("""(.*?),\s(τ?[ηοα]ι?)""")             //match greek articles after a komma: , ο, η, το, οι, τα
val adjectiveRegex = Regex("""(.*?)([οηόύή][ςιί]),\s?-(ε?[αάεέηήιί][αάς]?),\s?-([άαεέίοόύ]ς?)""")   //βαρύς, -εία, -ύ
val eeStartSound = Regex("""\b[οε]?[ιηυίήύ]""")

fun cleanSpeech(rawText: String, wordType: String) {
    var result = ""
    when (wordType) {
        "bijvoeglijk nw", "voornaamwoord", "telwoord" -> {
            /* uses adjectiveRegex to match most endings of greek adjectives, etc
             * contains capturing groups:
             *   matchResult.groups[0]: whole match
             *   matchResult.groups[1]: stem
             *   matchResult.groups[2]: male ending
             *   matchResult.groups[3]: female ending
             *   matchResult.groups[4]: neuter ending
             */

            /* multiple lines of related adjectives are possible, so assess all lines */
            rawText.lines().forEach {
                val matchResult = adjectiveRegex.find(it)
                result += if (matchResult != null) {
                    /* combine stem with endings */
                    val stem = matchResult.groups[1]?.value
                    stem + matchResult.groups[2]?.value + "," + stem + matchResult.groups[3]?.value + "," + stem + matchResult.groups[4]?.value + ","
                } else {
                    it.replace(inBracketsRegex, "") + ","
                }
            }
            result = result.dropLast(1)  // undo last added comma
        }
        "zelfstandig nw" -> {
            /* uses articleRegex to match greek articles: ο, η, το, οι, τα
               returns capturing groups:
                    * matchResult.groups[0]: whole match
                    * matchResult.groups[1]: noun
                    * matchResult.groups[2]: article */

            /* multiple lines of similar nouns are possible, so assess all lines */
            rawText.lines().forEach {
                val matchResult = articleRegex.find(it)
                if (matchResult != null) {
                    /* put article before noun */
                    val noun = matchResult.groups[1]?.value

                    var article = matchResult.groups[2]?.value
                    if (noun != null && article != null) {
                        /* if article ends with 'o' and noun begins with 'o', the speech engine combines to one 'o'
                        *  This is not what we want in this case
                        *  An extra ',' is inserted to ensure we hear two separate 'o's
                        *
                        *  Same thing for ee-sound (ie-klank)
                        */
                        if (unStressOneChar(noun.first()) == 'ο' && article.last() == 'ο') article += ','
                        if (eeStartSound.find(noun) != null && (article == "η" || article == "οι")) article += ','
                    }
                    result += "$article $noun,"
                } else {
                    result += "$it,"
                }
            }

            result = result.dropLast(1)  // undo last comma

        }
        "lidwoord" -> {
            result = rawText.replace(" ", ",")
        }
        else -> {
            /* Standard cleanup for other word types: */

            /* 1: In speech output we don't want text in brackets */
            result = rawText.replace(inBracketsRegex, "")

            /* 2: Change space dash space to comma for speech to force pause (gebiedende wijs) */
            result = result.replace(" - ", ", ")

            /* 3: Change equeals sign (=) to comma for speech to force pause */
            result = result.replace("=", ",")
        }
    }
    //    Log.d(TAG, "clean speech result: $result")
    zorbaSpeaks.speak(result, TextToSpeech.QUEUE_FLUSH, null, "")
}

/* This function returns all the greek characters from top line of the text until non greek is found */
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

fun buildHTMLtable(textGreek: String): String{
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

    var htmlText = "<table>"

    // top header row
    htmlText += "<tr><th style='border: 1px solid black; background-color:gold;' >ENESTOTAS</th><th style='border: 1px solid black; background-color:gold;'>MELLONTAS</th></tr>"
    // conjugations
    for (i in 0..5) {
        htmlText += "<tr><td>${ene[i]}</td><td>${mel[i]}</td></tr>"
    }

    // middle header row
    htmlText += "<tr><th style='border: 1px solid black; background-color:gold;'>PARATATIKOS</th><th style='border: 1px solid black; background-color:gold;'>AORISTOS</th></tr>"
    for (i in 0..5) {
        htmlText += "<tr><td>${par[i]}</td><td>${aor[i]}</td></tr>"
    }

    // imperatief προστακτική
    htmlText += "<tr><th colspan=2 style='border: 1px solid black; background-color:gold;' >PROSTAKTIKI</th></tr>"
    htmlText += "<tr><td>${pro[0]}</td><td>${pro[1]}</td></tr>"
    htmlText += "</table>"
    return htmlText
}



fun conjugateEnestotas(textGreek: String): List<String> {
    var stem = ""
    var verbType = ""
    var oneSyllable = false
    val enestotas = getEnestotas(textGreek)
    if (enestotas.isEmpty()) return listOf()

    when (enestotas) {
        "λέω", "πάω", "φταίω", "τρώω" -> {
            stem = enestotas.dropLast(1)
            verbType = "A2"
            oneSyllable = true //needed to remove stress from λές πάς, φταίς, τρώς, κτλ
        }
        "ζω" -> {
            stem = "ζ"
            verbType = "B4"
        }
        else -> {
            when {
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
                        stem.last() in allConsonants -> "A1"
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
        }
    }
    return when (verbType) {
        "A1" -> listOf("ω", "εις", "ει", "ουμε", "ετε", "ουν").map { stem + it }
        "A2" -> listOf("ω", "ς", "ει", "με", "τε", "νε").mapIndexed { idx, it -> (if (oneSyllable && idx == 1) stem.unStress() else stem) + it }
        "B1" -> listOf("άω(ώ)", "άς", "άει(ά)", "άμε(ούμε)", "άτε", "άνε(ούν)").map { stem + it }
        "B2" -> listOf("ώ", "είς", "εί", "ούμε", "είτε", "ούν").map { stem + it }
        "B3" -> listOf("ώ", "άς", "ά", "ούμε", "άτε", "ούν").map { stem + it }
        "B4" -> listOf("ω", "εις", "ει", "ούμε", "είτε", "ουν").map { stem + it }
        "Γ1" -> listOf("ομαι", "εσαι", "εται", "όμαστε", "εστε", "ονται").mapIndexed { idx, it -> (if (idx == 3) stem.unStress() else stem) + it }
        "Γ2" -> listOf("άμαι", "άσαι", "άται", "όμαστε", "άστε", "ούνται").map { stem + it }
        "Γ3" -> listOf("ιέμαι", "ιέσαι", "ιέται", "ιόμαστε", "ιέστε", "ιούνται").map { stem + it }
        "Γ4" -> listOf("ούμαι", "είσαι", "είται", "ούμαστε", "είστε", "ούνται").map { stem + it }
        "Γ5" -> listOf("είμαι", "είσαι", "είναι", "είμαστε", "είστε", "είναι").map { stem + it }
        else -> listOf()
    }
}

fun conjugateMellontas(textGreek: String): List<String> {
    val stem: String
    val verbType: String
    val mellontas = getMellontas(textGreek)
    if (mellontas.isEmpty()) return listOf()

    when (mellontas) {
        "είμαι" -> { // είμαι is the only(?) verb with mellontas not ending in ω or ώ
            return listOf("είμαι", "είσαι", "είναι", "είμαστε", "είστε", "είναι").map { "θα $it" }
        }
        "φάω", "πάω" -> {
            verbType = "irregular2"
            stem = mellontas.dropLast(1)
        }
        "πιω", "δω", "βρω", "πω", "μπω", "βγω" -> {
            verbType = "irregular3"
            stem = mellontas.dropLast(1)
        }
        else -> {
            verbType = when {
                mellontas.endsWith("ω") -> "regular"
                mellontas.endsWith("ώ") -> "irregular1"
                else -> "Werkwoordvorm onbekend"
            }
            stem = mellontas.dropLast(1)
        }
    }
    return when (verbType) {
        "regular" -> listOf("ω", "εις", "ει", "ουμε", "ετε", "ουν").map { "θα $stem$it" }
        "irregular1" -> listOf("ώ", "είς", "εί", "ούμε", "είτε", "ούν").map { "θα $stem$it" }
        "irregular2" -> listOf("ω", "ς", "ει", "με", "τε", "νε").mapIndexed { idx, it -> "θα " + (if (idx == 1) stem.unStress() else stem) + it }
        "irregular3" -> listOf("ω", "εις", "ει", "ούμε", "είτε", "ουν").map { "θα $stem$it" }
        else -> listOf()
    }
}

fun conjugateAoristos(textGreek: String): List<String> {
    val mellontas = getMellontas(textGreek)
    val aorist = getAorist(textGreek)
    var stemPlural:String
    if (aorist.isEmpty())  return listOf()

    //irregular
    if (aorist.endsWith("ε"))  return listOf("not 1st person","","","","","")
    if (aorist == "ήμουν")  return listOf("ήμουν", "ήσουν", "ήταν", "ήμασταν", "ήσασταν", "ήταν")

    /* STEM for conjugations EXCEPT for 1st and 2nd person plural*/
    val stemSingle = aorist.dropLast(1)

    /* STEM for conjugations of 1st and 2nd person PLURAL */
    stemPlural = when {
        // exceptions go here:
        aorist in listOf("βγήκα", "είδα", "βρήκα", "μπήκα", "ήρθα", "είπα", "πήγα", "ήπια", "υπήρξα") ->  stemSingle
        aorist.endsWith("είχα") ->  stemSingle // werkwoorden afgeleid van έχω
        aorist.endsWith("ήλθα") ->  stemSingle // werkwoorden afgeleid van έρχομαι
        aorist.endsWith("πήρα") ->  stemSingle // werkwoorden afgeleid van παίρνω
        else -> {
            val stemFromFuture = mellontas.dropLast(1)
            if (mellontas.last() == 'ώ') stemFromFuture + "ήκ" else stemFromFuture
        }
    }
    if (stemPlural.last() in("αά")) stemPlural += 'γ' // φά γ αμε
    return listOf("α", "ες", "ε", "αμε", "ατε", "αν").mapIndexed { idx, it -> (if (idx in 3..4) stemPlural else stemSingle) + it }
}

fun conjugateParatatikos(textGreek: String): List<String>{
    val stemSingle: String
    var stemPlural: String
    val enestotas = getEnestotas(textGreek)
    val paratatikos = getParatatikos(textGreek)
    if (paratatikos.isEmpty()) return listOf()

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
        else -> listOf()
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
        "πηγαίνω" -> listOf("πήγαινε","πηγαίνετε")
        "αφήνω" -> listOf("άσε/άφισε","άστε/αφήστε")
        "είμαι" -> listOf("να είσαι","να είστε")
        "έρχομαι" -> listOf("έλα","ελάτε")
        "τρώω" -> listOf("φάε","φάτε")
        "ανεβαίνω" -> listOf("ανέβα","ανεβείτε")
        "κατεβαίνω" -> listOf("κατέβα","κατεβείτε")
        "μπαίνω" -> listOf("μπες","μπείτε")
        "βγαίνω" -> listOf("βγες","βγείτε")
        "βρίσκω" -> listOf("βρες","βρείτε")
        "λέω" -> listOf("πες","πείτε")
        "βλέπω" -> listOf("δες","δείτε")
        "πίνω" -> listOf("πιες","πιείτε")
        "γίνομαι" -> listOf("γίνε","γίνετε")
        "επιτρέπομαι" -> listOf("επιτρέψου","επιτραπείτε")
        "κάθομαι" -> listOf("κάθισε/κάτσε","καθίστε")
        "προέρχομαι" -> listOf("πρόελθε","προέλθετε")
        "συνέρχομαι" -> listOf("σύνελθε","συνέλθετε")
        "φαίνομαι" -> listOf("alleen meervoud:","φανείτε")
        "χρωστάω" -> listOf("χρωστά","alleen enkelvoud")
        else -> emptyList()
    }
    if (prostaktiki.isNotEmpty()) return prostaktiki
    if (mellontas.isEmpty() || aoristos.isEmpty()) return listOf("Niet genoeg info voor vervoeging")

    /***** PASSIVE FORM *****/
    if (aoristos.endsWith("ηκα")) {
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
            else -> "stemAorist niet op: θ,στ,χτ,φτ,υτ"
        }
        return listOf(prostaktikiSingle,prostaktikiPlural)
    }

    /***** ACTIVE FORM *****/
    /* remove final 'ω' from mellontas to create the 2nd stem */
    var stem = mellontas.dropLast(1)

    /* If no accent in the stem then add accent to last vowel */
    var stressPos = stem.indexOfAny(allStressedVowels)
    if (stressPos == -1) {
        /* find last vowel */
        val vowelPos = stem.lastIndexOfAny(allUnstressedVowels)
        stem = stem.replace(atPosition = vowelPos, replacement = stressOneChar(unStressed = stem[vowelPos]))
        stressPos = vowelPos
    }

    /* create single Imperative from the stem */
    var single = stem + "ε"

    /* create plural Imperative from the stem, suffix depends on last character of the stem: '-ετε' or '-τε'  */
    val plural = if (stem.takeLast(1) in "νγβθχ") stem + "ετε" else stem + "τε"

    /* move stress 1 syllable to the left, if possible */
    if (stressPos > 0) {
        val vowelPos = single.take(stressPos - 1).lastIndexOfAny(allUnstressedVowels)  // stressPos minus one to deal with double vowels
        if (vowelPos > -1) single = single.unStress().replace(atPosition = vowelPos, replacement = stressOneChar(unStressed = single[vowelPos]))
    }
    /* return Imperative as: 2nd person single - 2nd person plural */
    return listOf(single,plural)
}


fun colorToast(context: Context, msg: String, bgColor: Int = Color.DKGRAY, fgColor: Int = Color.WHITE, duration: Int = 0) {
    /* create the normal Toast message */
    val myToast = Toast.makeText(context, msg, if (duration == 0) Toast.LENGTH_SHORT else Toast.LENGTH_LONG)

    /* create a reference to the view that holds this Toast */
    val myView = myToast.view

    /* create a reference to the text-view part of the Toast */
    val myText = myView.findViewById(android.R.id.message) as TextView

    /* change the text color */
    myText.setTextColor(fgColor)

    /* change background */
    myView.setBackgroundColor(bgColor)

    /* and  finally ... show this differently colored Toast */
    myToast.show()
}

object Utils {

    /* Extension function stressOneChar
     * input: exactly one(1) unstressed greek character.
     * output: same character with stress (tonos / accent).
     * if input not part of unstressed vowels it returns original input character. */
    fun stressOneChar(unStressed: Char): Char {
        val stressIndex = allUnstressedVowels.indexOf(unStressed)
        return if (stressIndex >= 0) allStressedVowels[stressIndex] else unStressed
    }

    /* Extension function unStressOneChar
     * input: exactly one(1) stressed greek character.
     * output: same character without stress (tonos / accent).
     * if input not part of stressed vowels it returns original input character. */
    fun unStressOneChar(target: Char): Char {
        val matchIndex = allStressedVowels.indexOf(target)
        return if (matchIndex >= 0) allUnstressedVowels[matchIndex] else target
    }

    /* Extension function unStress
     * input: string containing one stressed greek character.
     * output: same string without stress (tonos / accent).
     * if input does not contain a stressed vowel it returns original input string. */
    fun String.unStress(): String {
        val stressPos = this.indexOfAny(allStressedVowels)
        return if (stressPos > -1) this.replace(atPosition = stressPos, replacement = unStressOneChar(target = this[stressPos])) else this
    }

    /* Extension function replace character at index */
    fun String.replace(atPosition: Int, replacement: Char): String {
        return if (atPosition < length) take(atPosition) + replacement + drop(atPosition + 1) else this
    }

    /* Extension function normalize
     * input: string containing any diacritic or accented vowel.
     * output: same string without diacritic or ather marks.
     */
    private val allDiacriticVowels = "άέήίϊΐόύϋΰώ".toCharArray()
    private val allNormalizedVowels = "αεηιιιουυυω".toCharArray()

    fun String.normalize(): String {
        val diacritics = allDiacriticVowels + 'ς'     // ς normalized to
        val normalized = allNormalizedVowels + 'σ'    // σ just for hangman purpose !!!

        return this.map {
            val index = diacritics.indexOf(it)
            if (index >= 0) normalized[index] else it
        }.joinToString("")
    }

    fun View.enabled(isEnabled: Boolean) {
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

    /* extension function puts tonos on the last vowel of a greek stem (or word) */
    fun String.stressLastVowel(): String {
        /* indexOfAny finds from the start, but we need to find from the end, so: */
        val reverseThis = this.reversed().unStress()
        val vowelPos = reverseThis.indexOfAny(allUnstressedVowels)
        if (vowelPos==-1) return this
        val stressPos= this.length - vowelPos-1 // recalc from reversed word
        return this.replace(atPosition = stressPos, replacement = stressOneChar(this[stressPos]))
    }
}