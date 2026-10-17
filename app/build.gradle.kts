import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

/*
──────────────────────────────────────────────────────────────────────────
ALERTE CLÉS MANQUANTES

`local.properties` est gitignoré, et il est aussi exclu des archives du
projet — c'est voulu, les secrets ne doivent pas circuler. Mais ça a un
effet de bord vicieux : quand le projet est ré-extrait dans un dossier
neuf, le fichier n'existe pas et Android Studio en régénère un vierge, avec
seulement `sdk.dir`. Toutes les clés disparaissent SANS AUCUNE ERREUR.

Le build réussit (chaque clé absente vaut chaîne vide, et le code sait s'en
passer), l'app démarre, et les pannes qui suivent ne ressemblent pas du
tout à leur cause : historique ETH vide, swap impossible, plus aucun
message de diagnostic Telegram. On cherche alors un bug dans le code
pendant des jours pour un fichier de configuration effacé.

D'où cet avertissement en tête de build : la panne se nomme elle-même.
Volontairement NON bloquant — compiler sans clés reste légitime.
──────────────────────────────────────────────────────────────────────────
*/
run {
    val expected = mapOf(
        "changenow.key"        to "swap impossible",
        "etherscan.key"        to "historique ETH/BNB vide",
        "telegram.admin.token" to "aucun diagnostic a distance",
        "coingecko.key"        to "ecran Marche limite en debit",
        "trongrid.key"         to "soldes TRX/USDT-TRC20 souvent illisibles",
        "oneinch.key"          to "pas de devis d'echange sur place"
    )
    val missing = expected.filter { (key, _) -> localProps.getProperty(key).isNullOrBlank() }
    if (missing.isNotEmpty()) {
        logger.warn("")
        logger.warn("+-- VaultEx : cles absentes de local.properties ".padEnd(72, '-'))
        missing.forEach { (key, effet) -> logger.warn("|  $key".padEnd(28) + "-> $effet") }
        logger.warn("|  Modele complet : local.properties.example")
        logger.warn("+".padEnd(72, '-'))
        logger.warn("")
    }
}

// ─── Signature RELEASE ────────────────────────────────────────────────────
// Identifiants lus depuis keystore.properties (gitignoré) OU variables
// d'environnement (CI). Aucun secret n'est versionné. Si le keystore est absent
// (machine sans les secrets, CI de test…), la release se construit NON signée
// au lieu d'échouer — voir keystore.properties.example.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) load(f.inputStream())
}
fun signingValue(prop: String, env: String): String? =
    keystoreProps.getProperty(prop) ?: System.getenv(env)

val releaseStorePath = signingValue("storeFile", "VAULTEX_STORE_FILE")
val hasReleaseKeystore = releaseStorePath != null && rootProject.file(releaseStorePath).exists()

/*
══════════════════════════════════════════════════════════════════════════
NUMÉRO DE VERSION — CALCULÉ, JAMAIS SAISI À LA MAIN
══════════════════════════════════════════════════════════════════════════

`versionCode` valait 1 depuis le premier jour. Tous les APK jamais produits
portaient donc le MÊME numéro.

Or c'est ce numéro, et lui seul, qui dit à Android qu'un paquet est plus
récent que celui déjà installé. Face à un numéro identique, beaucoup
d'installateurs — ceux de Xiaomi, Samsung et Huawei en particulier, très
répandus sur le marché visé — affichent « installé » SANS rien remplacer.
Le testeur croit avoir mis à jour, l'ancien code continue de tourner, et
aucune correction ne se voit. Désinstaller supprime la comparaison : d'où
le remède empirique trouvé par les testeurs, « désinstaller puis
réinstaller », qui masquait la vraie cause.

Le numéro est donc DÉRIVÉ DU NOMBRE DE COMMITS. Il augmente tout seul à
chaque validation, sans rien à penser — et une discipline qu'on doit
penser est une discipline qu'on oublie, surtout à la fin d'une longue
séance de corrections.

`versionName` reprend ce numéro (« 1.0.365 ») pour qu'il soit LISIBLE dans
l'application. Sans ça, impossible de savoir à distance quelle version un
testeur fait réellement tourner : c'est la question qu'on se pose d'abord
quand quelqu'un dit « ça n'a rien changé chez moi ».

REPLI. Sans dépôt git (archive, machine sans git), le calcul échoue. On
n'invente alors PAS un numéro : un numéro trop bas serait refusé par
Android, un numéro trop haut interdirait toutes les mises à jour futures.
Le build s'arrête en nommant la solution — renseigner version.code dans
local.properties, qui prend de toute façon le pas quand il est présent.
══════════════════════════════════════════════════════════════════════════
*/
val versionCodeCalcule: Int = run {
    val force = localProps.getProperty("version.code")?.trim()?.toIntOrNull()
    if (force != null && force > 0) return@run force
    val depuisGit = try {
        val p = ProcessBuilder("git", "rev-list", "--count", "HEAD")
            .directory(rootProject.projectDir)
            .redirectErrorStream(true)
            .start()
        val sortie = p.inputStream.bufferedReader().readText().trim()
        p.waitFor()
        sortie.toIntOrNull()?.takeIf { it > 0 }
    } catch (_: Exception) { null }
    depuisGit ?: throw GradleException(
        "Impossible de calculer versionCode : ni dépôt git utilisable, ni " +
            "version.code dans local.properties.\n" +
            "Ajoute une ligne « version.code=N » (N strictement supérieur au " +
            "numéro du dernier APK distribué), sinon Android refusera la mise " +
            "à jour ou l'appliquera sans rien remplacer."
    )
}

