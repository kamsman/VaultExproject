/*
═══════════════════════════════════════════════════════════════════════════
LA LECTURE DES SMS D'ENCAISSEMENT
═══════════════════════════════════════════════════════════════════════════

    node tools/relais-cours/test-sms.mjs

    node tools/relais-cours/test-sms.mjs "Vous avez recu 10000 FCFA de ..."
      └── MODE VÉRIFICATION : colle un VRAI SMS et vois ce qui en sort.

═══════════════════════════════════════════════════════════════════════════
POURQUOI LE SECOND MODE EXISTE
═══════════════════════════════════════════════════════════════════════════

Je n'ai jamais vu un SMS d'encaissement d'Orange Money Burkina. Les motifs
de ce fichier sont écrits sur ce que je crois être leur forme, et ce n'est
pas une base suffisante pour un mécanisme qui fait envoyer de la crypto.

Les tests ci-dessous prouvent donc quelque chose de plus modeste, et de
plus solide : que les RÈGLES tiennent. Qu'un SMS d'envoi n'est jamais lu
comme un encaissement, qu'un montant à séparateurs de milliers n'est pas
divisé par mille, qu'un numéro n'est pas confondu avec un montant.

Le format réel, lui, se vérifie en une commande. Colle un SMS reçu, lis ce
qui en est extrait, et corrige le motif si c'est faux. Trente secondes, et
on cesse de supposer.

CE QUE TU COLLES RESTE SUR TA MACHINE : ce fichier n'ouvre aucune
connexion. Tu peux remplacer le numéro par un autre avant de coller.
═══════════════════════════════════════════════════════════════════════════
*/

import { lireSmsPaiement, sansAccents } from './worker.js'

// ─── Mode vérification : un vrai SMS en argument ────────────────────

const colle = process.argv.slice(2).join(' ').trim()
if (colle) {
  console.log('\nTexte recu :')
  console.log(`  ${colle}`)
  console.log('\nApres normalisation :')
  console.log(`  ${sansAccents(colle)}`)
  const lu = lireSmsPaiement(colle)
  console.log('\nCe que le relais en lit :')
  if (!lu) {
    console.log('  ❌ RIEN. Le motif ne reconnait pas ce format.')
    console.log('\n  Deux causes possibles :')
    console.log('   1. aucun mot de reception reconnu (« avez recu », « credite de »…)')
    console.log('   2. un mot d’emission present, qui fait refuser par securite')
    console.log('      (« envoye », « retrait », « debite »…)')
    console.log('\n  Envoie-moi ce texte et je corrige MOTS_RECEPTION dans worker.js.')
    process.exit(1)
  }
  console.log(`  Montant   : ${lu.montant} FCFA`)
  console.log(`  Numero    : ${lu.numero || '(aucun)'}`)
  console.log(`  Reference : ${lu.reference || '(aucune)'}`)
  console.log('\n  ✅ Lu. Verifie que les trois valeurs sont EXACTES :')
  console.log('     un montant divise par mille, ou un numero qui est en')
  console.log('     fait un identifiant de transaction, passerait ce test')
  console.log('     sans que le rapprochement marche jamais.')
  process.exit(0)
}

// ─── Les règles, qui se testent sans connaître le format ────────────

let passes = 0
let echecs = 0

function verifie(nom, condition, detail) {
  if (condition) {
    passes += 1
  } else {
    echecs += 1
    console.log(`❌ ${nom}`)
    if (detail !== undefined) console.log(`   obtenu : ${JSON.stringify(detail)}`)
  }
}

// ─── Ce qui doit être lu ────────────────────────────────────────────

{
  const lu = lireSmsPaiement(
    'Vous avez recu 10000 FCFA de 70123456. Ref: 1234567890. Nouveau solde: 125000 FCFA.'
  )
  verifie('LU : un encaissement simple', lu !== null, lu)
  verifie('LU : le montant', lu && lu.montant === 10000, lu)
  verifie('LU : le numero', lu && lu.numero === '70123456', lu)
  verifie('LU : la reference', lu && lu.reference === '1234567890', lu)
}

