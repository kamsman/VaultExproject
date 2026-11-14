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
# IL ATTRAPE AUSSI, DESORMAIS, le ticker qui existe et designe autre chose.
# « usdt » tout court existe : c'est Tether Omni. Un ticker juste de nom et
# faux de chaine rend une adresse de depot valide, et les fonds partent.
#
# C'ETAIT A L'OEIL, ET C'EST POUR CA QUE CA NE SE FAISAIT PAS. L'en-tete
# affichait trois contrats de reference et demandait de lire dix-huit lignes
# contre eux. Les neuf jetons Ethereum n'y figuraient pas du tout : les
# verifier voulait dire chercher neuf contrats ailleurs, a la main, et la
# verification est restee a faire pendant des semaines. Un controle qu'on
# reporte ne protege de rien.
#
# Le script lit donc maintenant le contrat ATTENDU dans le registre de
# SwapViewModel.kt — la seule liste que l'ecran de swap consulte — et le
# compare lui-meme a celui du catalogue. La colonne ETAT dit :
#
#   JUSTE      le contrat du catalogue est celui du registre. Rien a faire.
#   ECART      le ticker existe et designe UNE AUTRE monnaie. <<< DANGER :
#              c'est le seul etat qui peut faire partir des fonds.
#   SANS CTR   le registre ne porte pas de contrat pour cette cle (monnaie
#              native : BTC, ETH, BNB, SOL, TRX, et l'USDT Tron). Le reseau
#              reste a lire a l'oeil.
#   ABSENT     le ticker n'existe pas. Le devis echouera, aucune perte.
#
# La comparaison ignore la casse : une adresse EVM s'ecrit avec ou sans
# somme de controle, et « 0xdAC17… » et « 0xdac17… » sont la meme adresse.
# Comparer les chaines telles quelles aurait rendu dix-huit faux ECART.
#
# --------------------------------------------------------------------------
# ESSAYER LA COMPARAISON SANS APPELER SIMPLESWAP
# --------------------------------------------------------------------------
#
#   SSWAP_CATALOGUE=chemin/vers/catalogue.json ./tools/simpleswap-verifier-table.sh
#
# Le catalogue est alors lu dans ce fichier au lieu d'etre demande a l'API,
# et la cle devient inutile. C'est ainsi que la comparaison a ete testee —
# sur un catalogue juste ET sur un catalogue fausse — plutot que relue.
#
# Un verificateur qu'on ne peut pas verifier n'est qu'une opinion de plus.
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
REGISTRE="app/src/main/java/com/vaultex/ui/viewmodel/SwapViewModel.kt"
SOLANA="app/src/main/java/com/vaultex/core/tx/SolanaSpl.kt"
PROPS="local.properties"
CATALOGUE="${SSWAP_CATALOGUE:-}"

[ -f "$TABLE" ] || { echo "ERREUR : $TABLE introuvable. Lancez depuis la racine." >&2; exit 1; }
[ -f "$REGISTRE" ] || { echo "ERREUR : $REGISTRE introuvable. Lancez depuis la racine." >&2; exit 1; }

# LA CLE N'EST EXIGEE QUE SI L'ON APPELLE L'API. Avec SSWAP_CATALOGUE, le
# catalogue vient d'un fichier : reclamer la cle interdirait d'essayer la
# comparaison sur une machine qui ne l'a pas.
CLE=""
if [ -z "$CATALOGUE" ]; then
  [ -f "$PROPS" ] || { echo "ERREUR : $PROPS introuvable. Lancez depuis la racine." >&2; exit 1; }
  CLE=$(grep -E '^[[:space:]]*simpleswap\.key[[:space:]]*=' "$PROPS" | head -1 | cut -d'=' -f2- | tr -d ' \r\n')
  [ -n "$CLE" ] || { echo "ERREUR : aucune ligne 'simpleswap.key=...' dans $PROPS." >&2; exit 1; }
elif [ ! -f "$CATALOGUE" ]; then
  echo "ERREUR : SSWAP_CATALOGUE pointe sur un fichier absent : $CATALOGUE" >&2
  exit 1
fi