/*
══════════════════════════════════════════════════════════════════════════
LE NUMÉRO DE VERSION NE DISAIT PAS QUEL CODE TOURNE
══════════════════════════════════════════════════════════════════════════

`version.code` dans local.properties prend le pas sur le calcul git, et
c'est nécessaire — sans lui, un numéro trop bas ferait refuser la mise à
jour par Android. Mais dès qu'il est épinglé à la main, le numéro cesse
d'avoir le moindre rapport avec le code embarqué : on peut compiler
trois commits différents sous « 1.0.653 ».

Ce n'est pas théorique. La question « est-ce que ce build contient le
correctif ? » est revenue à chaque séance, et ni le testeur ni personne
ne pouvait y répondre : « v1.0.596 », « v1.0.598 », « v1.0.603 » ne
prouvaient rien. Plusieurs allers-retours ont été dépensés à comparer des
captures d'écran pour deviner ce qu'un identifiant de sept caractères
aurait dit tout de suite.

LE SUFFIXE VOYAGE TOUT SEUL. AdminBot joint déjà VERSION_NAME à chaque
alerte et à chaque crash, et CrashReporter l'enregistre comme clé. Rien
d'autre n'est à modifier : toutes les remontées nomment désormais le
commit exact.

    💥 Crash VaultEx v1.0.653+f663fca

LE POINT D'EXCLAMATION COMPTE AUTANT QUE LE HACHAGE. Un APK compilé avec
des modifications non validées ne correspond à AUCUN commit — le relire
plus tard est impossible, et c'est précisément le build qu'on distribue
par erreur en fin de séance. « 1.0.653+f663fca! » le dit.

On ne regarde que les fichiers SUIVIS : les fichiers non suivis sont du
brouillon (sorties de build, notes), et les faire compter mettrait le
point d'exclamation en permanence, donc le rendrait invisible.

REPLI. Sans git utilisable, le suffixe est simplement absent — on ne
casse pas une compilation pour un confort de diagnostic, et un numéro
sans suffixe se reconnaît au premier coup d'œil.
══════════════════════════════════════════════════════════════════════════
*/
val suffixeCommit: String = run {
    fun git(vararg args: String): String? = try {
        val p = ProcessBuilder(listOf("git") + args)
            .directory(rootProject.projectDir)
            .redirectErrorStream(true)
            .start()
        val sortie = p.inputStream.bufferedReader().readText().trim()
        if (p.waitFor() == 0) sortie else null
    } catch (_: Exception) { null }

    val court = git("rev-parse", "--short=7", "HEAD")?.takeIf { it.isNotBlank() }
        ?: return@run ""
    val modifie = !git("status", "--porcelain", "--untracked-files=no").isNullOrBlank()
    "+$court" + if (modifie) "!" else ""
}

