package io.github.akarp0508.koifolio.review;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/review")
@Tag(name = "Review")
class ReviewController {

    private final ReviewService review;

    ReviewController(ReviewService review) {
        this.review = review;
    }

    @GetMapping("/count")
    @Operation(summary = "How much is waiting for you", description = "Agent transactions and changes together.")
    WaitingResponse count(@AuthenticationPrincipal Jwt jwt) {
        return new WaitingResponse(review.waitingCount(userId(jwt)));
    }

    @GetMapping("/changes")
    @Operation(summary = "Changes from agents, waiting for you", description = """
            Nothing here has applied yet. current has the values each would replace.""")
    List<PendingChangeResponse> changes(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = userId(jwt);
        return review.list(userId).stream().map(pending -> PendingChangeResponse.of(pending, review.current(userId, pending))).toList();
    }

    @PostMapping("/changes/{id}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Apply a change", description = "It can still be reverted from the change log.")
    void approve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        review.approve(userId(jwt), id);
    }

    @PostMapping("/changes/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Drop a change without applying it")
    void reject(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        review.reject(userId(jwt), id);
    }

    @PostMapping("/approve-all")
    @Operation(summary = "Approve everything waiting, transactions and changes", description = """
            With agent, only that agent's. If any can't apply, none are.""")
    DecidedResponse approveAll(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String agent) {
        return DecidedResponse.of(review.approveAll(userId(jwt), agent));
    }

    @PostMapping("/reject-all")
    @Operation(summary = "Reject everything waiting, transactions and changes", description = """
            With agent, only that agent's. Rejected transactions come out of every number.""")
    DecidedResponse rejectAll(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String agent) {
        return DecidedResponse.of(review.rejectAll(userId(jwt), agent));
    }

    @PostMapping("/log/{id}/revert")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Undo an agent's approved change", description = """
            Puts back the values from before it, in full. An account can only be undone while it has no transactions. If the same thing has changed since, this returns 409 \
            changed_since unless force is on, because those later changes would be lost.""")
    void revert(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestParam(defaultValue = "false") boolean force) {
        review.revert(userId(jwt), id, force);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    /** @param proposed left out when the change removes something */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    record PendingChangeResponse(UUID id, String agentName, String summary, Map<String, Object> current, Map<String, Object> proposed,
                                 Instant createdAt) {

        static PendingChangeResponse of(ReviewService.Pending pending, Map<String, Object> current) {
            return new PendingChangeResponse(pending.id(), pending.agent(), pending.summary(), current, pending.changes(),
                    pending.createdAt());
        }
    }

    record WaitingResponse(int waiting) {
    }

    record DecidedResponse(int transactions, int changes) {

        static DecidedResponse of(ReviewService.Decided decided) {
            return new DecidedResponse(decided.transactions(), decided.changes());
        }
    }
}
