package com.vaultex.data.remote.api

import com.vaultex.data.remote.dto.*
import okhttp3.RequestBody
import retrofit2.http.*

/**
 * Ethereum / BNB JSON-RPC interface (compatible Infura, Alchemy, BSC public RPC).
 */
interface EvmRpcApi {
    @POST("./")
    @Headers("Content-Type: application/json")
    suspend fun rpcCall(@Body request: JsonRpcRequest): JsonRpcResponse
}

/**
 * Bitcoin via Blockstream API (REST).
 */
interface BitcoinApi {
    @GET("address/{address}")
    suspend fun getAddressInfo(@Path("address") address: String): BlockstreamAddressDto

    @GET("address/{address}/utxo")
    suspend fun getUtxos(@Path("address") address: String): List<BlockstreamUtxoDto>

    @GET("address/{address}/txs")
    suspend fun getTransactions(@Path("address") address: String): List<BlockstreamTxDto>

    @GET("tx/{txid}")
    suspend fun getTransaction(@Path("txid") txid: String): BlockstreamTxDto

    // Blockstream renvoie le TXID en TEXTE BRUT (pas du JSON). On retourne donc
    // le corps brut (ResponseBody) pour éviter que Gson tente de le parser et
    // échoue (« Use JsonReader.setLenient... malformed JSON »).
    @POST("tx")
    @Headers("Content-Type: text/plain")
    suspend fun broadcastTx(@Body rawHex: RequestBody): okhttp3.ResponseBody

    @GET("fee-estimates")
    suspend fun getFeeEstimates(): Map<String, Double>
}

/**
 * Solana JSON-RPC.
 */
interface SolanaRpcApi {
    @POST("./")
    @Headers("Content-Type: application/json")
    suspend fun rpcCall(@Body request: JsonRpcRequest): JsonRpcResponse
}

/**
 * Tron via TronGrid REST API.
 */
interface TronApi {
    @GET("v1/accounts/{address}")
    suspend fun getAccount(@Path("address") address: String): TronAccountDto

    @GET("v1/accounts/{address}/transactions")
    suspend fun getTransactions(
        @Path("address") address: String,
        @Query("limit") limit: Int = 50
    ): TronTxListDto

    @GET("v1/accounts/{address}/transactions/trc20")
    suspend fun getTrc20Transactions(
        @Path("address") address: String,
        @Query("limit") limit: Int = 50
    ): TronTrc20ListDto

    // Retour brut (JsonObject) : on doit rediffuser la transaction COMPLÈTE
    // (txID + raw_data + raw_data_hex + signature), pas seulement raw_data_hex.
    @POST("wallet/createtransaction")
    suspend fun createTransaction(@Body body: TronCreateTxBody): com.google.gson.JsonObject

    @POST("wallet/triggersmartcontract")
    suspend fun triggerSmartContract(@Body body: TronTriggerSmartContractBody): com.google.gson.JsonObject

    @POST("wallet/broadcasttransaction")
    suspend fun broadcast(@Body tx: com.google.gson.JsonObject): TronBroadcastResultDto

    // Suivi de confirmation : infos de la tx (blockNumber + receipt) et bloc courant.
    @POST("wallet/gettransactioninfobyid")
    suspend fun getTransactionInfoById(@Body body: TronTxInfoBody): com.google.gson.JsonObject

    @POST("wallet/getnowblock")
    suspend fun getNowBlock(): com.google.gson.JsonObject
}


/**
 * CoinGecko Pro — prix temps réel et historique.
 */
interface CoinGeckoApi {
    @GET("simple/price")
    suspend fun getPrices(
        @Query("ids") ids: String,         // ex: "bitcoin,ethereum,solana"
        @Query("vs_currencies") vsCurrencies: String = "usd",
        @Query("include_24hr_change") include24hChange: Boolean = true,
        @Query("include_market_cap") includeMarketCap: Boolean = true
    ): Map<String, CoinGeckoPriceDto>

