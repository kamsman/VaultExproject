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

═══════════════════════════════════════════════════════════════════════════
LES RÉGLAGES DU CHANGE, ET LEQUEL PORTE LA PREUVE
═══════════════════════════════════════════════════════════════════════════

Settings → Variables and Secrets. Aucun n'est obligatoire : sans eux,
l'écran de change ne propose rien, ce qui est le comportement voulu.

  CHANGE_ACTIF      0 ferme le service en une seconde, sans republier.
  CHANGE_MARGE      Marge en FRANCS PAR DOLLAR (défaut 25).
  CHANGE_MIN        Minimum par opération, en francs (défaut 5000).
  CHANGE_MAX        Plafond par opération, en francs (défaut 50000).
  CHANGE_NUMERO     Numéro Mobile Money du changeur, pour les ACHATS.
  CHANGE_NOM        Son nom, tel qu'il s'affiche.
  CHANGE_OPERATEUR  « Orange Money », « Moov »…
  CHANGE_DELAI      Délai annoncé, en minutes (défaut 30).
  CHANGE_MONNAIES   « USDT,BTC,ETH » — ce qu'il accepte à l'achat.
  TG_CHANGE_TOKEN   Jeton du bot Telegram. SECRET, jamais dans l'APK.
  TG_CHANGE_CHAT    Identifiant du groupe du changeur.
  TRONGRID_KEY      Facultatif : relève le quota de l'API TronGrid.

  CHANGE_ADRESSES   ⚠️ CELUI-LÀ PORTE LA PREUVE DES VENTES.

                    Un objet JSON : {"USDT":"T...","BTC":"bc1..."}.

                    Ce sont les adresses où le changeur reçoit. Deux rôles,
                    et le second est le plus important : l'application les
                    affiche pour que le vendeur sache où envoyer, ET le
                    Worker s'en sert pour CHERCHER le versement sur la
                    chaîne.

                    C'est pourquoi l'adresse ne peut pas venir du
                    téléphone. Si l'application pouvait dire quelle adresse
                    surveiller, n'importe qui donnerait la sienne,
                    s'enverrait huit USDT à lui-même, et tout serait
                    « vérifié ». Elle vient d'ici, et d'ici seulement.

                    Une adresse mal collée se voit sur /diag, qui sonde
                    TronGrid SUR CETTE ADRESSE — et nulle part ailleurs
                    avant une vraie vente.

Aucune donnée personnelle ne transite ici : ni mnémonique, ni identifiant
d'utilisateur. Les adresses du changeur sont publiques — l'application les
montre à chaque vendeur — et les cours le sont aussi.
═══════════════════════════════════════════════════════════════════════════
*/

