#!/usr/bin/env bash
# ==========================================================================
# VaultEx — CHERCHER UN TICKER DANS LE CATALOGUE SIMPLESWAP
# ==========================================================================
#
# UTILISATION (Git Bash, depuis la racine du projet) :
#
#   ./tools/simpleswap-ticker.sh usdt          <-- commencez par celui-ci
#   ./tools/simpleswap-ticker.sh tether
#   ./tools/simpleswap-ticker.sh usdt sol
#
# Le premier argument cherche dans le symbole ET le nom. Le second, s'il est
# donne, ne garde que les lignes dont le RESEAU le contient.
#
# LE SECOND ARGUMENT SE DONNE EN DERNIER, PAS EN PREMIER. Le reseau porte le
# nom que SIMPLESWAP lui donne — « sol » et non « solana », « bsc » et non
# « BNB Chain ». Filtrer d'emblee sur le nom qu'on a en tete rend une liste
# vide qui ressemble a « ce ticker n'existe pas », alors qu'elle dit
# seulement « je ne sais pas comment ils l'appellent ». Cherchez large,
# lisez la colonne reseau, filtrez ensuite si la liste est longue.
#
# --------------------------------------------------------------------------
# POURQUOI CET OUTIL EXISTE
# --------------------------------------------------------------------------
#
# FournisseurSimpleSwap.kt le dit deja : « les tickers SimpleSwap ne se
# devinent pas — ils se verifient ». Deux lignes de la table avaient ete
# recopiees de ChangeNOW et etaient fausses ; « usdtsol » a ete pose comme
# une supposition assumee, en toutes lettres dans le commentaire.
#
# Cette supposition s'est revelee fausse en production : toute paire PARTANT
# de l'USDT Solana rendait « Devis indisponible. Not Found », vers SOL comme
# vers DAI, ETH ou BNB.
#
# Or le catalogue n'obeit a AUCUNE regle. Dans le meme fichier cohabitent
# « usdtbep20 » et « ethbsc » — meme chaine, deux suffixes. « bnb-bsc » prend
# un trait d'union que personne d'autre ne prend.
#
# --------------------------------------------------------------------------
# CE QUE CE SCRIPT AFFICHE, ET POURQUOI IL AFFICHE LE CONTRAT
# --------------------------------------------------------------------------
#
# Ticker, symbole, nom, reseau, adresse de contrat.
#
# Le contrat n'est pas decoratif. « usdt » tout court, chez SimpleSwap,
# designe l'Omni Layer et non Ethereum : concatener un symbole et un reseau
# finit par produire un ticker qui EXISTE et qui designe autre chose. Un
# ticker faux qui n'existe pas fait seulement echouer le devis, sans risque.
# Un ticker faux qui existe donne une adresse de depot valide pour une AUTRE
# monnaie — et la, les fonds partent et ne reviennent pas.
#
# C'est tout l'ecart entre verifier et supposer. Lisez le reseau et le
# contrat avant de recopier un ticker dans le code.
#
# --------------------------------------------------------------------------
# LA CLE NE SORT PAS DE VOTRE MACHINE
# --------------------------------------------------------------------------
#
# Elle est lue dans local.properties (jamais versionne) et n'est JAMAIS
# affichee, ni dans la sortie, ni dans un message d'erreur. Le tableau
# imprime ne contient que des tickers, des noms et des adresses publiques :
# il peut se copier et se partager sans risque.
# ==========================================================================

set -uo pipefail

