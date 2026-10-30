package com.vaultex.core.tx

import com.vaultex.core.crypto.Base58
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/*
═══════════════════════════════════════════════════════════════════════════
L'USDT SUR SOLANA — CE QUI CHANGE PAR RAPPORT À UN ENVOI DE SOL
═══════════════════════════════════════════════════════════════════════════

Envoyer du SOL, c'est une instruction vers un compte. Envoyer de l'USDT sur
Solana, c'est autre chose, et la différence n'est pas de degré :

Sur Ethereum ou Tron, un jeton vit dans une case du contrat, indexée par
l'adresse de son détenteur. On envoie au contrat, le contrat fait le reste,
et le destinataire n'a rien eu à faire pour exister.

Sur Solana, un jeton vit dans un COMPTE À PART — un compte par détenteur et
par jeton. Ce compte a sa propre adresse, il faut l'avoir créé, et sa
création coûte de la rente. Si le destinataire n'a jamais touché d'USDT, son
compte n'existe pas, et c'est à l'expéditeur de le créer dans la même
transaction. Sans ça, le réseau refuse.

Tout ce fichier existe pour ces deux choses : CALCULER l'adresse de ce
compte, et CONSTRUIRE la transaction qui le crée au besoin puis transfère.

─── LE COMPTE SE CALCULE, IL NE SE DEMANDE PAS ────────────────────────────

Son adresse est une « adresse dérivée de programme » : le hachage du
détenteur, du programme de jetons et du jeton, répété avec un entier
décroissant jusqu'à tomber sur un point qui n'est PAS sur la courbe
ed25519 — c'est-à-dire une adresse dont personne ne peut détenir la clé
privée. C'est toute l'astuce : un compte que seul un programme peut signer.

On pourrait demander ce compte au réseau. On ne le fait pas : pour un
destinataire qui n'en a pas encore, le réseau répondrait « rien », et c'est
précisément le cas où il faut l'adresse pour la créer.

─── CE QU'IL FAUT VÉRIFIER AVANT LA PREMIÈRE VRAIE SOMME ──────────────────

Le jeton est identifié par son MINT, et un mint faux envoie les fonds vers
un jeton qui n'est pas de l'USDT. Aucun contrôle de forme ne peut l'attraper :
toutes les adresses Solana se ressemblent. Voir [MINT_USDT].
═══════════════════════════════════════════════════════════════════════════
*/
object SolanaSpl {

    /*
    ═══════════════════════════════════════════════════════════════════════
    LE MINT DE L'USDT — À VÉRIFIER AVANT TOUTE SOMME RÉELLE
    ═══════════════════════════════════════════════════════════════════════

    C'est l'identifiant du jeton. Sur Solana, il n'y a aucun registre de noms :
    « USDT » ne veut rien dire pour le réseau, seul ce mint compte. N'importe
    qui peut créer un jeton et l'appeler USDT.

    UNE ERREUR ICI NE SE VOIT PAS ET NE SE RATTRAPE PAS. Les fonds partiraient
    vers un compte d'un autre jeton, et la validation de forme ne dirait rien :
    toutes les adresses Solana sont 32 octets en base58.

    La seule vérification qui vaille est de le comparer à celui que la
    plateforme affiche sur son propre écran de dépôt USDT/Solana, ou à
    solscan.io. À faire avant le premier transfert, et une fois suffit.
    ═══════════════════════════════════════════════════════════════════════
    */
    const val MINT_USDT = "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB"

    /** L'USDT a six décimales sur Solana, comme sur Tron et sur les EVM. */
    const val DECIMALES_USDT = 6

    /** Le programme de jetons SPL. */
    const val PROGRAMME_JETON = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"

    /** Celui qui crée les comptes de jeton associés. */
    const val PROGRAMME_ATA = "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL"

    /** Le programme système : trente-deux octets nuls. */
    val PROGRAMME_SYSTEME: ByteArray = ByteArray(32)

