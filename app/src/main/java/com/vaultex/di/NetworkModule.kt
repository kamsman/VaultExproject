package com.vaultex.di

import android.content.Context
import android.content.SharedPreferences
import com.vaultex.core.config.ApiKeys
import com.vaultex.data.remote.api.*
import com.vaultex.data.remote.api.EtherscanApi
import com.vaultex.data.repository.MarketRepository
import com.vaultex.data.repository.PriceRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // ─── Base OkHttp client ───────────────────────────────────────────

    @Provides @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            // Fail-fast pour permettre le basculement RPC (#2)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            // Durcissement MITM : TLS 1.2+ uniquement (C-02)
            .connectionSpecs(
                listOf(okhttp3.ConnectionSpec.RESTRICTED_TLS, okhttp3.ConnectionSpec.MODERN_TLS)
            )
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.NONE })

        // Certificate pinning (P1) — anti-MITM. Activé uniquement quand
        // ENABLE_CERT_PINNING=true ET que des empreintes réelles existent.
        if (com.vaultex.BuildConfig.ENABLE_CERT_PINNING) {
            buildCertificatePinner()?.let { builder.certificatePinner(it) }
        }
        return builder.build()
    }

    /**
     * Empreintes SHA-256 (SPKI) des hôtes sensibles. À RENSEIGNER avant
     * d'activer le pinning, sinon toutes les connexions échoueraient.
     *
     * Pour obtenir l'empreinte d'un hôte :
     *   openssl s_client -connect api.changenow.io:443 -servername api.changenow.io < /dev/null 2>/dev/null \
     *     | openssl x509 -pubkey -noout \
     *     | openssl pkey -pubin -outform der \
     *     | openssl dgst -sha256 -binary | openssl enc -base64
     * Préfixer chaque valeur par "sha256/". Mettre au moins 2 pins par hôte
     * (certificat courant + backup) pour survivre aux rotations.
     */
    private val CERT_PINS: Map<String, List<String>> = mapOf(
        // ─ Services critiques (mouvement d'argent) — 2 pins ─
        "api.changenow.io" to listOf(
            "sha256/oqpstRWo8o/smZIWpFWTUuTyu17sZ5onK7mXJiy5Zpc=",
            "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4="
        ),
        "api.flutterwave.com" to listOf(
            "sha256/11kFWr8Qs08a+tX7u9pQ7RUUWuejKKNV1Jh4x9INTcA=",
            "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4="
        ),
        "api.trongrid.io" to listOf(
            "sha256/y+ZJG53oAEWQ76+So+SztvGgByATWEG1KMwNdarn3go=",
            // Backup : Amazon Root CA 1 (TronGrid est derrière AWS)
            "sha256/++MBgDH5WGvL9Bcn5Be30cRcL0f5O+NyoXuWtQdX1aI="
        ),
        // ─ Services secondaires (données) — 1 pin ─
        "api.etherscan.io" to listOf("sha256/kjWU9H91qtu39iBXltykNck8+xWT425ShPW+wFF2WTg="),
        "api.bscscan.com" to listOf("sha256/i9oogB4vKEVz0R5PhIsBqJsyJV1l3SPEnwQl65LR5/w="),
        "api.coingecko.com" to listOf("sha256/dgrX3vEnFHd+VpOBXSDSp5oFpJTB0v6FRx0pl00ifSM=")
    )

    private fun buildCertificatePinner(): okhttp3.CertificatePinner? {
        if (CERT_PINS.isEmpty()) return null
        val b = okhttp3.CertificatePinner.Builder()
        CERT_PINS.forEach { (host, pins) -> pins.forEach { b.add(host, it) } }
        return b.build()
    }

    /**
     * Clients RPC nœud (#2) : bascule automatiquement sur un nœud public
     * secondaire si le primaire échoue (timeout / IOException). Les corps
     * JSON-RPC (EVM, Solana) et l'API REST Bitcoin (Blockstream/mempool)
     * sont compatibles entre primaire et secours.
     */
    private fun fallbackClient(
        base: OkHttpClient,
        prefs: SharedPreferences,
        prefKey: String,
        defaultBase: String,
        backupHosts: List<String>
    ): OkHttpClient = base.newBuilder()
        .addInterceptor(DynamicBaseUrlInterceptor(prefs, prefKey, defaultBase))
        .addInterceptor(RpcFallbackInterceptor(backupHosts))
        .build()

    private fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit =
        Retrofit.Builder().baseUrl(baseUrl).client(client)
            .addConverterFactory(GsonConverterFactory.create()).build()

    /** Builds an OkHttpClient that rewrites the base URL from SharedPreferences on every request. */
    private fun dynamicClient(
        base: OkHttpClient,
        prefs: SharedPreferences,
        prefKey: String,
        defaultBase: String
    ): OkHttpClient = base.newBuilder()
        .addInterceptor(DynamicBaseUrlInterceptor(prefs, prefKey, defaultBase))
        .build()

    // ─── Fixed / non-configurable APIs ───────────────────────────────

    @Provides @Singleton
    fun provideCoinGeckoApi(client: OkHttpClient): CoinGeckoApi {
        // Clé Demo CoinGecko (header x-cg-demo-api-key) si renseignée : lève le
        // rate-limit qui faisait échouer le Marché. Sinon, API publique libre.
        val cgClient = if (ApiKeys.COINGECKO.isNotBlank()) {
            client.newBuilder().addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .addHeader("x-cg-demo-api-key", ApiKeys.COINGECKO)
                        .build()
                )
            }.build()
        } else client
        return retrofit("https://api.coingecko.com/api/v3/", cgClient).create(CoinGeckoApi::class.java)
    }

    // ─── User-configurable RPC / explorer APIs ────────────────────────

    @Provides @Singleton @Named("eth")
    fun provideEthRpcApi(
        @ApplicationContext ctx: Context, client: OkHttpClient
    ): EvmRpcApi {
        // Ankr/free a fini par exiger une clé (403/429) → eth_call ETH échouait
        // (prix et détection de token KO) alors que BNB marchait. On passe sur
        // des nœuds publics ouverts et fiables, avec bascule automatique.
        val default = "https://ethereum-rpc.publicnode.com/"
        val backups = listOf(
            "https://eth.llamarpc.com/",
            "https://cloudflare-eth.com/",
            "https://rpc.ankr.com/eth/"
        )
        return retrofit(default, fallbackClient(client, rpcPrefs(ctx), "rpc_eth", default, backups))
            .create(EvmRpcApi::class.java)
    }

    @Provides @Singleton @Named("bnb")
    fun provideBnbRpcApi(
        @ApplicationContext ctx: Context, client: OkHttpClient
    ): EvmRpcApi {
        val default = "https://bsc-dataseed.binance.org/"
        val backups = listOf("https://bsc.publicnode.com/", "https://bsc-dataseed1.defibit.io/")
        return retrofit(default, fallbackClient(client, rpcPrefs(ctx), "rpc_bnb", default, backups))
            .create(EvmRpcApi::class.java)
    }

    @Provides @Singleton
    fun provideBitcoinApi(
        @ApplicationContext ctx: Context, client: OkHttpClient
    ): BitcoinApi {
        val default = "https://blockstream.info/api/"
        val backups = listOf("https://mempool.space/api/") // API REST compatible Blockstream
        return retrofit(default, fallbackClient(client, rpcPrefs(ctx), "rpc_btc", default, backups))
            .create(BitcoinApi::class.java)
    }

    @Provides @Singleton
    fun provideSolanaRpcApi(
        @ApplicationContext ctx: Context, client: OkHttpClient
    ): SolanaRpcApi {
        val default = "https://api.mainnet-beta.solana.com/"
        val backups = listOf("https://solana-rpc.publicnode.com/", "https://api.mainnet-beta.solana.com/")
        return retrofit(default, fallbackClient(client, rpcPrefs(ctx), "rpc_sol", default, backups))
            .create(SolanaRpcApi::class.java)
    }

    @Provides @Singleton
    fun provideTronApi(
        @ApplicationContext ctx: Context, client: OkHttpClient
    ): TronApi {
        val default = "https://api.trongrid.io/"
        var trxClient = dynamicClient(client, rpcPrefs(ctx), "rpc_trx", default)
        // Clé TronGrid optionnelle (header TRON-PRO-API-KEY) — anti rate-limit
        if (ApiKeys.TRONGRID.isNotBlank()) {
            trxClient = trxClient.newBuilder().addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .addHeader("TRON-PRO-API-KEY", ApiKeys.TRONGRID)
                        .build()
                )
            }.build()
        }
        return retrofit(default, trxClient).create(TronApi::class.java)
    }

    @Provides @Singleton @Named("etherscan")
    fun provideEtherscanApi(
        @ApplicationContext ctx: Context, client: OkHttpClient
    ): EtherscanApi {
        val default = "https://api.etherscan.io/"
        return retrofit(default, dynamicClient(client, rpcPrefs(ctx), "rpc_etherscan", default))
            .create(EtherscanApi::class.java)
    }

    @Provides @Singleton @Named("bscscan")
    fun provideBscScanApi(
        @ApplicationContext ctx: Context, client: OkHttpClient
    ): EtherscanApi {
        val default = "https://api.bscscan.com/"
        return retrofit(default, dynamicClient(client, rpcPrefs(ctx), "rpc_bscscan", default))
            .create(EtherscanApi::class.java)
    }

    @Provides @Singleton
    fun provideChangeNowApi(
        @ApplicationContext ctx: Context, client: OkHttpClient
    ): ChangeNowApi {
        val default = "https://api.changenow.io/v1/"
        return retrofit(default, dynamicClient(client, rpcPrefs(ctx), "rpc_changenow", default))
            .create(ChangeNowApi::class.java)
    }

    @Provides @Singleton
    fun provideFlutterwaveApi(client: OkHttpClient): FlutterwaveApi {
        val authedClient = client.newBuilder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer ${ApiKeys.FLUTTERWAVE}")
                        .addHeader("Content-Type", "application/json")
                        .build()
                )
            }.build()
        return retrofit("https://api.flutterwave.com/v3/", authedClient)
            .create(FlutterwaveApi::class.java)
    }

    @Provides @Singleton
    fun provideMarketRepository(
        api: CoinGeckoApi,
        @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context
    ): MarketRepository = MarketRepository(api, context)

    @Provides @Singleton
    fun providePriceRepository(api: CoinGeckoApi): PriceRepository = PriceRepository(api)

    // ─── Helpers ─────────────────────────────────────────────────────

    private fun rpcPrefs(ctx: Context): SharedPreferences =
        com.vaultex.core.security.RpcPrefs.get(ctx)
}