android {
    namespace = "com.vaultex"

    /*
    ═══════════════════════════════════════════════════════════════════════
    ANDROID 16 (API 36) — EXIGÉ PAR GOOGLE PLAY
    ═══════════════════════════════════════════════════════════════════════

    Depuis le 31 août 2026, toute NOUVELLE application doit cibler API 36
    pour être acceptée. Ce n'est pas un rejet après examen : c'est un refus
    au téléversement du fichier. Avec targetSdk 34, VaultEx ne pouvait tout
    simplement pas être envoyée.

    compileSdk 36 dit « je compile avec les API d'Android 16 » ; targetSdk 36
    dit « applique-moi les comportements d'Android 16 ». Les deux vont
    ensemble : compiler contre 36 sans le cibler laisserait le système nous
    traiter en application ancienne, ce que Play refuse.

    minSdk reste à 26 (Android 8). Une grande part du parc visé en Afrique de
    l'Ouest tourne encore sur Android 8 à 10 : le remonter exclurait des
    utilisateurs réels pour ne rien gagner.

    CE QUE CE CHANGEMENT ENTRAÎNE VRAIMENT — l'edge-to-edge.

    À partir d'API 35, Android impose que le contenu passe SOUS la barre
    d'état et la barre de navigation, et c'est à l'application de gérer ses
    marges. Sur API 36, on ne peut plus s'y soustraire.

    Vérifié écran par écran avant de basculer :
      · 43 écrans utilisent Scaffold + TopAppBar — Material applique les
        marges lui-même, rien à faire ;
      · 1 écran a une barre de titre maison (Swap) — traité au commit 1ca6e0f ;
      · 9 écrans n'ont pas de Scaffold (démarrage, code PIN, intégration) —
        tous à contenu centré, que les barres système ne recouvrent pas.

    La barre de navigation flottante et les feuilles modales portaient déjà
    navigationBarsPadding().
    */
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vaultex"
        minSdk = 26
        targetSdk = 36
        versionCode = versionCodeCalcule
        // Le suffixe est vide quand git n'est pas disponible : « 1.0.653 »,
        // exactement comme avant.
        versionName = "1.0.$versionCodeCalcule$suffixeCommit"
        // Aussi seul, pour qui a besoin du hachage sans le numéro.
        buildConfigField("String", "GIT_COMMIT", "\"${suffixeCommit.removePrefix("+")}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        /*
        Adresses de perception. SEULE celle du Bitcoin est utilisée à ce jour
        — SendCryptoUseCase est le seul appelant, et il ne lit que BTC. Les
        trois autres sont déclarées et lues nulle part.

        On les garde : ce sont des adresses de perception, qu'on ne remplace
        pas à la légère, et le jour où la commission d'envoi s'étend elles
        devront être exactes. Mais on l'écrit, faute de quoi leur présence
        laisse croire qu'une commission est déjà prélevée sur ces chaînes —
        c'est déjà arrivé, deux commentaires plus bas.
        */
        buildConfigField("String", "VAULTEX_FEE_RECIPIENT_EVM","\"0xe97c1d479648106fdeab414a298bf89d97563f48\"")
        buildConfigField("String", "VAULTEX_FEE_RECIPIENT_TRX","\"TLMb88tu3HWTdQCtV4uSbgKJcpEBsR2mUj\"")
        buildConfigField("String", "VAULTEX_FEE_RECIPIENT_BTC","\"bc1qjjt4wq46z6kftzantvckhfn03svyu6rwswwqq0\"")
        buildConfigField("String", "VAULTEX_FEE_RECIPIENT_SOL","\"7wXTW4DH9PMtmY4zvwNxKa58xxhBUy1tG4BpfZveC3PD\"")
        /*
        Commission d'ÉCHANGE annoncée. Attention : ce n'est PAS elle qui
        s'applique — la commission réellement pratiquée appartient au
        fournisseur en service (FournisseurSwap.commissionPourcent : 0,4 %
        chez ChangeNOW, réglable chez SimpleSwap), et c'est celle-là que
        l'écran de confirmation affiche.

        Deux nombres pour une même commission avaient déjà divergé :
        l'historique enregistrait ce 1,5 % pendant que l'écran en annonçait
        un autre pour le même échange. Corrigé dans SwapUseCase.recordSwap,
        qui lit désormais le fournisseur.
        */
        buildConfigField("double", "VAULTEX_FEE_PERCENT","1.5")
        /*
        PLUS AUCUNE COMMISSION D'ENVOI N'EST PRÉLEVÉE. Ces deux valeurs ne
        sont plus lues : SendViewModel.serviceFeeCrypto rend zéro pour toute
        chaîne et tout montant.

        Elles décrivaient la commission Bitcoin — 0,5 % plafonnés à 0,50 $,
        la seule que l'application ait jamais prise sur un envoi. Elle est
        retirée parce qu'aucune phrase simple n'était vraie tant qu'elle
        existait : « envoi gratuit » devenait faux pour ceux qui déplacent
        les plus gros montants.

        On les garde pour que le rétablissement soit un choix, pas une
        réécriture. Le commentaire qui les accompagnait annonçait « BTC +
        SOL » alors que le code n'a jamais prélevé que sur BTC — un
        commentaire faux sur des FRAIS n'est pas une coquille, c'est
        l'endroit d'où partent les annonces et les réponses au support.
        */
        buildConfigField("double", "VAULTEX_SEND_FEE_PERCENT","0.5")
        buildConfigField("double", "VAULTEX_SEND_FEE_CAP_USD","0.5")
        // Certificate pinning (P1) — activer en release UNE FOIS les empreintes
        // SHA-256 réelles renseignées dans NetworkModule.CERT_PINS.
        buildConfigField("boolean", "ENABLE_CERT_PINNING","false")
        /*
        ─── SECRETS ───────────────────────────────────────────────────────
        AUCUNE valeur par défaut ici. Ces champs finissent dans BuildConfig,
        donc dans le DEX de l'APK : R8 obfusque les NOMS, jamais le CONTENU
        des chaînes. Une clé écrite ici est lisible par un simple `strings`
        sur l'APK, par n'importe qui.

        Tout se renseigne dans local.properties (gitignoré, jamais versionné).
        Voir local.properties.example pour la liste complète.

        Une clé absente = chaîne vide : le code sait déjà s'en passer
        (en-tête non ajouté, monitoring Telegram désactivé). Le build ne
        casse jamais, la fonctionnalité concernée est simplement inactive.
        ───────────────────────────────────────────────────────────────────
         */
        fun secret(key: String): String = localProps.getProperty(key, "")

        buildConfigField("String", "ETHERSCAN_KEY",   "\"${secret("etherscan.key")}\"")
        buildConfigField("String", "BSCSCAN_KEY",     "\"${secret("bscscan.key")}\"")
        buildConfigField("String", "CHANGENOW_KEY",   "\"${secret("changenow.key")}\"")
        /*
        ─── SIMPLESWAP : CLE, COMMISSION, INTERRUPTEUR ─────────────────────
        ChangeNOW verse 0,4 % au partenaire, sans plus. SimpleSwap laisse
        fixer ce taux entre 0,4 et 5 % SUR LA CLE : la commission est prise
        DANS l'echange, sans seconde transaction — la seule facon d'atteindre
        1,5 % sans payer des frais reseau superieurs a ce qu'on encaisse.

        `swap.provider` choisit le fournisseur. Il vaut desormais
        « simpleswap », et c'est une decision prise, pas un defaut hérité :
        SimpleSwap permet de REGLER la commission entre 0,4 et 5 % depuis
        l'espace partenaire, ce que ChangeNOW ne permet pas. ChangeNOW reste
        en place, entier, et redevient actif d'une ligne : swap.provider=changenow.

        LE DEFAUT VIT ICI, PAS DANS UN FICHIER NON VERSIONNE. Tant qu'il
        valait « changenow », la decision d'utiliser SimpleSwap n'existait
        que dans le local.properties d'une seule machine : une compilation
        ailleurs, ou ce fichier perdu, et l'application repartait en silence
        chez l'autre echangeur, avec une autre commission. Ecrite ici, elle
        survit au poste de travail.

        SANS CLE SIMPLESWAP, ON RETOMBE SUR CHANGENOW. Voir SwapModule : une
        machine sans local.properties n'a pas de cle, et SimpleSwap sans cle
        refuse tout. Mieux vaut un echangeur moins avantageux qu'un ecran
        d'echange mort.

        `simpleswap.commission` ne PRELEVE rien — la cle s'en charge. Il ne
        sert qu'a AFFICHER le bon chiffre, la ou l'ecran annoncait 1,5 %
        quand rien n'etait pris.

        IL DOIT SUIVRE LA CLE EN SERVICE, ET RIEN NE LE VERIFIE. L'espace
        partenaire porte plusieurs cles a des taux differents — une a 0,4 %
        pour les essais, une a 1,5 % en production. Changer de cle sans
        changer ce nombre fait annoncer a l'utilisateur une commission qui
        n'est pas celle qu'on prend : l'ecran d'echange l'affiche en toutes
        lettres, « Frais : 0,015 USDT (1,50 %) ».

        Deux endroits detiennent la meme verite et aucun ne confronte
        l'autre. C'est le genre d'ecart qu'on ne decouvre que le jour ou
        quelqu'un compte.
        */
        buildConfigField("String", "SIMPLESWAP_KEY",  "\"${secret("simpleswap.key")}\"")
        /*
        1inch — devis d'echange SUR PLACE (meme chaine), etape 1.
        Sans cle, le devis est simplement absent et le courtier garde la
        main : c'est le comportement d'aujourd'hui, inchange.
        Cle gratuite sur https://portal.1inch.dev
        */
        buildConfigField("String", "ONEINCH_KEY",     "\"${secret("oneinch.key")}\"")
        /*
        PLAFOND D'ESSAI DE L'ECHANGE SUR PLACE, EN DOLLARS (2 = 2 $).

        Il etait compare a un NOMBRE DE JETONS, ce qui coincidait pour
        l'USDT et valait plus de mille dollars pour le BNB. Voir
        EchangeSurPlaceUseCase : la valeur en dollars se lit desormais du
        cote dollar de la paire, qui existe toujours.

        Ce chemin SIGNE et DEPENSE, et un appel de contrat rate ne se
        rembourse pas — la ou un depot mal parti chez un courtier se
        rattrape. Tant qu'il n'a pas tourne des dizaines de fois en
        production, une erreur doit couter le prix d'un pain.

        On le leve quand on a des raisons de le lever, pas quand on est
        presse : echange.surplace.max dans local.properties.
        */
        buildConfigField(
            "double", "ECHANGE_SUR_PLACE_MAX_USD",
            localProps.getProperty("echange.surplace.max", "2.0")
        )
        buildConfigField("String", "SWAP_PROVIDER",   "\"${localProps.getProperty("swap.provider", "simpleswap")}\"")
        buildConfigField("double", "SIMPLESWAP_COMMISSION", localProps.getProperty("simpleswap.commission", "1.5"))
        /*
        ─── TAUX FIXE OU FLOTTANT ──────────────────────────────────────────

        FLOTTANT (false) : meilleur taux, mais le montant recu peut bouger
        entre le devis et l'execution. Si le marche tourne pendant que le
        depot voyage, l'utilisateur recoit plus — ou moins — que le chiffre
        qu'on lui a montre.

        FIXE (true) : SimpleSwap bloque le taux quelques minutes et se paie
        cette garantie. Le montant affiche est celui qui arrive.

        ON PREND LE FIXE PAR DEFAUT, et c'est une decision de produit, pas
        une optimisation. Sur un reseau lent, face a des gens qui decouvrent
        la crypto, « tu recois exactement ce qui est ecrit » vaut plus que
        quelques dixiemes de pour cent. Un montant qui change apres coup ne
        se lit pas « le marche a bouge » : il se lit « on m'a pris quelque
        chose ».

        REVENIR EN ARRIERE EST D'UNE LIGNE : simpleswap.taux.fixe=false.

        CE REGLAGE DOIT ETRE LE MEME POUR LES TROIS APPELS — devis, bornes,
        creation. Devisier en fixe et creer en flottant afficherait un
        montant et en livrerait un autre ; et les minimums ne sont pas les
        memes dans les deux modes. FournisseurSimpleSwap n'en garde donc
        qu'une seule copie.
        */
        buildConfigField(
            "boolean", "SIMPLESWAP_TAUX_FIXE",
            localProps.getProperty("simpleswap.taux.fixe", "true")
        )
        /*
        ─── INTERRUPTEUR DE TEST : MANQUE DE FRAIS ─────────────────────────
        Simule l'absence de monnaie native pour payer le gaz, afin de pouvoir
        essayer le bouton de deblocage sans avoir a vider un portefeuille.

        Creer la situation pour de vrai est impraticable : on ne peut pas
        descendre a zero — envoyer son BNB ailleurs coute du BNB — et a
        quelques centimes pres le resultat depend du prix du gaz a l'instant
        du test, pas du code. Un essai dont l'issue varie avec le reseau ne
        prouve rien.

        Cette cle ne simule QUE la condition de declenchement. Les soldes
        restent reels, les appels au fournisseur aussi, et l'echange propose
        partirait pour de bon s'il etait confirme.

        Elle ne peut pas atteindre la production : elle n'existe que dans
        local.properties, jamais versionne, et sa lecture est encadree par
        BuildConfig.DEBUG cote application.
        ───────────────────────────────────────────────────────────────────
        */
        buildConfigField(
            "boolean", "FORCE_GAS_SHORTFALL",
            localProps.getProperty("debug.force.gas.shortfall", "false")
        )
        buildConfigField("String", "FLUTTERWAVE_KEY", "\"${secret("flutterwave.key")}\"")
        // Optionnelle — améliore les limites de débit TronGrid (header TRON-PRO-API-KEY)
        buildConfigField("String", "TRONGRID_KEY",    "\"${secret("trongrid.key")}\"")
        // Optionnelle — clé CoinGecko Demo (gratuite) : supprime quasiment le
        // rate-limit du Marché. https://www.coingecko.com/en/developers/dashboard
        buildConfigField("String", "COINGECKO_KEY",   "\"${secret("coingecko.key")}\"")
        /*
        ───────────────────────────────────────────────────────────────────
        RELAIS DE COURS — l'adresse du Worker Cloudflare
        ───────────────────────────────────────────────────────────────────

        Le quota gratuit de CoinGecko vaut pour la CLÉ, donc pour toute
        l'application réunie : 10 000 appels par mois. À un millier par
        téléphone, une dizaine d'appareils l'épuisent, et l'API répond alors
        429 à tout le monde en même temps. Aucune optimisation embarquée ne
        peut lever ce plafond, puisque le coût croît avec le nombre
        d'installations.

        Le relais pose la question une fois et sert la réponse à tous. Voir
        tools/relais-cours/worker.js.

        VIDE = comportement d'avant : appel direct à CoinGecko. Le réglage
        est donc sans risque à laisser vide, et une compilation sur une
        machine qui l'ignore reste fonctionnelle.

        Format attendu, barre oblique finale COMPRISE :
          prix.relay.url=https://vaultex-prix.<compte>.workers.dev/api/v3/
        ───────────────────────────────────────────────────────────────────
         */
        buildConfigField("String", "PRICE_RELAY_URL", "\"${secret("prix.relay.url")}\"")
        // Play Integrity : numéro de projet Google Cloud (Console > Paramètres du projet).
        // 0 = désactivé. Renseigner play.integrity.project dans local.properties pour activer.
        buildConfigField("long", "PLAY_INTEGRITY_PROJECT", "${localProps.getProperty("play.integrity.project", "0")}L")
        // Bot Telegram d'administration (groupe « Vaultex Administration ») :
        // événements temps réel — wallet créé, swap, gros swap, échec.
        // Absent = monitoring simplement désactivé (AdminBot sort si vide).
        buildConfigField("String", "TG_ADMIN_TOKEN", "\"${secret("telegram.admin.token")}\"")
        // ID NÉGATIF = le supergroupe « Vaultex Administration » (un id positif
        // enverrait en chat privé). Récupéré via getUpdates après ajout du bot.
        buildConfigField("String", "TG_ADMIN_CHAT",  "\"${secret("telegram.admin.chat")}\"")
        // Empreinte SHA-256 du certificat de signature RELEASE. Vide = contrôle
        // anti-repackaging inactif (builds de dev). À renseigner une fois la
        // première release signée :
        //   keytool -printcert -jarfile app-release.apk
        // Voir AppIntegrity.kt pour ce que ce contrôle empêche exactement.
        buildConfigField("String", "APP_SIGNATURE_SHA256", "\"${secret("app.signature.sha256")}\"")
    }

    signingConfigs {
        // Créée UNIQUEMENT si le keystore est présent → les builds sans secrets
        // (debug, CI de test) ne cassent pas.
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(releaseStorePath!!)
                storePassword = signingValue("storePassword", "VAULTEX_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "VAULTEX_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "VAULTEX_KEY_PASSWORD")
                // Signatures v1+v2+v3 : compatibilité large + intégrité APK.
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            /*
             * Certificate pinning — actif par defaut, desactivable pour DIAGNOSTIC.
             *
             * Le pinning est la seule difference reseau entre debug et release.
             * Si une empreinte de CERT_PINS ne correspond plus au certificat
             * reel du serveur, OkHttp rejette la connexion : l'appel echoue en
             * SSLPeerUnverifiedException, silencieusement du point de vue de
             * l'utilisateur. Symptomes typiques — soldes a 0,00, prix absents,
             * historique vide — alors que la meme version debug fonctionne.
             *
             * Les certificats tournent (Let's Encrypt : tous les 60 a 90 jours).
             * Une empreinte de feuille non renouvelee casse donc l'app en
             * production sans qu'aucun code n'ait change. Epingler l'AC
             * intermediaire plutot que la feuille evite ce piege.
             *
             * Pour tester : `cert.pinning=false` dans local.properties, puis
             * reconstruire. Si les soldes reapparaissent, ce sont les empreintes
             * qui sont en cause — pas R8, pas le reseau.
             *
             * NE PAS distribuer une version avec cert.pinning=false : le pinning
             * protege contre l'interception TLS, un risque reel sur les reseaux
             * Wi-Fi publics et les appareils dont le magasin de certificats a
             * ete altere.
             */
            buildConfigField(
                "boolean", "ENABLE_CERT_PINNING",
                localProps.getProperty("cert.pinning", "true")
            )
            // Signature applied uniquement si le keystore est disponible.
            signingConfig = if (hasReleaseKeystore) signingConfigs.getByName("release") else null
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            // Désactivé en debug pour ne pas bloquer les tests (proxy/Charles)
            buildConfigField("boolean", "ENABLE_CERT_PINNING", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
            // Doublons apportés par les jars netty (transitifs de web3j)
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"
            excludes += "/META-INF/native-image/**"
            excludes += "/META-INF/versions/**"
            // BlockHound : outil de diagnostic Project Reactor, côté SERVEUR.
            // Arrive ici par les jars netty transitifs de web3j et ne sert
            // strictement à rien sur Android. Sa déclaration de service faisait
            // planter R8 (ConcurrentModificationException) au moment de la
            // minification release, avec l'avertissement :
            //   "Unexpected reference to missing service class:
            //    META-INF/services/reactor.blockhound.integration.BlockHoundIntegration"
            excludes += "/META-INF/services/reactor.blockhound.*"
        }
    }
}

