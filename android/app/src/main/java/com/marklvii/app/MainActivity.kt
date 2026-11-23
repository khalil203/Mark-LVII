package com.marklvii.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

private const val DEFAULT_KEY = BuildConfig.OPENROUTER_KEY
private const val DEFAULT_MODEL = "nvidia/nemotron-3.5-super-vl:free"
private const val DEFAULT_URL = "https://openrouter.ai/api/v1/chat/completions"
private const val DEFAULT_SYSTEM = "You are Mark LVII, a helpful, concise personal AI assistant running on a phone. Answer clearly."

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MarkApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember {
        context.getSharedPreferences("marklvii", Context.MODE_PRIVATE)
    }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var input by remember { mutableStateOf(TextFieldValue("")) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var apiKey by remember { mutableStateOf(prefs.getString("api_key", DEFAULT_KEY) ?: DEFAULT_KEY) }
    var model by remember { mutableStateOf(prefs.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL) }
    var baseUrl by remember { mutableStateOf(prefs.getString("base_url", DEFAULT_URL) ?: DEFAULT_URL) }
    var system by remember { mutableStateOf(prefs.getString("system", DEFAULT_SYSTEM) ?: DEFAULT_SYSTEM) }
    var speak by remember { mutableStateOf(prefs.getBoolean("speak", false)) }

    val tts = remember {
        TextToSpeech(context) { status ->
            if (status != TextToSpeech.SUCCESS) {
                Toast.makeText(context, "Text-to-speech unavailable", Toast.LENGTH_SHORT).show()
            }
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { tts.shutdown() }
    }

    fun loadMessages() {
        val raw = prefs.getString("messages", null) ?: return
        try {
            val arr = JSONArray(raw)
            messages.clear()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                messages.add(ChatMessage(o.getString("role"), o.getString("text")))
            }
        } catch (_: Exception) {}
    }
    fun saveMessages() {
        val arr = JSONArray()
        for (m in messages) {
            if (m.imageBase64 == null) {
                arr.put(JSONObject().put("role", m.role).put("text", m.text))
            }
        }
        prefs.edit().putString("messages", arr.toString()).apply()
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { loadMessages() }

    fun sendMessage(text: String, image: String?) {
        if (text.isBlank() && image == null) return
        messages.add(ChatMessage("user", text, image))
        val history = messages.toList()
        input = TextFieldValue("")
        error = ""
        busy = true
        scope.launch {
            try {
                val reply = Ai.chat(baseUrl, apiKey, model, history, system)
                messages.add(ChatMessage("assistant", reply))
                if (speak) {
                    tts.language = Locale.US
                    tts.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "mark_reply")
                }
                saveMessages()
            } catch (e: Exception) {
                error = e.message ?: "Request failed"
            } finally {
                busy = false
            }
        }
    }

    val requestAudio = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) Toast.makeText(context, "Microphone permission needed", Toast.LENGTH_SHORT).show()
    }
    val speech = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) sendMessage(spoken, null)
        }
    }
    fun startVoice() {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestAudio.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Mark LVII")
        }
        try { speech.launch(intent) } catch (e: Exception) {
            Toast.makeText(context, "Voice input unavailable: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok && cameraUri != null) {
            val b64 = context.contentResolver.openInputStream(cameraUri!!)?.use {
                Base64.encodeToString(it.readBytes(), Base64.NO_WRAP)
            }
            sendMessage("What do you see in this image?", b64)
        }
    }
    fun startCamera() {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        cameraUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        camera.launch(cameraUri!!)
    }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val text = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8).take(8000)
            }.orEmpty()
            input = TextFieldValue((input.text + "\n\n" + text).trim())
        }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Mark LVII") },
                    actions = {
                        IconButton(onClick = {
                            messages.clear()
                            prefs.edit().remove("messages").apply()
                        }) { Icon(Icons.Default.Delete, "Clear chat") }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, "Settings")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(8.dp)
            ) {
                if (error.isNotBlank()) {
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(4.dp)
                    )
                }
                LazyColumn(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    reverseLayout = true
                ) {
                    items(messages) { msg ->
                        Card(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (msg.role == "user")
                                    Color(0xFF1D4ED8) else Color(0xFF1F2937)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                msg.text,
                                color = Color.White,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
                if (busy) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) { CircularProgressIndicator() }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("Ask Mark LVII…") },
                        modifier = Modifier.weight(1f),
                        singleLine = false,
                        maxLines = 3
                    )
                    IconButton(onClick = { startVoice() }) { Icon(Icons.Default.Mic, "Voice") }
                    IconButton(onClick = { startCamera() }) { Icon(Icons.Default.PhotoCamera, "Camera") }
                    IconButton(onClick = { pickFile.launch("text/*") }) { Icon(Icons.Default.AttachFile, "Attach") }
                    IconButton(onClick = { sendMessage(input.text, null) }) { Icon(Icons.Default.Send, "Send") }
                }
            }
        }

        if (showSettings) {
            AlertDialog(
                onDismissRequest = { showSettings = false },
                title = { Text("Settings") },
                text = {
                    Column {
                        OutlinedTextField(apiKey, { apiKey = it }, label = { Text("API key") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(model, { model = it }, label = { Text("Model") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("API URL") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(system, { system = it }, label = { Text("System prompt") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = speak, onCheckedChange = { speak = it })
                            Text("Speak replies aloud")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        prefs.edit()
                            .putString("api_key", apiKey)
                            .putString("model", model)
                            .putString("base_url", baseUrl)
                            .putString("system", system)
                            .putBoolean("speak", speak)
                            .apply()
                        showSettings = false
                    }) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { showSettings = false }) { Text("Cancel") } }
            )
        }
    }
}
