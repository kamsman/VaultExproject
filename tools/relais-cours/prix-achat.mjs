/*
═══════════════════════════════════════════════════════════════════════════
CE QU'UN CLIENT PAIERAIT AUJOURD'HUI, RÉSEAU PAR RÉSEAU
═══════════════════════════════════════════════════════════════════════════

    node tools/relais-cours/prix-achat.mjs
    node tools/relais-cours/prix-achat.mjs --cible 660
    node tools/relais-cours/prix-achat.mjs --cible 660 --montant 10

`--cible` : le taux client visé, en FCFA par dollar. L'outil dit alors
quelle valeur de `CHANGE_MARGE` l'atteint aujourd'hui.

─── POURQUOI CET OUTIL ───────────────────────────────────────────────────

Une table de tarifs se construit sur un prix fournisseur — « Mr. Sako
vend à 640 ». Le relais, lui, calcule sur le COURS DU MARCHÉ :

    prix client = cours + CHANGE_MARGE

Et le cours n'est pas 640. Le franc CFA est arrimé à l'euro par une
parité fixe (655,957 XOF pour 1 EUR), donc le dollar vaut entre 545 et
635 francs selon l'euro du jour. Le 640 d'un fournisseur contient déjà
SA marge.

POSER `CHANGE_MARGE = 20` EN CROYANT VENDRE À 660 FERAIT VENDRE À ~620.
C'est-à-dire sous le prix d'achat, sur chaque transaction, sans qu'aucune
alerte ne se déclenche — une perte qui ne se voit qu'en comptant.

Cet outil lit le cours réel et la marge réellement posée, et montre le
prix qui en sort.

─── LES FRAIS RÉSEAU SE CONVERTISSENT AU MARCHÉ, PAS AU TAUX CLIENT ──────

Mesuré dans `ChangeViewModel.fraisFixesFcfa` : `usd * fcfaParDollar`.

Une table qui écrit `(10 + frais) × 660` applique donc la marge au frais
aussi. L'écart vaut `frais × marge` — 40 FCFA sur un frais de 2 $ avec
une marge de 20. Le relais a un argument : prendre une marge sur un coût
qu'on ne garde pas, c'est annoncer un frais réseau qui n'en est plus un.
═══════════════════════════════════════════════════════════════════════════
*/

import { readFileSync } from 'node:fs'

const PEG = 655.957 // 1 EUR = 655,957 XOF, parité fixe

function relaisDepuisProprietes() {
  for (const chemin of ['local.properties', '../../local.properties']) {
    try {
      const ligne = readFileSync(chemin, 'utf8')
        .split('\n')
        .find((l) => l.trim().startsWith('prix.relay.url'))
      if (!ligne) continue
      const v = ligne.split('=').slice(1).join('=').trim()
      if (v) return v.replace(/\/?api\/v3\/?$/, '').replace(/\/$/, '')
    } catch (_) { /* on essaie le suivant */ }
  }
  return ''
}

const args = process.argv.slice(2)
const nomme = (nom, defaut) => {
  const i = args.indexOf(nom)
  return i >= 0 && args[i + 1] !== undefined ? Number(args[i + 1]) : defaut
}
const cible = nomme('--cible', null)
const montant = nomme('--montant', 10)
const relaisArg = args.find((a) => a.startsWith('https://'))
const relais = (relaisArg || relaisDepuisProprietes()).replace(/\/$/, '')

if (!relais.startsWith('https://')) {
  console.log('\n❌ Adresse du relais introuvable.')
  console.log('   Lance depuis le depot, ou passe l adresse en argument.')
  process.exit(2)
}

async function lire(chemin) {
  const r = await fetch(`${relais}${chemin}`)
  const brut = await r.text()
  if (!r.ok) throw new Error(`HTTP ${r.status} sur ${chemin} — ${brut.slice(0, 120)}`)
  return JSON.parse(brut)
}

console.log(`\nRelais : ${relais}`)

let params, prix
try {
  ;[params, prix] = await Promise.all([
    lire('/change/parametres'),
    lire('/api/v3/simple/price?ids=tether&vs_currencies=xof,usd'),
  ])
} catch (e) {
  console.log(`\n❌ ${e.message}`)
  process.exit(1)
}

