package com.example.gnsscn0viewer

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssMeasurementsEvent
import android.location.GnssStatus
import android.location.LocationManager
import android.os.Bundle
import android.os.SystemClock
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

//消除闪烁
import android.os.Handler
import android.os.Looper

class MainActivity : AppCompatActivity() {

    private lateinit var rv: RecyclerView
    private val adapter = SatelliteAdapter()
    private lateinit var btnClearAssist: TextView
    private lateinit var topBar: android.view.View

    private lateinit var lm: LocationManager

    // ✅ 每个信号组一个 15 长度滑动窗口
    private val windowManager = SignalCn0WindowManager(windowSize = 15)

    //消除闪烁
    private val uiHandler = Handler(Looper.getMainLooper())
    private var pendingUi = false
    private var lastUiMs = 0L
    private var userIsScrolling = false
    private val gnssUiLock = Any()

    // UI 刷新最小间隔（毫秒）
    private val UI_MIN_INTERVAL_MS = 1000L


    // ✅ 本轮（最近一次）观测到的卫星行：按 “星座+信号标签” 分组存放，方便渲染
    private val lastRowsBySignal = linkedMapOf<SignalCn0WindowManager.SignalKey, MutableList<SatelliteUiItem.Row>>()

    // ✅ 运行时权限申请
    private val requestFineLocation = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startGnss()
        else {
            // 没权限就不注册监听，避免闪退
            renderUiEmpty("No location permission")
        }
    }

    private val measurementCallback = object : GnssMeasurementsEvent.Callback() {
        override fun onGnssMeasurementsReceived(eventArgs: GnssMeasurementsEvent) {
            synchronized(gnssUiLock) {
                // 清空本轮显示的行（滑动窗口不清空，它是跨刷新的）
                lastRowsBySignal.clear()

                for (m in eventArgs.measurements) {
                    val constellation = m.constellationType
                    val svId = m.svid

                    val cn0 = m.cn0DbHz.toDouble()
                    if (cn0 <= 0.0) continue // 你要求：CN0=0 排除

                    val freq =
                        if (m.hasCarrierFrequencyHz()) m.carrierFrequencyHz.toDouble() else null
                    val codeType = try { m.codeType } catch (_: Throwable) { null }

                    val label = classifySignal(constellation, freq, codeType)

                    val codeKey = when {
                        codeType.isNullOrBlank() -> "?"
                        codeType.uppercase().startsWith("C") -> "C"
                        codeType.uppercase().startsWith("L") -> "L"
                        else -> codeType.take(1).uppercase() // 兜底：取首字母
                    }

                    // 1) 更新该信号组的滑动窗口
                    windowManager.push(constellation, label, codeKey, cn0)

                    // 2) 生成卫星行（方位/仰角/usedInFix 如需可再接 GnssStatus 去补）
                    val row = SatelliteUiItem.Row(
                        constellation = constellation,
                        svId = svId,
                        signalLabel = label,
                        codeType = codeType,
                        carrierFreqHz = freq,
                        cn0DbHz = cn0,
                        azimuthDeg = null,
                        elevationDeg = null,
                        usedInFix = false
                    )

                    val key = SignalCn0WindowManager.SignalKey(constellation, label, codeKey)

                    lastRowsBySignal.getOrPut(key) { mutableListOf() }.add(row)
                }
            }

            requestRenderUi(force = false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        topBar = findViewById(R.id.topBar)
        rv = findViewById(R.id.rvMain)
        btnClearAssist = findViewById(R.id.btnClearAssist)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        rv.itemAnimator = null   // ✅ 就加在这里

        // 用户滚动时尽量不重建列表，避免“划上去又被刷新下去”
        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                userIsScrolling =
                    newState == RecyclerView.SCROLL_STATE_DRAGGING ||
                        newState == RecyclerView.SCROLL_STATE_SETTLING

                if (newState == RecyclerView.SCROLL_STATE_IDLE && pendingUi) {
                    pendingUi = false
                    requestRenderUi(force = false)
                }
            }
        })

        lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // 处理状态栏/刘海屏 inset，避免标题栏与系统状态栏重叠
        val baseTopPadding = topBar.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(topBar) { v, insets ->
            val topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(v.paddingLeft, baseTopPadding + topInset, v.paddingRight, v.paddingBottom)
            insets
        }
        ViewCompat.requestApplyInsets(topBar)

        // 处理底部导航栏（虚拟按键/手势条）inset，避免遮挡列表末尾内容
        val baseBottomPadding = rv.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(rv) { v, insets ->
            val bottomInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, baseBottomPadding + bottomInset)
            insets
        }
        ViewCompat.requestApplyInsets(rv)

        btnClearAssist.setOnClickListener {
            clearAssistData()
        }

        ensurePermissionThenStart()
    }

    private fun ensurePermissionThenStart() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        if (granted) startGnss()
        else requestFineLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    @SuppressLint("MissingPermission")
    private fun startGnss() {
        // ⚠️ 只有在权限已授予时才会进入这里
        try {
            lm.registerGnssMeasurementsCallback(measurementCallback)
        } catch (se: SecurityException) {
            renderUiEmpty("SecurityException: no permission")
        } catch (t: Throwable) {
            renderUiEmpty("Error: ${t.javaClass.simpleName}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            lm.unregisterGnssMeasurementsCallback(measurementCallback)
        } catch (_: Throwable) {}
    }

    private fun renderUiEmpty(msg: String) {
        adapter.submitList(listOf(SatelliteUiItem.Header(msg)))
    }

    @SuppressLint("MissingPermission")
    private fun clearAssistData() {
        val ok = try {
            // delete_aiding_data：清除 A-GNSS/辅助数据（不同机型/ROM 支持度不同）
            lm.sendExtraCommand(LocationManager.GPS_PROVIDER, "delete_aiding_data", Bundle())
        } catch (_: SecurityException) {
            false
        } catch (_: Throwable) {
            false
        }

        synchronized(gnssUiLock) {
            lastRowsBySignal.clear()
            windowManager.clearAll()
        }
        requestRenderUi(force = true)

        Toast.makeText(
            this,
            if (ok) "已请求清除 Assist Data（可能需要数秒生效）" else "清除 Assist Data 失败/不支持（已清空本地窗口）",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun requestRenderUi(force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (!force) {
            if (userIsScrolling) {
                pendingUi = true
                return
            }
            if (now - lastUiMs < UI_MIN_INTERVAL_MS) {
                pendingUi = true
                return
            }
        }

        pendingUi = false
        lastUiMs = now
        uiHandler.post { renderUi() }
    }

    private fun renderUi() {
        val items = mutableListOf<SatelliteUiItem>()
        synchronized(gnssUiLock) {
            val snapshots = windowManager.snapshotAll()
                .sortedWith(compareBy(
                    { constellationName(it.key.constellation) },
                    { it.key.signalLabel }
                ))

            for (snap in snapshots) {
                val key = snap.key
                val sys = constellationName(key.constellation)

                val title = buildString {
                    append("$sys · ${key.signalLabel} · code=${key.codeKey}")
                    append(
                        "    窗口 ${snap.count}/${windowManager.getWindowSize()} · 均值 ${SignalCn0WindowManager.formatAvg4(snap.average)}"
                    )
                }
                items.add(SatelliteUiItem.Header(title))

                items.add(
                    SatelliteUiItem.SignalValuesRow(
                        constellation = key.constellation,
                        signalLabel = key.signalLabel,
                        codeKey = key.codeKey,
                        values = snap.values,
                        average = snap.average,
                        count = snap.count,
                        windowSize = windowManager.getWindowSize()
                    )
                )

                val rows = lastRowsBySignal[key].orEmpty().sortedByDescending { it.cn0DbHz }
                items.addAll(rows)
            }

            if (items.isEmpty()) {
                items.add(SatelliteUiItem.Header("等待 GNSS 测量数据中（建议到室外测试）"))
            }
        }

        adapter.submitList(items)
    }

    private fun classifySignal(constellation: Int, freqHz: Double?, codeType: String?): String {
        val code = codeType?.uppercase()?.trim()

        fun near(mhz: Double, tolMHz: Double = 3.0): Boolean {
            val f = freqHz ?: return false
            return kotlin.math.abs(f / 1_000_000.0 - mhz) <= tolMHz
        }

        return when (constellation) {
            GnssStatus.CONSTELLATION_GPS -> {
                when {
                    near(1176.45) -> "L5"
                    near(1575.42) -> when {
                        code == null -> "L1"
                        code.startsWith("C1C") || code.startsWith("C1W") || code.startsWith("C1P") || code.startsWith("C1Y") -> "L1C/A"
                        code.startsWith("C1L") || code.startsWith("C1X") || code.startsWith("C1S") -> "L1C"
                        else -> "L1"
                    }
                    else -> "Other"
                }
            }
            GnssStatus.CONSTELLATION_BEIDOU -> when {
                near(1561.098) -> "B1i"
                near(1575.42) -> "B1c"
                near(1207.14) -> "B2i"
                near(1176.45) -> "B2a"
                else -> "Other"
            }
            GnssStatus.CONSTELLATION_GALILEO -> when {
                near(1575.42) -> "E1"
                near(1176.45) -> "E5a"
                near(1207.14) -> "E5b"
                else -> "Other"
            }
            GnssStatus.CONSTELLATION_GLONASS -> when {
                freqHz != null && freqHz / 1_000_000.0 in 1598.0..1608.0 -> "G1"
                freqHz != null && freqHz / 1_000_000.0 in 1242.0..1252.0 -> "G2"
                else -> "Other"
            }
            GnssStatus.CONSTELLATION_SBAS -> when {
                near(1176.45) -> "L5"
                near(1575.42) -> "L1"
                else -> "Other"
            }
            else -> "Other"
        }
    }
}
