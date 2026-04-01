package com.example.mratsmastudenttracker.ui.utils

import android.util.Log
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URL
import java.util.*

object NetworkUtils {
    fun getLocalIpAddress(): String? {
        try {
            val interfaces: List<NetworkInterface> = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs: List<InetAddress> = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        val sAddr = addr.hostAddress
                        val isIPv4 = sAddr.indexOf(':') < 0
                        if (isIPv4) return sAddr
                    }
                }
            }
        } catch (ex: Exception) {
            Log.e("NetworkUtils", "Error getting local IP", ex)
        }
        return null
    }

    suspend fun getMyPublicIpAsync() : Deferred<String> =
        coroutineScope {
            async(Dispatchers.IO) {
                try {
                    val url = URL("https://api.ipify.org")
                    val connection = url.openConnection()
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    connection.getInputStream().use { iStream ->
                        val buff = ByteArray(1024)
                        val read = iStream.read(buff)
                        if (read > 0) String(buff, 0, read).trim() else "unknown"
                    }
                } catch (e: Exception) {
                    "error : ${e.message}"
                }
            }
        }
}
