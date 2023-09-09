package com.caburum.tasmotaqs

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val TAG = "ControlTileService"

class ControlTileService : TileService() {
	companion object {
		fun getComponentName(context: Context): ComponentName {
			return ComponentName(context.applicationContext, ControlTileService::class.java)
		}

		fun getIconOff(context: Context): Icon {
			return Icon.createWithResource(context, R.drawable.outline_lightbulb_24)
		}

		fun getIconOn(context: Context): Icon {
			return Icon.createWithResource(context, R.drawable.filled_lightbulb_24)
		}
	}

	// The coroutine scope that's available from onCreate to onDestroy.
	private var coroutineScope: CoroutineScope? = null

	// The job for observing the state change. Available from onStartListening to onStopListening.
	private var listeningJob: Job? = null

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

	// Called when the tile is added to the Quick Settings by the user.
	// Note that this won't be called when the tile was added by
	// [StatusBarManager.requestAddTileService()].
	override fun onTileAdded() {
		super.onTileAdded()
		Log.d(TAG, "onTileAdded + ${Thread.currentThread().name}")
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

	// Called when the tile should start listening to some state change that it needs to react to.
	// Typically, this is invoked when the app calls [TileService.requestListeningState].
	override fun onStartListening() {
		super.onStartListening()
		Log.d(TAG, "onStartListening")
		listeningJob = coroutineScope?.launch {
//			dataStore.data.map { prefs -> prefs[TILE_ACTIVE] ?: false }
//				.collect { active -> updateTile(active) }
			TasmotaManager().getOnState {
				updateTile(it)
			}
		}
	}

	override fun onStopListening() {
		super.onStopListening()
		Log.d(TAG, "onStopListening")
		listeningJob?.cancel()
	}

	override fun onClick() {
		super.onClick()
		Log.d(TAG, "onClick")
		coroutineScope?.launch {
//			dataStore.edit { prefs ->
//				val newState = !(prefs[TILE_ACTIVE] ?: true)
//				Log.d(TAG, "New state: $newState")
//				prefs[TILE_ACTIVE] = newState
//				updateTile(newState)
//			}
			val man = TasmotaManager()
			man.doRequest("power2 toggle") {
				man.getOnState {
					updateTile(it)
				}
			}
		}
	}

	private fun updateTile(actives: List<Boolean>) {
		val tile = qsTile
		tile.label = getString(R.string.toggle_white)
		tile.subtitle = getString(if (actives[1]) R.string.white_on else R.string.white_off) +
			", " + getString(if (actives[0]) R.string.color_on else R.string.color_off)
		tile.icon = if (actives[1]) getIconOn(this) else getIconOff(this)
		tile.state = if (actives[1]) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
//		if (Build.VERSION.SDK_INT >= 30) {
//			tile.stateDescription = if (actives[0]) "yes" else "no"
//		}
		tile.updateTile()
	}
}