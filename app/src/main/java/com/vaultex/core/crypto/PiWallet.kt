package com.vaultex.core.crypto

/*
═══════════════════════════════════════════════════════════════════════════
PI NETWORK — DÉRIVATION DE CLÉS ET ADRESSE
═══════════════════════════════════════════════════════════════════════════

Pi Network est un dérivé de Stellar : mêmes clés Ed25519, même format
d'adresse (StrKey, « G… »), même famille de transactions. Tout ce qui suit
vaut donc aussi pour Stellar, au numéro de chaîne près.

CE QUE CE FICHIER FAIT, ET CE QU'IL NE FAIT PAS. Il dérive une adresse et sa
clé privée, rien d'autre. Lire un solde, construire et signer une
transaction viendront après — et séparément, parce qu'une erreur ici rendrait
tout le reste faux sans qu'on le voie : une adresse mal calculée reçoit
parfaitement des fonds, et personne ne peut plus jamais les en sortir.

C'EST POURQUOI IL N'EST ENCORE BRANCHÉ NULLE PART. Tant que l'adresse
produite n'a pas été comparée à celle qu'affiche le portefeuille Pi officiel
pour la même phrase, cette fonction ne doit apparaître sur aucun écran. Voir
la section « VÉRIFIER AVANT DE BRANCHER » en bas.

───────────────────────────────────────────────────────────────────────────
L'ADRESSE DÉRIVÉE ICI EST UN PORTEFEUILLE PI NEUF, ET VIDE
───────────────────────────────────────────────────────────────────────────

Elle vient des douze mots de VaultEx. Le Pi qu'un utilisateur a miné vit
dans SON portefeuille Pi, protégé par vingt-quatre mots qui ne sont pas
ceux-là — et qu'on ne lui demandera jamais.

VaultEx n'est donc pas « là où est son Pi » mais « là où il l'envoie pour
l'échanger ». C'est une différence qu'il faudra écrire à l'écran : quelqu'un
qui ouvre l'onglet Pi en s'attendant à voir son solde de minage verrait zéro,
et en conclurait que l'application a perdu ses fonds.
*/
object PiWallet {

    /*
    LE NUMÉRO DE CHAÎNE EST 314159, ET CE N'EST PAS UNE COQUETTERIE.

    C'est ce que Pi a enregistré comme coin type SLIP-0044 — les six
    premières décimales de π. Stellar, lui, utilise 148.

    Se tromper de numéro ne lève AUCUNE erreur : on obtient simplement une
    autre adresse, parfaitement valide, dont personne ne détient la clé chez
    Pi. Les fonds envoyés dessus seraient perdus sans le moindre message.
    */
    private const val COIN_TYPE_PI = 314159

    /** Version StrKey d'une clé publique de compte : 6 << 3, ce qui donne « G ». */
    private const val VERSION_COMPTE: Byte = 0x30

    /**
     * Chemin SEP-0005 : m/44'/314159'/0'
     *
     * Trois niveaux, tous durcis — c'est la convention Stellar, et elle
     * diffère de Solana (quatre niveaux) comme d'Ethereum (cinq, dont deux
     * non durcis). Reprendre le chemin d'une autre chaîne produirait là
     * encore une adresse valide et inaccessible.
     */
    private fun chemin(compte: Int = 0) = intArrayOf(
        44 or Int.MIN_VALUE,
        COIN_TYPE_PI or Int.MIN_VALUE,
        compte or Int.MIN_VALUE
    )

    /** Clé privée Ed25519 (32 octets) du compte Pi [compte] de cette graine. */
    fun clePrivee(seed: ByteArray, compte: Int = 0): ByteArray =
        Slip10.deriveEd25519Key(seed, chemin(compte)).privateKey

    /** Adresse Pi publique, au format StrKey — 56 caractères commençant par G. */
    fun adresse(seed: ByteArray, compte: Int = 0): String =
        adresseDepuisClePublique(Ed25519Utils.publicKeyFromPrivate(clePrivee(seed, compte)))

    /** Encode une clé publique Ed25519 de 32 octets en adresse « G… ». */
    fun adresseDepuisClePublique(clePublique: ByteArray): String {
        require(clePublique.size == 32) { "Clé publique Ed25519 attendue : 32 octets" }
        val charge = ByteArray(1 + 32) { i -> if (i == 0) VERSION_COMPTE else clePublique[i - 1] }
        val somme = crc16XModem(charge)
        // La somme de contrôle est écrite en petit-boutiste : c'est ce que
        // spécifie Stellar, et l'inverser produirait une adresse que tous les
        // portefeuilles refuseraient — défaut au moins visible, contrairement
        // aux précédents.
        val complet = charge + byteArrayOf((somme and 0xFF).toByte(), ((somme shr 8) and 0xFF).toByte())
        return base32(complet)
    }

