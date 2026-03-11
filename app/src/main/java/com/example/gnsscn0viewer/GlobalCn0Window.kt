package com.example.gnsscn0viewer

/**
 * 全局CN0滑动窗口：收集所有卫星（所有制式）的 CN0（>0）流
 * 只保留最近 windowSize 个值，并计算平均值
 */
class GlobalCn0Window(private val windowSize: Int = 15) {
    private val values = ArrayDeque<Float>(windowSize)

    fun add(cn0: Float) {
        if (cn0 <= 0f) return // 你要求：CN0=0 自动排除
        if (values.size == windowSize) values.removeFirst()
        values.addLast(cn0)
    }

    fun isFull(): Boolean = values.size == windowSize

    fun count(): Int = values.size

    fun getValues(): List<Float> = values.toList()

    fun average(): Float {
        if (values.isEmpty()) return 0f
        var sum = 0f
        for (v in values) sum += v
        return sum / values.size
    }

    fun clear() {
        values.clear()
    }
}
