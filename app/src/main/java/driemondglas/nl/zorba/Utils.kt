package driemondglas.nl.zorba

import android.content.Context
import android.graphics.Color
import android.speech.tts.TextToSpeech
import android.util.Log
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

val articleRegex = """(.*?),\s(τ?[ηοα]ι?)""".toRegex()
val adjectiveRegex = """(.*?)([οηόύή]ς),\s?-(ε?[ηήιίαά][αάς]?),\s?-([οόίύεέ]ς?)""".toRegex()
val inBracketsRegex = """\(.*?\)""".toRegex()
val firstWordRegex = """^[\p{InGreek}]*""".toRegex()

fun cleanSpeech(rawText: String, wordType: String) {
    var result = ""
    when (wordType) {
        "bijvoeglijk nw", "voornaamwoord", "telwoord" -> {
            /* uses adjectiveRegex to match most endings of greek adjectives
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
                    result += matchResult.groups[2]?.value + " " + matchResult.groups[1]?.value + ","
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
            result = result.replaceFirst(";", "?")

            /* 3: Change space dash space to comma and newline for speech to force pause (gebiedende wijs) */
            result = result.replace(" - ", ", ")

            /* 4: Change equeals sign (=) to comma for speech to force pause */
            result = result.replace("=", ",")
        }
    }
    Log.d("hvr", result)
    zorbaSpeaks.speak(result, TextToSpeech.QUEUE_FLUSH, null, "")
}

/* This function returns all the greek characters from top line of the text until non greek is found */
fun getEnestotas(textGreek: String): String {
    return firstWordRegex.find(textGreek)?.value ?: ""
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

fun conjureEnestotas(textGreek: String): String {
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
                        verbType = "C1"
                    }
                    enestotas.endsWith("άμαι") -> {
                        stem = enestotas.dropLast(4)
                        verbType = "C2"
                    }
                    enestotas.endsWith("ιέμαι") -> {
                        stem = enestotas.dropLast(5)
                        verbType = "C3"
                    }
                    enestotas.endsWith("ούμαι") -> {
                        stem = enestotas.dropLast(5)
                        verbType = "C4"
                    }
                    enestotas.endsWith("είμαι") -> {
                        stem = enestotas.dropLast(5)
                        verbType = "C5"
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
            "C1" -> "${stem}ομαι, ${stem}εσαι, ${stem}εται, ${stem.unStress()}όμαστε, ${stem}εστε, ${stem}ονται"
            "C2" -> "${stem}άμαι, ${stem}άσαι, ${stem}άται, ${stem}όμαστε, ${stem}άστε, ${stem}ούνται"
            "C3" -> "${stem}ιέμαι, ${stem}ιέσαι, ${stem}ιέται, ${stem}ιόμαστε, ${stem}ιέστε, ${stem}ιούνται"
            "C4" -> "${stem}ούμαι, ${stem}είσαι, ${stem}είται, ${stem}ούμαστε, ${stem}είστε, ${stem}ούνται"
            "C5" -> "${stem}είμαι, ${stem}είσαι, ${stem}είναι, ${stem}είμαστε, ${stem}είστε, ${stem}είναι"
            else -> "Werkwoordtype onbekend"
        }
    }
    return "Tegenwoordige tijd ontbreekt op regel 1"
}

fun conjureMellontas(textGreek: String): String {
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
                    else -> "unknown"
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
            else -> "Werkwoordtype onbekend"
        }
    }
    return "Toekomende tijd ontbreekt op regel 2"
}

