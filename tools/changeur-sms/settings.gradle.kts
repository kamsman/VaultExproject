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

/*
IMPORTANT — CE BLOC VIENT APRÈS `pluginManagement`, ET PAS AVANT.

Gradle exige que `pluginManagement` soit le PREMIER code du script : une
instruction placée au-dessus fait échouer l'évaluation des réglages avec
« pluginManagement {} block must be the first code in the script ». Les
commentaires, eux, peuvent précéder.

Ça ne change rien à son effet : le SDK est cherché quand le greffon
Android s'applique, c'est-à-dire bien après que ce fichier a fini d'être lu.
*/
/*
═══════════════════════════════════════════════════════════════════════════
RETROUVER LE SDK ANDROID TOUT SEUL
═══════════════════════════════════════════════════════════════════════════

MESURÉ à la première compilation :

    SDK location not found. Define a valid SDK location with an
    ANDROID_HOME environment variable or by setting the sdk.dir path in
    'tools/changeur-sms/local.properties'

C'est la conséquence directe du choix d'isoler ce projet : il a sa propre
racine, donc Gradle cherche `local.properties` ICI, et pas celui du dépôt
— lequel contient déjà le chemin du SDK, puisque VaultEx se construit.

Demander au changeur de créer un second fichier serait une étape de plus
à chaque machine, et une étape qu'on oublie. On recopie donc la ligne
depuis la racine, la première fois seulement.

─── UNE SEULE LIGNE, ET C'EST ESSENTIEL ─────────────────────────────────

`local.properties` à la racine contient AUSSI le mot de passe du magasin
de signature et des clés d'API. Copier le fichier entier les répandrait
dans un second endroit, et il suffirait d'un `.gitignore` incomplet un
jour pour qu'ils partent sur GitHub.

On ne copie que `sdk.dir`. Rien d'autre n'est lu, rien d'autre n'est
écrit.

─── ON NE FAIT RIEN SI CE N'EST PAS NÉCESSAIRE ──────────────────────────

Un fichier déjà présent n'est jamais écrasé — quelqu'un a pu y mettre un
autre SDK exprès. Et si ANDROID_HOME est réglé, l'outillage le trouvera
sans nous.
═══════════════════════════════════════════════════════════════════════════
*/
run {
    val ici = File(rootDir, "local.properties")
    val racine = File(rootDir, "../../local.properties")
    val viaEnvironnement = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")

    if (!ici.exists() && viaEnvironnement.isNullOrBlank() && racine.exists()) {
        val ligne = racine.readLines().firstOrNull { it.trim().startsWith("sdk.dir") }
        if (ligne != null) {
            ici.writeText(
                "# Genere par settings.gradle.kts : recopie de sdk.dir depuis la\n" +
                    "# racine du depot. Ce fichier est ignore par git. Supprime-le\n" +
                    "# pour qu'il soit regenere, ou remplace-le par le tien.\n" +
                    ligne + "\n"
            )
            println("changeur-sms : sdk.dir recopie depuis la racine du depot.")
        }
    }
}

rootProject.name = "changeur-sms"
