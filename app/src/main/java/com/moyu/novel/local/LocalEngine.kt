package com.moyu.novel.local

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Thin coroutine wrapper around llama.cpp. All calls are serialised on a dedicated
 * single-threaded dispatcher because the native side keeps one model and one context.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocalEngine(context: Context) {

    private val appContext = context.applicationContext
    private val dispatcher = Dispatchers.Default.limitedParallelism(1)
    @Suppress("unused")
    private val scope = CoroutineScope(dispatcher + SupervisorJob())
    private val mutex = Mutex()

    @Volatile
    private var libraryLoaded = false

    @Volatile
    private var initialized = false

    @Volatile
    private var loadedPath: String? = null

    @Volatile
    private var cancelRequested = false

    private external fun nativeInit(nativeLibDir: String)
    private external fun loadModel(path: String, nCtx: Int, nThreads: Int): Boolean
    private external fun isLoaded(): Boolean
    private external fun modelDesc(): String
    private external fun nativeContextSize(): Int
    private external fun countTokens(text: String): Int
    private external fun pastTokens(): Int
    private external fun newChat(systemPrompt: String): Boolean
    private external fun addUser(text: String): Boolean
    private external fun addAssistantHistory(content: String, reasoning: String): Boolean
    private external fun nativeFinishAssistant(content: String, reasoning: String)
    private external fun startGeneration(
        maxTokens: Int,
        temperature: Float,
        topK: Int,
        topP: Float,
        minP: Float,
        repeatPenalty: Float,
    ): Boolean
    private external fun nextToken(): String?
    private external fun freeModel()
    private external fun nativeShutdown()

    private fun ensureInitialized() {
        if (!libraryLoaded) {
            System.loadLibrary("moyu")
            libraryLoaded = true
        }
        if (!initialized) {
            nativeInit(appContext.applicationInfo.nativeLibraryDir)
            initialized = true
        }
    }

    fun loadedModelPath(): String? = loadedPath

    suspend fun load(path: String, contextSize: Int, threads: Int): Boolean = mutex.withLock {
        withContext(dispatcher) {
            ensureInitialized()
            if (loadedPath == path && isLoaded()) return@withContext true
            if (loadedPath != null) {
                freeModel()
                loadedPath = null
            }
            val ok = loadModel(path, contextSize.coerceIn(1024, 16384), threads.coerceIn(1, 8))
            if (ok) loadedPath = path
            ok
        }
    }

    suspend fun release() = mutex.withLock {
        withContext(dispatcher) {
            if (initialized) {
                freeModel()
                loadedPath = null
            }
        }
    }

    suspend fun startChat(systemPrompt: String): Boolean = mutex.withLock {
        withContext(dispatcher) {
            if (loadedPath == null) return@withContext false
            cancelRequested = false
            newChat(systemPrompt)
        }
    }

    suspend fun appendUser(text: String): Boolean = mutex.withLock {
        withContext(dispatcher) {
            if (loadedPath == null) return@withContext false
            addUser(text)
        }
    }

    suspend fun appendAssistantHistory(content: String, reasoning: String): Boolean = mutex.withLock {
        withContext(dispatcher) {
            if (loadedPath == null) return@withContext false
            addAssistantHistory(content, reasoning)
        }
    }

    suspend fun finishAssistant(content: String, reasoning: String) = mutex.withLock {
        withContext(dispatcher) {
            if (loadedPath != null) nativeFinishAssistant(content, reasoning)
        }
    }

    suspend fun generate(
        maxTokens: Int,
        temperature: Float,
        topK: Int = 40,
        topP: Float = 0.95f,
        minP: Float = 0.05f,
        repeatPenalty: Float = 1.08f,
        onToken: (String) -> Unit,
    ): Boolean = mutex.withLock {
        withContext(dispatcher) {
            if (loadedPath == null) return@withContext false
            cancelRequested = false
            if (!startGeneration(
                    maxTokens = maxTokens,
                    temperature = temperature,
                    topK = topK,
                    topP = topP,
                    minP = minP,
                    repeatPenalty = repeatPenalty,
                )
            ) {
                return@withContext false
            }
            while (!cancelRequested) {
                val piece = nextToken() ?: break
                if (piece.isNotEmpty()) onToken(piece)
            }
            true
        }
    }

    fun cancel() {
        cancelRequested = true
    }

    fun tokenCount(text: String): Int = runCatching { countTokens(text) }.getOrDefault(0)

    fun usedTokens(): Int = runCatching { pastTokens() }.getOrDefault(0)

    fun contextSize(): Int = runCatching { nativeContextSize() }.getOrDefault(0)

    fun description(): String = runCatching { modelDesc() }.getOrDefault("")

    fun shutdown() {
        cancelRequested = true
        if (initialized) {
            runCatching { nativeShutdown() }
            initialized = false
            loadedPath = null
        }
    }
}
