package com.vaultex.core.tx

import java.io.ByteArrayOutputStream

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * LA FORME D'UNE TRANSACTION DOGECOIN, EN OCTETS
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Deuxième brique du support Dogecoin, après [com.vaultex.core.crypto.DogeWallet].
 * Elle ne sait que SÉRIALISER — transformer des champs en octets. Pas signer,
 * pas diffuser, pas choisir quelles pièces dépenser. Ces trois-là viendront
 * séparément, et chacune mérite ses propres vecteurs.
 *
 * ─── POURQUOI CE DÉCOUPAGE ─────────────────────────────────────────────────
 *
 * Parce que la sérialisation est la seule partie qu'on peut prouver hors
 * ligne, contre une transaction RÉELLE. On en prend une publiée sur la
 * chaîne, on la découpe en champs, on recompose les octets, et on compare au
 * hexadécimal d'origine. Un entier écrit à l'envers, une longueur sur le
 * mauvais nombre d'octets, et l'écart se voit immédiatement.
 *
 * Les trois autres ne se prouvent qu'en envoyant pour de vrai.
 *
 * ─── CE QUI SE TROMPE SANS LEVER D'ERREUR ──────────────────────────────────
 *
 * Tout, ici. Une transaction mal sérialisée n'échoue pas : elle est refusée
 * par le réseau sans explication, ou — et c'est le cas qui coûte — acceptée
 * avec un montant faux. Quatre pièges, tous silencieux :
 *
 *   — L'ORDRE DES OCTETS. Les entiers sont écrits POIDS FAIBLE EN TÊTE. Une
 *     valeur de 10 DOGE écrite dans l'autre sens devient un nombre
 *     astronomique, ou zéro.
 *
 *   — L'IDENTIFIANT DE LA TRANSACTION DÉPENSÉE S'ÉCRIT À L'ENVERS de la
 *     façon dont tout le monde l'affiche. Les explorateurs montrent
 *     « 7d9cc3… », la transaction contient « …c39c7d ». Un identifiant
 *     non retourné désigne une transaction qui n'existe pas.
 *
 *   — LES LONGUEURS SONT DES « varint » : un octet jusqu'à 252, puis un
 *     préfixe qui annonce la taille. Écrire toujours un octet marche sur
 *     tous les essais et casse sur le premier script long.
 *
 *   — LES MONTANTS SONT EN UNITÉS DE 10⁻⁸, comme Bitcoin. Un DOGE vaut
 *     100 000 000. Passer un montant en DOGE au lieu d'unités envoie cent
 *     millions de fois moins que prévu — et ça part quand même.
 *
 * ─── D'OÙ VIENT LE VECTEUR DE TEST ─────────────────────────────────────────
 *
 * D'une transaction réellement publiée, récupérée par `tools/doge-sondes.sh`
 * depuis le réseau Dogecoin, avec son hexadécimal. Voir [DogeTransactionTest].
 * ═══════════════════════════════════════════════════════════════════════════
 */
object DogeTransaction {

    /** Unités par DOGE : huit décimales, comme Bitcoin. */
    const val UNITES_PAR_DOGE = 100_000_000L

    /** Dépense une sortie précédente. */
    data class Entree(
        /**
         * L'identifiant de la transaction dépensée, écrit comme un
         * explorateur l'affiche. Il est retourné à la sérialisation — c'est
         * [serialiser] qui s'en charge, pas l'appelant.
         */
        val txidAffiche: String,
        /** Le rang de la sortie dépensée dans cette transaction. */
        val index: Int,
        /**
         * Le script qui débloque. Vide tant que la transaction n'est pas
         * signée ; c'est l'état dans lequel on calcule ce qu'il faut signer.
         */
        val script: ByteArray = ByteArray(0),
        val sequence: Long = 0xFFFFFFFFL,
    ) {
        /*
        `equals` et `hashCode` sont écrits à la main parce qu'un ByteArray
        se compare par identité : deux entrées portant les mêmes octets
        seraient déclarées différentes, ce qu'aucun test ne pardonnerait.
        */
        override fun equals(other: Any?): Boolean =
            other is Entree && txidAffiche == other.txidAffiche && index == other.index &&
                script.contentEquals(other.script) && sequence == other.sequence

        override fun hashCode(): Int =
            (txidAffiche.hashCode() * 31 + index) * 31 + script.contentHashCode()
    }

    /** Crée une sortie : un montant, et le script qui la verrouille. */
    data class Sortie(val unites: Long, val script: ByteArray) {
        override fun equals(other: Any?): Boolean =
            other is Sortie && unites == other.unites && script.contentEquals(other.script)

        override fun hashCode(): Int = unites.hashCode() * 31 + script.contentHashCode()
    }

    /** Une transaction complète, prête à être sérialisée. */
    data class Transaction(
        val entrees: List<Entree>,
        val sorties: List<Sortie>,
        val version: Int = 1,
        val locktime: Long = 0L,
    )

