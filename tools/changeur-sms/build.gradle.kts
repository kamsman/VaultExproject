/*
═══════════════════════════════════════════════════════════════════════════
LE ROBOT SMS DU CHANGEUR
═══════════════════════════════════════════════════════════════════════════

AUCUNE DÉPENDANCE, et ce n'est pas de l'ascétisme.

Ni Compose, ni AndroidX, ni OkHttp, ni Gson. Les écrans sont construits
avec les vues du cadre Android, les requêtes avec HttpURLConnection, le
JSON avec org.json — qui est fourni par Android lui-même.

Trois raisons :

  UN APPAREIL, UNE SEULE INSTALLATION. Cette application tourne sur le
  téléphone du changeur et nulle part ailleurs. Rien ne justifie d'y
  tirer vingt mégaoctets de bibliothèques.

  UNE SURFACE D'ATTAQUE MINUSCULE. Elle porte un secret qui peut faire
  envoyer de la crypto. Moins de code tiers y tourne, mieux c'est.

  ELLE DOIT SE CONSTRUIRE DU PREMIER COUP. Chaque dépendance est une
  version à accorder, et l'auteur de ce fichier ne peut pas la compiler
  pour le vérifier.
═══════════════════════════════════════════════════════════════════════════
*/
/*
═══════════════════════════════════════════════════════════════════════════
OÙ DÉPOSER L'APK UNE FOIS CONSTRUIT
═══════════════════════════════════════════════════════════════════════════

L'APK sort au fond de `build/outputs/apk/release/`, et il faut aller l'y
chercher à chaque fois. Comme il finit toujours au même endroit — un
dossier qu'on ouvre pour le transférer sur le téléphone — autant l'y
déposer tout seul.

LE CHEMIN N'EST PAS DANS CE FICHIER. Il est propre à une machine, et ce
fichier est versionné : l'écrire ici obligerait tout le monde à le
corriger, ou pire, ferait écrire sur un bureau qui n'existe pas.

Il se règle dans `tools/changeur-sms/local.properties`, qui est ignoré
par git :

    apk.sortie=C:/Users/HP/Desktop/app changeur

─── LES DEUX ÉCRITURES DU CHEMIN FONCTIONNENT ───────────────────────────

    apk.sortie=C:/Users/HP/Desktop/app changeur
    apk.sortie=C:\\Users\\HP\\Desktop\\app changeur

La ligne est lue telle qu'elle est écrite, sans passer par
`java.util.Properties` — qui traiterait la contre-oblique comme un
échappement et transformerait « C:\\Users » en « C:Users » sans rien dire.

La tâche imprime quand même le chemin où elle écrit : c'est ce qui fait
voir un chemin faux tout de suite, au lieu d'un dossier fantôme quelque
part.
═══════════════════════════════════════════════════════════════════════════
*/
val sortieApk: String? = run {
    val fichier = rootProject.file("local.properties")
    /*
    ON LIT LA LIGNE À LA MAIN, ET NON AVEC `java.util.Properties`.

    Deux raisons, et la seconde vaut mieux que la première.

    D'ABORD PARCE QUE `java.util` EST IRRÉSOLVABLE ICI. Dans un
    `build.gradle.kts`, `java` désigne l'extension Java du projet, qui
    masque le paquet du même nom. La compilation du script échoue sur
    « Unresolved reference: util » — mesuré.

    ENSUITE, ET SURTOUT, PARCE QUE `Properties` ABÎMERAIT LE CHEMIN.
    Il traite la contre-oblique comme un échappement : « C:\Users »
    deviendrait « C:Users », silencieusement. En lisant la ligne telle
    qu'elle est écrite, les deux écritures fonctionnent — avec des
    barres obliques comme avec des contre-obliques — et le piège
    disparaît au lieu d'être documenté.
    */
    val depuisFichier = if (fichier.exists()) {
        fichier.readLines()
            .firstOrNull { it.trim().startsWith("apk.sortie") && it.contains('=') }
            ?.substringAfter('=')
            ?.trim()
    } else null
    // `-Papk.sortie=…` l'emporte : c'est le moyen de dévier une
    // construction ponctuelle sans toucher aux réglages de la machine.
    (project.findProperty("apk.sortie") as String? ?: depuisFichier)?.trim()?.ifEmpty { null }
}

plugins {
    id("com.android.application") version "8.5.0"
    id("org.jetbrains.kotlin.android") version "1.9.22"
}

