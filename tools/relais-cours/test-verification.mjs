/*
═══════════════════════════════════════════════════════════════════════════
LE TRI DES TRANSFERTS, MIS À L'ÉPREUVE
═══════════════════════════════════════════════════════════════════════════

    node tools/relais-cours/test-verification.mjs

Ce fichier n'appelle aucune chaîne. Il donne aux fonctions de tri des
réponses forgées — celles d'un nœud normal, et celles d'un attaquant — et
vérifie qu'elles répondent ce qu'il faut.

POURQUOI CES FONCTIONS ET PAS LE RESTE. Parce qu'une faute ici ne lève
aucune erreur. Un `fetch` qui échoue se voit ; une comparaison d'adresses
faite sans la casse sur une chaîne où elle compte, des décimales devinées,
un contrat qu'on oublie de vérifier — tout ça rend tranquillement
« vérifié » un versement qui n'existe pas. Et ce mot-là, le changeur
l'utilise pour décider d'envoyer de l'argent.

CHAQUE CAS D'ATTAQUE EST UN CAS QUE J'AI DÛ IMAGINER AVANT DE L'ÉCRIRE.
C'est la limite de ce fichier, et il faut la dire : il prouve que les
attaques listées échouent, pas qu'il n'en existe pas d'autres.
═══════════════════════════════════════════════════════════════════════════
*/

import {
  choisirTransfertTron,
  choisirJournalErc20,
  choisirNatifEvm,
  choisirSortieBtc,
  montantProche,
  depuisUnites,
  entierDepuisHex,
  nombreFrancais,
  topicDepuisAdresse,
  lignesVerification,
  messageOrdre,
  groupeMilliers,
  dateCourteUtc,
  formatMontant,
  CHAINES_VENTE,
  TOPIC_TRANSFER,
} from './worker.js'

let passes = 0
let echecs = 0

function verifie(nom, condition, detail) {
  if (condition) {
    passes += 1
  } else {
    echecs += 1
    console.log(`❌ ${nom}`)
    if (detail !== undefined) console.log(`   obtenu : ${JSON.stringify(detail)}`)
  }
}

// ─── Le décor ───────────────────────────────────────────────────────

const CHANGEUR_TRON = 'TQn9Y2khDD95J42FQtQTdwVVRZqjGBCvpM'
const CLIENT_TRON = 'TMuA6YqfCeX8EhbfYEg5y7S4DqzSJireY9'
const USDT_TRON = CHAINES_VENTE.USDT.contrat
const MAINTENANT = 1_760_000_000_000

/** Un transfert TRC-20 tel que TronGrid le rend, avec de quoi le déformer. */
function transfertTron(modifications) {
  return {
    transaction_id: 'a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90',
    token_info: { address: USDT_TRON, decimals: 6, symbol: 'USDT' },
    from: CLIENT_TRON,
    to: CHANGEUR_TRON,
    value: '8000000', // 8 USDT
    block_timestamp: MAINTENANT - 5 * 60 * 1000,
    ...(modifications || {}),
  }
}

const critereTron = (extra) => ({
  adresse: CHANGEUR_TRON,
  contrat: USDT_TRON,
  decimales: 6,
  montant: 8,
  txid: '',
  maintenant: MAINTENANT,
  ...(extra || {}),
})

// ─── Ce qui doit marcher ────────────────────────────────────────────

{
  const r = choisirTransfertTron([transfertTron()], critereTron())
  verifie('TRON : un versement conforme est trouve', r !== null, r)
  verifie('TRON : le montant vient de la chaine', r && r.montant === 8, r)
  verifie('TRON : l expediteur est rendu', r && r.de === CLIENT_TRON, r)
}

{
  // Le bon versement n'est pas le premier de la liste : TronGrid rend
  // l'activité complète du changeur, et ses autres clients y sont aussi.
  const liste = [
    transfertTron({ value: '25000000', transaction_id: 'ff'.repeat(32) }),
    transfertTron({ value: '1000000', transaction_id: 'ee'.repeat(32) }),
    transfertTron(),
  ]
  const r = choisirTransfertTron(liste, critereTron())
  verifie('TRON : trouve le bon parmi plusieurs', r && r.montant === 8, r)
}

