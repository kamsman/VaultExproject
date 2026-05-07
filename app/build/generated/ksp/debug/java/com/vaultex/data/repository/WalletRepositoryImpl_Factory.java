package com.vaultex.data.repository;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
public final class WalletRepositoryImpl_Factory implements Factory<WalletRepositoryImpl> {
  @Override
  public WalletRepositoryImpl get() {
    return newInstance();
  }

  public static WalletRepositoryImpl_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static WalletRepositoryImpl newInstance() {
    return new WalletRepositoryImpl();
  }

  private static final class InstanceHolder {
    private static final WalletRepositoryImpl_Factory INSTANCE = new WalletRepositoryImpl_Factory();
  }
}
