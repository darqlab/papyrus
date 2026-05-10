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

    @GetMapping("/manage")
    public String manage(Model model) {
        model.addAttribute("activePage", "manage");
        return "manage";
    }

    @GetMapping("/admin/users")
    public String adminUsers(Model model) {
        model.addAttribute("activePage", "admin");
        return "admin/users";
    }
}
