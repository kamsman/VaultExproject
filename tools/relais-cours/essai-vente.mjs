/*
═══════════════════════════════════════════════════════════════════════════
L'ESSAI D'UNE VENTE, DE BOUT EN BOUT, EN UNE COMMANDE
═══════════════════════════════════════════════════════════════════════════

    node tools/relais-cours/essai-vente.mjs USDT-BNB 2

Il fait exactement ce que fait l'application : il demande la consigne,
affiche le montant exact à envoyer, puis interroge le relais toutes les
quinze secondes jusqu'à ce que le versement soit reconnu.

─── POURQUOI CET OUTIL EXISTE ───────────────────────────────────────────

Parce que le même essai, fait à la main, demande d'ouvrir quatre adresses
dans un navigateur, de recopier une référence d'une page à l'autre, et de
recharger en se demandant si c'est le cache qui répond. On y a perdu des
heures, et chaque perte venait d'une étape manuelle, pas du code.

Ici il n'y a rien à recopier : la référence est tirée au sort comme
l'application le fait, et elle circule toute seule jusqu'au bout.

─── CE QU'IL NE TESTE PAS ───────────────────────────────────────────────

L'APPLICATION. Ni la carte qui passe au vert, ni le message Telegram, ni
l'historique local. Il valide le RELAIS, qui est la partie qu'on ne voit
pas et celle qui porte la preuve.

Le vrai essai reste donc celui fait depuis le téléphone. Celui-ci sert
quand il échoue, pour voir où — il imprime tout ce qui passe.
═══════════════════════════════════════════════════════════════════════════
*/

import { readFileSync } from 'node:fs'

const [monnaieBrute, montantBrut, relaisArg] = process.argv.slice(2)

if (!monnaieBrute || !montantBrut) {
  console.log('\nUsage :')
  console.log('  node tools/relais-cours/essai-vente.mjs <MONNAIE> <montant> [adresse-du-relais]\n')
  console.log('Exemple :')
  console.log('  node tools/relais-cours/essai-vente.mjs USDT-BNB 2\n')
  console.log("Sans le troisieme argument, l'adresse est lue dans local.properties.")
  process.exit(2)
}

const monnaie = monnaieBrute.trim().toUpperCase()
const montant = Number(String(montantBrut).replace(',', '.'))

/**
 * L'adresse du relais, depuis local.properties.
 *
 * ON NE LIT QU'UNE LIGNE, et c'est volontaire : ce fichier contient aussi
 * des cles d'API et le mot de passe du magasin de signature. Rien d'autre
 * que `prix.relay.url` n'est extrait, et rien d'autre n'est imprime.
 */
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
      /* fichier absent a cet endroit : on essaie le suivant */
    }
  }
  return ''
}

const relais = (relaisArg || relaisDepuisProprietes()).replace(/\/$/, '')
if (!relais.startsWith('https://')) {
  console.log("\n❌ Adresse du relais introuvable.")
  console.log('   Passe-la en troisieme argument, ou lance la commande depuis')
  console.log('   la racine du depot (local.properties y est lu).')
  process.exit(2)
}

/*
LA REFERENCE EST TIREE COMME L'APPLICATION LA TIRE.

Meme alphabet, meme longueur — sans O ni 0, sans I ni 1, qui se confondent
a l'oral et a l'ecrit. Un essai qui emploierait une autre forme de
reference ne testerait pas tout a fait le meme chemin.
*/
const ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
const reference =
  'VX-' + Array.from({ length: 8 }, () => ALPHABET[Math.floor(Math.random() * 32)]).join('')

const attendre = (ms) => new Promise((r) => setTimeout(r, ms))

/*
UN OUTIL DE DIAGNOSTIC NE DOIT PAS TOMBER SUR UNE TRACE DE PILE.

`fetch` leve quand le reseau manque, et Node imprime alors quarante lignes
d'internals d'undici. Au moment ou l'on vient justement chercher pourquoi
quelque chose ne marche pas, c'est le pire des messages : il ensevelit la
cause sous du bruit qui ne concerne personne.

On rend donc l'echec comme une reponse ordinaire, avec son motif en clair.
*/
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
console.log(`Monnaie   : ${monnaie}`)
console.log(`Reference : ${reference}  (tiree au sort, comme l'application le fait)`)

