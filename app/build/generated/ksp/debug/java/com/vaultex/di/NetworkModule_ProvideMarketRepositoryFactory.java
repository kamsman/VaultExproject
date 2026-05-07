package com.vaultex.di;

import com.vaultex.data.remote.api.CoinGeckoApi;
import com.vaultex.data.repository.MarketRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast"
})
public final class NetworkModule_ProvideMarketRepositoryFactory implements Factory<MarketRepository> {
  private final Provider<CoinGeckoApi> apiProvider;

  public NetworkModule_ProvideMarketRepositoryFactory(Provider<CoinGeckoApi> apiProvider) {
    this.apiProvider = apiProvider;
  }

  @Override
  public MarketRepository get() {
    return provideMarketRepository(apiProvider.get());
  }

  public static NetworkModule_ProvideMarketRepositoryFactory create(
      Provider<CoinGeckoApi> apiProvider) {
    return new NetworkModule_ProvideMarketRepositoryFactory(apiProvider);
  }

  public static MarketRepository provideMarketRepository(CoinGeckoApi api) {
    return Preconditions.checkNotNullFromProvides(NetworkModule.INSTANCE.provideMarketRepository(api));
  }
}