{
  // 8,33 envoyés pour 8,3333… affichés : l'arrondi d'affichage, pas une fraude.
  const r = choisirTransfertTron(
    [transfertTron({ value: '8330000' })],
    critereTron({ montant: 8.3333 })
  )
  verifie('TRON : un arrondi d affichage passe', r !== null, r)
}

// ─── Les attaques, et elles doivent toutes échouer ──────────────────

{
  /*
  LE FAUX JETON. N'importe qui déploie en dix minutes un contrat nommé
  « USDT » avec six décimales. Sans vérifier l'adresse du contrat, cinq
  mille jetons sans valeur se lisent « 5 000 USDT reçus » — et la
  vérification sert alors à voler le changeur en le rassurant.
  */
  const r = choisirTransfertTron(
    [transfertTron({ token_info: { address: 'TFauxJetonXXXXXXXXXXXXXXXXXXXXXXXX', decimals: 6, symbol: 'USDT' } })],
    critereTron()
  )
  verifie('ATTAQUE : un faux contrat nomme USDT est refuse', r === null, r)
}

{
  // Le symbole est bon, le contrat absent : on refuse au lieu de se
  // rabattre sur le symbole, qui ne prouve rien.
  const r = choisirTransfertTron(
    [transfertTron({ token_info: { decimals: 6, symbol: 'USDT' } })],
    critereTron()
  )
  verifie('ATTAQUE : un token_info sans contrat est refuse', r === null, r)
}

{
  // Des décimales qui ne sont pas celles de l'USDT : 8 unités à 18
  // décimales ne sont pas 8 USDT, elles sont un milliardième de rien.
  const r = choisirTransfertTron(
    [transfertTron({ token_info: { address: USDT_TRON, decimals: 18, symbol: 'USDT' } })],
    critereTron()
  )
  verifie('ATTAQUE : des decimales inattendues sont refusees', r === null, r)
}

{
  // Versement vers une AUTRE adresse. Le cas du client qui s'est trompé,
  // et celui de l'attaquant qui s'envoie des fonds à lui-même.
  const r = choisirTransfertTron(
    [transfertTron({ to: CLIENT_TRON })],
    critereTron()
  )
  verifie('ATTAQUE : un versement vers une autre adresse est refuse', r === null, r)
}

{
  /*
  LA CASSE. Sur Tron, « TQn9Y2… » et « tqn9y2… » ne sont pas la même
  adresse : base58 distingue les casses, et une comparaison insensible
  accepterait une adresse qui ne diffère que par là.
  */
  const r = choisirTransfertTron(
    [transfertTron({ to: CHANGEUR_TRON.toLowerCase() })],
    critereTron()
  )
  verifie('ATTAQUE : la casse de l adresse compte', r === null, r)
}

{
  // Un vrai versement, mais d'hier : il ne peut pas justifier une demande
  // déposée maintenant, sinon il en justifierait dix.
  const r = choisirTransfertTron(
    [transfertTron({ block_timestamp: MAINTENANT - 26 * 60 * 60 * 1000 })],
    critereTron()
  )
  verifie('ATTAQUE : un versement hors fenetre est refuse', r === null, r)
}

{
  // Une horodate dans le futur : un nœud ne rend pas ça, un faux oui.
  const r = choisirTransfertTron(
    [transfertTron({ block_timestamp: MAINTENANT + 60 * 60 * 1000 })],
    critereTron()
  )
  verifie('ATTAQUE : une horodate future est refusee', r === null, r)
}

{
  // Dix pour cent de moins qu'annoncé : au-delà de l'arrondi, c'est un
  // montant différent, et le changeur paierait la différence.
  const r = choisirTransfertTron(
    [transfertTron({ value: '7200000' })],
    critereTron()
  )
  verifie('ATTAQUE : un montant trop faible est refuse', r === null, r)
}

{
  // Un txid demandé qui ne correspond à rien dans la liste.
  const r = choisirTransfertTron([transfertTron()], critereTron({ txid: 'bb'.repeat(32) }))
  verifie('TRON : un txid qui ne correspond pas est refuse', r === null, r)
}

