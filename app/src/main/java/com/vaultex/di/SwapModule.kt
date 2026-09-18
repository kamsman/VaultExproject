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
 * Le défaut reste « changenow » : sans décision écrite, aucune version
 * distribuée ne change de comportement.
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
        "simpleswap" -> simpleSwap
        // Toute autre valeur — vide, mal orthographiée, oubliée — retombe sur
        // le fournisseur historique. Une faute de frappe dans un fichier de
        // configuration ne doit jamais désactiver les échanges.
        else -> changeNow
    }
}
