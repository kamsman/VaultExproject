/*
═══════════════════════════════════════════════════════════════════════════
ARCHIVER LE mapping.txt DE LA VERSION QU'ON VIENT DE CONSTRUIRE
═══════════════════════════════════════════════════════════════════════════

    node tools/archiver-mapping.mjs

À lancer après chaque `assembleRelease` dont l'APK sera DISTRIBUÉ.

─── POURQUOI CE N'EST PAS UNE LIGNE DE SHELL ─────────────────────────────

J'en avais donné une :

    V=$(grep -oP 'versionName\s*=\s*"\K[^"]+' app/build.gradle.kts | ...)

Elle a créé un dossier nommé

    tools/mappings/1.0.$versionCodeCalcule$suffixeCommit/

parce que `versionName` n'est PAS un littéral dans ce projet : il vaut
`"1.0.$versionCodeCalcule$suffixeCommit"`, et les deux variables sont
calculées depuis git au moment de la compilation. Lire le fichier de
build revient à lire la recette au lieu du plat.

LA SEULE SOURCE FIABLE EST CELLE QUE GRADLE ÉCRIT LUI-MÊME, une fois le
calcul fait : `output-metadata.json`, posé à côté de l'APK.

─── POURQUOI ARCHIVER TOUT COURT ─────────────────────────────────────────

`assembleRelease` RÉÉCRIT `mapping.txt` à chaque fois. Un crash remonté
par Telegram porte des noms comme `j.T:1288` ; sans le mapping de CETTE
version-là, ils ne veulent rien dire et ne voudront jamais rien dire.

Mesuré : la pile du crash v1.0.1660 est définitivement illisible, parce
qu'un build postérieur avait écrasé son mapping avant qu'on y pense.
═══════════════════════════════════════════════════════════════════════════
*/

import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs'

const META = 'app/build/outputs/apk/release/output-metadata.json'
const MAPPING = 'app/build/outputs/mapping/release/mapping.txt'

function echec(titre, ...details) {
  console.log(`\n❌ ${titre}`)
  for (const d of details) console.log(`   ${d}`)
  console.log('')
  process.exit(1)
}

if (!existsSync(META)) {
  echec(
    'Aucun APK release construit.',
    `Fichier attendu : ${META}`,
    'Lance d\'abord : ./gradlew assembleRelease'
  )
}
if (!existsSync(MAPPING)) {
  echec(
    'Aucun mapping.txt.',
    `Fichier attendu : ${MAPPING}`,
    "Il n'est produit que si `isMinifyEnabled` vaut true sur la variante release."
  )
}

let version
try {
  const meta = JSON.parse(readFileSync(META, 'utf8'))
  version = meta?.elements?.[0]?.versionName
} catch (e) {
  echec('output-metadata.json illisible.', String(e).slice(0, 160))
}

if (!version) {
  echec('Pas de versionName dans output-metadata.json.',
    'Le format a peut-etre change avec la version du plugin Android.')
}

/*
LE GARDE-FOU QUI MANQUAIT À MA LIGNE DE SHELL.

Un nom qui contient « $ » n'est pas un nom : c'est un gabarit qu'on n'a
pas su développer. Mieux vaut refuser que créer un dossier dont le nom
ne désignera jamais rien.
*/
if (version.includes('$') || version.includes('{')) {
  echec(`Nom de version non développé : « ${version} »`,
    'Ce n\'est pas un numéro, c\'est un gabarit. Rien n\'a ete archive.')
}

const dossier = `tools/mappings/${version}`
const cible = `${dossier}/mapping.txt`

if (existsSync(cible)) {
  const avant = readFileSync(cible)
  const apres = readFileSync(MAPPING)
  if (avant.equals(apres)) {
    console.log(`\n✅ Deja archive, a l'identique : ${cible}\n`)
    process.exit(0)
  }
  /*
  DEUX MAPPINGS DIFFÉRENTS POUR UNE MÊME VERSION, ça veut dire qu'on a
  reconstruit sans que le numéro bouge — donc que l'APK distribué et
  celui du disque ne sont plus le même binaire. Écraser l'archive
  rendrait illisibles les crashs du premier.
  */
  echec(`Une archive DIFFERENTE existe deja pour ${version}.`,
    `Fichier : ${cible}`,
    'Deux binaires portent donc le meme numero. Si le premier a ete',
    'distribue, ecraser son mapping rendrait ses crashs illisibles.',
    'Renomme l\'ancien dossier avant de relancer.')
}

mkdirSync(dossier, { recursive: true })
writeFileSync(cible, readFileSync(MAPPING))

const lignes = readFileSync(cible, 'utf8').split('\n').length
console.log(`\n✅ ${cible}`)
console.log(`   version ${version} · ${lignes.toLocaleString('fr-FR')} lignes`)
console.log('')
console.log('   N\'OUBLIE PAS DE LE VERSIONNER :')
console.log(`     git add ${dossier} && git commit -m "mapping ${version}"`)
console.log('')
console.log('   Sur ta seule machine, un disque qui lache emporte toutes les')
console.log('   versions distribuees — et avec elles, toute chance de lire')
console.log('   un crash a posteriori.')
console.log('')
