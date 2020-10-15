package driemondglas.nl.zorba

import android.content.Context
import android.graphics.Color
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.TextView
import android.widget.Toast
import driemondglas.nl.zorba.Utils.replace
import driemondglas.nl.zorba.Utils.stressOneChar
import driemondglas.nl.zorba.Utils.unStress
import driemondglas.nl.zorba.Utils.unStressOneChar

const val allConsonants = "βγδζθκλμνξπρσςτφχψ"
val allStressedVowels = "άέήίΐόύΰώ".toCharArray()
val allUnstressedVowels = "αεηιϊουϋω".toCharArray()

val allDiacriticVowels = "άέήίϊΐόύϋΰώ".toCharArray()
val allNormalizedVowels = "αεηιιιουυυω".toCharArray()

val allDoubleVowels = listOf("ευ", "αυ", "ου", "ει", "οι", "αι", "ια")
val allTripleVowels = listOf("οια")


val articleRegex = """(.*?),\s(τ?[ηοα]ι?)""".toRegex()
val adjectiveRegex = """(.*?)([οηόύή][ςιί]),\s?-(ε?[αάεέηήιί][αάς]?),\s?-([άαεέίοόύ]ς?)""".toRegex()   //βαρύς, -εία, -ύ
val inBracketsRegex = """\(.*?\)""".toRegex()
val firstWordRegex = """^[\p{InGreek}]*""".toRegex()
val eeStartSound = """\b[οε]?[ιηυίήύ]""".toRegex()

/* function is called from the menu item 'Clear All' to reset all selections made by user. */
fun clearAll() {
    wordGroup = ""
    wordType = ""
    search = ""
    zorbaPreferences.edit()
          .putString("wordgroup", "")
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
          .putBoolean("orderdecending", orderDescending)
          .putInt("jumpthreshold", jumpThreshold)
          .putBoolean("hidejumpers", hideJumpers)
          .apply()
}

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
            /* uses articleRegex to match greek articles , ο, η, το, οι, τα
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

            /* 2: change greek questionmark ";" to normal "?" for speech */
            //result = result.replaceFirst(";", "?")

            /* 3: Change space dash space to comma and newline for speech to force pause (gebiedende wijs) */
            result = result.replace(" - ", ", ")

            /* 4: Change equeals sign (=) to comma for speech to force pause */
            result = result.replace("=", ",")
        }
    }
    //    Log.d(TAG, "clean speech result: $result")
    zorbaSpeaks.speak(result, TextToSpeech.QUEUE_FLUSH, null, "")
}

/* This function returns all the greek characters from top line of the text until non greek is found */
fun getEnestotas(textGreek: String): String {
    return firstWordRegex.find(input = textGreek)?.value ?: ""
}

/* This function returns all the greek characters from the second line of the text until non greek is found */
fun getMellontas(textGreek: String): String {
    val splitLines = textGreek.lines()
    return if (splitLines.size > 1) firstWordRegex.find(splitLines[1])?.value ?: "" else ""
}

/* This function returns all the greek characters from the third line of the text until non greek is found */
fun getAorist(textGreek: String): String {
    val splitLines = textGreek.lines()
    return if (splitLines.size > 2) firstWordRegex.find(splitLines[2])?.value ?: "" else ""
}

/* This function returns all the greek characters from the fourth line of the text until non greek is found */
fun getParatatikos(textGreek: String): String {
    val splitLines = textGreek.lines()
    return if (splitLines.size > 3) firstWordRegex.find(splitLines[3])?.value ?: "" else ""
}

fun hasMellontas(textGreek: String): Boolean {
    return Regex("""(.*\R)\p{InGREEK}+(.*\R)*.*""").matches(textGreek)
}

fun hasAorist(textGreek: String): Boolean {
    return Regex("""(.*\R){2}\p{InGREEK}+(.*\R)*.*""").matches(textGreek)
}

fun hasParatatikos(textGreek: String): Boolean {
    return Regex("""(.*\R){3}\p{InGREEK}+(.*\R)*.*""").matches(textGreek)
}

