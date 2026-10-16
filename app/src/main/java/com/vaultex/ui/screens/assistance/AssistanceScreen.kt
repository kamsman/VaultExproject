package com.vaultex.ui.screens.assistance

import android.content.Intent
import android.net.Uri

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vaultex.R
import com.vaultex.domain.support.ContactsVaultex
import com.vaultex.ui.theme.AccentBlue
import com.vaultex.ui.theme.AccentOrange
import com.vaultex.ui.theme.AccentRed
import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.BorderColor
import com.vaultex.ui.theme.SurfaceLight
import com.vaultex.ui.theme.TextMuted
import com.vaultex.ui.theme.TextPrimary
import com.vaultex.ui.theme.TextSecondary
import com.vaultex.ui.theme.Surface as SurfaceColor
import com.vaultex.ui.viewmodel.AssistanceViewModel
import com.vaultex.ui.viewmodel.MessageAssistance

/*
═══════════════════════════════════════════════════════════════════════════
L'ÉCRAN D'ASSISTANCE
═══════════════════════════════════════════════════════════════════════════

Une conversation, et rien d'autre. Pas de catégories à choisir, pas de
formulaire : quelqu'un qui bloque veut écrire sa phrase, pas remplir des
champs.

─── CE QUI EST DIT D'ENTRÉE, ET POURQUOI ────────────────────────────────

Que c'est une assistance automatique, et que le changeur reste joignable.
Laisser croire à un humain serait payé deux fois : par la déception quand
la réponse sonne faux, et par la confiance qu'on ne regagne pas.

Et qu'on ne demandera JAMAIS la phrase secrète. C'est l'arnaque la plus
courante sur ce marché — quelqu'un se fait passer pour le support et
réclame les douze mots. Le dire ici, dans l'écran qui ressemble le plus à
ce qu'un escroc imiterait, est le seul endroit où ça protège vraiment.

─── POURQUOI CET ÉCRAN RESSEMBLE À UNE MESSAGERIE ───────────────────────

Parce que c'en est une, et que personne n'a besoin d'apprendre à s'en
servir. Les bulles sont asymétriques — le coin de la pointe est rogné du
côté de celui qui parle — la barre de saisie est une pastille avec un
bouton rond, et l'attente est annoncée par trois points qui respirent.
Ce sont les conventions de WhatsApp, et c'est exactement l'intérêt :
l'écran est déjà connu avant d'être ouvert.
═══════════════════════════════════════════════════════════════════════════
*/
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistanceScreen(navController: NavController) {
    val vm: AssistanceViewModel = hiltViewModel()
    val state by vm.state.collectAsState()
    val defilement = rememberScrollState()

    // On suit la conversation : une réponse qui arrive hors de l'écran
    // ressemble à une absence de réponse.
    LaunchedEffect(state.messages.size, state.enCours) {
        defilement.animateScrollTo(defilement.maxValue)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.assistance_titre),
                        fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.back), tint = AccentBlue)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BgPrimary)
            )
        },
        containerColor = BgPrimary
    ) { padding ->
        /*
        ═══════════════════════════════════════════════════════════════════
        LE CLAVIER NE DOIT PAS RECOUVRIR LA BARRE DE SAISIE
        ═══════════════════════════════════════════════════════════════════

        Vu sur un vrai téléphone : le clavier ouvert, et le champ passé
        dessous. On écrit sans voir ce qu'on écrit — sur l'écran où l'on
        pose une question, c'est-à-dire où l'on formule déjà mal.

        Le manifeste déclare pourtant `adjustResize`, et d'autres écrans
        de cette application emploient `imePadding()`. Celui-ci ne
        l'avait pas : la barre de saisie est fixée en bas d'une colonne
        qui remplit l'écran, donc elle descend avec le clavier.

        `imePadding` réserve la hauteur du clavier ouvert. La conversation
        se comprime, la barre reste visible, et le défilement automatique
        la suit.
        ═══════════════════════════════════════════════════════════════════
        */
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {

            Column(
                Modifier.weight(1f).verticalScroll(defilement)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.messages.isEmpty()) {
                    Accueil()
                    Suggestions(vm)
                }

                state.messages.forEach { Bulle(it) }

                if (state.enCours) Reflexion()

                state.erreur?.let { Alerte(it, AccentRed, Icons.Default.Warning) }

                /*
                « PARLER AU CHANGEUR » N'EST PAS UN ÉCHEC.

                C'est la sortie prévue, et elle doit être visible au moment
                où elle sert : quand la réponse automatique n'a pas suffi.
                L'afficher en permanence la banaliserait.
                */
                if (state.passerAuChangeur) JoindreEquipe()

                Spacer(Modifier.height(4.dp))
            }

            if (state.numeroDemande) DemandeNumero(vm, state.numero, state.numeroValide)
            else Saisie(vm, state.saisie, state.peutEnvoyer)
        }
    }
}

