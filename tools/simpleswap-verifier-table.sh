#!/usr/bin/env bash
# ==========================================================================
# VaultEx — VERIFIER TOUTE LA TABLE DES TICKERS SIMPLESWAP
# ==========================================================================
#
# UTILISATION (Git Bash, depuis la racine du projet) :
#
#   ./tools/simpleswap-verifier-table.sh
#
# Lit les tickers de FournisseurSimpleSwap.kt et demande a SimpleSwap si
# chacun existe. Affiche, pour chaque ligne, le reseau et le contrat.
#
# --------------------------------------------------------------------------
# CE QU'IL ATTRAPE, ET CE QU'IL N'ATTRAPE PAS
# --------------------------------------------------------------------------
#
# IL ATTRAPE le ticker qui n'existe pas. C'est le cas « usdtsol » : pose au
# juge, jamais verifie, decouvert en production par quatre paires refusees
# d'affilee. Ce script l'aurait nomme en une seconde.
#
# IL N'ATTRAPE PAS, TOUT SEUL, le ticker qui existe et designe autre chose.
# « usdt » tout court existe : c'est Tether Omni. Un ticker juste de nom et
# faux de chaine rend une adresse de depot valide, et les fonds partent.
#
# C'est pour ca que la colonne CONTRAT est affichee et qu'elle est la seule
# qui tranche. Lisez-la contre le registre de SwapViewModel :
#
#   USDT-ETH   0xdAC17F958D2ee523a2206206994597C13D831ec7
#   USDT-BNB   0x55d398326f99059fF775485246999027B3197955
#   USDT-SOL   Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB
#
# --------------------------------------------------------------------------
# LA CLE NE SORT PAS DE VOTRE MACHINE
# --------------------------------------------------------------------------
#
# Lue dans local.properties, jamais affichee. La sortie ne contient que des
# tickers, des reseaux et des adresses publiques.
# ==========================================================================

set -uo pipefail

TABLE="app/src/main/java/com/vaultex/domain/swap/FournisseurSimpleSwap.kt"
PROPS="local.properties"

[ -f "$TABLE" ] || { echo "ERREUR : $TABLE introuvable. Lancez depuis la racine." >&2; exit 1; }
[ -f "$PROPS" ] || { echo "ERREUR : $PROPS introuvable. Lancez depuis la racine." >&2; exit 1; }

CLE=$(grep -E '^[[:space:]]*simpleswap\.key[[:space:]]*=' "$PROPS" | head -1 | cut -d'=' -f2- | tr -d ' \r\n')
[ -n "$CLE" ] || { echo "ERREUR : aucune ligne 'simpleswap.key=...' dans $PROPS." >&2; exit 1; }

# Les paires « "CLE" -> "ticker" » de la table. Le filtre sur les guillemets
# ecarte le repli `else -> token.lowercase()`, qui n'est pas un ticker.
PAIRES=$(sed -n 's/^[[:space:]]*"\([A-Z0-9-]*\)"[[:space:]]*->[[:space:]]*"\([a-z0-9-]*\)".*/\1 \2/p' "$TABLE")

NB=$(printf '%s\n' "$PAIRES" | grep -c . || true)
if [ "$NB" -eq 0 ]; then
  echo "ERREUR : aucune paire lue dans $TABLE." >&2
  echo "         La table a change de forme : c'est le script a corriger," >&2
  echo "         pas une reponse sur les tickers." >&2
  exit 1
fi

echo
echo "  $NB tickers lus dans FournisseurSimpleSwap.kt — interrogation du catalogue…"
echo

REPONSE=$(curl -s --max-time 30 \
  "https://api.simpleswap.io/get_all_currencies?api_key=${CLE}" 2>/dev/null)

[ -n "$REPONSE" ] || { echo "  Aucune reponse de SimpleSwap. On ne conclut RIEN." >&2; exit 1; }

PREMIER=$(printf '%s' "$REPONSE" | tr -d ' \t\r\n' | cut -c1)
if [ "$PREMIER" != "[" ]; then
  echo "  SimpleSwap n'a pas rendu un catalogue (cle, debit, ou panne)." >&2
  echo "  Le detail n'est pas affiche : il peut contenir la cle." >&2
  exit 1
fi

TMP=$(mktemp 2>/dev/null || printf '%s' "${TMPDIR:-/tmp}/sswaptable.$$")
trap 'rm -f "$TMP"' EXIT
printf '%s' "$REPONSE" | sed 's/},[[:space:]]*{/}\n{/g' > "$TMP"

