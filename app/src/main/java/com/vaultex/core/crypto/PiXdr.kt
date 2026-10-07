package com.vaultex.core.crypto

import java.io.ByteArrayOutputStream

/*
═══════════════════════════════════════════════════════════════════════════
PI — LE FORMAT D'UNE TRANSACTION, ET SA SIGNATURE
═══════════════════════════════════════════════════════════════════════════

Pi est un dérivé de Stellar : une transaction y est une structure XDR, et la
signature porte sur l'empreinte de cette structure préfixée par
l'identifiant du réseau.

CE FICHIER NE PARLE À PERSONNE. Aucun appel réseau, aucune lecture de
secret, aucune horloge. Il transforme des nombres en octets, et c'est tout.
C'est ce qui le rend entièrement vérifiable hors ligne — et il l'est :
PiXdrTest rejoue les vecteurs du SDK Stellar officiel et compare octet par
octet.

─── POURQUOI ÉCRIRE CE CODE PLUTÔT QUE PRENDRE UNE BIBLIOTHÈQUE ─────────

Le SDK Stellar pour Java existe. Il apporte un encodeur XDR complet — des
dizaines d'opérations, les actifs, les offres, les contrats — là où il en
faut DEUX. Ce qu'on n'utilise pas, on ne peut pas le relire, et sur un
chemin qui signe de l'argent c'est ce qu'on peut relire qui compte.

Le format, lui, est petit et figé. Il tient dans ce fichier, il est testé
contre la référence, et il ne bougera pas.

─── LES CINQ CHOSES QUI NE PARDONNENT PAS ───────────────────────────────

1. ON SIGNE L'EMPREINTE, PAS LA CHARGE. La signature Ed25519 porte sur les
   32 octets de l'empreinte SHA-256, pas sur la structure complète. Signer
   la charge produit une signature que le réseau refuse — échec visible,
   donc bénin, mais qui coûte un aller-retour à comprendre.

2. L'IDENTIFIANT DU RÉSEAU EST DANS LA CHOSE SIGNÉE. C'est ce qui empêche
   une transaction signée pour le réseau d'essai d'être rejouée sur le
   réseau principal. Il entre dans l'empreinte, jamais dans la transaction.

3. TOUT EST EN GROS-BOUTISME, ET TOUT EST ALIGNÉ SUR QUATRE OCTETS. Une
   chaîne de caractères est précédée de sa longueur puis complétée par des
   zéros jusqu'au multiple de quatre suivant. Oublier ce remplissage décale
   tout ce qui suit.

4. LES MONTANTS SONT DES ENTIERS, EN STROOPS. Un Pi vaut dix millions de
   stroops. Aucun flottant n'entre ici : 0,1 + 0,2 ne fait pas 0,3 en
   virgule flottante, et sur un montant à envoyer ce n'est pas une
   curiosité, c'est une erreur de caisse.

5. UN MÉMO SE MESURE EN OCTETS, PAS EN CARACTÈRES. Vingt-huit octets.
   « é » en compte deux. Un mémo tronqué parce qu'on a compté des
   caractères est un dépôt perdu en bourse — c'est le mémo qui dit à qui
   créditer.
═══════════════════════════════════════════════════════════════════════════
*/
object PiXdr {

    /** Un Pi vaut dix millions de stroops. L'unité de tous les montants. */
    const val STROOPS_PAR_PI = 10_000_000L

    /** Nombre de décimales du Pi — sept, comme toute la famille Stellar. */
    const val DECIMALES = 7

    /** Longueur maximale d'un mémo texte, EN OCTETS UTF-8. */
    const val MEMO_TEXTE_MAX_OCTETS = 28

    private const val ENVELOPPE_TYPE_TX = 2
    private const val TYPE_CLE_ED25519 = 0
    private const val PRECOND_TEMPS = 1
    private const val ACTIF_NATIF = 0
    private const val OP_CREATION_COMPTE = 0
    private const val OP_PAIEMENT = 1
    private const val MEMO_AUCUN = 0
    private const val MEMO_TEXTE = 1
    private const val MEMO_IDENTIFIANT = 2

    /**
     * Mémo d'une transaction.
     *
     * [Texte] et [Identifiant] existent tous les deux parce que les
     * plateformes d'échange exigent l'un OU l'autre, jamais au choix : en
     * donner le mauvais type équivaut à n'en donner aucun.
     */
    sealed class Memo {
        data object Aucun : Memo()
        data class Texte(val valeur: String) : Memo()
        data class Identifiant(val valeur: Long) : Memo()
    }

