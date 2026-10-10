/*
═══════════════════════════════════════════════════════════════════════════
LE MESSAGE DU CHANGEUR, IMPRIMÉ
═══════════════════════════════════════════════════════════════════════════

    node tools/relais-cours/rendu-message.mjs

Ce n'est pas un test : rien n'échoue ici. C'est un REGARD. Il imprime le
message exactement comme il arrivera dans le canal, dans ses cinq états.

POURQUOI ÇA EXISTE. Deux défauts du message ont été trouvés en le
regardant, et aucun en relisant le code : une ligne « Frais : 25 FCFA/$ »
qui se lisait comme un montant alors que c'était un taux — cent fois
d'écart sur cent USDT — et un `filter(Boolean)` qui supprimait les lignes
vides, transformant trois blocs aérés en un pavé illisible.

Un message lu par quelqu'un qui décide vite, le soir, se juge à l'oeil.
═══════════════════════════════════════════════════════════════════════════
*/

import { messageOrdre } from './worker.js'

const MAINTENANT = Date.now()

const venteType = {
  reference: 'VX-UK4GWGQH',
  sens: 'vente',
  monnaie: 'USDT',
  montantFcfa: '5 000',
  montantCrypto: '8',
  taux: '625 FCFA',
  marge: '25 FCFA/$ (4,17 %)',
  adresse: '',
  telephone: '69008549',
  referencePaiement: '',
  txid: '',
}

const achatType = {
  ...venteType,
  reference: 'VX-PQ3MNK7D',
  sens: 'achat',
  adresse: 'TQn9Y2khDD95J42FQtQTdwVVRZqjGBCvpM',
  referencePaiement: 'MP251008123456',
}

const cas = [
  [
    'ACHAT (rien a verifier : Orange Money n est pas une chaine publique)',
    achatType,
    null,
    null,
  ],
  [
    'VENTE VERIFIEE — le cas normal, et celui qui change tout',
    venteType,
    {
      etat: 'confirme',
      txid: 'a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90',
      montant: 8,
      quand: MAINTENANT - 6 * 60 * 1000,
      confirme: true,
      de: 'TMuA6YqfCeX8EhbfYEg5y7S4DqzSJireY9',
      explorateur:
        'https://tronscan.org/#/transaction/a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90',
    },
    { etat: 'coherent', attendu: 5000, ecartPourcent: 0.1 },
  ],
  [
    'VENTE INTROUVABLE — le client n a rien envoye, ou son exchange est lent',
    venteType,
    { etat: 'absent', raison: 'aucun versement correspondant sur les 3 dernieres heures' },
    { etat: 'coherent', attendu: 5000, ecartPourcent: 0.1 },
  ],
  [
    'TRANSFERT DEJA SERVI — un vrai versement reutilise pour se faire payer deux fois',
    venteType,
    {
      etat: 'deja_servi',
      txid: 'a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90',
      raison: 'ce transfert a deja servi',
    },
    { etat: 'coherent' },
  ],
  [
    'CHAINE INJOIGNABLE — on n a pas regarde, et on ne pretend pas le contraire',
    venteType,
    { etat: 'indisponible', raison: 'trongrid HTTP 503' },
    { etat: 'indisponible' },
  ],
  [
    'ECART DE PRIX — transfert reel, ordre absurde : 8 USDT contre 500 000 FCFA',
    { ...venteType, montantFcfa: '500 000' },
    {
      etat: 'confirme',
      txid: 'a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90',
      montant: 8,
      quand: MAINTENANT - 6 * 60 * 1000,
      confirme: true,
      explorateur: '',
    },
    { etat: 'ecart', attendu: 5000, ecartPourcent: 9900 },
  ],
]

for (const [titre, ordre, verif, coherence] of cas) {
  const texte = messageOrdre(ordre, verif, coherence).join('\n')
  console.log('\n' + '═'.repeat(68))
  console.log(titre)
  console.log('═'.repeat(68))
  console.log(texte)
  console.log('─'.repeat(68))
  console.log(`${texte.length} caracteres, ${texte.split('\n').length} lignes`)
}

/*
LA LIMITE DE TELEGRAM EST DE 4096 CARACTÈRES.

Très loin au-dessus de ces messages, et c'est voulu : un message tronqué
perdrait sa fin, où se trouve la mise en garde. On le mesure quand même,
parce que le bloc de vérification a fait grandir le message de moitié et
qu'un futur ajout pourrait continuer.
*/
const plusLong = Math.max(
  ...cas.map(([, o, v, c]) => messageOrdre(o, v, c).join('\n').length)
)
console.log(`\nLe plus long fait ${plusLong} caracteres sur les 4096 permis par Telegram.`)
