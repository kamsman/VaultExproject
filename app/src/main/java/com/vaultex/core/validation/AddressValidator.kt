package com.vaultex.core.validation

import com.vaultex.core.crypto.Base58
import java.security.MessageDigest

object AddressValidator {

    /*
    ═══════════════════════════════════════════════════════════════════════
    LES CHAÎNES DONT ON SAIT VRAIMENT VÉRIFIER UNE ADRESSE
    ═══════════════════════════════════════════════════════════════════════

    `isValid` finit par `else -> address.length >= 26`, c'est-à-dire « on
    ne sait pas, on laisse passer ». C'est le bon repli pour ne pas bloquer
    une monnaie nouvelle — mais ça veut dire qu'un écran qui PROPOSE une
    chaîne absente du `when` ci-dessous n'offre aucune vérification réelle.

    VU LE 26 NOVEMBRE 2026 : le carnet d'adresses proposait six chaînes,
    écrites à la main chez lui, et DOGE n'y était pas — alors que ce
    validateur sait le vérifier depuis le début. Un utilisateur venait donc
    de coller une adresse DOGE à la main, sans pouvoir l'enregistrer.

    Le défaut n'était pas l'absence de DOGE : c'était qu'une liste vive
    ailleurs. Les écrans qui offrent un choix de chaîne lisent désormais
    celle-ci, et il n'y a plus qu'un endroit où oublier une monnaie.

    L'ORDRE EST CELUI DE L'USAGE, pas l'alphabet : ce que les gens
    envoient le plus vient en premier dans les listes déroulantes.
    ═══════════════════════════════════════════════════════════════════════
    */
    val CHAINES_VERIFIABLES: List<String> =
        listOf("USDT", "USDT-ETH", "USDT-BNB", "ETH", "BNB", "BTC", "TRX", "SOL", "DOGE")

    fun isValid(address: String, chain: String): Boolean = when (chain) {
        "ETH", "BNB", "USDT-ETH", "USDT-BNB" -> isValidEvm(address)
        "BTC"        -> isValidBtc(address)
        "TRX", "USDT" -> isValidTron(address)
        /*
        « USDT-SOL » DOIT ETRE ICI, et c'est la meme faute que celle du Pi
        decrite juste en dessous : sans cette branche, il tomberait sur la
        regle par defaut — « plus de 26 caracteres » — qui accepte a peu
        pres n'importe quoi, y compris une adresse Solana dont un caractere
        a ete mal recopie.

        Un USDT envoye sur une adresse Solana fausse ne revient pas.
        */
        "SOL", "USDT-SOL" -> isValidSolana(address)
        /*
        Le Pi a sa propre somme de contrôle, et il faut la lui appliquer.

        Sans cette branche, « PI » tombait sur la règle par défaut
        ci-dessous — longueur supérieure à 26 — qui accepte à peu près
        n'importe quoi, y compris une adresse Pi dont un caractère a été
        mal recopié. PiWallet.adresseValide vérifie, elle, le préfixe, la
        longueur exacte et le CRC16 : c'est le seul contrôle capable de
        refuser une faute de frappe.

        Rien ne s'envoie encore en Pi, donc ce code n'est pas sur un chemin
        d'argent aujourd'hui. Il y sera à l'étape suivante, et il valait
        mieux l'écrire maintenant qu'au moment où une transaction en
        dépendra.
        */
        "PI"         -> com.vaultex.core.crypto.PiWallet.adresseValide(address)
        /*
        MEME RAISON QUE LES DEUX BRANCHES AU-DESSUS, et maintenant sur un
        chemin d'argent : sans elle, « DOGE » tombait sur la regle par
        defaut — plus de 26 caracteres — qui accepte presque tout.

        `estAdresseValide` verifie l'octet de version ET la somme de
        controle. C'est le seul controle capable de refuser une adresse
        recopiee a la main avec un caractere faux : elle garde sa
        longueur, son « D » et son alphabet, et seule la somme la demasque.
        */
        "DOGE"       -> com.vaultex.core.crypto.DogeWallet.estAdresseValide(address)
        else         -> address.length >= 26
    }

    fun isValidEvm(address: String): Boolean =
        address.matches(Regex("^0x[0-9a-fA-F]{40}$"))

    fun isValidBtc(address: String): Boolean =
        address.matches(Regex("^(1[a-km-zA-HJ-NP-Z1-9]{25,34}|3[a-km-zA-HJ-NP-Z1-9]{25,34}|bc1[a-z0-9]{39,59})$"))

    fun isValidTron(address: String): Boolean {
        if (!address.startsWith("T") || address.length != 34) return false
        return try {
            val decoded = Base58.decode(address)
            if (decoded.size != 25 || decoded[0] != 0x41.toByte()) return false
            val payload = decoded.copyOfRange(0, 21)
            val checksum = decoded.copyOfRange(21, 25)
            sha256d(payload).copyOfRange(0, 4).contentEquals(checksum)
        } catch (_: Exception) { false }
    }

    fun isValidSolana(address: String): Boolean =
        address.matches(Regex("^[1-9A-HJ-NP-Za-km-z]{32,44}$"))

    private fun sha256d(data: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(md.digest(data))
    }
}
