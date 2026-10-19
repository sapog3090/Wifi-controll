package com.wifictl.app.agent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.nsd.NsdManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.wifictl.app.core.MacroEngine
import com.wifictl.app.core.PairingPayload
import com.wifictl.app.net.AgentConfig
import com.wifictl.app.net.AgentServer
import com.wifictl.app.net.Discovery
import com.wifictl.app.store.Prefs
import com.wifictl.app.util.NetworkUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground-сервис агента: держит TLS-сервер и объявляет устройство в сети,
 * пока пользователь явно включил режим «агент». Постоянное уведомление делает
 * режим видимым (как сервис Shizuku).
 */
class AgentService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var server: AgentServer? = null
    private var discovery: Discovery? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundCompat()
        if (server == null) start()
        return START_STICKY
    }

    private fun start() {
        val prefs = Prefs(this)
        val identity = prefs.agentIdentity()
        val token = prefs.agentToken()
        val engine = MacroEngine.defaults()

        val allowArbitrary = intent_allowArbitrary
        val config = AgentConfig(port = PORT, token = token, allowArbitrary = allowArbitrary)

        val pairing = PairingPayload(
            name = Build.MODEL ?: "android",
            host = NetworkUtil.localIpv4(),
            port = PORT,
            token = token,
            fingerprint = identity.fingerprint,
        )
        AgentState.pairing.value = pairing
        AgentState.running.value = true
        AgentState.log("Агент запущен на ${pairing.host}:$PORT")

        acquireLocks()

        val srv = AgentServer(identity, config, engine) { line -> AgentState.log(line) }
        server = srv

        val nsd = getSystemService(Context.NSD_SERVICE) as NsdManager
        val disc = Discovery(nsd)
        discovery = disc
        try {
            disc.register(pairing.name, PORT, identity.fingerprint)
        } catch (e: Exception) {
            AgentState.log("mDNS недоступен: ${e.message}")
        }

        scope.launch {
            try {
                srv.serve(scope)
            } catch (e: Exception) {
                AgentState.log("Сервер остановлен: ${e.message}")
            }
        }
    }

    private fun acquireLocks() {
        try {
            val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifi.createMulticastLock("wifictl").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) {
        }
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "wifictl:agent").apply {
                setReferenceCounted(false)
                acquire(12 * 60 * 60 * 1000L)
            }
        } catch (_: Exception) {
        }
    }

    private fun startForegroundCompat() {
        val channelId = "wifictl_agent"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Агент WifiCtl", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(channel)
        }
        val notification: Notification = Notification.Builder(this, channelId)
            .setContentTitle("WifiCtl: агент активен")
            .setContentText("Устройство принимает команды по локальной сети")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    override fun onDestroy() {
        AgentState.log("Агент остановлен")
        server?.stop()
        discovery?.unregister()
        discovery?.stopDiscovery()
        try {
            multicastLock?.release()
        } catch (_: Exception) {
        }
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {
        }
        scope.cancel()
        AgentState.reset()
        super.onDestroy()
    }

    // allow_arbitrary прокидывается через статическое поле (устанавливается UI перед стартом)
    private val intent_allowArbitrary: Boolean
        get() = allowArbitrary

    companion object {
        const val PORT = 8765
        private const val NOTIF_ID = 42
        const val ACTION_STOP = "com.wifictl.app.STOP"

        /** Разрешать ли произвольные команды (ставит UI перед запуском). */
        @Volatile
        var allowArbitrary: Boolean = false

        fun start(context: Context, allowArbitrary: Boolean) {
            this.allowArbitrary = allowArbitrary
            val intent = Intent(context, AgentService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(Intent(context, AgentService::class.java).apply { action = ACTION_STOP })
        }
    }
}
