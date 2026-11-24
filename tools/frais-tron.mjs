/*
═══════════════════════════════════════════════════════════════════════════
CE QUE COÛTE VRAIMENT UN ENVOI USDT TRC-20
═══════════════════════════════════════════════════════════════════════════

    node tools/frais-tron.mjs                      le coût théorique du jour
    node tools/frais-tron.mjs T...                 + les ressources du compte
    node tools/frais-tron.mjs T... --historique    + ce que les envois PASSÉS
                                                     ont réellement coûté

L'adresse est celle DEPUIS LAQUELLE tu envoies aux clients.

─── POURQUOI CET OUTIL ───────────────────────────────────────────────────

`CHANGE_FRAIS_USD_PAR_MONNAIE` porte 2 $ pour l'USDT TRC-20. Ce chiffre
n'a jamais été mesuré, et il décide de tout : il place le prix à 779 FCFA
par USDT quand la concurrence affiche 666. C'est 17 % d'écart sur le
réseau que tout le monde emploie ici.

Or ce coût N'EST PAS UNE CONSTANTE. Sur TRON, un transfert consomme de
l'ÉNERGIE. On peut la payer en brûlant des TRX, ou la posséder en gelant
des TRX — et dans le second cas le transfert est GRATUIT.

Dire « 2 $ » revient donc à figer le pire des cas.

─── LE MODÈLE VIENT DE L'APPLICATION, PAS DE MOI ─────────────────────────

Les deux quantités d'énergie sont celles de `SendCryptoUseCase` :

    ENERGIE_TRC20_HABITUELLE        65 000   le destinataire détient déjà
                                             le jeton
    ENERGIE_TRC20_NOUVEAU_DETENTEUR 130 000  il faut créer son emplacement

Le prix de l'énergie est lu sur la chaîne (`getEnergyFee`), jamais
supposé : il a valu 420, puis 280, puis 210 sun — une constante écrite
aujourd'hui serait fausse dans six mois.

─── ET L'HISTORIQUE TRANCHE ──────────────────────────────────────────────

Le calcul dit ce qu'un envoi DEVRAIT coûter. `--historique` dit ce que
les tiens ont coûté, en lisant le champ `fee` que la chaîne a enregistré.
Quand les deux divergent, c'est la chaîne qui a raison.
═══════════════════════════════════════════════════════════════════════════
*/

import { readFileSync } from 'node:fs'

const TRONGRID = 'https://api.trongrid.io'
const USDT_TRC20 = 'TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t'
const SUN = 1e6

// Reprises de `SendCryptoUseCase`. Les y changer sans les changer ici
// ferait diverger l'outil de ce que l'application provisionne.
const ENERGIE_HABITUELLE = 65_000
const ENERGIE_NOUVEAU_DETENTEUR = 130_000
const SUN_PAR_ENERGIE_DEFAUT = 420

const args = process.argv.slice(2)
const adresse = args.find((a) => /^T[1-9A-HJ-NP-Za-km-z]{33}$/.test(a)) ?? null
const avecHistorique = args.includes('--historique')

function proprieteLocale(nom) {
  for (const c of ['local.properties', '../local.properties']) {
    try {
      const l = readFileSync(c, 'utf8').split('\n').find((x) => x.trim().startsWith(`${nom}=`))
      if (l) return l.split('=').slice(1).join('=').trim()
    } catch (_) { /* suivant */ }
  }
  return ''
}

/*
LA CLÉ TRONGRID EST FACULTATIVE, et c'est voulu : sans elle l'API
répond quand même, avec une limite de débit plus basse. Un outil de
diagnostic qui exigerait une clé ne servirait pas le jour où l'on en a
besoin.
*/
const cleTron = proprieteLocale('trongrid.key')
const entetes = { 'content-type': 'application/json', accept: 'application/json' }
if (cleTron) entetes['TRON-PRO-API-KEY'] = cleTron

async function tron(chemin, corps) {
  const r = await fetch(`${TRONGRID}${chemin}`, {
    method: corps ? 'POST' : 'GET',
    headers: entetes,
    ...(corps ? { body: JSON.stringify(corps) } : {}),
  })
  const brut = await r.text()
  if (!r.ok) throw new Error(`HTTP ${r.status} sur ${chemin} — ${brut.slice(0, 140)}`)
  return JSON.parse(brut)
}

/** Le cours du TRX, demandé au relais — pas à une source de plus. */
async function prixTrx() {
  const base = (() => {
    const v = proprieteLocale('prix.relay.url')
    return v ? v.replace(/\/?api\/v3\/?$/, '').replace(/\/$/, '') : ''
  })()
  if (!base.startsWith('https://')) return null
  try {
    const r = await fetch(`${base}/api/v3/simple/price?ids=tron&vs_currencies=usd`)
    const j = await r.json()
    const p = Number(j?.tron?.usd)
    return p > 0 ? p : null
  } catch (_) {
    return null
  }
}

console.log('')
let params
try {
  params = await tron('/wallet/getchainparameters')
} catch (e) {
  console.log(`❌ TronGrid injoignable : ${e.message}`)
  process.exit(1)
}

