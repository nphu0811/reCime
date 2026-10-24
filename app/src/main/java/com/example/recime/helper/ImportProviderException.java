package com.example.recime.helper;
public final class ImportProviderException extends java.io.IOException {
  public final ImportProviders.Status status;
  public ImportProviderException(ImportProviders.Status status, String message) { super(message); this.status=status; }
}
