#!/usr/bin/env bash
# ==========================================================================
# VaultEx — Préparer la photo d'un membre de l'équipe pour l'écran À propos
# ==========================================================================
#
#   ./tools/annonces/avatar-equipe.sh photo-source.png moumine
#   ./tools/annonces/avatar-equipe.sh photo.jpg sakande --cadre 600x600+200+150
#
# Rend `moumine.webp` : 256×256, détouré si la source l'est, sans aucune
# métadonnée, et pesant une vingtaine de kilo-octets.
#
# --------------------------------------------------------------------------
# POURQUOI ON NE DÉPOSE PAS LA PHOTO TELLE QUELLE
# --------------------------------------------------------------------------
#
# Une photo sortie d'un téléphone fait 3 à 5 Mo en 4000×3000. Elle serait
# téléchargée par CHAQUE utilisateur, sur son forfait mobile, pour finir
# affichée dans un rond de 46 points. Mesuré sur la première photo traitée :
# 261 Ko en entrée, 13 Ko en sortie, sans différence visible à cette taille.
#
# ET ELLE PORTE DES MÉTADONNÉES. Un appareil photo de téléphone écrit dans
# le fichier la date, le modèle, et souvent les COORDONNÉES GPS du lieu de
# prise de vue. Déposer le fichier brut publierait le domicile de quelqu'un
# à côté de son visage. `-strip` les enlève toutes, et ce n'est pas une
# optimisation : c'est la raison principale de ce script.
#
# --------------------------------------------------------------------------
# POURQUOI WEBP ET NON JPEG
# --------------------------------------------------------------------------
#
# Parce que les photos de l'équipe sont DÉTOURÉES, et que le rond de l'écran
# a un fond teinté qui change avec le thème : bleu nuit en sombre, bleu pâle
# en clair. Une transparence conservée laisse ce fond transparaître et
# s'accorde aux deux. Un JPEG, qui ne sait pas être transparent, imposerait
# un disque blanc au milieu d'un écran sombre.
#
# Le PNG sait être transparent mais pèse huit fois plus (100 Ko contre 13).
# WebP fait les deux, et Android le lit nativement.
#
# Si la source n'a PAS de transparence, le script le dit et sort quand même
# un WebP : simplement, le fond de la photo restera visible dans le rond.
#
# --------------------------------------------------------------------------
# LE CADRAGE EST MESURÉ, PAS DEVINÉ
# --------------------------------------------------------------------------
#
# Sur une source détourée, le script relève la LARGEUR DU SUJET ligne par
# ligne. Le profil donne la tête sans ambiguïté : étroit au sommet du crâne,
# large aux pommettes, resserré au cou, puis une rupture nette aux épaules.
# C'est cette rupture qui borne le portrait.
#
# Sur une source non détourée, il n'y a rien à mesurer : donner `--cadre`.
#
# --------------------------------------------------------------------------
# DÉTOURER UNE PHOTO QUI NE L'EST PAS
# --------------------------------------------------------------------------
#
# Ce script ne détoure pas : il mesure, recadre et compresse. Le détourage
# demande un modèle, et un modèle n'a pas sa place dans un dépôt.
#
# Ce qui a servi pour deux des trois photos de l'équipe, sur une machine de
# travail — PAS sur le relais, PAS dans l'application :
#
#     python3 -m venv venv && ./venv/bin/pip install "rembg[cpu,cli]"
#     ./venv/bin/rembg i photo.jpg photo-detouree.png
#
# Le modèle fait un gigaoctet et se télécharge au premier appel. Sur une
# photo de salle de réunion — mur, micro, chaise — le résultat a été propre
# sans retouche.
#
# PUIS REPASSER PAR CE SCRIPT sur le PNG détouré : la mesure de la tête
# redevient possible, et c'est lui qui fait la taille, la compression et le
# retrait des métadonnées.
#
# SI LE CRÂNE TOUCHE LE HAUT de la photo d'origine, il n'y a pas d'air à
# prendre au-dessus. En ajouter avant de mesurer :
#
#     convert detouree.png -background none -gravity north -splice 0x60 \
#       avec-air.png
#
# Sans ça, les cheveux touchent le bord du rond et la tête paraît coincée.
# ==========================================================================
set -u

