package com.caburum.tasmotaqs

import android.annotation.SuppressLint
import android.app.StatusBarManager
import android.content.Context
import android.os.Build
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@SuppressLint("ObsoleteSdkInt")
@Composable
fun QuickSettings() {
	Surface {
		Column(
			modifier = Modifier
				.padding(16.dp)
				.verticalScroll(rememberScrollState()),
			verticalArrangement = Arrangement.spacedBy(16.dp),
		) {
			val context = LocalContext.current
			val coroutineScope = rememberCoroutineScope()

			// Whether the tile has been added to the Quick Settings.
			val addedFlow = remember { context.dataStore.data.map { it[TILE_ADDED] ?: false } }
			val added by addedFlow.collectAsState(initial = false)

			if (added) {
				Text(text = "The tile has been added to the Quick Settings.")
			} else if (Build.VERSION.SDK_INT >= 33) {
				// On API level 33 and above, we can request to the system that our tile should be added to the Quick Settings.
				val executor = rememberExecutor()
				Button(onClick = { addTile(context, executor, coroutineScope) }) {
					Text(text = "ADD TILE TO QUICK SETTINGS")
				}
			} else {
				Text(text = "Open Quick Settings and add tile.")
			}

//			// The tile is toggleable. This state represents whether it's currently active or not.
//			val activeFlow = remember { context.dataStore.data.map { it[TILE_ACTIVE] ?: false } }
//			val active by activeFlow.collectAsState(initial = false)
//
//			Text(text = "The state of this switch is synchronized with the tile state.")
//			Row(
//				horizontalArrangement = Arrangement.spacedBy(16.dp),
//				verticalAlignment = Alignment.CenterVertically,
//			) {
//				Text(text = "Toggle Active/Inactive")
//				Switch(
//					checked = active,
//					onCheckedChange = { checked ->
//						// Modify the state. The same state is shared between this switch and the tile.
//						coroutineScope.launch {
//							context.dataStore.edit { it[TILE_ACTIVE] = checked }
//						}
//						val componentName = ControlTileService.getComponentName(context)
//						// Request to the system that the tile should catch this state change.
//						TileService.requestListeningState(context, componentName)
//					},
//				)
//			}

			Column {
				Text("Address (IP:port) is required for use")

				SettingRow("Address") {
					DataStoreTextField(context, coroutineScope,
						{ p: Preferences -> p[TASMOTA_ADDRESS] ?: "" },
						{ p: MutablePreferences, value: String -> p[TASMOTA_ADDRESS] = value })
				}
			}

			SettingRow("Networks") {
				Row(
					horizontalArrangement = Arrangement.spacedBy(0.dp),
					verticalAlignment = Alignment.CenterVertically
				) {
					var correctNetwork by remember { mutableStateOf(false) }
					OutlinedIconButton(
						onClick = {
							correctNetwork = TasmotaManager().isCorrectNetwork(context)
						},
						colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = if (correctNetwork) Color.Green else Color.Red)
					) {
						Icon(
							Icons.Filled.Refresh,
							contentDescription = "Refresh",
							modifier = Modifier.size(32.dp)
						)
					}

					DataStoreTextField(context, coroutineScope,
						{ p: Preferences -> p[ALLOWED_NETWORKS]?.joinToString(",") ?: "" },
						{ p: MutablePreferences, value: String ->
							p[ALLOWED_NETWORKS] = value.split(",").toSet()
						})
				}
			}

			Column {
				val enableAuthFlow =
					remember { context.dataStore.data.map { it[TASMOTA_AUTH_ENABLE] ?: false } }
				val enableAuth by enableAuthFlow.collectAsState(initial = false)
				SettingRow("Enable API auth") {
					Switch(
						checked = enableAuth,
						onCheckedChange = { checked ->
							coroutineScope.launch {
								context.dataStore.edit { it[TASMOTA_AUTH_ENABLE] = checked }
							}
						},
					)
				}

				SettingRow("Username") {
					DataStoreTextField(context, coroutineScope,
						{ p: Preferences -> p[TASMOTA_AUTH_USER] ?: "" },
						{ p: MutablePreferences, value: String -> p[TASMOTA_AUTH_USER] = value })
				}

				SettingRow("Password") {
					DataStoreTextField(context, coroutineScope,
						{ p: Preferences -> p[TASMOTA_AUTH_PASS] ?: "" },
						{ p: MutablePreferences, value: String -> p[TASMOTA_AUTH_PASS] = value })
				}
			}
		}
	}
}

@Composable
private fun DataStoreTextField(
	context: Context,
	coroutineScope: CoroutineScope,
	readCallback: (Preferences) -> String,
	writeCallback: (MutablePreferences, String) -> Unit
) {
	var value by remember { mutableStateOf("") }

	// read the text value from DataStore on composition
	LaunchedEffect(Unit) {
		context.dataStore.data.collect { preferences ->
			value = readCallback(preferences)
		}
	}

	// save the value to DataStore when the value changes
	DisposableEffect(value) {
		coroutineScope.launch {
			context.dataStore.edit { preferences ->
				writeCallback(preferences, value)
			}
		}
		onDispose {}
	}

	TextField(
		value = value,
		onValueChange = { newText: String -> value = newText },
		keyboardOptions = KeyboardOptions.Default.copy(
			capitalization = KeyboardCapitalization.None,
			autoCorrect = false,
			imeAction = ImeAction.Done
		),
		textStyle = MaterialTheme.typography.bodyMedium,
		modifier = Modifier.padding(16.dp),
		singleLine = true,
	)
}

@Composable
private fun SettingRow(text: String, content: @Composable () -> Unit) {
	Row(
		horizontalArrangement = Arrangement.spacedBy(16.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(text)
		content()
	}
}

@SuppressLint("ObsoleteSdkInt")
@RequiresApi(33)
private fun addTile(context: Context, executor: Executor, coroutineScope: CoroutineScope) {
	val statusBarManager = context.getSystemService(StatusBarManager::class.java)
	val componentName = ControlTileService.getComponentName(context)
	// Request to the system that our tile should be added to the Quick Settings.
	// This opens up a system dialog, and the tile is added upon the user's approval.
	statusBarManager.requestAddTileService(
		componentName,
		context.getString(R.string.toggle_white),
		ControlTileService.getIconOff(context),
		executor,
	) { result ->
		if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED || result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) {
			// Record that the tile has been added.
			coroutineScope.launch {
				context.dataStore.edit { it[TILE_ADDED] = true }
			}
			// Request to the system that tile should be updated to the latest state.
			TileService.requestListeningState(context, componentName)
		}
	}
}

@Composable
private fun rememberExecutor(): Executor {
	val executor = remember { Executors.newSingleThreadExecutor() }
	DisposableEffect(Unit) {
		onDispose {
			executor.shutdown()
		}
	}
	return executor
}