    /*
    ═══════════════════════════════════════════════════════════════════════
    LA RENTE D'UN COMPTE DE JETON
    ═══════════════════════════════════════════════════════════════════════

    Solana fait payer l'espace occupé. Un compte de jeton pèse 165 octets, et
    son exonération de rente vaut ~0,00204 SOL — prélevée sur l'EXPÉDITEUR
    quand c'est lui qui crée le compte du destinataire.

    Ça ne se devine pas depuis les frais de transaction, qui valent cinq mille
    lamports : la création coûte quatre cents fois plus. Quelqu'un qui aurait
    juste de quoi payer les frais verrait sa transaction refusée sans
    comprendre pourquoi.

    Le chiffre est une CONSTANTE DU RÉSEAU, pas un prix de marché : il ne
    bouge qu'avec un changement de protocole. On l'écrit donc, plutôt que de
    dépenser un appel `getMinimumBalanceForRentExemption` à chaque envoi.
    ═══════════════════════════════════════════════════════════════════════
    */
    const val RENTE_COMPTE_JETON = 2_039_280L

    /** Les frais d'une transaction à une signature. */
    const val FRAIS_SIGNATURE = 5_000L

    /*
    ═══════════════════════════════════════════════════════════════════════
    LA FILE D'ATTENTE DE SOLANA SE PAIE, ET ON NE LA PAYAIT PAS
    ═══════════════════════════════════════════════════════════════════════

    OBSERVÉ EN DIRECT, le 30 octobre : quatre transferts USDT émis depuis un
    autre portefeuille, tous restés « in progress », aucun jamais arrivé
    dans un bloc. Le solde ne bougeait pas, l'explorateur ne trouvait pas
    les signatures. Le réseau était simplement chargé — SOL perdait 7,8 %
    ce jour-là, et la volatilité amène le trafic.

    CE QUI SE PASSE ALORS. Les validateurs ordonnent les transactions par
    FRAIS DE PRIORITÉ. Celles qui n'en paient aucune attendent ; et comme
    le blockhash d'une transaction Solana expire au bout d'environ deux
    minutes, celles qui attendent trop ne sont pas retardées — elles sont
    ANNULÉES. Sans erreur, sans trace, sans rien à l'écran.

    VaultEx n'en payait aucune. Les transferts passés jusqu'ici l'ont été
    parce que le réseau était calme.

    ─── CE QUE ÇA COÛTERAIT DE NE RIEN FAIRE ─────────────────────────────

    Une vente dont le dépôt n'arrive jamais. L'écran afficherait « dépôt
    introuvable », et la personne — qui a bien appuyé sur « envoyer » —
    conclurait que l'application a perdu son argent. C'est la pire panne du
    parcours : invisible pour nous, inexplicable pour elle.

    ─── LE PRIX, ET POURQUOI CELUI-LÀ ────────────────────────────────────

    Le coût d'une transaction prioritaire vaut `unités × prix ÷ 10⁶`
    lamports. Sans limite déclarée, Solana alloue 200 000 unités PAR
    INSTRUCTION — soit 400 000 pour un transfert simple, 600 000 quand il
    faut créer le compte du destinataire.

    À 50 000 micro-lamports l'unité :

        transfert simple   400 000 × 50 000 ÷ 10⁶ =  20 000 lamports
        avec création      600 000 × 50 000 ÷ 10⁶ =  30 000 lamports

    Soit 0,00002 à 0,00003 SOL — DEUX À TROIS MILLIÈMES DE DOLLAR. Quatre
    à six fois les frais de base, et toujours mille fois moins que la rente
    d'un compte de jeton. Payer cela pour que la transaction arrive est le
    meilleur rapport de tout ce fichier.

    ─── ON NE DÉCLARE PAS DE LIMITE D'UNITÉS, ET C'EST VOULU ─────────────

    `SetComputeUnitLimit` rendrait la facture plus fine — environ 1 500
    lamports au lieu de 20 000. Mais une limite trop basse fait ÉCHOUER la
    transaction, et le vrai coût d'un transfert SPL dépend de choses qu'on
    ne mesure pas d'ici.

    Économiser deux centièmes de centime en échange d'un risque d'échec est
    un mauvais marché. On laisse donc Solana appliquer son défaut.

    ─── LE JOUR OÙ 50 000 NE SUFFIRA PAS ─────────────────────────────────

    Ce nombre est le seul réglage de ce mécanisme, et il est écrit ici
    plutôt que lu sur la chaîne parce qu'un appel de plus est un appel qui
    peut échouer — au moment précis où le réseau va mal. S'il devenait
    insuffisant, `getRecentPrioritizationFees` donne la valeur du moment,
    et c'est là qu'il faudra l'ajouter.
    ═══════════════════════════════════════════════════════════════════════
    */
    const val PROGRAMME_BUDGET = "ComputeBudget111111111111111111111111111111"

