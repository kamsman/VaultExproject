/*
═══════════════════════════════════════════════════════════════════════════
LIRE UNE PILE DE CRASH OBFUSQUÉE
═══════════════════════════════════════════════════════════════════════════

    node tools/retracer.mjs "j.T:1288 ← j.W:9 ← j.R:164"
    node tools/retracer.mjs "j.T:1288" --mapping chemin/vers/mapping.txt

Rend les noms d'origine :

    j.T:1288  →  com.vaultex.ui.viewmodel.ChangeViewModel.calculer:842

─── POURQUOI CET OUTIL EXISTE ────────────────────────────────────────────

Un rapport de crash arrive par Telegram avec des noms comme « j.T:1288 ».
Ce ne sont PAS des classes de bibliothèque : ce sont les nôtres, renommées
par R8. Je l'avais cru l'inverse — `enableR8.fullMode=false` ne garde pas
la hiérarchie des paquets, contrairement à ce que j'avais affirmé — et
cette erreur a fait écarter la bonne piste pendant deux versions.

Sans `mapping.txt`, « j.T:1288 » ne veut rien dire et ne voudra jamais
rien dire. AVEC, il désigne une ligne précise.

─── LE FICHIER mapping.txt EST PÉRISSABLE ────────────────────────────────

Il est RÉÉCRIT à chaque `assembleRelease`. Celui qui est sur le disque
décrit la DERNIÈRE version construite — pas celle qui tourne chez les
223 utilisateurs.

Un crash remonté d'une version plus ancienne se décode avec le
mapping.txt de CETTE version-là, ou ne se décode pas du tout. D'où
`tools/mappings/<version>/mapping.txt`, que cet outil cherche en premier
quand le rapport porte un numéro de version.

─── LE FORMAT, EN DEUX LIGNES ────────────────────────────────────────────

    com.vaultex.ui.ChangeViewModel -> j.T:
        1288:1290:void calculer():842:844 -> a

Une classe, puis ses membres indentés. Pour une méthode :

    <debutObfusque>:<finObfusque>:<type> <nom>(<args>)[:<debutOrigine>] -> <nouveauNom>

La ligne 1288 tombe dans l'intervalle 1288-1290, donc la méthode est
`calculer`, et la ligne d'origine se déduit du décalage.
═══════════════════════════════════════════════════════════════════════════
*/

import { readFileSync, existsSync } from 'node:fs'

/** Les classes du mapping : obfusqué → { origine, methodes[] }. */
function lireMapping(texte) {
  const classes = new Map()
  let courante = null
  for (const brute of texte.split('\n')) {
    if (!brute || brute.startsWith('#')) continue
    if (!brute.startsWith(' ') && !brute.startsWith('\t')) {
      // « com.exemple.Foo -> a.b: »
      const m = brute.match(/^(\S+) -> (\S+):$/)
      if (m) {
        courante = { origine: m[1], methodes: [] }
        classes.set(m[2], courante)
      } else {
        courante = null
      }
      continue
    }
    if (!courante) continue
    const l = brute.trim()
    /*
    ON NE GARDE QUE LES MEMBRES AVEC UN INTERVALLE DE LIGNES. Les champs
    s'ecrivent « java.lang.String nom -> a », sans chiffres : ils ne
    peuvent pas porter un numero de ligne, donc ils ne nous servent pas.
    */
    const m = l.match(/^(\d+):(\d+):(.+?) -> (\S+)$/)
    if (!m) continue
    const [, debut, fin, corps, nouveau] = m
    // `corps` vaut « void calculer() » ou « void calculer():842 » ou
    // « void calculer():842:844 ». Les deux-points finaux portent la
    // ligne d'origine ; sans eux, l'origine EST le numero obfusque.
    const sig = corps.match(/^(.*?)(?::(\d+))?(?::(\d+))?$/)
    /*
    ═══════════════════════════════════════════════════════════════════
    DEUX FORMES, ET ELLES NE SE LISENT PAS PAREIL
    ═══════════════════════════════════════════════════════════════════

        1288:1295:void f():842:849     intervalle → correspondance LINEAIRE
        1:12:void f():164              une seule ligne → TOUT retombe sur 164

    La seconde forme est celle de l'INLINING : R8 a replie un bloc
    entier sur son point d'appel, et les douze lignes obfusquees
    designent la meme ligne d'origine.

    Traiter la seconde comme la premiere donne une ligne FAUSSE —
    mesure faite sur un mapping fabrique : « 164 » devenait « 172 ».
    Un retracage qui se trompe de huit lignes est pire qu'un retracage
    absent : on part lire le mauvais bout de code en croyant savoir.
    ═══════════════════════════════════════════════════════════════════
    */
    const oDebut = sig[2] !== undefined ? Number(sig[2]) : null
    const oFin = sig[3] !== undefined ? Number(sig[3]) : null
    courante.methodes.push({
      debut: Number(debut),
      fin: Number(fin),
      // Le type de retour n'apprend rien et noie le nom : « void
      // calculer() » se lit, « ChangeViewModel.void calculer() » non.
      signature: sig[1].replace(/^\S+\s+/, ''),
      origine: oDebut,
      lineaire: oFin !== null && oFin !== oDebut,
      nouveau,
    })
  }
  return classes
}