# --------------------------------------------------------------------------
# LE CONTRAT ATTENDU, LU DANS LE REGISTRE DE L'ECRAN DE SWAP
# --------------------------------------------------------------------------
#
# C'est SwapViewModel.kt qui decide quelle monnaie l'ecran propose et sur
# quel contrat il la lit. C'est donc LUI la reference, et pas une liste
# recopiee dans ce script — une liste recopiee se desynchronise, et elle se
# desynchronise sans bruit.
#
# `contract = com.vaultex.core.tx.SolanaSpl.MINT_USDT` n'est pas une chaine
# litterale : la constante est resolue depuis SolanaSpl.kt, sinon l'USDT de
# Solana serait le seul jeton a contrat que la comparaison sauterait — et
# c'est justement celui qui a deja coute une panne en production.
MINT_USDT=$(sed -n 's/.*MINT_USDT[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' "$SOLANA" | head -1)

ATTENDUS=$(awk -v mint="$MINT_USDT" '
  /SwapAsset\("/ {
    if (match($0, /SwapAsset\("[A-Z0-9-]+"/)) cle = substr($0, RSTART + 11, RLENGTH - 12)
  }
  /contract[ \t]*=[ \t]*"/ {
    if (cle != "" && match($0, /contract[ \t]*=[ \t]*"[^"]+"/)) {
      v = substr($0, RSTART, RLENGTH)
      sub(/^contract[ \t]*=[ \t]*"/, "", v); sub(/"$/, "", v)
      print cle "|" v; cle = ""
    }
  }
  /contract[ \t]*=[ \t]*com\.vaultex\.core\.tx\.SolanaSpl\.MINT_USDT/ {
    if (cle != "" && mint != "") { print cle "|" mint; cle = "" }
  }
' "$REGISTRE")

# --------------------------------------------------------------------------
# L'USDT DE TRON N'EST PAS DANS LE REGISTRE, ET IL NE DOIT PAS Y ETRE
# --------------------------------------------------------------------------
#
# C'est l'actif PRINCIPAL de l'activite, et c'etait la seule ligne a contrat
# que la comparaison ne pouvait pas trancher : sa SwapAsset ne porte pas de
# champ `contract`.
#
# LE LUI AJOUTER CASSERAIT QUELQUE CHOSE. `tokenEntityFor`, dans le meme
# fichier, fabrique a partir de ce champ une TokenEntity dont la blockchain
# vaut « ETH » ou « BNB » — jamais « TRX ». On enregistrerait donc dans le
# portefeuille un faux jeton Ethereum portant une adresse Tron. C'est
# exactement le defaut que ce fichier decrit deja pour USDT-SOL, et c'est
# pour cette raison que les variantes d'USDT y sont explicitement exclues.
#
# La reference est donc prise la ou elle est VRAIMENT porteuse :
# SendCryptoUseCase.USDT_TRC20_CONTRACT, la constante qui part sur le reseau
# quand on envoie des USDT Tron. Si elle etait fausse, les envois le
# seraient — elle ne peut pas pourrir en silence.
ENVOI="app/src/main/java/com/vaultex/domain/usecase/SendCryptoUseCase.kt"
if [ -f "$ENVOI" ]; then
  USDT_TRC20=$(sed -n 's/.*USDT_TRC20_CONTRACT[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' "$ENVOI" | head -1)
  [ -n "$USDT_TRC20" ] && ATTENDUS="$ATTENDUS
USDT|$USDT_TRC20"
fi

NB_ATTENDUS=$(printf '%s\n' "$ATTENDUS" | grep -c . || true)
if [ "$NB_ATTENDUS" -eq 0 ]; then
  echo "ERREUR : aucun contrat lu dans $REGISTRE." >&2
  echo "         Le registre a change de forme : c'est ce script a corriger." >&2
  echo "         Sans reference, la comparaison dirait JUSTE sur tout — donc" >&2
  echo "         on s'arrete plutot que de rassurer a tort." >&2
  exit 1
fi

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
echo "  $NB tickers lus dans FournisseurSimpleSwap.kt,"
echo "  $NB_ATTENDUS contrats de reference lus dans le code de l'app."
if [ -n "$CATALOGUE" ]; then
  echo "  Catalogue lu dans $CATALOGUE (SimpleSwap n'est PAS appele)."
else
  echo "  Interrogation du catalogue SimpleSwap…"
fi
echo

if [ -n "$CATALOGUE" ]; then
  REPONSE=$(cat "$CATALOGUE")
else
  REPONSE=$(curl -s --max-time 30 \
    "https://api.simpleswap.io/get_all_currencies?api_key=${CLE}" 2>/dev/null)
fi

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
echo "  ----------------------------------------------------------------------------"

# La liste passe par un FICHIER et non par un tube : dans un tube la boucle
# tourne dans un sous-shell, et ABSENTS y mourrait — le script rendrait 0
# meme en ayant trouve un ticker absent. Un verificateur qui ne sait pas
# dire qu'il a echoue ne verifie rien.
LISTE=$(mktemp 2>/dev/null || printf '%s' "${TMPDIR:-/tmp}/sswappaires.$$")
PLAT=$(mktemp 2>/dev/null || printf '%s' "${TMPDIR:-/tmp}/sswapplat.$$")
trap 'rm -f "$TMP" "$LISTE" "$PLAT"' EXIT
REF=$(mktemp 2>/dev/null || printf '%s' "${TMPDIR:-/tmp}/sswapref.$$")
trap 'rm -f "$TMP" "$LISTE" "$PLAT" "$REF"' EXIT
printf '%s\n' "$PAIRES" > "$LISTE"
printf '%s\n' "$APLAT" > "$PLAT"
printf '%s\n' "$ATTENDUS" > "$REF"

# `tr` plutot que ${x,,} : cette derniere forme est du bash 4, et le bash de
# Git pour Windows n'est pas garanti de l'etre. Un script de verification qui
# ne demarre pas sur la machine de deploiement ne verifie rien.
minuscules() { printf '%s' "$1" | tr '[:upper:]' '[:lower:]'; }

ABSENTS=0
ECARTS=0
SANS_REF=0
while read -r cle ticker; do
  [ -n "$ticker" ] || continue

  # Le contrat que le registre attend pour cette cle, s'il en porte un.
  attendu=""
  while IFS='|' read -r rcle rctr; do
    [ "$rcle" = "$cle" ] || continue
    attendu="$rctr"
    break
  done < "$REF"

  trouve=""
  while IFS='|' read -r sym net ctr; do
    [ "$sym" = "$ticker" ] || continue
    trouve="oui"
    if [ -z "$attendu" ]; then
      etat="SANS CTR"
      SANS_REF=$((SANS_REF + 1))
      suffixe="${ctr:-(natif)}"
    elif [ "$(minuscules "$ctr")" = "$(minuscules "$attendu")" ]; then
      etat="JUSTE"
      suffixe="$ctr"
    else
      etat="ECART"
      ECARTS=$((ECARTS + 1))
      suffixe="${ctr:-(aucun)}  <<< attendu ${attendu}"
    fi
    printf '  %-11s %-14s %-9s %-11s %s\n' \
      "$cle" "$ticker" "$etat" "${net:--}" "$suffixe"
    break
  done < "$PLAT"

  if [ -z "$trouve" ]; then
    printf '  %-11s %-14s %-9s %s\n' "$cle" "$ticker" "ABSENT" "<<< a corriger"
    ABSENTS=$((ABSENTS + 1))
  fi
done < "$LISTE"

echo "  ----------------------------------------------------------------------------"
echo
if [ "$ECARTS" -gt 0 ]; then
  echo "  /!\\ $ECARTS ECART(S) — UN TICKER DESIGNE UNE AUTRE MONNAIE QUE LA NOTRE."
  echo "  C'est le seul etat dangereux de cette sortie : le devis aboutit, une"
  echo "  adresse de depot valide s'affiche, et les fonds partent vers le mauvais"
  echo "  jeton. Corrigez la table AVANT de distribuer une version."
  echo
fi
if [ "$ABSENTS" -gt 0 ]; then
  echo "  $ABSENTS ticker(s) ABSENT(S) du catalogue — le devis echouera avec"
  echo "  « Not Found » pour ces monnaies. Aucune perte possible : sans devis,"
  echo "  il n'y a pas d'adresse de depot."
  echo
fi
if [ "$SANS_REF" -gt 0 ]; then
  echo "  $SANS_REF ligne(s) SANS CTR : le registre ne porte pas de contrat pour"
  echo "  elles — ce sont les monnaies natives : BTC, ETH, BNB, SOL, TRX. Une"
  echo "  monnaie native n'a pas de contrat, il n'y a donc rien a comparer."
  echo "  Pour ces cinq lignes, et pour elles seules, lisez la colonne RESEAU"
  echo "  a l'oeil : btc, eth, bsc, sol, trx."
  echo
fi
if [ "$ECARTS" -eq 0 ] && [ "$ABSENTS" -eq 0 ]; then
  echo "  Aucun ecart, aucun ticker absent. Les contrats du catalogue sont ceux"
  echo "  du registre, compares un a un — et non plus supposes."
  echo
fi
exit $([ $((ECARTS + ABSENTS)) -gt 0 ] && echo 1 || echo 0)
