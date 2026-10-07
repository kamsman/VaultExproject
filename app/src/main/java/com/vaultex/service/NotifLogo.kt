package com.vaultex.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.vaultex.R
import com.vaultex.ui.components.CryptoIcon
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Charge le logo (grande icône) d'une notification :
 *  - logo de la crypto reçue (ex. USDT) via CryptoIcon, téléchargé en bloquant ;
 *  - repli sur le logo VaultEx si le symbole est absent ou le réseau indisponible.
 *
 * À appeler depuis un thread d'arrière-plan (worker / coroutine IO).
 */
object NotifLogo {

    private val client = OkHttpClient.Builder()
        .callTimeout(6, TimeUnit.SECONDS)
        .build()

    /** Logos déjà obtenus. Sans ce cache, CHAQUE notification relançait un
     *  téléchargement de 6 s au pire — au moment précis où l'utilisateur
     *  attend de voir sa transaction. */
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Bitmap>()

    /**
     * À N'APPELER QUE depuis un thread de fond : effectue un appel réseau.
     *
     * Renvoie null si aucune image n'a pu être obtenue. La notification
     * s'affiche alors sans grande icône — ce qui est très préférable à ce
     * qui se passait avant, voir [logoApplication].
     */
    fun forSymbol(context: Context, symbol: String?): Bitmap? {
        if (symbol.isNullOrBlank()) return logoApplication(context)
        cache[symbol]?.let { return it }
        return try {
            val req = Request.Builder().url(CryptoIcon.url(symbol)).build()
            client.newCall(req).execute().use { resp ->
                val bytes = resp.body?.bytes()
                if (resp.isSuccessful && bytes != null) {
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) { cache[symbol] = bitmap; bitmap }
                    else logoApplication(context)
                } else logoApplication(context)
            }
        } catch (_: Exception) {
            logoApplication(context)
        }
    }

    /**
     * Image LIBRE d'une annonce, désignée par son adresse.
     *
     * Sert de grande image de bandeau (BigPictureStyle), pas de logo. Elle
     * est facultative de bout en bout : une adresse injoignable, un format
     * illisible ou un réseau absent renvoient null, et la notification
     * s'affiche alors sans image. Une annonce ne doit jamais être perdue
     * parce qu'une illustration n'a pas pu être chargée.
     *
     * L'adresse vient d'un message de notre propre canal d'administration,
     * jamais d'un tiers. Elle n'est donc pas une entrée hostile — mais le
     * délai d'appel reste court et le résultat mis en cache, pour ne pas
     * retenir un service que le système peut tuer à tout instant.
     *
     * À N'APPELER QUE depuis un thread de fond.
     */
    fun forUrl(url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        cache[url]?.let { return it }
        return try {
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                val bytes = resp.body?.bytes()
                if (resp.isSuccessful && bytes != null) {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        ?.also { cache[url] = it }
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Logo de l'application, en image.
     *
     * ═══════════════════════════════════════════════════════════════════════
     * POURQUOI CE N'EST PAS UN SIMPLE decodeResource
     * ═══════════════════════════════════════════════════════════════════════
     *
     * Cette fonction se réduisait à :
     *
     *     BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
     *
     * Or `ic_launcher` est une icône ADAPTATIVE : depuis Android 8, elle se
     * résout vers `mipmap-anydpi-v26/ic_launcher.xml`, un fichier XML décrivant
     * un avant-plan et un arrière-plan. `decodeResource` ne sait décoder que des
     * images — sur un XML, elle renvoie null.
     *
     * Le type de retour déclarait pourtant un Bitmap non-nullable. Kotlin
     * insérait donc un contrôle, et ce contrôle levait une
     * NullPointerException SANS MESSAGE, avalée par le rattrapage de
     * NotificationHub. Aucune notification ne s'affichait, et rien ne disait
     * pourquoi.
     *
     * La portée dépassait largement les annonces : ce logo sert aussi de repli
     * quand le téléchargement de l'icône d'une monnaie échoue. Une notification
     * de dépôt reçue hors couverture réseau ne s'affichait donc pas non plus.
     *
     * On dessine désormais le drawable — adaptatif ou non — sur une image, et
     * l'échec renvoie null plutôt que de faire échouer toute la notification.
     * Une bannière sans grande icône vaut infiniment mieux qu'aucune bannière.
     * ═══════════════════════════════════════════════════════════════════════
     */
    private fun logoApplication(context: Context): Bitmap? {
        // Appareils antérieurs à Android 8, ou icône restée en image.
        BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)?.let { return it }
        return try {
            val drawable = androidx.core.content.ContextCompat
                .getDrawable(context, R.mipmap.ic_launcher) ?: return null
            // Une icône adaptative annonce une taille intrinsèque ; le repli à
            // 108 dp est la dimension standard, au cas où elle serait absente.
            val largeur = drawable.intrinsicWidth.takeIf { it > 0 } ?: 108
            val hauteur = drawable.intrinsicHeight.takeIf { it > 0 } ?: 108
            val image = Bitmap.createBitmap(largeur, hauteur, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, largeur, hauteur)
            drawable.draw(android.graphics.Canvas(image))
            image
        } catch (_: Exception) {
            null
        }
    }
}
