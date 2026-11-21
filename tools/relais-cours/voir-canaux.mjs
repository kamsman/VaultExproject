/*
═══════════════════════════════════════════════════════════════════════════
LES DEUX CANAUX TELEGRAM SONT-ILS LE MÊME BOT ?
═══════════════════════════════════════════════════════════════════════════

    cd tools/relais-cours
    node voir-canaux.mjs
      └── demande la clé de diagnostic sans l'afficher, puis répond

    node voir-canaux.mjs --tout
      └── imprime tout /diag, pas seulement cette réponse

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

if (!relais.startsWith('https://')) {
  console.log('\n❌ Adresse du relais introuvable.')
  console.log('   Lance la commande depuis le depot, ou passe l adresse en argument.')
  process.exit(2)
}

console.log(`\nRelais : ${relais}`)

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

  let reponse
  try {
    const r = await fetch(`${relais}/diag?cle=${encodeURIComponent(cle)}`)
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
      return 1
    }
    if (!r.ok) {
      console.log(`\n❌ HTTP ${r.status} — ${brut.slice(0, 200)}`)
      return 1
    }
    reponse = JSON.parse(brut)
  } catch (e) {
    const cause = e?.cause?.code || e?.code || e?.message || 'inconnue'
    console.log(`\n❌ reseau : ${cause}`)
    return 1
  }

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

  /*
  LES DEUX LIGNES SONT ALIGNEES, et l'annotation est a DROITE.

  Collee au nom — « TG_ADMIN_TOKEN (compile dans l'APK) : @bot » — elle
  decalait la seconde ligne et cassait la comparaison, qui est la seule
  chose qu'on vient faire ici : lire deux noms de bots l'un sous l'autre.
  */
  const etat = (c, nom, note) => {
    const valeur =
      !c?.pose ? 'ABSENTE'
      : c.valide === false ? `refusee par Telegram — ${c.raison}`
      : `@${c.bot}`
    return `${nom.padEnd(15)} : ${valeur}${note ? `   ← ${note}` : ''}`
  }

  console.log('\n' + '─'.repeat(70))
  console.log(etat(s.changeur, 'TG_CHANGE_TOKEN', 'porte les ordres a executer'))
  console.log(etat(s.admin, 'TG_ADMIN_TOKEN', "compile dans l'APK, donc public"))
  console.log('─'.repeat(70))
  console.log(
    `Meme bot    : ${s.memeBot === null ? 'indeterminable' : s.memeBot ? 'OUI' : 'non'}`
  )
  console.log(
    `Meme groupe : ${s.memeGroupe === null ? 'indeterminable' : s.memeGroupe ? 'OUI' : 'non'}`
  )
  console.log('─'.repeat(70))

  const grave = s.memeBot === true || !s.changeur?.pose || s.memeGroupe === true
  console.log(`\n${grave ? '🔴' : s.memeBot === false ? '✅' : '⚠️ '} ${s.verdict}`)

  if (grave) {
    console.log('\nCE QU IL FAUT FAIRE, dans cet ordre :')
    console.log('  1. Creer un bot NEUF chez @BotFather pour le tableau de bord')
    console.log('     admin, et le mettre dans TG_ADMIN_TOKEN du Worker.')
    console.log('  2. Revoquer l ancien jeton (/revoke chez @BotFather) : tant')
    console.log("     qu il vit, les APK deja distribues le portent en clair.")
    console.log('  3. Verifier que TG_CHANGE_TOKEN et TG_CHANGE_CHAT sont posees,')
    console.log('     pour que le canal des ordres ne depende plus du repli.')
    console.log('  4. Relancer cet outil : il doit afficher « Deux bots distincts ».')
    console.log('')
    return 1
  }
  console.log('')
  return 0
}

process.exitCode = await rapport()
