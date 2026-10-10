package com.vaultex.changeur

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/*
═══════════════════════════════════════════════════════════════════════════
L'ENVOI AU RELAIS
═══════════════════════════════════════════════════════════════════════════

Une requête, un en-tête, et une réponse qu'on lit vraiment.

─── ON LIT LA RÉPONSE, ET CE N'EST PAS DU LUXE ──────────────────────────

Un robot qui poste sans regarder ce qu'on lui répond est un robot qui
tombe en panne en silence. Les quatre refus du relais appellent quatre
gestes différents, et aucun ne se devine :

  401  le jeton ne correspond pas       → le corriger dans l'écran
  503  CHANGE_SMS_TOKEN n'est pas réglé → le poser dans Cloudflare
  400  expéditeur non reconnu           → corriger la liste des expéditeurs
  422  format non reconnu               → le motif du Worker doit changer

Les confondre dans un « échec » enverrait le changeur réinstaller
l'application alors que c'est une variable Cloudflare qui manque.

─── UN SEUL CAS JUSTIFIE DE REJOUER PLUS TARD ───────────────────────────

L'absence de réseau. Les autres sont des refus définitifs : rejouer un
format non reconnu mille fois ne le fera pas reconnaître, ça videra la
batterie. [Issue.reessayer] porte cette distinction, et c'est elle que la
file d'attente consulte.
═══════════════════════════════════════════════════════════════════════════
*/
object Relais {

    /**
     * Ce qu'il advient d'un envoi.
     *
     * @param reessayer vrai seulement quand une nouvelle tentative a du
     *   sens — c'est-à-dire pour une panne de réseau, et rien d'autre.
     */
    data class Issue(
        val ok: Boolean,
        val message: String,
        val reessayer: Boolean
    )

    /** Transmet un SMS au relais. Bloquant : à appeler hors du fil principal. */
    fun envoyer(reglages: Reglages, expediteur: String, texte: String): Issue {
        if (!reglages.configure) {
            return Issue(false, "adresse ou jeton manquant", reessayer = false)
        }

        var connexion: HttpURLConnection? = null
        return try {
            val url = URL("${reglages.relais}/change/sms")
            connexion = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                /*
                DIX SECONDES, ET C'EST UN PLAFOND IMPOSÉ PAR ANDROID.

                Un BroadcastReceiver dispose d'environ dix secondes avant
                que le système ne le considère bloqué et ne tue le
                processus. Deux fois cinq secondes tiennent dedans ; un
                délai par défaut — illimité sur certaines implémentations
                — ferait tuer l'application au lieu d'échouer proprement,
                et l'envoi ne serait même pas mis en file.
                */
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("content-type", "application/json; charset=utf-8")
                setRequestProperty("x-vaultex-sms", reglages.jeton)
                // Pas de compression : les corps font quelques centaines
                // d'octets, et un en-tête de moins est une chose de moins
                // qui peut mal tourner sur un réseau mobile capricieux.
                setRequestProperty("accept-encoding", "identity")
            }

            /*
            org.json CONSTRUIT LE CORPS, et non une concaténation de
            chaînes. Un SMS contient des apostrophes, des retours à la
            ligne, parfois des guillemets — une concaténation produirait du
            JSON invalide, le relais répondrait « corps illisible », et on
            chercherait du côté du réseau.

            org.json est fourni par Android : aucune dépendance ajoutée.
            */
            val corps = org.json.JSONObject()
                .put("expediteur", expediteur)
                .put("texte", texte)
                .put("recu", System.currentTimeMillis())
                .toString()

            connexion.outputStream.use { flux ->
                flux.write(corps.toByteArray(Charsets.UTF_8))
            }

            val code = connexion.responseCode
            /*
            LE CORPS D'ERREUR SE LIT SUR errorStream, PAS SUR inputStream.

            Sur un code 4xx, `inputStream` lève une IOException : on
            perdrait la raison que le relais a pris soin de donner, pour
            n'afficher qu'« erreur réseau ». C'est une erreur classique, et
            elle transforme un diagnostic en devinette.
            */
            val flux = if (code in 200..299) connexion.inputStream else connexion.errorStream
            val reponse = flux?.let {
                BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { l -> l.readText() }
            }.orEmpty()

            when {
                code in 200..299 -> Issue(true, resume(reponse), reessayer = false)
                code == 401 -> Issue(false, "jeton refuse par le relais", reessayer = false)
                code == 503 -> Issue(false, "CHANGE_SMS_TOKEN absent du Worker", reessayer = false)
                else -> Issue(false, "refus $code : ${raison(reponse)}", reessayer = false)
            }
        } catch (e: Exception) {
            /*
            LE SEUL CAS QUI MÉRITE D'ÊTRE REJOUÉ.

            Pas de réseau au moment où le SMS arrive — ce qui est le cas le
            plus probable ici, et pas une anomalie. L'encaissement attend
            dans la file et repart au prochain SMS.
            */
            Issue(false, "reseau : ${e.javaClass.simpleName}", reessayer = true)
        } finally {
            connexion?.disconnect()
        }
    }

    /** « 10000 FCFA de 70123456 », depuis la réponse du relais. */
    private fun resume(reponse: String): String = try {
        val lu = org.json.JSONObject(reponse).optJSONObject("lu")
        if (lu == null) "transmis" else {
            val montant = lu.optInt("montant", 0)
            val numero = lu.optString("numero")
            val ref = lu.optString("reference")
            buildString {
                append("$montant FCFA")
                if (numero.isNotEmpty()) append(" de $numero")
                if (ref.isNotEmpty()) append(" [$ref]")
            }
        }
    } catch (_: Exception) {
        "transmis"
    }

    private fun raison(reponse: String): String = try {
        org.json.JSONObject(reponse).optString("raison").ifEmpty { "sans raison" }
    } catch (_: Exception) {
        "reponse illisible"
    }

    /**
     * Rejoue les envois en attente.
     *
     * ═══════════════════════════════════════════════════════════════════
     * UN ÉCHEC DÉFINITIF SORT DE LA FILE, UN ÉCHEC RÉSEAU Y RESTE
     * ═══════════════════════════════════════════════════════════════════
     *
     * Sans cette distinction, un SMS dont le format n'est pas reconnu
     * resterait en file pour toujours et serait rejoué à chaque SMS
     * suivant — des centaines de requêtes pour un refus qui ne changera
     * pas, et une file qui ne se vide jamais.
     *
     * ON S'ARRÊTE AU PREMIER ÉCHEC RÉSEAU. Si le relais est injoignable,
     * il l'est pour les cinquante suivants : insister ne ferait que tenir
     * le receveur éveillé jusqu'à ce que le système le tue.
     * ═══════════════════════════════════════════════════════════════════
     */
    fun rejouerFile(reglages: Reglages) {
        val file = reglages.lireFile()
        if (file.isEmpty()) return
        val restants = mutableListOf<Reglages.EnAttente>()
        var reseauMort = false

        for (e in file) {
            if (reseauMort) {
                restants.add(e)
                continue
            }
            val issue = envoyer(reglages, e.expediteur, e.texte)
            if (issue.ok) {
                reglages.journaliser("✓ (file) ${issue.message}")
            } else if (issue.reessayer) {
                restants.add(e)
                reseauMort = true
            } else {
                reglages.journaliser("✗ (file) ${issue.message}")
            }
        }
        reglages.ecrireFile(restants)
    }
}
