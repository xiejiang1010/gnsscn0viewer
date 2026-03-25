package com.example.gnsscn0viewer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

import com.google.android.flexbox.FlexboxLayout
import android.graphics.drawable.GradientDrawable

class SatelliteAdapter : ListAdapter<SatelliteUiItem, RecyclerView.ViewHolder>(Diff) {

    companion object {
        private const val VT_HEADER = 1
        private const val VT_VALUES = 2
        private const val VT_ROW = 3

        private object Diff : DiffUtil.ItemCallback<SatelliteUiItem>() {
            override fun areItemsTheSame(oldItem: SatelliteUiItem, newItem: SatelliteUiItem): Boolean {
                // 关键：区分“同一个条目（复用 ViewHolder）”和“内容变化（需要重绑）”
                return when {
                    oldItem is SatelliteUiItem.Header && newItem is SatelliteUiItem.Header ->
                        oldItem.title == newItem.title

                    oldItem is SatelliteUiItem.SignalValuesRow && newItem is SatelliteUiItem.SignalValuesRow ->
                        oldItem.constellation == newItem.constellation &&
                            oldItem.signalLabel == newItem.signalLabel &&
                            oldItem.codeKey == newItem.codeKey

                    oldItem is SatelliteUiItem.Row && newItem is SatelliteUiItem.Row ->
                        oldItem.constellation == newItem.constellation &&
                            oldItem.svId == newItem.svId &&
                            oldItem.signalLabel == newItem.signalLabel &&
                            oldItem.codeType == newItem.codeType

                    else -> false
                }
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
                    setTextColor(ContextCompat.getColor(context, R.color.ui_primary_text))
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
        private val tvLevel: TextView = v.findViewById(R.id.tvLevel)
        private val tvLine2: TextView = v.findViewById(R.id.tvLine2)

        fun bind(item: SatelliteUiItem.Row) {
            val sys = constellationName(item.constellation)
            val cn0 = SignalCn0WindowManager.format1(item.cn0DbHz)

            val freq = item.carrierFreqHz?.let { String.format("%.3f MHz", it / 1_000_000.0) } ?: "--"
            val az = item.azimuthDeg?.let { String.format("%.0f°", it) } ?: "--"
            val el = item.elevationDeg?.let { String.format("%.0f°", it) } ?: "--"
            val fix = if (item.usedInFix) "FIX" else "----"
            val level = cn0Level(item.cn0DbHz)

            tvLine1.text = "$sys SVID=${item.svId}  ${item.signalLabel}  C/N0=$cn0 dB-Hz"
            tvLine2.text = "freq=$freq  az=$az  el=$el  $fix  code=${item.codeType ?: "--"}"
            tvLevel.text = level.label
            applyLevelStyle(tvLevel, level.bgColorRes, level.textColorRes)
        }

        private data class Cn0Level(val label: String, val bgColorRes: Int, val textColorRes: Int)

        private fun cn0Level(cn0: Double): Cn0Level {
            return when {
                cn0 >= 40.0 -> Cn0Level("优秀信号", R.color.cn0_good_bg, R.color.cn0_good_text)
                cn0 >= 30.0 -> Cn0Level("良好信号", R.color.cn0_good_bg, R.color.cn0_good_text)
                cn0 >= 22.0 -> Cn0Level("一般信号", R.color.cn0_mid_bg, R.color.cn0_mid_text)
                else -> Cn0Level("弱信号", R.color.cn0_low_bg, R.color.cn0_low_text)
            }
        }

        private fun applyLevelStyle(view: TextView, bgColorRes: Int, textColorRes: Int) {
            val context = view.context
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 999f
                setColor(ContextCompat.getColor(context, bgColorRes))
            }
            view.background = bg
            view.setTextColor(ContextCompat.getColor(context, textColorRes))
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
