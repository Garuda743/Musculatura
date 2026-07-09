package com.example

import android.app.Application
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.example.data.db.AppDatabase
import com.example.data.repository.WorkoutRepository
import com.example.ui.WorkoutViewModel
import com.example.ui.WorkoutViewModelFactory
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Setup database and repository
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = WorkoutRepository(database.workoutDao())

        // Obtain ViewModel with Factory
        val viewModel: WorkoutViewModel by viewModels {
            WorkoutViewModelFactory(application, repository)
        }

        setContent {
            MyApplicationTheme {
                WebViewScreen(
                    viewModel = viewModel,
                    onWebViewCreated = { wv ->
                        webView = wv
                        setupBridgeCollection(viewModel, wv)
                    }
                )
            }
        }
    }

    private fun setupBridgeCollection(viewModel: WorkoutViewModel, wv: WebView) {
        // Collect messages from Gemini Coach and push to WebView in real-time
        lifecycleScope.launch {
            viewModel.chatMessages.collect { messages ->
                val jsonArray = JSONArray()
                messages.forEach { msg ->
                    val obj = JSONObject().apply {
                        put("text", msg.text)
                        put("isUser", msg.isUser)
                        put("timestamp", msg.timestamp)
                    }
                    jsonArray.put(obj)
                }
                // Double escape single quotes and backslashes for JS execution safety
                val escapedJson = jsonArray.toString()
                    .replace("\\", "\\\\")
                    .replace("'", "\\'")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                
                wv.post {
                    wv.evaluateJavascript("javascript:if(window.onChatMessagesUpdated) { window.onChatMessagesUpdated('$escapedJson') }", null)
                }
            }
        }

        // Collect loading state from Gemini IA and push to WebView in real-time
        lifecycleScope.launch {
            viewModel.isAiLoading.collect { isLoading ->
                wv.post {
                    wv.evaluateJavascript("javascript:if(window.onAiLoadingUpdated) { window.onAiLoadingUpdated($isLoading) }", null)
                }
            }
        }
    }
}

@Composable
fun WebViewScreen(viewModel: WorkoutViewModel, onWebViewCreated: (WebView) -> Unit) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                webViewClient = WebViewClient()
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    databaseEnabled = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                }
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun askGemini(message: String) {
                        viewModel.sendMessageToCoach(message)
                    }
                }, "AndroidBridge")

                loadUrl("file:///android_asset/index.html")
                onWebViewCreated(this)
            }
        }
    )
}
