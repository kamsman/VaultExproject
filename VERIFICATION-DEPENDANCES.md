# Vérification des dépendances — procédure

## Pourquoi

VaultEx compile aujourd'hui une soixantaine de bibliothèques tierces, et chacune
s'exécute avec **les mêmes droits que le code du portefeuille** : accès mémoire,
accès réseau, accès au stockage. Une bibliothèque compromise n'a pas besoin
d'exploiter une faille — elle *est* déjà à l'intérieur.

Sans vérification, Gradle télécharge ce que le dépôt lui sert et compile sans
poser de question. Si un compte de mainteneur est piraté, ou un miroir Maven
compromis, la version malveillante entre dans l'APK **en silence**. C'est
exactement le scénario qui s'est répété sur npm et PyPI.

Une fois la vérification active, Gradle compare l'empreinte SHA-256 de chaque
artefact téléchargé à celle enregistrée. La moindre différence **arrête le
build**.

---

## Génération (à faire sur ta machine)

> Ces commandes ont besoin du SDK Android et d'un accès réseau : elles ne
> peuvent pas être lancées depuis l'environnement de développement distant.

### 1. Partir d'un cache propre

C'est le point le plus important, et le plus souvent négligé. **Les empreintes
sont générées à partir de ce que tu as déjà téléchargé.** Si ton cache local
contient déjà un artefact corrompu, tu ne fais que graver le problème dans le
marbre. On repart donc de zéro :

```bash
rm -rf ~/.gradle/caches/modules-2
```

### 2. Générer les empreintes

```bash
./gradlew --write-verification-metadata sha256 resolveAllDependencies --refresh-dependencies
```

**Ne remplace pas `resolveAllDependencies` par `help`**, comme le montrent la
plupart des tutoriels : `help` ne résout aucune dépendance et produit un fichier
quasi vide, qui fait ensuite échouer le build au premier artefact inconnu. La
tâche `resolveAllDependencies` (définie dans `build.gradle.kts`) force la
résolution de toutes les configurations.

Cela crée `gradle/verification-metadata.xml`, plusieurs milliers de lignes.
**Ce fichier doit être versionné** : c'est lui la référence.

### 3. Compléter avec les variantes de build

La commande n'enregistre que ce qu'elle a téléchargé. Certaines dépendances
n'apparaissent qu'à la compilation réelle (processeurs d'annotations, variantes
release). On complète :

```bash
./gradlew --write-verification-metadata sha256 assembleDebug assembleRelease
```

### 4. Vérifier que ça tient

```bash
./gradlew clean assembleDebug
```

Si le build passe, la vérification est opérationnelle.

---

## Vivre avec, au quotidien

**À chaque ajout ou montée de version d'une dépendance, le build échouera** avec
un message du type :

```
Dependency verification failed for configuration ':app:debugRuntimeClasspath'
  - On artifact machin-1.2.3.jar: checksum is 'abc...' but no checksum is configured
```

Ce n'est pas une panne, **c'est le système qui fonctionne**. Il faut réenregistrer
l'empreinte :

```bash
./gradlew --write-verification-metadata sha256 resolveAllDependencies
```

Puis — et c'est le geste qui donne toute sa valeur au dispositif — **regarder le
diff Git** de `verification-metadata.xml` :

- une ligne ajoutée pour la bibliothèque que tu viens d'ajouter → normal ;
- une empreinte **modifiée** sur une bibliothèque que tu n'as pas touchée →
  **arrête tout**. Un artefact publié ne change jamais. Cela signifie soit un
  dépôt compromis, soit une réécriture de version, et dans les deux cas il faut
  enquêter avant de compiler.

Sans cette relecture, la vérification ne sert à rien : régénérer aveuglément
revient à signer tout ce qui passe.

---

## Ce que ça ne couvre pas

Sois lucide sur le périmètre :

- **Les composants du SDK Android** (build-tools, platforms) sont téléchargés par
  le gestionnaire de SDK, hors de Gradle. Non couverts.
- **Gradle lui-même.** Le wrapper télécharge `gradle-8.7-bin.zip` sans vérifier
  quoi que ce soit. Récupère l'empreinte officielle sur
  <https://gradle.org/release-checksums/> (colonne *Binary-only ZIP checksum*,
  ligne 8.7) et ajoute-la dans `gradle/wrapper/gradle-wrapper.properties` :

  ```properties
  distributionSha256Sum=<empreinte copiée depuis gradle.org>
  ```

  Copie-la depuis le site, ne la recopie pas d'un tutoriel : une valeur erronée
  bloque tout téléchargement du wrapper.
- **Une bibliothèque légitime mais malveillante dès l'origine.** La vérification
  garantit que tu reçois *exactement* ce que le mainteneur a publié — pas que ce
  qu'il a publié soit honnête. La seule parade reste de limiter le nombre de
  dépendances et de préférer celles qui sont largement auditées.

---

## Renforcement optionnel : les signatures PGP

`sha256` fige les artefacts tels qu'ils existent aujourd'hui. La vérification
des **signatures PGP** va plus loin : elle atteste que l'artefact vient bien du
mainteneur.

```bash
./gradlew --write-verification-metadata pgp,sha256 resolveAllDependencies
```

En pratique, toutes les bibliothèques ne sont pas signées, et il faut alors
maintenir une liste d'exceptions. À réserver pour plus tard — **commence par
sha256**, qui apporte l'essentiel de la protection pour une fraction de l'effort.
