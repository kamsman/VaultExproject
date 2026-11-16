package com.vaultex.core.tx

import org.bitcoinj.core.ECKey
import org.bitcoinj.core.Sha256Hash
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * CE QU'UNE TRANSACTION DOGECOIN SIGNE, ET COMMENT
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Troisième brique, après [com.vaultex.core.crypto.DogeWallet] qui dérive et
 * [DogeTransaction] qui sérialise.
 *
 * ─── LE PIÈGE CENTRAL : ON NE SIGNE PAS LA TRANSACTION ─────────────────────
 *
 * On signe une VERSION MODIFIÉE d'elle-même, et c'est contre-intuitif au
 * point de se tromper sans s'en apercevoir.
 *
 * Pour signer l'entrée numéro N, on fabrique une copie de la transaction où :
 *
 *   — le script de l'entrée N est remplacé par le script de VERROUILLAGE de
 *     la sortie qu'elle dépense — pas par son propre script, qui n'existe
 *     pas encore puisqu'on est en train de le construire ;
 *   — le script de TOUTES LES AUTRES entrées est VIDE ;
 *   — quatre octets annonçant le type de signature sont ajoutés à la fin.
 *
 * On prend le double SHA-256 de tout ça, et c'est ce nombre qu'on signe.
 *
 * Une transaction à trois entrées se signe donc trois fois, sur trois
 * nombres différents.
 *
 * ─── POURQUOI CE FICHIER EST VÉRIFIABLE HORS LIGNE ─────────────────────────
 *
 * Je pensais que signer était la partie qu'on ne pourrait prouver qu'en
 * envoyant pour de vrai. C'est faux.
 *
 * Une transaction P2PKH déjà publiée porte, dans le script de son entrée, LA
 * SIGNATURE ET LA CLÉ PUBLIQUE. Si le calcul ci-dessous est juste, cette
 * signature se vérifie contre lui ; s'il est faux, elle échoue. Il n'y a ni
 * demi-mesure ni coïncidence possible.
 *
 * Et le script de verrouillage dépensé n'a même pas besoin d'être
 * téléchargé : pour du P2PKH il se reconstruit depuis la clé publique du
 * script de déblocage. Voir [DogeSignerTest], dont le vecteur est une
 * transaction réelle du bloc 6460312.
 *
 * ─── CE QUI SE TROMPE SANS RIEN LEVER ──────────────────────────────────────
 *
 *   — signer la transaction telle quelle, sans la copie modifiée ;
 *   — oublier de vider les autres entrées ;
 *   — oublier les quatre octets de type, ou les écrire sur un seul ;
 *   — un seul SHA-256 au lieu de deux ;
 *   — une signature dont le second nombre est « haut » : le réseau la
 *     refuse comme non canonique, et rien ne le dit avant la diffusion.
 *
 * Les cinq produisent une transaction parfaitement formée que personne
 * n'acceptera, ou pire, dépensable par un autre.
 * ═══════════════════════════════════════════════════════════════════════════
 */
object DogeSigner {

    /**
     * Le seul type de signature employé ici : « je signe toutes les entrées
     * et toutes les sorties ». C'est ce que veut un portefeuille ordinaire —
     * les autres types servent à des montages où plusieurs personnes
     * complètent une même transaction, ce que VaultEx ne fait pas.
     */
    const val SIGHASH_ALL = 1

    /**
     * Le nombre que l'entrée [rang] doit signer.
     *
     * [scriptVerrou] est le script de la sortie dépensée — pour une adresse
     * Dogecoin ordinaire, `76a914<20 octets>88ac`. Il se reconstruit depuis
     * l'adresse, sans appel réseau : voir [DogeTransaction.scriptVersAdresse].
     */
    fun sighash(
        tx: DogeTransaction.Transaction,
        rang: Int,
        scriptVerrou: ByteArray,
    ): ByteArray {
        require(rang in tx.entrees.indices) {
            "entrée $rang hors des ${tx.entrees.size} entrées"
        }
        /*
        LES AUTRES ENTRÉES SONT VIDÉES, PAS RECOPIÉES. Les laisser telles
        quelles produit un nombre différent à chaque signature déjà posée :
        la première entrée signée invaliderait la deuxième, qui
        invaliderait la première. Sur une transaction à une seule entrée,
        l'erreur ne se voit JAMAIS — et c'est le cas de presque tous les
        essais.
        */
        val copie = tx.copy(
            entrees = tx.entrees.mapIndexed { i, e ->
                e.copy(script = if (i == rang) scriptVerrou else ByteArray(0))
            }
        )
        val corps = DogeTransaction.serialiser(copie) +
            DogeTransaction.entier32(SIGHASH_ALL)
        return sha256d(corps)
    }