    // Prix d'un token ERC-20/BEP-20 par adresse de contrat.
    // platform : "ethereum" ou "binance-smart-chain".
    // Clé du Map retourné = adresse de contrat en minuscules.
    @GET("simple/token_price/{platform}")
    suspend fun getTokenPrice(
        @Path("platform") platform: String,
        @Query("contract_addresses") contractAddresses: String,
        @Query("vs_currencies") vsCurrencies: String = "usd,eur,xof",
        @Query("include_24hr_change") include24hChange: Boolean = true,
        // Demandee pour la fiche d'un jeton importe par contrat : sans ce
        // parametre, CoinGecko ne renvoie pas la capitalisation et le bloc
        // reste vide alors que la donnee existe.
        @Query("include_market_cap") includeMarketCap: Boolean = true
    ): Map<String, CoinGeckoPriceDto>

    /*
    `per_page` vaut 250, le maximum accepté par CoinGecko — et non 100.

    C'est gratuit au sens propre : un appel de 250 lignes coûte exactement le
    même appel qu'un appel de 100. La liste passait donc à côté de 150
    monnaies sans rien économiser du tout.
    */
    @GET("coins/markets")
    suspend fun getMarkets(
        @Query("vs_currency") vsCurrency: String = "usd",
        @Query("order") order: String = "market_cap_desc",
        @Query("per_page") perPage: Int = 250,
        @Query("page") page: Int = 1,
        @Query("sparkline") sparkline: Boolean = true,
        @Query("price_change_percentage") priceChangeRanges: String = "24h",
        @Query("ids") ids: String? = null   // filtre optionnel : 1 ou plusieurs coins
    ): List<CoinGeckoMarketDto>

    /*
    RECHERCHE DANS LE CATALOGUE COMPLET (~19 000 monnaies).

    La barre de recherche du Marché ne filtrait que les lignes déjà
    téléchargées : au-delà du rang 100, elle ne trouvait rien, ce qui se lit
    comme une panne et non comme une limite.

    Cet appel ne renvoie AUCUN prix — uniquement identifiant, nom, symbole,
    logo et rang. C'est ce qui le rend bon marché : le relais le garde 24 h,
    puisqu'un catalogue ne périme pas. Les prix des résultats sont demandés
    ensuite par getMarkets(ids = ...).
    */
    @GET("search")
    suspend fun search(
        @Query("query") query: String
    ): CoinGeckoSearchDto

    /*
    JETON DÉSIGNÉ PAR SON ADRESSE DE CONTRAT — fiche complète et courbe.

    L'application se contentait de `simple/token_price`, qui ne rend qu'un
    prix. Ces deux appels donnent à un jeton importé exactement ce qu'a une
    monnaie du registre : nom réel, logo, capitalisation, variation, et
    l'historique sur sept jours.

    `platform` vaut "ethereum" ou "binance-smart-chain". Se tromper de
    plateforme ne renvoie pas une erreur parlante mais un 404 : le contrat
    existe, simplement pas sur cette chaîne-là.
    */
    @GET("coins/{platform}/contract/{contract}")
    suspend fun getContractInfo(
        @Path("platform") platform: String,
        @Path("contract") contract: String
    ): com.vaultex.data.remote.dto.CoinGeckoContractDto

    @GET("coins/{platform}/contract/{contract}/market_chart")
    suspend fun getContractChart(
        @Path("platform") platform: String,
        @Path("contract") contract: String,
        @Query("vs_currency") vsCurrency: String = "usd",
        @Query("days") days: Int = 7
    ): com.vaultex.data.remote.dto.CoinGeckoChartDto

    @GET("coins/{id}/market_chart")
    suspend fun getMarketChart(
        @Path("id") coinId: String,
        @Query("vs_currency") vsCurrency: String = "usd",
        @Query("days") days: Int
    ): CoinGeckoChartDto