/**
 * On every request, replaces the Retrofit base URL with the value saved in SharedPreferences.
 * Falls back to [defaultBase] if no override is stored.
 *
 * Strategy: string-replace the default base URL prefix in the full request URL.
 * This preserves API paths (e.g., /address/{addr}) while swapping the host/scheme/port.
 */
private class DynamicBaseUrlInterceptor(
    private val prefs: SharedPreferences,
    private val prefKey: String,
    defaultBase: String
) : Interceptor {

    private val normalizedDefault = defaultBase.trimEnd('/') + "/"

    override fun intercept(chain: Interceptor.Chain): Response {
        val saved = (prefs.getString(prefKey, normalizedDefault) ?: normalizedDefault)
            .let { it.trimEnd('/') + "/" }

        val original = chain.request()
        if (saved == normalizedDefault) return chain.proceed(original)

        /*
        Réécriture par HttpUrl, et non par remplacement de texte.

        L'ancienne version faisait `requestUrl.replaceFirst(base, saved)` sur
        l'URL entière. Un remplacement de chaîne ne connaît pas la structure
        d'une URL : selon la valeur saisie par l'utilisateur, le chemin pouvait
        se retrouver dupliqué ou tronqué, et la requête partait vers une adresse
        silencieusement fausse. La classe voisine RpcFallbackInterceptor
        utilisait déjà la bonne méthode — les deux sont maintenant cohérentes.

        On ne remplace QUE l'origine (schéma, hôte, port) : chemin, paramètres
        et fragment de la requête d'origine sont préservés tels quels.
         */
        val override = saved.toHttpUrlOrNull()
            ?: return chain.proceed(original)          // valeur illisible : on ignore
        if (override.scheme != "https") return chain.proceed(original)

        val newUrl = original.url.newBuilder()
            .scheme(override.scheme)
            .host(override.host)
            .port(override.port)
            .build()
        return chain.proceed(original.newBuilder().url(newUrl).build())
    }
}

