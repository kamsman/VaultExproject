#!/usr/bin/env node
/*
═══════════════════════════════════════════════════════════════════════════
LE NUMERO DE VERSION EST-IL A JOUR ?
═══════════════════════════════════════════════════════════════════════════

    node tools/relais-cours/verifier-version.mjs

POURQUOI CE SCRIPT EXISTE. `VERSION` dans worker.js est le seul moyen de
savoir quel code tourne reellement : /sante le rend, wrangler ne rend
aucune empreinte, et l'identifiant de version de Cloudflare ne dit rien du
contenu.

Il est reste a 73 pendant DOUZE commits — l'analyseur de SMS, les cles de
reference, l'alerte de silence, les boutons de copie. Pendant tout ce
temps, /sante repondait « 73 », et un deploiement reussi ressemblait
exactement a un deploiement oublie. C'est le pire etat possible pour un
indicateur : il affirme quelque chose de faux au moment precis ou l'on
vient verifier.

COMMENT IL TRANCHE. Il demande a git deux choses : le dernier commit qui a
touche worker.js, et le dernier qui a touche la ligne `const VERSION`. Si
le premier est plus recent, le numero est en retard, et il dit de combien.

CE QU'IL NE SAIT PAS FAIRE. Il ne dit pas si le deploiement a eu lieu —
seulement si le numero qu'il porterait serait juste. Pour le reste, il y a
/sante, et c'est tout l'interet de garder ce numero exact.

Code de sortie 1 si le numero est en retard : utilisable comme garde.
═══════════════════════════════════════════════════════════════════════════
*/
import { execFileSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const ici = dirname(fileURLToPath(import.meta.url))
const fichier = join(ici, 'worker.js')

function git(...args) {
  try {
    return execFileSync('git', args, { cwd: ici, encoding: 'utf8' }).trim()
  } catch (_) {
    return ''
  }
}

const source = readFileSync(fichier, 'utf8')
const trouve = source.match(/^const VERSION = (\d+)$/m)
if (!trouve) {
  console.log('\n  ERREUR : ligne `const VERSION = <nombre>` introuvable dans worker.js.')
  console.log('  Ce n’est pas une reponse sur la version : c’est ce script a corriger.\n')
  process.exit(2)
}
const version = Number(trouve[1])

// `-S` cherche les commits qui ont CHANGE le nombre d'occurrences de la
// chaine : c'est exactement « quand ce numero a-t-il bouge ».
const commitVersion = git('log', '-1', '--format=%H', '-S', 'const VERSION = ', '--', 'worker.js')
const commitFichier = git('log', '-1', '--format=%H', '--', 'worker.js')

console.log('')
console.log(`  worker.js annonce la version ${version}`)

if (!commitVersion || !commitFichier) {
  console.log('  git n’a pas repondu — rien a conclure, et surtout pas que tout va bien.\n')
  process.exit(2)
}

if (commitVersion === commitFichier) {
  console.log('  Le numero a bouge dans le dernier commit qui touche ce fichier. A jour.\n')
  process.exit(0)
}

const retard = git('rev-list', '--count', `${commitVersion}..HEAD`, '--', 'worker.js')
const n = Number(retard) || 0
console.log('')
console.log(`  ⚠  EN RETARD DE ${n} COMMIT(S).`)
console.log('')
console.log(`  worker.js a change ${n} fois depuis que VERSION a bouge. /sante`)
console.log(`  repondra donc « ${version} » pour un code qui n'est plus celui-la,`)
console.log('  et un deploiement reussi ressemblera a un deploiement oublie.')
console.log('')
console.log(`  A FAIRE : passer VERSION a ${version + 1} dans worker.js, puis deployer.`)
console.log('')
process.exit(1)