/*
═══ 0. QUELLE VERSION DU RELAIS NOUS REPOND ? ════════════════════════════

Cette etape n'existait pas, et son absence nous a coute quatre allers-
retours : on corrigeait le code, on relancait l'essai, on lisait la meme
erreur — parce que le tableau de bord Cloudflare avait « enregistre une
version » sans la DEPLOYER. On debuggait du code que personne n'executait.

Un essai doit donc commencer par dire contre quoi il s'execute. Sans ca,
chaque echec est ambigu : le code est-il faux, ou pas en ligne ?

On n'ARRETE pas sur un decalage — c'est peut-etre volontaire d'essayer
contre l'ancienne version. On le dit, fort, et on continue.
*/
const ATTENDUE = 25

const sante = await lire('/sante?x=' + Date.now())
const enLigne = sante.corps?.version
if (typeof enLigne === 'number') {
  console.log(`Version   : ${enLigne}${enLigne === ATTENDUE ? '' : `  (ce depot en attend ${ATTENDUE})`}`)
  if (enLigne < ATTENDUE) {
    console.log(`\n⚠ LE RELAIS EN LIGNE EST PLUS ANCIEN QUE CE DEPOT.`)
    console.log(`  Les corrections faites depuis la version ${enLigne} ne sont pas`)
    console.log(`  actives. Recolle worker.js dans Cloudflare → Edit code, puis`)
    console.log(`  clique DEPLOY (« Save version » enregistre sans publier).`)
    console.log(`  L'essai continue quand meme — mais lis cet avertissement`)
    console.log(`  avant de conclure quoi que ce soit d'un echec.\n`)
  }
} else {
  console.log(`Version   : illisible (HTTP ${sante.statut}) — /sante ne repond pas comme attendu`)
}

// ─── 1. Les réglages, pour connaître l'adresse de destination ───────

const reglages = await lire('/change/parametres?x=' + Date.now())
if (reglages.statut !== 200 || !reglages.corps) {
  if (reglages.statut === 0) {
    console.log(`\n\u274C Le relais est injoignable — ${reglages.texte}`)
    console.log("   Verifie l'adresse ci-dessus et ta connexion.")
  } else {
    console.log(`\n\u274C Le relais a repondu ${reglages.statut} aux reglages.`)
    if (reglages.texte) console.log(`   ${reglages.texte.slice(0, 200)}`)
  }
  process.exit(1)
}
const adresse = reglages.corps.adresses?.[monnaie]
if (!adresse) {
  console.log(`\n❌ Aucune adresse reglee pour ${monnaie}.`)
  console.log(`   Monnaies connues du relais : ${Object.keys(reglages.corps.adresses || {}).join(', ') || '(aucune)'}`)
  process.exit(1)
}

// ─── 2. La consigne : quel montant exact envoyer ────────────────────

const consigne = await lire(
  `/change/consigne?monnaie=${encodeURIComponent(monnaie)}` +
    `&montant=${montant}&reference=${encodeURIComponent(reference)}`
)
if (consigne.statut !== 200 || consigne.corps?.ok !== true) {
  console.log(`\n❌ La consigne a echoue (HTTP ${consigne.statut}).`)
  console.log(`   ${JSON.stringify(consigne.corps ?? consigne.texte)}`)
  process.exit(1)
}

const exact = consigne.corps.montantExact
const marque = consigne.corps.marque === true

