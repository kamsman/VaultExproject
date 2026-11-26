package com.vaultex.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vaultex.BuildConfig
import com.vaultex.R
import com.vaultex.core.monitoring.AdminBot
import com.vaultex.core.session.GenreOperation
import com.vaultex.core.session.MemoireAvis
import com.vaultex.ui.theme.AccentBlue
import com.vaultex.ui.theme.AccentGreen
import com.vaultex.ui.theme.AccentOrange
import com.vaultex.ui.theme.BgSecondary
import com.vaultex.ui.theme.Surface as SurfaceColor
import com.vaultex.ui.theme.SurfaceLight
import com.vaultex.ui.theme.ThemeSombre
import com.vaultex.ui.theme.TextMuted
import com.vaultex.ui.theme.TextPrimary
import com.vaultex.ui.theme.TextSecondary
import com.vaultex.ui.viewmodel.AvisViewModel
import com.vaultex.ui.viewmodel.EtatAvis
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
═══════════════════════════════════════════════════════════════════════════
« COMMENT ÇA S'EST PASSÉ ? » — UNE QUESTION OUVERTE, DES ÉTOILES EN DÉCOR
═══════════════════════════════════════════════════════════════════════════

L'ORDRE DES DEUX ÉLÉMENTS EST LE FOND DE CET ÉCRAN.

Un formulaire d'avis ordinaire demande une note, puis offre un champ
libre « si vous voulez ». Résultat : on tape cinq étoiles et on s'en va.
On récolte une moyenne, et une moyenne ne se corrige pas — « 4,2 sur 5 »
ne dit pas quoi réparer.

Ici la PHRASE est la question, et les étoiles ne sont qu'un hameçon : un
geste à coût nul qui ouvre la conversation, posé À CÔTÉ du titre pour
qu'on ne puisse pas croire qu'il suffit.

─── CE QUI SE VOIT ET CE QU'IL FAUT CROIRE ────────────────────────────────

La ligne affichée dit « ni votre adresse, ni votre numéro, ni la
référence ne partent ». Elle est VRAIE, et elle a dû être RÉÉCRITE pour
l'être : ma première version promettait aussi « ni montant », ce que
l'anonymisation ne fait pas — un montant de cinq chiffres ne désigne
personne, et le retirer priverait la plainte de son sens. C'est l'outil
`essai-avis.mjs` qui l'a montré, en imprimant le texte transformé au lieu
de me laisser raisonner dessus.

Le même essai a révélé plus grave : la fonction ne masquait que les
adresses EVM et TRON. Une adresse BTC, bech32, DOGE ou Solana partait en
clair, alors que ce formulaire s'ouvre après un envoi sur n'importe
laquelle de ces chaînes. Le relais applique désormais une règle écrite
sur la FORME d'une adresse et non sur la liste des chaînes.

Ce qu'elle promet est appliqué APRÈS cet écran, par le serveur, et
épinglé par les tests du relais : une promesse que l'écran tiendrait
lui-même ne vaudrait rien, puisque l'écran est dans l'APK.

Si cette ligne mentait, elle ferait exactement le contraire de son but :
quelqu'un qui écrit « j'ai envoyé à DH5… et rien reçu » en croyant que
ça reste entre nous mérite que ce soit le cas.

─── NON MODALE, FLOTTANTE, COMME « QUOI DE NEUF » ─────────────────────────

