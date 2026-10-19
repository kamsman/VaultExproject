<#
===========================================================================
COMPOSER UNE BANNIERE D'ANNONCE — PORTRAIT A GAUCHE, LOGO A DROITE
===========================================================================

    powershell -ExecutionPolicy Bypass -File .\tools\annonces\composer-banniere.ps1 `
      -Portrait "C:\Users\HP\Downloads\photo.jpg"

Une infographie deja composee, servie telle quelle :

    powershell -ExecutionPolicy Bypass -File .\tools\annonces\composer-banniere.ps1 `
      -Pleine -Portrait "C:\...\hold.png" `
      -Sortie "tools\annonces\hold-banniere.jpg"

  L'image est recadree en 2:1 PAR LE HAUT — la ou vivent le titre et le
  premier element. Android, lui, recadrerait au centre et perdrait tout.

Sans portrait — pour citer quelqu'un dont le visage n'existe pas :

    powershell -ExecutionPolicy Bypass -File .\tools\annonces\composer-banniere.ps1 `
      -FichierCitation "tools\annonces\citation-satoshi.txt" `
      -Sortie "tools\annonces\la-confiance.jpg"

Avec une citation :

    powershell -ExecutionPolicy Bypass -File .\tools\annonces\composer-banniere.ps1 `
      -Portrait "C:\...\photo.jpg" `
      -Citation "Money will stop being relevant at some point in the future." `
      -Auteur "Elon Musk"

Options :

    -Sortie <chemin>   defaut : tools\annonces\banniere.jpg
    -Logo <chemin>     defaut : le logo rond de l'application
    -Citation <texte>  une phrase a droite ; le logo passe en petit, en coin
    -FichierCitation <chemin>
                       la citation ligne 1, l'auteur ligne 2, en UTF-8.
                       A PREFERER des qu'il y a des accents : la ligne de
                       commande Windows les abime, un fichier non.
    -Auteur <nom>      le nom sous la phrase
    -Qualite <1-100>   defaut : 85

ATTRIBUER UNE PHRASE A QUELQU'UN L'ENGAGE. Entre guillemets, elle
affirme qu'il a dit CES MOTS-LA. Si la formulation exacte n'est pas
verifiee contre une source primaire, mieux vaut une paraphrase sans
guillemets — qui dit la meme idee sans pretendre citer.

--- POURQUOI CET OUTIL EXISTE -------------------------------------------

Android recadre la banniere d'une notification en 2:1, au centre. Une
photo de portrait donnee telle quelle y perd le haut et le bas : sur un
visage, c'est le visage qui disparait. Mesure sur une vraie photo :
473 x 648, soit 0,73:1 la ou il en faut 2.

Le recadrage ne peut donc pas etre automatique — il faut COMPOSER :
poser le portrait sur un cote, et remplir le reste. C'est ce que fait ce
script.

--- POURQUOI DU JPEG, ET PAS DU PNG -------------------------------------

Le PNG est sans perte, donc une photo de 1024 x 512 y pese souvent plus
d'un demi-megaoctet. Or cette image est telechargee sur le reseau mobile
de CHAQUE utilisateur, au moment ou la notification arrive : au-dela de
300 Ko, elle n'arrive simplement jamais sur une connexion lente.

Le JPEG a 85 % tient la meme image sous les 150 Ko, et la difference ne
se voit pas sur une banniere de notification. Le script accepte .png si
on le demande, et previent du poids.

--- POURQUOI LE HAUT DE LA PHOTO EST GARDE ------------------------------

Quand la photo est plus etroite que la zone — ce qui est le cas de tout
portrait — il faut couper en hauteur. On garde le HAUT : c'est la que se
trouve le visage. Couper au centre, comme le fait Android, decapiterait.

--- AUCUNE DEPENDANCE, ET WINDOWS SEULEMENT -----------------------------

System.Drawing est fourni avec Windows. Rien a installer, rien a
telecharger — ce qui compte sur une machine qui construit deja une
application Android et n'a pas besoin d'un outil graphique de plus.

