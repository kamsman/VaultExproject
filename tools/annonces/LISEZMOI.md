# Annonces prêtes à envoyer — onglet Swap

## Comment envoyer

    ./tools/send-announcement.sh --fichier tools/annonces/swap-01-sans-compte.txt
    ./tools/send-announcement.sh --fichier tools/annonces/swap-03-mauvaise-chaine.txt USDT

Première ligne du fichier = TITRE, le reste = CORPS. Le troisième argument,
facultatif, remplace le logo VaultEx par celui d'une monnaie.

TOUJOURS passer par `--fichier`. Le texte contient des accents, et
l'en-tête de send-announcement.sh explique pourquoi la ligne de commande
Windows les détruit. Ces fichiers sont en UTF-8.

## Ce que ces messages ne disent pas, et pourquoi

Une annonce est lue AVANT d'ouvrir l'application. Tout écart entre ce
qu'elle promet et ce que l'écran affiche ensuite se paie en confiance, et
ne se rattrape pas. Trois promesses ont donc été écartées.

**« Frais les plus bas » / « frais minimes ».** Sur un petit montant, le
coût réel dépasse couramment 20 % — l'écran l'affiche noir sur blanc. Une
annonce qui promet le contraire se fait démentir par l'application
elle-même, trente secondes plus tard.

**« Sans aucune vérification ».** Vrai dans l'immense majorité des cas,
mais les fournisseurs se réservent le droit de demander une vérification
si leur surveillance anti-blanchiment signale un échange. « Sans créer de
compte » est exact en toutes circonstances, et c'est déjà le vrai
avantage : personne ne garde les fonds à la place de l'utilisateur.

**« Instantané ».** C'est 2 à 5 minutes, parfois plus si la chaîne est
chargée. Le chiffre honnête est déjà un bon argument.

**Aucun montant minimum n'est cité.** Il change selon la paire ET selon
les frais de la chaîne d'arrivée : le même échange n'a pas le même seuil
d'un mois sur l'autre. L'application le demande au fournisseur et
l'affiche sous le champ de saisie ; une annonce qui graverait un chiffre
serait fausse avant d'être lue.

## Deux publics qui veulent l'inverse l'un de l'autre

Quelqu'un qui n'a jamais rien déposé NE PEUT PAS échanger : il n'a rien à
mettre en face, et chaque paire impose en plus un minimum qui dépasse
souvent 10 $. Lui envoyer une annonce sur le Swap, c'est lui montrer une
porte qu'il ne peut pas franchir — au mieux le message tombe à plat, au
pire il fait couper toutes les notifications suivantes.

    swap-*    →  portefeuilles qui ont déjà quelque chose
    depot-*   →  portefeuilles vides

Tant que les annonces partent sur le canal unique `vaultex_all`, les deux
publics reçoivent tout. Les textes sont donc écrits pour que chacun
reconnaisse en une ligne si le message le concerne — « Ton portefeuille
est encore vide ? » se saute tout seul quand on a des fonds. Le jour où
la segmentation par canal sera en place, il n'y aura rien à réécrire.

Les deux annonces `depot-*` insistent sur le RÉSEAU, et ce n'est pas de
la prudence décorative : de l'USDT envoyé sur la mauvaise chaîne est
perdu définitivement. C'est la seule erreur de cet écran qui ne se
rattrape pas, et le moment où quelqu'un reçoit ses premiers fonds est
précisément celui où il ne le sait pas encore.

## Lequel envoyer

Le 01 et le 03 attirent, le 02 rassure, le 04 prépare. Le 03 est le plus
concret — il décrit une situation vécue plutôt qu'une fonctionnalité — et
gagne à partir avec le logo USDT. Le 04 se garde pour une seconde vague :
adressé à quelqu'un qui n'a jamais échangé, il inquiète ; adressé à
quelqu'un qui vient de voir « Coût de l'échange ≈ 26 % », il explique.

Pour un portefeuille vide, `depot-01` ouvre la marche : il dit quoi
faire, en trois gestes. `depot-02` le suit plus tard — c'est un
avertissement, et un avertissement envoyé avant qu'on ait compris à quoi
il sert ne s'imprime pas.

Un seul message à la fois, espacé de plusieurs jours. Une notification
ignorée coûte peu ; une notification de trop fait désactiver toutes les
suivantes.

## Le logo d'une mise à jour

Le 3e argument du script n'accepte qu'un TICKER de monnaie : il va chercher
l'icone correspondante chez le fournisseur d'icones. Il n'existe donc aucun
symbole « mise a jour » a lui passer — laisse-le vide, le logo VaultEx
s'affiche, et c'est le bon choix pour une annonce qui parle de l'application
elle-meme.

Le signe visuel tient dans le TITRE : l'emoji 🔄 ou ⬆️ en tete. Il traverse
sans dommage l'echappement du script, qui convertit chaque caractere non-ASCII
en sequence \uXXXX — y compris les paires de substitution des emoji.

Pour un vrai visuel, il reste le 4e argument (banniere 1024x512). Facultatif,
et pas indispensable pour une annonce de mise a jour.

## Annoncer une mise a jour : une date est une promesse

maj-01 annonce, maj-02 confirme la disponibilite. N'envoie maj-01 QUE si le
paquet est deja construit et pret a publier. Une date annoncee a des milliers
de personnes et non tenue coute plus cher que deux jours de retard : la
prochaine annonce sera lue avec le souvenir de celle-la.

Si le moindre doute subsiste sur la date, saute maj-01 et n'envoie que
maj-02, le jour ou la version est effectivement disponible. Une mise a jour
qui arrive sans preavis ne decoit personne.

VERIFIE AUSSI QUE version.code A AUGMENTE. Sans cela, beaucoup
d'installateurs affichent « installe » sans rien remplacer : les gens
croiront avoir mis a jour et ne verront aucun changement — et c'est
l'annonce qui passera pour mensongere.

## Envoi

    ./tools/send-announcement.sh --fichier tools/annonces/depot-01-premier-depot.txt
    ./tools/send-announcement.sh --fichier tools/annonces/depot-02-le-reseau.txt USDT

`depot-01` parle de toutes les monnaies : il garde le logo VaultEx.
`depot-02` ne parle que d'USDT, le logo Tether y est à sa place.

    ./tools/send-announcement.sh --fichier tools/annonces/maj-01-a-venir.txt
    ./tools/send-announcement.sh --fichier tools/annonces/maj-02-disponible.txt

Les deux gardent le logo VaultEx : elles parlent de l'application, pas
d'une monnaie.