source="${1:-}"
nom="${2:-}"
cadre=""
if [ "${3:-}" = "--cadre" ]; then cadre="${4:-}"; fi

if [ -z "$source" ] || [ -z "$nom" ]; then
  echo "usage : $0 <photo-source> <nom-sans-extension> [--cadre LxH+X+Y]" >&2
  exit 2
fi
if [ ! -f "$source" ]; then
  echo "introuvable : $source" >&2
  exit 2
fi
if ! command -v convert >/dev/null 2>&1; then
  echo "ImageMagick (convert) est requis." >&2
  exit 2
fi

# Le nom doit survivre au filtre du relais — voir nomPhotoSur() dans
# worker.js. Le refuser ICI evite de deposer une image que CHANGE_EQUIPE
# ne pourra jamais designer.
case "$nom" in
  *[!a-zA-Z0-9._-]*|*..*|"")
    echo "nom invalide : lettres, chiffres, point, tiret et souligne seulement" >&2
    exit 2;;
esac

sortie="$(dirname "$source")/$nom.webp"

transparent=$(identify -format "%A" "$source" 2>/dev/null)
if [ "$transparent" != "True" ] && [ "$transparent" != "Blend" ] && [ -z "$cadre" ]; then
  echo "⚠ La source n'est pas detouree : le cadrage ne peut pas etre mesure."
  echo "  Relancer avec --cadre LxH+X+Y, ou detourer la photo d'abord."
  exit 3
fi

if [ -z "$cadre" ]; then
  # ─── Profil de largeur, de haut en bas ───
  bb=$(convert "$source" -alpha extract -threshold 40% -format "%@" info:)
  haut=$(echo "$bb" | sed 's/.*+//')
  hauteur_totale=$(identify -format "%h" "$source")

  precedente=0
  epaules=0
  centre=0
  reference=0
  y=$haut
  while [ "$y" -lt "$hauteur_totale" ]; do
    ligne=$(convert "$source" -alpha extract -threshold 40% \
      -crop "x6+0+$y" +repage -format "%@" info: 2>/dev/null)
    largeur=$(echo "$ligne" | sed 's/x.*//')
    gauche=$(echo "$ligne" | sed 's/.*+\([0-9]*\)+.*/\1/')
    [ -z "$largeur" ] && largeur=0
    [ -z "$gauche" ] && gauche=0

    # La largeur de reference est celle du VISAGE, prise une fois que la
    # tete est franchement commencee. Prise au sommet du crane, elle
    # vaudrait quelques pixels et tout ressemblerait a une epaule.
    if [ "$reference" -eq 0 ] && [ "$y" -gt $((haut + 150)) ]; then
      reference=$largeur
      centre=$((gauche + largeur / 2))
    fi

    # LA RUPTURE : une ligne nettement plus large que le visage, c'est le
    # haut des epaules. On s'arrete la.
    if [ "$reference" -gt 0 ] && [ "$largeur" -gt $((reference * 150 / 100)) ]; then
      epaules=$y
      break
    fi
    precedente=$largeur
    y=$((y + 24))
  done

  [ "$epaules" -eq 0 ] && epaules=$((haut + (hauteur_totale - haut) / 3))
  [ "$centre" -eq 0 ] && centre=$(( $(identify -format "%w" "$source") / 2 ))

  tete=$((epaules - haut))
  cote=$((tete * 140 / 100))
  x=$((centre - cote / 2))
  # Un peu d'air au-dessus du crane : sans lui, les cheveux touchent le
  # bord du rond et la tete parait coincee.
  yy=$((haut - tete / 12))
  [ "$x" -lt 0 ] && x=0
  [ "$yy" -lt 0 ] && yy=0
  cadre="${cote}x${cote}+${x}+${yy}"
  echo "  tete mesuree : y $haut → $epaules   centre x=$centre"
fi

echo "  cadre : $cadre"
convert "$source" -crop "$cadre" +repage -resize 256x256 \
  -strip -quality 84 -define webp:method=6 "$sortie" || exit 1

