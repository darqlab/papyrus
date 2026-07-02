package io.darqlab.papyrus.api.controller;

import io.darqlab.papyrus.api.controller.dto.CreateMcpClientRequest;
import io.darqlab.papyrus.api.controller.dto.McpClientResponse;
import io.darqlab.papyrus.api.controller.dto.McpTokenResponse;
import io.darqlab.papyrus.api.security.ZitadelManagementClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/mcp-clients")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMcpClientController {

    private final ZitadelManagementClient managementClient;

    public AdminMcpClientController(ZitadelManagementClient managementClient) {
        this.managementClient = managementClient;
    }

    @GetMapping
    public ResponseEntity<List<McpClientResponse>> listClients() {
        List<McpClientResponse> clients = managementClient.listMcpClients().stream()
                .map(McpClientResponse::from)
                .toList();
        return ResponseEntity.ok(clients);
    }

    @PostMapping
    public ResponseEntity<McpTokenResponse> createClient(@RequestBody CreateMcpClientRequest req) {
        McpTokenResponse token = McpTokenResponse.from(managementClient.createMcpClient(req.name(), req.role()));
        return ResponseEntity.status(HttpStatus.CREATED).body(token);
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> revokeClient(@PathVariable String userId) {
        managementClient.deleteMcpClient(userId);
        return ResponseEntity.noContent().build();
    }
}