{
  // Le même txid, en majuscules : les hash hexadécimaux se recopient dans
  // les deux casses, et là elle ne doit PAS compter.
  const t = transfertTron()
  const r = choisirTransfertTron([t], critereTron({ txid: t.transaction_id.toUpperCase() }))
  verifie('TRON : la casse d un txid ne compte pas', r !== null, r)
}

{
  const r = choisirTransfertTron(null, critereTron())
  verifie('TRON : une reponse vide ne plante pas', r === null, r)
  const r2 = choisirTransfertTron([null, undefined, {}, { to: CHANGEUR_TRON }], critereTron())
  verifie('TRON : des entrees illisibles ne plantent pas', r2 === null, r2)
}

// ─── EVM : les journaux ─────────────────────────────────────────────

const CHANGEUR_EVM = '0x1111111111111111111111111111111111111111'
const CLIENT_EVM = '0x2222222222222222222222222222222222222222'
const USDT_BNB = CHAINES_VENTE['USDT-BNB'].contrat

function journalErc20(modifications) {
  return {
    address: USDT_BNB,
    topics: [TOPIC_TRANSFER, topicDepuisAdresse(CLIENT_EVM), topicDepuisAdresse(CHANGEUR_EVM)],
    // 8 USDT à DIX-HUIT décimales : 8 × 10^18.
    data: '0x' + (8n * 10n ** 18n).toString(16),
    transactionHash: '0x' + 'ab'.repeat(32),
    ...(modifications || {}),
  }
}

const critereEvm = (extra) => ({
  adresse: CHANGEUR_EVM,
  contrat: USDT_BNB,
  decimales: 18,
  montant: 8,
  ...(extra || {}),
})

{
  const r = choisirJournalErc20({ status: '0x1', logs: [journalErc20()] }, critereEvm())
  verifie('EVM : un journal conforme est trouve', r !== null, r)
  verifie('EVM : 18 decimales sont lues juste', r && r.montant === 8, r)
}

{
  /*
  LA TRANSACTION ÉCHOUÉE. Un appel à `transfer` qui échoue existe, a coûté
  des frais, et n'a rien transféré. Si on lisait les données d'appel au
  lieu du journal, elle passerait pour un versement.
  */
  const r = choisirJournalErc20({ status: '0x0', logs: [journalErc20()] }, critereEvm())
  verifie('ATTAQUE : une transaction echouee est refusee', r === null, r)
}

{
  // Un faux contrat qui émet un journal Transfer parfaitement formé.
  const r = choisirJournalErc20(
    { status: '0x1', logs: [journalErc20({ address: '0x9999999999999999999999999999999999999999' })] },
    critereEvm()
  )
  verifie('ATTAQUE : un journal d un autre contrat est refuse', r === null, r)
}

{
  // Versement vers quelqu'un d'autre, dans la même transaction. Un
  // routeur de swap en émet plusieurs : seul celui qui vise le changeur
  // compte.
  const r = choisirJournalErc20(
    { status: '0x1', logs: [journalErc20({ topics: [TOPIC_TRANSFER, topicDepuisAdresse(CLIENT_EVM), topicDepuisAdresse(CLIENT_EVM)] })] },
    critereEvm()
  )
  verifie('ATTAQUE : un journal vers un autre destinataire est refuse', r === null, r)
}

{
  // Le bon journal noyé parmi d'autres, comme dans une transaction de swap.
  const autres = [
    journalErc20({ address: '0x9999999999999999999999999999999999999999' }),
    journalErc20({ topics: [TOPIC_TRANSFER, topicDepuisAdresse(CLIENT_EVM), topicDepuisAdresse(CLIENT_EVM)] }),
    journalErc20(),
  ]
  const r = choisirJournalErc20({ status: '0x1', logs: autres }, critereEvm())
  verifie('EVM : trouve le bon journal parmi plusieurs', r && r.montant === 8, r)
}

