# VaultEx Changeur — le robot SMS

Une application qui tourne sur **un seul téléphone**, celui du changeur, et
qui transmet au relais les SMS d'encaissement de l'opérateur.

## À quoi ça sert

Une **vente** se vérifie sur une chaîne publique : le relais lit le
transfert lui-même, et personne n'a besoin de croire le client.

Un **achat**, non. Le client paie par Orange Money, et aucune chaîne ne
porte ça. Jusqu'ici le changeur n'avait que la parole du client : un faux
reçu, une référence recopiée, et il envoyait de la crypto irréversible
contre un paiement qui n'avait jamais eu lieu.

Or l'**opérateur envoie un SMS** à chaque encaissement. C'est une
affirmation d'Orange, pas du client. Ce robot la transmet au relais, qui la
rapproche de la demande.

```
Orange ──SMS──▶ téléphone du changeur ──▶ relais ──▶ message Telegram
                     (ce robot)          (analyse)      ✅ ou ❌
```

Le client ne touche à rien de cette chaîne. C'est l'inverse exact du
dispositif qu'elle remplace, où la preuve venait de celui qui avait intérêt
à mentir.

## Ce que ça ne prouve pas

**Un identifiant d'expéditeur de SMS se falsifie.** Quelqu'un qui saurait
envoyer un SMS en se faisant passer pour « OrangeMoney » pourrait fabriquer
un faux encaissement.

La règle du changeur ne bouge donc pas, et le message Telegram la répète à
chaque fois : **il n'envoie qu'après avoir vu l'argent sur son propre
compte.** Ce qui change, c'est qu'un faux reçu du client ne suffit plus, et
qu'un paiement réel est reconnu tout seul.

## La vie privée, et où est la frontière

**Seuls les SMS des expéditeurs listés dans l'écran quittent le
téléphone.** Ce filtre est dans le robot, pas dans le relais — un filtre
côté serveur arriverait trop tard, le message serait déjà parti.

L'application demande **`RECEIVE_SMS` et pas `READ_SMS`** : elle voit les
messages qui arrivent, jamais la boîte de réception. Rien de l'historique
n'est lisible, même pour elle.

Ce qui part vers le relais, pour un SMS accepté : le texte et
l'identifiant de l'expéditeur. Le relais en extrait un montant, un numéro
et une référence, et garde ça trois heures.

## Construire

**Une seule commande, et c'est `assembleRelease`.** Depuis la racine du
dépôt :

```bash
./gradlew -p tools/changeur-sms assembleRelease
```

Sous Windows :

```powershell
.\gradlew.bat -p tools\changeur-sms assembleRelease
```

L'APK sort dans `tools/changeur-sms/build/outputs/apk/release/`.

### Pourquoi `assembleDebug` n'est pas une variante acceptable

Cette page donnait `assembleDebug` comme commande principale, et la mise en
garde venait quatorze lignes plus bas. C'est l'ordre inverse de celui qu'il
faut : on copie la première commande qu'on voit.

**La différence qui compte est `debuggable`.** Un APK de debug est
débogable par construction : quiconque branche un câble sur ce téléphone
peut lire les préférences de l'application, donc le **jeton**. Or ce jeton
permet d'affirmer au relais qu'un paiement a eu lieu — et donc de faire
confirmer des achats jamais payés.

Pour un APK qu'on installe une fois et qui reste en place des mois sur le
téléphone qui encaisse, ça n'est pas un détail. Et la faute ne se voit pas :
les deux APK s'installent, s'ouvrent et transmettent les SMS exactement
pareil. Rien sur l'écran ne distingue le mauvais du bon.

`assembleDebug` reste utile pour **vérifier que ça compile**, sur sa propre
machine, sans rien installer nulle part. L'APK sort alors dans
`tools/changeur-sms/build/outputs/apk/debug/` — et il n'a rien à faire sur
le téléphone du changeur.

### La signature

L'APK release est signé avec la clé de debug, ce qui suffit : il ne passera
jamais par le Play Store et s'installe à la main. Sans configuration de
signature, `assembleRelease` produirait un APK **non signé**, qu'Android
refuse d'installer — et on ne s'en apercevrait qu'une fois le fichier
transféré sur le téléphone.