# ==========================================================================
# HARMONISER L'EXPOSITION — MESUREE, PAS JUGEE A L'OEIL
# ==========================================================================
#
# MESURE SUR LES TROIS PREMIERES PHOTOS : luminance du visage 133, 65 et 93
# sur 255. Celle du milieu etait DEUX FOIS plus sombre que la premiere.
# Alignees dans une liste, elles ne ressemblaient pas a un meme jeu — l'une
# paraissait sous-exposee, et on l'aurait mise sur le dos du photographe
# plutot que sur celui de l'ecran.
#
# ON MESURE LE CENTRE, PAS L'IMAGE. Sur une photo detouree, le fond est
# transparent : le compter ferait croire qu'une image est claire alors que
# seul son vide l'est. Le carre central est le visage, a peu de chose pres.
#
# LE GAMMA SEUL NE SUFFIT PAS. Eclaircir une image sombre d'un gamma 1,7
# remonte tout, y compris les noirs : le costume sombre de la deuxieme photo
# virait au gris. Une legere courbe sigmoide rend les noirs sans reprendre
# la lumiere du visage — mesure : contraste 65 sans elle, 72 avec.
#
# LES BORNES SONT LA POUR UNE RAISON. Au-dela de 1,8 on ne revele plus rien,
# on amplifie le bruit d'une photo ratee ; en-dessous de 0,8 on assombrit une
# photo qui allait bien. Hors de ces bornes, c'est la photo qu'il faut
# refaire, pas la courbe qu'il faut forcer.
#
# CE QU'ON NE FAIT PAS : rien qui change le visage de quelqu'un. Corriger une
# exposition est du developpement ; lisser une peau ou retoucher des traits
# est autre chose, et ca ne se decide pas dans un script.
# ==========================================================================
CIBLE_LUMINANCE=115

mesure() {
  convert "$1" -crop 110x110+73+60 +repage -alpha off -colorspace Gray \
    -format "%[fx:int(mean*255)] %[fx:int(standard_deviation*255)]" info:
}

avant=$(mesure "$sortie")
lum=${avant%% *}

if [ "${lum:-0}" -gt 5 ] && [ "${lum:-0}" -lt 250 ]; then
  gamma=$(awk -v l="$lum" -v c="$CIBLE_LUMINANCE" 'BEGIN{
    g = log(l/255) / log(c/255)
    if (g < 0.8) g = 0.8
    if (g > 1.8) g = 1.8
    printf "%.2f", g
  }')
  # La sigmoide ne sert qu'aux images qu'on a reellement eclaircies.
  sig=""
  case "$(awk -v g="$gamma" 'BEGIN{print (g > 1.2) ? "oui" : "non"}')" in
    oui) sig="-sigmoidal-contrast 4x42%";;
  esac
  convert "$sortie" -gamma "$gamma" $sig -unsharp 0x0.7+0.5+0.02 \
    -strip -quality 84 -define webp:method=6 "$sortie"
  apres=$(mesure "$sortie")
  echo "  exposition : visage $avant → $apres   (gamma $gamma ${sig:+avec sigmoide})"
fi

# ─── Apercu rond, tel que l'ecran le dessine ───
apercu="$(dirname "$source")/$nom-apercu.png"
convert -size 256x256 xc:none -fill white -draw "circle 128,128 128,2" \
  "$(dirname "$source")/.masque.png"
convert -size 256x256 "xc:#1F3051" "$sortie" -compose Over -composite \
  "$(dirname "$source")/.masque.png" -alpha set -compose DstIn -composite "$apercu"
rm -f "$(dirname "$source")/.masque.png"

echo "  ✓ $sortie  ($(stat -c%s "$sortie") octets)"
echo "  ✓ $apercu  — a REGARDER avant de deposer"
echo
echo "  Deposer :"
echo "    cd tools/relais-cours"
echo "    npx.cmd wrangler kv key put --config wrangler.toml --remote \\"
echo "      --binding MEMOIRE \"img:$nom.webp\" --path \"$sortie\""
echo
echo "  Puis ajouter |$nom.webp au membre concerne dans CHANGE_EQUIPE."
