package com.caburum.tasmotaqs

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.stream.Collectors

class TasmotaManager {
	private var ip = "192.168.123.165"
	private var port = "80"
	private var allowedNetworks: Array<String> =
		arrayOf("andy-5.0", "andy-2.4", "kate-5.0", "kate-2.4", "calum-5.0", "calum-2.4")

	fun isCorrectNetwork(context: Context): Boolean {
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

	fun doRequest(cmnd: String, callback: (JSONObject) -> Unit) {
		// fixme:
		val policy = ThreadPolicy.Builder().permitAll().build()
		StrictMode.setThreadPolicy(policy)

		val url = URL("http://$ip:$port/cm?cmnd=$cmnd")

		val conn = url.openConnection() as HttpURLConnection;
		try {
			conn.requestMethod = "GET";
			conn.connect();
			val jsonStr = BufferedReader(InputStreamReader(conn.inputStream)).lines().collect(
				Collectors.joining()
			)
			Log.d("doRequest", jsonStr)
			val json = JSONObject(jsonStr)
			callback(json);
		} finally {
			conn.disconnect();
		}
	}

	fun getOnState(callback: (List<Boolean>) -> Unit) {
		doRequest("power0") {
			callback(listOf<Boolean>("ON" == it.get("POWER1"), "ON" == it.get("POWER2")));
		}
	}
}