/*
L'ACCUEIL DIT TROIS CHOSES, ET PAS UNE DE PLUS.

Ce que c'est, ce que ça ne fera jamais, et par quoi commencer. Un écran
vide ferait écrire « bonjour » et attendre ; une page d'explications ne
serait pas lue.

L'AVERTISSEMENT EST UNE LIGNE AVEC UN CADENAS, pas un paragraphe en
gras. Un pavé de texte orange se lit comme une décoration et s'oublie à
la deuxième visite ; une ligne tenue par une icône reste un repère.
*/
@Composable
private fun Accueil() {
    Surface(shape = RoundedCornerShape(16.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(AccentBlue.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.SupportAgent, null,
                        tint = AccentBlue, modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.assistance_accueil_titre),
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary
                )
            }
            Text(
                stringResource(R.string.assistance_accueil_aide),
                fontSize = 12.sp, lineHeight = 18.sp, color = TextSecondary
            )
            Alerte(stringResource(R.string.assistance_jamais_phrase), AccentOrange, Icons.Default.Lock)
        }
    }
}

/*
═══════════════════════════════════════════════════════════════════════════
« PARLER À UN HUMAIN » N'EST PAS UN ÉCHEC, ET CE N'EST PLUS UNE PHRASE
═══════════════════════════════════════════════════════════════════════════

C'est la sortie prévue, et elle apparaît au moment où elle sert : quand la
réponse automatique n'a pas suffi, ou quand le relais n'a pas répondu du
tout. L'afficher en permanence la banaliserait.

─── ELLE DISAIT « ÉCRIS AU CHANGEUR SUR LE CANAL HABITUEL » ────────────

C'est-à-dire rien. Quelqu'un qui ouvre l'assistance pour la première fois
n'a pas de canal habituel, et on venait de lui annoncer que la machine ne
savait pas répondre. Une sortie qui demande de deviner où aller n'est pas
une sortie.

Trois canaux, chacun à un geste : WhatsApp, Telegram, courriel.

─── LES ADRESSES SONT DANS L'APK, ET C'EST LA RAISON ───────────────────

Le relais les connaît aussi — le modèle doit pouvoir les donner. Mais
cette carte s'affiche PRÉCISÉMENT quand le relais n'a pas répondu. Des
coordonnées de support qui dépendent du serveur en panne ne servent à rien
le jour où elles servent.

─── ET L'AVERTISSEMENT VOYAGE AVEC ELLES ───────────────────────────────

Donner trois canaux sans dire qu'aucun ne demandera la phrase secrète,
c'est apprendre à faire confiance à un canal. C'est exactement ce qu'un
escroc exploite ensuite : « je suis le support VaultEx, sur WhatsApp ».
═══════════════════════════════════════════════════════════════════════════
*/
@Composable
private fun JoindreEquipe() {
    val contexte = LocalContext.current

    /*
    UNE OUVERTURE QUI PEUT NE TROUVER PERSONNE.

    `ACTION_VIEW` sur un téléphone sans navigateur, ou `ACTION_SENDTO` sans
    client de messagerie, lève `ActivityNotFoundException`. Laisser passer
    ferait fermer l'application sur un appui — depuis l'écran où quelqu'un
    cherchait de l'aide.
    */
    fun ouvrir(action: String, lien: String) {
        runCatching {
            contexte.startActivity(Intent(action, Uri.parse(lien)))
        }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = AccentOrange.copy(alpha = 0.10f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.assistance_humain_titre),
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AccentOrange
            )
            Text(
                stringResource(R.string.assistance_humain_aide),
                fontSize = 12.sp, lineHeight = 17.sp, color = TextSecondary
            )

            Canal(
                Icons.Default.Chat,
                stringResource(R.string.assistance_canal_whatsapp),
                ContactsVaultex.WHATSAPP_AFFICHE
            ) { ouvrir(Intent.ACTION_VIEW, ContactsVaultex.LIEN_WHATSAPP) }

            Canal(
                Icons.Default.Public,
                stringResource(R.string.assistance_canal_telegram),
                ContactsVaultex.TELEGRAM_AFFICHE
            ) { ouvrir(Intent.ACTION_VIEW, ContactsVaultex.TELEGRAM) }

            Canal(
                Icons.Default.Email,
                stringResource(R.string.assistance_canal_email),
                ContactsVaultex.EMAIL
            ) { ouvrir(Intent.ACTION_SENDTO, ContactsVaultex.LIEN_EMAIL) }

            Text(
                stringResource(R.string.assistance_canal_avertissement),
                fontSize = 11.sp, lineHeight = 15.sp, color = AccentOrange
            )
        }
    }
}

