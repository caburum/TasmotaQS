package com.caburum.tasmotaqs

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.skydoves.colorpickerview.ColorEnvelope
import com.skydoves.colorpickerview.ColorPickerView
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.properties.Delegates


class DialogActivity : ComponentActivity() {
	companion object {
		private const val TAG = "DialogActivity"
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		// redirect to permissions request
		if (!Settings.canDrawOverlays(this)) {
			Toast.makeText(this, R.string.overlay_permission, Toast.LENGTH_LONG).show()
			val startForResult =
				registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
			val intent =
				Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri())
			startForResult.launch(intent)
		}

		val tasmotaManager = TasmotaManager(this)
		val mainThreadExecutor = ContextCompat.getMainExecutor(this)

		// load initial state
		var initialWhitePower = false
		var initialWhiteTemperature = 153
		var initialWhiteBrightness = 0
		var initialColorPower = false
		var initialColor = Color.WHITE
		var initialColorBrightness = 0
		try {
			val response = tasmotaManager.doRequestAsync("HSBColor").get()
			initialWhitePower = response.optString("POWER2") == "ON"
			initialWhiteTemperature = response.optInt("CT", 153)
			initialWhiteBrightness = response.optInt("Dimmer2")
			initialColorPower = response.optString("POWER1") == "ON"
			initialColor = ("#" + response.optString("Color", "FFFFFF").slice(0..5)).toColorInt()
			initialColorBrightness = response.optInt("Dimmer1")
		} catch (_: Exception) {
			// doRequestAsync will have shown an error toast already
			finish()
		}

		val dialog = object : Dialog(this) {
			override fun onStop() {
				super.onStop()
				finish()
			}
		}
		dialog.setContentView(R.layout.control_dialog)

		dialog.findViewById<MaterialButton>(R.id.dialog_close).setOnClickListener {
			dialog.dismiss()
		}

		val whiteToggleButton = dialog.findViewById<MaterialButton>(R.id.whiteToggleButton)
		val colorToggleButton = dialog.findViewById<MaterialButton>(R.id.colorToggleButton)
		val iconOn = AppCompatResources.getDrawable(this, R.drawable.filled_lightbulb_24)
		val iconOff = AppCompatResources.getDrawable(this, R.drawable.outline_lightbulb_24)

		// store power state locally as we know which behaviors will cause it to turn on
		// under the assumption that while controlling lights with this dialog, it won't be changed elsewhere
		var whitePower by Delegates.observable(initialWhitePower) { _, _, it ->
			whiteToggleButton.icon = if (it) iconOn else iconOff
			whiteToggleButton.isChecked = it
		}
		whitePower = initialWhitePower
		var colorPower by Delegates.observable(initialColorPower) { _, _, it ->
			colorToggleButton.icon = if (it) iconOn else iconOff
			colorToggleButton.isChecked = it
		}
		colorPower = initialColorPower

		whiteToggleButton.setOnClickListener {
			val state = !whiteToggleButton.isChecked
			tasmotaManager.doRequestAsync("power2 $state").thenAcceptAsync({
				whitePower = it.optString("POWER2") == "ON"
			}, mainThreadExecutor)
		}
		colorToggleButton.setOnClickListener {
			val state = !colorToggleButton.isChecked
			tasmotaManager.doRequestAsync("power1 $state").thenAcceptAsync({
				colorPower = it.optString("POWER1") == "ON"
			}, mainThreadExecutor)
		}

		val whitePickerTemperature = dialog.findViewById<SeekBar>(R.id.whitePickerTemperature)
		whitePickerTemperature.progress = initialWhiteTemperature

		val whitePickerBrightness = dialog.findViewById<SeekBar>(R.id.whitePickerBrightness)
		whitePickerBrightness.progress = initialWhiteBrightness
		val whitePickerBrightnessProgressDrawable =
			(whitePickerBrightness.progressDrawable as LayerDrawable).findDrawableByLayerId(android.R.id.progress)
				.mutate() as GradientDrawable

		val cwColor = ContextCompat.getColor(this, R.color.cw)
		val cwValue = whitePickerTemperature.min.toFloat()
		val wwColor = ContextCompat.getColor(this, R.color.ww)
		val wwValue = whitePickerTemperature.max.toFloat()

