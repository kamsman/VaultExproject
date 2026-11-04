package com.vaultex.changeur

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony

/*
═══════════════════════════════════════════════════════════════════════════
LE RATTRAPAGE — CE QUI REPÊCHE UN SMS QUE LA DIFFUSION A MANQUÉ
═══════════════════════════════════════════════════════════════════════════

MESURÉ LE 3 NOVEMBRE 2026, et c'est ce qui a motivé ce fichier.

Un encaissement de 6 108 FCFA arrive à 16:38. L'application avait été
« forcée à l'arrêt » quelques jours plus tôt — Android ne délivre alors
AUCUNE diffusion tant que personne ne la rouvre. Le changeur la rouvre à
16:52 : l'essai de transmission réussit, mais le SMS de 16:38 est perdu.
Définitivement, parce que le robot n'avait aucun moyen de le relire.

L'achat correspondant a été refusé alors que le client avait payé.

─── POURQUOI IL N'Y AVAIT AUCUN MOYEN DE LE RELIRE ──────────────────────

Le manifeste refusait `READ_SMS`, et l'argument était bon : cette
permission donne accès à toute la boîte de réception. `RECEIVE_SMS` ne
donne que les messages qui ARRIVENT, pendant qu'ils arrivent.

Ce que cet argument ne disait pas, c'est son prix : un message manqué est
manqué pour toujours. Tant que la diffusion passe, le robot est parfait.
Le jour où elle ne passe pas — arrêt forcé, gestionnaire de batterie
d'un constructeur, mise à jour en cours — il n'y a pas de seconde chance.

Ce fichier accepte ce prix dans l'autre sens, et il faut le dire
franchement : LA PERMISSION DONNE PLUS QUE CE QU'ON EN FAIT. C'est
désormais le code qui limite, plus le système.

─── CE QUE CE CODE S'INTERDIT, ET QUI SE VÉRIFIE EN LE LISANT ───────────

  1. Il ne lit QUE les expéditeurs de la liste du changeur. Le même filtre
     que la diffusion, le même `expediteurAccepte`.
  2. Il ne remonte JAMAIS au-delà de [FENETRE_MAX]. Le rapprochement du
     relais dure trois heures ; relire un mois d'historique ne servirait à
     rien et exposerait tout.
  3. Il ne transmet JAMAIS le texte d'un expéditeur écarté. Pas de
     journalisation de son contenu non plus.
  4. Il ne traite qu'un nombre borné de messages par passage.

Un `grep` sur ce fichier doit suffire à vérifier ces quatre points. S'ils
cessent d'être vrais, l'argument de vie privée tombe pour de bon.
═══════════════════════════════════════════════════════════════════════════
*/
object BalayageSms {

    /*
    VINGT-QUATRE HEURES, ET PAS DAVANTAGE.

    La fenêtre de rapprochement du relais est de trois heures : un
    encaissement plus ancien ne sert plus à rien. On prend huit fois cette
    marge pour couvrir un téléphone éteint une nuit entière, et on s'arrête
    là.

    Remonter plus loin ne récupérerait aucun achat — seulement des SMS
    d'opérateur sans objet, transmis pour rien.
    */
    const val FENETRE_MAX = 24L * 60 * 60 * 1000

    /*
    VINGT MESSAGES PAR PASSAGE.

    Un balayage n'est pas une migration. Si vingt encaissements attendent,
    c'est que quelque chose va mal ailleurs, et les transmettre tous d'un
    coup ferait cinquante requêtes dans la même seconde — le relais limite
    son débit, et on se ferait couper au milieu.

    Le prochain passage prendra la suite : le repère avance à chaque
    message traité.
    */
    const val MAX_PAR_PASSAGE = 20

    /** Ce qu'un passage a fait, pour la notification et le journal. */
    data class Bilan(val lus: Int, val transmis: Int, val refuses: Int)

