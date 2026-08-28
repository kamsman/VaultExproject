/*
═══════════════════════════════════════════════════════════════════════════
RELAIS DE COURS VAULTEX — Cloudflare Worker
═══════════════════════════════════════════════════════════════════════════

POURQUOI CE FICHIER EXISTE

Le quota gratuit de CoinGecko est de 10 000 appels par mois — pas par
utilisateur, mais pour la CLÉ, donc pour toute l'application réunie. Chaque
téléphone en consomme environ un millier par mois : une dizaine d'appareils
suffisent à l'épuiser. Deux téléphones de test l'ont fait, et l'API a
répondu 429 à tout le monde en même temps. Plus un seul prix affiché.

La cause n'est pas le nombre d'appels d'un téléphone, c'est qu'ils sont
POSÉS SÉPARÉMENT. Mille utilisateurs qui regardent le Bitcoin à la même
minute, c'est mille fois la même question.

Ce relais la pose UNE fois et sert la réponse à tout le monde. Le coût cesse
de dépendre du nombre d'utilisateurs : que l'application ait dix ou cent
mille installations, le marché est interrogé au même rythme.

═══════════════════════════════════════════════════════════════════════════
IL PARLE LE LANGAGE DE COINGECKO, VOLONTAIREMENT
═══════════════════════════════════════════════════════════════════════════

Les chemins et les formats de réponse sont ceux de CoinGecko, à
l'identique. Ce n'est pas de la paresse : cela réduit la modification côté
application à UNE ligne — l'adresse de base. Aucun modèle de données à
réécrire, aucun analyseur à adapter, donc aucune occasion de se tromper sur
un champ au passage.

Corollaire à ne pas oublier : si un jour CoinGecko change un format, c'est
ici qu'il faudra suivre.

═══════════════════════════════════════════════════════════════════════════
DEUX SOURCES, ET LAQUELLE POUR QUOI
═══════════════════════════════════════════════════════════════════════════

BINANCE sert les cours simples (/simple/price). Son point d'entrée public
n'a ni clé, ni compte, ni quota mensuel. C'est le chemin CHAUD, celui que
chaque téléphone emprunte à chaque ouverture — il ne doit rien coûter.

COINGECKO sert le reste : capitalisations, courbes, jetons par adresse de
contrat. Binance ne sait pas les fournir. Ces appels sont rares et mis en
cache agressivement.

L'euro et le FCFA se déduisent d'un seul appel supplémentaire : la paire
EURUSDT donne le taux euro/dollar, et le franc CFA est arrimé à l'euro à
parité FIXE et légale (1 € = 655,957 FCFA). Aucun service de change à
ajouter, donc aucun quota de plus à surveiller.

═══════════════════════════════════════════════════════════════════════════
DÉPLOIEMENT
═══════════════════════════════════════════════════════════════════════════

  1. dash.cloudflare.com → Workers & Pages → Create → Worker
  2. Nommer par exemple « vaultex-prix », puis Deploy
  3. Edit code → tout remplacer par ce fichier → Deploy
  4. L'adresse obtenue (https://vaultex-prix.<compte>.workers.dev/api/v3/)
     remplace https://api.coingecko.com/api/v3/ dans NetworkModule.kt

  Clé Demo CoinGecko (facultatif, relève les limites de débit) :
  Settings → Variables and Secrets → ajouter le secret COINGECKO_KEY.

Aucune donnée personnelle ne transite ici : ni adresse de portefeuille, ni
identifiant, ni clé. Uniquement des cours publics.
═══════════════════════════════════════════════════════════════════════════
*/

const COINGECKO = 'https://api.coingecko.com'

/*
HÔTES BINANCE, PAR ORDRE DE PRÉFÉRENCE — CHOISIS SUR MESURE

`api.binance.com` répond 403 depuis un Worker, avec une page d'erreur
CloudFront : Binance filtre les adresses de centres de données, et
Cloudflare en est un. Même refus pour `api1` et `data-api.binance.vision`.

Seul `api-gcp.binance.com` a répondu 200. Ce n'est pas une déduction, c'est
une sonde exécutée depuis le Worker déployé — voir /diag, qui reste en
place pour refaire la mesure le jour où celui-ci se fermerait à son tour.

L'ordre compte : le premier hôte qui répond gagne. Les suivants ne coûtent
rien à conserver puisqu'ils parlent la même langue, et ils reprendront le
service si la situation s'inverse — ce genre de filtrage change sans
préavis.

Si TOUS venaient à tomber, le relais bascule sur CoinGecko (voir
coursSimples), et l'application garde de son côté son propre appel direct à
Binance depuis les connexions mobiles, qui ne sont pas filtrées.
*/
const HOTES_BINANCE = [
  'https://api-gcp.binance.com',
  'https://api.binance.com',
  'https://api1.binance.com',
]

