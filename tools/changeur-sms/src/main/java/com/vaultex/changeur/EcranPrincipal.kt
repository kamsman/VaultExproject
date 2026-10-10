package com.vaultex.changeur

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/*
═══════════════════════════════════════════════════════════════════════════
L'ÉCRAN, EN VUES NUES
═══════════════════════════════════════════════════════════════════════════

Pas de XML de mise en page, pas de Compose, pas d'AndroidX. Les vues sont
construites en Kotlin, et l'activité est une `android.app.Activity`.

C'est un choix de robustesse, pas d'esthétique : cette application doit se
construire du premier coup sur une machine dont l'auteur de ce fichier ne
peut rien vérifier. Chaque dépendance est une version à accorder, chaque
fichier de ressources une occasion de ne pas compiler pour une balise.

─── CE QUE L'ÉCRAN DOIT PERMETTRE, ET RIEN DE PLUS ──────────────────────

Régler l'adresse et le jeton. Allumer. ESSAYER — et c'est le bouton le
plus important : sans lui, on ne découvre qu'un jeton est faux qu'au
premier vrai paiement, c'est-à-dire au pire moment. Et lire le journal,
qui dit si le dernier SMS est parti ou pourquoi il ne l'est pas.
═══════════════════════════════════════════════════════════════════════════
*/
class EcranPrincipal : Activity() {

    private lateinit var reglages: Reglages
    private lateinit var champRelais: EditText
    private lateinit var champJeton: EditText
    private lateinit var champExpediteurs: EditText
    private lateinit var caseActif: CheckBox
    private lateinit var vueJournal: TextView
    private lateinit var vueEtat: TextView

    override fun onCreate(etat: Bundle?) {
        super.onCreate(etat)
        reglages = Reglages(this)

        val racine = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }

        racine.addView(titre("VaultEx Changeur"))
        racine.addView(
            paragraphe(
                "Transmet au relais les SMS d'encaissement de ton opérateur, " +
                    "pour que les ACHATS de tes clients soient vérifiés " +
                    "automatiquement.\n\n" +
                    "Seuls les SMS des expéditeurs listés plus bas quittent ce " +
                    "téléphone. Rien d'autre."
            )
        )

        vueEtat = TextView(this).apply {
            setPadding(0, 20, 0, 20)
            textSize = 14f
        }
        racine.addView(vueEtat)

        racine.addView(etiquette("Adresse du relais"))
        champRelais = EditText(this).apply {
            setText(reglages.relais)
            hint = "https://vaultex-prix.xxx.workers.dev"
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine()
        }
        racine.addView(champRelais)

        racine.addView(etiquette("Jeton (CHANGE_SMS_TOKEN)"))
        champJeton = EditText(this).apply {
            setText(reglages.jeton)
            /*
            MASQUÉ PAR DÉFAUT. Ce jeton peut faire envoyer de la crypto :
            il n'a pas à rester lisible sur un écran qu'on ouvre devant un
            client, ni à partir dans une capture d'écran d'aide.
            */
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine()
        }
        racine.addView(champJeton)
        racine.addView(
            petit(
                "Le même que dans Cloudflare → Variables. Ce n'est PAS celui " +
                    "du bot Telegram."
            )
        )

        racine.addView(etiquette("Expéditeurs transmis (séparés par des virgules)"))
        champExpediteurs = EditText(this).apply {
            setText(reglages.expediteurs)
            setSingleLine()
        }
        racine.addView(champExpediteurs)
        racine.addView(
            petit(
                "Si un encaissement n'est pas transmis, regarde le journal : " +
                    "il affiche l'expéditeur écarté. Ajoute-le ici tel quel."
            )
        )

        caseActif = CheckBox(this).apply {
            text = "Transmettre les SMS"
            isChecked = reglages.actif
            setPadding(0, 30, 0, 10)
        }
        racine.addView(caseActif)

        val boutons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        boutons.addView(
            Button(this).apply {
                text = "Enregistrer"
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { enregistrer() }
            }
        )
        boutons.addView(
            Button(this).apply {
                text = "Essayer"
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { essayer() }
            }
        )
        racine.addView(boutons)

        racine.addView(etiquette("Journal"))
        vueJournal = TextView(this).apply {
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(Color.GRAY)
        }
        racine.addView(vueJournal)

        racine.addView(
            Button(this).apply {
                text = "Rejouer les envois en attente"
                setOnClickListener { rejouer() }
            }
        )

        val defilement = ScrollView(this)
        defilement.addView(racine)
        setContentView(defilement)

