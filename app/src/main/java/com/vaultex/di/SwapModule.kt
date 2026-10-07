package com.vaultex.di

import com.vaultex.core.config.ApiKeys
import com.vaultex.domain.swap.FournisseurChangeNow
import com.vaultex.domain.swap.FournisseurSimpleSwap
import com.vaultex.domain.swap.FournisseurSwap
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Choisit l'échangeur en service.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * UN INTERRUPTEUR PLUTÔT QU'UN REMPLACEMENT
 * ═══════════════════════════════════════════════════════════════════════
 *
 * SimpleSwap est ajouté À CÔTÉ de ChangeNOW, pas à sa place. Trois raisons,
 * et aucune n'est de la prudence excessive :
 *
 * · On ne bascule pas un chemin qui déplace de l'argent sur la foi d'une
 *   documentation lue. Les tickers, la forme des réponses et les minimums
 *   se vérifient contre le vrai service, sur un échange réel de quelques
 *   dollars. Tant que ce test n'est pas passé, la production ne bouge pas.
 *
 * · Le retour en arrière tient en une ligne de local.properties. Si
 *   SimpleSwap déçoit — paire manquante, minimum trop haut, taux décevant —
 *   on revient sans rien recompiler d'autre.
 *
 * · Les deux clés SimpleSwap, l'une à 0,4 % et l'autre à 1,5 %, permettent
 *   de COMPARER les devis avant de choisir. L'écart entre les deux, c'est
 *   exactement ce que la commission retire à l'utilisateur.
 *
 * LE DÉFAUT EST DÉSORMAIS « simpleswap », et c'est une décision prise :
 * SimpleSwap permet de RÉGLER la commission entre 0,4 et 5 % depuis l'espace
 * partenaire, ce que ChangeNOW ne permet pas. ChangeNOW reste entier et
 * redevient actif d'une ligne.
 *
 * Elle vit dans build.gradle.kts, pas dans un local.properties non versionné :
 * sinon elle n'existerait que sur une machine, et une compilation ailleurs
 * repartirait en silence chez l'autre échangeur, avec une autre commission.
 */
@Module
@InstallIn(SingletonComponent::class)
object SwapModule {

    @Provides
    @Singleton
    fun fournisseurSwap(
        changeNow: FournisseurChangeNow,
        simpleSwap: FournisseurSimpleSwap
    ): FournisseurSwap = when (ApiKeys.SWAP_PROVIDER.lowercase().trim()) {
        /*
        ═══════════════════════════════════════════════════════════════════
        SIMPLESWAP SANS CLÉ NE SERAIT PAS SIMPLESWAP, CE SERAIT RIEN
        ═══════════════════════════════════════════════════════════════════

        Chaque appel porte `api_key`. Vide, le service refuse tout : devis,
        bornes, création. L'écran d'échange ne rendrait plus aucun prix, et
        le message parlerait d'un service indisponible alors que rien n'est
        indisponible — il manque une ligne dans un fichier.

        Ce cas n'était pas théorique avant, parce que le défaut était
        « changenow » : il fallait écrire simpleswap pour y arriver, donc on
        avait forcément la clé en tête. Depuis que SimpleSwap est le défaut,
        toute machine sans local.properties y tombe — une compilation
        fraîche, un autre poste, une intégration continue.

        On retombe donc sur ChangeNOW, qui marche sans configuration. Mieux
        vaut un échangeur moins avantageux qu'un écran d'échange mort.
        ═══════════════════════════════════════════════════════════════════
        */
        "simpleswap" -> if (ApiKeys.SIMPLESWAP.isNotBlank()) simpleSwap else changeNow
        // Toute autre valeur — vide, mal orthographiée, oubliée — retombe sur
        // le fournisseur historique. Une faute de frappe dans un fichier de
        // configuration ne doit jamais désactiver les échanges.
        else -> changeNow
    }

    /**
     * Fournisseur de devis SUR PLACE — même chaîne, sans courtier.
     *
     * Un seul aujourd'hui, et il ne remplace personne : il s'ajoute. Tant
     * que l'étape qui signe n'existe pas, son devis ne sert qu'à comparer.
     */
    @Provides
    @Singleton
    fun fournisseurSurPlace(
        oneInch: com.vaultex.domain.swap.FournisseurOneInch
    ): com.vaultex.domain.swap.FournisseurSurPlace = oneInch
}
