#!/usr/bin/env bash
# ==========================================================================
# VaultEx — DEPLOYER LE RELAIS, DEPUIS N'IMPORTE OU
# ==========================================================================
#
# UTILISATION (Git Bash, depuis n'importe quel dossier du depot) :
#
#   ./tools/deployer-relais.sh
#   tools/deployer-relais.sh            <-- depuis la racine
#   ../deployer-relais.sh               <-- depuis tools/relais-cours
#
#   --sans-tests   deploie sans lancer les garde-fous d'abord
#   --sans-verif   ne verifie pas la version en ligne apres
#
# ─── POURQUOI CE SCRIPT EXISTE ────────────────────────────────────────────
#
# MESURE, sur une seule soiree : la commande de deploiement a echoue TROIS
# fois, et chaque fois pour la meme raison — elle etait lancee depuis le
# mauvais dossier. Le message de wrangler ne le dit pas ; il affiche un
# chemin double et parle d'un fichier introuvable :
#
#   ENOENT: no such file or directory, open
#   'C:\...\VaultEx\C:\...\VaultEx\wrangler.toml'
#
# Celui qui lit ca cherche son wrangler.toml, qui est pourtant la.
#
# L'EN-TETE DE wrangler.toml DOCUMENTE DEJA LE `cd`. Ca n'a pas suffi, et
# c'est l'enseignement : une consigne rangee dans le fichier qu'on ne lit
# qu'en cas de probleme ne previent pas le probleme. Le `cd` doit etre
# DANS le script, pas dans un commentaire a cote.
#
# ─── CE QU'IL AJOUTE, ET QUI N'EST PAS DU CONFORT ─────────────────────────
#
# IL LANCE LES GARDE-FOUS AVANT DE DEPLOYER, et refuse de deployer s'ils
# tombent. Un relais qui echoue a ses propres tests est deja parti en
# production deux fois dans ce projet, et chaque fois le defaut s'est
# decouvert par un client.
#
# IL VERIFIE LA VERSION APRES. Le dépôt en v48 pendant que le Worker
# tournait en v44 a coute cinq allers-retours : rien ne le signalait,
# l'ecran marchait, les prix s'affichaient. `verifier-deploiement.mjs`
# existe pour ca — autant qu'il parte tout seul.
# ==========================================================================

set -euo pipefail

# ─── Le dossier du relais, resolu depuis CE fichier ───────────────────────
#
# `$0` EST LE SEUL REPERE FIABLE. Le dossier courant est precisement ce
# qu'on ne veut plus supposer, et `cd -- "$(dirname …)"` suit les deux
# formes d'appel : relative et absolue.
ICI="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
RELAIS="$ICI/relais-cours"

if [ ! -f "$RELAIS/wrangler.toml" ]; then
  echo "❌ wrangler.toml introuvable dans $RELAIS"
  echo "   Ce script doit rester dans tools/, a cote de relais-cours/."
  exit 2
fi

# ─── npx ou npx.cmd : MESURE, PAS SUPPOSITION ─────────────────────────────
#
# L'en-tete de wrangler.toml affirme « npx.cmd ET NON npx ». C'est vrai
# SOUS POWERSHELL, ou `npx` lance `npx.ps1` et se fait refuser par la
# politique d'execution. Sous Git Bash, les deux existent souvent, et sur
# une machine Linux `npx.cmd` n'existe pas du tout.
#
# On ne tranche donc pas d'avance : on regarde ce qui est la.
if command -v npx.cmd >/dev/null 2>&1; then
  NPX="npx.cmd"
elif command -v npx >/dev/null 2>&1; then
  NPX="npx"
else
  echo "❌ Ni npx.cmd ni npx ne sont dans le PATH."
  echo "   Node.js est-il installe ? Essaie : node --version"
  exit 2
fi

TESTS=1
VERIF=1
for a in "$@"; do
  case "$a" in
    --sans-tests) TESTS=0 ;;
    --sans-verif) VERIF=0 ;;
    *) echo "❌ Option inconnue : $a"; echo "   Connues : --sans-tests  --sans-verif"; exit 2 ;;
  esac
done

cd -- "$RELAIS"

VERSION="$(sed -n 's/^const VERSION = \([0-9]*\).*/\1/p' worker.js | head -1)"
echo
echo "Dossier : $RELAIS"
echo "Version : worker.js declare ${VERSION:-?}"
echo "Outil   : $NPX wrangler"

# ─── 1. Les garde-fous ────────────────────────────────────────────────────
if [ "$TESTS" = "1" ]; then
  echo
  echo "── Garde-fous ───────────────────────────────────────────────────────"
  if ! node test-garde-fous.mjs; then
    echo
    echo "❌ Les garde-fous tombent. RIEN n'a ete deploye."
    echo "   Corrige, ou passe --sans-tests si tu sais ce que tu fais."
    exit 1
  fi
fi

# ─── 2. Le deploiement ────────────────────────────────────────────────────
echo
echo "── Deploiement ──────────────────────────────────────────────────────"
if ! "$NPX" wrangler deploy --config wrangler.toml; then
  echo
  echo "❌ Le deploiement a echoue. La version en ligne n'a pas change."
  exit 1
fi

# ─── 3. Le code en ligne est-il celui du depot ? ──────────────────────────
#
# SEPAREMENT DU CODE DE SORTIE DU DEPLOIEMENT : wrangler peut rendre 0 et
# le Worker servir encore l'ancien code le temps de la propagation. Un
# echec ici n'annule pas le deploiement, il demande de relancer la verif.
if [ "$VERIF" = "1" ]; then
  echo
  echo "── Verification ─────────────────────────────────────────────────────"
  if ! node verifier-deploiement.mjs; then
    echo
    echo "⚠️  La verification n'a pas confirme la version ${VERSION:-?}."
    echo "   La propagation prend parfois quelques secondes. Relance :"
    echo "     node verifier-deploiement.mjs"
    exit 1
  fi
fi

echo
# ─── LE CONSEIL DOIT ETRE COPIABLE DEPUIS LA OU ON EST ────────────────────
#
# PREMIERE VERSION DE CES DEUX LIGNES : « node voir-canaux.mjs --plages=bnb ».
# Elle supposait le dossier du relais, parce que le script y avait fait son
# `cd`. Mais le `cd` ne vit QUE dans ce script : celui qui lit le conseil
# est reste ou il etait, et la commande a echoue « Cannot find module ».
#
# C'est exactement la faute que ce script repare, reintroduite par son
# propre message d'aide. On imprime donc le chemin resolu, qui marche
# depuis n'importe quel dossier — les outils savent desormais trouver
# local.properties par eux-memes.
echo "Pour sonder les noeuds d'un reseau (la cle est demandee sans echo) :"
echo "  node $RELAIS/voir-canaux.mjs --plages=bnb"
echo