**Conséquence pour les mises à jour :** cette clé vit dans
`~/.android/debug.keystore`, propre à la machine qui construit. Un APK
reconstruit sur un AUTRE ordinateur ne s'installera pas par-dessus — il
faudra désinstaller d'abord, donc **reposer l'adresse, le jeton et la liste
des expéditeurs**. Reconstruire toujours depuis la même machine évite ça.

### Si la compilation dit « SDK location not found »

C'est la conséquence directe de l'isolement du projet : ayant sa propre
racine, Gradle cherche `local.properties` **ici** et non à la racine du
dépôt — où le chemin du SDK est pourtant déjà réglé, puisque VaultEx se
construit.

`settings.gradle.kts` le règle tout seul à la première compilation : il
recopie **la seule ligne `sdk.dir`** depuis la racine. Rien d'autre n'est
lu — `local.properties` contient aussi le mot de passe du magasin de
signature et des clés d'API, qui n'ont aucune raison d'exister en double.

Si ça ne suffit pas, règle `ANDROID_HOME`, ou crée toi-même
`tools/changeur-sms/local.properties` avec une seule ligne :

```properties
sdk.dir=C:\\Users\\<toi>\\AppData\\Local\\Android\\Sdk
```

Ce fichier est ignoré par git, à ce niveau comme à la racine.

### Déposer l'APK directement dans un dossier

Pour ne plus aller le chercher au fond de `build/outputs/`, ajoute une
ligne dans le **même** `tools/changeur-sms/local.properties` :

```properties
apk.sortie=C:/Users/HP/Desktop/app changeur
```

Chaque `assembleDebug` ou `assembleRelease` y dépose alors l'APK sous un
nom stable — `VaultEx-Changeur-release.apk` — de sorte qu'une
réinstallation écrase la précédente au lieu d'empiler des fichiers dont on
ne sait plus lequel est le dernier.

> **Les deux écritures fonctionnent**, avec des barres obliques comme avec
> des contre-obliques :
>
> ```properties
> apk.sortie=C:/Users/HP/Desktop/app changeur
> apk.sortie=C:\Users\HP\Desktop\app changeur
> ```
>
> La ligne est lue telle qu'elle est écrite, sans passer par
> `java.util.Properties` — qui traiterait la contre-oblique comme un
> échappement et transformerait `C:\Users` en `C:Users` sans rien dire.
> Et dans un `build.gradle.kts`, `java` désigne de toute façon l'extension
> Java du projet : `java.util` n'y est même pas résolvable.
>
> La tâche imprime quand même le chemin où elle écrit : c'est ce qui fait
> voir un chemin faux tout de suite, au lieu d'un dossier fantôme.

Pour une construction ponctuelle ailleurs, sans toucher aux réglages :

```powershell
.\gradlew.bat -p tools\changeur-sms assembleRelease "-Papk.sortie=D:/ailleurs"
```

> Ce dossier a son **propre** `settings.gradle.kts` : il n'est pas un module
> de VaultEx, et une faute ici ne peut pas faire échouer la compilation de
> l'application principale. C'est voulu — cette application ne tourne que
> sur un téléphone, il n'y a aucune raison qu'elle puisse empêcher les
> autres de recevoir une mise à jour.

Aucune dépendance : ni Compose, ni AndroidX, ni OkHttp, ni Gson. Les écrans
sont des vues nues, le JSON passe par `org.json` que fournit Android. La
construction ne télécharge donc rien de plus que l'AGP et Kotlin, déjà
présents pour VaultEx.

## Installer et régler

1. **Poser le jeton dans Cloudflare.** Dashboard → le Worker → Settings →
   Variables and Secrets → ajouter un **secret** `CHANGE_SMS_TOKEN`.
   Prends une valeur longue et au hasard, par exemple :

   ```bash
   openssl rand -hex 24
   ```

   ⚠️ **Ce n'est pas le jeton du bot Telegram.** Deux rôles, deux secrets :
   celui de Telegram ne peut qu'écrire un message, celui-ci peut faire
   envoyer de la crypto.

   ⚠️ **Il ne doit jamais entrer dans l'APK public de VaultEx.** Il vit ici,
   dans une application installée à la main sur un seul téléphone et jamais
   distribuée — c'est ce qui rend acceptable qu'un appareil le porte.

