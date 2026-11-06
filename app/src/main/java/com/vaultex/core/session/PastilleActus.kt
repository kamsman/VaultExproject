package com.vaultex.core.session

import android.content.Context
import com.vaultex.data.remote.api.ChangeApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/*
═══════════════════════════════════════════════════════════════════════════
LA PASTILLE DES ACTUALITÉS — UN POINT, ET RIEN D'AUTRE
═══════════════════════════════════════════════════════════════════════════

Elle dit « il y a du nouveau » sur le bouton des actualités de l'écran
Marché. Pas de son, pas de vibration, pas d'écran qui s'allume : elle se
découvre quand on ouvre l'application, et c'est l'utilisateur qui décide.

─── POURQUOI PAS UNE VRAIE NOTIFICATION ────────────────────────────────

Trois raisons, et la troisième suffirait à elle seule.

1. UNE ACTUALITÉ POUSSÉE FABRIQUE DE L'URGENCE. « Bitcoin chute, le
   Nasdaq plonge de 4 % » sur un téléphone à 22 h fait vendre au pire
   moment. Nos utilisateurs découvrent la crypto ; beaucoup ont acheté
   pour la première fois cette semaine. Un écran qu'on OUVRE est un
   choix, une notification est une injonction.

2. CES TITRES NE SONT PAS LES NÔTRES. Le relais recopie cinq rédactions
   qui écrivent pour le clic. Poussés en notification, leurs titres
   deviennent des messages de VaultEx dans l'esprit du lecteur, et on
   prêterait notre crédibilité à des phrases qu'on n'a ni écrites ni
   vérifiées.

3. ÇA ENTRAÎNERAIT LE MAUVAIS RÉFLEXE. VaultEx répète « ta phrase ne se
   tape jamais sur un site web ». Une notification d'actualité enseigne
   l'inverse : notification VaultEx → j'appuie → une page web s'ouvre.
   Le jour où quelqu'un imite ce format, l'utilisateur a été dressé à
   appuyer. C'est le raisonnement qui a fait refuser la WebView dans
   `ActusScreen` ; une notification la réintroduirait par la porte de
   derrière.

Le canal d'annonces reste réservé à ce qui le mérite — un dépôt reçu, un
achat confirmé, une alerte de sécurité. Plus il est rare, plus il est lu.

─── CE QU'ELLE COÛTE, ET C'EST ASSUMÉ ──────────────────────────────────

Un appel réseau de plus à l'ouverture de l'écran Marché. Amorti par le
cache du relais (un quart d'heure) et par [INTERVALLE] ici, mais ce n'est
pas gratuit et il ne faut pas le cacher.

Un échec réseau ne pose ni n'efface la pastille : il ne change rien. Une
pastille qui apparaîtrait à chaque coupure de réseau dirait n'importe
quoi.
═══════════════════════════════════════════════════════════════════════════
*/
@Singleton
class PastilleActus @Inject constructor(
    private val api: ChangeApi,
    @ApplicationContext appContext: Context
) {

    private val prefs =
        appContext.getSharedPreferences("vaultex_actus", Context.MODE_PRIVATE)

    private val _aDuNouveau = MutableStateFlow(false)

    /** Vrai quand un article est paru depuis la dernière visite de l'écran. */
    val aDuNouveau: StateFlow<Boolean> = _aDuNouveau.asStateFlow()

    private var derniereVerification = 0L

    /**
     * Regarde s'il y a du nouveau, au plus une fois par [INTERVALLE].
     *
     * Ne lève jamais : elle est appelée depuis l'écran Marché, qui ne doit
     * pas tomber parce qu'un flux d'actualités est injoignable.
     */
    suspend fun verifier() {
        val maintenant = System.currentTimeMillis()
        /*
        LE FREIN EST ICI ET PAS DANS L'ÉCRAN.

        L'écran Marché se recompose souvent — défilement, recherche,
        changement de filtre — et un `LaunchedEffect` peut repartir à
        chaque retour sur l'onglet. Sans ce frein, naviguer entre les
        onglets ferait un appel réseau par aller-retour.
        */
        if (maintenant - derniereVerification < INTERVALLE) return
        derniereVerification = maintenant

        val plusRecent = try {
            withContext(Dispatchers.IO) { api.actus() }
                .articles.orEmpty()
                .mapNotNull { it.quand }
                .maxOrNull() ?: 0L
        } catch (_: Exception) {
            /*
            RÉSEAU ABSENT : ON NE SAIT PAS, DONC ON NE DIT RIEN. La
            pastille garde l'état qu'elle avait — l'allumer sur une panne
            de réseau lui ferait dire n'importe quoi.

            Et on REND le jeton du frein : un échec ne doit pas interdire
            la prochaine tentative pendant un quart d'heure. Quelqu'un qui
            ouvre l'application dans un tunnel, puis la rouvre deux minutes
            plus tard avec du réseau, doit voir la pastille.
            */
            derniereVerification = 0L
            return
        }

        // Un flux sans aucune date ne permet aucune comparaison. Mieux vaut
        // pas de pastille qu'une pastille allumée en permanence.
        if (plusRecent <= 0L) return

        _aDuNouveau.value = plusRecent > prefs.getLong(CLE_VU, 0L)
    }

    /**
     * L'utilisateur vient de lire l'écran : on retient la date du plus
     * récent article affiché, et la pastille s'éteint.
     */
    fun marquerVu(plusRecent: Long) {
        _aDuNouveau.value = false
        if (plusRecent <= 0L) return
        if (plusRecent > prefs.getLong(CLE_VU, 0L)) {
            prefs.edit().putLong(CLE_VU, plusRecent).apply()
        }
    }

    companion object {
        /*
        UN QUART D'HEURE, parce que c'est la durée du cache du relais :
        demander plus souvent rendrait la même réponse, au prix d'un appel
        sur le réseau mobile de l'utilisateur.
        */
        const val INTERVALLE = 15L * 60 * 1000

        /*
        ZÉRO À LA PREMIÈRE INSTALLATION — DONC LA PASTILLE S'ALLUME.

        C'est un choix, et il se discute. L'argument contraire est bon :
        une pastille allumée dès la première seconde n'apprend rien, comme
        une icône qui ne distingue rien.

        Il perd parce que ce bouton est difficile à trouver. Il est en haut
        d'un onglet, entre une loupe et une cloche, et rien n'annonce qu'un
        écran d'actualités existe. Le point est alors la seule chose qui
        dise « regarde ici » — une fois, sans bruit, et il s'éteint dès la
        première visite.
        */
        private const val CLE_VU = "dernier_article_vu"
    }
}
