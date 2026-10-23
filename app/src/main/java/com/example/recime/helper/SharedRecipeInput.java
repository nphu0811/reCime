package com.example.recime.helper;
import java.util.regex.*;

/** Preserve the caption while extracting a share URL; never discard either input. */
public final class SharedRecipeInput {
  public final String url, text;
  private SharedRecipeInput(String url, String text) { this.url=url; this.text=text; }
  public static SharedRecipeInput parse(String value) {
    String raw = value == null ? "" : value.trim();
    Matcher match = Pattern.compile("https://[^\\s<>]+", Pattern.CASE_INSENSITIVE).matcher(raw);
    if (!match.find()) return new SharedRecipeInput("",raw);
    String url = match.group().replaceFirst("[.,;!?]+$", "");
    return new SharedRecipeInput(url, raw.equals(url) ? "" : raw);
  }
}
