package com.vaultex.core.session

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Photo de profil : copiée dans le stockage interne de l'app. Persiste tant
 * que l'utilisateur ne la supprime pas explicitement.
 */
object ProfilePhotoStore {

    private const val FILE_NAME = "profile_photo.jpg"

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun exists(context: Context): Boolean = file(context).exists()

    /** Copie l'image choisie dans le stockage interne. */
    fun save(context: Context, uri: Uri): Boolean = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            file(context).outputStream().use { output -> input.copyTo(output) }
        }
        true
    } catch (_: Exception) {
        false
    }

    fun delete(context: Context) {
        try { file(context).delete() } catch (_: Exception) { /* ignore */ }
    }
}
