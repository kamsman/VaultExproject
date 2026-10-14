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

Depuis la racine du dépôt :

```bash
./gradlew -p tools/changeur-sms assembleDebug
```

Sous Windows :

```powershell
.\gradlew.bat -p tools\changeur-sms assembleDebug
```

### Pour l'APK qu'on installe vraiment : `assembleRelease`

```powershell
.\gradlew.bat -p tools\changeur-sms assembleRelease
```

L'APK sort dans `tools/changeur-sms/build/outputs/apk/release/`.

**La différence qui compte est `debuggable`.** Un APK de debug est
débogable par construction : quiconque branche un câble sur ce téléphone
peut lire les préférences de l'application, donc le **jeton**. Or ce jeton
permet d'affirmer au relais qu'un paiement a eu lieu — et donc de faire
confirmer des achats jamais payés.

Pour un APK qu'on installe une fois et qui reste en place des mois sur le
téléphone qui encaisse, ça n'est pas un détail.

Il est signé avec la clé de debug, ce qui suffit : il ne passera jamais par
le Play Store et s'installe à la main. Sans configuration de signature,
`assembleRelease` produirait un APK **non signé**, qu'Android refuse
d'installer — et on ne s'en apercevrait qu'une fois le fichier transféré.

L'APK sort dans `tools/changeur-sms/build/outputs/apk/debug/`.

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