    /**
     * Les deux seules opérations dont un portefeuille a besoin.
     *
     * [CreationCompte] n'est pas un raffinement : sur un réseau de la
     * famille Stellar, un PAIEMENT vers une adresse qui n'a jamais été
     * créditée ÉCHOUE. C'est la première cause d'envoi raté, et elle ne se
     * devine pas — il faut avoir lu la règle.
     */
    sealed class Operation {
        data class Paiement(val destination: String, val stroops: Long) : Operation()
        data class CreationCompte(val destination: String, val stroops: Long) : Operation()
    }

    /**
     * Identifiant d'un réseau : l'empreinte SHA-256 de sa phrase.
     *
     * Deux réseaux de phrases différentes donnent deux identifiants
     * différents, donc deux empreintes différentes pour la même
     * transaction. C'est le mécanisme — le seul — qui empêche de rejouer
     * sur le réseau principal ce qui a été signé pour le réseau d'essai.
     */
    fun idReseau(phrase: String): ByteArray =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(phrase.toByteArray(Charsets.UTF_8))

    /**
     * Sérialise une transaction.
     *
     * @param source adresse G… qui paie et qui signe.
     * @param fraisStroops enchère de frais, pour l'ensemble des opérations.
     * @param numeroSequence numéro de séquence du compte source, DÉJÀ incrémenté.
     * @param finValiditeEpochSec instant après lequel la transaction ne peut
     *   plus être appliquée. **Zéro signifie « jamais périmée »**, et aucun
     *   envoi réel ne doit l'utiliser — voir ci-dessous.
     *
     * ═══════════════════════════════════════════════════════════════════
     * POURQUOI CETTE FONCTION N'IMPOSE PAS DE DATE LIMITE
     * ═══════════════════════════════════════════════════════════════════
     *
     * Une transaction sans date limite reste applicable pour toujours : une
     * diffusion qu'on croyait perdue peut ressurgir des semaines plus tard
     * et débiter le compte une seconde fois. C'est une règle absolue pour
     * un envoi d'argent, et elle est imposée — mais dans
     * PiEnvoiUseCase.borneDeValidite, pas ici.
     *
     * La raison est que ce fichier est un ENCODEUR, et qu'un encodeur doit
     * rendre fidèlement ce qu'on lui demande, y compris zéro. C'est ce qui
     * permet à PiXdrConformiteTest de rejouer les vecteurs officiels du SDK
     * Stellar — qui, eux, utilisent zéro — à travers EXACTEMENT ce code et
     * non une variante écrite pour le test.
     *
     * Un encodeur qui refuse une valeur légale de la spécification ne peut
     * plus être confronté à la spécification. On perdrait la preuve pour
     * gagner une garde que l'appelant applique déjà, et mieux : lui sait
     * quelle heure il est.
     * ═══════════════════════════════════════════════════════════════════
     */
    fun transaction(
        source: String,
        fraisStroops: Long,
        numeroSequence: Long,
        finValiditeEpochSec: Long,
        memo: Memo,
        operations: List<Operation>
    ): ByteArray {
        require(operations.isNotEmpty()) { "une transaction sans opération ne fait rien" }
        require(operations.size <= 100) { "cent opérations au maximum" }
        require(fraisStroops > 0) { "les frais doivent être positifs" }
        require(numeroSequence > 0) { "numéro de séquence invalide" }
        require(finValiditeEpochSec >= 0) { "borne de validité négative" }
        require(fraisStroops <= Int.MAX_VALUE.toLong()) { "frais hors du champ uint32" }

        val out = ByteArrayOutputStream()
        out.write(compteMuxe(source))
        out.write(u32(fraisStroops.toInt()))
        out.write(i64(numeroSequence))
        /*
        PRÉCONDITIONS — ET UNE COMPATIBILITÉ QUI TOMBE BIEN.

        Avant le protocole 19, ce champ était un `TimeBounds*` optionnel :
        un drapeau de présence suivi de la structure. Depuis, c'est une
        union `Preconditions` dont le cas 1 est… un TimeBounds.

        Les deux s'encodent donc EXACTEMENT pareil quand on fournit des
        bornes de temps : un 1, puis deux entiers de huit octets. Ce code
        n'a pas besoin de savoir quel protocole Pi fait tourner, et il
        continuera de fonctionner si Pi en change.
        */
        out.write(u32(PRECOND_TEMPS))
        out.write(u64(0L))                       // début : immédiat
        out.write(u64(finValiditeEpochSec))
        out.write(encoderMemo(memo))
        out.write(u32(operations.size))
        operations.forEach { out.write(encoderOperation(it)) }
        out.write(u32(0))                        // extension, version 0
        return out.toByteArray()
    }

