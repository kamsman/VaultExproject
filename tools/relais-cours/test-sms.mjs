/*
═══════════════════════════════════════════════════════════════════════════
LA LECTURE DES SMS D'ENCAISSEMENT
═══════════════════════════════════════════════════════════════════════════

    node tools/relais-cours/test-sms.mjs

    node tools/relais-cours/test-sms.mjs --fichier=sms.txt
      └── MODE VÉRIFICATION : mets un VRAI SMS dans un fichier et vois
          ce qui en sort. Le fichier plutôt que les guillemets, parce
          qu'une apostrophe d'opérateur tronque un argument PowerShell.

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

import { readFileSync } from 'node:fs'
import { lireSmsPaiement, sansAccents, montantFcfaDepuisTexte, expediteurAccepte } from './worker.js'

// ─── Mode vérification : un vrai SMS en argument ────────────────────

/*
═══════════════════════════════════════════════════════════════════════════
TROIS FAÇONS DE DONNER LE SMS, ET LA PREMIÈRE EST LA BONNE
═══════════════════════════════════════════════════════════════════════════

    node tools/relais-cours/test-sms.mjs --fichier=sms.txt     ← recommandé
    Get-Content sms.txt | node tools/relais-cours/test-sms.mjs
    node tools/relais-cours/test-sms.mjs "le texte entre guillemets"

Le fichier est recommandé parce qu'un vrai SMS d'opérateur contient des
apostrophes, des accents et parfois des retours à la ligne. Passé en
argument sous PowerShell, l'apostrophe ferme la chaîne et le texte arrive
tronqué — on analyserait alors un SMS qui n'est pas celui qu'on a reçu, et
on corrigerait un motif qui n'avait rien.

C'est la même famille de problème que le `&&` de PowerShell et que les
exemples collés littéralement : la machine entre nous deux abîme le texte
en silence. Un fichier ne s'abîme pas.
═══════════════════════════════════════════════════════════════════════════
*/
function texteDonne() {
  const args = process.argv.slice(2)
  const viaFichier = args.find((a) => a.startsWith('--fichier='))
  if (viaFichier) {
    const chemin = viaFichier.slice(10)
    try {
      return readFileSync(chemin, 'utf8').trim()
    } catch (e) {
      console.log(`\n❌ Fichier illisible : ${chemin}`)
      console.log(`   ${e?.message}`)
      process.exit(2)
    }
  }
  const positionnels = args.filter((a) => !a.startsWith('--')).join(' ').trim()
  if (positionnels) return positionnels
  /*
  L'ENTREE STANDARD, quand on tube un fichier. `isTTY` distingue « rien
  n'est tube » de « un texte vide est tube » : sans ça, lancer le script
  sans argument resterait bloque a attendre une saisie qui ne vient pas.
  */
  if (!process.stdin.isTTY) {
    try {
      return readFileSync(0, 'utf8').trim()
    } catch (_) {
      return ''
    }
  }
  return ''
}

const colle = texteDonne()
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


/*
═══════════════════════════════════════════════════════════════════════════
LE FORMAT REEL D'ORANGE MONEY BURKINA
═══════════════════════════════════════════════════════════════════════════

Enfin mesure, et il a immediatement montre un defaut qui aurait coute cher.

Le SMS ci-dessous est un VRAI message, avec ses chiffres remplaces. Tout le
reste est verbatim : la ponctuation, les champs vides, l'ordre des mots, le
lien a la fin.

CE QU'IL A REVELE : « 100000.0 FCFA ». Le point est une DECIMALE, pas un
separateur de milliers. Le code supprimait tous les separateurs et lisait
UN MILLION. Sur un achat, ca aurait confirme une demande de 1 000 000 FCFA
pour un paiement de 100 000 — et le changeur aurait envoye dix fois trop
de crypto.

Le commentaire que ce defaut a fait supprimer affirmait : « le franc CFA
n'a pas de centimes, il n'y a donc aucune ambiguite a lever ». Une
deduction raisonnable, et fausse. C'est tout l'interet d'un format MESURE.
═══════════════════════════════════════════════════════════════════════════
*/

