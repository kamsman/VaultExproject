#!/usr/bin/env bash
# ==========================================================================
# VaultEx — QUEL FOURNISSEUR DOGECOIN REPOND, ET DANS QUELLE FORME ?
# ==========================================================================
#
# UTILISATION (Git Bash, depuis la racine du projet) :
#
#   ./tools/doge-sondes.sh                      <-- commencez par celui-ci
#   ./tools/doge-sondes.sh DH5yaieqoZN36fDVciNyRueRGvGLR3mr7L
#
# L'argument est une adresse Dogecoin a interroger. Sans lui, la sonde
# emploie l'adresse du vecteur de test de DogeWalletTest, qui est VIDE.
#
# UNE ADRESSE VIDE NE PROUVE PAS LA MOITIE DE CE QU'ON CHERCHE. Elle dit
# « le service repond » ; elle ne dit pas quels champs porte une sortie non
# depensee, puisqu'il n'y en a aucune. Pour la seconde moitie, donnez une
# adresse qui DETIENT des DOGE — n'importe laquelle, copiee depuis un bloc
# recent sur un explorateur public. Elle n'a pas a etre la votre : on ne
# fait que lire.
#
# --------------------------------------------------------------------------
# POURQUOI CETTE SONDE EXISTE, ET POURQUOI ELLE TOURNE CHEZ VOUS
# --------------------------------------------------------------------------
#
# Envoyer du Dogecoin demande trois choses qu'aucune bibliotheque ne donne :
# lire les sorties non depensees d'une adresse, estimer des frais, et
# diffuser la transaction signee. Il faut donc un fournisseur, et celui de
# Bitcoin n'en est pas un — il ne sert pas cette chaine.
#
# LE CHOIX NE SE FAIT PAS SUR UNE PAGE DE DOCUMENTATION. Ce depot porte deja
# deux contre-exemples : `api.binance.com` repond 403 a un Worker
# Cloudflare et parfaitement a un navigateur, et sept noeuds BSC refusent
# `eth_getLogs` depuis cette meme IP en l'acceptant ailleurs. Maven Central
# a refuse ce conteneur le jour ou ces lignes ont ete ecrites.
#
# La seule mesure qui decrive ce que vivra l'application est celle faite
# depuis le reseau ou elle tourne. C'est pour ca que ce script s'execute sur
# VOTRE machine, au Burkina Faso, et pas la ou il a ete ecrit.
#
# --------------------------------------------------------------------------
# CE QU'ELLE MESURE, ET CE QU'ELLE NE MESURE PAS
# --------------------------------------------------------------------------
#
# ELLE MESURE : qui repond, en combien de temps, et si la reponse porte les
# champs dont un envoi a besoin. Les noms de champs sont AFFICHES plutot que
# supposes — si l'un d'eux s'appelle autrement, le tri ne leverait aucune
# erreur, il rendrait « aucune sortie trouvee » sur une adresse pleine, et
# on chercherait le defaut du cote de la chaine pendant des heures.
#
# ELLE NE MESURE PAS la diffusion. Pousser une transaction demande une
# transaction signee, donc le moteur d'envoi qui n'existe pas encore. La
# sonde se contente de dire si le point d'entree de diffusion EXISTE, en
# l'appelant avec un corps volontairement invalide : un service qui repond
# « transaction invalide » est un service qui diffuse, et c'est tout ce
# qu'on peut savoir a ce stade. AUCUNE transaction reelle n'est envoyee.
#
# ELLE NE DIT RIEN DES TARIFS NI DES QUOTAS. Ils changent, ils dependent du
# compte, et les lire ici reviendrait a les inventer. Allez les lire chez le
# fournisseur que la mesure aura retenu.
#
# --------------------------------------------------------------------------
# LES CLES
# --------------------------------------------------------------------------
#
# Seuls les services interrogeables SANS cle sont sondes par defaut : on ne
# veut pas ouvrir un compte chez quatre fournisseurs pour en choisir un.
# Ceux qui en exigent une sont nommes a la fin, sans etre appeles.
#
# Si vous avez deja une cle, donnez-la par l'environnement et la sonde
# l'emploiera :
#
#   NOWNODES_CLE=xxxx ./tools/doge-sondes.sh
#
# ==========================================================================

set -uo pipefail

ADRESSE="${1:-DBus3bamQjgJULBJtYXpEzDWQRwF5iwxgC}"
DELAI=20

echo
echo "  Adresse interrogee : $ADRESSE"
if [ "$ADRESSE" = "DBus3bamQjgJULBJtYXpEzDWQRwF5iwxgC" ]; then
  echo "  (adresse de test, VIDE — relancez avec une adresse qui detient des"
  echo "   DOGE pour voir la forme reelle d'une sortie non depensee)"
fi
echo

