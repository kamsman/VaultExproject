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
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.NONE })
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
    fun provideCoinGeckoApi(client: OkHttpClient): CoinGeckoApi =
        retrofit("https://api.coingecko.com/api/v3/", client).create(CoinGeckoApi::class.java)

    @Provides @Singleton
    fun provideOneInchApi(client: OkHttpClient): OneInchApi =
        retrofit("https://api.1inch.io/", client).create(OneInchApi::class.java)

    // ─── User-configurable RPC / explorer APIs ────────────────────────

    @Provides @Singleton @Named("eth")
    fun provideEthRpcApi(
        @ApplicationContext ctx: Context, client: OkHttpClient
    ): EvmRpcApi {
        val default = "https://rpc.ankr.com/eth/"
        return retrofit(default, dynamicClient(client, rpcPrefs(ctx), "rpc_eth", default))
            .create(EvmRpcApi::class.java)
    }

    @Provides @Singleton @Named("bnb")
    fun provideBnbRpcApi(
        @ApplicationContext ctx: Context, client: OkHttpClient
    ): EvmRpcApi {
        val default = "https://bsc-dataseed.binance.org/"
        return retrofit(default, dynamicClient(client, rpcPrefs(ctx), "rpc_bnb", default))
            .create(EvmRpcApi::class.java)
    }

    @Provides @Singleton
    fun provideBitcoinApi(
        @ApplicationContext ctx: Context, client: OkHttpClient
    ): BitcoinApi {
        val default = "https://blockstream.info/api/"
        return retrofit(default, dynamicClient(client, rpcPrefs(ctx), "rpc_btc", default))
            .create(BitcoinApi::class.java)
    }

    @Provides @Singleton
    fun provideSolanaRpcApi(
        @ApplicationContext ctx: Context, client: OkHttpClient
    ): SolanaRpcApi {
        val default = "https://api.mainnet-beta.solana.com/"
        return retrofit(default, dynamicClient(client, rpcPrefs(ctx), "rpc_sol", default))
            .create(SolanaRpcApi::class.java)
    }

    @Provides @Singleton
    fun provideTronApi(
        @ApplicationContext ctx: Context, client: OkHttpClient
    ): TronApi {
        val default = "https://api.trongrid.io/"
        return retrofit(default, dynamicClient(client, rpcPrefs(ctx), "rpc_trx", default))
            .create(TronApi::class.java)
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
    fun provideMarketRepository(api: CoinGeckoApi): MarketRepository = MarketRepository(api)

    @Provides @Singleton
    fun providePriceRepository(api: CoinGeckoApi): PriceRepository = PriceRepository(api)

    // ─── Helpers ─────────────────────────────────────────────────────

    private fun rpcPrefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("vaultex_rpc_prefs", Context.MODE_PRIVATE)
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
        val requestUrl = original.url.toString()

        // Rewrite only if the user has set a custom URL
        val newUrl = if (saved != normalizedDefault && requestUrl.startsWith(normalizedDefault)) {
            requestUrl.replaceFirst(normalizedDefault, saved)
        } else {
            requestUrl
        }

        return chain.proceed(original.newBuilder().url(newUrl).build())
    }
}
