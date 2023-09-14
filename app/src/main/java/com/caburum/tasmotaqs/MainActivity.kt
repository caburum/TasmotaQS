package com.caburum.tasmotaqs

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

//		var denied = false
//		val requestPermissionLauncher =
//			registerForActivityResult(
//				ActivityResultContracts.RequestMultiplePermissions()
//			) { granteds: Map<String, Boolean> ->
//				if (granteds.values.all { it }) {
//
//				} else {
//					denied = true
//				}
//			}

		setContent {
			AppTheme {
				QuickSettings()
			}
		}
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