{
  // Un autre topic (Approval) sur le bon contrat et le bon destinataire :
  // une autorisation n'est pas un versement.
  const r = choisirJournalErc20(
    { status: '0x1', logs: [journalErc20({ topics: ['0x' + '11'.repeat(32), topicDepuisAdresse(CLIENT_EVM), topicDepuisAdresse(CHANGEUR_EVM)] })] },
    critereEvm()
  )
  verifie('ATTAQUE : un topic qui n est pas Transfer est refuse', r === null, r)
}

{
  const r = choisirJournalErc20({ status: '0x1', logs: [journalErc20({ data: '0x' })] }, critereEvm())
  verifie('EVM : une donnee vide ne plante pas', r === null, r)
}

{
  /*
  LE CAS QUI JUSTIFIE BigInt. 8 USDT-BNB font 8 × 10^18, un entier de
  dix-neuf chiffres. `Number` en garde quinze : lu en flottant, ce montant
  perd ses dernières décimales. Ici on vérifie qu'un wei de moins se voit.
  */
  const presque = (8n * 10n ** 18n - 1n).toString(16)
  const r = choisirJournalErc20(
    { status: '0x1', logs: [journalErc20({ data: '0x' + presque })] },
    critereEvm()
  )
  verifie('EVM : un wei de moins reste accepte (tolerance)', r !== null, r)
}

{
  /*
  CE QUE BigInt APPORTE VRAIMENT, mesuré plutôt que supposé.

  Ma première version de ce test affirmait que 10^18 + 1 se lit comme plus
  grand que 1. C'est FAUX, et ça ne prouvait rien : un milliardième de
  milliardième de jeton ne tient pas dans un flottant, et n'a aucune
  importance.

  Ce qui compte est ailleurs — la PARTIE ENTIÈRE. Divisé en flottant,
  999 999 999 999,000001 USDT se lit « 999999999999.0001 » : les
  décimales sont inventées. Divisé en entier puis converti, il reste
  exact. C'est cette différence-là qui fait qu'un montant lu sur la chaîne
  est le montant de la chaîne.
  */
  const brut = (999999999999n * 10n ** 6n + 1n).toString()
  verifie(
    'PRECISION : la partie entiere reste exacte sur un gros montant',
    depuisUnites(brut, 6) === 999999999999,
    depuisUnites(brut, 6)
  )
  verifie(
    'PRECISION : la division flottante naive, elle, se trompe',
    Number(brut) / 1e6 !== 999999999999,
    Number(brut) / 1e6
  )
}

// ─── EVM natif ──────────────────────────────────────────────────────

{
  const tx = { to: CHANGEUR_EVM, from: CLIENT_EVM, value: '0x' + (2n * 10n ** 18n).toString(16) }
  const r = choisirNatifEvm(tx, { status: '0x1' }, { adresse: CHANGEUR_EVM, montant: 2, decimales: 18 })
  verifie('EVM natif : un versement conforme est trouve', r && r.montant === 2, r)

  const r2 = choisirNatifEvm(tx, { status: '0x0' }, { adresse: CHANGEUR_EVM, montant: 2, decimales: 18 })
  verifie('ATTAQUE : un natif sur transaction echouee est refuse', r2 === null, r2)

  const r3 = choisirNatifEvm(
    { ...tx, to: CLIENT_EVM },
    { status: '0x1' },
    { adresse: CHANGEUR_EVM, montant: 2, decimales: 18 }
  )
  verifie('ATTAQUE : un natif vers une autre adresse est refuse', r3 === null, r3)

  // La casse NE compte PAS sur EVM : les adresses y sont hexadécimales, et
  // le mélange de casses n'est qu'une somme de contrôle.
  const r4 = choisirNatifEvm(
    { ...tx, to: CHANGEUR_EVM.toUpperCase().replace('0X', '0x') },
    { status: '0x1' },
    { adresse: CHANGEUR_EVM, montant: 2, decimales: 18 }
  )
  verifie('EVM natif : la casse de l adresse ne compte pas', r4 !== null, r4)
}

// ─── Bitcoin ────────────────────────────────────────────────────────

const CHANGEUR_BTC = 'bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq'