--- CE FICHIER EST EN UTF-8 AVEC BOM, ET SON CODE EN ASCII --------------

Windows PowerShell 5.1 lit un .ps1 sans BOM comme de l'ANSI. Un tiret
cadratin dans une chaine y devient un guillemet typographique, que
PowerShell accepte comme delimiteur : la chaine se ferme au milieu du
message et le script refuse de s'analyser, sur des erreurs qui designent
des lignes saines. C'est arrive. Deux protections valent mieux qu'une.
===========================================================================
#>

param(
    [string]$Portrait,
    [string]$Sortie,
    [string]$Logo,
    [switch]$Pleine,
    [string]$Citation,
    [string]$FichierCitation,
    [string]$Auteur,
    [int]$Qualite = 85
)

Add-Type -AssemblyName System.Drawing

# La racine du depot, quel que soit l'endroit d'ou le script est lance.
$racine = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

function Absolu([string]$chemin, [string]$defaut) {
    if ([string]::IsNullOrWhiteSpace($chemin)) { $chemin = $defaut }
    if ([System.IO.Path]::IsPathRooted($chemin)) { return $chemin }
    return [System.IO.Path]::GetFullPath((Join-Path $racine $chemin))
}

<#
===========================================================================
LE PORTRAIT EST FACULTATIF, ET CE N'EST PAS UN CONFORT
===========================================================================

Il y a des gens qu'on cite et dont le visage n'existe pas. Satoshi
Nakamoto en est un : personne ne sait qui c'est, et les « photos de
Satoshi » qui circulent sont soit des images fabriquees, soit — et c'est
le pire cas — le visage de Dorian Nakamoto, un homme designe a tort par
Newsweek en 2014 et dont la vie a ete bouleversee par cette erreur.

Mettre un visage la ou il n'y en a pas, c'est affirmer quelque chose de
faux sur quelqu'un de reel. Sans portrait, la citation prend toute la
largeur : c'est elle l'objet de la carte, et elle se suffit.
===========================================================================
#>
$avecPortrait = -not [string]::IsNullOrWhiteSpace($Portrait)
$cheminPortrait = if ($avecPortrait) { Absolu $Portrait '' } else { '' }
$cheminLogo     = Absolu $Logo    'app\src\main\res\drawable\logorond.png'
$cheminSortie   = Absolu $Sortie  'tools\annonces\banniere.jpg'

<#
===========================================================================
LA CITATION PEUT VENIR D'UN FICHIER, ET C'EST LE MOYEN SUR
===========================================================================

MESURE : une citation francaise passee en argument perd ses accents, ou
pire, la ligne de commande se mange les continuations et le script recoit
« m » comme valeur de -Qualite.

C'est le meme probleme que send-announcement.sh, dont le LISEZMOI impose
--fichier pour cette raison exacte : la ligne de commande Windows abime
les accents, un fichier UTF-8 non.

