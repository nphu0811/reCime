package com.example.recime.helper;

import static org.junit.Assert.*;
import org.junit.Test;

public class PublicWebExtractorTypeTest {
  @Test public void recognizesSupportedSchemaRecipeTypeForms() {
    assertTrue(PublicWebExtractor.isRecipeTypeName("Recipe"));
    assertTrue(PublicWebExtractor.isRecipeTypeName(" https://schema.org/Recipe "));
    assertTrue(PublicWebExtractor.isRecipeTypeName("http://schema.org/Recipe"));
    assertFalse(PublicWebExtractor.isRecipeTypeName(null));
    assertFalse(PublicWebExtractor.isRecipeTypeName("HowToStep"));
    assertFalse(PublicWebExtractor.isRecipeTypeName("https://invalid.example/Recipe"));
  }

  @Test public void socialHostMatchRequiresDomainBoundary() {
    assertTrue(PublicWebExtractor.isSocialHost("tiktok.com"));
    assertTrue(PublicWebExtractor.isSocialHost("www.TikTok.com"));
    assertTrue(PublicWebExtractor.isSocialHost("m.youtube.com"));
    assertTrue(PublicWebExtractor.isSocialHost("youtu.be"));
    assertFalse(PublicWebExtractor.isSocialHost("mytiktok.com"));
    assertFalse(PublicWebExtractor.isSocialHost("fakeyoutube.com"));
    assertFalse(PublicWebExtractor.isSocialHost("youtube.com.attacker.example"));
    assertFalse(PublicWebExtractor.isSocialHost("recipes.example"));
    assertFalse(PublicWebExtractor.isSocialHost(null));
  }
}