dependencies {

    // ANDROIDX
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // COMPOSE
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    // Material 2 — utilisé uniquement pour le pull-to-refresh
    implementation("androidx.compose.material:material")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // HILT
    implementation("com.google.dagger:hilt-android:2.51")
    ksp("com.google.dagger:hilt-compiler:2.51")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // ROOM
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // SQLCipher — chiffrement de la base Room (C-03)
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")

    // SECURITY
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.biometric:biometric:1.1.0")

    // CRYPTO (PROPRE)
    /*
     * web3j — 4.8.8-android.
     *
     * La version 4.9.8-android declaree auparavant N'EXISTE SUR AUCUN DEPOT
     * (404 sur Maven Central ; seules 4.8.7, 4.8.8 et 4.12.3 sont publiees en
     * variante -android). Le projet ne compilait que sur une machine ayant
     * deja l'artefact en cache local — un clone neuf echouait a la resolution.
     *
     * 4.12.3 est distribuee en AAR dont le JAR est ABSENT (404) : Gradle la
     * resout sans jamais poser les classes sur le classpath, et TOUS les
     * imports web3j deviennent introuvables. 4.8.8 et toute sa fermeture
     * (abi, crypto, rlp, utils, tuples) sont en JAR — verifie artefact par
     * artefact. C'est donc la version utilisable la plus recente.
     *
     * Repartition des classes, pour eviter les mauvaises reductions :
     *   org.web3j:crypto -> Bip32ECKeyPair, Credentials, ECKeyPair,
     *                       MnemonicUtils, RawTransaction, Sign,
     *                       TransactionEncoder
     *   org.web3j:utils  -> Numeric ET org.web3j.crypto.Hash
     * `crypto` seul ne suffit donc pas, malgre son nom.
     *
     * Bonus : 4.8.8 ne tire pas Netty, contrairement a la 4.9.x. Le
     * declarateur de service BlockHound qui faisait planter R8 disparait donc
     * de lui-meme.
     */
    implementation("org.web3j:core:4.8.8-android")

    /*
     * BouncyCastle — declare EXPLICITEMENT, et c'est important.
     *
     * Ed25519Utils (signature Solana) importe
     * org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters et
     * org.bouncycastle.crypto.signers.Ed25519Signer. Ces classes n'arrivaient
     * jusqu'ici que par la fermeture transitive de web3j : le bloc
     * `configurations.all` plus bas exclut bcprov-jdk15on et bcprov-jdk15to18
     * (celui de bitcoinj) sans rien declarer en echange.
     *
     * Autrement dit, la signature Solana dependait d'un artefact que personne
     * n'avait choisi, dont la version suivait les mises a jour de web3j, et
     * qui disparaissait des qu'on touchait a cette dependance. Pour la brique
     * cryptographique d'un portefeuille, c'est inacceptable.
     *
     * jdk18on est la denomination courante ; elle n'est pas visee par les
     * exclusions, qui ciblent les anciennes (jdk15on, jdk15to18).
     */
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("org.bitcoinj:bitcoinj-core:0.16.2")

    // ❌ SUPPRIMÉ (IMPORTANT)
    // BIP39
    // BIP44
    // BOUNCYCASTLE MANUEL

    // NETWORK
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")

    // COROUTINES
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // UI / QR
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("com.google.zxing:core:3.5.3")

    // CameraX — scanner QR
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    // WorkManager — vérification des alertes de prix en arrière-plan
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")
    implementation("io.github.g00fy2.quickie:quickie-bundled:1.10.0")
    // ROOT DETECTION
    implementation("com.scottyab:rootbeer-lib:0.1.0")
    // DEVICE INTEGRITY — Play Integrity API (successeur de SafetyNet)
    implementation("com.google.android.play:integrity:1.4.0")

    // FIREBASE
    implementation(platform("com.google.firebase:firebase-bom:32.7.4"))
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")

    // TEST
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.10")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

configurations.all {
    // Une seule implementation BouncyCastle : celle declaree explicitement
    // (bcprov-jdk18on). On ecarte les anciennes denominations, apportees
    // transitivement par bitcoinj et web3j, qui provoqueraient des classes
    // dupliquees au packaging.
    exclude(group = "org.bouncycastle", module = "bcprov-jdk15on")
    exclude(group = "org.bouncycastle", module = "bcprov-jdk15to18")

    // ─── Transport JVM inutile sur Android, apporte par web3j:core ─────────
    // jnr-unixsocket sert aux sockets Unix (IPC avec un noeud local) et
    // Java-WebSocket au transport WebSocket de web3j. Le projet n'utilise ni
    // l'un ni l'autre : tous les appels RPC passent par son propre client
    // Retrofit/OkHttp, et seules les classes org.web3j.crypto.* et
    // org.web3j.utils.* sont importees.
    //
    // Ces jars declenchaient aussi l'avertissement du build :
    //   "Component com.github.jnr:jffi:1.2.17 maps to multiple files"
    // et alourdissaient inutilement l'entree de R8.
    exclude(group = "com.github.jnr")
    exclude(group = "org.java-websocket", module = "Java-WebSocket")
}
/**
 * Garde-fou de compilation des ressources.
 *
 * Une apostrophe non échappée dans un `<string>` passe la validation XML sans
 * broncher, puis casse `mergeDebugResources` avec un message qui ne nomme ni le
 * fichier, ni la ligne, ni la règle :
 *
 *     Can not extract resource from com.android.aaptcompiler.ParsedResource@...
 *
 * Ce contrôle s'exécute AVANT AAPT et nomme précisément le fautif. Il est
 * volontairement non bloquant si Python est absent : il ne doit jamais empêcher
 * quelqu'un de compiler, seulement lui faire gagner l'itération perdue.
 */
val checkStringResources by tasks.registering {
    group = "verification"
    description = "Détecte les apostrophes et guillemets non échappés dans les strings.xml"

    val script = rootProject.file("tools/check_strings.py")
    val resDir = file("src/main/res")
    inputs.files(fileTree(resDir) { include("values*/strings.xml") })
    inputs.file(script)
    outputs.upToDateWhen { false }

    doLast {
        if (!script.exists()) return@doLast

        // ProcessBuilder plutôt qu'une API Gradle : `project.exec` est déprécié
        // avec le cache de configuration, et `providers.exec` est une API de
        // phase de configuration qu'on ne veut pas appeler dans un doLast.
        val proc = try {
            ProcessBuilder("python3", script.absolutePath)
                .redirectErrorStream(true)
                .start()
        } catch (e: java.io.IOException) {
            // Python absent : on n'a rien vérifié, mais on ne bloque personne.
            logger.lifecycle("check_strings : python3 introuvable, contrôle ignoré.")
            return@doLast
        }

        val output = proc.inputStream.bufferedReader().readText()
        val code = proc.waitFor()

        if (code != 0) {
            // En échec on affiche TOUT : la règle violée est sur la ligne
            // suivant « ERREUR », la filtrer perdrait l'explication.
            logger.error(output)
            throw GradleException(
                "strings.xml invalide — voir les lignes ERREUR ci-dessus. " +
                    "Détail complet : python3 tools/check_strings.py"
            )
        }
    }
}

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Resources") }
    .configureEach { dependsOn(checkStringResources) }

/**
 * Références mortes dans le code de test.
 *
 * Le code de test se compile dans une tâche SÉPARÉE du code principal. Une
 * constante supprimée du code principal mais encore appelée par un test ne
 * casse donc RIEN pendant des mois : `assembleDebug` réussit, l'APK sort. Puis
 * un jour une tâche touche `compileDebugUnitTestKotlin` et le build échoue sur
 * un symbole disparu depuis longtemps, sans rapport avec le travail en cours.
 *
 * C'est exactement ce qui est arrivé avec `MOBILE_MONEY_FEE_PERCENT`, retirée
 * avec l'écran Mobile Money et encore référencée par SwapUseCaseFeeTest.
 *
 * Ce contrôle attache la vérification à la compilation du code PRINCIPAL, donc
 * elle tourne même quand on ne construit pas les tests.
 */
val checkTestRefs by tasks.registering {
    group = "verification"
    description = "Détecte les constantes appelées par les tests mais supprimées du code principal"

    val script = rootProject.file("tools/check_test_refs.py")
    inputs.files(fileTree(file("src/main/java")) { include("**/*.kt") })
    inputs.files(fileTree(file("src/test/java")) { include("**/*.kt") })
    inputs.file(script)
    outputs.upToDateWhen { false }

    doLast {
        if (!script.exists()) return@doLast
        val proc = try {
            ProcessBuilder("python3", script.absolutePath)
                .redirectErrorStream(true)
                .start()
        } catch (e: java.io.IOException) {
            logger.lifecycle("check_test_refs : python3 introuvable, contrôle ignoré.")
            return@doLast
        }
        val output = proc.inputStream.bufferedReader().readText()
        if (proc.waitFor() != 0) {
            logger.error(output)
            throw GradleException(
                "Le code de test appelle des symboles supprimés — voir ci-dessus. " +
                    "Détail : python3 tools/check_test_refs.py"
            )
        }
    }
}

tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }
    .configureEach { dependsOn(checkTestRefs) }

