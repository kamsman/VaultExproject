package com.vaultex.core.crypto

/**
 * Standards BIP44 — paths de dérivation HD wallet par blockchain.
 * Format : m / purpose' / coin_type' / account' / change / address_index
 */
object Bip44Paths {
    // Coin types officiels SLIP-44
    const val COIN_TYPE_BTC = 0
    const val COIN_TYPE_ETH = 60
    const val COIN_TYPE_BNB = 60   // BSC partage le coin_type ETH (EVM compatible)
    const val COIN_TYPE_SOL = 501
    const val COIN_TYPE_TRX = 195
    const val COIN_TYPE_DOGE = 3

    /**
     * Construit le path BIP44 standard pour une chaîne donnée.
     * Account 0, Change 0 (external), Address 0 par défaut
     */
    fun pathFor(coinType: Int, account: Int = 0, addressIndex: Int = 0): String {
        return "m/44'/$coinType'/$account'/0/$addressIndex"
    }

    fun btcPath(addressIndex: Int = 0): String = "m/84'/$COIN_TYPE_BTC'/0'/0/$addressIndex"
    fun ethPath(addressIndex: Int = 0): String = pathFor(COIN_TYPE_ETH, 0, addressIndex)
    fun bnbPath(addressIndex: Int = 0): String = pathFor(COIN_TYPE_BNB, 0, addressIndex)
    fun solPath(addressIndex: Int = 0): String = "m/44'/$COIN_TYPE_SOL'/$addressIndex'/0'"  // Solana spec
    fun trxPath(addressIndex: Int = 0): String = pathFor(COIN_TYPE_TRX, 0, addressIndex)

    /*
    DOGECOIN EST EN m/44', PAS EN m/84'. Bitcoin derive en BIP-84 parce
    qu'il produit du SegWit ; Dogecoin n'en a pas, et reste sur le BIP-44
    historique. Recopier btcPath en changeant le type de piece donnerait
    une adresse valide et FAUSSE — personne n'y trouverait les fonds.
    */
    fun dogePath(addressIndex: Int = 0): String = pathFor(COIN_TYPE_DOGE, 0, addressIndex)
}

enum class Blockchain(
    val displayName: String,
    val ticker: String,
    val coinType: Int,
    val decimals: Int,
    val explorerUrl: String
) {
    BITCOIN("Bitcoin", "BTC", 0, 8, "https://blockstream.info/tx/"),
    ETHEREUM("Ethereum", "ETH", 60, 18, "https://etherscan.io/tx/"),
    BNB_CHAIN("BNB Smart Chain", "BNB", 60, 18, "https://bscscan.com/tx/"),
    SOLANA("Solana", "SOL", 501, 9, "https://solscan.io/tx/"),
    TRON("Tron", "TRX", 195, 6, "https://tronscan.org/#/transaction/"),

    /*
    ─── LE PI SE LIT, IL NE S'ENVOIE PAS ENCORE ───────────────────────────

    Cette entrée existe pour que le Pi puisse PORTER un solde et un cours à
    l'accueil, comme les autres monnaies. Elle n'ouvre aucun chemin de
    signature : PiWallet ne sait dériver qu'une adresse, et c'est tout ce
    dont une réception a besoin.

    L'ajouter ici a un effet voulu : le compilateur réclame maintenant une
    branche « PI » dans chaque `when (blockchain)` exhaustif de
    l'application. C'est exactement la garantie qu'on cherche — aucun écran
    ne peut traiter le Pi par défaut sans qu'on l'ait décidé.

    SEPT DÉCIMALES, pas dix-huit : le Pi est un réseau de la famille
    Stellar, et c'est la précision de cette famille. La prendre pour celle
    d'une chaîne EVM décalerait tout montant d'un facteur de onze chiffres.

    L'ADRESSE D'EXPLORATEUR N'EST PAS ENCORE VÉRIFIÉE, et rien ne la lit :
    aucun code de ce dépôt ne consulte `explorerUrl` aujourd'hui. Elle ne
    servira qu'au moment où une transaction Pi figurera dans l'historique —
    donc à l'étape de l'envoi. À confirmer sur appareil à ce moment-là,
    plutôt que de laisser croire qu'elle l'a déjà été.
    */
    PI("Pi Network", "PI", 314159, 7, "https://blockexplorer.minepi.com/mainnet/transactions/"),

    /*
    ─── LE DOGE SE LIT ET SE SIGNE, IL NE S'ENVOIE PAS ENCORE ─────────────

    Même raison d'être que l'entrée PI juste au-dessus, et même effet
    voulu : le compilateur réclame désormais une branche « DOGECOIN » dans
    chaque `when (blockchain)` exhaustif. Aucun écran ne peut traiter le
    Dogecoin par défaut sans qu'on l'ait décidé.

    Les briques existent et sont vérifiées — dérivation d'adresse,
    sérialisation, signature, choix des pièces, lecture des montants — mais
    AUCUN écran ne les appelle. L'envoi n'est pas branché.

    TANT QUE L'ENVOI N'EXISTE PAS, AUCUN ÉCRAN NE DOIT PROPOSER DE
    RECEVOIR DU DOGE. Une adresse qui reçoit sans pouvoir rendre est un
    piège : les fonds y entrent et n'en sortent plus.

    HUIT DÉCIMALES, comme Bitcoin — et le type de pièce est 3, pas 0.
    Recopier celui de Bitcoin produirait une adresse valide et fausse.

    L'ADRESSE D'EXPLORATEUR N'EST PAS VÉRIFIÉE, et rien ne la lit : aucun
    code de ce dépôt ne consulte `explorerUrl` aujourd'hui. À confirmer
    quand une transaction Dogecoin figurera dans l'historique, plutôt que
    de laisser croire qu'elle l'a déjà été.
    */
    DOGECOIN("Dogecoin", "DOGE", 3, 8, "https://dogechain.com/tx/");

    fun isEvm(): Boolean = this == ETHEREUM || this == BNB_CHAIN
}
