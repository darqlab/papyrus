package io.darqlab.papyrus.api.controller;

import io.darqlab.papyrus.api.controller.dto.ChangeRoleRequest;
import io.darqlab.papyrus.api.controller.dto.CreateUserRequest;
import io.darqlab.papyrus.api.controller.dto.UserResponse;
import io.darqlab.papyrus.api.security.ZitadelManagementClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final ZitadelManagementClient managementClient;

    public AdminUserController(ZitadelManagementClient managementClient) {
        this.managementClient = managementClient;
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> listUsers() {
        List<UserResponse> users = managementClient.listProjectUsers().stream()
                .map(UserResponse::from)
                .toList();
        return ResponseEntity.ok(users);
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> inviteUser(@RequestBody CreateUserRequest req) {
        managementClient.inviteUser(req.firstName(), req.lastName(), req.email(), req.role());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "Invitation sent to " + req.email()));
    }

    @PutMapping("/{userId}/role")
    public ResponseEntity<Void> changeRole(@PathVariable String userId,
                                           @RequestBody ChangeRoleRequest req) {
        managementClient.changeRole(userId, req.grantId(), req.role());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> removeUser(@PathVariable String userId,
                                           @RequestParam String grantId) {
        managementClient.removeUser(userId, grantId);
        return ResponseEntity.noContent().build();
    }
}
