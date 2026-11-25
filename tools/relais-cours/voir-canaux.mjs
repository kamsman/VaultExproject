/*
═══════════════════════════════════════════════════════════════════════════
LES DEUX CANAUX TELEGRAM SONT-ILS LE MÊME BOT ?
═══════════════════════════════════════════════════════════════════════════

    cd tools/relais-cours
    node voir-canaux.mjs
      └── demande la clé de diagnostic sans l'afficher, puis répond

    node voir-canaux.mjs --tout
      └── imprime tout /diag, pas seulement cette réponse

    node voir-canaux.mjs --plages=bnb
      └── ne pose plus la question des canaux : sonde les NŒUDS du réseau
          et dit lequel répond, jusqu'à quelle largeur de blocs.
          `--plages` seul vaut `--plages=bnb`.

─── POURQUOI CET OUTIL ET PAS UN curl ────────────────────────────────────

Parce que la commande équivalente s'écrit

    curl "https://TON-RELAIS/diag?cle=TA_CLE_DIAG"

et que, dans ce projet, ce genre de ligne a déjà été collée TELLE QUELLE
trois fois — « TON-RELAIS » compris. Ce n'est pas une inattention : une
commande à trous demande de savoir où est chaque valeur, et l'une des deux
vit dans un fichier ignoré par git, l'autre dans un secret Cloudflare.

Ici il n'y a aucun trou. L'adresse se lit dans `local.properties`, la clé
se demande à la frappe, et rien n'est à remplacer.

─── LA CLÉ NE PASSE PAS PAR L'HISTORIQUE DU SHELL ────────────────────────

Elle est saisie SANS ÉCHO, et c'est le mode par défaut. Passée en
argument, elle resterait dans `~/.bash_history` — et un secret dans un
historique est un secret qu'on oublie d'avoir écrit.

Les deux autres entrées existent quand même, pour un script :

    CHANGE_DIAG_CLE=... node voir-canaux.mjs
    node voir-canaux.mjs --cle=...

─── CE QU'IL N'IMPRIME PAS ───────────────────────────────────────────────

Par défaut, rien d'autre que la réponse à la question. `/diag` rend une
quarantaine de champs, dont des longueurs de clés : les faire défiler à
chaque fois apprend à ne plus les lire.
═══════════════════════════════════════════════════════════════════════════
*/

import { readFileSync } from 'node:fs'
import { createInterface } from 'node:readline'

/**
 * L'adresse du relais, depuis local.properties.
 *
 * ON NE LIT QU'UNE LIGNE. Ce fichier porte aussi les clés d'API et le mot
 * de passe du magasin de signature : rien d'autre que `prix.relay.url`
 * n'est extrait, et rien d'autre n'est imprimé.
 */
function propriete(nom) {
  /*
  LES CHEMINS SONT RESOLUS DEPUIS CE FICHIER, PAS DEPUIS LE DOSSIER
  COURANT — et la nuance a coute quatre commandes perdues en une soiree.

  La liste precedente, [local.properties, ../../local.properties], decrit
  deux dossiers : la racine du depot, et tools/relais-cours. Lancer
  loutil depuis app/, depuis /tmp, ou depuis la racine avec le chemin
  complet — node tools/relais-cours/cet-outil.mjs — ne trouvait rien, et
  loutil annoncait une adresse de relais introuvable alors que le fichier
  etait la.

  import.meta.url ne depend de rien dexterieur. Cest ce que fait deja
  verifier-deploiement.mjs, et cest pour cela que lui seul marchait
  depuis les deux dossiers.

  LES DEUX FORMES RELATIVES RESTENT EN DERNIER RECOURS : si quelquun
  copie cet outil ailleurs, le dossier courant redevient le seul repere.
  */
  const candidats = [
    new URL('../../local.properties', import.meta.url),
    new URL('./local.properties', import.meta.url),
    'local.properties',
    '../../local.properties',
  ]
  for (const chemin of candidats) {
    try {
      const ligne = readFileSync(chemin, 'utf8')
        .split('\n')
        .find((l) => l.trim().startsWith(`${nom}=`) || l.trim().startsWith(`${nom} =`))
      if (!ligne) continue
      const valeur = ligne.split('=').slice(1).join('=').trim()
      if (valeur) return valeur
    } catch (_) {
      /* fichier absent à cet endroit : on essaie le suivant */
    }
  }
  return ''
}

