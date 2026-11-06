package edu.ucsb.cs156.spikegmail.controllers;

import edu.ucsb.cs156.spikegmail.entities.GmailSender;
import edu.ucsb.cs156.spikegmail.entities.User;
import edu.ucsb.cs156.spikegmail.repositories.GmailSenderRepository;
import edu.ucsb.cs156.spikegmail.services.CurrentUserService;
import edu.ucsb.cs156.spikegmail.services.GmailService;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Sends a test email, from an admin that has authorized Gmail, to the user who is logged in.
 *
 * <p>The recipient is deliberately not a parameter: it is always the logged in user's own address,
 * so that this endpoint cannot be used as a mail relay to send to arbitrary people.
 */
@Slf4j
@Controller
public class GmailSendController {

  static final String SUBJECT = "Test email from spike-gmail";

  private final CurrentUserService currentUserService;
  private final GmailSenderRepository gmailSenderRepository;
  private final GmailService gmailService;

  public GmailSendController(
      CurrentUserService currentUserService,
      GmailSenderRepository gmailSenderRepository,
      GmailService gmailService) {
    this.currentUserService = currentUserService;
    this.gmailSenderRepository = gmailSenderRepository;
    this.gmailService = gmailService;
  }

  @PostMapping("/gmail/send/{id}")
  public String send(@PathVariable long id, RedirectAttributes redirectAttributes) {
    User user = currentUserService.getUser();
    Optional<GmailSender> sender = gmailSenderRepository.findById(id);

    if (sender.isEmpty()) {
      redirectAttributes.addFlashAttribute("error", "No such sender.");
      return "redirect:/";
    }

    String body =
        "Hello "
            + user.getEmail()
            + ",\n\nThis message was sent by spike-gmail using the Gmail API, on behalf of "
            + sender.get().getEmail()
            + ".\n";
    try {
      gmailService.sendEmail(sender.get(), user.getEmail(), SUBJECT, body);
      redirectAttributes.addFlashAttribute(
          "message", "Sent an email to " + user.getEmail() + " from " + sender.get().getEmail());
    } catch (Exception e) {
      log.error("failed to send email from {}", sender.get().getEmail(), e);
      redirectAttributes.addFlashAttribute(
          "error",
          "Could not send the email: "
              + e.getMessage()
              + " (if this persists, "
              + sender.get().getEmail()
              + " may need to authorize again)");
    }
    return "redirect:/";
  }
}
