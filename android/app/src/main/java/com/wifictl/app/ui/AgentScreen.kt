package com.wifictl.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wifictl.app.agent.AgentService
import com.wifictl.app.agent.AgentState
import com.wifictl.app.core.Pairing
import com.wifictl.app.util.QrUtil

@Composable
fun AgentScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val running by AgentState.running.collectAsState()
    val pairing by AgentState.pairing.collectAsState()
    val logs by AgentState.logs.collectAsState()
    var allowArbitrary by remember { mutableStateOf(false) }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* старт всё равно пробуем */ AgentService.start(context, allowArbitrary) }

    fun startAgent() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            AgentService.start(context, allowArbitrary)
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Агент (это устройство)")
        Spacer(Modifier.height(4.dp))
        Text(
            "Включая агент, вы разрешаете выполнять команды на ЭТОМ устройстве тем, " +
                "у кого есть QR/токен. Включайте только осознанно и для доверенных людей.",
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = allowArbitrary, onCheckedChange = { allowArbitrary = it }, enabled = !running)
            Spacer(Modifier.size(8.dp))
            Text("Разрешать произвольные команды (не только макросы)", fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))

        if (!running) {
            Button(onClick = { startAgent() }) { Text("Включить агент") }
        } else {
            Button(onClick = { AgentService.stop(context) }) { Text("Выключить агент") }
        }

        val p = pairing
        if (running && p != null) {
            Spacer(Modifier.height(16.dp))
            Text("Покажите этот QR контроллеру для сопряжения:")
            Spacer(Modifier.height(8.dp))
            val qr = remember(p) { QrUtil.generate(Pairing.encode(p)) }
            Image(bitmap = qr.asImageBitmap(), contentDescription = "QR сопряжения", modifier = Modifier.size(240.dp))
            Spacer(Modifier.height(8.dp))
            Text("Адрес: ${p.host}:${p.port}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            Text("Отпечаток: ${p.fingerprint.take(16)}…", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }

        Spacer(Modifier.height(16.dp))
        Text("Журнал")
        Card(modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 300.dp)) {
            Column(Modifier.padding(8.dp).verticalScroll(rememberScrollState())) {
                if (logs.isEmpty()) Text("— пусто —")
                logs.forEach { line ->
                    Text(line, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
        }
    }
}
