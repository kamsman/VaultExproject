/*
═══════════════════════════════════════════════════════════════════════════
LE CODE EN LIGNE EST-IL CELUI DU DÉPÔT ?
═══════════════════════════════════════════════════════════════════════════

    node tools/relais-cours/verifier-deploiement.mjs

─── POURQUOI CE FICHIER EXISTE ──────────────────────────────────────────

MESURÉ, ET ÇA A COÛTÉ CINQ ALLERS-RETOURS. Le dépôt était en v48. Le
Worker que l'application interroge tournait en v44. Quatre versions —
l'équipe, les contacts officiels, la connaissance de l'application, le
vocabulaire — étaient écrites, testées, poussées… et nulle part.

Rien ne le signalait. L'assistance répondait, l'écran marchait, les prix
s'affichaient. Elle répondait simplement comme il y a quatre versions. On
a donc cherché le défaut dans le code — en relisant du code déjà corrigé.

Un déploiement par copier-coller de sept mille lignes dans un éditeur de
navigateur échoue de plusieurs façons silencieuses : le collage tronque,
le bouton « Deploy » n'est pas pressé, ou le code part dans un second
Worker à une autre adresse. Aucune ne lève d'erreur du côté qu'on
regarde.

LA SEULE PREUVE EST LE NUMÉRO RENDU PAR LE SERVEUR. Ce script le
demande, et le compare à celui du fichier. Dix secondes, avant de
soupçonner quoi que ce soit d'autre.

─── IL INTERROGE L'ADRESSE DE L'APPLICATION, PAS UNE AUTRE ──────────────

Il lit `prix.relay.url` dans `local.properties` — l'adresse réellement
compilée dans l'APK. C'est ce détail qui distingue « j'ai déployé » de
« l'application reçoit mon déploiement » : on peut publier dans un Worker
parfaitement à jour que personne n'appelle.

L'adresse finit par `/api/v3/`, parce que le relais sert aussi de
mandataire aux cours. La sonde, elle, est à la RACINE — on retire donc
tout ce qui suit le domaine. Cette confusion a déjà renvoyé une erreur
CoinGecko à quelqu'un qui croyait interroger la sonde.

AUCUNE DÉPENDANCE, et rien d'autre n'est lu dans local.properties : ce
fichier contient aussi des mots de passe de signature.
═══════════════════════════════════════════════════════════════════════════
*/

import { readFileSync } from 'node:fs'

/** La version déclarée en tête de worker.js. */
function versionDuDepot() {
  const source = readFileSync(new URL('./worker.js', import.meta.url), 'utf8')
  const trouve = source.match(/^const VERSION = (\d+)/m)
  if (!trouve) {
    console.log('❌ VERSION introuvable dans worker.js.')
    process.exit(2)
  }
  return Number(trouve[1])
}

/**
 * L'adresse du relais, telle que l'application la connaît.
 *
 * Lue à la main, ligne par ligne : `local.properties` porte aussi des
 * secrets, et on ne veut en extraire qu'une seule clé.
 */
function adresseDuRelais() {
  const chemin = new URL('../../local.properties', import.meta.url)
  let lignes
  try {
    lignes = readFileSync(chemin, 'utf8').split('\n')
  } catch (_) {
    console.log('\n❌ local.properties introuvable a la racine du depot.')
    console.log("   C'est la que vit prix.relay.url, et il est ignore par git :")
    console.log('   sur une machine fraiche, il faut le recreer.\n')
    process.exit(2)
  }
  const ligne = lignes.find(
    (l) => l.includes('=') && l.split('=')[0].trim() === 'prix.relay.url'
  )
  if (!ligne) {
    console.log('\n❌ Aucune ligne prix.relay.url dans local.properties.')
    console.log("   Sans elle, l'application ne sait pas ou appeler.\n")
    process.exit(2)
  }
  return ligne.slice(ligne.indexOf('=') + 1).trim()
}

/*
LA RACINE, ET PAS L'ADRESSE TELLE QUELLE.

`https://vaultex-prix.xxx.workers.dev/api/v3/` + `sante` donnerait
`/api/v3/sante`, que le relais ne connaît pas — il le transmet donc à
CoinGecko, qui répond « Incorrect path ». Vu en vrai.
*/
function racine(brut) {
  try {
    const u = new URL(brut)
    return `${u.protocol}//${u.host}`
  } catch (_) {
    console.log(`\n❌ prix.relay.url n'est pas une adresse lisible : ${brut}\n`)
    process.exit(2)
  }
}

