#!/usr/bin/env python3
"""
Valide les fichiers de chaînes AVANT de lancer Gradle.

POURQUOI CE SCRIPT EXISTE
-------------------------
Un `xml.etree.ElementTree.parse()` qui réussit ne prouve RIEN sur la validité
d'un fichier de ressources Android. AAPT2 applique des règles supplémentaires
que le parseur XML générique ignore complètement. Une apostrophe non échappée
passe le parseur XML sans broncher, puis fait échouer `mergeDebugResources`
avec un message parfaitement opaque :

    Can not extract resource from com.android.aaptcompiler.ParsedResource@...

Ce message ne nomme ni le fichier fautif, ni la ligne, ni la règle violée.
C'est exactement le genre de panne qui coûte une itération complète de
compilation pour trouver un caractère. Ce script attrape la faute en une
seconde et dit précisément où elle est.

RÈGLES VÉRIFIÉES
----------------
1. Apostrophe `'` non échappée         → ERREUR AAPT (build cassé)
2. Guillemet droit `"` non échappé     → silencieusement SUPPRIMÉ à la
                                          compilation (le texte s'affiche
                                          sans ses guillemets)
3. `@` ou `?` en début de chaîne       → interprété comme une référence de
                                          ressource, pas comme du texte
4. Chaîne présente en français mais    → l'app retombe en français au milieu
   absente en anglais ou en arabe        d'un écran anglais ou arabe
5. Nombre de `%s` / `%d` divergent     → plantage à l'exécution sur la locale
   entre locales                         concernée (IllegalFormatException)

USAGE
-----
    python3 tools/check_strings.py

Sortie 0 = rien à signaler. Sortie 1 = au moins une erreur bloquante.
"""

import os
import re
import sys
import xml.etree.ElementTree as ET

RES_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res")

# Locales à comparer avec la référence française (values/).
TRANSLATED = ["values-en", "values-ar"]

FORMAT_SPEC = re.compile(r"%(?:\d+\$)?[sdfx]")


def raw_bodies(path):
    """
    Renvoie [(nom, corps_brut, no_ligne)] en lisant le FICHIER TEL QUEL.

    On ne passe pas par ElementTree pour extraire le corps : ET normalise les
    entités et nous ferait perdre justement les caractères qu'on veut
    inspecter. On garde ET uniquement pour valider la syntaxe XML de base.
    """
    with open(path, encoding="utf-8") as fh:
        content = fh.read()

    out = []
    for match in re.finditer(
        r'<string\s+name="([^"]+)"[^>]*>(.*?)</string>', content, re.DOTALL
    ):
        line = content.count("\n", 0, match.start()) + 1
        out.append((match.group(1), match.group(2), line))
    return out


def unescaped(body, char):
    """Vrai si `char` apparaît sans `\\` devant, hors balise imbriquée."""
    stripped = re.sub(r"<[^>]+>", "", body)          # ignore <b>, <xliff:g>, …
    for i, c in enumerate(stripped):
        if c == char and (i == 0 or stripped[i - 1] != "\\"):
            return True
    return False


def main():
    errors, warnings = [], []
    catalogue = {}

    for locale in ["values"] + TRANSLATED:
        path = os.path.join(RES_DIR, locale, "strings.xml")
        if not os.path.exists(path):
            continue

        # Syntaxe XML de base — nécessaire mais très loin d'être suffisante.
        try:
            ET.parse(path)
        except ET.ParseError as exc:
            errors.append(f"{locale}/strings.xml : XML invalide — {exc}")
            continue

        catalogue[locale] = {}
        for name, body, line in raw_bodies(path):
            catalogue[locale][name] = body
            where = f"{locale}/strings.xml:{line}  <{name}>"

            if unescaped(body, "'"):
                errors.append(f"{where}\n    apostrophe ' non échappée — écrire \\'")

            if unescaped(body, '"'):
                warnings.append(
                    f'{where}\n    guillemet " non échappé — sera SUPPRIMÉ à '
                    'la compilation, écrire \\" ou utiliser « »'
                )

            text = body.strip()
            if text[:1] in ("@", "?"):
                errors.append(
                    f"{where}\n    commence par « {text[0]} » — Android y verra une "
                    f"référence de ressource, échapper avec \\{text[0]}"
                )

    # Couverture et cohérence des locales.
    reference = catalogue.get("values", {})
    for locale in TRANSLATED:
        if locale not in catalogue:
            continue
        for name, body in reference.items():
            if name not in catalogue[locale]:
                warnings.append(
                    f"{locale}/strings.xml : <{name}> absente — cet écran "
                    "s'affichera en français"
                )
                continue
            expected = sorted(FORMAT_SPEC.findall(body))
            actual = sorted(FORMAT_SPEC.findall(catalogue[locale][name]))
            if expected != actual:
                errors.append(
                    f"{locale}/strings.xml : <{name}> attend {expected} mais "
                    f"contient {actual} — plantage garanti sur cette locale"
                )

    for w in warnings:
        print(f"AVERTISSEMENT  {w}")
    for e in errors:
        print(f"ERREUR         {e}")

    total = len(reference)
    if errors:
        print(f"\n{len(errors)} erreur(s) bloquante(s), {len(warnings)} avertissement(s).")
        print("Le build Gradle échouera. Corriger avant de compiler.")
        return 1

    print(f"\n{total} chaînes vérifiées sur {len(catalogue)} locales — "
          f"aucune erreur bloquante, {len(warnings)} avertissement(s).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
