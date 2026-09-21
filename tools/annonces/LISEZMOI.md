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

## Lequel envoyer

Le 01 et le 03 attirent, le 02 rassure, le 04 prépare. Le 03 est le plus
concret — il décrit une situation vécue plutôt qu'une fonctionnalité — et
gagne à partir avec le logo USDT. Le 04 se garde pour une seconde vague :
adressé à quelqu'un qui n'a jamais échangé, il inquiète ; adressé à
quelqu'un qui vient de voir « Coût de l'échange ≈ 26 % », il explique.

Un seul message à la fois, espacé de plusieurs jours. Une notification
ignorée coûte peu ; une notification de trop fait désactiver toutes les
suivantes.
