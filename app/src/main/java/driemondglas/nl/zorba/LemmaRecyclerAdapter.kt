package driemondglas.nl.zorba

import android.speech.tts.TextToSpeech
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.util.*
import driemondglas.nl.zorba.Utils.enabled

class LemmaRecyclerAdapter(private val lemmaArrayList: ArrayList<LemmaItem>) : RecyclerView.Adapter<LemmaRecyclerAdapter.MyViewHolder>() {
    private var viewHolderClickListener: View.OnClickListener? = null

    /* display greek, dutch or both */
    var showGreek = true
    var showDutch = true

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.item_layout, parent, false)
        return MyViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        with(holder) {
            meaningNl.text = lemmaArrayList[position].meaningNL
            meaningNl.visibility = if (showDutch) View.VISIBLE else View.GONE

            lemmaGr.text = lemmaArrayList[position].pureLemma
            lemmaGr.visibility = if (showGreek) View.VISIBLE else View.GONE

            /* This is the speaker button in the recycler list itself */
            speaker.tag = lemmaArrayList[position].woordsoort
            speaker.enabled(useSpeech)
            if (useSpeech) speaker.setOnClickListener {
                zorbaSpeaks?.speak(lemmaArrayList[position].pureLemma, TextToSpeech.QUEUE_FLUSH, null, "")
            }
        }
    }

    override fun getItemCount(): Int = lemmaArrayList.size

    fun setOnItemClickListener(itemClickListener: View.OnClickListener) {
        viewHolderClickListener = itemClickListener
    }

    /* Kotlin reference:
     *  A class may be marked as inner to be able to access members of outer class.
     *  Inner classes carry a reference to an object of an outer class.
     *  HvR: In this case a reference to the 'viewHolderClickListener' */

    inner class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val lemmaGr: TextView = itemView.findViewById(R.id.lemma_gr) as TextView
        val meaningNl: TextView = itemView.findViewById(R.id.meaning_nl) as TextView
        val speaker: TextView = itemView.findViewById(R.id.speaker) as TextView

        init {
            /* set tag as current view holder
             * setOnClickListener() as local View.OnClickListener variable.
             */
            itemView.tag = this
            itemView.setOnClickListener(viewHolderClickListener)
        }
    }
}