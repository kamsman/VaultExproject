package com.vaultex.changeur

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/*
═══════════════════════════════════════════════════════════════════════════
LA VEILLE — CE QUI REND LE ROBOT VISIBLE ET LE FAIT RATTRAPER
═══════════════════════════════════════════════════════════════════════════

Deux rôles, et le premier n'est pas technique.

─── 1. LA NOTIFICATION EST UN INDICATEUR DE SANTÉ ───────────────────────

Avant, le changeur n'avait aucun moyen de savoir si le robot écoutait
encore. Il fallait ouvrir l'application et lire « Dernier SMS : il y a
3 j » — ce que personne ne fait tant que rien ne va mal, c'est-à-dire
jusqu'à ce qu'un client ait payé pour rien.

Une notification permanente répond à la question sans rien ouvrir. Si
elle disparaît, le robot est arrêté. C'est tout, et ça suffit.

Elle a un second effet, celui-là bien technique : les gestionnaires de
batterie de Samsung et Xiaomi épargnent les services en avant-plan. Un
processus sans notification visible est tué en silence ; celui-ci, non.

─── 2. LE BALAYAGE PÉRIODIQUE ───────────────────────────────────────────

Toutes les [PERIODE] minutes, [BalayageSms] relit les SMS arrivés depuis
le dernier passage. Voir ce fichier pour ce qu'il s'interdit.

─── POURQUOI UN HANDLER ET PAS WORKMANAGER ──────────────────────────────

Cette application n'a AUCUNE dépendance externe — pas une ligne hors du
framework Android. C'est ce qui permet de la lire en entier avant de
l'installer sur le téléphone qui encaisse, et ça vaut plus qu'une API
plus confortable.

Le service tourne déjà en avant-plan pour la raison n°1. Lui faire poster
sa propre boucle coûte quelques lignes ; ajouter WorkManager coûterait une
bibliothèque entière, et pour un travail qu'on sait faire.

─── CE QU'IL NE RÉPARE PAS, ET IL FAUT LE SAVOIR ────────────────────────

Une application FORCÉE À L'ARRÊT ne reçoit rien : ni diffusion, ni
alarme, ni la boucle de ce service. Android le garantit, et aucun code ne
le contourne.

Ce qui change, c'est qu'à la reprise — quelqu'un rouvre l'application —
le balayage repêche tout ce qui a été manqué pendant l'arrêt, dans la
limite de la fenêtre. Avant, rouvrir ne récupérait rien.
═══════════════════════════════════════════════════════════════════════════
*/
class ServiceVeille : Service() {

    private val fil = Handler(Looper.getMainLooper())
    private lateinit var reglages: Reglages

    private val battement = object : Runnable {
        override fun run() {
            /*
            LE BALAYAGE SORT DU FIL PRINCIPAL.

            Il interroge un fournisseur de contenu et fait des requêtes
            réseau. Sur le fil principal, vingt messages suffiraient à
            faire apparaître « l'application ne répond pas ».
            */
            Thread {
                val bilan = try {
                    BalayageSms.balayer(this@ServiceVeille, reglages)
                } catch (_: Exception) {
                    BalayageSms.Bilan(0, 0, 0)
                }
                fil.post { majNotification(bilan) }
            }.start()
            fil.postDelayed(this, PERIODE)
        }
    }

    override fun onCreate() {
        super.onCreate()
        reglages = Reglages(this)
        creerCanal()
        demarrerAvantPlan()
        /*
        UN PREMIER BALAYAGE TOUT DE SUITE, PUIS LA BOUCLE.

        Le service démarre à l'ouverture de l'application et au
        redémarrage du téléphone — les deux moments exacts où un retard
        s'est accumulé. Attendre quinze minutes avant de regarder serait
        attendre au pire moment.
        */
        fil.post(battement)
    }

    override fun onStartCommand(intention: Intent?, drapeaux: Int, id: Int): Int {
        /*
        START_STICKY : si le système tue le service sous la pression
        mémoire, il le relance. C'est tout l'intérêt par rapport à un
        simple fil.
        */
        return START_STICKY
    }

    override fun onDestroy() {
        fil.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onBind(intention: Intent?): IBinder? = null

    // ─── La notification ────────────────────────────────────────────

    private fun creerCanal() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val gestionnaire = getSystemService(NotificationManager::class.java) ?: return
        /*
        IMPORTANCE_LOW : visible dans le tiroir, SANS son ni vibration.

        Un robot qui sonne toutes les quinze minutes finirait par être
        coupé par le changeur lui-même — et couper la notification, ici,
        c'est couper le service.
        */
        val canal = NotificationChannel(
            CANAL,
            "Veille du robot",
            NotificationManager.IMPORTANCE_LOW
        )
        canal.description = "Indique que le robot ecoute les SMS d'encaissement."
        canal.setShowBadge(false)
        gestionnaire.createNotificationChannel(canal)
    }

