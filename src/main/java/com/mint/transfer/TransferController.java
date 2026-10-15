package com.mint.transfer;

import com.mint.transfer.dto.TransferRequest;
import com.mint.transfer.dto.TransferResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller exposing endpoints to initiate and query money transfers.
 */
@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    /**
     * Executes a money transfer between two accounts.
     * Requires the `Idempotency-Key` HTTP header.
     * POST /api/v1/transfers
     */
    @PostMapping
    public ResponseEntity<TransferResponse> executeTransfer(
            @AuthenticationPrincipal String email,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        TransferResponse response = transferService.executeTransfer(email, idempotencyKey, request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Inspects the status and audit details of a past transfer.
     * GET /api/v1/transfers/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<TransferResponse> getTransferById(
            @AuthenticationPrincipal String email,
            @PathVariable UUID id) {
        TransferResponse response = transferService.getTransferById(id, email);
        return ResponseEntity.ok(response);
    }
}