/*
═══════════════════════════════════════════════════════════════════════════
DÉPOSER L'APK LÀ OÙ ON VA LE CHERCHER
═══════════════════════════════════════════════════════════════════════════

Même mécanisme que pour le robot du changeur, et pour la même raison :
l'APK sort au fond de `app/build/outputs/apk/`, et il finit toujours dans
le dossier qu'on ouvre pour l'installer ou l'envoyer.

LE CHEMIN N'EST PAS DANS CE FICHIER VERSIONNÉ. Il se règle dans
`local.properties`, qui est ignoré par git :

    apk.sortie=C:/Users/HP/Desktop/app vaultex

─── LA LIGNE EST LUE À LA MAIN, ET PAS AVEC `localProps` ────────────────

Ce fichier charge déjà `local.properties` dans un `Properties` (plus
haut), et il serait naturel d'y piocher cette clé-là aussi. On ne le fait
pas, pour une raison précise : `Properties` traite la contre-oblique comme
un ÉCHAPPEMENT. « C:\Users\HP » y devient « C:UsersHP », silencieusement
— et l'APK atterrirait dans un dossier au nom absurde, créé sans erreur.

Ça ne gêne pas les clés que `localProps` sert déjà : un mot de passe, un
alias, un chemin de keystore relatif écrit avec des barres obliques. Ça
gêne exactement celle-ci, qui est un chemin Windows absolu tapé à la main.

Lue telle qu'elle est écrite, les deux écritures du chemin fonctionnent.

─── LA VARIANTE FIGURE DANS LE NOM, ET CE N'EST PAS COSMÉTIQUE ──────────

`VaultEx-release.apk` et `VaultEx-debug.apk`. Le debug porte un
`applicationIdSuffix = ".debug"` : les deux s'installent donc CÔTE À CÔTE
sur un téléphone, avec la même icône et le même nom. Distribuer l'un en
croyant distribuer l'autre ne se verrait nulle part — sauf que le debug
est débogable et non optimisé.
═══════════════════════════════════════════════════════════════════════════
*/
val sortieApkVaultex: String? = run {
    val fichier = rootProject.file("local.properties")
    val depuisFichier = if (fichier.exists()) {
        fichier.readLines()
            /*
            LA CLÉ EXACTE, ET PAS UN PRÉFIXE.

            C'était `startsWith("apk.sortie")`. Le jour où l'on écrit une
            seconde clé — `apk.sortie.robot`, par exemple — et qu'elle se
            trouve plus haut dans le fichier, c'est elle qui est lue. Rien
            ne le signale : l'APK part simplement ailleurs.
            */
            .firstOrNull { it.contains('=') && it.substringBefore('=').trim() == "apk.sortie" }
            ?.substringAfter('=')
            ?.trim()
    } else null
    (project.findProperty("apk.sortie") as String? ?: depuisFichier)?.trim()?.ifEmpty { null }
}

