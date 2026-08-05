// Top-level build file

/*
─── VERSION DE R8 FORCÉE ──────────────────────────────────────────────────
AGP 8.5.0 embarque une version de R8 qui plante en `ConcurrentModificationException`
sur ce projet, pendant `minifyReleaseWithR8` — un bug interne du minifieur,
pas une erreur du code applicatif. Le build release échouait donc toujours.

Google documente ce contournement : on surcharge la dépendance R8 du
classpath de build par une version corrigée, sans toucher à AGP.
Voir https://developer.android.com/build/shrink-code#r8-version

8.5.35 est le dernier correctif de la ligne 8.5, celle qu'AGP 8.5.0 attend :
on récupère les corrections de bugs sans changer de comportement.
───────────────────────────────────────────────────────────────────────────
 */
buildscript {
    repositories {
        maven { url = uri("https://storage.googleapis.com/r8-releases/raw") }
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools:r8:8.5.35")
    }
}

plugins {
    id("com.android.application") version "8.5.0" apply false
    id("com.android.library") version "8.5.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.google.dagger.hilt.android") version "2.51" apply false
    id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22" apply false
    id("com.google.gms.google-services") version "4.4.1" apply false
    id("com.google.firebase.crashlytics") version "2.9.9" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}

/*
─── VÉRIFICATION DES DÉPENDANCES ──────────────────────────────────────────
Cette tâche force la résolution de TOUTES les configurations du projet.

Elle existe pour une raison précise : la commande officielle de génération
des empreintes,

    ./gradlew --write-verification-metadata sha256 help

n'enregistre que les dépendances effectivement téléchargées pendant son
exécution. Or `help` n'en résout AUCUNE. Résultat classique : un fichier
verification-metadata.xml quasi vide, puis un build qui échoue au premier
artefact non listé. On lance donc la génération sur CETTE tâche :

    ./gradlew --write-verification-metadata sha256 resolveAllDependencies

Voir VERIFICATION-DEPENDANCES.md pour la procédure complète.
───────────────────────────────────────────────────────────────────────────
 */
tasks.register("resolveAllDependencies") {
    group = "verification"
    description = "Résout toutes les configurations — sert à générer les empreintes de dépendances."
    doLast {
        allprojects {
            configurations
                // Une configuration non résolvable (ex. `implementation`, qui
                // n'est qu'un conteneur de déclarations) lèverait une exception.
                .filter { it.isCanBeResolved }
                .forEach { config ->
                    // Une configuration peut échouer pour des raisons légitimes
                    // (variante absente pour la plateforme courante) : on ignore
                    // l'échec plutôt que d'interrompre toute la génération.
                    runCatching { config.resolve() }
                }
        }
    }
}
