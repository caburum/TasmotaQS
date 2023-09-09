package com.caburum.tasmotaqs

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.RangeSlider
import com.skydoves.colorpickerview.ColorEnvelope
import com.skydoves.colorpickerview.ColorPickerView
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener

class DialogActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		intent.flags = intent.flags or android.content.Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS

		fun finish() {
			this.finish()
//			Process.killProcess(Process.myPid())
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

		val colorPickerView = findViewById<ColorPickerView>(R.id.colorPickerView)
//		colorPickerView.setColorListener(
//			ColorEnvelopeListener { envelope: ColorEnvelope, fromUser: Boolean ->
//				var hsv = FloatArray(3)
//				Color.colorToHSV(envelope.color, hsv)
//				var fullySaturated = Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f))
//			})

		val colorPickerBrightness = findViewById<RangeSlider>(R.id.colorPickerBrightness)

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
