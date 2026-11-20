package com.jorge.livetranslate

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.*
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.net.URL
import java.util.zip.ZipInputStream

data class LanguageOption(
    val displayName: String,
    val mlKitCode: String,
    val voskFolderName: String,
    val voskZipUrl: String
)

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var languageSpinner: Spinner
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var projectionManager: MediaProjectionManager
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val languages = listOf(
        LanguageOption(
            "🇺🇸 Inglés  ➔  🇲🇽 Español",
            TranslateLanguage.ENGLISH,
            "vosk-model-small-en-us-0.15",
            "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
        ),
        LanguageOption(
            "🇧🇷 Portugués  ➔  🇲🇽 Español",
            TranslateLanguage.PORTUGUESE,
            "vosk-model-small-pt-0.3",
            "https://alphacephei.com/vosk/models/vosk-model-small-pt-0.3.zip"
        ),
        LanguageOption(
            "🇫🇷 Francés  ➔  🇲🇽 Español",
            TranslateLanguage.FRENCH,
            "vosk-model-small-fr-0.22",
            "https://alphacephei.com/vosk/models/vosk-model-small-fr-0.22.zip"
        ),
        LanguageOption(
            "🇩🇪 Alemán  ➔  🇲🇽 Español",
            TranslateLanguage.GERMAN,
            "vosk-model-small-de-0.15",
            "https://alphacephei.com/vosk/models/vosk-model-small-de-0.15.zip"
        ),
        LanguageOption(
            "🇮🇹 Italiano  ➔  🇲🇽 Español",
            TranslateLanguage.ITALIAN,
            "vosk-model-small-it-0.22",
            "https://alphacephei.com/vosk/models/vosk-model-small-it-0.22.zip"
        ),
        LanguageOption(
            "🇯🇵 Japonés  ➔  🇲🇽 Español",
            TranslateLanguage.JAPANESE,
            "vosk-model-small-ja-0.22",
            "https://alphacephei.com/vosk/models/vosk-model-small-ja-0.22.zip"
        )
    )

    private var selectedLanguage: LanguageOption = languages[0]

    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val intent = Intent(this, LiveCaptionService::class.java).apply {
                putExtra("RESULT_CODE", result.resultCode)
                putExtra("DATA", result.data)
                putExtra("SOURCE_LANG", selectedLanguage.mlKitCode)
                putExtra("VOSK_FOLDER", selectedLanguage.voskFolderName)
            }
            ContextCompat.startForegroundService(this, intent)
            statusText.text = "✅ Subtítulos activos:\n${selectedLanguage.displayName}"
        } else {
            statusText.text = "⚠️ Permiso de captura de audio cancelado."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(56, 56, 56, 56)
            setBackgroundColor(Color.parseColor("#121212"))
        }

        val appTitle = TextView(this).apply {
            text = "LiveTranslate"
            textSize = 26f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }

        val titleLabel = TextView(this).apply {
            text = "Selecciona el idioma del audio:"
            textSize = 15f
            setTextColor(Color.parseColor("#BBBBBB"))
            setPadding(0, 0, 0, 12)
        }

        languageSpinner = Spinner(this).apply {
            setBackgroundColor(Color.parseColor("#242424"))
            setPadding(16, 16, 16, 16)
            adapter = object : ArrayAdapter<String>(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                languages.map { it.displayName }
            ) {
                override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                    val v = super.getView(position, convertView, parent) as TextView
                    v.setTextColor(Color.WHITE)
                    v.textSize = 17f
                    return v
                }
            }
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    selectedLanguage = languages[position]
                    startButton.isEnabled = false
                    prepareOfflineModels(selectedLanguage)
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }

        statusText = TextView(this).apply {
            text = "Verificando modelos offline..."
            textSize = 15f
            setTextColor(Color.parseColor("#E0E0E0"))
            gravity = Gravity.CENTER
            setPadding(0, 40, 0, 40)
        }

        startButton = Button(this).apply {
            text = "▶ INICIAR SUBTÍTULOS EN VIVO"
            isEnabled = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1E88E5"))
            setPadding(36, 32, 36, 32)
            setOnClickListener { checkPermissionsAndStart() }
        }

        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 24)
        }

        stopButton = Button(this).apply {
            text = "⏹ DETENER"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#D32F2F"))
            setPadding(36, 28, 36, 28)
            setOnClickListener {
                stopService(Intent(this@MainActivity, LiveCaptionService::class.java))
                statusText.text = "Subtítulos detenidos."
            }
        }

        layout.addView(appTitle)
        layout.addView(titleLabel)
        layout.addView(languageSpinner)
        layout.addView(statusText)
        layout.addView(startButton)
        layout.addView(spacer)
        layout.addView(stopButton)
        setContentView(layout)
    }

    private fun prepareOfflineModels(lang: LanguageOption) {
        statusText.text = "Preparando diccionario ${lang.displayName}..."
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(lang.mlKitCode)
            .setTargetLanguage(TranslateLanguage.SPANISH)
            .build()
        val translator = Translation.getClient(options)
        val conditions = DownloadConditions.Builder().build()

        translator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                downloadVoskModelIfNeeded(lang)
            }
            .addOnFailureListener { e ->
                statusText.text = "Error descargando diccionario: ${e.message}"
            }
    }

    private fun downloadVoskModelIfNeeded(lang: LanguageOption) {
        val modelDir = File(filesDir, lang.voskFolderName)
        if (modelDir.exists() && modelDir.isDirectory) {
            statusText.text = "✅ Listo (${lang.displayName}).\nToca Iniciar para comenzar."
            startButton.isEnabled = true
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    statusText.text = "Descargando voz ${lang.displayName}\n(~45 MB, solo la primera vez)..."
                }
                val zipUrl = URL(lang.voskZipUrl)
                zipUrl.openStream().use { input ->
                    ZipInputStream(input).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val outFile = File(filesDir, entry.name)
                            if (entry.isDirectory) {
                                outFile.mkdirs()
                            } else {
                                outFile.parentFile?.mkdirs()
                                outFile.outputStream().use { zis.copyTo(it) }
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    statusText.text = "✅ Listo (${lang.displayName}).\nToca Iniciar para comenzar."
                    startButton.isEnabled = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusText.text = "Error bajando modelo de voz: ${e.message}"
                }
            }
        }
    }

    private fun checkPermissionsAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            statusText.text = "Activa 'Mostrar sobre otras apps' y vuelve."
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 101)
            return
        }
        captureLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}