function relaisDepuisProprietes() {
  const v = propriete('prix.relay.url')
  return v ? v.replace(/\/?api\/v3\/?$/, '').replace(/\/$/, '') : ''
}

/*
═══════════════════════════════════════════════════════════════════════════
LE JETON DE L'APK, QUE LA SONDE DU WORKER NE PEUT PAS VOIR
═══════════════════════════════════════════════════════════════════════════

CE MANQUE EST LA RAISON D'ETRE DE CETTE FONCTION, et il m'a echappe.

`separationCanaux` tourne DANS le Worker : elle ne connait que
`TG_CHANGE_TOKEN` et `TG_ADMIN_TOKEN`, les variables Cloudflare. Or le
jeton qui part dans les telephones est une TROISIEME valeur — posee dans
`local.properties` sous `telegram.admin.token`, lue a la compilation par
`build.gradle.kts`, gravee dans `BuildConfig`.

Mesure faite sur une vraie configuration : le Worker a repondu
« TG_ADMIN_TOKEN : ABSENTE », ce qui se lit comme rassurant, alors que
l'APK en porte un. Les deux noms se ressemblent et designent deux choses.

J'avais donc construit, pour repondre a « le jeton de l'APK atteint-il le
canal des ordres ? », une sonde qui ne regarde pas l'APK. C'est la meme
faute, un cran plus haut, que celle que je reprochais a `canalChange`.

SEULE CETTE MACHINE PEUT LES COMPARER : le Worker ne voit pas
`local.properties`, et `local.properties` ne voit pas le Worker.

LE JETON N'EST JAMAIS IMPRIME. Il part vers api.telegram.org, qui en est
le proprietaire legitime, et seul le nom du bot revient a l'ecran.
═══════════════════════════════════════════════════════════════════════════
*/
async function botDeLApk() {
  const jeton = propriete('telegram.admin.token')
  if (!jeton) return { pose: false }
  try {
    const r = await fetch(`https://api.telegram.org/bot${jeton}/getMe`)
    const o = await r.json()
    if (!o?.ok) {
      return { pose: true, valide: false, raison: String(o?.description ?? `HTTP ${r.status}`) }
    }
    return {
      pose: true,
      valide: true,
      id: Number(o.result?.id) || null,
      bot: String(o.result?.username ?? ''),
    }
  } catch (_) {
    return { pose: true, valide: false, raison: 'injoignable' }
  }
}

/**
 * Demande la clé sans l'afficher.
 *
 * LE REPLI N'EST PAS DÉCORATIF. Sans terminal — dans un tube, sous un
 * outil de construction — `setRawMode` n'existe pas et lèverait. On lit
 * alors la ligne normalement : moins bien, mais ça marche, et un outil de
 * diagnostic qui tombe au moment où l'on vient diagnostiquer est le pire
 * des outils.
 */
function demanderCle() {
  return new Promise((resolve) => {
    const rl = createInterface({ input: process.stdin, output: process.stdout })
    const muet = Boolean(process.stdin.isTTY)
    if (muet) {
      // On remplace l'écriture de l'écho par un seul caractère neutre, au
      // lieu de ne rien écrire du tout : un champ parfaitement muet laisse
      // croire que le clavier ne répond pas.
      rl._writeToOutput = function (chaine) {
        if (chaine.includes('\n') || chaine.startsWith('Cle')) rl.output.write(chaine)
        else rl.output.write('*')
      }
    }
    rl.question('Cle de diagnostic (CHANGE_DIAG_CLE) : ', (reponse) => {
      rl.close()
      /*
      ═══════════════════════════════════════════════════════════════════
      SANS CES DEUX LIGNES, NODE HURLE EN SORTANT — SOUS WINDOWS
      ═══════════════════════════════════════════════════════════════════

      MESURE :

        Assertion failed: !(handle->flags & UV_HANDLE_CLOSING),
        file src\win\async.c, line 76

      Ca s'imprime APRES le verdict, en rouge, et ca ressemble a un
      plantage de l'outil. Ce n'en est pas un : le resultat est deja
      affiche et juste. Mais quelqu'un qui vient verifier un point de
      securite et lit « Assertion failed » ne croira pas la ligne du
      dessus, et c'est tout ce qui compte ici.

      La cause : `rl.close()` ne rend pas `process.stdin` inactif. Le
      handle reste arme, et `process.exit()` le ferme pendant que libuv
      le fermait deja. `pause()` l'arrete, `unref()` l'empeche de retenir
      la boucle d'evenements.
      ═══════════════════════════════════════════════════════════════════
      */
      try {
        process.stdin.pause()
        process.stdin.unref()
      } catch (_) { /* pas de stdin : rien a liberer */ }
      if (muet) process.stdout.write('\n')
      resolve(String(reponse).trim())
    })
  })
}

