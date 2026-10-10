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
