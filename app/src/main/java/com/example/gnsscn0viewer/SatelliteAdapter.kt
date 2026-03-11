package com.example.gnsscn0viewer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

import com.google.android.flexbox.FlexboxLayout


class SatelliteAdapter : ListAdapter<SatelliteUiItem, RecyclerView.ViewHolder>(Diff) {

    companion object {
        private const val VT_HEADER = 1
        private const val VT_VALUES = 2
        private const val VT_ROW = 3

        private object Diff : DiffUtil.ItemCallback<SatelliteUiItem>() {
            override fun areItemsTheSame(oldItem: SatelliteUiItem, newItem: SatelliteUiItem): Boolean {
                return oldItem == newItem
            }
            override fun areContentsTheSame(oldItem: SatelliteUiItem, newItem: SatelliteUiItem): Boolean {
                return oldItem == newItem
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is SatelliteUiItem.Header -> VT_HEADER
            is SatelliteUiItem.SignalValuesRow -> VT_VALUES
            is SatelliteUiItem.Row -> VT_ROW
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (viewType) {
            VT_HEADER -> HeaderVH(inf.inflate(R.layout.item_header, parent, false))
            VT_VALUES -> ValuesVH(inf.inflate(R.layout.item_signal_values, parent, false))
            else -> RowVH(inf.inflate(R.layout.item_satellite, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is SatelliteUiItem.Header -> (holder as HeaderVH).bind(item)
            is SatelliteUiItem.SignalValuesRow -> (holder as ValuesVH).bind(item)
            is SatelliteUiItem.Row -> (holder as RowVH).bind(item)
        }
    }

    private class HeaderVH(v: View) : RecyclerView.ViewHolder(v) {
        private val tvTitle: TextView = v.findViewById(R.id.tvHeaderTitle)
        fun bind(item: SatelliteUiItem.Header) {
            tvTitle.text = item.title
        }
    }

    private class ValuesVH(v: View) : RecyclerView.ViewHolder(v) {
        private val flex: FlexboxLayout = v.findViewById(R.id.flexValues)

        fun bind(item: SatelliteUiItem.SignalValuesRow) {
            flex.removeAllViews()

            // 15 个值：按已有多少显示多少（最多 15），会自动换行，保证同屏内可见
            val values = item.values.map { SignalCn0WindowManager.format1(it) }

            for (s in values) {
                val tv = TextView(flex.context).apply {
                    text = s
                    textSize = 14f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding(10, 6, 10, 6)
                    setBackgroundResource(R.drawable.bg_value_chip)
                }

                val lp = FlexboxLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 8, 8) // chip 间距：右/下 8dp
                }

                flex.addView(tv, lp)
            }
        }
    }


    private class RowVH(v: View) : RecyclerView.ViewHolder(v) {
        private val tvLine1: TextView = v.findViewById(R.id.tvLine1)
        private val tvLine2: TextView = v.findViewById(R.id.tvLine2)

        fun bind(item: SatelliteUiItem.Row) {
            val sys = constellationName(item.constellation)
            val cn0 = SignalCn0WindowManager.format1(item.cn0DbHz)

            val freq = item.carrierFreqHz?.let { String.format("%.3f MHz", it / 1_000_000.0) } ?: "--"
            val az = item.azimuthDeg?.let { String.format("%.0f°", it) } ?: "--"
            val el = item.elevationDeg?.let { String.format("%.0f°", it) } ?: "--"
            val fix = if (item.usedInFix) "FIX" else "----"

            tvLine1.text = "$sys SVID=${item.svId}  ${item.signalLabel}  CN0=$cn0"
            tvLine2.text = "freq=$freq  az=$az  el=$el  $fix  code=${item.codeType ?: "--"}"
        }
    }

    private class ValueChipAdapter : ListAdapter<String, ValueChipAdapter.VH>(Diff) {
        companion object {
            private object Diff : DiffUtil.ItemCallback<String>() {
                override fun areItemsTheSame(oldItem: String, newItem: String) = oldItem == newItem
                override fun areContentsTheSame(oldItem: String, newItem: String) = oldItem == newItem
            }
        }

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            private val tv: TextView = v.findViewById(R.id.tvChip)
            fun bind(s: String) { tv.text = s }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_value_chip, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(getItem(position))
        }
    }
}
