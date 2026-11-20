package com.jorge.livetranslate

import android.graphics.Color
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.jorge.livetranslate.R

class MainActivity : AppCompatActivity() {

    private var translator: Translator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val spinnerOrigen = findViewById<Spinner>(R.id.spinnerOrigen)
        val spinnerDestino = findViewById<Spinner>(R.id.spinnerDestino)
        val seekTamano = findViewById<SeekBar>(R.id.seekTamano)
        val seekOpacidad = findViewById<SeekBar>(R.id.seekOpacidad)
        val tvTamanoLabel = findViewById<TextView>(R.id.tvTamanoLabel)
        val tvOpacidadLabel = findViewById<TextView>(R.id.tvOpacidadLabel)
        val tvVistaPrevia = findViewById<TextView>(R.id.tvVistaPrevia)
        val etPruebaTexto = findViewById<EditText>(R.id.etPruebaTexto)
        val btnIniciarTraductor = findViewById<Button>(R.id.btnIniciarTraductor)

        val idiomas = listOf(
            "Inglés", "Español", "Francés", "Alemán",
            "Italiano", "Portugués", "Japonés", "Coreano"
        )

        val codigos = listOf(
            TranslateLanguage.ENGLISH, TranslateLanguage.SPANISH,
            TranslateLanguage.FRENCH, TranslateLanguage.GERMAN,
            TranslateLanguage.ITALIAN, TranslateLanguage.PORTUGUESE,
            TranslateLanguage.JAPANESE, TranslateLanguage.KOREAN
        )

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, idiomas)
        spinnerOrigen.adapter = adapter
        spinnerDestino.adapter = adapter
        spinnerOrigen.setSelection(0)
        spinnerDestino.setSelection(1)

        seekTamano.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val size = if (progress < 12) 12 else progress
                tvTamanoLabel.text = "Tamaño de texto: ${size}sp"
                tvVistaPrevia.textSize = size.toFloat()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekOpacidad.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvOpacidadLabel.text = "Opacidad de fondo: $progress%"
                val alpha = (progress * 255) / 100
                tvVistaPrevia.setBackgroundColor(Color.argb(alpha, 0, 0, 0))
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnIniciarTraductor.setOnClickListener {
            val langOrigen = codigos[spinnerOrigen.selectedItemPosition]
            val langDestino = codigos[spinnerDestino.selectedItemPosition]
            val texto = etPruebaTexto.text.toString().ifBlank { "Hello, welcome to VoxSub AI" }

            tvVistaPrevia.text = "Descargando modelo IA..."
            ejecutarTraduccion(langOrigen, langDestino, texto, tvVistaPrevia)
        }
    }

    private fun ejecutarTraduccion(origen: String, destino: String, texto: String, salida: TextView) {
        translator?.close()

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(origen)
            .setTargetLanguage(destino)
            .build()

        translator = Translation.getClient(options)
        val conditions = DownloadConditions.Builder().build()

        translator?.downloadModelIfNeeded(conditions)
            ?.addOnSuccessListener {
                salida.text = "Traduciendo..."
                translator?.translate(texto)
                    ?.addOnSuccessListener { textoTraducido ->
                        salida.text = textoTraducido
                    }
                    ?.addOnFailureListener { e ->
                        salida.text = "Error al traducir: ${e.localizedMessage}"
                    }
            }
            ?.addOnFailureListener { e ->
                salida.text = "Error descargando modelo IA: ${e.localizedMessage}"
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        translator?.close()
    }
}

