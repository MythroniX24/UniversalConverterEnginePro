package com.universalconverter.pro.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PdfConverter {

    // ─── Image(s) → PDF ───────────────────────────────────────────────────────
    suspend fun imagesToPdf(
        context: Context,
        imageUris: List<Uri>,
        outputName: String,
        @Suppress("UNUSED_PARAMETER") quality: Int = 85,
        onProgress: (Int, String) -> Unit
    ): String? = withContext(Dispatchers.IO) {
        try {
            onProgress(5, "Preparing PDF…")
            val outputDir = getOutputDir(context)
            val outFile   = File(outputDir, "${outputName}_${System.currentTimeMillis()}.pdf")
            val pdfDoc    = PdfDocument()

            imageUris.forEachIndexed { index, uri ->
                val pct = 10 + (index * 80 / imageUris.size)
                onProgress(pct, "Adding page ${index + 1} of ${imageUris.size}…")

                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: return@forEachIndexed
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                bitmap ?: return@forEachIndexed

                val pageWidth  = bitmap.width
                val pageHeight = bitmap.height
                val pageInfo   = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                val page       = pdfDoc.startPage(pageInfo)
                page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                pdfDoc.finishPage(page)
                bitmap.recycle()
            }

            onProgress(95, "Writing PDF…")
            FileOutputStream(outFile).use { pdfDoc.writeTo(it) }
            pdfDoc.close()
            onProgress(100, "PDF created!")
            outFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    // ─── PDF → Images ─────────────────────────────────────────────────────────
    suspend fun pdfToImages(
        context: Context,
        pdfUri: Uri,
        outputFormat: String = "jpg",
        quality: Int = 85,
        onProgress: (Int, String) -> Unit
    ): List<String> = withContext(Dispatchers.IO) {
        val results = mutableListOf<String>()
        try {
            val pfd = context.contentResolver.openFileDescriptor(pdfUri, "r")
                ?: return@withContext results
            val renderer  = PdfRenderer(pfd)
            val pageCount = renderer.pageCount
            val outputDir = getOutputDir(context)

            for (i in 0 until pageCount) {
                onProgress(
                    (i * 100 / pageCount),
                    "Rendering page ${i + 1} of $pageCount…"
                )
                val page   = renderer.openPage(i)
                // ✅ Save dimensions BEFORE close
                val pageWidth  = page.width * 2
                val pageHeight = page.height * 2
                val bitmap = Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close() // ✅ close AFTER all page.xxx accesses

                val outFile = File(
                    outputDir,
                    "page_${i + 1}_${System.currentTimeMillis()}.$outputFormat"
                )
                val format = if (outputFormat == "png") Bitmap.CompressFormat.PNG
                             else Bitmap.CompressFormat.JPEG
                FileOutputStream(outFile).use { fos ->
                    bitmap.compress(format, quality, fos)
                }
                bitmap.recycle()
                results.add(outFile.absolutePath)
            }

            renderer.close()
            pfd.close()
            onProgress(100, "Done! ${results.size} pages exported")
        } catch (e: Exception) {
            // Return partial results
        }
        results
    }

    // ─── PDF Compression ──────────────────────────────────────────────────────
    suspend fun compressPdf(
        context: Context,
        pdfUri: Uri,
        @Suppress("UNUSED_PARAMETER") quality: Int = 70,
        onProgress: (Int, String) -> Unit
    ): String? = withContext(Dispatchers.IO) {
        try {
            onProgress(10, "Opening PDF…")
            val pfd = context.contentResolver.openFileDescriptor(pdfUri, "r")
                ?: return@withContext null
            val renderer  = PdfRenderer(pfd)
            val pageCount = renderer.pageCount
            val pdfDoc    = PdfDocument()
            val outputDir = getOutputDir(context)

            for (i in 0 until pageCount) {
                val pct = 10 + (i * 80 / pageCount)
                onProgress(pct, "Compressing page ${i + 1}/$pageCount…")

                val page      = renderer.openPage(i)
                // ✅ Save dimensions BEFORE close
                val pageWidth  = page.width
                val pageHeight = page.height
                val bitmap = Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.RGB_565)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close() // ✅ close AFTER all page.xxx accesses

                // Use saved dimensions (not page.width after close)
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, i + 1).create()
                val pdfPage  = pdfDoc.startPage(pageInfo)
                val paint    = Paint().apply { isFilterBitmap = true }
                pdfPage.canvas.drawBitmap(bitmap, 0f, 0f, paint)
                pdfDoc.finishPage(pdfPage)
                bitmap.recycle()
            }

            renderer.close()
            pfd.close()

            onProgress(95, "Saving compressed PDF…")
            val outFile = File(outputDir, "compressed_${System.currentTimeMillis()}.pdf")
            FileOutputStream(outFile).use { pdfDoc.writeTo(it) }
            pdfDoc.close()
            onProgress(100, "Compression complete!")
            outFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    // ─── PDF Merge ────────────────────────────────────────────────────────────
    suspend fun mergePdfs(
        context: Context,
        pdfUris: List<Uri>,
        onProgress: (Int, String) -> Unit
    ): String? = withContext(Dispatchers.IO) {
        try {
            onProgress(5, "Merging ${pdfUris.size} PDFs…")
            val pdfDoc    = PdfDocument()
            val outputDir = getOutputDir(context)
            var pageNum   = 1

            pdfUris.forEachIndexed { docIdx, uri ->
                val pct = 5 + (docIdx * 85 / pdfUris.size)
                onProgress(pct, "Adding PDF ${docIdx + 1} of ${pdfUris.size}…")

                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    ?: return@forEachIndexed
                val renderer = PdfRenderer(pfd)

                for (i in 0 until renderer.pageCount) {
                    val page   = renderer.openPage(i)
                    // ✅ Save dimensions BEFORE close
                    val pageWidth  = page.width
                    val pageHeight = page.height
                    val bitmap = Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    page.close() // ✅ close AFTER all page.xxx accesses

                    // Use saved dimensions
                    val pageInfo = PdfDocument.PageInfo.Builder(
                        pageWidth, pageHeight, pageNum++
                    ).create()
                    val pdfPage = pdfDoc.startPage(pageInfo)
                    pdfPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    pdfDoc.finishPage(pdfPage)
                    bitmap.recycle()
                }
                renderer.close()
                pfd.close()
            }

            onProgress(95, "Saving merged PDF…")
            val outFile = File(outputDir, "merged_${System.currentTimeMillis()}.pdf")
            FileOutputStream(outFile).use { pdfDoc.writeTo(it) }
            pdfDoc.close()
            onProgress(100, "Merge complete! $pageNum pages total")
            outFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun getOutputDir(context: Context): File {
        val dir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            "UCEngine"
        )
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}