{
  const tx = {
    txid: 'cc'.repeat(32),
    status: { confirmed: true, block_time: Math.floor((MAINTENANT - 600_000) / 1000) },
    vout: [
      { scriptpubkey_address: 'bc1qautre', value: '500000' },
      { scriptpubkey_address: CHANGEUR_BTC, value: '10000000' },
    ],
  }
  const r = choisirSortieBtc(tx, { adresse: CHANGEUR_BTC, montant: 0.1, decimales: 8 })
  verifie('BTC : une sortie conforme est trouvee', r && r.montant === 0.1, r)
  verifie('BTC : la confirmation est rendue', r && r.confirme === true, r)
}

{
  /*
  LE PAIEMENT DÉCOUPÉ. Un portefeuille peut payer le même destinataire en
  deux sorties. N'en lire qu'une annoncerait la moitié du montant, et le
  changeur paierait la moitié de ce qu'il devait.
  */
  const tx = {
    txid: 'dd'.repeat(32),
    status: { confirmed: true, block_time: Math.floor(MAINTENANT / 1000) },
    vout: [
      { scriptpubkey_address: CHANGEUR_BTC, value: '6000000' },
      { scriptpubkey_address: CHANGEUR_BTC, value: '4000000' },
    ],
  }
  const r = choisirSortieBtc(tx, { adresse: CHANGEUR_BTC, montant: 0.1, decimales: 8 })
  verifie('BTC : deux sorties vers la meme adresse s additionnent', r && r.montant === 0.1, r)
}

{
  const tx = {
    status: { confirmed: false },
    vout: [{ scriptpubkey_address: CHANGEUR_BTC, value: '10000000' }],
  }
  const r = choisirSortieBtc(tx, { adresse: CHANGEUR_BTC, montant: 0.1, decimales: 8 })
  verifie('BTC : une transaction non confirmee est rendue, marquee', r && r.confirme === false, r)
}

{
  const tx = { vout: [{ scriptpubkey_address: 'bc1qautre', value: '10000000' }] }
  const r = choisirSortieBtc(tx, { adresse: CHANGEUR_BTC, montant: 0.1, decimales: 8 })
  verifie('ATTAQUE : aucune sortie vers le changeur est refusee', r === null, r)
}

// ─── Les briques ────────────────────────────────────────────────────

verifie('montantProche : identique', montantProche(8, 8))
verifie('montantProche : un demi pour cent', montantProche(7.96, 8))
verifie('montantProche : dix pour cent, non', !montantProche(7.2, 8))
verifie('montantProche : zero, non', !montantProche(0, 8))
verifie('montantProche : attendu nul, non', !montantProche(8, 0))

verifie('depuisUnites : 6 decimales', depuisUnites('8000000', 6) === 8)
verifie('depuisUnites : 18 decimales', depuisUnites((8n * 10n ** 18n).toString(), 18) === 8)
verifie('depuisUnites : illisible rend zero', depuisUnites('abc', 6) === 0)
verifie('depuisUnites : negatif rend zero', depuisUnites('-5', 6) === 0)

verifie('entierDepuisHex : refuse 0x', entierDepuisHex('0x') === '0')
verifie('entierDepuisHex : refuse le decimal nu', entierDepuisHex('123') === '0')
verifie('entierDepuisHex : lit un hex', entierDepuisHex('0xff') === '255')

/*
LES TROIS ESPACES DU FRANÇAIS. `NumberFormat` sépare les milliers par
U+202F sur les ICU récents, U+00A0 sur les anciens, une espace ordinaire
ailleurs — selon la version d'Android du téléphone. `parseFloat('5 000')`
rend CINQ, et une vente de 5 000 francs se chercherait comme une vente de
5 : aucun versement ne correspondrait, et la vérification dirait
« introuvable » sur une opération honnête.
*/
verifie('nombreFrancais : espace ordinaire', nombreFrancais('5 000') === 5000)
verifie('nombreFrancais : espace insecable', nombreFrancais('5 000') === 5000)
verifie('nombreFrancais : espace fine insecable', nombreFrancais('5 000') === 5000)
verifie('nombreFrancais : virgule decimale', nombreFrancais('8,33') === 8.33)
verifie('nombreFrancais : texte rend zero', nombreFrancais('abc') === 0)
verifie('nombreFrancais : vide rend zero', nombreFrancais('') === 0)
verifie('nombreFrancais : negatif rend zero', nombreFrancais('-3') === 0)

