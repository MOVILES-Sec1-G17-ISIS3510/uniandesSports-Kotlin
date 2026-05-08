package com.uniandes.sport.data.local

import android.content.Context
import com.uniandes.sport.models.Reto
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// almacenamiento en archivos locales para exportar snapshots de retos como json.
// los archivos se guardan en context.filesdir/retos/ (almacenamiento privado de la app).
// segun las diapos, los app-specific files son para datos privados que se eliminan
// al desinstalar la app. se pueden inspeccionar desde android studio > device explorer.
// este patron es util para debugging, auditoria y analytics offline
object RetosFileStorage {

    // directorio base: filesdir/retos/
    private fun baseDir(context: Context): File {
        val dir = File(context.filesDir, "retos")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    // sufijo de timestamp para que cada archivo sea unico
    private fun timestampSuffix(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    // exporta un snapshot de todos los retos como json.
    // incluye metadata (fecha de exportacion, cantidad) y la lista completa
    fun exportRetosSnapshot(context: Context, retos: List<Reto>): File {
        val payload = JSONObject().apply {
            put("exportedAt", System.currentTimeMillis())
            put("type", "retos_snapshot")
            put("count", retos.size)
            put("items", JSONArray().apply {
                retos.forEach { reto ->
                    put(JSONObject().apply {
                        put("id", reto.id)
                        put("title", reto.title)
                        put("sport", reto.sport)
                        put("difficulty", reto.difficulty)
                        put("type", reto.type)
                        put("goalLabel", reto.goalLabel)
                        put("createdBy", reto.createdBy)
                        put("status", reto.status)
                        put("participantsCount", reto.participantsCount)
                        put("progress", reto.progress)
                    })
                }
            })
        }

        val file = File(baseDir(context), "retos_snapshot_${timestampSuffix()}.json")
        file.writeText(payload.toString(2))
        return file
    }

    // exporta el detalle completo de un reto individual incluyendo participantes y progreso
    fun exportRetoDetail(context: Context, reto: Reto): File {
        val payload = JSONObject().apply {
            put("exportedAt", System.currentTimeMillis())
            put("retoType", "reto_detail")
            put("id", reto.id)
            put("title", reto.title)
            put("sport", reto.sport)
            put("difficulty", reto.difficulty)
            put("challengeType", reto.type)
            put("goalLabel", reto.goalLabel)
            put("createdBy", reto.createdBy)
            put("status", reto.status)
            put("participants", JSONArray(reto.participants))
            put("participantsCount", reto.participantsCount)
            put("progress", reto.progress)
            put("progressByUser", JSONObject(reto.progressByUser.mapValues { it.value }))
            put("startDate", reto.startDate?.toDate()?.time ?: 0L)
            put("endDate", reto.endDate?.toDate()?.time ?: 0L)
        }

        val safeId = reto.id.ifBlank { "reto" }
        val file = File(baseDir(context), "reto_${safeId}_${timestampSuffix()}.json")
        file.writeText(payload.toString(2))
        return file
    }
}
