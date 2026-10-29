package com.vaultex.ui.screens.change

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream

/*
═══════════════════════════════════════════════════════════════════════════
LE REÇU QU'ON EMPORTE
═══════════════════════════════════════════════════════════════════════════

L'écran de fin affiche tout ce qu'il faut — référence, montants, adresse —
et le perd dès qu'on en sort. Il reste l'historique des demandes, qui ne
vit que dans ce téléphone : une personne qui change d'appareil, ou qui
réinstalle, n'a plus rien. Et c'est précisément ce qu'on demande à
quelqu'un quand une opération est contestée.

UN FICHIER, LUI, SURVIT À L'APPLICATION. Il s'envoie sur WhatsApp, il
s'imprime, il se garde dans un dossier. C'est la différence entre « j'ai
vu passer un écran » et « voilà le document ».

─── POURQUOI PDF ET NON UNE IMAGE ───────────────────────────────────────

Une capture d'écran se recadre, se recompresse et se retouche ; elle n'a
le poids d'aucune pièce. Un PDF s'ouvre partout, s'imprime à l'identique,
et `android.graphics.pdf.PdfDocument` est dans le cadre d'Android depuis
l'API 19 — aucune dépendance à ajouter pour un APK qu'on veut léger.

─── CE FICHIER NE SAIT RIEN DE COMPOSE, ET C'EST VOULU ──────────────────

Il reçoit des couples « libellé, valeur » DÉJÀ TRADUITS. Le document suit
donc la langue de l'application sans que le dessin n'ait à connaître
`stringResource` — et il se teste sans écran.

─── CE QUE CE DOCUMENT N'EST PAS ────────────────────────────────────────

Une facture fiscale. Il n'y a ni IFU, ni RCCM, ni TVA : ce sont des
mentions qu'une entreprise déclarée doit porter, et les inventer serait
plus grave que de les omettre. Le document s'intitule donc « reçu », et
une mention en bas de page le dit en clair.

Le jour où VaultEx facture pour de vrai, ce fichier est le bon endroit —
mais il faudra les vraies mentions, pas un titre changé.
═══════════════════════════════════════════════════════════════════════════
*/

/**
 * Tout ce que le document porte, déjà mis en forme et déjà traduit.
 *
 * @param vedette la ligne mise en avant — le montant qui compte pour celui
 *   qui lit. Rendue plus grande et en gras, parce qu'un reçu se lit en une
 *   seconde avant de se lire en entier.
 * @param lignes le reste, dans l'ordre d'affichage. Une valeur vide est
 *   écartée par [construireFacturePdf] : il vaut mieux une ligne de moins
 *   qu'un libellé suivi du vide.
 * @param mentions le bas de page. Plusieurs lignes, chacune repliée à la
 *   largeur utile.
 */
data class DonneesFacture(
    val titre: String,
    val sousTitre: String,
    val vedette: Pair<String, String>?,
    val lignes: List<Pair<String, String>>,
    val mentions: List<String>
)

/*
A4 EN POINTS PostScript, l'unité de PdfDocument : 72 par pouce, donc
595 x 842. Les prendre en pixels d'écran donnerait un document dont la
taille dépend du téléphone qui l'a produit.
*/
private const val LARGEUR_PAGE = 595
private const val HAUTEUR_PAGE = 842
private const val MARGE = 48f

/**
 * Dessine le reçu et rend les octets du PDF, ou null si ça échoue.
 *
 * NULL PLUTÔT QU'UNE EXCEPTION : l'appelant est un bouton. Une exception
 * qui remonte jusqu'à Compose ferait apparaître « l'application s'est
 * arrêtée » sur un écran qui annonce une opération réussie — le pire
 * moment du parcours pour un plantage.
 */
fun construireFacturePdf(d: DonneesFacture): ByteArray? {
    val document = PdfDocument()
    return try {
        val page = document.startPage(
            PdfDocument.PageInfo.Builder(LARGEUR_PAGE, HAUTEUR_PAGE, 1).create()
        )
        dessiner(page.canvas, d)
        document.finishPage(page)
        ByteArrayOutputStream().use { flux ->
            document.writeTo(flux)
            flux.toByteArray()
        }
    } catch (_: Exception) {
        null
    } finally {
        /*
        `close()` DANS UN `finally`, ET PAS APRÈS LE SUCCÈS.

        PdfDocument tient des pages natives. Une exception au milieu du
        dessin les laisserait ouvertes, et l'avertissement qui en sort
        n'apparaît que dans les journaux — donc jamais.
        */
        document.close()
    }
}