/*
═══════════════════════════════════════════════════════════════════════════
« JE N'ARRIVE PAS À LE METTRE DANS LE DOSSIER »
═══════════════════════════════════════════════════════════════════════════

Signalé sur une vraie machine, deux fois — une fois pour cet APK, une fois
pour celui du robot. Le dossier restait vide et la construction disait
BUILD SUCCESSFUL.

LA COPIE N'ÉCHOUAIT PAS : ELLE SE TAISAIT. Les deux seuls cas d'échec
étaient des `logger.lifecycle` suivis d'un `return@doLast`. Sur une sortie
Gradle de plusieurs centaines de lignes — et filtrée, dans la commande
qu'on donne à taper — une ligne d'information ne se voit pas. On lit
« BUILD SUCCESSFUL », on ouvre le dossier, il est vide, et il n'y a rien à
quoi se raccrocher.

Un dépôt qu'on a demandé et qui n'a pas eu lieu est un ÉCHEC. Il fait donc
échouer la tâche, avec la raison et le chemin complet.

─── LE PIÈGE DU BUREAU ONEDRIVE, QUI EST LE PLUS PROBABLE ──────────────

Sur une machine Windows où OneDrive est actif, le Bureau n'est pas
`C:\Users\HP\Desktop` mais `C:\Users\HP\OneDrive\Desktop`. Le premier
chemin n'existe parfois même plus.

Et `mkdirs()` crée toute la chaîne manquante, sans rien dire. L'APK
atterrissait donc dans un `C:\Users\HP\Desktop\app vaultex` tout neuf,
réel, sur le disque — et invisible sur le Bureau qu'on regarde.

On refuse maintenant de créer un dossier dont le PARENT n'existe pas. Un
dossier de dépôt se range à côté d'autres choses ; si son parent manque,
c'est le chemin qui est faux, pas le dossier qui manque.

─── ET UNE COMMANDE POUR VÉRIFIER SANS CONSTRUIRE ──────────────────────

`gradlew ouEstLApk` répond en quelques secondes au lieu de quelques
minutes. On ne découvre plus un chemin faux à la fin d'une compilation.
═══════════════════════════════════════════════════════════════════════════
*/
val AIDE_CHEMIN_APK = """
    Le chemin est faux. Pour le corriger :

      1. Demande ou l'APK devrait aller :   gradlew ouEstLApk
         Sans reglage, cette commande ECRIT LA LIGNE A COLLER, avec le
         Bureau reel de cette machine — OneDrive compris.

      2. Ouvre le fichier :   notepad local.properties
         (a la racine du depot) et colle la ligne A LA FIN.
         C'est une ligne DANS UN FICHIER, pas une commande a taper.

      3. Reverifie :   gradlew ouEstLApk
""".trimIndent()