/*
CoinGecko REFUSE les requêtes sans User-Agent descriptif — code 403, avec
un message qui explique la règle. Un navigateur en envoie un tout seul ;
`fetch` depuis un Worker, non. Sans cet en-tête, tout le chemin de repli
vers CoinGecko était mort, et l'erreur ressemblait à un problème de clé ou
de quota alors qu'il n'en était rien.
*/
const AGENT = 'VaultEx-Wallet/1.0 (+https://github.com/kamsman/vaultexproject)'

/** Parité fixe et légale du franc CFA avec l'euro. Ce n'est pas un cours. */
const XOF_PAR_EURO = 655.957

/*
Durées de cache.

Les cours changent en permanence, mais pas de façon perceptible en deux
minutes sur un écran de portefeuille. Ce délai est le rapport de
mutualisation : à 2 minutes, le marché est interrogé 720 fois par jour quel
que soit le nombre d'utilisateurs.

Les capitalisations et les courbes 7 jours bougent encore moins : 5 minutes.
*/
const TTL_COURS = 120
const TTL_MARCHE = 300

/*
Identifiant CoinGecko → symbole Binance.

DOIT rester aligné sur CoinIds.kt côté application. Un identifiant absent
d'ici n'est pas une erreur : il bascule simplement sur CoinGecko. Mieux vaut
ça qu'une correspondance devinée, qui renverrait le cours d'une AUTRE
monnaie — l'erreur exacte qui a déjà touché ce projet, où SHIB et DAI
affichaient tous deux le prix de l'Ethereum.
*/
const SYMBOLE_BINANCE = {
  bitcoin: 'BTC',
  ethereum: 'ETH',
  binancecoin: 'BNB',
  solana: 'SOL',
  tron: 'TRX',
  'usd-coin': 'USDC',
  dai: 'DAI',
  'shiba-inu': 'SHIB',
  chainlink: 'LINK',
  'pancakeswap-token': 'CAKE',
  pepe: 'PEPE',
  uniswap: 'UNI',
  aave: 'AAVE',
  'wrapped-bitcoin': 'WBTC',
}

/*
Paires dont l'existence chez Binance ne fait aucun doute.

Binance rejette l'appel ENTIER avec un code 400 dès qu'un seul symbole est
inconnu. Une paire de jeton peut être retirée de la cote du jour au
lendemain ; la mettre dans le même appel que Bitcoin, c'est accepter que son
retrait fasse disparaître le prix du Bitcoin. Ce groupe reste donc minimal —
les cinq monnaies natives et l'euro, qui sert de pivot de conversion.
*/
const PAIRES_SURES = new Set(['BTC', 'ETH', 'BNB', 'SOL', 'TRX'])

export default {
  async fetch(requete, env) {
    const url = new URL(requete.url)

    // Sonde de vie : permet de vérifier le déploiement sans lancer l'app.
    if (url.pathname === '/' || url.pathname === '/sante') {
      return json({ ok: true, service: 'relais-cours-vaultex' })
    }

    if (url.pathname === '/diag') {
      return await diagnostic(env)
    }

    if (url.pathname === '/api/v3/simple/price') {
      return await coursSimples(url, env)
    }

    if (url.pathname.startsWith('/api/v3/simple/token_price/')) {
      return await prixParContrat(url, env)
    }

    // Tout le reste part chez CoinGecko, mais UNE SEULE FOIS par période de
    // cache et non une fois par utilisateur.
    return await relaisCoinGecko(url, env, TTL_MARCHE)
  },
}

/**
 * /simple/price — servi par Binance, avec repli sur CoinGecko.
 *
 * C'est le chemin le plus emprunté de toute l'application : chaque ouverture
 * de l'accueil et chaque réveil du worker d'alertes passent par lui. C'est
 * donc celui qui doit être gratuit, pas seulement rapide.
 */
