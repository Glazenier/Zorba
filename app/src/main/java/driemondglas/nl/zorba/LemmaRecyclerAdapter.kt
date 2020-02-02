package driemondglas.nl.zorba

import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.util.*
import driemondglas.nl.zorba.Utils.enabled

class LemmaRecyclerAdapter(private val lemmaArrayList: ArrayList<LemmaItem>) : RecyclerView.Adapter<LemmaRecyclerAdapter.MyViewHolder>() {
    private var viewHolderClickListener: View.OnClickListener? = null

    /* display greek or dutch or both */
    var showGreek=true
    var showDutch=true

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.item_layout, parent, false)
        return MyViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        holder.meaningNl.visibility = if (showDutch) View.VISIBLE else View.GONE
        holder.lemmaGr.visibility = if (showGreek) View.VISIBLE else  View.GONE
        holder.speaker.visibility = if (showGreek) View.VISIBLE else  View.GONE

        holder.lemmaGr.text = lemmaArrayList[position].pureLemma
        holder.meaningNl.text = lemmaArrayList[position].meaningNL
        holder.speaker.tag = lemmaArrayList[position].woordsoort
//        holder.speaker.text = if (soundOn) "\uD83D\uDD08" else  "\uD83D\uDD07"
        holder.speaker.enabled(useSpeech)


        /* This is the speaker in the recycler list itself */
        if(useSpeech) holder.speaker.setOnClickListener { cleanSpeech(holder.lemmaGr.text.toString(), "standard") }
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
        val speaker: TextView=itemView.findViewById(R.id.speaker) as TextView


        init {
            /* setTag() as current view holder along with
             * setOnClickListener() as your local View.OnClickListener variable.
             * You can set the same viewHolderClickListener on multiple views if required
             * and later differentiate those clicks using view's id.*/
            itemView.tag = this
            itemView.setOnClickListener(viewHolderClickListener)
        }
    }
}