/** « j.T:1288 » → texte lisible. */
function retracerCadre(cadre, classes) {
  /*
  DEUX FORMES ACCEPTEES, parce que les deux arrivent :

      j.T:1288                        le rapport Telegram
      j.T.a(Unknown Source:1288)      une pile collee depuis logcat

  Je pretendais deja accepter la seconde et ne le faisais pas — la
  parenthese fermante faisait echouer l'expression. Teste, cette fois.
  */
  const brut = cadre.trim()
  const logcat = brut.match(/^(\S+?)\.\w+\((?:[^:)]*:)?(\d+)\)$/)
  const m = logcat ?? brut.match(/^(.+?)[:.](\d+)$/)
  if (!m) return `${cadre}  (format non reconnu)`
  const [, nom, ligneTxt] = m
  const ligne = Number(ligneTxt)

  /*
  LE CADRE PEUT PORTER UNE METHODE : « j.T.a:1288 ». Le rapporteur de
  crash n'en envoie pas, mais une pile collee depuis logcat, si. On
  essaie la classe entiere, puis la classe sans son dernier segment.
  */
  /*
  ═══════════════════════════════════════════════════════════════════════
  LE NOM DE LA MÉTHODE EST LA MOITIÉ DE L'INFORMATION, ET JE LE JETAIS
  ═══════════════════════════════════════════════════════════════════════

  MESURÉ le 26 novembre 2026. Pour « s1.j.W:9 », cet outil rendait

      ComposerImpl.<init>(Applier, CompositionContext, ...):1249

  Une méthode nommée `W` à la ligne 9, qui tomberait dans un constructeur
  à la ligne 1249. Impossible, et affirmé sans la moindre réserve.

  La cause tenait en une ligne :

      cl.methodes.find((x) => ligne >= x.debut && ligne <= x.fin)

  La recherche portait sur le SEUL intervalle de lignes. C'était juste
  tant que le rapport n'envoyait que la classe — il n'y avait pas de nom
  de méthode à confronter. Depuis que `AdminBot` envoie
  « classe.méthode », l'information la plus discriminante arrivait et
  était ignorée : l'outil rendait la première méthode dont la plage
  contenait le numéro, au hasard.

  UN OUTIL DE DIAGNOSTIC QUI SE TROMPE AVEC ASSURANCE EST PIRE QU'UN
  OUTIL QUI DIT « JE NE SAIS PAS ». Celui-ci a envoyé chercher dans un
  constructeur de Compose.

  ─── CHAQUE CANDIDAT PORTE DÉSORMAIS CE QU'ON SAIT DE LUI ──────────────

  `{ classe, methode }` — et `methode: null` veut dire « le cadre ne
  nommait pas la méthode », pas « n'importe laquelle convient ». La
  distinction est exactement celle qui manquait.
  ═══════════════════════════════════════════════════════════════════════
  */
  const candidats = [{ classe: nom, methode: null }]
  const point = nom.lastIndexOf('.')
  if (point > 0) {
    candidats.push({ classe: nom.slice(0, point), methode: nom.slice(point + 1) })
  }

  /*
  ═══════════════════════════════════════════════════════════════════════
  LA TROISIÈME FORME : CELLE QUE LE RAPPORTEUR ENVOYAIT VRAIMENT
  ═══════════════════════════════════════════════════════════════════════

  MESURÉ le 26 novembre 2026, avec le mapping de la version qui a planté.
  Le rapport disait « j.T:1288 » et cet outil répondait « « j » existe
  (16 classes) mais pas « j.T » ». Les deux avaient raison, et pourtant
  rien ne se décodait.

  La cause était entre les deux. `AdminBot` formatait ses cadres ainsi :

      "${it.className.substringAfterLast('.')}.${it.methodName}:${line}"

  `substringAfterLast('.')` JETTE LE PAQUET. « j.T:1288 » ne désigne donc
  pas la classe `j.T` : c'est la classe dont le nom court est `j`, la
  méthode `T`, ligne 1288. Le paquet, lui, est perdu — et c'est justement
  ce qui identifie la classe dans le mapping.

  ─── ON PEUT LE RETROUVER QUAND MÊME ──────────────────────────────────

  Un nom court seul est ambigu : seize classes s'appellent `j` quelque
  part. Mais le NOM DE MÉTHODE et le NUMÉRO DE LIGNE, eux, ne le sont
  presque jamais ensemble. On cherche donc toutes les classes dont le
  dernier segment vaut `j`, et parmi elles celle qui possède une méthode
  obfusquée `T` couvrant la ligne 1288.

  S'il en reste plusieurs, on les rend TOUTES plutôt que d'en choisir
  une : un outil de diagnostic qui tranche au hasard est pire qu'un outil
  qui montre l'ambiguïté.

  `AdminBot` envoie désormais le nom complet, donc cette recherche ne
  servira plus aux crashes à venir. Elle reste pour les anciens rapports,
  qui sont les seuls qu'on ait.
  ═══════════════════════════════════════════════════════════════════════
  */
  const pointNom = nom.lastIndexOf('.')
  if (pointNom > 0) {
    const court = nom.slice(0, pointNom)
    const methode = nom.slice(pointNom + 1)
    const trouves = []
    for (const [obfusque, cl] of classes) {
      const dernier = obfusque.slice(obfusque.lastIndexOf('.') + 1)
      if (dernier !== court) continue
      for (const x of cl.methodes) {
        if (x.nouveau !== methode) continue
        if (ligne < x.debut || ligne > x.fin) continue
        const d =
          x.origine === null ? ligne
          : x.lineaire ? x.origine + (ligne - x.debut)
          : x.origine
        trouves.push(`${cl.origine}.${x.signature}:${d}`)
      }
    }
    if (trouves.length === 1) {
      return `${cadre}  →  ${trouves[0]}  (paquet reconstitue)`
    }
    if (trouves.length > 1) {
      return `${cadre}  →  ${trouves.length} correspondances possibles :\n` +
        trouves.map((t) => `                 ${t}`).join('\n')
    }
  }

  for (const c of candidats) {
    const cl = classes.get(c.classe)
    if (!cl) continue
    /*
    QUAND LE CADRE NOMME LA MÉTHODE, ON L'EXIGE. Deux méthodes d'une même
    classe partagent souvent une plage de lignes — R8 les replie — et
    choisir par la seule plage revient à tirer au sort.

    Quand il ne la nomme pas (`methode === null`), la plage est tout ce
    qu'on a, et c'est le comportement d'avant.
    */
    const eligibles =
      c.methode === null
        ? cl.methodes
        : cl.methodes.filter((x) => x.nouveau === c.methode)
    const meth = eligibles.find((x) => ligne >= x.debut && ligne <= x.fin)
    if (!meth) {
      /*
      ON DIT LAQUELLE DES DEUX CHOSES MANQUE. « La classe est connue mais
      pas la ligne » envoyait chercher un mauvais mapping, alors que le
      vrai cas est souvent « cette classe n'a pas de methode de ce nom ».
      */
      if (c.methode !== null && eligibles.length === 0) {
        return `${cadre}  →  ${cl.origine}  (aucune methode « ${c.methode} » dans cette classe)`
      }
      return `${cadre}  →  ${cl.origine}  (ligne ${ligne} hors des intervalles connus)`
    }
    const dOrigine =
      meth.origine === null ? ligne
      : meth.lineaire ? meth.origine + (ligne - meth.debut)
      : meth.origine
    return `${cadre}  →  ${cl.origine}.${meth.signature}:${dOrigine}`
  }
  /*
  ═══════════════════════════════════════════════════════════════════════
  « INCONNU » NE SUFFIT PAS : IL FAUT DIRE POURQUOI
  ═══════════════════════════════════════════════════════════════════════

  Deux causes, et elles demandent deux gestes opposes :

    · le mapping vient d'un AUTRE BUILD — alors AUCUNE classe du paquet
      cherche n'y figure, et il faut retrouver l'archive de la bonne
      version ;
    · le paquet existe mais pas cette classe-la — alors le mapping est
      probablement le bon et c'est la pile qui a ete mal recopiee.

  Rendre « inconnu » dans les deux cas laisse chercher au hasard.
  ═══════════════════════════════════════════════════════════════════════
  */
  const paquet = nom.includes('.') ? nom.slice(0, nom.indexOf('.')) : null
  if (paquet) {
    const voisines = [...classes.keys()].filter((c) => c.startsWith(paquet + '.'))
    if (voisines.length === 0) {
      return `${cadre}  →  le paquet « ${paquet} » n'existe pas dans ce mapping` +
        `  (il vient d'un AUTRE build)`
    }
    return `${cadre}  →  « ${paquet} » existe (${voisines.length} classes) mais pas « ${nom} »`
  }
  return `${cadre}  →  inconnu de ce mapping.txt`
}

