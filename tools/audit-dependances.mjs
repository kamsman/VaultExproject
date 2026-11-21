/*
═══════════════════════════════════════════════════════════════════════════
LES BIBLIOTHÈQUES VULNÉRABLES, DEMANDÉES À QUI LES CONNAÎT
═══════════════════════════════════════════════════════════════════════════

    node tools/audit-dependances.mjs

Lit les dépendances épinglées dans `app/build.gradle.kts`, les soumet à
OSV — la base de vulnérabilités d'open source maintenue par Google, qui
agrège les avis GitHub, les CVE et les bases des distributions — et rend
la liste de celles qui portent un avis.

─── POURQUOI UN OUTIL ET PAS UNE RÉPONSE ─────────────────────────────────

Parce qu'une vulnérabilité se vérifie, elle ne se récite pas. Un modèle
qui énumère des CVE de mémoire se trompe de version, invente des
identifiants, et manque celles publiées après son entraînement — c'est-
à-dire précisément celles qui comptent.

Et parce que la réponse d'aujourd'hui sera fausse dans un mois. Un avis
peut paraître demain sur une bibliothèque qui n'a pas bougé. Cet audit
doit pouvoir se relancer, pas se relire.

─── CE QU'IL NE VOIT PAS, ET IL FAUT LE SAVOIR ───────────────────────────

LES DÉPENDANCES TRANSITIVES. Ce fichier lit les versions ÉCRITES dans
`build.gradle.kts`. Or une bibliothèque en entraîne d'autres, et c'est
souvent l'une d'elles qui porte le défaut. Seul `./gradlew
:app:dependencies` donne l'arbre complet.

Pour l'avoir en entier :

    ./gradlew :app:dependencies --configuration releaseRuntimeClasspath > arbre.txt

puis relancer ce script avec le fichier en argument :

    node tools/audit-dependances.mjs arbre.txt

Il lit alors l'arbre résolu — transitives comprises — au lieu du fichier
de construction.

LES BOM. `compose-bom` et `firebase-bom` ne sont pas des bibliothèques :
ils fixent les versions d'un ensemble. OSV ne les connaît pas, et leurs
membres n'apparaissent que dans l'arbre résolu.
═══════════════════════════════════════════════════════════════════════════
*/

import { readFileSync } from 'node:fs'

const OSV = 'https://api.osv.dev'

/** Les coordonnées Maven d'un fichier de construction ou d'un arbre résolu. */
function dependances(texte) {
  const vues = new Map()
  // « groupe:artefact:version », quelle que soit la ponctuation autour :
  // guillemets du .kts, ou préfixes d'arbre « +--- » de Gradle.
  const motif = /([a-zA-Z0-9_.-]+:[a-zA-Z0-9_.-]+):([0-9][a-zA-Z0-9_.-]*)/g
  let m
  while ((m = motif.exec(texte)) !== null) {
    const [, nom, version] = m
    // Gradle écrit « demandée -> retenue » : c'est la retenue qui tourne.
    vues.set(`${nom}:${version}`, { nom, version })
  }
  return [...vues.values()].sort((a, b) => a.nom.localeCompare(b.nom))
}

async function interroger(paquets) {
  const r = await fetch(`${OSV}/v1/querybatch`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({
      queries: paquets.map((p) => ({
        package: { name: p.nom, ecosystem: 'Maven' },
        version: p.version,
      })),
    }),
  })
  if (!r.ok) throw new Error(`OSV a répondu ${r.status}`)
  return (await r.json()).results ?? []
}

/*
LE LOT NE REND QUE DES IDENTIFIANTS. Le détail — gravité, résumé,
version corrigée — demande un appel par avis. On ne le fait que pour les
paquets touchés, qui sont rares : demander le détail des quarante-cinq
coûterait quarante-cinq appels pour rien.
*/
async function detail(id) {
  try {
    const r = await fetch(`${OSV}/v1/vulns/${id}`)
    if (!r.ok) return null
    return await r.json()
  } catch {
    return null
  }
}

function gravite(v) {
  const s = v?.database_specific?.severity
  if (s) return String(s).toUpperCase()
  const c = v?.severity?.find((x) => x.type === 'CVSS_V3')?.score
  return c ? `CVSS ${c}` : 'non précisée'
}

/** La première version qui corrige, si l'avis la nomme. */
function corrigeePar(v, nom) {
  for (const a of v?.affected ?? []) {
    if (a?.package?.name !== nom) continue
    for (const r of a?.ranges ?? []) {
      const fixe = (r.events ?? []).find((e) => e.fixed)?.fixed
      if (fixe) return fixe
    }
  }
  return null
}

const source = process.argv[2] ?? 'app/build.gradle.kts'
const paquets = dependances(readFileSync(source, 'utf8'))

console.log(`Source   : ${source}`)
console.log(`Paquets  : ${paquets.length}`)
if (source.endsWith('.kts')) {
  console.log('ATTENTION : versions ÉCRITES seulement — les transitives ne sont pas vues.')
  console.log('            Voir l\'en-tête du fichier pour l\'arbre résolu.')
}
console.log('')

const resultats = await interroger(paquets)
let touches = 0

for (let i = 0; i < paquets.length; i++) {
  const ids = (resultats[i]?.vulns ?? []).map((v) => v.id)
  if (ids.length === 0) continue
  touches++
  const p = paquets[i]
  console.log(`── ${p.nom}:${p.version}`)
  for (const id of ids) {
    const v = await detail(id)
    const fixe = v ? corrigeePar(v, p.nom) : null
    console.log(`   ${id}  [${v ? gravite(v) : '?'}]`)
    if (v?.summary) console.log(`     ${v.summary}`)
    if (fixe) console.log(`     corrigé dans : ${fixe}`)
    console.log(`     https://osv.dev/vulnerability/${id}`)
  }
  console.log('')
}

console.log(
  touches === 0
    ? '✅ Aucun avis sur les versions examinées.'
    : `⚠️  ${touches} paquet(s) avec au moins un avis.`
)
if (touches > 0) {
  console.log('')
  console.log('UN AVIS N\'EST PAS UNE FAILLE EXPLOITABLE CHEZ VOUS. Il faut lire')
  console.log('ce qu\'il décrit et vérifier si ce chemin de code est employé ici.')
  console.log('Le lien osv.dev le dit en quelques lignes.')
}
