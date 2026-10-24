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
  choisirTransfertEtherscan,
  txidValide,
  choisirTransfertAlchemy,
  estAlchemy,
  chaineHorsOffre,
  noeudsEvm,
  hoteMasque,
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

{
  /*
  ═════════════════════════════════════════════════════════════════════
  L'ANTÉRIORITÉ, SUR TRON AUSSI — ET CE TEST EXISTE À CAUSE D'UNE MUTATION
  ═════════════════════════════════════════════════════════════════════

  En éprouvant le tri d'Etherscan, j'ai supprimé par erreur la ligne
  d'antériorité de `choisirTransfertTron` au lieu de la sienne. Aucun
  test de ce fichier n'est tombé.

  Elle était couverte de bout en bout ailleurs (test-garde-fous.mjs),
  donc le garde-fou tenait. Mais un test de bout en bout qui tombe dit
  « quelque chose s'est cassé quelque part », là où celui-ci dit « cette
  ligne-là ». Entre les deux, il y a une heure de recherche.

  Bob voit le versement d'Alice sur un explorateur — il est public — et
  ouvre aussitôt une demande du même montant pour se l'attribuer.
  ═════════════════════════════════════════════════════════════════════
  */
  const versementAlice = transfertTron({ block_timestamp: MAINTENANT - 10 * 60 * 1000 })
  verifie(
    'TRON : Bob ne peut pas reclamer le versement anterieur d Alice',
    choisirTransfertTron([versementAlice], critereTron({ apres: MAINTENANT - 5 * 60 * 1000 })) === null,
    'il a ete attribue a Bob'
  )
  verifie(
    'TRON : la meme demande ouverte avant trouve son versement',
    choisirTransfertTron([versementAlice], critereTron({ apres: MAINTENANT - 20 * 60 * 1000 })) !== null
  )
  /*
  LA MARGE D'UNE MINUTE EST TESTÉE DANS LES DEUX SENS, parce qu'elle est
  le seul endroit où ce contrôle peut être trop strict. L'horloge d'un
  bloc et celle du relais ne sont pas la même : un versement émis à la
  seconde où la demande s'ouvre ne doit pas être rejeté pour trois
  centièmes de décalage.
  */
  verifie(
    'TRON : trente secondes avant la demande passent (derive d horloge)',
    choisirTransfertTron(
      [transfertTron({ block_timestamp: MAINTENANT - 30_000 })],
      critereTron({ apres: MAINTENANT })
    ) !== null
  )
  verifie(
    'TRON : deux minutes avant la demande ne passent pas',
    choisirTransfertTron(
      [transfertTron({ block_timestamp: MAINTENANT - 120_000 })],
      critereTron({ apres: MAINTENANT })
    ) === null
  )
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
verifie('formatMontant : garde les decimales utiles', formatMontant(0.00012345) === '0,00012345')
/*
LA VIRGULE, ET LES MILLIERS GROUPES.

Vu sur un vrai message : « Crypto : 2,021 USDT-BNB » puis « Recu : 2.021
USDT-BNB » — le meme nombre ecrit de deux facons, sur les deux lignes
mêmes qui existent pour comparer l'annonce au versement. Sur deux USDT
l'absurdite saute aux yeux ; sur « 1.132 » contre « 1 132 », non, et ici
le point est un separateur de milliers.

Tout le reste du message vient du telephone, deja a la francaise. Cette
fonction est la seule du Worker qui mette un nombre en forme.
*/
verifie('formatMontant : virgule decimale', formatMontant(2.021) === '2,021')
verifie('formatMontant : milliers groupes', formatMontant(1234567.89) === '1 234 567,89')

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
  verifie('MESSAGE : le montant de la chaine est affiche', texte.includes('0,8'), texte)
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
/*
LES ASSERTIONS PORTENT SUR LE SENS, PAS SUR LE BALISAGE.

Depuis la v58 le message part en HTML : la consigne s'ecrit « ENVOYER
<b>5 000 FCFA</b> par Orange Money au <code>69008549</code> ». Un test qui
cherche « ENVOYER 5 000 FCFA » echoue alors sur une difference de mise en
forme, pas sur une difference de comportement — et l'echec apprend a
desapprendre le test au lieu de le lire.

Les balises sont donc retirees ici. Celles qui comptent ont leurs propres
assertions, juste en dessous : on verifie qu'elles sont la, une fois,
explicitement — pas en les laissant polluer vingt comparaisons.
*/
const sansBalises = (t) => String(t).replace(/<\/?(b|i|u|s|code|pre)>/g, '')
const ligneBrute = (ordre, verif, coherence) =>
  messageOrdre(ordre, verif, coherence).find((l) => typeof l === 'string' && l.includes('A FAIRE')) || ''
const consigne = (ordre, verif, coherence) => sansBalises(ligneBrute(ordre, verif, coherence))

{
  /*
  LE TAUX PORTE SON UNITE. « 561 FCFA » ne dit pas de quoi ; c'est la
  ligne qu'on relit pour verifier qu'on n'a pas inverse un sens, et c'est
  la qu'un sous-entendu lache.
  */
  const t = messageOrdre(venteBrute, confirme, { etat: 'coherent' })
    .find((l) => typeof l === 'string' && l.startsWith('Taux')) || ''
  verifie('TAUX : la monnaie suit le chiffre', t.includes('625 FCFA / USDT'), t)
  const nu = messageOrdre({ ...venteBrute, monnaie: '' }, confirme, { etat: 'coherent' })
    .find((l) => typeof l === 'string' && l.startsWith('Taux')) || ''
  verifie('TAUX : sans monnaie, pas de barre orpheline', !nu.includes('/'), nu)
}

{
  /*
  LE BALISAGE N'EST PAS DECORATIF : le gras porte le chiffre qui decide,
  et le `code` rend le numero copiable d'un appui — c'est lui qui supprime
  la seule retranscription a la main du parcours du changeur.
  */
  const brute = ligneBrute(venteBrute, confirme, { etat: 'coherent' })
  verifie('HTML : le montant a payer est en gras', brute.includes('<b>5 000 FCFA</b>'), brute)
  verifie('HTML : le numero se copie d un appui', brute.includes('<code>69008549</code>'), brute)
}

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

// ─── La fenêtre de rapprochement ────────────────────────────────────

/*
═══════════════════════════════════════════════════════════════════════════
CE RÉGLAGE EST NÉ D'UN ESSAI RÉEL
═══════════════════════════════════════════════════════════════════════════

Un versement de test était bien arrivé sur la chaîne, et le relais
répondait « absent » — non pas parce qu'il ne le voyait pas, mais parce
qu'il datait de plus de trois heures.

Le comportement était juste ; la durée, trop courte pour le cas qui se
présentait. Et ce cas n'a rien d'exceptionnel : quelqu'un retire depuis
une plateforme d'échange dont la file traîne, part travailler, et revient
déposer sa demande le soir.
═══════════════════════════════════════════════════════════════════════════
*/

import { fenetreVente } from './worker.js'

verifie('FENETRE : trois heures par defaut', fenetreVente({}) === 3 * 3600_000, fenetreVente({}))
verifie(
  'FENETRE : reglable en minutes',
  fenetreVente({ CHANGE_FENETRE_MINUTES: '720' }) === 12 * 3600_000
)
verifie(
  'FENETRE : une valeur illisible retombe sur trois heures',
  fenetreVente({ CHANGE_FENETRE_MINUTES: 'beaucoup' }) === 3 * 3600_000
)
verifie(
  'FENETRE : zero ou negatif retombe sur trois heures',
  fenetreVente({ CHANGE_FENETRE_MINUTES: '0' }) === 3 * 3600_000 &&
    fenetreVente({ CHANGE_FENETRE_MINUTES: '-5' }) === 3 * 3600_000
)
/*
LES BORNES. En dessous d'un quart d'heure, on refuserait des versements en
cours de confirmation. Au-dela de sept jours, la fenetre cesse de borner
quoi que ce soit — et son role est precisement qu'un versement d'il y a un
mois ne justifie pas une demande d'aujourd'hui.
*/
verifie('FENETRE : plancher a quinze minutes', fenetreVente({ CHANGE_FENETRE_MINUTES: '1' }) === 15 * 60_000)
verifie(
  'FENETRE : plafond a sept jours',
  fenetreVente({ CHANGE_FENETRE_MINUTES: '999999' }) === 7 * 24 * 3600_000
)

{
  // Et elle est bien APPLIQUEE par le tri : un versement de six heures
  // passe a douze heures de fenetre, pas a trois.
  const vieux = transfertTron({ block_timestamp: MAINTENANT - 6 * 3600_000 })
  verifie(
    'FENETRE : six heures refusees par defaut',
    choisirTransfertTron([vieux], critereTron()) === null
  )
  verifie(
    'FENETRE : six heures acceptees a douze heures de fenetre',
    choisirTransfertTron([vieux], critereTron({ fenetre: 12 * 3600_000 })) !== null
  )
}


/*
═══════════════════════════════════════════════════════════════════════════
ETHERSCAN — LE MÊME TRI, LA MÊME BATTERIE D'ATTAQUES
═══════════════════════════════════════════════════════════════════════════

Cette source est devenue le chemin NORMAL des ventes BNB et ETH, parce que
les sept nœuds publics refusent `eth_getLogs` sur un seul bloc depuis un
Worker. Un chemin normal se teste comme l'autre.

ET IL Y A UNE RAISON PARTICULIÈRE DE L'ÉPROUVER : il ressemble beaucoup à
`choisirTransfertTron`, et deux de ses lignes disent délibérément le
CONTRAIRE de leur jumelle — la casse des adresses, et l'unité de
l'horodate. Ce sont exactement les endroits où l'œil glisse en relisant.
═══════════════════════════════════════════════════════════════════════════
*/

/*
DES ADRESSES EN CASSE MIXTE, VOLONTAIREMENT.

Celles du décor EVM plus haut sont des répétitions de chiffres — très
lisibles, mais insensibles à la casse. Elles ne pourraient donc pas
révéler une comparaison faite sans `toLowerCase`, qui est précisément la
faute que ce tri-ci doit éviter. `USDT_BNB`, lui, est repris tel quel :
il porte déjà sa casse de contrôle.
*/
const CHANGEUR_BNB = '0xbde137a9684f0bc437294b2cf2ab7295831e327e'
const CLIENT_BNB = '0x28c6c06298d514db089934071355e5743bf21d60'

/** Un transfert de jeton tel qu'Etherscan le rend, avec de quoi le déformer. */
function transfertEs(modifications) {
  return {
    hash: '0x' + 'ab'.repeat(32),
    from: CLIENT_BNB,
    to: CHANGEUR_BNB,
    contractAddress: USDT_BNB.toLowerCase(),
    tokenDecimal: '18',
    tokenSymbol: 'USDT',
    // DIX-HUIT décimales. 2,000359 USDT-BNB en unités brutes, ce qui fait
    // dix-neuf chiffres — au-delà de ce qu'un `Number` tient.
    value: '2000359000000000000',
    timeStamp: String(Math.floor((MAINTENANT - 5 * 60 * 1000) / 1000)),
    ...(modifications || {}),
  }
}

const critereEs = (extra) => ({
  adresse: CHANGEUR_BNB,
  contrat: USDT_BNB,
  decimales: 18,
  montant: 2.000359,
  txid: '',
  maintenant: MAINTENANT,
  exact: true,
  ...(extra || {}),
})

{
  const r = choisirTransfertEtherscan([transfertEs()], critereEs())
  verifie('ES : un versement conforme est trouve', r !== null, r)
  verifie('ES : le montant vient de la reponse, pas de la demande', r && r.montant === 2.000359, r)
  verifie('ES : l expediteur est rendu', r && r.de === CLIENT_BNB, r)
  verifie('ES : le txid est rendu', r && r.txid === '0x' + 'ab'.repeat(32), r)
}

{
  /*
  LA CASSE DE CONTRÔLE NE DOIT PAS FAIRE RATER UN VERSEMENT.

  C'est le cas qui a motivé la ligne : Etherscan rend les adresses en
  casse mixte, et CHANGE_ADRESSES porte souvent la forme minuscule. Une
  comparaison stricte refuserait ici un versement parfaitement valide —
  et on chercherait le défaut sur la chaîne.
  */
  const r = choisirTransfertEtherscan(
    [transfertEs({ to: '0xBdE137a9684F0bC437294b2cF2ab7295831E327e' })],
    critereEs()
  )
  verifie('ES : la casse de controle de l adresse est toleree', r !== null, r)
}

{
  const r = choisirTransfertEtherscan(
    [transfertEs({ contractAddress: USDT_BNB.toUpperCase() })],
    critereEs()
  )
  verifie('ES : la casse du contrat est toleree', r !== null, r)
}

{
  /*
  UN WEI D'ÉCART EST ACCEPTÉ, ET C'EST UNE DÉCISION, PAS UN RELÂCHEMENT.

  « Exact » veut dire exact À SIX DÉCIMALES, parce que c'est la précision
  du montant qu'on a demandé au client. Exiger le wei refuserait des
  versements honnêtes : le client tape 2,000359 dans son exchange, et ce
  qui part est arrondi par l'exchange, pas par lui.

  Et ça ne rouvre rien : six décimales décrivent déjà un million de
  montants possibles par unité, dont les quatre dernières sont dérivées
  de la référence. L'écart qu'on tolère ici est mille milliards de fois
  plus petit que celui qui permettrait une confusion.

  J'avais d'abord écrit ici un test « dix-huit décimales ne débordent
  pas ». Il passait sans rien prouver : à ces grandeurs, une conversion
  naïve en Number rend le même résultat qu'un parcours en BigInt, parce
  que l'arrondi à six décimales efface la différence. Un test qui ne peut
  pas échouer est pire qu'un test absent — il rassure.
  */
  const r = choisirTransfertEtherscan(
    [transfertEs({ value: '2000358999999999999' })],
    critereEs()
  )
  verifie('ES : un wei de moins est encore le bon montant', r !== null, r)

  // Mais un MILLIONIÈME de moins, non : c'est la dernière décimale du
  // marquage, et c'est elle qui distingue deux demandes.
  const trop = choisirTransfertEtherscan(
    [transfertEs({ value: '2000358000000000000' })],
    critereEs()
  )
  verifie('ES : un millionieme de moins ne passe pas', trop === null, trop)
}

// ─── Ce qui doit échouer ────────────────────────────────────────────

{
  const r = choisirTransfertEtherscan(
    [transfertEs({ to: CLIENT_BNB })],
    critereEs()
  )
  verifie('ES : un versement vers quelqu un d autre est refuse', r === null, r)
}

{
  /*
  LE FAUX JETON. Déployer un contrat nommé « USDT » à dix-huit décimales
  prend dix minutes. Sans la ligne du contrat, ces jetons sans valeur
  seraient lus comme de l'USDT et le changeur paierait en FCFA.
  */
  const r = choisirTransfertEtherscan(
    [transfertEs({ contractAddress: '0x' + '99'.repeat(20), tokenSymbol: 'USDT' })],
    critereEs()
  )
  verifie('ES : un faux jeton nomme USDT est refuse', r === null, r)
}

{
  const r = choisirTransfertEtherscan([transfertEs({ contractAddress: '' })], critereEs())
  verifie('ES : un contrat absent est refuse, pas devine', r === null, r)
}

{
  // Six décimales annoncées là où on en attend dix-huit : lire la valeur
  // avec les mauvaises décimales donne un facteur mille milliards.
  const r = choisirTransfertEtherscan([transfertEs({ tokenDecimal: '6' })], critereEs())
  verifie('ES : des decimales qui ne sont pas les notres sont refusees', r === null, r)
}

{
  const r = choisirTransfertEtherscan([transfertEs({ tokenDecimal: '' })], critereEs())
  verifie('ES : des decimales absentes sont refusees', r === null, r)
}

{
  // Montant marqué : un montant arrondi au voisinage ne passe PAS. C'est
  // toute la raison d'être du marquage.
  const r = choisirTransfertEtherscan(
    [transfertEs({ value: '2000000000000000000' })],
    critereEs()
  )
  verifie('ES : marque, 2.000000 ne paie pas 2.000359', r === null, r)
}

{
  /*
  ═════════════════════════════════════════════════════════════════════
  L'ATTAQUE D'ALICE ET BOB, REJOUÉE SUR CETTE SOURCE
  ═════════════════════════════════════════════════════════════════════

  Bob voit passer le versement d'Alice sur un explorateur public — il est
  public, c'est tout l'objet d'une chaîne. Il ouvre aussitôt une demande
  pour le même montant, en espérant que le relais lui attribue.

  Deux barrières le refusent, et il faut les DEUX :

  — l'antériorité : sa demande naît après le versement ;
  — le marquage : le montant d'Alice ne porte pas les décimales de SA
    référence, donc même sans antériorité il ne correspondrait pas.

  Ce test vérifie que le refus tient sur la première, en isolant : on lui
  donne le montant exact d'Alice, pour qu'il ne reste que l'antériorité.
  ═════════════════════════════════════════════════════════════════════
  */
  const versementAlice = transfertEs({
    timeStamp: String(Math.floor((MAINTENANT - 10 * 60 * 1000) / 1000)),
  })
  const demandeBob = critereEs({
    // La demande de Bob naît cinq minutes APRÈS le versement d'Alice.
    apres: MAINTENANT - 5 * 60 * 1000,
  })
  verifie(
    'ES : Bob ne peut pas reclamer le versement anterieur d Alice',
    choisirTransfertEtherscan([versementAlice], demandeBob) === null,
    'il a ete attribue a Bob'
  )
  // Et la même demande, ouverte AVANT, trouve bien son versement : la
  // barrière doit refuser Bob sans refuser Alice.
  verifie(
    'ES : la meme demande ouverte avant trouve son versement',
    choisirTransfertEtherscan([versementAlice], critereEs({ apres: MAINTENANT - 20 * 60 * 1000 })) !== null
  )
}

{
  /*
  L'HORODATE EST EN SECONDES ICI, en millisecondes chez TronGrid. Si la
  conversion sautait, tous les versements se placeraient en 1970 et la
  fenêtre les refuserait tous — ou, selon le sens de la faute, les
  accepterait tous. Ce test fixe le sens.
  */
  const vieux = transfertEs({ timeStamp: String(Math.floor((MAINTENANT - 6 * 3600_000) / 1000)) })
  verifie('ES : six heures refusees par defaut', choisirTransfertEtherscan([vieux], critereEs()) === null)
  verifie(
    'ES : six heures acceptees a douze heures de fenetre',
    choisirTransfertEtherscan([vieux], critereEs({ fenetre: 12 * 3600_000 })) !== null
  )
  // Et une horodate en millisecondes, donnée par erreur, tombe DANS LE
  // FUTUR de cinquante ans : elle doit être refusee, pas acceptee.
  const futur = transfertEs({ timeStamp: String(MAINTENANT) })
  verifie('ES : une horodate en millisecondes est refusee', choisirTransfertEtherscan([futur], critereEs()) === null)
}

{
  const r = choisirTransfertEtherscan([transfertEs({ hash: '' })], critereEs())
  verifie('ES : un versement sans txid est refuse', r === null, r)
}

{
  verifie('ES : une reponse qui n est pas un tableau rend null', choisirTransfertEtherscan(null, critereEs()) === null)
  verifie('ES : une liste vide rend null', choisirTransfertEtherscan([], critereEs()) === null)
}

{
  // Le bon versement n'est pas le premier : Etherscan rend l'activité
  // complète du changeur, et ses autres clients y sont aussi.
  const liste = [
    transfertEs({ value: '25000000000000000000', hash: '0x' + 'cd'.repeat(32) }),
    transfertEs({ to: CLIENT_BNB, hash: '0x' + 'ef'.repeat(32) }),
    transfertEs(),
  ]
  const r = choisirTransfertEtherscan(liste, critereEs())
  verifie('ES : le bon versement est trouve au milieu des autres', r && r.montant === 2.000359, r)
}


/*
═══════════════════════════════════════════════════════════════════════════
LE NŒUD NOMINATIF, ET LA CLÉ QUI NE DOIT PAS FUIR
═══════════════════════════════════════════════════════════════════════════
*/

{
  /*
  LE NŒUD À NOUS PASSE DEVANT, LES PUBLICS RESTENT DERRIÈRE.

  L'ordre est le test : s'il passait derrière, les sept publics seraient
  essayés d'abord et chacun coûterait son délai d'attente avant qu'on
  arrive au seul qui marche. Et s'il les REMPLAÇAIT, on échangerait sept
  secours contre un point de rupture unique.
  */
  const liste = noeudsEvm('bnb', { CHANGE_NOEUD_BNB: 'https://bnb.exemple.com/v2/CLE' })
  verifie('NOEUD : le nominatif est en premier', liste[0] === 'https://bnb.exemple.com/v2/CLE', liste[0])
  verifie('NOEUD : les publics sont toujours la', liste.length >= 8, liste.length)
}

{
  const sansRien = noeudsEvm('bnb', {})
  verifie('NOEUD : sans reglage, la liste publique est rendue telle quelle', sansRien.length === 7, sansRien.length)
  verifie('NOEUD : un reseau inconnu rend une liste vide', noeudsEvm('solana', {}).length === 0)
}

{
  /*
  CE QUI N'EST PAS UNE URL HTTPS EST ÉCARTÉ.

  Le cas réel qu'on attend ici : un collage raté. Une valeur vide, un
  « https://... » laissé tel quel, un espace. Les laisser entrer ferait
  échouer le PREMIER appel de chaque vérification — donc ajouterait un
  délai d'attente à toutes les ventes, pour rien.
  */
  const sale = noeudsEvm('bnb', { CHANGE_NOEUD_BNB: '  ,  , http://pas-sur.com , https://bon.com/v2/K  ' })
  verifie('NOEUD : le vide et le non-https sont ecartes', sale[0] === 'https://bon.com/v2/K', sale[0])
  verifie('NOEUD : rien d autre ne s est glisse devant', sale.length === 8, sale.length)
}

{
  // Deux nœuds nominatifs, séparés par une virgule : l'ordre est gardé.
  const deux = noeudsEvm('eth', { CHANGE_NOEUD_ETH: 'https://a.com/1,https://b.com/2' })
  verifie('NOEUD : deux nominatifs gardent leur ordre', deux[0] === 'https://a.com/1' && deux[1] === 'https://b.com/2', deux.slice(0, 2))
}

{
  /*
  ═════════════════════════════════════════════════════════════════════
  LA CLÉ NE DOIT PAS SORTIR DANS UN DIAGNOSTIC
  ═════════════════════════════════════════════════════════════════════

  `/diag/plages` imprime quel nœud a répondu quoi. Sans masquage, il
  publierait l'URL complète — donc la clé — à quiconque ouvre l'adresse
  du relais dans un navigateur. Le diagnostic est en accès libre : c'est
  son intérêt, et c'est pourquoi il ne doit rien porter de secret.
  ═════════════════════════════════════════════════════════════════════
  */
  verifie(
    'MASQUE : la cle du chemin disparait',
    hoteMasque('https://bnb-mainnet.exemple.com/v2/abcdef0123456789') ===
      'bnb-mainnet.exemple.com/v2/***',
    hoteMasque('https://bnb-mainnet.exemple.com/v2/abcdef0123456789')
  )
  verifie(
    'MASQUE : on sait encore QUEL noeud a repondu',
    hoteMasque('https://bnb-mainnet.exemple.com/v2/abcdef0123456789').includes('bnb-mainnet.exemple.com')
  )
  verifie(
    'MASQUE : un hote sans chemin est rendu entier',
    hoteMasque('https://bsc.meowrpc.com') === 'bsc.meowrpc.com',
    hoteMasque('https://bsc.meowrpc.com')
  )
  verifie(
    'MASQUE : un chemin profond ne garde que son dernier segment cache',
    hoteMasque('https://h.com/bsc/mainnet/CLE123') === 'h.com/bsc/mainnet/***',
    hoteMasque('https://h.com/bsc/mainnet/CLE123')
  )
  /*
  LE CAS QUI COMPTE VRAIMENT : aucune clé plausible ne doit se retrouver
  dans la sortie. On le vérifie sur la chaîne elle-même plutôt que sur
  une forme attendue — c'est la propriété qu'on veut, pas le format.
  */
  const CLE = 'sk_live_9f3c1aa7b2e4'
  verifie(
    'MASQUE : la cle est absente de la sortie, quelle que soit la forme',
    !hoteMasque(`https://noeud.exemple.com/rpc/${CLE}`).includes(CLE)
  )
}

{
  /*
  « PAS DANS TON OFFRE » SE DISTINGUE DE « CLÉ INVALIDE ».

  Etherscan rend le même `status: "0"` pour les deux, et seul le texte
  les sépare. Les confondre coûterait dans les deux sens : un refus
  d'offre traité comme une panne empêcherait le secours de prendre la
  main, et une clé invalide traitée comme un refus d'offre masquerait un
  reglage à corriger.
  */
  verifie(
    'OFFRE : le refus mesure sur BNB est reconnu',
    chaineHorsOffre('etherscan : Free API access is not supported for this chain. Please upgrade your api plan')
  )
  verifie('OFFRE : une invitation a changer d offre est reconnue', chaineHorsOffre('Please upgrade your plan'))
  verifie('OFFRE : une cle invalide N EST PAS un refus d offre', !chaineHorsOffre('Invalid API Key'))
  verifie('OFFRE : un hote muet N EST PAS un refus d offre', !chaineHorsOffre('etherscan HTTP 502'))
  verifie('OFFRE : rien du tout n est pas un refus d offre', !chaineHorsOffre('') && !chaineHorsOffre(undefined))
}


/*
═══════════════════════════════════════════════════════════════════════════
LES TRANSFERTS ENRICHIS — LE TROISIÈME TRI, ET SES DEUX PIÈGES À LUI
═══════════════════════════════════════════════════════════════════════════

C'est le chemin qui marche sur BNB. Deux détails lui sont propres et ne
lèveraient aucune erreur s'ils étaient manqués :

— les décimales arrivent en HEXADÉCIMAL (« 0x12 » vaut 18, pas 12) ;
— la réponse contient DEUX montants, et le commode est le faux.
═══════════════════════════════════════════════════════════════════════════
*/

const USDT_BNB_BAS = USDT_BNB.toLowerCase()

/** Un transfert tel qu'`alchemy_getAssetTransfers` le rend. */
function transfertAl(modifications, brutMods) {
  return {
    hash: '0x' + 'ab'.repeat(32),
    from: CLIENT_BNB,
    to: CHANGEUR_BNB,
    category: 'erc20',
    asset: 'USDT',
    // Le montant COMMODE, celui qu'il ne faut pas lire.
    value: 2.000359,
    rawContract: {
      address: USDT_BNB_BAS,
      value: '0x' + (2000359000000000000n).toString(16),
      decimal: '0x12', // 18
      ...(brutMods || {}),
    },
    metadata: { blockTimestamp: new Date(MAINTENANT - 5 * 60 * 1000).toISOString() },
    ...(modifications || {}),
  }
}

const critereAl = (extra) => ({
  adresse: CHANGEUR_BNB,
  contrat: USDT_BNB,
  decimales: 18,
  montant: 2.000359,
  txid: '',
  maintenant: MAINTENANT,
  exact: true,
  ...(extra || {}),
})

{
  const r = choisirTransfertAlchemy([transfertAl()], critereAl())
  verifie('AL : un versement conforme est trouve', r !== null, r)
  verifie('AL : le montant vient de l entier brut', r && r.montant === 2.000359, r)
  verifie('AL : l expediteur est rendu', r && r.de === CLIENT_BNB, r)
}

{
  /*
  ═════════════════════════════════════════════════════════════════════
  LE MONTANT COMMODE EST LE FAUX, ET CE TEST LE PROUVE
  ═════════════════════════════════════════════════════════════════════

  On donne ici un `value` flottant GROSSIÈREMENT faux, tout en laissant
  l'entier brut correct. Si le tri lisait `value`, il refuserait ce
  versement parfaitement valide.

  En production la différence serait bien plus discrète — un arrondi sur
  la sixième décimale — et c'est précisément pour ça qu'il faut la fixer
  ici, où elle est visible.
  ═════════════════════════════════════════════════════════════════════
  */
  const r = choisirTransfertAlchemy([transfertAl({ value: 999.999 })], critereAl())
  verifie('AL : le flottant commode est ignore au profit de l entier brut', r !== null, r)
  verifie('AL : et le montant rendu est le vrai', r && r.montant === 2.000359, r)
}

{
  /*
  LES DÉCIMALES SONT EN HEXADÉCIMAL. « 0x12 » vaut 18. Les lire en base
  dix donnerait douze — six ordres de grandeur, un versement de 2 USDT
  lu comme deux millions.
  */
  /*
  SIX DÉCIMALES LUES EN HEXADÉCIMAL DONNENT BIEN SIX, et le montant s'en
  déduit correctement. (J'avais d'abord écrit ici une assertion terminée
  par `|| true` : elle ne pouvait pas échouer. C'est le défaut que ce
  fichier dénonce deux cents lignes plus haut, et je l'avais commis.)
  */
  const six = choisirTransfertAlchemy(
    [transfertAl(null, { decimal: '0x6', value: '0x' + (2000359n).toString(16) })],
    critereAl({ decimales: 6 })
  )
  verifie('AL : 0x6 vaut six, et le montant suit', six !== null && six.montant === 2.000359, six)

  // Et le cas qui compte : 0x12 n'est pas douze.
  const faux = choisirTransfertAlchemy([transfertAl()], critereAl({ decimales: 12 }))
  verifie('AL : 0x12 n est pas douze', faux === null, faux)
  const vrai = choisirTransfertAlchemy([transfertAl()], critereAl({ decimales: 18 }))
  verifie('AL : 0x12 est bien dix-huit', vrai !== null, vrai)
}

{
  const r = choisirTransfertAlchemy([transfertAl(null, { decimal: undefined })], critereAl())
  verifie('AL : des decimales absentes sont refusees, pas devinees', r === null, r)
}

// ─── Ce qui doit échouer ────────────────────────────────────────────

{
  verifie(
    'AL : un versement vers quelqu un d autre est refuse',
    choisirTransfertAlchemy([transfertAl({ to: CLIENT_BNB })], critereAl()) === null
  )
  verifie(
    'AL : un faux jeton nomme USDT est refuse',
    choisirTransfertAlchemy([transfertAl({ asset: 'USDT' }, { address: '0x' + '99'.repeat(20) })], critereAl()) === null
  )
  verifie(
    'AL : un contrat absent est refuse',
    choisirTransfertAlchemy([transfertAl(null, { address: '' })], critereAl()) === null
  )
  /*
  LA CATÉGORIE : sans elle, un transfert natif de BNB pourrait se
  présenter là où on attend un jeton. Le contrat le rattraperait sans
  doute, mais une barrière qui dépend d'une autre n'est pas une barrière.
  */
  verifie(
    'AL : un transfert natif n est pas un transfert de jeton',
    choisirTransfertAlchemy([transfertAl({ category: 'external' })], critereAl()) === null
  )
  verifie(
    'AL : marque, 2.000000 ne paie pas 2.000359',
    choisirTransfertAlchemy(
      [transfertAl(null, { value: '0x' + (2000000000000000000n).toString(16) })],
      critereAl()
    ) === null
  )
  verifie('AL : un versement sans txid est refuse', choisirTransfertAlchemy([transfertAl({ hash: '' })], critereAl()) === null)
  verifie('AL : une reponse qui n est pas un tableau rend null', choisirTransfertAlchemy(null, critereAl()) === null)
}

{
  /*
  L'HORODATE EST UNE DATE ISO ICI — ni des secondes comme chez Etherscan,
  ni des millisecondes comme chez TronGrid. Trois sources, trois formats.
  */
  const vieux = transfertAl({ metadata: { blockTimestamp: new Date(MAINTENANT - 6 * 3600_000).toISOString() } })
  verifie('AL : six heures refusees par defaut', choisirTransfertAlchemy([vieux], critereAl()) === null)
  verifie(
    'AL : six heures acceptees a douze heures de fenetre',
    choisirTransfertAlchemy([vieux], critereAl({ fenetre: 12 * 3600_000 })) !== null
  )
  verifie(
    'AL : une horodate illisible est refusee',
    choisirTransfertAlchemy([transfertAl({ metadata: { blockTimestamp: 'bientot' } })], critereAl()) === null
  )
  verifie(
    'AL : une horodate absente est refusee',
    choisirTransfertAlchemy([transfertAl({ metadata: {} })], critereAl()) === null
  )
}

{
  // L'attaque d'Alice et Bob, sur cette source aussi.
  const versementAlice = transfertAl({
    metadata: { blockTimestamp: new Date(MAINTENANT - 10 * 60 * 1000).toISOString() },
  })
  verifie(
    'AL : Bob ne peut pas reclamer le versement anterieur d Alice',
    choisirTransfertAlchemy([versementAlice], critereAl({ apres: MAINTENANT - 5 * 60 * 1000 })) === null,
    'il a ete attribue a Bob'
  )
  verifie(
    'AL : la meme demande ouverte avant trouve son versement',
    choisirTransfertAlchemy([versementAlice], critereAl({ apres: MAINTENANT - 20 * 60 * 1000 })) !== null
  )
}

{
  /*
  ON N'ENVOIE LA MÉTHODE ENRICHIE QU'À UN NŒUD QUI LA SERT. L'envoyer à
  un nœud public coûterait un aller-retour pour une erreur certaine, à
  chaque vérification.
  */
  verifie('ENRICHI : une URL Alchemy est reconnue', estAlchemy('https://bnb-mainnet.g.alchemy.com/v2/CLE'))
  verifie('ENRICHI : un noeud public ne l est pas', !estAlchemy('https://bsc-rpc.publicnode.com'))
  verifie('ENRICHI : une valeur vide ne l est pas', !estAlchemy('') && !estAlchemy(undefined))
  /*
  ET UN NOM QUI CONTIENT « alchemy.com » SANS ÊTRE ALCHEMY NE PASSE PAS.
  Sans cette précision, n'importe qui enregistrant `alchemy.com.mal.net`
  se verrait adresser nos requêtes — avec l'adresse du changeur dedans.
  */
  verifie('ENRICHI : un domaine qui imite alchemy.com est refuse', !estAlchemy('https://alchemy.com.mal.net/v2/x'))
}


/*
═══════════════════════════════════════════════════════════════════════════
LA FORME D'UN IDENTIFIANT DE TRANSACTION
═══════════════════════════════════════════════════════════════════════════

Ce bloc existe a cause d'un VRAI message envoye a un vrai changeur :

    eth_getTransactionByHash : invalid argument 0:
    hex string has length 40, want 64

Quarante caracteres, c'est une adresse. Soixante-quatre, c'est une
transaction. Les deux commencent par « 0x » et se ressemblent assez pour
qu'on colle l'une a la place de l'autre.

LE COUT N'ETAIT PAS CELUI QU'ON CROIT : le noeud a refuse l'appel, et
TOUTE la verification a repondu « indisponible » — alors que le versement
etait peut-etre la, trouvable par son montant marque. Un champ FACULTATIF
empechait le chemin OBLIGATOIRE de s'executer.
═══════════════════════════════════════════════════════════════════════════
*/

{
  const HASH = '0x' + 'ab'.repeat(32)
  verifie('TXID : un hash EVM bien forme passe', txidValide(HASH, 'evm'), HASH)
  verifie('TXID : la casse ne gene pas', txidValide('0x' + 'AB'.repeat(32), 'evm'))
}

{
  /*
  LE CAS MESURE : l'adresse du changeur collee dans le champ du hash.
  Quarante caracteres hexadecimaux apres « 0x ».
  */
  const ADRESSE = '0xbde137a9684f0bc437294b2cf2ab7295831e327e'
  verifie('TXID : une ADRESSE EVM est refusee', !txidValide(ADRESSE, 'evm'), ADRESSE)
  verifie('TXID : et elle l est aussi sur les autres chaines', !txidValide(ADRESSE, 'tron'))
}

{
  verifie('TXID : vide, ce n est pas un identifiant', !txidValide('', 'evm'))
  verifie('TXID : des espaces seuls non plus', !txidValide('   ', 'evm'))
  verifie('TXID : un hash tronque est refuse', !txidValide('0x' + 'ab'.repeat(30), 'evm'))
  verifie('TXID : un hash trop long est refuse', !txidValide('0x' + 'ab'.repeat(33), 'evm'))
  verifie(
    'TXID : un caractere non hexadecimal est refuse',
    !txidValide('0x' + 'ab'.repeat(31) + 'zz', 'evm')
  )
}

{
  /*
  LES FORMES NE SONT PAS INTERCHANGEABLES. Un hash TRON est nu, un hash
  EVM porte « 0x ». Accepter l'un pour l'autre deplacerait simplement
  l'erreur d'une ligne — le noeud refuserait plus loin.
  */
  const NU = 'cd'.repeat(32)
  verifie('TXID : un hash TRON nu passe sur TRON', txidValide(NU, 'tron'))
  verifie('TXID : le meme SANS 0x est refuse en EVM', !txidValide(NU, 'evm'))
  verifie('TXID : un hash Bitcoin nu passe', txidValide(NU, 'btc'))
  /*
  ON TOLERE UN « 0x » COLLE PAR HABITUDE sur TRON et Bitcoin : le hash
  est par ailleurs correct, et refuser quelqu'un pour deux caracteres de
  politesse ferait echouer une vente honnete.
  */
  verifie('TXID : un 0x de trop est tolere sur TRON', txidValide('0x' + NU, 'tron'))
}

{
  verifie('TXID : une chaine inconnue refuse tout', !txidValide('0x' + 'ab'.repeat(32), 'solana'))
}

// ─── Verdict ────────────────────────────────────────────────────────

console.log(`\n${passes} verifications passees, ${echecs} en echec`)
if (echecs > 0) process.exit(1)
console.log('✅ Le tri des transferts resiste a tous les cas listes ici.')
