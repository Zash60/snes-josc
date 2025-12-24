package com.example.mysnesemulator

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewAssetLoader
import com.example.mysnesemulator.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var assetLoader: WebViewAssetLoader

    // Interface para comunicação HTML -> Android
    inner class WebAppInterface(private val context: Context) {
        
        @JavascriptInterface
        fun saveStateToDisk(base64Data: String, fileName: String) {
            try {
                // Remove o cabeçalho do Base64 se existir (data:application/octet-stream;base64,)
                val cleanBase64 = if (base64Data.contains(",")) base64Data.split(",")[1] else base64Data
                val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                
                // Salva com o nome do jogo + .state
                val file = File(context.filesDir, "$fileName.state")
                FileOutputStream(file).use { it.write(bytes) }
                
                runOnUiThread {
                    binding.webView.evaluateJavascript("showToast('Salvo no Android!');", null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    binding.webView.evaluateJavascript("showToast('Erro ao gravar disco', true);", null)
                }
            }
        }

        @JavascriptInterface
        fun loadStateFromDisk(fileName: String) {
            try {
                val file = File(context.filesDir, "$fileName.state")
                if (!file.exists()) {
                    runOnUiThread {
                        binding.webView.evaluateJavascript("showToast('Nenhum save para este jogo', true);", null)
                    }
                    return
                }

                val bytes = file.readBytes()
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                
                // Envia de volta para o JS
                runOnUiThread {
                    binding.webView.evaluateJavascript("receiveStateFromAndroid('$base64');", null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    binding.webView.evaluateJavascript("showToast('Erro ao ler disco', true);", null)
                }
            }
        }
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { loadRom(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        hideSystemUI()
        setupWebView()
        setupControls()

        binding.fabLoadRom.setOnClickListener {
            filePickerLauncher.launch("*/*")
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
            
            // Adiciona a interface JS com o nome "AndroidInterface"
            addJavascriptInterface(WebAppInterface(this@MainActivity), "AndroidInterface")

            // Performance
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
        // Mapeamento
        mapButton(binding.btnUp, "UP")
        mapButton(binding.btnDown, "DOWN")
        mapButton(binding.btnLeft, "LEFT")
        mapButton(binding.btnRight, "RIGHT")
        mapButton(binding.btnA, "A")
        mapButton(binding.btnB, "B")
        mapButton(binding.btnX, "X")
        mapButton(binding.btnY, "Y")
        mapButton(binding.btnL, "L")
        mapButton(binding.btnR, "R")
        mapButton(binding.btnStart, "START")
        mapButton(binding.btnSelect, "SELECT")

        // Botões Save/Load chamam JS
        binding.btnSaveState.setOnClickListener {
            binding.webView.evaluateJavascript("triggerSaveState();", null)
        }
        binding.btnLoadState.setOnClickListener {
            binding.webView.evaluateJavascript("triggerLoadState();", null)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun mapButton(view: View, keyName: String) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    binding.webView.evaluateJavascript("androidButtonEvent('$keyName', true);", null)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    binding.webView.evaluateJavascript("androidButtonEvent('$keyName', false);", null)
                }
            }
            true
        }
    }

    private fun loadRom(uri: Uri) {
        Toast.makeText(this, "Carregando...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val contentResolver = applicationContext.contentResolver
                var fileName = "game.sfc"
                contentResolver.query(uri, null, null, null, null)?.use {
                    if (it.moveToFirst()) {
                        val idx = it.getColumnIndex("_display_name")
                        if (idx != -1) fileName = it.getString(idx)
                    }
                }
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
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                }
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
