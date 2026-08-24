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
        "trongrid.key"         to "soldes TRX/USDT-TRC20 souvent illisibles"
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

android {
    namespace = "com.vaultex"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.vaultex"
        minSdk = 26
        targetSdk = 34
        versionCode = versionCodeCalcule
        versionName = "1.0.$versionCodeCalcule"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        buildConfigField("String", "VAULTEX_FEE_RECIPIENT_EVM","\"0xe97c1d479648106fdeab414a298bf89d97563f48\"")
        buildConfigField("String", "VAULTEX_FEE_RECIPIENT_TRX","\"TLMb88tu3HWTdQCtV4uSbgKJcpEBsR2mUj\"")
        buildConfigField("String", "VAULTEX_FEE_RECIPIENT_BTC","\"bc1qjjt4wq46z6kftzantvckhfn03svyu6rwswwqq0\"")
        buildConfigField("String", "VAULTEX_FEE_RECIPIENT_SOL","\"7wXTW4DH9PMtmY4zvwNxKa58xxhBUy1tG4BpfZveC3PD\"")
        buildConfigField("double", "VAULTEX_FEE_PERCENT","1.5")
        // Frais de service DIRECT sur les envois (BTC + SOL uniquement : sortie/
        // instruction dans la même tx → coût quasi nul). 0.5% plafonné à 0.50 USD.
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