    // Détail léger d'une pièce : sert uniquement à lire ses adresses de
    // contrat par réseau (platforms). Toutes les sections lourdes désactivées.
    @GET("coins/{id}")
    suspend fun getCoinPlatforms(
        @Path("id") coinId: String,
        @Query("localization") localization: Boolean = false,
        @Query("tickers") tickers: Boolean = false,
        @Query("market_data") marketData: Boolean = false,
        @Query("community_data") communityData: Boolean = false,
        @Query("developer_data") developerData: Boolean = false,
        @Query("sparkline") sparkline: Boolean = false
    ): com.vaultex.data.remote.dto.CoinGeckoDetailDto

    // Cap. de marché totale + dominance BTC (bandeau de l'écran Marché).
    @GET("global")
    suspend fun getGlobal(): com.vaultex.data.remote.dto.CoinGeckoGlobalDto
}

/**
 * ChangeNOW — swaps cross-chain avec marge 1.5% côté VaultEx.
 * Base URL : https://api.changenow.io/v1/
 */
interface ChangeNowApi {
    @GET("exchange-amount/{amount}/{fromTo}")
    suspend fun getEstimatedAmount(
        @Path("amount") amount: String,
        @Path("fromTo") fromTo: String,  // ex: "btc_eth"
        @Query("api_key") apiKey: String
    ): ChangeNowEstimateDto

    @GET("min-amount/{fromTo}")
    suspend fun getMinAmount(
        @Path("fromTo") fromTo: String,
        @Query("api_key") apiKey: String
    ): ChangeNowMinAmountDto

    @POST("transactions/{apiKey}")
    suspend fun createTransaction(
        @Path("apiKey") apiKey: String,
        @Body body: ChangeNowTransactionBody
    ): ChangeNowTransactionDto

    @GET("transactions/{id}/{apiKey}")
    suspend fun getTransactionStatus(
        @Path("id") transactionId: String,
        @Path("apiKey") apiKey: String
    ): ChangeNowStatusDto
}

/**
 * Flutterwave — Mobile Money UEMOA (Orange Money, Wave, Moov, Free).
 * Base URL : https://api.flutterwave.com/v3/
 * Requires Authorization: Bearer <secret_key> header.
 */
interface FlutterwaveApi {
    @POST("charges?type=mobile_money_franco")
    suspend fun charge(@Body body: FlutterwaveChargeBody): FlutterwaveChargeDto

    @GET("transactions/{id}/verify")
    suspend fun verify(@Path("id") transactionId: Long): FlutterwaveVerifyDto
}

/**
 * Etherscan / BscScan — historique de transactions EVM.
 * Etherscan API **V2**.
 *
 * La V1 (api.etherscan.io/api, api.bscscan.com/api) est DESACTIVEE : elle
 * repond desormais « You are using a deprecated V1 endpoint ». C'est ce refus,
 * invisible faute de diagnostic, qui a rendu l'historique ETH et BNB muet.
 *
 * La V2 unifie toutes les chaines derriere UN SEUL hote,
 * https://api.etherscan.io/v2/api, distinguees par le parametre `chainid`
 * (1 = Ethereum, 56 = BNB Chain). Une meme cle d'API vaut pour toutes.
 */
interface EtherscanApi {
    @GET("api")
    suspend fun getTransactions(
        /** 1 = Ethereum, 56 = BNB Chain. Obligatoire en V2. */
        @Query("chainid") chainId: Int,
        @Query("module") module: String = "account",
        @Query("action") action: String = "txlist",
        @Query("address") address: String,
        @Query("startblock") startBlock: Long = 0L,
        @Query("endblock") endBlock: Long = 99_999_999L,
        @Query("page") page: Int = 1,
        @Query("offset") offset: Int = 50,
        @Query("sort") sort: String = "desc",
        @Query("apikey") apiKey: String = ""
    ): EtherscanResponse

