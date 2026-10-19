package com.wifictl.app.agent

import com.wifictl.app.core.PairingPayload
import kotlinx.coroutines.flow.MutableStateFlow

/** Наблюдаемое состояние агента для UI. */
object AgentState {
    val running = MutableStateFlow(false)
    val pairing = MutableStateFlow<PairingPayload?>(null)
    val logs = MutableStateFlow<List<String>>(emptyList())

    fun log(line: String) {
        val stamped = "${timeNow()}  $line"
        logs.value = (logs.value + stamped).takeLast(200)
    }

    fun reset() {
        running.value = false
        pairing.value = null
    }

    private fun timeNow(): String {
        val now = java.util.Calendar.getInstance()
        return "%02d:%02d:%02d".format(
            now.get(java.util.Calendar.HOUR_OF_DAY),
            now.get(java.util.Calendar.MINUTE),
            now.get(java.util.Calendar.SECOND),
        )
    }
}
