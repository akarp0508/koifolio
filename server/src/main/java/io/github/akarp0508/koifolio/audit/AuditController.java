package io.github.akarp0508.koifolio.audit;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-log")
@Tag(name = "Change log")
class AuditController {

    private final AuditLog log;

    AuditController(AuditLog log) {
        this.log = log;
    }

    @GetMapping
    @Operation(summary = "Recent changes to transactions, companies, rules, accounts and plans, newest first", description = """
            From you or an agent. before and after hold the values on each side of the change. Deleting an account \
            keeps the transactions, plans and rules that went with it under before.""")
    List<AuditEntryResponse> recent(@AuthenticationPrincipal Jwt jwt,
                                    @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit) {
        return log.recent(UUID.fromString(jwt.getSubject()), limit).stream().map(AuditEntryResponse::of).toList();
    }

    /**
     * @param actorName the agent's name, left out for you
     * @param revertible an agent's company or rule change that can still be undone
     * @param changedSince the same thing changed again later, so reverting would lose that too
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AuditEntryResponse(UUID id, Actor.Type actorType, String actorName, String entityType, UUID entityId, String action,
                              String summary, Object before, Object after, Instant createdAt, boolean revertible,
                              boolean changedSince) {

        static AuditEntryResponse of(AuditLog.Entry entry) {
            return new AuditEntryResponse(entry.id(), entry.actor().type(), entry.actor().name(), entry.entityType(),
                    entry.entityId(), entry.action(), entry.summary(), entry.before(), entry.after(), entry.createdAt(),
                    entry.revertible(), entry.changedSince());
        }
    }
}
