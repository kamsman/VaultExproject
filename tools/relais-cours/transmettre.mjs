/*
═══════════════════════════════════════════════════════════════════════════
TRANSMETTRE UNE VENTE AU CHANGEUR, DEPUIS LE PC
═══════════════════════════════════════════════════════════════════════════

    node tools/relais-cours/transmettre.mjs USDT-BNB 6 VX-FFA3DRSG 70123456

    <monnaie> <montant socle> <reference> <numero Mobile Money>

─── POURQUOI CET OUTIL EXISTE ───────────────────────────────────────────

Une vente vérifiée dont l'écran a été fermé ne peut plus être transmise :
l'application garde la demande en cours, mais une fois sa trace perdue —
désinstallation, changement d'appareil, délai dépassé — le versement reste
sur la chaîne sans que personne ne demande à être payé.

Ce n'est pas un ordre fabriqué. Le relais REVÉRIFIE tout avant d'envoyer
quoi que ce soit : le versement doit être sur la chaîne, au montant marqué
dérivé de cette référence, dans la fenêtre. Cet outil ne peut pas faire
dire « vérifié » à une vente qui ne l'est pas — il ne fait que poser la
demande que l'écran aurait posée.

─── CE QU'IL CALCULE, ET CE QU'IL NE DEVINE PAS ─────────────────────────

Le montant en francs vient du cours du moment et de la marge réglée,
exactement comme l'application le calcule. On ne l'invente pas : un
montant inventé serait celui que le changeur paierait.
═══════════════════════════════════════════════════════════════════════════
*/

import { readFileSync } from 'node:fs'

const [monnaieBrute, montantBrut, referenceBrute, numeroBrut, relaisArg] = process.argv.slice(2)

if (!monnaieBrute || !montantBrut || !referenceBrute || !numeroBrut) {
  console.log('\nUsage :')
  console.log('  node tools/relais-cours/transmettre.mjs <MONNAIE> <montant> <reference> <numero>\n')
  console.log('Exemple :')
  console.log('  node tools/relais-cours/transmettre.mjs USDT-BNB 6 VX-FFA3DRSG 70123456\n')
  console.log('Le <montant> est le SOCLE — celui tape dans l’application,')
  console.log('sans les decimales du marquage.')
  process.exit(2)
}

const monnaie = monnaieBrute.trim().toUpperCase()
const montant = Number(String(montantBrut).replace(',', '.'))
const reference = referenceBrute.trim().toUpperCase()
const numero = numeroBrut.replace(/\D/g, '')

if (numero.length !== 8) {
  console.log(`\n❌ Le numero doit faire huit chiffres. Recu : « ${numeroBrut} »`)
  process.exit(2)
}

/** L'adresse du relais, depuis local.properties. Une seule ligne est lue. */
function relaisDepuisProprietes() {
  for (const chemin of ['local.properties', '../../local.properties']) {
    try {
      const ligne = readFileSync(chemin, 'utf8')
        .split('\n')
        .find((l) => l.trim().startsWith('prix.relay.url'))
      if (!ligne) continue
      const valeur = ligne.split('=').slice(1).join('=').trim()
      if (valeur) return valeur.replace(/\/?api\/v3\/?$/, '').replace(/\/$/, '')
    } catch (_) {
      /* fichier absent ici : on essaie le suivant */
    }
  }
  return ''
}

const relais = (relaisArg || relaisDepuisProprietes()).replace(/\/$/, '')
if (!relais.startsWith('https://')) {
  console.log('\n❌ Adresse du relais introuvable.')
  process.exit(2)
}

async function lire(chemin) {
  try {
    const r = await fetch(`${relais}${chemin}`, { headers: { accept: 'application/json' } })
    const texte = await r.text()
    try {
      return { statut: r.status, corps: JSON.parse(texte) }
    } catch (_) {
      return { statut: r.status, corps: null, texte }
    }
  } catch (e) {
    const cause = e?.cause?.code || e?.code || e?.message || 'inconnue'
    return { statut: 0, corps: null, texte: `reseau : ${cause}` }
  }
}

console.log(`\nRelais    : ${relais}`)
console.log(`Reference : ${reference}`)

// ─── 1. La vérification, d'abord ────────────────────────────────────

/*
ON VÉRIFIE AVANT DE TRANSMETTRE, et pas seulement parce que le relais le
refera : si la vente n'est pas vérifiée, on veut le dire ICI, en clair,
plutôt que de laisser partir un ordre qui arrivera chez le changeur avec
un « ❌ AUCUN VERSEMENT TROUVÉ ». Un message pareil l'inquiète pour rien.
*/
const v = await lire(
  `/change/verifier?monnaie=${encodeURIComponent(monnaie)}` +
    `&montant=${montant}&reference=${encodeURIComponent(reference)}`
)
if (v.statut === 0) {
  console.log(`\n❌ Relais injoignable — ${v.texte}`)
  process.exit(1)
}
if (v.corps?.etat !== 'confirme') {
  console.log(`\n❌ Cette vente n'est pas verifiee : ${v.corps?.etat ?? v.statut}`)
  console.log(`   ${v.corps?.raison ?? ''}`)
  console.log('\n   On ne transmet pas. Le changeur recevrait un ordre marque')
  console.log('   « aucun versement trouve », ce qui l’inquieterait pour rien.')
  process.exit(1)
}