    /**
     * Signe l'entrée [rang] et rend le script de déblocage à y poser.
     *
     * La signature est CANONIQUE — `toCanonicalised` ramène son second
     * nombre dans la moitié basse. Une signature non canonique est
     * mathématiquement valide et refusée par le réseau, ce qui est le pire
     * des deux mondes : elle passe toutes les vérifications locales.
     */
    fun signerEntree(
        tx: DogeTransaction.Transaction,
        rang: Int,
        scriptVerrou: ByteArray,
        cle: ECKey,
    ): ByteArray {
        val h = sighash(tx, rang, scriptVerrou)
        val signature = cle.sign(Sha256Hash.wrap(h)).toCanonicalised()
        return scriptDeblocage(signature.encodeToDER(), cle.pubKey)
    }

    /**
     * Le script qui débloque une sortie ordinaire : la signature suivie du
     * type, puis la clé publique. Chacune précédée de sa longueur.
     */
    fun scriptDeblocage(signatureDer: ByteArray, clePublique: ByteArray): ByteArray {
        val avecType = signatureDer + byteArrayOf(SIGHASH_ALL.toByte())
        val out = ByteArrayOutputStream()
        out.write(pousser(avecType))
        out.write(pousser(clePublique))
        return out.toByteArray()
    }

    /**
     * Sépare un script de déblocage en (signature sans son type, type, clé).
     *
     * Sert aux tests, et c'est sa raison d'être : c'est ce qui permet de
     * reprendre une transaction PUBLIÉE et de vérifier sa signature contre
     * notre propre calcul. Rien dans l'envoi ne l'appelle.
     */
    fun decouperDeblocage(script: ByteArray): Triple<ByteArray, Int, ByteArray> {
        require(script.size > 2) { "script de déblocage trop court" }
        val nSig = script[0].toInt() and 0xFF
        require(script.size > 1 + nSig) { "longueur de signature incohérente" }
        val avecType = script.copyOfRange(1, 1 + nSig)
        val nCle = script[1 + nSig].toInt() and 0xFF
        require(script.size >= 2 + nSig + nCle) { "longueur de clé incohérente" }
        val cle = script.copyOfRange(2 + nSig, 2 + nSig + nCle)
        return Triple(
            avecType.copyOfRange(0, avecType.size - 1),
            avecType[avecType.size - 1].toInt() and 0xFF,
            cle
        )
    }

    /*
    UN SCRIPT « POUSSE » SES DONNEES, et la façon de le faire dépend de leur
    taille. Jusqu'à 75 octets, un seul octet suffit à annoncer la longueur ;
    au-delà il faut un opérateur dédié.

    Une signature fait entre 70 et 72 octets et une clé compressée 33 : les
    deux passent toujours par le cas simple. Le second cas n'arrivera donc
    jamais ici — il est écrit quand même, parce qu'une fonction qui rend
    silencieusement un script invalide hors de son domaine est une bombe à
    retardement, et parce que le coût est de trois lignes.
    */
    private fun pousser(donnees: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        when {
            donnees.size < 0x4C -> out.write(donnees.size)
            donnees.size <= 0xFF -> { out.write(0x4C); out.write(donnees.size) }
            else -> {
                out.write(0x4D)
                out.write(DogeTransaction.entier16(donnees.size))
            }
        }
        out.write(donnees)
        return out.toByteArray()
    }

    private fun sha256d(data: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(md.digest(data))
    }
}
