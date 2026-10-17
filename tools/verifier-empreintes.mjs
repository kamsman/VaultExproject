/*
═══════════════════════════════════════════════════════════════════════════
LES EMPREINTES ÉPINGLÉES CORRESPONDENT-ELLES ENCORE ?
═══════════════════════════════════════════════════════════════════════════

    node tools/verifier-empreintes.mjs

À LANCER AVANT CHAQUE PUBLICATION. `NetworkModule.kt` le demande en toutes
lettres — « Empreintes relevées le 5 août 2026. Vérifier avant chaque
publication » — et jusqu'ici il n'y avait aucun moyen de le faire autrement
qu'à la main, avec openssl, sur une machine qui en dispose.

─── POURQUOI ÇA COMPTE, ET POURQUOI ÇA NE SE VOIT PAS ───────────────────

Le pinning est la SEULE différence réseau entre debug et release. Si une
empreinte ne correspond plus au certificat réel, OkHttp refuse la
connexion et lève une SSLPeerUnverifiedException — invisible pour
l'utilisateur, qui voit seulement des soldes à 0,00, des prix absents, un
historique vide. Et la même version debug fonctionne parfaitement, donc on
cherche partout sauf là.

C'est le pire mode de panne possible : il n'apparaît qu'en production,
qu'après distribution, et il ne ressemble pas à sa cause.

─── CE QUE CE SCRIPT LIT, ET CE QU'IL NE DEVINE PAS ─────────────────────

Les hôtes et leurs empreintes sont lus DANS `NetworkModule.kt`, pas
recopiés ici. Une seconde liste finirait par diverger de la vraie, et
c'est la vraie qui part dans l'APK.

─── POURQUOI SON VERDICT EST PRUDENT ────────────────────────────────────

Il compare les empreintes réglées à celles de la chaîne SERVIE par
l'hôte. Or OkHttp valide contre la chaîne CONSTRUITE, qui comprend la
racine du magasin du téléphone — souvent absente de ce que le serveur
envoie.

Une empreinte de racine introuvable ici est donc normale. Ce qui doit
alerter, c'est qu'AUCUNE des empreintes réglées n'apparaisse : alors soit
l'intermédiaire a tourné, soit il ne reste que la racine pour tenir — et
ça mérite un regard humain avant de publier.

Le script ne tranche donc pas à votre place. Il montre les empreintes
réelles, dit lesquelles correspondent, et vous laisse décider.
═══════════════════════════════════════════════════════════════════════════
*/

import { readFileSync } from 'node:fs'
import { connect } from 'node:tls'
import { createHash, X509Certificate } from 'node:crypto'

const SOURCE = new URL(
  '../app/src/main/java/com/vaultex/di/NetworkModule.kt',
  import.meta.url
)

/**
 * Les hôtes épinglés et leurs empreintes, lus dans le code Kotlin.
 *
 * On cherche les chaînes `"hote" to listOf(` suivies d'empreintes
 * `"sha256/…"`. Un analyseur Kotlin complet serait disproportionné ; ce
 * motif-là suit la forme que le fichier emploie, et s'il changeait, la
 * lecture rendrait zéro hôte — ce que le script signale au lieu de se
 * taire.
 */
function epinglesDuCode() {
  const source = readFileSync(SOURCE, 'utf8')
  const bloc = source.match(/CERT_PINS[^=]*=\s*mapOf\(([\s\S]*?)\n    \)/)
  if (!bloc) return null

  const epingles = []
  const motif = /"([a-z0-9.\-]+)"\s*to\s*listOf\(([\s\S]*?)\)/g
  let trouve
  while ((trouve = motif.exec(bloc[1])) !== null) {
    const empreintes = [...trouve[2].matchAll(/"(sha256\/[A-Za-z0-9+/=]+)"/g)]
      .map((m) => m[1])
    if (empreintes.length) epingles.push({ hote: trouve[1], empreintes })
  }
  return epingles
}

/** L'empreinte OkHttp d'un certificat : sha256 de sa clé publique DER. */
function empreinte(der) {
  const spki = new X509Certificate(der).publicKey.export({
    type: 'spki',
    format: 'der',
  })
  return 'sha256/' + createHash('sha256').update(spki).digest('base64')
}

