package com.uniandes.sport.data.cache

import android.util.Log
import android.util.LruCache

// cache lru personalizado para resultados de analisis de ia.
// implementa android.util.lrucache que internamente usa un linkedhashmap
// con ordering por acceso (access-order), lo que permite eviccion automatica
// del elemento menos recientemente usado cuando se supera el tamano maximo.
//
// segun las diapos (isis-3510), lrucache es thread-safe y es ideal para
// cachear datos costosos como respuestas de apis externas.
//
// decisiones de implementacion:
// - tamano: se define por numero de entradas (no bytes), porque los valores
//   son strings de texto (feedback de ia) que ocupan poco espacio.
//   usamos 50 entradas como maximo, suficiente para el historico de un usuario
//   activo sin riesgo de oom.
// - key: "{tipo}_{eventId}_{userId}" para identificar resultados unicos.
//   tipo puede ser "pose" (analisis de calistenia) o "track" (analisis de sesion).
// - value: el texto de respuesta de la ia (feedback o json de progreso).
// - politica de eviccion: lru (least recently used). si el cache esta lleno y
//   se agrega una nueva entrada, se elimina la que lleva mas tiempo sin acceder.
//   esto tiene sentido porque un usuario probablemente revise sus analisis
//   recientes con mas frecuencia que los antiguos.
//
// beneficios:
// - evita llamadas repetidas a la api de openai (ahorro de latencia y costo)
// - el usuario puede volver a ver resultados anteriores sin esperar
// - los datos se mantienen en memoria durante la sesion de la app

object AiResultCache {

    // tamano del cache: 50 entradas.
    // cada entrada es un par key-value de strings (~1-2kb por entrada),
    // por lo que 50 entradas ocupan ~100kb max en memoria, muy por debajo
    // del limite de memoria de cualquier dispositivo android moderno.
    // si quisieramos ser mas dinamicos, podriamos calcular:
    //   val maxMemory = Runtime.getRuntime().maxMemory() / 1024
    //   val cacheSize = (maxMemory / 16).toInt()
    // pero para strings de texto no es necesario
    private const val MAX_ENTRIES = 50

    private val cache: LruCache<String, String> = LruCache(MAX_ENTRIES)

    // genera la key unica para un resultado de analisis de pose
    fun poseKey(eventId: String, userId: String): String = "pose_${eventId}_${userId}"

    // genera la key unica para un resultado de analisis de track/sesion
    fun trackKey(eventId: String, userId: String): String = "track_${eventId}_${userId}"

    // guardar un resultado en el cache.
    // si el cache esta lleno (50 entradas), lrucache automaticamente
    // elimina la entrada menos recientemente accedida (lru eviction)
    fun put(key: String, value: String) {
        cache.put(key, value)
        Log.d("AiResultCache", "resultado cacheado: $key (tamano actual: ${cache.size()}/$MAX_ENTRIES)")
    }

    // buscar un resultado en el cache.
    // retorna null si no existe (cache miss) o el valor si existe (cache hit).
    // internamente, lrucache mueve el elemento accedido al frente (mru),
    // protegiendolo de ser eliminado en la proxima eviccion
    fun get(key: String): String? {
        val result = cache.get(key)
        if (result != null) {
            Log.d("AiResultCache", "cache hit: $key")
        } else {
            Log.d("AiResultCache", "cache miss: $key")
        }
        return result
    }

    // estadisticas del cache para debugging y sustentacion.
    // hitcount: cuantas veces se encontro el dato en cache (ahorro de api calls).
    // misscount: cuantas veces no se encontro (se tuvo que llamar a la api).
    // evictioncount: cuantas entradas fueron eliminadas por la politica lru
    fun stats(): String {
        return "hits=${cache.hitCount()}, misses=${cache.missCount()}, " +
                "evictions=${cache.evictionCount()}, size=${cache.size()}/$MAX_ENTRIES"
    }

    // limpiar todo el cache (ej: al cerrar sesion)
    fun clear() {
        cache.evictAll()
        Log.d("AiResultCache", "cache limpiado")
    }
}
