package com.example.recime.helper;

import android.content.Context;
import android.security.keystore.*;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Bearer credentials are encrypted with a non-exportable Android Keystore key. */
public final class SessionStore {
  private static final String ALIAS = "recime-account-session-v1";
  private final android.content.SharedPreferences prefs;
  public SessionStore(Context context) { prefs = context.getSharedPreferences("account_session", 0); }
  private javax.crypto.SecretKey key() throws Exception {
    KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
    if (store.containsAlias(ALIAS)) return (javax.crypto.SecretKey) store.getKey(ALIAS, null);
    KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
    generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
    return generator.generateKey();
  }
  public synchronized void save(String token, String user, String email, String endpoint) throws Exception {
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
    String encrypted = Base64.encodeToString(cipher.doFinal(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)), Base64.NO_WRAP);
    if (!prefs.edit().putString("token", encrypted).putString("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
        .putString("user", user).putString("email", email).putString("endpoint", endpoint).commit())
      throw new java.io.IOException("Không lưu được phiên đăng nhập");
  }
  public synchronized String token(String endpoint) {
    if (!endpoint.equals(prefs.getString("endpoint", ""))) return "";
    try {
      String encoded = prefs.getString("token", ""); if (encoded.isEmpty()) return "";
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(prefs.getString("iv", ""), Base64.DEFAULT)));
      return new String(cipher.doFinal(Base64.decode(encoded, Base64.DEFAULT)), java.nio.charset.StandardCharsets.UTF_8);
    } catch (Exception invalidated) { clear(); return ""; }
  }
  public String user() { return prefs.getString("user", ""); }
  public String email() { return prefs.getString("email", ""); }
  public boolean belongsTo(String endpoint) { return endpoint.equals(prefs.getString("endpoint", "")) && !prefs.getString("user", "").isEmpty(); }
  public void clear() { prefs.edit().clear().commit(); }
}
