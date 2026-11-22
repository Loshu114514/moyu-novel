package com.moyu.novel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream
import kotlin.math.max

object AttachTools {

    private const val MAX_TEXT_CHARS = 120_000

    fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                val value = cursor.getString(index)
                if (!value.isNullOrBlank()) return value
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "附件"
    }

    fun attachmentFromUri(context: Context, uri: Uri): Attachment {
        val name = displayName(context, uri)
        val mime = context.contentResolver.getType(uri).orEmpty()
        val lower = name.lowercase()
        val isImage = mime.startsWith("image/") ||
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
            lower.endsWith(".png") || lower.endsWith(".webp")
        if (isImage) {
            val file = copyToCache(context, uri, name, "images")
            val type = when {
                lower.endsWith(".png") -> "image/png"
                lower.endsWith(".webp") -> "image/webp"
                else -> "image/jpeg"
            }
            return Attachment(name = name, kind = AttachKind.IMAGE, path = file.absolutePath, mime = type)
        }
        val text = readDocumentText(context, uri, name)
            ?: throw IOException("这个文件暂时读不了，换 txt / md / pdf / docx 试试")
        return Attachment(
            name = name,
            kind = AttachKind.TEXT,
            text = text.take(MAX_TEXT_CHARS),
            mime = mime.ifBlank { "text/plain" },
        )
    }

    fun cameraAttachment(context: Context, file: File): Attachment = Attachment(
        name = "拍照_" + System.currentTimeMillis() + ".jpg",
        kind = AttachKind.IMAGE,
        path = file.absolutePath,
        mime = "image/jpeg",
    )

    fun newCameraFile(context: Context): File {
        val dir = File(context.cacheDir, "images").apply { mkdirs() }
        return File(dir, "cam_${System.currentTimeMillis()}.jpg")
    }

    fun uriForFile(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)

    fun imageDataUrl(path: String, maxDim: Int = 1600): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bitmap = BitmapFactory.decodeFile(path, options) ?: return null

        val orientation = runCatching {
            ExifInterface(path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        }
        if (!matrix.isIdentity) {
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

        val longest = max(bitmap.width, bitmap.height)
        if (longest > maxDim) {
            val scale = maxDim.toFloat() / longest.toFloat()
            bitmap = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        }
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 86, stream)
        bitmap.recycle()
        val encoded = android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)
        return "data:image/jpeg;base64,$encoded"
    }

    fun readDocumentText(context: Context, uri: Uri, name: String): String? {
        val lower = name.lowercase()
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                when {
                    lower.endsWith(".pdf") -> readPdf(context, input)
                    lower.endsWith(".docx") -> readDocx(input)
                    lower.endsWith(".doc") -> null
                    else -> {
                        val bytes = input.readBytes()
                        if (!looksTextual(bytes)) return@use null
                        decodeText(bytes)
                    }
                }
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun readPdf(context: Context, input: java.io.InputStream): String {
        PDFBoxResourceLoader.init(context)
        PDDocument.load(input).use { document ->
            val total = document.numberOfPages
            val stripper = PDFTextStripper()
            stripper.startPage = 1
            stripper.endPage = if (total > 60) 60 else total
            return stripper.getText(document).take(MAX_TEXT_CHARS)
        }
    }

    private fun readDocx(input: java.io.InputStream): String {
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val bytes = zip.readBytes()
                    var xml = String(bytes, Charsets.UTF_8)
                    xml = xml.replace("</w:p>", "\n")
                        .replace("<w:tab/>", "\t")
                        .replace("<w:br/>", "\n")
                    xml = xml.replace(Regex("<[^>]+>"), "")
                    return unescapeXml(xml).take(MAX_TEXT_CHARS)
                }
                entry = zip.nextEntry
            }
        }
        return ""
    }

    private fun looksTextual(bytes: ByteArray): Boolean {
        val sample = minOf(bytes.size, 512)
        for (i in 0 until sample) {
            if (bytes[i] == 0.toByte()) return false
        }
        return true
    }

    private fun decodeText(bytes: ByteArray): String {
        var text = String(bytes, Charsets.UTF_8)
        if (text.startsWith("\uFEFF")) text = text.removePrefix("\uFEFF")
        return text
    }

    private fun unescapeXml(text: String): String {
        var value = text
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
        value = value.replace(Regex("&#(\\d+);")) { match ->
            runCatching { String(Character.toChars(match.groupValues[1].toInt())) }.getOrDefault("")
        }
        return value.replace("&amp;", "&")
    }

    private fun copyToCache(context: Context, uri: Uri, name: String, folder: String): File {
        val dir = File(context.cacheDir, folder).apply { mkdirs() }
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val file = File(dir, System.currentTimeMillis().toString() + "_" + safe)
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IOException("无法读取文件")
        return file
    }
}
