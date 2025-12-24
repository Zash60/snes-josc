package com.example.mysnesemulator

import android.os.Bundle
import android.util.Base64
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader
import com.example.mysnesemulator.databinding.ActivityMainBinding
import java.io.InputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var assetLoader: WebViewAssetLoader

    // Seletor de Arquivos (ROMs)
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            loadRomIntoWebView(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Desabilitar FAB até a página carregar
        binding.fabLoadRom.isEnabled = false

        setupWebView()

        binding.fabLoadRom.setOnClickListener {
            // Filtra para arquivos comuns de SNES ou todos
            filePickerLauncher.launch("*/*")
        }
    }

    private fun setupWebView() {
        // Configura o Loader para ler o HTML da pasta assets
        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        binding.webView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.allowFileAccessFromFileURLs = true
            settings.mediaPlaybackRequiresUserGesture = false
            
            // Hardware acceleration
            setLayerType(WebView.LAYER_TYPE_HARDWARE, null)

            webChromeClient = object : WebChromeClient() {
                // Redireciona logs do JS para o Logcat do Android
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    android.util.Log.d("WebViewConsole", "${consoleMessage?.message()} -- From line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}")
                    return true
                }
            }

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ) = assetLoader.shouldInterceptRequest(request!!.url)

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    binding.fabLoadRom.isEnabled = true
                }
            }

            // Carrega o arquivo HTML local usando o domínio virtual
            loadUrl("https://appassets.androidplatform.net/assets/index.html")
        }
    }

    private fun loadRomIntoWebView(uri: android.net.Uri) {
        try {
            val contentResolver = applicationContext.contentResolver
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            
            // Obtém o nome do arquivo para passar ao emulador
            var fileName = "game.sfc"
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex("_display_name")
                    if (index != -1) fileName = cursor.getString(index)
                }
            }

            inputStream?.use { stream ->
                val bytes = stream.readBytes()
                // Converte ROM para Base64
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                
                // Executa o script JS injetando a ROM
                val jsCommand = "launchGame('$base64', '$fileName');"
                
                binding.webView.evaluateJavascript(jsCommand) { result ->
                    android.util.Log.d("Emulator", "ROM enviada para JS: $result")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("Emulator", "Erro ao carregar ROM: ${e.message}")
            Toast.makeText(this, "Erro ao carregar ROM: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
