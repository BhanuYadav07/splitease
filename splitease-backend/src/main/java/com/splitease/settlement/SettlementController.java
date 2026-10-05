package com.splitease.settlement;

import com.splitease.security.CurrentUserProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/groups/{groupId}/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/suggested")
    public List<SettlementDtos.SuggestedSettlementResponse> suggested(@PathVariable UUID groupId) {
        return settlementService.getSuggestedSettlements(groupId);
    }

    @GetMapping
    public List<SettlementDtos.SettlementHistoryResponse> history(@PathVariable UUID groupId) {
        return settlementService.getHistory(groupId);
    }

    @PostMapping
    public ResponseEntity<SettlementDtos.SettlementHistoryResponse> record(
            @PathVariable UUID groupId, @Valid @RequestBody SettlementDtos.RecordSettlementRequest request) {
        Settlement s = settlementService.recordSettlement(groupId, currentUserProvider.getCurrentUserId(), request);
        var response = new SettlementDtos.SettlementHistoryResponse(
                s.getId(), s.getFromUser().getId(), s.getFromUser().getName(),
                s.getToUser().getId(), s.getToUser().getName(), s.getAmount(), s.getStatus().name(), s.getSettledAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
