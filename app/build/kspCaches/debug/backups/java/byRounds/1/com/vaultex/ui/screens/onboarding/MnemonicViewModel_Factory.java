package com.vaultex.ui.screens.onboarding;

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
public final class MnemonicViewModel_Factory implements Factory<MnemonicViewModel> {
  @Override
  public MnemonicViewModel get() {
    return newInstance();
  }

  public static MnemonicViewModel_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static MnemonicViewModel newInstance() {
    return new MnemonicViewModel();
  }

  private static final class InstanceHolder {
    private static final MnemonicViewModel_Factory INSTANCE = new MnemonicViewModel_Factory();
  }
}
