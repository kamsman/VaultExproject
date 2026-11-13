#!/usr/bin/env node
// ==========================================================================
// Chaque réglage lu par le relais est-il documenté, et l'inverse ?
// ==========================================================================
//
//   node verifier-reglages.mjs
//
// --------------------------------------------------------------------------
// POURQUOI CE SCRIPT EXISTE
// --------------------------------------------------------------------------
//
// MESURÉ LE 13 NOVEMBRE 2026. Deux endroits lisaient
// `CHANGE_MARGE_FCFA_PAR_DOLLAR`, un réglage qui n'existe pas. Ils tombaient
// donc sur leur valeur par défaut — 25 — pendant que la marge réelle était
// de 50, et l'assistance annonçait la moitié du prix aux clients.
//
// Rien ne pouvait le signaler. Un nom de réglage est une chaîne de
// caractères : un nom faux ne lève pas, et un réglage absent ne se distingue
// pas d'un réglage laissé à sa valeur par défaut.
//
// Ce qui aurait permis de le voir, c'est de comparer la liste de ce que le
// code LIT à la liste de ce qui est DOCUMENTÉ. Un nom lu mais absent de la
// documentation est soit un oubli de documentation, soit une faute de frappe
// — et dans les deux cas il mérite un regard.
//
// --------------------------------------------------------------------------
// CE QU'IL NE PEUT PAS FAIRE
// --------------------------------------------------------------------------
//
// Il ne sait pas ce qui est réglé dans Cloudflare. Un réglage documenté, lu
// par le code, et jamais posé dans le tableau de bord reste invisible ici :
// c'est /diag et /change/parametres qui répondent à cette question-là.
//
// Il vérifie la cohérence entre deux choses qui sont dans ce dépôt, et rien
// de plus. C'est déjà ce qui manquait.
// ==========================================================================

import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const ici = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(join(ici, 'worker.js'), 'utf8')

/*
LA DOCUMENTATION S'ARRÊTE À `const VERSION`.

Tout ce qui précède est l'en-tête de commentaires ; tout ce qui suit est du
code et des commentaires de code. Chercher les noms documentés dans le
fichier entier ferait passer pour « documenté » tout nom cité dans
n'importe quel commentaire interne.
*/
const finEntete = source.indexOf('const VERSION')
if (finEntete < 0) {
  console.error("  worker.js ne contient pas `const VERSION` — fichier inattendu.")
  process.exit(2)
}
const entete = source.slice(0, finEntete)
const code = source.slice(finEntete)

/*
UNE LIAISON N'EST PAS UN RÉGLAGE. MEMOIRE est déclarée dans wrangler.toml,
pas dans Variables and Secrets, et elle a son propre contrôle —
`verifier-deploiement.mjs` vérifie qu'elle est bien là.
*/
const LIAISONS = new Set(['MEMOIRE'])

const lus = new Set(
  [...code.matchAll(/env\?\.([A-Z_][A-Z0-9_]*)/g)]
    .map((m) => m[1])
    .filter((n) => !LIAISONS.has(n))
)

/*
UN NOM EST « DOCUMENTÉ » s'il apparaît dans l'en-tête. On ne cherche pas
une mise en forme particulière : exiger un tableau alignerait ce script sur
une présentation, et la présentation changera.
*/
const documentes = new Set(
  [...entete.matchAll(/\b([A-Z][A-Z0-9_]{4,})\b/g)]
    .map((m) => m[1])
    .filter((n) => n.includes('_') && !LIAISONS.has(n))
)

const nonDocumentes = [...lus].filter((n) => !documentes.has(n)).sort()

/*
L'INVERSE EST UN AVERTISSEMENT, PAS UNE ERREUR. L'en-tête cite des noms qui
ne sont pas des réglages — des en-têtes HTTP, des constantes, des exemples.
Les signaler aide à repérer un réglage supprimé du code et oublié dans la
documentation, mais une correspondance parfaite n'est pas l'objectif.
*/
const SANS_OBJET = new Set([
  'COINGECKO_KEY', 'HTTP_PROXY', 'HTTPS_PROXY',
  /*
  CELUI-LÀ N'A JAMAIS EXISTÉ, et c'est pour ça qu'il est cité.

  L'en-tête le nomme en racontant le défaut du 13 novembre — deux
  endroits le lisaient, et annonçaient 25 FCFA de marge au lieu de 50.
  Le script le verrait donc comme un réglage documenté et jamais lu,
  c'est-à-dire exactement ce qu'il est censé signaler.

  L'exclure garde la sortie propre, pour qu'un VRAI réglage retiré du
  code se voie au premier coup d'œil au lieu de se perdre à côté d'un
  avertissement permanent.
  */
  'CHANGE_MARGE_FCFA_PAR_DOLLAR',
])
const documentesNonLus = [...documentes]
  .filter((n) => !lus.has(n) && n.startsWith('CHANGE_') && !SANS_OBJET.has(n))
  .sort()

console.log()
console.log(`  ${lus.size} réglages lus par le code, ${documentes.size} cités dans l'en-tête.`)
console.log()

if (documentesNonLus.length) {
  console.log('  ⚠ Documentés mais JAMAIS LUS — réglage retiré du code ?')
  for (const n of documentesNonLus) console.log(`      ${n}`)
  console.log()
}

if (nonDocumentes.length) {
  console.log('  ✗ LUS MAIS NON DOCUMENTÉS :')
  for (const n of nonDocumentes) console.log(`      ${n}`)
  console.log()
  console.log('  Chacun est soit un oubli de documentation, soit une faute de')
  console.log("  frappe qui fait retomber le code sur une valeur par défaut.")
  console.log('  Les deux se corrigent ici, pas en production.')
  console.log()
  process.exit(1)
}

console.log('  ✓ Tout réglage lu est documenté.')
console.log()
