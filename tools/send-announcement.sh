#!/usr/bin/env bash
# ==========================================================================
# VaultEx — Envoi d'une annonce a TOUS les utilisateurs
# ==========================================================================
#
# UTILISATION (Git Bash, depuis la racine du projet) :
#
#   ./tools/send-announcement.sh "Titre" "Corps du message"
#
# Exemple :
#   ./tools/send-announcement.sh "Mise a jour" "La version 1.1 est disponible"
#
# --------------------------------------------------------------------------
# PREPARATION — a faire UNE SEULE FOIS
# --------------------------------------------------------------------------
#
# 1. Console Firebase -> icone engrenage -> Parametres du projet
# 2. Onglet « Comptes de service »
# 3. Bouton « Generer une nouvelle cle privee » -> un fichier .json se
#    telecharge
# 4. Renommer ce fichier `firebase-service-account.json` et le placer a la
#    racine du projet
#
# CE FICHIER EST UN SECRET. Il donne un acces complet a ton projet Firebase.
# Il est deja couvert par .gitignore — ne jamais le versionner, ne jamais
# l'envoyer par message.
#
# --------------------------------------------------------------------------
# POURQUOI CE SCRIPT PLUTOT QUE LA CONSOLE FIREBASE
# --------------------------------------------------------------------------
#
# La console n'envoie que des messages de type « notification ». Le SDK
# Firebase les affiche lui-meme et n'appelle jamais `onMessageReceived` : le
# contenu n'atteint donc JAMAIS le code de l'application, et rien ne peut etre
# inscrit dans la cloche. Verifie sur appareil — l'intention de lancement
# arrive sans aucun extra, meme quand l'utilisateur touche la notification.
#
# Ce script envoie un message « data » (aucun champ `notification`). Celui-ci
# passe TOUJOURS par `VaultExFcmService.onMessageReceived`, application
# ouverte, fermee ou en arriere-plan. L'annonce s'affiche ET reste consultable
# dans la cloche, meme si la notification est balayee sans etre lue.
#
# Il vise le canal `vaultex_all`, auquel chaque appareil s'abonne au demarrage
# (voir VaultExApplication.ANNOUNCE_TOPIC). Aucun serveur n'a besoin de
# connaitre les jetons individuels : pas de Cloud Function, donc pas de plan
# Blaze, donc pas de carte bancaire.
# ==========================================================================

set -euo pipefail

TITLE="${1:-}"
BODY="${2:-}"
SA_FILE="${SA_FILE:-firebase-service-account.json}"
TOPIC="vaultex_all"   # doit correspondre a VaultExApplication.ANNOUNCE_TOPIC

if [ -z "$TITLE" ] || [ -z "$BODY" ]; then
  echo "Usage : $0 \"Titre\" \"Corps du message\"" >&2
  exit 1
fi

if [ ! -f "$SA_FILE" ]; then
  echo "ERREUR : $SA_FILE introuvable." >&2
  echo "Voir l'en-tete de ce script, section PREPARATION." >&2
  exit 1
fi

# ─── Lecture du compte de service ─────────────────────────────────────────
# Extraction sans jq, qui n'est pas garanti present dans Git Bash.
PROJECT_ID=$(grep -o '"project_id"[[:space:]]*:[[:space:]]*"[^"]*"' "$SA_FILE" | head -1 | sed 's/.*"\([^"]*\)"$/\1/')
CLIENT_EMAIL=$(grep -o '"client_email"[[:space:]]*:[[:space:]]*"[^"]*"' "$SA_FILE" | head -1 | sed 's/.*"\([^"]*\)"$/\1/')
PRIVATE_KEY_ESC=$(grep -o '"private_key"[[:space:]]*:[[:space:]]*"[^"]*"' "$SA_FILE" | head -1 | sed 's/^"private_key"[[:space:]]*:[[:space:]]*"//; s/"$//')

if [ -z "$PROJECT_ID" ] || [ -z "$CLIENT_EMAIL" ] || [ -z "$PRIVATE_KEY_ESC" ]; then
  echo "ERREUR : $SA_FILE ne ressemble pas a une cle de compte de service." >&2
  exit 1
fi

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
# La cle est stockee avec des \n litteraux dans le JSON : on les restaure.
printf '%b\n' "$PRIVATE_KEY_ESC" > "$TMP/key.pem"

# ─── base64url, sans retour a la ligne ni caractere de remplissage ────────
b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }

# ─── Jeton d'acces OAuth2, obtenu par JWT signe (RS256) ──────────────────
NOW=$(date +%s)
EXP=$((NOW + 3600))
HEADER=$(printf '{"alg":"RS256","typ":"JWT"}' | b64url)
CLAIM=$(printf '{"iss":"%s","scope":"https://www.googleapis.com/auth/firebase.messaging","aud":"https://oauth2.googleapis.com/token","exp":%s,"iat":%s}' \
  "$CLIENT_EMAIL" "$EXP" "$NOW" | b64url)
SIG=$(printf '%s.%s' "$HEADER" "$CLAIM" | openssl dgst -sha256 -sign "$TMP/key.pem" | b64url)
JWT="$HEADER.$CLAIM.$SIG"

ACCESS_TOKEN=$(curl -s -X POST https://oauth2.googleapis.com/token \
  -d "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer" \
  --data-urlencode "assertion=$JWT" \
  | grep -o '"access_token"[[:space:]]*:[[:space:]]*"[^"]*"' | sed 's/.*"\([^"]*\)"$/\1/')

if [ -z "$ACCESS_TOKEN" ]; then
  echo "ERREUR : impossible d'obtenir un jeton d'acces." >&2
  echo "Verifie que $SA_FILE est bien la cle du projet $PROJECT_ID." >&2
  exit 1
fi

# ─── Echappement JSON du titre et du corps ────────────────────────────────
json_escape() { printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g' | tr -d '\r' | sed ':a;N;$!ba;s/\n/\\n/g'; }
TITLE_J=$(json_escape "$TITLE")
BODY_J=$(json_escape "$BODY")

# `key` sert a la deduplication cote application : deux envois du meme
# contenu a quelques minutes d'intervalle ne produiront qu'une entree dans la
# cloche. L'horodatage la rend unique pour une annonce reellement nouvelle.
MSG_KEY="announce:$NOW"

# AUCUN champ `notification` : c'est ce qui force le passage par
# onMessageReceived. En ajouter un ferait retomber dans le probleme d'origine.
PAYLOAD=$(printf '{"message":{"topic":"%s","data":{"title":"%s","body":"%s","key":"%s"},"android":{"priority":"high"}}}' \
  "$TOPIC" "$TITLE_J" "$BODY_J" "$MSG_KEY")

echo "Projet : $PROJECT_ID"
echo "Canal  : $TOPIC"
echo "Titre  : $TITLE"
echo

RESPONSE=$(curl -s -X POST \
  "https://fcm.googleapis.com/v1/projects/$PROJECT_ID/messages:send" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d "$PAYLOAD")

if echo "$RESPONSE" | grep -q '"name"'; then
  echo "ENVOYE."
  echo "$RESPONSE"
else
  echo "ECHEC :" >&2
  echo "$RESPONSE" >&2
  exit 1
fi
