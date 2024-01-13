package com.caburum.tasmotaqs

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.util.Log
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.stream.Collectors

class TasmotaManager {
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

	@Throws(TasmotaException::class)
	fun doRequest(context: Context, cmnd: String, callback: (JSONObject) -> Unit) {
		// fixme:
		val policy = ThreadPolicy.Builder().permitAll().build()
		StrictMode.setThreadPolicy(policy)

		var address: String
		var auth: String = "" // will become url query params
		runBlocking {
			address = context.dataStore.data.first()[TASMOTA_ADDRESS] ?: ""
			val enableAuth = context.dataStore.data.first()[TASMOTA_AUTH_ENABLE] ?: false
			if (enableAuth) {
				val user = context.dataStore.data.first()[TASMOTA_AUTH_USER] ?: ""
				val pass = context.dataStore.data.first()[TASMOTA_AUTH_PASS] ?: ""
				auth = "&user=$user&password=$pass"
			}
		}

		val cmndEnc = Uri.encode(cmnd);
		val urlString = "http://$address/cm?cmnd=$cmndEnc$auth"
		Log.i("urlString", urlString)
		val url = URL(urlString)

		val conn = url.openConnection() as HttpURLConnection
		try {
			conn.requestMethod = "GET"
			conn.connectTimeout = 5000
			conn.connect()
			val jsonStr = BufferedReader(InputStreamReader(conn.inputStream)).lines().collect(
				Collectors.joining()
			)
			Log.d("doRequest", jsonStr)
			val json = JSONObject(jsonStr)
			callback(json)
		} catch (e: Exception) {
			Log.e("doRequest", "caught exception: " + e.message, e)
			throw TasmotaException(e)
			// todo: show toast? or somehow show info to user
		} finally {
			conn.disconnect()
		}
	}

	@Throws(TasmotaException::class)
	fun getOnState(context: Context, callback: (List<Boolean>) -> Unit) {
		doRequest(context, "power0") {
			callback(listOf<Boolean>("ON" == it.get("POWER1"), "ON" == it.get("POWER2")))
		}
	}
}