Format du fichier : la citation sur la PREMIERE ligne, l'auteur sur la
SECONDE. Deux lignes, rien a echapper, et le texte se relit dans un
editeur au lieu de vivre dans un historique de terminal.
===========================================================================
#>
if (-not [string]::IsNullOrWhiteSpace($FichierCitation)) {
    $cheminCitation = Absolu $FichierCitation ''
    if (-not (Test-Path $cheminCitation)) {
        Write-Host ""
        Write-Host "Introuvable : $cheminCitation" -ForegroundColor Red
        Write-Host ""
        exit 1
    }
    # UTF8 explicite : sans ca, Windows PowerShell lit en ANSI et les
    # accents deviennent des paires de caracteres illisibles.
    $lignes = Get-Content -Path $cheminCitation -Encoding UTF8
    $lignes = @($lignes | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($lignes.Count -lt 1) {
        Write-Host ""
        Write-Host "Ce fichier est vide : $cheminCitation" -ForegroundColor Red
        Write-Host ""
        exit 1
    }
    $Citation = $lignes[0].Trim()
    if ($lignes.Count -ge 2 -and [string]::IsNullOrWhiteSpace($Auteur)) {
        $Auteur = $lignes[1].Trim()
    }
}

# Su tot, parce que la mise en page en depend : voir $zoneP plus bas.
$avecCitation = -not [string]::IsNullOrWhiteSpace($Citation)

$aControler = if ($avecPortrait) { @($cheminPortrait, $cheminLogo) } else { @($cheminLogo) }
foreach ($f in $aControler) {
    if (-not (Test-Path $f)) {
        Write-Host ""
        Write-Host "Introuvable : $f" -ForegroundColor Red
        Write-Host ""
        exit 1
    }
}

# ---- Les mesures, toutes au meme endroit --------------------------------
#
# 1024 x 512 est le format sur pour une notification Android : exactement
# 2:1, et assez large pour ne pas etre etire sur un grand ecran.
$L = 1024
$H = 512
<#
LA ZONE DE GAUCHE N'EST PAS CELLE DU PORTRAIT : C'EST CELLE DE L'IMAGE.

Avec un portrait, elle vaut 460 points et le portrait l'occupe.

SANS PORTRAIT MAIS AVEC UNE CITATION, elle vaut 360 et c'est LE LOGO qui
s'y installe, en grand. Mis a zero, la citation courait d'un bord a
l'autre et le logo se recroquevillait dans un coin a 70 points, pendant
que la moitie gauche de la carte restait vide. La meme grammaire que la
version avec portrait : une image a gauche, le texte a droite.

Sans citation du tout, elle vaut zero : le logo prend le centre.
#>
$zoneP = if ($avecPortrait) { 460 } elseif ($avecCitation) { 360 } else { 0 }
$fondu = 150          # largeur du degrade qui fond le portrait dans le fond
$tailleLogo = 230

# Le degrade du splash de l'application : c'est deja la marque.
$haut = [System.Drawing.ColorTranslator]::FromHtml('#080D22')
$bas  = [System.Drawing.ColorTranslator]::FromHtml('#1A2858')

<#
CHARGER UNE IMAGE, OU DIRE POURQUOI ON N'A PAS PU.

`FromFile` leve une exception .NET sur un fichier illisible, tronque, ou
qui n'est pas une image malgre son extension — un .jpg a moitie
telecharge, par exemple. Sans cette enveloppe, on obtient une pile
d'appels au lieu d'une phrase.

La fonction rend donc l'image, ou arrete le script en nommant le fichier
fautif. Elle sert aux deux : le portrait et le logo.
#>
function Charger([string]$chemin, [string]$quoi) {
    try {
        return [System.Drawing.Image]::FromFile($chemin)
    } catch {
        Write-Host ""
        Write-Host "Ce fichier n'est pas une image lisible ($quoi) :" -ForegroundColor Red
        Write-Host "   $chemin"
        Write-Host "   $($_.Exception.Message)"
        Write-Host ""
        exit 1
    }
}

$image = New-Object System.Drawing.Bitmap $L, $H
$g = [System.Drawing.Graphics]::FromImage($image)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality

<#
===========================================================================
-Pleine : L'IMAGE ENTIERE, RECADREE EN 2:1 PAR LE HAUT
===========================================================================

Pour une infographie deja composee ailleurs — un classement, une affiche —
qu'on veut servir telle quelle en banniere.

LE PROBLEME QUE CA RESOUT. Android recadre toute banniere en 2:1 AU
CENTRE. Une infographie verticale y perd son titre, sa signature, et
souvent l'essentiel : il ne reste qu'une bande prise au milieu, sans
contexte, qui ne veut rien dire.

ON COUPE DONC NOUS-MEMES, ET PAR LE HAUT. Dans une affiche, le haut porte
le titre et le premier element — c'est ce qui se lit et c'est ce qui
donne envie de voir le reste. Laisser Android choisir, c'est accepter
qu'il choisisse mal.

CE QUE CA NE FAIT PAS : montrer toute l'infographie. Aucune banriere ne
le peut. Pour ca, publier l'image entiere sur Telegram et faire pointer
l'annonce dessus — `send-announcement.sh` accepte un lien `t.me` en
cinquieme argument.
#>
if ($Pleine) {
    if (-not $avecPortrait) {
        Write-Host ""
        Write-Host "-Pleine demande une image : ajoute -Portrait <chemin>." -ForegroundColor Red
        Write-Host ""
        exit 1
    }
    $source = Charger $cheminPortrait 'image'
    $rapportCible = $L / $H
    $rapportSource = $source.Width / $source.Height
    if ($rapportSource -gt $rapportCible) {
        # Plus large que 2:1 : on coupe les cotes, a egalite.
        $sh2 = $source.Height
        $sw2 = [int]($source.Height * $rapportCible)
        $sx2 = [int](($source.Width - $sw2) / 2)
        $sy2 = 0
    } else {
        # Plus haute : on garde LE HAUT, ou vit le titre.
        $sw2 = $source.Width
        $sh2 = [int]($source.Width / $rapportCible)
        $sx2 = 0
        $sy2 = 0
    }
    $plein = New-Object System.Drawing.Rectangle 0, 0, $L, $H
    $g.DrawImage($source, $plein, $sx2, $sy2, $sw2, $sh2,
        [System.Drawing.GraphicsUnit]::Pixel)
    $source.Dispose()

    $extPleine = [System.IO.Path]::GetExtension($cheminSortie).ToLower()
    $dossierPlein = Split-Path -Parent $cheminSortie
    if (-not (Test-Path $dossierPlein)) {
        New-Item -ItemType Directory -Force -Path $dossierPlein | Out-Null
    }
    if ($extPleine -eq '.png') {
        $image.Save($cheminSortie, [System.Drawing.Imaging.ImageFormat]::Png)
    } else {
        $codecP = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() |
            Where-Object { $_.MimeType -eq 'image/jpeg' }
        $reglagesP = New-Object System.Drawing.Imaging.EncoderParameters 1
        $reglagesP.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter(
            [System.Drawing.Imaging.Encoder]::Quality, [long]$Qualite)
        $image.Save($cheminSortie, $codecP, $reglagesP)
        $reglagesP.Dispose()
    }
    $imageLogoPlein = $null
    $g.Dispose()
    $image.Dispose()

    $poidsPlein = [math]::Round((Get-Item $cheminSortie).Length / 1KB, 1)
    Write-Host ""
    Write-Host "Banniere ecrite : $cheminSortie" -ForegroundColor Green
    Write-Host "   $L x $H, $poidsPlein Ko  (image entiere, recadree par le haut)"
    Write-Host ""
    Write-Host "Verifie-la :" -ForegroundColor DarkGray
    Write-Host "   node tools\annonces\verifier-banniere.mjs $cheminSortie"
    Write-Host ""
    exit 0
}

# ---- Le fond ------------------------------------------------------------
$tout = New-Object System.Drawing.Rectangle 0, 0, $L, $H
$pinceauFond = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
    $tout, $haut, $bas, 25.0)