    // Transferts de TOKENS ERC-20/BEP-20 de l'adresse (action=tokentx) —
    // absents de txlist, qui ne liste que les transactions natives.
    @GET("api")
    suspend fun getTokenTransactions(
        @Query("chainid") chainId: Int,
        @Query("module") module: String = "account",
        @Query("action") action: String = "tokentx",
        @Query("address") address: String,
        @Query("startblock") startBlock: Long = 0L,
        @Query("endblock") endBlock: Long = 99_999_999L,
        @Query("page") page: Int = 1,
        @Query("offset") offset: Int = 50,
        @Query("sort") sort: String = "desc",
        @Query("apikey") apiKey: String = ""
    ): EtherscanResponse
}


/**
 * Binance public — SOURCE DE PRIX DE SECOURS.
 *
 * Aucune clé, aucun compte, aucun quota mensuel : ce point d'entrée est
 * ouvert et supporte des milliers d'appels par minute et par IP. Il sert de
 * filet quand CoinGecko refuse de répondre — voir PriceFallbackSource pour
 * la raison précise de sa présence ici.
 */
interface BinanceApi {
    /**
     * Cours et variation 24 h de plusieurs paires en UN appel.
     *
     * [symbolsJson] doit être un tableau JSON, par ex. `["BTCUSDT","ETHUSDT"]`.
     * C'est le format qu'impose Binance sur ce paramètre ; Retrofit l'encode,
     * l'API le décode.
     *
     * ATTENTION : une seule paire inconnue fait échouer TOUT l'appel avec un
     * code 400. Le regroupement des symboles est donc fait avec précaution —
     * voir PriceFallbackSource.
     *
     * Binance renvoie les nombres sous forme de CHAÎNES (`"3421.15000000"`).
     * C'est volontaire de leur part : cela évite les pertes de précision des
     * flottants sur les paires à très petit prix, SHIB en tête, dont le cours
     * tient dans les huitièmes décimales. On les convertit à la lecture.
     */
    @GET("api/v3/ticker/24hr")
    suspend fun getTickers(
        @Query("symbols") symbolsJson: String
    ): List<com.vaultex.data.remote.dto.BinanceTickerDto>
}

/**
 * GeckoTerminal — prix d'un jeton par son ADRESSE DE CONTRAT, sans clé.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * POURQUOI DEPUIS LE TÉLÉPHONE, ET NON DEPUIS LE RELAIS
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Le relais a d'abord tenté cet appel. Mesuré : « 429 Rate Limited » —
 * comme Binance et OKX avant lui. Ces services limitent le débit par
 * adresse, et l'adresse d'un serveur Cloudflare est partagée par des
 * milliers de sites : le quota est consommé par d'autres.
 *
 * Depuis un téléphone, l'adresse est celle de l'opérateur mobile de
 * l'utilisateur. Le même appel passe. C'est déjà ce qui fait fonctionner
 * la source Binance de PriceFallbackSource.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * POURQUOI C'EST SÛR POUR N'IMPORTE QUEL JETON IMPORTÉ
 * ═══════════════════════════════════════════════════════════════════════
 *
 * L'application refuse de coter un jeton d'après son SYMBOLE : n'importe
 * qui peut déployer un contrat et l'appeler « SHIB ». Ce refus reste.
 *
 * Ici, la clé de recherche est l'ADRESSE DE CONTRAT — c'est-à-dire
 * l'identité même du jeton, celle que l'utilisateur a collée et qui
 * détermine son solde. Aucune confusion possible : le prix rendu est
 * celui de ce contrat-là, pas d'un homonyme.
 *
 * C'est ce qui autorise ce repli sur TOUS les jetons importés, alors que
 * le repli par symbole restait limité au registre vérifié.
 */
interface GeckoTerminalApi {
    /**
     * [reseau] vaut "eth" ou "bsc". [contrats] accepte plusieurs adresses
     * séparées par des virgules — un seul appel pour tout le portefeuille.
     */
    @GET("api/v2/simple/networks/{reseau}/token_price/{contrats}")
    suspend fun prixJetons(
        @Path("reseau") reseau: String,
        @Path("contrats") contrats: String
    ): com.vaultex.data.remote.dto.GeckoTerminalDto
}
