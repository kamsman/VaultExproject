package com.vaultex.ui.screens.receive

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** QR standard (correction d'erreur par défaut). */
internal fun generateQr(content: String, size: Int): Bitmap? = generateQr(content, size, false)

/**
 * Génère un QR code. [highEcc] = niveau de correction H (30%) pour supporter
 * un logo au centre sans casser la lecture.
 */
internal fun generateQr(content: String, size: Int, highEcc: Boolean): Bitmap? {
    if (content.isEmpty()) return null
    return try {
        val hints: Map<EncodeHintType, Any> = if (highEcc)
            mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H)
        else emptyMap()
        val matrix: BitMatrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) for (y in 0 until size)
            bmp.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        bmp
    } catch (_: Exception) { null }
}

/**
 * Enregistre le QR dans la galerie (MediaStore — aucune permission requise sur
 * Android 10+). Retourne vrai si l'enregistrement a réussi.
 */
internal fun saveQrToGallery(context: Context, bitmap: Bitmap, displayName: String): Boolean {
    return try {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/VaultEx")
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        } ?: return false
        true
    } catch (_: Exception) { false }
}
