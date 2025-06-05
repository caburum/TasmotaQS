package com.caburum.tasmotaqs

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
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
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.slider.Slider
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
		var initialScheme = 0
		var initialSpeed = 1
		try {
			val response = tasmotaManager.doRequestAsync("Status 11").get()
			val status = response.optJSONObject("StatusSTS") ?: throw Exception("Invalid response")
			initialWhitePower = status.optString("POWER2") == "ON"
			initialWhiteTemperature = status.optInt("CT", 153)
			initialWhiteBrightness = status.optInt("Dimmer2")
			initialColorPower = status.optString("POWER1") == "ON"
			initialColor = ("#" + status.optString("Color", "FFFFFF").slice(0..5)).toColorInt()
			initialColorBrightness = status.optInt("Dimmer1")
			initialScheme = status.optInt("Scheme")
			initialSpeed = status.optInt("Speed")
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
				if (fromUser) debounceColorPickerView("Json {\"HSBColor1\":${hsv[0]},\"HSBColor2\":${hsv[1] * 100}}")
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

		val schemeSpeedWrapper = dialog.findViewById<View>(R.id.schemeSpeedWrapper)
		val schemeSpeedSlider = dialog.findViewById<Slider>(R.id.schemeSpeedSlider)
		schemeSpeedSlider.value = initialSpeed.coerceAtMost(10).toFloat() // really goes to 40
		schemeSpeedSlider.addOnChangeListener { _, value, _ ->
			tasmotaManager.doRequestAsync("Speed ${value.toInt()}")
		}
		schemeSpeedSlider.setLabelFormatter { getString(R.string.speed_label, it.toInt()) }

		var schemeSetup = false
		var scheme by Delegates.observable(initialScheme) { _, _, it ->
			colorPickerView.visibility = if (it == 0) View.VISIBLE else View.GONE
			// todo: only disable hue not saturation
			schemeSpeedWrapper.visibility = if (it == 0) View.GONE else View.VISIBLE
			if (!schemeSetup) {
				// don't call api when initializing
				schemeSetup = true
			} else if (it == 0) {
				// to a color
				tasmotaManager.doRequestAsync("HSBColor").thenAcceptAsync({
					colorPickerView.selectByHsvColor(
						("#" + it.optString("Color", "FFFFFF").slice(0..5)).toColorInt()
					)
					try {
						val hsv = it.optString("HSBColor").split(",").map { c -> c.toFloat() }
							.toFloatArray()
						colorPickerBrightnessProgressDrawable.setColors(
							intArrayOf(
								Color.BLACK,
								Color.HSVToColor(floatArrayOf(hsv[0], hsv[1] / 100f, 1f))
							),
							null
						)
					} catch (_: Exception) {
					}
				}, mainThreadExecutor)
			} else {
				// to a scheme
				colorPickerBrightnessProgressDrawable.setColors(
					intArrayOf(Color.BLACK, Color.WHITE),
					null
				)
			}
		}
		scheme = initialScheme
		val schemeToggleGroup =
			dialog.findViewById<MaterialButtonToggleGroup>(R.id.schemeToggleGroup)
		schemeToggleGroup.check(
			when (initialScheme) {
				2 -> R.id.schemeCycleButton
				4 -> R.id.schemeRandomButton
				else -> R.id.schemeSingleButton
			}
		)
		// add listener after initial update to prevent false request
		schemeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
			if (!isChecked) return@addOnButtonCheckedListener
			scheme = when (checkedId) {
				R.id.schemeCycleButton -> 2
				R.id.schemeRandomButton -> 4
				else -> 0
			}
			tasmotaManager.doRequestAsync("Scheme $scheme").thenRunAsync({
				colorPower = true
			}, mainThreadExecutor)
		}

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