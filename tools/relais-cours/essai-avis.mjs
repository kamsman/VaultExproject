/*
═══════════════════════════════════════════════════════════════════════════
LE FORMULAIRE D'AVIS, ÉPROUVÉ DEPUIS LE PC
═══════════════════════════════════════════════════════════════════════════

    node tools/relais-cours/essai-avis.mjs
      └── SANS RIEN ÉCRIRE dans le canal : montre ce que l'anonymisation
          fait d'un texte piégé, et confirme que la route est déployée

    node tools/relais-cours/essai-avis.mjs --envoyer
      └── envoie pour de vrai un avis d'essai, à lire dans Telegram

    node tools/relais-cours/essai-avis.mjs --envoyer "ton texte a toi"

─── POURQUOI LE DÉFAUT N'ENVOIE RIEN ─────────────────────────────────────

Parce que chaque envoi écrit dans le canal de l'administrateur et consomme
l'un des soixante avis du jour. Un outil de vérification qu'on relance
trois fois ne doit pas laisser trois traces dans le canal qu'il vérifie.

LA SONDE EST GRATUITE, et ce n'est pas un hasard : `recevoirAvis` refuse
un texte de moins de trois caractères AVANT de toucher au compteur et
AVANT d'appeler Telegram. Un POST au texte vide distingue donc

    404  la route n'existe pas — le déploiement n'a pas pris
    400  la route répond, et elle refuse ce qu'elle doit refuser

sans rien écrire nulle part. C'est exactement ce qu'on veut savoir après
un déploiement, et `/sante` ne le dit pas.

─── CE QUE CET OUTIL NE PEUT PAS VOIR, ET IL FAUT LE SAVOIR ──────────────

CE QUI ARRIVE DANS TELEGRAM. Le relais écrit dans le canal ; il ne rend
pas ce qu'il a écrit. L'aperçu ci-dessous est produit par la fonction
`anonymiserQuestion` du worker.js LOCAL — donc par le code de ce dépôt,
et non par celui qui tourne sur Cloudflare.

Les deux concordent si le déploiement est à jour. Le seul contrôle qui
tranche vraiment est de lancer `--envoyer` et de LIRE le message dans le
canal. L'aperçu dit ce qu'on attend ; Telegram dit ce qui est.
═══════════════════════════════════════════════════════════════════════════
*/

import { readFileSync } from 'node:fs'
import { anonymiserQuestion } from './worker.js'

/*
UN TEXTE PIÉGÉ, et piégé avec ce que les gens écrivent vraiment.

Ce n'est pas une suite de motifs choisis pour passer le test : c'est la
phrase type de quelqu'un qui se plaint après un échange qui a mal tourné,
et c'est précisément le moment où le formulaire s'ouvre. Référence,
montant, numéro, adresse, courriel : tout y est parce que tout y est dans
la vraie vie.
*/
const PIEGE =
  'jai paye 10000 FCFA pour la demande VX-A1B2C3D4 au 70123456, ' +
  'ladresse DOGE etait DH5yaieqoZN36fDVciNyRueRGvGLR3mr7L et la TRON ' +
  'TJYeasTPa6gpEEfYYSCCDHxZLBPbEgE1Qp, jai rien recu, ' +
  'ecrivez moi a quelquun@exemple.com'

/**
 * L'adresse du relais, depuis local.properties.
 *
 * ON NE LIT QU'UNE LIGNE. Ce fichier contient aussi les clés d'API et le
 * mot de passe du magasin de signature : rien d'autre que `prix.relay.url`
 * n'est extrait, et rien d'autre n'est imprimé.
 */
function relaisDepuisProprietes() {
  for (const chemin of ['local.properties', '../../local.properties']) {
    try {
      const ligne = readFileSync(chemin, 'utf8')
        .split('\n')
        .find((l) => l.trim().startsWith('prix.relay.url'))
      if (!ligne) continue
      const valeur = ligne.split('=').slice(1).join('=').trim()
      if (valeur) return valeur.replace(/\/?api\/v3\/?$/, '').replace(/\/$/, '')
    } catch (_) {
      /* fichier absent à cet endroit : on essaie le suivant */
    }
  }
  return ''
}

