pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

/*
─── VERSION DE R8 : PLUS DE SURCHARGE ─────────────────────────────────────
Ce fichier forçait R8 8.7.18 par un bloc `buildscript`. C'était un
contournement pour AGP 8.5.0, dont le R8 embarqué plantait en
`java.util.ConcurrentModificationException` pendant `minifyReleaseWithR8` :
un bug interne du minifieur, pas une erreur du code — la compilation Kotlin
passait entièrement, c'est l'étape suivante qui échouait, et aucun APK
release ne pouvait sortir.

LA SURCHARGE EST DEVENUE LE PROBLÈME. Depuis la montée en AGP 8.13.2, la
compilation échouait sur :

    'R8Command$Builder.enableLegacyFullModeForKeepRules(boolean)'

AGP 8.13 appelle cette méthode sur R8 ; R8 8.7.18, figé ici, ne la possède
pas. Le contournement d'hier bloquait la version d'aujourd'hui.

AGP 8.13 embarque un R8 bien postérieur à 8.7.18 : la raison même de
l'épinglage a disparu. On laisse donc AGP choisir, comme prévu par défaut.

`android.enableR8.fullMode=false` reste dans gradle.properties pour l'instant.
C'était le second contournement du même bug ; il est probablement inutile lui
aussi, mais on ne change qu'une chose à la fois — sinon un échec ne dirait pas
laquelle des deux en est la cause. À retenter une fois cette compilation
passée.
───────────────────────────────────────────────────────────────────────────
 */

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