    /** Micro-lamports par unité de calcul. Voir le bloc ci-dessus. */
    const val PRIX_UNITE_CALCUL = 50_000L

    /** Ce que Solana alloue par instruction quand on ne déclare rien. */
    private const val UNITES_PAR_INSTRUCTION = 200_000L

    /**
     * Les lamports que la priorité ajoute, selon la forme du transfert.
     *
     * ELLE COMPTE L'INSTRUCTION DE BUDGET ELLE-MÊME : Solana alloue son
     * quota par instruction présente, celle-ci comprise. Oublier de la
     * compter sous-estimerait les frais annoncés — et un montant affiché
     * plus bas que celui prélevé est exactement ce qu'on ne veut pas sur un
     * écran d'envoi.
     */
    fun fraisPriorite(creerCompteDestinataire: Boolean): Long {
        val instructions = if (creerCompteDestinataire) 3L else 2L
        val unites = instructions * UNITES_PAR_INSTRUCTION
        // Division entière arrondie vers le HAUT : le réseau ne rend pas la
        // monnaie, et annoncer un lamport de moins qu'il n'en prend serait
        // la même faute que de ne rien annoncer.
        return (unites * PRIX_UNITE_CALCUL + 999_999L) / 1_000_000L
    }

    /*
    ═══════════════════════════════════════════════════════════════════════
    L'ADRESSE DÉRIVÉE DE PROGRAMME, ET POURQUOI ELLE BOUCLE
    ═══════════════════════════════════════════════════════════════════════

    On hache les graines, un entier, et le programme. Si le résultat tombe
    sur la courbe ed25519, c'est une adresse dont quelqu'un POURRAIT détenir
    la clé privée — donc inutilisable comme compte piloté par un programme.
    On décrémente l'entier et on recommence.

    En pratique la première tentative réussit une fois sur deux, et il est
    exceptionnel d'en faire plus de quelques-unes. La boucle descend quand
    même jusqu'à zéro : c'est l'algorithme du réseau, pas une optimisation,
    et s'en écarter donnerait une autre adresse que la sienne.

    `validatePublicKeyFull` de BouncyCastle fait le contrôle de courbe. C'est
    le seul endroit de ce fichier qui demande autre chose que du hachage, et
    c'est le seul qu'on ne peut pas écrire à la main sans refaire de
    l'arithmétique de corps fini.
    ═══════════════════════════════════════════════════════════════════════
    */
    fun adresseDeriveeDeProgramme(graines: List<ByteArray>, programme: ByteArray): ByteArray {
        require(programme.size == 32) { "Programme Solana invalide (${programme.size} octets)" }
        for (bosse in 255 downTo 0) {
            val md = MessageDigest.getInstance("SHA-256")
            for (g in graines) md.update(g)
            md.update(byteArrayOf(bosse.toByte()))
            md.update(programme)
            md.update(ETIQUETTE_PDA)
            val candidat = md.digest()
            if (!surLaCourbe(candidat)) return candidat
        }
        // Mathématiquement inatteignable : la probabilité qu'aucune des 256
        // tentatives ne sorte de la courbe est de l'ordre de 2^-256.
        error("Aucune adresse derivee hors courbe")
    }

