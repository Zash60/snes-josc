package com.example.mysnesemulator

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewAssetLoader
import com.example.mysnesemulator.databinding.ActivityEmulatorBinding // Usa o layout do jogo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class EmulatorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEmulatorBinding
    private lateinit var assetLoader: WebViewAssetLoader

    inner class WebAppInterface(private val context: Context) {
        @JavascriptInterface
        fun saveStateToDisk(base64Data: String, fileName: String) {
            try {
                val cleanBase64 = if (base64Data.contains(",")) base64Data.split(",")[1] else base64Data
                val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                val file = File(context.filesDir, "$fileName.state")
                FileOutputStream(file).use { it.write(bytes) }
                runOnUiThread { binding.webView.evaluateJavascript("showToast('Salvo no Android!');", null) }
            } catch (e: Exception) {
                runOnUiThread { binding.webView.evaluateJavascript("showToast('Erro ao gravar disco', true);", null) }
            }
        }

        @JavascriptInterface
        fun loadStateFromDisk(fileName: String) {
            try {
                val file = File(context.filesDir, "$fileName.state")
                if (!file.exists()) {
                    runOnUiThread { binding.webView.evaluateJavascript("showToast('Nenhum save encontrado', true);", null) }
                    return
                }
                val bytes = file.readBytes()
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                runOnUiThread { binding.webView.evaluateJavascript("receiveStateFromAndroid('$base64');", null) }
            } catch (e: Exception) {
                runOnUiThread { binding.webView.evaluateJavascript("showToast('Erro ao ler disco', true);", null) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEmulatorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        hideSystemUI()
        setupWebView()
        setupControls()

        val useCrt = intent.getBooleanExtra("CRT_MODE", false)
        binding.scanlineOverlay.visibility = if (useCrt) View.VISIBLE else View.GONE

        val romUri = intent.data
        val romName = intent.getStringExtra("ROM_NAME") ?: "game.sfc"
        if (romUri != null) {
            loadRom(romUri, romName)
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun setupWebView() {
        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        binding.webView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.mediaPlaybackRequiresUserGesture = false
            addJavascriptInterface(WebAppInterface(this@EmulatorActivity), "AndroidInterface")
            
            clearCache(true)
            settings.cacheMode = WebSettings.LOAD_NO_CACHE 
            settings.setRenderPriority(WebSettings.RenderPriority.HIGH)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?) 
                    = assetLoader.shouldInterceptRequest(request!!.url)

                override fun onPageFinished(view: WebView?, url: String?) {
                    hideSystemUI()
                }
            }
            loadUrl("https://appassets.androidplatform.net/assets/index.html")
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupControls() {
        setupDpadSliding()

        mapButton(binding.btnA, "A")
        mapButton(binding.btnB, "B")
        mapButton(binding.btnX, "X")
        mapButton(binding.btnY, "Y")
        mapButton(binding.btnL, "L")
        mapButton(binding.btnR, "R")
        mapButton(binding.btnStart, "START")
        mapButton(binding.btnSelect, "SELECT")

        binding.btnTurbo.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    binding.webView.evaluateJavascript("window.dispatchEvent(new KeyboardEvent('keydown', {code:'Space', key:' ', keyCode:32, bubbles:true}));", null)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    binding.webView.evaluateJavascript("window.dispatchEvent(new KeyboardEvent('keyup', {code:'Space', key:' ', keyCode:32, bubbles:true}));", null)
                }
            }
            true
        }

        binding.btnSaveState.setOnClickListener { binding.webView.evaluateJavascript("triggerSaveState();", null) }
        binding.btnLoadState.setOnClickListener { binding.webView.evaluateJavascript("triggerLoadState();", null) }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDpadSliding() {
        var lastUp = false
        var lastDown = false
        var lastLeft = false
        var lastRight = false

        binding.dpadContainer.setOnTouchListener { view, event ->
            val w = view.width.toFloat()
            val h = view.height.toFloat()
            val x = event.x
            val y = event.y

            val isLeft = x < (w / 3)
            val isRight = x > (w * 2 / 3)
            val isUp = y < (h / 3)
            val isDown = y > (h * 2 / 3)

            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    if (isLeft != lastLeft) { sendInputToJs("LEFT", isLeft); binding.btnLeft.isPressed = isLeft; lastLeft = isLeft }
                    if (isRight != lastRight && !isLeft) { sendInputToJs("RIGHT", isRight); binding.btnRight.isPressed = isRight; lastRight = isRight }
                    else if (isLeft && lastRight) { sendInputToJs("RIGHT", false); binding.btnRight.isPressed = false; lastRight = false }

                    if (isUp != lastUp) { sendInputToJs("UP", isUp); binding.btnUp.isPressed = isUp; lastUp = isUp }
                    if (isDown != lastDown && !isUp) { sendInputToJs("DOWN", isDown); binding.btnDown.isPressed = isDown; lastDown = isDown }
                    else if (isUp && lastDown) { sendInputToJs("DOWN", false); binding.btnDown.isPressed = false; lastDown = false }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (lastLeft) { sendInputToJs("LEFT", false); binding.btnLeft.isPressed = false }
                    if (lastRight) { sendInputToJs("RIGHT", false); binding.btnRight.isPressed = false }
                    if (lastUp) { sendInputToJs("UP", false); binding.btnUp.isPressed = false }
                    if (lastDown) { sendInputToJs("DOWN", false); binding.btnDown.isPressed = false }
                    lastLeft = false; lastRight = false; lastUp = false; lastDown = false
                }
            }
            true
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun mapButton(view: View, keyName: String) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { v.isPressed = true; sendInputToJs(keyName, true) }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { v.isPressed = false; sendInputToJs(keyName, false) }
            }
            true
        }
    }

    private fun sendInputToJs(key: String, isDown: Boolean) {
        binding.webView.evaluateJavascript("androidButtonEvent('$key', $isDown);", null)
    }

    private fun loadRom(uri: Uri, fileName: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val contentResolver = applicationContext.contentResolver
                val stream = contentResolver.openInputStream(uri)
                val bytes = stream?.readBytes()
                stream?.close()

                if (bytes != null) {
                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    withContext(Dispatchers.Main) {
                        binding.webView.evaluateJavascript("launchGame('$base64', '$fileName');") {
                            hideSystemUI()
                        }
                    }
                }
            } catch (e: Exception) {
            }
        }
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        }
    }
}
