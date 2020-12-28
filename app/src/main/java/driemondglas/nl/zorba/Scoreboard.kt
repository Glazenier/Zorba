package driemondglas.nl.zorba

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import kotlin.math.absoluteValue

object ScoreBoard {

    /* scoremap holds ids and correct(positive) or incorrect(negative) count per lemma in a block */
    private var scoreMap = HashMap<Int, Int>()
    private var correctCount = 0
    private var incorrectCount = 0
    private var previousCorrectCount = 0
    private var previousIncorrectCount = 0
    private var previousIdx = 0
    private var previousLemmaScore = 0

    fun getSessionScore(): SpannableString {
        /* This function formats the score text as: 'totaal: 6 fout: 3 = 50%'
         * The total count, incorrect count and percentage correct get a larger font size.
         * For that purpose the string is divided into partitions using SpannableString */
        val totaalCount = correctCount + incorrectCount
        val pctCorrect = if (totaalCount == 0) 100 else (100 * correctCount / totaalCount)
        val textScore = SpannableString("goed: $correctCount totaal: $totaalCount  =  $pctCorrect %")
        /*                                     |span1 |     span2        |  span3 |      span4      |  5  |    span6      |sp7|  */
        val span1Length = "goed: ".length
        val span2Length = correctCount.toString().length
        val span3Length = " totaal: ".length
        val span4Length = totaalCount.toString().length

        var spanStart = span1Length
        var spanEnd = spanStart + span2Length
        textScore.setSpan(RelativeSizeSpan(3f), spanStart, spanEnd, 0)
        textScore.setSpan(StyleSpan(Typeface.BOLD), spanStart, spanEnd, 0)

        spanStart = spanEnd + span3Length
        spanEnd = spanStart + span4Length
        textScore.setSpan(RelativeSizeSpan(3f), spanStart, spanEnd, 0)
        textScore.setSpan(StyleSpan(Typeface.BOLD), spanStart, spanEnd, 0)

        spanStart = textScore.length - pctCorrect.toString().length - 2
        spanEnd = textScore.length - 2
        textScore.setSpan(RelativeSizeSpan(2f), spanStart, spanEnd, 0)
        textScore.setSpan(StyleSpan(Typeface.BOLD), spanStart, spanEnd, 0)

        return textScore
    }

    fun undoLastScore() {
        correctCount = previousCorrectCount
        incorrectCount = previousIncorrectCount
        if (previousIdx > 0) scoreMap[previousIdx] = previousLemmaScore
    }

    fun updateCounters(thisIdx: Int, isCorrect: Boolean) {
        /* save old values for undo functionality */
        previousCorrectCount = correctCount
        previousIncorrectCount = incorrectCount
        previousIdx = thisIdx
        previousLemmaScore = scoreMap.getOrDefault(thisIdx, 0)

        if (isCorrect) correctCount++ else incorrectCount++

        /* increase the correct or incorrect counter per individual lemma */
        scoreMap[thisIdx] = if (isCorrect) previousLemmaScore + 1 else previousLemmaScore - 1
    }

    /* show correct-incorrect count using a string of green or red rectangles */
    fun showLemmaScore(context: Context, thisIdx: Int): SpannableString {
        val correctCount = scoreMap.getOrDefault(thisIdx, 0)
        /* set text color depending on negative or positive count */
        val colorInt = if (correctCount > 0) R.color.colorPrimaryDark else R.color.colorAccent
        val textScore = SpannableString(if (correctCount < -3) """✚▉▉▉""" else """▉""".repeat(correctCount.absoluteValue))

        textScore.setSpan(ForegroundColorSpan(ContextCompat.getColor(context, colorInt)), 0, textScore.length, 0)
        return textScore
    }

    fun resetScoreMap() {
        scoreMap.clear()
        previousIdx = 0
        previousLemmaScore = 0
    }

    fun resetScores() {
        resetScoreMap()
        correctCount = 0
        incorrectCount = 0
        previousCorrectCount = 0
        previousIncorrectCount = 0
    }

    /* the progress bar shows the total correct count for the current block */
    fun getBlockScore(): Int = scoreMap.values.sum()

    /*  Retrieve the lowest value in the score list
     *  If lowest value is higher than threshold then all entries are higher so all are correct. */
    fun allAreCorrect(): Boolean = (scoreMapMin() > jumpThreshold)

    private fun scoreMapMin() =  scoreMap.values.minOrNull() ?: 0
    private fun scoreMapMax() =  scoreMap.values.maxOrNull() ?: 0

    /*  Prepare list for sql expression to add jumpers
     *  result is like  "(163, 1), (1816, 1), (1082, 1)" for 3 entries */
    fun scoreMapToString(): String = scoreMap.toList().toString().removeSurrounding("[", "]")

    fun noJumper(theIdx: Int): Boolean = (scoreMap.getOrDefault(theIdx, 0) <= jumpThreshold)

    /* all lemma's in block answered correctly 1x without any errors (100% score after one pass) */
    fun strike():Boolean = (scoreMap.size == blockSize &&  scoreMapMin() == 1 && scoreMapMax() == 1)
}