/*
═══════════════════════════════════════════════════════════════════════════
RETROUVER LE BUREAU TOUT SEUL
═══════════════════════════════════════════════════════════════════════════

Parce que « mets ça sur ton Bureau » n'a pas de réponse unique sur
Windows. Avec OneDrive actif, le Bureau est `%OneDrive%\Desktop` ; sans
lui, `%USERPROFILE%\Desktop` ; sur une installation francophone ancienne,
`Bureau`. Les trois existent dans la nature, et on ne peut pas deviner
laquelle depuis un dépôt.

CE QUE ÇA ÉVITE EST CONCRET. Le chemin se tape à la main, une fois, et
une faute dedans ne se voit qu'à la fin d'une compilation — par un
dossier vide. Donner la ligne toute faite, avec le Bureau RÉEL de cette
machine-ci, supprime la frappe et la faute avec elle.

On ne devine rien : on ne retient que le premier candidat qui existe
VRAIMENT sur le disque. Si aucun n'existe, on se tait plutôt que de
proposer un chemin inventé.
═══════════════════════════════════════════════════════════════════════════
*/
fun bureauProbable(): File? {
    val profil = System.getenv("USERPROFILE") ?: System.getenv("HOME") ?: return null
    return listOfNotNull(
        System.getenv("OneDrive")?.let { File(it, "Desktop") },
        File(profil, "OneDrive/Desktop"),
        File(profil, "Desktop"),
        File(profil, "Bureau"),
    ).firstOrNull { it.isDirectory }
}

/** Le dossier de dépôt, prêt à recevoir — ou une erreur qui dit pourquoi. */
fun dossierDeDepot(chemin: String): File {
    val destination = File(chemin)
    if (destination.isDirectory) return destination
    if (destination.isFile) {
        throw GradleException(
            "vaultex : apk.sortie designe un FICHIER, pas un dossier — " +
                "${destination.absolutePath}\n\n$AIDE_CHEMIN_APK"
        )
    }
    val parent = destination.absoluteFile.parentFile
    if (parent != null && !parent.isDirectory) {
        throw GradleException(
            "vaultex : le dossier parent n'existe pas — ${parent.absolutePath}\n" +
                "Creer toute la chaine ferait atterrir l'APK dans un dossier reel " +
                "mais invisible la ou tu le cherches.\n\n$AIDE_CHEMIN_APK"
        )
    }
    if (!destination.mkdirs()) {
        throw GradleException(
            "vaultex : impossible de creer ${destination.absolutePath}\n\n$AIDE_CHEMIN_APK"
        )
    }
    return destination
}

/*
ELLE EXISTE MÊME SANS RÉGLAGE, et c'est volontaire : quand rien n'est
réglé, c'est précisément ce qu'il faut apprendre.
*/
tasks.register("ouEstLApk") {
    group = "vaultex"
    description = "Dit ou l'APK sera depose, sans rien construire."
    doLast {
        /*
        ON RECOPIE DANS UNE VARIABLE LOCALE AVANT DE TESTER.

        `sortieApkVaultex` est une propriété du script. Kotlin refuse le
        transtypage implicite d'une propriété à l'intérieur d'un lambda —
        il ne peut pas garantir qu'elle vaut encore la même chose. Une
        copie locale lève la question au lieu de la contourner.
        */
        val chemin = sortieApkVaultex
        if (chemin == null) {
            /*
            ON DONNE LA LIGNE TOUTE FAITE, AVEC LE VRAI BUREAU.

            Et on dit que c'est une LIGNE DANS UN FICHIER. Vu sur une vraie
            machine : l'exemple a ete tape dans PowerShell, qui a repondu
            « n'est pas reconnu comme nom d'applet de commande ». Un
            exemple de chemin, seul sur sa ligne, ressemble a une commande
            — et rien dans le message ne disait le contraire.
            */
            val bureau = bureauProbable()
            val propose = if (bureau != null) {
                File(bureau, "app vaultex").absolutePath.replace('\\', '/')
            } else {
                "C:/Users/HP/Desktop/app vaultex"
            }
            logger.lifecycle(
                "\nvaultex : aucun dossier de depot regle.\n" +
                    "L'APK restera dans app/build/outputs/apk/.\n\n" +
                    "POUR LE DEPOSER AILLEURS : ouvre le fichier local.properties, a la\n" +
                    "racine du depot, et AJOUTE CETTE LIGNE A LA FIN.\n" +
                    "Ce n'est pas une commande a taper dans le terminal :\n\n" +
                    "    apk.sortie=$propose\n\n" +
                    (if (bureau != null) "Ton Bureau a ete trouve ici : ${bureau.absolutePath}\n"
                    else "Le Bureau n'a pas ete trouve : remplace le chemin par le tien.\n") +
                    "\nPour ouvrir le fichier :  notepad local.properties\n" +
                    "Puis reviens verifier :   gradlew ouEstLApk\n"
            )
            return@doLast
        }
        val destination = File(chemin)
        logger.lifecycle("\nvaultex : apk.sortie = $chemin")
        logger.lifecycle("vaultex : chemin complet = ${destination.absolutePath}")
        logger.lifecycle(
            if (destination.isDirectory) {
                "vaultex : le dossier existe. L'APK y sera depose sous " +
                    "VaultEx-debug.apk ou VaultEx-release.apk.\n"
            } else {
                // On ne CRÉE rien ici : cette tâche observe, elle n'agit pas.
                val parent = destination.absoluteFile.parentFile
                if (parent != null && !parent.isDirectory) {
                    "vaultex : ATTENTION — le parent ${parent.absolutePath} n'existe pas.\n" +
                        "Le depot echouera.\n\n$AIDE_CHEMIN_APK\n"
                } else {
                    "vaultex : le dossier n'existe pas encore ; il sera cree au premier depot.\n"
                }
            }
        )

        /*
        ET CE QU'IL Y A DANS LE DOSSIER DE SORTIE, MAINTENANT.

        C'est la moitie de l'enquete, et elle manquait. « Le dossier reste
        vide » a deux causes opposees : soit l'APK n'a jamais ete
        construit, soit il l'a ete et n'a pas ete copie. Sans regarder
        app/build/outputs/apk, on ne peut pas trancher — et on cherche du
        mauvais cote.
        */
        listOf("debug", "release").forEach { variante ->
            val source = File(layout.buildDirectory.dir("outputs/apk").get().asFile, variante)
            val trouves = source.listFiles { f -> f.name.endsWith(".apk") }.orEmpty()
            logger.lifecycle(
                when {
                    !source.isDirectory ->
                        "vaultex : $variante — rien n'a encore ete construit " +
                            "(${source.absolutePath} n'existe pas)"
                    trouves.isEmpty() ->
                        "vaultex : $variante — dossier present mais AUCUN apk dedans"
                    else ->
                        "vaultex : $variante — construit : " +
                            trouves.joinToString { "${it.name} (${it.length() / 1024} Ko)" }
                }
            )
        }
        logger.lifecycle("")
    }
}

