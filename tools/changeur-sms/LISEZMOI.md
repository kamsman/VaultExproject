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

L'APK sort dans `tools/changeur-sms/build/outputs/apk/debug/`.

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
