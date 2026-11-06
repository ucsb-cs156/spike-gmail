package edu.ucsb.cs156.spikegmail.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.support.ResourcePropertySource;

/**
 * Checks how <code>application.properties</code> resolves the token encryption key: from the
 * <code>TOKEN_ENCRYPTION_KEY</code> environment variable, or from a <code>.env</code> file. The
 * real system environment is deliberately left out so the result does not depend on the machine
 * the tests run on.
 */
public class TokenEncryptionKeyPropertyTests {

  private static final String PROPERTY = "app.token-encryption-key";

  private String resolve(Map<String, Object> environment) throws IOException {
    MutablePropertySources sources = new MutablePropertySources();
    sources.addLast(new MapPropertySource("environment", environment));
    sources.addLast(new ResourcePropertySource("classpath:application.properties"));
    return new PropertySourcesPropertyResolver(sources).getProperty(PROPERTY);
  }

  @Test
  void uses_TOKEN_ENCRYPTION_KEY() throws IOException {
    assertEquals("theKey", resolve(Map.of("TOKEN_ENCRYPTION_KEY", "theKey")));
  }

  @Test
  void uses_TOKEN_ENCRYPTION_KEY_from_dotenv() throws IOException {
    assertEquals("theKey", resolve(Map.of("env.TOKEN_ENCRYPTION_KEY", "theKey")));
  }

  @Test
  void is_empty_when_not_set() throws IOException {
    assertEquals("", resolve(Map.of()));
  }
}