private fun dessiner(canvas: Canvas, d: DonneesFacture) {
    val noir = Paint().apply { color = Color.BLACK; isAntiAlias = true }
    val gris = Paint().apply { color = Color.rgb(110, 110, 110); isAntiAlias = true }
    val trait = Paint().apply { color = Color.rgb(220, 220, 220); strokeWidth = 1f }

    val sansGras = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    val sansMaigre = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

    var y = MARGE + 24f
    val droite = LARGEUR_PAGE - MARGE

    // ─── L'en-tête ──────────────────────────────────────────────────
    noir.typeface = sansMaigre
    noir.textSize = 26f
    canvas.drawText("VaultEx", MARGE, y, noir)

    gris.typeface = sansGras
    gris.textSize = 11f
    gris.textAlign = Paint.Align.RIGHT
    canvas.drawText(d.sousTitre, droite, y, gris)
    gris.textAlign = Paint.Align.LEFT

    y += 26f
    noir.typeface = sansGras
    noir.textSize = 13f
    canvas.drawText(d.titre, MARGE, y, noir)

    y += 18f
    canvas.drawLine(MARGE, y, droite, y, trait)
    y += 34f

    // ─── Le montant qui compte ──────────────────────────────────────
    d.vedette?.let { (libelle, valeur) ->
        gris.textSize = 10f
        canvas.drawText(libelle.uppercase(), MARGE, y, gris)
        y += 26f
        noir.typeface = sansMaigre
        noir.textSize = 22f
        canvas.drawText(valeur, MARGE, y, noir)
        y += 26f
        canvas.drawLine(MARGE, y, droite, y, trait)
        y += 30f
    }

    // ─── Le détail ──────────────────────────────────────────────────
    for ((libelle, valeur) in d.lignes) {
        if (valeur.isBlank()) continue
        gris.typeface = sansGras
        gris.textSize = 11f
        canvas.drawText(libelle, MARGE, y, gris)

        noir.typeface = sansGras
        noir.textSize = 11f
        noir.textAlign = Paint.Align.RIGHT
        /*
        LA VALEUR EST TRONQUÉE SI ELLE DÉBORDE, et une adresse de
        quarante-deux caractères déborde. On garde le début et la fin —
        c'est la forme sous laquelle on reconnaît une adresse — plutôt
        que de laisser le texte passer sous le libellé.
        */
        canvas.drawText(abrege(valeur, noir, droite - MARGE - mesure(libelle, gris) - 16f), droite, y, noir)
        noir.textAlign = Paint.Align.LEFT
        y += 22f
    }

    // ─── Le bas de page ─────────────────────────────────────────────
    var bas = HAUTEUR_PAGE - MARGE
    gris.typeface = sansGras
    gris.textSize = 9f
    for (ligne in d.mentions.asReversed()) {
        for (morceau in replier(ligne, gris, droite - MARGE).asReversed()) {
            canvas.drawText(morceau, MARGE, bas, gris)
            bas -= 13f
        }
        bas -= 5f
    }
}

private fun mesure(texte: String, paint: Paint): Float = paint.measureText(texte)

/**
 * Raccourcit au milieu pour tenir dans [place].
 *
 * `TextUtils.ellipsize` existe, et coupe À LA FIN : une adresse y perdrait
 * exactement la partie qui sert à la reconnaître. On garde les deux bouts.
 */
private fun abrege(texte: String, paint: Paint, place: Float): String {
    if (place <= 0f || paint.measureText(texte) <= place) return texte
    var garde = texte.length / 2
    while (garde > 4) {
        val essai = texte.take(garde) + "…" + texte.takeLast(garde)
        if (paint.measureText(essai) <= place) return essai
        garde--
    }
    return texte.take(6) + "…" + texte.takeLast(6)
}

/**
 * Replie un paragraphe à la largeur utile, sans couper de mot.
 *
 * [largeur] est la place DISPONIBLE, pas la position du bord droit : la
 * marge en est déjà retirée par l'appelant. Elle l'était une seconde fois
 * ici, ce qui repliait les mentions quarante-huit points trop tôt — assez
 * pour ajouter une ligne au bas de page sans que rien n'ait l'air faux.
 */
private fun replier(texte: String, paint: Paint, largeur: Float): List<String> {
    val mots = texte.split(' ').filter { it.isNotEmpty() }
    if (mots.isEmpty()) return emptyList()
    val lignes = mutableListOf<String>()
    var courante = StringBuilder()
    for (mot in mots) {
        val essai = if (courante.isEmpty()) mot else "$courante $mot"
        if (paint.measureText(essai) <= largeur) {
            courante = StringBuilder(essai)
        } else {
            if (courante.isNotEmpty()) lignes.add(courante.toString())
            courante = StringBuilder(mot)
        }
    }
    if (courante.isNotEmpty()) lignes.add(courante.toString())
    return lignes
}

/**
 * Le nom du fichier proposé.
 *
 * LA RÉFÉRENCE EN FAIT PARTIE, parce que c'est par elle qu'on retrouve une
 * opération — dans le dossier des téléchargements comme dans une
 * conversation WhatsApp. « recu.pdf » se perd au deuxième.
 *
 * On ne garde que les caractères sûrs : une référence est déjà
 * alphanumérique, mais ce nom traverse des systèmes de fichiers dont on ne
 * choisit pas les règles.
 */
fun nomFichierFacture(reference: String): String {
    val propre = reference.filter { it.isLetterOrDigit() }.take(16)
    return if (propre.isEmpty()) "VaultEx-recu.pdf" else "VaultEx-$propre.pdf"
}
