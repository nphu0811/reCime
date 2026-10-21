package com.example.recime.importing;

/** Extension point. Only PublicWebExtractor has a real adapter. Speech and LLM: unsupported. */
public interface ExtractionAdapter {
  String extract(String input) throws Exception;
}