2. **Redéployer le Worker**, puis vérifier :

   ```
   https://<ton-worker>.workers.dev/diag
   ```

   Le bloc `lectureSms` doit dire `"configuree": true`.

3. **Installer l'APK** sur le téléphone du changeur (transfert par câble, ou
   fichier envoyé puis ouvert ; autoriser les sources inconnues).

4. **Ouvrir l'application, autoriser les SMS**, coller l'adresse du relais
   et le jeton, cocher « Transmettre les SMS », **Enregistrer**.

5. **Appuyer sur « Essayer ».**

   C'est l'étape à ne pas sauter. Elle envoie un faux encaissement d'un
   franc et affiche la réponse du relais. Sans elle, on ne découvre qu'un
   jeton est mal collé qu'au premier vrai paiement d'un client — c'est-à-dire
   au moment où quelqu'un attend sa crypto.

   | Réponse | Ce qu'il faut faire |
   |---|---|
   | *Le relais répond et accepte le jeton* | C'est en place. |
   | `jeton refuse par le relais` | Le jeton de l'écran ne correspond pas à celui de Cloudflare. |
   | `CHANGE_SMS_TOKEN absent du Worker` | L'étape 1 ou 2 n'est pas faite. |
   | `reseau : …` | Adresse du relais fausse, ou pas de connexion. |

## Le premier vrai paiement

Demande à quelqu'un de t'envoyer un petit montant par Orange Money, puis
regarde le **journal** de l'application.

**Si le journal affiche `— ecarte : expediteur « ... »`** : l'identifiant
réel d'Orange Money n'est pas dans la liste. Ajoute-le **tel qu'il
s'affiche**, enregistre, et refais un essai. Cette liste par défaut est une
supposition : l'identifiant exact n'a jamais été mesuré.

**Si le journal affiche `✗ refus 422 : format non reconnu`** : le relais a
bien reçu le SMS mais n'a pas su le lire. C'est le cas le plus probable au
premier essai, et il se corrige en trente secondes :

```bash
node tools/relais-cours/test-sms.mjs "colle ici le texte du SMS"
```

L'outil dit ce qu'il extrait, ou pourquoi il ne reconnaît rien. Envoie le
texte et le motif sera corrigé **dans le Worker** — pas besoin de
réinstaller quoi que ce soit sur le téléphone. C'est précisément pour ça
que le robot transmet le texte brut au lieu de l'analyser lui-même.

## Lire l'écran : le robot écoute-t-il encore ?

La ligne d'état en haut dit deux choses, sur deux lignes :

```
● Actif   ·   SMS autorisés
Dernier SMS : il y a 12 min
```

**Il faut connaître une faiblesse d'Android.** Une application qu'on a
« forcée à s'arrêter » — à la main, ou par un nettoyeur de batterie qui le
fait tout seul — ne reçoit **plus aucun SMS** tant qu'on ne la rouvre pas.
Et comme elle ne tourne plus, rien à l'intérieur ne peut s'en apercevoir ni
prévenir : l'écran affichait « ● Actif » parce que c'est un réglage
enregistré, pas un état mesuré. Le robot était sourd et disait qu'il
écoutait.

La date du dernier SMS est la seule trace visible de ça. Elle passe en
**ambre au-delà de douze heures**.

**Ce qu'elle ne distingue pas**, et c'est important : « le robot est sourd »
et « personne n'a payé depuis ce matin » donnent exactement la même date.
L'ambre ne dit pas « c'est cassé », il dit « regarde ». C'est au changeur de
savoir s'il attendait un paiement.

**Le remède est le même que le diagnostic** : ouvrir l'application pour
regarder cette date est précisément le geste qui la réveille si elle avait
été arrêtée.

### Les deux habitudes qui évitent le problème

1. **Exclure VaultEx Changeur de l'optimisation de batterie** (Paramètres →
   Applications → VaultEx Changeur → Batterie → *Sans restriction*). C'est ce
   qui empêche le système de l'arrêter tout seul.
2. **L'ouvrir une fois par jour**, et regarder la ligne. Douze heures d'ambre
   un jour de paiements est le seul signal qu'on aura.

## Si le réseau manque