console.log('\n' + '═'.repeat(66))
console.log(`  ENVOIE EXACTEMENT   ${exact} ${monnaie}`)
console.log(`  A L'ADRESSE         ${adresse}`)
console.log('═'.repeat(66))
if (marque) {
  console.log('\n  Le montant est MARQUE : ces decimales sont derivees de la')
  console.log('  reference ci-dessus, et elles seules permettront de reconnaitre')
  console.log('  ton versement. Un montant arrondi ne sera pas reconnu.')
} else {
  /*
  SANS MARQUAGE, ON LE DIT. C'est la difference entre « ton versement sera
  reconnu avec certitude » et « un versement du meme montant sera reconnu,
  d'ou qu'il vienne ». La seconde laisse la porte ouverte a quelqu'un qui
  reclamerait le versement d'un autre.
  */
  console.log('\n  ⚠ CHANGE_MARQUAGE n’est pas regle : le relais acceptera')
  console.log('    tout versement de ce montant a 1 % pres, d’ou qu’il vienne.')
}

// ─── 3. On attend le versement ──────────────────────────────────────

const INTERVALLE = 15_000
const ESSAIS = 60 // quinze minutes

console.log(`\nJ'interroge le relais toutes les 15 s (${ESSAIS} fois au plus).`)
console.log('Ctrl+C pour arreter.\n')

for (let tour = 1; tour <= ESSAIS; tour += 1) {
  const v = await lire(
    `/change/verifier?monnaie=${encodeURIComponent(monnaie)}` +
      `&montant=${montant}&reference=${encodeURIComponent(reference)}`
  )
  const heure = new Date().toLocaleTimeString('fr-FR')
  /*
  UNE COUPURE PENDANT LE SONDAGE N'ARRETE PAS L'ESSAI.

  On attend un versement qui met des minutes a arriver ; perdre le reseau
  dix secondes au milieu est banal. Abandonner pour ca ferait recommencer
  tout l'essai — avec une nouvelle reference, donc un nouveau montant
  marque, donc un versement deja parti qui ne correspondrait plus a rien.
  */
  if (v.statut === 0) {
    console.log(`  ${heure}  ${v.texte} — on reessaie`)
    if (tour < ESSAIS) await attendre(INTERVALLE)
    continue
  }
  const etat = v.corps?.etat ?? `HTTP ${v.statut}`

  if (etat === 'confirme') {
    console.log(`\n${'═'.repeat(66)}`)
    console.log('  ✅ VERSEMENT RECONNU')
    console.log('═'.repeat(66))
    console.log(`  Montant lu sur la chaine : ${v.corps.montant} ${monnaie}`)
    console.log(`  Expediteur               : ${v.corps.de || '(non rendu)'}`)
    console.log(`  Txid                     : ${v.corps.txid}`)
    if (v.corps.explorateur) console.log(`  Voir                     : ${v.corps.explorateur}`)
    console.log('\n  La chaine de preuve fonctionne de bout en bout.')
    console.log('  Il reste a la verifier depuis le telephone : la carte qui')
    console.log('  passe au vert, et le message qui arrive chez le changeur.')
    process.exit(0)
  }

  /*
  UN ETAT SANS ESPOIR ARRETE TOUT DE SUITE.

  `indisponible` veut dire que le relais n'a PAS PU regarder — un noeud
  muet, une adresse non reglee. Insister soixante fois ne ferait
  qu'afficher la meme ligne pendant un quart d'heure, et la vraie
  information est dans la raison.
  */
  if (etat === 'indisponible' || etat === 'inconnue' || etat === 'deja_servi') {
    console.log(`\n❌ ${etat} : ${v.corps?.raison ?? ''}`)
    if (etat === 'deja_servi') {
      console.log('\n  Ce versement a deja paye une autre demande. Si c’est un')
      console.log('  second essai avec le meme montant, c’est le comportement voulu.')
    }
    process.exit(1)
  }

  const reste = ESSAIS - tour
  console.log(`  ${heure}  ${etat}${reste > 0 ? `  (${reste} essais restants)` : ''}`)
  if (tour < ESSAIS) await attendre(INTERVALLE)
}

console.log('\n⏱  Quinze minutes sans rien trouver.')
console.log('   Verifie sur un explorateur que le versement est bien arrive,')
console.log('   et que le montant correspond au centieme pres.')
process.exit(1)
