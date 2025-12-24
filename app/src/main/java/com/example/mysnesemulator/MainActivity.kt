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

        // Ativa modo tela cheia (esconde barra de status e navegação)
        hideSystemUI()

        setupWebView()
        setupControls()

        binding.fabLoadRom.setOnClickListener {
            filePickerLauncher.launch("*/*") // Aceita qualquer extensão, filtro é feito visualmente
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun setupWebView() {
        // Configura o loader para interceptar chamadas locais
        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        binding.webView.apply {
            // Configurações de Web
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.mediaPlaybackRequiresUserGesture = false
            
            // Otimizações de Performance
            settings.cacheMode = WebSettings.LOAD_NO_CACHE // Evita cache de versões antigas do HTML
            setLayerType(View.LAYER_TYPE_HARDWARE, null) // Força GPU

            // Configuração do Cliente Chrome (Logs)
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    android.util.Log.d("WebViewConsole", consoleMessage?.message() ?: "")
                    return true
                }
            }

            // Configuração do Cliente WebView (Interceptação de Assets)
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ) = assetLoader.shouldInterceptRequest(request!!.url)

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // Re-aplica tela cheia caso o teclado tenha aparecido
                    hideSystemUI()
                }
            }

            // Carrega o index.html
            loadUrl("https://appassets.androidplatform.net/assets/index.html")
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupControls() {
        // Mapeamento dos botões da interface para comandos
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
                    v.isPressed = true // Feedback visual nativo
                    sendInputToJs(keyName, true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    sendInputToJs(keyName, false)
                }
            }
            // Retorna true para indicar que consumimos o evento (evita scroll/zoom acidental)
            true 
        }
    }

    private fun sendInputToJs(key: String, isDown: Boolean) {
        // Envia o comando JS instantaneamente
        binding.webView.evaluateJavascript("androidButtonEvent('$key', $isDown);", null)
    }

    private fun loadRom(uri: Uri) {
        binding.fabLoadRom.isEnabled = false
        Toast.makeText(this, "Processando ROM...", Toast.LENGTH_SHORT).show()

        // Usa Coroutines para não travar a UI durante a leitura do arquivo
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val contentResolver = applicationContext.contentResolver
                
                // Tenta pegar o nome do arquivo
                var fileName = "game.sfc"
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex("_display_name")
                        if (index != -1) fileName = cursor.getString(index)
                    }
                }

                // Lê os bytes do arquivo
                val inputStream = contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()

                if (bytes != null) {
                    // Converte para Base64 (pode ser pesado, por isso estamos no IO)
                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    
                    withContext(Dispatchers.Main) {
                        // Injeta no JS na Thread Principal
                        binding.webView.evaluateJavascript("launchGame('$base64', '$fileName');") {
                            binding.fabLoadRom.isEnabled = true
                            hideSystemUI() // Garante tela cheia ao começar
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