# --------------------------------------------------------------------------
# UNE REQUETE, SON TEMPS, ET LE DEBUT DE SA REPONSE
# --------------------------------------------------------------------------
#
# `curl -w` donne le code HTTP et la duree sans processus supplementaire.
# La reponse est TRONQUEE a l'affichage : certains catalogues rendent des
# centaines de kilo-octets, et noyer la sortie la rend illisible — or une
# sonde qu'on ne lit pas ne sert a rien.
sonder() {
  local nom="$1" url="$2" methode="${3:-GET}" corps="${4:-}"
  local sortie code duree
  if [ "$methode" = "POST" ]; then
    sortie=$(curl -sS -m "$DELAI" -w '\n%{http_code} %{time_total}' \
             -H 'content-type: application/json' -d "$corps" "$url" 2>&1)
  else
    sortie=$(curl -sS -m "$DELAI" -w '\n%{http_code} %{time_total}' "$url" 2>&1)
  fi
  code=$(printf '%s' "$sortie" | tail -1 | cut -d' ' -f1)
  # TRONQUEE AU CARACTERE, ET NON ARRONDIE PAR printf : « %.2f » depend de
  # LC_NUMERIC, et sur une machine en francais il attend une virgule — la
  # colonne se briserait la ou le script est justement cense tourner.
  duree=$(printf '%s' "$sortie" | tail -1 | cut -d' ' -f2 | cut -c1-5)
  local contenu etat temps
  contenu=$(printf '%s' "$sortie" | sed '$d')
  temps="${duree}s"

  case "$code" in
    200|201) etat="OK" ;;
    400|422) etat="REJET" ;;   # attendu, et souhaite, sur la diffusion
    000)     etat="MUET"; temps="—" ;;
    *)       etat="HTTP$code" ;;
  esac
  printf '  %-34s %-8s %7s  %s\n' \
    "$nom" "$etat" "$temps" "$(printf '%s' "$contenu" | tr -d '\n' | cut -c1-100)"
}

# --------------------------------------------------------------------------
# LES CANDIDATS SANS CLE
# --------------------------------------------------------------------------
#
# Blockbook est l'implementation que Trezor publie et fait tourner pour
# plusieurs chaines. Son interet ici : une SEULE forme d'API couvre les
# sorties non depensees, les frais et la diffusion, et c'est la meme forme
# que proposent plusieurs fournisseurs payants — donc changer de maison plus
# tard ne voudrait pas dire reecrire le moteur d'envoi.
echo "── Blockbook (Trezor) ────────────────────────────────────────────────"
sonder "blockbook / sorties non depensees" "https://doge1.trezor.io/api/v2/utxo/$ADRESSE"
sonder "blockbook / adresse"               "https://doge1.trezor.io/api/v2/address/$ADRESSE"
sonder "blockbook / frais 6 blocs"         "https://doge1.trezor.io/api/v2/estimatefee/6"
sonder "blockbook / diffusion (invalide)"  "https://doge1.trezor.io/api/v2/sendtx/00"
echo

echo "── BlockCypher ───────────────────────────────────────────────────────"
sonder "blockcypher / sorties"  "https://api.blockcypher.com/v1/doge/main/addrs/$ADRESSE?unspentOnly=true&includeScript=true"
sonder "blockcypher / chaine"   "https://api.blockcypher.com/v1/doge/main"
sonder "blockcypher / diffusion (invalide)" \
       "https://api.blockcypher.com/v1/doge/main/txs/push" POST '{"tx":"00"}'
echo

echo "── Blockchair ────────────────────────────────────────────────────────"
sonder "blockchair / adresse"   "https://api.blockchair.com/dogecoin/dashboards/address/$ADRESSE?limit=5"
sonder "blockchair / etat"      "https://api.blockchair.com/dogecoin/stats"
echo

# --------------------------------------------------------------------------
# CE QUI DEMANDE UNE CLE
# --------------------------------------------------------------------------
if [ -n "${NOWNODES_CLE:-}" ]; then
  echo "── NOWNodes (cle fournie) ────────────────────────────────────────────"
  sonder "nownodes / sorties" \
    "https://dogebook.nownodes.io/api/v2/utxo/$ADRESSE?api_key=${NOWNODES_CLE}"
  echo
else
  echo "── Non sondes, faute de cle ──────────────────────────────────────────"
  echo "  NOWNodes, GetBlock, Tatum : comptes a ouvrir. Plusieurs servent du"
  echo "  Blockbook, donc la MEME forme d'API que la premiere section — si"
  echo "  elle vous convient, en changer plus tard ne touche pas le moteur."
  echo "  Relancez avec NOWNODES_CLE=... pour en sonder un."
  echo
fi

cat <<'FIN'
  ─── COMMENT LIRE CECI ──────────────────────────────────────────────────

  OK sur « sorties non depensees » est le resultat qui compte : c'est la
  lecture dont depend tout envoi. Sans elle, le reste ne sert a rien.

  REJET sur une diffusion est un SUCCES : le corps « 00 » n'est pas une
  transaction, et un service qui le refuse en le disant est un service qui
  diffuse. C'est INJOIGNABLE ou un HTTP 404 qui seraient mauvais signe.

  LES DUREES COMPTENT AUTANT QUE LES CODES. Un envoi enchaine deux appels
  avant que l'utilisateur voie quoi que ce soit ; trois secondes chacun
  font six secondes d'ecran fige sur une connexion deja lente.

  RELANCEZ LA SONDE DEUX OU TROIS FOIS, a des moments differents. Un
  service qui repond une fois sur deux est pire qu'un service qui ne repond
  pas : la panne devient intermittente, donc introuvable.
FIN