/** Version du Worker déployé — lisible sur /sante et /diag. */
const VERSION = 14

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

    /*
    LA MÊME VÉRIFICATION, EN LECTURE SEULE.

    L'application interroge ce point d'entrée pendant que l'utilisateur
    attend, et lui dit « transfert trouvé » avant qu'il appuie sur le
    bouton. C'est le même code que celui qui décidera du marquage de son
    ordre : s'il répond vert ici, il répondra vert là.

    IL NE MARQUE RIEN COMME SERVI. Un sondage qui consommerait le txid
    rendrait l'ordre suivant « deja_servi » — l'application se serait
    volé sa propre preuve. Le marquage n'a lieu qu'au dépôt de l'ordre.

    PAS DE CACHE : on y revient toutes les quinze secondes en attendant
    qu'un transfert apparaisse, et une réponse de dix secondes d'âge
    répondrait « pas encore » alors que c'est arrivé.
    */
    if (url.pathname === '/change/verifier') {
      const resultat = await verifierVente({
        monnaie: url.searchParams.get('monnaie'),
        montant: url.searchParams.get('montant'),
        txid: url.searchParams.get('txid'),
        env,
      })
      return json(resultat, 0)
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

  /*
  ═══════════════════════════════════════════════════════════════════════
  LES NOEUDS DE CHAINE, SONDES DEPUIS L'ENDROIT QUI COMPTE
  ═══════════════════════════════════════════════════════════════════════

  La vérification des ventes repose entièrement sur ces hôtes. S'ils ne
  répondent pas depuis un Worker, la vérification rend `indisponible` à
  chaque fois — et le changeur revient au contrôle manuel sans que
  personne ne sache pourquoi.

  Or cette liste de nœuds n'a PAS été mesurée : elle a été choisie sur ce
  qu'on sait du filtrage des centres de données, qui est précisément le
  piège où `api.binance.com` est tombé dans ce même fichier. Des hôtes
  choisis par raisonnement, dans un fichier qui porte déjà la trace d'un
  raisonnement démenti par la mesure.

  Ces sondes-là tranchent. Elles sont ici et pas dans un test : seul le
  Worker déployé est au bon endroit du réseau.

  CE QU'IL FAUT LIRE. Un `code: 200` avec un extrait qui contient
  « result » : le nœud marche. Un 403 : filtrage, l'hôte est inutile et il
  faut réordonner NOEUDS_EVM. Pour TronGrid, un extrait contenant
  « data » : l'API publique répond sans clé.
  ═══════════════════════════════════════════════════════════════════════
  */
  const chaines = {}
  for (const [reseau, hotes] of Object.entries(NOEUDS_EVM)) {
    for (const hote of hotes) {
      const nom = `${reseau}:${hote.replace('https://', '')}`
      try {
        const r = await fetchBorne(
          hote,
          {
            method: 'POST',
            headers: { 'content-type': 'application/json', 'user-agent': AGENT },
            body: JSON.stringify({ jsonrpc: '2.0', id: 1, method: 'eth_blockNumber', params: [] }),
          },
          6000
        )
        chaines[nom] = { code: r.status, extrait: (await r.text()).slice(0, 120) }
      } catch (e) {
        chaines[nom] = { erreur: String(e).slice(0, 120) }
      }
    }
  }

  /*
  TRONGRID EST SONDE SUR L'ADRESSE DU CHANGEUR quand elle est réglée, et
  non sur une adresse d'exemple. Ça teste la chaîne complète d'un coup :
  l'hôte répond, la clé passe, ET l'adresse réglée est lisible. Une
  adresse mal collée — un caractère de trop au copier-coller — se voit
  ici, et nulle part ailleurs avant une vraie vente.
  */
  const adressesPosees = adressesChangeur(env)
  const pourSonde = adressesPosees.USDT || 'TMuA6YqfCeX8EhbfYEg5y7S4DqzSJireY9'
  try {
    const entetes = { accept: 'application/json', 'user-agent': AGENT }
    if (env?.TRONGRID_KEY) entetes['TRON-PRO-API-KEY'] = String(env.TRONGRID_KEY)
    const r = await fetchBorne(
      `${TRONGRID}/v1/accounts/${encodeURIComponent(pourSonde)}` +
        '/transactions/trc20?limit=1&only_to=true&only_confirmed=true',
      { headers: entetes },
      7000
    )
    chaines.trongrid = {
      code: r.status,
      // L'adresse sondée est rendue : elle est publique (l'application la
      // montre à chaque vendeur) et c'est elle qu'on veut confirmer.
      adresse_sondee: pourSonde,
      sur_adresse_reglee: Boolean(adressesPosees.USDT),
      cle_presente: Boolean(env?.TRONGRID_KEY),
      extrait: (await r.text()).slice(0, 200),
    }
  } catch (e) {
    chaines.trongrid = { erreur: String(e).slice(0, 200) }
  }

  try {
    const r = await fetchBorne(
      `${BLOCKSTREAM}/blocks/tip/height`,
      { headers: { 'user-agent': AGENT } },
      7000
    )
    chaines.blockstream = { code: r.status, extrait: (await r.text()).slice(0, 60) }
  } catch (e) {
    chaines.blockstream = { erreur: String(e).slice(0, 120) }
  }

  sondes.chainesVente = chaines
  /*
  QUELLES MONNAIES SONT VRAIMENT VERIFIABLES, croisement de deux réglages.

  Une monnaie est vérifiable si elle figure dans CHAINES_VENTE (on sait
  lire sa chaîne) ET si CHANGE_ADRESSES porte son adresse (on sait quoi
  regarder). Les deux se règlent séparément et on les oublie séparément :
  afficher le croisement évite de chercher pourquoi « USDT-BNB » reste
  éternellement non vérifié alors que le nœud BSC répond.
  */
  sondes.venteVerifiable = Object.keys(CHAINES_VENTE).reduce((acc, m) => {
    acc[m] = {
      chaine_connue: true,
      adresse_reglee: Boolean(adressesPosees[m]),
      verifiable: Boolean(adressesPosees[m]),
    }
    return acc
  }, {})

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

/*
═══════════════════════════════════════════════════════════════════════════
RELIRE UN NOMBRE MIS EN FORME EN FRANÇAIS
═══════════════════════════════════════════════════════════════════════════

L'application envoie du texte déjà présentable — « 5 000 » et « 8,33 » —
parce que le message Telegram est lu par un humain. Pour vérifier, il faut
le relire en nombre, et c'est là que ça se gâte.

`parseFloat('5 000')` rend 5. Pas une erreur, pas un NaN : CINQ. Une vente
de 5 000 francs se chercherait donc comme une vente de 5, aucun versement
ne correspondrait, et la vérification dirait « introuvable » sur une
opération parfaitement honnête. Le défaut serait passé pour une panne de
TronGrid.

TROIS ESPACES, ET PAS UNE.

`NumberFormat` français sépare les milliers par U+202F (espace fine
insécable) sur les ICU récents, par U+00A0 sur les plus anciens, et par une
espace ordinaire ailleurs. Laquelle arrive dépend de la version d'Android
du téléphone. On les écarte donc toutes les trois — tester avec la seule
espace ordinaire aurait marché sur l'émulateur et échoué sur l'appareil.

La virgule devient un point, et c'est tout : ce qui reste doit être un
nombre, sinon on rend zéro, et zéro ne vérifie rien.
═══════════════════════════════════════════════════════════════════════════
*/
function nombreFrancais(v) {
  const nu = String(v ?? '')
    .replace(/[\s\u00a0\u202f\u2009]/g, '')
    .replace(',', '.')
  if (!/^[0-9]*\.?[0-9]+$/.test(nu)) return 0
  const n = parseFloat(nu)
  return Number.isFinite(n) && n > 0 ? n : 0
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
    /*
    LE TXID N'EST QU'UN POINTEUR, et c'est pour ça qu'on peut l'accepter
    d'un téléphone. Il dit QUELLE transaction regarder ; il ne dit pas ce
    qu'elle contient. Le montant, le destinataire et le contrat du jeton
    sont lus sur la chaîne, par le Worker. Désigner la transaction de
    quelqu'un d'autre ne sert donc à rien d'autre qu'à la faire marquer
    comme servie.
    */
    txid: champ(corps.txid, 96),
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

  /*
  ═══════════════════════════════════════════════════════════════════════
  UNE VENTE SE VÉRIFIE, UN ACHAT NON
  ═══════════════════════════════════════════════════════════════════════

  Dans une vente, l'utilisateur envoie de la crypto : ça se lit sur une
  chaîne publique, et le Worker le lit lui-même.

  Dans un achat, il envoie des francs par Orange Money. Aucune chaîne
  publique ne porte ça, et VaultEx n'a pas accès aux relevés de l'opérateur
  — c'est le changeur qui regarde son propre téléphone. Vérifier l'un et
  pas l'autre n'est donc pas une asymétrie de soin, c'est la nature des
  deux réseaux.
  ═══════════════════════════════════════════════════════════════════════
  */
  let verif = null
  let coherence = null
  if (!achat) {
    verif = await verifierVente({
      monnaie: ordre.monnaie,
      // Le montant vient du téléphone, et il SERT DE CRITÈRE DE RECHERCHE,
      // pas de preuve : on cherche un versement qui lui ressemble, et c'est
      // le montant LU SUR LA CHAÎNE qui est ensuite affiché au changeur.
      montant: nombreFrancais(ordre.montantCrypto),
      txid: ordre.txid,
      env,
    })
    coherence = await coherenceFcfa(
      ordre.monnaie,
      nombreFrancais(ordre.montantCrypto),
      nombreFrancais(ordre.montantFcfa),
      env
    )
  }

  const lignes = messageOrdre(ordre, verif, coherence)

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

  /*
  LE TXID EST MARQUÉ ICI, ET PAS PLUS TÔT.

  Plus tôt — au moment de la vérification — un échec Telegram aurait brûlé
  la preuve de l'utilisateur : son ordre n'est pas parti, et son transfert
  est désormais « déjà servi ». Il n'aurait plus aucun moyen de redéposer
  sa demande.

  Ici, le changeur a le message. Le transfert a effectivement payé quelque
  chose, et peut cesser de pouvoir payer autre chose.
  */
  if (verif && verif.etat === 'confirme' && verif.txid) {
    await marquerTxidServi(verif.txid)
  }

  return json({
    ok: true,
    reference: ordre.reference,
    // Rendu à l'application pour qu'elle dise à l'utilisateur ce que le
    // changeur voit : « ta demande est partie, et elle est partie vérifiée ».
    verification: verif ? verif.etat : null,
    txid: verif && verif.etat === 'confirme' ? verif.txid : '',
  })
}

/**
 * Le message complet, tel qu'il arrive dans le canal du changeur.
 *
 * SORTIE DE ordreChange POUR ÊTRE AFFICHABLE. Le rendu de ce message s'est
 * déjà payé deux fois : une ligne « Frais : 25 FCFA/$ » lue comme un
 * montant, et un `filter(Boolean)` qui avalait les lignes vides et rendait
 * un pavé compact. Les deux se voyaient en une seconde en REGARDANT le
 * message, et pas du tout en relisant le code.
 *
 * tools/relais-cours/rendu-message.mjs l'imprime pour les quatre états.
 */
function messageOrdre(ordre, verif, coherence) {
  const achat = ordre.sens === 'achat'
  /*
  ═══════════════════════════════════════════════════════════════════════
  LA CONSIGNE CHANGE AVEC LA VÉRIFICATION, ET C'EST LE COEUR DE L'APPORT
  ═══════════════════════════════════════════════════════════════════════

  « ENVOYER 5 000 FCFA » sur une vente dont le transfert est introuvable
  serait une consigne dangereuse, lue vite un soir de forte activité. Elle
  ne doit apparaître que quand la chaîne a confirmé — sinon la ligne la
  plus visible du message dit d'envoyer de l'argent que rien ne justifie.

  ─── IL FAUT LES DEUX FEUX VERTS, ET JE L'AI VU EN REGARDANT ──────────

  Ma première version ne regardait que la vérification on-chain. Sur un
  ordre « 8 USDT reçus, paie 500 000 FCFA » — transfert parfaitement réel,
  montant cent fois trop élevé — elle écrivait donc en haut du message, à
  l'endroit le plus visible : « A FAIRE : ENVOYER 500 000 FCFA ». L'alarme
  d'écart de prix était bien là, trois lignes plus bas.

  Ça ne se voyait pas en relisant le code. Ça sautait aux yeux en
  imprimant le message, ce qui est exactement la raison d'être de
  rendu-message.mjs.

  La consigne d'envoyer exige maintenant que la chaîne confirme ET que le
  prix tienne debout. Un seul des deux qui manque, et le message dit
  d'attendre.
  ═══════════════════════════════════════════════════════════════════════
  */
  const prixDouteux = Boolean(coherence && coherence.etat === 'ecart')
  const verseVraiment = Boolean(verif && verif.etat === 'confirme') && !prixDouteux
  const aFaire = achat
    ? `ENVOYER ${ordre.montantCrypto} ${ordre.monnaie} a l'adresse ci-dessous`
    : verseVraiment
      ? `ENVOYER ${ordre.montantFcfa} FCFA au ${ordre.telephone || 'numero du client'}`
      : `NE RIEN ENVOYER POUR L'INSTANT - lis les lignes du bas`

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
    ...lignesVerification(verif, coherence, ordre),
    '',
    '\u26A0\uFE0F N\u2019ENVOIE RIEN AVANT D\u2019AVOIR VU L\u2019ARGENT SUR TON',
    'PROPRE COMPTE. Une capture d\u2019\u00e9cran se fabrique en cinq minutes ;',
    'une r\u00e9f\u00e9rence se recopie. Seul ton relev\u00e9 fait foi.',
  ].filter((l) => l !== null)

  return lignes

}

