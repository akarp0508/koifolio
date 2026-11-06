package io.github.akarp0508.koifolio.review;

import io.github.akarp0508.koifolio.ApiException;
import io.github.akarp0508.koifolio.audit.Actor;
import io.github.akarp0508.koifolio.audit.AuditLog;
import io.github.akarp0508.koifolio.instrument.InstrumentDetails;
import io.github.akarp0508.koifolio.instrument.InstrumentService;
import io.github.akarp0508.koifolio.rule.RuleService;
import io.github.akarp0508.koifolio.transaction.TransactionService;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Keeps agents under the user's control. Company and rule edits wait here until approved, and a change
 * that was approved can still be reverted from the change log.
 */
@Service
public class ReviewService {

    private static final String PROPOSAL = "proposal";

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final AuditLog auditLog;
    private final InstrumentService instruments;
    private final RuleService rules;
    private final TransactionService transactions;

    ReviewService(JdbcClient jdbc, JsonMapper json, AuditLog auditLog, InstrumentService instruments, RuleService rules,
                  TransactionService transactions) {
        this.jdbc = jdbc;
        this.json = json;
        this.auditLog = auditLog;
        this.instruments = instruments;
        this.rules = rules;
        this.transactions = transactions;
    }

    enum Kind {
        COMPANY,
        RULE,
        RULE_DELETE
    }

    record Pending(UUID id, String agent, Kind kind, String ticker, UUID ruleId, String summary, Map<String, Object> changes,
                   Instant createdAt) {
    }

    /** @return how many of each kind were handled */
    record Decided(int transactions, int changes) {
    }

    public record Proposed(UUID id, String status, String summary) {
    }

    @Transactional
    public Proposed proposeCompany(UUID userId, String agent, String ticker, InstrumentService.Edit edit) {
        String normalized = InstrumentService.normalizeTicker(ticker);
        Map<String, Object> changes = json.convertValue(edit, MAP);
        return propose(userId, agent, Kind.COMPANY, normalized, null, changes,
                "Change %s: %s".formatted(normalized, String.join(", ", changes.keySet())));
    }

    /** @param id the rule to replace, null for a new one */
    @Transactional
    public Proposed proposeRule(UUID userId, String agent, UUID id, RuleService.Input input) {
        rules.check(userId, input);
        if (id != null) {
            rules.get(userId, id);
        }
        return propose(userId, agent, Kind.RULE, null, id, json.convertValue(input, MAP),
                (id == null ? "Add rule " : "Change rule to ") + RuleService.describe(input));
    }

    @Transactional
    public Proposed proposeRuleDelete(UUID userId, String agent, UUID id) {
        return propose(userId, agent, Kind.RULE_DELETE, null, id, Map.of(),
                "Remove rule " + RuleService.describe(rules.get(userId, id)));
    }