Les envois ratés **pour cause de réseau** sont gardés et rejoués au SMS
suivant, ou à l'ouverture de l'écran. L'état en haut affiche le nombre en
attente.

Les refus définitifs — jeton faux, format non reconnu — ne sont pas rejoués :
insister ne les ferait pas aboutir, et viderait la batterie.

## Si le téléphone est perdu

Change `CHANGE_SMS_TOKEN` dans Cloudflare et redéploie. L'ancien jeton
devient inutile immédiatement, partout. C'est la seule protection qui
compte : un chiffrement local du jeton, avec une clé posée dans le même
appareil, ne protégerait de rien.

## La veille et le rattrapage — version 1.1

### Ce que c'était, et pourquoi ça ne suffisait pas

Le robot n'avait qu'un seul chemin : la diffusion `SMS_RECEIVED`, au moment
où le message arrive. `READ_SMS` était refusé exprès, et l'argument était
bon — cette permission donne accès à toute la boîte de réception.

Le prix de ce refus n'était pas écrit : **un SMS manqué était manqué pour
toujours.** Le robot n'avait aucun moyen de le relire.

**Constaté le 3 novembre 2026.** L'application avait été « forcée à
l'arrêt » — Android ne délivre alors aucune diffusion tant que personne ne
la rouvre. Un encaissement de 6 108 FCFA arrive à 16:38, l'application est
rouverte à 16:52. Le SMS était perdu, et l'achat refusé alors que le client
avait payé.

### Les trois niveaux, maintenant

1. **Le SMS arrive** → transmis en une seconde, par la diffusion. Inchangé,
   c'est le chemin normal et le plus rapide.
2. **La diffusion est manquée** → `BalayageSms` relit les SMS toutes les
   quinze minutes, et **immédiatement à chaque ouverture de l'application**.
   C'est ce second point qui répare le cas du 3 novembre.
3. **Le robot est vraiment arrêté** → la notification permanente disparaît
   du téléphone du changeur, et le tableau Telegram passe à 🟠 après six
   heures.

Avant, seul le niveau 3 existait, et il mettait six heures à parler.

### Ce que le rattrapage s'interdit

La permission donne plus que ce qu'on en fait : c'est désormais **le code**
qui limite, plus le système. Quatre règles, toutes vérifiables en lisant
`BalayageSms.kt` :

- il ne lit que les **expéditeurs de la liste** du changeur, avec le même
  `expediteurAccepte` que la diffusion ;
- il ne remonte **jamais au-delà de vingt-quatre heures** — la fenêtre de
  rapprochement du relais est de trois heures, relire un mois d'historique
  ne récupérerait aucun achat ;
- il ne transmet ni ne journalise **jamais le texte** d'un expéditeur
  écarté ;
- il traite **vingt messages au maximum** par passage.

Si ces quatre règles cessent d'être vraies, l'argument de vie privée tombe
pour de bon. C'est une promesse qui ne tient que par relecture.

### Ce qu'il ne répare PAS

**Pendant** qu'une application est forcée à l'arrêt, Android ne lui donne
rien : ni diffusion, ni service, ni balayage. Aucun code ne contourne ça.

Ce qui change, c'est la **reprise** : rouvrir l'application repêche tout ce
qui a été manqué. Avant, rouvrir ne récupérait rien.

### L'installation chez le changeur

C'est la **même application**, en version 2 — pas une seconde appli. Elle
s'installe par-dessus et les réglages sont conservés : relais, jeton,
expéditeurs. Rien à ressaisir.

**À UNE CONDITION : construire sur la MÊME machine que la fois précédente.**
L'APK est signé avec la clé de débogage du poste qui compile
(`~/.android/debug.keystore`). Depuis un autre poste, Android refuse la mise
à jour — il faudrait désinstaller, et le jeton serait perdu.

Ce que le changeur verra, une seule fois :

1. L'installation par-dessus l'ancienne
2. Une demande de permission SMS — **Autoriser**
3. Une notification permanente : « Actif · dernier encaissement il y a 4 min »

Puis plus jamais rien. S'il refuse la lecture des SMS, le robot fonctionne
comme avant la 1.1 : la diffusion suffit tant qu'elle passe. La notification
le dit alors en toutes lettres — « Rattrapage inactif ».