const args = process.argv.slice(2)
const envoyer = args.includes('--envoyer')
const relaisArg = args.find((a) => a.startsWith('https://'))
const texteArg = args
  .filter((a) => !a.startsWith('https://') && !a.startsWith('--'))
  .join(' ')
  .trim()
const relais = (relaisArg || relaisDepuisProprietes()).replace(/\/$/, '')

if (!relais.startsWith('https://')) {
  console.log('\n❌ Adresse du relais introuvable.')
  console.log('   Lance la commande depuis la racine du depot, ou passe')
  console.log("   l'adresse en argument.")
  process.exit(2)
}

/* Un outil de diagnostic ne doit jamais tomber sur une trace de pile :
   `fetch` lève quand le réseau manque, et quarante lignes d'internals
   sont le pire message au moment où l'on cherche une panne. */
async function poster(corps) {
  try {
    const r = await fetch(`${relais}/avis`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(corps),
    })
    const brut = await r.text()
    try {
      return { statut: r.status, corps: JSON.parse(brut) }
    } catch (_) {
      return { statut: r.status, corps: null, texte: brut.slice(0, 200) }
    }
  } catch (e) {
    const cause = e?.cause?.code || e?.code || e?.message || 'inconnue'
    return { statut: 0, corps: null, texte: `reseau : ${cause}` }
  }
}

async function version() {
  try {
    const r = await fetch(`${relais}/sante`)
    const j = await r.json()
    return j?.version ?? null
  } catch (_) {
    return null
  }
}

console.log(`\nRelais : ${relais}`)
const v = await version()
/*
DEUX SEUILS, ET ILS NE DISENT PAS LA MEME CHOSE.

105 a apporte la route `/avis`. 106 a apporte l'anonymisation des
adresses BTC, bech32, DOGE et Solana, qui partaient en clair avant. Un
relais en 105 accepte donc les avis SANS masquer ces adresses — ce qui
est pire qu'un relais qui les refuse, parce que l'application y promet
le contraire.
*/
const manque =
  v === null ? ''
  : v < 105 ? '  ⚠️  /avis est arrive en 105'
  : v < 106 ? '  ⚠️  les adresses BTC / DOGE / SOL ne sont masquees qu a partir de 106'
  : ''
console.log(`Version : ${v ?? 'illisible'}${manque}`)

// ─── 1. Ce que l'anonymisation fait du texte piégé ──────────────────────
const texte = texteArg || PIEGE
console.log('\n' + '─'.repeat(70))
console.log('CE QUE LA PERSONNE ECRIT')
console.log('─'.repeat(70))
console.log(texte)
console.log('\n' + '─'.repeat(70))
console.log("CE QUI PARTIRAIT, selon le worker.js DE CE DEPOT")
console.log('─'.repeat(70))
const anonyme = anonymiserQuestion(texte, 400)
console.log(anonyme)

/*
ON NE SE CONTENTE PAS DE L'AFFICHER : ON LE CONTRÔLE.

Un aperçu qu'on relit à l'œil laisse passer exactement ce qu'il devait
attraper. Ici, chaque fragment sensible du texte piégé est recherché dans
la sortie, et sa présence est un échec.
*/
if (!texteArg) {
  const restes = [
    ['la reference', 'A1B2C3D4'],
    ['le numero', '70123456'],
    /*
    L'ADRESSE DOGE EST ICI PARCE QU'ELLE FUYAIT. La fonction ne
    connaissait que l'EVM et TRON ; BTC, bech32, DOGE et Solana
    passaient en clair. Ce controle-la est le seul de la liste qui ait
    deja echoue pour de vrai.
    */
    ['l adresse DOGE', 'DH5yaieqoZN36fDVciNyRueRGvGLR3mr7L'],
    ['l adresse TRON', 'TJYeasTPa6gpEEfYYSCCDHxZLBPbEgE1Qp'],
    ['le courriel', 'quelquun@exemple.com'],
  ].filter(([, fragment]) => anonyme.includes(fragment))

  console.log('')
  if (restes.length === 0) {
    console.log('✅ Reference, numero, adresse et courriel ont tous disparu.')
    console.log('   La plainte, elle, reste lisible — et c est le but.')
  } else {
    for (const [quoi, fragment] of restes) {
      console.log(`❌ ${quoi} est RESTE : ${fragment}`)
    }
    console.log('\n   La ligne affichee dans l application promet le contraire.')
    process.exitCode = 1
  }
}