$g.FillRectangle($pinceauFond, $tout)

# ---- Le portrait, a gauche (s'il y en a un) -----------------------------
if ($avecPortrait) {
$photo = Charger $cheminPortrait 'portrait'

<#
RECADRAGE « COUVRIR », JAMAIS « ETIRER ».

On prend la plus grande partie de la photo qui ait le rapport de la zone,
et on la met a l'echelle. Etirer deformerait un visage, ce qui se voit
immediatement et donne l'air d'un montage bacle.
#>
$rapportZone  = $zoneP / $H
$rapportPhoto = $photo.Width / $photo.Height

if ($rapportPhoto -gt $rapportZone) {
    # Photo plus large que la zone : on coupe les cotes, a egalite.
    $sh = $photo.Height
    $sw = [int]($photo.Height * $rapportZone)
    $sx = [int](($photo.Width - $sw) / 2)
    $sy = 0
} else {
    # Photo plus etroite : on coupe en hauteur, et ON GARDE LE HAUT.
    $sw = $photo.Width
    $sh = [int]($photo.Width / $rapportZone)
    $sx = 0
    $sy = 0
}

$zone = New-Object System.Drawing.Rectangle 0, 0, $zoneP, $H
$g.DrawImage($photo, $zone, $sx, $sy, $sw, $sh,
    [System.Drawing.GraphicsUnit]::Pixel)

<#
LE FONDU, POUR QUE LA PHOTO NE SOIT PAS UN TIMBRE COLLE.

Sans lui, le portrait s'arrete sur une arete verticale nette au milieu de
la banniere. Un degrade du transparent vers la couleur du fond le fait
entrer dans l'image au lieu d'y etre pose.
#>
$zoneFondu = New-Object System.Drawing.Rectangle ($zoneP - $fondu), 0, $fondu, $H
$invisible = [System.Drawing.Color]::FromArgb(0, $bas.R, $bas.G, $bas.B)
$pinceauFondu = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
    $zoneFondu, $invisible, $bas, 0.0)
