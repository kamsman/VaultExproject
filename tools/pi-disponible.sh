#!/usr/bin/env bash
# ==========================================================================
# VaultEx — Le Pi est-il devenu echangeable ?
# ==========================================================================
#
#   ./tools/pi-disponible.sh
#
# Repond a UNE question, celle qui decide de tout : un echangeur ouvre-t-il
# enfin une paire depuis le PI ? Tant que la reponse est non, integrer le
# reseau Pi dans VaultEx donnerait une adresse, un solde affiche et un bouton
# « Envoyer » sans aucun moyen de convertir — des fonds visibles qui ne
# bougent pas.
#
# --------------------------------------------------------------------------
# POURQUOI UN SCRIPT PLUTOT QU'UNE REPONSE ECRITE QUELQUE PART
# --------------------------------------------------------------------------
#
# Une paire fermee s'ouvre quand la liquidite arrive. La reponse du 28
# septembre 2026 — sept destinations testees chez SimpleSwap, sept refus, et
# PI absent des monnaies actives de ChangeNOW — vaut pour ce jour-la et pour
# aucun autre.
#
# Ecrire « Pi n'est pas echangeable » dans un document, c'est publier une
# information qui deviendra fausse sans que personne ne s'en apercoive. Un
# script, lui, redit la verite du jour ou on le lance.
#
# --------------------------------------------------------------------------
# LES CLES NE SORTENT PAS DE VOTRE MACHINE
# --------------------------------------------------------------------------
#
# Lues dans local.properties, jamais affichees, ni dans la sortie ni dans une
# erreur. Le resultat imprime ne contient que des tickers et des nombres.
# ==========================================================================

set -uo pipefail

PROPS="local.properties"
if [ ! -f "$PROPS" ]; then
  echo "ERREUR : $PROPS introuvable. Lancez ce script depuis la racine du projet." >&2
  exit 1
fi

lire() { grep -E "^[[:space:]]*$1[[:space:]]*=" "$PROPS" | head -1 | cut -d'=' -f2- | tr -d ' \r\n'; }

CLE_SS=$(lire 'simpleswap\.key')
CLE_CN=$(lire 'changenow\.key')

# Les destinations qui comptent pour VaultEx. La premiere est la seule qui
# compte vraiment : c'est « je transforme mon Pi en argent utilisable ».
DESTINATIONS_SS="usdtbep20 usdttrc20 usdterc20 btc eth bnb-bsc trx sol"
DESTINATIONS_CN="usdtbsc usdttrc20 usdterc20 btc eth bnbbsc trx sol"

trouve=0

echo
echo "  PI → ... chez SIMPLESWAP"
if [ -z "$CLE_SS" ]; then
  echo "    (pas de cle simpleswap.key — ignore)"
else
  for v in $DESTINATIONS_SS; do
    rep=$(curl -s --max-time 15 \
      "https://api.simpleswap.io/get_ranges?api_key=${CLE_SS}&currency_from=pi&currency_to=${v}&fixed=false" 2>/dev/null)
    min=$(printf '%s' "$rep" | sed -n 's/.*"min"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
    if [ -n "$min" ] && [ "$min" != "null" ]; then
      printf "    %-12s OUVERTE — minimum %s PI\n" "$v" "$min"
      trouve=1
    else
      printf "    %-12s fermee\n" "$v"
    fi
    sleep 0.35
  done
fi

echo
echo "  PI → ... chez CHANGENOW"
if [ -z "$CLE_CN" ]; then
  echo "    (pas de cle changenow.key — ignore)"
else
  for v in $DESTINATIONS_CN; do
    rep=$(curl -s --max-time 15 \
      "https://api.changenow.io/v1/min-amount/pi_${v}?api_key=${CLE_CN}" 2>/dev/null)
    min=$(printf '%s' "$rep" | sed -n 's/.*"minAmount"[[:space:]]*:[[:space:]]*\([0-9.]*\).*/\1/p')
    if [ -n "$min" ]; then
      printf "    %-12s OUVERTE — minimum %s PI\n" "$v" "$min"
      trouve=1
    else
      printf "    %-12s fermee\n" "$v"
    fi
    sleep 0.35
  done
fi

echo
if [ "$trouve" = "1" ]; then
  echo "  >>> UNE PAIRE EST OUVERTE. L'integration Pi redevient utile."
  echo "      La derivation de cles et l'adresse sont deja ecrites et"
  echo "      verifiees : voir PiWallet.kt. Restent le solde (API Horizon)"
  echo "      et la signature de transactions (XDR)."
else
  echo "  >>> Toujours aucune paire. Ne rien integrer."
  echo "      La reponse a donner aux utilisateurs reste le chemin"
  echo "      Pi Wallet -> OKX -> USDT-BEP20 -> VaultEx :"
  echo "      voir tools/annonces/guide-pi-vers-usdt.md"
fi
echo
