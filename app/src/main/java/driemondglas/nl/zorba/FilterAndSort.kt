package driemondglas.nl.zorba

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import driemondglas.nl.zorba.databinding.FilterAndSortBinding

/*  This class represents the the activity "Filter and Sort" that holds
 *  the selection controls to filter the greek words on length, first letter and/or difficulty level etc.
 *  Sorting or shuffling is also set here. */

var voorbeeldtext ="Ξεσκεπάζω την ψυχοφθόρα σας βδελυγμία."
class FilterAndSort : AppCompatActivity() {

    private lateinit var binding: FilterAndSortBinding // replaces synthetic view binding

    // flags "user typed input" (true) or "programmic text input" (false). Flag used by text change listener to update related fields or not.
    private var userTyped = true

    // preserve initial values to prepare for possible cancel action
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
    private val oldFlashed = flashed
    private val oldSpeechRate = speechRate

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = FilterAndSortBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.title_activity)
            subtitle = getString(R.string.subtitle_filter_sort)
        }

        // First of all,
        // grab the active settings from the global variables and set the respective controls.
        loadControlsFromVars()

        // set listener for changes in seekbar for speech rate
        binding.sbSpeechrate.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb_speed: SeekBar, progress: Int, fromUser: Boolean) {
                speechRate = progress / 100f
                zorbaSpeaks.setSpeechRate(speechRate)
                binding.txtSpeed.text = getString(R.string.whole_percent, progress)
                zorbaPreferences.edit().putFloat("speechrate", speechRate).apply()
            }
            override fun onStartTrackingTouch(sb_speed: SeekBar) {}
            override fun onStopTrackingTouch(sb_speed: SeekBar) {}
        })

        with (binding) {
            /* add listeners for switches and buttons */
            swLevel1.setOnClickListener { onLevelChange() }
            swLevel2.setOnClickListener { onLevelChange() }
            swLevel3.setOnClickListener { onLevelChange() }
            swUseLength.setOnClickListener { onLengthSwitch() }
            swInitial.setOnClickListener { onInitialChange() }
            swUseBlocks.setOnClickListener { onBlockSwitch() }
            swIndex.setOnClickListener { onSort(it) }
            swAlfa.setOnClickListener { onSort(it) }
            swRandom.setOnClickListener { onSort(it) }
            swDesc.setOnClickListener { onDescending() }
            swHideJumpers.setOnClickListener { onHideJumpersSwitch() }
            btnSelect.setOnClickListener { goBack() }
            btnCancel.setOnClickListener { cancelChanges() }
            btnDefault.setOnClickListener { restoreDefaults() }
            swFlashed.setOnClickListener { onFlash() }
            btnSpeedtest.setOnClickListener{ cleanSpeech(voorbeeldtext, "standaard")}

            /* set listener for changes in text field for block size  */
            textBlocksize.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable) { if (userTyped) onBlockSizeChange(s) }
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            })

            /* set listener for changes in text field for lemma length  */
            textLength.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable) { if (userTyped) onLengthChange(s) }
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            })

            /* set listener for changes in text field for threshold  */
            textThreshold.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable) { if (userTyped) onThresholdChange(s) }
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            })
        }
    }

    /* this function sets all UI controls from global variable values */
    private fun loadControlsFromVars() {

        userTyped = false  // prevents unwanted actions triggered by the TextChangedListeners

        /* set level switches according to global values */
        with(binding) {
            swLevel1.isChecked = levelBasic
            swLevel2.isChecked = levelAdvanced
            swLevel3.isChecked = levelBallast

            /* set lemma length to global value */
            textLength.setText(pureLemmaLength.toString())  // NOTE: change listener activated if flag [userTyped] is true!!!
            swUseLength.isChecked = useLength

            /* set the initial (first letter of a word) greek letter to global value */
            tvInitial.text = initial
            swInitial.isChecked = initial.isNotEmpty()

            /* set blocks to global value */
            textBlocksize.setText(blockSize.toString()) // NOTE: change listener activated if flag [userTyped] is true!!!
            swUseBlocks.isChecked = useBlocks

            /* set sortby switches to queryManager's state */
            swIndex.isChecked = false
            swAlfa.isChecked = false
            swRandom.isChecked = false
            when (orderbyTag) {
                "alfa" -> swAlfa.isChecked = true
                "random" -> swRandom.isChecked = true
                else -> swIndex.isChecked = true
            }

            /* descending or ascending order */
            swDesc.isChecked = orderDescending

            /* set to hide the lemmas that jumped the threshold */
            textThreshold.setText((jumpThreshold + 1).toString()) // NOTE: change listener activated if flag [userTyped] is true!!!
            swHideJumpers.isChecked = hideJumpers

            swFlashed.isChecked = flashed
            val rate = (100 * speechRate).toInt()
            txtSpeed.text = getString(R.string.whole_percent, rate)
            sbSpeechrate.progress = rate
        }
        userTyped = true  //prepare for user input
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        /* Inflate the menu; this adds menu items to the zorba action bar. */
        menuInflater.inflate(R.menu.menu_filter_sort, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        /* Handle action bar item clicks here. The action bar will
         * automatically handle clicks on the Home/Up button, so long
         * as you specify a parent activity in Android Manifest.
         * We use our own goBack routine (hvr) */
        when (item.itemId) {
            R.id.menu_set_theme_wordtype -> {
                // launch the wordgroup/wordtype selection activity
                val myIntent = Intent(this, ThemeAndWordType::class.java)
                startActivity(myIntent)
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            }
            R.id.menu_clear_selects -> {
                resetThemeType()
                restoreDefaults()
            }

            R.id.menu_set_default -> saveAsDefaults()

            android.R.id.home -> goBack()

            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun onLevelChange() {
        /* if no level is selected, select all */
        if (!(binding.swLevel1.isChecked || binding.swLevel2.isChecked || binding.swLevel3.isChecked)) {
            binding.swLevel1.isChecked = true
            binding.swLevel2.isChecked = true
            binding.swLevel3.isChecked = true
        }

        levelBasic = binding.swLevel1.isChecked
        levelAdvanced = binding.swLevel2.isChecked
        levelBallast = binding.swLevel3.isChecked

        zorbaPreferences.edit()
            .putBoolean("levelbasic", levelBasic)
            .putBoolean("leveladvanced", levelAdvanced)
            .putBoolean("levelballast", levelBallast)
            .apply()
    }

    private fun onBlockSwitch() {
        /* use of blocks  OFF -> ON  */
        if (binding.swUseBlocks.isChecked) {
            /* check valid block size in text field */
            val numberInField = binding.textBlocksize.text.toString().toIntOrNull()
            if (numberInField == null || numberInField < 1) {
                colorToast(context = this, msg = getString(R.string.msg_blocksize_number))
                binding.swUseBlocks.isChecked = false
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
                binding.swUseBlocks.isChecked = false
            }
            0 -> {
                zorbaPreferences.edit()
                    .putInt("blocksize", 0)
                    .putBoolean("useblocks", false)
                    .apply()
                blockSize = 0
                useBlocks = false
                binding.swUseBlocks.isChecked = false
            }
            else -> {
                zorbaPreferences.edit()
                    .putInt("blocksize", targetSize)
                    .putBoolean("useblocks", true)
                    .apply()
                blockSize = targetSize
                useBlocks = true
                binding.swUseBlocks.isChecked = true
            }
        }
    }

    private fun onLengthSwitch() {
        /* OFF -> ON  */
        if (binding.swUseLength.isChecked) {
            /* check valid length in text field */
            val textField = binding.textLength.text.toString().toIntOrNull()
            if (textField == null || textField < 1) {
                colorToast(context = this, msg = getString(R.string.msg_lemmalength_number))
                binding.swUseLength.isChecked = false
                useLength = false
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
                binding.swUseLength.isChecked = false
            }
            0 -> {
                pureLemmaLength = 0
                useLength = false
                binding.swUseLength.isChecked = false
            }
            else -> {
                pureLemmaLength = targetLength
                useLength = true
                binding.swUseLength.isChecked = true
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
            binding.tvInitial.text = ""
            initial = ""
            binding.swInitial.isChecked = false
        } else {
            binding.tvInitial.text = tag
            initial = tag
            binding.swInitial.isChecked = true
        }
        zorbaPreferences.edit().putString("initial", initial).apply()
    }


    private fun onInitialChange() {
        val textvalue = binding.tvInitial.text.toString()
        if (binding.swInitial.isChecked && textvalue.isNotEmpty()) {
            initial = textvalue
        } else {
            initial = ""
            binding.swInitial.isChecked = false
        }
        zorbaPreferences.edit().putString("initial", initial).apply()
    }

    private fun onSort(view: View) {
        /* reset all sorting switches */
        binding.swIndex.isChecked = false
        binding.swAlfa.isChecked = false
        binding.swRandom.isChecked = false

        /* identify which order has been selected */
        val tag = view.tag.toString()

        /* set the appropriate flag with selected sort order */
        when (tag) {
            "index" -> binding.swIndex.isChecked = true
            "alfa" -> {
                binding.swAlfa.isChecked = true
                binding.swDesc.isChecked = false
                onDescending()
            }
            "random" -> binding.swRandom.isChecked = true
        }
        orderbyTag = tag
        zorbaPreferences.edit().putString("orderbytag", orderbyTag).apply()
    }

    private fun onDescending() {
        orderDescending = binding.swDesc.isChecked
        zorbaPreferences.edit().putBoolean("orderdescending", orderDescending).apply()
    }

    private fun onFlash() {
        flashed = binding.swFlashed.isChecked
        zorbaPreferences.edit().putBoolean("flashed", flashed).apply()
    }

    private fun onHideJumpersSwitch() {
        /* OFF --> ON  */
        if (binding.swHideJumpers.isChecked) {
            /* check valid length in text field */
            val textField = binding.textThreshold.text.toString().toIntOrNull()
            if (textField == null || textField < 1) {
                colorToast(context = this, msg = getString(R.string.msg_threshold_number))
                binding.swHideJumpers.isChecked = false
                hideJumpers = false
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
                binding.swHideJumpers.isChecked = false
            }
            0 -> {
                jumpThreshold = 0
                hideJumpers = false
                binding.swHideJumpers.isChecked = false
            }
            else -> jumpThreshold = targetHeight - 1
        }
        zorbaPreferences.edit()
            .putInt("jumpthreshold", jumpThreshold)
            .putBoolean("hidejumpers", hideJumpers)
            .apply()
    }

    private fun cancelChanges() {
        // restore configuration values as they were on activity start
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
        flashed = oldFlashed
        speechRate=oldSpeechRate

        // ... and store them as recent use
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

        // go back to calling activity
        val myIntent = Intent()
        myIntent.putExtra("result", "cancel")
        setResult(RESULT_OK, myIntent)
        finish()
    }

    private fun goBack() {
        // finish this intent, go back to calling activity, providing action result
        val myIntent = Intent()
        myIntent.putExtra("result", "selected")
        setResult(RESULT_OK, myIntent)
        finish()
    }

    override fun finish() {
        if (QueryManager.selectedCount() == 0) {
            colorToast(this, getString(R.string.warning_empty_cursor))
        } else {
            super.finish()
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }
    }

    private fun restoreDefaults() {
        // get default values from shared preferences and assign them to applicable global variables
        resetDetails()

        // show these values in the various fields
        loadControlsFromVars()

    }

    /* method stores current configuration as default configuration in shared preferences */
    private fun saveAsDefaults() {
        zorbaPreferences.edit()
            .putBoolean("defaultuseblocks", useBlocks)
            .putInt("defaultblocksize", blockSize)
            .putBoolean("defaultlevelbasic", levelBasic)
            .putBoolean("defaultleveladvanced", levelAdvanced)
            .putBoolean("defaultlevelballast", levelBallast)
            .putBoolean("defaultuselength", useLength)
            .putInt("defaultpurelemmalength", pureLemmaLength)
            .putString("defaultinitial", initial)
            .putString("defaultorderbytag", orderbyTag)
            .putBoolean("defaultorderdescending", orderDescending)
            .putInt("defaultjumpthreshold", jumpThreshold)
            .putBoolean("defaulthidejumpers", hideJumpers)
            .putBoolean("defaultflashed", flashed)
            .putFloat("defaultspeechrate", speechRate)
            .apply()
    }
}