    /**
     * Empreinte d'une transaction : c'est CE QU'ON SIGNE, et c'est aussi
     * son identifiant sur le réseau.
     *
     * Les deux usages n'en font qu'un, et c'est précieux : on connaît
     * l'identifiant de la transaction AVANT de la diffuser. Une diffusion
     * dont on n'a pas reçu la réponse peut donc être retrouvée par cet
     * identifiant, au lieu d'être renvoyée à l'aveugle.
     */
    fun empreinte(transactionXdr: ByteArray, idReseau: ByteArray): ByteArray {
        require(idReseau.size == 32) { "identifiant de réseau attendu sur 32 octets" }
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        digest.update(idReseau)
        digest.update(u32(ENVELOPPE_TYPE_TX))
        digest.update(transactionXdr)
        return digest.digest()
    }

    /**
     * Signe [transactionXdr] et rend l'enveloppe prête à être diffusée.
     *
     * C'est la SEULE fonction de ce fichier qui touche une clé privée, et
     * elle ne la garde pas : elle la passe à Ed25519Utils et rend des
     * octets.
     */
    fun enveloppeSignee(
        transactionXdr: ByteArray,
        idReseau: ByteArray,
        clePrivee: ByteArray,
        clePublique: ByteArray
    ): String {
        require(clePublique.size == 32) { "clé publique attendue sur 32 octets" }
        // On signe l'EMPREINTE — trente-deux octets — et non la structure.
        val signature = Ed25519Utils.sign(empreinte(transactionXdr, idReseau), clePrivee)
        require(signature.size == 64) { "signature Ed25519 attendue sur 64 octets" }

        val out = ByteArrayOutputStream()
        out.write(u32(ENVELOPPE_TYPE_TX))
        out.write(transactionXdr)
        out.write(u32(1))                                    // une signature
        // L'indice : les QUATRE DERNIERS octets de la clé publique. Il permet
        // au réseau de retrouver le signataire sans essayer toutes les clés.
        out.write(clePublique.copyOfRange(28, 32))
        out.write(u32(signature.size))
        out.write(signature)                                 // 64 : déjà aligné
        /*
        java.util.Base64 ET NON android.util.Base64, pour une raison qui
        n'a rien d'esthétique : la classe d'Android n'existe pas dans un
        test JVM — elle y lève « not mocked ». Tout ce fichier deviendrait
        donc invérifiable hors émulateur, alors que c'est précisément le
        fichier qu'il faut pouvoir vérifier.

        Disponible depuis l'API 26, qui est le minimum de l'application.
        */
        return java.util.Base64.getEncoder().encodeToString(out.toByteArray())
    }

    /**
     * Convertit un montant écrit par un humain en stroops.
     *
     * REFUSE au lieu d'arrondir. Un montant à huit décimales est soit une
     * faute de frappe, soit un copier-coller depuis une autre monnaie :
     * dans les deux cas, en tronquer la dernière décimale sans rien dire
     * envoie un montant que l'utilisateur n'a pas écrit.
     */
    fun stroopsDepuisTexte(montant: String): Long? {
        val propre = montant.trim().replace(',', '.').replace(" ", "")
        if (propre.isEmpty()) return null
        val decimal = try { java.math.BigDecimal(propre) } catch (_: Exception) { return null }
        if (decimal.signum() <= 0) return null
        if (decimal.scale() > DECIMALES) return null
        return try {
            decimal.movePointRight(DECIMALES).toBigIntegerExact().let {
                if (it.bitLength() >= 63) null else it.toLong()
            }
        } catch (_: Exception) { null }
    }

    /** Stroops → Pi, pour l'affichage. Jamais pour un calcul de montant. */
    fun piDepuisStroops(stroops: Long): java.math.BigDecimal =
        java.math.BigDecimal(stroops).movePointLeft(DECIMALES)

