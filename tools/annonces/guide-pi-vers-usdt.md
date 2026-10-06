# Transformer ses Pi en USDT utilisable

**À publier dans le groupe Telegram**, pas en notification : c'est trop long
pour une bannière, et ça se relit.

---

## Pourquoi ce guide existe

VaultEx ne gère pas le réseau Pi, et ce n'est pas un oubli. SimpleSwap —
l'échangeur que l'application utilise — connaît bien la monnaie PI, mais
**aucune paire n'est ouverte** : ni vers le BTC, ni vers l'ETH, ni vers
aucun des USDT, ni vers le BNB, le TRX ou le SOL. Vérifié le 28 septembre
2026, sept destinations testées, sept refus.

Ajouter Pi dans ces conditions donnerait une adresse, un solde affiché, un
bouton « Envoyer » — et aucun moyen de convertir. Des fonds visibles qui ne
peuvent pas bouger, ce que cette application s'emploie précisément à éviter.

Le chemin ci-dessous, lui, fonctionne aujourd'hui.

---

## Le trajet

```
Pi Wallet  →  OKX  →  vendre le Pi  →  retirer en USDT  →  VaultEx
```

VaultEx est la destination : l'endroit où l'USDT arrive et devient utilisable
— envoyer, échanger, garder.

---

## Les étapes

**1. Un compte OKX, vérifié.** La vérification d'identité est exigée avant
tout retrait. À faire d'abord : la subir au moment où l'on veut sortir ses
fonds est le meilleur moyen de se décourager.

**2. Récupérer l'adresse de dépôt PI sur OKX.** Dépôt → chercher PI →
choisir le réseau Pi.

> **Le point qui fait perdre des fonds : le mémo.**
>
> Le réseau Pi est un dérivé de Stellar, et ces réseaux-là identifient le
> compte destinataire par une adresse **et** un mémo (parfois appelé « tag »,
> « memo ID »). Si OKX en affiche un, il est **obligatoire**.
>
> Un envoi sans mémo, ou avec le mauvais, arrive chez OKX sans qu'on sache à
> qui l'attribuer. Récupérer est long, et parfois impossible.
>
> Copie l'adresse **et** le mémo, colle-les tous les deux.

**3. Envoyer depuis le Pi Wallet.** Commencer par **un petit montant** — 1 ou
2 Pi. Attendre qu'il arrive. Ensuite seulement, envoyer le reste.

Ce n'est pas de la timidité : c'est la seule façon de vérifier l'adresse et
le mémo sans rien risquer. Tout le monde le fait, y compris ceux qui font ça
depuis des années.

**4. Vendre le Pi contre de l'USDT** sur OKX.

**5. Retirer vers VaultEx — et c'est ici que le réseau compte.**

Dans VaultEx : onglet **Recevoir** → **USDT (BEP20)**. Cette ligne porte la
mention « Frais les plus bas », et ce n'est pas décoratif :

| Réseau de retrait | Ce que coûtera chaque envoi ensuite |
|---|---|
| **BNB Chain (BEP20)** | quelques centimes |
| Ethereum (ERC20) | plusieurs dizaines de centimes, parfois des dollars |
| Tron (TRC20) | plusieurs dollars sans énergie gelée |

**Choisis BEP20 sur OKX**, colle l'adresse que VaultEx affiche, et vérifie
que le réseau annoncé des deux côtés est bien le même.

> Une erreur de réseau ici est **définitive**. De l'USDT envoyé sur une chaîne
> que l'adresse ne sert pas est perdu, sans recours.

**6. Garde un peu de BNB.** Les frais de réseau se paient toujours dans la
monnaie de la chaîne, jamais dans le jeton. De l'USDT sur BNB Chain sans un
centime de BNB, c'est de l'argent qu'on voit et qu'on ne peut pas sortir.
Quelques centimes suffisent pour des dizaines d'envois.

---

## Ce que ce guide ne promet pas

**Tous les Pi ne sont pas transférables.** Selon l'avancement du KYC et les
verrouillages de minage, une partie d'un solde peut rester bloquée dans le
Pi Wallet. Cela ne dépend ni de VaultEx ni d'OKX.

**Les frais et les cours changent.** Aucun chiffre n'est écrit ici pour cette
raison — VaultEx affiche le coût réel de chaque opération avant qu'on valide,
et c'est ce chiffre-là qui fait foi.

**Le jour où une paire PI s'ouvrira chez un échangeur**, l'échange direct
deviendra possible dans VaultEx et ce guide n'aura plus lieu d'être. La
vérification tient en une commande : `./tools/pi-disponible.sh`.

Rien n'est écrit côté Pi dans l'application — ni dérivation de clés, ni
adresse, ni solde. Tout reste à faire le jour où ça vaudra la peine.
