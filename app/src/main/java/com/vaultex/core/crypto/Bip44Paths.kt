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
    TRON("Tron", "TRX", 195, 6, "https://tronscan.org/#/transaction/");

    fun isEvm(): Boolean = this == ETHEREUM || this == BNB_CHAIN
}