const SMS_REEL =
  'Vous avez recu 100000.0 FCFA, Frais:  FCFA, Taxe:  FCFA du 74936353,HAMIDOU. ' +
  'Le solde de votre compte est de 100484.4 FCFA Trans ID: PP261013.2118.60776882. ' +
  'Flashez le QR CODE marchand avec Max it pour plus de facilite : https://onelink.to/nn64xw'

{
  const lu = lireSmsPaiement(SMS_REEL)
  verifie('REEL : le SMS d Orange est reconnu', lu !== null)
  verifie('REEL : le point est une decimale, pas des milliers', lu?.montant === 100000, lu?.montant)
  verifie('REEL : le numero de l expediteur est lu', lu?.numero === '74936353', lu?.numero)
  verifie('REEL : la reference de l operateur est lue', lu?.reference === 'PP261013211860776882', lu?.reference)
}

{
  /*
  LE SOLDE NE DOIT JAMAIS ETRE PRIS POUR LE MONTANT.

  Ce SMS porte DEUX sommes en francs : celle qu'on recoit, et le solde du
  compte. Elles sont du meme ordre de grandeur ici, et le solde vient en
  second — c'est pour ca que le motif s'arrete au premier.

  Ce test le fige : si quelqu'un elargissait la recherche un jour, le
  changeur se verrait confirmer son propre solde comme un paiement recu.
  */
  const lu = lireSmsPaiement(SMS_REEL)
  verifie('REEL : le solde n est pas pris pour le montant', lu?.montant !== 100484, lu?.montant)
}

{
  /*
  LES CHAMPS VIDES — « Frais:  FCFA, Taxe:  FCFA » — ne doivent pas faire
  echouer la lecture, ni etre lus comme des montants nuls. C'est une forme
  qu'aucun format invente n'aurait produite.
  */
  const lu = lireSmsPaiement(SMS_REEL)
  verifie('REEL : les champs Frais et Taxe vides ne genent pas', lu?.montant === 100000)
}

{
  /*
  LE LIEN A LA FIN contient des chiffres (« nn64xw ») et une adresse. Il ne
  doit influencer ni le numero ni la reference.
  */
  const sansLien = SMS_REEL.split('Flashez')[0]
  const avec = lireSmsPaiement(SMS_REEL)
  const sans = lireSmsPaiement(sansLien)
  verifie(
    'REEL : le lien publicitaire ne change rien a la lecture',
    avec?.montant === sans?.montant && avec?.numero === sans?.numero && avec?.reference === sans?.reference,
    [avec, sans]
  )
}

/*
═══════════════════════════════════════════════════════════════════════════
LA REGLE DU SEPARATEUR, ISOLEE
═══════════════════════════════════════════════════════════════════════════

On regarde ce qui suit le DERNIER separateur : une ou deux decimales, trois
chiffres pour des milliers. Trois restent lus comme des milliers, et c'est
volontaire — le franc CFA n'ayant pas de subdivision en usage, « 5.000 » ne
peut pas designer cinq francs et des millemes.
═══════════════════════════════════════════════════════════════════════════
*/
{
  verifie('SEP : 100000.0 vaut cent mille', montantFcfaDepuisTexte('100000.0') === 100000, montantFcfaDepuisTexte('100000.0'))
  verifie('SEP : 100484.4 vaut cent mille quatre cent quatre-vingt-quatre', montantFcfaDepuisTexte('100484.4') === 100484)
  verifie('SEP : 5.000 vaut cinq mille', montantFcfaDepuisTexte('5.000') === 5000, montantFcfaDepuisTexte('5.000'))
  verifie('SEP : 5,000 vaut cinq mille', montantFcfaDepuisTexte('5,000') === 5000)
  verifie('SEP : 5 000 vaut cinq mille', montantFcfaDepuisTexte('5 000') === 5000)
  verifie('SEP : 1.234.567,8 vaut un million deux cent trente-quatre mille cinq cent soixante-sept',
    montantFcfaDepuisTexte('1.234.567,8') === 1234567, montantFcfaDepuisTexte('1.234.567,8'))
  verifie('SEP : 1,50 est tronque a un', montantFcfaDepuisTexte('1,50') === 1, montantFcfaDepuisTexte('1,50'))
  /*
  ON TRONQUE, ON N'ARRONDIT PAS : arrondir vers le haut crediterait un franc
  de plus que ce qui a ete paye. Une sous-estimation se voit — le client
  reclame. Une surestimation, non.
  */
  verifie('SEP : 999.6 est tronque a 999, pas arrondi a 1000', montantFcfaDepuisTexte('999.6') === 999, montantFcfaDepuisTexte('999.6'))
  verifie('SEP : rien du tout vaut zero', montantFcfaDepuisTexte('') === 0 && montantFcfaDepuisTexte('   FCFA') === 0)
  verifie('SEP : une espace insecable etroite ne gene pas', montantFcfaDepuisTexte('5 000') === 5000)
}


