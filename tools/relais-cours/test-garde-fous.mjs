/*
═══════════════════════════════════════════════════════════════════════════
LES GARDE-FOUS DE /change/ordre, EXÉCUTÉS
═══════════════════════════════════════════════════════════════════════════

    node tools/relais-cours/test-garde-fous.mjs

Ce fichier appelle `ordreChange` pour de vrai, avec un faux `caches` et un
faux `fetch`. Rien ne sort sur le réseau, et rien n'est relu à l'oeil.

POURQUOI PAS UNE RELECTURE. Parce que ces contrôles forment une CHAÎNE, et
que l'erreur qu'on y commet n'est jamais dans un contrôle isolé : elle est
dans leur ORDRE. Un plafond vérifié après l'envoi Telegram ne plafonne
rien. Un marquage posé avant l'envoi brûle la preuve de quelqu'un dont la
demande n'est jamais partie. Un numéro comparé avant d'être normalisé se
contourne en ajoutant une espace.

Aucune de ces trois fautes ne lève d'erreur, et aucune ne se voit en
relisant les contrôles un par un — il faut faire passer une demande dans
la chaîne entière et regarder ce qui en sort.
═══════════════════════════════════════════════════════════════════════════
*/

import { ordreChange, numeroComparable } from './worker.js'

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

// ─── Le décor : un cache en mémoire, un Telegram en papier ──────────

/**
 * UN FAUX CACHE, mais avec la MÊME sémantique que celui de Cloudflare :
 * on y met des Request et on en sort des Response. Un faux plus simple —
 * une Map de chaînes — laisserait passer une confusion entre la clé et sa
 * valeur, qui est exactement le genre de faute qu'on cherche.
 */
function faireCache() {
  const sac = new Map()
  return {
    sac,
    api: {
      default: {
        async match(req) {
          const v = sac.get(req.url)
          return v === undefined ? undefined : new Response(v)
        },
        async put(req, rep) {
          sac.set(req.url, await rep.text())
        },
      },
    },
  }
}

let envois = []
let telegramEchoue = false

function installer(cache) {
  globalThis.caches = cache.api
  envois = []
  globalThis.fetch = async (url) => {
    const u = String(url)
    if (u.includes('api.telegram.org')) {
      envois.push(u)
      if (telegramEchoue) {
        return new Response(JSON.stringify({ ok: false, description: 'chat not found' }), {
          status: 400,
        })
      }
      return new Response(JSON.stringify({ ok: true }), { status: 200 })
    }
    // Toute chaîne interrogée répond « rien trouvé » : les ventes de ce
    // fichier ressortent donc `absent`, ce qui est voulu — on teste les
    // garde-fous, pas la lecture des chaînes (test-verification.mjs le fait).
    if (u.includes('trongrid')) {
      return new Response(JSON.stringify({ data: [] }), { status: 200 })
    }
    if (u.includes('binance')) {
      return new Response(JSON.stringify({ price: '1.08' }), { status: 200 })
    }
    return new Response('{}', { status: 200 })
  }
}

const ENV = {
  TG_CHANGE_TOKEN: 'jeton-de-test',
  TG_CHANGE_CHAT: '-100123',
  CHANGE_MIN: '5000',
  CHANGE_MAX: '50000',
  CHANGE_ADRESSES: '{"USDT":"TQn9Y2khDD95J42FQtQTdwVVRZqjGBCvpM"}',
}

let compteurRef = 0
function demande(extra) {
  compteurRef += 1
  return {
    reference: `VX-TEST${String(compteurRef).padStart(4, '0')}`,
    sens: 'achat',
    monnaie: 'USDT',
    montantFcfa: '10 000',
    montantCrypto: '16',
    taux: '625 FCFA',
    marge: '25 FCFA/$',
    telephone: '70123456',
    referencePaiement: `MP${compteurRef}`,
    ...(extra || {}),
  }
}

async function poster(corps, env, cache) {
  installer(cache || faireCache())
  const requete = new Request('https://relais.vaultex/change/ordre', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(corps),
  })
  const rep = await ordreChange(requete, { ...ENV, ...(env || {}) })
  return { statut: rep.status, corps: await rep.json() }
}

// ─── Les bornes, appliquées ICI et pas seulement dans le téléphone ──

{
  /*
  LE CONTRÔLE QUI MANQUAIT. Le plafond était servi par /change/parametres
  et vérifié par l'application. Une requête postée à la main sur cette
  adresse publique passait cinq millions de francs sans rien rencontrer.
  */
  const r = await poster(demande({ montantFcfa: '5 000 000' }))
  verifie('BORNE : un montant au-dessus du plafond est refuse', r.statut === 400, r)
  verifie('BORNE : la raison nomme le plafond', String(r.corps.raison).includes('50000'), r.corps)
  verifie('BORNE : et rien ne part sur Telegram', envois.length === 0, envois.length)
}

{
  const r = await poster(demande({ montantFcfa: '500' }))
  verifie('BORNE : sous le minimum est refuse', r.statut === 400, r)
  verifie('BORNE : rien ne part', envois.length === 0, envois.length)
}

