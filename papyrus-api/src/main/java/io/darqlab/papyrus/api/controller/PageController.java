package io.darqlab.papyrus.api.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/")
    public String index() {
        return "redirect:/chat";
    }

    @GetMapping("/chat")
    public String chat(Model model) {
        model.addAttribute("activePage", "chat");
        return "chat";
    }

    @GetMapping("/ingest")
    public String ingest(Model model) {
        model.addAttribute("activePage", "ingest");
        return "ingest";
    }

    @GetMapping("/documents")
    public String documents(Model model) {
        model.addAttribute("activePage", "documents");
        return "documents";
    }

    // ADR-008: Manage merged into Documents (role-gated). Route kept as a redirect
    // so old bookmarks/deep links to /manage don't 404.
    @GetMapping("/manage")
    public String manage() {
        return "redirect:/documents";
    }

    @GetMapping("/admin/users")
    public String adminUsers(Model model) {
        model.addAttribute("activePage", "admin");
        return "admin/users";
    }
}
