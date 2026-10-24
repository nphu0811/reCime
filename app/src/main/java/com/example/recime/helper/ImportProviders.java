package com.example.recime.helper;

import java.net.URI;
import java.util.Locale;

/** Provider capabilities are explicit; a URL is never evidence of video understanding. */
public final class ImportProviders {
  public enum Status { SUPPORTED, AUTHENTICATION_REQUIRED, PROVIDER_UNSUPPORTED }
  public static final class Provider {
    public final String name;
    public final Status status;
    public final boolean metadataOnly;
    Provider(String name, Status status, boolean metadataOnly) {
      this.name = name; this.status = status; this.metadataOnly = metadataOnly;
    }
  }
  public static Provider forUrl(String input) {
    try {
      URI uri = new URI(input);
      if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
          || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443))
        return new Provider("URL", Status.PROVIDER_UNSUPPORTED, false);
      String host = uri.getHost().toLowerCase(Locale.ROOT);
      if (matches(host, "tiktok.com")) return new Provider("TikTok", Status.SUPPORTED, true);
      for (String domain : new String[] {"instagram.com", "facebook.com", "fb.watch", "youtube.com", "youtu.be", "pinterest.com", "pin.it"})
        if (matches(host, domain)) return new Provider(domain, Status.PROVIDER_UNSUPPORTED, true);
      return new Provider("Recipe JSON-LD", Status.SUPPORTED, false);
    } catch (Exception ignored) { return new Provider("URL", Status.PROVIDER_UNSUPPORTED, false); }
  }
  private static boolean matches(String host, String domain) { return host.equals(domain) || host.endsWith("." + domain); }
  private ImportProviders() {}
}
