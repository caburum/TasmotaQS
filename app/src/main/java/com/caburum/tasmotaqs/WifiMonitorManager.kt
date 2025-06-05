import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.caburum.tasmotaqs.ALLOWED_NETWORKS
import com.caburum.tasmotaqs.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object WifiMonitorManager {
	private const val TAG = "WifiMonitorManager"

	@Volatile
	private var connectivityManager: ConnectivityManager? = null
	private var networkCallback: ConnectivityManager.NetworkCallback? = null
	private var dataStore: DataStore<Preferences>? = null

	// Coroutine scope for DataStore operations and other background tasks within the manager
	private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

	private val listenersLock = Any()

	private val connectionStatusListeners = mutableSetOf<(Boolean) -> Unit>()

	@Volatile
	private var currentConnectedSsid: String? = null
		private set(value) {
			field = value
			evaluateConnectionAgainstLoadedAllowedNetworks()
		}

	@Volatile
	private var isConnectedToAllowedNetwork: Boolean = false
		private set(value) {
			if (field != value) {
				field = value
				notifyListeners()
			}
		}

	@Volatile
	private var allowedNetworkSsidsInternal: Set<String> = emptySet()

	// Call this from your Application's onCreate
	fun initialize(applicationContext: Context) {
		if (connectivityManager == null) {
			synchronized(this) {
				if (connectivityManager == null) {
					Log.i(TAG, "Initializing WifiMonitorManager.")
					this.dataStore = applicationContext.dataStore
					connectivityManager =
						applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

					// Fetch initial allowed networks and then start listening
					refreshAllowedNetworksAndListen(applicationContext)
				}
			}
		} else {
			shutdown()
			refreshAllowedNetworksAndListen(applicationContext)
		}
	}

	private fun refreshAllowedNetworksAndListen(appContext: Context) {
		managerScope.launch {
			dataStore!!.data
				.map { preferences ->
					preferences[ALLOWED_NETWORKS]?.toSet() ?: emptySet()
				}
				.distinctUntilChanged()
				.collect { newAllowedSsids ->
					Log.i(
						TAG,
						"DataStore observation: Allowed networks updated to: $newAllowedSsids"
					)
					allowedNetworkSsidsInternal = newAllowedSsids
					evaluateConnectionAgainstLoadedAllowedNetworks()
				}
		}
		managerScope.launch {
			// Now that allowed networks are fetched (or defaults used), start network listening
			withContext(Dispatchers.Main) { // Or appropriate dispatcher for CM
				startNetworkListeningInternal(appContext)
			}
		}
	}

	private fun evaluateConnectionAgainstLoadedAllowedNetworks() {
		isConnectedToAllowedNetwork =
			currentConnectedSsid != null && allowedNetworkSsidsInternal.contains(
				currentConnectedSsid
			)
//		Log.d(TAG, "Evaluating connection against allowed networks: $allowedNetworkSsidsInternal")
//		Log.d(TAG, "Connection status: $isConnectedToAllowedNetwork")
	}

	private fun startNetworkListeningInternal(appContext: Context) {
		if (networkCallback != null) { // Avoid re-registering if already listening
			Log.d(TAG, "Network listener already active.")
			return
		}

		if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION)
			!= PackageManager.PERMISSION_GRANTED
		) {
			Log.w(TAG, "ACCESS_FINE_LOCATION permission not granted.")
		}
		if (ContextCompat.checkSelfPermission(
				appContext,
				Manifest.permission.ACCESS_BACKGROUND_LOCATION
			)
			!= PackageManager.PERMISSION_GRANTED
		) {
			Log.w(TAG, "ACCESS_BACKGROUND_LOCATION permission not granted.")
		}

		val networkRequest = NetworkRequest.Builder()
			.addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
			.build()

		networkCallback = object : ConnectivityManager.NetworkCallback(
			FLAG_INCLUDE_LOCATION_INFO
		) {
			override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
				super.onCapabilitiesChanged(network, caps)
				Log.d(TAG, "Manager: Capabilities changed $caps")
				handleCapabilitiesChanged(caps)
			}

			override fun onLost(network: Network) {
				super.onLost(network)
				Log.d(TAG, "Manager: Network lost $network")
//				evaluateCurrentConnectionStatus() // Re-check active network
				currentConnectedSsid = null
			}

			override fun onUnavailable() {
				super.onUnavailable()
				Log.d(TAG, "Manager: Callback unavailable")
				currentConnectedSsid = null
			}
		}

		try {
			connectivityManager?.registerNetworkCallback(networkRequest, networkCallback!!)
			Log.d(TAG, "WifiMonitorManager registered network callback.")
//			evaluateCurrentConnectionStatus() // Initial check for current network status
		} catch (e: Exception) {
			Log.e(TAG, "Exception registering network callback: ${e.message}")
			currentConnectedSsid = null
		}
	}

	private fun handleCapabilitiesChanged(networkCapabilities: NetworkCapabilities) {
		if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
			val transportInfo = networkCapabilities.transportInfo
			if (transportInfo is WifiInfo) {
				val rawSsid = transportInfo.ssid
				if (rawSsid != null && rawSsid != WifiManager.UNKNOWN_SSID) {
					currentConnectedSsid = rawSsid.removeSurrounding("\"")
				} else {
					Log.d(TAG, "Still connected, reusing old SSID")
				}
			}
		} else {
			Log.d(TAG, "Not connected to WiFi")
			currentConnectedSsid = null
		}
		Log.d(
			TAG,
			"Current SSID: $currentConnectedSsid, IsAllowed: $isConnectedToAllowedNetwork. Allowed list: $allowedNetworkSsidsInternal"
		)
	}

	fun addConnectionStatusListener(listener: (Boolean) -> Unit) {
		synchronized(listenersLock) {
			connectionStatusListeners.add(listener)
		}
		listener(isConnectedToAllowedNetwork)
	}

	fun removeConnectionStatusListener(listener: (Boolean) -> Unit) {
		synchronized(listenersLock) {
			connectionStatusListeners.remove(listener)
		}
	}

	private fun notifyListeners() {
		val currentListeners: List<(Boolean) -> Unit>
		synchronized(listenersLock) {
			currentListeners = connectionStatusListeners.toList()
		}
		currentListeners.forEach { listener ->
			// Consider Handler(Looper.getMainLooper()).post if listeners update UI directly
			listener(isConnectedToAllowedNetwork)
		}
		Log.d(
			TAG,
			"Notified ${currentListeners.size} listeners. isConnectedToAllowed: $isConnectedToAllowedNetwork"
		)
	}

	fun isCurrentlyConnectedToAllowedNetwork(): Boolean = isConnectedToAllowedNetwork

	private fun shutdown() {
		networkCallback?.let {
			try {
				connectivityManager?.unregisterNetworkCallback(it)
			} catch (e: Exception) { /* Ignore */
			}
		}
		networkCallback = null
		managerScope.coroutineContext.cancelChildren() // Cancel ongoing coroutines
		Log.i(TAG, "WifiMonitorManager shut down.")
	}
}