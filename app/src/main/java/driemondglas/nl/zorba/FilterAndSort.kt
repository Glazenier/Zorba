package driemondglas.nl.zorba

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.MenuItem
import android.view.View
import kotlinx.android.synthetic.main.filter_and_sort.*


/*  This class represents the the activity "Selecties" that holds
 *  the selection controls to filter the greek words on length, first letter or difficulty level etc.
 *  Sorting or shuffling is also done here.
 *  The layout currently only has a portrait version
 *  The landscape layout is solved by putting it all in a vertical scroller
 */

class FilterAndSort : AppCompatActivity() {
    private val queryManager: QueryManager = QueryManager.getInstance()

    /* save initial values to prepare for possible cancel action */
    private var oldLevel1 = queryManager.levelBasic
    private var oldLevel2 = queryManager.levelAdvanced
    private var oldLevel3 = queryManager.levelBallast
    private var oldUseLength= queryManager.useLength
    private val oldPureLength = queryManager.pureLemmaLength
    private val oldInitiaalGrieks = queryManager.initial
    private val oldUseBlocks=queryManager.useBlocks
    private val oldBlockSize=queryManager.blockSize
    private val oldHideJumpers=queryManager.hideJumpers
    private val oldThreshold=queryManager.jumpThreshold
    private val oldSortTag=queryManager.orderbyTag
    private val oldDecending=queryManager.orderDecending

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
         *  grab the active settings from the query manager and set the respective controls.*/
        setLikeQueryManager()

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
        sw_hide_jumpers.setOnClickListener{ onHideJumpersSwitch() }
        btn_select.setOnClickListener { goBack() }
        btn_cancel.setOnClickListener { cancelChanges() }
        btn_default.setOnClickListener { defaultSelects() }

