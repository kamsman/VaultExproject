#!/usr/bin/env bash
# ==========================================================================
# VaultEx — Tableau des MINIMUMS d'echange SimpleSwap
# ==========================================================================
#
# UTILISATION (Git Bash, depuis la racine du projet) :
#
#   ./tools/simpleswap-minimums.sh
#   ./tools/simpleswap-minimums.sh usdttrc20 btc eth trx
#
# Sans argument, il teste les huit monnaies qui portent l'essentiel des
# echanges reels de l'application.
#
# --------------------------------------------------------------------------
# POURQUOI UN OUTIL PLUTOT QU'UNE TABLE DANS LE CODE
# --------------------------------------------------------------------------
#
# SimpleSwap n'expose AUCUN point d'entree qui rende tous les minimums a la
# fois, et ce n'est pas un oubli : un minimum depend du cout de retrait de la
# monnaie d'ARRIVEE, qui change avec les frais de sa chaine. Retirer du BTC
# coute cher, retirer du TRX coute des centimes — d'ou 17 USDT de minimum
# vers BTC et bien moins vers TRX.
#
# Les ecrire en dur dans l'application serait donc doublement faux : faux le
# jour de l'ecriture, et de plus en plus faux ensuite. L'application demande
# le minimum a chaque devis (FournisseurSwap.minimum), et c'est le bon
# comportement.
#
# Cet outil ne sert qu'a DECIDER : savoir, une fois, quelles paires sont a la
# portee d'un utilisateur qui detient 2 000 ou 10 000 FCFA. C'est un
# instrument de mesure, pas une source de verite.
#
# --------------------------------------------------------------------------
# LA CLE NE SORT PAS DE VOTRE MACHINE
# --------------------------------------------------------------------------
#
# Elle est lue dans local.properties (jamais versionne) et n'est JAMAIS
# affichee, ni dans la sortie, ni dans un message d'erreur. Le tableau
# imprime ne contient que des tickers et des nombres : il peut se copier et
# se partager sans risque.
# ==========================================================================

set -uo pipefail

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

# Les huit monnaies qui comptent. Les trois USDT sont des monnaies DISTINCTES
# chez SimpleSwap, comme chez ChangeNOW : un reseau, un ticker.
if [ $# -gt 0 ]; then
  MONNAIES=("$@")
else
  MONNAIES=(usdttrc20 usdterc20 usdtbsc btc eth bnbbsc trx sol)
fi

echo
echo "  MINIMUMS SIMPLESWAP — exprimes dans la monnaie de DEPART"
echo "  (un tiret = paire refusee ou ticker inconnu)"
echo

# En-tete : la colonne est la monnaie d'ARRIVEE.
printf "  %-12s" "de \\ vers"
for v in "${MONNAIES[@]}"; do printf "%12s" "$v"; done
echo
printf "  %-12s" ""
for _ in "${MONNAIES[@]}"; do printf "%12s" "------------"; done
echo

for de in "${MONNAIES[@]}"; do
  printf "  %-12s" "$de"
  for vers in "${MONNAIES[@]}"; do
    if [ "$de" = "$vers" ]; then
      printf "%12s" "·"
      continue
    fi
    rep=$(curl -s --max-time 15 \
      "https://api.simpleswap.io/get_ranges?api_key=${CLE}&currency_from=${de}&currency_to=${vers}&fixed=false" \
      2>/dev/null)
    # Extraction sans jq, qui n'est pas garanti present dans Git Bash.
    min=$(printf '%s' "$rep" | sed -n 's/.*"min"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
    if [ -z "$min" ] || [ "$min" = "null" ]; then
      printf "%12s" "—"
    else
      # Deux decimales suffisent pour decider ; le chiffre exact est rendu
      # par l'application au moment du devis.
      printf "%12s" "$(printf '%.2f' "$min" 2>/dev/null || printf '%s' "$min")"
    fi
    # Respiration : SimpleSwap limite le debit, et une rafale de soixante
    # appels se ferait couper au milieu du tableau.
    sleep 0.35
  done
  echo
done

echo
echo "  Reperes : 1 \$ ≈ 600 FCFA. Un minimum de 3 correspond a ~1 800 FCFA,"
echo "            un minimum de 17 a ~10 400 FCFA."
echo "  Les lignes/colonnes entierement vides signalent un ticker mal ecrit —"
echo "  dites-le, la table des tickers de l'application se corrige en deux minutes."
echo
