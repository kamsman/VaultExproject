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

/** Version du Worker déployé — lisible sur /sante et /diag. */
const VERSION = 13

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

Les capitalisations et les courbes 7 jours bougent encore moins : 10 minutes.

Ce dernier chiffre n'est pas un détail de confort, c'est le poste de dépense
principal du quota. À 300 s, la seule liste marché consommait 8 640 appels
par mois et par centre de données — pour un plafond mensuel de 10 000. Elle
épuisait donc le quota à elle seule, ce qui laissait les jetons importés à
« Prix : $0 ». À 600 s, elle en consomme 4 320. Dix minutes de décalage sur
un classement par capitalisation ne se voient pas ; un quota épuisé, si.

CATALOGUE : le résultat d'une recherche (quelles monnaies existent, leur nom,
leur symbole, leur logo) ne contient AUCUN prix et ne change pas d'une
semaine à l'autre. Le garder 24 h est ce qui permet d'ouvrir les ~19 000
monnaies de CoinGecko sans peser sur le quota.
*/
const TTL_COURS = 120
const TTL_MARCHE = 600
/*
COURBES : /coins/markets filtré par `ids`, demandé par l'accueil pour la
forme de ses quatre mini-courbes. Une heure, parce que rien de ce qui se lit
sur ces cartes n'en dépend — le prix et la variation viennent de
/simple/price. Voir le commentaire du routage.
*/
const TTL_COURBES = 3600
/*
REGLAGES DU CHANGE : UNE MINUTE.

Assez court pour qu'une fermeture du service, ou une marge corrigee, soit
prise en compte presque tout de suite — c'est l'interet d'un reglage
distant. Assez long pour qu'un ecran rafraichi n'interroge pas le Worker a
chaque frappe.
*/
const TTL_PARAMETRES_CHANGE = 60
const TTL_CATALOGUE = 86400

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
      // VERSION : à incrémenter à chaque modification de ce fichier.
      // Sans elle, impossible de savoir si le code déployé est bien le
      // dernier — on a perdu du temps à corriger un défaut déjà corrigé,
      // simplement parce que l'ancienne version tournait encore.
      return json({ ok: true, service: 'relais-cours-vaultex', version: VERSION })
    }

    if (url.pathname === '/diag') {
      return await diagnostic(env)
    }

    /*
    ═══════════════════════════════════════════════════════════════════════
    CHANGE FCFA — LES DEUX POINTS D'ENTRÉE, ET POURQUOI ILS SONT ICI
    ═══════════════════════════════════════════════════════════════════════

    Un utilisateur achète ou vend de la crypto contre des francs, auprès
    d'un changeur joignable par Telegram. VaultEx affiche, calcule et trace ;
    l'argent va d'Orange Money à Orange Money, la crypto d'un portefeuille à
    l'autre. Rien ne transite par nous.

    POURQUOI LE RELAIS ET NON L'APPLICATION. Le jeton du bot Telegram est
    aujourd'hui compilé dans l'APK. Quiconque décompile l'application le
    récupère — dix minutes avec des outils gratuits. Tant qu'il ne sert
    qu'à des alertes d'administration, c'est une nuisance. Pour des ORDRES
    sur lesquels un changeur agit, ce serait un vol : on forge un résumé
    crédible, le changeur envoie la crypto, personne n'a jamais payé.

    Le jeton vit donc ici, dans les secrets du Worker, et le téléphone ne
    l'a jamais. Forger un ordre redevient « casser ce serveur » au lieu de
    « décompiler une application ».

    CE QUE CELA NE PROTÈGE PAS, ET IL FAUT LE DIRE. Cette adresse est
    publique : n'importe qui peut y poster un faux ordre. C'est inévitable
    — l'application est distribuée, aucun secret qu'elle porterait ne
    resterait secret. La vraie protection n'est pas technique, elle est
    dans la règle du changeur : IL N'ENVOIE QU'APRÈS AVOIR VU LES FONDS SUR
    SON PROPRE COMPTE. Un faux ordre ne coûte alors que du bruit, et c'est
    pour borner ce bruit qu'il y a une limite de débit.
    ═══════════════════════════════════════════════════════════════════════
    */
    if (url.pathname === '/change/parametres') {
      return parametresChange(env)
    }

    if (url.pathname === '/change/ordre' && requete.method === 'POST') {
      return await ordreChange(requete, env)
    }

    if (url.pathname === '/api/v3/simple/price') {
      return await coursSimples(url, env)
    }

    if (url.pathname.startsWith('/api/v3/simple/token_price/')) {
      return await prixParContrat(url, env)
    }

    if (url.pathname === '/api/v3/search') {
      return await recherche(url, env)
    }

    /*
    LES COURBES DE L'ACCUEIL N'ONT PAS BESOIN D'ÊTRE FRAÎCHES
    ────────────────────────────────────────────────────────
    L'accueil demande /coins/markets avec `ids` : quatre monnaies nommées,
    uniquement pour la forme des mini-courbes. À dix minutes de cache, cette
    seule URL peut coûter 4 300 appels par mois — 43 % d'un quota Demo, pour
    un dessin.

    Le prix et la variation affichés sur ces cartes NE VIENNENT PAS D'ICI :
    ils arrivent par /simple/price, que Binance sert en premier, donc hors
    quota CoinGecko. Ce qu'on retarde en allongeant ce cache, c'est la forme
    d'une courbe sur vingt-quatre heures — invisible à l'heure près.

    On distingue par la présence d'`ids` : une liste de marché ORDINAIRE
    n'en a pas, et celle-là garde ses dix minutes parce qu'on la fait défiler
    en lisant des prix.
    */
    if (url.pathname === '/api/v3/coins/markets' && url.searchParams.has('ids')) {
      return await relaisCoinGecko(url, env, TTL_COURBES)
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
  corps = corps || {}

  /*
  ═══════════════════════════════════════════════════════════════════════
  UNE RÉPONSE PARTIELLE N'EST PAS UNE RÉPONSE
  ═══════════════════════════════════════════════════════════════════════

  Le repli sur CoinGecko ne se déclenchait que si Binance ne rendait RIEN.
  Une réponse partielle — six monnaies sur sept — passait donc pour
  complète, et la septième ressortait sans cours.

  C'est exactement ce qui est arrivé au Pi. Il n'est listé sur aucune
  place que Binance sert, donc il manquait de `sortie`. Les six autres
  étant là, `corps` n'était pas vide, le repli ne partait pas, et
  l'accueil affichait « Prix : $0 » pendant que l'écran Marché — qui passe
  par /coins/markets, une autre route — affichait 0,0827 $. Deux écrans de
  la même application en désaccord, à la même seconde.

  Le défaut n'a rien de propre au Pi : TOUTE monnaie absente de Binance
  perdait son cours, en silence, dès qu'une seule autre était présente. Le
  Pi est simplement la première de l'application dans ce cas.

  ON COMPLÈTE, ON NE REMPLACE PAS. Binance reste la source principale —
  c'est elle qui rend ce chemin gratuit, et c'est le chemin le plus
  emprunté de l'application. CoinGecko n'est interrogé que pour les
  identifiants qui manquent, et la réponse fusionnée est mise en cache
  comme avant : le surcoût de quota est donc au plus un appel par tranche
  de TTL_COURS, partagé par tous les téléphones.

  Le format est le même des deux côtés — usd, eur, xof, usd_24h_change —
  puisque `ligne()` a été écrite pour imiter celui de CoinGecko.
  ═══════════════════════════════════════════════════════════════════════
  */
  const manquants = ids.filter((id) => !corps[id])
  if (manquants.length > 0) {
    Object.assign(corps, await complementCoinGecko(manquants, url, env))
  }

  // Les deux sources muettes : on relaie la demande d'origine telle quelle,
  // plutôt que de rendre un « 0,00 » sur un portefeuille — qui ne se lit pas
  // « prix indisponible » mais « mon argent a disparu ».
  if (Object.keys(corps).length === 0) {
    return await relaisCoinGecko(url, env, TTL_COURS)
  }

  const reponse = json(corps, TTL_COURS)
  // waitUntil n'est pas indispensable ici : la mise en cache est rapide et
  // la réponse est déjà construite.
  await cache.put(cle, reponse.clone())
  return reponse
}