const args = process.argv.slice(2)
const iMap = args.indexOf('--mapping')
const mappingArg = iMap >= 0 ? args[iMap + 1] : null
const iVer = args.indexOf('--version')
/*
`indexOf` REND -1 QUAND LE DRAPEAU EST ABSENT, et -1 + 1 font 0.

Ecrit sans garde, le filtre devient « i !== 0 » et mange le PREMIER
argument — c'est-a-dire la pile elle-meme. L'outil repondait alors par
son mode d'emploi, comme si on l'avait lance sans rien.

Le defaut etait deja la pour `--mapping` ; il ne s'etait jamais vu
parce que je passais toujours ce drapeau en essai.
*/
const retire = new Set()
for (const i of [iMap, iVer]) if (i >= 0) { retire.add(i); retire.add(i + 1) }
const pile = args.filter((_, i) => !retire.has(i)).join(' ').trim()

if (!pile) {
  console.log('\nUsage :')
  console.log('  node tools/retracer.mjs "j.T:1288 ← j.W:9 ← j.R:164"')
  console.log('  node tools/retracer.mjs "j.T:1288" --mapping tools/mappings/1.0.1660/mapping.txt')
  process.exit(2)
}

/*
L'ARCHIVE EST CHERCHEE AVANT LE BUILD COURANT, et c'est l'ordre qui
compte. `app/build/.../mapping.txt` decrit la DERNIERE version
construite ; un crash venu d'une version plus ancienne s'y decoderait
en silence et en donnant de mauvaises lignes.

Un retracage faux est pire qu'un retracage absent : on part lire le
mauvais bout de code en croyant savoir.
*/
const versionArg = (() => {
  const i = args.indexOf('--version')
  if (i >= 0) return args[i + 1]
  // « Crash VaultEx v1.0.1660+179ec8d » colle tel quel : on le reconnait.
  const m = pile.match(/\bv?(\d+\.\d+\.\d+)/)
  return m ? m[1] : null
})()