verifie('formatMontant : retire les zeros de fin', formatMontant(8) === '8')
verifie('formatMontant : garde les decimales utiles', formatMontant(0.00012345) === '0.00012345')

verifie('dateCourteUtc : jour et heure', dateCourteUtc(1_760_000_000_000).length === 11, dateCourteUtc(1_760_000_000_000))
verifie('dateCourteUtc : illisible rend vide', dateCourteUtc('x') === '')

// ─── Le message du changeur ─────────────────────────────────────────

const ordreType = {
  reference: 'VX-UK4GWGQH',
  monnaie: 'USDT',
  montantFcfa: '5 000',
  montantCrypto: '8',
  telephone: '69008549',
}

{
  const l = lignesVerification(
    { etat: 'confirme', txid: 'ab'.repeat(32), montant: 8, quand: MAINTENANT, confirme: true, explorateur: 'https://tronscan.org/#/transaction/abc' },
    { etat: 'coherent' },
    ordreType
  )
  const texte = l.join('\n')
  verifie('MESSAGE : confirme porte la coche', texte.includes('✅'), texte)
  verifie('MESSAGE : confirme dit « lu sur la chaine »', texte.includes('lu sur la chaine'), texte)
  verifie('MESSAGE : confirme porte le txid', texte.includes('ab'.repeat(32)), texte)
}

{
  /*
  LE CAS LE PLUS IMPORTANT DE CE FICHIER. Le téléphone annonce 8 USDT, la
  chaîne en montre 0,8 — la recherche a trouvé un versement de 0,8 pour
  une demande de 0,8, puis l'ordre annonce 8. Le changeur doit voir le
  chiffre de la CHAÎNE, et savoir qu'il est inférieur.
  */
  const l = lignesVerification(
    { etat: 'confirme', txid: 'cd'.repeat(32), montant: 0.8, quand: MAINTENANT, confirme: true },
    { etat: 'coherent' },
    ordreType
  )
  const texte = l.join('\n')
  verifie('MESSAGE : un recu inferieur est signale', texte.includes('MOINS'), texte)
  verifie('MESSAGE : le montant de la chaine est affiche', texte.includes('0.8'), texte)
}

{
  const l = lignesVerification({ etat: 'absent', raison: 'aucun versement' }, { etat: 'coherent' }, ordreType)
  const texte = l.join('\n')
  verifie('MESSAGE : absent porte la croix', texte.includes('❌'), texte)
  verifie('MESSAGE : absent dit de ne rien envoyer', texte.includes('N’ENVOIE RIEN'), texte)
}

{
  const l = lignesVerification({ etat: 'deja_servi', txid: 'ef'.repeat(32) }, { etat: 'coherent' }, ordreType)
  verifie('MESSAGE : deja_servi alerte', l.join('\n').includes('DEJA SERVI'), l)
}

{
  const l = lignesVerification({ etat: 'indisponible', raison: 'trongrid HTTP 503' }, { etat: 'indisponible' }, ordreType)
  const texte = l.join('\n')
  verifie('MESSAGE : indisponible ne promet rien', texte.includes('Rien') && texte.includes('prouve'), texte)
  verifie('MESSAGE : indisponible donne la raison', texte.includes('503'), texte)
}

{
  /*
  L'ÉCART DE PRIX PASSE AVANT LA CHAÎNE. Un transfert peut être réel et
  l'ordre absurde : « 8 USDT reçus, paie 500 000 FCFA ». La vérification
  on-chain dirait ✅ et le changeur paierait cent fois trop.
  */
  const l = lignesVerification(
    { etat: 'confirme', txid: 'ab'.repeat(32), montant: 8, quand: MAINTENANT, confirme: true },
    { etat: 'ecart', attendu: 5000, ecartPourcent: 9900 },
    { ...ordreType, montantFcfa: '500 000' }
  )
  const texte = l.join('\n')
  verifie('MESSAGE : un ecart de prix alerte', texte.includes('ECART DE PRIX'), texte)
  verifie(
    'MESSAGE : l ecart est AVANT la confirmation',
    texte.indexOf('ECART DE PRIX') < texte.indexOf('✅'),
    texte
  )
}

