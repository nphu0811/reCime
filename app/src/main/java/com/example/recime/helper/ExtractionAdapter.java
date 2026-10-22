package com.example.recime.helper;

/** Extension point. Only PublicWebExtractor has a real adapter. Speech and LLM: unsupported. */
public interface ExtractionAdapter {
  String extract(String input) throws Exception;
}
