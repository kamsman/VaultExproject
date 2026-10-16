/*
═══════════════════════════════════════════════════════════════════════════
L'ASSISTANCE, ESSAYÉE DEPUIS LE PC
═══════════════════════════════════════════════════════════════════════════

    node tools/relais-cours/essai-assistance.mjs
      └── joue les six questions connues, d'affilée

    node tools/relais-cours/essai-assistance.mjs "ta question a toi"
      └── en pose une seule

─── POURQUOI CET OUTIL PLUTÔT QUE curl ─────────────────────────────────

Parce que le corps est du JSON, que PowerShell mange les guillemets, et
qu'on a déjà perdu des tours là-dessus dans ce projet. Ici il n'y a rien à
échapper : on écrit la question entre guillemets simples et c'est tout.

Et parce qu'il DIT D'OÙ VIENT LA RÉPONSE. C'est la seule chose qu'on ne
peut pas deviner en lisant le texte : une réponse construite sur les
réglages et une réponse d'un modèle se ressemblent, et seule la première
est exacte par construction.
═══════════════════════════════════════════════════════════════════════════
*/

import { readFileSync } from 'node:fs'

const QUESTIONS_CONNUES = [
  "C'est quoi le minimum pour échanger ?",
  'Combien de temps ça prend ?',
  'Vous prenez combien de frais ?',
  'Quel réseau je dois choisir ?',
  "J'ai envoyé et je n'ai rien reçu",
  "On me demande ma phrase secrète, c'est normal ?",
]

