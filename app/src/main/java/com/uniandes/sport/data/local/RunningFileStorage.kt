package com.uniandes.sport.data.local

import android.content.Context
import com.uniandes.sport.models.RunSession
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RunningFileStorage {

    private fun baseDir(context: Context): File {
        val dir = File(context.filesDir, "running")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun timestampSuffix(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    fun exportRunReport(context: Context, run: RunSession): File {
        val exportedAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date())
        val runDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date(run.timestamp))
        val payload = buildString {
            appendLine("UNIANDES SPORTS RUN REPORT")
            appendLine()
            appendLine("Exported at: $exportedAt")
            appendLine("Run date: $runDate")
            appendLine("Run ID: ${run.id.ifBlank { "N/A" }}")
            appendLine("User ID: ${run.userId.ifBlank { "N/A" }}")
            appendLine("Distance: ${String.format(Locale.US, "%.2f", run.distanceKm)} km")
            appendLine("Average pace: ${run.pace}")
            appendLine("Elevation gain: ${String.format(Locale.US, "%.1f", run.elevationGain)} m")
            appendLine("Cadence: ${run.cadence} spm")
            appendLine()
            appendLine("AI Coach Feedback:")
            appendLine(if (run.aiFeedback.isBlank()) "No AI feedback available." else run.aiFeedback)
        }

        val safeId = run.id.ifBlank { "run" }
        val file = File(baseDir(context), "run_report_${safeId}_${timestampSuffix()}.txt")
        file.writeText(payload)
        return file
    }

    fun exportRunHistory(context: Context, runs: List<RunSession>): File {
        val exportedAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date())
        val payload = buildString {
            appendLine("UNIANDES SPORTS RUN HISTORY")
            appendLine()
            appendLine("Exported at: $exportedAt")
            appendLine("Total runs: ${runs.size}")
            runs.forEachIndexed { index, run ->
                val runDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(Date(run.timestamp))
                appendLine()
                appendLine("RUN #${index + 1}")
                appendLine("Date: $runDate")
                appendLine("Run ID: ${run.id.ifBlank { "N/A" }}")
                appendLine("Distance: ${String.format(Locale.US, "%.2f", run.distanceKm)} km")
                appendLine("Average pace: ${run.pace}")
                appendLine("Elevation gain: ${String.format(Locale.US, "%.1f", run.elevationGain)} m")
                appendLine("Cadence: ${run.cadence} spm")
                appendLine("AI Feedback: ${if (run.aiFeedback.isBlank()) "No AI feedback available." else run.aiFeedback}")
            }
        }

        val file = File(baseDir(context), "run_history_${timestampSuffix()}.txt")
        file.writeText(payload)
        return file
    }
}
