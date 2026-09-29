#!/usr/bin/env bash
# ==========================================================================
# VaultEx — Le Pi est-il devenu echangeable ?
# ==========================================================================
#
#   ./tools/pi-disponible.sh
#
# Repond a UNE question, celle qui decide de tout : peut-on transformer du PI
# en USDT sans depositaire ? Tant que la reponse est non, integrer le reseau
# Pi dans VaultEx donnerait une adresse, un solde affiche et un bouton
# « Envoyer » sans aucun moyen de convertir — des fonds visibles qui ne
# bougent pas.
#
# Elle se teste desormais par DEUX chemins, et il suffit qu'un seul reponde :
#
#   1. Un courtier instantane ouvre-t-il une paire depuis le PI ? C'est le
#      modele que VaultEx utilise deja pour tout le reste.
#
#   2. La chaine Pi elle-meme permet-elle l'echange ? Son protocole 27, actif
#      depuis le 15 septembre 2026, a apporte un carnet d'ordres, un AMM et
#      un RPC public. Cette voie n'existait pas quand ce script a ete ecrit.
#
# Ce qui ne repond PAS a la question, et qu'on ne teste donc pas ici : les
# rampes fiat verifiees par Pi (TransFi, Onramper, Onramp Money, Banxa).
# Elles vendent le Pi contre de la monnaie locale, pas contre de l'USDT ;
# elles exigent une identite, que VaultEx n'a jamais demandee ; et aucune ne
# regle en XOF. Deux sauts, deux commissions, deux KYC, et pas un franc CFA.
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

# --------------------------------------------------------------------------
# LES CATALOGUES OUVERTS — SANS CLE, DONC SANS COMPTE
# --------------------------------------------------------------------------
#
# Ces trois-la publient leur liste de monnaies sans authentification. On n'y
# verifie donc pas une paire mais une PRESENCE : tant que PI n'est meme pas au
# catalogue, la question des paires ne se pose pas.
#
# Ils servent aussi a autre chose. Le jour ou SimpleSwap tombe ou revoque la
# cle, l'application n'a plus d'echange du tout. Savoir lesquels repondent
# sans compte, c'est connaitre ses recours.
echo
echo "  PI est-il seulement au CATALOGUE ailleurs ?"
for entree in \
  "exolix|https://exolix.com/api/v2/currencies?search=pi&size=100" \
  "godex|https://api.godex.io/api/v1/coins" \
  "stealthex|https://api.stealthex.io/api/v2/currency" ; do
  nom="${entree%%|*}"
  url="${entree#*|}"
  rep=$(curl -s --max-time 25 "$url" 2>/dev/null)
  # Un catalogue injoignable renvoyait une chaine vide, donc « absent » :
  # exactement la reponse rassurante. Une panne de reseau se lisait comme
  # une confirmation que Pi n'est toujours pas la. On les separe.
  if [ -z "$rep" ]; then
    printf "    %-12s SANS REPONSE — rien conclu, relancer\n" "$nom"
  elif printf '%s' "$rep" | grep -qoiE '"(code|ticker|symbol)":"pi"'; then
    printf "    %-12s PRESENT — verifier si une paire est ouverte\n" "$nom"
    trouve=1
  else
    printf "    %-12s absent\n" "$nom"
  fi
done

# --------------------------------------------------------------------------
# LA CHAINE PI ELLE-MEME — DEPUIS PROTOCOLE 27
# --------------------------------------------------------------------------
#
# Le 15 septembre 2026, Pi a active son protocole 27 : carnet d'ordres, AMM,
# et surtout un RPC public. La chaine est un derive de Stellar, donc son API
# est une Horizon : les memes chemins, les memes reponses.
#
# Cela ouvre une troisieme voie, qui n'existait pas quand ce script a ete
# ecrit. Jusqu'ici la seule question etait « un courtier accepte-t-il le
# PI ? ». Desormais il y en a une autre : « peut-on echanger SUR Pi ? »
#
# Elle se decompose en deux, et les deux doivent etre vraies :
#
#   1. Un USDT existe-t-il sur le mainnet Pi ? Sans actif en face, un carnet
#      d'ordres ne sert a rien. On le demande a /assets.
#
#   2. Ce carnet a-t-il de la liquidite ? Une paire ouverte sans profondeur
#      affiche un prix et ne remplit aucun ordre. On le demande a /order_book.
#
# ATTENTION AU FAUX ESPOIR. Meme si les deux repondent oui, l'USDT obtenu
# serait un USDT EMIS SUR PI — enferme dans Pi, inutilisable sur BNB Chain,
# et sans rapport avec celui que VaultEx detient. Il faudrait encore un pont
# pour en sortir. Ce test dit si la premiere marche existe, pas si l'escalier
# est complet.
PI_API="https://api.mainnet.minepi.com"
echo
echo "  LA CHAINE PI PERMET-ELLE D'ECHANGER SUR PLACE ?"

