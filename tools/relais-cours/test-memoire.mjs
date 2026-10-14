/*
═══════════════════════════════════════════════════════════════════════════
LA MÉMOIRE DU RELAIS, MISE À L'ÉPREUVE
═══════════════════════════════════════════════════════════════════════════

    node tools/relais-cours/test-memoire.mjs

Ce fichier existe à cause d'un défaut qui n'a JAMAIS levé d'erreur et qui a
coûté deux barrières de sécurité.

`/change/consigne` écrivait la naissance d'une demande ; `/change/verifier`
ne la retrouvait pas. Les deux fonctions étaient justes. Le magasin, non :
`caches.default` est par centre de données, évinçable à tout moment, et
échoue en silence.

Conséquence invisible : l'antériorité se désactivait toute seule, et
`deja_servi` — la garantie qu'un transfert ne paie QU'UNE FOIS — reposait
sur la même fondation.

─── CE QUE CE FICHIER VÉRIFIE ─────────────────────────────────────────────

Que les deux magasins, KV et cache, se comportent PAREIL. Parce que le
secours ne doit pas être une seconde implémentation avec ses propres
surprises : le jour où KV manque, le relais doit se dégrader, pas changer
de comportement.

Le faux KV ci-dessous obéit au contrat de Cloudflare — `get` rend null sur
une clé absente, `put` accepte `expirationTtl` — et rien de plus.
═══════════════════════════════════════════════════════════════════════════
*/

import {
  poserMemoire,
  dejaVu,
  marquer,
  compteur,
  incrementer,
  marquerValeur,
  lireValeur,
} from './worker.js'

let passes = 0
let echecs = 0

function verifie(nom, condition, obtenu) {
  if (condition) {
    passes += 1
  } else {
    echecs += 1
    console.log(`❌ ${nom}`)
    if (obtenu !== undefined) console.log(`   obtenu : ${JSON.stringify(obtenu)}`)
  }
}

/**
 * Un KV de laboratoire.
 *
 * IL GARDE LE TTL SANS L'APPLIQUER. Le vérifier demanderait de faire
 * avancer l'horloge, ce que ce fichier ne sait pas faire ; on vérifie
 * seulement qu'il est TRANSMIS, et qu'il respecte le plancher de soixante
 * secondes imposé par Cloudflare — une écriture refusée pour un TTL trop
 * court serait un oubli de plus, c'est-à-dire le défaut qu'on corrige.
 */
function fauxKv() {
  const donnees = new Map()
  const ttls = new Map()
  return {
    donnees,
    ttls,
    async get(cle) {
      return donnees.has(cle) ? donnees.get(cle) : null
    },
    async put(cle, valeur, options) {
      donnees.set(cle, String(valeur))
      ttls.set(cle, options?.expirationTtl)
    },
  }
}

// ─── Avec KV ────────────────────────────────────────────────────────

{
  const kv = fauxKv()
  poserMemoire({ MEMOIRE: kv })

  verifie('KV : une marque absente ne se voit pas', (await dejaVu('txid', '0xabc')) === false)
  await marquer('txid', '0xabc', 86400)
  verifie('KV : une marque posée se retrouve', (await dejaVu('txid', '0xabc')) === true)
  verifie('KV : une AUTRE marque reste absente', (await dejaVu('txid', '0xdef')) === false)

  /*
  LES ESPACES NE SE MÉLANGENT PAS. Sans préfixe, un txid et un numéro de
  téléphone identiques se marqueraient l'un l'autre — et « ce transfert a
  déjà servi » se déclencherait sur un client qui n'a rien fait.
  */
  verifie('KV : un meme nom dans un autre espace est distinct', (await dejaVu('refpay', '0xabc')) === false)

  verifie('KV : le TTL est transmis', [...kv.ttls.values()][0] === 86400, [...kv.ttls.values()])
}

{
  const kv = fauxKv()
  poserMemoire({ MEMOIRE: kv })

  verifie('KV : un compteur absent vaut zero', (await compteur('debit', '64000000')) === 0)
  verifie('KV : incrementer rend un', (await incrementer('debit', '64000000', 86400)) === 1)
  verifie('KV : puis deux', (await incrementer('debit', '64000000', 86400)) === 2)
  verifie('KV : et le compteur suit', (await compteur('debit', '64000000')) === 2)
}

{
  const kv = fauxKv()
  poserMemoire({ MEMOIRE: kv })

  /*
  LE CAS EXACT QUI A ÉCHOUÉ EN PRODUCTION : la naissance d'une demande,
  écrite par la consigne et relue par la vérification.
  */
  verifie('KV : une demande inconnue rend null', (await lireValeur('demande', 'VX-FFA3DRSG')) === null)
  await marquerValeur('demande', 'VX-FFA3DRSG', JSON.stringify({ ne: 1_760_000_000_000 }))
  const relu = await lireValeur('demande', 'VX-FFA3DRSG')
  verifie('KV : la naissance se relit', relu?.ne === 1_760_000_000_000, relu)

  /*
  LE PLANCHER DE SOIXANTE SECONDES. KV refuse une expiration plus courte,
  et une écriture refusée serait un oubli — exactement ce qu'on corrige.
  */
  await marquer('court', 'x', 5)
  verifie('KV : un TTL trop court est releve a 60', kv.ttls.get('court:x') === 60, kv.ttls.get('court:x'))
}

{
  const kv = fauxKv()
  poserMemoire({ MEMOIRE: kv })
  await marquerValeur('demande', 'VX-ABIME', 'ceci n est pas du JSON')
  verifie('KV : une valeur illisible rend null, sans lever', (await lireValeur('demande', 'VX-ABIME')) === null)
}

// ─── Sans KV : le secours doit se comporter pareil ──────────────────

/*
LE SECOURS N'EST PAS UNE SECONDE IMPLÉMENTATION.

Le jour où la liaison KV manque — oubliée à la création, supprimée par
mégarde — le relais doit se DÉGRADER, pas changer de comportement. Les
mêmes appels doivent rendre les mêmes choses.

`caches` n'existe pas sous Node : les primitives tombent alors dans leur
`catch` et rendent « rien ». C'est précisément le mode dégradé, et le
vérifier ici a une valeur — il prouve qu'aucun appel ne LÈVE quand le
magasin manque. Une exception ici ferait répondre « indisponible » à une
vente parfaitement valide.
*/
{
  poserMemoire({})

  let leve = false
  try {
    await marquer('txid', '0x123', 86400)
    await dejaVu('txid', '0x123')
    await compteur('debit', '64000000')
    await incrementer('debit', '64000000', 86400)
    await marquerValeur('demande', 'VX-X', '{"ne":1}')
    await lireValeur('demande', 'VX-X')
  } catch (e) {
    leve = true
    console.log(`   (exception : ${e?.message})`)
  }
  verifie('SANS KV : aucun appel ne leve', leve === false)
  verifie('SANS KV : une marque absente rend false, pas une erreur', (await dejaVu('txid', '0xzzz')) === false)
  verifie('SANS KV : un compteur rend zero', (await compteur('debit', 'inconnu')) === 0)
  verifie('SANS KV : une valeur absente rend null', (await lireValeur('demande', 'inconnue')) === null)
}

// ─── Verdict ────────────────────────────────────────────────────────

console.log(`\n${passes} verifications passees, ${echecs} en echec`)
if (echecs > 0) process.exit(1)
console.log('✅ La memoire se comporte pareil avec KV et sans.')
console.log('\n⚠️  CE QUI N’EST PAS VERIFIE ICI : que KV soit bien LIE au Worker.')
console.log('   Seul /sante le dit, et il faut le lire apres chaque deploiement.')
