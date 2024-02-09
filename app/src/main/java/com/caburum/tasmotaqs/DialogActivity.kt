package com.caburum.tasmotaqs

import android.app.Dialog
import android.content.Intent
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.google.android.material.button.MaterialButton
import com.skydoves.colorpickerview.ColorEnvelope
import com.skydoves.colorpickerview.ColorPickerView
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import java.util.concurrent.ExecutionException

class DialogActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		intent.flags =
			intent.flags or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or Intent.FLAG_ACTIVITY_NO_HISTORY

		fun finish() {
			this.finish()
//			super.onBackPressed()
//			startActivity(Intent(this@DialogActivity, MainActivity::class.java))
		}

		if (!intent.action.equals(android.service.quicksettings.TileService.ACTION_QS_TILE_PREFERENCES)) finish()

		// load initial state
		var initialColor = Color.WHITE
		var initialColorBrightness = 0
		try {
			val response = TasmotaManager().doRequestAsync(baseContext, "HSBColor").get()
			initialColor = Color.parseColor("#" + response.optString("Color", "FFFFFF").slice(0..5))
			initialColorBrightness = response.optInt("Dimmer1")
		} catch (e: ExecutionException) {
			// will have shown a toast
			finish()
		}

		val dialog = object : Dialog(this) {
			override fun onStop() {
				super.onStop()
				Log.d("dialogClose", "close")
				finish()
			}
		}
		dialog.setContentView(R.layout.control_dialog)

		dialog.findViewById<MaterialButton>(R.id.dialog_close).setOnClickListener {
			Toast.makeText(this, "Hi", Toast.LENGTH_LONG).show()
			dialog.dismiss()
		}

		val colorPickerView = dialog.findViewById<ColorPickerView>(R.id.colorPickerView)
		colorPickerView.setInitialColor(initialColor)

		val colorPickerBrightness = dialog.findViewById<SeekBar>(R.id.colorPickerBrightness)
		colorPickerBrightness.progress = initialColorBrightness
		val colorPickerBrightnessProgressDrawable =
			(colorPickerBrightness.progressDrawable as LayerDrawable).findDrawableByLayerId(
				Resources.getSystem().getIdentifier("progress", "id", "android")
			) as GradientDrawable

		var colorPickerViewFirst = true
		colorPickerView.setColorListener(object : ColorEnvelopeListener {
			override fun onColorSelected(envelope: ColorEnvelope, fromUser: Boolean) {
				val hsv = FloatArray(3)
				Color.colorToHSV(envelope.color, hsv)
				Log.d("color", hsv.contentToString())
				colorPickerBrightnessProgressDrawable.setColors(
					intArrayOf(
						Color.HSVToColor(
							floatArrayOf(0f, 0f, 1 - hsv[1])
						), Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f))
					), null
				)

				if (colorPickerViewFirst) {
					colorPickerViewFirst = false
					return
				}
				try {
					val response = TasmotaManager().doRequestAsync(
						baseContext,
						"Json {\"HSBColor1\":" + hsv[0] + ",\"HSBColor2\":" + hsv[1] * 100 + "}"
					).get()
					Log.d("colorPickerView onColorSelected", response.optInt("HSBColor").toString())
				} catch (e: ExecutionException) {
				}
			}
		})

		var colorPickerBrightnessFirst = true
		colorPickerBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
			override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
				if (colorPickerBrightnessFirst) {
					colorPickerBrightnessFirst = false
					return
				}
				try {
					val response =
						TasmotaManager().doRequestAsync(baseContext, "HSBColor3 $progress").get()
					Log.d(
						"colorPickerBrightness onProgressChanged",
						response.optInt("HSBColor").toString()
					)
				} catch (e: ExecutionException) {
				}
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
			setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
		}

		dialog.show()
	}
}
