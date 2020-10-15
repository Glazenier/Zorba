package driemondglas.nl.zorba

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.filter_and_sort.*

/*  This class represents the the activity "Selections" that holds
 *  the selection controls to filter the greek words on length, first letter and/or difficulty level etc.
 *  Sorting or shuffling is also set here.
 *  The layout currently only has a portrait version
 *  The landscape layout is solved by putting it all in a vertical scroller
 */

class FilterAndSort : AppCompatActivity() {

    /* preserve initial values to prepare for possible cancel action */
    private val oldLevel1 = levelBasic
    private val oldLevel2 = levelAdvanced
    private val oldLevel3 = levelBallast
    private val oldUseLength = useLength
    private val oldPureLength = pureLemmaLength
    private val oldInitiaalGrieks = initial
    private val oldUseBlocks = useBlocks
    private val oldBlockSize = blockSize
    private val oldHideJumpers = hideJumpers
    private val oldThreshold = jumpThreshold
    private val oldSortTag = orderbyTag
    private val oldDescending = orderDescending

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.filter_and_sort)

        val myBar = supportActionBar
        if (myBar != null) {
            /* This next line shows the home/back button.
             * The functionality is handled by the android system as long as a parent activity
             * is specified in the manifest.xls file
             * We OVERRIDE this functionality with our own goBack() function to cleanly close cursor and database
             */
            myBar.setDisplayHomeAsUpEnabled(true)
            myBar.title = "ZORBA"
            myBar.subtitle = "Detail selecties, blokken en volgorde"
        }

        /* First of all,
         *  grab the active settings from the global variables and set the respective controls.*/
        loadControlsWithGlobals()

        /* add listeners for switches and buttons */
        sw_level1.setOnClickListener { onLevelChange() }
        sw_level2.setOnClickListener { onLevelChange() }
        sw_level3.setOnClickListener { onLevelChange() }
        sw_use_length.setOnClickListener { onLengthSwitch() }
        sw_initial.setOnClickListener { onInitialChange() }
        sw_use_blocks.setOnClickListener { onBlockSwitch() }
        sw_index.setOnClickListener { onSort(it) }
        sw_alfa.setOnClickListener { onSort(it) }
        sw_random.setOnClickListener { onSort(it) }
        sw_desc.setOnClickListener { onDescending() }
        sw_hide_jumpers.setOnClickListener { onHideJumpersSwitch() }
        btn_select.setOnClickListener { goBack() }
        btn_cancel.setOnClickListener { cancelChanges() }
        btn_default.setOnClickListener { defaultSelects() }

        /* set listener for changes in text field for block size  */
        text_blocksize.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                onBlockSizeChange(s)
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        })

        /* set listener for changes in text field for lemma length  */
        text_length.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                onLengthChange(s)
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        })

        /* set listener for changes in text field for threshold  */
        text_threshold.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                onThresholdChange(s)
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        })

    }

    /* this function sets all UI views to values found in the QueryManager */
    private fun loadControlsWithGlobals() {
        /* set level switches according to global values */
        sw_level1.isChecked = levelBasic
        sw_level2.isChecked = levelAdvanced
        sw_level3.isChecked = levelBallast

        /* set lemma length to global value */
        sw_use_length.isChecked = useLength
        text_length.setText(pureLemmaLength.toString())

        /* set the initial (first letter of a word) greek letter to global value */
        tv_initial.text = initial
        sw_initial.isChecked = initial.isNotEmpty()

        /* set blocks to global value */
        sw_use_blocks.isChecked = useBlocks
        text_blocksize.setText(blockSize.toString())

        /* set sortby switches to queryManager's state */
        sw_index.isChecked = false
        sw_alfa.isChecked = false
        sw_random.isChecked = false
        when (orderbyTag) {
            "alfa" -> sw_alfa.isChecked = true
            "random" -> sw_random.isChecked = true
            else -> sw_index.isChecked = true
        }

        /* descending or ascending order */
        sw_desc.isChecked = orderDescending

        /* set to hide the lemmas that jumped the threshold */
        text_threshold.setText((jumpThreshold + 1).toString())
        sw_hide_jumpers.isChecked = hideJumpers
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        /* Handle action bar item clicks here. The action bar will
         * automatically handle clicks on the Home/Up button, so long
         * as you specify a parent activity in Android Manifest.
         * We use our own goBack routine (hvr)
         */
        if (item.itemId == android.R.id.home) goBack()
        return true
    }

    private fun onLevelChange() {
        /* if no level is selected, select all */
        if (!(sw_level1.isChecked || sw_level2.isChecked || sw_level3.isChecked)) {
            sw_level1.isChecked = true
            sw_level2.isChecked = true
            sw_level3.isChecked = true
        }

        levelBasic = sw_level1.isChecked
        levelAdvanced = sw_level2.isChecked
        levelBallast = sw_level3.isChecked

        zorbaPreferences.edit()
              .putBoolean("levelbasic", levelBasic)
              .putBoolean("leveladvanced", levelAdvanced)
              .putBoolean("levelballast", levelBallast)
              .apply()
    }

    private fun onBlockSwitch() {
        /* use of blocks  OFF -> ON  */
        if (sw_use_blocks.isChecked) {
            /* check valid block size in text field */
            val numberInField = text_blocksize.text.toString().toIntOrNull()
            if (numberInField == null || numberInField < 1) {
                colorToast(context = this, msg = getString(R.string.msg_blocksize_number))
                sw_use_blocks.isChecked = false
            } else {
                useBlocks = true
                zorbaPreferences.edit().putBoolean("useblocks", true).apply()
            }
        } else {
            /* ON -> OFF  */
            useBlocks = false
            zorbaPreferences.edit().putBoolean("useblocks", false).apply()
        }
    }

    private fun onBlockSizeChange(numberAsString: CharSequence) {
        val targetSize = numberAsString.toString().toIntOrNull()
        when (targetSize) {
            null -> {
                colorToast(context = this, msg = getString(R.string.msg_blocksize_number))
                useBlocks = false
                sw_use_blocks.isChecked = false
            }
            0 -> {
                zorbaPreferences.edit().putInt("blocksize", 0).apply()
                zorbaPreferences.edit().putBoolean("useblocks", false).apply()
                blockSize = 0
                useBlocks = false
                sw_use_blocks.isChecked = false
            }
            else -> {
                zorbaPreferences.edit().putInt("blocksize", targetSize).apply()
                zorbaPreferences.edit().putBoolean("useblocks", true).apply()

                blockSize = targetSize
                useBlocks = true
                sw_use_blocks.isChecked = true
            }
        }
    }

    private fun onLengthSwitch() {
        /* OFF -> ON  */
        if (sw_use_length.isChecked) {
            /* check valid length in text field */
            val textField = text_length.text.toString().toIntOrNull()
            if (textField == null || textField < 1) {
                colorToast(context = this, msg = getString(R.string.msg_lemmalength_number))
                sw_use_length.isChecked = false
            } else {
                useLength = true
            }
        } else {
            /* ON -> OFF  */
            useLength = false
        }
        zorbaPreferences.edit().putBoolean("uselength", useLength).apply()
    }

    private fun onLengthChange(numberAsString: CharSequence) {
        val targetLength = numberAsString.toString().toIntOrNull()
        when (targetLength) {
            null -> {
                colorToast(context = this, msg = getString(R.string.msg_lemmalength_number))
                useLength = false
                sw_use_length.isChecked = false
            }
            0 -> {
                pureLemmaLength = 0
                useLength = false
                sw_use_length.isChecked = false
            }
            else -> {
                pureLemmaLength = targetLength
                useLength = true
                sw_use_length.isChecked = true
            }
        }
        zorbaPreferences.edit()
              .putBoolean("uselength", useLength)
              .putInt("purelemmalenght", pureLemmaLength)
              .apply()
    }

    fun onGreekLetter(view: View) {
        val tag = view.tag.toString()
        if (tag == "_") {
            tv_initial.text = ""
            initial = ""
            sw_initial.isChecked = false
        } else {
            tv_initial.text = tag
            initial = tag
            sw_initial.isChecked = true
        }
        zorbaPreferences.edit().putString("initial", initial).apply()
    }


    private fun onInitialChange() {
        val textvalue = tv_initial.text.toString()
        if (sw_initial.isChecked && textvalue.isNotEmpty()) {
            initial = textvalue
        } else {
            initial = ""
            sw_initial.isChecked = false
        }
        zorbaPreferences.edit().putString("initial", initial).apply()
    }

    private fun onSort(view: View) {
        /* reset all sorting switches */
        sw_index.isChecked = false
        sw_alfa.isChecked = false
        sw_random.isChecked = false

        /* identify which order has been selected */
        val tag = view.tag.toString()

        /* set the appropriate flag with selected sort order */
        when (tag) {
            "index" -> sw_index.isChecked = true
            "alfa" -> sw_alfa.isChecked = true
            "random" -> sw_random.isChecked = true
        }
        orderbyTag = tag
        zorbaPreferences.edit().putString("orderbytag", orderbyTag).apply()
    }

    private fun onDescending() {
        orderDescending = sw_desc.isChecked
        zorbaPreferences.edit().putBoolean("orderdescending", orderDescending).apply()
    }

    private fun onHideJumpersSwitch() {
        /* OFF --> ON  */
        if (sw_hide_jumpers.isChecked) {
            /* check valid length in text field */
            val textField = text_threshold.text.toString().toIntOrNull()
            if (textField == null || textField < 1) {
                colorToast(context = this, msg = getString(R.string.msg_threshold_number))
                sw_hide_jumpers.isChecked = false
            } else {
                hideJumpers = true
            }
            /* ON --> OFF  */
        } else {
            hideJumpers = false
        }
        zorbaPreferences.edit().putBoolean("hidejumpers", hideJumpers).apply()
    }

    private fun onThresholdChange(numberAsText: CharSequence) {
        val targetHeight = numberAsText.toString().toIntOrNull()
        when (targetHeight) {
            null -> {
                colorToast(context = this, msg = getString(R.string.msg_threshold_number))
                hideJumpers = false
                sw_hide_jumpers.isChecked = false
            }
            0 -> {
                jumpThreshold = 0
                hideJumpers = false
                sw_hide_jumpers.isChecked = false
            }
            else -> jumpThreshold = targetHeight - 1
        }
        zorbaPreferences.edit()
              .putInt("jumpthreshold", jumpThreshold)
              .putBoolean("hidejumpers", hideJumpers)
              .apply()
    }

    private fun cancelChanges() {
        /* restore initial values */
        useBlocks = oldUseBlocks
        blockSize = oldBlockSize
        levelBasic = oldLevel1
        levelAdvanced = oldLevel2
        levelBallast = oldLevel3
        useLength = oldUseLength
        pureLemmaLength = oldPureLength
        initial = oldInitiaalGrieks
        orderbyTag = oldSortTag
        orderDescending = oldDescending
        hideJumpers = oldHideJumpers
        jumpThreshold = oldThreshold

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
              .apply()

        /* go back to calling activity */
        val myIntent = Intent()
        myIntent.putExtra("result", "cancel")
        setResult(RESULT_OK, myIntent)
        finish()
    }

    private fun goBack() {
        /* finish this intent, go back to main activity, providing action result */
        val myIntent = Intent()
        myIntent.putExtra("result", "selected")
        setResult(RESULT_OK, myIntent)
        finish()
    }

    private fun defaultSelects() {
        resetDetails()
        loadControlsWithGlobals()
    }
}