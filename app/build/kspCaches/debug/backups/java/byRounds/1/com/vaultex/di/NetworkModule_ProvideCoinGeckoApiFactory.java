package com.vaultex.di;

import com.vaultex.data.remote.api.CoinGeckoApi;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
public final class NetworkModule_ProvideCoinGeckoApiFactory implements Factory<CoinGeckoApi> {
  @Override
  public CoinGeckoApi get() {
    return provideCoinGeckoApi();
  }

  public static NetworkModule_ProvideCoinGeckoApiFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static CoinGeckoApi provideCoinGeckoApi() {
    return Preconditions.checkNotNullFromProvides(NetworkModule.INSTANCE.provideCoinGeckoApi());
  }

  private static final class InstanceHolder {
    private static final NetworkModule_ProvideCoinGeckoApiFactory INSTANCE = new NetworkModule_ProvideCoinGeckoApiFactory();
  }
}