fun conjureAorist(textGreek: String): String {
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
            else -> {
                /* The code below formats the stem for 1st and 2nd person plural,
                 * having shifted accent and possible extra prefix ή or έ removed:
                 */
                val targetCharacter: Char
                val newStressPos: Int

                // 1 - find and remember the position of the stressed character in the normal stem:
                val stressPos = stemSingle.indexOfAny(allStressedVowels)

                // 2 - unstress the stem (to stemPlural, we still need single stem later)
                stemPlural = stemSingle.unStress()

                // 3 - find target vowel (receiving new stress) past the original stressed vowel
                //     first check for double vowels; search past original stress.
                val doubleVowelPos = stemSingle.indexOfAny(allDoubleVowels, stressPos + 1)
                val vowelPos: Int
                if (doubleVowelPos > -1) {  // if double vowel, second one gets the accent, see wich character there is at that position
                    targetCharacter = stemSingle[doubleVowelPos + 1]
                    newStressPos = doubleVowelPos + 1
                } else {
                    // 4 - no doubles found, so first single vowel gets the accent
                    vowelPos = stemSingle.indexOfAny(allUnstressedVowels, stressPos + 1)
                    targetCharacter = stemSingle[vowelPos]
                    newStressPos = vowelPos
                }

                // 5 - replace target character with accented one
                stemPlural = stemPlural.replace(newStressPos, stressOneChar(targetCharacter))

                // 6 - if needed, lose the extraneous ή or έ at the start
                if (unStressOneChar(enestotas[0]) != unStressOneChar(aorist[0])) stemPlural = stemPlural.drop(1)

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
                            stemPlural.replace(positionOfStressedE, mellontas[positionOfStressedE])
                        }
                    }
                }
            }
        }
        return "${stemSingle}α, ${stemSingle}ες, ${stemSingle}ε, ${stemPlural}αμε, ${stemPlural}ατε, ${stemSingle}αν"
    } else return "Verleden tijd ontbreekt op regel 3"
}

fun conjureParatatikos(textGreek: String): String {
    val stemSingle: String
    var stemPlural: String
    val enestotas = getEnestotas(textGreek)
    val paratatikos = getParatatikos(textGreek)
    val mellontas = getMellontas(textGreek)

    if (paratatikos.isNotEmpty()) {

        when {
            paratatikos.endsWith("ούσα") -> {  //werkwoorden op -άω en -ώ
                stemSingle = paratatikos.dropLast(4)
                return "${stemSingle}ούσα, ${stemSingle}ούσες, ${stemSingle}ούσε, ${stemSingle}ούσαμε, ${stemSingle}ούσατε, ${stemSingle}ούσαν"
            }
            enestotas.endsWith('ω') -> { //werkwoorden op -ω

                stemSingle = paratatikos.dropLast(1)
                //stem for 1st and 2nd person plural, having shifted accent and possible extraeneous prefix ή or έ:
                val charTarget: Char
                val newStressPos: Int

                // 1 - find and remember the position of the stress:
                val stressPos = stemSingle.indexOfAny(allStressedVowels)

                // 2 - unstress to strStemPlural  (we need single stem later)
                stemPlural = stemSingle.unStress()

                // 3 - find target vowel after the original stress, first check for double vowels; search after original stress.
                val doubleVowelPos = stemSingle.indexOfAny(allDoubleVowels, stressPos + 1)
                val vowelPos: Int
                if (doubleVowelPos > -1) {  // if double vowel, second one gets the accent, see wich character there is at that position
                    charTarget = stemSingle[doubleVowelPos + 1]
                    newStressPos = doubleVowelPos + 1
                } else {
                    // 4 - no doubles found so first single vowel gets the accent
                    vowelPos = stemSingle.indexOfAny(allUnstressedVowels, stressPos + 1)
                    charTarget = stemSingle[vowelPos]
                    newStressPos = vowelPos
                }

                // 5 - replace target character with accented one
                stemPlural = stemPlural.replace(newStressPos, stressOneChar(charTarget))

                // 6 - if needed, lose the extraneous ή or έ at the start
                if (unStressOneChar(enestotas[0]) != unStressOneChar(paratatikos[0])) stemPlural = stemPlural.drop(1)

                /* step 7 can only be done if mellontas is available for comparison */
                if (mellontas.isNotEmpty()) {
                    // 7 - Special case: replace alternate έ after prefix with original vowel like: αναπνέω -> αναπνεύσω -> ανέπνευσα maar: αναπνεύσαμε en αναπνεύσατε
                    //  Find stressed epsilon (έ) but not on position 0, save position
                    //  put back original vowel (α) from same position in present tense
                    val positionOfStressedE = stemSingle.indexOf('έ', 1)
                    if (positionOfStressedE > -1) stemPlural = stemPlural.replace(positionOfStressedE, mellontas[positionOfStressedE])
                }
                return "${stemSingle}α, ${stemSingle}ες, ${stemSingle}ε, ${stemPlural}αμε, ${stemPlural}ατε, ${stemSingle}αν"
            }
            else -> return "Geen vervoeging gevonden voor $paratatikos"
        }
    }
    return "Onvoltooid Verleden Tijd ontbreekt op lijn 4"
}

