/*
═══════════════════════════════════════════════════════════════════════════
VÉRIFIER UNE ADRESSE AVANT DE LA COLLER DANS CLOUDFLARE
═══════════════════════════════════════════════════════════════════════════

    node tools/relais-cours/verifier-adresse.mjs USDT T9yD14Nj9j7xAB4dbGeiX9h8...

═══════════════════════════════════════════════════════════════════════════
POURQUOI AVANT, ET PAS APRÈS
═══════════════════════════════════════════════════════════════════════════

/diag dit déjà si une adresse a été refusée. Mais il le dit APRÈS le
déploiement — et entre-temps l'application a pu afficher cette adresse à
un vendeur.

Or ce que coûte une erreur ici n'est pas une gêne : des USDT TRC-20 envoyés
à une adresse Ethereum sont perdus définitivement. Il n'y a pas de
réclamation possible, pas de support à contacter, rien.

Cinq secondes avant valent mieux qu'un diagnostic après.

═══════════════════════════════════════════════════════════════════════════
C'EST LE CODE DU RELAIS QUI JUGE, PAS UNE COPIE
═══════════════════════════════════════════════════════════════════════════

Cet outil importe `trierAdresses` depuis worker.js. Il applique donc
EXACTEMENT la règle qui s'appliquera au déploiement — pas une
approximation écrite à côté, qui pourrait dériver et dire « correcte »
d'une adresse que le relais refusera.

C'est le même principe que test-sms.mjs : on exécute le vrai code sur une
vraie valeur, au lieu de raisonner sur ce qu'il devrait faire.

═══════════════════════════════════════════════════════════════════════════
CE QUE ÇA NE DIT PAS
═══════════════════════════════════════════════════════════════════════════

Que l'adresse appartient à ton changeur. Aucun outil ne peut le dire : une
adresse TRON syntaxiquement parfaite peut être celle de n'importe qui.

La seule vérification qui couvre ça est humaine, et elle vaut la minute
qu'elle prend : que le changeur la lise à voix haute depuis SON téléphone
pendant que tu la compares à l'écran. Les six premiers et six derniers
caractères suffisent.
═══════════════════════════════════════════════════════════════════════════
*/

import { trierAdresses, formeAttendue } from './worker.js'

const [monnaieBrute, adresse] = process.argv.slice(2)

if (!monnaieBrute || !adresse) {
  console.log('\nUsage :')
  console.log('  node tools/relais-cours/verifier-adresse.mjs <MONNAIE> <adresse>\n')
  console.log('Exemples :')
  console.log('  node tools/relais-cours/verifier-adresse.mjs USDT TQn9Y2khDD95J42FQtQTdwVVRZqjGBCvpM')
  console.log('  node tools/relais-cours/verifier-adresse.mjs BTC bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq')
  console.log('\nLes adresses ci-dessus sont des EXEMPLES complets, pour montrer la forme.')
  console.log('Colle celle de ton changeur a la place.\n')
  process.exit(2)
}

const monnaie = monnaieBrute.trim().toUpperCase()
const forme = formeAttendue(monnaie)

/*
L'EXEMPLE COLLÉ TEL QUEL.

C'est arrivé au premier usage : la consigne disait « T… », et « T… » a été
tapé tel quel. Le programme a répondu « longueur lue : 4 », ce qui est
exact et n'aide personne — on relit son adresse en se demandant ce qui
cloche, alors qu'on ne l'a pas encore collée.

Une faute de l'auteur de la consigne, pas de celui qui l'a suivie. On la
nomme donc, au lieu de laisser deviner.
*/
if (/^T?\.{2,}$/.test(adresse.trim()) || adresse.trim() === '<adresse>') {
  console.log(`\n\u{1F4A1} « ${adresse} » est l'EXEMPLE de la consigne, pas une adresse.`)
  console.log(`   Remplace-le par celle du changeur : 34 caracteres commencant par T,`)
  console.log(`   copiee depuis son portefeuille > Recevoir > USDT > reseau TRON.`)
  process.exit(2)
}

/*
LE CONTRAT USDT DE TRON EST UNE ADRESSE TRON PARFAITEMENT VALIDE.

C'est pour ça qu'il mérite son propre test : il passe la vérification de
forme sans problème. Et il a servi d'exemple dans les tests de ce dépôt,
donc il traîne dans l'historique de la conversation et dans les
presse-papiers — exactement la façon dont on colle la mauvaise valeur.

Des USDT envoyés au contrat USDT sont perdus.
*/
const CONTRATS_CONNUS = {
  TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t: 'le contrat USDT de TRON',
  '0x55d398326f99059fF775485246999027B3197955': "le contrat USDT de BNB Chain",
  '0xdAC17F958D2ee523a2206206994597C13D831ec7': "le contrat USDT d'Ethereum",
}

console.log(`\nMonnaie  : ${monnaie}`)
console.log(`Adresse  : ${adresse}`)
console.log(`Forme attendue : ${forme ?? '(monnaie inconnue — longueur >= 20 seulement)'}`)

const contrat = Object.entries(CONTRATS_CONNUS).find(
  ([a]) => a.toLowerCase() === adresse.trim().toLowerCase()
)
if (contrat) {
  console.log(`\n\u{1F6A8} NE COLLE PAS CETTE ADRESSE.`)
  console.log(`   C'est ${contrat[1]}, pas une adresse de reception.`)
  console.log(`   Des fonds envoyes la sont perdus definitivement.`)
  process.exit(1)
}

const tri = trierAdresses(JSON.stringify({ [monnaie]: adresse }))

if (tri.bonnes[monnaie]) {
  console.log(`\n✅ FORME CORRECTE. Le relais acceptera cette adresse.`)
  console.log(`\n   A coller dans Cloudflare, variable CHANGE_ADRESSES :`)
  console.log(`   ${JSON.stringify(tri.bonnes)}`)
  console.log(`\n   ⚠ Il reste UNE verification que ce programme ne peut pas faire :`)
  console.log(`   que cette adresse soit bien celle de ton changeur. Fais-lui`)
  console.log(`   lire a voix haute depuis SON telephone — les six premiers et`)
  console.log(`   six derniers caracteres suffisent.`)
  process.exit(0)
}

const refus = tri.refusees[0]
console.log(`\n❌ REFUSEE. Le relais ecartera cette adresse.`)
if (!tri.lisible) {
  console.log(`   Raison : le JSON n'a pas pu etre lu.`)
} else if (refus) {
  console.log(`   Attendu : ${refus.attendu}`)
  if (refus.attendu === 'tron') {
    console.log(`\n   Une adresse TRON commence par « T » et fait 34 caracteres.`)
    console.log(`   Longueur lue : ${adresse.trim().length}`)
    if (adresse.trim().startsWith('0x')) {
      console.log(`\n   Celle-ci commence par « 0x » : c'est une adresse Ethereum`)
      console.log(`   ou BNB Chain. Sous la cle ${monnaie}, le relais attend du TRON.`)
      console.log(`   Dans le portefeuille du changeur : Recevoir > USDT > reseau TRON.`)
    } else if (/[0OIl]/.test(adresse.trim())) {
      console.log(`\n   Elle contient un 0, un O, un I ou un l — absents de`)
      console.log(`   l'alphabet base58. C'est le signe d'un copier-coller abime`)
      console.log(`   ou d'une adresse recopiee a la main.`)
    }
  }
}
process.exit(1)
