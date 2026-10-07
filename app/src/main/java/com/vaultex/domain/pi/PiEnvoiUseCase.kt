package com.vaultex.domain.pi

import com.vaultex.core.crypto.PiWallet
import com.vaultex.core.crypto.PiXdr
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/*
═══════════════════════════════════════════════════════════════════════════
ENVOYER DES PI — L'ORCHESTRATION, ET TOUT CE QUI PEUT MAL TOURNER
═══════════════════════════════════════════════════════════════════════════

Les octets d'une transaction sont construits par PiXdr, prouvé contre les
vecteurs du SDK Stellar officiel. Ce fichier-ci ne fabrique rien : il
décide. Et c'est ici que se joue la différence entre « ça marche quand tout
va bien » et « ça marche ».

─── LE DANGER PRINCIPAL N'EST PAS LA SIGNATURE, C'EST LE DOUBLE ENVOI ───

Signer faux échoue proprement : le réseau refuse, rien ne bouge, aucun
frais. Le vrai danger est ailleurs, et il est banal sur un réseau mobile :
la requête part, la réponse se perd. L'application ne sait pas si les Pi
sont partis. Si elle renvoie, elle peut débiter deux fois.

La parade tient à une propriété de cette famille de réseaux : L'EMPREINTE
D'UNE TRANSACTION SE CALCULE AVANT DE LA DIFFUSER. On la connaît, on
l'écrit sur le disque, et en cas de doute on DEMANDE au réseau ce qu'elle
est devenue au lieu de deviner. Combinée à la date limite inscrite dans la
transaction, l'absence de réponse devient une information exploitable :
passé cette date, la transaction ne pourra plus JAMAIS être appliquée, donc
reconstruire est sûr.

C'est reconcilier() qui porte cela, et aucun envoi ne commence sans être
passé par elle.

─── LES SIX GARDES, ET CE QUE CHACUNE ÉVITE ─────────────────────────────

1. LA DESTINATION EXISTE-T-ELLE ? Un PAIEMENT vers une adresse jamais
   créditée ÉCHOUE sur cette famille de réseaux — il faut une CRÉATION DE
   COMPTE. C'est la première cause d'envoi raté, et elle ne se devine pas.
   Une adresse VaultEx neuve est exactement dans ce cas, donc le premier
   envoi d'un utilisateur vers un ami tombe dessus.

2. LA RÉSERVE. Le réseau oblige à laisser un minimum sur le compte. Envoyer
   son solde entier échoue, et les frais sont brûlés. Le montant maximum
   réellement envoyable est donc solde − réserve − frais, et c'est ce que
   rend disponible().

3. LE MÉMO. Un dépôt en bourse sans mémo est perdu : c'est le mémo qui dit
   à qui créditer. L'application ne peut pas savoir si la destination en
   exige un, donc elle ne devine pas — mais elle le permet, le mesure en
   octets, et refuse de tronquer.

4. S'ENVOYER À SOI-MÊME. Ne déplace rien et brûle les frais.

5. LA DATE LIMITE. Deux minutes. Sans elle, une diffusion perdue reste
   applicable indéfiniment et peut ressurgir des semaines plus tard.

6. LA SÉQUENCE. Relue à chaque envoi, et un second essai si une autre
   transaction est passée entre-temps.
═══════════════════════════════════════════════════════════════════════════
*/

sealed class ResultatEnvoiPi {
    /** Inscrite dans un grand livre. [empreinte] identifie la transaction. */
    data class Reussi(val empreinte: String, val compteCree: Boolean) : ResultatEnvoiPi()

    /**
     * Refus net : les Pi n'ont PAS été envoyés, et on en est sûr.
     *
     * [fraisPreleves] distingue les deux sortes de refus, parce qu'elles
     * se voient différemment sur le solde. Un refus avant inscription ne
     * coûte rien ; un `tx_failed` est entré dans un grand livre et a
     * prélevé ses frais. Annoncer « rien n'a bougé » dans le second cas
     * serait démenti par le solde quelques secondes plus tard.
     */
    data class Refuse(
        val raison: String,
        val fraisPreleves: Boolean = false
    ) : ResultatEnvoiPi()

