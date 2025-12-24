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
    ) { uri -> uri?.let { loadRom(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        hideSystemUI()
        setupWebView()
        setupControls()

        // Garante que o botão esteja visível ao iniciar
        binding.fabLoadRom.visibility = View.VISIBLE
        
        // Ação do clique no botão
        binding.fabLoadRom.setOnClickListener {
            // Abre o explorador de arquivos filtrando tudo (para garantir compatibilidade)
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
            
            // Otimizações
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
        // Mapeamento dos botões (Lógica de Toque)
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

        // Botões de Sistema (Save/Load)
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
        // NÃO escondemos mais o botão. Ele fica visível para você trocar de jogo.
        Toast.makeText(this, "Carregando Jogo...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val contentResolver = applicationContext.contentResolver
                
                // Pega nome do arquivo (opcional, mas bom para logs)
                var fileName = "game.sfc"
                contentResolver.query(uri, null, null, null, null)?.use {
                    if (it.moveToFirst()) {
                        val idx = it.getColumnIndex("_display_name")
                        if (idx != -1) fileName = it.getString(idx)
                    }
                }

                // Lê o arquivo
                val stream = contentResolver.openInputStream(uri)
                val bytes = stream?.readBytes()
                stream?.close()

                if (bytes != null) {
                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    
                    withContext(Dispatchers.Main) {
                        // Envia para o WebView
                        binding.webView.evaluateJavascript("launchGame('$base64', '$fileName');") {
                            hideSystemUI() // Garante tela cheia novamente
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Erro ao abrir: ${e.message}", Toast.LENGTH_LONG).show()
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