android {
    namespace = "com.vaultex.changeur"
    /*
    LES MÊMES SDK QUE VAULTEX, parce qu'ils sont déjà installés sur la
    machine qui construit. Demander un compileSdk différent déclencherait
    un téléchargement, voire un échec si l'installation est hors ligne.
    */
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vaultex.changeur"
        /*
        VINGT-QUATRE, et non vingt-six comme VaultEx.

        Le téléphone du changeur n'est pas forcément récent, et rien ici
        n'a besoin d'une API moderne : la lecture d'un SMS entrant existe
        depuis l'API 19.
        */
        minSdk = 24
        /*
        TRENTE-QUATRE, ET PAS TRENTE-SIX COMME VAULTEX.

        À partir de targetSdk 35, Android impose le bord-à-bord : le
        contenu passe sous la barre d'état, et une mise en page qui ne s'y
        prépare pas se fait rogner son titre. VaultEx s'en occupe ; cette
        application-ci, faite de vues nues sans thème, non.

        Rien ne l'y oblige : la règle de targetSdk minimum est une règle du
        Play Store, et cet APK n'y passera jamais — il s'installe à la main
        sur un téléphone.
        */
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        /*
        PAS DE MINIFICATION, MÊME EN RELEASE.

        R8 n'apporterait rien — il n'y a pas de dépendance à réduire — et
        il ajoute une occasion de casser silencieusement un nom de classe
        de receveur déclaré dans le manifeste.
        */
        release {
            isMinifyEnabled = false
            /*
            ═══════════════════════════════════════════════════════════════
            SIGNÉ AVEC LA CLÉ DE DEBUG, ET CE N'EST PAS UN RACCOURCI
            ═══════════════════════════════════════════════════════════════

            Sans configuration de signature, `assembleRelease` produit un
            APK NON SIGNÉ, qu'Android refuse d'installer. On ne s'en
            aperçoit qu'une fois le fichier transféré sur le téléphone.

            La clé de debug convient ici : cet APK ne passera jamais par le
            Play Store — il s'installe à la main, sur un appareil, et il n'a
            pas de mise à jour à faire correspondre à une signature d'auteur.

            ─── CE QU'ON VIENT CHERCHER EN RELEASE ───────────────────────

            `debuggable = false`, et c'est TOUT l'intérêt.

            Un APK de debug est débogable par construction : quiconque
            branche un câble sur ce téléphone peut lire les préférences de
            l'application — donc le jeton. Or ce jeton permet d'affirmer au
            relais qu'un paiement a eu lieu, et donc de faire confirmer des
            achats jamais payés.

            Pour un APK qu'on installe une fois et qui reste en place des
            mois sur le téléphone qui encaisse, ça change tout.
            ═══════════════════════════════════════════════════════════════
            */
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    /*
    LE LINT NE DOIT PAS BLOQUER LA CONSTRUCTION.

    `assembleRelease` déclenche `lintVitalRelease`, qui échoue sur des
    avertissements dont aucun ne compte ici — une icône prise dans les
    ressources du cadre, l'absence de thème, un libellé écrit en clair
    dans le manifeste. Tous assumés.

    Ce qui compte vraiment, le compilateur le dira de toute façon.
    */
    lint {
        abortOnError = false
    }
}

/*
LA COPIE, APRÈS CHAQUE CONSTRUCTION.

`doLast` et non une tâche séparée : il n'y a rien à faire si l'APK n'a pas
été produit, et une tâche de copie qu'on oublie de lancer ne vaut pas
mieux que d'aller chercher le fichier à la main.

Le dossier de sortie est capturé ICI, à la configuration, et non lu depuis
`project` pendant l'exécution — c'est ce que demande le cache de
configuration de Gradle, et ça évite une dépréciation de plus à chaque
version.
*/
if (sortieApk != null) {
    val dossierApk = layout.buildDirectory.dir("outputs/apk")
    val destination = File(sortieApk)

    listOf("assembleDebug", "assembleRelease").forEach { nom ->
        tasks.matching { it.name == nom }.configureEach {
            val variante = if (nom.endsWith("Release")) "release" else "debug"
            doLast {
                val source = File(dossierApk.get().asFile, variante)
                val apk = source.listFiles { f -> f.name.endsWith(".apk") }?.firstOrNull()
                if (apk == null) {
                    logger.lifecycle("changeur-sms : aucun APK trouve dans $source")
                    return@doLast
                }
                if (!destination.exists() && !destination.mkdirs()) {
                    logger.lifecycle("changeur-sms : impossible de creer $destination")
                    return@doLast
                }
                /*
                UN NOM STABLE, pour que la reinstallation ecrase la
                precedente au lieu d'empiler des fichiers dont on ne sait
                plus lequel est le dernier. La variante y figure : installer
                un debug en croyant poser un release serait invisible, et
                c'est precisement la difference qui compte ici.
                */
                val cible = File(destination, "VaultEx-Changeur-$variante.apk")
                apk.copyTo(cible, overwrite = true)
                logger.lifecycle("changeur-sms : APK depose dans $cible")
            }
        }
    }
}