Même raison, écrite dans `Nouveautes.kt` : une note qui bloque l'écran
pour une chose qui n'exige aucune décision se referme sans être lue, et
une note posée dans le flux d'une liste disparaît dès qu'on descend.
═══════════════════════════════════════════════════════════════════════════
*/
/**
 * L'état de la carte, DÉCIDÉ UNE FOIS et à hisser HORS de la LazyColumn.
 *
 * ─── POURQUOI CE N'EST PAS UN `remember` DANS LA CARTE ─────────────────
 *
 * La carte vit maintenant dans le flux de l'accueil, c'est-à-dire dans un
 * `item { }` de LazyColumn. Or l'en-tête de `DashboardScreen` le dit déjà,
 * et pour l'avoir payé : « LazyColumn détruit un item dès qu'il sort de
 * l'écran et le recrée dès qu'il revient ».
 *
 * Un `remember` posé dans l'item se rejouerait donc à chaque aller-retour
 * de défilement. Et il se rejouerait FAUX : la marque « déjà proposé »
 * ayant été posée au premier passage, `aProposer` rendrait false et la
 * carte s'effacerait toute seule en revenant à l'écran.
 *
 * Hissé dans l'écran, cet état est calculé une fois et survit au
 * défilement. Le texte en cours de frappe, lui, vit dans le ViewModel,
 * qui est porté par la destination de navigation — il survit déjà.
 *
 * Nul = rien à proposer, ou la carte a été fermée.
 */
@Composable
fun rememberEtatAvis(): MutableState<GenreOperation?> {
    val contexte = LocalContext.current
    return remember {
        /*
        LE GENRE EST LU EN MEME TEMPS QUE LA DECISION, et retenu avec elle.
        Le relire plus tard le ferait disparaitre sous la carte : la
        fenetre de 24 h peut expirer pendant qu'elle est a l'ecran, et le
        titre deviendrait vide au milieu d'une saisie.
        */
        val genre = MemoireAvis.operationRecente(contexte)
        val doit = MemoireAvis.aProposer(contexte)
        if (doit) MemoireAvis.nePlusProposer(contexte)
        mutableStateOf(if (doit) genre else null)
    }
}