    private fun construire(texte: String): Notification {
        val ouvrir = PendingIntent.getActivity(
            this,
            0,
            Intent(this, EcranPrincipal::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CANAL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return b
            .setContentTitle("VaultEx Changeur")
            .setContentText(texte)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setOngoing(true)
            .setContentIntent(ouvrir)
            .build()
    }

    private fun demarrerAvantPlan() {
        val notif = construire(etatLisible(null))
        /*
        À PARTIR D'ANDROID 10, LE TYPE EST OBLIGATOIRE.

        `dataSync` est celui qui décrit ce service : il synchronise des
        messages avec un serveur. Un type absent ou faux fait refuser le
        démarrage par le système à partir d'Android 14, et l'application
        s'arrête — exactement la panne qu'on répare.
        */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(ID_NOTIF, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(ID_NOTIF, notif)
        }
    }

    private fun majNotification(bilan: BalayageSms.Bilan) {
        val gestionnaire = getSystemService(NotificationManager::class.java) ?: return
        try {
            gestionnaire.notify(ID_NOTIF, construire(etatLisible(bilan)))
        } catch (_: Exception) {
            // Une notification refusée ne doit pas arrêter la veille.
        }
    }

    /*
    CE QUE LA LIGNE DIT, ET DANS QUEL ORDRE.

    La question du changeur est toujours la même : « est-ce que ça
    marche ? ». La réponse la plus utile est la date du dernier
    encaissement entendu — pas le nombre de balayages, qui ne lui apprend
    rien.

    Quand quelque chose manque pour fonctionner, c'est ÇA qui passe
    devant : une permission absente rend le robot inutile, et il vaut
    mieux le lire dans le tiroir de notifications que le découvrir sur un
    achat refusé.
    */
    private fun etatLisible(bilan: BalayageSms.Bilan?): String {
        if (!reglages.configure) return "A configurer — ouvre l'application"
        if (!reglages.actif) return "Transmission desactivee"
        if (!BalayageSms.permissionLecture(this)) {
            return "Rattrapage inactif — autorise la lecture des SMS"
        }
        val rattrape = if (bilan != null && bilan.transmis > 0) {
            " · ${bilan.transmis} rattrape(s)"
        } else {
            ""
        }
        val quand = reglages.dernierSms
        if (quand <= 0L) return "Actif — aucun encaissement recu pour l'instant"
        return "Actif · dernier encaissement ${depuis(System.currentTimeMillis() - quand)}$rattrape"
    }

    private fun depuis(ms: Long): String {
        val minutes = ms / 60000
        if (minutes < 1) return "a l'instant"
        if (minutes < 60) return "il y a $minutes min"
        val heures = minutes / 60
        if (heures < 24) return "il y a $heures h"
        return "il y a ${heures / 24} j"
    }

    companion object {
        const val CANAL = "veille"
        const val ID_NOTIF = 1

        /*
        QUINZE MINUTES.

        La fenêtre de rapprochement du relais est de trois heures : un
        rattrapage qui arrive en quinze minutes est très largement dans
        les temps, et le client n'a pas encore eu le temps de réclamer.

        Plus court userait la batterie pour rien — la diffusion fait
        déjà le travail dans le cas normal, et ce balayage n'existe que
        pour le cas où elle a manqué.
        */
        const val PERIODE = 15L * 60 * 1000

        /**
         * Démarre la veille si elle ne tourne pas déjà.
         *
         * Appelée à l'ouverture de l'écran et au démarrage du téléphone.
         * Redémarrer un service déjà en route ne fait rien de plus que
         * rappeler `onStartCommand`, qui ne fait rien.
         */
        fun demarrer(contexte: Context) {
            val intention = Intent(contexte, ServiceVeille::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    contexte.startForegroundService(intention)
                } else {
                    contexte.startService(intention)
                }
            } catch (_: Exception) {
                /*
                UN DÉMARRAGE REFUSÉ NE DOIT PAS FAIRE TOMBER L'APPELANT.

                Android refuse de démarrer un service en avant-plan depuis
                l'arrière-plan dans certains états. Le robot continue alors
                de fonctionner par la diffusion, comme avant ce fichier :
                on perd le rattrapage, pas l'écoute.
                */
            }
        }
    }
}
