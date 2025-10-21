package com.namit.presentation_displays

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.ViewGroup
import android.widget.FrameLayout
import io.flutter.embedding.android.FlutterView
import io.flutter.embedding.engine.FlutterEngineCache
import android.app.Activity

class PresentationDisplay(context: Context, private val tag: String, display: Display) :
    Presentation(context, display) {
    private var flutterView: FlutterView? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val flContainer = FrameLayout(context)
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        flContainer.layoutParams = params

        setContentView(flContainer)

        val view = FlutterView(context)
        flutterView = view
        flContainer.addView(view, params)
        val flutterEngine = FlutterEngineCache.getInstance().get(tag)
        if (flutterEngine != null) {
            view.attachToFlutterEngine(flutterEngine)
        } else {
            Log.e("PresentationDisplay", "Can't find the FlutterEngine with cache name $tag")
        }
    }
    override fun onStop() {
        super.onStop()
        detachFlutter()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        detachFlutter()
    }

    private fun detachFlutter() {
        flutterView?.let {
            try {
                it.detachFromFlutterEngine()
            } catch (_: Throwable) { /* no-op */ }
        }
    }

}
