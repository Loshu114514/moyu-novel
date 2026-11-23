package com.moyu.novel

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

object OcrTools {

    suspend fun recognize(context: Context, path: String): String? = withContext(Dispatchers.IO) {
        if (path.isBlank() || !File(path).exists()) return@withContext null
        suspendCancellableCoroutine { continuation ->
            val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            try {
                val image = InputImage.fromFilePath(context, Uri.fromFile(File(path)))
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        val text = result.text.trim()
                        runCatching { recognizer.close() }
                        if (continuation.isActive) {
                            continuation.resume(text.takeIf { it.isNotBlank() }?.take(6000))
                        }
                    }
                    .addOnFailureListener {
                        runCatching { recognizer.close() }
                        if (continuation.isActive) continuation.resume(null)
                    }
            } catch (_: Exception) {
                runCatching { recognizer.close() }
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }
}