/**
 * Le bloc de vérification, tel que le changeur le lit.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * UN SYMBOLE, PUIS CE QU'IL FAUT FAIRE
 * ═══════════════════════════════════════════════════════════════════════
 *
 * À la cinquantième demande, un soir, on ne lit pas un paragraphe : on
 * lit la couleur. Chaque état commence donc par un symbole différent —
 * ✅ ❌ 🚨 ⚠️ — et la ligne suivante dit l'action, pas le diagnostic.
 *
 * LE MONTANT RENDU EST CELUI DE LA CHAÎNE, et le message le précise. Si le
 * téléphone avait annoncé 8 USDT et que la chaîne en montre 0,8, c'est
 * 0,8 qui s'affiche — avec la mention « lu sur la chaine ». Sans cette
 * précision, le changeur croirait relire le chiffre du client.
 * ═══════════════════════════════════════════════════════════════════════
 */
function lignesVerification(verif, coherence, ordre) {
  const lignes = []

  /*
  L'ÉCART DE PRIX PASSE AVANT TOUT LE RESTE.

  Un transfert peut être parfaitement réel et l'ordre absurde : « 8 USDT
  reçus, paie 500 000 FCFA ». La vérification on-chain dirait ✅, et le
  changeur paierait cent fois trop. Cette ligne vient donc en premier,
  avant même le résultat de la chaîne.
  */
  if (coherence && coherence.etat === 'ecart') {
    lignes.push('')
    lignes.push('\u{1F6A8} ECART DE PRIX - NE PAIE PAS SANS RECALCULER')
    lignes.push(`Annonce : ${ordre.montantFcfa} FCFA`)
    lignes.push(
      `Marche  : ~${groupeMilliers(coherence.attendu)} FCFA  ` +
        `(ecart ${coherence.ecartPourcent} %)`
    )
  }

  if (!verif) return lignes

  if (verif.etat === 'confirme') {
    lignes.push('')
    lignes.push('\u2705 VERSEMENT VERIFIE SUR LA CHAINE PAR LE RELAIS')
    lignes.push(`Recu      : ${formatMontant(verif.montant)} ${ordre.monnaie}  (lu sur la chaine)`)
    if (verif.quand) lignes.push(`Horodate  : ${dateCourteUtc(verif.quand)}`)
    if (verif.confirme === false) {
      lignes.push('Bloc      : PAS ENCORE MINE - attends une confirmation')
    }
    if (verif.txid) lignes.push(`Txid      : ${verif.txid}`)
    if (verif.explorateur) lignes.push(`Voir      : ${verif.explorateur}`)
    /*
    LE MONTANT LU CONTRE LE MONTANT ANNONCÉ.

    La tolérance de recherche est d'un pour cent : un versement de 7,95
    passe pour une demande de 8. L'écart est minuscule et il est réel, et
    c'est le changeur qui paie la différence. On le lui dit.
    */
    const annonce = nombreFrancais(ordre.montantCrypto)
    if (annonce > 0 && verif.montant > 0 && verif.montant < annonce * 0.999) {
      lignes.push(
        `\u26A0\uFE0F Recu MOINS qu'annonce (${formatMontant(verif.montant)} au lieu de ` +
          `${ordre.montantCrypto}) - paie au prorata`
      )
    }
    return lignes
  }

  if (verif.etat === 'deja_servi') {
    lignes.push('')
    lignes.push('\u{1F6A8} CE TRANSFERT A DEJA SERVI UNE AUTRE DEMANDE')
    lignes.push(`Txid : ${verif.txid || 'inconnu'}`)
    lignes.push('N\u2019ENVOIE RIEN. Cherche ce txid dans les demandes recentes :')
    lignes.push('soit c\u2019est un doublon, soit quelqu\u2019un reutilise un vrai')
    lignes.push('versement pour se faire payer deux fois.')
    return lignes
  }

  if (verif.etat === 'absent') {
    lignes.push('')
    lignes.push('\u274C AUCUN VERSEMENT TROUVE SUR LA CHAINE')
    lignes.push(`Le relais a regarde ton adresse ${ordre.monnaie} : ${verif.raison}.`)
    lignes.push('N\u2019ENVOIE RIEN MAINTENANT. Deux causes possibles, et une')
    lignes.push('seule demande d\u2019agir : le retrait du client est encore en')
    lignes.push('file chez son exchange (ca arrive, regarde dans 15 min), ou')
    lignes.push('il n\u2019a rien envoye.')
    return lignes
  }

  // indisponible / inconnue : on n'a pas regardé, et on le dit sans
  // l'habiller. Le changeur revient à la vérification manuelle, qui est
  // ce qu'il faisait avant — mais il sait qu'il doit la faire.
  lignes.push('')
  lignes.push('\u26A0\uFE0F VERIFICATION AUTOMATIQUE IMPOSSIBLE')
  lignes.push(`Raison : ${verif.raison || 'inconnue'}.`)
  lignes.push('Rien n\u2019est prouve ici. Verifie ton portefeuille comme avant.')
  return lignes
}

/**
 * « 500000 » devient « 500 000 ».
 *
 * À LA MAIN, ET NON PAR Intl. Tous les autres montants du message
 * arrivent déjà groupés — l'application les met en forme avant d'envoyer.
 * Celui-ci est le seul que le Worker calcule, et il se retrouvait nu au
 * milieu des autres : « 5000 » à côté de « 5 000 », dans la ligne même qui
 * sert à comparer les deux. Difficile de comparer deux nombres écrits
 * différemment.
 */
function groupeMilliers(v) {
  const n = Math.round(Number(v))
  if (!Number.isFinite(n)) return '0'
  return String(Math.abs(n))
    .replace(/\B(?=(\d{3})+(?!\d))/g, ' ')
    .replace(/^/, n < 0 ? '-' : '')
}