const attendue = versionDuDepot()
const brut = adresseDuRelais()
const base = racine(brut)

console.log('')
console.log(`Depot      : worker.js declare la version ${attendue}`)
console.log(`Application: ${brut}`)
console.log(`Sonde      : ${base}/sante`)
console.log('')

let sante
try {
  const r = await fetch(`${base}/sante`, { headers: { accept: 'application/json' } })
  if (!r.ok) {
    console.log(`❌ La sonde repond ${r.status}.`)
    console.log("   Le Worker ne repond pas a cette adresse, ou il n'existe pas.\n")
    process.exit(1)
  }
  sante = await r.json()
} catch (e) {
  console.log(`❌ Injoignable : ${String(e?.message ?? e).slice(0, 120)}\n`)
  process.exit(1)
}

/*
UNE RÉPONSE QUI N'EST PAS LA NÔTRE EST UN DIAGNOSTIC, pas une panne. Si
CoinGecko répond à notre place, c'est que la requête a traversé le relais
sans être reconnue — donc que l'adresse ou le chemin est faux.
*/
if (typeof sante?.version !== 'number') {
  console.log('❌ La reponse ne porte pas de numero de version :')
  console.log('   ' + JSON.stringify(sante).slice(0, 200))
  console.log('\n   Ce n’est pas notre sonde. Verifie prix.relay.url.\n')
  process.exit(1)
}

console.log(`En ligne   : version ${sante.version}`)
console.log(`Memoire    : ${sante.memoire ?? '(non dit)'}`)
console.log('')

let probleme = 0

if (sante.version !== attendue) {
  console.log(`❌ LE CODE EN LIGNE N'EST PAS CELUI DU DEPOT.`)
  console.log(`   En ligne ${sante.version}, depot ${attendue}.`)
  if (sante.version < attendue) {
    console.log(`   ${attendue - sante.version} version(s) ecrites et jamais publiees.`)
    console.log('')
    console.log('   Deployer :')
    console.log('     cd tools/relais-cours')
    console.log('     npx wrangler deploy --config wrangler.toml')
    console.log('     (npx.cmd sous PowerShell)')
    console.log('')
    console.log('   --config n’est pas decoratif : sans lui, Wrangler remonte')
    console.log("   l'arborescence et peut lire un wrangler.jsonc de la racine")
    console.log('   qui ne nous appartient pas.')
    console.log('')
    console.log('   Ou, depuis le tableau de bord Cloudflare : ouvrir le Worker')
    console.log(`   nomme « ${base.replace('https://', '').split('.')[0] } », Edit code,`)
    console.log('   TOUT selectionner, coller worker.js, puis DEPLOY — et relancer')
    console.log('   ce script. Un collage tronque ou un Deploy non presse laisse')
    console.log("   l'ancienne version en service, sans aucune erreur visible.")
  } else {
    console.log('   En ligne est PLUS RECENT que le depot : fais un git pull.')
  }
  probleme += 1
}

/*
LA MÉMOIRE EST VÉRIFIÉE ICI AUSSI, parce qu'un déploiement peut l'emporter.
Sans KV, le relais retombe sur le cache de Cloudflare — qui est par centre
de données, évinçable, et échoue en silence. Deux barrières s'éteignent
alors toutes seules : l'antériorité des demandes et « un versement ne paie
qu'une fois ». Rien ne le signale ailleurs.
*/
if (typeof sante.memoire === 'string' && !sante.memoire.startsWith('KV')) {
  console.log('')
  console.log(`❌ LA MEMOIRE DURABLE N'EST PAS LIEE : ${sante.memoire}`)
  console.log('   Le relais retombe sur le cache, qui oublie en silence.')
  console.log('   Workers & Pages → le Worker → Settings → Bindings → Add → KV')
  console.log('   Variable name : MEMOIRE')
  probleme += 1
}

console.log('')
if (probleme > 0) {
  process.exitCode = 1
} else {
  console.log(`✅ En ligne et a jour : version ${attendue}, memoire ${sante.memoire}.`)
}
console.log('')
