/*
═══════════════════════════════════════════════════════════════════════════
VÉRIFIER UNE BANNIÈRE AVANT DE L'ENVOYER À TOUT LE MONDE
═══════════════════════════════════════════════════════════════════════════

    node tools/annonces/verifier-banniere.mjs tools/annonces/ma-banniere.png
    node tools/annonces/verifier-banniere.mjs https://raw.githubusercontent.com/...

─── POURQUOI ────────────────────────────────────────────────────────────

Une annonce part à TOUS les utilisateurs et ne se rattrape pas. Si la
bannière est trop lourde, au mauvais format, ou aux mauvaises proportions,
on ne l'apprend qu'après — et chacun l'aura vue coupée, ou pas du tout.

Les contraintes ne viennent pas de nous : Android télécharge cette image
sur le réseau mobile de chaque personne, au moment où la notification
arrive. Un fichier d'un mégaoctet sur une connexion lente, c'est une image
qui n'apparaît jamais.

AUCUNE DÉPENDANCE. Les dimensions se lisent dans l'en-tête du fichier —
quelques octets à un emplacement connu — et c'est tout ce dont on a
besoin. Installer une bibliothèque d'images pour lire deux entiers serait
disproportionné, et elle manquerait le jour où l'on en a besoin.
═══════════════════════════════════════════════════════════════════════════
*/

import { readFileSync } from 'node:fs'

const cible = process.argv[2]
if (!cible) {
  console.log('\nUsage :')
  console.log('  node tools/annonces/verifier-banniere.mjs <fichier ou adresse https>\n')
  process.exit(2)
}

/** Les octets de l'image, qu'elle soit sur le disque ou en ligne. */
async function octets(ou) {
  if (ou.startsWith('https://') || ou.startsWith('http://')) {
    const r = await fetch(ou)
    if (!r.ok) {
      console.log(`\n❌ L'adresse repond ${r.status}.`)
      console.log("   Android ne pourra pas la telecharger : l'annonce partira sans banniere.")
      if (ou.includes('raw.githubusercontent.com')) {
        console.log('\n   Sur raw.githubusercontent, un 404 veut presque toujours dire')
        console.log('   que le fichier n’est pas sur la branche citee dans l’adresse.')
      }
      process.exit(1)
    }
    return { buf: Buffer.from(await r.arrayBuffer()), type: r.headers.get('content-type') }
  }
  return { buf: readFileSync(ou), type: null }
}

/*
LES DIMENSIONS, LUES DANS L'EN-TÊTE.

PNG les écrit à un emplacement fixe : octets 16 à 23, juste après le
marqueur « IHDR ». JPEG demande de parcourir ses segments jusqu'à celui
qui décrit l'image — il n'y a pas d'emplacement fixe, parce qu'un JPEG
peut commencer par une vignette, des données d'appareil photo, un profil
de couleurs.
*/
function dimensions(buf) {
  // PNG : 89 50 4E 47
  if (buf.length > 24 && buf.readUInt32BE(0) === 0x89504e47) {
    return { format: 'PNG', largeur: buf.readUInt32BE(16), hauteur: buf.readUInt32BE(20) }
  }
  // JPEG : FF D8
  if (buf.length > 4 && buf[0] === 0xff && buf[1] === 0xd8) {
    let i = 2
    while (i < buf.length - 9) {
      if (buf[i] !== 0xff) { i += 1; continue }
      const marqueur = buf[i + 1]
      // Les marqueurs SOF portent les dimensions ; on écarte DHT, DAC et
      // les marqueurs de redémarrage, qui partagent leur plage.
      const estSof =
        (marqueur >= 0xc0 && marqueur <= 0xc3) ||
        (marqueur >= 0xc5 && marqueur <= 0xc7) ||
        (marqueur >= 0xc9 && marqueur <= 0xcb) ||
        (marqueur >= 0xcd && marqueur <= 0xcf)
      if (estSof) {
        return { format: 'JPEG', hauteur: buf.readUInt16BE(i + 5), largeur: buf.readUInt16BE(i + 7) }
      }
      const taille = buf.readUInt16BE(i + 2)
      if (taille < 2) break
      i += 2 + taille
    }
    return { format: 'JPEG', largeur: 0, hauteur: 0 }
  }
  if (buf.length > 4 && buf.slice(0, 4).toString() === '<svg') return { format: 'SVG' }
  if (buf.slice(0, 5).toString().trim().startsWith('<?xml')) return { format: 'SVG' }
  return { format: 'inconnu' }
}