/** Un montant lisible : assez de décimales pour être exact, pas plus. */
function formatMontant(v) {
  const n = Number(v)
  if (!Number.isFinite(n) || n <= 0) return '0'
  /*
  Huit décimales suffisent pour toutes les chaînes traitées, et
  `parseFloat` retire les zéros de fin — « 8.00000000 » redevient « 8 ».
  Sans ça, un versement rond s'afficherait avec une traîne de zéros qui
  fait hésiter sur l'unité.
  */
  return String(parseFloat(n.toFixed(8)))
}

/**
 * « 09/10 18:52 ».
 *
 * EN UTC, ET C'EST L'HEURE LOCALE. Le Burkina Faso est à UTC+0 toute
 * l'année : pas de décalage à appliquer, pas d'heure d'été. Un Worker n'a
 * de toute façon pas de fuseau local — `toLocaleString` y rend de l'UTC
 * en se faisant passer pour autre chose.
 */
function dateCourteUtc(ms) {
  const d = new Date(Number(ms))
  if (Number.isNaN(d.getTime())) return ''
  const deux = (n) => String(n).padStart(2, '0')
  return (
    `${deux(d.getUTCDate())}/${deux(d.getUTCMonth() + 1)} ` +
    `${deux(d.getUTCHours())}:${deux(d.getUTCMinutes())}`
  )
}

/*
═══════════════════════════════════════════════════════════════════════════
LA VÉRIFICATION ON-CHAIN DES VENTES
═══════════════════════════════════════════════════════════════════════════

Jusqu'ici, une vente reposait sur une phrase : « j'ai envoyé ». Le changeur
devait ouvrir son propre portefeuille et chercher. Pour l'utilisateur,
c'était pire : il avait envoyé de la crypto — irréversible — et n'avait
qu'une référence recopiée à la main pour le prouver.

Or une vente, contrairement à un achat, est VÉRIFIABLE PAR N'IMPORTE QUI.
Les fonds vont sur une chaîne publique. Personne n'a besoin de croire :
il suffit de regarder.

─── POURQUOI CE CODE EST ICI ET PAS DANS L'APPLICATION ──────────────────

Parce qu'une vérification faite par le téléphone ne prouve rien. L'APK se
décompile ; on remplace « le transfert existe » par « oui » et le changeur
reçoit un ordre vérifié qui ne l'est pas. Une preuve calculée par la partie
qu'elle doit convaincre n'est pas une preuve.

Le Worker, lui, le téléphone ne le contrôle pas. Il interroge la chaîne
lui-même, et lit LE MONTANT ET LE DESTINATAIRE SUR LA CHAÎNE — jamais dans
la requête. Le téléphone ne fournit au mieux qu'un txid, c'est-à-dire un
pointeur : il peut désigner la mauvaise transaction, il ne peut pas en
inventer le contenu.

─── L'ADRESSE DU CHANGEUR NE VIENT JAMAIS DU TÉLÉPHONE ──────────────────

Elle vient de CHANGE_ADRESSES, un réglage du Worker. Si l'application
pouvait dire « vérifie les versements vers cette adresse », il suffirait de
donner la sienne et tout serait « vérifié ».

─── QUATRE RÉPONSES, ET IL FAUT LES QUATRE ──────────────────────────────

  confirme      — la chaîne montre le transfert. On dit ce qu'on a lu.
  absent        — on a regardé, il n'y est pas.
  deja_servi    — le transfert existe, mais il a déjà payé une demande.
  indisponible  — on n'a pas pu regarder : nœud muet, monnaie non traitée,
                  adresse non réglée.

La différence entre `absent` et `indisponible` est tout ce module. Les
confondre, c'est soit soupçonner des ventes honnêtes chaque fois qu'un nœud
public tousse, soit annoncer « vérifié » quand on n'a rien vérifié. La
deuxième est la pire : elle déplace le risque sur le changeur en lui
faisant croire le contraire.

─── AUCUN ÉTAT NE BLOQUE L'ORDRE, ET C'EST RÉFLÉCHI ─────────────────────

Refuser de transmettre une vente non vérifiée était tentant : le canal du
changeur resterait propre. C'est le mauvais choix, pour une raison de
chronologie.

Quand l'utilisateur arrive ici, SES FONDS SONT DÉJÀ PARTIS. C'est
irréversible. Lui refuser le dépôt de sa demande parce que sa plateforme
d'échange a mis quarante minutes à exécuter son retrait, c'est le laisser
avec de la crypto envoyée et rien de déposé chez le changeur — exactement
la situation qu'on cherche à supprimer.

L'ordre passe donc toujours, et il porte son état en toutes lettres : ✅
vérifié, ❌ introuvable, ⚠️ non vérifiable. Le changeur y gagne quand même
l'essentiel : aujourd'hui il doit tout contrôler, demain il ne contrôle
vraiment que ce qui n'est pas vert.

L'écran, lui, pousse à attendre avant de transmettre quand c'est ❌. Freiner
dans l'application se corrige en revenant dix minutes plus tard ; bloquer
dans le relais ne se corrige pas.

─── CE QUE ÇA NE FAIT PAS ───────────────────────────────────────────────

Ça ne dispense pas le changeur de regarder son portefeuille. La règle reste
écrite dans chaque message. Ce que ça change : elle passe de « seule
protection » à « deuxième protection ».
═══════════════════════════════════════════════════════════════════════════
*/

/*
LES MONNAIES VÉRIFIABLES, ET CE QU'IL FAUT POUR CHACUNE.

UNE MONNAIE ABSENTE DE CETTE TABLE N'EST PAS BLOQUÉE : elle rend
`indisponible`, et la vente passe en étant marquée non vérifiée. C'est le
choix inverse de celui qui consisterait à n'accepter que le vérifiable —
lequel ferait disparaître des monnaies que le changeur accepte.

LE CONTRAT EST VÉRIFIÉ, ET C'EST LE POINT LE PLUS IMPORTANT DE CETTE TABLE.
N'importe qui déploie en dix minutes un jeton appelé « USDT » avec six
décimales. Sans comparer l'adresse du contrat, « 5 000 USDT reçus » peut
être cinq mille jetons sans valeur, et la vérification sert alors à voler le
changeur en lui donnant confiance. Le symbole ne prouve RIEN ; le contrat,
tout.
*/
const CHAINES_VENTE = {
  USDT: {
    chaine: 'tron',
    contrat: 'TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t',
    decimales: 6,
    explorateur: 'https://tronscan.org/#/transaction/',
  },
  'USDT-TRX': {
    chaine: 'tron',
    contrat: 'TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t',
    decimales: 6,
    explorateur: 'https://tronscan.org/#/transaction/',
  },
  'USDT-BNB': {
    chaine: 'evm',
    reseau: 'bnb',
    contrat: '0x55d398326f99059fF775485246999027B3197955',
    // DIX-HUIT, pas six. L'USDT de BNB Chain n'a pas les décimales de
    // l'USDT d'Ethereum, et l'erreur se voit mal : un facteur mille
    // milliards passe pour un montant « bizarre », pas pour un défaut.
    decimales: 18,
    explorateur: 'https://bscscan.com/tx/',
  },
  BNB: {
    chaine: 'evm',
    reseau: 'bnb',
    natif: true,
    decimales: 18,
    explorateur: 'https://bscscan.com/tx/',
  },
  'USDT-ETH': {
    chaine: 'evm',
    reseau: 'eth',
    contrat: '0xdAC17F958D2ee523a2206206994597C13D831ec7',
    decimales: 6,
    explorateur: 'https://etherscan.io/tx/',
  },
  ETH: {
    chaine: 'evm',
    reseau: 'eth',
    natif: true,
    decimales: 18,
    explorateur: 'https://etherscan.io/tx/',
  },
  BTC: {
    chaine: 'btc',
    decimales: 8,
    explorateur: 'https://blockstream.info/tx/',
  },
}