// ─── 2. La route existe-t-elle ? (gratuit) ──────────────────────────────
console.log('\n' + '─'.repeat(70))
console.log('LA ROUTE REPOND-ELLE ?')
console.log('─'.repeat(70))
const sonde = await poster({ texte: '' })
if (sonde.statut === 400) {
  console.log('✅ 400 — la route existe et refuse le texte vide, comme prevu.')
  console.log('   Rien n a ete ecrit dans le canal, aucun avis consomme.')
} else if (sonde.statut === 404) {
  console.log('❌ 404 — la route est absente : le deploiement n a pas pris.')
  /*
  LES QUATRE LIGNES, PAS LA DERNIERE SEULE.

  La version precedente de ce message n'imprimait que `npx wrangler
  deploy --config wrangler.toml`. Lancee depuis la RACINE du depot, ou
  `wrangler.toml` n'existe pas, elle echoue sur un chemin double et
  incomprehensible — et c'est arrive. Le `cd` est la marche qu'on omet
  parce qu'elle va de soi pour celui qui ecrit la commande.

  La procedure complete vit dans l'en-tete de `wrangler.toml`.
  */
  console.log('')
  console.log('   Deployer :')
  console.log('     cd tools/relais-cours')
  console.log('     node verifier-version.mjs')
  console.log('     npx wrangler deploy --config wrangler.toml')
  console.log('     (npx.cmd sous PowerShell)')
  process.exitCode = 1
} else if (sonde.statut === 0) {
  console.log(`❌ ${sonde.texte}`)
  process.exitCode = 1
} else {
  console.log(`⚠️  ${sonde.statut} — inattendu.`)
  console.log(`   ${JSON.stringify(sonde.corps ?? sonde.texte)}`)
  process.exitCode = 1
}

// ─── 3. L'envoi réel, seulement si on l'a demandé ───────────────────────
if (!envoyer) {
  console.log('\n' + '─'.repeat(70))
  console.log('Rien n a ete envoye. Pour voir le message dans Telegram :')
  console.log('  node tools/relais-cours/essai-avis.mjs --envoyer')
  console.log('─'.repeat(70) + '\n')
  process.exit(process.exitCode ?? 0)
}

console.log('\n' + '─'.repeat(70))
console.log('ENVOI REEL')
console.log('─'.repeat(70))
const r = await poster({
  texte,
  etoiles: 4,
  jours: 12,
  modele: 'Essai depuis le PC',
  version: '0.0.0-essai',
})

if (r.statut === 200 && r.corps?.ok === true) {
  if (r.corps.note === 'canal absent') {
    /*
    CE CAS A L'AIR D'UN SUCCÈS ET N'EN EST PAS UN.

    Le relais répond « reçu » sans canal configuré, exprès : l'application
    a déjà refermé son formulaire et ne le reproposera jamais, donc lui
    rendre une erreur lui ferait afficher un échec sur un geste qu'elle ne
    peut plus refaire. Mais sur ce PC, où l'on vérifie, « reçu » sans
    `TG_ADMIN_CHAT` doit se lire comme un réglage manquant.
    */
    console.log('⚠️  Accepte, mais TG_ADMIN_CHAT n est pas posee sur le Worker.')
    console.log('   Rien n arrivera dans Telegram tant qu elle manque.')
    process.exitCode = 1
  } else {
    console.log('✅ Parti. Va LIRE le message dans le canal : c est le seul')
    console.log('   controle qui dise ce qui est reellement arrive.')
    console.log('')
    console.log('   A verifier de l oeil, dans cet ordre :')
    console.log('   · la premiere ligne est bien l en-tete « Avis d un utilisateur »')
    console.log('   · le texte tient sur UNE ligne, entre guillemets')
    console.log('   · ni reference, ni numero, ni adresse, ni courriel')
    console.log('   · le message est dans le canal de l ADMIN, pas celui du changeur')
  }
} else if (r.statut === 429) {
  console.log('⚠️  429 — les 60 avis du jour sont atteints.')
  console.log('   Le plafond fait son travail ; reessaie demain.')
} else {
  console.log(`❌ ${r.statut} — ${JSON.stringify(r.corps ?? r.texte)}`)
  process.exitCode = 1
}
console.log('')
