package com.vaultex.data.repository;

import com.vaultex.data.remote.api.CoinGeckoApi;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
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
public final class MarketRepository_Factory implements Factory<MarketRepository> {
  private final Provider<CoinGeckoApi> apiProvider;

  public MarketRepository_Factory(Provider<CoinGeckoApi> apiProvider) {
    this.apiProvider = apiProvider;
  }

  @Override
  public MarketRepository get() {
    return newInstance(apiProvider.get());
  }

  public static MarketRepository_Factory create(Provider<CoinGeckoApi> apiProvider) {
    return new MarketRepository_Factory(apiProvider);
  }

  public static MarketRepository newInstance(CoinGeckoApi api) {
    return new MarketRepository(api);
  }
}