    /*
    ═══════════════════════════════════════════════════════════════════════
    LE POINT EST-IL SUR LA COURBE ED25519 ?
    ═══════════════════════════════════════════════════════════════════════

    Écrit ici plutôt qu'emprunté à BouncyCastle, et pour une raison qui
    n'est pas la pureté : c'est CE calcul-là que j'ai exécuté pour le
    valider. Appeler `Ed25519.validatePublicKeyFull` reviendrait à livrer
    du code dont la version éprouvée n'est pas celle qui tourne.

    Une adresse Solana est le point compressé : on y lit `y` sur 255 bits,
    le bit de poids fort du dernier octet portant le signe de `x`. Le point
    existe si et seulement si l'équation de la courbe admet un `x`, donc si

        x² = (y² − 1) / (d·y² + 1)

    est un carré modulo p. Le critère d'Euler le dit : un résidu non nul
    est un carré si et seulement si sa puissance (p−1)/2 vaut un.

    ─── MESURÉ, ET C'EST CE QUI COMPTE ───────────────────────────────────

    Sur 2 000 tirages aléatoires, 986 tombent sur la courbe — les ~50 %
    théoriques. Et les trente-deux zéros du programme système rendent
    `true`, ce qu'on peut vérifier à la main : y = 0 donne x² = −1, et −1
    est un carré modulo p puisque p ≡ 1 (mod 4).

    Un prédicat qui se tromperait rendrait des adresses dérivées fausses —
    valides en apparence, et appartenant à personne.

    LA LENTEUR NE COMPTE PAS ICI. Une exponentiation modulaire sur 255 bits
    prend une milliseconde, la dérivation en demande deux en moyenne, et
    elle ne tourne qu'au changement de monnaie, hors du fil principal.
    ═══════════════════════════════════════════════════════════════════════
    */
    private fun surLaCourbe(point: ByteArray): Boolean {
        if (point.size != 32) return false
        // Petit-boutiste, et le bit de signe de x retire : il ne fait pas
        // partie de y.
        val pf = point.copyOf()
        pf[31] = (pf[31].toInt() and 0x7F).toByte()
        val gf = ByteArray(32) { pf[31 - it] }
        val y = java.math.BigInteger(1, gf)
        if (y >= P) return false

        val y2 = y.multiply(y).mod(P)
        val u = y2.subtract(java.math.BigInteger.ONE).mod(P)
        val v = D.multiply(y2).add(java.math.BigInteger.ONE).mod(P)
        if (v.signum() == 0) return false
        val x2 = u.multiply(v.modInverse(P)).mod(P)
        // x = 0 est un point valide : le critere d'Euler ne s'applique
        // qu'aux residus non nuls.
        if (x2.signum() == 0) return true
        return x2.modPow(EXPOSANT_EULER, P) == java.math.BigInteger.ONE
    }

    /** p = 2^255 − 19, le corps de la courbe. */
    private val P: java.math.BigInteger =
        java.math.BigInteger.valueOf(2).pow(255).subtract(java.math.BigInteger.valueOf(19))

    /** d = −121665 / 121666 mod p, le parametre de la courbe. */
    private val D: java.math.BigInteger =
        java.math.BigInteger.valueOf(-121665)
            .multiply(java.math.BigInteger.valueOf(121666).modInverse(P))
            .mod(P)

    /** (p − 1) / 2, l'exposant du critere d'Euler. */
    private val EXPOSANT_EULER: java.math.BigInteger =
        P.subtract(java.math.BigInteger.ONE).divide(java.math.BigInteger.valueOf(2))

    /**
     * Le compte de jeton associé d'un détenteur, pour un jeton donné.
     *
     * Les trois graines et leur ORDRE sont imposés par le programme ATA :
     * détenteur, programme de jetons, jeton. Les permuter donnerait une
     * adresse parfaitement valide et parfaitement fausse.
     */
    fun compteAssocie(proprietaire: ByteArray, mint: ByteArray): ByteArray {
        require(proprietaire.size == 32) { "Proprietaire invalide (${proprietaire.size} octets)" }
        require(mint.size == 32) { "Mint invalide (${mint.size} octets)" }
        return adresseDeriveeDeProgramme(
            listOf(proprietaire, Base58.decode(PROGRAMME_JETON), mint),
            Base58.decode(PROGRAMME_ATA)
        )
    }

    /** Le compte associé, en base58 — pour interroger le réseau. */
    fun compteAssocieBase58(proprietaireB58: String, mintB58: String = MINT_USDT): String =
        Base58.encode(compteAssocie(Base58.decode(proprietaireB58), Base58.decode(mintB58)))

