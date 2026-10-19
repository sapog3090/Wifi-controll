package com.wifictl.app.net

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

/** Найденный в сети агент (через mDNS/NSD). */
data class DiscoveredAgent(
    val name: String,
    val host: String,
    val port: Int,
    val fingerprint: String?,
)

/** Регистрация и поиск агентов в локальной сети через Android NSD. */
class Discovery(private val nsd: NsdManager) {

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    companion object {
        const val SERVICE_TYPE = "_wifictl._tcp."
    }

    /** Объявить этого агента в сети. */
    fun register(name: String, port: Int, fingerprint: String) {
        unregister()
        val info = NsdServiceInfo().apply {
            serviceName = name
            serviceType = SERVICE_TYPE
            setPort(port)
            setAttribute("fp", fingerprint)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onRegistrationFailed(si: NsdServiceInfo?, errorCode: Int) {}
            override fun onUnregistrationFailed(si: NsdServiceInfo?, errorCode: Int) {}
            override fun onServiceRegistered(si: NsdServiceInfo?) {}
            override fun onServiceUnregistered(si: NsdServiceInfo?) {}
        }
        registrationListener = listener
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun unregister() {
        registrationListener?.let {
            try {
                nsd.unregisterService(it)
            } catch (_: Exception) {
            }
        }
        registrationListener = null
    }

    /** Начать поиск; [onFound] вызывается для каждого разрешённого агента. */
    fun startDiscovery(onFound: (DiscoveredAgent) -> Unit) {
        stopDiscovery()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {}
            override fun onDiscoveryStarted(serviceType: String?) {}
            override fun onDiscoveryStopped(serviceType: String?) {}

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(si: NsdServiceInfo?, errorCode: Int) {}
                    override fun onServiceResolved(si: NsdServiceInfo) {
                        val fp = si.attributes["fp"]?.let { String(it, Charsets.UTF_8) }
                        val host = si.host?.hostAddress ?: return
                        onFound(DiscoveredAgent(si.serviceName ?: "агент", host, si.port, fp))
                    }
                })
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo?) {}
        }
        discoveryListener = listener
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun stopDiscovery() {
        discoveryListener?.let {
            try {
                nsd.stopServiceDiscovery(it)
            } catch (_: Exception) {
            }
        }
        discoveryListener = null
    }
}