    fun permissionLecture(contexte: Context): Boolean =
        contexte.checkSelfPermission(Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Relit les SMS reçus depuis le dernier balayage et transmet ceux qui
     * viennent d'un expéditeur autorisé.
     *
     * Rend un bilan vide si la permission manque ou si le robot est
     * désactivé — jamais d'exception : ce code tourne dans un service qui
     * ne doit pas mourir.
     */
    fun balayer(contexte: Context, reglages: Reglages): Bilan {
        if (!reglages.actif || !reglages.configure) return Bilan(0, 0, 0)
        if (!permissionLecture(contexte)) return Bilan(0, 0, 0)

        /*
        LE REPÈRE, ET POURQUOI IL EST BORNÉ DES DEUX CÔTÉS.

        En bas : la dernière date déjà balayée, pour ne pas repasser sur
        les mêmes. En haut : [FENETRE_MAX], pour qu'une première
        installation — ou un repère perdu — ne déclenche pas la relecture
        de tout l'historique.

        Le `maxOf` fait les deux en une ligne, et c'est le plus récent des
        deux qui gagne.
        */
        val plancher = System.currentTimeMillis() - FENETRE_MAX
        val depuis = maxOf(reglages.dernierBalaye, plancher)

        var lus = 0
        var transmis = 0
        var refuses = 0
        var plusRecent = depuis

        try {
            val curseur = contexte.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                "${Telephony.Sms.DATE} > ?",
                arrayOf(depuis.toString()),
                "${Telephony.Sms.DATE} ASC"
            ) ?: return Bilan(0, 0, 0)

            curseur.use { c ->
                val iAdresse = c.getColumnIndex(Telephony.Sms.ADDRESS)
                val iCorps = c.getColumnIndex(Telephony.Sms.BODY)
                val iDate = c.getColumnIndex(Telephony.Sms.DATE)
                if (iAdresse < 0 || iCorps < 0 || iDate < 0) return Bilan(0, 0, 0)

                while (c.moveToNext() && lus < MAX_PAR_PASSAGE) {
                    val date = c.getLong(iDate)
                    val expediteur = c.getString(iAdresse).orEmpty()

                    /*
                    LE REPÈRE AVANCE MÊME SUR UN MESSAGE ÉCARTÉ.

                    Sinon le SMS d'un proche, reçu une fois, serait relu à
                    chaque passage — toutes les quinze minutes, pour
                    toujours. Ce qu'on retient, c'est « j'ai regardé
                    jusqu'ici », pas « j'ai transmis jusqu'ici ».
                    */
                    if (date > plusRecent) plusRecent = date

                    if (!reglages.expediteurAccepte(expediteur)) continue

                    /*
                    DEJA PARTI EN DIRECT : on ne le refait pas.

                    Le chemin rapide a traite ce message a la seconde ou
                    il est arrive. Le repecher doublerait le trafic et
                    remplirait le journal — qui ne garde que soixante
                    lignes — deux fois plus vite.

                    On compare des DATES EXACTES, pas une frontiere : un
                    message manque n'est jamais dans cette liste, donc il
                    reste toujours repechable.
                    */
                    if (reglages.dejaTransmis(date)) continue

                    val texte = c.getString(iCorps).orEmpty()
                    if (texte.isBlank()) continue
                    lus++

                    /*
                    LA DATE D'ÉCOUTE SE POSE ICI AUSSI.

                    Le robot a bien « entendu » cet encaissement, même s'il
                    l'entend en différé. Ne pas la poser laisserait le
                    tableau du relais afficher « robot silencieux » alors
                    qu'il vient de rattraper un paiement.
                    */
                    if (date > reglages.dernierSms) reglages.dernierSms = date

                    val issue = Relais.envoyer(reglages, expediteur, texte)
                    when {
                        issue.ok -> {
                            transmis++
                            // Meme raison que ci-dessus : un second passage
                            // dans la meme fenetre le refarait sinon.
                            reglages.marquerTransmis(date)
                            reglages.journaliser("⟲ rattrape : ${issue.message}")
                        }
                        issue.reessayer -> {
                            reglages.mettreEnFile(expediteur, texte)
                            reglages.journaliser("↻ rattrape en attente : ${issue.message}")
                        }
                        // Reparable a la main : on garde, voir Relais.Issue.
                        issue.garder -> {
                            reglages.mettreEnFile(expediteur, texte)
                            reglages.journaliser("⏸ rattrape garde : ${issue.message}")
                        }
                        else -> {
                            refuses++
                            reglages.journaliser("✗ rattrape : ${issue.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            /*
            UN BALAYAGE QUI LÈVE NE DOIT PAS TUER LE SERVICE.

            Le fournisseur de SMS peut refuser la requête sur certains
            téléphones, et le service doit continuer à écouter la
            diffusion — qui, elle, marche. On trace sans le texte.
            */
            reglages.journaliser("✗ balayage : ${e.javaClass.simpleName}")
            return Bilan(lus, transmis, refuses)
        }

        /*
        LE REPÈRE NE S'ÉCRIT QU'À LA FIN, et jamais en arrière.

        Écrit au fil de la boucle, une exception au milieu le laisserait
        au-delà de messages non traités — qui ne seraient alors jamais
        repêchés. C'est exactement la panne qu'on répare.
        */
        if (plusRecent > reglages.dernierBalaye) reglages.dernierBalaye = plusRecent

        if (transmis > 0) Relais.rejouerFile(reglages)
        return Bilan(lus, transmis, refuses)
    }
}
