#!/usr/bin/env python3
"""
Détecte les constantes référencées par les tests mais absentes du code principal.

POURQUOI CE SCRIPT EXISTE
-------------------------
`SwapUseCaseFeeTest` appelait `SwapUseCase.MOBILE_MONEY_FEE_PERCENT` des mois
après le retrait de cette constante avec l'écran Mobile Money. Personne ne l'a
vu, pour une raison mécanique : le code de test se compile dans une tâche
SÉPARÉE du code principal. `assembleDebug` réussit, l'APK se construit, tout va
bien — jusqu'au jour où quelqu'un lance une tâche qui touche
`compileDebugUnitTestKotlin`, et le build casse sur un symbole disparu depuis
longtemps, sans rapport avec ce qu'on était en train de faire.

Ce contrôle relit les deux arbres et signale les références mortes en une
seconde, sans compilateur ni SDK Android.

CE QU'IL VÉRIFIE
----------------
Les références de la forme `MaClasse.MA_CONSTANTE` dans src/test, où
`MaClasse` est un type DÉFINI DANS LE PROJET (les classes de la bibliothèque
standard et d'Android sont ignorées : on n'a aucun moyen fiable de les
résoudre ici, et ce n'est pas le sujet).

Volontairement limité aux identifiants en MAJUSCULES : ce sont les constantes,
c'est là que se produisent les ruptures silencieuses, et la règle ne produit
pratiquement aucun faux positif.

USAGE
-----
    python3 tools/check_test_refs.py

Sortie 0 = aucune référence morte.
"""

import os
import re
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src")
MAIN = os.path.join(ROOT, "main", "java")
TEST = os.path.join(ROOT, "test", "java")

# `Classe.CONSTANTE` — constante en majuscules, au moins deux caractères.
REF = re.compile(r"\b([A-Z][A-Za-z0-9_]*)\.([A-Z][A-Z0-9_]{1,})\b")
# Déclarations de types du projet.
DECL = re.compile(r"\b(?:class|object|interface|enum class)\s+([A-Za-z0-9_]+)")


def kt_files(root):
    for dirpath, _, files in os.walk(root):
        for f in files:
            if f.endswith(".kt"):
                yield os.path.join(dirpath, f)


def main():
    if not os.path.isdir(TEST):
        print("Aucun répertoire de tests.")
        return 0

    # 1) Types définis dans le projet, et tous les identifiants du code principal.
    project_types = set()
    main_identifiers = set()
    for path in kt_files(MAIN):
        content = open(path, encoding="utf-8").read()
        project_types.update(DECL.findall(content))
        main_identifiers.update(re.findall(r"\b[A-Z][A-Z0-9_]{1,}\b", content))

    # 2) Références des tests vers ces types.
    dead = []
    for path in kt_files(TEST):
        # Un test peut définir ses propres constantes : on les tolère.
        content = open(path, encoding="utf-8").read()
        local = set(re.findall(r"\b[A-Z][A-Z0-9_]{1,}\b",
                               "\n".join(l for l in content.splitlines()
                                         if re.search(r"\b(val|var|const)\b", l))))
        for lineno, line in enumerate(content.splitlines(), 1):
            stripped = line.strip()
            if stripped.startswith(("//", "*", "/*")):
                continue
            for owner, const in REF.findall(line):
                if owner not in project_types:
                    continue                     # type externe : non résoluble ici
                if const in main_identifiers or const in local:
                    continue
                dead.append((os.path.relpath(path, ROOT), lineno, owner, const))

    for rel, lineno, owner, const in dead:
        print(f"ERREUR  {rel}:{lineno}")
        print(f"        {owner}.{const} n'existe pas dans le code principal")

    if dead:
        print(f"\n{len(dead)} référence(s) morte(s). "
              f"`compileDebugUnitTestKotlin` échouera.")
        return 1

    print("Aucune référence morte dans les tests.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