        demanderPermission()
    }

    override fun onResume() {
        super.onResume()
        rafraichir()
        /*
        ON REJOUE LA FILE À CHAQUE OUVERTURE.

        Le réseau revient souvent pendant que l'application est fermée. Si
        on n'essayait qu'à la réception d'un SMS, un encaissement en attente
        pourrait y rester jusqu'au paiement suivant — et sortir de la
        fenêtre de trois heures du relais entre-temps, donc ne plus servir
        à rien.
        */
        if (reglages.actif && reglages.configure) {
            Thread { Relais.rejouerFile(reglages) }.start()
        }
    }

    private fun enregistrer() {
        reglages.relais = champRelais.text.toString()
        reglages.jeton = champJeton.text.toString()
        reglages.expediteurs = champExpediteurs.text.toString()
        reglages.actif = caseActif.isChecked
        rafraichir()
        Toast.makeText(this, "Enregistré", Toast.LENGTH_SHORT).show()
    }

    /**
     * Envoie un SMS d'exemple pour vérifier l'adresse et le jeton.
     *
     * ═══════════════════════════════════════════════════════════════════
     * LE BOUTON LE PLUS IMPORTANT DE CET ÉCRAN
     * ═══════════════════════════════════════════════════════════════════
     *
     * Sans lui, on ne découvre qu'un jeton est mal collé qu'au premier
     * vrai paiement d'un client — c'est-à-dire au moment où quelqu'un
     * attend sa crypto, et où le message dit « aucun encaissement
     * trouvé ». Le changeur chercherait du côté du client, qui n'y est
     * pour rien.
     *
     * Le texte envoyé est volontairement RECONNAISSABLE comme un essai :
     * un franc, et un numéro qui n'existe pas. S'il était rapproché par
     * erreur d'une vraie demande, le montant ne correspondrait à rien.
     * ═══════════════════════════════════════════════════════════════════
     */
    private fun essayer() {
        enregistrer()
        if (!reglages.configure) {
            Toast.makeText(this, "Adresse ou jeton manquant", Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, "Essai en cours…", Toast.LENGTH_SHORT).show()
        Thread {
            val issue = Relais.envoyer(
                reglages,
                "OrangeMoney",
                "Vous avez recu 1 FCFA de 00000000. Ref: ESSAI-VAULTEX"
            )
            reglages.journaliser(if (issue.ok) "✓ essai : ${issue.message}" else "✗ essai : ${issue.message}")
            runOnUiThread {
                rafraichir()
                Toast.makeText(
                    this,
                    if (issue.ok) "Le relais répond et accepte le jeton" else issue.message,
                    Toast.LENGTH_LONG
                ).show()
            }
        }.start()
    }

    private fun rejouer() {
        Thread {
            Relais.rejouerFile(reglages)
            runOnUiThread { rafraichir() }
        }.start()
    }

    private fun rafraichir() {
        val attente = reglages.lireFile().size
        val permission = aLaPermission()
        vueEtat.text = buildString {
            append(if (reglages.actif) "● Actif" else "○ Éteint")
            append("   ·   ")
            append(if (permission) "SMS autorisés" else "⚠ SMS NON autorisés")
            if (attente > 0) append("   ·   $attente en attente")
        }
        vueEtat.setTextColor(
            when {
                !permission -> Color.rgb(200, 80, 60)
                reglages.actif -> Color.rgb(40, 150, 90)
                else -> Color.GRAY
            }
        )
        vueJournal.text = reglages.journal().ifEmpty { "(rien encore)" }
    }

    // ─── La permission ──────────────────────────────────────────────

    private fun aLaPermission(): Boolean =
        checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

    private fun demanderPermission() {
        if (!aLaPermission()) {
            requestPermissions(arrayOf(Manifest.permission.RECEIVE_SMS), 1)
        }
    }

    override fun onRequestPermissionsResult(
        code: Int,
        permissions: Array<out String>,
        resultats: IntArray
    ) {
        super.onRequestPermissionsResult(code, permissions, resultats)
        rafraichir()
        if (!aLaPermission()) {
            /*
            SANS LA PERMISSION, RIEN NE MARCHE ET IL FAUT LE DIRE FORT.

            Le receveur ne serait jamais appelé, l'application aurait l'air
            de fonctionner, et les achats ne seraient jamais vérifiés. Un
            écran qui ment par omission est pire qu'un écran en panne.
            */
            Toast.makeText(
                this,
                "Sans l'accès aux SMS, rien ne sera transmis. " +
                    "Réglages → Applications → VaultEx Changeur → Autorisations.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ─── Les briques d'affichage ────────────────────────────────────

    private fun titre(texte: String) = TextView(this).apply {
        text = texte
        textSize = 22f
        gravity = Gravity.CENTER
        setPadding(0, 0, 0, 20)
    }

    private fun paragraphe(texte: String) = TextView(this).apply {
        text = texte
        textSize = 13f
        setTextColor(Color.GRAY)
    }

    private fun etiquette(texte: String) = TextView(this).apply {
        text = texte
        textSize = 13f
        setPadding(0, 26, 0, 4)
    }

    private fun petit(texte: String): View = TextView(this).apply {
        text = texte
        textSize = 11f
        setTextColor(Color.GRAY)
        setPadding(0, 4, 0, 0)
    }
}