		val debounceWhitePickerTemperature = debounce(lifecycleScope) { progress: Int ->
			tasmotaManager.doRequestAsync("CT $progress").thenRunAsync({
				whitePower = true
			}, mainThreadExecutor)
		}

		fun updateWhiteBrightnessTemperature(progress: Int) {
			whitePickerBrightnessProgressDrawable.setColors(
				intArrayOf(
					Color.BLACK,
					ColorUtils.blendARGB(
						cwColor,
						wwColor,
						(progress - cwValue) / (wwValue - cwValue)
					)
				), null
			)
		}
		whitePickerTemperature.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
			override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
				updateWhiteBrightnessTemperature(progress)

				if (fromUser) debounceWhitePickerTemperature(progress)
			}

			override fun onStartTrackingTouch(seekBar: SeekBar?) {}
			override fun onStopTrackingTouch(seekBar: SeekBar?) {}
		})
		updateWhiteBrightnessTemperature(initialWhiteTemperature) // initialize color temperature

		val debounceWhitePickerBrightness = debounce(lifecycleScope) { progress: Int ->
			tasmotaManager.doRequestAsync("Dimmer2 $progress").thenRunAsync({
				whitePower = true
			}, mainThreadExecutor)
		}
		whitePickerBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
			override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
				if (fromUser) debounceWhitePickerBrightness(progress)
			}

			override fun onStartTrackingTouch(seekBar: SeekBar?) {}
			override fun onStopTrackingTouch(seekBar: SeekBar?) {}
		})

		val colorPickerView = dialog.findViewById<ColorPickerView>(R.id.colorPickerView)
		colorPickerView.setInitialColor(initialColor)

		val colorPickerBrightness = dialog.findViewById<SeekBar>(R.id.colorPickerBrightness)
		colorPickerBrightness.progress = initialColorBrightness
		val colorPickerBrightnessProgressDrawable =
			(colorPickerBrightness.progressDrawable as LayerDrawable).findDrawableByLayerId(android.R.id.progress)
				.mutate() as GradientDrawable

		val debounceColorPickerView = debounce(lifecycleScope) { cmnd: String ->
			tasmotaManager.doRequestAsync(cmnd).thenRunAsync({
				colorPower = true
			}, mainThreadExecutor)
		}
		var colorPickerViewInitCount =
			0 // listener is fired 2 times on load, so skip sending a request & breaking stuff
		colorPickerView.setColorListener(object : ColorEnvelopeListener {
			override fun onColorSelected(envelope: ColorEnvelope, fromUser: Boolean) {
				val hsv = FloatArray(3)
				Color.colorToHSV(envelope.color, hsv)
				colorPickerBrightnessProgressDrawable.setColors(
					intArrayOf(Color.BLACK, Color.HSVToColor(floatArrayOf(hsv[0], hsv[1], 1f))),
					null
				)

				if (colorPickerViewInitCount != 2) {
					colorPickerViewInitCount++
					return
				}
				debounceColorPickerView("Json {\"HSBColor1\":${hsv[0]},\"HSBColor2\":${hsv[1] * 100}}")
			}
		})

		val debounceColorPickerBrightness = debounce(lifecycleScope) { progress: Int ->
			tasmotaManager.doRequestAsync("HSBColor3 $progress").thenRunAsync({
				colorPower = true
			}, mainThreadExecutor)
		}
		colorPickerBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
			override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
				if (fromUser) debounceColorPickerBrightness(progress)
			}

			override fun onStartTrackingTouch(seekBar: SeekBar?) {}
			override fun onStopTrackingTouch(seekBar: SeekBar?) {}
		})

		val layoutParams = WindowManager.LayoutParams()
		layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
		layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
		layoutParams.gravity = Gravity.CENTER

		dialog.window?.apply {
			attributes = layoutParams
			setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
			setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
		}

		dialog.show()
	}
}

// https://stackoverflow.com/a/57252799
fun <T> debounce(
	coroutineScope: CoroutineScope,
	waitMs: Long = 150L,
	destinationFunction: (T) -> Unit
): (T) -> Unit {
	var debounceJob: Job? = null
	return { param: T ->
		// if no previous job, send immediately (for instant click)
		val delayMs = if (debounceJob?.isActive == true) 0 else waitMs

		debounceJob?.cancel()
		debounceJob = coroutineScope.launch {
			delay(delayMs)
			destinationFunction(param)
		}
	}
}