    /**
     * Montant en Pi, écrit comme on l'écrirait à la main.
     *
     * `piDepuisStroops` rend un BigDecimal dont la représentation textuelle
     * garde les sept décimales : « 2 Pi » s'y lit « 2.0000000 ». Dans un
     * message d'erreur du genre « 2.0000000 Pi au maximum », ces zéros
     * donnent l'air d'une précision qui n'a aucun sens ici — et surtout
     * ils rendent le chiffre plus difficile à lire au moment précis où
     * quelqu'un essaie de comprendre pourquoi son envoi a été refusé.
     */
    fun texteDepuisStroops(stroops: Long): String {
        val pi = piDepuisStroops(stroops).stripTrailingZeros()
        // toPlainString : sans lui, un petit montant sortirait en notation
        // scientifique — « 1E-7 Pi » n'est pas un montant lisible.
        return pi.toPlainString()
    }

    /**
     * Vrai si [texte] tient dans un mémo.
     *
     * Mesuré en OCTETS UTF-8 : « é » en vaut deux, un emoji jusqu'à quatre.
     * Compter les caractères laisserait passer un mémo trop long, qui
     * serait alors refusé par le réseau — ou pire, accepté tronqué.
     */
    fun memoTexteValide(texte: String): Boolean =
        texte.toByteArray(Charsets.UTF_8).size <= MEMO_TEXTE_MAX_OCTETS

    // ─── Encodage ────────────────────────────────────────────────────

    private fun encoderMemo(memo: Memo): ByteArray = when (memo) {
        is Memo.Aucun -> u32(MEMO_AUCUN)
        is Memo.Texte -> {
            val octets = memo.valeur.toByteArray(Charsets.UTF_8)
            require(octets.size <= MEMO_TEXTE_MAX_OCTETS) {
                "mémo de ${octets.size} octets : $MEMO_TEXTE_MAX_OCTETS au maximum"
            }
            u32(MEMO_TEXTE) + u32(octets.size) + remplir(octets)
        }
        is Memo.Identifiant -> u32(MEMO_IDENTIFIANT) + u64(memo.valeur)
    }

    private fun encoderOperation(op: Operation): ByteArray = when (op) {
        is Operation.Paiement -> {
            require(op.stroops > 0) { "montant nul ou négatif" }
            u32(0) +                               // source de l'opération : absente
                u32(OP_PAIEMENT) +
                compteMuxe(op.destination) +
                u32(ACTIF_NATIF) +
                i64(op.stroops)
        }
        is Operation.CreationCompte -> {
            require(op.stroops > 0) { "solde de départ nul ou négatif" }
            u32(0) +
                u32(OP_CREATION_COMPTE) +
                identifiantCompte(op.destination) +
                i64(op.stroops)
        }
    }

    /**
     * Union MuxedAccount, cas d'une simple clé Ed25519.
     *
     * S'encode comme un identifiant de compte — même discriminant, même
     * clé — mais les deux types sont distincts dans la spécification et
     * apparaissent à des endroits différents : le paiement veut un
     * MuxedAccount, la création de compte un AccountID. Les confondre
     * marcherait par accident aujourd'hui et casserait au premier champ
     * qui les distingue.
     */
    private fun compteMuxe(adresse: String): ByteArray =
        u32(TYPE_CLE_ED25519) + clePubliqueDe(adresse)

    /** Union PublicKey : discriminant puis trente-deux octets. */
    private fun identifiantCompte(adresse: String): ByteArray =
        u32(TYPE_CLE_ED25519) + clePubliqueDe(adresse)

    private fun clePubliqueDe(adresse: String): ByteArray {
        val propre = adresse.trim()
        require(PiWallet.adresseValide(propre)) { "adresse Pi invalide" }
        return PiWallet.clePubliqueDeLAdresse(propre)
    }

    private fun u32(valeur: Int): ByteArray = byteArrayOf(
        (valeur ushr 24).toByte(), (valeur ushr 16).toByte(),
        (valeur ushr 8).toByte(), valeur.toByte()
    )

    private fun i64(valeur: Long): ByteArray = ByteArray(8) {
        (valeur ushr (56 - it * 8)).toByte()
    }

    private fun u64(valeur: Long): ByteArray = i64(valeur)

    /** Complète par des zéros jusqu'au multiple de quatre octets suivant. */
    private fun remplir(donnees: ByteArray): ByteArray {
        val reste = donnees.size % 4
        if (reste == 0) return donnees
        return donnees + ByteArray(4 - reste)
    }
}