/*
`BringIntoViewRequester` EST ENCORE EXPÉRIMENTAL dans Compose 1.6, la
version que porte le BOM 2024.02.00 de ce projet. L'annotation est donc
obligatoire : sans elle, la compilation échoue sur une erreur qui parle
d'API « opt-in » et pas du clavier.

C'est la seule API expérimentale de ce fichier, et l'opt-in est posé sur
cette fonction précise plutôt que sur le module entier : une activation
globale ferait passer en silence la prochaine, qui ne sera peut-être pas
aussi stable que celle-ci.
*/
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CarteAvis(
    modifier: Modifier = Modifier,
    genre: GenreOperation?,
    onFermer: () -> Unit
) {
    val vm: AvisViewModel = hiltViewModel()
    val s by vm.state.collectAsState()

    /*
    DE QUOI FAIRE REMONTER LE CHAMP QUAND LE CLAVIER S'OUVRE. Les deux
    vivent ici plutôt qu'au champ parce que `remember` leur donne alors la
    durée de vie de la carte : recréés à chaque recomposition, la demande
    de défilement porterait sur un objet que plus personne n'écoute.
    Voir le champ, plus bas, pour la mesure qui a rendu ça nécessaire.
    */
    val demandeVisible = remember { BringIntoViewRequester() }
    val porteeVisible = rememberCoroutineScope()

    /*
    LE REMERCIEMENT S'EFFACE TOUT SEUL.

    Laisser « Merci » à l'écran obligerait à fermer une carte dont on n'a
    plus rien à faire — un dernier geste demandé à quelqu'un qui vient
    justement de nous rendre un service.
    */
    LaunchedEffect(s.etat) {
        if (s.etat == EtatAvis.MERCI) {
            delay(2200)
            onFermer()
        }
    }

    /*
    ═══════════════════════════════════════════════════════════════════════
    OPAQUE, ET POSEE SUR L'ECRAN PAR UNE OMBRE
    ═══════════════════════════════════════════════════════════════════════

    La premiere version reprenait le fond translucide de `CarteNouveautes`
    — `alpha = 0.96f`. Vu sur l'appareil, ca ne marche pas ICI, et la
    raison tient a ce que les deux cartes demandent.

    « Quoi de neuf » ne demande RIEN : on la lit, on la ferme. Laisser la
    page transparaitre derriere elle la pose joliment sur le contenu.

    Celle-ci demande qu'on ECRIVE. Elle tombe sur la liste des actifs, et
    les lignes de soldes passaient au travers du texte et du champ de
    saisie. Un formulaire a travers lequel on voit des chiffres n'a pas
    l'air d'attendre une reponse : il a l'air d'un reste d'affichage.

    Fond plein, donc, et une OMBRE plutot qu'une simple bordure : c'est
    l'ombre qui dit « ceci est au-dessus », bien plus qu'un trait de 1 dp.
    ═══════════════════════════════════════════════════════════════════════
    */
    /*
    ═══════════════════════════════════════════════════════════════════════
    LE FOND DEPEND DU THEME, PARCE QUE LA TECHNIQUE N'EST PAS LA MEME
    ═══════════════════════════════════════════════════════════════════════

    MESURES, contre la page de chaque theme :

      SOMBRE   page #0B1120
        BgSecondary  #111827   1,06  ← invisible (premiere version)
        SurfaceLight #243044   1,42
        + bleu 8 %   #263752   1,57  ← retenu
        + bleu 12 %  #273A59   1,65  mais texte secondaire a 4,46,
                                      sous le seuil de 4,5

      CLAIR    page #F5F8FF
        SurfaceLight #F1F5F9   1,03  ← PLUS SOMBRE que la page
        BgSecondary  #FFFFFF   1,06  ← retenu

    DEUX LECONS, ET LA SECONDE EST UNE REGRESSION QUE J'AI FAITE.

    En sombre, l'elevation est une surface plus claire : une ombre y est
    du noir sur du presque-noir. J'avais d'abord juge la carte sur une
    maquette ou l'ombre etait dessinee en CSS, bien plus marquee qu'un
    `shadow` de Compose — un reflexe de theme clair au mauvais endroit.

    Puis, en corrigeant le sombre avec `SurfaceLight`, j'ai CASSE LE
    CLAIR sans le voir : la, `SurfaceLight` est plus sombre que la page
    et ne separe plus rien. Je n'avais mesure qu'un seul theme.

    En clair, aucune surface n'est plus claire qu'une page deja blanche.
    C'est l'OMBRE qui y fait tout le travail — et elle y fonctionne,
    justement parce que le fond est clair.

    LE BLEU N'EST PAS DECORATIF : il eloigne la carte des lignes de
    soldes, qui sont neutres, et il la rattache a ce qu'elle demande.
    Huit pour cent et pas douze : au-dela, le texte secondaire tombe
    sous le seuil de lisibilite.
    ═══════════════════════════════════════════════════════════════════════
    */
    val sombre = ThemeSombre
    val fondCarte =
        if (sombre) AccentBlue.copy(alpha = 0.08f).compositeOver(SurfaceLight)
        else BgSecondary

    /*
    CE QUI EST CREUSE DANS LA CARTE — le champ de saisie, et le bouton
    tant qu'il est inactif.

    IL DOIT ETRE PLUS SOMBRE QUE LA CARTE DANS LES DEUX THEMES, et la
    couleur qui le donne n'est pas la meme :

      sombre : `Surface`      #1A2234  sous la carte #263752  ✓
      clair  : `SurfaceLight` #F1F5F9  sous la carte #FFFFFF  ✓

    Pris tel quel, `Surface` vaut #FFFFFF en theme clair — exactement la
    couleur de la carte. Le champ y aurait disparu, et le bouton inactif
    avec lui. C'est la meme asymetrie que celle qui a casse le fond, et
    elle se serait vue au meme endroit : nulle part, jusqu'a ce que
    quelqu'un ouvre l'application en clair.
    */
    val fondCreux = if (sombre) SurfaceColor else SurfaceLight

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = fondCarte,
        // Plus epaisse et plus franche qu'avant : sur un fond sombre, un
        // bord net ajoute autant que le fond lui-meme, et il ne coute
        // aucun contraste de texte.
        border = BorderStroke(1.5.dp, AccentBlue.copy(alpha = if (sombre) 0.80f else 0.55f)),
        modifier = modifier
            .fillMaxWidth()
            .shadow(if (sombre) 14.dp else 20.dp, RoundedCornerShape(20.dp), clip = false)
    ) {
        Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 14.dp)) {

            /*
            ═══════════════════════════════════════════════════════════
            UN `return@Column` FAISAIT PLANTER COMPOSE
            ═══════════════════════════════════════════════════════════

            MESURÉ le 26 novembre 2026, pile complète lue dans logcat :

                IndexOutOfBoundsException: Index -1 out of bounds for length 0
                  at java.util.ArrayList.remove
                  at ComposerImpl … Stack.pop()
                  at ComposerImpl.endGroup()
                  at ComposerImpl.doCompose()
                  at CompositionImpl.recompose()

            Aucun cadre de VaultEx dans toute la pile, et aucune exception
            antérieure dans le journal. Ce n'était donc pas une erreur de
            notre code rattrapée trop tard : c'est la STRUCTURE de cette
            composition qui était fausse.

            ─── CE QUE FAISAIT L'ANCIENNE FORME ───────────────────────

                Column {
                    if (etat == MERCI) {
                        Row { … }       // émet des groupes
                        return@Column   // et sort
                    }
                    titre; étoiles; champ; bouton   // jamais émis
                }

            Au passage à MERCI, la Column cesse d'émettre quatre blocs
            qu'elle émettait juste avant. Le composeur attend de refermer
            autant de groupes qu'il en a ouverts ; il dépile du vide, et
            `ArrayList.remove(-1)` lève.

            Un `return` qualifié depuis la lambda d'un composable INLINE
            — Column, Row, Box — est un piège connu de Compose : le
            compilateur n'a aucun moyen de refermer les groupes restés
            ouverts sur ce chemin de sortie.

            ─── POURQUOI UN `else` SUFFIT ─────────────────────────────

            Les deux branches deviennent des chemins DÉCLARÉS, et le
            compilateur Compose pose les groupes des deux côtés. Rien ne
            sort au milieu, donc rien ne reste ouvert.

            Le corps n'a pas changé d'une ligne — seulement d'un cran
            d'indentation. Le diff est long, la modification ne l'est pas.
            ═══════════════════════════════════════════════════════════
            */
            if (s.etat == EtatAvis.MERCI) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CheckCircle, null,
                        tint = AccentGreen, modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.avis_merci),
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary
                    )
                }
            } else {

                // ─── Titre, étoiles, croix ───────────────────────────────────
                /*
                ALIGNE EN HAUT, PAS AU CENTRE. Le titre nomme maintenant
                l'operation, donc il se replie sur deux lignes sur beaucoup
                d'ecrans. Centree, la croix se retrouvait alors a cheval
                entre les deux — elle doit rester en face de la premiere.
                */
                Row(verticalAlignment = Alignment.Top) {
                    // Le meme marqueur que le 🚀 de « Quoi de neuf » : deux
                    // cartes au meme endroit doivent se reconnaitre d'un coup
                    // d'oeil, sans etre lues.
                    Text("💬", fontSize = 17.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        /*
                        LE TITRE EST GENERAL, ET C'EST UN CHOIX DE FOND.

                        Il a d'abord nomme l'operation — « Comment s'est passé
                        votre envoi ? » — parce qu'une question sans referent
                        oblige a deviner de quoi on parle.

                        MAIS CE N'EST PAS LA QUESTION QU'ON SE POSE. 223
                        installations, zero transaction en francs : ce qu'il
                        faut trouver n'est pas le defaut d'un envoi, c'est
                        celui de l'application. Une question sur une operation
                        recoit une reponse sur une operation — le premier vrai
                        retour recu l'a prouve : « c'est bien », deux etoiles,
                        inexploitable.

                        LE RISQUE EST ASSUME : une question large attire
                        aussi des reponses larges, et on ne fait rien pour
                        l'orienter.

                        J'avais d'abord mis le travail de diagnostic dans
                        l'invite du champ — « ce qui vous bloque, ce qui
                        manque ». Ecarte, et a juste titre : ca se lit comme
                        une enquete, et on ne demande pas a quelqu'un de
                        remplir un formulaire de plainte. On lui demande ce
                        qu'il pense. Ce qui le gene sortira de lui-meme, ou
                        ne sortira pas — mais au moins il aura parle.

                        LE GENRE DE L'OPERATION N'EST PAS PERDU POUR AUTANT :
                        il part au relais et s'affiche dans Telegram. Savoir
                        qu'un avis suit un envoi plutot qu'une reception se
                        lit a la lecture, pas a l'ecriture.
                        */
                        stringResource(R.string.avis_titre),
                        fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable { onFermer() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Close,
                            stringResource(R.string.close),
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.avis_aide),
                    fontSize = 12.sp, color = TextSecondary, lineHeight = 17.sp
                )

                Spacer(Modifier.height(10.dp))
                Etoiles(note = s.etoiles, onNoter = vm::noter)

                // ─── La question ouverte ─────────────────────────────────────
                Spacer(Modifier.height(10.dp))
                /*
                LE CHAMP EST PLUS SOMBRE QUE LA CARTE, et c'est le bon sens :
                la carte est posee SUR la page, le champ est creuse DANS la
                carte. La couleur vient de `fondCreux`, qui change avec le
                theme — voir sa definition plus haut.
                */
                Surface(shape = RoundedCornerShape(14.dp), color = fondCreux) {
                    Box(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (s.texte.isEmpty()) {
                            Text(
                                stringResource(R.string.avis_invite),
                                fontSize = 13.sp, color = TextMuted
                            )
                        }
                        BasicTextField(
                            value = s.texte,
                            onValueChange = vm::saisir,
                            enabled = s.etat != EtatAvis.ENVOI,
                            textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp, lineHeight = 19.sp),
                            cursorBrush = SolidColor(AccentBlue),
                            /*
                            QUATRE LIGNES, PAS UNE.

                            Un champ d'une seule ligne dit « réponds court ».
                            Celui-ci doit dire le contraire : c'est la phrase
                            qu'on est venu chercher.
                            */
                            maxLines = 4,
                            /*
                            ═══════════════════════════════════════════════════
                            LE CHAMP SE FAIT REMONTER LUI-MÊME
                            ═══════════════════════════════════════════════════

                            VU SUR L'APPAREIL, deux fois : le clavier s'ouvre
                            et recouvre le champ. On tape sans voir ce qu'on
                            écrit, sur l'écran précisément fait pour recueillir
                            une phrase.

                            LA PREMIÈRE CORRECTION NE POUVAIT PAS MARCHER, et
                            c'est mesurable plutôt que supposable. Elle ajoutait
                            au bas de la liste

                                maxOf(BottomBarSpace, WindowInsets.ime...)

                            Or l'application n'appelle NI `enableEdgeToEdge` NI
                            `setDecorFitsSystemWindows(false)`. Avec
                            `adjustResize`, le système rétrécit donc la fenêtre
                            lui-même, et `WindowInsets.ime` rend ZÉRO : le
                            `maxOf` retombe sur `BottomBarSpace`, et la ligne
                            entière ne fait rien. Elle avait l'air d'un
                            correctif, elle n'en était pas un.

                            ─── CE QUI MANQUAIT N'ÉTAIT PAS DE LA PLACE ───────

                            C'était un DÉFILEMENT. Même avec la place en bas,
                            rien ne remonte la carte : elle vit dans un
                            `item` de LazyColumn, loin du haut, et la fenêtre
                            qui rétrécit la pousse hors champ.

                            `bringIntoView` demande au conteneur défilant de
                            l'amener à l'écran — il traverse la LazyColumn, ce
                            qu'un `imePadding` ne sait pas faire. Et il ne
                            dépend d'aucune mesure d'encart, donc il marche que
                            la fenêtre soit redimensionnée ou non.

                            L'APPEL EST DANS UNE CORUTINE PARCE QU'IL ATTEND :
                            `bringIntoView()` suspend jusqu'à la fin du
                            défilement, qui se joue pendant que le clavier
                            monte encore.
                            ═══════════════════════════════════════════════════
                            */
                            modifier = Modifier
                                .fillMaxWidth()
                                .bringIntoViewRequester(demandeVisible)
                                .onFocusEvent { etat ->
                                    if (etat.isFocused) {
                                        porteeVisible.launch { demandeVisible.bringIntoView() }
                                    }
                                }
                        )
                    }
                }

                if (s.etat == EtatAvis.ECHEC) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.avis_echec),
                        fontSize = 12.sp, color = AccentOrange, lineHeight = 17.sp
                    )
                }

                // ─── La promesse, puis le bouton ─────────────────────────────
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.avis_prive),
                        fontSize = 11.sp, color = TextMuted, lineHeight = 15.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(10.dp))
                    BoutonEnvoyerAvis(s.peutEnvoyer, s.etat == EtatAvis.ENVOI, fondCreux) {
                        vm.envoyer(
                            /*
                            PRIS À LA SOURCE D'`AdminBot`, et non recopiés
                            ici : une deuxième copie de la clé « install_at »
                            finirait par dire autre chose que la première.
                            */
                            jours = AdminBot.joursDepuisInstallation(),
                            modele = AdminBot.modeleAppareil(),
                            version = BuildConfig.VERSION_NAME,
                            /*
                            En minuscules et sans accent : ce texte traverse
                            un JSON, un Worker et Telegram. Le nom de l'enum
                            suffit, le relais le met en francais.
                            */
                            apres = genre?.name?.lowercase()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Cinq étoiles, et le droit de les retirer.
 *
 * `StarBorder` plutôt qu'une étoile grise : le contour dit « à remplir »,
 * une étoile grise dit « indisponible ».
 */
@Composable
private fun Etoiles(note: Int, onNoter: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        for (i in 1..5) {
            val pleine = i <= note
            Box(
                Modifier
                    // 36 dp : la cible du doigt, et non la taille de l'icône.
                    // Cinq étoiles de 20 dp collées sont impossibles à viser.
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { onNoter(i) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (pleine) Icons.Default.Star else Icons.Default.StarBorder,
                    /*
                    UNE SEULE DESCRIPTION POUR LES CINQ, sur la première.
                    Cinq étoiles annoncées une par une font lire « étoile,
                    étoile, étoile… » à un lecteur d'écran, ce qui ne dit
                    rien de la note.
                    */
                    if (i == 1) stringResource(R.string.avis_etoiles) else null,
                    /*
                    CHAUDES MEME VIDES. En gris (`TextMuted`), les cinq
                    etoiles se lisaient comme desactivees : elles avaient
                    la couleur des textes secondaires de cette carte. Un
                    contour orange pale dit « a remplir » ; le gris disait
                    « indisponible ».
                    */
                    tint = if (pleine) AccentOrange else AccentOrange.copy(alpha = 0.40f),
                    modifier = Modifier.size(27.dp)
                )
            }
        }
    }
}

@Composable
private fun BoutonEnvoyerAvis(
    actif: Boolean,
    enCours: Boolean,
    fondInactif: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        // `SurfaceLight` en dur se confondait avec la carte en theme
        // sombre : le bouton inactif y devenait invisible. Il recoit
        // maintenant la meme couleur creusee que le champ.
        color = if (actif) AccentBlue else fondInactif,
        modifier = Modifier.clip(RoundedCornerShape(12.dp))
            .clickable(enabled = actif, onClick = onClick)
    ) {
        Box(
            Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            if (enCours) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            } else {
                Text(
                    stringResource(R.string.avis_envoyer),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (actif) Color.White else TextMuted
                )
            }
        }
    }
}
