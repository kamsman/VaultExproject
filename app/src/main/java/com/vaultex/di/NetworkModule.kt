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

    private fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit =
        Retrofit.Builder().baseUrl(baseUrl).client(client)
            .addConverterFactory(GsonConverterFactory.create()).build()

    @Provides @Singleton
    fun provideCoinGeckoApi(client: OkHttpClient): CoinGeckoApi =
        retrofit("https://api.coingecko.com/api/v3/", client).create(CoinGeckoApi::class.java)

    @Provides @Singleton
    fun provideOneInchApi(client: OkHttpClient): OneInchApi =
        retrofit("https://api.1inch.io/", client).create(OneInchApi::class.java)

    @Provides @Singleton
    fun provideChangeNowApi(client: OkHttpClient): ChangeNowApi =
        retrofit("https://api.changenow.io/v1/", client).create(ChangeNowApi::class.java)

    @Provides @Singleton
    @Named("eth")
    fun provideEthRpcApi(client: OkHttpClient): EvmRpcApi =
        retrofit("https://mainnet.infura.io/v3/YOUR_INFURA_KEY/", client).create(EvmRpcApi::class.java)

    @Provides @Singleton
    @Named("bnb")
    fun provideBnbRpcApi(client: OkHttpClient): EvmRpcApi =
        retrofit("https://bsc-dataseed.binance.org/", client).create(EvmRpcApi::class.java)

    @Provides @Singleton
    fun provideBitcoinApi(client: OkHttpClient): BitcoinApi =
        retrofit("https://api.blockcypher.com/v1/btc/main/", client).create(BitcoinApi::class.java)

    @Provides @Singleton
    fun provideSolanaRpcApi(client: OkHttpClient): SolanaRpcApi =
        retrofit("https://api.mainnet-beta.solana.com/", client).create(SolanaRpcApi::class.java)

    @Provides @Singleton
    fun provideTronApi(client: OkHttpClient): TronApi =
        retrofit("https://api.trongrid.io/", client).create(TronApi::class.java)

    @Provides @Singleton
    fun provideMarketRepository(api: CoinGeckoApi): MarketRepository = MarketRepository(api)

    @Provides @Singleton
    fun providePriceRepository(api: CoinGeckoApi): PriceRepository = PriceRepository(api)
}