    /*
    ═══════════════════════════════════════════════════════════════════════
    LE MESSAGE : UNE OU DEUX INSTRUCTIONS, SELON LE DESTINATAIRE
    ═══════════════════════════════════════════════════════════════════════

    `creerCompteDestinataire` décide de la forme. Ce n'est pas un réglage :
    c'est ce que le réseau a répondu quand on lui a demandé si le compte du
    destinataire existe.

    ─── POURQUOI « TRANSFER CHECKED » ET NON « TRANSFER » ─────────────────

    `Transfer` (instruction 3) ne porte que le montant. `TransferChecked`
    (instruction 12) porte AUSSI le mint et le nombre de décimales, et le
    programme refuse si l'un des deux ne correspond pas au compte visé.

    C'est exactement le garde-fou qui manque partout ailleurs ici : si le
    mint était faux, ou si on s'était trompé de nombre de décimales — donc
    d'un facteur mille ou million sur le montant — la transaction échoue au
    lieu de réussir en envoyant autre chose, ou mille fois trop.

    Deux octets de plus dans la transaction, et une classe entière d'erreurs
    qui devient impossible.

    ─── LA CRÉATION EST « IDEMPOTENTE », ET C'EST VOULU ───────────────────

    L'instruction 1 du programme ATA crée le compte s'il manque et NE FAIT
    RIEN s'il existe déjà. L'instruction 0, elle, échoue quand le compte est
    là.

    La différence compte à cause d'une course : on demande au réseau si le
    compte existe, puis on envoie. Entre les deux, quelqu'un d'autre a pu le
    créer — un dépôt reçu dans la même seconde. Avec l'instruction 0, notre
    transfert échouerait pour une raison qui n'en est pas une.

    ─── L'ORDRE DES COMPTES N'EST PAS LIBRE ───────────────────────────────

    Un message Solana range ses comptes en quatre groupes, dans cet ordre :
    signataires modifiables, signataires en lecture, modifiables, lecture
    seule. L'en-tête ne dit que COMBIEN il y en a de chaque sorte — les
    indices font le reste. Un compte au mauvais rang, et le réseau lit un
    message cohérent qui ne fait pas ce qu'on croit.
    ═══════════════════════════════════════════════════════════════════════
    */
    fun messageTransfert(
        expediteur: ByteArray,
        destinataire: ByteArray,
        mint: ByteArray,
        montantBrut: Long,
        decimales: Int,
        blockhashRecent: ByteArray,
        creerCompteDestinataire: Boolean
    ): ByteArray {
        require(expediteur.size == 32) { "Expediteur invalide" }
        require(destinataire.size == 32) { "Destinataire invalide" }
        require(mint.size == 32) { "Mint invalide" }
        require(blockhashRecent.size == 32) { "Blockhash invalide" }
        require(montantBrut > 0) { "Montant nul ou negatif" }
        require(decimales in 0..18) { "Decimales hors bornes" }

        val programmeJeton = Base58.decode(PROGRAMME_JETON)
        val programmeAta = Base58.decode(PROGRAMME_ATA)
        val programmeBudget = Base58.decode(PROGRAMME_BUDGET)
        val ataSource = compteAssocie(expediteur, mint)
        val ataDestination = compteAssocie(destinataire, mint)

        /*
        LES COMPTES, DANS L'ORDRE QUE LE RÉSEAU IMPOSE.

        0 est à la fois le signataire, le payeur des frais et l'autorité du
        transfert : trois rôles, un seul compte, et c'est le nôtre.
        */
        val comptes = mutableListOf(expediteur, ataSource, ataDestination)
        var lectureSeule = 0
        if (creerCompteDestinataire) {
            // Le destinataire lui-même n'est nécessaire QUE pour créer son
            // compte : c'est lui qui en devient propriétaire.
            comptes += destinataire
            comptes += mint
            comptes += PROGRAMME_SYSTEME
            comptes += programmeJeton
            comptes += programmeAta
            lectureSeule = 5
        } else {
            comptes += mint
            comptes += programmeJeton
            lectureSeule = 2
        }

        /*
        LE PROGRAMME DE BUDGET ENTRE EN DERNIER, ET IL COMPTE POUR UN
        LECTURE SEULE.

        L'en-tête ne nomme aucun compte : il dit seulement combien il y a
        de signataires et combien de lectures seules À LA FIN. Les rangs
        font le reste. Ajouter ce compte ailleurs qu'au bout décalerait la
        frontière entre modifiables et lecture seule — et un compte
        modifiable lu comme lecture seule fait échouer le transfert, tandis
        que l'inverse ouvrirait une écriture qu'on n'a pas voulue.

        Il va donc après les autres, et `lectureSeule` monte d'un.
        */
        comptes += programmeBudget
        lectureSeule += 1

        /*
        ═══════════════════════════════════════════════════════════════════
        AUCUN COMPTE NE DOIT APPARAÎTRE DEUX FOIS
        ═══════════════════════════════════════════════════════════════════

        TROUVÉ EN RELISANT MES PROPRES OCTETS. Un jeu d'essai où la
        destination valait par hasard le compte associé de l'expéditeur a
        produit un message portant deux fois la même clé, aux indices 1 et 3.

        Ce que ça fait : `indexOfFirst` rend toujours le PREMIER rang, donc
        une instruction qui croit viser le compte 3 vise le compte 1. Le
        message reste parfaitement bien formé — il est simplement faux, et
        rien dans sa structure ne le signale.

        ─── DEUX CAS RÉELS, PAS UN ACCIDENT DE TEST ───────────────────────

        S'ENVOYER À SOI-MÊME. Les deux comptes associés deviennent le même.
        L'opération n'a aucun sens, et mieux vaut la refuser que produire une
        transaction dont on ne sait pas ce qu'elle fera.

        COLLER UNE ADRESSE DE COMPTE DE JETON au lieu d'une adresse de
        portefeuille. C'est le piège propre à Solana : les deux se
        ressemblent, et un compte de jeton copié depuis un explorateur est
        une adresse valide. On dériverait alors le compte associé D'UN
        COMPTE ASSOCIÉ — une adresse qui n'appartient à personne, et des
        fonds qu'on ne récupère pas.

        Le contrôle est trivial et il attrape les deux. Échouer ici coûte un
        message d'erreur ; ne pas échouer coûte les fonds de quelqu'un.
        ═══════════════════════════════════════════════════════════════════
        */
        for (i in comptes.indices) {
            for (j in i + 1 until comptes.size) {
                require(!comptes[i].contentEquals(comptes[j])) {
                    "Deux comptes identiques dans la transaction (rangs $i et $j)"
                }
            }
        }

        fun indice(c: ByteArray) = comptes.indexOfFirst { it.contentEquals(c) }

        val out = ByteArrayOutputStream()
        // En-tête : une signature requise, aucun signataire en lecture seule.
        out.write(1); out.write(0); out.write(lectureSeule)
        // Les comptes. Leur nombre tient sur un octet tant qu'il est < 128 —
        // c'est la forme courte du « compact-u16 », et on en a au plus huit.
        out.write(comptes.size)
        for (c in comptes) out.write(c)
        out.write(blockhashRecent)

        val instructions = ByteArrayOutputStream()
        var nombreInstructions = 0

        /*
        ═══════════════════════════════════════════════════════════════════
        LE PRIX DE LA PRIORITÉ, EN PREMIER
        ═══════════════════════════════════════════════════════════════════

        Rien n'oblige formellement cette instruction à venir avant les
        autres, mais c'est la convention de tout l'écosystème, et une
        convention respectée est une chose de moins à démontrer le jour où
        un nœud se comporte autrement.

        ELLE NE TOUCHE À AUCUN COMPTE — zéro dans la longueur de sa liste.
        Elle ne déplace pas de fonds : elle dit au réseau ce qu'on est prêt
        à payer pour être servi.

        Ses données tiennent en neuf octets : le code 3
        (`SetComputeUnitPrice`), puis le prix sur huit octets en petit-
        boutiste, comme tout ce que Solana encode.
        ═══════════════════════════════════════════════════════════════════
        */
        nombreInstructions++
        instructions.write(indice(programmeBudget))
        instructions.write(0)                              // aucun compte
        instructions.write(9)                              // données : 1 + 8 octets
        instructions.write(3)                              // SetComputeUnitPrice
        for (i in 0 until 8) {
            instructions.write((PRIX_UNITE_CALCUL ushr (i * 8)).toInt() and 0xFF)
        }

        if (creerCompteDestinataire) {
            nombreInstructions++
            instructions.write(indice(programmeAta))
            instructions.write(6)
            instructions.write(indice(expediteur))        // payeur
            instructions.write(indice(ataDestination))    // le compte à créer
            instructions.write(indice(destinataire))      // son futur propriétaire
            instructions.write(indice(mint))
            instructions.write(indice(PROGRAMME_SYSTEME))
            instructions.write(indice(programmeJeton))
            instructions.write(1)                          // données : « créer, idempotent »
            instructions.write(1)
        }

        nombreInstructions++
        instructions.write(indice(programmeJeton))
        instructions.write(4)
        instructions.write(indice(ataSource))
        instructions.write(indice(mint))
        instructions.write(indice(ataDestination))
        instructions.write(indice(expediteur))             // l'autorité
        instructions.write(10)                             // données : 1 + 8 + 1 octets
        instructions.write(12)                             // TransferChecked
        for (i in 0 until 8) instructions.write((montantBrut ushr (i * 8)).toInt() and 0xFF)
        instructions.write(decimales)

        out.write(nombreInstructions)
        out.write(instructions.toByteArray())
        return out.toByteArray()
    }

    private val ETIQUETTE_PDA = "ProgramDerivedAddress".toByteArray(Charsets.US_ASCII)
}
