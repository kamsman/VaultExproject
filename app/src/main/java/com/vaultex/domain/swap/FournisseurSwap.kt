package com.vaultex.domain.swap

/*
═══════════════════════════════════════════════════════════════════════════
UN ÉCHANGEUR DERRIÈRE UNE PORTE, PLUTÔT QU'UN ÉCHANGEUR PARTOUT
═══════════════════════════════════════════════════════════════════════════

ChangeNOW était appelé directement depuis le cas d'usage ET depuis le
ViewModel : ses types de réponse remontaient jusqu'à l'écran. Changer de
fournisseur revenait donc à toucher l'interface, ce qui est absurde — le nom
de l'échangeur ne regarde pas l'affichage.

CE QUI MOTIVE LE CHANGEMENT. ChangeNOW verse 0,4 % au partenaire, sans plus.
SimpleSwap laisse fixer ce taux entre 0,4 et 5 % SUR LA CLÉ elle-même : la
commission est prélevée DANS l'échange, sans transaction supplémentaire.

Cette nuance décide de tout. Prélever soi-même 1 % exigerait un second envoi
vers une adresse VaultEx — environ 2,20 $ de frais réseau sur Tron pour
encaisser 0,50 $ sur un échange de 50 $. La commission ne devient rentable
qu'au-delà de 220 $ par opération, c'est-à-dire jamais, vu ce qu'échangent
réellement les utilisateurs de l'application.

LES MODÈLES CI-DESSOUS SONT NEUTRES. Aucun champ ne porte le nom d'un
fournisseur : c'est ce qui permet d'en ajouter un troisième sans rien
retoucher en amont.
═══════════════════════════════════════════════════════════════════════════
*/

/** Estimation de ce que l'utilisateur recevra. */
data class DevisSwap(
    val montantEstime: String,
    val avertissement: String? = null
)

/**
 * Échange créé, prêt à être approvisionné.
 *
 * [adresseDepot] est l'adresse vers laquelle l'application envoie les fonds.
 * Elle ne peut JAMAIS être vide : un fournisseur qui ne la rend pas doit
 * faire échouer la création, pas laisser partir un virement dans le vide.
 */
data class EchangeCree(
    val id: String,
    val adresseDepot: String,
    val memoDepot: String?,
    val montantAttendu: String?
)

/** Avancement d'un échange en cours. */
data class StatutSwap(
    val id: String,
    val statut: String,          // waiting, confirming, exchanging, sending, finished, failed…
    val hashDepot: String? = null,
    val hashSortie: String? = null,
    val montantRecu: String? = null
)

/**
 * Ce que VaultEx attend d'un échangeur, et rien de plus.
 *
 * Quatre opérations : coter, connaître le minimum, créer, suivre.
 */
interface FournisseurSwap {

    /** Nom affiché à l'utilisateur — « ChangeNOW », « SimpleSwap ». */
    val nom: String

    /**
     * Commission VaultEx appliquée par ce fournisseur, en pourcentage.
     *
     * Portée par la CLÉ d'API, pas par le code : l'application ne prélève
     * rien elle-même. Cette valeur ne sert qu'à l'affichage, pour que
     * l'écran dise ce qui est réellement pris — ce qu'il ne faisait pas,
     * annonçant 1,5 % quand rien n'était prélevé.
     */
    val commissionPourcent: Double

    suspend fun devis(de: String, vers: String, montant: Double): DevisSwap

    /** Montant minimum de la paire, ou null si le fournisseur ne le dit pas. */
    suspend fun minimum(de: String, vers: String): Double?

    suspend fun creerEchange(
        de: String,
        vers: String,
        montant: Double,
        adresseReception: String,
        adresseRemboursement: String?
    ): EchangeCree

    suspend fun statut(id: String): StatutSwap?
}
