package com.namit.presentation_displays

import android.app.Activity
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import androidx.annotation.NonNull
import com.google.gson.Gson
import io.flutter.FlutterInjector
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.FlutterEngineCache
import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import org.json.JSONObject

/** PresentationDisplaysPlugin (Embedding V2) */
class PresentationDisplaysPlugin :
  FlutterPlugin,
  ActivityAware,
  MethodChannel.MethodCallHandler {

  private lateinit var channel: MethodChannel
  private lateinit var eventChannel: EventChannel

  // Channel untuk kirim data ke engine di presentation
  private var flutterEngineChannel: MethodChannel? = null

  // Context: default = applicationContext; akan dioverride ke activity saat available
  private var appContext: Context? = null
  private var activity: Activity? = null
  private var displayManager: DisplayManager? = null

  private var presentation: PresentationDisplay? = null

  override fun onAttachedToEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    appContext = binding.applicationContext
    displayManager =
      appContext?.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    channel = MethodChannel(binding.binaryMessenger, VIEW_TYPE_ID)
    channel.setMethodCallHandler(this)

    eventChannel = EventChannel(binding.binaryMessenger, VIEW_TYPE_EVENTS_ID)
    val displayConnectedStreamHandler = DisplayConnectedStreamHandler(displayManager)
    eventChannel.setStreamHandler(displayConnectedStreamHandler)
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
    eventChannel.setStreamHandler(null)
    flutterEngineChannel = null
    // Tutup presentation kalau masih tampil
    presentation?.dismiss()
    presentation = null
    displayManager = null
    appContext = null
  }

  // ActivityAware
  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    activity = binding.activity
    // Boleh pakai activity sebagai context aktif
    displayManager =
      activity?.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    activity = binding.activity
    displayManager =
      activity?.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
  }

  override fun onDetachedFromActivityForConfigChanges() {
    activity = null
    // Kembali pakai appContext jika ada
    displayManager =
      appContext?.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
  }

  override fun onDetachedFromActivity() {
    activity = null
    displayManager =
      appContext?.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
  }

  override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
    Log.i(TAG, "method=${call.method} args=${call.arguments}")

    when (call.method) {
      "showPresentation" -> {
        try {
          val obj = JSONObject(call.arguments as String)
          val displayId: Int = obj.getInt("displayId")
          val tag: String = obj.getString("routerName")

          val dm = displayManager
          val disp = dm?.getDisplay(displayId)
          if (disp == null) {
            result.error("404", "Can't find display with displayId=$displayId", null)
            return
          }

          val engine = createFlutterEngine(tag)
          if (engine == null) {
            result.error("404", "Can't create/find FlutterEngine (tag=$tag)", null)
            return
          }

          // Channel untuk kirim data ke engine di layar eksternal
          flutterEngineChannel =
            MethodChannel(engine.dartExecutor.binaryMessenger, "${VIEW_TYPE_ID}_engine")

          // Tutup presentation lama jika ada
          presentation?.dismiss()
          presentation = null

          // Context yang aman untuk Presentation: pakai activity kalau ada, fallback ke appContext
          val ctx = activity ?: appContext
          if (ctx == null) {
            result.error("NO_CTX", "Context not available", null)
            return
          }

          val pres = PresentationDisplay(ctx, tag, disp)
          pres.show()
          presentation = pres

          result.success(true)
        } catch (e: Exception) {
          result.error(call.method, e.message, null)
        }
      }

      "hidePresentation" -> {
        try {
          // argumen tidak dipakai, tapi kita tetap parse agar kompatibel
          // val obj = JSONObject(call.arguments as String)
          presentation?.dismiss()
          presentation = null
          result.success(true)
        } catch (e: Exception) {
          result.error(call.method, e.message, null)
        }
      }

      "listDisplay" -> {
        val dm = displayManager
        val category = call.arguments as? String
        val displays: Array<Display>? = if (category.isNullOrBlank()) {
          dm?.displays
        } else {
          dm?.getDisplays(category)
        }

        val listJson = ArrayList<DisplayJson>()
        displays?.forEach { display ->
          Log.i(TAG, "display: $display")
          listJson.add(
            DisplayJson(
              id = display.displayId,
              flags = display.flags,
              rotation = display.rotation,
              name = display.name
            )
          )
        }
        result.success(Gson().toJson(listJson))
      }

      "transferDataToPresentation" -> {
        try {
          flutterEngineChannel?.invokeMethod("DataTransfer", call.arguments)
          result.success(true)
        } catch (e: Exception) {
          result.success(false)
        }
      }

      else -> result.notImplemented()
    }
  }

  private fun createFlutterEngine(tag: String): FlutterEngine? {
    // Pakai activity kalau ada, fallback ke appContext
    val ctx = activity ?: appContext ?: return null

    if (FlutterEngineCache.getInstance().get(tag) == null) {
      val flutterEngine = FlutterEngine(ctx)
      // Set initial route sesuai "routerName"
      flutterEngine.navigationChannel.setInitialRoute(tag)

      // Init & jalankan entrypoint khusus
      FlutterInjector.instance().flutterLoader().startInitialization(ctx)
      val path = FlutterInjector.instance().flutterLoader().findAppBundlePath()
      val entrypoint = DartExecutor.DartEntrypoint(path, "secondaryDisplayMain")
      flutterEngine.dartExecutor.executeDartEntrypoint(entrypoint)
      flutterEngine.lifecycleChannel.appIsResumed()

      // Cache supaya bisa di-attach oleh PresentationDisplay
      FlutterEngineCache.getInstance().put(tag, flutterEngine)
    }
    return FlutterEngineCache.getInstance().get(tag)
  }

  companion object {
    private const val TAG = "PresentationDisplays"
    private const val VIEW_TYPE_ID = "presentation_displays_plugin"
    private const val VIEW_TYPE_EVENTS_ID = "presentation_displays_plugin_events"
  }
}

/** Stream handler untuk event add/remove external display */
class DisplayConnectedStreamHandler(private var displayManager: DisplayManager?) :
  EventChannel.StreamHandler {
  private var sink: EventChannel.EventSink? = null
  private var handler: Handler? = null

  private val displayListener =
    object : DisplayManager.DisplayListener {
      override fun onDisplayAdded(displayId: Int) {
        sink?.success(1)
      }
      override fun onDisplayRemoved(displayId: Int) {
        sink?.success(0)
      }
      override fun onDisplayChanged(displayId: Int) { /* no-op */ }
    }

  override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
    sink = events
    handler = Handler(Looper.getMainLooper())
    displayManager?.registerDisplayListener(displayListener, handler)
  }

  override fun onCancel(arguments: Any?) {
    sink = null
    handler = null
    displayManager?.unregisterDisplayListener(displayListener)
  }
}

/** POJO untuk serialisasi info Display */
data class DisplayJson(
  val id: Int,
  val flags: Int,
  val rotation: Int,
  val name: String
)
