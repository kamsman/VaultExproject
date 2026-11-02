#!/usr/bin/env bash
# ==========================================================================
# VaultEx — CHERCHER UN TICKER DANS LE CATALOGUE SIMPLESWAP
# ==========================================================================
#
# UTILISATION (Git Bash, depuis la racine du projet) :
#
#   ./tools/simpleswap-ticker.sh tether
#   ./tools/simpleswap-ticker.sh usdt solana
#   ./tools/simpleswap-ticker.sh sol
#
# Le premier argument cherche dans le symbole ET le nom. Le second, s'il est
# donne, ne garde que les lignes dont le RESEAU le contient.
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

# La cle n'apparait jamais dans la sortie : seule $REPONSE est imprimee, et
# elle ne contient que le catalogue.
case "$REPONSE" in
  *'"code"'*|*'"error"'*)
    echo "  SimpleSwap a refuse la demande (cle invalide, ou debit depasse)." >&2
    echo "  Le detail n'est pas affiche : il peut contenir la cle." >&2
    exit 1
    ;;
esac

# Un objet JSON par monnaie, sans jq — absent de Git Bash par defaut.
printf '%s' "$REPONSE" \
  | sed 's/},[[:space:]]*{/}\n{/g' \
  | while IFS= read -r ligne; do
      champ() {
        printf '%s' "$ligne" \
          | sed -n "s/.*\"$1\"[[:space:]]*:[[:space:]]*\"\([^\"]*\)\".*/\1/p" \
          | head -1
      }
      sym=$(champ symbol)
      nom=$(champ name)
      net=$(champ network)
      ctr=$(champ contract_address)
      [ -z "$sym" ] && continue

      bas=$(printf '%s %s' "$sym" "$nom" | tr '[:upper:]' '[:lower:]')
      case "$bas" in *"$RECHERCHE"*) ;; *) continue ;; esac

      if [ -n "$RESEAU" ]; then
        netbas=$(printf '%s' "$net" | tr '[:upper:]' '[:lower:]')
        case "$netbas" in *"$RESEAU"*) ;; *) continue ;; esac
      fi

      # Chez SimpleSwap le TICKER EST le symbole : c'est « symbol » qu'on
      # passe a get_ranges et get_estimated, pas un champ separe.
      #
      # Un contrat vide ne veut pas dire « monnaie native » : il veut dire
      # que la reponse n'en portait pas. Les deux se confondent a l'oeil et
      # la confusion ferait recopier un ticker sans l'avoir verifie — ce
      # que ce script existe precisement pour empecher.
      printf '  %-18s %-28s %-14s %s\n' \
        "$sym" "${nom:--}" "${net:--}" "${ctr:-(aucun dans la reponse)}"
    done

echo "  ------------------------------------------------------------------"
echo "  Colonnes : ticker · nom · reseau · contrat"
echo
echo "  AVANT DE RECOPIER UN TICKER DANS FournisseurSimpleSwap.kt :"
echo "  lisez le RESEAU et le CONTRAT de la ligne, pas seulement son nom."
echo "  L'USDT Solana doit porter le contrat Es9vMFrza...BenwNYB ; une"
echo "  autre adresse est une autre monnaie, et le depot y serait perdu."
echo
