package com.vaultex.changeur

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/*
═══════════════════════════════════════════════════════════════════════════
LE SMS QUI ARRIVE
═══════════════════════════════════════════════════════════════════════════

Déclaré dans le manifeste, donc réveillé par le système même quand
l'application n'est pas ouverte — et c'est indispensable : le changeur ne
va pas garder cet écran allumé toute la journée.

SMS_RECEIVED est l'une des rares diffusions implicites qu'Android 8 n'a pas
interdites aux receveurs du manifeste. Un service en avant-plan, avec sa
notification permanente, n'est donc pas nécessaire.

─── TROIS CHOSES SE PASSENT ICI, DANS CET ORDRE ─────────────────────────

  1. On recolle les morceaux. Un SMS de plus de 160 caractères arrive
     découpé, et un message d'encaissement avec le solde dépasse souvent.
     Lire un seul morceau donnerait un texte tronqué au milieu du montant.
  2. On filtre sur l'expéditeur. C'est LA frontière de vie privée : ce qui
     n'est pas de l'opérateur ne quitte pas le téléphone.
  3. On transmet, hors du fil principal, avec goAsync().
═══════════════════════════════════════════════════════════════════════════
*/
class RecepteurSms : BroadcastReceiver() {

    override fun onReceive(contexte: Context, intention: Intent) {
        if (intention.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val reglages = Reglages(contexte)
        if (!reglages.actif) return

        /*
        RECOLLER LES MORCEAUX, PAR EXPÉDITEUR.

        `getMessagesFromIntent` rend un tableau : un seul élément pour un
        SMS court, plusieurs pour un long. Les corps se concatènent dans
        l'ordre du tableau.

        Prendre `messages[0].messageBody` — ce qu'on fait naturellement —
        donnerait « Vous avez recu 10000 FCFA de 70123456. Nouveau sol »
        et perdrait la référence qui suit. Pire : sur un découpage
        différent, le montant lui-même pourrait être coupé.
        */
        val messages = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intention)
        } catch (_: Exception) {
            null
        } ?: return
        if (messages.isEmpty()) return

        val expediteur = messages.firstOrNull { it?.originatingAddress != null }
            ?.originatingAddress.orEmpty()
        val texte = messages.joinToString("") { it?.messageBody.orEmpty() }
        if (texte.isBlank()) return

        /*
        ═══════════════════════════════════════════════════════════════════
        LE FILTRE DE VIE PRIVÉE, ET IL EST ICI ET PAS AILLEURS
        ═══════════════════════════════════════════════════════════════════

        Le relais filtre aussi les expéditeurs, mais trop tard : à ce
        moment-là le message a déjà quitté l'appareil. Les SMS d'un proche,
        un code de connexion bancaire, un message personnel — rien de tout
        cela ne doit sortir, et c'est cette ligne-ci qui le garantit.

        L'expéditeur écarté est journalisé SANS SON TEXTE. C'est ce qui
        permet au changeur de corriger la liste en lisant l'identifiant
        réel d'Orange Money — qui n'a jamais été mesuré — plutôt qu'en le
        devinant. Sans cette trace, un identifiant inattendu se
        manifesterait par des achats qui ne sont jamais vérifiés, et rien
        pour dire pourquoi.
        ═══════════════════════════════════════════════════════════════════
        */
        if (!reglages.expediteurAccepte(expediteur)) {
            reglages.journaliser("— ecarte : expediteur « $expediteur »")
            return
        }

        /*
        goAsync() REPOUSSE LA FIN DU RECEVEUR.

        Sans lui, `onReceive` rendrait la main immédiatement et le système
        pourrait tuer le processus pendant que le fil d'envoi travaille —
        l'envoi serait perdu, parfois, selon la charge du téléphone. Un
        défaut qui ne se reproduit pas à la demande.

        Le budget reste d'environ dix secondes : d'où les délais de cinq
        secondes dans Relais, et `finish()` dans un `finally` — un receveur
        qu'on oublie de terminer est signalé par le système et finit par
        faire tuer l'application.
        */
        val suite = goAsync()
        Thread {
            try {
                val issue = Relais.envoyer(reglages, expediteur, texte)
                when {
                    issue.ok -> reglages.journaliser("✓ ${issue.message}")
                    issue.reessayer -> {
                        reglages.mettreEnFile(expediteur, texte)
                        reglages.journaliser("↻ en attente : ${issue.message}")
                    }
                    else -> reglages.journaliser("✗ ${issue.message}")
                }

                /*
                ON REJOUE LA FILE APRÈS UN ENVOI RÉUSSI, et seulement là.

                C'est le signe que le réseau est revenu. Le faire avant
                l'envoi du SMS courant retarderait celui qu'on vient de
                recevoir — le plus utile des deux — derrière cinquante
                anciens, et on dépasserait le budget du receveur.
                */
                if (issue.ok) Relais.rejouerFile(reglages)
            } catch (e: Exception) {
                // Un receveur qui lève fait apparaître « l'application
                // s'est arrêtée » sur le téléphone du changeur, pour un
                // SMS. On trace et on se taît.
                reglages.journaliser("✗ interne : ${e.javaClass.simpleName}")
            } finally {
                suite.finish()
            }
        }.start()
    }
}
