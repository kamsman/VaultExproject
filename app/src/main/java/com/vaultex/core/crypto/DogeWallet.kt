package com.vaultex.core.crypto

import org.bitcoinj.crypto.ChildNumber
import org.bitcoinj.crypto.HDKeyDerivation
import java.security.MessageDigest

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * DOGECOIN — ADRESSE ET CLÉ, DÉRIVÉES DE LA MÊME GRAINE QUE LE RESTE
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Objet à part, comme [PiWallet], et pour la même raison : une chaîne qu'on
 * ajoute se tient d'abord toute seule. Rien ici ne touche aux cinq chaînes
 * existantes, donc rien ici ne peut les casser.
 *
 * ─── CE N'EST PAS DU BITCOIN AVEC UN AUTRE NOM ─────────────────────────────
 *
 * La tentation est de recycler [WalletManager.deriveBtcAddress]. Trois
 * différences l'interdisent, et chacune produirait une adresse VALIDE mais
 * fausse — donc des fonds envoyés à personne, sans aucune erreur levée :
 *
 *   — LE CHEMIN. Bitcoin dérive en `m/84'/0'` (BIP-84, SegWit natif).
 *     Dogecoin n'a pas de SegWit : c'est `m/44'/3'`, le BIP-44 historique.
 *     Un `m/84'` donnerait une autre clé, donc une autre adresse.
 *
 *   — L'ÉCRITURE. Bitcoin sort du bech32 (« bc1… »). Dogecoin sort du
 *     base58check hérité, et commence par « D ».
 *
 *   — L'OCTET DE VERSION. 0x1E pour une adresse, 0x9E pour une clé WIF.
 *     Bitcoin emploie 0x00 et 0x80. C'est cet octet, et lui seul, qui fait
 *     qu'une adresse commence par « D » plutôt que par « 1 ».
 *
 * ─── D'OÙ VIENNENT CES DEUX OCTETS ─────────────────────────────────────────
 *
 * Pas de ma mémoire. Ils ont été lus dans `bip_utils`, une bibliothèque de
 * référence indépendante, puis la chaîne complète — BIP-39, BIP-32,
 * HASH160, base58check — a été réécrite de zéro et confrontée à elle sur
 * quatre phrases : adresses ET clés WIF identiques des deux côtés.
 *
 * Les vecteurs qui en sortent sont dans `DogeWalletTest`. Ils existent
 * parce que je n'avais pas de compilateur Kotlin en écrivant ceci : c'est
 * le build qui vérifie que bitcoinj donne bien les mêmes octets que les
 * deux implémentations de référence.
 *
 * ─── CE QUE CE FICHIER NE FAIT PAS ─────────────────────────────────────────
 *
 * Il ne sait pas ENVOYER. Dogecoin est une chaîne UTXO : un envoi demande
 * de lire les sorties non dépensées, d'estimer des frais, de signer chaque
 * entrée et de diffuser — et surtout un fournisseur qui serve Dogecoin,
 * celui de Bitcoin ne le fait pas.
 *
 * TANT QUE L'ENVOI N'EXISTE PAS, AUCUN ÉCRAN NE DOIT PROPOSER DE RECEVOIR
 * DU DOGE. Une adresse qui reçoit sans pouvoir rendre est un piège : les
 * fonds y entrent et n'en sortent plus. C'est la seule règle à ne pas
 * enfreindre en attendant la suite.
 * ═══════════════════════════════════════════════════════════════════════════
 */
object DogeWallet {

    /** Type de pièce SLIP-44 de Dogecoin. Bitcoin est 0, Dogecoin est 3. */
    const val TYPE_PIECE = 3

    /** Octet de version d'une adresse P2PKH. C'est lui qui donne le « D ». */
    const val VERSION_ADRESSE = 0x1E

    /** Octet de version d'une clé privée au format WIF. */
    const val VERSION_WIF = 0x9E

    /** Décimales du Dogecoin — huit, comme Bitcoin. */
    const val DECIMALES = 8

    /**
     * Adresse de réception, dérivée de [seed] en `m/44'/3'/0'/0/[index]`.
     *
     * [seed] est la graine BIP-39 des 64 octets, celle que produit déjà
     * `MnemonicUtils.generateSeed` pour toutes les autres chaînes : la
     * même phrase de douze mots ouvre donc aussi ce portefeuille-ci.
     */
    fun deriveAddress(seed: ByteArray, index: Int = 0): String =
        encoderAdresse(cle(seed, index).pubKeyHash)

    /**
     * Clé privée au format WIF, pour l'export vers un portefeuille de
     * référence. Compressée — le suffixe 0x01 l'indique, et son absence
     * produirait une adresse différente à l'import.
     */
    fun derivePrivateKeyWif(seed: ByteArray, index: Int = 0): String {
        val brut = cle(seed, index).privKeyBytes
        /*
        TRENTE-DEUX OCTETS, TOUJOURS. `privKeyBytes` passe par un BigInteger :
        une clé dont le premier octet est nul en rend trente et un, et une
        clé sur trente et un octets donne un WIF que les autres
        portefeuilles refusent. Ça arrive une fois sur deux cent cinquante-
        six, donc jamais pendant les essais, et chez un utilisateur ensuite.
        */
        val cle32 = ByteArray(32)
        System.arraycopy(brut, maxOf(0, brut.size - 32), cle32,
            maxOf(0, 32 - brut.size), minOf(32, brut.size))
        return base58Check(byteArrayOf(VERSION_WIF.toByte()) + cle32 + byteArrayOf(0x01))
    }

    /**
     * Vrai si [adresse] est une adresse Dogecoin bien formée.
     *
     * Le contrôle porte sur la SOMME DE CONTRÔLE, pas sur l'allure. Une
     * adresse recopiée à la main avec un caractère faux passe tous les
     * tests de forme et échoue celui-ci — c'est précisément son rôle.
     */
    fun estAdresseValide(adresse: String): Boolean {
        if (adresse.length !in 26..34) return false
        val brut = try { Base58.decode(adresse) } catch (_: Exception) { return false }
        if (brut.size != 25) return false
        if ((brut[0].toInt() and 0xFF) != VERSION_ADRESSE) return false
        val charge = brut.copyOfRange(0, 21)
        val somme = brut.copyOfRange(21, 25)
        return sha256d(charge).copyOfRange(0, 4).contentEquals(somme)
    }

    /** `hash160` → adresse écrite. Séparé pour être testable seul. */
    internal fun encoderAdresse(hash160: ByteArray): String {
        require(hash160.size == 20) { "HASH160 attendu sur 20 octets, reçu ${hash160.size}" }
        return base58Check(byteArrayOf(VERSION_ADRESSE.toByte()) + hash160)
    }

    private fun cle(seed: ByteArray, index: Int) =
        listOf(
            ChildNumber(44, true), ChildNumber(TYPE_PIECE, true),
            ChildNumber(0, true), ChildNumber(0, false),
            ChildNumber(index, false)
        ).fold(HDKeyDerivation.createMasterPrivateKey(seed)) { k, enfant ->
            HDKeyDerivation.deriveChildKey(k, enfant)
        }

    private fun base58Check(charge: ByteArray): String =
        Base58.encode(charge + sha256d(charge).copyOfRange(0, 4))

    private fun sha256d(data: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(md.digest(data))
    }
}