/*
═══════════════════════════════════════════════════════════════════════════
LE FILTRE D'EXPEDITEUR, ET LE TROU QU'IL AVAIT
═══════════════════════════════════════════════════════════════════════════

Le test etait `expediteur.includes(e) || e.includes(expediteur)`. La
seconde moitie sert a reconnaitre une forme abregee — « orange » pour
« orange money ». Mais `'orangemoney'.includes('')` vaut VRAI : un
expediteur VIDE etait donc accepte par tous les motifs a la fois.

Ce filtre n'est pas une commodite. C'est lui qui empeche qu'un message
quelconque — un code de connexion, un mot d'un proche — soit traite comme
un encaissement. Le contourner ne demandait aucune astuce : il suffisait
que le champ manque.
═══════════════════════════════════════════════════════════════════════════
*/
{
  const L = ['orangemoney', 'orange money', 'wave']
  verifie('EXPEDITEUR : vide est REFUSE', expediteurAccepte('', L) === false)
  verifie('EXPEDITEUR : des espaces seuls sont refuses', expediteurAccepte('   ', L) === false)
  verifie('EXPEDITEUR : une seule lettre est refusee', expediteurAccepte('a', L) === false)
  verifie('EXPEDITEUR : deux lettres sont refusees', expediteurAccepte('ab', L) === false)
  verifie('EXPEDITEUR : null est refuse', expediteurAccepte(null, L) === false)

  verifie('EXPEDITEUR : orangemoney passe', expediteurAccepte('orangemoney', L) === true)
  verifie('EXPEDITEUR : wave passe', expediteurAccepte('wave', L) === true)
  /*
  LA FORME ABREGEE RESTE ACCEPTEE : c'est la raison d'etre de la seconde
  moitie du test, et la corriger ne doit pas la supprimer.
  */
  verifie('EXPEDITEUR : une forme abregee passe encore', expediteurAccepte('orange', L) === true)
  verifie('EXPEDITEUR : un suffixe de pays passe', expediteurAccepte('orangemoneybf', L) === true)
  verifie('EXPEDITEUR : un inconnu est refuse', expediteurAccepte('mtn', L) === false)
  /*
  UN MOTIF TROP COURT DANS LA LISTE ne doit pas tout ouvrir non plus :
  quelqu'un qui reglerait CHANGE_SMS_EXPEDITEURS a « a,b » accepterait
  sinon la moitie des expediteurs du monde.
  */
  verifie('EXPEDITEUR : un motif trop court est ignore', expediteurAccepte('orangemoney', ['a', 'o']) === false)
}

console.log(`\n${passes} verifications passees, ${echecs} en echec`)
if (echecs > 0) process.exit(1)
console.log('✅ Les REGLES de lecture tiennent.')
console.log('✅ Et le FORMAT reel d’Orange Money Burkina est fige depuis un vrai SMS.')
console.log('\n   Un SEUL format est connu : l’encaissement depuis un particulier.')
console.log('   Un paiement marchand, un depot en agence ou un transfert')
console.log('   international peuvent s’ecrire autrement. Pour en verifier un :')
console.log('   node tools/relais-cours/test-sms.mjs --fichier=sms.txt')
