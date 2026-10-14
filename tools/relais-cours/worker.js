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
  CHANGE_MIN        UN NOMBRE, en francs (défaut 5000). Sert de repli aux
                    monnaies absentes de CHANGE_MINIMUMS.

                    ⚠️ Ne pas confondre avec CHANGE_MINIMUMS ci-dessous :
                    les deux noms ne diffèrent que par trois lettres, et
                    le tableau de bord les affiche l'un sous l'autre. Y
                    coller le JSON par monnaie fait retomber le seuil
                    général à 5000, sans aucun message. /diag le signale
                    désormais dans `minimumsParMonnaie.alerte`.
  CHANGE_MINIMUMS   Minimum PAR MONNAIE :
                    {"USDT":20000,"USDT-BNB":2000,"USDT-ETH":50000}

                    Les frais de réseau n'ont rien à voir d'une chaîne à
                    l'autre — 13 à 27 TRX sur TRON, environ 0,20 $ sur BNB
                    Chain, jusqu'à 10 $ sur Ethereum. Sur une vente de
                    8 USDT, Ethereum prend entre douze et cent vingt-cinq
                    pour cent de l'opération, et l'utilisateur ne le
                    découvre qu'au moment de signer dans son portefeuille.

                    Une monnaie absente retombe sur CHANGE_MIN : ajouter
                    une chaîne sans penser à son seuil n'ouvre aucun trou.
                    Le plafond, lui, reste global — il borne un risque de
                    fraude, pas un coût de réseau.
  CHANGE_MAX        Plafond par opération, en francs (défaut 50000).
  CHANGE_NUMERO     Numéro Mobile Money du changeur, pour les ACHATS.
  CHANGE_NOM        Son nom, tel qu'il s'affiche.
  CHANGE_OPERATEUR  « Orange Money », « Moov »…
  CHANGE_DELAI      Délai annoncé, en minutes (défaut 30).
  CHANGE_FENETRE_MINUTES
                    Ancienneté maximale d'un versement pour qu'il puisse
                    justifier une demande. Défaut 180 (trois heures), borné
                    entre 15 minutes et 7 jours.

                    Trois heures suffisent quand le client envoie depuis
                    son propre portefeuille. Pas quand il retire depuis une
                    plateforme d'échange dont la file traîne, part
                    travailler et revient le soir : ses fonds sont arrivés
                    à midi, et le relais lui répond « aucun versement ».

                    Élargir ne rouvre PAS la réutilisation d'un versement —
                    le marquage du txid s'en charge, pendant 24 h. Ce que
                    cette fenêtre borne, c'est qu'un versement d'il y a un
                    mois, légitime et jamais utilisé, serve aujourd'hui.
  CHANGE_MONNAIES   « USDT,BTC,ETH » — ce qu'il accepte à l'achat.
  TG_CHANGE_TOKEN   Jeton du bot Telegram. SECRET, jamais dans l'APK.
  TG_CHANGE_CHAT    Identifiant du groupe du changeur.
  TRONGRID_KEY      Facultatif : relève le quota de l'API TronGrid.

  ─── LES GARDE-FOUS ANTI-FRAUDE (v15) ───────────────────────────────

  CHANGE_MIN et CHANGE_MAX sont désormais APPLIQUÉS ICI, et pas seulement
  affichés. Avant la v15 ils n'étaient vérifiés que par l'application :
  une requête postée à la main sur cette adresse publique passait cinq
  millions de francs sans rien rencontrer. Un réglage qui n'est appliqué
  que par le client n'est pas un réglage.

  CHANGE_BLOQUES       « 70123456,64000000 ». Numéros refusés à l'entrée.
                       Les formes sont normalisées des deux côtés : avec
                       ou sans indicatif, avec ou sans espaces.
  CHANGE_MAX_PAR_JOUR  Demandes abouties par numéro et par jour (10).
                       N'empêche pas une fraude ; empêche d'en tenter
                       quarante dans la soirée, ce qui noierait le canal.
  CHANGE_MAX_PREMIER   Plafond de la PREMIÈRE opération d'un numéro
                       inconnu — par exemple 10000. ÉTEINT par défaut.
                       C'est le meilleur levier anti-fraude du
                       pair-à-pair, et il s'appuie sur une mémoire
                       évictable : un client régulier peut se retrouver
                       replafonné sans raison visible. Lire le commentaire
                       dans ordreChange avant de l'activer.

  /diag dit lesquels sont réellement actifs. Trois d'entre eux ne font
  rien tant qu'on ne les règle pas, et rien ne le signale autrement.

  ─── LA LECTURE DES SMS D'ENCAISSEMENT (v16) ────────────────────────

  Une vente se vérifie sur une chaîne publique. Un achat, non : le client
  paie par Orange Money. Mais l'OPÉRATEUR envoie un SMS au changeur à
  chaque encaissement — une affirmation d'Orange, pas du client.

  L'application compagnon (tools/changeur-sms) transmet ces SMS ici, et le
  relais les rapproche des demandes d'achat.

  CHANGE_SMS_TOKEN       ⚠️ SECRET, ET PAS CELUI DE TELEGRAM.

                         Le point d'entrée /change/sms est le seul du
                         Worker qui ÉCRIVE une affirmation sur laquelle de
                         la crypto partira. Ouvert, il serait pire que
                         l'absence de vérification : n'importe qui
                         déclarerait ses propres encaissements et les
                         achats passeraient en « vérifié ». On aurait
                         transformé une incertitude en fausse certitude.

                         CE JETON NE DOIT JAMAIS ENTRER DANS L'APK PUBLIC
                         DE VAULTEX. Il vit dans l'application compagnon,
                         installée à la main sur un seul téléphone et
                         jamais distribuée — c'est ce qui rend acceptable
                         qu'un appareil le porte.

                         Non réglé, tout le mécanisme est éteint et les
                         achats repartent comme avant la v16.

  CHANGE_MARQUAGE        ⚠️ SECRET, au moins 16 caractères. Active le
                         MARQUAGE DES MONTANTS sur les ventes.

                         Sans lui, le relais accepte « environ 2 USDT » à
                         1 % près. C'est agréable, et c'est précisément ce
                         qui permet de voler le versement d'un autre :
                         l'adresse du changeur est publique, n'importe qui
                         la surveille, voit arriver 2 USDT, et dépose une
                         demande pour les réclamer.

                         Avec lui, le relais impose un montant exact
                         dérivé de la référence — 2,004731 pour l'une,
                         2,009182 pour une autre. Deux demandes ne peuvent
                         plus se confondre, et nul ne peut prédire son
                         chiffre sans le secret.

                         `openssl rand -hex 24` en produit un bon. Un
                         secret trop court est REFUSÉ plutôt qu'accepté
                         en donnant l'illusion d'une protection : il se
                         retrouve par force brute dès qu'on observe un
                         couple (référence, montant), et les deux sont
                         publics.

                         Éteint par défaut : un relais sans ce réglage se
                         comporte exactement comme avant.

  CHANGE_USSD            Modèle de code USSD pré-rempli sur l'écran de
                         paiement d'un ACHAT, par exemple
                         « *144*2*1*{numero}*{montant}# ». Vide par
                         défaut : la syntaxe réelle d'Orange Money Burkina
                         n'a pas été mesurée, et un modèle deviné
                         enverrait quelqu'un valider un transfert vers un
                         numéro tronqué.
                         ⚠️ JAMAIS LE CODE SECRET dedans : une chaîne USSD
                         s'affiche en clair et reste dans le journal
                         d'appels. Deux emplacements admis, {numero} et
                         {montant} ; tout autre fait refuser le modèle.
                         Ce n'est PAS une intégration Orange : on ouvre le
                         composeur, l'utilisateur appuie, Orange exécute.

  CHANGE_SMS_EXPEDITEURS « OrangeMoney,MoovMoney ». Les SMS venant d'un
                         autre expéditeur sont écartés sans bruit : le
                         robot transmet ce qu'il voit, et ce qu'il voit
                         comprend les publicités et les messages privés.
                         Par défaut, la liste EXPEDITEURS_SMS.

  CE QUE ÇA NE PROUVE PAS : un identifiant d'expéditeur de SMS se
  falsifie. La règle du changeur ne bouge donc pas, et reste écrite dans
  chaque message — il n'envoie qu'après avoir vu l'argent sur son propre
  compte. Ce qui change, c'est qu'un faux reçu du client ne suffit plus.

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

                    Une adresse mal collée se voit sur /diag : le bloc
                    `adressesChangeur` rend celles qui ont été REFUSÉES
                    avec la forme attendue, et TronGrid y est sondé sur
                    l'adresse réellement réglée.

                    La forme est vérifiée par monnaie : une adresse 0x
                    sous la clé « USDT » est écartée. Sans ce contrôle,
                    l'application l'afficherait à chaque vendeur, et des
                    USDT TRC-20 envoyés à une adresse Ethereum sont perdus
                    définitivement.

Aucune donnée personnelle ne transite ici : ni mnémonique, ni identifiant
d'utilisateur. Les adresses du changeur sont publiques — l'application les
montre à chaque vendeur — et les cours le sont aussi.
═══════════════════════════════════════════════════════════════════════════
*/