fun conjugateEnestotas(textGreek: String): String {
    var stem = ""
    var verbType = ""
    var oneSyllable = false
    val enestotas = getEnestotas(textGreek)
    if (enestotas.isNotEmpty()) {
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
            "εγκαθιστώ" -> {
                stem = "εγκαθιστ"
                verbType = "B3"
            }
            else -> {
                when {
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
            "A1" -> "${stem}ω, ${stem}εις, ${stem}ει, ${stem}ουμε, ${stem}ετε, ${stem}ουν"
            "A2" -> "${stem}ω, ${if (oneSyllable) stem.unStress() else stem}ς, ${stem}ει, ${stem}με, ${stem}τε, ${stem}νε"
            "B1" -> "${stem}άω(ώ), ${stem}άς, ${stem}άει(ά), ${stem}άμε(ούμε), ${stem}άτε, ${stem}άνε(ούν)"
            "B2" -> "${stem}ώ, ${stem}είς, ${stem}εί, ${stem}ούμε, ${stem}είτε, ${stem}ούν"
            "B3" -> "${stem}ώ, ${stem}άς, ${stem}ά, ${stem}ούμε, ${stem}άτε, ${stem}ούν"
            "B4" -> "${stem}ω, ${stem}εις, ${stem}ει, ${stem}ούμε, ${stem}είτε, ${stem}ουν"
            "Γ1" -> "${stem}ομαι, ${stem}εσαι, ${stem}εται, ${stem.unStress()}όμαστε, ${stem}εστε, ${stem}ονται"
            "Γ2" -> "${stem}άμαι, ${stem}άσαι, ${stem}άται, ${stem}όμαστε, ${stem}άστε, ${stem}ούνται"
            "Γ3" -> "${stem}ιέμαι, ${stem}ιέσαι, ${stem}ιέται, ${stem}ιόμαστε, ${stem}ιέστε, ${stem}ιούνται"
            "Γ4" -> "${stem}ούμαι, ${stem}είσαι, ${stem}είται, ${stem}ούμαστε, ${stem}είστε, ${stem}ούνται"
            "Γ5" -> "${stem}είμαι, ${stem}είσαι, ${stem}είναι, ${stem}είμαστε, ${stem}είστε, ${stem}είναι"
            else -> "Werkwoordvorm onbekend"
        }
    }
    return "Tegenwoordige tijd ontbreekt op regel 1"
}

fun conjugateMellontas(textGreek: String): String {
    val stem: String
    val verbType: String
    val mellontas = getMellontas(textGreek)

    if (mellontas.isNotEmpty()) {
        when (mellontas) {
            "είμαι" -> {
                verbType = "irregular4"
                stem = "εί"
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
            "regular" -> "θα ${stem}ω, θα ${stem}εις, θα ${stem}ει, θα ${stem}ουμε, θα ${stem}ετε, θα ${stem}ουν"
            "irregular1" -> "θα ${stem}ώ, θα ${stem}είς, θα ${stem}εί, θα ${stem}ούμε, θα ${stem}είτε, θα ${stem}ούν"
            "irregular2" -> "θα ${stem}ω, θα ${stem.unStress()}ς, θα ${stem}ει, θα ${stem}με, θα ${stem}τε, θα ${stem}νε"
            "irregular3" -> "θα ${stem}ω, θα ${stem}εις, θα ${stem}ει, θα ${stem}ουμε, θα ${stem}είτε, θα ${stem}ουν"
            "irregular4" -> "θα ${stem}μαι, θα ${stem}σαι, θα ${stem}ναι, θα ${stem}μαστε, θα ${stem}στε, θα ${stem}ναι"
            else -> "Werkwoordvorm onbekend"
        }
    }
    return "Toekomende tijd ontbreekt op regel 2"
}

fun conjugateAorist(textGreek: String): String {
    val stemSingle: String
    var stemPlural: String
    val enestotas = getEnestotas(textGreek)
    val mellontas = getMellontas(textGreek)
    val aorist = getAorist(textGreek)

    if (aorist.isNotEmpty()) {
        //irregular
        if (aorist == "ήμουν") {
            return "ήμουν, ήσουν, ήταν, ήμασταν, ήσασταν, ήταν"
        }
        stemSingle = aorist.dropLast(1)
        when {
            // exceptions go here:
            aorist in listOf("βγήκα", "είδα", "βρήκα", "μπήκα", "ήρθα", "είπα", "πήγα", "ήπια", "υπήρξα") -> stemPlural = stemSingle
            aorist.endsWith("είχα") -> stemPlural = stemSingle // werkwoorden afgeleid van έχω
            aorist.endsWith("ήλθα") -> stemPlural = stemSingle // werkwoorden afgeleid van έρχομαι
            aorist.endsWith("πήρα") -> stemPlural = stemSingle // werkwoorden afgeleid van παίρνω
            enestotas.endsWith("ταΐζω") -> stemPlural = "ταΐσ" // trema komt niet voor in aoristos dus afkijken bij enestotas

            else -> {
                /* The code below formats the stem for 1st and 2nd person plural,
                 * having shifted accent and possible extra prefix ή or έ removed: */
                val targetCharacter: Char
                val newStressPos: Int

                // 1 - find and remember the position of the stressed character in the normal stem:
                val stressPos = stemSingle.indexOfAny(allStressedVowels)

                // 2 - unstress the stem (to stemPlural, we still need single stem later)
                stemPlural = stemSingle.unStress()

                // 3 - find target vowel (receiving new stress) past the original stressed vowel
                val vowelPos: Int
                //     first check for triple vowels; search past original stress.
                val tripleVowelPos = stemSingle.indexOfAny(allTripleVowels, stressPos + 1)
                //     first check for double vowels; search past original stress.
                val doubleVowelPos = stemSingle.indexOfAny(allDoubleVowels, stressPos + 1)
                when {
                    tripleVowelPos > -1 -> {  // if triple vowel, last one gets the accent, see wich character there is at that position
                        targetCharacter = stemSingle[tripleVowelPos + 2]
                        newStressPos = tripleVowelPos + 2
                    }
                    doubleVowelPos > -1 -> {  // if double vowel, second one gets the accent, see wich character there is at that position
                        targetCharacter = stemSingle[doubleVowelPos + 1]
                        newStressPos = doubleVowelPos + 1
                    }
                    else -> {
                        // 4 - no doubles found, so first single vowel gets the accent
                        vowelPos = stemSingle.indexOfAny(allUnstressedVowels, stressPos + 1)
                        targetCharacter = stemSingle[vowelPos]
                        newStressPos = vowelPos
                    }
                }

                // 5 - replace target character with accented one
                stemPlural = stemPlural.replace(atPosition = newStressPos, replacement = stressOneChar(targetCharacter))

                // 6 - if needed, lose the extraneous ή or έ at the start
                if (aorist[0] == 'ή' || aorist[0] == 'έ') {
                    /* is the 1st character added as 3rd syllable or was it already part of the verb ??? */
                    if (unStressOneChar(enestotas[0]) != unStressOneChar(aorist[0])) {   // ελέγχω  ->  έλεγξα, ελέγχαμε, ελέγχατε So keep the ε in plural (it was not added)
                        // ελπίζω  ->  έλπισα, ελπίσαμε ...
                        stemPlural = stemPlural.drop(1)
                    }
                }

                /* step 7 can only be done if mellontas is available for comparison */
                if (mellontas.isNotEmpty()) {
                    // 7 - Special case:
                    // Sometimes an έ is inserted not at the beginning af a composite verb like: αναπνέω
                    // Replace alternate έ after prefix with original vowel like: αναπνέω -> αναπνεύσω -> ανέπνευσα maar: αναπνεύσαμε en αναπνεύσατε
                    // Find stressed epsilon (έ) but not on position 1, save position
                    // put back original vowel (α) from same position in present tense

                    val positionOfStressedE = stemSingle.indexOf('έ', 1)

                    if (positionOfStressedE > -1) {
                        // If the original verb has a consonant on that position, then jou just remove the accented epsilon (replace by nothing)
                        stemPlural = if (mellontas[positionOfStressedE] in allConsonants) {
                            stemPlural.removeRange(positionOfStressedE, positionOfStressedE + 1)
                        } else {
                            // put back original vowel from same position in future tense
                            stemPlural.replace(atPosition = positionOfStressedE, replacement = mellontas[positionOfStressedE])
                        }
                    }
                }
            }
        }
        return "${stemSingle}α, ${stemSingle}ες, ${stemSingle}ε, ${stemPlural}αμε, ${stemPlural}ατε, ${stemSingle}αν"
    } else return "Verleden tijd ontbreekt op regel 3"
}

fun conjugateParatatikos(textGreek: String): String {  //past continuous (imperfect)
    val stemSingle: String
    var stemPlural: String
    val stem3pmv: String
    val enestotas = getEnestotas(textGreek)
    val mellontas = getMellontas(textGreek)
    val paratatikos = getParatatikos(textGreek)


    return when {
        paratatikos.isEmpty() -> "Onvoltooid Verleden Tijd ontbreekt op lijn 4"

        paratatikos.endsWith("ούσα") -> {  //werkwoorden op -άω en -ώ
            stemSingle = paratatikos.dropLast(4)
            "${stemSingle}ούσα, ${stemSingle}ούσες, ${stemSingle}ούσε, ${stemSingle}ούσαμε, ${stemSingle}ούσατε, ${stemSingle}ούσαν"
        }

        paratatikos.endsWith("όμουν") -> {  //werkwoorden op -όμαι of -έμαι
            stemSingle = paratatikos.dropLast(5)
            stem3pmv = when {
                enestotas.endsWith("ομαι") -> enestotas.dropLast(4)
                enestotas.endsWith("άμαι") -> enestotas.dropLast(4)
                enestotas.endsWith("ιέμαι") -> enestotas.dropLast(5)
                enestotas.endsWith("ούμαι") -> enestotas.dropLast(5)
                enestotas.endsWith("είμαι") -> enestotas.dropLast(5)
                else -> "?-"
            }
            "$paratatikos, ${stemSingle}όσουν, ${stemSingle}όταν, ${stemSingle}όμαστε, ${stemSingle}όσαστε, ${stem3pmv}ονταν"
        }

        enestotas.endsWith('ω') -> {             //werkwoorden op -ω bijvoorbeeld μαγειρεύω
            // stem for the single form is easy from the given paratatikos: μαγείρευα -> μαγείρευ
            stemSingle = paratatikos.dropLast(1)

            //stem for 1st and 2nd person plural, having shifted accent and possible extraeneous prefix ή or έ:
            val charTarget: Char
            val newStressPos: Int
            val vowelPos: Int
            // 1 - find and remember the position of the stress:
            // μαγείρευ -> 4
            val stressPos = stemSingle.indexOfAny(allStressedVowels)

            // 2 - unstress to stemPlural  (we need single stem later)
            // μαγείρευ -> μαγειρευ
            stemPlural = stemSingle.unStress()

            // 3 - find target vowel after the original stress, first check for double vowels; search after original stress.

            val doubleVowelPos = stemSingle.indexOfAny(allDoubleVowels, stressPos + 1)
            // in example μαγείρευ  find position of ευ -> 7

            if (doubleVowelPos > -1) {  // if double vowel, second one gets the accent, see wich character there is at that position
                charTarget = stemSingle[doubleVowelPos + 1] // υ in position 7+1
                newStressPos = doubleVowelPos + 1           // position 8 gets new stress
            } else {
                // 4 - no doubles found so first single vowel gets the accent
                vowelPos = stemSingle.indexOfAny(allUnstressedVowels, stressPos + 1)
                charTarget = stemSingle[vowelPos]
                newStressPos = vowelPos
            }

            // 5 - replace target character with accented one
            // in example, replace  υ with ύ on pos 8
            stemPlural = stemPlural.replace(atPosition = newStressPos, replacement = stressOneChar(charTarget))

            // 6 - if needed, lose the extraneous ή or έ at the start
            if (unStressOneChar(enestotas[0]) != unStressOneChar(paratatikos[0])) stemPlural = stemPlural.drop(1)

            /* step 7 can only be done if mellontas is available for comparison */
            if (mellontas.isNotEmpty()) {
                // 7 - Special case: replace alternate έ after prefix with original vowel like: αναπνέω -> αναπνεύσω -> ανέπνευσα maar: αναπνεύσαμε en αναπνεύσατε
                //  Find stressed epsilon (έ) but not on position 0, save position
                //  put back original vowel (α) from same position in present tense
                val positionOfStressedE = stemSingle.indexOf('έ', 1)
                if (positionOfStressedE > -1) stemPlural = stemPlural.replace(atPosition = positionOfStressedE, replacement = mellontas[positionOfStressedE])
            }
            "${stemSingle}α, ${stemSingle}ες, ${stemSingle}ε, ${stemPlural}αμε, ${stemPlural}ατε, ${stemSingle}αν"
        }
        else -> "Geen vervoeging gevonden voor $paratatikos"
    }
}

fun createProstaktiki(textGreek: String): String {
    val enestotas = getEnestotas(textGreek)
    val mellontas = getMellontas(textGreek)
    val aoristos = getAorist(textGreek)
    val stemAorist: String
    val prostaktiki: String
    val prostaktikiSingle: String
    val prostaktikiPlural: String

    if (mellontas.isEmpty()) return "Geen mellontas in GR."

    /***** EXCEPTIONS *****/
    prostaktiki = when (enestotas) {
        "ανεβαίνω" -> "ανέβα – ανεβείτε"
        "αφήνω" -> "άσε/άφισε – άστε/αφήστε"
        "βγαίνω" -> "βγες – βγείτε"
        "βλέπω" -> "δες – δείτε"
        "βρίσκω" -> "βρες – βρείτε"
        "γίνομαι" -> "γίνε – γίνετε"
        "είμαι" -> "να είσαι – να είστε"
        "επιτρέπομαι" -> "επιτρέψου - επιτραπείτε"
        "έρχομαι" -> "έλα – ελάτε"
        "κάθομαι" -> "κάθισε/κάτσε – καθίστε"
        "κατεβαίνω" -> "κατέβα – κατεβείτε"
        "λέω" -> "πες – πείτε"
        "μπαίνω" -> "μπες - μπείτε"
        "πηγαίνω" -> "πήγαινε - πηγαίνετε"
        "προέρχομαι" -> "πρόελθε - προέλθετε"
        "πίνω" -> "πιες – πιείτε"
        "συνέρχομαι" -> "σύνελθε - συνέλθετε"
        "τρώω" -> "φάε - φάτε"
        "φαίνομαι" -> "alleen meervoud: φανείτε"
        "χρωστάω" -> "alleen enkelvoud: χρωστά"
        else -> ""
    }
    if (prostaktiki.isNotEmpty()) return prostaktiki

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
        return "$prostaktikiSingle - $prostaktikiPlural"
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
    val plural = if (stem.takeLast(1) in listOf("ν", "γ", "β", "θ", "χ")) stem + "ετε" else stem + "τε"

    /* move stress 1 syllable to the left, if possible */
    if (stressPos > 0) {
        val vowelPos = single.take(stressPos - 1).lastIndexOfAny(allUnstressedVowels)  // stressPos minus one to deal with double vowels
        if (vowelPos > -1) single = single.unStress().replace(atPosition = vowelPos, replacement = stressOneChar(unStressed = single[vowelPos]))
    }
    /* return Imperative as: 2nd person single - 2nd person plural */
    return "$single - $plural"
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
    fun String.normalize(): String {
        val original = allDiacriticVowels + 'ς'     //  ς normalized to σ just for hangman purpose !!!
        val normalized = allNormalizedVowels + 'σ'

        return this.map {
            val index = original.indexOf(it)
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
}