/** La chaîne réellement servie par l'hôte, de la feuille vers la racine. */
function chaineServie(hote) {
  return new Promise((resoudre, rejeter) => {
    const prise = connect(
      { host: hote, port: 443, servername: hote, timeout: 12000 },
      () => {
        const chaine = []
        const vus = new Set()
        let c = prise.getPeerCertificate(true)
        // La racine se désigne elle-même comme son propre émetteur : sans
        // ce garde, la boucle ne s'arrête jamais.
        while (c && c.raw && !vus.has(c.fingerprint256)) {
          vus.add(c.fingerprint256)
          chaine.push({
            sujet: c.subject?.CN || c.subject?.O || '(sans nom)',
            empreinte: empreinte(c.raw),
          })
          c = c.issuerCertificate
        }
        prise.end()
        resoudre(chaine)
      }
    )
    prise.on('timeout', () => {
      prise.destroy()
      rejeter(new Error('delai depasse'))
    })
    prise.on('error', rejeter)
  })
}

const epingles = epinglesDuCode()
if (epingles === null) {
  console.log('\n❌ CERT_PINS introuvable dans NetworkModule.kt.')
  console.log('   Le bloc a change de forme : corriger la lecture ci-dessus.\n')
  process.exit(2)
}
if (epingles.length === 0) {
  console.log('\nℹ  Aucun hote epingle. Le pinning est desactive de fait.\n')
  process.exit(0)
}

console.log('')
console.log(`${epingles.length} hote(s) epingle(s) dans NetworkModule.kt\n`)

let alertes = 0

for (const { hote, empreintes } of epingles) {
  console.log(`─── ${hote} ─────────────────────────────────────────`)

  let chaine
  try {
    chaine = await chaineServie(hote)
  } catch (e) {
    console.log(`  ❌ Injoignable : ${String(e?.message ?? e).slice(0, 80)}`)
    console.log('     Sans reponse, on ne peut rien conclure — pas meme que')
    console.log("     l'empreinte est mauvaise.\n")
    alertes += 1
    continue
  }

  const servies = new Set(chaine.map((c) => c.empreinte))
  console.log('  Chaine servie :')
  for (const c of chaine) {
    const marque = empreintes.includes(c.empreinte) ? '✓ EPINGLE' : '  '
    console.log(`    ${marque}  ${c.empreinte}`)
    console.log(`               ${c.sujet}`)
  }

  const correspondent = empreintes.filter((e) => servies.has(e))
  const absentes = empreintes.filter((e) => !servies.has(e))

  console.log('')
  if (correspondent.length > 0) {
    console.log(`  ✅ ${correspondent.length} empreinte(s) reglee(s) sur ${empreintes.length} retrouvee(s) dans la chaine.`)
    if (absentes.length > 0) {
      console.log(`     ${absentes.length} absente(s) — souvent la racine, que le serveur`)
      console.log('     n’envoie pas et que le telephone possede deja. Normal.')
    }
  } else {
    /*
    AUCUNE CORRESPONDANCE N'EST UN AVERTISSEMENT, PAS UN VERDICT.

    OkHttp valide contre la chaîne CONSTRUITE, racine du magasin comprise.
    Une racine épinglée mais non servie tiendrait donc quand même. Dire
    « cassé » ici enverrait retirer un pinning qui fonctionne.
    */
    console.log('  ⚠  AUCUNE empreinte reglee ne figure dans la chaine servie.')
    console.log('     Deux lectures possibles, et il faut trancher a la main :')
    console.log('       — les empreintes reglees sont des RACINES, que le serveur')
    console.log('         n’envoie pas : le pinning tient quand meme ;')
    console.log("       — l'intermediaire a TOURNE : la version release ne pourra")
    console.log('         plus joindre cet hote, et ca ne se verra qu’apres coup.')
    console.log('     Compare les empreintes ci-dessus a celles de NetworkModule.kt.')
    alertes += 1
  }
  console.log('')
}

if (alertes > 0) {
  console.log('⚠  Verification incomplete — relis ci-dessus AVANT de publier.\n')
  process.exitCode = 1
} else {
  console.log('✅ Chaque hote epingle presente au moins une empreinte reglee.\n')
}