    private Proposed propose(UUID userId, String agent, Kind kind, String ticker, UUID ruleId, Map<String, Object> changes,
                             String summary) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO pending_changes (id, user_id, agent_name, kind, ticker, rule_id, changes, summary, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)""")
                .params(id, userId, agent, kind.name(), ticker, ruleId, json.writeValueAsString(changes), summary,
                        Timestamp.from(Instant.now()))
                .update();
        auditLog.record(userId, Actor.agent(agent), new AuditLog.Change(PROPOSAL, id, "propose", "Proposed: " + summary, null, changes));
        return new Proposed(id, "waiting for review", summary);
    }

    /** Oldest first, which is the order they'd apply in. */
    @Transactional(readOnly = true)
    public List<Pending> list(UUID userId) {
        return jdbc.sql("SELECT * FROM pending_changes WHERE user_id = ? ORDER BY created_at, id")
                .param(userId).query(this::pending).list();
    }

    public int count(UUID userId) {
        return jdbc.sql("SELECT count(*) FROM pending_changes WHERE user_id = ?").param(userId).query(Integer.class).single();
    }

    /** What the proposal would replace, just the fields it touches. Empty for something new. */
    @Transactional(readOnly = true)
    Map<String, Object> current(UUID userId, Pending pending) {
        return switch (pending.kind()) {
            case COMPANY -> instruments.details(userId, pending.ticker()).map(details -> {
                Map<String, Object> all = json.convertValue(details, MAP);
                Map<String, Object> touched = new LinkedHashMap<>();
                pending.changes().keySet().forEach(field -> touched.put(field, all.get(field)));
                return touched;
            }).orElse(Map.of());
            case RULE, RULE_DELETE -> pending.ruleId() == null ? Map.of()
                    : rules.lookUp(userId, pending.ruleId()).<Map<String, Object>>map(input -> json.convertValue(input, MAP)).orElse(Map.of());
        };
    }

    @Transactional
    void approve(UUID userId, UUID id) {
        approve(userId, find(userId, id));
    }

    @Transactional
    void reject(UUID userId, UUID id) {
        reject(userId, find(userId, id));
    }

    /** @param agent limits it to one agent's work, or null for everyone's */
    @Transactional
    Decided approveAll(UUID userId, String agent) {
        List<Pending> waiting = waiting(userId, agent);
        waiting.forEach(pending -> approve(userId, pending));
        return new Decided(transactions.approveAll(userId, agent), waiting.size());
    }

    /** @param agent limits it to one agent's work, or null for everyone's */
    @Transactional
    Decided rejectAll(UUID userId, String agent) {
        List<Pending> waiting = waiting(userId, agent);
        waiting.forEach(pending -> reject(userId, pending));
        return new Decided(transactions.rejectAll(userId, agent), waiting.size());
    }

    private List<Pending> waiting(UUID userId, String agent) {
        return list(userId).stream().filter(pending -> agent == null || pending.agent().equals(agent)).toList();
    }

    /** The change is logged under the agent, since it's the agent's work, and the approval under the user. */
    private void approve(UUID userId, Pending pending) {
        Actor agent = Actor.agent(pending.agent());
        switch (pending.kind()) {
            case COMPANY -> instruments.applyEdit(userId, pending.ticker(), convert(pending.changes(), InstrumentService.Edit.class), agent);
            case RULE -> {
                RuleService.Input input = convert(pending.changes(), RuleService.Input.class);
                if (pending.ruleId() == null) {
                    rules.create(userId, input, agent);
                } else {
                    rules.update(userId, pending.ruleId(), input, agent);
                }
            }
            case RULE_DELETE -> rules.delete(userId, pending.ruleId(), agent);
        }
        decide(userId, pending, "approve", "Approved: ");
    }

    private void reject(UUID userId, Pending pending) {
        decide(userId, pending, "reject", "Rejected: ");
    }

    private void decide(UUID userId, Pending pending, String action, String prefix) {
        jdbc.sql("DELETE FROM pending_changes WHERE id = ?").param(pending.id()).update();
        auditLog.record(userId, Actor.USER, new AuditLog.Change(PROPOSAL, pending.id(), action, prefix + pending.summary(), null, null));
    }

    /**
     * Puts back the values from before an agent's change.
     *
     * @param force go ahead even though the same thing has changed since, which those later changes then lose
     */
    @Transactional
    void revert(UUID userId, UUID entryId, boolean force) {
        AuditLog.Entry entry = auditLog.find(userId, entryId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "change_not_found", "Change not found"));
        if (!entry.revertible()) {
            throw new ApiException(HttpStatus.CONFLICT, "change_not_revertible", "This change can't be reverted");
        }
        if (entry.changedSince() && !force) {
            throw new ApiException(HttpStatus.CONFLICT, "changed_since", "This has changed since, and reverting would undo those changes too");
        }
        boolean added = "create".equals(entry.action());
        boolean removed = "delete".equals(entry.action());
        switch (entry.entityType()) {
            case "company" -> {
                if (added) {
                    instruments.delete(userId, entry.entityId());
                } else {
                    instruments.update(userId, entry.entityId(), convert(entry.before(), InstrumentDetails.class), Actor.USER);
                }
            }
            case "rule" -> {
                if (added) {
                    rules.delete(userId, entry.entityId(), Actor.USER);
                } else if (removed) {
                    rules.create(userId, convert(entry.before(), RuleService.Input.class), Actor.USER);
                } else {
                    rules.update(userId, entry.entityId(), convert(entry.before(), RuleService.Input.class), Actor.USER);
                }
            }
            default -> throw new IllegalStateException(entry.entityType());
        }
        auditLog.markReverted(entryId);
        auditLog.record(userId, Actor.USER, new AuditLog.Change(entry.entityType(), entry.entityId(), "revert",
                "Reverted: " + entry.summary(), null, null));
    }

    private Pending find(UUID userId, UUID id) {
        return jdbc.sql("SELECT * FROM pending_changes WHERE id = ? AND user_id = ?").params(id, userId)
                .query(this::pending).optional().orElseThrow(() ->
                        new ApiException(HttpStatus.NOT_FOUND, "pending_change_not_found", "Pending change not found"));
    }

    private Pending pending(ResultSet row, int number) throws SQLException {
        return new Pending(row.getObject("id", UUID.class), row.getString("agent_name"), Kind.valueOf(row.getString("kind")),
                row.getString("ticker"), row.getObject("rule_id", UUID.class), row.getString("summary"),
                json.readerFor(MAP).with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readValue(row.getString("changes")),
                row.getTimestamp("created_at").toInstant());
    }

    private <T> T convert(Object values, Class<T> type) {
        return json.convertValue(values, type);
    }

    private static final tools.jackson.core.type.TypeReference<Map<String, Object>> MAP = new tools.jackson.core.type.TypeReference<>() {
    };
}
