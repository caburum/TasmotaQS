package com.caburum.tasmotaqs

import WifiMonitorManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import org.json.JSONObject

private const val TAG = "ControlTileService"

class ControlTileService : TileService() {
	companion object {
		fun getComponentName(context: Context): ComponentName {
			return ComponentName(context.applicationContext, ControlTileService::class.java)
		}

		fun getIconOff(context: Context): Icon {
			return Icon.createWithResource(context, R.drawable.outline_lightbulb_24)
//			return Icon.createWithResource(context, R.drawable.qs_lightbulb_icon_off)
		}

		fun getIconOn(context: Context): Icon {
			return Icon.createWithResource(context, R.drawable.filled_lightbulb_24)
//			return Icon.createWithResource(context, R.drawable.qs_lightbulb_icon_on)
		}

		fun getIconError(context: Context): Icon {
			return Icon.createWithResource(context, R.drawable.error_lightbulb_24)
		}
	}

	private var coroutineScope: CoroutineScope? = null
	private var isConnectedToAllowedNetwork: Boolean = false

	private val wifiConnectionListener: (Boolean) -> Unit = { isAllowed ->
		Log.d(TAG, "WifiMonitorManager reported: isConnectedToAllowedNetwork = $isAllowed")
		isConnectedToAllowedNetwork = isAllowed

		updateTileBasedOnCurrentWifiAndDeviceState()
	}

	override fun onCreate() {
		super.onCreate()
		Log.d(TAG, "onCreate")
		coroutineScope = CoroutineScope(Job() + Dispatchers.Main)
	}

	override fun onDestroy() {
		super.onDestroy()
		Log.d(TAG, "onDestroy")
		coroutineScope?.cancel()
	}

	override fun onTileAdded() {
		super.onTileAdded()
		Log.d(TAG, "onTileAdded + ${Thread.currentThread().name}")
		loadingTile()
		coroutineScope?.launch {
			dataStore.edit { it[TILE_ADDED] = true }
		}
	}

	override fun onTileRemoved() {
		super.onTileRemoved()
		Log.d(TAG, "onTileRemoved")
		coroutineScope?.launch {
			dataStore.edit { it[TILE_ADDED] = false }
		}
	}

	override fun onStartListening() {
		super.onStartListening()
		Log.d(TAG, "onStartListening")
		loadingTile()
		WifiMonitorManager.addConnectionStatusListener(wifiConnectionListener)
	}

	override fun onStopListening() {
		super.onStopListening()
		Log.d(TAG, "onStopListening")
		WifiMonitorManager.removeConnectionStatusListener(wifiConnectionListener)
	}

	private fun updateTileBasedOnCurrentWifiAndDeviceState() {
		if (isConnectedToAllowedNetwork) {
			Log.d(TAG, "Background update: On allowed network, fetching device state.")
			fetchUpdateTile(this)
		} else {
			Log.w(
				TAG,
				"Background update: Not on allowed network, setting tile to 'incorrect network' state."
			)
			val tile = qsTile ?: return
//			tile.label = getString(R.string.toggle_white)
			tile.subtitle = getString(R.string.incorrect_network)
			tile.icon = getIconError(this)
			tile.state = Tile.STATE_INACTIVE
			tile.updateTile()
		}
	}

	private val tasmotaOperationMutex = Mutex()

	override fun onClick() {
		super.onClick()
		Log.d(TAG, "onClick initiated by user.")
		loadingTile()
		if (tasmotaOperationMutex.tryLock()) {
			TasmotaManager(this).doRequestAsync("power2 toggle")
				.whenComplete { it: JSONObject?, ex: Throwable? ->
					coroutineScope?.launch {
						if (ex != null || it == null) {
							Log.e(TAG, "onClick Tasmota request failed.", ex)
							errorTile()
						} else {
							Log.d(
								TAG,
								"onClick Tasmota request successful, fetching updated state."
							)
							fetchUpdateTile(this@ControlTileService)
						}
						tasmotaOperationMutex.unlock()
					}
				}
		}
	}

	private fun loadingTile() {
		val tile = qsTile ?: return
//		tile.label = getString(R.string.toggle_white)
		tile.subtitle = getString(R.string.loading)
//		tile.state = Tile.STATE_INACTIVE
		tile.updateTile()
	}

	private fun errorTile() {
		val tile = qsTile ?: return
//		tile.label = getString(R.string.toggle_white)
		tile.subtitle = getString(R.string.connection_error)
		tile.icon = getIconError(this)
		tile.state = Tile.STATE_INACTIVE
		tile.updateTile()
	}

	private fun fetchUpdateTile(context: Context) {
		Log.d(TAG, "fetchUpdateTile: Attempting to get device state.")
		TasmotaManager(context).getOnState().whenComplete { it: List<Boolean>?, ex: Throwable? ->
			coroutineScope?.launch {
				if (ex != null || it == null) {
					Log.e(TAG, "fetchUpdateTile: Tasmota getOnState failed.", ex)
					errorTile()
				} else {
					try {
						Log.d(TAG, "fetchUpdateTile: Tasmota getOnState successful. States: $it")
						val tile = qsTile ?: return@launch
//						tile.label = getString(R.string.toggle_white)
						tile.subtitle =
							getString(if (it[1]) R.string.white_on_short else R.string.white_off_short) +
								", " + getString(if (it[0]) R.string.color_on_short else R.string.color_off_short)
//						tile.icon = null // makes tile temporarily unavailable
						tile.icon =
							if (it[1]) getIconOn(context) else getIconOff(context)
						tile.state = if (it[1]) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
						tile.updateTile()
						tile.icon =
							if (it[1]) getIconOn(context) else getIconOff(context)
						tile.updateTile()
					} catch (e: IndexOutOfBoundsException) {
						Log.e(TAG, "fetchUpdateTile: Error processing device states.", e)
						errorTile()
					}
				}
			}
		}
	}
}