{
  // Avec accents et majuscules, comme un opérateur les écrit vraiment.
  const lu = lireSmsPaiement('Vous avez reçu 5 000 F CFA de 64 00 00 00')
  verifie('LU : accents et espaces', lu !== null, lu)
  verifie('LU : montant a espaces', lu && lu.montant === 5000, lu)
}

{
  const lu = lireSmsPaiement('Votre compte a ete credite de 25.000 XOF. ID: ABC123XY')
  verifie('LU : « credite de »', lu !== null, lu)
  verifie('LU : separateur point', lu && lu.montant === 25000, lu)
  verifie('LU : reference alphanumerique', lu && lu.reference === 'ABC123XY', lu)
}

// ─── CE QUI NE DOIT JAMAIS ÊTRE LU ──────────────────────────────────

{
  /*
  ═══════════════════════════════════════════════════════════════════
  LE CAS QUI JUSTIFIE TOUT CE FICHIER
  ═══════════════════════════════════════════════════════════════════

  Un changeur ENVOIE des francs toute la journée, et chaque envoi produit
  un SMS qui contient un montant et un numéro — exactement comme un
  encaissement.

  Un extracteur générique les lirait tous les deux. Le changeur
  enverrait alors de la crypto contre SON PROPRE virement sortant, et ça
  arriverait sans aucune fraude, plusieurs fois par jour.
  ═══════════════════════════════════════════════════════════════════
  */
  for (const texte of [
    'Vous avez envoye 10000 FCFA a 70123456. Ref: 1234567890',
    'Vous avez envoyé 10 000 F CFA à 70123456',
    'Transfert de 10000 FCFA transfere a 70123456 effectue',
    'Retrait de 10000 FCFA au point marchand 70123456',
    'Votre compte a ete debite de 10000 FCFA',
    'Paiement de 10000 FCFA effectue chez MARCHAND 70123456',
  ]) {
    verifie(`REFUS : « ${texte.slice(0, 34)}… »`, lireSmsPaiement(texte) === null, texte)
  }
}

{
  // Un SMS qui dit les deux : refusé. En cas de doute, on ne verifie pas.
  const lu = lireSmsPaiement('Vous avez recu 10000 FCFA puis envoye 5000 FCFA a 70123456')
  verifie('REFUS : reception ET emission dans le meme texte', lu === null, lu)
}

{
  for (const texte of [
    'Votre solde est de 125000 FCFA',
    'Promo ! Rechargez 1000 FCFA et gagnez un bonus',
    'Votre code de connexion est 123456',
    '',
    null,
    undefined,
  ]) {
    verifie(
      `REFUS : sans mot de reception « ${String(texte).slice(0, 28)} »`,
      lireSmsPaiement(texte) === null,
      texte
    )
  }
}

{
  // Un mot de réception mais aucun montant : rien à rapprocher.
  verifie(
    'REFUS : reception sans montant',
    lireSmsPaiement('Vous avez recu un transfert de votre contact') === null
  )
}

// ─── Les pièges de lecture ──────────────────────────────────────────

{
  /*
  LE NUMÉRO SE CHERCHE APRÈS LE MONTANT.

  « recu 5000 FCFA de 70123456 » : si on prenait le premier groupe de huit
  chiffres du texte, un SMS portant d'abord une date ou un identifiant
  rendrait celui-là comme numéro — et le rapprochement par montant et
  numéro ne marcherait jamais, sans qu'on sache pourquoi.
  */
  const lu = lireSmsPaiement('20251010 - Vous avez recu 5000 FCFA de 70123456')
  verifie('PIEGE : le numero vient apres le montant', lu && lu.numero === '70123456', lu)
}

