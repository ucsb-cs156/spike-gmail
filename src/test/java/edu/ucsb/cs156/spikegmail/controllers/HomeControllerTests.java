package edu.ucsb.cs156.spikegmail.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import edu.ucsb.cs156.spikegmail.entities.GmailSender;
import edu.ucsb.cs156.spikegmail.entities.User;
import edu.ucsb.cs156.spikegmail.repositories.GmailSenderRepository;
import edu.ucsb.cs156.spikegmail.services.CurrentUserService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

@ExtendWith(MockitoExtension.class)
public class HomeControllerTests {

  @Mock CurrentUserService currentUserService;
  @Mock GmailSenderRepository gmailSenderRepository;

  HomeController controller;
  Model model;

  @BeforeEach
  public void setUp() {
    controller = new HomeController(currentUserService, gmailSenderRepository);
    model = new ConcurrentModel();
  }

  @Test
  public void not_logged_in_shows_only_the_login_state() {
    when(currentUserService.getUser()).thenReturn(null);

    assertEquals("home", controller.home(model));

    assertEquals(false, model.getAttribute("loggedIn"));
    assertFalse(model.containsAttribute("senders"));
    verifyNoInteractions(gmailSenderRepository);
  }

  @Test
  public void regular_user_sees_the_senders_but_no_admin_section() {
    GmailSender sender = GmailSender.builder().id(1L).email("admin@ucsb.edu").build();
    when(currentUserService.getUser()).thenReturn(User.builder().email("user@ucsb.edu").build());
    when(currentUserService.isAdmin()).thenReturn(false);
    when(gmailSenderRepository.findAllByOrderByEmailAsc()).thenReturn(List.of(sender));

    controller.home(model);

    assertEquals(true, model.getAttribute("loggedIn"));
    assertEquals("user@ucsb.edu", model.getAttribute("email"));
    assertEquals(false, model.getAttribute("admin"));
    assertEquals(List.of(sender), model.getAttribute("senders"));
    assertEquals(false, model.getAttribute("myAuthorization"));
  }

  @Test
  public void admin_who_has_not_authorized_yet() {
    when(currentUserService.getUser()).thenReturn(User.builder().email("admin@ucsb.edu").build());
    when(currentUserService.isAdmin()).thenReturn(true);
    when(gmailSenderRepository.findAllByOrderByEmailAsc()).thenReturn(List.of());
    when(gmailSenderRepository.findByEmail("admin@ucsb.edu")).thenReturn(Optional.empty());

    controller.home(model);

    assertEquals(true, model.getAttribute("admin"));
    assertEquals(false, model.getAttribute("myAuthorization"));
  }

  @Test
  public void admin_who_has_already_authorized() {
    GmailSender sender = GmailSender.builder().id(1L).email("admin@ucsb.edu").build();
    when(currentUserService.getUser()).thenReturn(User.builder().email("admin@ucsb.edu").build());
    when(currentUserService.isAdmin()).thenReturn(true);
    when(gmailSenderRepository.findAllByOrderByEmailAsc()).thenReturn(List.of(sender));
    when(gmailSenderRepository.findByEmail("admin@ucsb.edu")).thenReturn(Optional.of(sender));

    controller.home(model);

    assertTrue((Boolean) model.getAttribute("myAuthorization"));
  }
}
