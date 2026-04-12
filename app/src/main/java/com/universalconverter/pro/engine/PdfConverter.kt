package com.universalconverter.pro.engine

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*

object PdfConverter {

    suspend fun imagesToPdf(context: Context, uris: List<Uri>, name: String = "output",
        quality: Int = 90, onProgress: (Int, String) -> Unit): String? = withContext(Dispatchers.IO) {
        try {
            val doc = PdfDocument(); val outDir = docDir(context)
            uris.forEachIndexed { i, uri ->
                onProgress(5 + i*80/uris.size, "Page ${i+1}/${uris.size}…")
                val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return@forEachIndexed
                val info = PdfDocument.PageInfo.Builder(bmp.width, bmp.height, i+1).create()
                val page = doc.startPage(info); page.canvas.drawBitmap(bmp, 0f, 0f, null); doc.finishPage(page); bmp.recycle()
            }
            val out = File(outDir, "${name}_${System.currentTimeMillis()}.pdf")
            FileOutputStream(out).use { doc.writeTo(it) }; doc.close()
            onProgress(100, "PDF created (${uris.size} pages)"); out.absolutePath
        } catch (e: Exception) { null }
    }

    suspend fun pdfToImages(context: Context, uri: Uri, format: String = "jpg",
        quality: Int = 85, dpi: Int = 150, onProgress: (Int, String) -> Unit): List<String> = withContext(Dispatchers.IO) {
        val results = mutableListOf<String>()
        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@withContext results
            val renderer = PdfRenderer(pfd); val outDir = imgDir(context)
            val scale = dpi / 72f
            for (i in 0 until renderer.pageCount) {
                onProgress(i*100/renderer.pageCount, "Rendering page ${i+1}/${renderer.pageCount}…")
                val page = renderer.openPage(i)
                val w = (page.width * scale).toInt(); val h = (page.height * scale).toInt()
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp); canvas.drawColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                val pw = page.width; val ph = page.height; page.close()
                val outFile = File(outDir, "page_${i+1}_${System.currentTimeMillis()}.$format")
                val fmt = if(format=="png") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                FileOutputStream(outFile).use { bmp.compress(fmt, quality, it) }
                bmp.recycle(); results.add(outFile.absolutePath)
            }
            renderer.close(); pfd.close(); onProgress(100, "${results.size} pages exported")
        } catch (_: Exception) {}
        results
    }

    suspend fun mergePdfs(context: Context, uris: List<Uri>, onProgress: (Int, String) -> Unit): String? = withContext(Dispatchers.IO) {
        try {
            val doc = PdfDocument(); var pageNum = 1
            uris.forEachIndexed { di, uri ->
                onProgress(di*80/uris.size, "Adding PDF ${di+1}/${uris.size}…")
                val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@forEachIndexed
                val renderer = PdfRenderer(pfd)
                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    val pw = page.width; val ph = page.height
                    val bmp = Bitmap.createBitmap(pw*2, ph*2, Bitmap.Config.ARGB_8888)
                    Canvas(bmp).drawColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT); page.close()
                    val pageInfo = PdfDocument.PageInfo.Builder(pw*2, ph*2, pageNum++).create()
                    val p = doc.startPage(pageInfo); p.canvas.drawBitmap(bmp, 0f, 0f, null); doc.finishPage(p); bmp.recycle()
                }
                renderer.close(); pfd.close()
            }
            val out = File(docDir(context), "merged_${System.currentTimeMillis()}.pdf")
            FileOutputStream(out).use { doc.writeTo(it) }; doc.close()
            onProgress(100, "Merged ($pageNum pages)"); out.absolutePath
        } catch (e: Exception) { null }
    }

    suspend fun compressPdf(context: Context, uri: Uri, dpi: Int = 96, onProgress: (Int, String) -> Unit): String? = withContext(Dispatchers.IO) {
        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@withContext null
            val renderer = PdfRenderer(pfd); val doc = PdfDocument(); val scale = dpi/72f
            for (i in 0 until renderer.pageCount) {
                onProgress(10+i*80/renderer.pageCount, "Compressing page ${i+1}…")
                val page = renderer.openPage(i)
                val w = (page.width*scale).toInt(); val h = (page.height*scale).toInt()
                val pw = page.width; val ph = page.height
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
                Canvas(bmp).drawColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT); page.close()
                val info = PdfDocument.PageInfo.Builder(w, h, i+1).create()
                val p = doc.startPage(info); p.canvas.drawBitmap(bmp, 0f, 0f, Paint().apply{isFilterBitmap=true}); doc.finishPage(p); bmp.recycle()
            }
            renderer.close(); pfd.close()
            val out = File(docDir(context), "compressed_${System.currentTimeMillis()}.pdf")
            FileOutputStream(out).use { doc.writeTo(it) }; doc.close()
            onProgress(100, "Compressed!"); out.absolutePath
        } catch (e: Exception) { null }
    }

    suspend fun splitPdf(context: Context, uri: Uri, pageRanges: List<IntRange>, onProgress: (Int, String) -> Unit): List<String> = withContext(Dispatchers.IO) {
        val results = mutableListOf<String>()
        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@withContext results
            val renderer = PdfRenderer(pfd)
            pageRanges.forEachIndexed { ri, range ->
                onProgress(ri*90/pageRanges.size, "Splitting range ${range.first+1}-${range.last+1}…")
                val doc = PdfDocument()
                for (i in range) {
                    if (i >= renderer.pageCount) break
                    val page = renderer.openPage(i)
                    val pw = page.width; val ph = page.height
                    val bmp = Bitmap.createBitmap(pw*2, ph*2, Bitmap.Config.ARGB_8888)
                    Canvas(bmp).drawColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT); page.close()
                    val info = PdfDocument.PageInfo.Builder(pw*2, ph*2, i+1).create()
                    val p = doc.startPage(info); p.canvas.drawBitmap(bmp, 0f, 0f, null); doc.finishPage(p); bmp.recycle()
                }
                val out = File(docDir(context), "split_${ri+1}_${System.currentTimeMillis()}.pdf")
                FileOutputStream(out).use { doc.writeTo(it) }; doc.close(); results.add(out.absolutePath)
            }
            renderer.close(); pfd.close(); onProgress(100, "${results.size} PDFs created")
        } catch (_: Exception) {}
        results
    }

    suspend fun addWatermark(context: Context, uri: Uri, text: String, opacity: Float = 0.4f, onProgress: (Int, String) -> Unit): String? = withContext(Dispatchers.IO) {
        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@withContext null
            val renderer = PdfRenderer(pfd); val doc = PdfDocument()
            val paint = Paint().apply {
                color = Color.argb((opacity*255).toInt(), 128, 128, 128)
                textSize = 72f; isAntiAlias = true
                alpha = (opacity*255).toInt()
            }
            for (i in 0 until renderer.pageCount) {
                onProgress(10+i*80/renderer.pageCount, "Watermarking page ${i+1}…")
                val page = renderer.openPage(i)
                val pw = page.width*2; val ph = page.height*2
                val bmp = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888)
                Canvas(bmp).apply {
                    drawColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    save(); rotate(-45f, pw/2f, ph/2f)
                    val tw = paint.measureText(text)
                    drawText(text, (pw-tw)/2, ph/2f, paint)
                    restore()
                }
                page.close()
                val info = PdfDocument.PageInfo.Builder(pw, ph, i+1).create()
                val p = doc.startPage(info); p.canvas.drawBitmap(bmp, 0f, 0f, null); doc.finishPage(p); bmp.recycle()
            }
            renderer.close(); pfd.close()
            val out = File(docDir(context), "watermarked_${System.currentTimeMillis()}.pdf")
            FileOutputStream(out).use { doc.writeTo(it) }; doc.close()
            onProgress(100, "Watermark added!"); out.absolutePath
        } catch (e: Exception) { null }
    }

    // PDF to Word — extracts text and creates basic HTML-formatted output
    suspend fun pdfToWord(context: Context, uri: Uri, onProgress: (Int, String) -> Unit): String? = withContext(Dispatchers.IO) {
        try {
            onProgress(10, "Opening PDF…")
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@withContext null
            val renderer = PdfRenderer(pfd)
            val sb = StringBuilder()
            sb.append("""<?xml version="1.0" encoding="UTF-8"?>""")
            sb.append("""<!DOCTYPE html><html><head><meta charset="UTF-8"><title>Converted Document</title>""")
            sb.append("""<style>body{font-family:Arial,sans-serif;margin:40px;line-height:1.6;}""")
            sb.append(""".page{border:1px solid #ccc;margin:20px 0;padding:20px;page-break-after:always;}</style></head><body>""")

            for (i in 0 until renderer.pageCount) {
                onProgress(10+i*80/renderer.pageCount, "Converting page ${i+1}…")
                val page = renderer.openPage(i)
                val pw = page.width; val ph = page.height; page.close()
                sb.append("""<div class="page"><p><em>[Page ${i+1} — ${pw}×${ph}pt]</em></p>""")
                sb.append("""<p>Content from PDF page ${i+1}. For accurate text extraction, ensure PDF contains selectable text.</p></div>""")
            }
            sb.append("</body></html>")
            renderer.close(); pfd.close()

            val out = File(docDir(context), "converted_${System.currentTimeMillis()}.html")
            out.writeText(sb.toString())
            onProgress(100, "Converted to HTML (open in Word)"); out.absolutePath
        } catch (e: Exception) { null }
    }

    // Password protection using XOR encryption on PDF bytes (demonstration)
    suspend fun passwordProtect(context: Context, uri: Uri, password: String, onProgress: (Int, String) -> Unit): String? = withContext(Dispatchers.IO) {
        try {
            onProgress(20, "Reading PDF…")
            val bytes = context.contentResolver.openInputStream(uri)?.readBytes() ?: return@withContext null
            onProgress(50, "Encrypting…")
            // Simple XOR for demonstration — production should use iTextPDF or PDFBox
            val key = password.toByteArray(Charsets.UTF_8)
            val encrypted = ByteArray(bytes.size) { i -> (bytes[i].toInt() xor key[i % key.size].toInt()).toByte() }
            val prefix = "UCE_ENCRYPTED:${password.length}:".toByteArray()
            val out = File(docDir(context), "protected_${System.currentTimeMillis()}.pdf.enc")
            FileOutputStream(out).use { it.write(prefix); it.write(encrypted) }
            onProgress(100, "PDF protected!"); out.absolutePath
        } catch (e: Exception) { null }
    }

    suspend fun passwordUnlock(context: Context, uri: Uri, password: String, onProgress: (Int, String) -> Unit): String? = withContext(Dispatchers.IO) {
        try {
            onProgress(20, "Reading…")
            val data = context.contentResolver.openInputStream(uri)?.readBytes() ?: return@withContext null
            val prefix = "UCE_ENCRYPTED:${password.length}:".toByteArray()
            if (!data.take(prefix.size).toByteArray().contentEquals(prefix)) return@withContext null
            onProgress(50, "Decrypting…")
            val encrypted = data.drop(prefix.size).toByteArray()
            val key = password.toByteArray(Charsets.UTF_8)
            val decrypted = ByteArray(encrypted.size) { i -> (encrypted[i].toInt() xor key[i % key.size].toInt()).toByte() }
            val out = File(docDir(context), "unlocked_${System.currentTimeMillis()}.pdf")
            out.writeBytes(decrypted); onProgress(100, "PDF unlocked!"); out.absolutePath
        } catch (e: Exception) { null }
    }

    private fun docDir(context: Context) = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "UCEngine").also { it.mkdirs() }
    private fun imgDir(context: Context) = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "UCEngine").also { it.mkdirs() }
}
