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

import relais, { ordreChange, numeroComparable, verifierAchat } from './worker.js'

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
/*
LE CORPS DES ENVOIS, ET PAS SEULEMENT LEUR ADRESSE.

`envois` ne gardait que l'URL appelée, ce qui suffit pour compter les
messages — c'est tout ce qu'on en demandait. Mais le TEXTE du message
voyage dans le corps de la requête, et le chercher dans l'URL ne trouve
jamais rien : un test écrit comme ça serait vert sans rien vérifier.
*/
let corpsEnvoyes = []
let telegramEchoue = false

function installer(cache) {
  globalThis.caches = cache.api
  envois = []
  corpsEnvoyes = []
  globalThis.fetch = async (url, options) => {
    const u = String(url)
    if (u.includes('api.telegram.org')) {
      envois.push(u)
      corpsEnvoyes.push(String(options?.body ?? ''))
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
    /*
    L'ADRESSE EST DANS LA DEMANDE PAR DEFAUT, parce que le defaut de ce
    fixture est un ACHAT — et qu'un achat sans adresse est desormais
    refuse : le changeur n'aurait nulle part ou envoyer.

    Les cas qui veulent eprouver ce refus la retirent explicitement, ce
    qui se lit mieux qu'une absence qu'il faut deviner.
    */
    adresse: 'TQn9Y2khDD95J42FQtQTdwVVRZqjGBCvpM',
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

// ─── Le robot SMS, de bout en bout ──────────────────────────────────

/*
═══════════════════════════════════════════════════════════════════════════
LA CHAÎNE ENTIÈRE : UN SMS ENTRE, UN ACHAT SE VÉRIFIE
═══════════════════════════════════════════════════════════════════════════

test-sms.mjs prouve que le TEXTE est bien lu. Ici on prouve que ce qui en
est lu rejoint la demande du client — ce qui est une autre affaire, et
celle où les deux moitiés peuvent ne pas se parler : une clé de
rapprochement écrite d'un côté avec le numéro normalisé et de l'autre sans,
et plus rien ne se retrouve. Sans erreur, sans trace.
═══════════════════════════════════════════════════════════════════════════
*/

import { recevoirSms, verifierVente, marquerValeur, recevoirOtp, marqueOtp, nettoyerIa, systemeAssistance, reponseConnue, contactsVaultex, equipeVaultex } from './worker.js'

const ENV_SMS = { CHANGE_SMS_TOKEN: 'jeton-du-robot' }

async function posterSms(texte, env, cache, expediteur, jeton) {
  installer(cache)
  const requete = new Request('https://relais.vaultex/change/sms', {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      'x-vaultex-sms': jeton === undefined ? 'jeton-du-robot' : jeton,
    },
    body: JSON.stringify({ expediteur: expediteur ?? 'OrangeMoney', texte }),
  })
  const rep = await recevoirSms(requete, { ...ENV, ...ENV_SMS, ...(env || {}) })
  return { statut: rep.status, corps: await rep.json() }
}

{
  /*
  LE CONTRÔLE LE PLUS IMPORTANT DE CE FICHIER.

  Sans jeton, ce point d'entrée serait pire que l'absence de
  vérification : n'importe qui déclarerait ses propres encaissements et
  les achats passeraient en ✅ VÉRIFIÉ. On aurait transformé une
  incertitude en fausse certitude.
  */
  const r = await posterSms('Vous avez recu 10000 FCFA de 70123456', {}, faireCache(), 'OrangeMoney', '')
  verifie('SMS : sans jeton, refuse', r.statut === 401, r)

  const r2 = await posterSms('Vous avez recu 10000 FCFA de 70123456', {}, faireCache(), 'OrangeMoney', 'mauvais')
  verifie('SMS : mauvais jeton, refuse', r2.statut === 401, r2)
}

{
  // Sans CHANGE_SMS_TOKEN réglé, le mécanisme n'existe pas — et le dit,
  // au lieu de laisser le robot poster dans le vide.
  installer(faireCache())
  const requete = new Request('https://relais.vaultex/change/sms', {
    method: 'POST',
    headers: { 'content-type': 'application/json', 'x-vaultex-sms': 'x' },
    body: JSON.stringify({ expediteur: 'OrangeMoney', texte: 'recu 1000 FCFA de 70123456' }),
  })
  const rep = await recevoirSms(requete, ENV)
  verifie('SMS : non configure, le dit', rep.status === 503, rep.status)
}

{
  const r = await posterSms(
    'Vous avez recu 10000 FCFA de 70123456',
    {},
    faireCache(),
    'Pub-Promo'
  )
  verifie('SMS : un expediteur inconnu est ecarte', r.statut === 400, r)
}

{
  const r = await posterSms('Votre solde est de 125000 FCFA', {}, faireCache())
  verifie('SMS : un format non reconnu est rendu tel quel', r.statut === 422, r)
  verifie('SMS : et le texte revient pour diagnostic', String(r.corps.texte).includes('solde'), r.corps)
}

{
  /*
  LE RAPPROCHEMENT PAR MONTANT ET NUMÉRO — le cas du client qui ne
  recopie rien.
  */
  const cache = faireCache()
  const sms = await posterSms('Vous avez recu 10000 FCFA de 70123456', {}, cache)
  verifie('CHAINE : le SMS est accepte', sms.statut === 200, sms)

  const r = await poster(
    demande({ sens: 'achat', telephone: '70123456', montantFcfa: '10 000', referencePaiement: '' }),
    ENV_SMS,
    cache
  )
  verifie('CHAINE : l achat est transmis', r.statut === 200, r)
  verifie('CHAINE : et marque verifie', r.corps.verification === 'confirme', r.corps)
}

{
  // Et par référence, quand le client la recopie.
  const cache = faireCache()
  await posterSms('Vous avez recu 10000 FCFA de 70123456 Ref: MP251010999', {}, cache)
  const r = await poster(
    demande({ sens: 'achat', telephone: '70123456', montantFcfa: '10 000', referencePaiement: 'MP251010999' }),
    ENV_SMS,
    cache
  )
  verifie('CHAINE : rapprochement par reference', r.corps.verification === 'confirme', r.corps)
}

{
  /*
  LA RÉFÉRENCE COÏNCIDE, LE MONTANT NON.

  Un client recopie la référence d'un vrai paiement de mille francs et
  dépose une demande de quarante mille. Sans recomparer le montant LU DANS
  LE SMS, le relais répondrait ✅ sur la seule coïncidence de référence.
  */
  const cache = faireCache()
  await posterSms('Vous avez recu 1000 FCFA de 70123456 Ref: MP777', {}, cache)
  const r = await poster(
    demande({ sens: 'achat', telephone: '70123456', montantFcfa: '40 000', referencePaiement: 'MP777' }),
    ENV_SMS,
    cache
  )
  verifie('CHAINE : un montant qui ne correspond pas n est pas verifie',
    r.corps.verification === 'absent', r.corps)
  verifie('CHAINE : mais la demande part quand meme', r.statut === 200, r)
}

{
  /*
  ═══════════════════════════════════════════════════════════════════
  UN ENCAISSEMENT NE SERT QU'UNE FOIS — ET DEUX BARRIÈRES LE DISENT
  ═══════════════════════════════════════════════════════════════════

  Sinon un seul vrai paiement de dix mille francs justifie dix demandes
  de dix mille : le SMS existe, il est authentique, et il répond ✅ à
  chacune.

  MON PREMIER TEST ICI ATTENDAIT LA MAUVAISE BARRIÈRE. Il renvoyait la
  même référence de paiement, et c'est le dédoublonnage des références
  (409, rien n'est posté) qui l'arrêtait — plus tôt dans la chaîne, et
  c'est mieux ainsi. Le `deja_servi` de verifierAchat ne se voit donc que
  quand le client ne recopie AUCUNE référence : le rapprochement se fait
  alors sur montant et numéro, et c'est cet encaissement-là qui doit être
  marqué comme consommé.

  Les deux cas sont vérifiés, parce qu'ils passent par deux codes
  différents.
  ═══════════════════════════════════════════════════════════════════
  */
  const cache = faireCache()
  await posterSms('Vous avez recu 10000 FCFA de 70123456 Ref: MP888', {}, cache)
  const r1 = await poster(
    demande({ sens: 'achat', telephone: '70123456', montantFcfa: '10 000', referencePaiement: 'MP888' }),
    ENV_SMS,
    cache
  )
  verifie('REJEU : la premiere est verifiee', r1.corps.verification === 'confirme', r1.corps)

  // Même référence : arrêtée plus tôt, par le dédoublonnage des
  // références de paiement. Rien ne repart sur Telegram.
  const r2 = await poster(
    demande({ sens: 'achat', telephone: '70123456', montantFcfa: '10 000', referencePaiement: 'MP888' }),
    ENV_SMS,
    cache
  )
  verifie('REJEU : meme reference, refusee des l entree', r2.statut === 409, r2)
  verifie('REJEU : et rien ne repart', envois.length === 0, envois.length)
}

{
  // Sans référence recopiée : le rapprochement se fait sur montant et
  // numéro, et c'est verifierAchat qui doit refuser le second passage.
  const cache = faireCache()
  await posterSms('Vous avez recu 10000 FCFA de 70123456', {}, cache)
  const r1 = await poster(
    demande({ sens: 'achat', telephone: '70123456', montantFcfa: '10 000', referencePaiement: '' }),
    ENV_SMS,
    cache
  )
  verifie('REJEU SANS REF : la premiere est verifiee', r1.corps.verification === 'confirme', r1.corps)

  const r2 = await poster(
    demande({ sens: 'achat', telephone: '70123456', montantFcfa: '10 000', referencePaiement: '' }),
    ENV_SMS,
    cache
  )
  verifie('REJEU SANS REF : la seconde est deja_servi', r2.corps.verification === 'deja_servi', r2.corps)
  verifie('REJEU SANS REF : la demande part quand meme, marquee', r2.statut === 200, r2)
}

{
  // Aucun SMS : l'achat part, marqué non vérifié. Il ne doit JAMAIS être
  // bloqué — le client a déjà payé.
  const r = await poster(
    demande({ sens: 'achat', telephone: '70123456', montantFcfa: '10 000' }),
    ENV_SMS,
    faireCache()
  )
  verifie('SANS SMS : l achat part quand meme', r.statut === 200, r)
  verifie('SANS SMS : marque absent', r.corps.verification === 'absent', r.corps)
}

{
  /*
  LE ROBOT NON INSTALLÉ NE DOIT RIEN CHANGER.

  C'est la condition pour que la v16 ne casse pas le service d'un changeur
  qui n'a pas installé le robot : « on n'a pas regardé » n'est pas « il
  n'y a rien », et la consigne doit rester celle d'avant.
  */
  const r = await poster(
    demande({ sens: 'achat', telephone: '70123456', montantFcfa: '10 000' }),
    {},
    faireCache()
  )
  verifie('SANS ROBOT : l achat part', r.statut === 200, r)
  verifie('SANS ROBOT : marque indisponible', r.corps.verification === 'indisponible', r.corps)
}

// ─── Les adresses du changeur ───────────────────────────────────────

/*
═══════════════════════════════════════════════════════════════════════════
UNE ADRESSE DE LA MAUVAISE CHAINE COUTE LES FONDS D'UN CLIENT
═══════════════════════════════════════════════════════════════════════════

CHANGE_ADRESSES est affichee a chaque vendeur. Si « USDT » porte une
adresse Ethereum, quelqu'un y envoie de l'USDT TRC-20 et les fonds sont
perdus definitivement — on ne recupere pas des jetons TRON envoyes a une
adresse 0x.

Pour une seule touche de travers dans un tableau de bord, un soir, sur une
variable qu'on regle une fois et qu'on ne relit plus jamais.
═══════════════════════════════════════════════════════════════════════════
*/

import { trierAdresses, formeAttendue } from './worker.js'

const TRON_OK = 'TQn9Y2khDD95J42FQtQTdwVVRZqjGBCvpM'
const EVM_OK = '0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045'
const BTC_OK = 'bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq'

{
  const t = trierAdresses(JSON.stringify({ USDT: TRON_OK }))
  verifie('ADRESSES : une adresse TRON sous USDT est gardee', t.bonnes.USDT === TRON_OK, t)
  verifie('ADRESSES : rien de refuse', t.refusees.length === 0, t.refusees)
}

{
  /*
  LE CAS QUI JUSTIFIE TOUT CE BLOC. La premiere version n'exigeait qu'une
  longueur de vingt caracteres : cette adresse passait.
  */
  const t = trierAdresses(JSON.stringify({ USDT: EVM_OK }))
  verifie('ATTAQUE : une adresse EVM sous USDT est refusee', t.bonnes.USDT === undefined, t.bonnes)
  verifie('ATTAQUE : et la raison est rendue', t.refusees[0]?.attendu === 'tron', t.refusees)
}

{
  const t = trierAdresses(JSON.stringify({ 'USDT-BNB': TRON_OK, BNB: EVM_OK }))
  verifie('ADRESSES : TRON sous USDT-BNB refusee', t.bonnes['USDT-BNB'] === undefined, t.bonnes)
  verifie('ADRESSES : EVM sous BNB gardee', t.bonnes.BNB === EVM_OK, t.bonnes)
}

{
  const t = trierAdresses(JSON.stringify({ BTC: BTC_OK, USDT: TRON_OK }))
  verifie('ADRESSES : bech32 sous BTC gardee', t.bonnes.BTC === BTC_OK, t.bonnes)
  verifie('ADRESSES : les deux cohabitent', Object.keys(t.bonnes).length === 2, t.bonnes)
}

{
  // Tronquee : la faute de copier-coller la plus banale.
  const t = trierAdresses(JSON.stringify({ USDT: TRON_OK.slice(0, 30) }))
  verifie('ADRESSES : une adresse tronquee est refusee', t.bonnes.USDT === undefined, t.bonnes)
}

{
  /*
  L'ALPHABET BASE58 EXCLUT 0, O, I ET l — precisement les caracteres qui
  se confondent a l'oeil. Leur presence signale un copier-coller abime, ou
  une adresse recopiee a la main.
  */
  const t = trierAdresses(JSON.stringify({ USDT: 'T0n9Y2khDD95J42FQtQTdwVVRZqjGBCvpM' }))
  verifie('ADRESSES : un zero dans du base58 est refuse', t.bonnes.USDT === undefined, t.bonnes)
}

{
  // JSON invalide : AUCUNE lecture partielle. Une adresse a moitie lue est
  // une adresse vers laquelle des fonds partiraient sans retour.
  const t = trierAdresses('{"USDT": "T...",}')
  verifie('ADRESSES : un JSON invalide ne rend rien', Object.keys(t.bonnes).length === 0, t.bonnes)
  verifie('ADRESSES : et le signale', t.lisible === false, t)
}

{
  const t = trierAdresses(undefined)
  verifie('ADRESSES : non reglee rend vide et lisible', t.lisible === true && t.refusees.length === 0, t)
}

{
  // La cle est normalisee : « usdt » et « USDT » sont la meme monnaie.
  const t = trierAdresses(JSON.stringify({ ' usdt ': TRON_OK }))
  verifie('ADRESSES : la cle est normalisee', t.bonnes.USDT === TRON_OK, t.bonnes)
}

{
  // Monnaie inconnue : on retombe sur la longueur. Refuser tout ce qu'on
  // ne sait pas verifier fermerait le service a chaque nouveaute.
  const t = trierAdresses(JSON.stringify({ SOL: 'So11111111111111111111111111111111111111112' }))
  verifie('ADRESSES : une monnaie inconnue passe sur la longueur', t.bonnes.SOL !== undefined, t.bonnes)
}

verifie('FORME : USDT attend du tron', formeAttendue('USDT') === 'tron')
verifie('FORME : BNB attend de l evm', formeAttendue('BNB') === 'evm')
verifie('FORME : PI attend du stellar', formeAttendue('PI') === 'stellar')
verifie('FORME : une inconnue n attend rien', formeAttendue('DOGE') === null)

// ─── Le minimum par monnaie ─────────────────────────────────────────

/*
═══════════════════════════════════════════════════════════════════════════
LE RESEAU LE PLUS CHER NE DOIT PAS DEVENIR UN PIEGE
═══════════════════════════════════════════════════════════════════════════

Un seuil unique tenait tant qu'il n'y avait qu'une chaine. Avec plusieurs,
Ethereum prend entre douze et cent vingt-cinq pour cent d'une vente de
cinq mille francs — et l'utilisateur ne le decouvre qu'au moment de signer
dans son portefeuille.
═══════════════════════════════════════════════════════════════════════════
*/

import {
  minimumsChange,
  minimumPourMonnaie,
  minimumsUsdChange,
  plafondsUsdChange,
} from './worker.js'

const MINIMUMS = JSON.stringify({ USDT: 20000, 'USDT-BNB': 2000, 'USDT-ETH': 50000 })

verifie(
  'MINIMUM : chaque monnaie a le sien',
  minimumPourMonnaie('USDT', { CHANGE_MINIMUMS: MINIMUMS }) === 20000 &&
    minimumPourMonnaie('USDT-BNB', { CHANGE_MINIMUMS: MINIMUMS }) === 2000 &&
    minimumPourMonnaie('USDT-ETH', { CHANGE_MINIMUMS: MINIMUMS }) === 50000
)

verifie(
  'MINIMUM : la casse ne compte pas',
  minimumPourMonnaie('usdt-bnb', { CHANGE_MINIMUMS: MINIMUMS }) === 2000
)

/*
UNE MONNAIE ABSENTE RETOMBE SUR LE SEUIL GENERAL, et c'est ce qui rend
l'ajout d'une chaine sans risque : on applique le comportement d'avant au
lieu d'ouvrir un trou.
*/
verifie(
  'MINIMUM : une monnaie absente prend le general',
  minimumPourMonnaie('BTC', { CHANGE_MINIMUMS: MINIMUMS, CHANGE_MIN: '5000' }) === 5000
)

verifie(
  'MINIMUM : sans table, tout prend le general',
  minimumPourMonnaie('USDT', { CHANGE_MIN: '7000' }) === 7000
)

verifie(
  'MINIMUM : sans rien, 5000 par defaut',
  minimumPourMonnaie('USDT', {}) === 5000
)

/*
JSON ILLISIBLE : ON RETOMBE SUR LE SEUIL GENERAL, pas sur « aucun
minimum ». C'est l'inverse du choix fait pour les adresses, ou un JSON
casse rend un objet vide : la, ne rien rendre FERME le service ; ici, ca
l'ouvrirait sans garde-fou. On echoue du cote qui protege, dans les deux
cas.
*/
verifie(
  'MINIMUM : un JSON casse retombe sur le general',
  minimumPourMonnaie('USDT', { CHANGE_MINIMUMS: '{"USDT":}', CHANGE_MIN: '5000' }) === 5000
)

verifie(
  'MINIMUM : un seuil nul ou negatif est ecarte',
  minimumPourMonnaie('USDT', { CHANGE_MINIMUMS: '{"USDT":0}', CHANGE_MIN: '5000' }) === 5000 &&
    minimumPourMonnaie('USDT', { CHANGE_MINIMUMS: '{"USDT":-1}', CHANGE_MIN: '5000' }) === 5000
)

verifie('MINIMUM : un tableau n est pas une table', Object.keys(minimumsChange('[1,2]')).length === 0)

{
  /*
  ═══════════════════════════════════════════════════════════════════════
  UN ACHAT SANS ADRESSE N'A NULLE PART OU ALLER
  ═══════════════════════════════════════════════════════════════════════

  Le symetrique du numero exige sur une vente. Sans adresse, le message
  disait « ENVOYER 2,15 USDT-BNB a l'adresse ci-dessous » et la ligne
  d'adresse etait simplement omise : le client avait paye, le changeur
  n'avait nulle part ou envoyer, et c'est lui qui le decouvrait.

  Deux causes reelles, toutes deux dans le telephone : une phrase secrete
  devenue illisible, ou une monnaie absente de la table de derivation.
  L'ecran les attrape ; celui-ci attrape ce que l'ecran laisse passer.
  */
  const r = await poster(demande({ adresse: '' }), {}, faireCache())
  verifie('ACHAT : sans adresse de reception, c est refuse', r.statut === 400, r)
  verifie('ACHAT : la raison nomme l adresse',
    String(r.corps.raison).includes('adresse'), r.corps)
  verifie('ACHAT : et rien ne part', envois.length === 0, envois.length)
}

{
  // Une VENTE n'a pas d'adresse a fournir : c'est le client qui envoie.
  const r = await poster(
    demande({ sens: 'vente', adresse: '', montantCrypto: '16' }),
    {}, faireCache()
  )
  verifie('VENTE : l absence d adresse ne la bloque pas',
    !String(r.corps?.raison ?? '').includes('adresse'), r.corps)
}

{
  /*
  ET SURTOUT : IL EST APPLIQUE PAR LE RELAIS, pas seulement affiche.
  Un reglage que seul le client verifie n'est pas un reglage.
  */
  const r = await poster(
    demande({ monnaie: 'USDT-ETH', montantFcfa: '10 000' }),
    { CHANGE_MINIMUMS: MINIMUMS },
    faireCache()
  )
  verifie('MINIMUM : une vente ETH sous son seuil est refusee', r.statut === 400, r)
  verifie('MINIMUM : la raison nomme la monnaie', String(r.corps.raison).includes('USDT-ETH'), r.corps)
  verifie('MINIMUM : et rien ne part', envois.length === 0, envois.length)
}

/*
═══════════════════════════════════════════════════════════════════════════
LES BORNES EN DOLLARS, ET LE PIEGE DE LA CLE
═══════════════════════════════════════════════════════════════════════════

Celui-ci n'est pas theorique : le service a tourne des semaines avec
CHANGE_MINIMUMS_USD = {"BNB": 10} pendant que ses monnaies etaient
USDT-BNB et USDT. La recherche est une egalite EXACTE — « BNB » n'a jamais
rencontre « USDT-BNB » — donc la borne ne s'appliquait a rien, et personne
ne pouvait le deviner en lisant la variable : elle a l'air juste.

Le reste verifie que ces bornes sont appliquees PAR LE RELAIS. C'est la
meme lecon que pour le minimum en francs, et elles sont arrivees apres :
elles ne vivaient que dans le telephone.
═══════════════════════════════════════════════════════════════════════════
*/
verifie(
  'USD : la cle est la monnaie entiere, pas sa chaine',
  minimumsUsdChange({ CHANGE_MINIMUMS_USD: '{"BNB":10}' })['USDT-BNB'] === undefined &&
    minimumsUsdChange({ CHANGE_MINIMUMS_USD: '{"USDT-BNB":10}' })['USDT-BNB'] === 10
)

verifie(
  'USD : la casse et les espaces ne comptent pas',
  minimumsUsdChange({ CHANGE_MINIMUMS_USD: '{" usdt-bnb ":10}' })['USDT-BNB'] === 10 &&
    plafondsUsdChange({ CHANGE_PLAFONDS_USD: '{" usdt-eth ":50}' })['USDT-ETH'] === 50
)

verifie(
  'USD : un JSON casse ou nul n ouvre rien de plus',
  Object.keys(minimumsUsdChange({ CHANGE_MINIMUMS_USD: '{"USDT":}' })).length === 0 &&
    minimumsUsdChange({ CHANGE_MINIMUMS_USD: '{"USDT":0}' })['USDT'] === undefined
)

{
  /*
  DIX DOLLARS SUR LES TROIS USDT. L'USDT vaut un dollar par definition, donc
  le relais tranche sans appeler Binance — c'est ce qui rend ce test
  possible hors reseau, et c'est aussi ce qui le rend fiable en production.
  */
  const USD = JSON.stringify({ USDT: 10, 'USDT-BNB': 10, 'USDT-ETH': 10 })
  const r = await poster(
    demande({ monnaie: 'USDT-BNB', montantFcfa: '3 000', montantCrypto: '5' }),
    { CHANGE_MIN: '500', CHANGE_MINIMUMS_USD: USD },
    faireCache()
  )
  verifie('USD : 5 USDT sous un minimum de 10 $ est refuse', r.statut === 400, r)
  verifie('USD : la raison dit les dollars', String(r.corps.raison).includes('10 $'), r.corps)
  verifie('USD : et rien ne part', envois.length === 0, envois.length)
}

{
  // Juste au-dessus : elle passe les bornes. Le reste de la demande peut
  // echouer plus loin, mais pas ICI, et c'est tout ce qu'on mesure.
  const USD = JSON.stringify({ 'USDT-BNB': 10 })
  const r = await poster(
    demande({ monnaie: 'USDT-BNB', montantFcfa: '6 000', montantCrypto: '10' }),
    { CHANGE_MIN: '500', CHANGE_MINIMUMS_USD: USD },
    faireCache()
  )
  verifie(
    'USD : 10 USDT pour un minimum de 10 $ n est pas refuse par la borne',
    !String(r.corps?.raison ?? '').includes('minimum 10 $'),
    r.corps
  )
}

{
  /*
  LE JEU D'UN POUR MILLE. L'application calcule la crypto depuis les francs
  et arrondit le prix unitaire au franc : une demande qu'elle a montree
  comme valide peut arriver a 9,998. Refuser au franc pres ferait echouer,
  APRES l'envoi de la crypto, une demande que l'ecran venait d'accepter.
  */
  const USD = JSON.stringify({ 'USDT-BNB': 10 })
  const r = await poster(
    demande({ monnaie: 'USDT-BNB', montantFcfa: '6 000', montantCrypto: '9,998' }),
    { CHANGE_MIN: '500', CHANGE_MINIMUMS_USD: USD },
    faireCache()
  )
  verifie(
    'USD : 9,998 passe, l arrondi de l application ne doit pas couter une vente',
    !String(r.corps?.raison ?? '').includes('minimum 10 $'),
    r.corps
  )
}

{
  // Le plafond en dollars, meme regle, autre sens.
  const r = await poster(
    demande({ monnaie: 'USDT-BNB', montantFcfa: '30 000', montantCrypto: '50' }),
    { CHANGE_MIN: '500', CHANGE_PLAFONDS_USD: '{"USDT-BNB":20}' },
    faireCache()
  )
  verifie('USD : 50 USDT au-dessus d un plafond de 20 $ est refuse', r.statut === 400, r)
  verifie('USD : la raison dit le plafond', String(r.corps.raison).includes('plafond 20 $'), r.corps)
}

{
  // Le meme montant passe sur BNB Chain, dont les frais sont cent fois
  // plus bas. C'est tout l'interet du reglage.
  const r = await poster(
    demande({ monnaie: 'USDT-BNB', montantFcfa: '10 000' }),
    { CHANGE_MINIMUMS: MINIMUMS },
    faireCache()
  )
  verifie('MINIMUM : le meme montant passe sur BNB', r.statut === 200, r)
}

// ─── Le montant marqué, et l'attaque qu'il ferme ────────────────────

/*
═══════════════════════════════════════════════════════════════════════════
ON EXECUTE L'ATTAQUE, ON NE LA DECRIT PAS
═══════════════════════════════════════════════════════════════════════════

  Alice envoie 2 USDT a l'adresse du changeur et ne depose pas tout de
  suite sa demande. Bob surveille cette adresse — elle est publique. Il
  voit le versement, depose une demande pour 2 USDT avec SON numero.

Sans marquage, Bob est paye : le versement est reel, le contrat est le
bon, le montant correspond. Tout est vrai sauf l'identite du demandeur.

Les tests ci-dessous font passer Bob dans la chaine complete, avec le
versement d'Alice bien present sur la fausse chaine.
═══════════════════════════════════════════════════════════════════════════
*/

import { montantMarque, marquageActif, memeMontant } from './worker.js'

const SECRET = 'un-secret-de-test-assez-long-pour-passer'

verifie('MARQUAGE : eteint sans secret', marquageActif({}) === false)
verifie('MARQUAGE : eteint si le secret est trop court', marquageActif({ CHANGE_MARQUAGE: 'court' }) === false)
verifie('MARQUAGE : actif avec un vrai secret', marquageActif({ CHANGE_MARQUAGE: SECRET }) === true)

{
  const m = await montantMarque(2, 'VX-K7M2PQ4R', { CHANGE_MARQUAGE: SECRET })
  /*
  LE MONTANT MONTE, D'AU PLUS UNE UNITE.

  Le marquage occupe les trois decimales et s'ajoute a la partie entiere :
  le resultat est donc toujours SUPERIEUR a ce qui a ete demande, d'au
  plus 0,999. C'etait deja vrai avec six decimales — d'au plus 0,01 — mais
  l'ecart devient assez grand pour se voir, et pour compter.

  Consequence a ne pas perdre de vue : vendre TOUT son solde ne peut plus
  passer par un montant egal au solde. L'application doit le detecter, et
  c'est elle qui connait le solde.
  */
  verifie('MARQUAGE : le montant monte, d au plus une unite', m > 2 && m < 3, m)

  /*
  TROIS DECIMALES — ET MA PREMIERE VERSION DE CE TEST ETAIT FAUSSE.

  J'avais ecrit `Math.round(m * 1e6) === m * 1e6`. Sur 2,004933, le
  produit vaut 2004932,9999999998 : les deux cotes different, et le test
  echouait sur un montant parfaitement correct.

  C'est precisement la raison d'etre de memeMontant, qui arrondit des deux
  cotes au lieu de comparer des flottants. Mon propre test vient d'en
  faire la demonstration.
  */
  verifie(
    'MARQUAGE : trois decimales, pas une de plus',
    Math.abs(m * 1e3 - Math.round(m * 1e3)) < 1e-6,
    m
  )

  /*
  TAPABLE : c'est tout l'objet du passage de six decimales a trois. Un
  montant qu'on ne peut pas recopier sans erreur n'est pas une protection,
  c'est un obstacle — mesure sur cinq essais reels qui ont tous echoue la.
  */
  verifie('MARQUAGE : le montant tient en cinq caracteres', String(m).length <= 5, String(m))

  // DETERMINISTE : deux appels rendent le meme montant. Sans ca,
  // l'utilisateur verrait un chiffre a la confirmation et le relais en
  // chercherait un autre au depot.
  const m2 = await montantMarque(2, 'VX-K7M2PQ4R', { CHANGE_MARQUAGE: SECRET })
  verifie('MARQUAGE : deterministe', m === m2, [m, m2])

  // Deux references differentes donnent deux montants differents. C'est
  // toute la protection.
  const autre = await montantMarque(2, 'VX-P3QW8NZD', { CHANGE_MARQUAGE: SECRET })
  verifie('MARQUAGE : deux references, deux montants', m !== autre, [m, autre])

  // Et le meme secret est indispensable : qui ne l'a pas ne peut pas
  // predire le marquage.
  const autreSecret = await montantMarque(2, 'VX-K7M2PQ4R', { CHANGE_MARQUAGE: SECRET + 'x' })
  verifie('MARQUAGE : un autre secret donne un autre montant', m !== autreSecret, [m, autreSecret])
}

{
  const sans = await montantMarque(2, 'VX-K7M2PQ4R', {})
  verifie('MARQUAGE : sans secret, le montant ne bouge pas', sans === 2, sans)
}

{
  /*
  COMBIEN DE DEMANDES RESTENT DISTINGUABLES ?

  Mille, en theorie. On le MESURE plutot que de le supposer : si le modulo
  etait mal ecrit — 999 au lieu de 1000, un decalage de un — la moitie des
  valeurs pourrait manquer sans que rien ne le signale.

  Et ce chiffre ne protege que des demandes OUVERTES EN MEME TEMPS : deux
  demandes separees par une heure ne se confondent pas, la fenetre les
  separe.
  */
  const ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
  const vus = new Set()
  for (let i = 0; i < 400; i += 1) {
    const ref = 'VX-' + String(i).padStart(4, '0') + ALPHABET[i % 32]
    vus.add(await montantMarque(5, ref, { CHANGE_MARQUAGE: SECRET }))
  }
  /*
  QUATRE CENTS TIRAGES DANS MILLE CASES : le paradoxe des anniversaires
  predit environ 330 valeurs distinctes. Beaucoup moins signalerait un
  marquage qui n'emploie pas toute sa plage.
  */
  verifie('MARQUAGE : la plage est bien employee', vus.size > 280, vus.size)
  // Et tous restent dans l'unite au-dessus de la base.
  verifie('MARQUAGE : tous entre 5 et 6', [...vus].every((m) => m > 5 && m < 6))
}

{
  /*
  LE SOCLE EST TRONQUE A DEUX DECIMALES avant qu'on ajoute le marquage.
  Sinon les decimales du demandeur et les notres se melangent, et deux
  bases differentes peuvent tomber sur le meme total.
  */
  const a = await montantMarque(2.0, 'VX-SOCLE0001', { CHANGE_MARQUAGE: SECRET })
  const b = await montantMarque(2.0049, 'VX-SOCLE0001', { CHANGE_MARQUAGE: SECRET })
  verifie('MARQUAGE : le socle est tronque a deux decimales', a === b, [a, b])
}

verifie('MEME MONTANT : exact', memeMontant(2.004731, 2.004731))
verifie('MEME MONTANT : un millionieme suffit a differer', !memeMontant(2.004731, 2.004732))
verifie('MEME MONTANT : un pour cent ne passe plus', !memeMontant(2.0, 2.004731))

// ─── L'attaque, de bout en bout ─────────────────────────────────────

const ENV_MARQUE = { CHANGE_MARQUAGE: SECRET, CHANGE_MINIMUMS: '{"USDT":1}' }

/** Installe un faux TronGrid qui rend UN versement donne. */
function avecVersement(cache, montantRecu, quand) {
  installer(cache)
  const vrai = globalThis.fetch
  globalThis.fetch = async (url, options) => {
    if (String(url).includes('trongrid')) {
      return new Response(
        JSON.stringify({
          data: [
            {
              transaction_id: 'aa'.repeat(32),
              token_info: { address: 'TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t', decimals: 6, symbol: 'USDT' },
              from: 'TMuA6YqfCeX8EhbfYEg5y7S4DqzSJireY9',
              to: 'TQn9Y2khDD95J42FQtQTdwVVRZqjGBCvpM',
              value: String(Math.round(montantRecu * 1e6)),
              block_timestamp: quand,
            },
          ],
        }),
        { status: 200 }
      )
    }
    return vrai(url, options)
  }
}

const ENV_ADRESSE = { CHANGE_ADRESSES: '{"USDT":"TQn9Y2khDD95J42FQtQTdwVVRZqjGBCvpM"}' }

{
  /*
  ALICE : elle ouvre sa demande, PUIS envoie le montant marque qu'on lui
  a donne. C'est le parcours normal, et il doit aboutir.
  */
  const cache = faireCache()
  installer(cache)
  const refAlice = 'VX-ALICE001'
  const exact = await montantMarque(2, refAlice, ENV_MARQUE)

  // Son versement arrive APRES l'ouverture de sa demande.
  await marquerValeur('demande', refAlice, JSON.stringify({ ne: Date.now() - 60_000 }))
  avecVersement(cache, exact, Date.now())

  const r = await verifierVente({
    monnaie: 'USDT',
    montant: 2,
    reference: refAlice,
    env: { ...ENV_MARQUE, ...ENV_ADRESSE },
  })
  verifie('ALICE : son propre versement est reconnu', r.etat === 'confirme', r)
  verifie('ALICE : le montant lu est le montant marque', r.montant === exact, [r.montant, exact])
}

{
  /*
  BOB : le versement d'Alice est sur la chaine. Il depose une demande pour
  deux USDT avec SA reference. Le relais cherche SON montant marque, qui
  n'est pas celui d'Alice.

  C'EST LE TEST QUI JUSTIFIE TOUT CE FICHIER.
  */
  const cache = faireCache()
  installer(cache)
  const refAlice = 'VX-ALICE002'
  const refBob = 'VX-BOB00002'
  const exactAlice = await montantMarque(2, refAlice, ENV_MARQUE)
  const exactBob = await montantMarque(2, refBob, ENV_MARQUE)
  verifie('BOB : son marquage differe de celui d Alice', exactAlice !== exactBob, [exactAlice, exactBob])

  // Le versement d'Alice est bien la, et Bob ouvre sa demande apres.
  avecVersement(cache, exactAlice, Date.now() - 120_000)
  await marquerValeur('demande', refBob, JSON.stringify({ ne: Date.now() }))

  const r = await verifierVente({
    monnaie: 'USDT',
    montant: 2,
    reference: refBob,
    env: { ...ENV_MARQUE, ...ENV_ADRESSE },
  })
  verifie('BOB : ne peut pas reclamer le versement d Alice', r.etat === 'absent', r)
}

{
  /*
  BOB, SECONDE TENTATIVE : il annonce directement le montant exact
  d'Alice, qu'il a lu sur la chaine. Ca ne change rien — le relais ne se
  sert pas du montant qu'on lui donne, il le RECALCULE depuis la
  reference.
  */
  const cache = faireCache()
  installer(cache)
  const exactAlice = await montantMarque(2, 'VX-ALICE003', ENV_MARQUE)
  avecVersement(cache, exactAlice, Date.now() - 120_000)
  await marquerValeur('demande', 'VX-BOB00003', JSON.stringify({ ne: Date.now() - 300_000 }))

  const r = await verifierVente({
    monnaie: 'USDT',
    montant: exactAlice,
    reference: 'VX-BOB00003',
    env: { ...ENV_MARQUE, ...ENV_ADRESSE },
  })
  verifie('BOB : annoncer le montant d Alice ne sert a rien', r.etat === 'absent', r)
}

{
  /*
  L'ANTERIORITE SEULE, sans marquage : un versement anterieur a
  l'ouverture de la demande est ecarte. C'est la seconde barriere, et elle
  se teste separement parce qu'elle protege d'un attaquant qui aurait
  devine le marquage.
  */
  const cache = faireCache()
  installer(cache)
  const ref = 'VX-TARD0001'
  const exact = await montantMarque(2, ref, ENV_MARQUE)
  // Le versement precede l'ouverture de deux heures.
  avecVersement(cache, exact, Date.now() - 2 * 3600_000)
  await marquerValeur('demande', ref, JSON.stringify({ ne: Date.now() }))

  const r = await verifierVente({
    monnaie: 'USDT',
    montant: 2,
    reference: ref,
    env: { ...ENV_MARQUE, ...ENV_ADRESSE },
  })
  verifie('ANTERIORITE : un versement anterieur a la demande est ecarte', r.etat === 'absent', r)
}

{
  /*
  SANS MARQUAGE, LE COMPORTEMENT D'AVANT. Un relais deploye sans
  CHANGE_MARQUAGE doit se comporter comme la v22 : tolerance d'un pour
  cent, aucune anteriorite exigee. Sinon la mise a jour casserait le
  service de qui ne regle rien.
  */
  const cache = faireCache()
  installer(cache)
  avecVersement(cache, 2, Date.now() - 2 * 3600_000)
  const r = await verifierVente({
    monnaie: 'USDT',
    montant: 2,
    reference: 'VX-SANSMARQ',
    env: ENV_ADRESSE,
  })
  verifie('COMPATIBILITE : sans marquage, 2 USDT ronds passent', r.etat === 'confirme', r)
}

// ─── La plage de eth_getLogs ────────────────────────────────────────

/*
═══════════════════════════════════════════════════════════════════════════
LE DEFAUT QUE SEUL UN VRAI ESSAI POUVAIT MONTRER
═══════════════════════════════════════════════════════════════════════════

Les sept noeuds BSC ont repondu « limit exceeded » sur une vraie tentative.
Aucun point d'entree public gratuit n'accepte une plage de deux mille blocs
sur eth_getLogs.

Mes tests ne pouvaient pas le voir : ils nourrissent le code avec des
reponses que j'ecris, et une reponse forgee n'a pas de limite de plage.

Ceux-ci regardent ce que le relais DEMANDE, pas ce qu'on lui repond.
═══════════════════════════════════════════════════════════════════════════
*/

const ADRESSE_BNB = '0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045'
const ENV_BNB = { CHANGE_ADRESSES: JSON.stringify({ 'USDT-BNB': ADRESSE_BNB }) }

/** Installe un faux noeud EVM qui note les plages demandees. */
function avecNoeudEvm(cache, hauteur) {
  installer(cache)
  const plages = []
  globalThis.fetch = async (url, options) => {
    const corps = JSON.parse(options?.body ?? '{}')
    if (corps.method === 'eth_blockNumber') {
      return new Response(
        JSON.stringify({ jsonrpc: '2.0', id: 1, result: '0x' + hauteur.toString(16) }),
        { status: 200 }
      )
    }
    if (corps.method === 'eth_getLogs') {
      const f = Number(BigInt(corps.params[0].fromBlock))
      const t = Number(BigInt(corps.params[0].toBlock))
      plages.push({ de: f, a: t, largeur: t - f })
      return new Response(JSON.stringify({ jsonrpc: '2.0', id: 1, result: [] }), { status: 200 })
    }
    return new Response(JSON.stringify({ jsonrpc: '2.0', id: 1, result: null }), { status: 200 })
  }
  return plages
}

{
  const cache = faireCache()
  const plages = avecNoeudEvm(cache, 50_000_000)
  const ref = 'VX-PLAGE001'
  // La demande est nee il y a deux minutes.
  await marquerValeur('demande', ref, JSON.stringify({ ne: Date.now() - 2 * 60_000 }))

  const r = await verifierVente({
    monnaie: 'USDT-BNB',
    montant: 2,
    reference: ref,
    env: { ...ENV_BNB, CHANGE_MARQUAGE: SECRET },
  })
  verifie('PLAGE : rien trouve, et c est normal', r.etat === 'absent', r)
  verifie('PLAGE : au moins un appel a eth_getLogs', plages.length >= 1, plages)
  /*
  LE CONTROLE QUI AURAIT EVITE L'ECHEC REEL. Aucun appel ne doit depasser
  cinq cents blocs — c'est ce que les noeuds publics acceptent.
  */
  verifie(
    'PLAGE : aucun appel ne depasse 500 blocs',
    plages.every((p) => p.largeur <= 500),
    plages.map((p) => p.largeur)
  )
  /*
  ET LA PLAGE SUIT L'AGE DE LA DEMANDE. Deux minutes a 700 ms le bloc font
  environ 170 blocs, plus cinq minutes de marge : autour de 600. On
  verifie l'ordre de grandeur, pas le chiffre exact — le temps de bloc est
  une estimation volontairement basse.
  */
  const couvert = plages.reduce((n, p) => n + p.largeur, 0)
  verifie(
    'PLAGE : elle suit l age de la demande, pas 2000 blocs',
    couvert > 200 && couvert < 1200,
    couvert
  )
}

{
  // Sans naissance connue — marquage eteint — on retombe sur un quart
  // d'heure, borne par PLAGE_EVM_MAX_MS. Jamais sur deux mille blocs d'un
  // seul appel.
  const cache = faireCache()
  const plages = avecNoeudEvm(cache, 50_000_000)
  await verifierVente({ monnaie: 'USDT-BNB', montant: 2, env: ENV_BNB })
  verifie(
    'PLAGE : sans naissance, les tranches restent bornees',
    plages.length > 0 && plages.every((p) => p.largeur <= 500),
    plages.map((p) => p.largeur)
  )
}

{
  /*
  LE MOTIF D'ECHEC NOMME LA METHODE.

  « indisponible : limit exceeded » ne disait pas quel appel avait echoue,
  et c'etait precisement ce qu'il fallait savoir : la plage de getLogs, pas
  la lecture de la hauteur.
  */
  const cache = faireCache()
  installer(cache)
  globalThis.fetch = async () =>
    new Response(
      JSON.stringify({ jsonrpc: '2.0', id: 1, error: { message: 'limit exceeded' } }),
      { status: 200 }
    )
  const r = await verifierVente({ monnaie: 'USDT-BNB', montant: 2, env: ENV_BNB })
  verifie('MOTIF : indisponible', r.etat === 'indisponible', r)
  verifie('MOTIF : il nomme la methode', String(r.raison).includes('eth_'), r.raison)
}

// ─── Verdict ────────────────────────────────────────────────────────


/*
═══════════════════════════════════════════════════════════════════════════
LA CHAINE COMPLETE, AVEC LE VRAI FORMAT D'ORANGE MONEY BURKINA
═══════════════════════════════════════════════════════════════════════════

Les tests de chaine ci-dessus emploient un SMS que j'avais invente :
« Vous avez recu 10000 FCFA de 70123456 ». Il est propre, et c'est le
probleme — un vrai SMS d'operateur ne l'est pas.

Celui-ci est reel, chiffres remplaces, tout le reste verbatim. Il apporte
quatre choses que le format invente n'avait pas :

  — « 100000.0 » : une DECIMALE, la ou je lisais des milliers. Ce seul
    point valait un facteur dix sur le montant credite ;
  — « Frais:  FCFA, Taxe:  FCFA » : des unites sans nombre devant ;
  — un SOLDE du meme ordre de grandeur, dans la meme phrase ;
  — un lien publicitaire plein de chiffres a la fin.

ON LE FAIT PASSER DANS LA CHAINE ENTIERE, et pas seulement dans le
lecteur : c'est le rapprochement qui compte. Le montant lu doit servir de
CLE, et une troncature appliquee d'un cote mais pas de l'autre ferait
echouer tous les achats sans rien lever.
═══════════════════════════════════════════════════════════════════════════
*/
{
  const SMS_REEL =
    'Vous avez recu 20000.0 FCFA, Frais:  FCFA, Taxe:  FCFA du 74936353,HAMIDOU. ' +
    'Le solde de votre compte est de 100484.4 FCFA Trans ID: PP261013.2118.60776882. ' +
    'Flashez le QR CODE marchand avec Max it pour plus de facilite : https://onelink.to/nn64xw'

  const cache = faireCache()
  const sms = await posterSms(SMS_REEL, {}, cache)
  verifie('REEL : le SMS d Orange est accepte', sms.statut === 200, sms)
  verifie('REEL : le montant lu est 20000, pas 200000', sms.corps?.lu?.montant === 20000, sms.corps?.lu)
  verifie('REEL : le numero lu est celui de l expediteur', sms.corps?.lu?.numero === '74936353', sms.corps?.lu)

  /*
  LE RAPPROCHEMENT PAR MONTANT ET NUMERO, sur ce SMS-la. C'est le cas du
  client qui ne recopie aucune reference — le plus frequent.
  */
  const r = await poster(
    demande({
      sens: 'achat',
      telephone: '74936353',
      montantFcfa: '20 000',
      montantCrypto: '32',
      referencePaiement: '',
    }),
    ENV_SMS,
    cache
  )
  verifie('REEL : l achat est transmis', r.statut === 200, r)
  verifie('REEL : et marque verifie', r.corps.verification === 'confirme', r.corps)
}

{
  /*
  ET LE MEME SMS NE DOIT PAS PAYER UNE DEMANDE PLUS GROSSE.

  C'est le controle qui compte le plus ici : avec l'ancienne lecture,
  « 20000.0 » valait DEUX CENT MILLE, et une demande de 200 000 FCFA
  aurait ete confirmee par un paiement de vingt mille.

  Le relais compare le montant LU DANS LE SMS, jamais celui que la demande
  annonce — ce test le fige.
  */
  const SMS_REEL =
    'Vous avez recu 20000.0 FCFA, Frais:  FCFA, Taxe:  FCFA du 74936353,HAMIDOU. ' +
    'Le solde de votre compte est de 100484.4 FCFA Trans ID: PP261013.2118.60776883.'

  const cache = faireCache()
  await posterSms(SMS_REEL, {}, cache)

  const r = await poster(
    demande({
      sens: 'achat',
      telephone: '74936353',
      montantFcfa: '200 000',
      montantCrypto: '320',
      referencePaiement: '',
    }),
    { ...ENV_SMS, CHANGE_MAX: '500000' },
    cache
  )
  verifie('REEL : une demande dix fois trop grosse N EST PAS confirmee',
    r.corps.verification !== 'confirme', r.corps)
}

{
  /*
  LE SOLDE NE DOIT PAS PAYER LA DEMANDE.

  Le SMS porte « 100484.4 FCFA » comme solde du compte. Une demande de ce
  montant exact ne doit pas etre confirmee : ce chiffre n'est pas un
  encaissement, c'est ce que le changeur possede.
  */
  const SMS_REEL =
    'Vous avez recu 20000.0 FCFA, Frais:  FCFA, Taxe:  FCFA du 74936353,HAMIDOU. ' +
    'Le solde de votre compte est de 100484.4 FCFA Trans ID: PP261013.2118.60776884.'

  const cache = faireCache()
  await posterSms(SMS_REEL, {}, cache)

  const r = await poster(
    demande({
      sens: 'achat',
      telephone: '74936353',
      montantFcfa: '100 484',
      montantCrypto: '160',
      referencePaiement: '',
    }),
    { ...ENV_SMS, CHANGE_MAX: '500000' },
    cache
  )
  verifie('REEL : le solde du changeur ne paie aucune demande',
    r.corps.verification !== 'confirme', r.corps)
}


/*
═══════════════════════════════════════════════════════════════════════════
L'OTP INVERSE : LE CLIENT PROUVE QU'IL CONTROLE SON NUMERO
═══════════════════════════════════════════════════════════════════════════

LE TROU QU'IL BOUCHE. Sur une vente, le client tape le numero ou il veut
recevoir ses francs. Un chiffre de travers, et le changeur envoie l'argent
a un inconnu — irreversiblement. Rien n'empechait ca.

Le client envoie sa reference par SMS au numero du changeur. Le robot voit
le numero d'expediteur TEL QUE L'OPERATEUR LE DONNE, pas tel qu'il a ete
tape dans un formulaire.
═══════════════════════════════════════════════════════════════════════════
*/

/*
LE TEXTE DU MESSAGE EST DANS LE CORPS, PAS DANS L'URL.

Ma première version de ce lecteur cherchait dans `envois`, qui ne contient
que des adresses. Elle ne trouvait rien — et un test écrit comme ça serait
resté vert le jour où le texte aurait vraiment disparu.

C'est le piège de tous les tests d'absence : ils passent aussi quand on
regarde au mauvais endroit.
*/
function textesEnvoyes() {
  /*
  LE CORPS EST EN `x-www-form-urlencoded`, PAS EN JSON.

  « chat_id=-100…&text=%F0%9F%92%B1+DEMANDE… ». `URLSearchParams` le lit
  correctement, y compris le « + » qui code une espace — ce que
  `decodeURIComponent` seul ne fait PAS, et c'est l'erreur que j'ai
  commise avant celle-ci.
  */
  return corpsEnvoyes.map((c) => new URLSearchParams(c).get('text') ?? c)
}

async function posterOtp(reference, numero, cache, jeton) {
  installer(cache)
  const requete = new Request('https://relais.vaultex/change/otp', {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      'x-vaultex-sms': jeton === undefined ? 'jeton-du-robot' : jeton,
    },
    body: JSON.stringify({ reference, numero }),
  })
  const rep = await recevoirOtp(requete, { ...ENV, ...ENV_SMS })
  return { statut: rep.status, corps: await rep.json() }
}

{
  const cache = faireCache()
  const r = await posterOtp('VX-ABCD1234', '70123456', cache)
  verifie('OTP : une confirmation est acceptee', r.statut === 200 && r.corps.confirme === true, r)

  // La meme, rejouee : deja connue, et ca concorde.
  const r2 = await posterOtp('VX-ABCD1234', '70123456', cache)
  verifie('OTP : la meme confirmation ne change rien', r2.corps.deja === true && r2.corps.concorde === true, r2)
}

{
  /*
  ═════════════════════════════════════════════════════════════════════
  L'ATTAQUE QUE CE MECANISME DOIT ARRETER
  ═════════════════════════════════════════════════════════════════════

  La reference s'affiche a l'ecran, elle se dit a voix haute, elle se
  photographie. Quelqu'un qui la voit pourrait envoyer SON numero pour
  cette reference-la, et detourner les francs du client.

  LE PREMIER POSE TIENT. C'est la meme reponse que l'anteriorite sur les
  ventes, par le meme moyen.
  ═════════════════════════════════════════════════════════════════════
  */
  const cache = faireCache()
  await posterOtp('VX-VICTIME1', '70111111', cache)
  const voleur = await posterOtp('VX-VICTIME1', '70999999', cache)
  verifie('OTP : un second numero ne remplace pas le premier', voleur.corps.concorde === false, voleur)
  /*
  ET LE NUMERO MEMORISE N'EST PAS RENDU. Qui poste ici detient le jeton du
  robot, mais ca ne lui donne pas a LIRE les numeros des clients d'une
  reference qu'il aurait devinee.
  */
  verifie(
    'OTP : le numero memorise ne ressort pas',
    !JSON.stringify(voleur.corps).includes('70111111'),
    voleur.corps
  )
}

{
  const cache = faireCache()
  const sansJeton = await posterOtp('VX-ABCD1234', '70123456', cache, '')
  verifie('OTP : sans jeton, refuse', sansJeton.statut === 401, sansJeton)
  const mauvais = await posterOtp('VX-ABCD1234', '70123456', cache, 'mauvais')
  verifie('OTP : mauvais jeton, refuse', mauvais.statut === 401, mauvais)
}

{
  const cache = faireCache()
  verifie('OTP : une reference trop courte est refusee',
    (await posterOtp('VX-1', '70123456', cache)).statut === 400)
  verifie('OTP : un numero trop court est refuse',
    (await posterOtp('VX-ABCD1234', '701', cache)).statut === 400)
  verifie('OTP : un numero vide est refuse',
    (await posterOtp('VX-ABCD1234', '', cache)).statut === 400)
}

{
  /*
  LA REFERENCE EST NORMALISEE COMME PARTOUT AILLEURS. Le client la recopie
  a la main : il met ou non le tiret, l'ecrit ou non en minuscules.
  */
  const cache = faireCache()
  await posterOtp('VX-ABCD1234', '70123456', cache)
  const variante = await posterOtp('vx abcd1234', '70123456', cache)
  verifie('OTP : tiret, casse et espaces ne changent rien',
    variante.corps.deja === true && variante.corps.concorde === true, variante)
}

{
  /*
  LA CHAINE COMPLETE : le numero confirme doit apparaitre sur le message
  du changeur, parce que c'est lui qui recopie les huit chiffres.
  */
  const cache = faireCache()
  await posterOtp('VX-TESTOTP1', '70123456', cache)
  const r = await poster(
    demande({ sens: 'vente', reference: 'VX-TESTOTP1', telephone: '70123456', monnaie: 'USDT' }),
    ENV_SMS,
    cache
  )
  verifie('OTP : la vente passe', r.statut === 200, r)
  verifie('OTP : le message dit que le numero est confirme',
    textesEnvoyes().some((t) => t.includes('confirme par SMS')), textesEnvoyes())
}

{
  /*
  ET LE CAS ALARMANT : la reference a ete confirmee depuis un AUTRE
  numero que celui que la demande annonce. C'est soit une faute de frappe,
  soit une tentative de detournement — et dans les deux cas le changeur
  doit le voir AVANT de taper huit chiffres.
  */
  const cache = faireCache()
  await posterOtp('VX-TESTOTP2', '70111111', cache)
  const r = await poster(
    demande({ sens: 'vente', reference: 'VX-TESTOTP2', telephone: '70999999', monnaie: 'USDT' }),
    ENV_SMS,
    cache
  )
  verifie('OTP : la vente passe quand meme', r.statut === 200, r)
  verifie('OTP : mais le message CRIE qu un autre numero a confirme',
    textesEnvoyes().some((t) => t.includes('UN AUTRE NUMERO')), textesEnvoyes())
}

{
  /*
  SANS ROBOT INSTALLE, RIEN N'EST ECRIT. Un avertissement permanent sur
  toutes les ventes d'un changeur qui n'a pas de robot finirait par ne
  plus etre lu — et emousserait celui qui compte.
  */
  verifie('OTP : inactif n ecrit rien', marqueOtp('inactif') === '')
  /*
  « NON CONFIRME » NE SE DIT PLUS NON PLUS, et ce test a change de sens.

  Il verifiait que l'absence de confirmation s'annonce. C'etait juste tant
  qu'une confirmation etait possible : le client envoyait sa reference par
  SMS au numero du changeur, ce qui prouvait qu'il detenait la ligne.

  L'application ne l'envoie plus. La mention s'imprimait donc sur CHAQUE
  message, pour toujours, et demandait au changeur une verification que
  personne ne pouvait plus fournir — sous une ligne « ✅ VERSEMENT
  VERIFIE SUR LA CHAINE », qu'elle venait contredire.

  On dit ce qui est prouve, on ne commente pas ce qui ne l'est pas. Le cas
  'oui' reste : si le robot revient, une confirmation reelle a de la
  valeur.
  */
  verifie('OTP : non confirme ne dit plus rien', marqueOtp('non') === '')
  verifie('OTP : confirme porte une coche', marqueOtp('oui').includes('✔'))
}


/*
═══════════════════════════════════════════════════════════════════════════
L'ASSISTANCE NE DOIT JAMAIS ECRIRE OU PAYER
═══════════════════════════════════════════════════════════════════════════

Le systeme le lui interdit. Ca ne suffit pas, et c'est tout l'objet de ces
tests : une consigne est une preference, pas une garantie. Quelqu'un qui
insiste, qui joue un role, ou simplement un modele qui se trompe, et
l'adresse sort.

CE QUE CA COUTERAIT. Une adresse inventee par un modele est plausible et
fausse : l'argent part, irreversiblement, avec l'autorite d'une reponse
officielle. Et quelqu'un qui saurait amener le modele a ecrire SA propre
adresse aurait transforme notre assistance en outil de detournement.

On remplace plutot qu'on ne refuse : un refus sec laisserait le client
sans reponse a une question legitime — « ou j'envoie ? ». Le renvoi vers
l'ecran Change y repond, et c'est la bonne reponse.
═══════════════════════════════════════════════════════════════════════════
*/
{
  const EVM = '0xbde137a9684f0bc437294b2cf2ab7295831e327e'
  const TRON = 'TPsJsu5nHeZQWdF5YHJ6shTfaN8yix2Fbh'

  const r1 = nettoyerIa(`Envoie tes USDT a ${EVM} et tu recevras tes francs.`)
  verifie('IA : une adresse EVM ne sort pas', !r1.includes(EVM), r1)
  verifie('IA : et le client est renvoye a l ecran', r1.includes('ecran Change'), r1)

  const r2 = nettoyerIa(`Pour TRON utilise ${TRON}`)
  verifie('IA : une adresse TRON ne sort pas', !r2.includes(TRON), r2)

  const r3 = nettoyerIa('Appelle le 70123456 pour payer.')
  verifie('IA : un numero de telephone ne sort pas', !r3.includes('70123456'), r3)

  /*
  LES MONTANTS DOIVENT PASSER. Un filtre qui masquerait « 50000 » rendrait
  l'assistance incapable de repondre a « c'est quoi le plafond » — donc
  inutile pour la moitie des questions qu'on lui pose.
  */
  const r4 = nettoyerIa('Le minimum est 600 FCFA et le plafond 50000 FCFA.')
  verifie('IA : les montants passent', r4.includes('600') && r4.includes('50000'), r4)
  const r5 = nettoyerIa('Tu peux echanger entre 5 000 et 500 000 FCFA.')
  verifie('IA : les montants a separateurs passent', r5.includes('500 000'), r5)

  verifie('IA : une reponse ordinaire n est pas touchee',
    nettoyerIa('Le delai est de 10 minutes.') === 'Le delai est de 10 minutes.')
  verifie('IA : rien du tout ne plante pas', nettoyerIa(null) === '' && nettoyerIa(undefined) === '')
}

{
  /*
  LE SYSTEME PORTE LES VRAIS CHIFFRES, ET PAS L'ADRESSE.

  Un modele a qui l'on ne donne pas la marge en invente une plausible, et
  le client arrive chez le changeur avec un prix qu'on ne lui fera pas.

  Mais on ne lui donne PAS l'adresse : il n'en a pas besoin pour repondre,
  et ne pas la lui donner est la premiere des deux barrieres.
  */
  const sys = systemeAssistance({
    CHANGE_MIN: '600',
    CHANGE_MAX: '50000',
    CHANGE_DELAI: '10',
    CHANGE_ADRESSES: '{"USDT-BNB":"0xbde137a9684f0bc437294b2cf2ab7295831e327e"}',
    CHANGE_MINIMUMS: '{"USDT-BNB":600}',
  })
  verifie('IA : le systeme porte le minimum reel', sys.includes('600'), sys.slice(0, 200))
  verifie('IA : le systeme porte le delai reel', sys.includes('10 minutes'), sys.slice(0, 200))
  verifie('IA : le systeme nomme la monnaie reglee', sys.includes('USDT-BNB'))
  verifie(
    'IA : le systeme ne contient AUCUNE adresse',
    !sys.includes('0xbde137a9684f0bc437294b2cf2ab7295831e327e'),
    sys
  )
  verifie(
    'IA : et il interdit explicitement les phrases secretes',
    sys.includes('douze') || sys.includes('recuperation'),
    sys
  )

  /*
  ═════════════════════════════════════════════════════════════════════
  AUCUN CONSEIL D'INVESTISSEMENT, ET C'EST LA REGLE LA PLUS SERIEUSE
  ═════════════════════════════════════════════════════════════════════

  Un modele a qui l'on demande « est-ce que ca va monter » repond
  volontiers quelque chose de plausible. Dans une application d'argent,
  cette phrase devient un conseil recu de VaultEx — et quelqu'un y
  engagera des francs qu'il n'a pas de trop.

  Ce n'est pas une precaution juridique : c'est qu'une prevision est
  FAUSSE par nature, et qu'on n'a aucun droit de la faire payer a
  quelqu'un sous couvert d'assistance.
  ═════════════════════════════════════════════════════════════════════
  */
  verifie('IA : aucun conseil d investissement', sys.includes('CONSEIL D INVESTISSEMENT'), sys)
  verifie('IA : ni prevision ni « placement sur »',
    sys.includes('prevision') && sys.includes('sans risque'), sys)

  /*
  ET IL RESTE SUR SON SUJET. Sans cette borne, l'assistance repond aux
  devoirs des enfants et a la meteo — chaque fois au prix d'un appel
  paye, et chaque fois en donnant d'une application d'argent l'image
  d'un jouet.

  LA BORNE A CHANGE DE FORME, PAS D'EXISTENCE. Elle disait « VaultEx, le
  change, les bases de la crypto, et rien d autre » : lue par un modele,
  cette phrase est un interrupteur, et il se declarait hors sujet au
  moindre doute. Elle nomme maintenant ce qui est EXCLU — actualite,
  sante, devoirs, politique, vie privee — ce qui borne aussi bien sans
  fermer le reste.
  */
  verifie('IA : ce qui est hors sujet est nomme',
    sys.includes("CE QUI N'EST PAS TON SUJET"), sys.slice(0, 200))
  verifie('IA : les devoirs et la meteo en font partie',
    sys.includes('devoirs') && sys.includes('politique'), null)
}


/*
═══════════════════════════════════════════════════════════════════════════
LES REPONSES CONNUES PASSENT AVANT LE MODELE
═══════════════════════════════════════════════════════════════════════════

Ce n'est pas un repli, c'est le bon ordre. Les questions qu'on pose a une
assistance de change ne sont pas variees — le minimum, le delai, les
frais, quel reseau, « ou est mon argent » — et leurs reponses sont dans
les reglages du Worker, exactes par construction.

Y repondre la coute RIEN, arrive INSTANTANEMENT, et ne peut pas se
tromper. Les faire passer par un modele payant serait payer pour un
risque d'erreur.

CONSEQUENCE, PAS INTENTION, mais elle compte : le jour ou la cle manque,
ou le credit est epuise, ou le modele est injoignable, les questions les
plus frequentes trouvent quand meme leur reponse.
═══════════════════════════════════════════════════════════════════════════
*/
{
  const ENV_IA = {
    CHANGE_MIN: '600',
    CHANGE_MAX: '50000',
    CHANGE_DELAI: '10',
    CHANGE_MINIMUMS: '{"USDT":20000,"USDT-BNB":600}',
    CHANGE_ADRESSES: '{"USDT-BNB":"0xbde137a9684f0bc437294b2cf2ab7295831e327e"}',
  }

  /*
  LES CHIFFRES VIENNENT DES REGLAGES, pas d'un texte fige. Si le changeur
  monte son minimum, la reponse monte avec — sinon l'assistance annoncerait
  un service qui n'existe plus, avec l'autorite d'une reponse officielle.
  */
  const min = reponseConnue('cest quoi le minimum', ENV_IA)
  verifie('CONNUE : le minimum est reconnu', min !== null)
  verifie('CONNUE : et il porte le chiffre REGLE', min.includes('600'), min)
  verifie('CONNUE : par monnaie, les deux sont cites', min.includes('20 000'), min)

  const delai = reponseConnue('ca prend combien de temps', ENV_IA)
  verifie('CONNUE : le delai est reconnu et REGLE', delai?.includes('10 minutes'), delai)

  verifie('CONNUE : les frais sont reconnus', reponseConnue('vous prenez combien', ENV_IA) !== null)
  verifie('CONNUE : le reseau est reconnu', reponseConnue('quel reseau choisir', ENV_IA) !== null)
  verifie('CONNUE : « jai envoye et rien recu » est reconnu',
    reponseConnue('jai envoye mais jai rien recu', ENV_IA) !== null)

  /*
  LA QUESTION LA PLUS IMPORTANTE DE TOUTES. « On me demande ma phrase
  secrete » doit trouver une reponse SANS passer par un modele, sans
  dependre d'une cle, sans dependre du credit. C'est la seule ou une
  seconde d'hesitation coute un portefeuille entier.
  */
  const phrase = reponseConnue('le support me demande ma phrase secrete', ENV_IA)
  verifie('CONNUE : la phrase secrete est reconnue', phrase !== null, phrase)
  verifie('CONNUE : et la reponse dit JAMAIS', phrase.includes('JAMAIS'), phrase)

  /*
  CE QUI N'EST PAS RECONNU DESCEND AU MODELE. Un filet trop large
  repondrait a cote — et une reponse a cote est pire qu'un renvoi : elle a
  l'air d'une reponse.
  */
  verifie('CONNUE : une salutation n est pas une question', reponseConnue('bonjour ca va', ENV_IA) === null)
  verifie('CONNUE : une question hors sujet descend au modele',
    reponseConnue('est ce que je peux acheter du bitcoin avec western union', ENV_IA) === null)
  verifie('CONNUE : rien du tout rend null',
    reponseConnue('', ENV_IA) === null && reponseConnue('ok', ENV_IA) === null)

  /*
  LES ACCENTS ET LA CASSE NE DOIVENT PAS GENER : on tape vite, sur un
  telephone, et « Délai » comme « DELAI » designent la meme question.
  */
  verifie('CONNUE : les accents ne genent pas', reponseConnue('quel est le Délai ?', ENV_IA) !== null)
  verifie('CONNUE : les majuscules non plus', reponseConnue('MINIMUM', ENV_IA) !== null)

  /*
  ═════════════════════════════════════════════════════════════════════
  LES QUESTIONS PROPOSEES PAR L'ECRAN DOIVENT ABOUTIR
  ═════════════════════════════════════════════════════════════════════

  L'ecran d'assistance propose quatre questions toutes faites. Elles sont
  copiees ici MOT POUR MOT depuis strings.xml.

  Pourquoi ce test existe : proposer une question a laquelle on ne sait
  pas repondre est pire que ne rien proposer. On invite quelqu'un a
  appuyer, et on lui rend « je n'ai pas la reponse a celle-la ». Tant
  qu'il n'y a pas de cle d'API, ce sont meme les SEULES qui aboutissent.

  Si quelqu'un reformule un libelle dans strings.xml sans toucher aux
  mots reconnus, ce test tombe — et c'est exactement ce qu'on veut, parce
  que rien d'autre ne le signalerait.
  ═════════════════════════════════════════════════════════════════════
  */
  /*
  ═════════════════════════════════════════════════════════════════════
  LES QUESTIONS QUE LES GENS POSENT VRAIMENT
  ═════════════════════════════════════════════════════════════════════

  Ecrites comme on les tape : sans accents, sans ponctuation, avec les
  tournures qu'on emploie a l'oral. Un motif qui ne reconnait que la
  forme bien ecrite ne reconnait rien.

  CHACUNE DOIT ABOUTIR SANS CLE. Tant qu'il n'y a pas d'API, ce sont les
  seules qui repondent — et c'est la difference entre une assistance
  utile et une assistance qui dit « je ne sais pas » a tout le monde.
  ═════════════════════════════════════════════════════════════════════
  */
  const VRAIES = [
    'comment je fais pour vendre mes usdt',
    'je veux acheter des usdt comment faire',
    'je peux payer avec orange money',
    'cest quoi le taux aujourdhui',
    'je me suis trompe de montant comment annuler',
    'jai change de telephone comment recuperer mon compte',
    'mon solde affiche zero',
    'on me demande mes 12 mots',
  ]
  for (const q of VRAIES) {
    verifie(`VRAIE : « ${q} » trouve sa reponse`, reponseConnue(q, ENV_IA) !== null, q)
  }

  /*
  ET CE QUI RESTE HORS SUJET DOIT DESCENDRE AU MODELE. Un filet qui
  attrape tout repondrait a cote, et une reponse a cote a l'air d'une
  reponse — c'est pire qu'un aveu d'ignorance.
  */
  verifie('HORS SUJET : une question de logistique descend au modele',
    reponseConnue('est ce que vous livrez a bobo dioulasso', ENV_IA) === null)
  verifie('HORS SUJET : une salutation aussi',
    reponseConnue('bonjour', ENV_IA) === null)

  const PROPOSEES = [
    "C'est quoi le minimum pour échanger ?",
    'Combien de temps ça prend ?',
    'Quel réseau je dois choisir ?',
    "On me demande ma phrase secrète, c'est normal ?",
    "Comment joindre l'équipe VaultEx ?",
  ]
  for (const q of PROPOSEES) {
    verifie(`PROPOSEE : « ${q} » trouve sa reponse`, reponseConnue(q, ENV_IA) !== null, q)
  }
}

/*
LE MÊME DÉCOR QUE LE BLOC CI-DESSUS, mais déclaré au niveau du fichier :
les blocs qui suivent sont séparés, et `const` ne traverse pas un bloc.
Un nom distinct plutôt qu'un ENV_IA de plus — deux constantes homonymes
dans un même fichier finissent par ne plus porter les mêmes réglages.
*/
const ENV_EQUIPE = {
  CHANGE_MIN: '600',
  CHANGE_MAX: '50000',
  CHANGE_DELAI: '10',
  CHANGE_MINIMUMS: '{"USDT":20000,"USDT-BNB":600}',
  CHANGE_ADRESSES: '{"USDT-BNB":"0xbde137a9684f0bc437294b2cf2ab7295831e327e"}',
}

/*
═══════════════════════════════════════════════════════════════════════════
LES CANAUX OFFICIELS ET L'ÉQUIPE
═══════════════════════════════════════════════════════════════════════════

Un modèle à qui l'on demande « comment je vous joins » et qui ne le sait
pas invente une adresse plausible. Une adresse de support fausse écrite
par nous est le début d'une arnaque dont nous serions l'auteur.

Ce qui est éprouvé ici : que les trois canaux sortent, qu'ils soient les
VRAIS, que l'équipe sorte avec ses postes — et surtout l'ORDRE, parce que
c'est là qu'est le piège.
═══════════════════════════════════════════════════════════════════════════
*/
{
  const r = reponseConnue('comment je peux vous contacter', ENV_EQUIPE)
  verifie('CONTACT : la question trouve une reponse', r !== null)
  verifie('CONTACT : le whatsapp y est', String(r).includes('+22672306526'), r)
  verifie('CONTACT : le telegram y est', String(r).includes('t.me/vaultexCommunity'), r)
  verifie('CONTACT : l email y est', String(r).includes('vaultexteams@gmail.com'), r)
  /*
  ET L'AVERTISSEMENT VOYAGE AVEC. Donner trois canaux sans dire qu'aucun
  ne demandera la phrase secrète, c'est apprendre à faire confiance à un
  canal — ce qui est exactement ce qu'un escroc exploite ensuite.
  */
  verifie('CONTACT : l avertissement sur la phrase secrete suit',
    String(r).toLowerCase().includes('phrase secrete'), r)
}

{
  /*
  ═══════════════════════════════════════════════════════════════════════
  LES FORMES VERBALES, PARCE QUE C'EST CE QU'ON TAPE
  ═══════════════════════════════════════════════════════════════════════

  MESURÉ SUR UN VRAI ÉCRAN : « qui a fondé Vaultex ? » ne trouvait rien.
  J'avais listé « fondateur » — le NOM — et personne n'écrit « qui est le
  fondateur » quand « qui a fondé » est plus court.

  La leçon dépasse ce cas : une liste de mots-clés écrite depuis la
  réponse attrape le vocabulaire de celui qui RÉPOND, pas de celui qui
  DEMANDE. Les phrases ci-dessous sont celles qu'on pose vraiment.
  ═══════════════════════════════════════════════════════════════════════
  */
  const FORMULATIONS = [
    'tu connais moumine',
    'qui a fondé Vaultex ?',
    'qui est le fondateur',
    'qui a créé VaultEx',
    'c est qui le patron de vaultex',
    'vaultex appartient a qui',
    'qui dirige vaultex',
    'qui est derriere vaultex',
    'parle moi de l equipe',
    'qui est rasmane',
    'abdul hack sakande c est qui',
  ]
  for (const q of FORMULATIONS) {
    const r = reponseConnue(q, ENV_EQUIPE)
    verifie(`FORMULATION : « ${q} » trouve l equipe`,
      r !== null && String(r).includes('Koanda Moumine'), r)
  }
}

{
  const r = reponseConnue('qui est le directeur marketing de vaultex', ENV_EQUIPE)
  verifie('EQUIPE : la question trouve une reponse', r !== null)
  for (const nom of ['Koanda Moumine', 'Rasmane', 'Sakande']) {
    verifie(`EQUIPE : ${nom} y est`, String(r).includes(nom), r)
  }
  for (const poste of ['Fondateur', 'Directeur Marketing', 'Directeur Commercial']) {
    verifie(`EQUIPE : le poste « ${poste} » y est`, String(r).includes(poste), r)
  }
}

{
  /*
  ═══════════════════════════════════════════════════════════════════════
  LE PIÈGE DE L'ORDRE — LE SEUL CONTRÔLE DANGEREUX DE CE BLOC
  ═══════════════════════════════════════════════════════════════════════

  `reponseConnue` rend la PREMIÈRE entrée dont un mot apparaît, et
  l'entrée « contact » reconnaît le mot « support ». Or « le support m'a
  demande ma phrase secrete » contient « support ».

  Placée trop haut, elle aurait répondu « voici comment nous joindre » à
  quelqu'un en train de se faire arnaquer. C'est pour ça que les deux
  nouvelles entrées sont les DERNIÈRES de la liste, et c'est ce test qui
  le tient.
  ═══════════════════════════════════════════════════════════════════════
  */
  const pieges = [
    "le support m'a demande ma phrase secrete, c'est normal ?",
    'un agent du service client veut mes 12 mots',
    'on me contacte sur whatsapp pour ma phrase secrete',
  ]
  for (const q of pieges) {
    const r = String(reponseConnue(q, ENV_EQUIPE))
    verifie(`ORDRE : « ${q.slice(0, 34)}… » rend l avertissement, pas les contacts`,
      r.includes('escroc'), r)
  }
}

{
  /*
  LE NUMÉRO OFFICIEL SURVIT AU MASQUAGE, CELUI DU CHANGEUR NON.

  « 72306526 » est huit chiffres d'affilée, donc exactement ce que le
  masque attrape. Sans exception, l'assistance aurait répondu « ecris-nous
  au [numero masque] » — pire que pas de réponse, parce que ça a l'air
  d'une réponse.
  */
  const t = nettoyerIa(
    'Ecris-nous au 72306526 ou au +22672306526, mais pas au 70123456.',
    ENV_EQUIPE
  )
  verifie('MASQUE : le numero officiel en forme locale survit', t.includes('72306526'), t)
  verifie('MASQUE : en forme internationale aussi', t.includes('+22672306526'), t)
  verifie('MASQUE : un AUTRE numero reste masque',
    !t.includes('70123456') && t.includes('[numero masque]'), t)

  /*
  ET LE MASQUE N'EST PAS DEVENU PERMISSIF POUR AUTANT : c'est la seule
  raison pour laquelle ce bloc existe plutôt qu'un simple retrait de la
  règle.
  */
  const adresse = nettoyerIa('Envoie a 0x1234567890abcdef1234567890abcdef12345678', ENV_EQUIPE)
  verifie('MASQUE : une adresse EVM reste remplacee',
    !adresse.includes('0x1234567890'), adresse)
}

{
  /*
  L'ÉQUIPE EST RÉGLABLE SANS TOUCHER AU CODE, et un réglage mal tapé ne
  doit pas vider la fiche. Une entrée sans poste est ignoree ; si rien ne
  reste, on retombe sur la liste par defaut plutot que de repondre « notre
  equipe : ».
  */
  const regle = equipeVaultex({ CHANGE_EQUIPE: 'Ada Lovelace|Ingenieure; Alan Turing|Logicien' })
  verifie('EQUIPE REGLEE : deux entrees lues', regle.length === 2, regle)
  verifie('EQUIPE REGLEE : le poste suit le nom',
    regle[0].nom === 'Ada Lovelace' && regle[0].poste === 'Ingenieure', regle)

  const bancal = equipeVaultex({ CHANGE_EQUIPE: 'Ada Lovelace|Ingenieure; SansPoste' })
  verifie('EQUIPE REGLEE : l entree sans poste est ignoree', bancal.length === 1, bancal)

  const vide = equipeVaultex({ CHANGE_EQUIPE: '|||' })
  verifie('EQUIPE REGLEE : un reglage inutilisable retombe sur le defaut',
    vide.length === 3 && vide[0].nom === 'Koanda Moumine', vide)

  const contacts = contactsVaultex({ CHANGE_WHATSAPP: '+22600000000' })
  verifie('CONTACT REGLE : le whatsapp suit le reglage',
    contacts.whatsapp === '+22600000000', contacts)
  verifie('CONTACT REGLE : les autres gardent leur defaut',
    contacts.email === 'vaultexteams@gmail.com', contacts)
}

{
  /*
  LA CONSIGNE DU MODÈLE PORTE L'ÉQUIPE ET LES CANAUX — sinon il les
  invente, et c'est tout l'objet de ce travail.
  */
  const sys = systemeAssistance(ENV_EQUIPE)
  for (const attendu of [
    'Koanda Moumine', 'Rasmane', 'Sakande',
    'Fondateur', 'Directeur Marketing', 'Directeur Commercial',
    '+22672306526', 't.me/vaultexCommunity', 'vaultexteams@gmail.com',
  ]) {
    verifie(`CONSIGNE : « ${attendu} » est dans le prompt`, sys.includes(attendu), attendu)
  }
  verifie('CONSIGNE : elle interdit d inventer un poste',
    sys.includes('inventes aucun') || sys.includes("n'en inventes aucun"), null)
}

/*
═══════════════════════════════════════════════════════════════════════════
LA CONSIGNE DOIT DECRIRE L'APPLICATION, PAS SEULEMENT LE CHANGE
═══════════════════════════════════════════════════════════════════════════

« L'assistance n'est pas si intelligente, beaucoup de questions elle ne
peut pas y repondre » — signale apres usage, et c'etait exact.

LE MODELE N'ETAIT PAS EN CAUSE. On ne lui avait decrit QUE le service de
change. Tout le reste de l'application — envoyer, recevoir, echanger,
sauvegarder, le PIN, le carnet d'adresses — n'existait pas pour lui, donc
il repondait « ce n'est pas mon sujet » a des questions qui sont
exactement son sujet. Changer de modele n'aurait rien change.

Ces verifications tiennent le contenu de la consigne. Elles tomberaient
le jour ou quelqu'un l'allegerait sans voir ce qu'elle porte.
═══════════════════════════════════════════════════════════════════════════
*/
{
  const sys = systemeAssistance(ENV_EQUIPE)

  const ATTENDUS = [
    // Ce que l'application fait reellement, ecran par ecran.
    'NON CUSTODIAL', 'ENVOYER', 'RECEVOIR', 'Swap', 'HISTORIQUE',
    'CARNET', 'ALERTES', 'LANGUES',
    // Les chaines, parce qu'on les lui demande sans cesse.
    'Bitcoin', 'BNB Chain', 'TRON', 'Solana', 'Pi',
    // La securite, qui est la moitie des questions.
    'DOUZE MOTS', 'PIN DE PANIQUE', 'ANTI-PHISHING', 'biometrie',
  ]
  for (const attendu of ATTENDUS) {
    verifie(`APPLICATION : « ${attendu} » est decrit dans la consigne`,
      sys.includes(attendu), attendu)
  }

  /*
  ET LE PERIMETRE EST OUVERT, PAS FERME.

  L'ancienne consigne disait « VaultEx, le change, les bases de la crypto,
  et rien d autre ». Lue par un modele, cette phrase est un interrupteur :
  au moindre doute il se declare hors sujet.
  */
  verifie('PERIMETRE : la consigne nomme un sujet LARGE',
    sys.includes('TON SUJET EST LARGE'), null)
  verifie('PERIMETRE : elle interdit le refus par prudence',
    sys.includes('Ne refuse JAMAIS'), null)
  verifie('PERIMETRE : elle couvre les arnaques',
    sys.includes('ARNAQUES'), null)
  verifie('PERIMETRE : repondre passe AVANT renvoyer',
    sys.includes('TU REPONDS D ABORD'), null)
  verifie("PERIMETRE : l ancienne formule fermee a disparu",
    !sys.includes('et rien d autre'), null)
}

/*
═══════════════════════════════════════════════════════════════════════════
« LE CHANGEUR » EST UN MOT DE NOTRE CUISINE, PAS DU LEUR
═══════════════════════════════════════════════════════════════════════════

Pour celui qui ecrit a l'assistance, il n'y a pas un « changeur » quelque
part : il y a VaultEx. Le mot vient de notre organisation interne — c'est
celui qui detient les fonds et qui paie — et il avait fuite dans une
dizaine de reponses. Il sonne comme un intermediaire inconnu, sur un ecran
ou l'on vient justement chercher a qui on a affaire.

IL RESTE LEGITIME AILLEURS : dans le message Telegram, que le changeur lit
lui-meme, et dans l'ecran Change ou il designe la personne qui paie. Ce
test ne couvre QUE ce que l'assistance renvoie.
═══════════════════════════════════════════════════════════════════════════
*/
{
  const QUESTIONS = [
    'cest quoi le minimum', 'ca prend combien de temps', 'vous prenez combien',
    'quel reseau choisir', 'jai envoye et jai rien recu', 'comment vendre',
    'comment acheter', 'orange money', 'cest quoi le taux', 'je veux annuler',
    'jai un nouveau telephone', 'mon solde est faux', 'phrase secrete',
    'comment vous contacter', 'qui a fonde vaultex',
  ]
  for (const q of QUESTIONS) {
    const r = reponseConnue(q, ENV_EQUIPE)
    verifie(`VOCABULAIRE : « ${q} » ne dit pas « changeur »`,
      r !== null && !String(r).toLowerCase().includes('changeur'), r)
  }

  const sys = systemeAssistance(ENV_EQUIPE)
  verifie('VOCABULAIRE : la consigne interdit le mot',
    sys.includes("TU DIS « L'EQUIPE TECHNIQUE »"), null)
}

{
  /*
  ═══════════════════════════════════════════════════════════════════════
  L'ORDRE DES CANAUX
  ═══════════════════════════════════════════════════════════════════════

  WhatsApp d'abord : au Burkina Faso c'est l'application deja ouverte, la
  reponse y arrive en minutes, et elle marche sur une connexion faible.
  L'e-mail ensuite, parce qu'il accepte des captures et laisse une trace.
  Telegram en dernier, parce que c'est une COMMUNAUTE : y envoyer en
  premier quelqu'un qui a un probleme d'argent precis, c'est l'exposer.

  Donner les trois a plat laissait le modele choisir, et il choisissait au
  hasard. On verifie donc des POSITIONS, pas des presences.
  ═══════════════════════════════════════════════════════════════════════
  */
  const r = String(reponseConnue('comment vous contacter', ENV_EQUIPE))
  const wa = r.indexOf('+22672306526')
  const mail = r.indexOf('vaultexteams@gmail.com')
  const tg = r.indexOf('t.me/vaultexCommunity')
  verifie('ORDRE : les trois canaux sont la', wa >= 0 && mail >= 0 && tg >= 0, r)
  verifie('ORDRE : WhatsApp avant l e-mail', wa < mail, `wa=${wa} mail=${mail}`)
  verifie('ORDRE : l e-mail avant Telegram', mail < tg, `mail=${mail} tg=${tg}`)
  verifie('ORDRE : Telegram est annonce comme un groupe',
    r.includes('communaute') || r.includes('groupe'), r)

  const equipe = String(reponseConnue('qui a fonde vaultex', ENV_EQUIPE))
  verifie('ORDRE : la fiche equipe mene a WhatsApp en premier',
    equipe.indexOf('+22672306526') < equipe.indexOf('vaultexteams@gmail.com'), equipe)

  const sys = systemeAssistance(ENV_EQUIPE)
  verifie('ORDRE : la consigne nomme WhatsApp en premier',
    sys.includes('propose-le EN PREMIER'), null)
  verifie('ORDRE : et demande le canal en entier',
    sys.includes('donne-le en entier'), null)
}

{
  /*
  ═══════════════════════════════════════════════════════════════════════
  LES LIGNES VIDES SURVIVENT A LA CONSTRUCTION
  ═══════════════════════════════════════════════════════════════════════

  C'etait `.filter(Boolean)`, et une chaine vide est falsy : TOUTES les
  separations entre sections disparaissaient. La consigne arrivait au
  modele en un pave de cent lignes sans respiration.

  EXACTEMENT LA MEME FAUTE QUE DANS `messageOrdre`, ou elle a deja ete
  trouvee et corrigee. Un test par endroit, parce qu'une lecon apprise
  ailleurs ne protege pas celui-ci.
  ═══════════════════════════════════════════════════════════════════════
  */
  const sys = systemeAssistance(ENV_EQUIPE)
  const vides = sys.split('\n').filter((l) => l === '').length
  verifie('CONSIGNE : les lignes vides separent les sections', vides >= 8, vides)

  /*
  MAIS LE NUL, LUI, DOIT TOUJOURS DISPARAITRE. La ligne des minimums par
  monnaie vaut `null` quand il n'y en a pas : filtrer « tout sauf le
  vide » sans exclure le nul ecrirait « null » dans la consigne, et le
  modele le lirait comme un fait.
  */
  const sansMinimums = systemeAssistance({})
  verifie('CONSIGNE : aucun « null » ne fuit quand une section manque',
    !sansMinimums.includes('null'), null)
}

/*
═══════════════════════════════════════════════════════════════════════════
LA VÉRIFICATION DE L'ACHAT, VUE DEPUIS LE TÉLÉPHONE
═══════════════════════════════════════════════════════════════════════════

Le verdict existait déjà, mais il n'allait qu'au changeur. Il part
maintenant aussi vers l'application, par /change/verifier-achat, pour
qu'une référence recopiée de travers se voie là où elle a été tapée.

TROIS CHOSES SONT ÉPROUVÉES ICI, ET LA PREMIÈRE EST LA SEULE QUI PUISSE
COÛTER DE L'ARGENT.
═══════════════════════════════════════════════════════════════════════════
*/
{
  /*
  ═══════════════════════════════════════════════════════════════════════
  SONDER NE DOIT PAS CONSOMMER L'ENCAISSEMENT
  ═══════════════════════════════════════════════════════════════════════

  C'est LE défaut à ne pas écrire. L'application appelle cette adresse
  toutes les quinze secondes : si la trouver marquait le paiement comme
  servi, le PREMIER sondage brûlerait celui du client, et son propre
  ordre repartirait « déjà servi » quelques secondes plus tard. Il aurait
  payé, et le message partirait marqué comme un rejeu.

  Vingt sondages, puis l'ordre. L'ordre doit être vert.
  ═══════════════════════════════════════════════════════════════════════
  */
  const cache = faireCache()
  await posterSms('Vous avez recu 10000 FCFA de 70123456 Ref: MP901', {}, cache)
  installer(cache)

  let verdicts = []
  for (let i = 0; i < 20; i += 1) {
    verdicts.push(
      await verifierAchat(
        { montantFcfa: '10 000', telephone: '70123456', referencePaiement: 'MP901' },
        { ...ENV, ...ENV_SMS }
      )
    )
  }
  verifie('SONDE : les vingt sondages sont verts',
    verdicts.every((v) => v.etat === 'confirme'), verdicts.map((v) => v.etat))

  const r = await poster(
    demande({ sens: 'achat', telephone: '70123456', montantFcfa: '10 000', referencePaiement: 'MP901' }),
    ENV_SMS,
    cache
  )
  verifie('SONDE : et l ordre reste verifie apres eux',
    r.corps.verification === 'confirme', r.corps)
}

{
  /*
  CE QUE LE CLIENT DOIT VOIR SUR SON PROPRE ÉCRAN : une référence
  recopiée de travers. C'est la raison d'être de cette adresse.
  */
  const cache = faireCache()
  await posterSms('Vous avez recu 10000 FCFA de 70123456 Ref: MP902', {}, cache)
  installer(cache)

  const faux = await verifierAchat(
    // Un zéro pris pour un O : la faute de frappe la plus commune.
    { montantFcfa: '10 000', telephone: '', referencePaiement: 'MPO2' },
    { ...ENV, ...ENV_SMS }
  )
  verifie('SONDE : une reference fausse rend absent', faux.etat === 'absent', faux)

  const bon = await verifierAchat(
    { montantFcfa: '10 000', telephone: '70123456', referencePaiement: 'MP902' },
    { ...ENV, ...ENV_SMS }
  )
  verifie('SONDE : la bonne rend confirme', bon.etat === 'confirme', bon)

  /*
  ET LE MONTANT TRANCHE, MÊME QUAND LA RÉFÉRENCE COÏNCIDE. Sans ça,
  recopier la référence d'un vrai paiement de dix mille francs
  justifierait une demande de cent mille.
  */
  const gonfle = await verifierAchat(
    { montantFcfa: '100 000', telephone: '70123456', referencePaiement: 'MP902' },
    { ...ENV, ...ENV_SMS }
  )
  verifie('SONDE : un montant gonfle rend absent', gonfle.etat === 'absent', gonfle)
}

{
  /*
  SANS ROBOT : « indisponible », et surtout pas « absent ».

  C'est ce qui permet au telephone de ne PAS freiner. Un ❌ accusateur sur
  tous les achats d'un changeur qui n'a pas installé la lecture des SMS
  ferait douter de clients honnêtes, et éteindrait le bouton de chacun
  d'eux.
  */
  installer(faireCache())
  const v = await verifierAchat(
    { montantFcfa: '10 000', telephone: '70123456', referencePaiement: 'MP903' },
    ENV
  )
  verifie('SANS ROBOT : indisponible, pas absent', v.etat === 'indisponible', v)
}

{
  /*
  ═══════════════════════════════════════════════════════════════════════
  L'ADRESSE EST BIEN BRANCHÉE
  ═══════════════════════════════════════════════════════════════════════

  Les trois blocs ci-dessus appellent `verifierAchat` directement : ils
  ne diraient rien d'un chemin mal orthographié dans le routeur. Or
  l'application n'a que l'URL — et une URL qui ne correspond à aucune
  route part chez CoinGecko, qui répond poliment autre chose.

  On passe donc par `fetch`, comme le téléphone.
  ═══════════════════════════════════════════════════════════════════════
  */
  const cache = faireCache()
  await posterSms('Vous avez recu 10000 FCFA de 70123456 Ref: MP904', {}, cache)
  installer(cache)

  const rep = await relais.fetch(
    new Request(
      'https://relais.vaultex/change/verifier-achat' +
        '?montant=10000&numero=70123456&reference=MP904'
    ),
    { ...ENV, ...ENV_SMS }
  )
  const corps = await rep.json()
  verifie('ROUTE : /change/verifier-achat repond 200', rep.status === 200, rep.status)
  verifie('ROUTE : et rend le verdict', corps.etat === 'confirme', corps)
  verifie('ROUTE : avec le montant lu dans le SMS', corps.montant === 10000, corps)

  /*
  LES NOMS DES PARAMÈTRES COMPTENT. `numero` et non `telephone` : c'est
  ce que l'interface Retrofit envoie. Un nom qui ne correspond pas se lit
  comme un numéro vide, donc comme un rapprochement impossible — et ça ne
  se verrait que sur un vrai téléphone.
  */
  const sansRef = await relais.fetch(
    new Request('https://relais.vaultex/change/verifier-achat?montant=10000&numero=70123456'),
    { ...ENV, ...ENV_SMS }
  )
  const corpsSansRef = await sansRef.json()
  verifie('ROUTE : le parametre « numero » est bien celui qui est lu',
    corpsSansRef.etat === 'confirme', corpsSansRef)
}

/*
═══════════════════════════════════════════════════════════════════════════
LES IMAGES D'ANNONCE, ET LE NOM QUI NE DOIT PAS S'ÉCHAPPER
═══════════════════════════════════════════════════════════════════════════

Le bandeau d'une annonce doit vivre à une adresse HTTPS publique. Le relais
en est une, donc il les sert — depuis KV, sous la clé `img:<nom>`.

CE NOM VIENT DE L'URL, ET IL PART DANS UNE CLÉ KV. C'est tout l'objet de
ces vérifications : un nom libre laisserait lire n'importe quelle clé de
cet espace, dont celles qui portent la naissance des demandes et les
encaissements déjà servis. Le motif est fermé, et ces essais le tiennent.
═══════════════════════════════════════════════════════════════════════════
*/
{
  // Un KV de papier : on y met des octets, on en ressort des octets.
  const sac = new Map()
  sac.set('img:banniere.png', new Uint8Array([0x89, 0x50, 0x4e, 0x47]).buffer)
  sac.set('demande/VX-SECRET', 'naissance confidentielle')
  const MEMOIRE = {
    async get(cle, type) {
      const v = sac.get(cle)
      if (v === undefined) return null
      return type === 'arrayBuffer' ? v : String(v)
    },
    async put() {},
  }
  const ENV_IMG = { ...ENV, MEMOIRE }

  installer(faireCache())
  const demander = (chemin) =>
    relais.fetch(new Request(`https://relais.vaultex${chemin}`), ENV_IMG)

  {
    const r = await demander('/img/banniere.png')
    verifie('IMAGE : une image deposee est servie', r.status === 200, r.status)
    verifie('IMAGE : avec le bon type',
      r.headers.get('content-type') === 'image/png', r.headers.get('content-type'))
    verifie('IMAGE : et un cache long, car un bandeau ne change pas',
      String(r.headers.get('cache-control')).includes('immutable'),
      r.headers.get('cache-control'))
    /*
    `nosniff` N'EST PAS DECORATIF. Sans lui, un navigateur peut deviner un
    type a partir du contenu et executer ce qu'il a devine — depuis NOTRE
    domaine, donc avec notre nom.
    */
    verifie('IMAGE : rien n’est devine a partir du contenu',
      r.headers.get('x-content-type-options') === 'nosniff', null)
  }

  {
    const r = await demander('/img/absente.png')
    verifie('IMAGE : une image absente rend 404', r.status === 404, r.status)
  }

  /*
  ═══════════════════════════════════════════════════════════════════════
  LE CONTROLE QUI COMPTE : LE NOM NE SORT PAS DE SON ESPACE
  ═══════════════════════════════════════════════════════════════════════

  Chacune de ces adresses cherche a lire autre chose qu'une image. La
  derniere est la plus parlante : `../demande/VX-SECRET` viserait la
  naissance d'une demande — exactement ce qui tient le controle
  d'anteriorite.
  ═══════════════════════════════════════════════════════════════════════
  */
  const ATTAQUES = [
    '/img/%2E%2E%2Fdemande%2FVX-SECRET',
    '/img/demande/VX-SECRET',
    '/img/..%2Fpaiementservi%2FMP888',
    '/img/' + 'a'.repeat(200) + '.png',
    '/img/',
  ]
  for (const chemin of ATTAQUES) {
    const r = await demander(chemin)
    const corps = await r.text()
    verifie(`ECHAPPEMENT : « ${chemin.slice(0, 44)} » est refuse`,
      r.status >= 400, r.status)
    verifie(`ECHAPPEMENT : « ${chemin.slice(0, 44)} » ne rend rien de la memoire`,
      !corps.includes('confidentielle'), corps.slice(0, 60))
  }

  {
    /*
    ═══════════════════════════════════════════════════════════════════
    LA FORME `../` LITTERALE N'ATTEINT JAMAIS CETTE ROUTE
    ═══════════════════════════════════════════════════════════════════

    MON PREMIER ESSAI ICI ATTENDAIT UN 4xx, et il a echoue sur un 200 —
    ce qui m'a fait croire a une faille avant de comprendre.

    `new URL()` NORMALISE le chemin : « /img/../demande/VX-SECRET »
    devient « /demande/VX-SECRET » avant que le routeur ne le voie. La
    requete ne commence donc plus par /img/, ne passe pas par
    `servirImage`, et finit dans le relais de cours — qui ne lit aucune
    cle.

    Ce qui compte n'est pas le code de retour : c'est qu'AUCUNE image ne
    soit servie et qu'AUCUNE memoire ne sorte. C'est ce qu'on verifie,
    et c'est ce qui serait encore vrai si la normalisation changeait un
    jour de comportement.
    ═══════════════════════════════════════════════════════════════════
    */
    const r = await demander('/img/../demande/VX-SECRET')
    const corps = await r.text()
    verifie('NORMALISATION : rien de la memoire ne sort',
      !corps.includes('confidentielle'), corps.slice(0, 60))
    verifie('NORMALISATION : et aucune image n’est servie',
      !String(r.headers.get('content-type') ?? '').startsWith('image/'),
      r.headers.get('content-type'))
  }

  {
    /*
    ET LES TYPES EXECUTABLES SONT REFUSES, meme si quelqu'un deposait un
    tel fichier dans KV. Servir un HTML depuis ce domaine en ferait une
    page de notre origine.
    */
    sac.set('img:page.html', 'arrayBuffer')
    const r = await demander('/img/page.html')
    verifie('TYPE : un .html n’est pas servi', r.status === 415, r.status)
  }

  {
    // Sans liaison KV, on le dit au lieu de faire semblant.
    installer(faireCache())
    const r = await relais.fetch(
      new Request('https://relais.vaultex/img/banniere.png'), { ...ENV }
    )
    verifie('IMAGE : sans KV, 503 et pas 404', r.status === 503, r.status)
  }
}

console.log(`\n${passes} verifications passees, ${echecs} en echec`)
if (echecs > 0) process.exit(1)
console.log('✅ Les garde-fous tiennent, et dans le bon ordre.')
