#!/usr/bin/env bash
# ==========================================================================
# VaultEx — QUELS FRAIS PAIE-T-ON VRAIMENT SUR DOGECOIN ?
# ==========================================================================
#
# UTILISATION (Git Bash, depuis la racine du projet) :
#
#   BLOCKCYPHER_JETON=le_jeton ./tools/doge-frais.sh
#   BLOCKCYPHER_JETON=le_jeton ./tools/doge-frais.sh 12     <-- 12 transactions
#
# Sans jeton, le quota anonyme de BlockCypher — une requete — ne suffit
# pas : ce script en fait une par transaction echantillonnee. Le jeton est
# gratuit (accounts.blockcypher.com).
#
# --------------------------------------------------------------------------
# POURQUOI ON MESURE AU LIEU DE RECOPIER UN CHIFFRE
# --------------------------------------------------------------------------
#
# Un portefeuille qui envoie doit choisir des frais. Trop bas, la
# transaction reste en attente indefiniment et l'utilisateur ne comprend
# pas pourquoi. Trop hauts, il paie pour rien — et sur une chaine ou l'on
# envoie l'equivalent de quelques milliers de francs, « pour rien » se
# remarque.
#
# LES TARIFS RECITES SONT FAUX, ET DE BEAUCOUP. La premiere transaction
# rapportee par la sonde a paye 91 200 000 unites pour 225 octets — environ
# quatre DOGE par kilo-octet, la ou le minimum historique souvent cite est
# de 0,01. Un facteur quatre cents. Ecrire le minimum dans l'application
# produirait des envois qui ne partent jamais.
#
# ON LIT DONC CE QUE LA CHAINE PORTE, aujourd'hui, depuis le reseau d'ou
# l'application tournera. Comme tout le reste dans ce dossier.
#
# --------------------------------------------------------------------------
# CE QUE CE SCRIPT NE FAIT PAS
# --------------------------------------------------------------------------
#
# Il ne recommande pas un tarif. Il rend une DISTRIBUTION — le plus bas, le
# median, le plus haut — et c'est a la lecture qu'on decide. Un script qui
# rendrait un seul nombre cacherait justement ce qu'on cherche a voir : la
# dispersion.
#
# Il ignore les coinbases, qui n'ont pas de frais et fausseraient le bas de
# la distribution a zero.
# ==========================================================================

set -uo pipefail

COMBIEN="${1:-10}"
DELAI=20

if ! printf '%s' "$COMBIEN" | grep -qE '^[0-9]+$' || [ "$COMBIEN" -lt 2 ]; then
  echo "ERREUR : nombre de transactions attendu (2 ou plus), recu « $COMBIEN »." >&2
  exit 1
fi

JETON=""
[ -n "${BLOCKCYPHER_JETON:-}" ] && JETON="?token=${BLOCKCYPHER_JETON}"
JETON_ET=""
[ -n "${BLOCKCYPHER_JETON:-}" ] && JETON_ET="&token=${BLOCKCYPHER_JETON}"

if [ -z "$JETON" ]; then
  echo
  echo "  Aucun jeton : le quota anonyme de BlockCypher est d'une requete," >&2
  echo "  et ce script en fait $((COMBIEN + 2)). Posez BLOCKCYPHER_JETON." >&2
  echo "  Le jeton est gratuit : accounts.blockcypher.com" >&2
  exit 1
fi

echo
echo "  Lecture du dernier bloc…"
bloc=$(curl -sSL -m "$DELAI" "https://api.blockcypher.com/v1/doge/main$JETON" 2>/dev/null)
hash=$(sed 's/"/\n/g' <<<"$bloc" | grep -E '^[0-9a-f]{64}$' | head -1)
if [ -z "$hash" ]; then
  echo "  Pas de bloc lu — on ne conclut RIEN." >&2
  exit 1
fi