/*
NŒUDS PUBLICS, PAR ORDRE DE PRÉFÉRENCE.

Le premier qui répond gagne, exactement comme HOTES_BINANCE — et pour la
même raison, apprise dans ce fichier : Binance refuse les requêtes venant
d'un centre de données, et Cloudflare en est un. `bsc-dataseed.binance.org`
est donc volontairement ABSENT de cette liste, bien qu'il soit le nœud BSC
le plus cité.

CES HÔTES N'ONT PAS ÉTÉ MESURÉS DEPUIS LE WORKER DÉPLOYÉ. /diag les sonde
tous et dit lesquels répondent : c'est la mesure qui tranche, pas cette
liste. Le jour où l'un se ferme, la sonde le montre et l'ordre se corrige
sans toucher au reste.
*/
const NOEUDS_EVM = {
  bnb: [
    'https://bsc-rpc.publicnode.com',
    'https://bsc.drpc.org',
    'https://binance.llamarpc.com',
  ],
  eth: [
    'https://ethereum-rpc.publicnode.com',
    'https://eth.drpc.org',
    'https://eth.llamarpc.com',
  ],
}

const TRONGRID = 'https://api.trongrid.io'
const BLOCKSTREAM = 'https://blockstream.info/api'

/*
TROIS HEURES.

Un transfert plus ancien ne peut pas servir de preuve pour une demande
qu'on dépose maintenant — sinon un vrai versement d'hier justifierait
dix demandes aujourd'hui.

Trois heures et non trente minutes : quelqu'un envoie depuis une plateforme
d'échange, le retrait est mis en file, et il revient dans l'application une
heure plus tard. Lui refuser sa preuve parce que son exchange a été lent
serait lui faire perdre ses fonds.
*/
const FENETRE_VENTE_MS = 3 * 60 * 60 * 1000

/*
UN POUR CENT.

Les trois chaînes traitées ne prélèvent pas les frais SUR le montant
transféré : ce qui part est ce qui arrive. Un écart devrait donc être nul.

La tolérance n'est pas là pour les frais, elle est là pour l'arrondi
d'affichage — quelqu'un à qui on montre « 8,33 USDT » envoie 8,33, pas
8,333333. Au-delà d'un pour cent, ce n'est plus un arrondi, et le montant
réellement lu sur la chaîne est de toute façon affiché au changeur.
*/
const TOLERANCE_MONTANT = 0.01

/** Vrai si deux montants se valent, à [TOLERANCE_MONTANT] près. */
function montantProche(lu, attendu) {
  if (!(lu > 0) || !(attendu > 0)) return false
  return Math.abs(lu - attendu) <= attendu * TOLERANCE_MONTANT
}

/**
 * Un entier décimal depuis une chaîne hexadécimale de la chaîne.
 *
 * ZÉRO PLUTÔT QU'UNE EXCEPTION. `BigInt('0x')` lève — et un nœud qui rend
 * `data: '0x'` sur un journal mal formé ferait alors échouer la boucle
 * ENTIÈRE, donc perdre le bon journal qui venait après. Une valeur
 * illisible doit écarter sa ligne, pas la vérification.
 */
function entierDepuisHex(h) {
  const t = String(h ?? '').trim()
  if (!/^0x[0-9a-fA-F]+$/.test(t)) return '0'
  try {
    return BigInt(t).toString()
  } catch (_) {
    return '0'
  }
}

/** Un entier en base 10 depuis une chaîne d'unités brutes, divisé par 10^d. */
function depuisUnites(brut, decimales) {
  /*
  BigInt, et non Number.

  18 décimales font des entiers de vingt chiffres. `Number` en garde
  quinze : 1 000 000 000 000 000 001 et 1 000 000 000 000 000 000 y sont
  le MÊME nombre. On divise donc en entier, et on ne passe en flottant
  qu'après — sur une valeur qui tient.
  */
  try {
    const n = BigInt(String(brut).trim())
    if (n < 0n) return 0
    const d = BigInt(10) ** BigInt(decimales)
    const entier = n / d
    const reste = n % d
    return Number(entier) + Number(reste) / Number(d)
  } catch (_) {
    return 0
  }
}

/*
───────────────────────────────────────────────────────────────────────────
LE TRI, SÉPARÉ DE L'APPEL RÉSEAU
───────────────────────────────────────────────────────────────────────────

Les trois fonctions qui suivent ne touchent pas au réseau : on leur donne
ce que la chaîne a répondu, elles disent quel transfert correspond — ou
aucun. C'est délibéré, et c'est ce qui rend la partie dangereuse testable.

Car le danger n'est pas dans le `fetch`. Il est dans une comparaison
d'adresses faite sans tenir compte de la casse sur une chaîne où elle
compte, dans des décimales devinées, dans un contrat qu'on oublie de
vérifier. Aucune de ces trois fautes ne lève d'erreur : elles rendent
simplement « vérifié » quelque chose qui ne l'est pas.

Voir tools/relais-cours/test-verification.mjs, qui les exécute sur des
réponses forgées — y compris celles d'un attaquant.
───────────────────────────────────────────────────────────────────────────
*/

/**
 * Le transfert TRC-20 qui correspond, parmi ceux qu'a rendus TronGrid.
 *
 * LA CASSE COMPTE SUR TRON. Une adresse base58 « TR7NH… » n'est pas la
 * même chaîne que « tr7nh… », et la comparer sans casse ouvre la porte à
 * une adresse voisine qui ne diffère que par là. On compare donc à
 * l'identique, après trim.
 */
function choisirTransfertTron(liste, critere) {
  const { adresse, contrat, montant, txid, maintenant } = critere
  if (!Array.isArray(liste)) return null

  for (const t of liste) {
    const ident = String(t?.transaction_id ?? '').trim()
    if (!ident) continue
    if (txid && ident.toLowerCase() !== String(txid).trim().toLowerCase()) continue

    // Le destinataire : l'adresse du changeur, telle qu'elle est réglée
    // dans le Worker. Jamais celle que la requête aurait fournie.
    if (String(t?.to ?? '').trim() !== String(adresse).trim()) continue

    /*
    LE CONTRAT. Sans cette ligne, un jeton nommé « USDT » déployé le matin
    passerait pour de l'USDT. `token_info.address` est le seul champ qui
    identifie vraiment le jeton.

    S'IL MANQUE, ON REFUSE au lieu de se rabattre sur le symbole : une
    vérification qu'on ne peut pas faire doit se dire `indisponible`, pas
    s'arranger.
    */
    const info = t?.token_info || {}
    const contratLu = String(info?.address ?? '').trim()
    if (!contratLu || contratLu !== String(contrat).trim()) continue

    /*
    LES DÉCIMALES VIENNENT DE LA CHAÎNE, et on les compare aux nôtres.
    Les prendre sans vérifier laisserait un faux jeton à 18 décimales se
    faire lire avec 6 — soit mille milliards de fois trop.
    */
    const decimales = Number(info?.decimals)
    if (!Number.isInteger(decimales) || decimales !== critere.decimales) continue

    const quand = Number(t?.block_timestamp)
    if (!Number.isFinite(quand) || quand <= 0) continue
    if (maintenant - quand > FENETRE_VENTE_MS) continue
    // Une horodate dans le futur n'existe pas sur une chaîne : cinq
    // minutes d'avance tolérées pour la dérive d'horloge, pas plus.
    if (quand - maintenant > 5 * 60 * 1000) continue

    const lu = depuisUnites(t?.value, decimales)
    if (!montantProche(lu, montant)) continue

    return { txid: ident, montant: lu, quand, de: String(t?.from ?? '').trim() }
  }
  return null
}

