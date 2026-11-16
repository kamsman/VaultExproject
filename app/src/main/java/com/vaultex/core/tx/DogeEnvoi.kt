package com.vaultex.core.tx

import com.vaultex.core.crypto.Base58
import com.vaultex.core.crypto.DogeWallet
import org.bitcoinj.crypto.ChildNumber
import org.bitcoinj.crypto.HDKeyDerivation

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * ASSEMBLER UN ENVOI DOGECOIN, DE BOUT EN BOUT
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Dernière brique de calcul. Elle enchaîne les cinq précédentes :
 * [DogeSelection] choisit les pièces, [DogeTransaction] fabrique les octets,
 * [DogeSigner] les signe, [DogeWallet] fournit la clé.
 *
 * ─── ELLE NE FAIT AUCUN APPEL RÉSEAU, ET C'EST LE POINT ────────────────────
 *
 * Les pièces lui sont DONNÉES, le résultat lui est REPRIS. Entre les deux,
 * tout est du calcul — donc tout se vérifie hors ligne, sans fournisseur,
 * sans quota, sans dépendre de ce qu'un service répond ce jour-là.
 *
 * C'est ce découpage qui permet au test d'en face de construire une
 * transaction complète, de la signer, puis de RECALCULER ce qui aurait dû
 * être signé et de vérifier la signature contre ce nombre. Si une seule
 * étape de la chaîne était fausse, cette vérification échouerait.
 *
 * ─── CE QU'ELLE REFUSE DE FAIRE ────────────────────────────────────────────
 *
 * Elle ne devine pas l'adresse de rendu de monnaie : c'est celle de
 * l'expéditeur, dérivée de la même graine, et elle la calcule plutôt que de
 * l'accepter en paramètre. Une adresse de rendu passée par l'appelant est
 * une occasion de se tromper de destinataire pour la part la plus grosse de
 * la transaction — celle qu'on se renvoie à soi-même.
 *
 * Elle ne décide pas du tarif ni du seuil de poussière. Les deux sont
 * mesurés ailleurs et changent ; les figer ici les rendrait faux en
 * silence.
 * ═══════════════════════════════════════════════════════════════════════════
 */
object DogeEnvoi {

    /** Ce qu'il y a à envoyer au réseau, et de quoi l'expliquer à l'écran. */
    data class Pret(
        /** Les octets à pousser chez le diffuseur, en hexadécimal. */
        val hex: String,
        val choix: DogeSelection.Choix,
        val adresseRendu: String,
    )

    sealed class Resultat {
        data class Ok(val pret: Pret) : Resultat()
        /** Le refus vient du choix des pièces — il porte déjà sa raison. */
        data class Refus(val raison: DogeSelection.Resultat) : Resultat()
        /** L'adresse du destinataire n'est pas une adresse Dogecoin. */
        data class AdresseInvalide(val adresse: String) : Resultat()
    }

    /**
     * Prépare et signe un envoi. Rend les octets, sans les diffuser.
     *
     * [seed] est la graine BIP-39, la même que pour toutes les chaînes.
     * [pieces] viennent du fournisseur, déjà converties en unités.
     */
    fun preparer(
        seed: ByteArray,
        destinataire: String,
        unites: Long,
        pieces: List<DogeSelection.Piece>,
        unitesParOctet: Long,
        seuilPoussiere: Long,
        indexAdresse: Int = 0,
    ): Resultat {
        /*
        L'ADRESSE EST VÉRIFIÉE AVANT TOUT LE RESTE, et par sa somme de
        contrôle — pas par son allure. Une adresse recopiée à la main avec
        un caractère faux garde sa longueur, son « D » et son alphabet.
        Signer d'abord et découvrir ensuite reviendrait à fabriquer une
        transaction irréversible vers personne.
        */
        if (!DogeWallet.estAdresseValide(destinataire)) {
            return Resultat.AdresseInvalide(destinataire)
        }

        val choix = DogeSelection.choisir(
            pieces = pieces,
            montant = unites,
            unitesParOctet = unitesParOctet,
            seuilPoussiere = seuilPoussiere,
        )
        if (choix !is DogeSelection.Resultat.Ok) return Resultat.Refus(choix)
        val c = choix.choix

        val cle = cleDe(seed, indexAdresse)
        val adresseRendu = DogeWallet.encoderAdresse(cle.pubKeyHash)
        val verrouExpediteur = DogeTransaction.scriptVersAdresse(cle.pubKeyHash)

        val sorties = buildList {
            add(
                DogeTransaction.Sortie(
                    c.versDestinataire,
                    DogeTransaction.scriptVersAdresse(hash160De(destinataire))
                )
            )
            /*
            LE RENDU N'EST AJOUTÉ QUE S'IL EXISTE. Quand il est tombé sous
            le seuil de poussière, [DogeSelection] l'a versé aux frais et
            rendu zéro — ajouter une sortie nulle produirait une
            transaction que le réseau refuse, et la taille estimée ne
            correspondrait plus à celle qu'on a payée.
            */
            if (c.rendu > 0) add(DogeTransaction.Sortie(c.rendu, verrouExpediteur))
        }

        /*
        ═══════════════════════════════════════════════════════════════════
        ON CONSTRUIT D'ABORD SANS SIGNATURE, PUIS ON SIGNE CHAQUE ENTRÉE
        ═══════════════════════════════════════════════════════════════════

        L'ordre n'est pas une commodité. Ce qui est signé pour l'entrée N
        dépend de TOUTE la transaction — ses autres entrées, ses sorties,
        ses montants. Signer au fur et à mesure de la construction
        signerait des transactions qui n'existent pas encore.

        Chaque entrée est signée contre la forme NON SIGNÉE, où seule la
        sienne porte le script de verrouillage qu'elle dépense.
        ═══════════════════════════════════════════════════════════════════
        */
        val nonSignee = DogeTransaction.Transaction(
            entrees = c.pieces.map {
                DogeTransaction.Entree(it.txidAffiche, it.index, ByteArray(0))
            },
            sorties = sorties,
        )

        val signee = nonSignee.copy(
            entrees = nonSignee.entrees.mapIndexed { rang, entree ->
                entree.copy(
                    script = DogeSigner.signerEntree(nonSignee, rang, verrouExpediteur, cle)
                )
            }
        )

        return Resultat.Ok(
            Pret(
                hex = DogeTransaction.hex(DogeTransaction.serialiser(signee)),
                choix = c,
                adresseRendu = adresseRendu,
            )
        )
    }

    /**
     * Les vingt octets qu'une adresse Dogecoin encode.
     *
     * L'adresse a déjà été validée par sa somme de contrôle plus haut ;
     * ici on ne fait que la décoder.
     */
    internal fun hash160De(adresse: String): ByteArray =
        Base58.decode(adresse).copyOfRange(1, 21)

    /** La clé de l'adresse [index], en `m/44'/3'/0'/0/index`. */
    internal fun cleDe(seed: ByteArray, index: Int) =
        listOf(
            ChildNumber(44, true), ChildNumber(DogeWallet.TYPE_PIECE, true),
            ChildNumber(0, true), ChildNumber(0, false),
            ChildNumber(index, false)
        ).fold(HDKeyDerivation.createMasterPrivateKey(seed)) { k, enfant ->
            HDKeyDerivation.deriveChildKey(k, enfant)
        }
}
