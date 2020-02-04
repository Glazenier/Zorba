package driemondglas.nl.zorba

/* This file contains 3 classes all related to the splash screen:
*   Splash      Activity that gets called from main activity and finishes after animation is done.
*   SplashView  Custom view to draw graphic objects (blue lines mostly)
*               Note: this custom view is used in the activity_splash.xml layout file,
*               as type: driemondglas.nl.zorba.SplashView and id: "@+id/sundown"
*   BlueLine    Template for each line, contains start and and points as well as draw and show methods
*/

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.content.ContextCompat
import android.util.AttributeSet
import android.view.View
import kotlinx.android.synthetic.main.activity_splash.*
import java.util.ArrayList

/* Top level properties can be used by all classes in the project */

/* paint used to draw flag and letters 'Zorba' */
private val blue = Paint()

/* constants to size and place flag and Zorba graphic */
const val X0_ZORBA = 240f
const val Y0_ZORBA = 1000f
const val X0_FLAG = 240f
const val Y0_FLAG = 50f
const val SCALE_ZORBA = 20f

/* constants for animation timing */
const val SPLASH_START_DELAY: Long = 1000    // ms before animation starts
const val ANIMATION_DELAY: Long = 4         // ms between animation STEPS
const val STEPS = 20                        // number of STEPS between start end end of animation
/* there are 15 lines to be animated, each in 20 STEPS of 5 milisecs, so the animation should take 1500ms */

/* constants determining Greek flag size */
/* Thickness of the lines that make (the blue part of) the greek flag
 * All other dimensions are in relation to this value (
 */
const val THICKNESS = 20f
const val FLAG_WIDTH_FACTOR = 18   // flag is wide 18 x THICKNESS

/* flag used to end the animation loop */
var done = false

/* value keeping track of line currently animated, as listindex of the lines arraylist */
private var lineToMove = 0

/* arraylist holding all the lines that initially form the flag and the transform into Zorba graphic.*/
private var lineList: ArrayList<BlueLine> = ArrayList()


class Splash : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        sundown.init()
        t.start()
    }

    private val t = Thread {
        Thread.sleep(SPLASH_START_DELAY)
        while (!done) {
            sundown.move()
            Thread.sleep(ANIMATION_DELAY)
        }
        finish()
    }
}


/* SplashView is a custom view class to draw flag and other splash objects: */
class SplashView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    fun init() {
        blue.color = ContextCompat.getColor(context, R.color.κυανός)
        blue.strokeWidth = THICKNESS

        /* Each line is created as an instance of the class BlueLine.
         * This allows us to show the lines using their current start and end point
         * and move them by changing the start and end points.
         * Initially, all lines make up the Greek flag.
         * x and y coordinates are the ones to use for the flag
         * The p and q coordinates are the targets for these lines to form the word Zorba
         * The lines are put one by one in the arraylist
         */

