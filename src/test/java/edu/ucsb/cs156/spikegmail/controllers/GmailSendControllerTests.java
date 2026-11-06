package edu.ucsb.cs156.spikegmail.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.ucsb.cs156.spikegmail.entities.GmailSender;
import edu.ucsb.cs156.spikegmail.entities.User;
import edu.ucsb.cs156.spikegmail.repositories.GmailSenderRepository;
import edu.ucsb.cs156.spikegmail.services.CurrentUserService;
import edu.ucsb.cs156.spikegmail.services.GmailService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

@ExtendWith(MockitoExtension.class)
public class GmailSendControllerTests {

  @Mock CurrentUserService currentUserService;
  @Mock GmailSenderRepository gmailSenderRepository;
  @Mock GmailService gmailService;

  GmailSendController controller;
  RedirectAttributes redirectAttributes;

  @BeforeEach
  public void setUp() {
    controller = new GmailSendController(currentUserService, gmailSenderRepository, gmailService);
    redirectAttributes = new RedirectAttributesModelMap();
  }

  @Test
  public void unknown_sender_is_an_error_and_sends_nothing() throws Exception {
    when(currentUserService.getUser()).thenReturn(User.builder().email("user@ucsb.edu").build());
    when(gmailSenderRepository.findById(99L)).thenReturn(Optional.empty());

    String view = controller.send(99L, redirectAttributes);

    assertEquals("redirect:/", view);
    assertEquals("No such sender.", redirectAttributes.getFlashAttributes().get("error"));
    verify(gmailService, never()).sendEmail(any(), any(), any(), any());
  }

  @Test
  public void the_email_is_sent_to_the_logged_in_user_from_the_chosen_admin() throws Exception {
    GmailSender sender = GmailSender.builder().id(1L).email("admin@ucsb.edu").build();
    when(currentUserService.getUser()).thenReturn(User.builder().email("user@ucsb.edu").build());
    when(gmailSenderRepository.findById(1L)).thenReturn(Optional.of(sender));

    String view = controller.send(1L, redirectAttributes);

    assertEquals("redirect:/", view);
    verify(gmailService)
        .sendEmail(
            eq(sender), eq("user@ucsb.edu"), eq(GmailSendController.SUBJECT), anyString());
    assertTrue(
        redirectAttributes.getFlashAttributes().get("message").toString().contains("admin@ucsb.edu"));
  }

  @Test
  public void a_failure_to_send_is_reported_to_the_user() throws Exception {
    GmailSender sender = GmailSender.builder().id(1L).email("admin@ucsb.edu").build();
    when(currentUserService.getUser()).thenReturn(User.builder().email("user@ucsb.edu").build());
    when(gmailSenderRepository.findById(1L)).thenReturn(Optional.of(sender));
    doThrow(new IllegalStateException("boom"))
        .when(gmailService)
        .sendEmail(any(), any(), any(), any());

    String view = controller.send(1L, redirectAttributes);

    assertEquals("redirect:/", view);
    assertTrue(redirectAttributes.getFlashAttributes().get("error").toString().contains("boom"));
  }
}