        /* set listener for changes in text field for block size  */
        text_blocksize.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) { onBlockSizeChange(s) }
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        })

        /* set listener for changes in text field for lemma length  */
        text_length.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) { onLengthChange(s) }
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        })

        /* set listener for changes in text field for threshold  */
        text_threshold.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) { onThresholdChange(s) }
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        })

    }

    /* this function sets all UI views to values found in the QueryManager */
    private fun setLikeQueryManager(){
        /* set level switches according to QueryManager's values */
        queryManager.apply {
            sw_level1.isChecked = levelBasic
            sw_level2.isChecked = levelAdvanced
            sw_level3.isChecked = levelBallast
        }
        /* set lemma length to queryManager's value */
        sw_use_length.isChecked = queryManager.useLength
        text_length.setText(queryManager.pureLemmaLength.toString())

        /* set the initial (first letter of a word) greek letter to queryManager's value */
        val initiaalGrieks = queryManager.initial
        if (queryManager.initial != "") {
            tv_initial.text = initiaalGrieks
            sw_initial.isChecked = true
        }

        /* set blockSize to queryManager's value */
        sw_use_blocks.isChecked = queryManager.useBlocks
        text_blocksize.setText(queryManager.blockSize.toString())

        /* set sortby switches to queryManager's state */
        sw_index.isChecked = false
        sw_alfa.isChecked = false
        sw_random.isChecked = false
        when (queryManager.orderbyTag) {
            "alfa" -> sw_alfa.isChecked = true
            "random" -> sw_random.isChecked = true
            else -> sw_index.isChecked = true
        }

        /* descending or ascending order */
        sw_desc.isChecked = queryManager.orderDecending

        /* hide the lemmas that jumped the threshold */
        text_threshold.setText((queryManager.jumpThreshold + 1).toString())
        Log.d("hvr","queryManager.hideJumpers = ${queryManager.hideJumpers}")
        sw_hide_jumpers.isChecked = queryManager.hideJumpers
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        /* Handle action bar item clicks here. The action bar will
         * automatically handle clicks on the Home/Up button, so long
         * as you specify a parent activity in AndroidManifest.decor.
         * We have our own goBack routine (hvr)
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

        /* forward to query manager */
        queryManager.apply{
            levelBasic = sw_level1.isChecked
            levelAdvanced = sw_level2.isChecked
            levelBallast = sw_level3.isChecked
        }
    }

    private fun onBlockSwitch() {
        /* use of blocks  OFF -> ON  */
        if (sw_use_blocks.isChecked) {
            /* check valid block size in text field */
            val numberInField = text_blocksize.text.toString().toIntOrNull()
            if (numberInField == null || numberInField < 1) {
               colorToast(context = this, msg=getString(R.string.msg_blocksize_number))
                sw_use_blocks.isChecked = false
            } else {
                queryManager.useBlocks = true
            }
        } else {
            /* ON -> OFF  */
            queryManager.useBlocks = false
        }
    }

    private fun onBlockSizeChange(numberAsString: CharSequence) {
        val targetSize = numberAsString.toString().toIntOrNull()
        when (targetSize) {
            null -> {
                colorToast(context = this, msg = getString(R.string.msg_blocksize_number))
                queryManager.useBlocks = false
                sw_use_blocks.isChecked = false
            }
            0 -> {
                queryManager.blockSize = targetSize
                queryManager.useBlocks = false
                sw_use_blocks.isChecked = false
            }
            else -> {
                queryManager.blockSize = targetSize
                queryManager.useBlocks = true
                sw_use_blocks.isChecked = true
            }
        }
    }

    private fun onLengthSwitch() {
        /* OFF -> ON  */
        if (sw_use_length.isChecked) {
            /* check valid length in text field */
            val textField = text_length.text.toString().toIntOrNull()
            if (textField==null || textField<1){
                colorToast(context = this, msg = getString(R.string.msg_lemmalength_number))
                sw_use_length.isChecked = false
            } else {
                queryManager.useLength=true
            }
        } else {
            /* ON -> OFF  */
            queryManager.useLength=false
        }
    }

    private fun onLengthChange(numberAsString: CharSequence) {
        val targetLength = numberAsString.toString().toIntOrNull()
        when (targetLength) {
            null -> {
                colorToast(context = this, msg = getString(R.string.msg_lemmalength_number))
                queryManager.useLength = false
                sw_use_length.isChecked = false
            }
            0 -> {
                queryManager.pureLemmaLength = targetLength
                queryManager.useLength = false
                sw_use_length.isChecked = false
            }
            else -> {
                queryManager.pureLemmaLength = targetLength
                queryManager.useLength = true
                sw_use_length.isChecked = true
            }
        }
    }

    fun onGreekLetter(view: View) {
        val tag = view.tag.toString()
        if (tag == "_") {
            tv_initial.text = ""
            queryManager.initial = ""
            sw_initial.isChecked = false
        } else {
            tv_initial.text = tag
            queryManager.initial = tag
            sw_initial.isChecked = true
        }
    }

    private fun onInitialChange() {
        val textvalue = tv_initial.text.toString()
        if (sw_initial.isChecked && textvalue != "") {
            queryManager.initial = textvalue
        } else {
            queryManager.initial = ""
            sw_initial.isChecked = false
        }
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
        /* inform the query manager */
        queryManager.orderbyTag = tag
    }

    private fun onDescending(){
        queryManager.orderDecending = sw_desc.isChecked
    }

    private fun onHideJumpersSwitch(){
        /* OFF -> ON  */
        if (sw_hide_jumpers.isChecked) {
            /* check valid length in text field */
            val textField = text_threshold.text.toString().toIntOrNull()
            if (textField==null || textField<1){
                colorToast(context = this,msg = getString(R.string.msg_threshold_number))
                sw_hide_jumpers.isChecked = false
            } else {
                queryManager.hideJumpers=true
            }
        } else {
            /* ON -> OFF  */
            queryManager.hideJumpers=false
        }
    }

    private fun onThresholdChange(numberAsString: CharSequence) {
        val targetHeight = numberAsString.toString().toIntOrNull()
        when (targetHeight) {
            null -> {
                colorToast(context = this, msg = getString(R.string.msg_threshold_number))
                queryManager.hideJumpers = false
                sw_hide_jumpers.isChecked = false
            }
            0 -> {
                queryManager.jumpThreshold = targetHeight
                queryManager.hideJumpers = false
                sw_hide_jumpers.isChecked = false
            }
            else -> queryManager.jumpThreshold = targetHeight - 1
        }
    }

    private fun cancelChanges() {
        /* restore initial values */
        queryManager.apply{
            levelBasic = oldLevel1
            levelAdvanced = oldLevel2
            levelBallast = oldLevel3
            useLength = oldUseLength
            pureLemmaLength = oldPureLength
            initial = oldInitiaalGrieks
            useBlocks = oldUseBlocks
            blockSize = oldBlockSize
            orderbyTag = oldSortTag
            orderDecending = oldDecending
            hideJumpers = oldHideJumpers
            jumpThreshold = oldThreshold
        }
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
        queryManager.clearDetails()
        setLikeQueryManager()
    }
}