detail=$(curl -sSL -m "$DELAI" "https://api.blockcypher.com/v1/doge/main/blocks/$hash$JETON" 2>/dev/null)
# On cherche DANS le tableau txids : le hash du bloc est lui aussi
# soixante-quatre caracteres hexadecimaux et arrive avant.
liste=${detail#*\"txids\"}
# `tail -n +2` saute la premiere : c'est toujours la coinbase, sans frais.
txids=$(sed 's/"/\n/g' <<<"$liste" | grep -E '^[0-9a-f]{64}$' | tail -n +2 | head -"$COMBIEN")

nb=$(printf '%s\n' "$txids" | grep -c . || true)
if [ "$nb" -lt 2 ]; then
  echo "  Ce bloc ne porte que $nb transaction(s) hors coinbase."
  echo "  Relancez : le bloc suivant en portera d'autres."
  exit 1
fi

echo "  $nb transactions a lire dans le bloc ${hash:0:12}…"
echo
printf '  %-18s %8s %12s %14s\n' TRANSACTION OCTETS FRAIS 'UNITES/OCTET'
echo "  --------------------------------------------------------------"

TMP=$(mktemp 2>/dev/null || printf '%s' "${TMPDIR:-/tmp}/dogefrais.$$")
trap 'rm -f "$TMP"' EXIT
: > "$TMP"

for t in $txids; do
  rep=$(curl -sSL -m "$DELAI" "https://api.blockcypher.com/v1/doge/main/txs/$t$JETON" 2>/dev/null | tr -d '\n')
  # `fees` et `size` sont des NOMBRES, donc sans guillemets : le motif
  # differe de celui des champs texte, et se tromper rendrait vide sans
  # rien signaler.
  frais=$(sed -n 's/.*"fees"[[:space:]]*:[[:space:]]*\([0-9]\{1,\}\).*/\1/p' <<<"$rep" | head -1)
  taille=$(sed -n 's/.*"size"[[:space:]]*:[[:space:]]*\([0-9]\{1,\}\).*/\1/p' <<<"$rep" | head -1)
  if [ -z "$frais" ] || [ -z "$taille" ] || [ "$taille" -eq 0 ]; then
    printf '  %-18s %8s %12s %14s\n' "${t:0:16}…" "?" "?" "illisible"
    continue
  fi
  parOctet=$((frais / taille))
  printf '  %-18s %8s %12s %14s\n' "${t:0:16}…" "$taille" "$frais" "$parOctet"
  printf '%s\n' "$parOctet" >> "$TMP"
done

echo "  --------------------------------------------------------------"
echo

lus=$(grep -c . "$TMP" || true)
if [ "$lus" -lt 2 ]; then
  echo "  Trop peu de lignes lisibles ($lus) pour une distribution."
  echo "  Ce n'est PAS une reponse sur les frais : c'est la lecture qui a"
  echo "  echoue. Verifiez le quota du jeton."
  exit 1
fi

trie=$(sort -n "$TMP")
bas=$(printf '%s\n' "$trie" | head -1)
haut=$(printf '%s\n' "$trie" | tail -1)
median=$(printf '%s\n' "$trie" | sed -n "$(( (lus + 1) / 2 ))p")

echo "  SUR $lus TRANSACTIONS, EN UNITES PAR OCTET :"
printf '    le plus bas   %s\n' "$bas"
printf '    median        %s\n' "$median"
printf '    le plus haut  %s\n' "$haut"
echo
echo "  Pour une transaction ordinaire d'environ 225 octets :"
printf '    au tarif median : %s unites, soit %s.%06d DOGE\n' \
  "$((median * 225))" "$(( (median * 225) / 100000000 ))" "$(( ((median * 225) % 100000000) / 100 ))"
echo
cat <<'FIN'
  ─── COMMENT LIRE CECI ──────────────────────────────────────────────

  C'EST UN SEUL BLOC, donc un seul instant. Relancez a quelques heures
  d'ecart avant d'ecrire un tarif dans l'application : un bloc ou tout
  le monde a paye cher ne dit pas ce que coute un envoi ordinaire.

  LA DISPERSION COMPTE PLUS QUE LA MEDIANE. Si le plus bas et le plus
  haut tiennent dans un facteur deux, le reseau est calme et viser la
  mediane suffit. S'ils different d'un facteur dix, c'est qu'une partie
  des envoyeurs paie au hasard — et il faut viser le haut pour ne pas
  rester en attente.

  CES FRAIS NE NOUS REVIENNENT PAS. Ils vont au reseau qui inscrit la
  transaction, comme sur toutes les autres chaines de l'application.
FIN