console.log(`Verifie   : ${v.corps.montant} ${monnaie} — txid ${String(v.corps.txid).slice(0, 14)}…`)

// ─── 2. Le prix, comme l'application le calcule ─────────────────────

const params = await lire('/change/parametres?x=' + Date.now())
if (params.statut !== 200 || !params.corps) {
  console.log(`\n❌ Reglages illisibles (HTTP ${params.statut}).`)
  process.exit(1)
}
const marge = Number(params.corps.margeFcfaParDollar) || 0

/*
LE COURS VIENT DU RELAIS, par le même chemin que l'application. Prendre un
cours ailleurs ferait annoncer au changeur un prix que l'écran n'aurait
jamais montré — et c'est le prix qu'il paie.
*/
const cours = await lire('/api/v3/simple/price?ids=tether&vs_currencies=usd,xof')
const usd = Number(cours.corps?.tether?.usd)
const xof = Number(cours.corps?.tether?.xof)
if (!(usd > 0) || !(xof > 0)) {
  console.log('\n❌ Cours indisponible : impossible de calculer le montant en francs.')
  console.log('   Reessaie dans un instant.')
  process.exit(1)
}

const fcfaParDollar = xof / usd
const proportion = marge / fcfaParDollar
// Même plafond que l'application : au-delà, ce n'est plus une marge mais
// une faute de frappe dans un réglage, et elle ferait payer le double.
if (proportion > 0.2) {
  console.log(`\n❌ Marge aberrante (${(proportion * 100).toFixed(1)} %). On ne transmet pas.`)
  process.exit(1)
}

// Prix unitaire ARRONDI AU FRANC, puis tout calculé dessus : c'est ce que
// fait l'application, pour que le total reste verifiable de tete.
const venteFcfa = Math.round(xof * (1 - proportion))
const montantRecu = Number(v.corps.montant)
const fcfa = Math.round(montantRecu * venteFcfa)

console.log(`Cours     : ${Math.round(xof)} FCFA — taux applique ${venteFcfa} FCFA`)
console.log(`\n  LE CHANGEUR DEVRA ENVOYER ${fcfa.toLocaleString('fr-FR')} FCFA AU ${numero}`)

// ─── 3. L'ordre ─────────────────────────────────────────────────────

/*
LES MILLIERS SONT GROUPÉS, COMME L'APPLICATION LES GROUPE.

Mesuré sur le premier vrai message : « Montant : 3861 FCFA », là où un
ordre venu de l'application affiche « 3 861 ». Le changeur lit cette ligne
pour taper une somme dans Orange Money ; un nombre non groupé se relit
mal, et c'est précisément là qu'un zéro se perd.

Une espace ordinaire, et non l'espace insécable étroite de
`NumberFormat` : le relais la retire de toute façon avant de lire, et
elle traverse les URL et les messages sans se transformer en quelque
chose d'autre — ce que l'espace étroite a déjà fait dans ce projet.
*/
const ordre = {
  reference,
  sens: 'vente',
  monnaie,
  montantFcfa: String(fcfa).replace(/\B(?=(\d{3})+(?!\d))/g, ' '),
  montantCrypto: String(montantRecu),
  taux: `${venteFcfa} FCFA`,
  marge: `${marge} FCFA/$ (${(proportion * 100).toFixed(2)} %)`,
  adresse: '',
  telephone: numero,
  referencePaiement: '',
  // Le hash trouve par le relais : lui redonner lui evite de rechercher.
  txid: v.corps.txid ?? '',
}

let rep
try {
  const r = await fetch(`${relais}/change/ordre`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(ordre),
  })
  const texte = await r.text()
  rep = { statut: r.status, corps: (() => { try { return JSON.parse(texte) } catch (_) { return null } })(), texte }
} catch (e) {
  console.log(`\n❌ Envoi impossible — reseau : ${e?.cause?.code ?? e?.message}`)
  process.exit(1)
}

if (rep.corps?.ok === true) {
  console.log('\n' + '═'.repeat(66))
  console.log('  ✅ ORDRE TRANSMIS AU CHANGEUR')
  console.log('═'.repeat(66))
  console.log(`  Verification : ${rep.corps.verification ?? '(non rendue)'}`)
  console.log('\n  Regarde Telegram : le message doit dire ENVOYER, et non')
  console.log('  « ne rien envoyer pour l’instant ».')
  process.exit(0)
}

console.log(`\n❌ Refuse (HTTP ${rep.statut}) : ${rep.corps?.raison ?? rep.texte?.slice(0, 200)}`)
process.exit(1)