/**
 * L'adresse du relais, depuis local.properties.
 *
 * ON NE LIT QU'UNE LIGNE, et c'est volontaire : ce fichier contient aussi
 * des clés d'API et le mot de passe du magasin de signature. Rien d'autre
 * que `prix.relay.url` n'est extrait, et rien d'autre n'est imprimé.
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
const relaisArg = args.find((a) => a.startsWith('https://'))
const question = args.filter((a) => !a.startsWith('https://')).join(' ').trim()
const relais = (relaisArg || relaisDepuisProprietes()).replace(/\/$/, '')

if (!relais.startsWith('https://')) {
  console.log("\n❌ Adresse du relais introuvable.")
  console.log('   Lance la commande depuis la racine du depot, ou passe')
  console.log("   l'adresse en argument.")
  process.exit(2)
}

console.log(`\nRelais : ${relais}`)

/*
UN OUTIL DE DIAGNOSTIC NE DOIT PAS TOMBER SUR UNE TRACE DE PILE.

`fetch` lève quand le réseau manque, et Node imprime alors quarante lignes
d'internals. Au moment où l'on vient chercher pourquoi quelque chose ne
marche pas, c'est le pire des messages.
*/
async function demander(texte) {
  try {
    const r = await fetch(`${relais}/assistance`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ messages: [{ role: 'user', content: texte }] }),
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

const aPoser = question ? [question] : QUESTIONS_CONNUES
let connues = 0
let modele = 0
/*
LE TROISIÈME CAS, QUE JE N'AVAIS PAS COMPTÉ.

Le relais rend `source: 'repli'` quand il ne sait pas répondre ET qu'il
n'y a pas de clé : une bulle qui liste ce qu'il sait faire. Mon compteur
ne connaissait que « reglages » et « le reste », et classait donc ce repli
comme une réponse de MODÈLE — en concluant « la cle d'API est posee et le
credit tient » alors qu'il n'y a aucune clé.

Un diagnostic qui se trompe dans ce sens-là est pire qu'absent : il fait
croire qu'une facture court, ou qu'un réglage est en place quand il ne
l'est pas.
*/
let replis = 0
let echecs = 0

for (const q of aPoser) {
  console.log('\n' + '─'.repeat(70))
  console.log(`❓ ${q}`)
  const r = await demander(q)

  if (r.statut === 0) {
    console.log(`\n❌ ${r.texte}`)
    echecs += 1
    continue
  }
  if (r.corps?.ok === true) {
    /*
    LA SOURCE EST LA SEULE CHOSE QU'ON NE PEUT PAS DEVINER EN LISANT.

    Une réponse construite sur les réglages et une réponse d'un modèle se
    ressemblent — et seule la première est exacte par construction. Sans
    cette ligne, on croirait l'assistance installée alors qu'elle ne fait
    que réciter ce que le Worker sait déjà, ou l'inverse.
    */
    const source =
      r.corps.source === 'reglages' ? 'REGLAGES (gratuit, exact)'
      : r.corps.source === 'repli' ? 'REPLI (gratuit — aucune cle, aucun modele)'
      : 'MODELE (paye)'
    if (r.corps.source === 'reglages') connues += 1
    else if (r.corps.source === 'repli') replis += 1
    else modele += 1
    console.log(`\n💬 ${r.corps.reponse}`)
    console.log(`\n   source : ${source}`)
    continue
  }

  echecs += 1
  console.log(`\n❌ ${r.corps?.raison ?? r.texte ?? `HTTP ${r.statut}`}`)
  if (r.corps?.humain) console.log("   (l'ecran proposera de joindre le changeur)")
  if (r.corps?.detail) console.log(`   detail : ${r.corps.detail}`)
}

console.log('\n' + '═'.repeat(70))
console.log(
  `  ${connues} par les reglages · ${replis} en repli · ` +
    `${modele} par le modele · ${echecs} sans reponse`
)

if (!question && connues < QUESTIONS_CONNUES.length && modele === 0) {
  /*
  LE CAS QU'IL FAUT NOMMER : sans clé d'API, les six questions connues
  doivent TOUTES aboutir. Si l'une manque, ce n'est pas la clé qui
  manque — c'est un mot que le relais ne reconnaît pas, et ça se corrige
  dans `reponsesConnues`.
  */
  console.log('\n⚠  Une question connue n’a pas abouti alors qu’aucun modele')
  console.log('   n’a repondu. Ce n’est donc pas la cle d’API qui manque :')
  console.log('   c’est un mot que `reponsesConnues` ne reconnait pas.')
}
if (modele > 0) {
  console.log('\n✅ Le modele repond : la cle d’API est posee et le credit tient.')
} else if (replis > 0) {
  console.log('\nℹ  Repli : le relais ne sait pas repondre a celle-la, ET il n’y a')
  console.log('   pas de cle d’API. Il liste ce qu’il sait faire, ce qui est la')
  console.log('   bonne reponse en attendant — mais AUCUN modele n’a ete appele.')
} else if (connues > 0) {
  /*
  CETTE LIGNE DISAIT « c'est normal tant que CHANGE_IA_CLE n'est pas
  reglee ». Elle l'a dit APRES que la cle a ete posee — et c'etait faux.

  Aucun appel au modele ne prouve rien sur la cle quand toutes les
  questions posees avaient deja leur reponse dans les reglages : c'est
  precisement le resultat qu'on veut, et il se produit avec ou sans cle.

  Un diagnostic ne doit affirmer que ce qu'il a mesure. Ici il n'a rien
  mesure sur la cle, donc il dit comment la mesurer.
  */
  console.log('\nℹ  Aucune question n’a eu besoin du modele : les six connues ont')
  console.log('   repondu depuis les reglages. C’est le bon ordre — elles sont')
  console.log('   gratuites, instantanees, et exactes par construction.')
  console.log('\n   Ca ne dit RIEN sur la cle d’API. Pour l’eprouver, pose une')
  console.log('   question hors sujet :')
  console.log('     node tools/relais-cours/essai-assistance.mjs "est ce que vous etes a bobo"')
}

/*
`process.exitCode` ET NON `process.exit()`.

Mesuré sous Windows : « Assertion failed: !(handle->flags &
UV_HANDLE_CLOSING) » s'affichait apres le resume. `process.exit()` coupe
l'execution pendant que les descripteurs de `fetch` se referment encore,
et libuv s'en plaint bruyamment.

Ce n'est qu'un bruit, mais un outil de diagnostic qui finit sur une
assertion fait douter de ce qu'il vient d'imprimer. On pose le code de
sortie et on laisse Node se terminer tout seul.
*/
process.exitCode = echecs > 0 && connues === 0 && replis === 0 ? 1 : 0
