# VaultEx — Installation sur le téléphone qui reçoit les paiements

**Pour la personne dont le numéro Orange Money reçoit les paiements VaultEx.**
Temps nécessaire : une dizaine de minutes. Rien à comprendre à l'informatique.

---

## Avant de commencer, ce que fait cette application

Quand un client paie par Orange Money, votre téléphone reçoit un SMS de
confirmation. Cette application lit **ce SMS-là** et prévient VaultEx que
l'argent est arrivé. Sans elle, personne ne peut savoir qu'un client a payé,
et le client attend sans rien recevoir.

### Ce qu'elle lit, et ce qu'elle ne lit pas

- Elle voit **uniquement les SMS qui arrivent pendant qu'elle fonctionne.**
  Elle n'a **aucun accès à votre boîte de réception** : vos anciens messages
  lui sont invisibles, et le resteront.
- Parmi les SMS qui arrivent, **seuls ceux d'Orange Money, Moov ou Wave**
  quittent le téléphone. Un message d'un ami, d'un médecin, d'une banque :
  rien n'en sort. Le tri se fait **dans le téléphone**, avant tout envoi.
- Pour un SMS d'Orange Money accepté, ce qui part est le texte du message et
  le nom de l'expéditeur. VaultEx en retient le montant, le numéro et la
  référence, et les efface au bout de trois heures.

Si cela ne vous convient pas, dites-le plutôt que d'installer : il existe
d'autres façons d'organiser les paiements.

---

## Ce dont vous avez besoin

1. **Le fichier d'installation** (`app-release.apk`) — M. Koanda vous l'envoie.
2. **Le code secret** — une longue suite de lettres et de chiffres. Il vous
   l'envoie **séparément**, et jamais dans le même message que le fichier.

> **Ce code ne doit être montré à personne.** Qui le possède peut faire croire
> à VaultEx qu'un paiement est arrivé alors que personne n'a payé — et de la
> crypto part pour rien. Ne le photographiez pas, ne le transférez pas.

---

## Étape 1 — Installer l'application

1. Ouvrez le fichier `app-release.apk` que vous avez reçu.
2. Android affichera un avertissement du genre **« Pour votre sécurité, votre
   téléphone n'est pas autorisé à installer des applications inconnues »**.
   Appuyez sur **Paramètres**, activez **Autoriser depuis cette source**, puis
   revenez en arrière.
3. Appuyez sur **Installer**, puis sur **Ouvrir**.

L'application s'appelle **VaultEx Changeur**.

---

## Étape 2 — Autoriser les SMS

À la première ouverture, le téléphone demande l'autorisation de recevoir les
SMS. Appuyez sur **Autoriser**.

Si vous appuyez sur Refuser par erreur : fermez l'application, rouvrez-la, et
la demande revient.

---

## Étape 3 — Remplir les deux champs

L'écran tient en une page. De haut en bas :

```
VaultEx Changeur

● ...                              ← une ligne d'état

Adresse du relais
┌──────────────────────────────┐
│                              │   ← PREMIER champ
└──────────────────────────────┘

Jeton (CHANGE_SMS_TOKEN)
┌──────────────────────────────┐
│                              │   ← DEUXIÈME champ
└──────────────────────────────┘

☐ Transmettre les SMS              ← une case à cocher

[ Enregistrer ]   [ Essayer ]
```

**Dans le premier champ**, tapez exactement ceci :

```
https://vaultex-prix.mouminekoanda0.workers.dev
```

Rien après `.dev` — pas de barre oblique, pas d'espace.

**Dans le deuxième champ**, collez le code secret reçu de M. Koanda.

Puis :

4. **Cochez la case « Transmettre les SMS »**
5. Appuyez sur **Enregistrer**
6. **Seulement ensuite**, appuyez sur **Essayer**

L'ordre compte. « Enregistrer » met en place ce que vous venez de taper ;
« Essayer » le met à l'épreuve. Appuyer sur « Essayer » avant d'enregistrer
ne teste rien.

---

## Étape 4 — Lire la réponse de « Essayer »

C'est l'étape qui dit si tout est en place. L'application envoie un faux
paiement d'un franc et affiche ce que VaultEx répond.

| Ce qui s'affiche | Ce que ça veut dire |
|---|---|
| Le relais répond et accepte | ✅ **C'est bon, c'est terminé.** |
| `jeton refuse par le relais` | Le code secret est mal recopié — un caractère en trop ou en moins. Retapez-le. |
| `CHANGE_SMS_TOKEN absent du Worker` | Ce n'est pas vous : prévenez M. Koanda. |
| `reseau : ...` | L'adresse est mal tapée, ou le téléphone n'a pas Internet. |

**N'allez pas plus loin tant que ce n'est pas vert.** Un code mal recopié ne
se découvre sinon qu'au premier vrai paiement d'un client — au moment précis
où quelqu'un attend son argent.

---

## Étape 5 — Empêcher Android d'arrêter l'application

**C'est l'étape la plus importante, et celle qu'on oublie.**

Android arrête tout seul les applications qu'il juge inutiles, surtout pour
économiser la batterie. Une application arrêtée **ne reçoit plus aucun SMS**,
et ne peut même pas s'en apercevoir — elle ne tourne plus.

Pour l'empêcher :

**Paramètres → Applications → VaultEx Changeur → Batterie → Sans restriction**

Selon le téléphone, le chemin peut s'appeler « Utilisation de la batterie »,
« Optimisation », ou « Applications en veille ». Cherchez l'option qui dit
**Sans restriction** ou **Ne pas optimiser**, et choisissez-la.

Si un « nettoyeur de téléphone » ou un « économiseur de batterie » est installé,
ajoutez aussi **VaultEx Changeur** à sa liste d'exceptions.

---

## À faire une fois par jour

Ouvrez l'application et regardez la ligne du haut :

```
● Actif   ·   SMS autorisés
Dernier SMS : il y a 12 min
```

**Si la date passe en orange** (plus de douze heures), deux choses sont
possibles : personne n'a payé depuis ce matin, ou l'application a été arrêtée.
L'application ne peut pas faire la différence.

**Le simple fait de l'ouvrir pour regarder la réveille** si elle était
arrêtée. C'est pour ça que ce geste quotidien suffit.

---

## Si quelque chose ne va pas

| Situation | Quoi faire |
|---|---|
| Un client dit avoir payé, rien ne se passe | Ouvrez l'application, regardez la date du dernier SMS, prévenez M. Koanda |
| L'application a disparu | Elle a été désinstallée. Prévenez : il faut tout recommencer |
| Vous changez de téléphone | **Prévenez avant.** Le code secret doit être changé |
| Vous perdez le téléphone | **Prévenez immédiatement.** Le code doit être annulé le jour même |

Dans tous les cas : **ne désinstallez pas l'application** pour « réparer ».
Ouvrir suffit presque toujours, et désinstaller efface le code secret.
