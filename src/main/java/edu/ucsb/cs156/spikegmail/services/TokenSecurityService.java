package edu.ucsb.cs156.spikegmail.services;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Encrypts and decrypts OAuth tokens so they are never stored in the database in plaintext.
 *
 * <p>This follows the pattern of <code>CanvasApiTokenSecurityService</code> in <a
 * href="https://github.com/ucsb-cs156/proj-frontiers">proj-frontiers</a>: AES-GCM with a random
 * 96-bit IV per value and a 128-bit authentication tag, stored as <code>enc:v1:&lt;base64(iv +
 * ciphertext)&gt;</code>, with the key supplied in the <code>TOKEN_ENCRYPTION_KEY</code>
 * environment variable. It fails closed: if the key is missing or malformed, nothing is stored.
 */
@Service
public class TokenSecurityService {

  private static final String ENCRYPTION_PREFIX = "enc:v1:";
  private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
  private static final int GCM_TAG_LENGTH_BITS = 128;
  private static final int GCM_IV_LENGTH_BYTES = 12;

  private final String encodedEncryptionKey;
  private final SecureRandom secureRandom = new SecureRandom();

  public TokenSecurityService(@Value("${app.token-encryption-key:}") String encodedEncryptionKey) {
    this.encodedEncryptionKey = encodedEncryptionKey;
  }

  /**
   * Encrypt a token. Null, empty, and already-encrypted values are returned unchanged.
   *
   * @param token the plaintext token
   * @return the encrypted token, as <code>enc:v1:...</code>
   * @throws IllegalStateException if the key is not configured correctly
   */
  public String encrypt(String token) {
    if (token == null || token.isEmpty()) {
      return token;
    }
    if (token.startsWith(ENCRYPTION_PREFIX)) {
      return token;
    }

    try {
      Cipher cipher = getCipher();
      byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
      secureRandom.nextBytes(iv);
      cipher.init(
          Cipher.ENCRYPT_MODE, getSecretKey(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

      byte[] ciphertext = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
      byte[] payload = new byte[iv.length + ciphertext.length];
      System.arraycopy(iv, 0, payload, 0, iv.length);
      System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);
      return ENCRYPTION_PREFIX + Base64.getEncoder().encodeToString(payload);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to encrypt token", e);
    }
  }

  /**
   * Decrypt a stored token. Null, empty, and values not starting with <code>enc:v1:</code>
   * (plaintext written before encryption was introduced) are returned unchanged.
   *
   * @param storedToken the stored value
   * @return the plaintext token
   * @throws IllegalStateException if the value is malformed, was tampered with, or was encrypted
   *     with a different key
   */
  public String decrypt(String storedToken) {
    if (storedToken == null || storedToken.isEmpty()) {
      return storedToken;
    }
    if (!storedToken.startsWith(ENCRYPTION_PREFIX)) {
      return storedToken;
    }

    try {
      byte[] payload = Base64.getDecoder().decode(storedToken.substring(ENCRYPTION_PREFIX.length()));
      if (payload.length <= GCM_IV_LENGTH_BYTES) {
        throw new IllegalStateException("Encrypted token is malformed");
      }
      byte[] iv = Arrays.copyOfRange(payload, 0, GCM_IV_LENGTH_BYTES);
      byte[] ciphertext = Arrays.copyOfRange(payload, GCM_IV_LENGTH_BYTES, payload.length);

      Cipher cipher = getCipher();
      cipher.init(
          Cipher.DECRYPT_MODE, getSecretKey(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
      byte[] plaintext = cipher.doFinal(ciphertext);
      return new String(plaintext, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new IllegalStateException("Failed to decrypt token", e);
    }
  }

  protected Cipher getCipher() throws GeneralSecurityException {
    return Cipher.getInstance(CIPHER_ALGORITHM);
  }

  private SecretKeySpec getSecretKey() {
    if (encodedEncryptionKey == null || encodedEncryptionKey.isBlank()) {
      throw new IllegalStateException(
          "Token encryption key is not configured; set TOKEN_ENCRYPTION_KEY (use 'openssl rand -base64 32' to generate one).");
    }
    byte[] decodedKey;
    try {
      decodedKey = Base64.getDecoder().decode(encodedEncryptionKey);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("Token encryption key must be Base64 encoded", e);
    }
    if (decodedKey.length != 16 && decodedKey.length != 24 && decodedKey.length != 32) {
      throw new IllegalStateException("Token encryption key must decode to 16, 24, or 32 bytes");
    }
    return new SecretKeySpec(decodedKey, "AES");
  }
}
