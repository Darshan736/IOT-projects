package com.example.esp32cam

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.esp32cam.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import android.view.LayoutInflater
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * MainActivity configured for EXCLUSIVE connection to a fixed ESP32 IP.
 * NO dynamic detection, scanning, or configuration allowed.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var objectDetector: ObjectDetectorHelper
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    // IP CONFIGURATION
    private var esp32Ip = "10.54.244.117"
    private val FIXED_ESP32_URL: String
        get() = "http://$esp32Ip/capture"
    private var isRunning = false

    // Text-to-Speech (TTS) members
    private var tts: TextToSpeech? = null
    private var lastAnnouncementTime = 0L
    private var lastClearTime = 0L
    private val ANNOUNCEMENT_COOLDOWN_MS = 3000L
    private val CLEAR_PATH_COOLDOWN_MS = 10000L

    private var currentDetections: List<DetectionResult> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Load saved IP
        val prefs = getSharedPreferences("ESP32_PREFS", MODE_PRIVATE)
        esp32Ip = prefs.getString("esp32_ip", "10.54.244.117") ?: "10.54.244.117"

        objectDetector = ObjectDetectorHelper(this)
        initTTS()
        
        binding.btnAskAi.setOnClickListener {
            startVoiceRecognition()
        }

        binding.btnSettings.setOnClickListener {
            showSettingsDialog()
        }

        // Immediate launch of detection loop
        binding.tvStatus.text = "Status: Connecting to $esp32Ip..."
        startFetchingFrames()
    }

    private fun showSettingsDialog() {
        val input = EditText(this)
        input.setText(esp32Ip)
        input.hint = "Enter ESP32 IP (e.g. 10.54.244.117)"
        
        // Add padding for better UI
        val padding = (16 * resources.displayMetrics.density).toInt()
        val container = android.widget.FrameLayout(this)
        container.setPadding(padding, padding / 2, padding, 0)
        container.addView(input)
        
        AlertDialog.Builder(this)
            .setTitle("ESP32 Configuration")
            .setMessage("Enter the new IP address of your ESP32-CAM:")
            .setView(container)
            .setPositiveButton("Update") { _, _ ->
                val newIp = input.text.toString().trim()
                if (newIp.isNotEmpty()) {
                    esp32Ip = newIp
                    getSharedPreferences("ESP32_PREFS", MODE_PRIVATE).edit()
                        .putString("esp32_ip", esp32Ip)
                        .apply()
                    binding.tvStatus.text = "Status: Connecting to $esp32Ip..."
                    speak("IP updated to $esp32Ip")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun initTTS() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
            }
        }
    }

    private fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask about your surroundings...")
        }
        try {
            startActivityForResult(intent, 100)
        } catch (e: Exception) {
            speak("Speech recognition not available.")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == Activity.RESULT_OK) {
            val result = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val question = result?.get(0)
            if (question != null) {
                // Manually trigger AI guidance with the question immediately
                handleAiGuidance(currentDetections, question)
            }
        }
    }

    private var isCameraConnected = true
    private var connectionLostAnnouncementDone = false

    private fun startFetchingFrames() {
        isRunning = true
        lifecycleScope.launch(Dispatchers.IO) {
            while (isRunning) {
                try {
                    val rawBitmap = fetchFrame()
                    if (rawBitmap != null) {
                        isCameraConnected = true
                        connectionLostAnnouncementDone = false
                        
                        // Standardize resolution for inference stability
                        val bitmap = Bitmap.createScaledBitmap(rawBitmap, 640, 480, true)
                        
                        val detectionOutput = objectDetector.detect(bitmap)
                        
                        withContext(Dispatchers.Main) {
                            currentDetections = detectionOutput.results
                            binding.ivCamera.setImageBitmap(bitmap)
                            binding.overlayView.setResults(detectionOutput.results)
                            
                            val statusText = when {
                                detectionOutput.results.any { it.isWall } -> "Status: OBSTACLE DETECTED"
                                detectionOutput.results.any { !it.isWall } -> "Status: Person Detected"
                                else -> "Status: Path Clear"
                            }
                            
                            binding.tvStatus.text = statusText
                            binding.tvInference.text = "Inference: ${detectionOutput.inferenceTime}ms | FPS: ${1000/(detectionOutput.inferenceTime.coerceAtLeast(1))}"
                            triggerVoiceGuidance(detectionOutput.results)
                        }
                    } else {
                        handleConnectionLoss()
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Fixed-IP Connection Error: ${e.message}")
                    handleConnectionLoss()
                }
                delay(200) 
            }
        }
    }

    private suspend fun handleConnectionLoss() {
        isCameraConnected = false
        updateStatus("Status: CAMERA DISCONNECTED")
        if (!connectionLostAnnouncementDone) {
            withContext(Dispatchers.Main) {
                speak("Camera disconnected. Please check your hotspot.")
            }
            connectionLostAnnouncementDone = true
        }
    }


    private fun triggerVoiceGuidance(results: List<DetectionResult>) {
        if (!isCameraConnected) return

        // 0. CHECK AI MODE
        if (binding.switchAiMode.isChecked) {
            val currentTime = System.currentTimeMillis()
            // Only use AI guidance if not on cooldown, otherwise FALL THROUGH to standard mode
            if (results.isNotEmpty() && (currentTime - lastLlmCallTime > LLM_COOLDOWN_MS)) {
                handleAiGuidance(results)
                return
            }
        }

        val currentTime = System.currentTimeMillis()
        
        // 1. Person Detection (HIGH PRIORITY)
        val bestPerson = results.find { !it.isWall && it.confidence > 0.6f }
        if (bestPerson != null) {
            if (currentTime - lastAnnouncementTime < ANNOUNCEMENT_COOLDOWN_MS) return
            
            // Directional Logic
            val centerX = (bestPerson.boundingBox.left + bestPerson.boundingBox.right) / 2
            val direction = when {
                centerX < 0.33f -> "on left"
                centerX > 0.66f -> "on right"
                else -> "ahead"
            }

            // Distance Estimation (Relative)
            val height = bestPerson.boundingBox.bottom - bestPerson.boundingBox.top
            val distance = when {
                height > 0.7f -> "very close"
                height > 0.4f -> "at moderate distance"
                else -> "far away"
            }

            speak("Person $direction, $distance")
            lastAnnouncementTime = currentTime
            return
        }

        // 2. Obstacle Detection (MEDIUM PRIORITY)
        val obstacle = results.find { it.isWall && it.confidence > 0.3f }
        if (obstacle != null) {
            if (currentTime - lastAnnouncementTime < ANNOUNCEMENT_COOLDOWN_MS) return
            
            val centerX = (obstacle.boundingBox.left + obstacle.boundingBox.right) / 2
            val direction = when {
                centerX < 0.33f -> "on left"
                centerX > 0.66f -> "on right"
                else -> "ahead"
            }
            
            speak("Obstacle $direction")
            lastAnnouncementTime = currentTime
            return
        }

        // 3. Path Clear Notification (LOW PRIORITY)
        if (results.isEmpty()) {
            if (currentTime - lastClearTime > CLEAR_PATH_COOLDOWN_MS) {
                speak("Path is clear")
                lastClearTime = currentTime
                lastAnnouncementTime = currentTime 
            }
        }
    }

    private val aiAssistant = AiAssistantHelper()
    private var lastLlmCallTime = 0L
    private val LLM_COOLDOWN_MS = 15000L 

    private fun handleAiGuidance(results: List<DetectionResult>, question: String? = null) {
        val currentTime = System.currentTimeMillis()
        
        // If it's a passive update (no question), respect the cooldown
        if (question == null && currentTime - lastLlmCallTime < LLM_COOLDOWN_MS) return
        
        lastLlmCallTime = currentTime

        // Summarize detections for the LLM with requested metadata
        val detectionsSummary = results.joinToString(", ") { det ->
            val label = if (det.isWall) "Obstacle" else "Person"
            val pos = if (det.boundingBox.centerX() < 0.33f) "left" else if (det.boundingBox.centerX() > 0.66f) "right" else "center"
            
            // Map area to relative distance
            val area = det.boundingBox.width() * det.boundingBox.height()
            val dist = when {
                area > 0.5f -> "near"
                area > 0.15f -> "medium"
                else -> "far"
            }
            "{type: $label, pos: $pos, dist: $dist}"
        }

        val sceneStatus = if (results.any { it.isWall }) "obstacle present" else "clear"
        val fullContext = """
            Objects: [$detectionsSummary]
            Scene status: $sceneStatus
            Lighting: normal
            User context: ${if (question != null) "asking a question" else "walking"}
        """.trimIndent()

        lifecycleScope.launch {
            val naturalText = aiAssistant.getNaturalGuidance(fullContext, question)
            if (naturalText != null) {
                speak(naturalText)
            }
        }
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun fetchFrame(): Bitmap? {
        val request = Request.Builder().url(FIXED_ESP32_URL).build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bytes = response.body?.bytes() ?: return null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch ( e: IOException) {
            null
        }
    }

    private suspend fun updateStatus(status: String) {
        withContext(Dispatchers.Main) {
            binding.tvStatus.text = status
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        objectDetector.close()
        tts?.stop()
        tts?.shutdown()
    }
}