const table = Object.fromEntries(
  (params?.chainParameter ?? []).map((x) => [x.key, Number(x.value)])
)
const sunParEnergie = table.getEnergyFee > 0 ? table.getEnergyFee : SUN_PAR_ENERGIE_DEFAUT
const mesure = table.getEnergyFee > 0

const trx = await prixTrx()
const enUsd = (t) => (trx ? ` ≈ ${(t * trx).toFixed(2)} $` : '')

console.log('─'.repeat(72))
console.log(`Prix de l'energie : ${sunParEnergie} sun${mesure ? '' : '  (repli — la chaine n a pas repondu)'}`)
console.log(`Cours du TRX      : ${trx ? trx.toFixed(4) + ' $' : 'indisponible'}`)
console.log('─'.repeat(72))

/*
L'ÉNERGIE DÉJÀ POSSÉDÉE SE DÉDUIT, et c'est tout l'enjeu : c'est elle
qui sépare un envoi à deux dollars d'un envoi gratuit.
*/
let energieDispo = 0
if (adresse) {
  try {
    const res = await tron('/wallet/getaccountresource', { address: adresse, visible: true })
    energieDispo = Math.max(0, (res?.EnergyLimit ?? 0) - (res?.EnergyUsed ?? 0))
    const bandeGratuite = Math.max(0, (res?.freeNetLimit ?? 0) - (res?.freeNetUsed ?? 0))
    console.log(`Compte ${adresse}`)
    console.log(`  energie disponible      : ${energieDispo.toLocaleString('fr-FR')}`)
    console.log(`  bande passante gratuite : ${bandeGratuite.toLocaleString('fr-FR')} octets`)
    console.log('─'.repeat(72))
  } catch (e) {
    console.log(`⚠  Ressources du compte illisibles : ${e.message}`)
    console.log('─'.repeat(72))
  }
}

console.log('\nCE QUE COUTE UN ENVOI USDT TRC-20\n')
console.log(`${'cas'.padEnd(34)} ${'energie'.padStart(9)} ${'a bruler'.padStart(9)} ${'cout'.padStart(16)}`)
for (const [nom, besoin] of [
  ['destinataire detient deja USDT', ENERGIE_HABITUELLE],
  ['destinataire nouveau', ENERGIE_NOUVEAU_DETENTEUR],
]) {
  const aBruler = Math.max(0, besoin - energieDispo)
  const coutTrx = (aBruler * sunParEnergie) / SUN
  console.log(
    `${nom.padEnd(34)} ${besoin.toLocaleString('fr-FR').padStart(9)} ` +
    `${aBruler.toLocaleString('fr-FR').padStart(9)} ` +
    `${(coutTrx.toFixed(2) + ' TRX' + enUsd(coutTrx)).padStart(16)}`
  )
}

if (!adresse) {
  console.log('\n(Sans adresse, on suppose ZERO energie possedee — le pire cas.)')
  console.log('Relance avec ton adresse TRON d envoi pour le chiffre reel.')
}

/*
LE GEL EST LE SEUL LEVIER QUI CHANGE L'ORDRE DE GRANDEUR. On le chiffre
plutot que de le mentionner : « gele des TRX » sans un nombre ne se fait
jamais.
*/
const coutPlein = (ENERGIE_HABITUELLE * sunParEnergie) / SUN
console.log('\n─── LE GEL ───\n')
console.log('Sur TRON, l energie se BRULE ou se POSSEDE. Geler des TRX en')
console.log('donne chaque jour, sans les depenser : ils restent a toi et se')
console.log('degelent en quelques jours.')
console.log('')
console.log(`Un envoi sans energie brule ${coutPlein.toFixed(2)} TRX${enUsd(coutPlein)}.`)
console.log('Avec assez d energie gelee, il coute ZERO.')
console.log('')
console.log('Le montant a geler depend du total gele sur le reseau et change')
console.log('chaque jour : regarde-le dans ton portefeuille TRON, rubrique')
console.log('« Energie » ou « Ressources ». L ordre de grandeur usuel est de')
console.log('quelques milliers de TRX pour un envoi par jour.')

