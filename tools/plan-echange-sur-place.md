# Échange sur place — plan de livraison

Échanger deux monnaies **à l'intérieur d'une même chaîne**, sans courtier :
une transaction signée depuis le portefeuille de l'utilisateur vers une
réserve de liquidité, et retour, en une seule opération.

**Ce qui change pour l'utilisateur** : 10 à 26 % de coût deviennent moins de
2 %, 2 à 5 minutes deviennent 3 secondes, et surtout le **minimum disparaît**
— aujourd'hui quelqu'un avec 5 000 FCFA ne peut pas échanger du tout.

**Ce que ça ne change pas** : sans BNB, rien ne part. L'échange sur place
coûte du gaz comme tout le reste, et la première autorisation en demande un
peu plus. Le cul-de-sac du gaz reste un problème distinct.

**Périmètre : BNB Chain, paire USDT-BEP20 ↔ BNB, et rien d'autre.** Ethereum
viendra ensuite avec le même code. Tron et Solana sont d'autres protocoles,
donc d'autres projets.

---

## Ce qui existe déjà

Le socle réseau est complet, et c'est ce qui rend ce plan raisonnable.

| Brique | État |
|---|---|
| `eth_call` — lire un contrat sans rien signer | déjà utilisé |
| `eth_estimateGas` | oui |
| Encodage ABI, signature, diffusion | oui, `0xa9059cbb` sur deux chaînes |
| Plafonds de gaz anti-nœud-compromis | `requireSaneGas` |
| Adresse de perception EVM | déclarée, inutilisée |

---

## Étape 0 — Trois décisions, avant d'écrire une ligne

Elles ne se délèguent pas : elles engagent de l'argent.

**Quel routeur.**

| | Prix | Commission possible | Clé |
|---|---|---|---|
| **1inch** | meilleurs (agrège plusieurs réserves) | **oui**, paramètre dédié | oui, quota |
| **PancakeSwap** en direct | corrects sur les grosses paires | non sans transaction en plus | aucune |

Vérifier **sur la documentation 1inch** que le paramètre de commission existe
toujours dans la version courante de l'API. Ce point n'a pas pu être vérifié
depuis l'environnement de développement, et il décide du modèle économique.

**Quel taux.** 1 % laisse l'utilisateur dix fois moins cher qu'aujourd'hui et
reste vérifiable par quiconque ouvre un explorateur de blocs. Au-delà de 2 %,
on redevient l'intermédiaire qu'on supprime.

**Quelle adresse perçoit.** `VAULTEX_FEE_RECIPIENT_EVM` est déjà déclarée.
Confirmer qu'on en détient la clé avant de l'utiliser.

---

## Étape 1 — Le devis, sans rien signer · 2 jours

Lire le prix de la réserve et l'afficher **à côté** du devis du courtier.

Aucune transaction n'est construite. Aucune signature. Aucun risque.

**Vérifiable** : l'écran d'échange affiche deux chiffres pour la même paire.
On compare à la main avec PancakeSwap dans un navigateur — ils doivent
concorder à la fraction de pourcent près.

**Ce que ça apprend tout de suite** : l'écart réel entre les deux chemins,
sur les montants que les gens échangent vraiment. Si l'écart est décevant,
on s'arrête ici et on n'a perdu que deux jours.

---

## Étape 2 — L'autorisation · 2,5 jours

Un jeton ne se dépense pas tout seul : il faut d'abord autoriser le routeur à
en prélever une quantité. C'est une transaction à part.

**Le montant exact, jamais illimité.** L'autorisation illimitée est le défaut
de l'industrie, et c'est une erreur : elle laisse un contrat ponctionner le
solde entier, pour toujours, longtemps après l'échange. Tant qu'un contrat se
comporte bien ça ne se voit pas ; le jour où il est compromis, tout part. On
autorise ce qu'on échange, et rien de plus.

**Vérifiable** : l'allocation passe de 0 au montant demandé, lisible sur
BscScan sans croire l'application sur parole.

**Ce qu'il faut dire à l'utilisateur** : qu'il signe deux fois la première
fois, et une seule les suivantes. Un deuxième écran d'empreinte sans
explication ressemble à un bug.

---

## Étape 3 — L'échange, sur des montants minuscules · 2,5 jours

Construire l'appel du routeur, le signer, le diffuser.

**C'est ici que l'argent peut se perdre**, et c'est le code le plus dangereux
du dépôt après la signature Bitcoin. Un dépôt mal parti chez un courtier se
rembourse ; un appel de contrat raté brûle le gaz, et un montant minimum de
sortie mal calculé laisse partir la différence.

**Trois garde-fous, non négociables :**

- un **plafond de montant codé en dur** pendant toute la phase de test, pour
  qu'une erreur coûte 500 FCFA et jamais un solde ;
- un **montant minimum de sortie calculé**, jamais zéro — c'est lui qui
  empêche qu'on se serve au passage ;
- **le chiffre affiché est celui qui est signé.** Si le devis a vieilli entre
  l'affichage et l'empreinte, on redemande plutôt que de signer un autre.

**Vérifiable** : 500 FCFA d'USDT en BNB, sur le portefeuille du développeur,
transaction ouverte sur BscScan.

---

## Étape 4 — L'aiguillage · 1 jour

Même chaîne → sur place. Chaînes différentes → courtier, comme aujourd'hui.

**L'utilisateur ne choisit pas et ne voit pas ce mot.** Il voit un prix et un
délai. « DEX », « sur place », « agrégateur » n'apprennent rien à quelqu'un
qui veut convertir 5 000 FCFA.

`FournisseurSwap` ne peut pas porter ce chemin : son contrat suppose une
adresse de dépôt et un statut à interroger, qu'un échange sur place n'a pas.
Lui faire rendre une fausse adresse de dépôt serait un mensonge dans
l'interface. Il faut une seconde abstraction **à côté**, pas une
implémentation de plus.

---

## Étape 5 — Les échecs · 2 jours

Ce qui échoue réellement, et ce que chacun doit dire :

| Échec | Ce que l'écran doit dire |
|---|---|
| Glissement dépassé | le prix a bougé, voici le nouveau, on recommence |
| Gaz insuffisant | il manque du BNB, et où en recevoir |
| Contrat refusé (revert) | l'échange n'a pas eu lieu, le gaz est perdu |
| Réserve trop petite | ce montant ne passe pas sur cette paire |

Les quatre dans les trois langues, comme le reste.

---

## Étape 6 — Mise en service progressive · 2 jours

**Une semaine avec tes propres fonds**, paire unique, plafond en place, avant
qu'un seul utilisateur y touche.

Puis le plafond monte, et le canal d'administration remonte chaque échec —
sans quoi on apprendra les problèmes par capture d'écran, une semaine après.

---

## Total

**14 jours de travail.** Trois semaines calendaires au rythme réel.

Chaque étape est livrable et vérifiable seule. Les étapes 1 et 2 ne peuvent
coûter d'argent à personne : la première ne signe rien, la seconde n'autorise
qu'un montant choisi. Le risque commence à l'étape 3, et il est borné par le
plafond.

**On peut s'arrêter après l'étape 1** si l'écart de prix ne vaut pas la
suite. C'est précisément pour ça qu'elle est la première.