/** Le topic du journal `Transfer(address,address,uint256)`, identique partout. */
const TOPIC_TRANSFER =
  '0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef'

/** Une adresse EVM telle qu'elle apparaît dans un topic : 32 octets. */
function topicDepuisAdresse(adresse) {
  const nue = String(adresse).trim().toLowerCase().replace(/^0x/, '')
  return '0x' + nue.padStart(64, '0')
}

/**
 * Le transfert ERC-20 lu dans les journaux d'un reçu de transaction.
 *
 * ON LIT LE JOURNAL, PAS LES DONNÉES D'APPEL. Un appel à `transfer` qui
 * échoue laisse quand même ses données dans la transaction ; seul le
 * journal est émis par le contrat lui-même, et seulement si le transfert a
 * réellement eu lieu. Les lire, c'est la différence entre « quelqu'un a
 * demandé un virement » et « le virement a eu lieu ».
 */
function choisirJournalErc20(recu, critere) {
  const { adresse, contrat, montant } = critere
  // Un reçu dont le statut n'est pas 1 est une transaction qui a ÉCHOUÉ.
  // Elle existe, elle a coûté des frais, et elle n'a rien transféré.
  if (String(recu?.status ?? '').toLowerCase() !== '0x1') return null
  const journaux = Array.isArray(recu?.logs) ? recu.logs : []
  const attenduTo = topicDepuisAdresse(adresse)
  const contratBas = String(contrat).trim().toLowerCase()

  for (const j of journaux) {
    if (String(j?.address ?? '').toLowerCase() !== contratBas) continue
    const topics = Array.isArray(j?.topics) ? j.topics : []
    if (topics.length < 3) continue
    if (String(topics[0]).toLowerCase() !== TOPIC_TRANSFER) continue
    if (String(topics[2]).toLowerCase() !== attenduTo) continue

    const lu = depuisUnites(entierDepuisHex(j?.data), critere.decimales)
    if (!montantProche(lu, montant)) continue
    return { montant: lu, de: '0x' + String(topics[1]).slice(-40) }
  }
  return null
}

/**
 * Le versement natif (BNB, ETH) d'une transaction, si c'est bien le bon.
 *
 * `status` vient du reçu et non de la transaction : une transaction
 * existe même quand elle a échoué, et une transaction échouée n'a rien
 * déplacé.
 */
function choisirNatifEvm(tx, recu, critere) {
  if (String(recu?.status ?? '').toLowerCase() !== '0x1') return null
  if (String(tx?.to ?? '').toLowerCase() !== String(critere.adresse).trim().toLowerCase()) {
    return null
  }
  const lu = depuisUnites(entierDepuisHex(tx?.value), critere.decimales)
  if (!montantProche(lu, critere.montant)) return null
  return { montant: lu, de: String(tx?.from ?? '').toLowerCase() }
}

/**
 * La sortie d'une transaction Bitcoin qui paie l'adresse attendue.
 *
 * ON ADDITIONNE LES SORTIES vers cette adresse au lieu d'en prendre une.
 * Un portefeuille peut découper un paiement en deux sorties vers le même
 * destinataire ; n'en lire qu'une annoncerait la moitié du montant reçu,
 * et le changeur paierait la moitié de ce qu'il devait.
 */
function choisirSortieBtc(tx, critere) {
  const sorties = Array.isArray(tx?.vout) ? tx.vout : []
  const attendue = String(critere.adresse).trim()
  let satoshis = 0n
  for (const s of sorties) {
    if (String(s?.scriptpubkey_address ?? '').trim() !== attendue) continue
    try { satoshis += BigInt(String(s?.value ?? '0')) } catch (_) { /* sortie illisible */ }
  }
  if (satoshis <= 0n) return null
  const lu = depuisUnites(satoshis.toString(), critere.decimales)
  if (!montantProche(lu, critere.montant)) return null
  return { montant: lu, confirme: Boolean(tx?.status?.confirmed) }
}

/*
───────────────────────────────────────────────────────────────────────────
LES APPELS AUX CHAÎNES
───────────────────────────────────────────────────────────────────────────

Chacun rend soit un transfert, soit null (« regardé, pas trouvé »), soit
lève (« pas pu regarder »). Cette distinction remonte jusqu'à la réponse
faite au changeur : `absent` refuse l'ordre, `indisponible` le laisse
passer en le marquant. Les confondre serait soit bloquer des ventes
honnêtes quand un nœud tombe, soit annoncer vérifié sans l'avoir fait.
───────────────────────────────────────────────────────────────────────────
*/

/** Un délai borné : un nœud lent ne doit pas faire expirer la requête. */
async function fetchBorne(url, options, millisecondes) {
  const stop = new AbortController()
  const minuteur = setTimeout(() => stop.abort(), millisecondes || 6000)
  try {
    return await fetch(url, { ...(options || {}), signal: stop.signal })
  } finally {
    clearTimeout(minuteur)
  }
}

/** Un appel JSON-RPC sur le premier nœud qui répond. */
async function appelEvm(reseau, methode, params) {
  const noeuds = NOEUDS_EVM[reseau] || []
  let derniere = null
  for (const hote of noeuds) {
    try {
      const r = await fetchBorne(
        hote,
        {
          method: 'POST',
          headers: { 'content-type': 'application/json', 'user-agent': AGENT },
          body: JSON.stringify({ jsonrpc: '2.0', id: 1, method: methode, params: params || [] }),
        },
        6000
      )
      if (!r.ok) { derniere = new Error(`HTTP ${r.status}`); continue }
      const o = await r.json()
      if (o?.error) { derniere = new Error(String(o.error?.message ?? 'rpc')); continue }
      // `result: null` est une RÉPONSE VALIDE : « cette transaction est
      // inconnue de ce nœud ». On la rend telle quelle, sans basculer sur
      // le nœud suivant — une transaction inexistante l'est partout.
      return o?.result ?? null
    } catch (e) {
      derniere = e
    }
  }
  throw derniere || new Error('aucun noeud')
}

/** Vérifie un transfert TRC-20 vers l'adresse du changeur. */
async function verifierTron(cfg, critere) {
  /*
  ON INTERROGE L'ADRESSE DU CHANGEUR, et non celle de l'expéditeur.

  Le résultat porte alors une affirmation directement utile : « ce
  versement est arrivé chez toi ». Interroger l'expéditeur dirait « il a
  envoyé quelque part », ce qui n'est pas la même phrase.

  `only_to=true` écarte ses propres versements sortants, qui sont le gros
  de l'activité d'un changeur.
  */
  const url =
    `${TRONGRID}/v1/accounts/${encodeURIComponent(critere.adresse)}` +
    `/transactions/trc20?limit=50&only_to=true&only_confirmed=true`
  const entetes = { accept: 'application/json', 'user-agent': AGENT }
  // Une clé TronGrid relève les limites de débit. Facultative : sans elle
  // l'API publique répond, simplement avec un quota plus bas.
  if (critere.cleTron) entetes['TRON-PRO-API-KEY'] = critere.cleTron
  const r = await fetchBorne(url, { headers: entetes }, 7000)
  if (!r.ok) throw new Error(`trongrid HTTP ${r.status}`)
  const o = await r.json()
  return choisirTransfertTron(o?.data, {
    adresse: critere.adresse,
    contrat: cfg.contrat,
    decimales: cfg.decimales,
    montant: critere.montant,
    txid: critere.txid,
    maintenant: critere.maintenant,
  })
}