/**
 * Un canal, en une ligne sur laquelle on appuie.
 *
 * LA VALEUR EST AFFICHÉE, pas seulement le nom du canal. Quelqu'un dont
 * le téléphone n'ouvre pas WhatsApp doit pouvoir lire le numéro et le
 * recopier — et c'est aussi ce qui permet de vérifier, plus tard, qu'un
 * « support » qui le contacte ailleurs n'est pas nous.
 */
@Composable
private fun Canal(icone: ImageVector, nom: String, valeur: String, onClic: () -> Unit) {
    val forme = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth()
            .clip(forme)
            .background(SurfaceColor)
            .clickable(onClick = onClic)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icone, null, tint = AccentBlue, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(nom, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(valeur, fontSize = 11.sp, color = TextSecondary)
        }
        Icon(
            Icons.Default.ChevronRight, null,
            tint = AccentBlue, modifier = Modifier.size(17.dp)
        )
    }
}

/**
 * Une ligne tenue par une icône : l'avertissement de la phrase secrète,
 * et les erreurs. Même forme pour les deux, parce que dans les deux cas
 * il s'agit d'une phrase qu'on doit remarquer sans la chercher — mais
 * pas la même icône : un cadenas annonce un conseil de sécurité, un
 * triangle annonce que quelque chose vient d'échouer.
 */
@Composable
private fun Alerte(texte: String, teinte: Color, icone: ImageVector) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = teinte.copy(alpha = 0.10f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(icone, null, tint = teinte, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(9.dp))
            Text(texte, fontSize = 11.5.sp, lineHeight = 16.sp, color = teinte)
        }
    }
}

