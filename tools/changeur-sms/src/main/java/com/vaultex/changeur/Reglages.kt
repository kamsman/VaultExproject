package com.vaultex.changeur

import android.content.Context
import android.content.SharedPreferences

/*
═══════════════════════════════════════════════════════════════════════════
LES RÉGLAGES, ET LA FILE D'ATTENTE
═══════════════════════════════════════════════════════════════════════════

Quatre réglages et une file. Tout tient dans des préférences : il n'y a ni
base de données, ni chiffrement, et il faut dire pourquoi.

─── LE JETON N'EST PAS CHIFFRÉ, ET C'EST ASSUMÉ ─────────────────────────

Le chiffrer avec une clé posée dans le même appareil ne protégerait de
rien : qui peut lire les préférences peut lire la clé. Ce qui protège un
secret sur Android, c'est le bac à sable de l'application — aucune autre
application n'accède à ces fichiers — et il s'applique déjà.

Le vrai risque n'est pas là. Il est qu'un téléphone déverrouillé passe en
d'autres mains. La réponse à ça n'est pas un chiffrement local, c'est de
pouvoir révoquer : changer CHANGE_SMS_TOKEN dans Cloudflare rend ce jeton
inutile en une seconde, partout.

─── LA FILE EXISTE PARCE QU'UN SMS PERDU EST UN ACHAT NON VÉRIFIÉ ───────

Le réseau manque au moment où le SMS arrive — c'est fréquent, et c'est
même le cas le plus probable au Burkina Faso. Sans file, l'encaissement
n'est jamais transmis, l'achat repart « introuvable », et le changeur
cherche un paiement qui est bel et bien arrivé.

On garde donc les envois ratés, et on les rejoue au prochain SMS ou à
l'ouverture de l'écran.
═══════════════════════════════════════════════════════════════════════════
*/
class Reglages(contexte: Context) {

