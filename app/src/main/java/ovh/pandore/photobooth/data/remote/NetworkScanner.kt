package ovh.pandore.photobooth.data.remote

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Scanne le sous-réseau local /24 de la tablette pour détecter automatiquement
 * un serveur IP WebCam actif (test via GET http://<ip>:8080/videostatus).
 *
 * Stratégie :
 *  1. Récupère l'IP locale via WifiManager pour déduire le préfixe /24.
 *  2. Lance 254 requêtes HTTP en parallèle (timeouts 500 ms) sur le port 8080.
 *  3. Retourne la première URL répondant avec un code 2xx, ou null si aucune trouvée.
 */
class NetworkScanner(private val context: Context) {

    /**
     * Cherche le premier hôte du réseau local qui répond positivement à
     * GET http://<ip>:8080/videostatus.
     *
     * @return URL de base du serveur trouvé (ex. "http://192.168.1.42:8080") ou null.
     */
    suspend fun findVideoServer(): String? = withContext(Dispatchers.IO) {
        val subnet = getLocalSubnetPrefix() ?: return@withContext null
        var found: String? = null

        try {
            coroutineScope {
                (1..254).forEach { i ->
                    launch {
                        val ip = "$subnet.$i"
                        val result = checkHost(ip)
                        if (result != null) {
                            found = result
                            // Annule immédiatement tous les autres jobs dès qu'on a trouvé
                            this@coroutineScope.cancel()
                        }
                    }
                }
            }
        } catch (_: CancellationException) {
            // Attendu : on annule le scope dès qu'un serveur est trouvé
        }

        found
    }

    /**
     * Teste si l'hôte répond à GET http://<ip>:8080/videostatus.
     * @return URL de base "http://<ip>:8080" ou null.
     */
    private fun checkHost(ip: String): String? {
        return try {
            val request = Request.Builder()
                .url("http://$ip:8080/videostatus")
                .get()
                .build()
            NetworkClient.scanClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) "http://$ip:8080" else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Lit l'IP locale via WifiManager et retourne le préfixe /24
     * (ex. "192.168.1" pour une IP 192.168.1.45).
     * Retourne null si la tablette n'est pas connectée en Wi-Fi.
     */
    private fun getLocalSubnetPrefix(): String? {
        return try {
            @Suppress("DEPRECATION")
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val ipInt = wifiManager.connectionInfo.ipAddress
            if (ipInt == 0) return null

            // ipInt est en little-endian sur Android
            val b0 = ipInt and 0xff
            val b1 = (ipInt shr 8) and 0xff
            val b2 = (ipInt shr 16) and 0xff
            "$b0.$b1.$b2"
        } catch (_: Exception) {
            null
        }
    }
}