/**
 * Vérifie un transfert EVM.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * LE NATIF EXIGE UN TXID, LE JETON NON — ET CE N'EST PAS UN CHOIX
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Un transfert de jeton émet un journal, et `eth_getLogs` retrouve tous
 * les journaux adressés à quelqu'un : on peut donc CHERCHER.
 *
 * Un transfert natif n'émet rien. Le retrouver sans txid demanderait de
 * relire tous les blocs de la fenêtre un par un — des milliers d'appels
 * pour une vérification. Il faut donc que le txid soit fourni.
 *
 * Ce n'est pas une faiblesse : le txid ne sert qu'à DÉSIGNER. Le montant,
 * le destinataire et le succès sont lus sur la chaîne. Un téléphone
 * malveillant peut désigner la transaction de quelqu'un d'autre — d'où le
 * marquage des txid déjà servis.
 * ═══════════════════════════════════════════════════════════════════════
 */
async function verifierEvm(cfg, critere) {
  if (critere.txid) {
    const tx = await appelEvm(cfg.reseau, 'eth_getTransactionByHash', [critere.txid])
    if (!tx) return null
    const recu = await appelEvm(cfg.reseau, 'eth_getTransactionReceipt', [critere.txid])
    if (!recu) return null
    const trouve = cfg.natif
      ? choisirNatifEvm(tx, recu, {
          adresse: critere.adresse,
          montant: critere.montant,
          decimales: cfg.decimales,
        })
      : choisirJournalErc20(recu, {
          adresse: critere.adresse,
          contrat: cfg.contrat,
          montant: critere.montant,
          decimales: cfg.decimales,
        })
    if (!trouve) return null
    return { txid: String(critere.txid).toLowerCase(), montant: trouve.montant, de: trouve.de }
  }

  if (cfg.natif) {
    // Sans txid et sans journal, il n'y a rien à interroger. On le dit,
    // plutôt que de rendre « absent » — ce qui refuserait une vente
    // honnête en laissant croire qu'on a cherché.
    throw new Error('natif sans txid')
  }

  /*
  LA FENÊTRE EST EN BLOCS, et les nœuds publics la bornent — souvent à
  quelques milliers. On reste large sous la limite : deux mille blocs font
  une heure et demie sur BNB Chain, près de sept heures sur Ethereum.
  */
  const hauteur = await appelEvm(cfg.reseau, 'eth_blockNumber', [])
  const dernier = Number(BigInt(entierDepuisHex(hauteur)))
  if (!Number.isFinite(dernier) || dernier <= 0) throw new Error('hauteur illisible')
  const depuis = Math.max(0, dernier - 2000)
  const journaux = await appelEvm(cfg.reseau, 'eth_getLogs', [
    {
      address: cfg.contrat,
      topics: [TOPIC_TRANSFER, null, topicDepuisAdresse(critere.adresse)],
      fromBlock: '0x' + depuis.toString(16),
      toBlock: 'latest',
    },
  ])
  if (!Array.isArray(journaux)) throw new Error('journaux illisibles')
  // Du plus récent au plus ancien : entre deux versements du même montant,
  // c'est le dernier qui correspond à la demande qu'on dépose maintenant.
  for (const j of journaux.slice().reverse()) {
    const trouve = choisirJournalErc20(
      { status: '0x1', logs: [j] },
      {
        adresse: critere.adresse,
        contrat: cfg.contrat,
        montant: critere.montant,
        decimales: cfg.decimales,
      }
    )
    if (trouve) {
      return {
        txid: String(j?.transactionHash ?? '').toLowerCase(),
        montant: trouve.montant,
        de: trouve.de,
      }
    }
  }
  return null
}

/** Vérifie un versement Bitcoin vers l'adresse du changeur. */
async function verifierBtc(cfg, critere) {
  const base = critere.txid
    ? `${BLOCKSTREAM}/tx/${encodeURIComponent(critere.txid)}`
    : `${BLOCKSTREAM}/address/${encodeURIComponent(critere.adresse)}/txs`
  const r = await fetchBorne(base, { headers: { 'user-agent': AGENT } }, 7000)
  // 404 sur un txid, c'est une RÉPONSE : cette transaction n'existe pas.
  if (r.status === 404) return null
  if (!r.ok) throw new Error(`blockstream HTTP ${r.status}`)
  const o = await r.json()
  const liste = Array.isArray(o) ? o : [o]
  for (const tx of liste) {
    const quand = Number(tx?.status?.block_time) * 1000
    // Une transaction non confirmée n'a pas d'heure de bloc. On l'accepte
    // ici — `confirme: false` remonte au changeur, qui voit « 0
    // confirmation » et décide. La refuser ferait attendre dix minutes à
    // quelqu'un dont le versement est déjà public et visible.
    if (Number.isFinite(quand) && quand > 0 && critere.maintenant - quand > FENETRE_VENTE_MS) {
      continue
    }
    const trouve = choisirSortieBtc(tx, {
      adresse: critere.adresse,
      montant: critere.montant,
      decimales: cfg.decimales,
    })
    if (trouve) {
      return {
        txid: String(tx?.txid ?? critere.txid ?? '').toLowerCase(),
        montant: trouve.montant,
        confirme: trouve.confirme,
      }
    }
  }
  return null
}

/*
───────────────────────────────────────────────────────────────────────────
UN TRANSFERT NE PEUT SERVIR QU'UNE FOIS
───────────────────────────────────────────────────────────────────────────

Sans ça, la vérification se retourne : un VRAI versement de 8 USDT, fait
hier, justifierait dix demandes aujourd'hui. Chacune serait « vérifiée »,
et chacune le serait honnêtement — c'est le même transfert dix fois.

On marque donc le txid dès qu'il a servi. La fenêtre de trois heures
limitait déjà les dégâts ; ceci les ferme.

CE CACHE EST PAR CENTRE DE DONNÉES, et c'est sa limite — un Worker est
servi depuis le point le plus proche de l'appelant, et un attaquant qui
change de pays trouve un cache vierge. Le txid est donc AUSSI écrit dans
le message Telegram : le changeur qui voit deux fois le même hash le
reconnaît, et la fenêtre de trois heures borne ce qu'il faut regarder.

Un stockage durable (KV, D1) fermerait complètement le cas. C'est le
prochain pas, et il demande un réglage de plus côté Cloudflare — noté ici
pour ne pas croire le problème résolu.
───────────────────────────────────────────────────────────────────────────
*/
async function txidDejaServi(txid) {
  try {
    const cle = new Request(`https://relais.vaultex/txid/${encodeURIComponent(txid)}`)
    return Boolean(await caches.default.match(cle))
  } catch (_) {
    // Cache indisponible : on ne bloque pas une vente honnête pour ça. Le
    // txid reste écrit dans le message, et le changeur garde l'oeil.
    return false
  }
}

async function marquerTxidServi(txid) {
  try {
    const cle = new Request(`https://relais.vaultex/txid/${encodeURIComponent(txid)}`)
    await caches.default.put(
      cle,
      new Response('1', { headers: { 'cache-control': 'max-age=86400' } })
    )
  } catch (_) { /* sans effet sur l'ordre en cours */ }
}

