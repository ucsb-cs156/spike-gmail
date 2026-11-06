package edu.ucsb.cs156.spikegmail.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.Cipher;
import org.junit.jupiter.api.Test;

public class TokenSecurityServiceTests {

  private static final String VALID_KEY =
      Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
  private static final String OTHER_KEY =
      Base64.getEncoder().encodeToString("fedcba9876543210fedcba9876543210".getBytes());

  @Test
  public void encrypt_thenDecrypt_roundTripsToken() {
    TokenSecurityService service = new TokenSecurityService(VALID_KEY);

    String encrypted = service.encrypt("test-token");

    assertNotEquals("test-token", encrypted);
    assertTrue(encrypted.startsWith("enc:v1:"));
    assertEquals("test-token", service.decrypt(encrypted));
  }

  @Test
  public void decrypt_plaintextLegacyValue_returnsOriginalValue() {
    TokenSecurityService service = new TokenSecurityService("");

    assertEquals("legacy-plaintext-token", service.decrypt("legacy-plaintext-token"));
  }

  @Test
  public void encrypt_withoutConfiguredKey_throwsIllegalStateException() {
    TokenSecurityService service = new TokenSecurityService("");

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> service.encrypt("test-token"));
    assertTrue(exception.getMessage().contains("TOKEN_ENCRYPTION_KEY"));
  }

  @Test
  public void encrypt_withNullConfiguredKey_throwsIllegalStateException() {
    TokenSecurityService service = new TokenSecurityService(null);

    assertThrows(IllegalStateException.class, () -> service.encrypt("test-token"));
  }

  @Test
  public void encrypt_nullOrEmptyOrAlreadyEncrypted_returnsInputUnchanged() {
    TokenSecurityService service = new TokenSecurityService(VALID_KEY);

    assertNull(service.encrypt(null));
    assertEquals("", service.encrypt(""));
    assertEquals("enc:v1:already-encrypted", service.encrypt("enc:v1:already-encrypted"));
  }

  @Test
  public void decrypt_nullOrEmpty_returnsInputUnchanged() {
    TokenSecurityService service = new TokenSecurityService(VALID_KEY);

    assertNull(service.decrypt(null));
    assertEquals("", service.decrypt(""));
  }

  @Test
  public void decrypt_ivOnlyPayload_throwsMalformedException() {
    TokenSecurityService service = new TokenSecurityService(VALID_KEY);
    String ivOnlyPayload = Base64.getEncoder().encodeToString(new byte[12]);

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> service.decrypt("enc:v1:" + ivOnlyPayload));
    assertEquals("Encrypted token is malformed", exception.getMessage());
  }

  @Test
  public void decrypt_withDifferentKey_throwsIllegalStateException() {
    String encrypted = new TokenSecurityService(VALID_KEY).encrypt("test-token");
    TokenSecurityService other = new TokenSecurityService(OTHER_KEY);

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> other.decrypt(encrypted));
    assertEquals("Failed to decrypt token", exception.getMessage());
  }

  @Test
  public void decrypt_tamperedCiphertext_throwsIllegalStateException() {
    TokenSecurityService service = new TokenSecurityService(VALID_KEY);
    byte[] payload =
        Base64.getDecoder().decode(service.encrypt("test-token").substring("enc:v1:".length()));
    payload[payload.length - 1] ^= 1;
    String tampered = "enc:v1:" + Base64.getEncoder().encodeToString(payload);

    assertThrows(IllegalStateException.class, () -> service.decrypt(tampered));
  }

  @Test
  public void decrypt_withInvalidBase64Payload_throwsIllegalStateException() {
    TokenSecurityService service = new TokenSecurityService(VALID_KEY);

    assertThrows(IllegalStateException.class, () -> service.decrypt("enc:v1:%%%"));
  }

  @Test
  public void encrypt_samePlaintextTwice_usesDifferentRandomIv() {
    TokenSecurityService service = new TokenSecurityService(VALID_KEY);

    assertNotEquals(service.encrypt("same-token"), service.encrypt("same-token"));
  }

  @Test
  public void encrypt_withNonBase64Key_throwsIllegalStateException() {
    TokenSecurityService service = new TokenSecurityService("%%%");

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> service.encrypt("test-token"));
    assertEquals("Token encryption key must be Base64 encoded", exception.getMessage());
  }

  @Test
  public void encrypt_withInvalidDecodedKeyLength_throwsIllegalStateException() {
    String invalidLengthKey = Base64.getEncoder().encodeToString("short-key-15chr".getBytes());
    TokenSecurityService service = new TokenSecurityService(invalidLengthKey);

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> service.encrypt("test-token"));
    assertEquals(
        "Token encryption key must decode to 16, 24, or 32 bytes", exception.getMessage());
  }

  @Test
  public void encrypt_thenDecrypt_with16ByteKey_roundTripsToken() {
    String key16 = Base64.getEncoder().encodeToString("0123456789abcdef".getBytes());
    TokenSecurityService service = new TokenSecurityService(key16);

    assertEquals("test-token", service.decrypt(service.encrypt("test-token")));
  }

  @Test
  public void encrypt_thenDecrypt_with24ByteKey_roundTripsToken() {
    String key24 = Base64.getEncoder().encodeToString("0123456789abcdefghijklmn".getBytes());
    TokenSecurityService service = new TokenSecurityService(key24);

    assertEquals("test-token", service.decrypt(service.encrypt("test-token")));
  }

  @Test
  public void encrypt_whenCipherCreationFails_throwsIllegalStateException() {
    TokenSecurityService service =
        new TokenSecurityService(VALID_KEY) {
          @Override
          protected Cipher getCipher() throws GeneralSecurityException {
            throw new GeneralSecurityException("simulated cipher failure");
          }
        };

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> service.encrypt("test-token"));
    assertEquals("Failed to encrypt token", exception.getMessage());
  }

  @Test
  public void decrypt_whenCipherCreationFails_throwsIllegalStateException() {
    TokenSecurityService service =
        new TokenSecurityService(VALID_KEY) {
          @Override
          protected Cipher getCipher() throws GeneralSecurityException {
            throw new GeneralSecurityException("simulated cipher failure");
          }
        };
    String encryptedLikePayload = Base64.getEncoder().encodeToString(new byte[13]);

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class, () -> service.decrypt("enc:v1:" + encryptedLikePayload));
    assertEquals("Failed to decrypt token", exception.getMessage());
  }
}
