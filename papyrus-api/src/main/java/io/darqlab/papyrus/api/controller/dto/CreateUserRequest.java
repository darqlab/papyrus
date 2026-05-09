package io.darqlab.papyrus.api.controller.dto;

public record CreateUserRequest(
        String firstName,
        String lastName,
        String email,
        String role
) {}