const { buf, type } = await octets(cible)
const ko = Math.round(buf.length / 102.4) / 10
const d = dimensions(buf)

console.log(`\nFichier  : ${cible}`)
console.log(`Format   : ${d.format}${type ? `  (servi comme ${type})` : ''}`)
console.log(`Poids    : ${ko} Ko`)

let bloquant = 0
let gene = 0

if (d.format === 'SVG') {
  console.log('\n❌ ANDROID NE SAIT PAS AFFICHER UN SVG dans une notification.')
  console.log('   Exporte-le en PNG 1024x512 et verifie le PNG.')
  bloquant += 1
} else if (d.format === 'inconnu') {
  console.log('\n❌ Ni PNG ni JPEG. Android ne l’affichera pas.')
  bloquant += 1
} else {
  console.log(`Taille   : ${d.largeur} x ${d.hauteur}`)
  const rapport = d.hauteur > 0 ? d.largeur / d.hauteur : 0

  /*
  DEUX POUR UN, PARCE QU'ANDROID RECADRE AU CENTRE.

  Une photo verticale donnee telle quelle est rognee en haut et en bas :
  sur un portrait, c'est le visage qui disparait. Ce n'est pas un defaut
  d'affichage, c'est le format d'une banniere de notification.
  */
  if (rapport < 1.6 || rapport > 2.4) {
    console.log(`\n⚠  PROPORTIONS ${rapport.toFixed(2)}:1 — attendu 2:1 environ.`)
    if (rapport < 1) {
      console.log('   Une image VERTICALE sera rognee en haut et en bas : sur un')
      console.log('   portrait, c’est le visage qui disparait.')
    } else {
      console.log('   Android recadre au centre ; les bords seront perdus.')
    }
    console.log('   1024 x 512 est le format sûr.')
    gene += 1
  }

  if (d.largeur < 600) {
    console.log(`\n⚠  LARGEUR ${d.largeur} px — l’image sera etiree et floue.`)
    gene += 1
  }
}

/*
LE POIDS EST LA CONTRAINTE QU'ON SOUS-ESTIME.

Elle ne vient pas d'Android mais du réseau de chaque utilisateur. Sur une
connexion lente, une image lourde n'arrive simplement jamais — et la
notification s'affiche sans bandeau, sans que personne sache pourquoi.
*/
if (ko > 500) {
  console.log(`\n❌ ${ko} Ko, c’est trop. Vise moins de 300 Ko.`)
  console.log('   Cette image est telechargee sur le reseau mobile de CHAQUE')
  console.log('   utilisateur, au moment ou la notification arrive.')
  bloquant += 1
} else if (ko > 300) {
  console.log(`\n⚠  ${ko} Ko — ca passe, mais plus leger serait mieux.`)
  gene += 1
}

if (!cible.startsWith('http')) {
  console.log('\nℹ  Ce fichier doit etre servi depuis une adresse HTTPS PUBLIQUE.')
  console.log('   La convention du depot :')
  console.log('   https://raw.githubusercontent.com/kamsman/vaultexproject/master/<chemin>')
  console.log('\n   ATTENTION : l’adresse cite « master ». Un fichier pousse sur une')
  console.log('   autre branche rend 404, et l’annonce part sans banniere.')
  console.log('   Reverifie avec cette commande en lui donnant l’ADRESSE, une fois')
  console.log('   le fichier sur master.')
}

console.log('')
if (bloquant > 0) {
  console.log(`❌ ${bloquant} probleme(s) bloquant(s). N’envoie pas encore.`)
  process.exitCode = 1
} else if (gene > 0) {
  console.log(`⚠  ${gene} reserve(s). L’annonce partira, mais l’image sera moins bonne.`)
} else {
  console.log('✅ Cette banniere est utilisable.')
}
