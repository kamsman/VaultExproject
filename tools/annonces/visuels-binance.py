#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Visuels + PDF de l'alerte « 6,3 milliards volés chez Binance ».

Produit, dans tools/annonces/visuels/ :
  binance-1-chiffres.png        les trois chiffres de l'incident
  binance-2-plateforme.png      plateforme vs non-custodial
  binance-3-regle.png           la règle d'or + gbash.tools
et, dans tools/annonces/ :
  article-binance.pdf           l'article complet, visuels intégrés

Le texte de l'article vient de article-binance-ce-que-ca-change.md.
Les images sont dessinées ici : si un chiffre bouge, il se corrige
dans CHIFFRES ci-dessous et dans l'article, puis on relance :

    python3 tools/annonces/visuels-binance.py

Dépendances : pillow, reportlab.
"""

import os
import re
import shutil
import tempfile

from PIL import Image, ImageDraw, ImageFont

from reportlab.lib.pagesizes import A4
from reportlab.lib.units import mm
from reportlab.lib.enums import TA_JUSTIFY, TA_CENTER
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.colors import HexColor
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    BaseDocTemplate, PageTemplate, Frame, Paragraph, Spacer,
    Image as RLImage, Table, TableStyle, KeepTogether,
)

# ---------------------------------------------------------------- chemins

ICI = os.path.dirname(os.path.abspath(__file__))
RACINE = os.path.abspath(os.path.join(ICI, "..", ".."))
SORTIE = os.path.join(ICI, "visuels")
ARTICLE = os.path.join(ICI, "article-binance-ce-que-ca-change.md")
PDF = os.path.join(ICI, "article-binance.pdf")
LOGO = os.path.join(RACINE, "app", "src", "main", "res", "drawable", "logo_tex.png")
LOGO_ROND = os.path.join(RACINE, "app", "src", "main", "res", "drawable", "logorond.png")

POLICE = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
POLICE_GRAS = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"

# ------------------------------------------------- couleurs de la marque
# Reprises de app/src/main/java/com/vaultex/ui/theme/Theme.kt — un seul
# endroit décide des couleurs de VaultEx, et ce n'est pas ce fichier.

BLEU = "#1A6FE8"
BLEU_SOMBRE = "#0C447C"
VERT = "#16A34A"
ROUGE = "#EF4444"
ORANGE = "#F59E0B"
ENCRE = "#0F172A"
ENCRE_2 = "#64748B"
ENCRE_3 = "#94A3B8"
FOND = "#FFFFFF"
FOND_DOUX = "#F1F5F9"
TRAIT = "#E2E8F0"

# ------------------------------------------------------------- les faits
# Les chiffres vivent ici, en un seul exemplaire.

CHIFFRES = [
    ("6,3", "milliards $", "volés", "Montant annoncé par Binance", ROUGE),
    ("~3", "milliards $", "bloqués", "Avec d'autres acteurs du secteur", ORANGE),
    ("90", "%", "remboursés", "Sur les ressources de Binance", VERT),
]

SOURCE = ("Sources : communications de Binance (31 octobre) et presse "
          "spécialisée (Reuters, AP, Washington Post).")

CANAUX = [
    "Telegram  t.me/vaultexCommunity",
    "WhatsApp  +226 72 30 65 26",
    "Téléphone  +226 79 38 84 55",
    "Achat / Vente  +226 70 47 07 49",
    "Email  vaultexteams@gmail.com",
]


# =================================================== dessin (Pillow)

def fonte(taille, gras=False):
    return ImageFont.truetype(POLICE_GRAS if gras else POLICE, taille)


def largeur(dessin, texte, f):
    return dessin.textbbox((0, 0), texte, font=f)[2]


def couper(dessin, texte, f, largeur_max):
    """Découpe un texte en lignes qui tiennent dans largeur_max."""
    lignes, courante = [], ""
    for mot in texte.split():
        essai = (courante + " " + mot).strip()
        if largeur(dessin, essai, f) <= largeur_max or not courante:
            courante = essai
        else:
            lignes.append(courante)
            courante = mot
    if courante:
        lignes.append(courante)
    return lignes


def ecrire(dessin, xy, texte, f, couleur, ancre="la"):
    dessin.text(xy, texte, font=f, fill=couleur, anchor=ancre)


def paragraphe(dessin, x, y, texte, f, couleur, largeur_max, interligne=1.45):
    """Écrit un paragraphe et rend le y d'après la dernière ligne."""
    pas = int(f.size * interligne)
    for ligne in couper(dessin, texte, f, largeur_max):
        ecrire(dessin, (x, y), ligne, f, couleur)
        y += pas
    return y


