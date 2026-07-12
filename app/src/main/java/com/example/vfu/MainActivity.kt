package com.example.vfu

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    private lateinit var lineChart: LineChart
    private lateinit var tvStatus: TextView
    private val dataSets = ArrayList<ILineDataSet>()
    private var currentData = mutableListOf<DataRow>()

    // KRAV: MS Excel Standard 8 färger (Android-vänliga Int-färger)
    private val excelColors = intArrayOf(
        Color.parseColor("#4472C4"), Color.parseColor("#ED7D31"),
        Color.parseColor("#A5A5A5"), Color.parseColor("#FFC000"),
        Color.parseColor("#5B9BD5"), Color.parseColor("#70AD47"),
        Color.parseColor("#264478"), Color.parseColor("#9E480E")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lineChart = findViewById(R.id.lineChart)
        tvStatus = findViewById(R.id.tvStatus)
        val btnSelectFile = findViewById<Button>(R.id.btnSelectFile)
        val checkboxContainer = findViewById<LinearLayout>(R.id.checkboxContainer)

        // KRAV: Gör grafytan vit och konfigurera inbyggd zoom
        lineChart.setBackgroundColor(Color.WHITE)
        lineChart.setDrawGridBackground(false)
        lineChart.description.isEnabled = false
        lineChart.isDragEnabled = true
        lineChart.isScaleXEnabled = true
        lineChart.isScaleYEnabled = true

        // Skapa de 8 serierna (DataSets)
        for (i in 0 until 8) {
            val set = LineDataSet(ArrayList<Entry>(), "Kanal ${i + 1}") // KRAV: Namn i legend
            set.color = excelColors[i] // KRAV: Excel-färg
            set.setDrawCircles(false) // Skippa klumpiga punkter, visa bara linjen
            set.lineWidth = 2f
            dataSets.add(set)
        }
        lineChart.data = LineData(dataSets)

        // KRAV: Dynamiska kryssrutor för att tända/släcka kanaler
        for (i in 0 until 8) {
            val cb = CheckBox(this).apply {
                text = "Ch ${i + 1}"
                isChecked = true
                setOnCheckedChangeListener { _, isChecked ->
                    val set = lineChart.data.getDataSetByIndex(i) as LineDataSet
                    set.isVisible = isChecked
                    lineChart.invalidate() // Rita om grafen
                }
            }
            checkboxContainer.addView(cb)
        }

        btnSelectFile.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
            }
            filePickerLauncher.launch(intent)
        }
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                tvStatus.text = "Laddar fil..."
                readLogFileWithoutLock(uri)
            }
        }
    }

    // KRAV: Läser hela filen på en gång utan att låsa den
    private fun readLogFileWithoutLock(uri: Uri) {
        currentData.clear()
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val trimmedLine = line!!.trim()
                        if (trimmedLine.isEmpty() || trimmedLine.startsWith("Dato") || trimmedLine.startsWith("-")) continue

                        val tokens = trimmedLine.split("|")
                        if (tokens.size >= 9) {
                            val timestamp = tokens[0].trim()
                            val channels = DoubleArray(8)
                            for (i in 0 until 8) {
                                channels[i] = tokens[i + 1].trim().toDouble()
                            }
                            currentData.add(DataRow(timestamp, channels))
                        }
                    }
                }
            }
            updateGraphView()
            tvStatus.text = "Fil inläst: ${currentData.size} rader"
        } catch (e: Exception) {
            tvStatus.text = "Fel vid läsning: ${e.message}"
        }
    }

    private fun updateGraphView() {
        // Töm gamla punkter
        for (i in 0 until 8) {
            (dataSets[i] as LineDataSet).clear()
        }

        // Fyll på med ny data utifrån antal sampel (index)
        for (rowIndex in currentData.indices) {
            val row = currentData[rowIndex]
            for (ch in 0 until 8) {
                // MPAndroidChart vill ha Float för X och Y
                val entry = Entry(rowIndex.toFloat(), row.channels[ch].toFloat())
                lineChart.data.addEntry(entry, ch)
            }
        }

        // Meddela grafen att datan har ändrats och rita om
        lineChart.data.notifyDataChanged()
        lineChart.notifyDataSetChanged()
        lineChart.invalidate()
    }
}

data class DataRow(val timestamp: String, val channels: DoubleArray)