package com.wifictl.app.util

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtil {
    /** Локальный IPv4-адрес устройства в WiFi/LAN (или 0.0.0.0, если не найден). */
    fun localIpv4(): String {
        try {
            for (iface in NetworkInterface.getNetworkInterfaces()) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress ?: continue
                    }
                }
            }
        } catch (_: Exception) {
        }
        return "0.0.0.0"
    }
}
