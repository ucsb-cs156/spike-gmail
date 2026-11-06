package edu.ucsb.cs156.spikegmail.repositories;

import edu.ucsb.cs156.spikegmail.entities.GmailSender;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/** Repository for the admins that have authorized sending Gmail on their behalf. */
@Repository
public interface GmailSenderRepository extends CrudRepository<GmailSender, Long> {
  /**
   * Find the sender record for a Google account email.
   *
   * @param email the Google account email
   * @return Optional of GmailSender (empty if not found)
   */
  Optional<GmailSender> findByEmail(String email);

  /**
   * All senders, in a stable order for display.
   *
   * @return all senders ordered by email
   */
  List<GmailSender> findAllByOrderByEmailAsc();
}