    /**
     * Vrai si [adresse] a la forme d'une adresse Pi/Stellar valide.
     *
     * Contrôle la longueur, l'alphabet, le préfixe ET la somme de contrôle.
     * Cette dernière est l'essentiel : elle attrape la faute de frappe et le
     * caractère manquant, qui sinon enverraient les fonds dans le vide.
     */
    fun adresseValide(adresse: String): Boolean {
        val a = adresse.trim().uppercase()
        if (a.length != 56 || !a.startsWith("G")) return false
        val octets = base32Decode(a) ?: return false
        if (octets.size != 35 || octets[0] != VERSION_COMPTE) return false
        val attendue = crc16XModem(octets.copyOfRange(0, 33))
        val lue = (octets[33].toInt() and 0xFF) or ((octets[34].toInt() and 0xFF) shl 8)
        return attendue == lue
    }

    /*
    ───────────────────────────────────────────────────────────────────────
    CRC-16/XMODEM — la somme de contrôle des adresses Stellar
    ───────────────────────────────────────────────────────────────────────
    Polynôme 0x1021, registre initial 0, sans réflexion ni XOR final. C'est
    la variante exacte retenue par Stellar ; les autres CRC-16 (MODBUS, CCITT
    « FALSE », ARC) donnent des résultats différents sur les mêmes octets.
    */
    private fun crc16XModem(donnees: ByteArray): Int {
        var crc = 0
        for (octet in donnees) {
            crc = crc xor ((octet.toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1
                crc = crc and 0xFFFF
            }
        }
        return crc
    }

    /*
    ───────────────────────────────────────────────────────────────────────
    BASE32 (RFC 4648) — sans remplissage
    ───────────────────────────────────────────────────────────────────────
    Android n'en fournit pas : android.util.Base64 est du base64, et aucune
    dépendance du projet ne l'expose. Trente lignes valent mieux qu'une
    bibliothèque de plus dans un APK qu'on veut petit.

    Trente-cinq octets donnent exactement 56 caractères, sans reste : aucun
    « = » de remplissage n'apparaît jamais sur une adresse.
    */
    private const val ALPHABET32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    private fun base32(donnees: ByteArray): String {
        val sb = StringBuilder()
        var tampon = 0
        var bits = 0
        for (octet in donnees) {
            tampon = (tampon shl 8) or (octet.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                sb.append(ALPHABET32[(tampon shr (bits - 5)) and 0x1F])
                bits -= 5
            }
        }
        if (bits > 0) sb.append(ALPHABET32[(tampon shl (5 - bits)) and 0x1F])
        return sb.toString()
    }

    private fun base32Decode(texte: String): ByteArray? {
        val sortie = java.io.ByteArrayOutputStream()
        var tampon = 0
        var bits = 0
        for (c in texte) {
            val v = ALPHABET32.indexOf(c)
            if (v < 0) return null
            tampon = (tampon shl 5) or v
            bits += 5
            if (bits >= 8) {
                sortie.write((tampon shr (bits - 8)) and 0xFF)
                bits -= 8
            }
        }
        return sortie.toByteArray()
    }

    /*
    ═══════════════════════════════════════════════════════════════════════
    VÉRIFIER AVANT DE BRANCHER
    ═══════════════════════════════════════════════════════════════════════

    Une adresse fausse ne se voit pas : elle a la bonne forme, elle passe
    tous les contrôles, elle reçoit des fonds — et personne ne peut plus les
    en sortir. Aucun écran ne doit donc afficher cette adresse avant le
    contrôle ci-dessous.

    LE CONTRÔLE. Prendre une phrase de récupération Pi CONNUE — une phrase de
    test, jamais celle d'un portefeuille qui contient quelque chose — la
    passer à BIP-39 pour obtenir une graine, appeler `adresse(graine)`, et
    comparer au « G… » qu'affiche le portefeuille Pi officiel pour cette même
    phrase.

    Identiques : la dérivation, le chemin, l'encodage et la somme de contrôle
    sont tous justes d'un coup. Différents : ne rien brancher, et chercher
    lequel des quatre est en cause — le chemin et le numéro de chaîne sont
    les suspects habituels.

    CE CONTRÔLE VAUT AUSSI POUR STELLAR, à ceci près qu'il faut y remplacer
    314159 par 148. Un portefeuille Stellar est plus facile à créer pour un
    test, et valide tout sauf le numéro de chaîne.
    */
}