/**
 * Cours des identifiants que Binance ne cote pas, demandés à CoinGecko.
 *
 * Rend un objet, pas une Response : l'appelant le FUSIONNE avec ce que
 * Binance a déjà rendu. C'est toute la différence avec [relaisCoinGecko],
 * qui relaie la demande entière et remplace donc tout.
 *
 * On réutilise [relaisCoinGecko] plutôt que d'appeler CoinGecko à la main :
 * il porte la bascule clé / sans clé quand le quota mensuel est épuisé, et
 * sa règle de ne mettre en cache QUE ce qui a abouti. La réécrire ici, ce
 * serait la voir diverger.
 *
 * Ne lève jamais : un complément absent laisse la ligne sans cours, ce qui
 * est l'état d'avant. Il ne doit en aucun cas faire échouer les cours que
 * Binance a correctement rendus.
 */
async function complementCoinGecko(ids, urlOrigine, env) {
  try {
    const cible = new URL(urlOrigine.toString())
    cible.searchParams.set('ids', ids.join(','))
    const reponse = await relaisCoinGecko(cible, env, TTL_COURS)
    if (!reponse.ok) return {}
    const objet = await reponse.json()
    // CoinGecko signale ses refus par un objet { status: { error_code… } } :
    // le fusionner poserait une clé « status » au milieu des monnaies.
    if (!objet || typeof objet !== 'object' || objet.status) return {}
    return objet
  } catch (_) {
    return {}
  }
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
  const sondes = { version: VERSION }

  /*
  ═══════════════════════════════════════════════════════════════════════
  LE CANAL DU CHANGEUR, VERIFIE POUR DE VRAI
  ═══════════════════════════════════════════════════════════════════════

  `canalConfigure`, dans /change/parametres, ne dit qu'une chose : les deux
  reglages EXISTENT. C'est utile et c'est insuffisant — un jeton tronque au
  collage existe, et echoue quand meme.

  Ca s'est paye une heure : les reglages repondaient, l'ecran s'affichait,
  et l'echec n'arrivait qu'au bout de la chaine, resume en trois mots.

  Ici on DEMANDE a Telegram. getMe valide le jeton, getChat valide le
  groupe, et chacun rend sa propre raison. Deux appels sur un point
  d'entree qu'on consulte quand quelque chose ne va pas — jamais sur le
  chemin normal.

  Le nom du bot et le titre du groupe sont rendus : ils confirment d'un
  coup d'oeil qu'on parle du bon bot et du bon groupe, ce qu'un simple
  « true » ne dit pas.
  ═══════════════════════════════════════════════════════════════════════
  */
  const jeton = env?.TG_CHANGE_TOKEN || env?.TG_ADMIN_TOKEN
  const groupe = env?.TG_CHANGE_CHAT || env?.TG_ADMIN_CHAT
  const canal = { jetonPose: Boolean(jeton), groupePose: Boolean(groupe) }

  if (jeton) {
    try {
      const r = await fetch(`https://api.telegram.org/bot${jeton}/getMe`)
      const o = await r.json()
      canal.jetonValide = Boolean(o?.ok)
      if (o?.ok) canal.bot = String(o.result?.username ?? '')
      else canal.jetonRaison = String(o?.description ?? `HTTP ${r.status}`)
    } catch (e) {
      canal.jetonValide = false
      canal.jetonRaison = 'injoignable'
    }
  }

  if (jeton && groupe) {
    try {
      const r = await fetch(
        `https://api.telegram.org/bot${jeton}/getChat?chat_id=${encodeURIComponent(groupe)}`
      )
      const o = await r.json()
      canal.groupeJoignable = Boolean(o?.ok)
      if (o?.ok) {
        canal.groupe = String(o.result?.title ?? '')
        canal.groupeType = String(o.result?.type ?? '')
      } else {
        canal.groupeRaison = String(o?.description ?? `HTTP ${r.status}`)
      }
    } catch (e) {
      canal.groupeJoignable = false
      canal.groupeRaison = 'injoignable'
    }
  }
  sondes.canalChange = canal

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
    geckoterminal:
      'https://api.geckoterminal.com/api/v2/simple/networks/eth/token_price/' +
      '0xd533a949740bb3306d119cc777fa900ba034cd52',
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
 * /search — le catalogue complet de CoinGecko, à coût presque nul.
 *
 * L'onglet Marché ne montrait que les 100 premières capitalisations, et sa
 * barre de recherche filtrait ces 100 lignes en local : chercher une monnaie
 * au-delà du rang 100 ne renvoyait rien, ce qui ressemble à une panne bien
 * plus qu'à une limite.
 *
 * Cet appel-ci balaie les ~19 000 monnaies cotées et ne renvoie QUE du
 * catalogue — identifiant, nom, symbole, logo, rang. Aucun prix, donc rien
 * qui périme : 24 h de cache. Les prix des résultats sont demandés
 * séparément, par `/coins/markets?ids=`, sur le chemin déjà en place.
 *
 * DEUX PRÉCAUTIONS, sans lesquelles le cache travaillerait contre nous :
 *
 * · Normalisation. « PEPE », « pepe » et «  Pepe  » sont la même recherche.
 *   Sans mise en forme commune, chacune créerait sa propre entrée et son
 *   propre appel amont, pour un résultat identique.
 * · Deux caractères minimum. La saisie est envoyée au fil de la frappe :
 *   sans ce plancher, taper « pepe » produirait quatre recherches dont les
 *   trois premières sont sans valeur.
 */
async function recherche(url, env) {
  const q = (url.searchParams.get('query') || '').trim().toLowerCase()
  if (q.length < 2) return json({ coins: [] }, TTL_CATALOGUE)

  // Clé de cache indépendante de la casse et des espaces d'origine.
  const cle = new Request(`https://relais.vaultex/recherche?q=${encodeURIComponent(q)}`)
  const cache = caches.default
  const enCache = await cache.match(cle)
  if (enCache && enCache.ok) return enCache

  const cible = `${COINGECKO}/api/v3/search?query=${encodeURIComponent(q)}`
  const entetes = { 'user-agent': AGENT, accept: 'application/json' }

  let amont = null
  let texte = ''
  if (env && env.COINGECKO_KEY) {
    amont = await fetch(cible, { headers: { ...entetes, 'x-cg-demo-api-key': env.COINGECKO_KEY } })
    texte = await amont.text()
  }
  if (!amont || quotaEpuise(amont.status, texte)) {
    const sansCle = await fetch(cible, { headers: entetes })
    const texteSansCle = await sansCle.text()
    if (!amont || sansCle.ok) {
      amont = sansCle
      texte = texteSansCle
    }
  }

  const reponse = new Response(texte, {
    status: amont.status,
    headers: {
      'content-type': 'application/json; charset=utf-8',
      'cache-control': `public, max-age=${TTL_CATALOGUE}`,
    },
  })
  if (amont.ok) await cache.put(cle, reponse.clone())
  return reponse
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

function json(objet, ttl, statut) {
  return new Response(JSON.stringify(objet), {
    // Un refus doit se lire comme un refus. Rendu en 200, il arriverait
    // dans l'application comme une reponse valide, et un corps { ok: false }
    // finirait par etre ignore quelque part.
    status: statut || 200,
    headers: {
      'content-type': 'application/json; charset=utf-8',
      'cache-control': ttl ? `public, max-age=${ttl}` : 'no-store',
    },
  })
}


// ═════════════════════════════════════════════════════════════════════════
// CHANGE FCFA
// ═════════════════════════════════════════════════════════════════════════

/**
 * Réglages du change, lus par l'application avant chaque affichage.
 *
 * TOUT EST MODIFIABLE SANS REPUBLIER L'APPLICATION, et c'est l'intérêt
 * entier de les servir d'ici : la marge suit le marché, le plafond suit la
 * confiance, et `actif` ferme le service en une seconde le jour où le
 * changeur n'est pas joignable. Une application distribuée ne se corrige
 * pas en une seconde ; un réglage, si.
 *
 * Les valeurs vivent dans les variables du Worker (tableau de bord
 * Cloudflare → Settings → Variables). Celles par défaut ci-dessous valent
 * pour un Worker fraîchement déployé, et tournent autour de 25 FCFA par
 * dollar — la moitié de ce que prend FasoChange.
 */
function parametresChange(env) {
  const nombre = (v, defaut) => {
    const n = Number(v)
    return Number.isFinite(n) && n >= 0 ? n : defaut
  }
  return json(
    {
      // Interrupteur d'arrêt. Mettre CHANGE_ACTIF=0 suffit à retirer le
      // bouton de l'application, sans rien republier.
      actif: String(env?.CHANGE_ACTIF ?? '1') !== '0',
      // Marge en FRANCS PAR DOLLAR : la langue du marché local.
      margeFcfaParDollar: nombre(env?.CHANGE_MARGE, 25),
      // Bornes par opération. Le plafond est bas tant que la confiance se
      // construit : c'est l'utilisateur qui envoie en premier.
      minimumFcfa: nombre(env?.CHANGE_MIN, 5000),
      plafondFcfa: nombre(env?.CHANGE_MAX, 50000),
      // Le changeur, tel qu'il s'affiche à l'écran. Pas un secret : c'est
      // un numéro qu'on donne pour recevoir de l'argent.
      numeroMobileMoney: String(env?.CHANGE_NUMERO ?? ''),
      nomChangeur: String(env?.CHANGE_NOM ?? ''),
      operateur: String(env?.CHANGE_OPERATEUR ?? 'Orange Money'),
      // Délai annoncé à l'utilisateur, en minutes. Une promesse tenue vaut
      // mieux qu'une promesse courte.
      delaiMinutes: nombre(env?.CHANGE_DELAI, 30),
      /*
      LES MONNAIES ACCEPTEES, et les adresses ou le changeur les recoit.

      Deux reglages plutot qu'un seul, parce qu'ils ne tombent pas en panne
      ensemble : on peut ACHETER une monnaie sans que le changeur ait
      publie d'adresse pour la racheter. L'application n'ouvre la vente que
      pour celles dont elle a l'adresse — sinon elle enverrait quelqu'un
      vendre a personne.

      CHANGE_MONNAIES : « USDT,BTC,ETH ». CHANGE_ADRESSES : un objet JSON
      « {"USDT":"T...","BTC":"bc1..."} ».
      */
      /*
      LE CANAL EST-IL CONFIGURE ? Un booleen, et rien d'autre.

      Sans cette ligne, un jeton Telegram absent ne se voit NULLE PART :
      /change/parametres repond normalement, l'ecran s'affiche
      normalement, et l'echec n'arrive qu'au moment ou quelqu'un a deja
      paye. On ne revele ni le jeton ni le groupe — seulement s'ils
      existent, ce qui suffit a diagnostiquer en dix secondes.
      */
      canalConfigure: Boolean(
        (env?.TG_CHANGE_TOKEN || env?.TG_ADMIN_TOKEN) &&
          (env?.TG_CHANGE_CHAT || env?.TG_ADMIN_CHAT)
      ),
      monnaies: String(env?.CHANGE_MONNAIES ?? 'USDT')
        .split(',').map((m) => m.trim().toUpperCase()).filter(Boolean),
      adresses: adressesChangeur(env),
    },
    TTL_PARAMETRES_CHANGE
  )
}

/**
 * Adresses du changeur, par monnaie, pour les ventes.
 *
 * Un JSON illisible rend un objet VIDE, et non une lecture partielle : une
 * adresse à moitié lue est une adresse vers laquelle des fonds partiraient
 * sans retour. Aucune vente vaut mieux qu'une vente vers nulle part.
 */
function adressesChangeur(env) {
  const brut = env?.CHANGE_ADRESSES
  if (!brut) return {}
  try {
    const o = JSON.parse(String(brut))
    if (!o || typeof o !== 'object' || Array.isArray(o)) return {}
    const sortie = {}
    for (const [cle, valeur] of Object.entries(o)) {
      const adresse = String(valeur ?? '').trim()
      // Moins de vingt caractères n'est une adresse sur aucune des chaînes
      // traitées : c'est un champ à moitié rempli.
      if (adresse.length >= 20) sortie[String(cle).trim().toUpperCase()] = adresse
    }
    return sortie
  } catch (_) {
    return {}
  }
}

/**
 * Reçoit un ordre et le résume sur Telegram.
 *
 * NE DÉCIDE RIEN, N'AUTORISE RIEN. Il met en forme et transmet. C'est le
 * changeur qui décide, après avoir vu l'argent sur son compte.
 *
 * Le message porte une MISE EN GARDE EXPLICITE, à chaque fois. Elle
 * paraîtra répétitive au bout de cent ordres, et c'est exactement pour le
 * centième qu'elle est là : celui où l'on est pressé, où la capture d'écran
 * a l'air vraie, et où l'on envoie sans vérifier.
 */
async function ordreChange(requete, env) {
  const token = env?.TG_CHANGE_TOKEN || env?.TG_ADMIN_TOKEN
  const chat = env?.TG_CHANGE_CHAT || env?.TG_ADMIN_CHAT
  if (!token || !chat) {
    return json({ ok: false, raison: 'canal non configure' }, 0, 503)
  }

  let corps = null
  try {
    corps = await requete.json()
  } catch (_) {
    return json({ ok: false, raison: 'corps illisible' }, 0, 400)
  }

  const champ = (v, max) => String(v ?? '').trim().slice(0, max)
  const ordre = {
    reference: champ(corps.reference, 24),
    sens: champ(corps.sens, 8),
    monnaie: champ(corps.monnaie, 12),
    montantFcfa: champ(corps.montantFcfa, 20),
    montantCrypto: champ(corps.montantCrypto, 32),
    taux: champ(corps.taux, 32),
    marge: champ(corps.marge, 32),
    adresse: champ(corps.adresse, 128),
    telephone: champ(corps.telephone, 24),
    referencePaiement: champ(corps.referencePaiement, 48),
  }
  if (!ordre.reference || !ordre.sens || !ordre.monnaie || !ordre.montantFcfa) {
    return json({ ok: false, raison: 'ordre incomplet' }, 0, 400)
  }

  /*
  LIMITE DE DÉBIT, PAR RÉFÉRENCE.

  Cette adresse est publique, donc n'importe qui peut y poster. On ne peut
  pas l'empêcher — l'application est distribuée, et aucun secret qu'elle
  porterait ne resterait secret. Ce qu'on peut faire, c'est empêcher
  d'inonder le canal au point que le changeur n'y travaille plus.

  La même référence ne passe qu'une fois par heure : un renvoi après une
  coupure réseau ne crée pas un doublon, et une boucle ne crée pas mille
  messages.
  */
  const cache = caches.default
  const cle = new Request(`https://relais.vaultex/ordre/${encodeURIComponent(ordre.reference)}`)
  if (await cache.match(cle)) {
    return json({ ok: true, deja: true, reference: ordre.reference })
  }

  /*
  ═══════════════════════════════════════════════════════════════════════
  CE QUE LE CHANGEUR DOIT LIRE POUR AGIR, DANS L'ORDRE OU IL LE LIT
  ═══════════════════════════════════════════════════════════════════════

  UNE CONSIGNE, PAS SEULEMENT DES DONNEES. Le message donnait les chiffres
  et laissait deduire quoi en faire. A la cinquantieme demande, un soir, on
  ne deduit plus : on confond un achat et une vente, et on envoie des
  francs a quelqu'un qui attendait de l'USDT. La ligne « A FAIRE » dit
  l'action, en toutes lettres, et elle est la premiere apres la reference.

  LES LIGNES VIDES SURVIVENT. Elles separaient les trois blocs — identite,
  chiffres, consigne — et `filter(Boolean)` les supprimait toutes : une
  chaine vide est falsy. Le message arrivait en pave compact. On ne filtre
  donc que le nul, pas le vide.
  ═══════════════════════════════════════════════════════════════════════
  */
  const achat = ordre.sens === 'achat'
  const aFaire = achat
    ? `ENVOYER ${ordre.montantCrypto} ${ordre.monnaie} a l'adresse ci-dessous`
    : `ENVOYER ${ordre.montantFcfa} FCFA au ${ordre.telephone || 'numero du client'}`

  const lignes = [
    `\u{1F4B1} DEMANDE DE CHANGE \u00b7 ${ordre.reference}`,
    achat ? 'Le client paie en FCFA, tu envoies la crypto'
          : 'Le client envoie la crypto, tu paies en FCFA',
    '',
    `\u27A1\uFE0F A FAIRE : ${aFaire}`,
    '',
    `Montant   : ${ordre.montantFcfa} FCFA`,
    `Crypto    : ${ordre.montantCrypto} ${ordre.monnaie}`,
    `Taux      : ${ordre.taux}`,
    `Marge     : ${ordre.marge}`,
    ordre.adresse ? `Adresse   : ${ordre.adresse}` : null,
    ordre.telephone ? `Telephone : ${ordre.telephone}` : null,
    ordre.referencePaiement ? `Ref. paiement : ${ordre.referencePaiement}` : null,
    '',
    '\u26A0\uFE0F N\u2019ENVOIE RIEN AVANT D\u2019AVOIR VU L\u2019ARGENT SUR TON',
    'PROPRE COMPTE. Une capture d\u2019\u00e9cran se fabrique en cinq minutes ;',
    'une r\u00e9f\u00e9rence se recopie. Seul ton relev\u00e9 fait foi.',
  ].filter((l) => l !== null)

  try {
    const corpsTg = new URLSearchParams({ chat_id: chat, text: lignes.join('\n') })
    const envoi = await fetch(`https://api.telegram.org/bot${token}/sendMessage`, {
      method: 'POST',
      headers: { 'content-type': 'application/x-www-form-urlencoded' },
      body: corpsTg.toString(),
    })
    if (!envoi.ok) {
      /*
      ON REND LA RAISON DE TELEGRAM, PAS LA NOTRE.

      « telegram a refuse » ne dit rien, alors que Telegram dit toujours
      pourquoi : « chat not found », « bot was kicked from the group »,
      « Unauthorized », « group chat was upgraded to a supergroup chat ».
      Chacune appelle une correction differente, et aucune ne se devine.

      Le champ `description` est une phrase courte et ne contient jamais le
      jeton — il est dans l'URL, pas dans la reponse. On peut donc le
      transmettre sans rien exposer.
      */
      let description = ''
      try {
        const o = JSON.parse(await envoi.text())
        description = String(o?.description ?? '')
      } catch (_) {
        description = ''
      }
      return json(
        {
          ok: false,
          raison: description
            ? `telegram a refuse : ${description}`
            : `telegram a refuse (HTTP ${envoi.status})`,
        },
        0,
        502
      )
    }
  } catch (_) {
    return json({ ok: false, raison: 'telegram injoignable' }, 0, 502)
  }

  // Mémorisé APRÈS l'envoi : un échec doit pouvoir être réessayé.
  await cache.put(cle, new Response('1', { headers: { 'cache-control': 'max-age=3600' } }))
  return json({ ok: true, reference: ordre.reference })
}