/*
═══════════════════════════════════════════════════════════════════════════
QUATRE QUESTIONS TOUTES FAITES
═══════════════════════════════════════════════════════════════════════════

Un champ vide demande de formuler, et formuler est justement ce qu'on
fait mal quand on est bloqué ou pressé. Sur un téléphone, dans la rue,
« c'est quoi le minimum » prend vingt frappes.

CE SONT CELLES AUXQUELLES LE RELAIS SAIT RÉPONDRE SANS MODÈLE, et ce
n'est pas un hasard : tant qu'il n'y a pas de clé d'API, ce sont les
seules qui aboutissent. Les proposer, c'est mener vers ce qui marche au
lieu de laisser découvrir ce qui ne marche pas.

La dernière — la phrase secrète — n'est pas une question qu'on pense à
poser. C'est pour ça qu'elle est là : quelqu'un à qui l'on vient de
réclamer ses douze mots doit pouvoir vérifier en un geste, avant d'avoir
le temps d'hésiter.

ELLES SONT SORTIES DE LA CARTE D'ACCUEIL. Quatre blocs bleus pleins
empilés sous un paragraphe faisaient un mur ; posées à part, bordées et
terminées par un chevron, elles se lisent pour ce qu'elles sont — des
lignes sur lesquelles on appuie.
═══════════════════════════════════════════════════════════════════════════
*/
@Composable
private fun Suggestions(vm: AssistanceViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        listOf(
            R.string.assistance_ex_minimum,
            R.string.assistance_ex_delai,
            R.string.assistance_ex_reseau,
            R.string.assistance_ex_phrase,
            /*
            LA CINQUIÈME : COMMENT NOUS JOINDRE.

            Elle n'est pas là pour meubler. Quelqu'un qui doute d'un
            message reçu — « le support VaultEx vous contacte » — n'a
            aucun moyen simple de vérifier quels sont les vrais canaux.
            Cette ligne les lui donne en un geste, depuis l'application
            elle-même, qui est la seule source qu'un escroc ne contrôle
            pas.
            */
            R.string.assistance_ex_equipe
        ).forEach { id ->
            val texte = stringResource(id)
            val forme = RoundedCornerShape(12.dp)
            Row(
                Modifier.fillMaxWidth()
                    .clip(forme)
                    .border(1.dp, BorderColor, forme)
                    .clickable { vm.poser(texte) }
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(texte, fontSize = 12.5.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Default.ChevronRight, null,
                    tint = AccentBlue, modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

/*
LES BULLES.

Le coin de la pointe est rogné du côté de celui qui parle : c'est ce qui
distingue les deux voix même quand une réponse est courte et tient sur la
même largeur que la question.

LA LARGEUR MAXIMALE SUIT L'ÉCRAN. 300 dp fixes débordaient d'un petit
téléphone et flottaient sur un grand ; 78 % de la largeur laisse toujours
voir de quel côté la bulle est posée.
*/
@Composable
private fun Bulle(m: MessageAssistance) {
    val maxLargeur = (LocalConfiguration.current.screenWidthDp * 0.78f).dp
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (m.deMoi) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape =
                if (m.deMoi) RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
                else RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
            // Le message de l'utilisateur en bleu plein : c'est la couleur
            // de l'action dans toute l'application, et ici l'action c'est
            // d'avoir demandé. La réponse reste sur le fond des cartes.
            color = if (m.deMoi) AccentBlue else SurfaceColor,
            modifier = Modifier.widthIn(max = maxLargeur)
        ) {
            Text(
                m.texte,
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = if (m.deMoi) Color.White else TextPrimary
            )
        }
    }
}

/*
TROIS POINTS QUI RESPIRENT, DANS UNE BULLE.

Une barre de progression ronde et un « l'assistance réfléchit… » disaient
la même chose deux fois, et pas à la place où l'œil attend la réponse.
Les trois points sont posés là où la bulle va arriver : l'attente occupe
déjà sa forme.

LA PHRASE RESTE, POUR CEUX QUI N'ONT PAS L'IMAGE. Trois points ne disent
rien à un lecteur d'écran ; `assistance_en_cours` devient la description
de la bulle, et TalkBack annonce que l'assistance réfléchit.
*/
@Composable
private fun Reflexion() {
    val battement = rememberInfiniteTransition(label = "reflexion")
    val attente = stringResource(R.string.assistance_en_cours)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
            color = SurfaceColor,
            modifier = Modifier.semantics { contentDescription = attente }
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { i ->
                    val opacite by battement.animateFloat(
                        initialValue = 0.25f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(560, delayMillis = i * 160, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "point$i"
                    )
                    Box(
                        Modifier.size(7.dp).clip(CircleShape)
                            .background(TextSecondary.copy(alpha = opacite))
                    )
                }
            }
        }
    }
}