const args = process.argv.slice(2)
const tout = args.includes('--tout')
const relaisArg = args.find((a) => a.startsWith('https://'))
const cleArg = args.find((a) => a.startsWith('--cle='))?.slice('--cle='.length)
const relais = (relaisArg || relaisDepuisProprietes()).replace(/\/$/, '')

/*
`--plages` SEUL VAUT `--plages=bnb`, pour la même raison que le Worker
pose ce défaut de son côté : le réseau qu'on vient sonder est presque
toujours celui-là, et une commande qui exige un argument qu'on ne connaît
pas encore ne se lance pas. `null` signifie « pas demandé » — à
distinguer d'une chaîne vide, qui serait un `--plages=` mal tapé.
*/
const plagesArg = args.find((a) => a === '--plages' || a.startsWith('--plages='))
const plages =
  plagesArg === undefined
    ? null
    : (plagesArg.split('=')[1] || 'bnb').trim().toLowerCase()

if (!relais.startsWith('https://')) {
  console.log('\n❌ Adresse du relais introuvable.')
  console.log('   Lance la commande depuis le depot, ou passe l adresse en argument.')
  process.exit(2)
}

console.log(`\nRelais : ${relais}`)

/*
═══════════════════════════════════════════════════════════════════════════
UN SEUL LECTEUR POUR TOUS LES `/diag*`
═══════════════════════════════════════════════════════════════════════════

Les deux questions que cet outil pose — les canaux, les nœuds — partent au
MÊME endroit, avec la MÊME clé, et butent sur les MÊMES refus. En dupliquer
la lecture, c'est accepter qu'un jour l'une des deux explique le 403 et
l'autre dise « acces refuse ».

Il rend soit `{ reponse }`, soit `{ code }` — et dans le second cas il a
déjà tout imprimé. L'appelant n'a qu'à relayer le code.
═══════════════════════════════════════════════════════════════════════════
*/
async function interroger(chemin, cle) {
  const joint = chemin.includes('?') ? '&' : '?'
  try {
    const r = await fetch(`${relais}${chemin}${joint}cle=${encodeURIComponent(cle)}`)
    const brut = await r.text()
    /*
    LE 403 SE DISTINGUE DU RESTE, parce que ses deux causes demandent deux
    gestes differents : une mauvaise cle se retape, une variable absente se
    pose sur le Worker. `refusDiag` les separe deja dans sa raison — on la
    relaie plutot que de dire « acces refuse ».
    */
    if (r.status === 403) {
      let raison = ''
      try { raison = JSON.parse(brut)?.raison ?? '' } catch (_) { raison = brut.slice(0, 160) }
      console.log(`\n❌ 403 — ${raison}`)
      if (/n.est pas reglee/i.test(raison)) {
        console.log('\n   Elle n a JAMAIS ete posee sur ce relais. Pour en poser une :')
        console.log('     npx wrangler secret put CHANGE_DIAG_CLE --config wrangler.toml')
        console.log('   La valeur est de ton choix : rien ne la valide, c est un mot')
        console.log('   de passe que tu inventes. Un secret survit aux deploiements.')
      } else {
        console.log('\n   Elle EXISTE sur le relais, mais ce n est pas celle-la.')
        console.log('   Cloudflare ne reaffiche jamais un secret : si tu l as perdue,')
        console.log('   pose-en une neuve par-dessus, c est sans consequence.')
        console.log('     npx wrangler secret put CHANGE_DIAG_CLE --config wrangler.toml')
      }
      return { code: 1 }
    }
    if (!r.ok) {
      /*
      LE 400 DE `/diag/plages` PORTE UNE RAISON UTILE — « reseau inconnu »
      et la liste de ceux qu'il connaît. L'imprimer brut obligerait à lire
      du JSON pour apprendre qu'on a tapé « bsc » au lieu de « bnb ».
      */
      let detail = brut.slice(0, 200)
      try {
        const o = JSON.parse(brut)
        detail = o?.erreur || o?.raison || detail
        if (Array.isArray(o?.connus)) detail += `\n   reseaux connus : ${o.connus.join(', ')}`
      } catch (_) { /* pas du JSON : on garde le texte brut */ }
      console.log(`\n❌ HTTP ${r.status} — ${detail}`)
      return { code: 1 }
    }
    return { reponse: JSON.parse(brut) }
  } catch (e) {
    const cause = e?.cause?.code || e?.code || e?.message || 'inconnue'
    console.log(`\n❌ reseau : ${cause}`)
    return { code: 1 }
  }
}

