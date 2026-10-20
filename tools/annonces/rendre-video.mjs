#!/usr/bin/env node
/*
═══════════════════════════════════════════════════════════════════════════
LA VIDEO EST RENDUE DEPUIS LA PAGE, PAS REECRITE A COTE
═══════════════════════════════════════════════════════════════════════════

`pub-vaultex-15s.html` reste la seule source du dessin. Ce script l'ouvre
dans Chromium, fige l'animation image par image, et donne les images a
ffmpeg.

L'alternative aurait ete de redessiner les cinq scenes dans un generateur
d'images — GDI+, canvas, peu importe. Deux descriptions du meme dessin
divergent toujours : on corrige un mot dans la page, la video garde
l'ancien, et personne ne le voit avant que la pub soit partie.

─── POURQUOI ON NE FILME PAS L'ECRAN ────────────────────────────────────

Un enregistrement en temps reel depend de la charge de la machine : sous
charge, le navigateur saute des images, et l'animation accelere ou saccade
sans qu'on puisse le rattraper apres coup.

Ici on ne filme pas, on ECHANTILLONNE : pour chaque image, on pose
`currentTime` a l'instant exact voulu et on capture. Les 450 images sont
separees par exactement 33,33 ms, quelle que soit la lenteur de la
machine. Le rendu est reproductible : deux executions donnent le meme
fichier.

─── LA DERNIERE IMAGE N'EST PAS RENDUE, ET C'EST VOULU ──────────────────

La boucle de la page se referme sur elle-meme : l'instant 15 s est
identique a l'instant 0. Rendre les deux donnerait une image repetee a
chaque tour — un micro-arret visible quand WhatsApp reboucle le statut.
On rend donc 0 a 14,967 s, soit 450 images, et la lecture suivante
enchaine sans couture.

─── LE FORMAT EST CELUI QUE WHATSAPP ACCEPTE ────────────────────────────

H.264 en yuv420p, profil high, et `+faststart`. Le yuv420p n'est pas un
detail : en yuv444p ou yuv420p10, le fichier est valide et WhatsApp le
refuse, ou l'affiche vert. Les dimensions sont paires, comme H.264
l'exige.

UTILISATION

    node tools/annonces/rendre-video.mjs

    --ips <n>       images par seconde (defaut 30)
    --duree <s>     duree de la boucle (defaut 15)
    --sortie <f>    fichier de sortie
    --qualite <n>   CRF, plus petit = meilleur (defaut 20)

SUR UNE MACHINE SANS PLAYWRIGHT NI FFMPEG

    npm install playwright
    npx playwright install chromium

et ffmpeg sur le PATH, ou son chemin dans la variable FFMPEG.
═══════════════════════════════════════════════════════════════════════════
*/

import { spawn } from 'node:child_process'
import { existsSync, copyFileSync, mkdirSync, writeFileSync } from 'node:fs'
import { readFile } from 'node:fs/promises'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { tmpdir } from 'node:os'

const ici = dirname(fileURLToPath(import.meta.url))
const racine = resolve(ici, '..', '..')

function option(nom, defaut) {
  const i = process.argv.indexOf('--' + nom)
  return i !== -1 && process.argv[i + 1] ? process.argv[i + 1] : defaut
}

const IPS = Number(option('ips', 30))
const DUREE = Number(option('duree', 15))
const CRF = String(option('qualite', 20))
const SORTIE = resolve(option('sortie', join(ici, 'pub-vaultex-15s.mp4')))
const SOURCE = join(ici, 'pub-vaultex-15s.html')
const LARGEUR = 1080
const HAUTEUR = 1920

/*
Les deux logos vivent dans les ressources de l'application, pas a cote de
la page. Le navigateur les demande en chemin relatif : on monte donc un
dossier de rendu ou la page et ses images se touchent, au lieu de
reecrire les chemins dans le HTML — un chemin reecrit ici serait un
troisieme endroit a tenir a jour.
*/
const IMAGES = [
  ['logorond.png', 'app/src/main/res/drawable/logorond.png'],
  ['logo_tex.png', 'app/src/main/res/drawable/logo_tex.png']
]