    /**
     * On ne sait pas. L'envoi précédent n'a pas rendu de verdict.
     *
     * Ce n'est ni un succès ni un échec, et le présenter comme l'un des
     * deux serait mentir. L'écran doit dire d'attendre : reconcilier()
     * tranchera dès que le réseau répondra.
     */
    data class Indetermine(val empreinte: String, val raison: String) : ResultatEnvoiPi()

    /** Refus avant toute signature : rien n'a jamais quitté l'appareil. */
    data class Invalide(val raison: String) : ResultatEnvoiPi()
}

/** Ce qu'il faut savoir pour remplir l'écran d'envoi. */
data class CapaciteEnvoiPi(
    val adresse: String,
    val soldeStroops: Long,
    val reserveStroops: Long,
    val fraisStroops: Long,
    val disponibleStroops: Long
)

@Singleton
class PiEnvoiUseCase @Inject constructor(
    private val api: com.vaultex.data.remote.api.PiHorizonApi,
    private val comptes: PiCompteService,
    private val reseau: PiReseau,
    private val secureStorage: com.vaultex.core.security.SecureStorage
) {

    private val gson = com.google.gson.Gson()
    private val verrou = Mutex()

    /** Trace d'un envoi diffusé dont on attend encore le verdict. */
    private data class EnvoiEnCours(
        val empreinte: String = "",
        val finValiditeEpochSec: Long = 0L,
        val montantStroops: Long = 0L,
        val destination: String = ""
    )

    /**
     * Ce que l'utilisateur peut envoyer, réserve et frais déduits.
     *
     * Appelée par l'écran d'envoi AVANT toute saisie : c'est elle qui
     * alimente le bouton « Max ». Sans elle, « Max » proposerait le solde
     * entier, et chaque utilisation produirait une transaction refusée dont
     * les frais seraient perdus.
     */
    suspend fun capacite(mnemonic: String, passphrase: String): CapaciteEnvoiPi? {
        val paire = runCatching {
            PiWallet.derivePaire(
                org.web3j.crypto.MnemonicUtils.generateSeed(mnemonic.trim(), passphrase)
            )
        }.getOrNull() ?: return null
        val params = reseau.parametres() ?: return null
        val compte = (comptes.etat(paire.adresse) as? EtatComptePi.Existe)?.compte
            ?: return CapaciteEnvoiPi(paire.adresse, 0L, 0L, params.fraisDeBaseStroops, 0L)
        val frais = enchere(params.fraisDeBaseStroops, 1)
        return CapaciteEnvoiPi(
            adresse = paire.adresse,
            soldeStroops = compte.soldeStroops,
            reserveStroops = compte.reserveStroops(params.reserveDeBaseStroops),
            fraisStroops = frais,
            disponibleStroops = compte.disponibleStroops(params.reserveDeBaseStroops, frais)
        )
    }

    /**
     * Tranche le sort d'un envoi diffusé dont on n'a pas eu la réponse.
     *
     * ═══════════════════════════════════════════════════════════════════
     * LA FONCTION QUI EMPÊCHE DE PAYER DEUX FOIS
     * ═══════════════════════════════════════════════════════════════════
     *
     * Rend null quand il n'y a rien en suspens — le cas normal.
     *
     * Sinon, trois issues :
     *
     *   TROUVÉE sur le réseau → l'envoi avait abouti. On le dit, et on
     *   efface la trace. L'utilisateur apprend que ses Pi sont partis, au
     *   lieu de renvoyer.
     *
     *   ABSENTE, ET LA DATE LIMITE EST PASSÉE → elle ne pourra plus jamais
     *   être appliquée. C'est le seul état qui autorise à reconstruire. On
     *   efface et on laisse la voie libre.
     *
     *   ABSENTE, DATE LIMITE NON ÉCHUE → on ne sait pas encore. On REFUSE
     *   de laisser partir un nouvel envoi. Deux minutes d'attente valent
     *   mieux qu'un débit en double.
     * ═══════════════════════════════════════════════════════════════════
     */
    suspend fun reconcilier(): ResultatEnvoiPi? = verrou.withLock { reconcilierSansVerrou() }

    /*
    VERSION SANS VERROU, pour l'appel depuis envoyer().

    Un Mutex de kotlinx n'est PAS réentrant : si envoyer() prend le verrou
    puis appelle un reconcilier() qui le reprend, la coroutine s'endort sur
    son propre verrou et ne se réveille jamais. L'écran d'envoi se figerait
    au premier essai, définitivement.

    D'où deux fonctions au lieu d'une. La publique verrouille, l'interne
    suppose le verrou déjà tenu — et c'est le cas dans les deux seuls
    endroits qui l'appellent.
    */
    private suspend fun reconcilierSansVerrou(): ResultatEnvoiPi? {
        val trace = lireTrace() ?: return null
        val maintenant = System.currentTimeMillis() / 1000

        val inscrite = try {
            api.transaction(trace.empreinte).takeIf { it.hash != null }
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 404) null else return ResultatEnvoiPi.Indetermine(
                trace.empreinte, "Le réseau Pi ne répond pas. Ne renvoie pas : on vérifie."
            )
        } catch (_: Exception) {
            return ResultatEnvoiPi.Indetermine(
                trace.empreinte, "Le réseau Pi ne répond pas. Ne renvoie pas : on vérifie."
            )
        }

        if (inscrite != null) {
            secureStorage.savePiEnvoiEnCours(null)
            /*
            `successful` à faux veut dire INSCRITE MAIS ÉCHOUÉE : les frais
            ont été prélevés, le montant n'est pas parti. C'est un état réel
            de cette famille de réseaux, et il faut le distinguer d'un
            succès — sinon on annonce des Pi envoyés qui ne le sont pas.
            */
            return if (inscrite.successful == false)
                ResultatEnvoiPi.Refuse(
                    "L'envoi a été inscrit mais a échoué. Les Pi n'ont pas quitté " +
                        "ton compte ; seuls les frais ont été prélevés.",
                    fraisPreleves = true
                )
            else ResultatEnvoiPi.Reussi(trace.empreinte, compteCree = false)
        }

        // Absente. Est-elle encore susceptible d'être appliquée ?
        if (maintenant <= trace.finValiditeEpochSec + MARGE_EXPIRATION_SEC) {
            return ResultatEnvoiPi.Indetermine(
                trace.empreinte,
                "Un envoi est en cours de vérification. Attends une minute avant " +
                    "de réessayer — c'est ce qui évite d'envoyer deux fois."
            )
        }

        // Périmée et jamais appliquée : désormais impossible. On repart propre.
        secureStorage.savePiEnvoiEnCours(null)
        return null
    }

    /**
     * Envoie [montantTexte] Pi à [destination].
     *
     * Sérialisée par un verrou : deux envois simultanés partageraient le
     * même numéro de séquence, et le second serait refusé après que le
     * premier a déjà prélevé ses frais.
     */
    suspend fun envoyer(
        mnemonic: String,
        passphrase: String,
        destination: String,
        montantTexte: String,
        memo: PiXdr.Memo = PiXdr.Memo.Aucun
    ): ResultatEnvoiPi = verrou.withLock {

        // ── Garde 0 : un envoi en suspens bloque tout ─────────────────
        reconcilierSansVerrou()?.let { return@withLock it }

        // ── Gardes de saisie : avant tout appel réseau ────────────────
        val cible = destination.trim()
        if (!PiWallet.adresseValide(cible))
            return@withLock ResultatEnvoiPi.Invalide(
                "Adresse Pi invalide. Une adresse Pi commence par G et fait " +
                    "56 caractères."
            )

        val montantStroops = PiXdr.stroopsDepuisTexte(montantTexte)
            ?: return@withLock ResultatEnvoiPi.Invalide(
                "Montant invalide. Le Pi se compte avec 7 décimales au maximum."
            )

        if (memo is PiXdr.Memo.Texte && !PiXdr.memoTexteValide(memo.valeur))
            return@withLock ResultatEnvoiPi.Invalide(
                "Le mémo dépasse ${PiXdr.MEMO_TEXTE_MAX_OCTETS} caractères. " +
                    "Raccourcis-le plutôt que de le laisser couper : c'est lui qui " +
                    "dit à qui créditer le dépôt."
            )

        val paire = runCatching {
            PiWallet.derivePaire(
                org.web3j.crypto.MnemonicUtils.generateSeed(mnemonic.trim(), passphrase)
            )
        }.getOrNull() ?: return@withLock ResultatEnvoiPi.Invalide(
            "Portefeuille illisible. Rouvre l'application."
        )

        /*
        GARDE 4 : S'ENVOYER À SOI-MÊME.

        Ne déplace rien et brûle les frais. On refuse ici plutôt que de
        laisser le réseau accepter une opération inutile et payante.
        */
        if (cible.equals(paire.adresse, ignoreCase = true))
            return@withLock ResultatEnvoiPi.Invalide(
                "C'est ta propre adresse Pi. Rien ne serait déplacé, et les frais " +
                    "seraient quand même prélevés."
            )

        val params = reseau.parametres()
            ?: return@withLock ResultatEnvoiPi.Refuse(
                "Impossible de joindre le réseau Pi. Vérifie ta connexion et " +
                    "réessaie — aucun Pi n'a bougé."
            )

        // ── L'état des deux comptes ───────────────────────────────────
        val source = when (val etat = comptes.etat(paire.adresse)) {
            is EtatComptePi.Existe -> etat.compte
            EtatComptePi.Inexistant -> return@withLock ResultatEnvoiPi.Refuse(
                "Ton adresse Pi n'a encore jamais reçu de Pi : il n'y a rien à " +
                    "envoyer."
            )
            EtatComptePi.Indetermine -> return@withLock ResultatEnvoiPi.Refuse(
                "Impossible de lire ton solde Pi. Réessaie — aucun Pi n'a bougé."
            )
        }

        /*
        GARDE 1 : LA DESTINATION EXISTE-T-ELLE ?

        C'est la garde la plus importante de cette fonction, et la moins
        évidente. Sur cette famille de réseaux, payer une adresse qui n'a
        jamais été créditée ÉCHOUE — il faut l'opération de création de
        compte, avec un solde de départ au moins égal à la réserve.

        « Indéterminé » n'autorise PAS à choisir : émettre un paiement vers
        un compte inexistant échoue, et émettre une création vers un compte
        existant échoue aussi. Dans les deux cas les frais sont brûlés. On
        s'arrête donc plutôt que de tirer à la courte paille.
        */
        val creation = when (comptes.etat(cible)) {
            is EtatComptePi.Existe -> false
            EtatComptePi.Inexistant -> true
            EtatComptePi.Indetermine -> return@withLock ResultatEnvoiPi.Refuse(
                "Impossible de vérifier l'adresse de destination sur le réseau Pi. " +
                    "Réessaie — aucun Pi n'a bougé."
            )
        }

        val frais = enchere(params.fraisDeBaseStroops, 1)

        if (creation && montantStroops < params.reserveDeBaseStroops * 2) {
            val minimum = PiXdr.texteDepuisStroops(params.reserveDeBaseStroops * 2)
            return@withLock ResultatEnvoiPi.Invalide(
                "Cette adresse n'a encore jamais reçu de Pi : son compte doit être " +
                    "créé, et le réseau exige au moins $minimum Pi pour cela. " +
                    "Augmente le montant."
            )
        }

        // ── Garde 2 : la réserve ──────────────────────────────────────
        val disponible = source.disponibleStroops(params.reserveDeBaseStroops, frais)
        if (montantStroops > disponible) {
            val max = PiXdr.texteDepuisStroops(disponible)
            val reserve = PiXdr.texteDepuisStroops(source.reserveStroops(params.reserveDeBaseStroops))
            return@withLock ResultatEnvoiPi.Invalide(
                if (disponible <= 0L)
                    "Solde insuffisant. Le réseau Pi oblige à laisser $reserve Pi " +
                        "sur le compte, et il faut en plus de quoi payer les frais."
                else
                    "Montant trop élevé : $max Pi au maximum. Le réseau Pi oblige à " +
                        "laisser $reserve Pi sur le compte, frais en plus."
            )
        }

        val operation =
            if (creation) PiXdr.Operation.CreationCompte(cible, montantStroops)
            else PiXdr.Operation.Paiement(cible, montantStroops)

        // ── Diffusion, avec un second essai sur séquence dépassée ─────
        var sequence = source.numeroSequence
        repeat(ESSAIS) { essai ->
            val resultat = tenter(
                paire, params, sequence + 1, frais, memo, operation,
                montantStroops, cible, creation
            )
            if (resultat is RetourTentative.SequenceADepasser && essai < ESSAIS - 1) {
                /*
                Une autre transaction est partie de ce compte entre-temps —
                un autre appareil, ou la même phrase utilisée ailleurs. On
                relit la séquence et on reconstruit : la transaction
                refusée n'a rien consommé, donc reconstruire est sûr.
                */
                sequence = (comptes.etat(paire.adresse) as? EtatComptePi.Existe)
                    ?.compte?.numeroSequence ?: return@withLock ResultatEnvoiPi.Refuse(
                    "Une autre transaction est passée entre-temps et le solde n'a " +
                        "pas pu être relu. Réessaie."
                )
                return@repeat
            }
            return@withLock resultat.versResultat()
        }
        ResultatEnvoiPi.Refuse(
            "Une autre transaction est passée entre-temps à chaque essai. " +
                "Réessaie dans quelques secondes."
        )
    }

    // ─── Une tentative : construire, tracer, signer, diffuser ────────

    private sealed class RetourTentative {
        data class Fini(val resultat: ResultatEnvoiPi) : RetourTentative()
        data object SequenceADepasser : RetourTentative()

        fun versResultat(): ResultatEnvoiPi = when (this) {
            is Fini -> resultat
            SequenceADepasser -> ResultatEnvoiPi.Refuse(
                "Une autre transaction est passée entre-temps. Réessaie."
            )
        }
    }

    private suspend fun tenter(
        paire: PiWallet.PaireCles,
        params: ParametresPi,
        sequence: Long,
        frais: Long,
        memo: PiXdr.Memo,
        operation: PiXdr.Operation,
        montantStroops: Long,
        cible: String,
        creation: Boolean
    ): RetourTentative {
        val finValidite = borneDeValidite()
        val tx = try {
            PiXdr.transaction(
                source = paire.adresse,
                fraisStroops = frais,
                numeroSequence = sequence,
                finValiditeEpochSec = finValidite,
                memo = memo,
                operations = listOf(operation)
            )
        } catch (e: IllegalArgumentException) {
            return RetourTentative.Fini(
                ResultatEnvoiPi.Invalide(e.message ?: "Transaction impossible à construire.")
            )
        }

        val idReseau = PiXdr.idReseau(params.phraseReseau)
        val empreinte = PiXdr.empreinte(tx, idReseau).enHex()

        /*
        ON ÉCRIT LA TRACE AVANT DE DIFFUSER, JAMAIS APRÈS.

        C'est tout l'intérêt de connaître l'empreinte d'avance. Si
        l'application est tuée pendant l'appel réseau — batterie, système
        qui récupère la mémoire — la trace est déjà sur le disque et
        reconcilier() saura quoi demander au prochain lancement.

        Écrire après la réponse ne protégerait de rien : le seul moment
        dangereux est justement celui où il n'y a pas de réponse.
        */
        secureStorage.savePiEnvoiEnCours(
            gson.toJson(EnvoiEnCours(empreinte, finValidite, montantStroops, cible))
        )

        val enveloppe = try {
            PiXdr.enveloppeSignee(tx, idReseau, paire.clePrivee, paire.clePublique)
        } catch (e: Exception) {
            // Rien n'est parti : la signature a échoué sur l'appareil.
            secureStorage.savePiEnvoiEnCours(null)
            com.vaultex.core.monitoring.reportUnlessCancelled("signature Pi", e)
            return RetourTentative.Fini(
                ResultatEnvoiPi.Invalide("Signature impossible. Aucun Pi n'a bougé.")
            )
        }

        return try {
            val rep = api.diffuser(enveloppe)
            secureStorage.savePiEnvoiEnCours(null)
            if (rep.successful == false) RetourTentative.Fini(
                ResultatEnvoiPi.Refuse(
                    "L'envoi a été inscrit mais a échoué. Les Pi n'ont pas quitté " +
                        "ton compte ; seuls les frais ont été prélevés.",
                    fraisPreleves = true
                )
            )
            else RetourTentative.Fini(
                ResultatEnvoiPi.Reussi(rep.hash ?: empreinte, creation)
            )
        } catch (e: retrofit2.HttpException) {
            /*
            UN REFUS LU EST UN REFUS SÛR.

            Horizon a examiné la transaction et l'a rejetée : rien n'est
            entré dans un grand livre, aucun frais n'a été prélevé. On peut
            donc effacer la trace et laisser l'utilisateur corriger.

            Mais seulement si on a RÉUSSI À LIRE les codes. Un 500, un 502,
            une passerelle qui répond du HTML : la transaction a peut-être
            été reçue. On garde alors la trace et on rend « indéterminé ».
            */
            val corps = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
            val codes = PiCodesResultat.lire(corps)
            if (!PiCodesResultat.estRejetDefinitif(codes)) {
                return RetourTentative.Fini(
                    ResultatEnvoiPi.Indetermine(
                        empreinte,
                        "Le réseau Pi a répondu sans verdict lisible. Ne renvoie pas : " +
                            "on vérifie dans un instant si l'envoi est passé."
                    )
                )
            }
            secureStorage.savePiEnvoiEnCours(null)
            if (codes.transaction == "tx_bad_seq") return RetourTentative.SequenceADepasser
            com.vaultex.core.monitoring.AdminBot.sendFailed(
                "PI", codes.transaction ?: codes.operations.firstOrNull()
            )
            RetourTentative.Fini(
                ResultatEnvoiPi.Refuse(
                    PiCodesResultat.message(codes),
                    fraisPreleves = PiCodesResultat.fraisPreleves(codes)
                )
            )
        } catch (e: Exception) {
            /*
            LE CAS QUI COMPTE : PAS DE RÉPONSE DU TOUT.

            Coupure, délai dépassé, socket fermée. La transaction est
            peut-être partie. On NE TOUCHE PAS à la trace et on rend
            « indéterminé » — c'est reconcilier() qui tranchera, en
            demandant au réseau plutôt qu'en devinant.

            C'est le seul chemin de ce fichier où ne rien faire est la
            bonne action.
            */
            com.vaultex.core.monitoring.reportUnlessCancelled("diffusion Pi", e)
            RetourTentative.Fini(
                ResultatEnvoiPi.Indetermine(
                    empreinte,
                    "La connexion a été coupée pendant l'envoi. Ne renvoie pas : " +
                        "on vérifie si l'envoi est passé."
                )
            )
        }
    }

    // ─── Détails ─────────────────────────────────────────────────────

    /**
     * Instant après lequel la transaction devient inapplicable.
     *
     * C'EST ICI QU'EST IMPOSÉE LA RÈGLE que PiXdr ne peut pas imposer :
     * jamais zéro. Deux minutes suffisent largement à une diffusion, et
     * bornent la fenêtre pendant laquelle une transaction perdue pourrait
     * ressurgir.
     *
     * Trop court serait pire que trop long : une transaction périmée en
     * vol est refusée, et sur un réseau lent cela deviendrait la norme.
     */
    private fun borneDeValidite(): Long =
        System.currentTimeMillis() / 1000 + VALIDITE_SEC

    /**
     * Enchère de frais : le minimum du réseau, multiplié.
     *
     * Sur cette famille de réseaux, on ne paie PAS ce qu'on offre : on
     * paie ce qui était nécessaire, et l'enchère n'est qu'un plafond.
     * Enchérir au-dessus du minimum ne coûte donc rien et évite un refus
     * quand le réseau se charge. Le plafond absolu reste là pour qu'une
     * valeur aberrante venue d'Horizon ne puisse pas vider un compte.
     */
    private fun enchere(fraisDeBase: Long, operations: Int): Long =
        (fraisDeBase * operations * MULTIPLICATEUR_FRAIS)
            .coerceIn(1L, PLAFOND_FRAIS_TOTAL)

    private fun lireTrace(): EnvoiEnCours? {
        val json = secureStorage.getPiEnvoiEnCours() ?: return null
        val trace = try {
            gson.fromJson(json, EnvoiEnCours::class.java)
        } catch (_: Exception) { null }
        /*
        Une trace illisible ou sans empreinte ne peut rien nous apprendre :
        on ne saurait pas quoi demander au réseau. La garder bloquerait tous
        les envois à venir, pour rien. On l'efface.
        */
        if (trace == null || trace.empreinte.length != 64) {
            secureStorage.savePiEnvoiEnCours(null)
            return null
        }
        return trace
    }

    private fun ByteArray.enHex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        /** Deux minutes de validité. Voir borneDeValidite. */
        const val VALIDITE_SEC = 120L

        /**
         * Marge avant de déclarer une transaction définitivement périmée.
         *
         * L'horloge de l'appareil et celle du réseau ne sont pas
         * synchronisées, et un grand livre met quelques secondes à se
         * fermer. Déclarer périmé trop tôt rouvrirait la porte au double
         * envoi — exactement ce qu'on cherche à fermer. Une minute de
         * marge coûte une minute d'attente dans un cas rare.
         */
        const val MARGE_EXPIRATION_SEC = 60L

        /** Deux essais : le second sert au rattrapage de séquence. */
        const val ESSAIS = 2

        /** On enchérit au double du minimum. Voir enchere. */
        const val MULTIPLICATEUR_FRAIS = 2L

        /*
        PLAFOND ABSOLU DES FRAIS — RELEVÉ APRÈS MESURE.

        Il valait 1 000 000 de stroops (0,1 Pi), choisi quand on croyait
        les frais de base à 100 stroops : la marge paraissait énorme.

        Les frais de base réels sont 100 000 stroops, et l'enchère vaut le
        double, soit 200 000. La marge n'était donc plus que de cinq fois,
        et une hausse des frais votée par les validateurs aurait fait
        rogner l'enchère par ce plafond — silencieusement, pour produire
        un `tx_insufficient_fee` incompréhensible.

        Un plafond est là pour empêcher une valeur aberrante de vider un
        compte, pas pour brider le fonctionnement normal. Un Pi reste une
        barrière largement suffisante contre l'aberration, et laisse la
        place à cinq multiplications des frais du réseau.
        */
        const val PLAFOND_FRAIS_TOTAL = 10_000_000L
    }
}
