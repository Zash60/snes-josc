package com.example.mysnesemulator

import android.annotation.SuppressLint
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
import java.io.InputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var assetLoader: WebViewAssetLoader

    // Seletor de Arquivos
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { loadRom(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Força tela cheia (Immersive Mode)
        hideSystemUI()

        setupWebView()
        setupControls()

        binding.fabLoadRom.setOnClickListener {
            // Abre seletor de arquivos
            filePickerLauncher.launch("*/*")
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun setupWebView() {
        // Configura AssetLoader para ler arquivos da pasta assets/
        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        binding.webView.apply {
            // Configurações críticas para WebGL e WASM
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.mediaPlaybackRequiresUserGesture = false
            
            // PERFORMANCE MÁXIMA
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            settings.setRenderPriority(WebSettings.RenderPriority.HIGH) // Prioridade Alta para CPU/GPU
            setLayerType(View.LAYER_TYPE_HARDWARE, null) // Garante uso da GPU

            // Cliente Chrome para logs
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    android.util.Log.d("WebViewConsole", consoleMessage?.message() ?: "")
                    return true
                }
            }

            // Cliente WebView para interceptação local
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ) = assetLoader.shouldInterceptRequest(request!!.url)

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    hideSystemUI() // Garante tela cheia após carregar
                }
            }

            // Carrega o arquivo HTML local
            loadUrl("https://appassets.androidplatform.net/assets/index.html")
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupControls() {
        // Mapeia botões da UI para strings de comando
        mapButton(binding.btnUp, "UP")
        mapButton(binding.btnDown, "DOWN")
        mapButton(binding.btnLeft, "LEFT")
        mapButton(binding.btnRight, "RIGHT")
        
        mapButton(binding.btnA, "A")
        mapButton(binding.btnB, "B")
        mapButton(binding.btnX, "X")
        mapButton(binding.btnY, "Y")
        
        mapButton(binding.btnStart, "START")
        mapButton(binding.btnSelect, "SELECT")
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun mapButton(view: View, keyName: String) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    sendInputToJs(keyName, true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    sendInputToJs(keyName, false)
                }
            }
            true // Consome o evento
        }
    }

    private fun sendInputToJs(key: String, isDown: Boolean) {
        // Executa JS diretamente na WebView
        binding.webView.evaluateJavascript("androidButtonEvent('$key', $isDown);", null)
    }

    private fun loadRom(uri: Uri) {
        binding.fabLoadRom.isEnabled = false
        Toast.makeText(this, "Processando ROM...", Toast.LENGTH_SHORT).show()

        // Executa leitura pesada em Thread separada (IO)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val contentResolver = applicationContext.contentResolver
                
                var fileName = "game.sfc"
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex("_display_name")
                        if (index != -1) fileName = cursor.getString(index)
                    }
                }

                val inputStream: InputStream? = contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()

                if (bytes != null) {
                    // Converte para Base64
                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    
                    // Volta para Thread Principal para atualizar UI e JS
                    withContext(Dispatchers.Main) {
                        binding.webView.evaluateJavascript("launchGame('$base64', '$fileName');") {
                            binding.fabLoadRom.isEnabled = true
                            hideSystemUI()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                    binding.fabLoadRom.isEnabled = true
                }
            }
        }
    }

    // Função para esconder barras de navegação (Android 11+ e antigos)
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
