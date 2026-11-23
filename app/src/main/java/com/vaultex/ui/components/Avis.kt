package com.vaultex.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import com.vaultex.ui.theme.Surface as SurfaceColor
import com.vaultex.ui.theme.SurfaceLight
import com.vaultex.ui.theme.TextMuted
import com.vaultex.ui.theme.TextPrimary
import com.vaultex.ui.theme.TextSecondary
import com.vaultex.ui.viewmodel.AvisViewModel
import com.vaultex.ui.viewmodel.EtatAvis
import kotlinx.coroutines.delay

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
@Composable
fun CarteAvis(modifier: Modifier = Modifier) {
    val contexte = LocalContext.current

    /*
    DÉCIDÉ UNE SEULE FOIS, à la première composition — et la marque est
    posée dans le même geste.

    Même piège que dans `CarteNouveautes` : relire la préférence à chaque
    recomposition ferait disparaître la carte à l'instant où on la pose,
    puisque la valeur changerait sous elle.
    */
    /*
    LE GENRE EST LU EN MEME TEMPS QUE LA DECISION, et retenu avec elle.

    Le relire plus tard le ferait disparaitre sous la carte : la fenetre
    de 24 h peut expirer pendant qu'elle est a l'ecran, et le titre
    deviendrait vide au milieu d'une saisie.
    */
    val genre = remember {
        val g = MemoireAvis.operationRecente(contexte)
        val doit = MemoireAvis.aProposer(contexte)
        if (doit) MemoireAvis.nePlusProposer(contexte)
        mutableStateOf(if (doit) g else null)
    }
    var visible by remember { mutableStateOf(genre.value != null) }

    /*
    ═══════════════════════════════════════════════════════════════════════
    UN `if`, ET NON UN `return` SUIVI D'APPELS COMPOSABLES
    ═══════════════════════════════════════════════════════════════════════

    CE N'EST PAS UNE QUESTION DE STYLE. La version précédente écrivait :

        if (!visible) return
        val vm: AvisViewModel = hiltViewModel()
        val s by vm.state.collectAsState()
        LaunchedEffect(s.etat) { … }

    Quand `visible` passait à faux, cette fonction perdait d'un coup
    QUATRE appels composables — un ViewModel, un abonnement d'état, un
    effet et tout l'affichage — dans une seule recomposition.
    `CarteNouveautes` emploie le même `return`, mais sans RIEN après lui :
    la différence est là, et c'est elle qui compte.

    Un plantage est arrivé sur l'appareil, en `IndexOutOfBoundsException :
    Index -1 out of bounds for length 0`, SANS AUCUNE IMAGE `com.vaultex`
    dans la pile — l'exception venait entièrement du framework. Or
    `enableR8.fullMode=false` garde la hiérarchie des paquets : si une
    ligne de ce dépôt avait été impliquée, elle aurait figuré au rapport.
    Une pile sans notre code, sur un écran qu'on vient d'ajouter, désigne
    la façon dont on y entre et on en sort.

    JE N'AI PAS PROUVÉ QUE C'EST LA CAUSE, et je ne le prétends pas : il
    faudrait la pile complète par logcat pour trancher. Mais un `if`
    rend cette fonction structurellement stable — ce qui est à
    l'intérieur est présent ou absent en bloc, l'idiome normal de Compose
    — et il ne coûte rien. Si le plantage revient, on aura au moins
    écarté celle-ci.
    ═══════════════════════════════════════════════════════════════════════
    */
    if (visible) {
        CarteAvisOuverte(modifier, genre.value) { visible = false }
    }
}

@Composable
private fun CarteAvisOuverte(
    modifier: Modifier,
    genre: GenreOperation?,
    onFermer: () -> Unit
) {
    val vm: AvisViewModel = hiltViewModel()
    val s by vm.state.collectAsState()

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
    Surface(
        shape = RoundedCornerShape(20.dp),
        /*
        SUR FOND SOMBRE, L'ELEVATION EST UNE SURFACE PLUS CLAIRE — PAS UNE
        OMBRE.

        MESURE : `BgSecondary` (#111827) contre la page (#0B1120) donne un
        rapport de contraste de 1,06. C'est-a-dire RIEN : l'oeil ne separe
        pas deux surfaces a 1,06, et l'ombre que j'avais ajoutee est du
        noir sur du presque-noir, donc invisible elle aussi.

        J'avais juge la carte sur une maquette ou l'ombre etait dessinee
        en CSS, bien plus marquee qu'un `shadow` de Compose sur un theme
        sombre — un reflexe de theme clair, applique au mauvais endroit.

        `SurfaceLight` (#243044) donne 1,42. La, la carte se detache.

        L'OMBRE RESTE, mais pour ce qu'elle fait encore : adoucir le bord.
        Ce n'est plus elle qui porte la separation.
        */
        color = SurfaceLight,
        border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.70f)),
        modifier = modifier
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(20.dp), clip = false)
    ) {
        Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 14.dp)) {

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
                return@Column
            }

            // ─── Titre, étoiles, croix ───────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Le meme marqueur que le 🚀 de « Quoi de neuf » : deux
                // cartes au meme endroit doivent se reconnaitre d'un coup
                // d'oeil, sans etre lues.
                Text("💬", fontSize = 17.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    /*
                    TROIS PHRASES ENTIERES, PAS UN GABARIT A TROU.

                    « Comment s'est passé votre %1$s ? » aurait produit
                    « passé votre réception » : en français l'accord suit
                    le genre du nom, qu'un trou ne porte pas. Le repli
                    générique sert quand la préférence est illisible.
                    */
                    stringResource(
                        when (genre) {
                            GenreOperation.ENVOI -> R.string.avis_titre_envoi
                            GenreOperation.RECEPTION -> R.string.avis_titre_reception
                            GenreOperation.ECHANGE -> R.string.avis_titre_echange
                            null -> R.string.avis_titre
                        }
                    ),
                    // 16 sp et non 17 : « Comment s'est passée votre
                    // reception ? » tient alors sur une ligne de plus
                    // d'ecrans. Au-dela elle se replie, ce qui reste lisible.
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
            carte. `Surface` (#1A2234) sous `SurfaceLight` (#243044) donne
            exactement cette lecture.
            */
            Surface(shape = RoundedCornerShape(14.dp), color = SurfaceColor) {
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
                        modifier = Modifier.fillMaxWidth()
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
                BoutonEnvoyerAvis(s.peutEnvoyer, s.etat == EtatAvis.ENVOI) {
                    vm.envoyer(
                        /*
                        PRIS À LA SOURCE D'`AdminBot`, et non recopiés
                        ici : une deuxième copie de la clé « install_at »
                        finirait par dire autre chose que la première.
                        */
                        jours = AdminBot.joursDepuisInstallation(),
                        modele = AdminBot.modeleAppareil(),
                        version = BuildConfig.VERSION_NAME
                    )
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
private fun BoutonEnvoyerAvis(actif: Boolean, enCours: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (actif) AccentBlue else SurfaceLight,
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