/*
LE COURS SE DÉDUIT D'UNE MONNAIE COTÉE DANS LES DEUX DEVISES, exactement
comme `TauxFcfa.fcfaParDollar` le fait dans l'application. On prend le
tether : son cours en dollars vaut un, donc son cours en francs EST le
taux, et un écart viendrait de la source et non de la volatilité.
*/
const xof = Number(prix?.tether?.xof)
const usd = Number(prix?.tether?.usd)
if (!(xof > 0) || !(usd > 0)) {
  console.log('\n❌ Cours du tether illisible dans la reponse du relais.')
  console.log(`   Recu : ${JSON.stringify(prix).slice(0, 200)}`)
  process.exit(1)
}
const cours = xof / usd
const marge = Number(params?.margeFcfaParDollar ?? 0)
const client = Math.round(cours + marge)

console.log(`\n${'─'.repeat(72)}`)
console.log(`Cours du marche   : ${cours.toFixed(1)} FCFA/USD   (euro implicite : ${(PEG / cours).toFixed(3)})`)
console.log(`CHANGE_MARGE      : ${marge} FCFA/USD`)
console.log(`Prix client       : ${client} FCFA/USD`)
console.log('─'.repeat(72))

if (cible !== null) {
  const besoin = Math.round(cible - cours)
  const ecart = besoin - marge
  console.log(`\nPour viser ${cible} FCFA/USD aujourd'hui : CHANGE_MARGE = ${besoin}`)
  if (ecart !== 0) {
    console.log(`  → la marge posee est ${marge}, soit ${ecart > 0 ? ecart + ' DE MOINS' : -ecart + ' de trop'} que la cible.`)
  }
  /*
  LE COURS BOUGE AVEC L'EURO, DONC UNE MARGE FIXE NE TIENT PAS UN TAUX
  FIXE. On le dit plutot que de laisser croire qu'on vient de figer 660.
  */
  console.log(`\n  Attention : le cours suit l'euro. Avec CHANGE_MARGE = ${besoin},`)
  for (const e of [1.04, 1.12, 1.20]) {
    const c = PEG / e
    console.log(`    EUR/USD ${e.toFixed(2)} → cours ${c.toFixed(0)} → client ${Math.round(c + besoin)} FCFA/USD`)
  }
  console.log('  Une marge en francs fixes ne tient pas un prix fixe.')
}

const fraisParMonnaie = params?.fraisUsdAchatParMonnaie ?? {}
const forfait = Number(params?.fraisUsdAchat ?? 0)
const monnaies = Object.keys(fraisParMonnaie)

console.log(`\n${'─'.repeat(72)}`)
console.log(`CE QUE PAIE UN CLIENT POUR ${montant} USD`)
console.log('─'.repeat(72))
if (monnaies.length === 0) {
  console.log(`CHANGE_FRAIS_USD_PAR_MONNAIE n'est pas posee.`)
  console.log(`Toutes les monnaies retombent sur le forfait CHANGE_FRAIS_USD = ${forfait} $.`)
  monnaies.push('(toutes)')
}
console.log('')
const g = (v, n) => String(v).padEnd(n)
const d = (v, n) => String(v).padStart(n)
console.log(`${g('monnaie', 12)} ${d('frais $', 8)} ${d('frais FCFA', 11)} ${d('total', 9)} ${d('par USDT', 10)}`)
for (const m of monnaies) {
  const f = m === '(toutes)' ? forfait : Number(fraisParMonnaie[m])
  // Converti au COURS, pas au prix client : voir l'en-tete.
  const fFcfa = f * cours
  const total = montant * client + fFcfa
  console.log(
    `${g(m, 12)} ${d(f.toFixed(2), 8)} ${d(fFcfa.toFixed(0), 11)} ${d(Math.round(total), 9)} ${d((total / montant).toFixed(1), 10)}`
  )
}
console.log('')
console.log('« par USDT » est ce que le client paie reellement par unite recue.')
console.log("C'est ce chiffre-la qu'il comparera a la concurrence, pas le taux.")
console.log('')