async function coursSimples(url, env) {
  const ids = (url.searchParams.get('ids') || '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
  if (ids.length === 0) return json({})

  const cle = new Request(`https://relais.vaultex/cours?ids=${ids.sort().join(',')}`)
  const cache = caches.default
  const enCache = await cache.match(cle)
  if (enCache) return enCache

  let corps = null
  try {
    corps = await depuisBinance(ids)
  } catch (_) {
    corps = null
  }

  // Binance muet (panne, ou aucun identifiant reconnu) : on retombe sur
  // CoinGecko. Un cours venu d'une source coûteuse vaut mieux qu'un « 0,00 »
  // affiché sur un portefeuille, qui ne se lit pas « prix indisponible »
  // mais « mon argent a disparu ».
  if (!corps || Object.keys(corps).length === 0) {
    return await relaisCoinGecko(url, env, TTL_COURS)
  }

  const reponse = json(corps, TTL_COURS)
  // waitUntil n'est pas indispensable ici : la mise en cache est rapide et
  // la réponse est déjà construite.
  await cache.put(cle, reponse.clone())
  return reponse
}

/** Interroge Binance et rend le format attendu par l'application. */
async function depuisBinance(ids) {
  const voulus = ids
    .map((id) => [id, SYMBOLE_BINANCE[id]])
    .filter(([, sym]) => Boolean(sym))
  if (voulus.length === 0) return null

  const bases = new Set(voulus.map(([, sym]) => sym))
  // L'euro sert de pivot vers l'EUR et le FCFA : toujours demandé.
  const surs = [...bases].filter((b) => PAIRES_SURES.has(b)).concat('EUR')
  const incertains = [...bases].filter((b) => !PAIRES_SURES.has(b) && b !== 'USDT')

  const tickers = {}
  await ajouteTickers(tickers, surs)
  if (incertains.length > 0) {
    const avant = Object.keys(tickers).length
    await ajouteTickers(tickers, incertains)
    // Le groupe est tombé (une paire inconnue suffit) : on redemande chaque
    // symbole seul. Seul l'intrus reste sans cours, au lieu de tous.
    if (Object.keys(tickers).length === avant) {
      for (const base of incertains) await ajouteTickers(tickers, [base])
    }
  }
  if (Object.keys(tickers).length === 0) return null

  const eurUsd = nombre(tickers['EURUSDT']?.lastPrice)
  const sortie = {}
  for (const [id, base] of voulus) {
    // USDTUSDT n'existe pas : demandé tel quel, il déclencherait le rejet du
    // groupe entier. Sa valeur est posée à 1 $ — c'est la définition même de
    // ce jeton.
    if (base === 'USDT') {
      sortie[id] = ligne(1, eurUsd, 0)
      continue
    }
    const t = tickers[`${base}USDT`]
    const usd = nombre(t?.lastPrice)
    if (!(usd > 0)) continue
    sortie[id] = ligne(usd, eurUsd, nombre(t?.priceChangePercent))
  }
  return sortie
}

/** Une entrée au format CoinGecko : usd, eur, xof, variation 24 h. */
function ligne(usd, eurUsd, variation) {
  const eur = eurUsd > 0 ? usd / eurUsd : 0
  return {
    usd,
    eur,
    xof: eur * XOF_PAR_EURO,
    usd_24h_change: variation,
  }
}

async function ajouteTickers(cible, bases) {
  if (bases.length === 0) return
  const symboles = JSON.stringify(bases.map((b) => `${b}USDT`))
  // Premier hôte qui répond, dans l'ordre de HOTES_BINANCE.
  for (const hote of HOTES_BINANCE) {
    try {
      const r = await fetch(
        `${hote}/api/v3/ticker/24hr?symbols=${encodeURIComponent(symboles)}`,
        {
          headers: { 'user-agent': AGENT, accept: 'application/json' },
          cf: { cacheTtl: TTL_COURS, cacheEverything: true },
        }
      )
      if (!r.ok) continue
      const liste = await r.json()
      if (!Array.isArray(liste) || liste.length === 0) continue
      for (const t of liste) cible[t.symbol] = t
      return
    } catch (_) {
      // hôte injoignable : on essaie le suivant
    }
  }
}

/**
 * Sonde de diagnostic : que répondent RÉELLEMENT les deux sources ?
 *
 * Sans elle, un relais qui renvoie une erreur ne dit pas laquelle des deux
 * sources a échoué ni pourquoi : Binance muet est indiscernable d'un repli
 * CoinGecko refusé, et les deux produisent le même symptôme à l'écran.
 *
 * C'est exactement le piège rencontré ici : la réponse affichait une erreur
 * CoinGecko, ce qui donnait à croire à un problème de quota ou de clé, alors
 * que la vraie question était « pourquoi Binance n'a-t-il pas répondu ? ».
 *
 * Ce point d'entrée n'expose aucun secret — deux appels publics et leur code
 * de retour.
 */
async function diagnostic(env) {
  const sondes = {}

  /*
  Mesuré, pas supposé : api.binance.com répond 403 depuis un Worker, avec
  une page d'erreur CloudFront. Binance filtre les adresses de centres de
  données, et Cloudflare en est un. Insister sur cet hôte ne sert à rien.

  On teste donc plusieurs candidats d'un coup, plutôt que de les essayer un
  par jour. Les trois premiers parlent la MÊME langue que Binance : si l'un
  d'eux répond, le relais fonctionne sans qu'une ligne de conversion soit
  écrite. Les suivants auraient chacun leur format, donc du code en plus —
  on ne s'y résoudra que si les premiers échouent tous.
  */
  const candidats = {
    binance_vision: 'https://data-api.binance.vision/api/v3/ticker/24hr?symbols=%5B%22BTCUSDT%22%5D',
    binance_gcp: 'https://api-gcp.binance.com/api/v3/ticker/24hr?symbols=%5B%22BTCUSDT%22%5D',
    binance_api1: 'https://api1.binance.com/api/v3/ticker/24hr?symbols=%5B%22BTCUSDT%22%5D',
    okx: 'https://www.okx.com/api/v5/market/ticker?instId=BTC-USDT',
    coinbase: 'https://api.exchange.coinbase.com/products/BTC-USD/ticker',
    kraken: 'https://api.kraken.com/0/public/Ticker?pair=XBTUSDT',
  }

  for (const [nom, adresse] of Object.entries(candidats)) {
    try {
      const r = await fetch(adresse, {
        headers: { 'user-agent': AGENT, accept: 'application/json' },
      })
      sondes[nom] = { code: r.status, extrait: (await r.text()).slice(0, 160) }
    } catch (e) {
      sondes[nom] = { erreur: String(e).slice(0, 160) }
    }
  }

  try {
    const entetes = { 'user-agent': AGENT, accept: 'application/json' }
    if (env && env.COINGECKO_KEY) entetes['x-cg-demo-api-key'] = env.COINGECKO_KEY
    const r = await fetch(
      `${COINGECKO}/api/v3/simple/price?ids=bitcoin&vs_currencies=usd`,
      { headers: entetes }
    )
    /*
    On rapporte la LONGUEUR de la clé, jamais sa valeur.

    « Présente ou absente » ne suffit pas à diagnostiquer : une liaison
    manquante et une valeur vide donnent le même faux, et Cloudflare affiche
    « Value encrypted » dans les deux cas. La longueur les sépare —
    `undefined` pour une liaison absente, `0` pour un collage qui n'a pas
    pris, une trentaine de caractères pour une clé correcte — sans rien
    révéler d'exploitable.
    */
    sondes.coingecko = {
      code: r.status,
      cle_presente: Boolean(env && env.COINGECKO_KEY),
      cle_longueur: env && typeof env.COINGECKO_KEY === 'string'
        ? env.COINGECKO_KEY.length
        : null,
      extrait: (await r.text()).slice(0, 300),
    }
  } catch (e) {
    sondes.coingecko = { erreur: String(e).slice(0, 300) }
  }

  return json(sondes)
}

/**
 * Relais vers CoinGecko, mutualisé par le cache de Cloudflare.
 *
 * Même sans Binance, ce chemin change tout : `cacheEverything` fait qu'un
 * millier de téléphones demandant la même chose pendant la période de cache
 * ne produisent qu'UN appel vers CoinGecko.
 */
async function relaisCoinGecko(url, env, ttl) {
  const cache = caches.default
  const cle = new Request(url.toString())
  const enCache = await cache.match(cle)
  // Une version antérieure enregistrait TOUTES les réponses, refus de quota
  // compris. Une erreur ainsi mise en cache continuait d'être servie bien
  // après que la source fut redevenue disponible : on ne rend donc du cache
  // que ce qui a réellement abouti.
  if (enCache && enCache.ok) return enCache

  const cible = COINGECKO + url.pathname + url.search
  const entetes = { 'user-agent': AGENT, accept: 'application/json' }

  /*
  ═══════════════════════════════════════════════════════════════════════
  LA CLÉ D'ABORD, PUIS SANS ELLE
  ═══════════════════════════════════════════════════════════════════════

  Les deux voies n'ont pas les mêmes limites, et c'est ce qui rend ce
  repli indispensable :

  · AVEC la clé Demo — débit confortable, mais un plafond MENSUEL DUR de
    10 000 appels. Atteint, tout est refusé jusqu'au mois suivant.
  · SANS clé — aucun plafond mensuel, mais un débit par minute serré.

  Une fois le quota mensuel épuisé, la clé rend donc les choses PIRES que
  pas de clé du tout. Mesuré : le relais renvoyait « error_code 10006,
  You've reached 10,000 calls limit » pendant que le même appel sans clé
  répondait normalement. Les jetons importés restaient à « Prix : $0 »
  alors que la donnée était à portée de main.

  On tente donc la clé — elle donne le meilleur débit tant qu'il reste du
  quota — et l'on repart sans elle dès que la réponse annonce un
  épuisement. Aucun réglage à faire : quand le quota se réinitialise, la
  clé reprend d'elle-même.

  Le cache n'enregistre QUE les réponses réussies. Sans cette précaution,
  un refus de quota serait servi pendant toute la durée de cache — y
  compris à la tentative sans clé, qui aurait pourtant abouti.
  ═══════════════════════════════════════════════════════════════════════
   */
  let reponseAmont = null
  let texte = ''

  if (env && env.COINGECKO_KEY) {
    reponseAmont = await fetch(cible, {
      headers: { ...entetes, 'x-cg-demo-api-key': env.COINGECKO_KEY },
    })
    texte = await reponseAmont.text()
  }

  if (!reponseAmont || quotaEpuise(reponseAmont.status, texte)) {
    const sansCle = await fetch(cible, { headers: entetes })
    const texteSansCle = await sansCle.text()
    // On ne retient la tentative sans clé que si elle fait mieux : sinon on
    // garde la réponse d'origine, dont le message est plus parlant.
    if (!reponseAmont || sansCle.ok) {
      reponseAmont = sansCle
      texte = texteSansCle
    }
  }

  const reponse = new Response(texte, {
    status: reponseAmont.status,
    headers: {
      'content-type': 'application/json; charset=utf-8',
      'cache-control': `public, max-age=${ttl}`,
    },
  })
  if (reponseAmont.ok) await cache.put(cle, reponse.clone())
  return reponse
}

/**
 * La réponse annonce-t-elle un quota épuisé ?
 *
 * CoinGecko ne se contente pas d'un code HTTP : le plafond mensuel arrive
 * avec `error_code: 10006` dans le corps, parfois sur un statut 200. Se fier
 * au seul statut laisserait donc passer le cas le plus important.
 */
/**
 * /simple/token_price/{plateforme} — prix des JETONS par adresse de contrat.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * POURQUOI CE CHEMIN A SA PROPRE SOURCE
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * C'est l'appel dont dependent tous les jetons importes par l'utilisateur, et
 * c'est celui qui tombait le plus souvent. Mesure sur le relais deploye :
 *
 *   avec la cle Demo : error_code 10006, plafond mensuel atteint
 *   sans cle         : refuse par intermittence, le debit public etant
 *                      partage entre toutes les adresses Cloudflare
 *
 * Resultat sur appareil : CRV, GRT, 1INCH et XVS affiches a « Prix : $0 »,
 * alors que ces jetons sont cotes partout. Un portefeuille qui ne sait pas
 * dire ce que vaut ce qu'il contient ne remplit pas son office.
 *
 * GECKOTERMINAL — l'interface DEX de CoinGecko — repond a la meme question
 * SANS CLE ET SANS PLAFOND MENSUEL. Sa couverture est meme plus large : elle
 * porte sur les jetons ayant une liquidite reelle sur les places
 * decentralisees, donc au-dela des seules monnaies referencees.
 *
 * Elle ne rend qu'un prix en dollars : ni variation, ni capitalisation. C'est
 * suffisant ici — la ligne d'accueil a besoin d'une valeur, pas d'une fiche.
 * L'euro et le FCFA s'en deduisent par le meme pivot que partout ailleurs.
 *
 * ORDRE : CoinGecko d'abord, qui rend tout ; GeckoTerminal ensuite, qui rend
 * l'essentiel mais ne tombe jamais.
 * ═══════════════════════════════════════════════════════════════════════════
 */
async function prixParContrat(url, env) {
  const viaCoinGecko = await relaisCoinGecko(url, env, TTL_COURS)
  if (viaCoinGecko.ok) {
    const texte = await viaCoinGecko.clone().text()
    // Une reponse vide « {} » n'est pas une reussite : le jeton n'a
    // simplement pas ete trouve, et l'autre source peut le connaitre.
    if (texte.length > 4 && !texte.includes('"error_code"')) return viaCoinGecko
  }

  // Plateforme CoinGecko -> reseau GeckoTerminal.
  const plateforme = url.pathname.split('/').pop()
  const reseau =
    plateforme === 'binance-smart-chain' ? 'bsc' :
    plateforme === 'ethereum' ? 'eth' : null
  const contrats = url.searchParams.get('contract_addresses')
  if (!reseau || !contrats) return viaCoinGecko

  try {
    const r = await fetch(
      `https://api.geckoterminal.com/api/v2/simple/networks/${reseau}/token_price/${contrats}`,
      { headers: { 'user-agent': AGENT, accept: 'application/json' } }
    )
    if (!r.ok) return viaCoinGecko
    const donnees = await r.json()
    const prix = donnees && donnees.data && donnees.data.attributes
      ? donnees.data.attributes.token_prices
      : null
    if (!prix || Object.keys(prix).length === 0) return viaCoinGecko

    const eurUsd = await tauxEuroDollar()
    const sortie = {}
    for (const [adresse, valeur] of Object.entries(prix)) {
      const usd = parseFloat(valeur)
      if (!Number.isFinite(usd) || usd <= 0) continue
      const eur = eurUsd ? usd / eurUsd : 0
      // La variation reste absente : GeckoTerminal ne la donne pas, et on
      // n'invente pas un mouvement de marche.
      sortie[adresse.toLowerCase()] = {
        usd,
        eur,
        xof: eur * XOF_PAR_EURO,
        usd_24h_change: 0,
      }
    }
    if (Object.keys(sortie).length === 0) return viaCoinGecko
    return json(sortie, TTL_COURS)
  } catch (_) {
    return viaCoinGecko
  }
}

/**
 * Combien d'USDT vaut un euro, d'apres la paire EURUSDT.
 *
 * Le meme pivot que pour les cours simples : aucun service de change a
 * ajouter, et le FCFA se deduit ensuite d'une parite fixe et legale.
 */
async function tauxEuroDollar() {
  for (const hote of HOTES_BINANCE) {
    try {
      const r = await fetch(`${hote}/api/v3/ticker/price?symbol=EURUSDT`, {
        headers: { 'user-agent': AGENT, accept: 'application/json' },
        cf: { cacheTtl: TTL_COURS, cacheEverything: true },
      })
      if (!r.ok) continue
      const donnees = await r.json()
      const valeur = parseFloat(donnees && donnees.price)
      if (Number.isFinite(valeur) && valeur > 0) return valeur
    } catch (_) {
      // hote injoignable : on essaie le suivant
    }
  }
  return null
}

function quotaEpuise(statut, texte) {
  if (statut === 429) return true
  return texte.includes('"error_code":10006') || texte.includes('calls limit')
}

function nombre(v) {
  const n = parseFloat(v)
  return Number.isFinite(n) ? n : 0
}

function json(objet, ttl) {
  return new Response(JSON.stringify(objet), {
    headers: {
      'content-type': 'application/json; charset=utf-8',
      'cache-control': ttl ? `public, max-age=${ttl}` : 'no-store',
    },
  })
}
