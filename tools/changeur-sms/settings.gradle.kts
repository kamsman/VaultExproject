/*
═══════════════════════════════════════════════════════════════════════════
UN PROJET À PART, ET C'EST VOLONTAIRE
═══════════════════════════════════════════════════════════════════════════

Ce dossier n'est PAS un module de VaultEx. Il a son propre
settings.gradle.kts, donc Gradle ne le configure jamais quand on construit
l'application principale.

POURQUOI. Un module déclaré dans le settings.gradle.kts racine est
configuré à chaque build : une faute dans son fichier de build ferait
échouer `:app:assembleDebug` aussi. Cette application-ci n'est installée
que sur UN téléphone, celui du changeur ; il n'y a aucune raison qu'elle
puisse empêcher les mille autres de recevoir une mise à jour.

Elle emploie les MÊMES versions d'AGP et de Kotlin que la racine : elles
sont déjà dans le cache Gradle, donc rien de plus à télécharger.

─── CONSTRUIRE ──────────────────────────────────────────────────────────

Depuis la racine du dépôt :

    ./gradlew -p tools/changeur-sms assembleDebug

L'APK sort dans tools/changeur-sms/build/outputs/apk/debug/.

Sous Windows : .\gradlew.bat -p tools\changeur-sms assembleDebug
═══════════════════════════════════════════════════════════════════════════
*/
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "changeur-sms"
