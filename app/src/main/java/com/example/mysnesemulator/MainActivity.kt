package com.example.mysnesemulator

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.MotionEvent
import android.view.View
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

    // Interface para comunicação rápida Android -> JS
    inner class GameControlInterface {
        // Nada precisa vir do JS para o Android por enquanto, 
        // mas a presença da interface permite chamadas mais rápidas se necessário
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { loadRom(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
            
            // OTIMIZAÇÃO: Cache e Hardware
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            
            addJavascriptInterface(GameControlInterface(), "AndroidInterface")

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    android.util.Log.d("WebView", consoleMessage?.message() ?: "")
                    return true
                }
            }

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ) = assetLoader.shouldInterceptRequest(request!!.url)
            }

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
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> sendInputToJs(keyName, true)
                MotionEvent.ACTION_UP -> sendInputToJs(keyName, false)
            }
            // Retorna false para permitir o efeito visual de clique do botão
            false // ou true se quiser sobrescrever totalmente
        }
    }

    private fun sendInputToJs(key: String, isDown: Boolean) {
        // evaluateJavascript é rápido o suficiente para controles simples
        // Chama a função global definida no HTML
        binding.webView.evaluateJavascript("androidButtonEvent('$key', $isDown);", null)
    }

    private fun loadRom(uri: Uri) {
        binding.fabLoadRom.isEnabled = false
        Toast.makeText(this, "Carregando e convertendo ROM...", Toast.LENGTH_SHORT).show()

        // OTIMIZAÇÃO: Processamento pesado fora da UI Thread
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val contentResolver = applicationContext.contentResolver
                
                // Pega nome
                var fileName = "game.sfc"
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex("_display_name")
                        if (index != -1) fileName = cursor.getString(index)
                    }
                }

                // Lê bytes
                val inputStream = contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()

                if (bytes != null) {
                    // Conversão Base64 pode ser lenta para arquivos grandes
                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    
                    withContext(Dispatchers.Main) {
                        // Envia para o JS na Thread Principal
                        binding.webView.evaluateJavascript("launchGame('$base64', '$fileName');") {
                            binding.fabLoadRom.isEnabled = true
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
}
