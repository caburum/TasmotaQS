package com.caburum.tasmotaqs

import WifiMonitorManager
import android.Manifest
import android.annotation.SuppressLint
import android.app.StatusBarManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.TileService
import android.view.Menu
import android.view.MenuItem
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.Executor
import java.util.concurrent.Executors


class MainActivity : ComponentActivity() {
	@SuppressLint("ObsoleteSdkInt")
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		val allPermissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

		val multiplePermissionsContract = ActivityResultContracts.RequestMultiplePermissions()
		multiplePermissionLauncher = registerForActivityResult(multiplePermissionsContract) {
//			Log.d("PERMISSIONS", "Launcher result: $it")
//			if (it.containsValue(false)) {
//				Log.d("PERMISSIONS", "At least one of the permissions was not granted, launching again...")
//				multiplePermissionLauncher?.launch(permissions)
//			}
		}

		if (!hasPermissions(allPermissions)) {
//			Log.d("PERMISSIONS", "Launching multiple contract permission launcher for ALL required permissions")
			multiplePermissionLauncher!!.launch(allPermissions)
		}

		setContent {
			AppTheme {
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
						val addedFlow =
							remember { context.dataStore.data.map { it[TILE_ADDED] ?: false } }
						val added by addedFlow.collectAsState(initial = false)

						if (added) {
							Text(text = stringResource(R.string.qs_added))
						} else if (Build.VERSION.SDK_INT >= 33) {
							// On API level 33 and above, we can request to the system that our tile should be added to the Quick Settings.
							val executor = rememberExecutor()
							Button(onClick = { addTile(context, executor, coroutineScope) }) {
								Text(text = stringResource(R.string.qs_add_button))
							}
						} else {
							Text(text = stringResource(R.string.qs_add_open))
						}

						Column {
							Text(stringResource(R.string.settings_address_desc))

							SettingRow(stringResource(R.string.settings_address)) {
								DataStoreTextField(context, coroutineScope,
									{ p: Preferences -> p[TASMOTA_ADDRESS] ?: "" },
									{ p: MutablePreferences, value: String ->
										p[TASMOTA_ADDRESS] = value
									})
							}
						}

						SettingRow(stringResource(R.string.settings_networks)) {
							Row(
								horizontalArrangement = Arrangement.spacedBy(0.dp),
								verticalAlignment = Alignment.CenterVertically
							) {
								var isConnectedToAllowedNetwork by remember {
									mutableStateOf(WifiMonitorManager.isCurrentlyConnectedToAllowedNetwork())
								}
								DisposableEffect(WifiMonitorManager) { // Key on WifiMonitorManager or a stable instance
									val listener = { newStatus: Boolean ->
										isConnectedToAllowedNetwork = newStatus
									}
									WifiMonitorManager.addConnectionStatusListener(listener)

									onDispose {
										WifiMonitorManager.removeConnectionStatusListener(listener)
									}
								}
								OutlinedIconButton(
									onClick = {
										val wifiPermissions =
											arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
										if (!hasPermissions(wifiPermissions)) {
											multiplePermissionLauncher!!.launch(wifiPermissions)
										}
										// todo: make automatic somehow
									},
									colors = IconButtonDefaults.outlinedIconButtonColors(
										contentColor = if (isConnectedToAllowedNetwork) Color.Green else Color.Red
									)
								) {
									Icon(
										Icons.Filled.Refresh,
										contentDescription = stringResource(R.string.refresh),
										modifier = Modifier.size(32.dp)
									)
								}

								DataStoreTextField(context, coroutineScope,
									{ p: Preferences ->
										p[ALLOWED_NETWORKS]?.joinToString(",") ?: ""
									},
									{ p: MutablePreferences, value: String ->
										p[ALLOWED_NETWORKS] = value.split(",").toSet()
									})
							}
						}

						Column {
							val enableAuthFlow =
								remember {
									context.dataStore.data.map {
										it[TASMOTA_AUTH_ENABLE] ?: false
									}
								}
							val enableAuth by enableAuthFlow.collectAsState(initial = false)
							SettingRow(stringResource(R.string.settings_auth)) {
								Switch(
									checked = enableAuth,
									onCheckedChange = { checked ->
										coroutineScope.launch {
											context.dataStore.edit {
												it[TASMOTA_AUTH_ENABLE] = checked
											}
										}
									},
								)
							}

							SettingRow(stringResource(R.string.settings_username)) {
								DataStoreTextField(context, coroutineScope,
									{ p: Preferences -> p[TASMOTA_AUTH_USER] ?: "" },
									{ p: MutablePreferences, value: String ->
										p[TASMOTA_AUTH_USER] = value
									})
							}

							SettingRow(stringResource(R.string.settings_password)) {
								DataStoreTextField(context, coroutineScope,
									{ p: Preferences -> p[TASMOTA_AUTH_PASS] ?: "" },
									{ p: MutablePreferences, value: String ->
										p[TASMOTA_AUTH_PASS] = value
									})
							}
						}
					}
				}
			}
		}
	}

	// https://stackoverflow.com/q/66475027
	private var multiplePermissionLauncher: ActivityResultLauncher<Array<String>>? = null
	private fun hasPermissions(permissions: Array<String>?): Boolean {
		if (permissions != null) {
			for (permission in permissions) {
				if (ActivityCompat.checkSelfPermission(
						this,
						permission
					) != PackageManager.PERMISSION_GRANTED
				) {
//					Log.d("PERMISSIONS", "Permission is not granted: $permission")
					return false
				}
//				Log.d("PERMISSIONS", "Permission already granted: $permission")
			}
			return true
		}
		return false
	}

	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		// Inflate the menu; this adds items to the action bar if it is present.
		menuInflater.inflate(R.menu.menu_main, menu)
		return true
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		return when (item.itemId) {
			R.id.action_settings -> true
			else -> super.onOptionsItemSelected(item)
		}
	}
}

@SuppressLint("ObsoleteSdkInt")
@Composable
fun AppTheme(
	isDarkTheme: Boolean = isSystemInDarkTheme(),
	isDynamicColor: Boolean = true,
	content: @Composable () -> Unit
) {
	/**
	 * Dynamic Colors are supported on API level 31 and above
	 * */
	val dynamicColor = isDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
	val colorScheme = when {
		dynamicColor && isDarkTheme -> {
			dynamicDarkColorScheme(LocalContext.current)
		}
//		dynamicColor && !isDarkTheme -> {
		else -> {
			dynamicLightColorScheme(LocalContext.current)
		}
//		 isDarkTheme -> DarkThemeColors
//		 else -> LightThemeColors
	}

	// Make use of Material3 imports
	MaterialTheme(
		colorScheme = colorScheme,
//		typography = AppTypography,
		content = content
	)
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