/*
═══════════════════════════════════════════════════════════════════════════
LA BARRE DE SAISIE
═══════════════════════════════════════════════════════════════════════════

Une pastille et un bouton rond, séparés de la conversation par un trait.
Le trait n'est pas décoratif : il dit que ce qui est en dessous ne défile
pas et reste atteignable.

LE BOUTON D'ENVOI EST PLEIN QUAND IL SERT, éteint quand il ne sert pas.
Une icône qui change seulement de teinte laisse appuyer dans le vide ;
un disque qui s'allume se voit du coin de l'œil.

ET LA TOUCHE « ENVOYER » DU CLAVIER ENVOIE. Sans ça, il faut sortir du
clavier pour viser un bouton — deux gestes pour une phrase.
═══════════════════════════════════════════════════════════════════════════
*/
@Composable
private fun Saisie(vm: AssistanceViewModel, valeur: String, peutEnvoyer: Boolean) {
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(BorderColor))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = SurfaceColor,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (valeur.isEmpty()) {
                        Text(
                            stringResource(R.string.assistance_placeholder),
                            fontSize = 13.sp, color = TextMuted
                        )
                    }
                    BasicTextField(
                        value = valeur,
                        onValueChange = vm::onSaisie,
                        textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp, lineHeight = 19.sp),
                        cursorBrush = SolidColor(AccentBlue),
                        maxLines = 5,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { if (peutEnvoyer) vm.envoyer() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(44.dp)
                    .clip(CircleShape)
                    .background(if (peutEnvoyer) AccentBlue else SurfaceLight)
                    .clickable(enabled = peutEnvoyer, onClick = vm::envoyer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Send,
                    stringResource(R.string.assistance_envoyer),
                    tint = if (peutEnvoyer) Color.White else TextMuted,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

/*
═══════════════════════════════════════════════════════════════════════════
LA DEMANDE DE NUMÉRO, AU TROISIÈME ÉCHANGE
═══════════════════════════════════════════════════════════════════════════

Elle remplace la barre de saisie au lieu de s'empiler au-dessus. Deux
raisons : on ne peut pas la rater, et elle n'ampute pas la conversation
qu'on vient de lire.

ELLE DIT À QUOI LE NUMÉRO SERT, et c'est la seule chose qui fait répondre.
« Pour mieux vous servir » ne fait répondre personne ; « pour que le
changeur puisse te rappeler si ta question doit lui être transmise » est
une raison vérifiable.

ET « CONTINUER SANS » RESTE. Un blocage dur serait payé par ceux qui
n'osent pas donner leur numéro — souvent les mêmes qui ont le plus besoin
d'aide.
═══════════════════════════════════════════════════════════════════════════
*/
@Composable
private fun DemandeNumero(vm: AssistanceViewModel, numero: String, valide: Boolean) {
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(BorderColor))
        Column(
            Modifier.fillMaxWidth().background(SurfaceColor).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text(
                stringResource(R.string.assistance_numero_titre),
                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary
            )
            Text(
                stringResource(R.string.assistance_numero_aide),
                fontSize = 12.sp, lineHeight = 17.sp, color = TextSecondary
            )
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BgPrimary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (numero.isEmpty()) {
                        Text("Ex. 70 12 34 56", fontSize = 14.sp, color = TextMuted)
                    }
                    BasicTextField(
                        value = numero,
                        onValueChange = vm::onNumero,
                        singleLine = true,
                        textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                        cursorBrush = SolidColor(AccentBlue),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { if (valide) vm.validerNumero() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = vm::ignorerNumero, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.assistance_numero_sans), color = TextSecondary)
                }
                Button(
                    onClick = vm::validerNumero,
                    enabled = valide,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.assistance_numero_valider))
                }
            }
        }
    }
}
