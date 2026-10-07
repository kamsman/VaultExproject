package com.vaultex.core.crypto

/*
═══════════════════════════════════════════════════════════════════════════
PI NETWORK — L'ADRESSE, ÉTAPE A
═══════════════════════════════════════════════════════════════════════════

Pi est un dérivé de Stellar : mêmes clés Ed25519, même format d'adresse —
une chaîne base32 de 56 caractères commençant par G.

L'adresse est dérivée de la phrase VaultEx EXISTANTE. L'utilisateur n'a
aucune seconde phrase à saisir, et l'application ne détient aucun secret de
plus. En contrepartie, cette adresse démarre VIDE : les Pi déjà minés vivent
sous la phrase du Pi Wallet, qui est une autre phrase. Pour s'en servir, il
faut s'y envoyer ses Pi depuis le Pi Wallet.

─── TROIS CHOSES QUI NE PARDONNENT PAS ──────────────────────────────────

LE TYPE DE PIÈCE EST 314159, et le chemin n'a que TROIS niveaux, tous
durcis : m/44'/314159'/0'. Pas de /0/0 à la fin comme pour Ethereum, pas de
quatrième niveau comme pour Solana. Un chemin faux ne lève aucune erreur —
il produit une adresse parfaitement valide, simplement différente. Des fonds
envoyés dessus seraient inatteignables depuis le Pi Wallet, et personne ne
comprendrait pourquoi.

LA SOMME DE CONTRÔLE EST UN CRC16-XMODEM, ÉCRIT EN PETIT-BOUTISME. Deux
octets, inversés par rapport à l'ordre naturel. Les inverser donne une
adresse que tous les logiciels refuseront — ce qui est le bon échec, visible
tout de suite.

LE BASE32 EST CELUI DE LA RFC 4648, SANS REMPLISSAGE. Pas de base64, pas de
base58 comme Solana — la même famille, un alphabet différent.

─── CE QUE CE FICHIER NE FAIT PAS ───────────────────────────────────────

Il ne signe rien, et il ne parle à personne. Il dérive des clés, en tire une
adresse, et sait lire une adresse. Le format d'une transaction et sa
signature vivent dans PiXdr ; l'orchestration d'un envoi dans
PiEnvoiUseCase. Les trois sont séparés pour que chacun se relise seul.

─── CE QUI EST PROUVÉ, ET CE QUI NE L'EST PAS ───────────────────────────

PROUVÉ : toute la mécanique de dérivation. PiWalletSep5Test fait passer les
vecteurs officiels du SEP-0005 par le code ci-dessous, et les dix adresses
de référence tombent juste. SLIP-0010, StrKey, CRC16, base32 : vérifiés
contre la spécification, pas contre une relecture.

PAS PROUVÉ : le nombre 314159. Aucun vecteur public ne l'atteste. Il se
vérifie d'une seule façon — envoyer un Pi depuis le Pi Wallet vers l'adresse
produite ici, et le voir arriver.
═══════════════════════════════════════════════════════════════════════════
*/
object PiWallet {

    /** Type de pièce Pi Network, enregistré au SLIP-0044. */
    private const val COIN_TYPE = 314159

    /** Octet de version d'une adresse de compte : 6 << 3, qui donne « G ». */
    private const val VERSION_COMPTE: Byte = 0x30

    private const val ALPHABET_BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /**
     * Adresse Pi dérivée de [seed], la même graine BIP-39 que le reste du
     * portefeuille.
     *
     * Chemin m/44'/314159'/0' — trois niveaux, tous durcis.
     */
    fun deriveAddress(seed: ByteArray): String = derivePaire(seed).adresse

    /**
     * Clés et adresse Pi dérivées de [seed].
     *
     * La clé privée ne sert QU'À SIGNER, et l'appelant doit la traiter comme
     * telle : jamais journalisée, jamais conservée au-delà de l'opération.
     */
    data class PaireCles(
        val clePrivee: ByteArray,
        val clePublique: ByteArray,
        val adresse: String
    )

    fun derivePaire(seed: ByteArray, compte: Int = 0): PaireCles =
        derivePaireAvecTypePiece(seed, COIN_TYPE, compte)