$g.FillRectangle($pinceauFondu, $zoneFondu)
}

# ---- Le logo, a droite --------------------------------------------------
#
# LE NOM NE PEUT PAS ETRE $logo, ET CA A COUTE UN LOGO ABSENT.
#
# PowerShell NE DISTINGUE PAS LA CASSE des noms de variables : $logo et
# le parametre $Logo sont la MEME variable. Or ce parametre est declare
# [string], et PowerShell impose le type declare a chaque affectation.
#
# `$logo = [System.Drawing.Image]::FromFile(...)` convertissait donc
# l'image en la chaine « System.Drawing.Bitmap », sans rien dire. La
# banniere sortait complete, a la bonne taille, au bon poids — et sans
# logo. L'erreur n'apparaissait qu'au DrawImage suivant, en parlant d'une
# conversion de String vers Image, ce qui n'a aucun sens tant qu'on n'a
# pas vu la collision.
$imageLogo = Charger $cheminLogo 'logo'

<#
TROIS PLACES POUR LE LOGO, ET CHACUNE REPOND A CE QU'IL Y A A COTE.

  portrait + citation -> en bas a droite, petit. Le visage et la phrase
                         occupent deja la carte ; il signe, il ne dispute
                         pas le regard.
  citation seule      -> A GAUCHE ET EN GRAND, a la place qu'aurait prise
                         le portrait. Sinon la moitie gauche reste vide.
  portrait seul       -> au centre de la partie droite, en grand.
#>
if ($avecCitation -and -not $avecPortrait) {
    $taillePleine = 200
    $lx = [int](($zoneP - $taillePleine) / 2)
    $ly = [int](($H - $taillePleine) / 2)
    $g.DrawImage($imageLogo, $lx, $ly, $taillePleine, $taillePleine)
}