/*
═══════════════════════════════════════════════════════════════════════════
LES NŒUDS DU RÉSEAU RÉPONDENT-ILS, ET JUSQU'OÙ ?
═══════════════════════════════════════════════════════════════════════════

CE QUE CETTE SONDE MESURE, ET QU'AUCUN RAISONNEMENT NE DONNE : la largeur
de plage en blocs que chaque nœud accepte sur `eth_getLogs`. Deux limites
ont été écrites dans le Worker en la supposant — deux mille, puis cinq
cents, « qui passe partout » — et les deux fois la production a répondu
« limit exceeded », au milieu d'une vérification de vente.

ELLE TOURNE DANS LE WORKER, PAS ICI, et c'est tout l'intérêt : les points
d'entrée publics limitent PAR IP, et un Worker sort par des adresses
Cloudflare partagées avec tous les robots du monde. Une mesure faite
depuis cette machine décrirait ta connexion, pas celle du relais.

CE QU'ELLE N'IMPRIME PAS : l'URL des nœuds. Le Worker les masque avant de
les nommer (`hoteMasque`), parce que celle d'un nœud nominatif porte sa
clé d'API dans son chemin.
═══════════════════════════════════════════════════════════════════════════
*/
async function rapportPlages(cle) {
  const res = await interroger(`/diag/plages?reseau=${encodeURIComponent(plages)}`, cle)
  if (res.code !== undefined) return res.code
  const d = res.reponse

  if (tout) {
    console.log(JSON.stringify(d, null, 2))
    return 0
  }

  console.log(`Version : ${d?.version ?? 'illisible'}`)
  console.log('\n' + '─'.repeat(74))
  console.log(`Reseau sonde      : ${d?.reseau ?? plages}`)
  console.log(`Contrat interroge : ${d?.contrat ?? '(aucun)'}`)
  console.log(`Tranche en vigueur: ${d?.tranche_actuelle ?? '?'} blocs`)
  console.log(`Noeud nominatif   : ${d?.noeud_nominatif ?? '?'}`)
  console.log('─'.repeat(74))

  const noeuds = d?.noeuds ?? {}
  const noms = Object.keys(noeuds)
  if (noms.length === 0) {
    console.log('\n⚠️  Le relais n a sonde aucun noeud pour ce reseau.')
    return 1
  }

  /*
  `padEnd` SEUL NE SUFFIT PAS, et l'essai l'a montré tout de suite : la
  hauteur d'un nœud en échec vaut « ✗ 403 forbidden », soit quinze
  caractères dans une colonne de quatorze. `padEnd` ne tronque pas et ne
  complète pas — il rend la chaîne telle quelle, collée à la suivante :

      bsc-dataseed.binance.org     ✗ 403 forbidden—

  Une colonne doit donc garantir un séparateur même quand elle débord.
  */
  const col = (valeur, largeur) => {
    const s = String(valeur ?? '')
    return s.length >= largeur ? `${s}  ` : s.padEnd(largeur)
  }

  const large = Math.max(24, ...noms.map((n) => n.length))
  console.log(
    '\n' + col('noeud', large + 2) + col('hauteur', 16) + col('accepte', 14) + 'refus'
  )
  for (const nom of noms) {
    const n = noeuds[nom] ?? {}
    console.log(
      col(nom, large + 2) +
      col(n.hauteur ?? '?', 16) +
      col(n.accepte ?? '—', 14) +
      String(n.refus ?? '')
    )
  }

  /*
  LA CONCLUSION DU WORKER EST RELAYÉE TELLE QUELLE. Elle sait des choses
  que cette machine ne sait pas — notamment si `CHANGE_NOEUD_<RESEAU>` est
  réglée, donc si un échec général est attendu ou anormal. La réécrire
  ici, c'est se donner deux versions de la vérité.
  */
  if (d?.conclusion) {
    console.log('\n' + '─'.repeat(74))
    console.log(String(d.conclusion))
  }
  console.log('')

  /*
  LE CODE DE SORTIE SUIT CE QUI EST UTILISABLE, pas ce que le HTTP a dit.
  Un relais qui répond 200 « aucun noeud ne repond » est un échec pour
  celui qui vient vérifier, et doit pouvoir être enchaîné dans un script.
  */
  const vivant = noms.some((n) => typeof noeuds[n]?.hauteur === 'number' && noeuds[n]?.accepte !== 'AUCUNE')
  return vivant ? 0 : 1
}

