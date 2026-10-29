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

/*
═══════════════════════════════════════════════════════════════════════════
« ÉCHEC » ET « PAS PAR CE CHEMIN » NE SONT PAS LA MÊME CHOSE
═══════════════════════════════════════════════════════════════════════════

Cette fonction rendait un booléen, et c'est ce qui cachait le défaut.

MESURÉ EN RELISANT LE CODE : sur Android 8 et 9, écrire dans
`MediaStore.Images` réclame WRITE_EXTERNAL_STORAGE — le stockage cloisonné
n'arrive qu'avec Android 10. Cet APK ne déclare aucune permission de
stockage. L'insertion lève donc, le `catch` rendait `false`, et l'écran
affichait « Échec de l'enregistrement ».

Rien ne le distinguait d'une carte SD pleine. Or ce n'était pas un échec :
c'était un chemin inexistant sur cette version d'Android, et il en existe
un autre.

ET CE N'EST PAS UN CAS MARGINAL ICI. `minSdk` vaut 26, et le commentaire
qui l'explique dit pourquoi : « une grande part du parc visé en Afrique de
l'Ouest ». La fonctionnalité était donc cassée pour une part de ceux qu'on
vise, et silencieusement.

Trois issues, parce qu'il y a trois situations et que l'appelant doit agir
différemment dans chacune.
═══════════════════════════════════════════════════════════════════════════
*/
internal enum class IssueQr {
    /** Dans la galerie, visible dans Photos. */
    ENREGISTRE,

    /**
     * La galerie n'est pas atteignable sur cette version d'Android.
     *
     * CE N'EST PAS UNE ERREUR, et l'appelant ne doit rien annoncer : il
     * doit proposer l'autre chemin — le sélecteur du système, qui ne
     * demande aucune permission et existe depuis l'API 19.
     */
    GALERIE_INDISPONIBLE,

    /** Un vrai échec : plus de place, volume retiré, quota refusé. */
    ECHEC
}

/**
 * Enregistre le QR dans la galerie, sous Pictures/VaultEx.
 *
 * ON NE DEMANDE PAS LA PERMISSION MANQUANTE sur Android 8 et 9, et c'est
 * un choix. `WRITE_EXTERNAL_STORAGE` avec `maxSdkVersion="28"` serait le
 * motif officiel, et il marcherait. Mais un portefeuille de cryptomonnaies
 * qui réclame l'accès aux photos pour enregistrer une image a tout l'air
 * de ce qu'il ne faut pas installer — et la permission resterait visible
 * sur la fiche du magasin.
 *
 * Le sélecteur du système fait la même chose sans rien demander, et laisse
 * en plus choisir l'endroit. Une boîte de dialogue de permission en moins
 * valait mieux qu'un dossier impose.
 */
internal fun saveQrToGallery(context: Context, bitmap: Bitmap, displayName: String): IssueQr {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return IssueQr.GALERIE_INDISPONIBLE
    return try {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/VaultEx")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return IssueQr.ECHEC
        val ecrit = resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        } ?: false
        if (ecrit) IssueQr.ENREGISTRE else IssueQr.ECHEC
    } catch (_: Exception) {
        IssueQr.ECHEC
    }
}

/**
 * Le QR en octets PNG, pour l'écrire où l'appelant voudra.
 *
 * Sert au repli par le sélecteur du système : celui-ci rend une URI, et il
 * faut alors des octets à y verser. Null si la compression échoue — un
 * sélecteur ouvert pour rien serait pire que l'absence du bouton.
 */
internal fun qrEnPng(bitmap: Bitmap): ByteArray? = try {
    java.io.ByteArrayOutputStream().use { flux ->
        if (bitmap.compress(Bitmap.CompressFormat.PNG, 100, flux)) flux.toByteArray() else null
    }
} catch (_: Exception) {
    null
}
