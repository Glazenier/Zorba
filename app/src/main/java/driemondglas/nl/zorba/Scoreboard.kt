package driemondglas.nl.zorba

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableString
import android.text.style.*
import androidx.core.content.ContextCompat
import kotlin.math.absoluteValue

/* scoreboard keeps track of correctly anwered flashcards
 * scores are maintained in a hashmap linking lemma id to correct or incorrect count.
 * There are three(3) types of scores maintained:
 * 1) Individual Lemma score:
 *    Displayed as number of colored bars: green bars for correctly answered, red for incorrectly answered lemmas
 *    Scorecount > 3 displayed as three bars with + sign
 * 2) Block score:
 *    The score for the current block of lemma's is the sum of the individual scores (positive and negative).
 *    The running total is displayed as a single green bar extending over the top of the UI (just above NL flag)
 *    Only positive is shown. Runs to end of screen if all lemmas answered correctly to threshold count
 *    Reset each new block
 * 3) Session Score:
 *    The Session score is the overall score during the entire session from app start to app finish, unless reset manually.
 *    Current session score is presented as formatted spannable string in the form of:
 *    ___________________________
 *    |                         |
 *    | totaal: 6 fout: 3 = 50% |
 *    |_________________________|  Session Score runs until app activity finishes or by manual reset.
 */
object ScoreBoard {

    /* scoremap holds ids and correct(positive) or incorrect(negative) count per lemma in a block */
    private var scoreMap = HashMap<Int, Int>()

    private var correctCountSession = 0
    private var incorrectCountSession = 0
    private var prevCorrectCountSession = 0
    private var prevIncorrectCountSession = 0
    private var prevIdx = 0
    private var prevLemmaScore = 0

    /*  This is where the scoreboard receives each individual score */
    fun setScore(thisIdx: Int, isCorrect: Boolean) {
        // save old values for undo functionality
        prevCorrectCountSession = correctCountSession
        prevIncorrectCountSession = incorrectCountSession
        prevIdx = thisIdx
        prevLemmaScore = scoreMap.getOrDefault(thisIdx, 0)

        // update Session Score
        if (isCorrect) correctCountSession++ else incorrectCountSession++

        // update Individual lemma score
        scoreMap[thisIdx] = if (isCorrect) prevLemmaScore + 1 else prevLemmaScore - 1

        // Block score is calculated from the scoremap of individual scores
    }


    fun getSessionScore(): SpannableString {
        /* This function formats the score text as: 'totaal: 6 fout: 3 = 50%'
         * The total count, incorrect count and percentage correct get a larger font size.
         * For that purpose the string is divided into parts(spans) using SpannableString */
        val totaalCount = correctCountSession + incorrectCountSession
        val pctCorrect = if (totaalCount == 0) 100 else (100 * correctCountSession / totaalCount)
        val textScore = SpannableString(
            "goed: $correctCountSession totaal: $totaalCount  =  $pctCorrect %")
        //         |span1|     span2  |  span3 |   span4   |  5 |   span6  |sp7|
        val span1Length = "goed: ".length
        val span2Length = correctCountSession.toString().length
        val span3Length = " totaal: ".length
        val span4Length = totaalCount.toString().length

        var spanStart = span1Length
        var spanEnd = spanStart + span2Length
        with(textScore) {
            setSpan(RelativeSizeSpan(2f), spanStart, spanEnd, 0)
            setSpan(StyleSpan(Typeface.BOLD), spanStart, spanEnd, 0)

            spanStart = spanEnd + span3Length
            spanEnd = spanStart + span4Length
            setSpan(RelativeSizeSpan(2f), spanStart, spanEnd, 0)
            setSpan(StyleSpan(Typeface.BOLD), spanStart, spanEnd, 0)

            spanStart = textScore.length - pctCorrect.toString().length - 2
            spanEnd = textScore.length - 2
            setSpan(RelativeSizeSpan(1.5f), spanStart, spanEnd, 0)
            setSpan(StyleSpan(Typeface.BOLD), spanStart, spanEnd, 0)
        }
        return textScore
    }

    /* the progress bar shows the total correct count for the current block */
    fun getBlockScore(): Int = scoreMap.values.sum()

    /* show correct-incorrect count using a string of green or red bars */
    fun getLemmaScore(context: Context, thisIdx: Int): SpannableString {
        val correctCount = scoreMap.getOrDefault(thisIdx, 0)
        // set text color depending on negative or positive count
        val colorInt = if (correctCount > 0) R.color.colorPrimaryDark else R.color.colorAccent
        val scoreString = SpannableString(if (correctCount < -3) """✚▉▉▉""" else """▉""".repeat(correctCount.absoluteValue))
        scoreString.setSpan(ForegroundColorSpan(ContextCompat.getColor(context, colorInt)), 0, scoreString.length, 0)
        return scoreString
    }

    /* when user pauses (releases button outside it's borders) */
    fun undoLastScore() {
        correctCountSession = prevCorrectCountSession
        incorrectCountSession = prevIncorrectCountSession
        if (prevIdx > 0) scoreMap[prevIdx] = prevLemmaScore
    }

    fun resetScoreMap() {
        scoreMap.clear()
        prevIdx = 0
        prevLemmaScore = 0
    }

    fun resetAllScores() {
        resetScoreMap()
        correctCountSession = 0
        incorrectCountSession = 0
        prevCorrectCountSession = 0
        prevIncorrectCountSession = 0
    }

    private fun scoreMapMin() =  scoreMap.values.minOrNull() ?: 0
    private fun scoreMapMax() =  scoreMap.values.maxOrNull() ?: 0

    /*  Retrieve the lowest value in the score list
     *  If lowest value is higher than threshold then all entries are higher so all are correct. */
    fun allAreCorrect(): Boolean = (scoreMapMin() > jumpThreshold)

    /*  Prepare list for sql expression to add jumpers
     *  result is like  "(163, 1), (1816, 1), (1082, 1)" for 3 entries */
    fun scoreMapToString(): String = scoreMap.toList().toString().removeSurrounding("[", "]")

    fun noJumper(theIdx: Int): Boolean = (scoreMap.getOrDefault(theIdx, 0) <= jumpThreshold)

    /* all lemma's in block answered correctly 1x without any errors (100% score after one pass) */
    fun strike():Boolean = (useBlocks && scoreMap.size == blockSize &&  scoreMapMin() == 1 && scoreMapMax() == 1)
}

