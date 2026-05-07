package com.vaultex.ui.viewmodel;

import com.vaultex.data.repository.MarketRepository;
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
public final class MarketViewModel_Factory implements Factory<MarketViewModel> {
  private final Provider<MarketRepository> repositoryProvider;

  public MarketViewModel_Factory(Provider<MarketRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public MarketViewModel get() {
    return newInstance(repositoryProvider.get());
  }

  public static MarketViewModel_Factory create(Provider<MarketRepository> repositoryProvider) {
    return new MarketViewModel_Factory(repositoryProvider);
  }

  public static MarketViewModel newInstance(MarketRepository repository) {
    return new MarketViewModel(repository);
  }
}