{
  const r = await poster(demande({ montantFcfa: 'beaucoup' }))
  verifie('BORNE : un montant illisible est refuse', r.statut === 400, r)
}

{
  /*
  « 10 000 » AVEC UNE ESPACE FINE INSÉCABLE, telle que NumberFormat
  français la produit sur les ICU récents. `parseFloat` en ferait DIX, donc
  un refus « sous le minimum » sur une demande parfaitement valide.
  */
  const r = await poster(demande({ montantFcfa: '10 000' }))
  verifie('BORNE : l espace fine insecable est relue', r.statut === 200, r)
}

{
  const r = await poster(demande())
  verifie('NORMAL : une demande dans les bornes passe', r.statut === 200, r)
  verifie('NORMAL : et part sur Telegram', envois.length === 1, envois.length)
}

// ─── Le numéro, sur une vente ───────────────────────────────────────

{
  /*
  Sans numéro, le message disait « ENVOYER 5 000 FCFA au numero du
  client » — sans numéro. Le changeur n'avait aucun moyen de payer, et la
  crypto du client était déjà partie.
  */
  const r = await poster(demande({ sens: 'vente', telephone: '' }))
  verifie('VENTE : sans numero, refusee', r.statut === 400, r)
  verifie('VENTE : la raison le dit', String(r.corps.raison).includes('numero'), r.corps)
  verifie('VENTE : rien ne part', envois.length === 0, envois.length)
}

{
  const r = await poster(demande({ sens: 'vente', telephone: '70 12 34 56' }))
  verifie('VENTE : un numero avec des espaces passe', r.statut === 200, r)
}

{
  // Un achat n'a pas besoin du numéro : c'est le client qui paie.
  const r = await poster(demande({ sens: 'achat', telephone: '' }))
  verifie('ACHAT : sans numero, ca passe quand meme', r.statut === 200, r)
}

// ─── La liste noire ─────────────────────────────────────────────────

{
  const r = await poster(demande({ telephone: '70123456' }), { CHANGE_BLOQUES: '70123456' })
  verifie('LISTE NOIRE : le numero est refuse', r.statut === 403, r)
  verifie('LISTE NOIRE : rien ne part', envois.length === 0, envois.length)
  /*
  ON NE DIT PAS « TU ES BLOQUÉ ». Ça apprendrait au fraudeur qu'il lui
  suffit de changer de numéro. Une indisponibilité ordinaire ne lui
  apprend rien.
  */
  verifie(
    'LISTE NOIRE : la raison ne revele pas le blocage',
    !String(r.corps.raison).toLowerCase().includes('bloqu'),
    r.corps
  )
}

{
  /*
  LA FAUTE QU'ON CHERCHE ICI : comparer les numéros tels qu'ils sont
  écrits. « 70 12 34 56 » et « +226 70 12 34 56 » sont le même abonné ;
  comparés à l'identique, la liste noire se contourne en ajoutant une
  espace.
  */
  const r = await poster(
    demande({ telephone: '+226 70 12 34 56' }),
    { CHANGE_BLOQUES: '70123456' }
  )
  verifie('LISTE NOIRE : ne se contourne pas avec des espaces', r.statut === 403, r)

  const r2 = await poster(demande({ telephone: '70123456' }), { CHANGE_BLOQUES: ' 00226-70-12-34-56 ' })
  verifie('LISTE NOIRE : la liste elle-meme est normalisee', r2.statut === 403, r2)
}

{
  const r = await poster(demande({ telephone: '64000000' }), { CHANGE_BLOQUES: '70123456' })
  verifie('LISTE NOIRE : un autre numero passe', r.statut === 200, r)
}

// ─── La référence de paiement ───────────────────────────────────────

{
  /*
  LE CAS QUI JUSTIFIE CE MARQUAGE. Un seul vrai paiement Orange Money, dix
  demandes qui en citent la référence. Le changeur retrouve bien la ligne
  dans son relevé, chaque fois, et chaque fois elle est authentique.
  */
  const cache = faireCache()
  const r1 = await poster(demande({ referencePaiement: 'MP251008123456' }), {}, cache)
  verifie('REFPAY : la premiere passe', r1.statut === 200, r1)

  const r2 = await poster(demande({ referencePaiement: 'MP251008123456' }), {}, cache)
  verifie('REFPAY : la seconde est refusee', r2.statut === 409, r2)
  verifie('REFPAY : rien ne repart sur Telegram', envois.length === 0, envois.length)
}

{
  /*
  ═══════════════════════════════════════════════════════════════════
  LE MARQUAGE VIENT APRÈS L'ENVOI, ET C'EST TOUT L'ENJEU DE L'ORDRE
  ═══════════════════════════════════════════════════════════════════

  Posé avant, un échec de Telegram brûlerait la référence de paiement du
  client : sa demande n'est jamais arrivée, et il ne peut plus la
  redéposer. Il a payé, et il n'a aucun recours.
  */
  const cache = faireCache()
  telegramEchoue = true
  const r1 = await poster(demande({ referencePaiement: 'MP999' }), {}, cache)
  telegramEchoue = false
  verifie('ORDRE : un echec Telegram remonte', r1.statut === 502, r1)

  const r2 = await poster(demande({ referencePaiement: 'MP999' }), {}, cache)
  verifie('ORDRE : et ne brule pas la reference de paiement', r2.statut === 200, r2)
}