if (avecHistorique && adresse) {
  console.log('\n─── CE QUE TES ENVOIS ONT REELLEMENT COUTE ───\n')
  try {
    const liste = await tron(
      `/v1/accounts/${adresse}/transactions/trc20` +
      `?limit=10&only_from=true&contract_address=${USDT_TRC20}`
    )
    const envois = liste?.data ?? []
    if (envois.length === 0) {
      console.log('Aucun envoi USDT TRC-20 depuis cette adresse.')
      console.log('Le calcul ci-dessus reste la seule estimation possible.')
    } else {
      let total = 0
      let n = 0
      /*
      ═══════════════════════════════════════════════════════════════
      TRON OMET LES CHAMPS NULS — L'ABSENCE N'EST PAS UN TROU
      ═══════════════════════════════════════════════════════════════

      Premiere version de ce garde-fou : `fee` absent ⇒ « ne mesure
      rien, ne regle aucun prix dessus ». Trop prudent, et faux.

      La sortie JSON de TronGrid vient de protobuf, qui N'EMET PAS
      les champs valant zero. Un envoi reellement gratuit rend donc
      un recu SANS `fee` et SANS `energy_fee` — exactement ce qu'on
      a lu sur dix envois reels.

      CE QUI TRANCHE EST LA COHERENCE INTERNE DU RECU :

          energie possedee + deleguee == energie totale
          et aucun frais d'energie

      alors toute l'energie venait d'ailleurs que d'une combustion,
      et le cout est VRAIMENT nul. Si la somme ne boucle pas, une
      part a ete brulee et l'absence de `fee` devient suspecte.

      On ne refuse donc de conclure que dans ce second cas.
      ═══════════════════════════════════════════════════════════════
      */
      let incoherents = 0
      for (const e of envois) {
        const info = await tron('/wallet/gettransactioninfobyid', { value: e.transaction_id })
        const r = info?.receipt ?? {}
        const feeBrut = info?.fee
        const totalE = Number(r.energy_usage_total ?? 0)
        const couverte = Number(r.energy_usage ?? 0) + Number(r.origin_energy_usage ?? 0)
        const brulee = Number(r.energy_fee ?? 0)
        // Le recu boucle : toute l'energie est expliquee sans combustion.
        const boucle = totalE > 0 && couverte === totalE && brulee === 0
        if (feeBrut === undefined && !boucle) incoherents += 1
        const fraisTrx = Number(feeBrut ?? 0) / SUN
        const detail = [
          `energie ${Number(r.energy_usage_total ?? 0).toLocaleString('fr-FR')}`,
          `brulee ${(Number(r.energy_fee ?? 0) / SUN).toFixed(2)} TRX`,
          `possedee ${Number(r.energy_usage ?? 0).toLocaleString('fr-FR')}`,
          `deleguee ${Number(r.origin_energy_usage ?? 0).toLocaleString('fr-FR')}`,
          `bande ${(Number(r.net_fee ?? 0) / SUN).toFixed(2)} TRX`,
        ].join(' · ')
        const date = new Date(Number(e.block_timestamp)).toISOString().slice(0, 10)
        console.log(`  ${date}  ${fraisTrx.toFixed(2).padStart(7)} TRX${enUsd(fraisTrx)}`)
        const note =
          boucle ? '   ✓ energie entierement couverte, rien brule'
          : feeBrut === undefined ? '   ⚠ champ « fee » absent ET recu incoherent'
          : ''
        console.log(`              ${detail}${note}`)
        total += fraisTrx
        n += 1
      }
      if (incoherents > 0) {
        console.log('')
        console.log(`⚠  ${incoherents} recu(s) sans champ « fee » ET dont l energie ne boucle pas.`)
        console.log('   Le zero affiche pour ceux-la ne mesure rien.')
        process.exitCode = 1
      }
      const moyenne = total / n
      console.log('')
      console.log(`Moyenne sur ${n} envoi(s) : ${moyenne.toFixed(2)} TRX${enUsd(moyenne)}`)
      if (trx && incoherents === 0) {
        console.log('')
        console.log(`➜  CHANGE_FRAIS_USD_PAR_MONNAIE devrait porter environ`)
        console.log(`   ${(moyenne * trx).toFixed(2)} $ pour « USDT », et non un chiffre pose d avance.`)
        if (moyenne === 0) {
          /*
          UN COUT NUL AVEC DE L'ENERGIE CONSOMMEE VEUT DIRE QU'ELLE
          VENAIT D'AILLEURS — gelee, ou DELEGUEE par un tiers le temps
          de la transaction. Le detail au-dessus dit laquelle.

          Ca compte, parce qu'une delegation s'arrete : si elle vient
          d'un service de location, le jour ou on ne paie plus, le
          cout remonte a 2 $ sans prevenir.
          */
          console.log('')
          console.log('   CE ZERO EST REEL, mais il a une CAUSE, et la cause peut')
          console.log('   cesser. De l energie a ete consommee sans rien bruler :')
          console.log('   elle etait donc gelee, ou deleguee au moment de l envoi.')
          console.log('')
          console.log(`   Or le compte affiche ${energieDispo.toLocaleString('fr-FR')} d energie disponible MAINTENANT.`)
          if (energieDispo === 0) {
            console.log('   Zero. Elle n est donc pas gelee a demeure : quelque chose')
            console.log('   la fournit au moment de chaque envoi — un service de')
            console.log('   location, ou le portefeuille lui-meme.')
            console.log('')
            console.log('   CE QUE CE SERVICE TE COUTE EST TON VRAI FRAIS. Il ne se lit')
            console.log('   pas sur la chaine, seulement sur ta facture. Zero sur la')
            console.log(`   chaine ne veut pas dire zero pour toi — et le jour ou ca`)
            console.log(`   s arrete, l envoi coute ${((ENERGIE_HABITUELLE * sunParEnergie) / SUN * (trx || 0)).toFixed(2)} $ sans prevenir.`)
          }
        }
      }
    }
  } catch (e) {
    console.log(`⚠  Historique illisible : ${e.message}`)
  }
}
console.log('')
