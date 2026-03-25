package com.example.gnsscn0viewer

import java.util.ArrayDeque
import kotlin.math.round

/**
 * 按 “星座 + 信号标签(例如 GPS L1C/A, GPS L5, BDS B1i...)” 分组
 * 每组维护一个长度为 windowSize 的滑动窗口（先进先出）。
 */
class SignalCn0WindowManager(
    private val windowSize: Int = 15
) {
    data class SignalKey(
        val constellation: Int,
        val signalLabel: String,
        val codeKey: String // 新增：用来区分 C / L
    )


    data class Snapshot(
        val key: SignalKey,
        val values: List<Double>,
        val average: Double?,   // 未满 windowSize 时也可以算平均（按当前已有 n 个算）
        val count: Int
    )

    private class Window(private val windowSize: Int) {
        private val q = ArrayDeque<Double>(windowSize)

        fun push(v: Double) {
            if (q.size == windowSize) q.removeFirst()
            q.addLast(v)
        }

        fun values(): List<Double> = q.toList()

        fun avg(): Double? {
            if (q.isEmpty()) return null
            return q.sum() / q.size.toDouble()
        }

        fun count(): Int = q.size
    }

    private val map = linkedMapOf<SignalKey, Window>()

    fun push(constellation: Int, signalLabel: String, codeKey: String, cn0DbHz: Double) {
        val key = SignalKey(constellation, signalLabel, codeKey)
        val w = map.getOrPut(key) { Window(windowSize) }
        w.push(cn0DbHz)
    }


    fun snapshotAll(): List<Snapshot> {
        return map.entries.map { (k, w) ->
            Snapshot(
                key = k,
                values = w.values(),
                average = w.avg(),
                count = w.count()
            )
        }
    }

    fun getWindowSize(): Int = windowSize

    fun clearAll() {
        map.clear()
    }

    companion object {
        fun format1(v: Double): String = String.format("%.1f", v)
        fun formatAvg4(v: Double?): String = if (v == null) "--" else String.format("%.4f", v)
    }
}
