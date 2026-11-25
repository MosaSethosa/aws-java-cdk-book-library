package com.booklibrary.service;

/** Feature flags. The key must match the flag name in the AppConfig feature-flag profile. */
public enum FeatureFlag {
  CREATE_BOOK("enableCreateBook", true),
  DELETE_BOOK("enableDeleteBook", true),
  LIST_FILTER("enableListFilter", true);

  private final String key;
  private final boolean defaultValue;

  FeatureFlag(String key, boolean defaultValue) {
    this.key = key;
    this.defaultValue = defaultValue;
  }

  public String key() {
    return key;
  }

  /** Value used when AppConfig is unreachable or doesn't contain the flag. */
  public boolean defaultValue() {
    return defaultValue;
  }
}
