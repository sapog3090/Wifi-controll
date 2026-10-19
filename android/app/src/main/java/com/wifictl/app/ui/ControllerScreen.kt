package com.wifictl.app.ui

import android.content.Context
import android.net.nsd.NsdManager
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wifictl.app.core.Pairing
import com.wifictl.app.core.PairingPayload
import com.wifictl.app.net.ControllerClient
import com.wifictl.app.net.DiscoveredAgent
import com.wifictl.app.net.Discovery
import com.wifictl.app.store.Prefs
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ControllerScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val scope = rememberCoroutineScope()

    var devices by remember { mutableStateOf(prefs.savedDevices()) }
    val discovered = remember { mutableStateListOf<DiscoveredAgent>() }
    var client by remember { mutableStateOf<ControllerClient?>(null) }
    var connectedName by remember { mutableStateOf<String?>(null) }
    var allowArbitrary by remember { mutableStateOf(false) }
    val output = remember { mutableStateListOf<String>() }
    var status by remember { mutableStateOf("Отсканируйте QR на телефоне-агенте для сопряжения") }
    var command by remember { mutableStateOf("") }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        when {
            contents == null -> {}
            Pairing.isPairingString(contents) -> {
                val p = runCatching { Pairing.decode(contents) }.getOrNull()
                if (p != null) {
                    prefs.saveDevice(p)
                    devices = prefs.savedDevices()
                    status = "Сопряжено: ${p.name}"
                } else {
                    status = "Не удалось разобрать QR"
                }
            }
            else -> status = "Это не QR сопряжения WifiCtl"
        }
    }

    DisposableEffect(Unit) {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val disc = Discovery(nsd)
        val main = Handler(Looper.getMainLooper())
        disc.startDiscovery { agent ->
            main.post {
                if (discovered.none { it.host == agent.host && it.port == agent.port }) {
                    discovered.add(agent)
                }
            }
        }
        onDispose { disc.stopDiscovery() }
    }

    fun connect(payload: PairingPayload) {
        scope.launch {
            try {
                client?.close()
                val c = ControllerClient.connect(payload.host, payload.port, payload.token, payload.fingerprint)
                client = c
                connectedName = payload.name
                allowArbitrary = c.allowArbitrary
                output.clear()
                status = "Подключено: ${payload.name}" + if (c.allowArbitrary) " (разрешены произвольные)" else ""
            } catch (e: Exception) {
                status = "Ошибка подключения: ${e.message}"
            }
        }
    }

    fun runReq(kind: String, value: String, params: Map<String, String> = emptyMap()) {
        val c = client ?: return
        scope.launch {
            try {
                if (value.isNotEmpty()) output.add("$ $value")
                val res = c.run(kind, value, params) { frame -> output.add(frame.data.trimEnd('\n')) }
                if (res.error.isNotEmpty()) output.add("! ${res.error}")
                else if (res.exitCode != 0) output.add("[код возврата ${res.exitCode}]")
            } catch (e: Exception) {
                status = "Связь потеряна: ${e.message}"
                client = null
                connectedName = null
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Контроллер", fontFamily = FontFamily.SansSerif)
        Spacer(Modifier.height(4.dp))
        Text(status)
        Spacer(Modifier.height(12.dp))

        Button(onClick = {
            scanLauncher.launch(
                ScanOptions()
                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    .setPrompt("Наведите на QR телефона-агента")
                    .setBeepEnabled(false)
                    .setOrientationLocked(false),
            )
        }) { Text("Сканировать QR сопряжения") }

        Spacer(Modifier.height(16.dp))
        Text("Сохранённые устройства")
        if (devices.isEmpty()) Text("— пока нет —")
        devices.forEach { dev ->
            DeviceRow(dev.name, "${dev.host}:${dev.port}", onConnect = { connect(dev) }, onRemove = {
                prefs.removeDevice(dev.name)
                devices = prefs.savedDevices()
            })
        }

        if (discovered.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Найдены в сети (mDNS)")
            discovered.forEach { agent ->
                val known = devices.firstOrNull { it.name == agent.name }
                DeviceRow(
                    agent.name,
                    "${agent.host}:${agent.port}" + if (known == null) " · нужно сопряжение" else "",
                    onConnect = {
                        if (known != null) {
                            connect(known.copy(host = agent.host, port = agent.port))
                        } else {
                            status = "Для ${agent.name} сначала отсканируйте QR (нужен токен)"
                        }
                    },
                    onRemove = null,
                )
            }
        }

        if (client != null) {
            Spacer(Modifier.height(16.dp))
            Divider()
            Spacer(Modifier.height(8.dp))
            Text("Макросы на «${connectedName}»")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { runReq("list", "") }) { Text(":макросы") }
                listOf("root", "net", "battery", "full").forEach { m ->
                    Button(onClick = { runReq("macro", m) }) { Text(m) }
                }
            }

            if (allowArbitrary) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = command,
                        onValueChange = { command = it },
                        modifier = Modifier.fillMaxWidth(0.72f),
                        label = { Text("Команда") },
                        singleLine = true,
                    )
                    Spacer(Modifier.fillMaxWidth(0.04f))
                    Button(onClick = {
                        if (command.isNotBlank()) {
                            runReq("command", command.trim())
                            command = ""
                        }
                    }) { Text("→") }
                }
            }

            Spacer(Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 320.dp)) {
                Column(Modifier.padding(8.dp).verticalScroll(rememberScrollState())) {
                    if (output.isEmpty()) Text("— вывод появится здесь —")
                    output.forEach { line ->
                        Text(line, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(title: String, subtitle: String, onConnect: () -> Unit, onRemove: (() -> Unit)?) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.fillMaxWidth(0.55f)) {
                Text(title)
                Text(subtitle, fontSize = 12.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = onConnect) { Text("Подключиться") }
                if (onRemove != null) OutlinedButton(onClick = onRemove) { Text("✕") }
            }
        }
    }
}