    /**
     * Même dérivation, pour un type de pièce quelconque.
     *
     * ═══════════════════════════════════════════════════════════════════
     * CE QUI REND CE CHEMIN DE CODE VÉRIFIABLE
     * ═══════════════════════════════════════════════════════════════════
     *
     * Le Pi n'a pas de vecteurs de test publiés. Stellar, si : le SEP-0005
     * en publie dix adresses par phrase de référence, et Pi partage avec
     * Stellar la TOTALITÉ de cette mécanique — SLIP-0010 sur Ed25519, le
     * format StrKey, le CRC16, le base32. Seul le numéro de type de pièce
     * diffère : 314159 au lieu de 148.
     *
     * Exposer ce paramètre permet à PiWalletSep5Test de faire passer les
     * vecteurs officiels par EXACTEMENT le code qui dérive les adresses Pi,
     * et pas par une copie écrite pour le test. Le seul élément qui reste
     * hors de portée d'un test est donc le nombre 314159 lui-même.
     *
     * N'est appelée ailleurs qu'avec [COIN_TYPE].
     * ═══════════════════════════════════════════════════════════════════
     */
    internal fun derivePaireAvecTypePiece(
        seed: ByteArray,
        typePiece: Int,
        compte: Int = 0
    ): PaireCles {
        val chemin = intArrayOf(
            44 or Int.MIN_VALUE,
            typePiece or Int.MIN_VALUE,
            compte or Int.MIN_VALUE
        )
        val derivee = Slip10.deriveEd25519Key(seed, chemin)
        val publique = Ed25519Utils.publicKeyFromPrivate(derivee.privateKey)
        return PaireCles(derivee.privateKey, publique, encoderStrKey(publique))
    }

    /**
     * Clé publique de 32 octets contenue dans une adresse G….
     *
     * Exige une adresse VALIDE : sans ce contrôle, une adresse mal recopiée
     * rendrait trente-deux octets quelconques, qui deviendraient une
     * destination de paiement parfaitement bien formée et inexistante.
     */
    fun clePubliqueDeLAdresse(adresse: String): ByteArray {
        val propre = adresse.trim()
        require(adresseValide(propre)) { "adresse Pi invalide" }
        return decoderBase32(propre).copyOfRange(1, 33)
    }

    /**
     * Vrai si [adresse] est une adresse de compte Pi syntaxiquement valide,
     * somme de contrôle comprise.
     *
     * Vérifier la somme de contrôle et pas seulement la forme : une adresse
     * copiée avec un caractère en moins passerait le contrôle de longueur et
     * de préfixe, et les fonds partiraient dans le vide.
     */
    fun adresseValide(adresse: String): Boolean {
        val brut = try { decoderBase32(adresse.trim()) } catch (_: Exception) { return false }
        if (brut.size != 35) return false                 // 1 version + 32 clé + 2 contrôle
        if (brut[0] != VERSION_COMPTE) return false
        val charge = brut.copyOfRange(0, 33)
        val attendu = crc16XModem(charge)
        // Petit-boutisme : l'octet de poids faible en premier.
        val lu = ((brut[34].toInt() and 0xFF) shl 8) or (brut[33].toInt() and 0xFF)
        return lu == attendu
    }

    private fun encoderStrKey(clePublique: ByteArray): String {
        require(clePublique.size == 32) { "clé Ed25519 attendue sur 32 octets" }
        val charge = ByteArray(33)
        charge[0] = VERSION_COMPTE
        System.arraycopy(clePublique, 0, charge, 1, 32)
        val somme = crc16XModem(charge)
        val complet = ByteArray(35)
        System.arraycopy(charge, 0, complet, 0, 33)
        complet[33] = (somme and 0xFF).toByte()          // faible d'abord
        complet[34] = ((somme shr 8) and 0xFF).toByte()
        return encoderBase32(complet)
    }

    /*
    CRC16-XMODEM : polynôme 0x1021, registre initial à zéro, sans inversion
    finale. C'est la variante exacte de Stellar — une autre variante de CRC16
    produirait une adresse refusée partout, ce qui est préférable à une
    adresse acceptée et fausse.
    */
    private fun crc16XModem(donnees: ByteArray): Int {
        var crc = 0x0000
        for (octet in donnees) {
            crc = crc xor ((octet.toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1
                crc = crc and 0xFFFF
            }
        }
        return crc
    }

    /** Base32 RFC 4648, sans caractère de remplissage. */
    private fun encoderBase32(donnees: ByteArray): String {
        val sortie = StringBuilder()
        var tampon = 0
        var bits = 0
        for (octet in donnees) {
            tampon = (tampon shl 8) or (octet.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                sortie.append(ALPHABET_BASE32[(tampon shr (bits - 5)) and 0x1F])
                bits -= 5
            }
        }
        // Les bits restants sont complétés par des zéros à droite : 35 octets
        // font 280 bits, exactement 56 caractères, donc ce cas ne survient pas
        // ici — il est traité pour que la fonction reste correcte seule.
        if (bits > 0) sortie.append(ALPHABET_BASE32[(tampon shl (5 - bits)) and 0x1F])
        return sortie.toString()
    }

    private fun decoderBase32(texte: String): ByteArray {
        val sortie = java.io.ByteArrayOutputStream()
        var tampon = 0
        var bits = 0
        for (c in texte) {
            val valeur = ALPHABET_BASE32.indexOf(c)
            if (valeur < 0) throw IllegalArgumentException("caractère hors alphabet base32")
            tampon = (tampon shl 5) or valeur
            bits += 5
            if (bits >= 8) {
                sortie.write((tampon shr (bits - 8)) and 0xFF)
                bits -= 8
            }
        }
        return sortie.toByteArray()
    }
}
