package com.caburum.tasmotaqs

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView
import com.skydoves.colorpickerview.ColorEnvelope
import com.skydoves.colorpickerview.ColorPickerView
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener

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
		colorPickerView.setColorListener(object : ColorEnvelopeListener {
			override fun onColorSelected(envelope: ColorEnvelope, fromUser: Boolean) {
				val hsv = FloatArray(3)
				Color.colorToHSV(envelope.color, hsv)
				Log.d("color", hsv.contentToString())
				// var fullySaturated = Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f))
			}
		})

//		val colorPickerBrightness = dialog.findViewById<RangeSlider>(R.id.colorPickerBrightness)
		val colorPickerBrightness = dialog.findViewById<SeekBar>(R.id.colorPickerBrightness)
//		colorPickerBrightness.progressDrawable =
//			GradientDrawable(
//				GradientDrawable.Orientation.LEFT_RIGHT,
//				intArrayOf(Color.parseColor("#FF000000"), Color.parseColor("#FFFFFFFF"))
//			)

		val colorPickerLabel = dialog.findViewById<MaterialTextView>(R.id.colorPickerLabel)
		colorPickerLabel.text = getString(R.string.white_label, colorPickerBrightness.progress)

		colorPickerBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
			override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
				colorPickerLabel.text = getString(R.string.white_label, progress)
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