        /* empty lines-list */
        with(lineList) {
            clear()

            // Z
            add(BlueLine(0f, THICKNESS / 2, 2 * THICKNESS, THICKNESS / 2, 0f, 0f, 10f, 1f))
            add(BlueLine(3 * THICKNESS, THICKNESS / 2, 5 * THICKNESS, THICKNESS / 2, 0f, 12f, 10f, 0f))
            add(BlueLine(5 * THICKNESS, THICKNESS / 2, FLAG_WIDTH_FACTOR * THICKNESS, THICKNESS / 2, 0f, 10f, 10f, 12f))
            // O
            add(BlueLine(0f, 3 * THICKNESS / 2, 2 * THICKNESS, 3 * THICKNESS / 2, 11f, 10f, 16f, 5f))
            add(BlueLine(3 * THICKNESS, 3 * THICKNESS / 2, 5 * THICKNESS, 3 * THICKNESS / 2, 11f, 9f, 16f, 13f))
            add(BlueLine(5 * THICKNESS, 5 * THICKNESS / 2, FLAG_WIDTH_FACTOR * THICKNESS, 5 * THICKNESS / 2, 15f, 5f, 19f, 9f))
            add(BlueLine(0f, 7 * THICKNESS / 2, 2 * THICKNESS, 7 * THICKNESS / 2, 15f, 13f, 19f, 7f))
            // R
            add(BlueLine(3 * THICKNESS, 7 * THICKNESS / 2, 5 * THICKNESS, 7 * THICKNESS / 2, 21f, 12f, 22f, 6f))
            add(BlueLine(0f, 9 * THICKNESS / 2, 2 * THICKNESS, 9 * THICKNESS / 2, 21f, 7f, 27f, 6f))
            // B
            add(BlueLine(3 * THICKNESS, 4 * THICKNESS + THICKNESS / 2, 5 * THICKNESS, 4 * THICKNESS + THICKNESS / 2, 28f, 13f, 30f, 0f))
            add(BlueLine(5 * THICKNESS, 4 * THICKNESS + THICKNESS / 2, FLAG_WIDTH_FACTOR * THICKNESS, 4 * THICKNESS + THICKNESS / 2, 28f, 6f, 35f, 10f))
            add(BlueLine(0f, 6 * THICKNESS + THICKNESS / 2, 5 * THICKNESS, 6 * THICKNESS + THICKNESS / 2, 28f, 12f, 35f, 9f))
            // A
            add(BlueLine(5 * THICKNESS, 6 * THICKNESS + THICKNESS / 2, FLAG_WIDTH_FACTOR * THICKNESS, 6 * THICKNESS + THICKNESS / 2, 37f, 13f, 38f, 6f))
            add(BlueLine(0f, 8 * THICKNESS + THICKNESS / 2, 5 * THICKNESS, 8 * THICKNESS + THICKNESS / 2, 36f, 12f, 44f, 8f))
            add(BlueLine(5 * THICKNESS, 8 * THICKNESS + THICKNESS / 2, FLAG_WIDTH_FACTOR * THICKNESS, 8 * THICKNESS + THICKNESS / 2, 38f, 6f, 43f, 12f))
        }
    }

    /* on draw is triggered by the android OS whenever it is ready to do so
     * The animation thread triggered by the Splash activity makes sure the start and end points are updated so
     * each onDraw cycle the lines are drawn at their updated location
     */
    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        if (canvas == null) return

        /* on each iteration the lines are displayed with their current x and y coordinates */
        for (line in lineList) line.show(canvas)
//        lineList.forEach() {it.show(canvas)}
    }

    fun move() {
        val line = lineList[lineToMove]
        line.move()
        /* inform the OS that you'd like to redraw the canvas because the content is renewed*/
        postInvalidate()
    }
}

class BlueLine(
    private var x0: Float,
    private var y0: Float,
    private var x1: Float,
    private var y1: Float,
    private var p0: Float,
    private var q0: Float,
    private var p1: Float,
    private var q1: Float
) {

    private var stepCount = 0

    init {                            // only once for each new line, do:
        x0 += X0_FLAG                 // add the left margin to the start x-coordinate
        x1 += X0_FLAG                 // add the left margin to the end x-coordinate
        y0 += Y0_FLAG                 // add the top  margin to the start y-coordinate
        y1 += Y0_FLAG                 // add the top  margin to the end y-coordinate

        p0 =
            X0_ZORBA + SCALE_ZORBA * p0  // add the left margin of the Zorba text stroke startpoint and apply the scale factor
        p1 =
            X0_ZORBA + SCALE_ZORBA * p1  // add the left margin of the Zorba text stroke endpoint and apply the scale factor
        q0 =
            Y0_ZORBA + SCALE_ZORBA * q0  // add the top margin of the Zorba text stroke startpoint and apply the scale factor
        q1 =
            Y0_ZORBA + SCALE_ZORBA * q1  // add the top margin of the Zorba text stroke endpoint and apply the scale factor
    }

    /* calculate how big each stap needs to be to move the line from flag to text */
    private val beginXstep = (p0 - x0) / STEPS
    private val beginYstep = (q0 - y0) / STEPS
    private val endXstep = (p1 - x1) / STEPS
    private val endYstep = (q1 - y1) / STEPS

    fun show(canvas: Canvas) = canvas.drawLine(x0, y0, x1, y1, blue)

    fun move() {
        if (stepCount < STEPS) {
            x0 += beginXstep
            y0 += beginYstep
            x1 += endXstep
            y1 += endYstep
            stepCount++
        } else {
            if (lineToMove < lineList.size - 1) lineToMove++ else done = true
        }
    }
}