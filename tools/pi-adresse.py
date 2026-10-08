#!/usr/bin/env python3
"""
═══════════════════════════════════════════════════════════════════════════
QUELLE ADRESSE PI UNE PHRASE SECRÈTE DONNE-T-ELLE ?
═══════════════════════════════════════════════════════════════════════════

    python3 tools/pi-adresse.py

Ce script CALCULE, et rien d'autre. Il ne signe rien, n'envoie rien, ne
diffuse rien, n'écrit rien sur le disque.

─── CE QU'IL SERT À TRANCHER ────────────────────────────────────────────

VaultEx dérive ses adresses Pi au chemin m/44'/314159'/0'. Ce nombre vient
du registre SLIP-44, et AUCUN vecteur de test public ne l'atteste. C'est le
dernier paramètre du travail sur le Pi qui n'ait jamais été vérifié.

Il se vérifie d'une seule façon : prendre la phrase d'un Pi Wallet
EXISTANT, calculer l'adresse que ce chemin donne, et la comparer à celle
que le Pi Wallet affiche.

  Elles correspondent  → le chemin est bon, et tout le reste suit.
  Elles diffèrent      → le script montre les autres chemins essayés.
                         Celui qui tombe juste est le vrai, et il n'y a
                         plus qu'à corriger PiWallet.COIN_TYPE.

─── CE QU'IL NE FAIT PAS, ET COMMENT LE VÉRIFIER ────────────────────────

AUCUN ACCÈS RÉSEAU. Ni ici, ni dans pi-vecteurs.py dont il réutilise la
cryptographie. Les deux fichiers n'importent que hashlib, hmac, struct,
base64, zlib, unicodedata, getpass — rien qui sache ouvrir une connexion.

Vérifie-le toi-même avant de taper quoi que ce soit :

    Select-String -Path tools\\pi-adresse.py,tools\\pi-vecteurs.py `
      -Pattern 'requests|urllib|http|socket|fetch|post'

Si cette commande ne renvoie RIEN, aucun des deux fichiers ne peut parler
à l'extérieur. C'est vérifiable en dix secondes, et ça vaut mieux que ma
parole.

─── LA PHRASE ───────────────────────────────────────────────────────────

Elle est demandée en SAISIE MASQUÉE, jamais en argument de commande — un
argument atterrirait dans l'historique PowerShell, en clair, et y
resterait. Elle n'est ni affichée, ni enregistrée, ni journalisée. Elle
vit en mémoire le temps du calcul.

Elle est demandée DEUX FOIS. Sans écho, une faute de frappe est invisible,
et elle produirait une adresse parfaitement valide mais différente — on
conclurait que le chemin est faux alors qu'on aurait simplement mal tapé.

CONSEIL : utilise un Pi Wallet qui ne contient pas tes économies. Si le
tien en contient, déplace-les d'abord. Ce script est sûr ; l'habitude de
taper une phrase secrète dans un terminal ne l'est pas.
═══════════════════════════════════════════════════════════════════════════
"""
import getpass
import importlib.util
import pathlib
import sys
import unicodedata

# ─── Cryptographie réutilisée, jamais recopiée ──────────────────────
#
# pi-vecteurs.py est déjà prouvé contre les vecteurs officiels du SEP-0005
# et du SDK Stellar pour Go. En réécrire une copie ici, c'est se garantir
# qu'elles divergeront, et c'est l'erreur que ce dépôt documente ailleurs.
_chemin = pathlib.Path(__file__).with_name("pi-vecteurs.py")
_spec = importlib.util.spec_from_file_location("pi_vecteurs", _chemin)
_pv = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_pv)

H = 0x80000000

# Chemins candidats. Le premier est celui de VaultEx ; les autres sont là
# pour que, s'il tombe à côté, le script dise lequel est le bon plutôt que
# de se contenter de dire « non ».
CANDIDATS = [
    ("m/44'/314159'/0'", [44 | H, 314159 | H, 0 | H], "celui de VaultEx"),
    ("m/44'/314159'/0'/0'", [44 | H, 314159 | H, 0 | H, 0 | H], "variante à quatre niveaux"),
    ("m/44'/314159'/0'/0'/0'", [44 | H, 314159 | H, 0 | H, 0 | H, 0 | H], "variante à cinq niveaux"),
    ("m/44'/148'/0'", [44 | H, 148 | H, 0 | H], "celui de Stellar"),
    ("m/44'/0'/0'", [44 | H, 0 | H, 0 | H], "type de pièce 0"),
]


def _normaliser(phrase: str) -> str:
    """Minuscules, espaces simples — comme le fait toute implémentation BIP-39."""
    return " ".join(unicodedata.normalize("NFKD", phrase).lower().split())


def _demander() -> str:
    print("Colle la phrase secrète de ton Pi Wallet.")
    print("Rien ne s'affichera pendant la saisie — c'est normal.\n")
    a = _normaliser(getpass.getpass("Phrase : "))
    if not a:
        print("\nAucune phrase saisie. Arrêt.")
        sys.exit(1)
    b = _normaliser(getpass.getpass("À nouveau, pour vérifier : "))
    if a != b:
        print("\nLes deux saisies diffèrent. Recommence.")
        sys.exit(1)
    return a


def main() -> None:
    phrase = _demander()
    mots = len(phrase.split())
    print(f"\n{mots} mots lus.")
    if mots not in (12, 15, 18, 21, 24):
        print("  ⚠ Une phrase BIP-39 compte 12, 15, 18, 21 ou 24 mots.")
        print("    Vérifie ta saisie : ce qui suit sera calculé quand même,")
        print("    mais sur une phrase qu'aucun portefeuille ne produirait.")

    graine = _pv.bip39_seed(phrase)
    print("\nAdresses dérivées de cette phrase :\n")
    for libelle, chemin, note in CANDIDATS:
        cle = _pv.slip10_ed25519(graine, chemin)
        adresse = _pv.strkey_account(_pv.publickey(cle))
        print(f"  {libelle:<26} {adresse}")
        print(f"  {'':<26} ({note})\n")

    print("─" * 70)
    print("COMPARE avec l'adresse que ton Pi Wallet affiche.\n")
    print("  · Elle correspond à la PREMIÈRE ligne  → le chemin de VaultEx")
    print("    est le bon. Plus rien à corriger.\n")
    print("  · Elle correspond à une AUTRE ligne    → dis-moi laquelle.")
    print("    Il suffira de changer un nombre dans PiWallet.kt.\n")
    print("  · Elle ne correspond à AUCUNE          → relis ta saisie, puis")
    print("    dis-le-moi : le Pi Wallet dérive autrement, et il faudra")
    print("    chercher comment.\n")
    print("Ne me donne jamais la phrase — seulement laquelle des lignes")
    print("correspond, ou aucune.")


if __name__ == "__main__":
    main()
