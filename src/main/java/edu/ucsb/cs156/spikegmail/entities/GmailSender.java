package edu.ucsb.cs156.spikegmail.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * An admin who has authorized this application to send Gmail on their behalf.
 *
 * <p>This row holds no secrets. The access and refresh tokens live in the <code>
 * oauth2_authorized_client</code> table (managed by Spring Security's <code>
 * JdbcOAuth2AuthorizedClientService</code>), keyed by registration id <code>gmail</code> and {@link
 * #principalName}.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@Entity(name = "gmail_senders")
public class GmailSender {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private long id;

  /** The Google account email that mail will be sent from. */
  private String email;

  /** The Spring Security principal name (the Google "sub") the tokens are stored under. */
  private String principalName;

  private LocalDateTime authorizedAt;
}
