package com.caburum.tasmotaqs

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.stream.Collectors

/**
 * Creates an instance that will share settings values
 */
class TasmotaManager(private val context: Context) {
	companion object {
		fun isCorrectNetwork(context: Context): Boolean {
			val emptyStringList: List<String> = emptyList()
			val allowedNetworks: Collection<String> = runBlocking {
				context.dataStore.data.first()[ALLOWED_NETWORKS] ?: emptyStringList
			}

			val connectivityManager =
				context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
			val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager

			val network = connectivityManager.activeNetwork
			val networkCapabilities = connectivityManager.getNetworkCapabilities(network)

			if (networkCapabilities != null && networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
				val wifiInfo = wifiManager.connectionInfo
				Log.d("isCorrectNetwork", wifiInfo.ssid)
				return allowedNetworks.contains(wifiInfo.ssid.removeSurrounding("\""))
			}

			return false
		}
	}

	// todo: ensure not leaking
	private val executor = Executors.newCachedThreadPool(Executors.defaultThreadFactory())

	private data class UrlSettings(val address: String, val authQuery: String)

	private val urlSettingsFuture: Future<UrlSettings> = executor.submit(Callable {
		runBlocking {
			val address = context.dataStore.data.first()[TASMOTA_ADDRESS] ?: ""
			var auth = "" // will become url query params
			val enableAuth = context.dataStore.data.first()[TASMOTA_AUTH_ENABLE] ?: false
			if (enableAuth) {
				val user = context.dataStore.data.first()[TASMOTA_AUTH_USER] ?: ""
				val pass = context.dataStore.data.first()[TASMOTA_AUTH_PASS] ?: ""
				auth = "&user=$user&password=$pass"
			}
			return@runBlocking UrlSettings(address, auth)
		}
	})

	private var errorToast: Toast? = null

	fun doRequestAsync(cmnd: String): CompletableFuture<JSONObject> {
		return CompletableFuture.supplyAsync(supplier@{
			Log.d(
				"doRequestAsync",
				"running on ${if (Looper.getMainLooper().thread != Thread.currentThread()) "non-main" else "main"} thread #${Thread.currentThread().id}/${Thread.activeCount()}"
			)

			val cmndEnc = Uri.encode(cmnd)
			val urlSettings = urlSettingsFuture.get()
			val urlString = "http://${urlSettings.address}/cm?cmnd=$cmndEnc${urlSettings.authQuery}"
			Log.d("urlString", urlString)
			val url = URL(urlString)

			val conn = url.openConnection() as HttpURLConnection
			try {
				conn.requestMethod = "GET"
				conn.connectTimeout = 5000
				conn.connect()
				val jsonStr = BufferedReader(InputStreamReader(conn.inputStream)).lines().collect(
					Collectors.joining()
				)
//				Log.d("doRequest response", jsonStr)
				return@supplier JSONObject(jsonStr)
			} catch (e: Exception) {
				Log.e("doRequest", "caught exception: " + e.message, e)
				ContextCompat.getMainExecutor(context).execute {
					errorToast?.cancel() // prevent toast queue from stacking up
					errorToast = Toast.makeText(context, e.message, Toast.LENGTH_SHORT)
					errorToast?.show()
				}
				throw TasmotaException(e)
			} finally {
				conn.disconnect()
			}
		}, executor)
	}

	fun getOnState(): CompletableFuture<List<Boolean>> {
		return doRequestAsync("power0").thenApplyAsync {
			return@thenApplyAsync listOf("ON" == it.get("POWER1"), "ON" == it.get("POWER2"))
		}
	}
}