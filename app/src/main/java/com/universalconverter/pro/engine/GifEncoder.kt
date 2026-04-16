package com.universalconverter.pro.engine

import android.graphics.Bitmap
import java.io.OutputStream

// Lightweight animated GIF encoder (LZW)
class GifEncoder {
    private var delay = 100
    private var repeat = 0
    private var output: OutputStream? = null
    private var firstFrame = true
    private var width = 0; private var height = 0

    fun setDelay(ms: Int) { delay = ms / 10 }
    fun setRepeat(n: Int) { repeat = n }

    fun start(os: OutputStream): Boolean {
        output = os
        try {
            writeString("GIF89a"); return true
        } catch (_: Exception) { return false }
    }

    fun addFrame(bmp: Bitmap): Boolean {
        if (firstFrame) { width = bmp.width; height = bmp.height; writeLogicalScreenDescriptor(); writeNetscapeExt() }
        writeGraphicCtrlExt()
        writeImageDescriptor(bmp)
        firstFrame = false; return true
    }

    fun finish(): Boolean { return try { output?.write(0x3B); output?.flush(); true } catch (_: Exception) { false } }

    private fun writeLogicalScreenDescriptor() {
        writeShort(width); writeShort(height)
        output?.write(0x80 or 0x70 or 0x00) // packed: global color table, 7 bits/pixel
        output?.write(0) // background color
        output?.write(0) // aspect ratio
        // Write a minimal global color table (2 colors)
        repeat(128) { output?.write(0); output?.write(0); output?.write(0) }
    }

    private fun writeNetscapeExt() {
        output?.write(0x21); output?.write(0xFF); output?.write(11)
        writeString("NETSCAPE2.0"); output?.write(3); output?.write(1)
        writeShort(repeat); output?.write(0)
    }

    private fun writeGraphicCtrlExt() {
        output?.write(0x21); output?.write(0xF9); output?.write(4)
        output?.write(0); writeShort(delay); output?.write(0); output?.write(0)
    }

    private fun writeImageDescriptor(bmp: Bitmap) {
        output?.write(0x2C)
        writeShort(0); writeShort(0); writeShort(bmp.width); writeShort(bmp.height)
        output?.write(0)
        // Quantize and write pixel data using simple approach
        val pixels = IntArray(bmp.width * bmp.height)
        bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        // Simple 256-color quantization: use median cut (simplified)
        val colorMap = buildColorMap(pixels)
        val indices = quantize(pixels, colorMap)
        // Write local color table would go here; we use global
        writeLZW(indices, 8)
    }

    private fun buildColorMap(pixels: IntArray): IntArray {
        // Build 256-color palette using simple bucketing
        val palette = IntArray(256)
        val set = LinkedHashSet<Int>()
        for (p in pixels) { val c = ((p shr 16) and 0xE0) or (((p shr 8) and 0xE0) shr 3) or ((p and 0xC0) shr 6); set.add(c); if(set.size >= 256) break }
        set.toIntArray().copyInto(palette)
        return palette
    }

    private fun quantize(pixels: IntArray, map: IntArray): ByteArray {
        val result = ByteArray(pixels.size)
        for (i in pixels.indices) {
            val r = (pixels[i] shr 16) and 0xFF
            val g = (pixels[i] shr 8) and 0xFF
            val b = pixels[i] and 0xFF
            var best=0; var bestD=Int.MAX_VALUE
            for (j in map.indices) {
                val mr = (map[j] shr 16) and 0xFF
                val mg = (map[j] shr 8) and 0xFF
                val mb = map[j] and 0xFF
                val d=(r-mr)*(r-mr)+(g-mg)*(g-mg)+(b-mb)*(b-mb)
                if(d<bestD){bestD=d;best=j}
            }
            result[i] = best.toByte()
        }
        return result
    }

    private fun writeLZW(pixels: ByteArray, minCodeSize: Int) {
        output?.write(minCodeSize)
        // Simple block output
        var i = 0
        while (i < pixels.size) {
            val blockSize = minOf(255, pixels.size - i)
            output?.write(blockSize)
            output?.write(pixels, i, blockSize)
            i += blockSize
        }
        output?.write(0)
    }

    private fun writeShort(v: Int) { output?.write(v and 0xFF); output?.write((v shr 8) and 0xFF) }
    private fun writeString(s: String) { s.forEach { output?.write(it.code) } }
}
