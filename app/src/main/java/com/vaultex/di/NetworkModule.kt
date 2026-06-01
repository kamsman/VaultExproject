package com.vaultex.di

import com.vaultex.data.remote.api.*
import com.vaultex.data.repository.MarketRepository
import com.vaultex.data.repository.PriceRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.NONE })
        .build()

    private fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    // ── CoinGecko ──────────────────────────────────────────────────────────────

    @Provides @Singleton
    fun provideCoinGeckoApi(client: OkHttpClient): CoinGeckoApi =
        retrofit("https://api.coingecko.com/api/v3/", client).create(CoinGeckoApi::class.java)

    @Provides @Singleton
    fun provideMarketRepository(api: CoinGeckoApi): MarketRepository = MarketRepository(api)

    @Provides @Singleton
    fun providePriceRepository(api: CoinGeckoApi): PriceRepository = PriceRepository(api)

    // ── EVM — Ethereum (Infura public fallback) ────────────────────────────────

    @Provides @Singleton @Named("eth")
    fun provideEthRpcApi(client: OkHttpClient): EvmRpcApi =
        retrofit("https://cloudflare-eth.com/", client).create(EvmRpcApi::class.java)

    // ── EVM — BNB Chain ────────────────────────────────────────────────────────

    @Provides @Singleton @Named("bnb")
    fun provideBnbRpcApi(client: OkHttpClient): EvmRpcApi =
        retrofit("https://bsc-dataseed.binance.org/", client).create(EvmRpcApi::class.java)

    // ── Bitcoin — Blockstream ──────────────────────────────────────────────────

    @Provides @Singleton
    fun provideBitcoinApi(client: OkHttpClient): BitcoinApi =
        retrofit("https://blockstream.info/api/", client).create(BitcoinApi::class.java)

    // ── Solana ─────────────────────────────────────────────────────────────────

    @Provides @Singleton
    fun provideSolanaRpcApi(client: OkHttpClient): SolanaRpcApi =
        retrofit("https://api.mainnet-beta.solana.com/", client).create(SolanaRpcApi::class.java)

    // ── Tron ───────────────────────────────────────────────────────────────────

    @Provides @Singleton
    fun provideTronApi(client: OkHttpClient): TronApi =
        retrofit("https://api.trongrid.io/", client).create(TronApi::class.java)

    // ── 1inch DEX Aggregator ───────────────────────────────────────────────────

    @Provides @Singleton
    fun provideOneInchApi(client: OkHttpClient): OneInchApi =
        retrofit("https://api.1inch.dev/", client).create(OneInchApi::class.java)
}