class LiveCaptionService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var audioRecord: AudioRecord? = null
    private var windowManager: WindowManager? = null
    private var overlayContainer: LinearLayout? = null
    private var originalText: TextView? = null
    private var translatedText: TextView? = null
    private var translator: Translator? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotification()
        setupFloatingOverlay()

        val resultCode = intent?.getIntExtra("RESULT_CODE", Activity.RESULT_CANCELED) ?: return START_NOT_STICKY
        val sourceLang = intent.getStringExtra("SOURCE_LANG") ?: TranslateLanguage.ENGLISH
        val voskFolder = intent.getStringExtra("VOSK_FOLDER") ?: "vosk-model-small-en-us-0.15"

        val data: Intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("DATA", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("DATA")
        } ?: return START_NOT_STICKY

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLang)
            .setTargetLanguage(TranslateLanguage.SPANISH)
            .build()
        translator = Translation.getClient(options)

        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mgr.getMediaProjection(resultCode, data)

        startInternalAudioCapture(voskFolder)
        return START_NOT_STICKY
    }

    private fun startForegroundNotification() {
        val channelId = "live_translate_channel"
        val channel = NotificationChannel(channelId, "LiveTranslate Audio", NotificationManager.IMPORTANCE_LOW)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("LiveTranslate activo")
            .setContentText("Subtítulos bilingües en vivo")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()

        startForeground(1, notification)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        overlayContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#E0000000"))
            setPadding(36, 20, 36, 24)
            gravity = Gravity.CENTER
        }

        originalText = TextView(this).apply {
            text = "🎧 Escuchando audio interno..."
            textSize = 14f
            setTextColor(Color.parseColor("#CCCCCC"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 6)
        }

        translatedText = TextView(this).apply {
            text = "Esperando diálogo..."
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        overlayContainer?.addView(originalText)
        overlayContainer?.addView(translatedText)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 180
        }

        var initialY = 0
        var initialTouchY = 0f
        overlayContainer?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = params.y
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.y = initialY + (initialTouchY - event.rawY).toInt()
                    windowManager?.updateViewLayout(overlayContainer, params)
                    true
                }
                else -> false
            }
        }

        windowManager?.addView(overlayContainer, params)
    }

    @SuppressLint("MissingPermission")
    private fun startInternalAudioCapture(voskFolder: String) {
        val projection = mediaProjection ?: return
        val sampleRate = 16000

        val captureConfig = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()

        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ) * 2

        audioRecord = AudioRecord.Builder()
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(bufferSize)
            .setAudioPlaybackCaptureConfig(captureConfig)
            .build()

        isRunning = true
        serviceScope.launch {
            try {
                val modelPath = File(filesDir, voskFolder).absolutePath
                val model = Model(modelPath)
                val recognizer = Recognizer(model, sampleRate.toFloat())
                val buffer = ByteArray(4096)

                audioRecord?.startRecording()

                while (isRunning) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        val isFinal = recognizer.acceptWaveForm(buffer, read)
                        val jsonStr = if (isFinal) recognizer.result else recognizer.partialResult
                        val key = if (isFinal) "text" else "partial"
                        val spokenText = JSONObject(jsonStr).optString(key, "").trim()

                        if (spokenText.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                originalText?.text = spokenText
                            }
                            translateAndShow(spokenText)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    translatedText?.text = "Error de captura: ${e.message}"
                }
            }
        }
    }

    private fun translateAndShow(sourceText: String) {
        translator?.translate(sourceText)
            ?.addOnSuccessListener { spanishText ->
                translatedText?.text = spanishText
            }
    }

    override fun onDestroy() {
        isRunning = false
        serviceScope.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        mediaProjection?.stop()
        overlayContainer?.let { windowManager?.removeView(it) }
        super.onDestroy()
    }
}