    /** Les octets exacts que le réseau attend. */
    fun serialiser(tx: Transaction): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(entier32(tx.version))
        out.write(varint(tx.entrees.size.toLong()))
        for (e in tx.entrees) {
            out.write(txidInverse(e.txidAffiche))
            out.write(entier32(e.index))
            out.write(varint(e.script.size.toLong()))
            out.write(e.script)
            out.write(entier32u(e.sequence))
        }
        out.write(varint(tx.sorties.size.toLong()))
        for (s in tx.sorties) {
            out.write(entier64(s.unites))
            out.write(varint(s.script.size.toLong()))
            out.write(s.script)
        }
        out.write(entier32u(tx.locktime))
        return out.toByteArray()
    }

    /**
     * Le script qui verrouille une sortie vers une adresse Dogecoin
     * ordinaire : `OP_DUP OP_HASH160 <20 octets> OP_EQUALVERIFY OP_CHECKSIG`.
     *
     * C'est la forme que toute adresse commençant par D attend. Les vingt
     * octets sont ceux que l'adresse encode — on les retrouve en la décodant,
     * jamais en la recopiant telle quelle.
     */
    fun scriptVersAdresse(hash160: ByteArray): ByteArray {
        require(hash160.size == 20) { "HASH160 attendu sur 20 octets, reçu ${hash160.size}" }
        val out = ByteArrayOutputStream()
        out.write(0x76)            // OP_DUP
        out.write(0xA9)            // OP_HASH160
        out.write(0x14)            // longueur : 20
        out.write(hash160)
        out.write(0x88)            // OP_EQUALVERIFY
        out.write(0xAC)            // OP_CHECKSIG
        return out.toByteArray()
    }

    /*
    ═══════════════════════════════════════════════════════════════════════
    L'IDENTIFIANT S'ÉCRIT À L'ENVERS DE CE QU'ON LIT
    ═══════════════════════════════════════════════════════════════════════

    Un explorateur affiche « 7d9cc37b… ». La transaction qui le dépense
    contient ces mêmes octets dans l'ordre inverse.

    Ce n'est pas une bizarrerie à contourner : c'est la convention de la
    chaîne, et l'affichage est la couche qui retourne. Le faire ici, une
    fois, évite que chaque appelant se demande dans quel sens il tient son
    identifiant — question à laquelle on répond mal une fois sur deux, et
    dont la mauvaise réponse désigne une transaction inexistante.
    ═══════════════════════════════════════════════════════════════════════
    */
    fun txidInverse(txidAffiche: String): ByteArray {
        require(txidAffiche.length == 64) {
            "identifiant de 64 caractères attendu, reçu ${txidAffiche.length}"
        }
        val droit = ByteArray(32)
        for (i in 0 until 32) {
            droit[i] = txidAffiche.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return droit.reversedArray()
    }

    /**
     * Longueur à la mode de la chaîne : un octet jusqu'à 252, puis un
     * préfixe qui annonce combien d'octets suivent.
     *
     * Les scripts courants tiennent sous 253 octets, donc un sérialiseur
     * qui écrit toujours un octet passe TOUS les essais ordinaires. Il
     * casse au premier script long — une transaction à plusieurs entrées,
     * typiquement — c'est-à-dire chez un utilisateur et pas en test.
     */
    fun varint(n: Long): ByteArray = when {
        n < 0 -> throw IllegalArgumentException("longueur négative : $n")
        n < 0xFD -> byteArrayOf(n.toByte())
        n <= 0xFFFF -> byteArrayOf(0xFD.toByte()) + entier16(n.toInt())
        n <= 0xFFFFFFFFL -> byteArrayOf(0xFE.toByte()) + entier32u(n)
        else -> byteArrayOf(0xFF.toByte()) + entier64(n)
    }

    /** Entier sur 2 octets, poids faible en tête. */
    fun entier16(n: Int) = byteArrayOf(n.toByte(), (n ushr 8).toByte())

    /** Entier sur 4 octets, poids faible en tête. */
    fun entier32(n: Int) = byteArrayOf(
        n.toByte(), (n ushr 8).toByte(), (n ushr 16).toByte(), (n ushr 24).toByte()
    )

    /** Entier non signé sur 4 octets, poids faible en tête. */
    fun entier32u(n: Long) = byteArrayOf(
        n.toByte(), (n ushr 8).toByte(), (n ushr 16).toByte(), (n ushr 24).toByte()
    )

    /** Entier sur 8 octets, poids faible en tête — le format des montants. */
    fun entier64(n: Long) = ByteArray(8) { i -> (n ushr (8 * i)).toByte() }

    /** Les octets en hexadécimal, pour la diffusion et pour les tests. */
    fun hex(b: ByteArray): String =
        b.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}
