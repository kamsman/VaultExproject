pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

/*
─── VERSION DE R8 FORCÉE ──────────────────────────────────────────────────
Le R8 embarqué par AGP 8.5.0 plante en `java.util.ConcurrentModificationException`
pendant `minifyReleaseWithR8` : un bug interne du minifieur, pas une erreur du
code — la compilation Kotlin passe entièrement, c'est l'étape suivante qui
échoue. Aucun APK release ne pouvait être produit.

Ce qui a été essayé sans succès :
  · exclure le déclarateur de service BlockHound (il a disparu de lui-même
    avec web3j 4.8.8, qui ne tire plus Netty — le plantage persiste) ;
  · `android.enableR8.fullMode=false` : le bug se produit aussi en mode compat.

La surcharge DOIT être dans CE fichier et non dans build.gradle.kts : AGP étant
appliqué via le bloc `plugins`, il est chargé par le classloader du script de
settings. Une entrée de classpath déclarée dans le projet racine reste sans
effet — vérifié, la première tentative n'avait rien changé.

Et ce bloc doit venir APRÈS `pluginManagement`, qui doit être la toute première
instruction du fichier. Gradle refuse l'ordre inverse :
    Unexpected `buildscript` block found.
    `buildscript` can not appear before `pluginManagement`.

Voir https://developer.android.com/build/shrink-code#r8-version
───────────────────────────────────────────────────────────────────────────
 */
buildscript {
    repositories {
        maven { url = uri("https://storage.googleapis.com/r8-releases/raw") }
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools:r8:8.7.18")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // JitPack RETIRÉ : aucune dépendance du projet n'en provient, et c'est
        // une surface d'attaque inutile — JitPack compile à la demande n'importe
        // quel dépôt GitHub, et les artefacts produits n'ont pas d'empreinte
        // stable, ce qui entre en conflit direct avec la vérification des
        // dépendances. Si un jour une bibliothèque n'existe QUE sur JitPack,
        // la rajouter en la limitant à son groupe :
        //   maven {
        //       url = uri("https://jitpack.io")
        //       content { includeGroupByRegex("com\\.github\\..*") }
        //   }
    }
}

rootProject.name = "VaultEx"
include(":app")
