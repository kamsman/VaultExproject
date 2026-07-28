pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
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