const candidats = [
  mappingArg,
  versionArg ? `tools/mappings/${versionArg}/mapping.txt` : null,
  'app/build/outputs/mapping/release/mapping.txt',
].filter(Boolean)
const chemin = candidats.find((c) => existsSync(c))

if (chemin && versionArg && !chemin.includes(`/${versionArg}/`) && !mappingArg) {
  console.log(`\n⚠️  Le rapport dit v${versionArg}, mais aucune archive`)
  console.log(`   tools/mappings/${versionArg}/mapping.txt n'existe.`)
  console.log('   On retombe sur le mapping du DERNIER build. Si ce n\'est pas')
  console.log('   la meme version, les lignes rendues seront fausses.')
}

if (!chemin) {
  console.log('\n❌ mapping.txt introuvable.')
  console.log(`   Cherché : ${candidats.join(', ')}`)
  console.log('')
  console.log('   Il est produit par `./gradlew assembleRelease` et REECRIT a')
  console.log('   chaque build. Si la version qui a plante n\'est plus celle du')
  console.log('   disque, ce fichier ne peut plus la decoder — et personne ne le')
  console.log('   pourra jamais.')
  process.exit(1)
}

console.log(`\nmapping : ${chemin}`)
const classes = lireMapping(readFileSync(chemin, 'utf8'))
console.log(`classes : ${classes.size}\n`)

/*
LE SEPARATEUR DU RAPPORT EST « ← », MAIS UNE PILE COLLEE DEPUIS LOGCAT
emploie « at ... » sur des lignes separees. On accepte les deux plutot
que d'imposer un format a quelqu'un qui vient de recevoir un crash.
*/
const lignes = []
for (const cadre of pile.split(/\s*(?:←|<-|\n)\s*/).filter(Boolean)) {
  lignes.push(retracerCadre(cadre.replace(/^\s*at\s+/, ''), classes))
}
for (const l of lignes) console.log('  ' + l)

/*
QUAND RIEN N'A ETE TROUVE, ON MONTRE A QUOI RESSEMBLENT LES NOMS DE CE
MAPPING. C'est la facon la plus rapide de voir qu'on cherche « j.T »
dans un fichier qui n'emploie pas du tout cette forme.
*/
if (lignes.every((l) => /n'existe pas|inconnu|pas « /.test(l))) {
  const apercu = [...classes.keys()].filter((c) => c.length <= 6).slice(0, 8)
  console.log('')
  console.log("  Aucun cadre n'a ete retrouve. Les noms obfusques de ce mapping")
  console.log('  ressemblent a :')
  console.log('    ' + (apercu.length ? apercu.join('  ') : '(aucun nom court — paquets peut-etre conserves)'))
  console.log('')
  console.log('  Si la forme ne correspond pas a ta pile, ce mapping vient')
  console.log("  d'un autre build. Celui de la version qui a plante est le seul")
  console.log('  qui puisse la decoder.')
}
console.log('')