    private val prefs: SharedPreferences =
        contexte.applicationContext.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)

    /** Adresse du relais, sans le chemin : « https://vaultex-prix.xxx.workers.dev ». */
    var relais: String
        get() = prefs.getString(CLE_RELAIS, "").orEmpty().trim().trimEnd('/')
        set(v) = prefs.edit().putString(CLE_RELAIS, v.trim().trimEnd('/')).apply()

    /** Le jeton CHANGE_SMS_TOKEN du Worker. */
    var jeton: String
        get() = prefs.getString(CLE_JETON, "").orEmpty().trim()
        set(v) = prefs.edit().putString(CLE_JETON, v.trim()).apply()

    /**
     * Interrupteur.
     *
     * ÉTEINT À LA PREMIÈRE OUVERTURE, et c'est voulu : l'application
     * s'installe, demande sa permission, et ne transmet rien tant que le
     * changeur n'a pas vérifié que l'adresse et le jeton sont les bons.
     * Une application qui commence à envoyer avant d'être réglée envoie
     * vers le vide, et on met du temps à s'en apercevoir.
     */
    var actif: Boolean
        get() = prefs.getBoolean(CLE_ACTIF, false)
        set(v) = prefs.edit().putBoolean(CLE_ACTIF, v).apply()

    /*
    ═══════════════════════════════════════════════════════════════════════
    QUAND LE DERNIER ENCAISSEMENT EST ARRIVÉ
    ═══════════════════════════════════════════════════════════════════════

    UNE APPLICATION FORCÉE À S'ARRÊTER NE PEUT PAS LE DIRE. C'est tout le
    problème : Android ne livre plus aucune diffusion à une application
    qu'on a arrêtée de force — ou qu'un « nettoyeur » de batterie a arrêtée
    pour nous — tant qu'on ne la rouvre pas à la main. Et comme elle ne
    tourne plus, aucun code à l'intérieur ne peut s'en apercevoir, encore
    moins prévenir.

    L'écran affichait pourtant « ● Actif », parce que c'est un réglage
    enregistré et non un état mesuré. Le robot était sourd et disait qu'il
    écoutait. Pendant ce temps les achats repartaient « introuvable » alors
    que les paiements arrivaient bel et bien.

    Ce qu'on peut faire, c'est garder la DATE du dernier SMS accepté. Elle
    ne prouve pas que l'application écoute — rien ne le peut de l'intérieur
    — mais une date ancienne est la seule trace visible que quelque chose
    s'est arrêté.

    ─── CE QU'ELLE NE DISTINGUE PAS, ET IL FAUT LE DIRE ──────────────────

    « Le robot est sourd » et « personne n'a payé depuis ce matin » donnent
    exactement la même date. L'indicateur ne tranche pas entre les deux, et
    ne doit donc pas crier à la panne : il dit un fait, et c'est au changeur
    de savoir s'il attendait un paiement.

    L'essentiel est ailleurs : ouvrir l'application pour regarder cette date
    est précisément le geste qui la RÉVEILLE si elle avait été arrêtée. Le
    remède est le même que le diagnostic.

    ─── POSÉE SUR LES SMS ACCEPTÉS, PAS SUR TOUS ─────────────────────────

    Un SMS écarté prouverait que le receveur vit, et c'est tentant. Mais la
    question qu'on se pose n'est pas « le receveur vit-il » : c'est « la
    chaîne qui vérifie les achats fonctionne-t-elle ». Un message publicitaire
    qui remettrait le compteur à zéro répondrait oui à la mauvaise question.

    0 = jamais rien reçu. Une installation neuve n'est pas en panne, et
    l'écran le dit autrement.
    ═══════════════════════════════════════════════════════════════════════
    */
    var dernierSms: Long
        get() = prefs.getLong(CLE_DERNIER_SMS, 0L)
        set(v) = prefs.edit().putLong(CLE_DERNIER_SMS, v).apply()

    /**
     * Les expéditeurs dont on transmet les SMS, séparés par des virgules.
     *
     * ═══════════════════════════════════════════════════════════════════
     * CE FILTRE EST LA FRONTIÈRE DE VIE PRIVÉE, ET ELLE EST ICI
     * ═══════════════════════════════════════════════════════════════════
     *
     * Le relais filtre aussi, mais trop tard : à ce moment-là le message
     * est déjà parti du téléphone. Les SMS d'un proche, un code de
     * connexion bancaire, un message personnel — rien de tout cela ne doit
     * QUITTER l'appareil, et c'est cette ligne-ci qui le garantit.
     *
     * Le filtre du relais sert à autre chose : écarter un SMS qu'un client
     * enverrait lui-même au changeur en imitant l'opérateur.
     * ═══════════════════════════════════════════════════════════════════
     */
    var expediteurs: String
        get() = prefs.getString(CLE_EXPEDITEURS, DEFAUT_EXPEDITEURS).orEmpty()
        set(v) = prefs.edit().putString(CLE_EXPEDITEURS, v).apply()

    /**
     * Vrai si cet expéditeur fait partie de ceux qu'on transmet.
     *
     * ═══════════════════════════════════════════════════════════════════
     * UN EXPÉDITEUR VIDE PASSAIT CE FILTRE
     * ═══════════════════════════════════════════════════════════════════
     *
     * `attendu.contains(vu)` servait à reconnaître une forme abrégée —
     * « orange » pour « orange money ». Mais `"orangemoney".contains("")`
     * vaut VRAI : un expéditeur vide était donc accepté par tous les
     * motifs à la fois, et une seule lettre aussi.
     *
     * Ce n'est pas un détail : ce filtre est LA frontière de vie privée.
     * C'est lui qui garantit qu'un code de connexion ou le message d'un
     * proche ne quitte jamais ce téléphone. Le contourner ne demandait
     * aucune astuce — il suffisait qu'Android ne rende pas d'adresse
     * d'origine, ce qui arrive.
     *
     * Quatre caractères au moins : c'est la longueur du plus court motif
     * accepté (« wave ») et celle d'un numéro court d'opérateur. En deçà,
     * une correspondance partielle n'identifie plus personne.
     * ═══════════════════════════════════════════════════════════════════
     */
    fun expediteurAccepte(expediteur: String): Boolean {
        val vu = expediteur.lowercase().replace(" ", "").trim()
        if (vu.length < 4) return false
        return expediteurs.split(',')
            .map { it.trim().lowercase().replace(" ", "") }
            .filter { it.length >= 4 }
            .any { attendu -> vu.contains(attendu) || attendu.contains(vu) }
    }

    val configure: Boolean
        get() = relais.startsWith("https://") && jeton.isNotEmpty()

    // ─── La file des envois ratés ───────────────────────────────────

    /**
     * Ajoute un envoi raté à la file.
     *
     * PLAFONNÉE À [FILE_MAX]. Sans plafond, un relais injoignable pendant
     * une semaine remplirait les préférences — qui sont relues en entier à
     * chaque ouverture — jusqu'à ralentir l'application, puis à la faire
     * tomber. On garde les plus RÉCENTS : un encaissement de la semaine
     * dernière ne sert plus à rapprocher quoi que ce soit.
     */
    fun mettreEnFile(expediteur: String, texte: String) {
        val file = lireFile().toMutableList()
        file.add(EnAttente(expediteur, texte, System.currentTimeMillis()))
        while (file.size > FILE_MAX) file.removeAt(0)
        ecrireFile(file)
    }

    fun lireFile(): List<EnAttente> {
        val brut = prefs.getString(CLE_FILE, "").orEmpty()
        if (brut.isEmpty()) return emptyList()
        return try {
            val tableau = org.json.JSONArray(brut)
            (0 until tableau.length()).mapNotNull { i ->
                val o = tableau.optJSONObject(i) ?: return@mapNotNull null
                EnAttente(
                    o.optString("expediteur"),
                    o.optString("texte"),
                    o.optLong("quand")
                )
            }
        } catch (_: Exception) {
            // File illisible : on repart de zéro plutôt que de planter au
            // démarrage. On perd des envois en attente, ce qui est moins
            // grave qu'une application qui ne s'ouvre plus.
            emptyList()
        }
    }

    fun ecrireFile(file: List<EnAttente>) {
        val tableau = org.json.JSONArray()
        for (e in file) {
            tableau.put(
                org.json.JSONObject()
                    .put("expediteur", e.expediteur)
                    .put("texte", e.texte)
                    .put("quand", e.quand)
            )
        }
        prefs.edit().putString(CLE_FILE, tableau.toString()).apply()
    }

    data class EnAttente(val expediteur: String, val texte: String, val quand: Long)

    // ─── Le journal, pour voir que ça marche ────────────────────────

    /**
     * Garde une trace de ce qui s'est passé.
     *
     * CE N'EST PAS DU CONFORT. Sans journal, le changeur n'a aucun moyen
     * de distinguer « aucun SMS n'est arrivé » de « le relais refuse mon
     * jeton » : dans les deux cas, l'écran est vide et les achats repartent
     * non vérifiés. Il chercherait du côté du client, qui n'y est pour
     * rien.
     *
     * Le journal ne contient que l'issue et le montant lu — jamais le texte
     * entier d'un SMS, qui n'a pas besoin d'y séjourner.
     */
    fun journaliser(ligne: String) {
        val horodate = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.FRANCE)
            .format(java.util.Date())
        val precedent = prefs.getString(CLE_JOURNAL, "").orEmpty()
        val lignes = ("$horodate  $ligne\n$precedent").lines().take(JOURNAL_MAX)
        prefs.edit().putString(CLE_JOURNAL, lignes.joinToString("\n")).apply()
    }

    fun journal(): String = prefs.getString(CLE_JOURNAL, "").orEmpty()

    private companion object {
        const val FICHIER = "changeur_sms"
        const val CLE_RELAIS = "relais"
        const val CLE_JETON = "jeton"
        const val CLE_ACTIF = "actif"
        const val CLE_EXPEDITEURS = "expediteurs"
        const val CLE_FILE = "file"
        const val CLE_JOURNAL = "journal"
        const val CLE_DERNIER_SMS = "dernier_sms"

        /**
         * Les expéditeurs par défaut.
         *
         * À VÉRIFIER SUR L'APPAREIL : l'identifiant exact que présente
         * Orange Money Burkina n'a pas été mesuré. L'écran affiche
         * l'expéditeur de chaque SMS écarté, précisément pour qu'on
         * corrige cette liste en le lisant plutôt qu'en le devinant.
         */
        const val DEFAUT_EXPEDITEURS = "OrangeMoney,Orange Money,MoovMoney,Moov Money,Wave"

        const val FILE_MAX = 50
        const val JOURNAL_MAX = 60
    }
}