/*
TOUT CE QUI SUIT LE PROMPT VIT DANS UNE FONCTION, et ce n'est pas du
rangement.

`process.exit()` apres une lecture au clavier declenche l'assertion libuv
decrite plus haut. Remplacer chaque `exit` par un `exitCode` ne suffit
pas : le code CONTINUERAIT, et la branche 403 irait lire une reponse qui
n'existe pas. Il faut pouvoir SORTIR — donc `return`, donc une fonction.

Le code de sortie est rendu, pose une seule fois a la fin, et le
processus se termine tout seul.
*/
async function rapport() {
  const cle = (cleArg || process.env.CHANGE_DIAG_CLE || '').trim() || (await demanderCle())
  if (!cle) {
    console.log('\n❌ Aucune cle saisie. /diag est ferme sans elle.')
    return 2
  }

  /*
  L'AIGUILLAGE EST APRÈS LA SAISIE DE LA CLÉ, à dessein : les deux
  questions la demandent, et la demander deux fois pour un outil qui
  répond à l'une ou à l'autre serait absurde.
  */
  if (plages !== null) return await rapportPlages(cle)

  const res = await interroger('/diag', cle)
  if (res.code !== undefined) return res.code
  const reponse = res.reponse

  if (tout) {
    console.log(JSON.stringify(reponse, null, 2))
    return 0
  }

  const v = reponse?.version ?? reponse?.sondes?.version ?? null
  console.log(`Version : ${v ?? 'illisible'}`)

  const s = reponse?.sondes?.separationCanaux ?? reponse?.separationCanaux
  if (!s) {
    console.log('\n⚠️  Ce relais ne rend pas `separationCanaux`.')
    console.log('   Elle est arrivee en 107 : deploie et relance.')
    console.log('     cd tools/relais-cours')
    console.log('     npx wrangler deploy --config wrangler.toml')
    return 1
  }

  const apk = await botDeLApk()

  /*
  QUEL BOT ECRIT LES ORDRES ? Celui de `TG_CHANGE_TOKEN`, ou celui de
  `TG_ADMIN_TOKEN` quand la premiere manque — `worker.js:4033` fait
  `TG_CHANGE_TOKEN || TG_ADMIN_TOKEN`. On suit le MEME repli ici, sinon
  on comparerait l'APK a un bot qui n'ecrit rien.
  */
  const botOrdres = s.changeur?.pose ? s.changeur : s.admin

  const etat = (c, nom, note) => {
    const valeur =
      !c?.pose ? 'ABSENTE'
      : c.valide === false ? `refusee par Telegram — ${c.raison}`
      : `@${c.bot}`
    return `${nom.padEnd(27)} : ${valeur}${note ? `   ← ${note}` : ''}`
  }

  console.log('\n' + '─'.repeat(74))
  console.log(etat(apk, 'APK  telegram.admin.token', 'distribue dans les telephones'))
  console.log(etat(s.changeur, 'Worker TG_CHANGE_TOKEN', 'porte les ordres a executer'))
  console.log(etat(s.admin, 'Worker TG_ADMIN_TOKEN',
    s.admin?.pose ? 'tableau de bord' : 'absente : repli sur TG_CHANGE_TOKEN'))
  console.log('─'.repeat(74))

  /*
  LA QUESTION EST POSEE EN TOUTES LETTRES, parce que c'est la seule
  qu'on vient poser. « Meme bot : OUI » obligeait a se rappeler pourquoi
  ca comptait ; la phrase le porte.
  */
  const atteint =
    apk.id && botOrdres?.id ? apk.id === botOrdres.id : null
  console.log(
    "Le jeton de l'APK atteint-il le canal des ordres ?  " +
    (atteint === null ? 'INDETERMINABLE' : atteint ? 'OUI' : 'non')
  )
  if (s.memeGroupe !== null) {
    console.log(
      `Les deux groupes Telegram sont-ils distincts ?      ${s.memeGroupe ? 'NON' : 'oui'}`
    )
  }
  console.log('─'.repeat(74))

  if (atteint === true) {
    console.log('\n🔴 LE JETON COMPILE DANS L APK EST CELUI QUI ECRIT LES ORDRES.')
    console.log('')
    /*
    LA PREMIERE VERSION DE CE TEXTE SURESTIMAIT LE RISQUE.

    Elle disait « il peut poster dans le canal un message de la forme
    d'un ordre ». Mesure faite : `TG_CHANGE_CHAT` n'est PAS dans l'APK,
    il ne vit que sur le Worker. Il manque donc l'identifiant du groupe.

    Une alerte exageree se paie la fois suivante : celui qui verifie et
    trouve moins grave que ce qu'on lui annoncait croit moins la
    prochaine. On separe donc ce qui est CERTAIN de ce qui demande un
    cran de plus.
    */
    console.log('   CERTAIN : l APK porte AUSSI telegram.admin.chat. Qui l ouvre a')
    console.log('   donc le jeton ET l identifiant du groupe d administration. Il')
    console.log('   peut y ecrire sous l identite du bot — faux depot, faux crash,')
    console.log('   faux avis. C est la que tu lis ton monitoring : un message')
    console.log('   force y est une consigne que tu lis et que rien ne distingue.')
    console.log('')
    console.log('   PAS DIRECT : le groupe des ORDRES (TG_CHANGE_CHAT) n est pas')
    console.log('   dans l APK, il ne vit que sur le Worker. Forger un ordre')
    console.log('   demande d obtenir cet identifiant d abord — un cran de plus,')
    console.log('   pas une barriere.')
    console.log('')
    console.log('   A CORRIGER QUAND MEME, pour une raison simple : un jeton')
    console.log('   public est administrateur des deux groupes, dont celui ou')
    console.log('   passent les ordres. Rien ne justifie que ca dure.')
    console.log('')
    console.log('   CE QU IL FAUT FAIRE, dans cet ordre :')
    console.log('     1. @BotFather → /newbot → un bot NEUF pour le tableau de')
    console.log('        bord. Son jeton va dans TG_ADMIN_TOKEN du Worker.')
    console.log('     2. L ajouter au groupe d administration.')
    console.log('     3. RETIRER le jeton de l APK plutot que de le remplacer :')
    console.log('        depuis le formulaire d avis, le relais sait ecrire dans')
    console.log('        Telegram a la place du telephone. Un jeton qui n est pas')
    console.log('        dans l APK ne fuit jamais. C est le seul correctif qui')
    console.log('        ne se repose pas la question a la version suivante.')
    console.log('     4. Reconstruire et redistribuer.')
    console.log('     5. SEULEMENT APRES : @BotFather → /revoke sur l ancien.')
    console.log('        Revoquer avant couperait le monitoring des installations')
    console.log('        en service, qui portent encore l ancien jeton.')
    console.log('     6. Relancer cet outil : il doit afficher « non ».')
    console.log('')
    return 1
  }

  if (atteint === false) {
    console.log('\n✅ Non : le jeton de l APK est un autre bot que celui des ordres.')
    console.log('   Il reste lisible dans l APK et peut ecrire dans le canal')
    console.log('   d administration — du bruit, pas de l argent. A regenerer')
    console.log('   quand tu veux, sans urgence.')
    console.log('')
    return 0
  }

  console.log('\n⚠️  Indeterminable.')
  if (!apk.pose) {
    console.log('   `telegram.admin.token` est absent de local.properties sur')
    console.log('   CETTE machine. Si les APK distribues ont ete compiles ici,')
    console.log('   ils ne portent aucun jeton et il n y a rien a corriger.')
    console.log('   Sinon, la question reste ouverte : relance depuis la machine')
    console.log('   qui a produit les APK en circulation.')
  } else if (apk.valide === false) {
    console.log(`   Le jeton de l APK a ete refuse par Telegram : ${apk.raison}.`)
    console.log('   Un jeton revoque ne vaut plus rien, ce qui est une bonne')
    console.log('   nouvelle — verifie seulement que le monitoring fonctionne.')
  }
  console.log('')
  return 1
}

process.exitCode = await rapport()