/*
═══════════════════════════════════════════════════════════════════════════
DÉPOSER SANS RECONSTRUIRE
═══════════════════════════════════════════════════════════════════════════

Parce que « le dossier reste vide » survit aux trois explications que j'ai
données, et qu'à chaque essai il faut attendre une compilation complète
pour apprendre qu'on s'est trompé.

Cette tâche ne construit rien : elle prend l'APK déjà présent dans
`app/build/outputs/apk/` et le copie. Elle répond en une seconde, et elle
sépare les deux questions — « est-ce que l'APK existe » et « est-ce que la
copie marche » — qu'`assembleDebug` mélangeait.

Si elle dépose et qu'`assembleDebug` ne dépose pas, le défaut est dans
l'accrochage de l'action à la tâche, pas dans la copie. Si elle ne dépose
pas non plus, elle dit pourquoi, tout de suite.
═══════════════════════════════════════════════════════════════════════════
*/
tasks.register("deposerApk") {
    group = "vaultex"
    description = "Copie l'APK deja construit vers apk.sortie, sans rien reconstruire."
    doLast {
        val chemin = sortieApkVaultex
            ?: throw GradleException(
                "vaultex : aucun dossier de depot regle. Lance d'abord : gradlew ouEstLApk"
            )
        val racineApk = layout.buildDirectory.dir("outputs/apk").get().asFile
        var deposes = 0
        listOf("debug", "release").forEach { variante ->
            val source = File(racineApk, variante)
            val apk = source.listFiles { f -> f.name.endsWith(".apk") }?.firstOrNull()
            if (apk == null) {
                logger.lifecycle("vaultex : rien a deposer pour $variante (${source.absolutePath})")
                return@forEach
            }
            val cible = File(dossierDeDepot(chemin), "VaultEx-$variante.apk")
            apk.copyTo(cible, overwrite = true)
            logger.lifecycle("vaultex : APK depose dans ${cible.absolutePath}")
            deposes += 1
        }
        if (deposes == 0) {
            throw GradleException(
                "vaultex : aucun APK a deposer. Construis-le d'abord :\n" +
                    "    gradlew assembleDebug"
            )
        }
    }
}

if (sortieApkVaultex != null) {
    // Hors lambda : ici le transtypage implicite est possible, et la copie
    // locale est ce que les lambdas plus bas pourront capturer.
    val cheminRegle: String = sortieApkVaultex
    val dossierApk = layout.buildDirectory.dir("outputs/apk")

    listOf("assembleDebug" to "debug", "assembleRelease" to "release").forEach { (nom, variante) ->
        tasks.matching { it.name == nom }.configureEach {
            doLast {
                val source = File(dossierApk.get().asFile, variante)
                val apk = source.listFiles { f -> f.name.endsWith(".apk") }?.firstOrNull()
                    ?: throw GradleException(
                        "vaultex : aucun APK trouve dans ${source.absolutePath}"
                    )
                /*
                ═══════════════════════════════════════════════════════════
                UNE RELEASE NON SIGNÉE NE DOIT PAS PORTER LE NOM D'UNE VRAIE
                ═══════════════════════════════════════════════════════════

                Sans keystore, ce projet construit la release NON SIGNÉE au
                lieu d'échouer — c'est un choix assumé, pour qu'une machine
                sans les secrets puisse quand même compiler.

                Mais le dépôt effaçait la trace de ce choix : Gradle écrit
                `app-release-unsigned.apk`, et on le recopiait en
                `VaultEx-release.apk`. Le seul indice du problème
                disparaissait dans le renommage.

                Un APK non signé ne s'installe pas. Distribué, il ne produit
                qu'un message d'erreur d'Android que personne ne rattache à
                sa cause — et on le découvre sur le téléphone de quelqu'un
                d'autre.

                Le nom le dit donc, en capitales, et le message aussi.
                ═══════════════════════════════════════════════════════════
                */
                val nonSigne = apk.name.contains("unsigned", ignoreCase = true)
                val destination = dossierDeDepot(cheminRegle)
                val cible = File(
                    destination,
                    if (nonSigne) "VaultEx-$variante-NON-SIGNE.apk"
                    else "VaultEx-$variante.apk"
                )
                apk.copyTo(cible, overwrite = true)
                logger.lifecycle("vaultex : APK depose dans ${cible.absolutePath}")
                if (nonSigne) {
                    logger.lifecycle("")
                    logger.lifecycle("vaultex : ATTENTION — CET APK N'EST PAS SIGNE.")
                    logger.lifecycle("vaultex : Il ne s'installera pas. Ne le distribue pas.")
                    logger.lifecycle("vaultex : Il manque keystore.properties a la racine")
                    logger.lifecycle("vaultex : du depot, ou le fichier .jks qu'il designe.")
                    logger.lifecycle("vaultex : Voir keystore.properties.example.")
                    logger.lifecycle("")
                }
            }
        }
    }
}