// ─── La limite par numéro ───────────────────────────────────────────

{
  const cache = faireCache()
  let dernier = null
  for (let i = 0; i < 3; i += 1) {
    dernier = await poster(demande(), { CHANGE_MAX_PAR_JOUR: '3' }, cache)
    verifie(`DEBIT : demande ${i + 1} sur 3 passe`, dernier.statut === 200, dernier)
  }
  const quatrieme = await poster(demande(), { CHANGE_MAX_PAR_JOUR: '3' }, cache)
  verifie('DEBIT : la quatrieme est refusee', quatrieme.statut === 429, quatrieme)
  verifie(
    'DEBIT : la raison nomme la limite',
    String(quatrieme.corps.raison).includes('3'),
    quatrieme.corps
  )
}

{
  // Un autre numéro a son propre compteur : la limite est par abonné, pas
  // globale — sinon un client bavard fermerait le service aux autres.
  const cache = faireCache()
  await poster(demande({ telephone: '70123456' }), { CHANGE_MAX_PAR_JOUR: '1' }, cache)
  const autre = await poster(demande({ telephone: '64000000' }), { CHANGE_MAX_PAR_JOUR: '1' }, cache)
  verifie('DEBIT : le compteur est par numero', autre.statut === 200, autre)
}

{
  /*
  LE COMPTEUR NE COMPTE QUE LES DEMANDES ABOUTIES. Sinon un échec de
  Telegram consommerait le quota de quelqu'un d'honnête — et il
  n'aurait plus qu'à attendre demain pour redéposer une demande qu'il a
  déjà payée.
  */
  const cache = faireCache()
  telegramEchoue = true
  await poster(demande(), { CHANGE_MAX_PAR_JOUR: '1' }, cache)
  telegramEchoue = false
  const apres = await poster(demande(), { CHANGE_MAX_PAR_JOUR: '1' }, cache)
  verifie('DEBIT : un echec Telegram ne consomme pas le quota', apres.statut === 200, apres)
}

// ─── Le plafond de première opération ───────────────────────────────

{
  const cache = faireCache()
  const gros = await poster(
    demande({ montantFcfa: '40 000' }),
    { CHANGE_MAX_PREMIER: '10000' },
    cache
  )
  verifie('PREMIER : un gros montant sur un numero inconnu est refuse', gros.statut === 400, gros)
  verifie(
    'PREMIER : la raison explique quoi faire',
    String(gros.corps.raison).includes('premier'),
    gros.corps
  )

  const petit = await poster(
    demande({ montantFcfa: '8 000' }),
    { CHANGE_MAX_PREMIER: '10000' },
    cache
  )
  verifie('PREMIER : un petit montant passe', petit.statut === 200, petit)

  // Et maintenant que le numéro est connu, le gros montant passe.
  const ensuite = await poster(
    demande({ montantFcfa: '40 000' }),
    { CHANGE_MAX_PREMIER: '10000' },
    cache
  )
  verifie('PREMIER : une fois connu, le plafond ordinaire s applique', ensuite.statut === 200, ensuite)
}

{
  // Non réglé, il ne fait rien : c'est ce qui le rend éteint par défaut.
  const r = await poster(demande({ montantFcfa: '40 000' }))
  verifie('PREMIER : eteint par defaut', r.statut === 200, r)
}

// ─── Le dédoublonnage de la référence de demande, intact ────────────

{
  const cache = faireCache()
  const corps = demande()
  const r1 = await poster(corps, {}, cache)
  verifie('REFERENCE : la premiere passe', r1.statut === 200, r1)
  const r2 = await poster(corps, {}, cache)
  verifie('REFERENCE : un renvoi ne cree pas de doublon', r2.corps.deja === true, r2)
  verifie('REFERENCE : et ne reposte pas sur Telegram', envois.length === 0, envois.length)
}

// ─── La normalisation des numéros ───────────────────────────────────

verifie('NUMERO : huit chiffres', numeroComparable('70123456') === '70123456')
verifie('NUMERO : espaces retires', numeroComparable('70 12 34 56') === '70123456')
verifie('NUMERO : indicatif retire', numeroComparable('+226 70 12 34 56') === '70123456')
verifie('NUMERO : prefixe 00226 retire', numeroComparable('0022670123456') === '70123456')
verifie('NUMERO : tirets retires', numeroComparable('70-12-34-56') === '70123456')
verifie('NUMERO : trop court reste tel quel', numeroComparable('7012') === '7012')
verifie('NUMERO : vide', numeroComparable('') === '')
verifie('NUMERO : null', numeroComparable(null) === '')

// ─── Verdict ────────────────────────────────────────────────────────

console.log(`\n${passes} verifications passees, ${echecs} en echec`)
if (echecs > 0) process.exit(1)
console.log('✅ Les garde-fous tiennent, et dans le bon ordre.')