# --------------------------------------------------------------------------
# UNE SEULE PASSE, ET UN SEUL PROCESSUS POUR LIRE LE CATALOGUE
# --------------------------------------------------------------------------
#
# La premiere version relisait TOUT le catalogue pour CHACUN des dix-huit
# tickers, en lancant quatre `sed` par ligne. Sur un catalogue de plusieurs
# centaines de monnaies, cela fait des dizaines de milliers de processus.
#
# MESURE SUR APPAREIL : sous Git Bash, lancer un processus coute cher, et le
# script n'avait affiche que seize lignes sur dix-huit quand l'utilisateur
# l'a interrompu. Il ne plantait pas — il ramait, ce qui se voit pareil et
# se corrige autrement.
#
# `awk` fait maintenant tout le decoupage en UN processus et rend des lignes
# « symbole|reseau|contrat ». Le reste est du bash pur, sans aucun fork.
APLAT=$(awk '
  function val(s, k,   r, p) {
    p = index(s, "\"" k "\"")
    if (p == 0) return ""
    r = substr(s, p + length(k) + 2)
    p = index(r, ":")
    if (p == 0) return ""
    r = substr(r, p + 1)
    sub(/^[ \t]+/, "", r)
    # Pas de guillemet : la valeur est null ou un nombre, pas une chaine.
    # Sans ce test, on sauterait au guillemet du champ SUIVANT et on
    # recopierait sa valeur — un contrat attribue a la mauvaise monnaie.
    if (substr(r, 1, 1) != "\"") return ""
    r = substr(r, 2)
    p = index(r, "\"")
    if (p == 0) return ""
    return substr(r, 1, p - 1)
  }
  {
    s = val($0, "symbol")
    if (s == "") next
    printf "%s|%s|%s\n", s, val($0, "network"), val($0, "contract_address")
  }
' "$TMP")

# UN APLAT VIDE DECLARERAIT LES DIX-HUIT TICKERS ABSENTS, et c'est la pire
# sortie possible : une fausse alerte qui fait corriger du code juste. Si
# `awk` manque ou si le format a change, on le dit et on s'arrete.
NB_PLAT=$(printf '%s\n' "$APLAT" | grep -c . || true)
if [ "$NB_PLAT" -lt 10 ]; then
  echo "  Catalogue illisible : $NB_PLAT monnaie(s) extraite(s)." >&2
  echo "  Ce n'est PAS une reponse sur les tickers — c'est le decoupage qui" >&2
  echo "  a echoue (awk absent, ou format change). Ne corrigez rien." >&2
  exit 1
fi

printf '  %-11s %-14s %-9s %-11s %s\n' CLE TICKER ETAT RESEAU CONTRAT
echo "  ----------------------------------------------------------------------"

# La liste passe par un FICHIER et non par un tube : dans un tube la boucle
# tourne dans un sous-shell, et ABSENTS y mourrait — le script rendrait 0
# meme en ayant trouve un ticker absent. Un verificateur qui ne sait pas
# dire qu'il a echoue ne verifie rien.
LISTE=$(mktemp 2>/dev/null || printf '%s' "${TMPDIR:-/tmp}/sswappaires.$$")
PLAT=$(mktemp 2>/dev/null || printf '%s' "${TMPDIR:-/tmp}/sswapplat.$$")
trap 'rm -f "$TMP" "$LISTE" "$PLAT"' EXIT
printf '%s\n' "$PAIRES" > "$LISTE"
printf '%s\n' "$APLAT" > "$PLAT"

ABSENTS=0
while read -r cle ticker; do
  [ -n "$ticker" ] || continue
  trouve=""
  while IFS='|' read -r sym net ctr; do
    [ "$sym" = "$ticker" ] || continue
    trouve="oui"
    printf '  %-11s %-14s %-9s %-11s %s\n' \
      "$cle" "$ticker" "TROUVE" "${net:--}" "${ctr:-(natif ou absent)}"
    break
  done < "$PLAT"

  if [ -z "$trouve" ]; then
    printf '  %-11s %-14s %-9s %s\n' "$cle" "$ticker" "ABSENT" "<<< a corriger"
    ABSENTS=$((ABSENTS + 1))
  fi
done < "$LISTE"

echo "  ----------------------------------------------------------------------"
echo
if [ "$ABSENTS" -gt 0 ]; then
  echo "  $ABSENTS ticker(s) ABSENT(S) du catalogue — le devis echouera avec"
  echo "  « Not Found » pour ces monnaies. Aucune perte possible : sans devis,"
  echo "  il n'y a pas d'adresse de depot."
  echo
fi
echo "  TROUVE ne veut pas dire JUSTE. Le ticker existe, il reste a verifier"
echo "  qu'il designe LA BONNE monnaie : lisez le RESEAU et le CONTRAT, pas"
echo "  le nom. « usdt » tout court existe aussi, et c'est Tether Omni."
echo
exit $([ "$ABSENTS" -gt 0 ] && echo 1 || echo 0)