/**
 * Bascule RPC (#2). En cas d'échec réseau (IOException/timeout) ou de
 * réponse 5xx du nœud courant, rejoue la requête en remplaçant l'hôte
 * par un nœud de secours, jusqu'à épuisement de la liste.
 */
private class RpcFallbackInterceptor(
    private val backupBaseUrls: List<String>
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        // Codes qui doivent déclencher une bascule de nœud : 5xx (panne serveur)
        // mais AUSSI 403 (clé exigée), 408/425/429 (rate-limit). Les nœuds publics
        // gratuits renvoient souvent 403/429 sans être en panne — sinon eth_call
        // restait bloqué sur le primaire (cas ETH/Ankr).
        fun shouldFailover(code: Int): Boolean =
            code >= 500 || code == 403 || code == 408 || code == 425 || code == 429

        // Tentative sur le nœud courant
        val primary: Response? = try {
            val r = chain.proceed(original)
            if (!shouldFailover(r.code)) return r
            r.close()
            null
        } catch (_: Exception) {
            null
        }
        if (primary != null) return primary

        // Bascule successive sur les nœuds de secours
        for (backup in backupBaseUrls) {
            try {
                val backupHost = backup.toHttpUrlOrNull() ?: continue
                val newUrl = original.url.newBuilder()
                    .scheme(backupHost.scheme)
                    .host(backupHost.host)
                    .port(backupHost.port)
                    .build()
                val resp = chain.proceed(original.newBuilder().url(newUrl).build())
                if (!shouldFailover(resp.code)) return resp
                resp.close()
            } catch (_: Exception) {
                // essaie le suivant
            }
        }

        // Tout a échoué : on relance le primaire pour propager l'erreur réelle
        return chain.proceed(original)
    }
}
