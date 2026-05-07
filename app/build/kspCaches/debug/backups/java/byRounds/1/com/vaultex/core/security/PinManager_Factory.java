package com.vaultex.core.security;

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
public final class PinManager_Factory implements Factory<PinManager> {
  private final Provider<SecureStorage> secureStorageProvider;

  public PinManager_Factory(Provider<SecureStorage> secureStorageProvider) {
    this.secureStorageProvider = secureStorageProvider;
  }

  @Override
  public PinManager get() {
    return newInstance(secureStorageProvider.get());
  }

  public static PinManager_Factory create(Provider<SecureStorage> secureStorageProvider) {
    return new PinManager_Factory(secureStorageProvider);
  }

  public static PinManager newInstance(SecureStorage secureStorage) {
    return new PinManager(secureStorage);
  }
}
