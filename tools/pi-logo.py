#!/usr/bin/env python3
"""
Genere app/src/main/assets/crypto/pi.png — la pastille du Pi.

POURQUOI UN FICHIER LOCAL. Le depot d'icones utilise par CryptoIcon
(spothq/cryptocurrency-icons) n'a pas d'image pour le Pi : l'URL repond 404.
L'application tentait donc une requete vouee a l'echec a chaque affichage
d'une ligne Pi, et retombait sur les lettres « PI » — seule monnaie de
l'ecran sans logo.

CE N'EST PAS LE LOGO DE PI NETWORK. C'est la lettre grecque pi, blanche, sur
le violet de leur charte. Un symbole mathematique, pas une marque : on ne
reproduit pas un logo qui ne nous appartient pas. Si Pi Network publie un
jour un jeu d'icones reutilisable, ce fichier est a remplacer.

Aucune dependance : encodeur PNG ecrit a la main (zlib est dans la
bibliotheque standard). Lissage par sur-echantillonnage 4x.
"""
import struct, zlib, os

TAILLE = 128
SUR = 4                      # sur-echantillonnage pour le lissage
VIOLET = (0x7D, 0x46, 0x98)  # meme valeur que NetworkPi dans Theme.kt
BLANC = (255, 255, 255)


def dans_disque(x, y, cx, cy, r):
    return (x - cx) ** 2 + (y - cy) ** 2 <= r * r


def dans_pi(x, y):
    """La lettre pi, en trois rectangles : une barre et deux jambes."""
    barre = 30 <= x <= 98 and 38 <= y <= 50
    jambe_g = 40 <= x <= 52 and 50 < y <= 94
    jambe_d = 76 <= x <= 88 and 50 < y <= 94
    return barre or jambe_g or jambe_d


def rendre():
    lignes = []
    for py in range(TAILLE):
        ligne = bytearray()
        for px in range(TAILLE):
            # Moyenne des SUR x SUR sous-pixels : couleur + opacite.
            somme = [0, 0, 0]
            opaques = 0
            for sy in range(SUR):
                for sx in range(SUR):
                    x = px + (sx + 0.5) / SUR
                    y = py + (sy + 0.5) / SUR
                    if not dans_disque(x, y, TAILLE / 2, TAILLE / 2, TAILLE / 2 - 1):
                        continue
                    opaques += 1
                    c = BLANC if dans_pi(x, y) else VIOLET
                    for i in range(3):
                        somme[i] += c[i]
            total = SUR * SUR
            if opaques == 0:
                ligne += bytes((0, 0, 0, 0))
            else:
                ligne += bytes(somme[i] // opaques for i in range(3))
                ligne += bytes((round(255 * opaques / total),))
        lignes.append(bytes(ligne))
    return lignes


def ecrire_png(chemin, lignes):
    brut = b"".join(b"\x00" + l for l in lignes)   # filtre 0 par ligne

    def morceau(nom, donnees):
        c = nom + donnees
        return struct.pack(">I", len(donnees)) + c + struct.pack(">I", zlib.crc32(c))

    png = (b"\x89PNG\r\n\x1a\n"
           + morceau(b"IHDR", struct.pack(">IIBBBBB", TAILLE, TAILLE, 8, 6, 0, 0, 0))
           + morceau(b"IDAT", zlib.compress(brut, 9))
           + morceau(b"IEND", b""))
    os.makedirs(os.path.dirname(chemin), exist_ok=True)
    open(chemin, "wb").write(png)
    return len(png)


if __name__ == "__main__":
    cible = "app/src/main/assets/crypto/pi.png"
    taille = ecrire_png(cible, rendre())
    print(f"{cible} ecrit, {taille} octets, {TAILLE}x{TAILLE} RGBA")
