package com.example.gnsscn0viewer

import android.location.GnssStatus

sealed class SatelliteUiItem {
    data class Header(
        val title: String
    ) : SatelliteUiItem()

    /**
     * 新增：每个信号组（GPS L1C/A 等）Header 下面紧跟的一行横向窗口
     */
    data class SignalValuesRow(
        val constellation: Int,
        val signalLabel: String,
        val codeKey: String,      // 新增
        val values: List<Double>,
        val average: Double?,
        val count: Int,
        val windowSize: Int
    ) : SatelliteUiItem()


    /**
     * 你原来的卫星行（保持功能：svId、cn0、az/el、usedInFix、freq、codeType）
     */
    data class Row(
        val constellation: Int,
        val svId: Int,

        val signalLabel: String,       // 例如 GPS L1C/A / GPS L5 / BDS B1i ...
        val codeType: String?,         // 原始 codeType（可用于调试）
        val carrierFreqHz: Double?,    // 载波频率（Hz）

        val cn0DbHz: Double,           // CN0
        val azimuthDeg: Float?,        // 方位角
        val elevationDeg: Float?,      // 仰角
        val usedInFix: Boolean
    ) : SatelliteUiItem()
}

fun constellationName(c: Int): String = when (c) {
    GnssStatus.CONSTELLATION_GPS -> "GPS"
    GnssStatus.CONSTELLATION_BEIDOU -> "BDS"
    GnssStatus.CONSTELLATION_GLONASS -> "GLONASS"
    GnssStatus.CONSTELLATION_GALILEO -> "Galileo"
    GnssStatus.CONSTELLATION_SBAS -> "SBAS"
    GnssStatus.CONSTELLATION_QZSS -> "QZSS"
    GnssStatus.CONSTELLATION_IRNSS -> "IRNSS"
    else -> "Other($c)"
}