/**
 * Vérifie une vente sur la chaîne.
 *
 * @returns { etat, txid, montant, quand, confirme, raison, explorateur }
 *   etat ∈ confirme | absent | deja_servi | indisponible | inconnue
 */
async function verifierVente(options) {
  const monnaie = String(options?.monnaie ?? '').trim().toUpperCase()
  const montant = Number(options?.montant)
  const txid = String(options?.txid ?? '').trim()
  const env = options?.env

  const cfg = CHAINES_VENTE[monnaie]
  if (!cfg) return { etat: 'inconnue', raison: `${monnaie} n'est pas verifiable ici` }
  if (!(montant > 0)) return { etat: 'indisponible', raison: 'montant illisible' }

  /*
  L'ADRESSE VIENT DU WORKER, JAMAIS DE LA REQUÊTE.

  Si l'appelant pouvait dire quelle adresse surveiller, il donnerait la
  sienne, s'enverrait huit USDT à lui-même, et tout serait « vérifié ».
  */
  const adresse = adressesChangeur(env)[monnaie]
  if (!adresse) {
    return { etat: 'indisponible', raison: `aucune adresse reglee pour ${monnaie}` }
  }

  const critere = {
    adresse,
    montant,
    txid,
    maintenant: Date.now(),
    cleTron: env?.TRONGRID_KEY ? String(env.TRONGRID_KEY) : '',
  }

  let trouve = null
  try {
    if (cfg.chaine === 'tron') trouve = await verifierTron(cfg, critere)
    else if (cfg.chaine === 'evm') trouve = await verifierEvm(cfg, critere)
    else if (cfg.chaine === 'btc') trouve = await verifierBtc(cfg, critere)
    else return { etat: 'inconnue', raison: `chaine ${cfg.chaine} non traitee` }
  } catch (e) {
    /*
    ON N'A PAS PU REGARDER. C'est différent de « il n'y est pas », et la
    confusion coûterait des ventes honnêtes chaque fois qu'un nœud public
    tousse. La raison est rendue telle quelle : elle finit dans le message
    du changeur, qui saura que la vérification n'a pas eu lieu.
    */
    return {
      etat: 'indisponible',
      raison: String(e?.message ?? 'chaine injoignable').slice(0, 80),
    }
  }

  if (!trouve) {
    return {
      etat: 'absent',
      raison: txid
        ? "ce txid ne correspond a aucun versement attendu"
        : 'aucun versement correspondant sur les 3 dernieres heures',
    }
  }

  if (trouve.txid && (await txidDejaServi(trouve.txid))) {
    return { etat: 'deja_servi', txid: trouve.txid, raison: 'ce transfert a deja servi' }
  }

  return {
    etat: 'confirme',
    txid: trouve.txid || '',
    montant: trouve.montant,
    quand: trouve.quand || 0,
    // Bitcoin seul peut rendre un transfert vu mais pas encore miné.
    confirme: trouve.confirme !== false,
    explorateur: trouve.txid ? `${cfg.explorateur}${trouve.txid}` : '',
  }
}

/**
 * Le montant en francs annoncé est-il cohérent avec le marché ?
 *
 * ═══════════════════════════════════════════════════════════════════════
 * UNE DEUXIÈME SERRURE, SUR UNE AUTRE PORTE
 * ═══════════════════════════════════════════════════════════════════════
 *
 * La vérification on-chain prouve qu'un transfert a eu lieu. Elle ne dit
 * rien du PRIX : un téléphone modifié peut envoyer « 8 USDT reçus, paie
 * 500 000 FCFA ». Le transfert serait vrai et l'ordre absurde.
 *
 * On recalcule donc ici, avec NOTRE cours et NOTRE marge. On ne remplace
 * pas le chiffre annoncé — celui qui a été montré à l'utilisateur est
 * celui qui engage, et un cours qui bouge entre l'affichage et le paiement
 * ne doit pas lui coûter. On SIGNALE l'écart quand il dépasse ce qu'un
 * marché explique.
 *
 * TROIS POUR CENT. L'USDT est stable et le franc est arrimé à l'euro :
 * sur dix minutes, seul l'euro-dollar bouge, de bien moins que ça. Un
 * écart au-delà n'est pas un marché qui a bougé.
 * ═══════════════════════════════════════════════════════════════════════
 */
async function coherenceFcfa(monnaie, montantCrypto, fcfaAnnonce, env) {
  const m = Number(montantCrypto)
  const annonce = Number(fcfaAnnonce)
  if (!(m > 0) || !(annonce > 0)) return { etat: 'indisponible' }

  const prixUsd = await prixUsdDeMonnaie(monnaie)
  const eurUsd = await tauxEuroDollar()
  if (!(prixUsd > 0) || !(eurUsd > 0)) return { etat: 'indisponible' }

  const tauxXof = XOF_PAR_EURO / eurUsd
  const marge = Number(env?.CHANGE_MARGE)
  const margeFcfa = Number.isFinite(marge) && marge >= 0 ? marge : 25
  // Même formule que TauxFcfa.prix cote application, y compris l'arrondi
  // au franc du prix unitaire : c'est ce qui rend les deux comparables.
  const unitaire = Math.round(prixUsd * tauxXof - margeFcfa * prixUsd)
  const attendu = m * unitaire
  if (!(attendu > 0)) return { etat: 'indisponible' }

  const ecart = Math.abs(annonce - attendu) / attendu
  return {
    etat: ecart <= 0.03 ? 'coherent' : 'ecart',
    attendu: Math.round(attendu),
    ecartPourcent: Math.round(ecart * 1000) / 10,
  }
}

/**
 * Le cours en dollars d'une monnaie, chez Binance.
 *
 * L'USDT vaut un dollar par définition, et la paire USDTUSDT n'existe pas
 * — demandée telle quelle, elle fait rejeter l'appel entier. C'est le même
 * piège que dans depuisBinance, et pour la même raison.
 */
async function prixUsdDeMonnaie(monnaie) {
  const base = String(monnaie ?? '').trim().toUpperCase().split('-')[0]
  if (!base) return 0
  if (base === 'USDT' || base === 'USDC') return 1
  for (const hote of HOTES_BINANCE) {
    try {
      const r = await fetchBorne(
        `${hote}/api/v3/ticker/price?symbol=${encodeURIComponent(base + 'USDT')}`,
        { headers: { 'user-agent': AGENT, accept: 'application/json' } },
        6000
      )
      if (!r.ok) continue
      const o = await r.json()
      const v = parseFloat(o?.price)
      if (Number.isFinite(v) && v > 0) return v
    } catch (_) { /* hote suivant */ }
  }
  return 0
}

/*
═══════════════════════════════════════════════════════════════════════════
EXPORTS DE TEST
═══════════════════════════════════════════════════════════════════════════

Cloudflare ne lit que `export default` : ces noms-là lui sont invisibles et
ne changent rien au Worker déployé.

Ils existent pour que test-verification.mjs exécute la partie dangereuse —
le tri des transferts — sur de vraies formes de réponses, dont celles qu'un
attaquant enverrait. Ce sont les seules fonctions où une faute ne lève
aucune erreur : elle rend « vérifié » quelque chose qui ne l'est pas.

Le reste du module touche au réseau et ne se teste pas ici. Pour ça, il y a
/diag, qui sonde les nœuds depuis le Worker DÉPLOYÉ — l'endroit où la
mesure compte.
═══════════════════════════════════════════════════════════════════════════
*/
export {
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
  FENETRE_VENTE_MS,
  TOPIC_TRANSFER,
}