fun createProstaktiki(textGreek: String): String {
    val mellontas = getMellontas(textGreek)
    val stem = mellontas.dropLast(1)
    var single = stem + "ε"
    val singleEndsWith = single.takeLast(2)
    val plural = if (singleEndsWith in listOf("νε", "γε", "βε", "θε")) stem + "ετε" else stem + "τε"
    val stressPos = single.indexOfAny(allStressedVowels)
    //find vowel position before stress
    val vowelPos = single.take(stressPos - 1).lastIndexOfAny(allUnstressedVowels)  // stressPos minus one to deal with double vowels
    if (vowelPos > -1) single = single.unStress().replace(vowelPos, stressOneChar(single[vowelPos]))
    return "$single - $plural"
}

object Utils {

    /* function stressOneChar
    * input: exactly one(1) unstressed greek character.
    * output: same character with stress (tonos / accent).
    * if input not part of unstressed vowels it returns original input character. */
    fun stressOneChar(unStressed: Char): Char {
        val stressIndex = allUnstressedVowels.indexOf(unStressed)
        return if (stressIndex >= 0) allStressedVowels[stressIndex] else unStressed
    }

    /* function unStressOneChar
     * input: exactly one(1) stressed greek character.
     * output: same character without stress (tonos / accent).
     * if input not part of stressed vowels it returns original input character. */
    fun unStressOneChar(stressed: Char): Char {
        val matchIndex = allStressedVowels.indexOf(stressed)
        return if (matchIndex >= 0) allUnstressedVowels[matchIndex] else stressed
    }

    /* Extension function unStress
     * input: string containing one stressed greek character.
     * output: same string without stress (tonos / accent).
     * if input not containing stressed vowel it returns original input string. */
    fun String.unStress(): String {
        val stressPos = this.indexOfAny(allStressedVowels)
        return if (stressPos > -1) this.replace(stressPos, unStressOneChar(this[stressPos])) else this
    }

    /* Extension function replace character at index */
    fun String.replace(idx: Int, replacement: Char): String {
        return if (idx < length) take(idx) + replacement + drop(idx + 1) else this
    }


    fun colorToast(context: Context, msg: String, bgColor: Int = Color.DKGRAY, fgColor: Int = Color.WHITE) {
        /* create the normal Toast message */
        val myToast = Toast.makeText(context, msg, Toast.LENGTH_SHORT)

        /* create a reference to the view that holds this Toast */
        val myView = myToast.view

        /* create a reference to the text-view part of the Toast */
        val myText = myView.findViewById(android.R.id.message) as TextView

        /* change the text color */
        myText.setTextColor(fgColor)

        /* change background while keeping original border */
//        myView.background.setColorFilter(bgColor, PorterDuff.Mode.SRC_IN)
        myView.setBackgroundColor(bgColor)

        /* and  finally ... show this alternatively colored Toast */
        myToast.show()
    }

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

    /* extension function for Views: toggles visibility on/off */
    fun View.toggleVisibility() {
        /*  android ui View visibility is NOT a boolean!
         *  It can be one of 3 values: View.VISIBLE, View.INVISIBLE,  View.GONE */
        val viewIsVisible = visibility == View.VISIBLE
        visibility = if (viewIsVisible) View.INVISIBLE else View.VISIBLE
    }
}