{
  /*
  LE FRANC CFA N'A PAS DE CENTIMES.

  « 5.000 » et « 5,000 » désignent cinq mille dans les SMS d'Afrique de
  l'Ouest, où le point comme la virgule servent de séparateur de
  milliers. Les lire comme des décimales rendrait CINQ, et une demande de
  cinq mille francs ne trouverait jamais son encaissement.
  */
  verifie('PIEGE : 5.000 vaut cinq mille', lireSmsPaiement('recu de 5.000 FCFA').montant === 5000)
  verifie('PIEGE : 5,000 vaut cinq mille', lireSmsPaiement('recu de 5,000 FCFA').montant === 5000)
  verifie(
    'PIEGE : 1.250.000 vaut un million deux cent cinquante mille',
    lireSmsPaiement('recu de 1.250.000 FCFA').montant === 1250000
  )
}

{
  // Un numéro à huit chiffres écrit avec des espaces reste comparable.
  const lu = lireSmsPaiement('Vous avez recu 5000 FCFA de 70 12 34 56')
  verifie('PIEGE : numero espace est normalise', lu && lu.numero === '70123456', lu)
  const tiret = lireSmsPaiement('Vous avez recu 5000 FCFA de 70-12-34-56')
  verifie('PIEGE : numero a tirets', tiret && tiret.numero === '70123456', tiret)
}

{
  /*
  « ENVOYÉ PAR » DÉCRIT UNE RÉCEPTION.

  Refuser sur le mot « envoye » seul protège du cas dangereux — un
  virement sortant lu comme un encaissement — mais écarterait aussi cette
  formulation-là, que les opérateurs emploient réellement. Elle doit
  passer.
  */
  const lu = lireSmsPaiement(
    'Vous avez recu un transfert de 7500 FCFA envoye par 70123456. Ref: ZZ11223344'
  )
  verifie('NUANCE : « envoye par » reste une reception', lu !== null, lu)
  verifie('NUANCE : et le montant est bon', lu && lu.montant === 7500, lu)
}

{
  /*
  LE CAS QUE MA PREMIÈRE VERSION LAISSAIT PASSER, gardé ici en toutes
  lettres : un montant entre « envoye » et « a » suffisait à contourner
  les motifs « avez envoye » et « envoye a ».
  */
  verifie(
    'REFUS : « envoye <montant> a » ne contourne plus rien',
    lireSmsPaiement('Vous avez recu 10000 FCFA puis envoye 5000 FCFA a 70123456') === null
  )
}

{
  // La référence perd ses points et tirets des deux côtés : le client la
  // recopie comme il peut, et « MP.251008-123 » doit rejoindre
  // « MP251008123 ».
  const lu = lireSmsPaiement('Vous avez recu 5000 FCFA de 70123456 Ref. MP.251008-123456')
  verifie('PIEGE : reference sans ponctuation', lu && lu.reference === 'MP251008123456', lu)
}

{
  // Sans numéro lisible, on rend quand même le reste : le rapprochement
  // se fera sur la référence.
  const lu = lireSmsPaiement('Vous avez recu 5000 FCFA de M. KOANDA. Ref: XYZ99887')
  verifie('TOLERANCE : sans numero, la reference suffit', lu && lu.reference === 'XYZ99887', lu)
  verifie('TOLERANCE : et le numero est vide, pas faux', lu && lu.numero === '', lu)
}

// ─── Verdict ────────────────────────────────────────────────────────

console.log(`\n${passes} verifications passees, ${echecs} en echec`)
if (echecs > 0) process.exit(1)
console.log('✅ Les REGLES de lecture tiennent.')
console.log('⚠️  Le FORMAT reel d’Orange Money Burkina n’est PAS verifie ici.')
console.log('   Colle un vrai SMS en argument pour le verifier en trente secondes :')
console.log('   node tools/relais-cours/test-sms.mjs "Vous avez recu ..."')
