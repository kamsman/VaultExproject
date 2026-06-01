package com.vaultex.di

import com.vaultex.data.remote.api.CoinGeckoApi
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
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.NONE // BODY en debug si besoin
                }
            )
            .build()
    }

    @Provides
    @Singleton
    fun provideCoinGeckoApi(client: OkHttpClient): CoinGeckoApi {
        return Retrofit.Builder()
            .baseUrl("https://api.coingecko.com/api/v3/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CoinGeckoApi::class.java)
    }

    @Provides
    @Singleton
    fun provideMarketRepository(api: CoinGeckoApi): MarketRepository = MarketRepository(api)

    @Provides
    @Singleton
    fun providePriceRepository(api: CoinGeckoApi): PriceRepository = PriceRepository(api)
}