{
  /*
  LES LIGNES VIDES SURVIVENT. Le `filter` du message ne retire que `null`,
  jamais la chaîne vide — une faute déjà commise dans ce fichier, où
  `filter(Boolean)` avait aplati tous les blocs en un pavé compact.
  */
  const l = lignesVerification({ etat: 'absent', raison: 'rien' }, null, ordreType)
  verifie('MESSAGE : le bloc commence par une ligne vide', l[0] === '', l)
  verifie('MESSAGE : aucune ligne nulle', l.every((x) => x !== null), l)
}

{
  const l = lignesVerification(null, null, ordreType)
  verifie('MESSAGE : un achat n ajoute aucune ligne', l.length === 0, l)
}

// ─── La consigne « A FAIRE », qui est la ligne qu'on lit en premier ──

const venteBrute = {
  reference: 'VX-UK4GWGQH',
  sens: 'vente',
  monnaie: 'USDT',
  montantFcfa: '5 000',
  montantCrypto: '8',
  taux: '625 FCFA',
  marge: '25 FCFA/$',
  telephone: '69008549',
}
const confirme = { etat: 'confirme', txid: 'ab'.repeat(32), montant: 8, quand: MAINTENANT, confirme: true }
const consigne = (ordre, verif, coherence) =>
  messageOrdre(ordre, verif, coherence).find((l) => typeof l === 'string' && l.includes('A FAIRE')) || ''

{
  const l = consigne(venteBrute, confirme, { etat: 'coherent' })
  verifie('CONSIGNE : verifie et coherent -> envoyer', l.includes('ENVOYER 5 000 FCFA'), l)
}

{
  const l = consigne(venteBrute, { etat: 'absent', raison: 'rien' }, { etat: 'coherent' })
  verifie('CONSIGNE : introuvable -> ne rien envoyer', l.includes('NE RIEN ENVOYER'), l)
}

{
  /*
  LE CAS TROUVÉ EN IMPRIMANT LE MESSAGE, ET PAR AUCUN AUTRE MOYEN.

  Transfert parfaitement réel, montant cent fois trop élevé. La première
  version ne regardait que la chaîne : elle écrivait donc « A FAIRE :
  ENVOYER 500 000 FCFA » à l'endroit le plus visible du message, avec
  l'alarme d'écart trois lignes plus bas. Il faut les DEUX feux verts.
  */
  const l = consigne(
    { ...venteBrute, montantFcfa: '500 000' },
    confirme,
    { etat: 'ecart', attendu: 5000, ecartPourcent: 9900 }
  )
  verifie('CONSIGNE : verifie mais prix douteux -> ne rien envoyer', l.includes('NE RIEN ENVOYER'), l)
  verifie('CONSIGNE : et surtout, pas de montant a envoyer', !l.includes('500 000 FCFA'), l)
}

{
  const l = consigne({ ...venteBrute, sens: 'achat', montantCrypto: '8' }, null, null)
  verifie('CONSIGNE : un achat dit d envoyer la crypto', l.includes('ENVOYER 8 USDT'), l)
}

{
  const l = consigne(venteBrute, { etat: 'indisponible', raison: 'HTTP 503' }, { etat: 'indisponible' })
  verifie('CONSIGNE : non verifiable -> ne rien envoyer', l.includes('NE RIEN ENVOYER'), l)
}

verifie('groupeMilliers : 500000', groupeMilliers(500000) === '500 000')
verifie('groupeMilliers : 5000', groupeMilliers(5000) === '5 000')
verifie('groupeMilliers : 999', groupeMilliers(999) === '999')
verifie('groupeMilliers : 1234567', groupeMilliers(1234567) === '1 234 567')

// ─── Verdict ────────────────────────────────────────────────────────

console.log(`\n${passes} verifications passees, ${echecs} en echec`)
if (echecs > 0) process.exit(1)
console.log('✅ Le tri des transferts resiste a tous les cas listes ici.')