if ($avecCitation) {
    <#
    ===================================================================
    UNE CARTE DE CITATION : LE TEXTE PREND LA PLACE, LE LOGO SE RETIRE
    ===================================================================

    Quand il y a une phrase a lire, c'est elle l'objet de l'image. Un
    logo de 230 px au milieu lui disputerait le regard pour ne rien
    dire de plus que la signature.

    Il descend donc en bas a droite, a 70 px : assez pour qu'on sache
    d'ou vient la carte, pas assez pour qu'on le lise avant le texte.
    ===================================================================
    #>
    if ($avecPortrait) {
        $petit = 70
        $g.DrawImage($imageLogo, ($L - $petit - 34), ($H - $petit - 30), $petit, $petit)
    }

    # Sans portrait, le texte respire des deux cotes : une ligne qui court
    # d'un bord a l'autre sur 1024 points se lit mal.
    $marge = if ($avecPortrait) { 40 } else { 80 }
    $gauche = $zoneP + $marge
    $largeurTexte = $L - $gauche - $marge
    $hauteurTexte = $H - 150

    <#
    LA TAILLE DU TEXTE S'ADAPTE, ELLE N'EST PAS DEVINEE.

    Une citation courte doit remplir la carte, une longue doit tenir
    dedans. Un corps fixe fait l'un ou l'autre, jamais les deux : on
    mesure donc, en descendant jusqu'a ce que ca rentre.

    La borne basse existe aussi : en dessous de 15 px, une banniere de
    notification n'est plus lisible sur un telephone, et il vaut mieux
    une citation raccourcie qu'une image illisible.
    #>
    $ouvrante = [string][char]0x00AB
    $fermante = [string][char]0x00BB
    $texte = $ouvrante + ' ' + $Citation.Trim() + ' ' + $fermante

    $corps = 15
    for ($essai = 34; $essai -ge 15; $essai -= 1) {
        $test = New-Object System.Drawing.Font('Segoe UI', $essai, [System.Drawing.FontStyle]::Italic)
        $mesure = $g.MeasureString($texte, $test, [int]$largeurTexte)
        $test.Dispose()
        if ($mesure.Height -le $hauteurTexte) { $corps = $essai; break }
    }

    $police = New-Object System.Drawing.Font('Segoe UI', $corps, [System.Drawing.FontStyle]::Italic)
    $blanc = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::White)
    $cadre = New-Object System.Drawing.RectangleF $gauche, 56, $largeurTexte, $hauteurTexte
    $g.DrawString($texte, $police, $blanc, $cadre)

    if (-not [string]::IsNullOrWhiteSpace($Auteur)) {
        # Le nom sous la phrase, plus petit et dans le bleu de la marque :
        # on lit la phrase d'abord, l'auteur ensuite. C'est l'ordre naturel.
        $hauteurPhrase = $g.MeasureString($texte, $police, [int]$largeurTexte).Height
        $policeAuteur = New-Object System.Drawing.Font('Segoe UI', ([int]($corps * 0.62)), [System.Drawing.FontStyle]::Bold)
        $bleu = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml('#6FA8FF'))
        $g.DrawString(('- ' + $Auteur.Trim()), $policeAuteur, $bleu, $gauche, (56 + $hauteurPhrase + 18))
        $policeAuteur.Dispose()
        $bleu.Dispose()
    }

    $police.Dispose()
    $blanc.Dispose()
} else {
    $lx = $zoneP + [int](($L - $zoneP - $tailleLogo) / 2)
    $ly = [int](($H - $tailleLogo) / 2)
    $g.DrawImage($imageLogo, $lx, $ly, $tailleLogo, $tailleLogo)
}

# ---- Enregistrement -----------------------------------------------------
$extension = [System.IO.Path]::GetExtension($cheminSortie).ToLower()
$dossier = Split-Path -Parent $cheminSortie
if (-not (Test-Path $dossier)) { New-Item -ItemType Directory -Force -Path $dossier | Out-Null }

if ($extension -eq '.png') {
    $image.Save($cheminSortie, [System.Drawing.Imaging.ImageFormat]::Png)
} else {
    $codec = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() |
        Where-Object { $_.MimeType -eq 'image/jpeg' }
    $reglages = New-Object System.Drawing.Imaging.EncoderParameters 1
    $reglages.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter(
        [System.Drawing.Imaging.Encoder]::Quality, [long]$Qualite)
    $image.Save($cheminSortie, $codec, $reglages)
    $reglages.Dispose()
}

$imageLogo.Dispose()
if ($avecPortrait) {
    $photo.Dispose()
    $pinceauFondu.Dispose()
}
$pinceauFond.Dispose()
$g.Dispose()
$image.Dispose()

$poids = [math]::Round((Get-Item $cheminSortie).Length / 1KB, 1)
Write-Host ""
Write-Host "Banniere ecrite : $cheminSortie" -ForegroundColor Green
Write-Host "   $L x $H, $poids Ko"
if ($poids -gt 300) {
    Write-Host ""
    Write-Host "ATTENTION : $poids Ko, c'est lourd." -ForegroundColor Yellow
    Write-Host "Cette image est telechargee sur le reseau mobile de CHAQUE"
    Write-Host "utilisateur. Relance avec -Qualite 70, ou en .jpg si tu as"
    Write-Host "demande du .png."
}
Write-Host ""
Write-Host "Verifie-la, puis depose-la :" -ForegroundColor DarkGray
Write-Host "   node tools\annonces\verifier-banniere.mjs $cheminSortie"
Write-Host ""
