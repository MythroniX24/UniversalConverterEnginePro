package com.universalconverter.pro.engine.privacy

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PrivacyScanner {
    data class ScanResult(
        val risks: List<PrivacyRisk>, val riskLevel: RiskLevel,
        val summary: String
    )
    data class PrivacyRisk(val category: String, val description: String, val severity: Int)
    enum class RiskLevel { SAFE, LOW, MEDIUM, HIGH }

    suspend fun scan(context: Context, uri: Uri): ScanResult = withContext(Dispatchers.IO) {
        val risks = mutableListOf<PrivacyRisk>()
        try {
            val tmpFile = File(context.cacheDir, "pscan_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { tmpFile.outputStream().use { o -> it.copyTo(o) } }
            val exif = ExifInterface(tmpFile.absolutePath)

            if (exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE) != null)
                risks.add(PrivacyRisk("📍 GPS Location", "Exact coordinates embedded in image", 3))
            if (exif.getAttribute(ExifInterface.TAG_MAKE) != null)
                risks.add(PrivacyRisk("📷 Device Info", "Camera make/model: ${exif.getAttribute(ExifInterface.TAG_MAKE)} ${exif.getAttribute(ExifInterface.TAG_MODEL)}", 2))
            if (exif.getAttribute(ExifInterface.TAG_DATETIME) != null)
                risks.add(PrivacyRisk("🕐 Timestamp", "Photo taken: ${exif.getAttribute(ExifInterface.TAG_DATETIME)}", 1))
            if (exif.getAttribute(ExifInterface.TAG_SOFTWARE) != null)
                risks.add(PrivacyRisk("💻 Software", "Edited with: ${exif.getAttribute(ExifInterface.TAG_SOFTWARE)}", 1))
            if (exif.getAttribute(ExifInterface.TAG_ARTIST) != null)
                risks.add(PrivacyRisk("👤 Author", "Author name embedded", 2))
            tmpFile.delete()
        } catch (_: Exception) {}

        val maxSeverity = risks.maxOfOrNull { it.severity } ?: 0
        val level = when {
            risks.isEmpty() -> RiskLevel.SAFE
            maxSeverity >= 3 -> RiskLevel.HIGH
            maxSeverity == 2 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
        val summary = when(level) {
            RiskLevel.SAFE -> "✅ No sensitive data detected"
            RiskLevel.LOW  -> "⚠️ Minor data found (${risks.size} item${if(risks.size>1) "s" else ""})"
            RiskLevel.MEDIUM -> "🟡 Sensitive data found — review before sharing"
            RiskLevel.HIGH   -> "🔴 HIGH RISK — GPS location and device info present!"
        }
        ScanResult(risks, level, summary)
    }
}