racine=$(curl -s --max-time 20 "$PI_API/" 2>/dev/null)
if [ -z "$racine" ]; then
  printf "    %-12s SANS REPONSE — rien conclu, relancer\n" "rpc"
else
  printf "    %-12s repond\n" "rpc"

  # Les actifs non natifs. Un reseau sans aucun actif emis n'a pas de
  # marche possible, quelle que soit la qualite de son carnet d'ordres.
  actifs=$(curl -s --max-time 20 "$PI_API/assets?limit=200" 2>/dev/null)
  nb=$(printf '%s' "$actifs" | grep -oE '"asset_code"' | wc -l | tr -d ' ')
  printf "    %-12s %s actif(s) emis sur la chaine\n" "actifs" "${nb:-0}"

  # Un USDT, nomme comme tel. On imprime l'emetteur : sur un derive de
  # Stellar, n'importe qui peut emettre un actif appele USDT, et seul
  # l'emetteur distingue le vrai du faux.
  usdt=$(curl -s --max-time 20 "$PI_API/assets?asset_code=USDT" 2>/dev/null)
  emetteurs=$(printf '%s' "$usdt" | grep -oE '"asset_issuer":"[A-Z0-9]+"' | cut -d'"' -f4 | sort -u)
  if [ -n "$emetteurs" ]; then
    for e in $emetteurs; do
      printf "    %-12s USDT emis par %s\n" "usdt" "$e"
      # Profondeur du carnet PI / cet USDT. Sans ordre d'achat, personne
      # n'achete le Pi : le marche existe sur le papier seulement.
      carnet=$(curl -s --max-time 20 \
        "$PI_API/order_book?selling_asset_type=native&buying_asset_type=credit_alphanum4&buying_asset_code=USDT&buying_asset_issuer=$e" 2>/dev/null)
      offres=$(printf '%s' "$carnet" | grep -oE '"amount"' | wc -l | tr -d ' ')
      if [ "${offres:-0}" -gt 0 ]; then
        printf "    %-12s carnet PI/USDT : %s ordre(s)\n" "" "$offres"
        trouve=1
      else
        printf "    %-12s carnet PI/USDT vide\n" ""
      fi
    done
  else
    printf "    %-12s aucun USDT sur la chaine Pi\n" "usdt"
  fi
fi

echo
if [ "$trouve" = "1" ]; then
  echo "  >>> UNE PAIRE EST OUVERTE. L'integration Pi redevient utile."
  echo "      La derivation de cles et l'adresse sont deja ecrites et"
  echo "      verifiees : voir PiWallet.kt. Restent le solde (API Horizon)"
  echo "      et la signature de transactions (XDR)."
else
  echo "  >>> Toujours aucune paire, et rien a echanger sur la chaine Pi."
  echo "      Ne rien integrer. La reponse a donner aux utilisateurs reste"
  echo "      le chemin Pi Wallet -> OKX -> USDT-BEP20 -> VaultEx :"
  echo "      voir tools/annonces/guide-pi-vers-usdt.md"
  echo
  echo "      Les rampes fiat (TransFi, Onramper, Onramp Money) sont"
  echo "      verifiees par Pi mais ne repondent pas a la question : elles"
  echo "      vendent le Pi contre de la MONNAIE LOCALE, pas contre de"
  echo "      l'USDT, exigent une identite, et aucune ne regle en XOF."
fi
echo