/** Version du Worker déployé — lisible sur /sante et /diag. */
const VERSION = 33

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
    /diag/plages — COMBIEN DE BLOCS CHAQUE NŒUD ACCEPTE-T-IL VRAIMENT ?
    ═══════════════════════════════════════════════════════════════════════

    J'ai écrit deux fois une limite dans ce fichier en la supposant : deux
    mille blocs, puis cinq cents, « qui passe partout ». Les deux fois le
    relais a répondu « limit exceeded » en production, et les deux fois
    l'essai de vente s'est arrêté là.

    Cette sonde-ci ne suppose rien. Elle demande le MÊME appel que la
    vérification — même contrat, mêmes sujets — à des largeurs croissantes,
    et s'arrête au premier refus de chaque nœud. Ce qu'elle rend est la
    limite réelle, et non une hypothèse sur la limite.

    ─── POURQUOI ELLE VIT DANS LE RELAIS ────────────────────────────────

    Parce qu'une mesure faite depuis un PC de maison ne vaut pas ici. Les
    points d'entrée publics limitent PAR IP, et un Worker sort par des
    adresses Cloudflare partagées avec tous les robots du monde. Ce même
    fichier porte déjà la trace du phénomène : `api.binance.com` répond
    403 depuis un Worker et parfaitement depuis un navigateur.

    La seule mesure qui décrit ce que vit le relais est celle que le relais
    fait lui-même.
    ═══════════════════════════════════════════════════════════════════════
    */
    if (url.pathname === '/diag/plages') {
      return await diagnostiquerPlages(env, url.searchParams)
    }

    /*
    ═══════════════════════════════════════════════════════════════════════
    /diag/etherscan — LA SOURCE RÉPOND-ELLE, ET DANS QUELLE FORME ?
    ═══════════════════════════════════════════════════════════════════════

    Deux questions distinctes, et c'est pour ça que cette sonde existe
    plutôt qu'un simple essai de vente.

    1. ETHERSCAN RÉPOND-IL À UN WORKER ? On ne peut pas le déduire. Ce
       fichier porte deux contre-exemples : `api.binance.com` répond 403
       depuis Cloudflare, et les sept nœuds BSC viennent de refuser
       `eth_getLogs` depuis la même IP alors qu'ils l'acceptent ailleurs.

    2. LES CHAMPS PORTENT-ILS LES NOMS QUE J'ATTENDS ? `hash`, `to`,
       `contractAddress`, `tokenDecimal`, `value`, `timeStamp`. Si l'un
       d'eux est nommé autrement, le tri ne lèvera AUCUNE erreur : il
       rendra simplement « rien trouvé » sur tous les versements, et on
       chercherait le défaut du côté de la chaîne pendant des heures.

    La sonde rend donc les NOMS DE CHAMPS du premier enregistrement, et
    dit lesquels manquent parmi ceux dont le tri a besoin. Elle ne rend
    jamais la clé, ni l'URL qui la contient.
    ═══════════════════════════════════════════════════════════════════════
    */
    if (url.pathname === '/diag/etherscan') {
      return await diagnostiquerEtherscan(env, url.searchParams)
    }

    /*
    ═══════════════════════════════════════════════════════════════════════
    /diag/transferts — LA MÉTHODE ENRICHIE RÉPOND-ELLE, ET DANS QUELLE FORME ?
    ═══════════════════════════════════════════════════════════════════════

    Même raison d'être que /diag/etherscan, et même piège : si
    `rawContract.decimal` s'appelait autrement, le tri ne lèverait AUCUNE
    erreur — il rendrait « rien trouvé » sur tout versement, et on
    chercherait du côté de la chaîne pendant des heures.

    Deux choses s'y vérifient qu'on ne peut pas déduire : que le nœud sert
    `alchemy_getAssetTransfers` sur BNB — ce n'est pas vrai de toutes les
    chaînes — et que les champs portent les noms attendus.
    ═══════════════════════════════════════════════════════════════════════
    */
    if (url.pathname === '/diag/transferts') {
      return await diagnostiquerTransferts(env, url.searchParams)
    }

    /*
    ═══════════════════════════════════════════════════════════════════════
    /diag/pourquoi — « ABSENT » NE DIT PAS POURQUOI, ET C'EST LE PROBLÈME
    ═══════════════════════════════════════════════════════════════════════

    `absent` affirme une seule chose : aucun transfert n'a passé TOUS les
    contrôles. Il ne dit ni combien de transferts ont été regardés, ni
    lequel a échoué sur quoi.

    Or les causes possibles ne se corrigent pas de la même façon :

      — le montant diffère de quelques centièmes ⇒ la commission de
        l'exchange a été retranchée, et il faut viser « vous recevrez » ;
      — le versement précède la demande ⇒ l'antériorité, donc l'ordre des
        gestes ;
      — les décimales lues ne sont pas 18 ⇒ la forme de la réponse ;
      — rien du tout n'a été lu ⇒ le versement n'est pas arrivé.

    Sans ce point d'entrée, chacune demandait un aller-retour : une
    hypothèse, un déploiement, un essai. On en a fait quatre.

    ─── IL REJOUE LE TRI, IL NE LE RÉIMPLÉMENTE PAS ─────────────────────

    Un diagnostic qui recopierait les contrôles finirait par décrire un
    code qui n'est plus celui qui tourne — et mentirait au pire moment.
    Celui-ci décrit chaque contrôle PUIS appelle le vrai sélecteur, et
    signale lui-même tout désaccord entre les deux.
    ═══════════════════════════════════════════════════════════════════════
    */
    if (url.pathname === '/diag/pourquoi') {
      return await diagnostiquerPourquoi(env, url.searchParams)
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
    /*
    LE ROBOT SMS DU CHANGEUR POSTE ICI.

    Le point d'entrée le plus sensible du Worker : tout le reste ne fait
    que lire, celui-ci écrit une affirmation sur laquelle de la crypto
    partira. Il exige un jeton — voir recevoirSms, qui explique pourquoi
    ce jeton ne doit jamais entrer dans l'APK public.
    */
    if (url.pathname === '/change/sms' && requete.method === 'POST') {
      return await recevoirSms(requete, env)
    }

    /*
    LA CONSIGNE : QUEL MONTANT EXACT ENVOYER.

    L'application l'appelle au moment où l'utilisateur confirme, avant
    qu'il n'envoie quoi que ce soit. Deux choses s'y passent :

      — le relais calcule le montant marqué pour cette référence ;
      — il NOTE L'INSTANT, qui servira ensuite à exiger que le versement
        lui soit postérieur.

    Le second point est la raison pour laquelle cet appel existe en tant
    que tel au lieu d'être fondu dans /change/verifier : il faut un moment
    où l'on puisse dire « la demande commence ici », et c'est celui-là.
    */
    if (url.pathname === '/change/consigne') {
      return await consigneChange(url, env)
    }

    if (url.pathname === '/change/verifier') {
      const resultat = await verifierVente({
        monnaie: url.searchParams.get('monnaie'),
        montant: url.searchParams.get('montant'),
        txid: url.searchParams.get('txid'),
        /*
        LA RÉFÉRENCE PLUTÔT QUE LE MONTANT, quand le marquage est en
        service. Le relais recalcule alors le montant exact lui-même, et
        le `montant` reçu ne sert plus que de socle. Faire autrement
        reviendrait à laisser l'appelant choisir ce qu'on va chercher —
        c'est-à-dire à annuler le marquage.
        */
        reference: url.searchParams.get('reference'),
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
  const triAdresses = trierAdresses(env?.CHANGE_ADRESSES)
  const adressesPosees = triAdresses.bonnes
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
  /*
  ═══════════════════════════════════════════════════════════════════════
  QUELS GARDE-FOUS SONT RÉELLEMENT ACTIFS
  ═══════════════════════════════════════════════════════════════════════

  Trois d'entre eux ne font rien tant qu'on ne les règle pas : la liste
  noire, la limite par jour et le plafond de première opération. Un
  réglage mal collé — une virgule oubliée, une valeur vide — les laisse
  éteints SANS AUCUN SIGNE : les demandes passent, exactement comme avant.

  C'est le même piège que `canalConfigure` : croire une protection en
  place parce qu'on se souvient de l'avoir posée. Ici on la mesure.

  AUCUNE VALEUR SENSIBLE : des bornes en francs, un décompte, et le NOMBRE
  de numéros bloqués — pas les numéros eux-mêmes, qui n'ont pas à sortir
  d'ici.
  ═══════════════════════════════════════════════════════════════════════
  */
  const nb = (v, d) => {
    const n = Number(v)
    return Number.isFinite(n) && n >= 0 ? n : d
  }
  const plafondDiag = nb(env?.CHANGE_MAX, 50000)
  const premierDiag = nb(env?.CHANGE_MAX_PREMIER, plafondDiag)
  sondes.fenetreVenteHeures = Math.round(fenetreVente(env) / 3600000)

  /*
  ═══════════════════════════════════════════════════════════════════════
  CHANGE_MIN ET CHANGE_MINIMUMS SE RESSEMBLENT TROP
  ═══════════════════════════════════════════════════════════════════════

  C'est arrivé au premier réglage : le JSON destiné à CHANGE_MINIMUMS a
  été collé dans CHANGE_MIN. Les deux noms ne diffèrent que par trois
  lettres, et le tableau de bord les affiche l'un sous l'autre.

  Le résultat ne ressemblait pas à une erreur. CHANGE_MIN cessait d'être
  un nombre, donc le relais retombait sur 5 000 — silencieusement, comme
  il doit le faire face à une valeur illisible. Et CHANGE_MINIMUMS
  n'existait pas, donc aucun seuil par monnaie. Deux réglages perdus, zéro
  message, et l'écran qui refuse une vente sans dire pourquoi.

  Un repli silencieux est juste quand la valeur est ABSENTE. Il est
  trompeur quand elle est PRÉSENTE et mal formée : là, quelqu'un croit
  avoir réglé quelque chose.

  On ne corrige pas à sa place — deviner qu'un JSON dans CHANGE_MIN était
  destiné à CHANGE_MINIMUMS marcherait aujourd'hui et masquerait une vraie
  faute demain. On le DIT, et c'est à /diag de le dire, puisque c'est la
  page qu'on ouvre quand quelque chose ne va pas.
  ═══════════════════════════════════════════════════════════════════════
  */
  const minBrut = env?.CHANGE_MIN
  const minLisible = minBrut === undefined || Number.isFinite(Number(minBrut))
  sondes.minimumsParMonnaie = {
    general: nb(env?.CHANGE_MIN, 5000),
    generalLisible: minLisible,
    parMonnaie: minimumsChange(env),
    ...(minLisible
      ? {}
      : {
          alerte:
            'CHANGE_MIN n\'est pas un nombre — le seuil general retombe a 5000. ' +
            'Un JSON par monnaie se met dans CHANGE_MINIMUMS, pas ici.',
        }),
  }

  sondes.gardeFous = {
    // Appliquées par le relais depuis la v15. Avant, elles ne vivaient que
    // dans l'application — donc nulle part, pour qui ne l'utilise pas.
    bornesAppliquees: { minimum: nb(env?.CHANGE_MIN, 5000), plafond: plafondDiag },
    numerosBloques: String(env?.CHANGE_BLOQUES ?? '')
      .split(',').map((x) => numeroComparable(x)).filter((x) => x.length === 8).length,
    maxParJour: nb(env?.CHANGE_MAX_PAR_JOUR, 10),
    plafondPremiereOperation: premierDiag < plafondDiag ? premierDiag : 'eteint',
    /*
    LA LIMITE DE CES TROIS-LÀ, ÉCRITE DANS LE DIAGNOSTIC LUI-MÊME.

    Elles s'appuient sur `caches.default`, qui est par centre de données et
    évictable. Pour ce qui se joue en minutes — un renvoi, une rafale —
    c'est suffisant. Pour ce qui doit tenir des semaines, non : un
    attaquant qui change de pays trouve des compteurs neufs.

    Qui lit ce diagnostic doit le savoir, sinon il croit ces protections
    plus fortes qu'elles ne sont.
    */
    memoire: 'cache par centre de donnees, evictable — exact avec Workers KV',
  }

  /*
  LA LECTURE DES SMS EST-ELLE INSTALLÉE ?

  Sans jeton, les achats repartent « non vérifiables » et le changeur n'a
  aucun moyen de savoir si c'est parce qu'il n'a rien réglé ou parce que
  son robot ne poste pas. Deux causes, une seule apparence.

  On ne rend ni le jeton ni sa longueur — à la différence de la clé
  CoinGecko, dont la longueur servait à distinguer un collage raté d'une
  liaison absente. Ici le robot reçoit un 401 explicite dès son premier
  envoi : il n'y a rien à deviner, donc rien à exposer.
  */
  sondes.lectureSms = {
    configuree: Boolean(env?.CHANGE_SMS_TOKEN),
    expediteursAcceptes: String(env?.CHANGE_SMS_EXPEDITEURS ?? '')
      .split(',').map((x) => x.trim()).filter(Boolean),
    expediteursParDefaut: EXPEDITEURS_SMS,
    fenetreMinutes: Math.round(FENETRE_PAIEMENT_MS / 60000),
  }

  /*
  LES ADRESSES REFUSÉES, ET POURQUOI.

  Trois pannes se ressemblaient : la variable absente, le JSON invalide, et
  une adresse de la mauvaise chaîne. Les trois donnaient « adresses: {} »
  et « aucune adresse reglee » — et on cherchait une variable manquante
  alors qu'elle était là, fausse.

  `jsonLisible: false` dit que le JSON ne s'analyse pas. Une entrée dans
  `refusees` dit qu'une adresse ne ressemble pas à sa chaîne. Les deux
  champs vides avec `adressesReglees: 0` : la variable n'existe pas.
  */
  sondes.adressesChangeur = {
    jsonLisible: triAdresses.lisible,
    adressesReglees: Object.keys(adressesPosees).length,
    refusees: triAdresses.refusees,
  }

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
      /*
      ═══════════════════════════════════════════════════════════════════
      LE MODÈLE DE CODE USSD — RÉGLÉ ICI, ET VIDE PAR DÉFAUT
      ═══════════════════════════════════════════════════════════════════

      CHANGE_USSD = « *144*2*1*{numero}*{montant}# ».

      Deux emplacements, et deux seulement : {numero} et {montant}.
      L'application refuse tout modèle qui en porte un autre, et n'affiche
      alors aucun bouton — voir CodeUssd.modeleValide.

      ⚠️ LE CODE SECRET N'ENTRE JAMAIS DANS CE MODÈLE. Certaines syntaxes
      l'acceptent en dernier paramètre ; il ne faut pas s'en servir. Une
      chaîne USSD s'affiche en clair pendant la frappe et reste dans le
      journal d'appels du téléphone. L'utilisateur termine sur son
      clavier, là où son code ne quitte rien.

      VIDE PAR DÉFAUT, et c'est important : je n'ai pas mesuré la syntaxe
      de transfert Orange Money Burkina, et un modèle deviné enverrait des
      gens valider un transfert vers un numéro tronqué. Sans réglage,
      l'écran garde le numéro à copier — ce qui marche depuis le premier
      jour. À mesurer sur un vrai téléphone avant de poser cette variable.

      C'est un réglage du relais et non une constante de l'application
      pour la même raison que le format des SMS : une syntaxe USSD change
      sans préavis, et corriger une variable Cloudflare prend dix secondes
      là où republier un APK prend une semaine.
      ═══════════════════════════════════════════════════════════════════
      */
      ussdModele: String(env?.CHANGE_USSD ?? ''),
      /*
      LES SEUILS PAR MONNAIE, POUR QUE L'ÉCRAN DISE LE BON CHIFFRE.

      Le relais les applique de toute façon. S'il était seul à les
      connaître, l'utilisateur taperait un montant, verrait le bouton
      s'allumer, et se ferait refuser au dépôt — après avoir envoyé sa
      crypto. Le freiner avant vaut mieux que l'arrêter après.
      */
      minimumsParMonnaie: minimumsChange(env),
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
  return trierAdresses(env?.CHANGE_ADRESSES).bonnes
}

/*
═══════════════════════════════════════════════════════════════════════════
L'ADRESSE EST VÉRIFIÉE CONTRE LA FORME DE SA CHAÎNE
═══════════════════════════════════════════════════════════════════════════

La première version n'exigeait qu'une longueur de vingt caractères. Elle
acceptait donc « 0xd8dA6BF2…aA96045 » sous la clé « USDT ».

Ce que ça donne : l'application affiche cette adresse à chaque vendeur,
quelqu'un y envoie de l'USDT TRC-20, et les fonds sont perdus
définitivement — on ne récupère pas des jetons TRON envoyés à une adresse
Ethereum. Pour une seule touche de travers dans un tableau de bord, un
soir, sur une variable qu'on règle une fois et qu'on ne relit plus jamais.

C'est exactement le genre de faute qu'un réglage distant rend facile et
invisible : personne ne revoit CHANGE_ADRESSES après l'avoir posé.

─── CE QU'ON VÉRIFIE, ET CE QU'ON NE VÉRIFIE PAS ────────────────────────

La FORME, pas l'appartenance. Qu'une adresse soit syntaxiquement du TRON
ne dit pas qu'elle appartient au changeur — ça, rien ici ne peut le dire,
et c'est à lui de la coller juste.

Ce qui est écarté : une chaîne de la mauvaise famille, une adresse
tronquée, un reste de copier-coller. Trois fautes de doigt, et les trois
coûtent les fonds de quelqu'un.

─── UNE MONNAIE INCONNUE N'EST PAS BLOQUÉE ──────────────────────────────

Si le changeur ajoute demain une monnaie dont on ne connaît pas la forme,
on retombe sur la longueur minimale. Refuser tout ce qu'on ne sait pas
vérifier fermerait le service à chaque nouveauté ; c'est une validation,
pas une liste d'autorisation.
═══════════════════════════════════════════════════════════════════════════
*/
const FORMES_ADRESSE = {
  // TRON, base58check : « T » puis 33 caractères. L'alphabet base58 exclut
  // 0, O, I et l — précisément les caractères qui se confondent à l'oeil,
  // et dont la présence signale un copier-coller abîmé.
  tron: /^T[1-9A-HJ-NP-Za-km-z]{33}$/,
  // EVM : 0x et quarante chiffres hexadécimaux. La casse mélangée est une
  // somme de contrôle, qu'on ne vérifie pas ici — elle n'attrape que les
  // fautes de frappe, et la longueur les attrape déjà.
  evm: /^0x[0-9a-fA-F]{40}$/,
  // Bitcoin : bech32 « bc1… », ou base58 « 1… » / « 3… ».
  btc: /^(bc1[0-9a-z]{20,80}|[13][1-9A-HJ-NP-Za-km-z]{25,39})$/,
  // Stellar et Pi, StrKey : « G » puis 55 caractères en base32.
  stellar: /^G[A-Z2-7]{55}$/,
}

/*
═══════════════════════════════════════════════════════════════════════════
UN MINIMUM PAR MONNAIE, PARCE QUE LES FRAIS DE RÉSEAU N'ONT RIEN À VOIR
═══════════════════════════════════════════════════════════════════════════

CHANGE_MIN posait un seuil unique pour tout le monde. Ça tenait tant qu'il
n'y avait qu'une chaîne. Avec plusieurs, c'est un piège :

  TRON        13 à 27 TRX par transfert
  BNB Chain   environ 0,20 $
  Ethereum    1 à 10 $

Sur une vente de 8 USDT — cinq mille francs, le minimum d'aujourd'hui —
Ethereum prend entre douze et cent vingt-cinq pour cent de l'opération.
Quelqu'un choisirait « USDT-ETH » dans la liste PARCE QU'IL Y EST, et ne
découvrirait le coût qu'au moment de signer dans son portefeuille, bien
après avoir lu « Tu reçois 4 800 FCFA » sur l'écran.

L'application aurait dit la vérité sur le prix et menti par omission sur
le coût. C'est le genre de silence qu'on ne pardonne pas à un
portefeuille.

CHANGE_MINIMUMS = {"USDT":20000,"USDT-BNB":2000,"USDT-ETH":50000}

Une monnaie absente retombe sur CHANGE_MIN. Ajouter une chaîne sans penser
à son seuil ne casse donc rien : ça applique le seuil général, c'est-à-dire
le comportement d'avant.

─── LE PLAFOND RESTE GLOBAL, ET CE N'EST PAS UN OUBLI ───────────────────

Le minimum borne un COÛT DE RÉSEAU, qui dépend de la chaîne. Le plafond
borne un RISQUE DE FRAUDE, qui dépend de la confiance accordée au client —
la même quelle que soit la chaîne qu'il emploie.

Deux grandeurs différentes, deux réglages différents.
═══════════════════════════════════════════════════════════════════════════
*/
function minimumsChange(env) {
  const brut = env?.CHANGE_MINIMUMS
  if (!brut) return {}
  try {
    const o = JSON.parse(String(brut))
    if (!o || typeof o !== 'object' || Array.isArray(o)) return {}
    const sortie = {}
    for (const [cle, valeur] of Object.entries(o)) {
      const n = Number(valeur)
      // Un seuil nul ou négatif n'est pas un seuil : on l'écarte plutôt
      // que d'ouvrir la monnaie sans minimum du tout.
      if (Number.isFinite(n) && n > 0) sortie[String(cle).trim().toUpperCase()] = n
    }
    return sortie
  } catch (_) {
    /*
    JSON ILLISIBLE : ON RETOMBE SUR LE SEUIL GÉNÉRAL.

    Pas sur « aucun minimum ». C'est l'inverse du choix fait pour les
    adresses, où un JSON cassé rend un objet vide — là, ne rien rendre
    FERME le service ; ici, ça l'ouvrirait sans garde-fou. On échoue du
    côté qui protège, dans les deux cas.
    */
    return {}
  }
}

/** Le minimum applicable à cette monnaie, en francs. */
function minimumPourMonnaie(monnaie, env) {
  const general = Number(env?.CHANGE_MIN)
  const parDefaut = Number.isFinite(general) && general >= 0 ? general : 5000
  const propre = minimumsChange(env)[String(monnaie ?? '').trim().toUpperCase()]
  return Number.isFinite(propre) && propre > 0 ? propre : parDefaut
}

/**
 * Cet identifiant a-t-il la forme d'un hash de transaction sur cette chaîne ?
 *
 * ON VÉRIFIE LA FORME, PAS L'EXISTENCE. Un hash inexistant est une
 * réponse légitime du nœud (« je ne connais pas cette transaction ») ;
 * un hash MAL FORMÉ est une erreur d'appel qui fait tout échouer, y
 * compris les chemins qui n'en avaient pas besoin.
 *
 * Les longueurs ne sont pas interchangeables : trente-deux octets partout,
 * mais préfixés « 0x » sur les chaînes EVM et nus sur TRON et Bitcoin.
 * Accepter la forme d'une chaîne pour une autre déplacerait simplement
 * l'erreur d'une ligne.
 */
function txidValide(txid, chaine) {
  const t = String(txid ?? '').trim()
  if (!t) return false
  switch (chaine) {
    case 'evm':
      return /^0x[0-9a-fA-F]{64}$/.test(t)
    case 'tron':
    case 'btc':
      // Sans préfixe sur ces deux chaînes. On tolère un « 0x » collé par
      // habitude plutôt que de refuser un hash par ailleurs correct.
      return /^(0x)?[0-9a-fA-F]{64}$/.test(t)
    default:
      return false
  }
}

/** Quelle forme attendre pour cette monnaie, ou null si on ne sait pas. */
function formeAttendue(monnaie) {
  switch (monnaie) {
    case 'USDT':
    case 'USDT-TRX':
    case 'TRX':
      return 'tron'
    case 'USDT-BNB':
    case 'BNB':
    case 'USDT-ETH':
    case 'ETH':
      return 'evm'
    case 'BTC':
      return 'btc'
    case 'PI':
      return 'stellar'
    default:
      return null
  }
}

/**
 * Trie les adresses réglées : celles qu'on garde, celles qu'on refuse.
 *
 * LES REFUSÉES SONT RENDUES AVEC LEUR RAISON, et c'est tout l'intérêt.
 * Écartée en silence, une adresse mal collée se manifeste par une vente
 * impossible et un « aucune adresse reglee » qui donne l'impression que
 * la variable n'existe pas — alors qu'elle existe et qu'elle est fausse.
 * Deux pannes très différentes, une seule apparence. /diag les sépare.
 */
function trierAdresses(brut) {
  const bonnes = {}
  const refusees = []
  if (!brut) return { bonnes, refusees, lisible: true }

  let o = null
  try {
    o = JSON.parse(String(brut))
  } catch (_) {
    // JSON illisible : AUCUNE lecture partielle. Une adresse à moitié lue
    // est une adresse vers laquelle des fonds partiraient sans retour.
    return { bonnes, refusees, lisible: false }
  }
  if (!o || typeof o !== 'object' || Array.isArray(o)) {
    return { bonnes, refusees, lisible: false }
  }

  for (const [cleBrute, valeur] of Object.entries(o)) {
    const cle = String(cleBrute).trim().toUpperCase()
    const adresse = String(valeur ?? '').trim()
    const forme = formeAttendue(cle)

    if (forme) {
      if (FORMES_ADRESSE[forme].test(adresse)) {
        bonnes[cle] = adresse
      } else {
        refusees.push({ monnaie: cle, attendu: forme, adresse })
      }
      continue
    }
    // Monnaie inconnue : on retombe sur la longueur minimale. Moins de
    // vingt caractères n'est une adresse sur aucune chaîne connue.
    if (adresse.length >= 20) bonnes[cle] = adresse
    else refusees.push({ monnaie: cle, attendu: 'longueur >= 20', adresse })
  }
  return { bonnes, refusees, lisible: true }
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
  ═══════════════════════════════════════════════════════════════════════
  LE CONTRÔLE QUE JE CROYAIS FAIT, ET QUI NE L'ÉTAIT PAS
  ═══════════════════════════════════════════════════════════════════════

  Le minimum et le plafond étaient servis par /change/parametres, et
  vérifiés PAR L'APPLICATION — dans ChangeState.blocage(), qui éteint le
  bouton. C'est ce qu'il faut pour l'écran : la raison s'affiche là où on
  la corrige.

  Mais ce n'était vérifié QUE là. Un APK modifié, ou une requête postée à
  la main sur cette adresse publique, passait une demande de cinq millions
  de francs sans que rien ne s'y oppose. Or j'ai écrit hier au propriétaire
  que « le plafond borne les dégâts d'une fraude » — ce qui était faux
  tant que le plafond ne vivait que dans le téléphone.

  UN RÉGLAGE QUI N'EST APPLIQUÉ QUE PAR LE CLIENT N'EST PAS UN RÉGLAGE,
  c'est une suggestion. Il est donc appliqué ici aussi, et c'est ici qu'il
  compte : le client, on ne le contrôle pas.
  ═══════════════════════════════════════════════════════════════════════
  */
  const nombreRegle = (v, defaut) => {
    const n = Number(v)
    return Number.isFinite(n) && n >= 0 ? n : defaut
  }
  // Par monnaie : les frais de reseau n'ont rien a voir d'une chaine a
  // l'autre. Voir minimumsChange.
  const minimum = minimumPourMonnaie(ordre.monnaie, env)
  const plafond = nombreRegle(env?.CHANGE_MAX, 50000)
  const fcfa = nombreFrancais(ordre.montantFcfa)
  if (!(fcfa > 0)) {
    return json({ ok: false, raison: 'montant illisible' }, 0, 400)
  }
  if (fcfa < minimum) {
    return json(
      { ok: false, raison: `minimum ${minimum} FCFA pour ${ordre.monnaie}` },
      0,
      400
    )
  }
  if (fcfa > plafond) {
    return json({ ok: false, raison: `plafond ${plafond} FCFA` }, 0, 400)
  }

  const numero = numeroComparable(ordre.telephone)

  /*
  ═══════════════════════════════════════════════════════════════════════
  LE PLAFOND DE PREMIÈRE OPÉRATION — ET POURQUOI IL EST ÉTEINT PAR DÉFAUT
  ═══════════════════════════════════════════════════════════════════════

  C'est le meilleur levier anti-fraude du pair-à-pair : la PREMIÈRE
  opération d'un numéro inconnu est plafonnée bas. Un fraudeur ne connaît
  jamais le changeur ; sa première tentative est donc aussi sa plus
  grosse, et c'est celle-là qu'on veut petite.

  CHANGE_MAX_PREMIER = 10000, par exemple. Non réglé, il vaut le plafond
  ordinaire, donc il ne fait rien.

  ─── POURQUOI ÉTEINT, ALORS QUE C'EST LE MEILLEUR LEVIER ──────────────

  Parce que « ce numéro est connu » est une information qui doit tenir des
  SEMAINES, et que `caches.default` ne tient pas des semaines : il est par
  centre de données et évictable.

  La conséquence est bénigne mais réelle : un client régulier dont la
  marque a été évincée se retrouve plafonné comme un inconnu. Ce n'est pas
  dangereux — ça échoue dans le sens sûr — c'est agaçant, et ça se traduit
  en appels au changeur pour un plafond qui a bougé sans raison visible.

  À lui de choisir, donc. L'activer, c'est accepter ce désagrément contre
  une vraie protection ; une mémoire durable (Workers KV) supprimerait le
  compromis, et c'est ce qu'il faudra faire quand le volume le justifiera.
  C'est écrit ici pour que ce réglage ne soit pas activé en croyant qu'il
  est exact.
  ═══════════════════════════════════════════════════════════════════════
  */
  const plafondPremier = nombreRegle(env?.CHANGE_MAX_PREMIER, plafond)
  if (numero.length === 8 && plafondPremier < plafond && fcfa > plafondPremier) {
    if (!(await dejaVu('connu', numero))) {
      return json(
        {
          ok: false,
          raison:
            `premiere operation limitee a ${plafondPremier} FCFA. ` +
            `Fais un premier echange plus petit, puis reviens.`,
        },
        0,
        400
      )
    }
  }

  /*
  SUR UNE VENTE, PAS DE NUMÉRO, PAS D'ORDRE.

  Le changeur doit envoyer des francs quelque part. Sans numéro, le message
  disait « ENVOYER 5 000 FCFA au numero du client » — sans numéro. Il
  n'avait aucun moyen de payer, et la crypto du client était déjà partie.

  L'écran l'exige déjà depuis hier. On l'exige ici aussi, parce que l'écran
  est la partie qu'on ne contrôle pas.
  */
  if (ordre.sens !== 'achat' && numero.length < 8) {
    return json({ ok: false, raison: 'numero de telephone manquant' }, 0, 400)
  }

  /*
  LA LISTE NOIRE, et pourquoi elle est un réglage et non une base.

  CHANGE_BLOQUES = « 70123456,64000000 ». Le changeur y met un numéro
  après une tentative de fraude, redéploie, et c'est fini. Pas de table à
  administrer, pas d'écran à construire : au nombre de numéros concernés —
  quelques-uns par an — une variable d'environnement est la bonne taille
  d'outil.
  */
  const bloques = String(env?.CHANGE_BLOQUES ?? '')
    .split(',').map((x) => numeroComparable(x)).filter((x) => x.length === 8)
  if (numero.length === 8 && bloques.includes(numero)) {
    // On ne dit pas « tu es bloqué » : ça apprend au fraudeur qu'il doit
    // changer de numéro. Une indisponibilité ordinaire ne lui apprend rien.
    return json({ ok: false, raison: 'service indisponible pour cette demande' }, 0, 403)
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
  UNE RÉFÉRENCE DE PAIEMENT NE SERT QU'UNE FOIS
  ═══════════════════════════════════════════════════════════════════════

  C'est le pendant, pour les achats, du marquage des txid. Sur un ACHAT, le
  client recopie la référence que son opérateur lui a envoyée par SMS —
  « MP251008123456 ». Elle ne prouve rien (une référence se recopie depuis
  le SMS de quelqu'un d'autre, et le changeur ne doit jamais payer sur
  elle seule) mais elle sert à RETROUVER la ligne dans le relevé.

  Et si elle peut servir dix fois, elle sert à se faire payer dix fois sur
  un seul vrai paiement : le changeur retrouve bien la ligne, chaque fois,
  et chaque fois elle est authentique. C'est exactement l'attaque que le
  marquage des txid ferme sur les ventes.

  VINGT-QUATRE HEURES, comme pour les txid, et pour la même raison : une
  réclamation porte sur aujourd'hui ou sur hier.
  ═══════════════════════════════════════════════════════════════════════
  */
  if (ordre.sens === 'achat' && ordre.referencePaiement) {
    if (await dejaVu('refpay', ordre.referencePaiement)) {
      return json(
        {
          ok: false,
          raison: 'cette reference de paiement a deja servi une autre demande',
        },
        0,
        409
      )
    }
  }

  /*
  ═══════════════════════════════════════════════════════════════════════
  LA LIMITE PAR NUMÉRO — CE QU'ELLE PROTÈGE, ET CE QU'ELLE NE PROTÈGE PAS
  ═══════════════════════════════════════════════════════════════════════

  Elle n'empêche pas une fraude. Elle empêche d'en TENTER quarante dans la
  soirée, ce qui est autre chose : un canal Telegram noyé sous les fausses
  demandes est un canal où le changeur cesse de lire, et c'est là que la
  vraie fraude passe.

  CHANGE_MAX_PAR_JOUR, dix par défaut. Dix opérations par jour et par
  numéro est très au-dessus de l'usage réel — personne ne change dix fois
  dans la journée — et très en dessous d'une rafale.

  ELLE NE COMPTE QUE LES DEMANDES ABOUTIES. Compter les échecs permettrait
  à un échec de Telegram de consommer le quota de quelqu'un d'honnête.
  L'inverse — un fraudeur dont les demandes aboutissent toutes — est
  justement le cas qu'on veut compter.

  Et elle est PAR CENTRE DE DONNÉES : quelqu'un qui change de pays trouve
  un compteur neuf. Pour une rafale depuis un téléphone, c'est sans effet ;
  pour un attaquant organisé, ça ne tient pas. C'est le même plafond de
  verre que le marquage des txid, et la même réponse : un stockage durable
  le fermerait.
  ═══════════════════════════════════════════════════════════════════════
  */
  const parJour = nombreRegle(env?.CHANGE_MAX_PAR_JOUR, 10)
  const jour = new Date().toISOString().slice(0, 10)
  if (numero.length === 8 && parJour > 0) {
    if ((await compteur('debit', `${numero}-${jour}`)) >= parJour) {
      return json(
        { ok: false, raison: `limite de ${parJour} demandes par jour atteinte` },
        0,
        429
      )
    }
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
  if (achat) {
    /*
    L'ACHAT SE VÉRIFIE MAINTENANT AUSSI, mais pas sur une chaîne.

    Le client a payé par Orange Money ; l'opérateur l'a dit par SMS au
    changeur ; le robot a transmis ce SMS. On cherche donc le SMS, pas un
    bloc.

    Sans robot installé, ça rend « indisponible » et l'achat repart non
    vérifié — exactement comme avant la v16.
    */
    verif = await verifierAchat(ordre, env)
  } else {
    verif = await verifierVente({
      monnaie: ordre.monnaie,
      // Le montant vient du téléphone, et il SERT DE CRITÈRE DE RECHERCHE,
      // pas de preuve : on cherche un versement qui lui ressemble, et c'est
      // le montant LU SUR LA CHAÎNE qui est ensuite affiché au changeur.
      montant: nombreFrancais(ordre.montantCrypto),
      txid: ordre.txid,
      // Le relais recalcule le montant marqué depuis CETTE référence : un
      // ordre forgé avec le montant de quelqu'un d'autre ne trouve rien.
      reference: ordre.reference,
      env,
    })
    /*
    LA COHÉRENCE DU PRIX NE CONCERNE QUE LES VENTES, et c'est asymétrique
    pour une raison concrète.

    Sur une vente, le relais lit le montant de crypto SUR LA CHAÎNE : il
    peut donc recalculer les francs dus et comparer. Sur un achat, le
    montant qu'il lit est en FRANCS, dans le SMS — et c'est justement le
    chiffre annoncé. Comparer un chiffre à lui-même ne vérifie rien.

    Ce qui protège l'achat est ailleurs : le changeur envoie la crypto que
    la demande indique, et le SMS confirme que les francs sont arrivés. Un
    client qui gonflerait la crypto demandée pour un paiement réel de
    10 000 francs se ferait reprendre par les bornes et par le taux
    affiché — mais pas par ce calcul-ci, qui ne s'applique pas.
    */
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
  /*
  L'ENCAISSEMENT AUSSI NE SERT QU'UNE FOIS.

  Sinon un seul vrai paiement de 10 000 francs justifie dix demandes de
  10 000 francs : le SMS existe, il est authentique, et il répond ✅ à
  chacune. C'est la même attaque que la réutilisation d'un txid, et elle
  se ferme de la même façon.

  L'empreinte est la référence de l'opérateur, ou à défaut le couple
  montant-numéro — ce qui a servi à le retrouver.
  */
  if (achat && verif && verif.etat === 'confirme') {
    const empreinte = verif.txid || `${verif.montant}-${numero}`
    await marquer('paiementservi', empreinte, 86400)
  }

  /*
  LES AUTRES MARQUAGES SUIVENT LA MÊME RÈGLE : APRÈS L'ENVOI.

  Avant, un échec de Telegram aurait brûlé la référence de paiement du
  client et consommé son quota du jour, pour une demande qui n'est jamais
  arrivée. Il n'aurait pas pu la redéposer.
  */
  if (ordre.sens === 'achat' && ordre.referencePaiement) {
    await marquer('refpay', ordre.referencePaiement, 86400)
  }
  if (numero.length === 8) {
    await incrementer('debit', `${numero}-${jour}`, 86400)
    /*
    TRENTE JOURS. Assez long pour qu'un client régulier reste connu d'un
    mois sur l'autre, assez court pour qu'un numéro abandonné depuis un an
    repasse par le petit plafond.

    Posé à CHAQUE demande aboutie, et non seulement à la première : ça
    repousse l'échéance tant que la personne revient, ce qui est
    exactement le comportement voulu.
    */
    await marquer('connu', numero, 30 * 86400)
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
  /*
  ═══════════════════════════════════════════════════════════════════════
  L'ACHAT ENTRE DANS LA MÊME RÈGLE, SAUF QUAND LE ROBOT N'EST PAS LA
  ═══════════════════════════════════════════════════════════════════════

  Depuis la v16, un achat peut être confirmé par le SMS de l'opérateur.
  Quand il l'est, la consigne dit d'envoyer. Quand le relais a cherché et
  n'a rien trouvé — « absent », « deja_servi » — elle dit d'attendre,
  comme pour une vente.

  MAIS SI LE ROBOT N'EST PAS INSTALLÉ, l'état est « indisponible » et la
  consigne doit rester celle d'avant : envoyer. Sinon tous les achats d'un
  changeur qui n'a pas installé le robot afficheraient « NE RIEN ENVOYER »,
  c'est-à-dire que la v16 casserait son service en prétendant le protéger.

  C'est la même règle que partout dans ce fichier : « on n'a pas regardé »
  n'est pas « il n'y a rien ».
  ═══════════════════════════════════════════════════════════════════════
  */
  const etat = verif ? verif.etat : 'indisponible'
  const rienVu = etat === 'absent' || etat === 'deja_servi'
  const verseVraiment = (etat === 'confirme' || (achat && !rienVu)) && !prixDouteux
  const aFaire = verseVraiment
    ? achat
      ? `ENVOYER ${ordre.montantCrypto} ${ordre.monnaie} a l'adresse ci-dessous`
      : `ENVOYER ${ordre.montantFcfa} FCFA au ${ordre.telephone || 'numero du client'}`
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

  const achat = ordre.sens === 'achat'

  if (verif.etat === 'confirme') {
    lignes.push('')
    /*
    ═══════════════════════════════════════════════════════════════════
    LA SOURCE DE LA PREUVE EST NOMMÉE, ET ELLE N'EST PAS LA MÊME
    ═══════════════════════════════════════════════════════════════════

    Sur une vente, c'est une chaîne publique : n'importe qui peut
    revérifier avec le txid.

    Sur un achat, c'est un SMS d'Orange reçu sur le téléphone du
    changeur. C'est fort — ça vient de l'opérateur, pas du client — mais
    ce n'est pas de même nature : un identifiant d'expéditeur se
    falsifie, là où un bloc ne se falsifie pas.

    Écrire « vérifié » sans dire PAR QUOI mettrait les deux au même
    niveau. Le changeur doit savoir sur quoi il s'appuie.
    ═══════════════════════════════════════════════════════════════════
    */
    lignes.push(
      achat
        ? '\u2705 ENCAISSEMENT CONFIRME PAR LE SMS DE L\u2019OPERATEUR'
        : '\u2705 VERSEMENT VERIFIE SUR LA CHAINE PAR LE RELAIS'
    )
    lignes.push(
      achat
        ? `Recu      : ${formatMontant(verif.montant)} FCFA  (lu dans le SMS)`
        : `Recu      : ${formatMontant(verif.montant)} ${ordre.monnaie}  (lu sur la chaine)`
    )
    /*
    L'EXPÉDITEUR EST LA SEULE LIGNE QUI PERMETTE DE DIRE « CE N'EST PAS LUI ».

    Deux clients qui vendent le même montant dans la même heure donnent
    deux demandes identiques sur tout le reste. Cette ligne les sépare, et
    c'est elle qu'on relit quand quelqu'un réclame un paiement qu'il dit
    ne pas avoir reçu.
    */
    /*
    SUR UN ACHAT, LE NUMÉRO N'EST RÉPÉTÉ QUE S'IL DIFFÈRE.

    Quand il coïncide avec celui que le client a déclaré, c'est déjà deux
    lignes plus haut. Quand il DIFFÈRE, c'est une information de première
    importance : quelqu'un dépose une demande en citant un paiement venu
    d'un autre numéro — ce qui arrive honnêtement (on paie depuis le
    téléphone de son frère) et malhonnêtement (on cite le paiement de
    quelqu'un d'autre). Dans les deux cas le changeur doit le voir.
    */
    const memeNumero =
      achat && verif.de && verif.de.includes(numeroComparable(ordre.telephone))
    if (verif.de && !memeNumero) {
      lignes.push(`${achat ? 'Paye depuis' : 'Envoye par'}: ${verif.de}`)
    }
    if (achat && verif.de && !memeNumero) {
      lignes.push('\u26A0\uFE0F Ce numero n\u2019est PAS celui declare dans la demande.')
    }
    if (achat && verif.rapproche) {
      // Dire COMMENT le rapprochement a été fait : par référence, c'est
      // net ; par montant et numéro, deux paiements identiques dans
      // l'heure pourraient se confondre, et il vaut mieux le savoir.
      lignes.push(`Rapproche : par ${verif.rapproche}`)
    }
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
    lignes.push(
      achat
        ? '\u{1F6A8} CET ENCAISSEMENT A DEJA SERVI UNE AUTRE DEMANDE'
        : '\u{1F6A8} CE TRANSFERT A DEJA SERVI UNE AUTRE DEMANDE'
    )
    lignes.push(`Txid : ${verif.txid || 'inconnu'}`)
    lignes.push('N\u2019ENVOIE RIEN. Cherche ce txid dans les demandes recentes :')
    lignes.push('soit c\u2019est un doublon, soit quelqu\u2019un reutilise un vrai')
    lignes.push('versement pour se faire payer deux fois.')
    return lignes
  }

  if (verif.etat === 'absent') {
    lignes.push('')
    if (achat) {
      lignes.push('\u274C AUCUN ENCAISSEMENT CORRESPONDANT')
      lignes.push(`Le relais a cherche dans tes SMS : ${verif.raison}.`)
      lignes.push('N\u2019ENVOIE RIEN MAINTENANT. Regarde ton solde Orange')
      lignes.push('Money toi-meme : soit le SMS tarde, soit le montant ne')
      lignes.push('correspond pas, soit le client n\u2019a rien paye.')
    } else {
      lignes.push('\u274C AUCUN VERSEMENT TROUVE SUR LA CHAINE')
      lignes.push(`Le relais a regarde ton adresse ${ordre.monnaie} : ${verif.raison}.`)
      lignes.push('N\u2019ENVOIE RIEN MAINTENANT. Deux causes possibles, et une')
      lignes.push('seule demande d\u2019agir : le retrait du client est encore en')
      lignes.push('file chez son exchange (ca arrive, regarde dans 15 min), ou')
      lignes.push('il n\u2019a rien envoye.')
    }
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
  /*
  SEPT NOEUDS BNB, ET CE N'EST PAS DE L'EXCES.

  Une sonde a montré les TROIS premiers en panne au même instant : deux
  429 de limite de débit et un 530. Trois hôtes qui tombent ensemble, ce
  n'est pas une coïncidence — les points d'entrée publics gratuits sont
  saturés aux mêmes heures, par les mêmes robots.

  Sur une liste de trois, ça rend la vérification indisponible. Sur une
  liste de sept, il faudrait que les sept tombent ensemble, et ils
  n'appartiennent pas aux mêmes opérateurs.

  Un nœud en plus ne coûte rien : le premier qui répond gagne, et les
  suivants ne sont jamais interrogés. Ce sont les nœuds ABSENTS qui
  coûtent.

  `bsc-dataseed.bnbchain.org` est volontairement en dernier : c'est le
  nœud officiel de Binance, et ce fichier porte la trace du filtrage que
  Binance applique aux centres de données — `api.binance.com` répond 403
  depuis un Worker. Peut-être que leur nœud RPC ne filtre pas ; /diag le
  dira, et c'est la mesure qui tranchera, pas cette hypothèse.
  */
  bnb: [
    'https://bsc-rpc.publicnode.com',
    'https://bsc.meowrpc.com',
    'https://bsc-dataseed1.defibit.io',
    'https://bsc-dataseed1.ninicoin.io',
    'https://1rpc.io/bnb',
    'https://bsc.drpc.org',
    'https://bsc-dataseed.bnbchain.org',
  ],
  eth: [
    'https://ethereum-rpc.publicnode.com',
    'https://eth.drpc.org',
    'https://eth.meowrpc.com',
    'https://1rpc.io/eth',
    'https://rpc.flashbots.net',
  ],
}

/*
LE TEMPS D'UN BLOC, SOUS-ESTIMÉ VOLONTAIREMENT.

BNB Chain est passée de 3 s à 1,5 s puis à 0,75 s en deux ans. Une valeur
trop GRANDE ferait calculer trop peu de blocs et rater des versements ;
une valeur trop petite fait interroger quelques blocs de trop, ce qui ne
coûte rien. On se trompe donc du côté qui ne perd rien.
*/
const MS_PAR_BLOC = { bnb: 700, eth: 11_000 }

/*
CINQ CENTS BLOCS PAR APPEL.

Mesuré : les sept nœuds BSC refusent deux mille d'un coup — « limit
exceeded ». Cinq cents passe partout, et découper coûte un aller-retour de
plus seulement quand la demande est ancienne.
*/
const TRANCHE_EVM = 500

/*
VINGT MINUTES AU PLUS, et c'est l'antériorité qui le permet.

Un versement ne compte que s'il est postérieur à l'ouverture de la
demande. Chercher au-delà de ce que quelqu'un met à confirmer, ouvrir son
exchange et retirer ne trouverait que des versements déjà écartés — pour
une quarantaine d'appels réseau.

Au-delà, il reste le txid, qui ne demande qu'un seul appel et ne connaît
aucune limite de plage.
*/
const PLAGE_EVM_MAX_MS = 20 * 60_000

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

/**
 * La fenêtre réellement appliquée, en millisecondes.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * TROIS HEURES, SAUF QUAND TROIS HEURES NE SUFFISENT PAS
 * ═══════════════════════════════════════════════════════════════════════
 *
 * CHANGE_FENETRE_MINUTES, en minutes. Non réglée : trois heures.
 *
 * Ce réglage est né d'un essai réel. Un versement de test était bien
 * arrivé sur la chaîne, et le relais répondait « absent » — non pas parce
 * qu'il ne le voyait pas, mais parce qu'il datait de plus de trois heures.
 * Le comportement était juste ; la durée, trop courte pour le cas qui se
 * présentait.
 *
 * Et ce cas n'a rien d'exceptionnel : quelqu'un retire depuis une
 * plateforme d'échange dont la file traîne, part travailler, et revient
 * déposer sa demande le soir. Lui répondre « aucun versement » alors que
 * ses fonds sont arrivés depuis midi, c'est l'envoyer chercher une panne
 * qui n'existe pas.
 *
 * ─── CE QU'UNE FENÊTRE PLUS LARGE NE COÛTE PAS ──────────────────────
 *
 * Elle ne rouvre PAS la réutilisation d'un versement : le marquage du
 * txid s'en charge, et il tient vingt-quatre heures. Deux protections
 * distinctes, et c'est la seconde qui porte celle-là.
 *
 * Ce que la fenêtre borne, c'est autre chose : qu'un versement d'il y a
 * un mois, légitime et jamais utilisé, serve à justifier une demande
 * aujourd'hui. D'où le plafond de sept jours — au-delà, ce n'est plus un
 * retard, c'est un autre sujet.
 * ═══════════════════════════════════════════════════════════════════════
 */
function fenetreVente(env) {
  const minutes = Number(env?.CHANGE_FENETRE_MINUTES)
  if (!Number.isFinite(minutes) || minutes <= 0) return FENETRE_VENTE_MS
  // Entre un quart d'heure et sept jours. En dessous, on refuserait des
  // versements en cours de confirmation ; au-dessus, la fenêtre cesse de
  // borner quoi que ce soit.
  const borne = Math.min(Math.max(minutes, 15), 7 * 24 * 60)
  return borne * 60 * 1000
}

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
    if (maintenant - quand > (critere.fenetre || FENETRE_VENTE_MS)) continue
    /*
    ANTÉRIORITÉ. Une marge d'une minute parce que l'horloge d'un bloc et
    celle du relais ne sont pas la même, et qu'un versement émis à la
    seconde où la demande s'ouvre ne doit pas être rejeté pour un décalage
    de quelques centaines de millisecondes.
    */
    if (critere.apres && quand < critere.apres - 60_000) continue
    // Une horodate dans le futur n'existe pas sur une chaîne : cinq
    // minutes d'avance tolérées pour la dérive d'horloge, pas plus.
    if (quand - maintenant > 5 * 60 * 1000) continue

    const lu = depuisUnites(t?.value, decimales)
    /*
    EXACT QUAND LE MONTANT EST MARQUÉ, À UN POUR CENT PRÈS SINON.

    La tolérance existait pour absorber un arrondi d'affichage. Elle est
    aussi ce qui permettait de confondre deux versements voisins — et donc
    de réclamer celui de quelqu'un d'autre. Quand le relais impose
    lui-même le montant, il n'y a plus rien à absorber.
    */
    const correspond = critere.exact
      ? memeMontant(lu, montant)
      : montantProche(lu, montant)
    if (!correspond) continue

    return { txid: ident, montant: lu, quand, de: String(t?.from ?? '').trim() }
  }
  return null
}

/*
═══════════════════════════════════════════════════════════════════════════
ETHERSCAN V2 — PARCE QUE `eth_getLogs` EST UN CUL-DE-SAC
═══════════════════════════════════════════════════════════════════════════

MESURÉ, pas supposé. `/diag/plages?reseau=bnb` depuis le Worker, sur les
sept nœuds :

    bsc-rpc.publicnode.com      1 bloc → Rate limit exceeded
    bsc.meowrpc.com             1 bloc → HTTP 429 Too Many Requests
    bsc-dataseed1.defibit.io    1 bloc → limit exceeded
    bsc-dataseed1.ninicoin.io   1 bloc → limit exceeded
    1rpc.io/bnb                 usage limit (même pas la hauteur)
    bsc.drpc.org                Public endpoint rate limit
    bsc-dataseed.bnbchain.org   1 bloc → limit exceeded

UN SEUL BLOC. Ce n'était donc jamais une question de plage, et la
pagination que j'avais écrite corrigeait un défaut qui n'existait pas.

Ce que la mesure dit vraiment : `eth_blockNumber` passe sur cinq nœuds
sur sept, mais `eth_getLogs` est refusé partout. Les nœuds `dataseed` ne
servent pas cette méthode du tout, et les autres nous limitent PAR IP —
celle d'un Worker Cloudflare, partagée avec tous les robots du monde.
Aucun réglage de notre côté ne change ça.

─── POURQUOI CETTE SOURCE-LÀ ───────────────────────────────────────────

Parce qu'elle pose la question qu'on veut poser. `eth_getLogs` demande
« que s'est-il passé entre ces deux blocs ? » — et nous forçait donc à
deviner les bons blocs, puis à trier. Etherscan répond directement à
« quels transferts de ce jeton sont arrivés à cette adresse ? », et c'est
MOT POUR MOT la question que TronGrid sert déjà pour TRON.

Cette symétrie n'est pas un agrément : elle veut dire que `choisirTransfertEtherscan`
porte les mêmes contrôles, dans le même ordre, que `choisirTransfertTron`,
qui est relu et éprouvé. Un chemin de vérification qui ressemble à l'autre
est un chemin qu'on sait lire.

Et une seule clé gratuite couvre BNB (`chainid=56`) et Ethereum
(`chainid=1`). Pas deux comptes, pas deux quotas.

─── CE QU'ON GAGNE EN PLUS ─────────────────────────────────────────────

L'ANTÉRIORITÉ DEVIENT VRAIE. Un journal rendu par `eth_getLogs` ne porte
pas d'horodate : on bornait le temps en bornant les blocs, ce qui est une
approximation. Etherscan rend `timeStamp`, donc le même contrôle qu'en
TRON s'applique — au lieu d'être déduit d'un calcul sur la durée d'un bloc.
═══════════════════════════════════════════════════════════════════════════
*/
const ETHERSCAN = 'https://api.etherscan.io/v2/api'

/** L'identifiant de chaîne Etherscan V2, par réseau. */
const CHAINE_ETHERSCAN = { bnb: 56, eth: 1 }

/**
 * Le transfert de jeton choisi dans la liste rendue par Etherscan.
 *
 * MÊMES CONTRÔLES QUE `choisirTransfertTron`, DANS LE MÊME ORDRE. Les
 * noms des champs changent, les questions posées ne changent pas :
 * est-ce bien arrivé chez nous, est-ce bien ce jeton, avec ces décimales,
 * après l'ouverture de la demande, et pour ce montant exact.
 */
function choisirTransfertEtherscan(liste, critere) {
  const { adresse, contrat, montant, txid, maintenant } = critere
  if (!Array.isArray(liste)) return null

  for (const t of liste) {
    const ident = String(t?.hash ?? '').trim()
    if (!ident) continue
    if (txid && ident.toLowerCase() !== String(txid).trim().toLowerCase()) continue

    /*
    LE DESTINATAIRE, COMPARÉ SANS LA CASSE — et c'est une différence avec
    TRON, pas un relâchement. Une adresse EVM s'écrit indifféremment en
    minuscules ou en casse de contrôle ; Etherscan rend l'une, nos
    réglages portent souvent l'autre. Comparer brut rejetterait un
    versement parfaitement valide.

    Sur TRON, au contraire, la casse PORTE le code de contrôle : la
    comparer strictement y est obligatoire. Les deux lignes se
    ressemblent et disent le contraire ; c'est voulu.
    */
    if (String(t?.to ?? '').trim().toLowerCase() !== String(adresse).trim().toLowerCase()) continue

    // Le contrat, sans quoi un jeton nommé « USDT » déployé ce matin
    // passerait pour de l'USDT. Absent ⇒ on refuse, on ne devine pas.
    const contratLu = String(t?.contractAddress ?? '').trim().toLowerCase()
    if (!contratLu || contratLu !== String(contrat).trim().toLowerCase()) continue

    // Les décimales viennent de la réponse et doivent valoir les nôtres.
    // 18 lues comme 6, c'est un facteur mille milliards.
    const decimales = Number(t?.tokenDecimal)
    if (!Number.isInteger(decimales) || decimales !== critere.decimales) continue

    /*
    L'HORODATE EST EN SECONDES ICI, en millisecondes chez TronGrid. Une
    conversion oubliée placerait tous les versements en 1970 et ferait
    échouer l'antériorité sur tout — ou, selon le sens de l'erreur, la
    ferait passer sur n'importe quoi.
    */
    const quand = Number(t?.timeStamp) * 1000
    if (!Number.isFinite(quand) || quand <= 0) continue
    if (maintenant - quand > (critere.fenetre || FENETRE_VENTE_MS)) continue
    // Antériorité, avec la minute de marge des horloges de blocs.
    if (critere.apres && quand < critere.apres - 60_000) continue
    if (quand - maintenant > 5 * 60 * 1000) continue

    const lu = depuisUnites(t?.value, decimales)
    const correspond = critere.exact
      ? memeMontant(lu, montant)
      : montantProche(lu, montant)
    if (!correspond) continue

    return { txid: ident, montant: lu, quand, de: String(t?.from ?? '').trim() }
  }
  return null
}

/**
 * Interroge Etherscan pour les transferts du jeton arrivés chez le changeur.
 *
 * Rend la liste brute. Le tri est fait par `choisirTransfertEtherscan`,
 * qui est pur et donc éprouvable sans réseau.
 */
async function listerEtherscan(cfg, adresse, cle) {
  const chaine = CHAINE_ETHERSCAN[cfg.reseau]
  if (!chaine) throw new Error(`etherscan : reseau ${cfg.reseau} inconnu`)

  const url =
    `${ETHERSCAN}?chainid=${chaine}&module=account&action=tokentx` +
    `&contractaddress=${encodeURIComponent(cfg.contrat)}` +
    `&address=${encodeURIComponent(adresse)}` +
    `&page=1&offset=50&sort=desc&apikey=${encodeURIComponent(cle)}`

  const r = await fetchBorne(url, { headers: { accept: 'application/json', 'user-agent': AGENT } }, 8000)
  if (!r.ok) throw new Error(`etherscan HTTP ${r.status}`)
  const o = await r.json()

  /*
  « NO TRANSACTIONS FOUND » N'EST PAS UNE PANNE.

  Etherscan rend `status: "0"` à la fois pour « rien trouvé » et pour
  « ta clé est mauvaise ». Les confondre coûterait cher dans les deux
  sens : traiter une clé invalide comme « rien trouvé » refuserait
  silencieusement toutes les ventes, et traiter « rien trouvé » comme une
  panne afficherait `indisponible` à quelqu'un dont le versement n'est
  simplement pas encore arrivé.

  On les sépare sur le message, qui est le seul champ qui les distingue.
  */
  if (Array.isArray(o?.result)) return o.result
  const message = String(o?.message ?? o?.result ?? '').toLowerCase()
  if (message.includes('no transactions found')) return []
  throw new Error(`etherscan : ${String(o?.result ?? o?.message ?? 'reponse illisible').slice(0, 90)}`)
}

/*
═══════════════════════════════════════════════════════════════════════════
`alchemy_getAssetTransfers` — LA BONNE QUESTION, ENFIN POSABLE
═══════════════════════════════════════════════════════════════════════════

MESURÉ sur le nœud nominatif :

    bnb-mainnet.g.alchemy.com   accepte 10 blocs
    25 blocs → « Under the Free tier plan, you can make eth_getLogs
                 requests with up to a 10 block range »

C'est un vrai progrès — les sept publics refusaient UN bloc, celui-ci en
accepte dix. Mais dix blocs font sept secondes de chaîne BNB, et couvrir
vingt minutes demanderait CENT SOIXANTE-DIX appels par vérification.
Ce n'est pas une tranche à baisser, c'est un chemin impraticable.

─── POURQUOI CETTE MÉTHODE-LÀ CHANGE TOUT ───────────────────────────────

`eth_getLogs` demande « que s'est-il passé entre ces deux blocs ? ». La
plage est dans la question, donc la limite de plage est fatale.

`alchemy_getAssetTransfers` demande « quels transferts de ce jeton sont
arrivés à cette adresse ? » — la MÊME question que TronGrid sert pour
TRON et qu'Etherscan sert pour Ethereum. Il n'y a plus de plage à
découper, donc plus rien à plafonner : un seul appel couvre toute
l'histoire de l'adresse, et `order: "desc"` met le dernier versement en
premier.

Les trois chaînes posent désormais la même question par trois portes
différentes, et les trois tris portent les mêmes contrôles dans le même
ordre. C'est ce qui rend l'ensemble relisable.

─── LE PIÈGE DE CETTE RÉPONSE ───────────────────────────────────────────

Elle contient DEUX montants. `value` est un nombre flottant déjà converti,
commode et FAUX au-delà de quinze chiffres significatifs — c'est
exactement le défaut que `depuisUnites` existe pour éviter. On lit donc
`rawContract.value`, l'entier brut en hexadécimal, et on le divise nous-
mêmes. Un marquage se joue sur la sixième décimale ; un arrondi du
fournisseur le détruirait sans rien signaler.
═══════════════════════════════════════════════════════════════════════════
*/

/** Un nœud Alchemy sert-il les méthodes enrichies ? */
function estAlchemy(url) {
  return /(^|\/\/|\.)alchemy\.com\//.test(String(url))
}

/**
 * Le transfert choisi dans la réponse d'`alchemy_getAssetTransfers`.
 *
 * MÊMES CONTRÔLES QUE LES DEUX AUTRES TRIS, DANS LE MÊME ORDRE. Seuls les
 * noms des champs changent.
 */
function choisirTransfertAlchemy(liste, critere) {
  const { adresse, contrat, montant, txid, maintenant } = critere
  if (!Array.isArray(liste)) return null

  for (const t of liste) {
    const ident = String(t?.hash ?? '').trim()
    if (!ident) continue
    if (txid && ident.toLowerCase() !== String(txid).trim().toLowerCase()) continue

    // Casse tolérée : une adresse EVM s'écrit indifféremment.
    if (String(t?.to ?? '').trim().toLowerCase() !== String(adresse).trim().toLowerCase()) continue

    // La catégorie, sinon un transfert natif de BNB pourrait se présenter
    // là où on attend un jeton.
    if (String(t?.category ?? '').toLowerCase() !== 'erc20') continue

    const brut = t?.rawContract || {}
    const contratLu = String(brut?.address ?? '').trim().toLowerCase()
    if (!contratLu || contratLu !== String(contrat).trim().toLowerCase()) continue

    /*
    LES DÉCIMALES ARRIVENT EN HEXADÉCIMAL : « 0x12 » vaut 18, pas 12.
    Les lire en base dix donnerait douze — six ordres de grandeur
    d'écart, et un versement de 2 USDT lu comme deux millions.
    */
    const decimales = Number.parseInt(String(brut?.decimal ?? ''), 16)
    if (!Number.isInteger(decimales) || decimales !== critere.decimales) continue

    /*
    L'HORODATE EST UNE DATE ISO ICI — ni des secondes comme chez
    Etherscan, ni des millisecondes comme chez TronGrid. Trois sources,
    trois formats : c'est le genre de détail qui ne lève aucune erreur et
    fausse tout.
    */
    const quand = Date.parse(String(t?.metadata?.blockTimestamp ?? ''))
    if (!Number.isFinite(quand) || quand <= 0) continue
    if (maintenant - quand > (critere.fenetre || FENETRE_VENTE_MS)) continue
    if (critere.apres && quand < critere.apres - 60_000) continue
    if (quand - maintenant > 5 * 60 * 1000) continue

    /*
    ON LIT L'ENTIER BRUT, JAMAIS `t.value`. Voir le commentaire du bloc
    ci-dessus : `t.value` est un flottant déjà arrondi par le
    fournisseur, et le marquage se joue sur la sixième décimale.
    */
    let unites = ''
    try {
      unites = BigInt(String(brut?.value ?? '0x0')).toString()
    } catch (_) {
      continue
    }
    const lu = depuisUnites(unites, decimales)
    const correspond = critere.exact ? memeMontant(lu, montant) : montantProche(lu, montant)
    if (!correspond) continue

    return { txid: ident, montant: lu, quand, de: String(t?.from ?? '').trim() }
  }
  return null
}

/** Interroge un nœud Alchemy pour les transferts du jeton arrivés chez le changeur. */
async function listerAlchemy(noeud, cfg, adresse) {
  const r = await fetchBorne(
    noeud,
    {
      method: 'POST',
      headers: { 'content-type': 'application/json', 'user-agent': AGENT },
      body: JSON.stringify({
        jsonrpc: '2.0',
        id: 1,
        method: 'alchemy_getAssetTransfers',
        params: [
          {
            fromBlock: '0x0',
            toBlock: 'latest',
            // Le destinataire est le changeur. Jamais une adresse venue
            // de la requête : c'est le Worker qui décide chez qui on
            // regarde.
            toAddress: adresse,
            contractAddresses: [cfg.contrat],
            category: ['erc20'],
            // Sans quoi il n'y a pas d'horodate, et sans horodate il n'y
            // a ni fenêtre ni antériorité.
            withMetadata: true,
            excludeZeroValue: true,
            order: 'desc',
            maxCount: '0x32',
          },
        ],
      }),
    },
    9000
  )
  if (!r.ok) throw new Error(`alchemy HTTP ${r.status}`)
  const o = await r.json()
  if (o?.error) throw new Error(`alchemy : ${String(o.error?.message ?? 'rpc').slice(0, 90)}`)
  const transferts = o?.result?.transfers
  if (!Array.isArray(transferts)) throw new Error('alchemy : reponse sans transfers')
  return transferts
}

/**
 * Ce refus d'Etherscan dit-il « pas dans ton offre » ?
 *
 * ON RECONNAÎT LA PHRASE, FAUTE DE MIEUX. Etherscan ne rend pas de code
 * distinct pour ce cas : même `status: "0"` que pour une clé invalide, et
 * seul le texte les sépare. Reconnaître un texte est fragile — il peut
 * être réécrit sans préavis — mais l'alternative est de traiter tous les
 * refus pareil, et on perdrait soit le secours, soit le motif.
 *
 * Si la phrase change, le symptôme sera `indisponible` avec le motif
 * d'Etherscan en clair dans le message du changeur. Visible, donc
 * réparable. C'est le mode d'échec qu'on choisit.
 */
function chaineHorsOffre(message) {
  const m = String(message ?? '').toLowerCase()
  return (
    m.includes('not supported for this chain') ||
    m.includes('upgrade your api plan') ||
    m.includes('upgrade your plan')
  )
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
/*
LES LARGEURS SONDÉES, ET POURQUOI ELLES MONTENT PAR PALIERS.

On cherche un seuil, pas une valeur. Doubler à chaque fois trouverait le
seuil en moins d'appels, mais nous laisserait avec « entre 128 et 256 » —
alors que ce qu'il faut écrire dans TRANCHE_EVM est un nombre rond qu'on
peut tenir. Ces paliers-là SONT les candidats.

On s'arrête au premier refus : au-delà, ce sera pire, et insister ne fait
qu'allonger une sonde qui tourne déjà contre sept hôtes.
*/
const PLAGES_SONDEES = [1, 10, 25, 50, 100, 200, 500, 1000]

/**
 * Mesure, nœud par nœud, la plage de blocs acceptée par `eth_getLogs`.
 *
 * Rend pour chaque hôte la plus grande largeur qui a répondu, et le motif
 * exact du premier refus. C'est ce motif qui dit s'il s'agit d'une limite
 * de plage (« exceed maximum block range ») ou d'une limite de débit
 * (« rate limit »), et les deux ne se corrigent pas de la même façon.
 */
async function diagnostiquerPlages(env, params) {
  const reseau = (params?.get('reseau') || 'bnb').toLowerCase()
  /*
  LA SONDE MESURE LA LISTE RÉELLE, nœud nominatif compris. Mesurer la
  liste publique pendant que la vérification en emploie une autre
  rendrait un diagnostic qui décrit un code qui ne tourne pas — on a déjà
  perdu quatre tours là-dessus avec « Save version ».
  */
  const hotes = NOEUDS_EVM[reseau] ? noeudsEvm(reseau, env) : null
  if (!hotes) {
    return json({ ok: false, erreur: `reseau inconnu : ${reseau}`, connus: Object.keys(NOEUDS_EVM) }, 400)
  }

  /*
  ON SONDE AVEC LE VRAI CONTRAT ET LES VRAIS SUJETS.

  Un `eth_getLogs` sans filtre d'adresse rapporte tous les journaux de la
  plage — des milliers — et se ferait refuser pour une raison qui n'est
  pas celle qu'on mesure. La sonde doit poser la question que pose la
  vérification, mot pour mot, sinon elle mesure autre chose.
  */
  const cfg = Object.values(CHAINES_VENTE).find((c) => c.reseau === reseau && c.contrat)
  const sujets = [TOPIC_TRANSFER, null, topicDepuisAdresse('0x' + '11'.repeat(20))]

  const resultats = {}
  for (const hote of hotes) {
    // JAMAIS l'URL entière : celle du nœud nominatif porte sa clé.
    const nom = hoteMasque(hote)
    let dernier = 0
    try {
      const h = await rpcBrut(hote, 'eth_blockNumber', [])
      if (h.erreur) { resultats[nom] = { hauteur: `✗ ${h.erreur}` }; continue }
      dernier = parseInt(h.resultat, 16)
      if (!Number.isFinite(dernier) || dernier <= 0) {
        resultats[nom] = { hauteur: `✗ illisible : ${String(h.resultat).slice(0, 40)}` }
        continue
      }
    } catch (e) {
      resultats[nom] = { hauteur: `✗ ${String(e?.message ?? e).slice(0, 60)}` }
      continue
    }

    let maxOk = 0
    let refus = null
    for (const largeur of PLAGES_SONDEES) {
      // Deux blocs de marge : un `toBlock` égal à la tête est parfois
      // refusé le temps que le nœud propage le dernier bloc.
      const haut = dernier - 2
      const bas = haut - largeur + 1
      const r = await rpcBrut(hote, 'eth_getLogs', [
        {
          address: cfg?.contrat,
          topics: sujets,
          fromBlock: '0x' + bas.toString(16),
          toBlock: '0x' + haut.toString(16),
        },
      ])
      if (r.erreur) { refus = { largeur, motif: r.erreur }; break }
      if (!Array.isArray(r.resultat)) { refus = { largeur, motif: 'resultat non-tableau' }; break }
      maxOk = largeur
    }

    resultats[nom] = {
      hauteur: dernier,
      accepte: maxOk === 0 ? 'AUCUNE' : `${maxOk} blocs`,
      refus: refus ? `${refus.largeur} blocs → ${refus.motif}` : `rien jusqu'a ${maxOk}`,
    }
  }

  /*
  LA CONCLUSION EST CALCULÉE, PAS LAISSÉE À LIRE.

  Sept lignes de mesures demandent d'être comparées à la main, et c'est là
  qu'on se trompe. Ce qui décide de TRANCHE_EVM est la plus grande largeur
  qu'un nœud VIVANT accepte — parce qu'il suffit d'un nœud qui réponde.
  */
  const vivants = Object.values(resultats).filter((r) => typeof r.hauteur === 'number' && r.accepte !== 'AUCUNE')
  const meilleur = vivants.reduce((m, r) => Math.max(m, parseInt(r.accepte, 10) || 0), 0)

  /*
  LA CONCLUSION DIT AUSSI CE QU'IL FAUT FAIRE, et pas seulement ce qui
  s'est passé. « Aucun nœud ne répond » laissait chercher le remède ; la
  cause est connue et le remède tient en une variable.
  */
  const nomVariable = `CHANGE_NOEUD_${String(reseau).toUpperCase()}`
  const aUnNoeudPerso = hotes.length > (NOEUDS_EVM[reseau] || []).length

  return json({
    ok: true,
    version: VERSION,
    reseau,
    contrat: cfg?.contrat ?? '(aucun contrat regle pour ce reseau)',
    tranche_actuelle: TRANCHE_EVM,
    noeud_nominatif: aUnNoeudPerso ? `oui (${nomVariable} est reglee)` : `non (${nomVariable} n'est pas reglee)`,
    noeuds: resultats,
    conclusion:
      vivants.length === 0
        ? aUnNoeudPerso
          ? `AUCUN noeud ne repond a eth_getLogs, ${nomVariable} comprise. ` +
            'Verifie que son URL est complete et que son quota n’est pas epuise.'
          : 'AUCUN noeud ne repond a eth_getLogs. C’est attendu : les points d’entree ' +
            'publics gratuits refusent cette methode aux IP de Cloudflare. ' +
            `Regle ${nomVariable} dans Cloudflare avec l’URL d’un noeud nominatif.`
        : `${vivants.length} noeud(s) acceptent au moins une plage ; la plus large est ${meilleur} blocs. ` +
          (meilleur >= TRANCHE_EVM
            ? `TRANCHE_EVM (${TRANCHE_EVM}) passe.`
            : `TRANCHE_EVM (${TRANCHE_EVM}) est TROP GRANDE : il faut descendre a ${meilleur}.`),
  })
}

/**
 * Rejoue la sélection et dit, transfert par transfert, ce qui a échoué.
 *
 * Les paramètres sont ceux de `/change/verifier` : monnaie, montant,
 * reference. Le montant marqué est recalculé ici de la même façon, donc
 * un écart de marquage se verrait aussi.
 */
async function diagnostiquerPourquoi(env, params) {
  const monnaie = String(params?.get('monnaie') ?? '').trim().toUpperCase()
  const montantSocle = Number(params?.get('montant'))
  const reference = String(params?.get('reference') ?? '').trim()

  const cfg = CHAINES_VENTE[monnaie]
  if (!cfg) return json({ ok: false, erreur: `${monnaie} n'est pas verifiable ici` }, 400)
  if (!(montantSocle > 0)) return json({ ok: false, erreur: 'montant illisible' }, 400)
  if (cfg.chaine !== 'evm') {
    return json({ ok: false, erreur: `cette sonde ne couvre que les chaines EVM, pas ${cfg.chaine}` }, 400)
  }

  const adresse = adressesChangeur(env)[monnaie]
  if (!adresse) return json({ ok: false, erreur: `aucune adresse reglee pour ${monnaie}` }, 400)

  const marque = marquageActif(env) && reference.length > 0
  const cherche = marque ? await montantMarque(montantSocle, reference, env) : montantSocle
  const naissance = marque ? Number((await lireValeur('demande', reference))?.ne) || 0 : 0
  const maintenant = Date.now()
  const fenetre = fenetreVente(env)

  const noeud = noeudsEvm(cfg.reseau, env).find(estAlchemy)
  if (!noeud) {
    return json({ ok: false, erreur: `aucun noeud servant les transferts enrichis pour ${cfg.reseau}` }, 400)
  }

  let liste
  try {
    liste = await listerAlchemy(noeud, cfg, adresse)
  } catch (e) {
    return json({ ok: false, erreur: String(e?.message ?? e).slice(0, 160) })
  }

  const critere = {
    adresse, contrat: cfg.contrat, decimales: cfg.decimales,
    montant: cherche, maintenant, fenetre, apres: naissance, exact: marque,
  }

  /*
  CHAQUE CONTRÔLE EST DÉCRIT, PAS REJOUÉ DE MÉMOIRE.

  L'ordre suit celui du sélecteur. On ne s'arrête PAS au premier échec :
  savoir qu'un transfert rate l'antériorité ET le montant vaut mieux que
  d'apprendre l'un puis l'autre en deux essais.
  */
  const examen = liste.slice(0, 10).map((t) => {
    const brut = t?.rawContract || {}
    const decimalesLues = Number.parseInt(String(brut?.decimal ?? ''), 16)
    let lu = null
    try {
      lu = depuisUnites(BigInt(String(brut?.value ?? '0x0')).toString(), decimalesLues)
    } catch (_) { /* valeur illisible : signalée ci-dessous */ }
    const quand = Date.parse(String(t?.metadata?.blockTimestamp ?? ''))

    const controles = {
      destinataire:
        String(t?.to ?? '').toLowerCase() === adresse.toLowerCase()
          ? 'ok' : `recu par ${String(t?.to ?? '(vide)').slice(0, 12)}…, pas par le changeur`,
      categorie:
        String(t?.category ?? '').toLowerCase() === 'erc20'
          ? 'ok' : `categorie « ${t?.category} », attendu « erc20 »`,
      contrat:
        String(brut?.address ?? '').toLowerCase() === String(cfg.contrat).toLowerCase()
          ? 'ok' : `contrat ${String(brut?.address ?? '(vide)').slice(0, 12)}… — PAS le vrai jeton`,
      decimales:
        decimalesLues === cfg.decimales
          ? 'ok' : `lues ${decimalesLues}, attendues ${cfg.decimales}`,
      horodate:
        Number.isFinite(quand) && quand > 0 ? 'ok' : `illisible : « ${t?.metadata?.blockTimestamp} »`,
      fenetre:
        maintenant - quand <= fenetre
          ? 'ok' : `vieux de ${Math.round((maintenant - quand) / 60000)} min, fenetre ${Math.round(fenetre / 60000)} min`,
      /*
      « OK » NE DOIT PAS ÊTRE DIT QUAND LE CONTRÔLE N'A PAS EU LIEU.

      Sans naissance connue, l'antériorité est DÉSACTIVÉE — le relais
      laisse passer plutôt que de punir quelqu'un parce qu'il a oublié
      quand la demande est née. Afficher « ok » dans ce cas laisserait
      croire qu'une barrière a tenu, alors qu'elle n'a pas été posée.

      Et une naissance inconnue n'est pas anodine : elle signifie que
      cette référence n'est jamais passée par `/change/consigne`. Donc,
      le plus souvent, qu'on diagnostique une AUTRE référence que celle
      de l'essai.
      */
      anteriorite: !naissance
        ? 'NON APPLIQUE — naissance inconnue, donc ce controle est desactive. ' +
          'Cette reference n\'est jamais passee par /change/consigne.'
        : quand >= naissance - 60_000
          ? 'ok'
          : `arrive ${Math.round((naissance - quand) / 60000)} min AVANT l'ouverture de la demande`,
      montant:
        (marque ? memeMontant(lu, cherche) : montantProche(lu, cherche))
          ? 'ok'
          : `lu ${lu}, cherche ${cherche}` +
            (lu !== null && lu < cherche ? ' — la difference ressemble a une commission d\'exchange' : ''),
    }
    const rates = Object.entries(controles).filter(([, v]) => v !== 'ok').map(([k]) => k)
    return { txid: String(t?.hash ?? '').slice(0, 14) + '…', montant_lu: lu, rates, controles }
  })

  /*
  LE VERDICT VIENT DU VRAI SÉLECTEUR, et on compare. Si l'examen
  ci-dessus dit « tout passe » alors que le sélecteur rend null — ou
  l'inverse — c'est que ce diagnostic a dérivé du code qu'il décrit, et
  il doit le crier plutôt que d'induire en erreur.
  */
  /*
  LE CAS « TOUT SAUF LE MONTANT » MERITE SA PHRASE.

  C'est de loin le plus frequent, et c'est aussi le plus rassurant : il
  dit que toute la chaine de preuve fonctionne et que l'argent est arrive.
  Le noyer dans « lis la colonne rates » ferait chercher un defaut la ou
  il n'y en a pas.
  */
  const seulementMontant =
    examen.length > 0 && examen.every((e) => e.rates.length === 1 && e.rates[0] === 'montant')

  const trouve = choisirTransfertAlchemy(liste, critere)
  const examenDitOui = examen.some((e) => e.rates.length === 0)
  const desaccord = Boolean(trouve) !== examenDitOui

  /*
  ═══════════════════════════════════════════════════════════════════════
  UNE REFERENCE INCONNUE REND TOUT LE RESTE TROMPEUR
  ═══════════════════════════════════════════════════════════════════════

  Si la reference n'est jamais passee par /change/consigne, deux choses
  sont fausses d'un coup, et aucune ne se voit :

    — le montant cherche est derive d'une chaine qui n'a jamais ete
      utilisee, donc il ne correspond a AUCUN essai ;
    — l'anteriorite est desactivee faute de naissance.

  Le diagnostic reste alors parfaitement coherent et parfaitement inutile,
  et on peut le relire trois fois sans voir qu'il parle d'autre chose.

  La cause la plus frequente, mesuree sur ce projet : un exemple de
  documentation colle tel quel. Il faut donc que ca CRIE, en premier.
  ═══════════════════════════════════════════════════════════════════════
  */
  const referenceInconnue = marque && !naissance

  return json({
    ok: true,
    version: VERSION,
    ...(referenceInconnue
      ? {
          AVERTISSEMENT:
            `La reference « ${reference} » est inconnue du relais : elle n'est jamais ` +
            'passee par /change/consigne. Le montant cherche ci-dessous est derive ' +
            'd\'elle, donc il ne correspond a AUCUN essai, et l\'anteriorite est ' +
            'desactivee. Relance l\'essai et reprends la reference VX-… qu\'il imprime.',
        }
      : {}),
    monnaie,
    adresse_du_changeur: adresse,
    marquage: marque ? 'actif' : 'INACTIF (aucune reference, ou CHANGE_MARQUAGE non reglee)',
    montant_cherche: cherche,
    demande_ouverte_a: naissance ? new Date(naissance).toISOString() : '(inconnue)',
    fenetre_minutes: Math.round(fenetre / 60000),
    transferts_lus: liste.length,
    examen,
    verdict: trouve ? 'TROUVE' : 'absent',
    desaccord_du_diagnostic: desaccord
      ? 'OUI — ce diagnostic ne decrit plus le selecteur, ne te fie pas a son detail'
      : 'non',
    conclusion: trouve
      ? 'Un transfert passe tous les controles. Si /change/verifier dit encore absent, relance-le.'
      : liste.length === 0
        ? 'AUCUN transfert de ce jeton sur cette adresse : le versement n\'est pas arrive. ' +
          'Verifie le reseau choisi sur l\'exchange (BEP20) et l\'etat du retrait.'
        : seulementMontant
          ? 'TOUT PASSE SAUF LE MONTANT. La chaine, le contrat, les decimales et la ' +
            'fenetre sont bons : le versement est bien arrive chez le changeur, il ne ' +
            'correspond simplement a aucune demande. Compare « lu » et « cherche » : un ' +
            'ecart de quelques centiemes est une commission d\'exchange, un ecart franc ' +
            'est un montant qui n\'a pas ete recopie.'
          : 'Des transferts existent mais aucun ne passe. Lis la colonne « rates » : ' +
            'elle nomme le controle qui bloque, pour chacun.',
  })
}

/** Les champs dont `choisirTransfertAlchemy` a besoin pour trier. */
const CHAMPS_ALCHEMY = ['hash', 'from', 'to', 'category', 'rawContract', 'metadata']

/** Vérifie que le nœud nominatif sert les transferts enrichis, et sous quelle forme. */
async function diagnostiquerTransferts(env, params) {
  const reseau = (params?.get('reseau') || 'bnb').toLowerCase()
  if (!NOEUDS_EVM[reseau]) return json({ ok: false, erreur: `reseau inconnu : ${reseau}` }, 400)

  const noeud = noeudsEvm(reseau, env).find(estAlchemy)
  const nomVariable = `CHANGE_NOEUD_${String(reseau).toUpperCase()}`
  if (!noeud) {
    return json({
      ok: false,
      version: VERSION,
      erreur: `aucun noeud servant les transferts enrichis dans ${nomVariable}`,
      remede: `Regle ${nomVariable} avec l'URL d'un noeud Alchemy pour ce reseau.`,
    }, 400)
  }

  const entree = Object.entries(CHAINES_VENTE).find(
    ([, c]) => c.reseau === reseau && c.contrat && !c.natif
  )
  if (!entree) return json({ ok: false, erreur: `aucun jeton regle pour ${reseau}` }, 400)
  const [monnaie, cfg] = entree

  const adresse = trierAdresses(env?.CHANGE_ADRESSES).bonnes[monnaie] || ''
  if (!adresse) {
    return json({
      ok: false,
      version: VERSION,
      erreur: `aucune adresse reglee pour ${monnaie} dans CHANGE_ADRESSES`,
      remede: 'Sans elle, la sonde ne testerait pas la chaine complete.',
    }, 400)
  }

  try {
    const liste = await listerAlchemy(noeud, cfg, adresse)
    const premier = liste[0] || null
    const manquants = premier ? CHAMPS_ALCHEMY.filter((c) => !(c in premier)) : []
    /*
    LES SOUS-CHAMPS COMPTENT AUTANT QUE LES CHAMPS. `rawContract` peut
    exister et ne pas contenir `decimal` ; c'est pourtant lui qui porte
    les décimales, et sans elles le tri refuse tout.
    */
    const sousChamps = premier
      ? {
          'rawContract.address': premier?.rawContract?.address ?? '(absent)',
          'rawContract.value': premier?.rawContract?.value ? '(present)' : '(ABSENT)',
          'rawContract.decimal': premier?.rawContract?.decimal ?? '(ABSENT)',
          'metadata.blockTimestamp': premier?.metadata?.blockTimestamp ?? '(ABSENT)',
        }
      : {}
    const decimalesLues = premier ? Number.parseInt(String(premier?.rawContract?.decimal ?? ''), 16) : null

    return json({
      ok: true,
      version: VERSION,
      reseau,
      monnaie,
      noeud: hoteMasque(noeud),
      contrat: cfg.contrat,
      adresse_du_changeur: adresse,
      transferts_lus: liste.length,
      champs_du_premier: premier ? Object.keys(premier) : [],
      champs_manquants: manquants,
      sous_champs: sousChamps,
      decimales_lues: decimalesLues,
      decimales_attendues: cfg.decimales,
      conclusion: !premier
        ? 'Le noeud sert bien la methode, mais cette adresse n a encore recu aucun ' +
          'versement de ce jeton. La forme des champs reste non verifiee — refais ' +
          'cette sonde apres le premier envoi.'
        : manquants.length === 0 && decimalesLues === cfg.decimales
          ? 'Tout y est, et les decimales lues valent celles attendues. ' +
            'La verification des ventes BNB peut marcher.'
          : `ATTENTION : ${manquants.length ? 'champs absents (' + manquants.join(', ') + '). ' : ''}` +
            `${decimalesLues !== cfg.decimales ? `decimales lues ${decimalesLues} au lieu de ${cfg.decimales}. ` : ''}` +
            'Le tri rendrait « rien trouve » sur TOUT versement.',
    })
  } catch (e) {
    return json({
      ok: false,
      version: VERSION,
      reseau,
      noeud: hoteMasque(noeud),
      erreur: String(e?.message ?? e).slice(0, 160),
      remede:
        'Si le motif parle de methode inconnue, ce noeud ne sert pas les transferts ' +
        'enrichis sur ce reseau, et la verification retombera sur eth_getLogs.',
    })
  }
}

/** Les champs dont `choisirTransfertEtherscan` a besoin pour trier. */
const CHAMPS_ETHERSCAN = ['hash', 'to', 'from', 'contractAddress', 'tokenDecimal', 'value', 'timeStamp']

/**
 * Vérifie qu'Etherscan répond au Worker, et que ses champs portent les
 * noms que le tri attend.
 */
async function diagnostiquerEtherscan(env, params) {
  const reseau = (params?.get('reseau') || 'bnb').toLowerCase()
  const cle = env?.CHANGE_CLE_ETHERSCAN ? String(env.CHANGE_CLE_ETHERSCAN) : ''
  if (!cle) {
    return json({
      ok: false,
      version: VERSION,
      erreur: 'CHANGE_CLE_ETHERSCAN n’est pas reglee',
      remede:
        'Cree une cle gratuite sur etherscan.io/apis, puis ajoute-la dans ' +
        'Cloudflare → Settings → Variables and Secrets, sous le nom CHANGE_CLE_ETHERSCAN.',
    }, 400)
  }

  /*
  ON RETIENT AUSSI LE NOM DE LA MONNAIE, pas seulement sa configuration.
  C'est ce nom — « USDT-BNB » — qui sert de clé dans CHANGE_ADRESSES, et
  sans lui la sonde tomberait sur l'adresse d'exemple en croyant sonder
  celle du changeur.
  */
  const entree = Object.entries(CHAINES_VENTE).find(
    ([, c]) => c.reseau === reseau && c.contrat && !c.natif
  )
  if (!entree) return json({ ok: false, erreur: `aucun jeton regle pour ${reseau}` }, 400)
  const [monnaie, cfg] = entree

  const adresse = trierAdresses(env?.CHANGE_ADRESSES).bonnes[monnaie] || ''
  /*
  ON SONDE SUR L'ADRESSE DU CHANGEUR quand elle est réglée. Ça teste la
  chaîne complète d'un coup : l'hôte répond, la clé passe, ET l'adresse
  réglée est lisible. Une adresse mal collée — un caractère de trop au
  copier-coller — ne se voit nulle part ailleurs avant une vraie vente.
  */
  const cible = adresse || '0x28c6c06298d514db089934071355e5743bf21d60'

  try {
    const liste = await listerEtherscan(cfg, cible, cle)
    const premier = liste[0] || null
    const manquants = premier ? CHAMPS_ETHERSCAN.filter((c) => !(c in premier)) : []
    return json({
      ok: true,
      version: VERSION,
      reseau,
      monnaie,
      chaine_etherscan: CHAINE_ETHERSCAN[reseau],
      contrat: cfg.contrat,
      // L'adresse sondée, et SI c'est bien celle du changeur. Sonder une
      // adresse d'exemple prouverait que l'hôte répond, et rien de plus.
      adresse_sondee: cible,
      adresse_du_changeur: Boolean(adresse),
      transferts_lus: liste.length,
      champs_du_premier: premier ? Object.keys(premier) : [],
      champs_manquants: manquants,
      conclusion: !premier
        ? 'Etherscan repond, mais cette adresse n’a aucun transfert de ce jeton. ' +
          'La cle et l’hote sont bons ; la forme des champs reste non verifiee.'
        : manquants.length === 0
          ? 'Etherscan repond et tous les champs attendus sont la. La verification des ventes BNB/ETH peut marcher.'
          : `ATTENTION : champs absents (${manquants.join(', ')}). Le tri rendrait « rien trouve » sur TOUT versement.`,
    })
  } catch (e) {
    /*
    LE MOTIF EST RENDU TEL QUEL, mais jamais l'URL — elle porte la clé.
    `listerEtherscan` ne met que le message d'Etherscan dans son erreur,
    et c'est précisément pour pouvoir l'afficher ici.
    */
    const motif = String(e?.message ?? e)
    /*
    LE REMÈDE DÉPEND DU MOTIF, et depuis qu'on a mesuré le refus sur BNB
    il n'y a plus à deviner. Un conseil générique obligeait à relire la
    phrase d'Etherscan pour savoir quoi faire ; celui-ci la lit.
    */
    const horsOffre = chaineHorsOffre(motif)
    return json({
      ok: false,
      version: VERSION,
      reseau,
      erreur: motif.slice(0, 160),
      hors_offre: horsOffre,
      remede: horsOffre
        ? `Ta cle est BONNE et l’hote repond : le palier gratuit d’Etherscan ne couvre ` +
          `simplement pas ${reseau}. La verification passera par eth_getLogs, qui a besoin ` +
          `d’un noeud nominatif : regle CHANGE_NOEUD_${String(reseau).toUpperCase()} dans ` +
          `Cloudflare, puis mesure-le avec /diag/plages?reseau=${reseau}.`
        : 'Si le motif parle de cle invalide, verifie CHANGE_CLE_ETHERSCAN dans Cloudflare. ' +
          'S’il parle de reseau ou de 403, c’est Etherscan qui refuse les IP Cloudflare ' +
          'et il faudra une autre source.',
    })
  }
}

/**
 * Un appel JSON-RPC à UN SEUL nœud, dont l'échec est une valeur.
 *
 * `appelEvm` bascule sur le nœud suivant et ne garde que la dernière
 * erreur : c'est ce qu'il faut pour vérifier un versement, et c'est
 * exactement ce qu'il ne faut pas pour mesurer. Ici on veut la réponse de
 * CE nœud-là, refus compris, en clair.
 */
async function rpcBrut(hote, methode, params) {
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
    const texte = await r.text()
    let o = null
    try { o = JSON.parse(texte) } catch (_) {
      return { erreur: `HTTP ${r.status}, reponse non-JSON : ${texte.slice(0, 60)}` }
    }
    if (o?.error) return { erreur: String(o.error?.message ?? 'rpc').slice(0, 90) }
    if (!r.ok) return { erreur: `HTTP ${r.status}` }
    return { resultat: o?.result ?? null }
  } catch (e) {
    return { erreur: String(e?.message ?? e).slice(0, 90) }
  }
}

/*
═══════════════════════════════════════════════════════════════════════════
UN NŒUD À NOUS, DEVANT LES PUBLICS
═══════════════════════════════════════════════════════════════════════════

MESURÉ : les sept nœuds publics refusent `eth_getLogs` sur un seul bloc
depuis un Worker, et le palier gratuit d'Etherscan V2 ne couvre que
Ethereum — BNB répond « Free API access is not supported for this chain ».

Il reste donc une seule voie : un point d'entrée nominatif, dont le quota
est le nôtre et non celui de toutes les IP de Cloudflare réunies. Les
offres gratuites de ce genre existent et suffisent largement à notre
débit — un appel toutes les quinze secondes pendant une vente.

CE N'EST PAS UN RÉGLAGE D'EXPERT, c'est la condition pour que les ventes
BNB se vérifient. Sans lui, `/change/verifier` répond `indisponible` —
honnête, mais ne vend rien.

─── LES PUBLICS RESTENT, DERRIÈRE ────────────────────────────────────────

Le nœud nominatif passe DEVANT, il n'efface pas les autres. Un quota
épuisé, une panne, une clé révoquée : `appelEvm` descend alors la liste et
`eth_blockNumber` — qui passe sur cinq nœuds publics sur sept — continue
de répondre. Remplacer la liste au lieu de la préfixer échangerait sept
secours contre un point de rupture unique.

─── LA VALEUR CONTIENT UNE CLÉ ───────────────────────────────────────────

Ces URL portent l'identifiant dans leur chemin. Elle ne doit donc JAMAIS
être rendue en entier par un diagnostic : `hoteMasque` existe pour ça, et
tout ce qui imprime un nœud doit passer par elle.
═══════════════════════════════════════════════════════════════════════════
*/
function noeudsEvm(reseau, env) {
  const brut = String(env?.[`CHANGE_NOEUD_${String(reseau).toUpperCase()}`] ?? '')
  const perso = brut
    .split(',')
    .map((s) => s.trim())
    .filter((s) => s.startsWith('https://'))
  return [...perso, ...(NOEUDS_EVM[reseau] || [])]
}

/**
 * Le nom d'un nœud, sans ce qui l'identifie.
 *
 * `https://bnb.exemple.com/v2/abcdef123` devient `bnb.exemple.com/v2/***`.
 * Un diagnostic doit pouvoir dire QUEL nœud a répondu sans publier la clé
 * qui permet de s'en servir.
 */
function hoteMasque(url) {
  const sansSchema = String(url).replace(/^https?:\/\//, '')
  const bouts = sansSchema.split('/')
  if (bouts.length <= 1) return sansSchema
  /*
  ON MASQUE LE DERNIER SEGMENT, et c'est là que vit la clé chez tous les
  fournisseurs que j'ai vus. Masquer tout le chemin dirait seulement
  « un nœud », et on ne saurait plus lequel a échoué.
  */
  return bouts.slice(0, -1).join('/') + '/***'
}

/*
LA LISTE EST PASSÉE, PAS RELUE DANS `env`.

`appelEvm` est appelé au fond de la vérification, et lui donner `env`
ferait descendre tous les secrets du Worker — clé Telegram comprise — dans
une fonction qui n'a besoin que de sept URL. La liste est donc calculée
une fois, en haut, et passée.
*/
async function appelEvm(reseau, methode, params, noeudsDonnes) {
  const noeuds = Array.isArray(noeudsDonnes) && noeudsDonnes.length
    ? noeudsDonnes
    : NOEUDS_EVM[reseau] || []
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
  /*
  LE MOTIF NOMME LA METHODE.

  « indisponible : limit exceeded » ne disait pas quel appel avait echoue —
  et c'est precisement ce qu'il fallait savoir : la plage de eth_getLogs,
  pas la lecture de la hauteur. Trois mots de plus, et la cause saute aux
  yeux au lieu de demander une heure.
  */
  throw new Error(`${methode} : ${derniere?.message ?? 'aucun noeud'}`)
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
    exact: critere.exact,
    apres: critere.apres,
    fenetre: critere.fenetre,
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
    const tx = await appelEvm(cfg.reseau, 'eth_getTransactionByHash', [critere.txid], critere.noeuds)
    if (!tx) return null
    const recu = await appelEvm(cfg.reseau, 'eth_getTransactionReceipt', [critere.txid], critere.noeuds)
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
  ═══════════════════════════════════════════════════════════════════════
  ETHERSCAN D'ABORD, QUAND LA CLÉ EST RÉGLÉE
  ═══════════════════════════════════════════════════════════════════════

  C'est devenu le chemin NORMAL, et `eth_getLogs` en dessous n'est plus
  qu'un reste. La mesure est sans appel : les sept nœuds publics refusent
  `eth_getLogs` sur UN SEUL bloc depuis un Worker. Le code qui suit ne
  trouvera donc rien en production tant que ces nœuds restent les mêmes.

  On le garde quand même, pour deux raisons. Il ne coûte rien quand la clé
  est réglée — il n'est jamais atteint. Et il reste le seul chemin si
  Etherscan tombe ou change ses conditions : une source unique pour toute
  la vérification des ventes serait un point de rupture unique.
  ═══════════════════════════════════════════════════════════════════════
  */
  if (critere.cleEtherscan && CHAINE_ETHERSCAN[cfg.reseau]) {
    /*
    ═════════════════════════════════════════════════════════════════════
    UN REFUS D'ABONNEMENT N'EST PAS UNE PANNE : ON PASSE AU SUIVANT
    ═════════════════════════════════════════════════════════════════════

    MESURÉ sur BNB avec une clé valide :

        Free API access is not supported for this chain.
        Please upgrade your api plan for full chain support.

    Le palier gratuit d'Etherscan V2 ne couvre qu'Ethereum. La clé est
    bonne, l'hôte répond, et cette chaîne-là nous est simplement fermée.

    Rendre ça comme une erreur ferait répondre `indisponible` alors qu'un
    autre chemin existe juste en dessous. Et ce serait un défaut
    PERMANENT : il ne s'arrangerait jamais tout seul, contrairement à un
    nœud qui tousse.

    On distingue donc deux refus :

    — « cette chaîne n'est pas dans ton offre » ⇒ on continue vers
      `eth_getLogs`, qui marchera si un nœud nominatif est réglé ;
    — tout le reste (clé invalide, hôte muet) ⇒ on lève, parce que là il
      n'y a rien à tenter de plus et que le motif doit remonter.

    La même clé reste utile : sur `eth` (chainid=1) le gratuit passe, et
    c'est le même code qui s'en sert.
    ═════════════════════════════════════════════════════════════════════
    */
    let liste = null
    try {
      liste = await listerEtherscan(cfg, critere.adresse, critere.cleEtherscan)
    } catch (e) {
      if (!chaineHorsOffre(e?.message)) throw e
      liste = null
    }
    if (liste) {
      return choisirTransfertEtherscan(liste, {
        adresse: critere.adresse,
        contrat: cfg.contrat,
        decimales: cfg.decimales,
        montant: critere.montant,
        maintenant: critere.maintenant,
        fenetre: critere.fenetre,
        apres: critere.apres,
        exact: critere.exact,
      })
    }
  }

  /*
  ═══════════════════════════════════════════════════════════════════════
  DEUXIÈME SOURCE : LES TRANSFERTS ENRICHIS D'UN NŒUD ALCHEMY
  ═══════════════════════════════════════════════════════════════════════

  C'est le chemin qui marche sur BNB aujourd'hui. Il vient APRÈS
  Etherscan parce qu'Etherscan ne coûte rien quand il répond — mais sur
  BNB il ne répond pas, et c'est donc ici qu'on arrive.

  On n'essaie que sur un nœud qui sert cette méthode : l'envoyer à un
  nœud public ferait perdre un aller-retour pour une erreur certaine.
  ═══════════════════════════════════════════════════════════════════════
  */
  const noeudEnrichi = (critere.noeuds || []).find(estAlchemy)
  if (noeudEnrichi) {
    const liste = await listerAlchemy(noeudEnrichi, cfg, critere.adresse)
    return choisirTransfertAlchemy(liste, {
      adresse: critere.adresse,
      contrat: cfg.contrat,
      decimales: cfg.decimales,
      montant: critere.montant,
      maintenant: critere.maintenant,
      fenetre: critere.fenetre,
      apres: critere.apres,
      exact: critere.exact,
    })
  }

  /*
  ═══════════════════════════════════════════════════════════════════════
  LE CHEMIN DE SECOURS : `eth_getLogs`, DÉCOUPÉ
  ═══════════════════════════════════════════════════════════════════════

  Il est écrit pour les nœuds qui plafonnent la PLAGE — ce que je croyais
  être notre cas. La mesure a montré autre chose : ils refusent la méthode
  entière, pas une plage trop large. Le découpage ci-dessous ne sauve donc
  rien sur les nœuds publics d'aujourd'hui.

  Il garde sa valeur pour un nœud privé ou payant, où la limite de plage
  est réelle et où découper est exactement ce qu'il faut faire.

  ─── LE TEMPS DE BLOC EST SOUS-ESTIMÉ, ET C'EST VOULU ────────────────

  BNB Chain est passée de 3 s à 1,5 s puis à 0,75 s en deux ans. Prendre
  une valeur trop GRANDE ferait calculer trop peu de blocs, donc rater des
  versements. On prend donc une valeur basse : on interroge quelques blocs
  de trop, ce qui ne coûte rien, plutôt que pas assez, ce qui coûte un
  versement introuvable.
  ═══════════════════════════════════════════════════════════════════════
  */
  const hauteur = await appelEvm(cfg.reseau, 'eth_blockNumber', [], critere.noeuds)
  const dernier = Number(BigInt(entierDepuisHex(hauteur)))
  if (!Number.isFinite(dernier) || dernier <= 0) throw new Error('hauteur illisible')

  const msParBloc = MS_PAR_BLOC[cfg.reseau] || 1000
  // Depuis la naissance de la demande, plus cinq minutes de marge. Sans
  // naissance connue, le quart d'heure écoulé : au-delà, il faut un txid.
  const age = critere.apres > 0
    ? critere.maintenant - critere.apres + 5 * 60_000
    : 15 * 60_000
  const blocs = Math.min(
    Math.ceil(age / msParBloc),
    Math.ceil(PLAGE_EVM_MAX_MS / msParBloc)
  )

  const journaux = []
  let haut = dernier
  const bas = Math.max(0, dernier - blocs)
  /*
  DU PLUS RÉCENT AU PLUS ANCIEN, et on s'arrête dès qu'on a trouvé.

  Un versement qu'on attend vient d'arriver : il est dans la première
  tranche neuf fois sur dix. Commencer par les blocs anciens ferait payer
  à tout le monde le coût du cas rare.
  */
  /*
  UN PLAFOND D'APPELS, PARCE QUE LA TRANCHE PEUT ÊTRE MINUSCULE.

  Mesuré : le palier gratuit d'Alchemy limite `eth_getLogs` à DIX blocs.
  Couvrir vingt minutes à dix blocs ferait cent soixante-dix allers-
  retours pour une seule vérification — le Worker y passerait son temps,
  le quota y passerait tout court, et le client attendrait pour rien.

  On s'arrête donc à vingt appels et on rend ce qu'on a. Mieux vaut
  chercher sur une fenêtre plus courte que ne jamais répondre : le txid
  reste disponible pour les cas que ça ne couvre pas, et il ne connaît
  aucune limite de plage.
  */
  const MAX_APPELS_LOGS = 20
  let appels = 0
  while (haut > bas && journaux.length === 0 && appels < MAX_APPELS_LOGS) {
    appels += 1
    const debut = Math.max(bas, haut - (critere.tranche || TRANCHE_EVM))
    const tranche = await appelEvm(cfg.reseau, 'eth_getLogs', [
      {
        address: cfg.contrat,
        topics: [TOPIC_TRANSFER, null, topicDepuisAdresse(critere.adresse)],
        fromBlock: '0x' + debut.toString(16),
        toBlock: '0x' + haut.toString(16),
      },
    ], critere.noeuds)
    if (!Array.isArray(tranche)) throw new Error('journaux illisibles')
    journaux.push(...tranche)
    haut = debut - 1
  }

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
    const fenetre = critere.fenetre || FENETRE_VENTE_MS
    if (Number.isFinite(quand) && quand > 0 && critere.maintenant - quand > fenetre) {
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
/*
───────────────────────────────────────────────────────────────────────────
TROIS PRIMITIVES DE MÉMOIRE, ET CE QU'ELLES VALENT
───────────────────────────────────────────────────────────────────────────

`caches.default` est une mémoire PAR CENTRE DE DONNÉES, et évictable. Un
Worker est servi depuis le point le plus proche de l'appelant : ce qu'on y
écrit à Ouagadougou n'existe pas à Paris, et peut disparaître à tout
moment.

Ça rend ces primitives adaptées à ce qui se joue sur des MINUTES — un
renvoi après coupure réseau, une rafale de demandes — et inadaptées à ce
qui s'accumule sur des SEMAINES, comme une réputation.

ON NE CONFOND DONC PAS LES DEUX. Les marquages de référence et de txid, la
limite de débit : minutes ou heures, le cache suffit. Un plafond
progressif qui récompenserait dix opérations honnêtes : non, et il n'est
pas écrit ici pour cette raison. Voir `plafondApplicable`.

CHAQUE PRIMITIVE ÉCHOUE DANS LE SENS SÛR. Cache muet → `dejaVu` rend faux
(on laisse passer plutôt que de bloquer quelqu'un d'honnête) et `compteur`
rend zéro. Les protections se relâchent, elles ne se retournent pas contre
l'utilisateur.
───────────────────────────────────────────────────────────────────────────
*/

/** Vrai si cette marque a déjà été posée. */
async function dejaVu(espace, valeur) {
  try {
    const cle = new Request(
      `https://relais.vaultex/${espace}/${encodeURIComponent(valeur)}`
    )
    return Boolean(await caches.default.match(cle))
  } catch (_) {
    return false
  }
}

/** Pose une marque, pour [secondes]. */
async function marquer(espace, valeur, secondes) {
  try {
    const cle = new Request(
      `https://relais.vaultex/${espace}/${encodeURIComponent(valeur)}`
    )
    await caches.default.put(
      cle,
      new Response('1', { headers: { 'cache-control': `max-age=${secondes}` } })
    )
  } catch (_) { /* sans effet sur l'ordre en cours */ }
}

/** Lit un compteur. */
async function compteur(espace, valeur) {
  try {
    const cle = new Request(
      `https://relais.vaultex/${espace}/${encodeURIComponent(valeur)}`
    )
    const r = await caches.default.match(cle)
    if (!r) return 0
    const n = Number(await r.text())
    return Number.isFinite(n) && n >= 0 ? n : 0
  } catch (_) {
    return 0
  }
}

/**
 * Incrémente un compteur.
 *
 * LIRE PUIS ÉCRIRE N'EST PAS ATOMIQUE : deux demandes simultanées peuvent
 * lire la même valeur et l'écrire une fois. Pour une limite de débit
 * destinée à empêcher d'inonder un canal Telegram, perdre un coup sur
 * deux dans une rafale ne change rien — la rafale reste bornée. Un
 * décompte exact demanderait un Durable Object, c'est-à-dire un autre
 * modèle de déploiement pour un gain nul ici.
 */
async function incrementer(espace, valeur, secondes) {
  const n = (await compteur(espace, valeur)) + 1
  try {
    const cle = new Request(
      `https://relais.vaultex/${espace}/${encodeURIComponent(valeur)}`
    )
    await caches.default.put(
      cle,
      new Response(String(n), { headers: { 'cache-control': `max-age=${secondes}` } })
    )
  } catch (_) { /* sans effet */ }
  return n
}

async function txidDejaServi(txid) {
  return await dejaVu('txid', txid)
}

async function marquerTxidServi(txid) {
  await marquer('txid', txid, 86400)
}

/**
 * Un numéro ramené à sa forme comparable : ses huit derniers chiffres.
 *
 * ON NE PEUT PAS COMPARER DES NUMÉROS TELS QUE LES GENS LES ÉCRIVENT.
 * « 70 12 34 56 », « 70123456 » et « +226 70 12 34 56 » sont le même
 * abonné ; comparés à l'identique, ils seraient trois personnes — et une
 * liste noire, comme une limite de débit, se contournerait en ajoutant
 * une espace.
 *
 * Huit chiffres parce que c'est la longueur d'un numéro au Burkina Faso,
 * et qu'on écarte ainsi l'indicatif 226 qui est parfois là, parfois pas.
 */
function numeroComparable(brut) {
  const chiffres = String(brut ?? '').replace(/\D/g, '')
  return chiffres.length >= 8 ? chiffres.slice(-8) : chiffres
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
  const txidBrut = String(options?.txid ?? '').trim()
  const env = options?.env

  const cfg = CHAINES_VENTE[monnaie]
  if (!cfg) return { etat: 'inconnue', raison: `${monnaie} n'est pas verifiable ici` }

  /*
  ═══════════════════════════════════════════════════════════════════════
  UN IDENTIFIANT MAL FORMÉ NE DOIT PAS ATTEINDRE LA CHAÎNE
  ═══════════════════════════════════════════════════════════════════════

  MESURÉ en production, dans un vrai message au changeur :

      eth_getTransactionByHash : invalid argument 0:
      hex string has length 40, want 64

  Quarante caractères, c'est une ADRESSE. Soixante-quatre, c'est une
  transaction. Quelqu'un a collé l'adresse du changeur dans le champ
  prévu pour le hash — ce qui se comprend : les deux commencent par
  « 0x » et se ressemblent.

  ─── LE COÛT N'EST PAS CELUI QU'ON CROIT ────────────────────────────

  Le nœud a refusé l'appel, et toute la vérification a répondu
  `indisponible`. Or le versement était peut-être là, trouvable par son
  montant marqué : ce champ FACULTATIF a donc empêché le chemin
  obligatoire de s'exécuter. Un raccourci optionnel qui casse la route
  principale est pire que l'absence de raccourci.

  ─── ON L'IGNORE, ET ON LE DIT ──────────────────────────────────────

  Refuser la demande punirait quelqu'un pour un champ dont il pouvait se
  passer. L'ignorer en silence laisserait croire qu'il a servi. On
  l'écarte donc, on cherche par le montant marqué — qui prouve autant —
  et le changeur lit dans son message que l'identifiant fourni n'en
  était pas un.

  La forme est vérifiée PAR CHAÎNE : un hash EVM porte « 0x » et sa
  longueur n'est pas celle d'un hash TRON. Accepter l'un pour l'autre
  rendrait la même erreur une ligne plus loin.
  ═══════════════════════════════════════════════════════════════════════
  */
  const txid = txidValide(txidBrut, cfg.chaine) ? txidBrut : ''
  const txidRejete = txidBrut.length > 0 && txid.length === 0
  if (!(montant > 0)) return { etat: 'indisponible', raison: 'montant illisible' }

  /*
  ═══════════════════════════════════════════════════════════════════════
  LE MONTANT CHERCHÉ EST CALCULÉ ICI, PAS REÇU
  ═══════════════════════════════════════════════════════════════════════

  Quand le marquage est en service et qu'une référence est fournie, le
  relais dérive lui-même le montant exact. Celui que l'appelant a envoyé
  ne sert plus que de socle — partie entière et deux décimales.

  Sans ça, le marquage ne protégerait de rien : il suffirait d'annoncer
  directement le montant qu'on convoite.
  ═══════════════════════════════════════════════════════════════════════
  */
  const reference = String(options?.reference ?? '').trim()
  const marque = marquageActif(env) && reference.length > 0
  const cherche = marque ? await montantMarque(montant, reference, env) : montant

  // L'instant où la demande est née, pour exiger un versement postérieur.
  const naissance = marque ? Number((await lireValeur('demande', reference))?.ne) || 0 : 0

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
    montant: cherche,
    txid,
    exact: marque,
    /*
    LE VERSEMENT DOIT ÊTRE POSTÉRIEUR À LA DEMANDE.

    Deuxième barrière, et elle complète la première : le marquage empêche
    de deviner le montant, celle-ci empêche de déposer une demande APRÈS
    avoir vu passer un versement.

    Prises ensemble, elles obligent un attaquant à ouvrir sa demande avant
    le versement qu'il convoite, ET à tomber sur le bon marquage parmi dix
    mille. Prises séparément, chacune se contourne.

    Zéro quand on ne sait pas : la mémoire est évictable, et refuser un
    versement parce qu'on a oublié quand la demande est née punirait
    l'honnête.
    */
    apres: naissance,
    fenetre: fenetreVente(env),
    maintenant: Date.now(),
    cleTron: env?.TRONGRID_KEY ? String(env.TRONGRID_KEY) : '',
    /*
    SANS CETTE CLÉ, LES VENTES BNB ET ETH NE SE VÉRIFIENT PAS.

    Ce n'est pas une option de confort comme `TRONGRID_KEY`, qui ne relève
    qu'un quota. Mesuré depuis le Worker : les sept nœuds BSC publics
    refusent `eth_getLogs` sur un seul bloc. Non réglée, une vente BNB
    répondra `indisponible` — ce qui est au moins honnête, mais ne vend rien.
    */
    cleEtherscan: env?.CHANGE_CLE_ETHERSCAN ? String(env.CHANGE_CLE_ETHERSCAN) : '',
    // La liste des nœuds, calculée UNE FOIS ici : un nœud nominatif
    // devant, les publics derrière en secours.
    noeuds: noeudsEvm(cfg.reseau || '', env),
    /*
    LA TRANCHE EST RÉGLABLE, parce que sa bonne valeur est MESURÉE et
    change avec le fournisseur. Alchemy gratuit : 10. Un nœud payant :
    des milliers. L'écrire en dur obligerait à redéployer le code pour
    un chiffre que `/diag/plages` imprime déjà.
    */
    tranche: Math.max(1, Number(env?.CHANGE_TRANCHE_EVM) || TRANCHE_EVM),
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

  /*
  L'IDENTIFIANT ÉCARTÉ SE DIT DANS LES DEUX SENS.

  Trouvé ou non, le changeur doit savoir qu'un champ qu'il croit rempli
  n'a pas servi. Le taire sur un succès serait le pire des deux : il
  conclurait que le hash a confirmé le versement, alors que c'est le
  montant marqué qui l'a fait.
  */
  const noteTxid = txidRejete
    ? " (l'identifiant fourni n'est pas un hash de transaction : il a ete ignore, " +
      'la recherche a porte sur le montant marque)'
    : ''

  if (!trouve) {
    return {
      etat: 'absent',
      raison:
        (txid
          ? "ce txid ne correspond a aucun versement attendu"
          : marque
            ? `aucun versement de ${cherche} ${monnaie} depuis l'ouverture ` +
              `de cette demande`
            : `aucun versement correspondant sur les ` +
              `${Math.round(critere.fenetre / 3600000)} dernieres heures`) + noteTxid,
    }
  }

  if (trouve.txid && (await txidDejaServi(trouve.txid))) {
    return { etat: 'deja_servi', txid: trouve.txid, raison: 'ce transfert a deja servi' }
  }

  return {
    etat: 'confirme',
    // Vide quand rien n'a ete ecarte : le message n'affiche alors rien.
    noteTxid,
    txid: trouve.txid || '',
    montant: trouve.montant,
    quand: trouve.quand || 0,
    // Bitcoin seul peut rendre un transfert vu mais pas encore miné.
    confirme: trouve.confirme !== false,
    /*
    ═══════════════════════════════════════════════════════════════════════
    L'EXPÉDITEUR, QUE JE JETAIS
    ═══════════════════════════════════════════════════════════════════════

    Les trois lecteurs de chaîne le lisaient déjà ; `verifierVente` ne le
    rendait pas. C'était un oubli, et il ouvrait un cas précis :

      Alice envoie 8 USDT à l'adresse du changeur, honnêtement, et ne
      dépose pas tout de suite sa demande. Bob dépose une demande pour
      8 USDT avec SON numéro de téléphone. Le relais cherche un versement
      de 8 USDT, trouve celui d'Alice, répond « confirmé ». Bob est payé,
      Alice ne l'est pas.

    Rendre l'expéditeur ne ferme pas ce cas — il le rend VISIBLE. Le
    changeur lit « De : TAlice… » et peut trancher ; et si deux demandes
    citent le même hash, la seconde est déjà refusée par le marquage.

    CE QUI LE FERMERAIT VRAIMENT : faire signer la référence de la demande
    par la clé de l'adresse qui a envoyé, et vérifier la signature ici
    contre ce `de`. Bob ne peut pas signer avec la clé d'Alice. Ça demande
    la vérification secp256k1 dans le Worker, donc une dépendance et un
    déploiement par wrangler — c'est le pas suivant, pas celui-ci.
    ═══════════════════════════════════════════════════════════════════════
    */
    de: trouve.de || '',
    // Rendu pour que l'écran puisse dire « 2,004731 attendus » quand il
    // affiche une recherche en cours ou un échec.
    attendu: cherche,
    marque,
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
  verifierVente,
  marquerValeur,
  lireValeur,
  montantMarque,
  marquageActif,
  memeMontant,
  fenetreVente,
  minimumsChange,
  minimumPourMonnaie,
  trierAdresses,
  txidValide,
  formeAttendue,
  lireSmsPaiement,
  sansAccents,
  recevoirSms,
  verifierAchat,
  numeroComparable,
  dejaVu,
  marquer,
  compteur,
  incrementer,
  ordreChange,
  choisirTransfertTron,
  choisirTransfertEtherscan,
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
  FENETRE_VENTE_MS,
  TOPIC_TRANSFER,
}

/*
═══════════════════════════════════════════════════════════════════════════
LES ACHATS — LIRE LE SMS DE L'OPÉRATEUR
═══════════════════════════════════════════════════════════════════════════

Une vente se vérifie sur une chaîne publique. Un achat, non : le client
paie par Orange Money, et aucune chaîne ne porte ça. C'était le seul vrai
trou du dispositif, et c'est par là que la fraude serait venue — un faux
reçu, une référence recopiée, et le changeur envoie de la crypto
irréversible contre un paiement qui n'a jamais eu lieu.

Or l'opérateur ENVOIE UN SMS au changeur à chaque paiement reçu. C'est une
affirmation d'Orange, pas du client. Il suffit de la lire.

─── QUI DIT QUOI, ET DANS QUEL SENS ─────────────────────────────────────

  Le téléphone du changeur    reçoit le SMS, le transmet tel quel.
  Le relais                   l'analyse, le garde, et le rapproche
                              de la demande du client.
  Le client                   ne touche à rien de tout ça.

C'est l'inverse exact du dispositif qu'on remplace, où la preuve venait de
celui qui avait intérêt à mentir.

─── LE TÉLÉPHONE TRANSMET LE TEXTE BRUT, ET C'EST DÉLIBÉRÉ ──────────────

Il serait plus élégant qu'il analyse lui-même et n'envoie que trois
champs. Ce serait une faute pratique : le format d'un SMS d'opérateur
change sans préavis, et le téléphone du changeur est la chose la plus
difficile à mettre à jour de tout le dispositif. Analyser ici, c'est
corriger une ligne dans le Worker au lieu de réinstaller un APK sur le
téléphone de quelqu'un d'autre.

─── CE QUE CE MÉCANISME NE PROUVE PAS ───────────────────────────────────

L'identifiant d'expéditeur d'un SMS se falsifie. Quelqu'un qui saurait
envoyer un SMS en se faisant passer pour « OrangeMoney » pourrait fabriquer
un faux encaissement.

C'est pourquoi la règle du changeur ne bouge pas, et reste écrite dans
chaque message : IL N'ENVOIE QU'APRÈS AVOIR VU L'ARGENT SUR SON PROPRE
COMPTE. Ce qui change, c'est qu'un faux reçu du client ne suffit plus, et
qu'un paiement réel est reconnu tout seul.

─── ÉTEINT PAR DÉFAUT ───────────────────────────────────────────────────

Sans CHANGE_SMS_TOKEN, rien de tout ceci n'existe : les achats repartent
« non vérifiables », exactement comme avant. Un changeur qui n'installe
pas le robot ne voit aucune différence.
═══════════════════════════════════════════════════════════════════════════
*/

/*
LES EXPÉDITEURS ACCEPTÉS.

CHANGE_SMS_EXPEDITEURS, séparés par des virgules, sinon cette liste. La
comparaison ignore la casse et les espaces.

C'EST UNE BARRIÈRE, PAS UNE PREUVE : un identifiant d'expéditeur se
falsifie. Elle sert à écarter le bruit — les SMS publicitaires, les
notifications de solde, et un SMS qu'un client enverrait lui-même au
changeur en imitant l'opérateur, qui est le cas le plus probable des trois.
*/
const EXPEDITEURS_SMS = ['orangemoney', 'orange money', 'moovmoney', 'moov money', 'wave']

/*
TROIS HEURES, comme pour les ventes.

Un paiement plus ancien ne peut pas justifier une demande déposée
maintenant. Et trois heures et non trente minutes : quelqu'un paie, puis
son téléphone n'a plus de réseau, puis il revient dans l'application.
*/
const FENETRE_PAIEMENT_MS = 3 * 60 * 60 * 1000

/**
 * Un texte sans accents ni ponctuation superflue, en minuscules.
 *
 * TOUT CE QUI SUIT COMPARE DES MOTS. « reçu », « RECU » et « Reçu » sont
 * le même mot, et un opérateur peut écrire les trois selon le canal.
 * Normaliser une fois évite d'écrire chaque motif en six variantes — et
 * un motif écrit en six variantes finit toujours par en oublier une.
 */
function sansAccents(texte) {
  return String(texte ?? '')
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
}

/*
LES MOTS QUI DISENT « REÇU », ET CEUX QUI DISENT L'INVERSE.

═══════════════════════════════════════════════════════════════════════════
LA FAUTE À NE PAS COMMETTRE ICI
═══════════════════════════════════════════════════════════════════════════

Un extracteur générique qui cherche « un montant et un numéro » lirait
aussi bien « Vous avez RECU 5000 FCFA de 70123456 » que « Vous avez ENVOYE
5000 FCFA a 70123456 ».

Conséquence : un paiement que le changeur a FAIT serait compté comme un
paiement qu'il a REÇU. Il enverrait de la crypto contre son propre
virement sortant. Et ça arriverait sans aucune fraude — simplement parce
qu'un changeur envoie des francs toute la journée, et que chaque envoi
produit un SMS.

On exige donc un mot de réception, ET l'absence de tout mot d'émission.
Les deux conditions, pas une.
═══════════════════════════════════════════════════════════════════════════
*/
const MOTS_RECEPTION = [
  'avez recu',
  'a recu',
  'vous recevez',
  'recu un transfert',
  'recu de',
  'credite de',
  'received',
]

/*
« ENVOYE » TOUT COURT, ET POURQUOI C'ÉTAIT NÉCESSAIRE.

Ma première version listait « avez envoye » et « envoye a ». Le test lui a
soumis « recu 10000 FCFA puis envoye 5000 FCFA a 70123456 » : aucun des
deux motifs ne correspond — il y a un montant entre « envoye » et « a » —
et le texte passait donc pour un encaissement de dix mille francs.

Aucun opérateur n'écrit cette phrase. Ce n'est pas le point : une liste de
motifs exacts se fait toujours contourner par une formulation qu'on
n'avait pas prévue, et ici le coût d'une formulation non prévue est que le
changeur envoie de la crypto contre son propre virement sortant.

On refuse donc sur « envoye » seul, ce qui refuse plus large que
nécessaire — et c'est le bon sens de l'erreur : un SMS refusé repart
« non vérifiable » et le changeur regarde son compte, comme avant.
*/
const MOTS_EMISSION = [
  'envoye',
  'transfere a',
  'avez transfere',
  'retrait',
  'debite',
  'paiement de',
  'achat de',
  'frais de',
  'sent to',
]

/**
 * Ce qu'on lit dans un SMS d'encaissement, ou null.
 *
 * FONCTION PURE : on lui donne le texte, elle rend des champs. C'est la
 * seule partie de ce mécanisme où une faute ne lève aucune erreur — elle
 * rend simplement « payé » quelque chose qui ne l'est pas — et la seule
 * qui se teste sans téléphone et sans réseau.
 *
 * Voir tools/relais-cours/test-sms.mjs, et surtout `node test-sms.mjs
 * "<un vrai SMS>"` pour vérifier le format réel plutôt que celui qu'on
 * suppose.
 */
function lireSmsPaiement(texte) {
  const t = sansAccents(texte)
  if (!t) return null

  if (!MOTS_RECEPTION.some((m) => t.includes(m))) return null
  /*
  « ENVOYÉ PAR » EST UNE FORMULATION DE RÉCEPTION, PAS D'ÉMISSION.

  « Vous avez reçu un transfert de 5000 FCFA envoyé par 70123456 » décrit
  un encaissement. Refuser sur le mot « envoye » seul l'écarterait, et on
  perdrait une formulation que les opérateurs emploient réellement.

  On neutralise donc cette locution avant de chercher les mots
  d'émission, au lieu d'ajouter une exception dans chacun d'eux.
  */
  const pourEmission = t.replace(/envoye par/g, 'de la part de')
  if (MOTS_EMISSION.some((m) => pourEmission.includes(m))) return null

  /*
  LE MONTANT : des chiffres suivis de l'unité, dans cet ordre.

  Les séparateurs de milliers sautent — « 5.000 », « 5 000 », « 5,000 »
  désignent cinq mille dans les SMS d'opérateurs d'Afrique de l'Ouest, où
  le point et la virgule servent tous deux de séparateur de milliers et
  non de décimale. Le franc CFA n'a pas de centimes : il n'y a donc aucune
  ambiguïté à lever, et traiter « 5.000 » comme cinq a déjà produit des
  rapprochements ratés ailleurs.
  */
  const montantTrouve = t.match(
    /(\d[\d .,]*)\s*(?:f\s*cfa|fcfa|xof|francs?|f\b)/
  )
  if (!montantTrouve) return null
  const montant = Number(montantTrouve[1].replace(/[^\d]/g, ''))
  if (!Number.isFinite(montant) || montant <= 0) return null

  /*
  LE NUMÉRO DE L'EXPÉDITEUR : huit chiffres, qui ne soient pas le montant.

  On cherche APRÈS le montant quand c'est possible — « recu 5000 FCFA de
  70123456 » — parce que c'est l'ordre des SMS d'Orange. À défaut, on
  prend le premier groupe de huit chiffres du texte.

  UN NUMÉRO ABSENT N'EST PAS BLOQUANT : certains SMS ne nomment que le
  titulaire, pas son numéro. Le rapprochement se fera alors sur la
  référence, ou sur le montant seul — et c'est dit au changeur.
  */
  /*
  LES NUMÉROS S'ÉCRIVENT « 70 12 34 56 », ET LE TEST ME L'A RAPPELÉ.

  Cherché tel quel, `\d{8}` ne trouvait rien dans « de 70 12 34 56 » : le
  numéro ressortait vide, et le rapprochement par montant et numéro ne
  pouvait jamais aboutir. Silencieusement — il restait celui par
  référence, donc ça « marchait » pour les clients qui en recopient une.

  On recolle donc les chiffres que séparent une espace, un point ou un
  tiret. Appliqué APRÈS la lecture du montant, sur ce qui reste du texte,
  pour ne pas transformer le montant lui-même en route.
  */
  const recolle = (x) => x.replace(/(\d)[ .\-](?=\d)/g, '$1')
  const apres = recolle(t.slice(montantTrouve.index + montantTrouve[0].length))
  const numeroTrouve =
    apres.match(/(?<!\d)(\d{8})(?!\d)/) || recolle(t).match(/(?<!\d)(\d{8})(?!\d)/)
  const numero = numeroTrouve ? numeroTrouve[1] : ''

  /*
  LA RÉFÉRENCE : ce qui suit « ref », quand il y en a une.

  C'est le rapprochement le plus net, parce que le client la recopie
  depuis SON propre SMS : les deux références doivent coïncider. Quand
  elle manque, on retombe sur montant + numéro.
  */
  const refTrouvee = t.match(/(?:ref(?:erence)?|id|transaction)\s*[:.=n°#]*\s*([a-z0-9.\-]{5,32})/)
  const reference = refTrouvee ? refTrouvee[1].replace(/[.\-]/g, '').toUpperCase() : ''

  return { montant, numero: numeroComparable(numero), reference }
}

/**
 * Reçoit un SMS d'encaissement du téléphone du changeur.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * CE POINT D'ENTRÉE EST LE PLUS DANGEREUX DE TOUT LE WORKER
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Tout le reste du relais ne fait que LIRE : des cours, une chaîne
 * publique. Celui-ci ÉCRIT une affirmation — « ce paiement est arrivé » —
 * sur laquelle le changeur enverra de la crypto.
 *
 * S'il était ouvert, il serait bien pire que l'absence de vérification :
 * n'importe qui déclarerait ses propres encaissements, et les achats
 * passeraient en ✅ VÉRIFIÉ. On aurait transformé une incertitude en
 * fausse certitude, ce qui est le plus sûr moyen de faire envoyer sans
 * regarder.
 *
 * Il exige donc un jeton, CHANGE_SMS_TOKEN. Et ce jeton :
 *
 *   — N'EST PAS CELUI DE TELEGRAM. Deux rôles, deux secrets : celui de
 *     Telegram ne peut qu'écrire un message, celui-ci peut faire payer.
 *   — NE DOIT JAMAIS ENTRER DANS L'APK PUBLIC DE VAULTEX. Il vit dans
 *     l'application compagnon, installée à la main sur un seul téléphone
 *     et jamais distribuée. C'est ce qui rend acceptable qu'un secret
 *     soit porté par un appareil.
 * ═══════════════════════════════════════════════════════════════════════
 */
async function recevoirSms(requete, env) {
  const jeton = String(env?.CHANGE_SMS_TOKEN ?? '')
  if (!jeton) {
    // Mécanisme non installé. On le dit clairement : un robot qui poste
    // dans le vide sans jamais recevoir d'erreur chercherait longtemps.
    return json({ ok: false, raison: 'lecture sms non configuree' }, 0, 503)
  }
  const presente = String(requete.headers.get('x-vaultex-sms') ?? '')
  if (presente !== jeton) {
    return json({ ok: false, raison: 'jeton refuse' }, 0, 401)
  }

  let corps = null
  try {
    corps = await requete.json()
  } catch (_) {
    return json({ ok: false, raison: 'corps illisible' }, 0, 400)
  }

  const expediteur = sansAccents(corps?.expediteur).replace(/\s+/g, ' ').trim()
  const texte = String(corps?.texte ?? '').slice(0, 1000)
  if (!texte) return json({ ok: false, raison: 'texte vide' }, 0, 400)

  const acceptes = String(env?.CHANGE_SMS_EXPEDITEURS ?? '')
    .split(',').map((x) => sansAccents(x).replace(/\s+/g, ' ').trim()).filter(Boolean)
  const liste = acceptes.length > 0 ? acceptes : EXPEDITEURS_SMS
  if (!liste.some((e) => expediteur.includes(e) || e.includes(expediteur))) {
    /*
    ON REFUSE SANS BRUIT UN EXPÉDITEUR INCONNU, et c'est le filtre le plus
    utile du lot : le robot transmet ce qu'il voit, et ce qu'il voit
    comprend les publicités, les codes de connexion et les messages des
    proches du changeur. Rien de tout ça n'a à entrer ici.
    */
    return json({ ok: false, raison: 'expediteur non reconnu', expediteur }, 0, 400)
  }

  const lu = lireSmsPaiement(texte)
  if (!lu) {
    /*
    FORMAT NON RECONNU : ON REND LE TEXTE.

    C'est le seul endroit où le texte d'un SMS ressort, et c'est voulu :
    le changeur a besoin de voir ce que le relais n'a pas su lire pour que
    le motif soit corrigé. C'est SON propre SMS, rendu à SON propre
    appareil, derrière le jeton.

    Sans ça, un format qui change se manifesterait par des achats qui
    cessent d'être vérifiés, sans rien pour dire pourquoi.
    */
    return json(
      { ok: false, raison: 'format non reconnu', texte: texte.slice(0, 200) },
      0,
      422
    )
  }

  /*
  DEUX CLÉS POUR LE MÊME PAIEMENT, parce qu'on ne sait pas encore sur quoi
  le rapprochement se fera.

  Par RÉFÉRENCE quand le client en recopie une : c'est le plus net, les
  deux côtés citent le même identifiant d'opérateur.

  Par MONTANT + NUMÉRO sinon : « 10000 reçus de 70123456 » suffit à
  reconnaître une demande de 10 000 francs déposée par le 70123456.
  */
  const valeur = JSON.stringify({ ...lu, quand: Date.now() })
  if (lu.reference) await marquerValeur('paiementref', lu.reference, valeur)
  if (lu.numero) await marquerValeur('paiement', `${lu.montant}-${lu.numero}`, valeur)
  if (!lu.reference && !lu.numero) {
    // Ni référence ni numéro : on ne saurait pas le retrouver. Le dire
    // plutôt que de l'avaler, pour que le motif soit corrigé.
    return json({ ok: false, raison: 'ni reference ni numero lisibles', lu }, 0, 422)
  }

  return json({ ok: true, lu })
}

/** Garde une valeur, pour la durée de la fenêtre de rapprochement. */
async function marquerValeur(espace, cle, valeur) {
  try {
    const requete = new Request(
      `https://relais.vaultex/${espace}/${encodeURIComponent(cle)}`
    )
    await caches.default.put(
      requete,
      new Response(valeur, {
        headers: { 'cache-control': `max-age=${Math.floor(FENETRE_PAIEMENT_MS / 1000)}` },
      })
    )
  } catch (_) { /* sans effet sur l'ordre en cours */ }
}

/** Relit une valeur gardée, ou null. */
async function lireValeur(espace, cle) {
  try {
    const requete = new Request(
      `https://relais.vaultex/${espace}/${encodeURIComponent(cle)}`
    )
    const r = await caches.default.match(requete)
    if (!r) return null
    return JSON.parse(await r.text())
  } catch (_) {
    return null
  }
}

/**
 * Le paiement Orange Money d'un achat a-t-il été vu ?
 *
 * ═══════════════════════════════════════════════════════════════════════
 * MÊME FORME QUE verifierVente, ET CE N'EST PAS UNE COQUETTERIE
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Elle rend les mêmes états — confirme / absent / deja_servi /
 * indisponible — parce que `lignesVerification` les met déjà en forme,
 * que l'application les lit déjà, et que la consigne « A FAIRE » en
 * dépend déjà.
 *
 * Deux vocabulaires pour deux sens auraient doublé chacun de ces
 * endroits, et c'est exactement là qu'on finit par traiter un cas d'un
 * côté en oubliant l'autre.
 * ═══════════════════════════════════════════════════════════════════════
 */
async function verifierAchat(ordre, env) {
  if (!String(env?.CHANGE_SMS_TOKEN ?? '')) {
    /*
    LE ROBOT N'EST PAS INSTALLÉ, et ce n'est pas une panne.

    « indisponible » et non « absent » : on n'a pas regardé. Rendre
    « absent » collerait un ❌ ACCUSATEUR sur tous les achats d'un
    changeur qui n'a simplement pas installé le robot — et le ferait
    douter de clients honnêtes.
    */
    return { etat: 'indisponible', raison: 'lecture des SMS non installee' }
  }

  const fcfa = Math.round(nombreFrancais(ordre.montantFcfa))
  const numero = numeroComparable(ordre.telephone)
  const refClient = String(ordre.referencePaiement ?? '')
    .replace(/[^a-zA-Z0-9]/g, '').toUpperCase()

  /*
  LA RÉFÉRENCE D'ABORD, LE MONTANT ENSUITE.

  La référence est le rapprochement le plus net : le client la recopie
  depuis son SMS, l'opérateur l'a écrite dans celui du changeur, et les
  deux doivent coïncider. Le montant et le numéro ne viennent qu'après,
  pour le client qui n'a rien recopié.
  */
  let paiement = refClient ? await lireValeur('paiementref', refClient) : null
  let par = 'reference'
  if (!paiement && numero.length === 8 && fcfa > 0) {
    paiement = await lireValeur('paiement', `${fcfa}-${numero}`)
    par = 'montant et numero'
  }

  if (!paiement) {
    return {
      etat: 'absent',
      raison: refClient
        ? `aucun SMS d'encaissement ne porte la reference ${refClient}`
        : `aucun encaissement de ${fcfa} FCFA depuis le ${numero || 'numero du client'}`,
    }
  }

  if (Date.now() - Number(paiement.quand || 0) > FENETRE_PAIEMENT_MS) {
    return { etat: 'absent', raison: 'encaissement trop ancien pour cette demande' }
  }

  /*
  LE MONTANT EST RECOMPARÉ MÊME QUAND ON A TROUVÉ PAR LA RÉFÉRENCE.

  Sans ça, un client pourrait recopier la référence d'un vrai paiement de
  mille francs et demander cinquante mille : la référence coïnciderait, le
  relais dirait ✅. C'est le montant LU DANS LE SMS DE L'OPÉRATEUR qui
  tranche, jamais celui que la demande annonce.
  */
  const recu = Math.round(Number(paiement.montant) || 0)
  if (recu !== fcfa) {
    return {
      etat: 'absent',
      raison: `l'encaissement porte ${recu} FCFA, la demande en annonce ${fcfa}`,
      montant: recu,
    }
  }

  const empreinte = paiement.reference || `${recu}-${paiement.numero}`
  if (await dejaVu('paiementservi', empreinte)) {
    return { etat: 'deja_servi', txid: empreinte, raison: 'cet encaissement a deja servi' }
  }

  return {
    etat: 'confirme',
    // On réemploie `txid` : c'est le champ « identifiant du versement »
    // que le message et l'application savent déjà afficher. Ici c'est la
    // référence de l'opérateur.
    txid: paiement.reference || '',
    montant: recu,
    quand: Number(paiement.quand) || 0,
    confirme: true,
    de: paiement.numero ? `tel. ${paiement.numero}` : '',
    rapproche: par,
    explorateur: '',
  }
}

/*
═══════════════════════════════════════════════════════════════════════════
LE MONTANT MARQUÉ — FERMER LE VOL DU VERSEMENT D'AUTRUI
═══════════════════════════════════════════════════════════════════════════

L'attaque que ceci ferme, et elle est facile :

  Alice envoie 2 USDT à l'adresse du changeur et ne dépose pas tout de
  suite sa demande. Bob surveille cette adresse — elle est publique,
  l'application la montre à chaque vendeur. Il voit le versement, dépose
  une demande pour 2 USDT avec SON numéro de téléphone. Le relais cherche
  un versement de 2 USDT, trouve celui d'Alice, répond « confirmé ».
  Bob est payé. Alice ne l'est pas.

Rien dans la vérification on-chain ne s'y oppose : le versement est réel,
le contrat est le bon, le montant correspond. Tout est vrai sauf
l'identité du demandeur.

─── POURQUOI PAS UNE SIGNATURE ──────────────────────────────────────────

C'était ma première idée : faire signer la demande par la clé de l'adresse
qui a envoyé, et vérifier contre le `from` lu sur la chaîne.

Elle ne marche pas, et c'est un essai réel qui l'a montré. Le premier
versement de test est parti DEPUIS BINANCE : l'expéditeur sur la chaîne
est le portefeuille chaud de l'exchange, dont personne n'a la clé. Exiger
une signature refuserait tous les retraits d'exchange — c'est-à-dire la
majorité des ventes. Et l'accepter en option ne fermerait rien, puisque
l'attaquant se contenterait de ne pas signer.

─── CE QUE FAIT LE MARQUAGE ─────────────────────────────────────────────

Le relais n'accepte plus « environ 2 USDT ». Il impose un montant EXACT,
dérivé de la référence par un HMAC dont lui seul a la clé :

    VX-K7M2PQ4R  →  envoyer exactement 2,004731
    VX-P3QW8NZD  →  envoyer exactement 2,009182

Alice envoie 2,004731 pour SA référence. Bob dépose sa demande — la
sienne exige 2,009182, et aucun versement de ce montant n'existe. Il ne
peut pas choisir son chiffre : il ne connaît pas le secret.

─── CE QUE ÇA COÛTE, ET CE QUE ÇA NE COÛTE PAS ──────────────────────────

Ça coûte un montant bizarre à recopier. L'écran doit le mettre en avant.

Ça ne coûte RIEN à l'utilisateur : il envoie 2,004731 et reçoit des francs
pour 2,004731, au même taux. Le marquage ajoute moins d'un centième
d'unité, et cette fraction lui est payée comme le reste.

─── ÉTEINT PAR DÉFAUT ───────────────────────────────────────────────────

Sans CHANGE_MARQUAGE, la tolérance d'un pour cent s'applique comme avant.
Un relais déployé sans ce réglage se comporte exactement comme la v22.
═══════════════════════════════════════════════════════════════════════════
*/

/** Vrai si le marquage des montants est en service. */
function marquageActif(env) {
  return String(env?.CHANGE_MARQUAGE ?? '').length >= 16
}

/*
SEIZE CARACTÈRES AU MINIMUM, et ce n'est pas arbitraire.

Ce secret est la seule chose qui empêche de calculer le marquage d'une
référence. Un secret de six caractères se retrouve par force brute hors
ligne dès qu'on observe un seul couple (référence, montant) — et les deux
sont publics : la référence est affichée à l'utilisateur, le montant est
sur la chaîne.

Refuser un secret trop court vaut mieux que de l'accepter en donnant
l'impression d'une protection. `openssl rand -hex 24` en produit un bon.
*/

/**
 * Le montant exact à envoyer pour cette demande.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * QUATRE DÉCIMALES DE MARQUAGE, PAS SIX
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Le marquage occupe les décimales 3 à 6 : de 0,000001 à 0,009999. Au
 * maximum un centième d'unité ajouté.
 *
 * Six décimales pleines donneraient dix mille fois plus de combinaisons —
 * mais ajouteraient jusqu'à 0,999999 USDT, et « envoie 2,847193 » pour une
 * vente de 2 ne ressemble plus à rien. Quelqu'un croirait à une erreur et
 * enverrait 2.
 *
 * Dix mille combinaisons, c'est peu en soi : un attaquant qui interroge
 * /change/consigne en boucle finit par tomber sur le marquage qu'il vise.
 * C'est pourquoi le marquage ne vient JAMAIS seul — voir le contrôle
 * d'antériorité dans verifierVente, qui lui impose d'avoir déposé sa
 * demande AVANT le versement qu'il convoite.
 * ═══════════════════════════════════════════════════════════════════════
 */
async function montantMarque(base, reference, env) {
  const b = Number(base)
  if (!(b > 0)) return 0
  if (!marquageActif(env)) return b
  const ref = String(reference ?? '').trim()
  if (!ref) return b

  const cle = await crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(String(env.CHANGE_MARQUAGE)),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign']
  )
  const signature = await crypto.subtle.sign(
    'HMAC',
    cle,
    new TextEncoder().encode(`vaultex:marquage:${ref}`)
  )
  const octets = new Uint8Array(signature)
  // Quatre premiers octets en entier, modulo dix mille : de 1 à 9999. On
  // écarte zéro, qui laisserait le montant rond — donc non marqué, donc
  // confondable avec celui de quelqu'un d'autre.
  const brut =
    ((octets[0] << 24) >>> 0) + (octets[1] << 16) + (octets[2] << 8) + octets[3]

  /*
  ═══════════════════════════════════════════════════════════════════════
  TROIS DÉCIMALES, ET NON SIX — PARCE QUE SIX ONT RATÉ CINQ FOIS
  ═══════════════════════════════════════════════════════════════════════

  « 2,004718 » est juste et impraticable. Mesuré sur cinq essais réels :
  à chaque fois le versement arrivait rond, et à chaque fois pour la même
  raison — un exchange retranche ses frais du montant tapé, et compenser
  six décimales à la main rate.

  « 6,472 » se lit, se retient, se retape, et se compense sans effort.

  ─── CE QU'ON PERD, ET POURQUOI C'EST ACCEPTABLE ─────────────────────

  Mille montants distinguables au lieu de dix mille. Ce chiffre ne protège
  que des demandes OUVERTES EN MÊME TEMPS : deux demandes séparées par une
  heure ne se confondent pas, la fenêtre les sépare. Pour un changeur qui
  en a quelques-unes en vol, mille est très large.

  Et l'essentiel est intact : un montant ROND ne paie jamais une demande.
  C'est ça qui empêche de réclamer le versement d'un autre, pas le nombre
  de décimales.

  ─── LE MARQUAGE AJOUTE, DONC LE MONTANT MONTE ───────────────────────

  Le résultat est toujours SUPÉRIEUR à ce qui a été demandé, d'au plus
  0,999. C'était déjà le cas — d'au plus 0,01 — et ça devient visible.

  Conséquence à ne pas perdre de vue : vendre TOUT son solde ne peut plus
  passer par un montant égal au solde, puisque le marquage le dépasserait.
  L'application doit le détecter et le dire, et c'est elle qui connaît le
  solde — pas ce relais.
  ═══════════════════════════════════════════════════════════════════════
  */
  const marque = (brut % 999) + 1

  // On repart de la partie entière : le marquage occupe seul les trois
  // décimales. Garder des décimales de la base les mélangerait aux
  // nôtres, et deux bases différentes pourraient donner le même total.
  const socle = Math.floor(b)
  return Math.round((socle + marque / 1_000) * 1_000) / 1_000
}

/**
 * Deux montants sont-ils le même, à la micro-unité près ?
 *
 * EXACT, et non à un pour cent près. C'est tout l'objet du marquage : la
 * tolérance qui rendait le service agréable est précisément ce qui
 * permettait de confondre deux versements.
 *
 * On compare en micro-unités entières plutôt qu'en flottants : 2,004731
 * et 2,004731 calculés par deux chemins différents ne sont pas forcément
 * le même double.
 */
function memeMontant(lu, attendu) {
  return Math.round(Number(lu) * 1_000_000) === Math.round(Number(attendu) * 1_000_000)
}

/**
 * Le montant exact à envoyer, et la naissance de la demande.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * APPELÉ AVANT L'ENVOI, ET C'EST TOUT L'INTÉRÊT
 * ═══════════════════════════════════════════════════════════════════════
 *
 * L'application appelle ceci quand l'utilisateur confirme son prix, donc
 * avant qu'il ne touche à sa crypto. Le relais en profite pour noter
 * l'instant : à partir de là, seuls les versements POSTÉRIEURS pourront
 * satisfaire cette référence.
 *
 * C'est ce qui empêche de déposer une demande après avoir vu passer le
 * versement de quelqu'un d'autre. Le marquage du montant empêche de le
 * deviner ; celui-ci empêche de le constater.
 *
 * ─── RÉPÉTABLE SANS DOMMAGE ─────────────────────────────────────────
 *
 * Deux appels avec la même référence rendent le même montant — un HMAC
 * est déterministe — et le PREMIER instant est conservé. Une application
 * qui redemande après un changement d'écran ne repousse donc pas sa
 * propre date de naissance, ce qui l'empêcherait de reconnaître un
 * versement déjà parti.
 */
async function consigneChange(url, env) {
  const monnaie = String(url.searchParams.get('monnaie') ?? '').trim().toUpperCase()
  const base = nombreFrancais(url.searchParams.get('montant'))
  const reference = String(url.searchParams.get('reference') ?? '').trim()

  if (!monnaie || !(base > 0) || !reference) {
    return json({ ok: false, raison: 'consigne incomplete' }, 0, 400)
  }

  const montantExact = await montantMarque(base, reference, env)

  /*
  LA NAISSANCE EST POSÉE UNE SEULE FOIS.

  `lireValeur` d'abord : si la demande est déjà connue, on garde sa date.
  Sinon un simple retour sur l'écran de calcul, puis une reconfirmation,
  repousserait sa naissance après un versement déjà effectué — et
  l'utilisateur ne retrouverait jamais ses propres fonds.
  */
  const connue = await lireValeur('demande', reference)
  const ne = Number(connue?.ne) || Date.now()
  if (!connue) await marquerValeur('demande', reference, JSON.stringify({ ne }))

  return json(
    {
      ok: true,
      reference,
      marque: marquageActif(env),
      // En texte, avec ses six décimales : c'est ce que l'utilisateur doit
      // recopier, et un nombre JSON perdrait les zéros de fin.
      montantExact: montantExact.toFixed(6).replace(/0+$/, '').replace(/\.$/, ''),
      ne,
    },
    0
  )
}