if [ $# -lt 1 ]; then
  echo "Usage :  ./tools/simpleswap-ticker.sh <recherche> [reseau]" >&2
  echo "Exemple :  ./tools/simpleswap-ticker.sh usdt solana" >&2
  exit 2
fi

RECHERCHE=$(printf '%s' "$1" | tr '[:upper:]' '[:lower:]')
RESEAU=$(printf '%s' "${2-}" | tr '[:upper:]' '[:lower:]')

PROPS="local.properties"
if [ ! -f "$PROPS" ]; then
  echo "ERREUR : $PROPS introuvable. Lancez ce script depuis la racine du projet." >&2
  exit 1
fi

CLE=$(grep -E '^[[:space:]]*simpleswap\.key[[:space:]]*=' "$PROPS" | head -1 | cut -d'=' -f2- | tr -d ' \r\n')
if [ -z "$CLE" ]; then
  echo "ERREUR : aucune ligne 'simpleswap.key=...' dans $PROPS." >&2
  echo "         Recuperez la cle dans SimpleSwap → Web Tools → API." >&2
  exit 1
fi

echo
echo "  Catalogue SimpleSwap — recherche « $RECHERCHE »${RESEAU:+, reseau « $RESEAU »}"
echo "  ------------------------------------------------------------------"

REPONSE=$(curl -s --max-time 30 \
  "https://api.simpleswap.io/get_all_currencies?api_key=${CLE}" 2>/dev/null)

if [ -z "$REPONSE" ]; then
  echo "  Aucune reponse de SimpleSwap." >&2
  echo "  Sans reponse on ne peut RIEN conclure — surtout pas qu'un ticker" >&2
  echo "  est absent du catalogue. Verifiez la connexion et recommencez." >&2
  exit 1
fi

# --------------------------------------------------------------------------
# CATALOGUE OU ERREUR : ON LE DECIDE SUR LA FORME, PAS SUR UN MOT
# --------------------------------------------------------------------------
#
# Cette ligne cherchait « "code" » ou « "error" » N'IMPORTE OU dans la
# reponse. Or le catalogue contient des centaines de monnaies, et il suffit
# qu'une seule porte un champ ou un symbole ainsi nomme — il existe un jeton
# « CODE » — pour que le script declare une panne et s'arrete sur un
# catalogue parfaitement valide.
#
# Un tableau JSON commence par « [ », un objet d'erreur par « { ». C'est
# structurel, ca ne depend d'aucun contenu.
PREMIER=$(printf '%s' "$REPONSE" | tr -d ' \t\r\n' | cut -c1)
if [ "$PREMIER" != "[" ]; then
  echo "  SimpleSwap n'a pas rendu un catalogue (cle invalide, debit depasse," >&2
  echo "  ou service indisponible). Reponse recue, mais sous forme d'objet." >&2
  echo "  Le detail n'est pas affiche : il peut contenir la cle." >&2
  exit 1
fi

# Un objet JSON par ligne, sans jq — absent de Git Bash par defaut.
#
# Le decoupage passe par un FICHIER et non par un tube : dans un tube, la
# boucle tourne dans un sous-shell et les compteurs y meurent. Or c'est
# precisement le compteur qui distingue « aucune monnaie ne correspond » de
# « le decoupage n'a rien produit » — deux pannes qui s'affichent pareil,
# c'est-a-dire par un silence.
TMP=$(mktemp 2>/dev/null || printf '%s' "${TMPDIR:-/tmp}/sswap.$$")
trap 'rm -f "$TMP"' EXIT
printf '%s' "$REPONSE" | sed 's/},[[:space:]]*{/}\n{/g' > "$TMP"

champ() {
  printf '%s' "$2" \
    | sed -n "s/.*\"$1\"[[:space:]]*:[[:space:]]*\"\([^\"]*\)\".*/\1/p" \
    | head -1
}

LUES=0
TROUVEES=0
# `|| [ -n "$ligne" ]` : sans lui, la DERNIERE monnaie du catalogue est
# perdue. `read` rend un code d'echec quand la ligne n'est pas terminee par
# un retour chariot, et le dernier objet du tableau JSON n'en porte pas. La
# boucle s'arretait donc en ayant deja lu la ligne, sans la traiter.
#
# Constate sur un catalogue d'essai de quatre monnaies : trois lues.
while IFS= read -r ligne || [ -n "$ligne" ]; do
  sym=$(champ symbol "$ligne")
  [ -z "$sym" ] && continue
  LUES=$((LUES + 1))

  nom=$(champ name "$ligne")
  net=$(champ network "$ligne")
  ctr=$(champ contract_address "$ligne")

  bas=$(printf '%s %s' "$sym" "$nom" | tr '[:upper:]' '[:lower:]')
  case "$bas" in *"$RECHERCHE"*) ;; *) continue ;; esac

  if [ -n "$RESEAU" ]; then
    netbas=$(printf '%s' "$net" | tr '[:upper:]' '[:lower:]')
    case "$netbas" in *"$RESEAU"*) ;; *) continue ;; esac
  fi

  # Chez SimpleSwap le TICKER EST le symbole : c'est « symbol » qu'on passe
  # a get_ranges et get_estimated, pas un champ separe.
  #
  # Un contrat vide ne veut pas dire « monnaie native » : il veut dire que la
  # reponse n'en portait pas. Les deux se confondent a l'oeil, et la
  # confusion ferait recopier un ticker sans l'avoir verifie — ce que ce
  # script existe precisement pour empecher.
  printf '  %-18s %-28s %-14s %s\n' \
    "$sym" "${nom:--}" "${net:--}" "${ctr:-(aucun dans la reponse)}"
  TROUVEES=$((TROUVEES + 1))
done < "$TMP"

echo "  ------------------------------------------------------------------"

# UN SILENCE N'EST PAS UN RESULTAT. Sans ces lignes, « le ticker n'existe
# pas », « mon filtre est trop etroit » et « le decoupage a echoue »
# s'affichent tous les trois de la meme facon : rien.
if [ "$LUES" -eq 0 ]; then
  echo "  AUCUNE monnaie n'a pu etre lue dans la reponse."
  echo "  Ce n'est donc PAS une reponse sur le ticker : c'est le decoupage"
  echo "  de ce script qui a echoue. Signalez-le, ne concluez rien."
elif [ "$TROUVEES" -eq 0 ]; then
  echo "  $LUES monnaies lues, AUCUNE ne correspond a « $RECHERCHE »${RESEAU:+ sur « $RESEAU »}."
  if [ -n "$RESEAU" ]; then
    echo
    echo "  Le filtre de reseau est le premier suspect : SimpleSwap ecrit"
    echo "  « sol » la ou on dit « solana », « bsc » la ou on dit « BNB »."
    echo "  Relancez SANS le second argument pour voir tous les reseaux :"
    echo "      ./tools/simpleswap-ticker.sh $RECHERCHE"
  fi
else
  echo "  $TROUVEES monnaies sur $LUES — colonnes : ticker · nom · reseau · contrat"
  echo
  echo "  AVANT DE RECOPIER UN TICKER DANS FournisseurSimpleSwap.kt :"
  echo "  lisez le RESEAU et le CONTRAT de la ligne, pas seulement son nom."
  echo "  L'USDT Solana doit porter le contrat Es9vMFrza...BenwNYB ; une"
  echo "  autre adresse est une autre monnaie, et le depot y serait perdu."
fi
echo