def coller_logo(image, x, y, hauteur, chemin=LOGO):
    logo = Image.open(chemin).convert("RGBA")
    l = int(logo.width * hauteur / logo.height)
    logo = logo.resize((l, hauteur), Image.LANCZOS)
    image.paste(logo, (x, y), logo)
    return l


def croix(dessin, x, y, taille, couleur, epaisseur=7):
    d = taille // 2
    dessin.line([(x - d, y - d), (x + d, y + d)], fill=couleur, width=epaisseur)
    dessin.line([(x - d, y + d), (x + d, y - d)], fill=couleur, width=epaisseur)


def coche(dessin, x, y, taille, couleur, epaisseur=7):
    d = taille // 2
    dessin.line([(x - d, y), (x - d // 4, y + d)], fill=couleur, width=epaisseur)
    dessin.line([(x - d // 4, y + d), (x + d, y - d)], fill=couleur, width=epaisseur)


def bandeau(image, dessin, hauteur=150, titre=None):
    """Bandeau sombre du haut, avec le logo."""
    dessin.rectangle([0, 0, image.width, hauteur], fill=ENCRE)
    coller_logo(image, 60, (hauteur - 46) // 2, 46)
    if titre:
        ecrire(dessin, (image.width - 60, hauteur // 2), titre,
               fonte(26, True), ENCRE_3, ancre="rm")


def pied(image, dessin, texte, couleur=ENCRE_3):
    f = fonte(21)
    y = image.height - 86
    dessin.line([(60, y - 26), (image.width - 60, y - 26)], fill=TRAIT, width=2)
    paragraphe(dessin, 60, y, texte, f, couleur, image.width - 120, 1.3)


# ---------------------------------------------- visuel 1 : les chiffres

def visuel_chiffres(chemin):
    L, H = 1080, 1080
    im = Image.new("RGB", (L, H), FOND)
    d = ImageDraw.Draw(im)
    bandeau(im, d, titre="ALERTE SÉCURITÉ")

    y = 210
    ecrire(d, (60, y), "Vol chez Binance", fonte(62, True), ENCRE)
    y += 84
    y = paragraphe(d, 60, y,
                   "L'un des plus gros vols de l'histoire du secteur. "
                   "Voici les trois chiffres à retenir.",
                   fonte(27), ENCRE_2, L - 120)

    y += 26
    haut_tuile = 168
    for grand, unite, mot, detail, couleur in CHIFFRES:
        d.rounded_rectangle([60, y, L - 60, y + haut_tuile], radius=20,
                            fill=FOND_DOUX)
        d.rounded_rectangle([60, y, 70, y + haut_tuile], radius=5, fill=couleur)

        f_grand = fonte(74, True)
        f_unite = fonte(28, True)
        x = 110
        ecrire(d, (x, y + 56), grand, f_grand, couleur)
        x += largeur(d, grand, f_grand) + 14
        ecrire(d, (x, y + 90), unite, f_unite, couleur)

        ecrire(d, (L - 100, y + 58), mot, fonte(40, True), ENCRE, ancre="ra")
        ecrire(d, (L - 100, y + 112), detail, fonte(23), ENCRE_2, ancre="ra")
        y += haut_tuile + 22

    pied(im, d, SOURCE)
    im.save(chemin, "PNG")
    return chemin


# ------------------------------- visuel 2 : plateforme vs non-custodial

def visuel_plateforme(chemin):
    L, H = 1080, 1080
    im = Image.new("RGB", (L, H), FOND)
    d = ImageDraw.Draw(im)
    bandeau(im, d, titre="OÙ EST TON ARGENT ?")

    y = 196
    ecrire(d, (60, y), "Deux façons de garder", fonte(52, True), ENCRE)
    ecrire(d, (60, y + 62), "des cryptos", fonte(52, True), ENCRE)

    haut = 524
    y = 350
    largeur_col = (L - 120 - 28) // 2

    colonnes = [
        (60, ROUGE, "Sur une plateforme", [
            "Tes cryptos sont dans SES portefeuilles",
            "Un registre interne dit qui possède quoi",
            "Si ses systèmes tombent, tu dépends d'elle",
            "Ta protection = sa solidité financière",
        ], croix),
        (60 + largeur_col + 28, VERT, "Sur VaultEx", [
            "Tes clés sont sur TON téléphone",
            "Nous ne détenons aucun fonds",
            "Aucun registre de qui possède quoi",
            "Rien à voler chez nous : ce n'est pas là",
        ], coche),
    ]

    for x, couleur, titre, points, marque in colonnes:
        d.rounded_rectangle([x, y, x + largeur_col, y + haut], radius=24,
                            fill=FOND_DOUX)
        d.rounded_rectangle([x, y, x + largeur_col, y + 8], radius=4,
                            fill=couleur)
        ecrire(d, (x + 32, y + 44), titre, fonte(34, True), couleur)
        if couleur == VERT:
            ecrire(d, (x + 32, y + 90), "non-custodial", fonte(23), ENCRE_2)
        else:
            ecrire(d, (x + 32, y + 90), "custodial", fonte(23), ENCRE_2)

        yy = y + 146
        for point in points:
            marque(d, x + 50, yy + 14, 26, couleur, 6)
            lignes = couper(d, point, fonte(25), largeur_col - 110)
            for i, ligne in enumerate(lignes):
                ecrire(d, (x + 80, yy + i * 34), ligne, fonte(25), ENCRE)
            yy += 34 * len(lignes) + 28

    y += haut + 34
    d.rounded_rectangle([60, y, L - 60, y + 108], radius=18, fill="#FFF7E6")
    d.rounded_rectangle([60, y, 70, y + 108], radius=5, fill=ORANGE)
    paragraphe(d, 104, y + 24,
               "La contrepartie est réelle : personne ne peut perdre tes clés "
               "pour toi — et personne ne peut te les rendre non plus.",
               fonte(25), ENCRE, L - 190, 1.35)

    ecrire(d, (L // 2, H - 48),
           "VaultEx — ta phrase secrète est générée sur ton téléphone "
           "et n'en sort jamais.", fonte(21), ENCRE_3, ancre="ma")
    im.save(chemin, "PNG")
    return chemin


# ------------------------------------------ visuel 3 : la règle d'or

def visuel_regle(chemin):
    L, H = 1080, 1080
    im = Image.new("RGB", (L, H), ENCRE)
    d = ImageDraw.Draw(im)

    coller_logo(im, 60, 62, 46)
    ecrire(d, (L - 60, 85), "ARNAQUE EN COURS", fonte(26, True), ROUGE,
           ancre="rm")

    y = 172
    d.rounded_rectangle([60, y, L - 60, y + 140], radius=20, fill="#2A1215")
    d.rounded_rectangle([60, y, 70, y + 140], radius=5, fill=ROUGE)
    ecrire(d, (104, y + 28), "gbash.tools", fonte(48, True), ROUGE)
    ecrire(d, (104, y + 90), "n'a aucun rapport avec VaultEx.",
           fonte(28), "#FFFFFF")

    y += 196
    y = paragraphe(d, 60, y,
                   "Après chaque gros vol, les escrocs arrivent. Faux agents, "
                   "faux supports, « récupération de fonds ». Ils sont "
                   "rassurants et ils connaissent ton nom.",
                   fonte(27), ENCRE_3, L - 120, 1.4)

    y += 28
    d.rounded_rectangle([60, y, L - 60, y + 226], radius=24, fill=BLEU_SOMBRE)
    ecrire(d, (92, y + 30), "LA RÈGLE QUI TE PROTÈGE DE TOUT ÇA",
           fonte(23, True), "#9BC4FF")
    paragraphe(d, 92, y + 80,
               "Ta phrase de 12 ou 24 mots ne se tape JAMAIS sur un site web.",
               fonte(38, True), "#FFFFFF", L - 184, 1.3)
    ecrire(d, (92, y + 178),
           "Elle ne sert qu'à restaurer ton portefeuille dans VaultEx.",
           fonte(24), "#9BC4FF")

    y += 262
    for texte in ["Aucun site ne doit te la demander",
                  "Aucune « récupération de fonds » ne la demande",
                  "VaultEx ne te la demandera jamais, par aucun canal"]:
        croix(d, 84, y + 14, 25, ROUGE, 6)
        ecrire(d, (118, y), texte, fonte(26), "#FFFFFF")
        y += 50

    y += 14
    d.line([(60, y), (L - 60, y)], fill="#1E293B", width=2)
    y += 22
    y = paragraphe(d, 60, y,
                   "Si tu l'as déjà donnée : crée un nouveau portefeuille et "
                   "transfère tout, maintenant.",
                   fonte(25, True), ORANGE, L - 120, 1.35)
    y += 12
    ecrire(d, (60, y), "Nos seuls canaux : t.me/vaultexCommunity  ·  "
                       "+226 72 30 65 26  ·  +226 79 38 84 55",
           fonte(20), ENCRE_3)

    im.save(chemin, "PNG")
    return chemin


# ============================================================= le PDF

def en_jpeg(source, destination, fond=None):
    """Aplatit une image en JPEG sans transparence.

    Un PNG à canal alpha arrive dans le PDF en Flate avec un masque
    séparé, et les lecteurs allégés des téléphones sautent parfois ces
    images-là : la page s'affiche, le visuel manque. Le JPEG est le seul
    encodage qu'aucun lecteur ne refuse.
    """
    im = Image.open(source)
    if im.mode in ("RGBA", "LA", "P"):
        im = im.convert("RGBA")
        plat = Image.new("RGB", im.size, fond or "#FFFFFF")
        plat.paste(im, mask=im.split()[-1])
        im = plat
    else:
        im = im.convert("RGB")
    # subsampling=0 : sans ça le bleu du logo bave sur le fond sombre.
    im.save(destination, "JPEG", quality=95, subsampling=0, optimize=True)
    return destination


def lire_article():
    """Rend la liste (type, texte) des blocs de l'article."""
    with open(ARTICLE, encoding="utf-8") as f:
        brut = f.read()

    # L'article utile commence après le séparateur du chapeau de rédaction.
    corps = brut.split("\n---\n", 1)[1]
    # La liste des canaux est reprise en fin de PDF dans son propre encadré :
    # la laisser ici la ferait apparaître deux fois.
    corps = corps.split("**Nos seuls canaux officiels", 1)[0]

    blocs, tampon, type_tampon = [], [], "p"

    def vider():
        if tampon:
            blocs.append((type_tampon, " ".join(tampon).strip()))
            tampon.clear()

    for ligne in corps.split("\n"):
        nue = ligne.strip()
        if not nue:
            vider()
            type_tampon = "p"
            continue
        if nue.startswith("## "):
            vider()
            blocs.append(("h1", nue[3:]))
            type_tampon = "p"
        elif nue.startswith("### "):
            vider()
            blocs.append(("h2", nue[4:]))
            type_tampon = "p"
        elif nue.startswith("> "):
            if type_tampon != "citation":
                vider()
                type_tampon = "citation"
            tampon.append(nue[2:])
        elif nue.startswith("- "):
            vider()
            type_tampon = "puce"
            tampon.append(nue[2:])
        else:
            if type_tampon == "puce":
                tampon.append(nue)
            else:
                if type_tampon == "citation":
                    vider()
                type_tampon = "p"
                tampon.append(nue)
    vider()
    return blocs


def en_balises(texte):
    """Markdown gras / code vers les balises de reportlab."""
    texte = texte.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    texte = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", texte)
    texte = re.sub(r"\*(.+?)\*", r"<i>\1</i>", texte)
    texte = re.sub(r"`(.+?)`", r"<font face='DejaVuMono'>\1</font>", texte)
    return texte


def styles():
    pdfmetrics.registerFont(TTFont("DejaVu", POLICE))
    pdfmetrics.registerFont(TTFont("DejaVu-Bold", POLICE_GRAS))
    pdfmetrics.registerFont(TTFont(
        "DejaVuMono", "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf"))
    pdfmetrics.registerFontFamily("DejaVu", normal="DejaVu", bold="DejaVu-Bold",
                                  italic="DejaVu", boldItalic="DejaVu-Bold")
    s = {}
    s["titre"] = ParagraphStyle("titre", fontName="DejaVu-Bold", fontSize=24,
                                leading=30, textColor=HexColor(ENCRE),
                                spaceAfter=10)
    s["chapeau"] = ParagraphStyle("chapeau", fontName="DejaVu", fontSize=11.5,
                                  leading=17, textColor=HexColor(ENCRE_2),
                                  spaceAfter=16)
    s["h2"] = ParagraphStyle("h2", fontName="DejaVu-Bold", fontSize=14,
                             leading=19, textColor=HexColor(BLEU_SOMBRE),
                             spaceBefore=16, spaceAfter=7)
    s["p"] = ParagraphStyle("p", fontName="DejaVu", fontSize=10.5, leading=16.5,
                            textColor=HexColor(ENCRE), alignment=TA_JUSTIFY,
                            spaceAfter=9)
    s["puce"] = ParagraphStyle("puce", parent=s["p"], leftIndent=14,
                               bulletIndent=3, spaceAfter=5)
    s["citation"] = ParagraphStyle("citation", fontName="DejaVu-Bold",
                                   fontSize=11.5, leading=18,
                                   textColor=HexColor(BLEU_SOMBRE),
                                   leftIndent=12, rightIndent=12,
                                   spaceBefore=8, spaceAfter=12)
    s["legende"] = ParagraphStyle("legende", fontName="DejaVu", fontSize=8.5,
                                  leading=12, textColor=HexColor(ENCRE_3),
                                  alignment=TA_CENTER, spaceAfter=12)
    s["pied"] = ParagraphStyle("pied", fontName="DejaVu", fontSize=8,
                               textColor=HexColor(ENCRE_3))
    return s


def decor(canvas, doc):
    """Bandeau en haut, numéro de page en bas, sur chaque page."""
    canvas.saveState()
    L, H = A4
    canvas.setFillColor(HexColor(ENCRE))
    canvas.rect(0, H - 26 * mm, L, 26 * mm, stroke=0, fill=1)
    logo = Image.open(LOGO_PDF[0])
    h = 9 * mm
    l = logo.width * h / logo.height
    canvas.drawImage(LOGO_PDF[0], 18 * mm, H - 18.5 * mm, width=l, height=h)
    canvas.setFont("DejaVu", 8)
    canvas.setFillColor(HexColor(ENCRE_3))
    canvas.drawRightString(L - 18 * mm, H - 15 * mm, "Alerte sécurité")

    canvas.setStrokeColor(HexColor(TRAIT))
    canvas.setLineWidth(0.5)
    canvas.line(18 * mm, 15 * mm, L - 18 * mm, 15 * mm)
    canvas.setFillColor(HexColor(ENCRE_3))
    canvas.drawString(18 * mm, 11 * mm,
                      "VaultEx — portefeuille non-custodial · "
                      "t.me/vaultexCommunity")
    canvas.drawRightString(L - 18 * mm, 11 * mm, "Page %d" % doc.page)
    canvas.restoreState()


# Le logo aplati sur le bandeau sombre, posé par construire_pdf et lu par
# decor, qui est appelé par reportlab sans pouvoir recevoir d'argument.
LOGO_PDF = [LOGO]


def construire_pdf(images, atelier):
    s = styles()
    L, H = A4
    LOGO_PDF[0] = en_jpeg(LOGO, os.path.join(atelier, "logo.jpg"), ENCRE)
    images = [(titre, en_jpeg(png, os.path.join(
        atelier, os.path.basename(png).replace(".png", ".jpg"))), legende)
        for titre, png, legende in images]
    doc = BaseDocTemplate(PDF, pagesize=A4, title="VaultEx — Vol chez Binance :"
                          " ce que ça change pour toi",
                          author="VaultEx", subject="Alerte sécurité")
    cadre = Frame(18 * mm, 18 * mm, L - 36 * mm, H - 18 * mm - 30 * mm,
                  id="corps")
    doc.addPageTemplates([PageTemplate(id="page", frames=[cadre],
                                       onPage=decor)])

    histoire = []
    blocs = lire_article()

    for i, (genre, texte) in enumerate(blocs):
        if genre == "h1":
            histoire.append(Paragraph(en_balises(texte), s["titre"]))
            histoire.append(Paragraph(
                "Alerte sécurité VaultEx — 31 octobre 2026. " + SOURCE,
                s["chapeau"]))
        elif genre == "h2":
            histoire.append(Paragraph(en_balises(texte), s["h2"]))
        elif genre == "puce":
            for point in texte.split("\n"):
                histoire.append(Paragraph(en_balises(point), s["puce"],
                                          bulletText="•"))
        elif genre == "citation":
            t = Table([[Paragraph(en_balises(texte), s["citation"])]],
                      colWidths=[L - 36 * mm])
            t.setStyle(TableStyle([
                ("BACKGROUND", (0, 0), (-1, -1), HexColor("#EFF6FF")),
                ("LINEBEFORE", (0, 0), (0, -1), 3, HexColor(BLEU)),
                ("LEFTPADDING", (0, 0), (-1, -1), 10),
                ("RIGHTPADDING", (0, 0), (-1, -1), 10),
                ("TOPPADDING", (0, 0), (-1, -1), 8),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 2),
            ]))
            histoire.append(t)
        else:
            histoire.append(Paragraph(en_balises(texte), s["p"]))

        # Les visuels se posent après la section qu'ils illustrent.
        suivant = blocs[i + 1][1] if i + 1 < len(blocs) else ""
        for titre_section, image, legende in images:
            if genre == "h2" and texte.startswith(titre_section):
                continue
            if suivant.startswith(titre_section):
                histoire.append(figure(image, legende, s, L))

    histoire.append(Spacer(1, 10))
    # Sans ça, l'encadré se coupe en deux et les deux derniers canaux
    # partent seuls sur une page vide.
    histoire.append(KeepTogether(bloc_canaux(s, L)))
    doc.build(histoire)
    return PDF


def figure(chemin, legende, s, L):
    """Le visuel, réduit et centré : à pleine largeur un carré mange
    la moitié d'une page et pousse tout le texte à la page suivante."""
    im = Image.open(chemin)
    largeur_fig = 112 * mm
    hauteur_fig = im.height * largeur_fig / im.width
    t = Table([[RLImage(chemin, width=largeur_fig, height=hauteur_fig)]],
              colWidths=[L - 36 * mm])
    t.setStyle(TableStyle([
        ("ALIGN", (0, 0), (-1, -1), "CENTER"),
        ("LEFTPADDING", (0, 0), (-1, -1), 0),
        ("RIGHTPADDING", (0, 0), (-1, -1), 0),
        ("TOPPADDING", (0, 0), (-1, -1), 6),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
    ]))
    return KeepTogether([t, Paragraph(legende, s["legende"])])


def bloc_canaux(s, L):
    lignes = [[Paragraph("<b>Nos seuls canaux officiels</b>", ParagraphStyle(
        "c", fontName="DejaVu-Bold", fontSize=11, textColor=HexColor("#FFFFFF"),
        leading=15))]]
    for c in CANAUX:
        nom, valeur = c.split("  ", 1)
        lignes.append([Paragraph(
            "%s &nbsp;&nbsp;<font color='#FFFFFF'><b>%s</b></font>"
            % (nom, valeur.strip()),
            ParagraphStyle("d", fontName="DejaVu", fontSize=10, leading=15,
                           textColor=HexColor("#9BC4FF")))])
    lignes.append([Paragraph(
        "Tout autre lien ou numéro qui se réclame de VaultEx est faux.",
        ParagraphStyle("e", fontName="DejaVu", fontSize=9, leading=13,
                       textColor=HexColor(ENCRE_3), spaceBefore=6))])
    t = Table(lignes, colWidths=[L - 36 * mm])
    t.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), HexColor(ENCRE)),
        ("LEFTPADDING", (0, 0), (-1, -1), 14),
        ("RIGHTPADDING", (0, 0), (-1, -1), 14),
        ("TOPPADDING", (0, 0), (0, 0), 12),
        ("BOTTOMPADDING", (0, -1), (-1, -1), 12),
        ("TOPPADDING", (0, 1), (-1, -1), 1),
        ("BOTTOMPADDING", (0, 0), (-1, -2), 1),
    ]))
    return t


# =============================================================== départ

def main():
    os.makedirs(SORTIE, exist_ok=True)
    un = visuel_chiffres(os.path.join(SORTIE, "binance-1-chiffres.png"))
    deux = visuel_plateforme(os.path.join(SORTIE, "binance-2-plateforme.png"))
    trois = visuel_regle(os.path.join(SORTIE, "binance-3-regle.png"))

    # Chaque visuel se pose juste AVANT le titre de section indiqué —
    # donc à la fin de la section qui le précède, celle qu'il illustre.
    images = [
        ("Le chiffre qu'il faut regarder", un,
         "Les trois chiffres de l'incident, tels qu'annoncés par Binance."),
        ("Le danger immédiat", deux,
         "Là où sont tes cryptos décide de ce qui se passe quand ça tourne mal."),
        ("Si tu l'as déjà donnée", trois,
         "La seule règle à retenir — elle protège de toutes ces arnaques."),
    ]
    atelier = tempfile.mkdtemp(prefix="vaultex-pdf-")
    try:
        pdf = construire_pdf(images, atelier)
    finally:
        shutil.rmtree(atelier, ignore_errors=True)

    for chemin in (un, deux, trois, pdf):
        print("%8d o  %s" % (os.path.getsize(chemin), chemin))


if __name__ == "__main__":
    main()