function cheminFfmpeg() {
  if (process.env.FFMPEG) return process.env.FFMPEG
  const paquet = join(
    process.env.HOME || '/root',
    '.local/lib/python3.11/site-packages/imageio_ffmpeg/binaries',
    'ffmpeg-linux-x86_64-v7.0.2'
  )
  if (existsSync(paquet)) return paquet
  return 'ffmpeg'
}

async function main() {
  if (!existsSync(SOURCE)) {
    console.error('Introuvable : ' + SOURCE)
    process.exit(1)
  }

  /*
  NODE_PATH NE SERT A RIEN ICI. Un module ES se resout en remontant les
  dossiers `node_modules` depuis le fichier, et ignore NODE_PATH — c'est
  une variable du resolveur CommonJS. Un Playwright installe globalement
  reste donc introuvable par un simple `import 'playwright'`.
  On essaie donc aussi les emplacements globaux, par chemin absolu.
  */
  const pistes = ['playwright']
  if (process.env.PLAYWRIGHT_MODULE) pistes.push(process.env.PLAYWRIGHT_MODULE)
  for (const base of ['/opt/node22/lib/node_modules', '/usr/lib/node_modules',
    '/usr/local/lib/node_modules']) {
    pistes.push(pathToFileURL(join(base, 'playwright', 'index.js')).href)
  }

  let chromium = null
  let derniere = null
  for (const piste of pistes) {
    try {
      const mod = await import(piste)
      chromium = (mod.chromium || mod.default?.chromium) ?? null
      if (chromium) break
    } catch (e) { derniere = e }
  }
  if (!chromium) {
    console.error('Playwright est introuvable. Installe-le dans le projet :')
    console.error('    npm install playwright')
    console.error('    npx playwright install chromium')
    console.error('')
    console.error('Ou donne le chemin du module dans PLAYWRIGHT_MODULE.')
    if (derniere) console.error('Derniere erreur : ' + derniere.message)
    process.exit(1)
  }

  const src = await readFile(SOURCE, 'utf8')
  const iShell = src.indexOf('<div class="shell"')
  const iPanneau = src.indexOf('<div class="panneau">')
  if (iShell < 0 || iPanneau < 0) {
    console.error(
      'La page n\'a plus la structure attendue : il faut <div class="shell"> ' +
      'puis <div class="panneau">. Rien n\'a ete rendu.'
    )
    process.exit(1)
  }
  const tete = src.slice(0, iShell)
  const corps = src.slice(iShell, iPanneau)

  /*
  La page publiee recoit du service un squelette (doctype, charset, remise
  a zero des marges). En local il faut le fournir, sinon le navigateur lit
  le fichier en mode « quirks » et les marges par defaut decalent tout.

  Le cadre passe a 1080x1920 exactement et la scene perd sa mise a
  l'echelle : on capture le dessin a sa taille native, sans
  reechantillonnage.
  */
  const page_html = `<!doctype html>
<html lang="fr"><head><meta charset="utf-8">
${tete}
<style>
  html, body { margin: 0; padding: 0 !important; background: #000; display: block !important; }
  .shell { display: block !important; place-items: initial !important; }
  .frame {
    width: ${LARGEUR}px !important;
    height: ${HAUTEUR}px !important;
    aspect-ratio: auto !important;
    border-radius: 0 !important;
    box-shadow: none !important;
  }
  .stage { transform: none !important; }
  /* Le rendu n'est pas une lecture : l'animation doit tourner meme si la
     machine de rendu demande la reduction des animations. */
  @media (prefers-reduced-motion: reduce) {
    .stage * { animation-play-state: running !important; }
  }
</style>
</head><body>
${corps}
</body></html>`

  const dossier = join(tmpdir(), 'vaultex-rendu-' + process.pid)
  mkdirSync(dossier, { recursive: true })
  for (const [nom, depuis] of IMAGES) {
    const chemin = join(racine, depuis)
    if (!existsSync(chemin)) {
      console.error('Logo introuvable : ' + chemin)
      process.exit(1)
    }
    copyFileSync(chemin, join(dossier, nom))
  }
  const fichierRendu = join(dossier, 'rendu.html')
  writeFileSync(fichierRendu, page_html, 'utf8')

  const total = Math.round(DUREE * IPS)
  console.log(`Rendu : ${LARGEUR}x${HAUTEUR}, ${IPS} ips, ${DUREE} s — ${total} images`)

  const navigateur = await chromium.launch()
  const contexte = await navigateur.newContext({
    viewport: { width: LARGEUR, height: HAUTEUR },
    deviceScaleFactor: 1,
    colorScheme: 'dark',
    reducedMotion: 'no-preference'
  })
  const page = await contexte.newPage()
  await page.goto('file://' + fichierRendu, { waitUntil: 'load' })

  // Les polices sont chargees depuis le reseau. Capturer avant leur
  // arrivee donnerait les premieres images dans une fonte de secours,
  // visiblement differente du reste.
  await page.evaluate(() => document.fonts.ready)
  const polices = await page.evaluate(() =>
    document.fonts.check('900 104px Archivo') &&
    document.fonts.check('500 36px "IBM Plex Sans"')
  )
  if (!polices) {
    console.warn('ATTENTION : les polices Google ne se sont pas chargees.')
    console.warn('La video sortira dans une fonte de secours. Verifie le reseau.')
  }

  const nb = await page.evaluate(() => {
    document.getAnimations().forEach((a) => a.pause())
    return document.getAnimations().length
  })
  if (nb === 0) {
    console.error('Aucune animation trouvee dans la page. Rien a rendre.')
    await navigateur.close()
    process.exit(1)
  }
  console.log(`${nb} animations figees.`)

  const ffmpeg = cheminFfmpeg()
  const args = [
    '-y', '-hide_banner', '-loglevel', 'error',
    '-f', 'image2pipe', '-framerate', String(IPS), '-i', '-',
    '-c:v', 'libx264',
    '-preset', 'slow',
    '-crf', CRF,
    '-pix_fmt', 'yuv420p',
    '-profile:v', 'high',
    '-level', '4.0',
    '-movflags', '+faststart',
    '-an',
    SORTIE
  ]
  const enc = spawn(ffmpeg, args, { stdio: ['pipe', 'inherit', 'inherit'] })
  const fini = new Promise((ok, ko) => {
    enc.on('error', ko)
    enc.on('close', (code) =>
      code === 0 ? ok() : ko(new Error('ffmpeg a rendu ' + code))
    )
  })

  for (let i = 0; i < total; i++) {
    const ms = (i * 1000) / IPS
    await page.evaluate((t) => {
      document.getAnimations().forEach((a) => { a.currentTime = t })
      // Deux trames d'attente : poser `currentTime` met l'animation a
      // jour, le compositeur applique les flous et les transformees a la
      // trame suivante. Capturer tout de suite melange deux instants.
      return new Promise((r) =>
        requestAnimationFrame(() => requestAnimationFrame(r))
      )
    }, ms)
    const image = await page.screenshot({ type: 'png' })
    if (!enc.stdin.write(image)) {
      await new Promise((r) => enc.stdin.once('drain', r))
    }
    if ((i + 1) % 50 === 0 || i + 1 === total) {
      process.stdout.write(`\r  ${i + 1}/${total} images`)
    }
  }
  process.stdout.write('\n')

  enc.stdin.end()
  await navigateur.close()
  await fini

  const { statSync } = await import('node:fs')
  const ko = (statSync(SORTIE).size / 1024).toFixed(0)
  console.log(`\nVideo ecrite : ${SORTIE}`)
  console.log(`   ${LARGEUR}x${HAUTEUR}, ${DUREE} s, ${IPS} ips, ${ko} Ko`)
}

main().catch((e) => {
  console.error('\n' + (e && e.message ? e.message : e))
  process.exit(1)
})
