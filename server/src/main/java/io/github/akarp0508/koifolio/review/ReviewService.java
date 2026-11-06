package io.github.akarp0508.koifolio.review;

import io.github.akarp0508.koifolio.ApiException;
import io.github.akarp0508.koifolio.account.AccountService;
import io.github.akarp0508.koifolio.audit.Actor;
import io.github.akarp0508.koifolio.audit.AuditLog;
import io.github.akarp0508.koifolio.bond.BondService;
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
 * Keeps agents under the user's control. Their edits wait here until approved, and a change
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
    private final AccountService accounts;
    private final BondService bonds;

    ReviewService(JdbcClient jdbc, JsonMapper json, AuditLog auditLog, InstrumentService instruments, RuleService rules,
                  TransactionService transactions, AccountService accounts, BondService bonds) {
        this.bonds = bonds;
        this.accounts = accounts;
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
        RULE_DELETE,
        ACCOUNT,
        ACCOUNT_UPDATE,
        BOND,
        TRANSACTION_UPDATE,
        TRANSACTION_DELETE
    }

    record Pending(UUID id, String agent, Kind kind, String ticker, UUID targetId, String summary, Map<String, Object> changes,
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
    public Proposed proposeAccount(UUID userId, String agent, AccountService.Input input) {
        accounts.check(input);
        String balance = input.balance() == null ? "0" : input.balance().toPlainString();
        return propose(userId, agent, Kind.ACCOUNT, null, null, json.convertValue(input, MAP),
                "Create account %s (%s, balance %s)".formatted(input.name().strip(), input.currency(), balance));
    }

    @Transactional
    public Proposed proposeAccountUpdate(UUID userId, String agent, UUID id, AccountService.Edit edit) {
        accounts.check(userId, id, edit);
        Map<String, Object> changes = requireChanges(edit);
        return propose(userId, agent, Kind.ACCOUNT_UPDATE, null, id, changes,
                "Change account %s: %s".formatted(accounts.values(userId, id).get("name"), String.join(", ", changes.keySet())));
    }

    @Transactional
    public Proposed proposeTransactionUpdate(UUID userId, String agent, UUID id, TransactionService.Edit edit) {
        transactions.check(userId, id, edit);
        Map<String, Object> changes = requireChanges(edit);
        return propose(userId, agent, Kind.TRANSACTION_UPDATE, null, id, changes,
                "Change %s: %s".formatted(transactions.describe(userId, id), String.join(", ", changes.keySet())));
    }

    @Transactional
    public Proposed proposeTransactionDelete(UUID userId, String agent, UUID id) {
        return propose(userId, agent, Kind.TRANSACTION_DELETE, null, id, Map.of(), "Delete " + transactions.describe(userId, id));
    }

    @Transactional
    public Proposed proposeBond(UUID userId, String agent, BondService.Input input) {
        bonds.check(userId, input);
        return propose(userId, agent, Kind.BOND, null, null, json.convertValue(input, MAP),
                "Add %d %s bonds".formatted(input.quantity(), input.series()));
    }

    @Transactional
    public Proposed proposeRuleDelete(UUID userId, String agent, UUID id) {
        return propose(userId, agent, Kind.RULE_DELETE, null, id, Map.of(),
                "Remove rule " + RuleService.describe(rules.get(userId, id)));
    }

    private Map<String, Object> requireChanges(Object edit) {
        Map<String, Object> changes = json.convertValue(edit, MAP);
        if (changes.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "nothing_to_change", "Give at least one field to change");
        }
        return changes;
    }

    private Proposed propose(UUID userId, String agent, Kind kind, String ticker, UUID targetId, Map<String, Object> changes,
                             String summary) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO pending_changes (id, user_id, agent_name, kind, ticker, target_id, changes, summary, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)""")
                .params(id, userId, agent, kind.name(), ticker, targetId, json.writeValueAsString(changes), summary,
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

    /** Everything waiting, transactions and changes. */
    @Transactional(readOnly = true)
    int waitingCount(UUID userId) {
        return transactions.countUnvalidated(userId) + count(userId);
    }

    public int count(UUID userId) {
        return jdbc.sql("SELECT count(*) FROM pending_changes WHERE user_id = ?").param(userId).query(Integer.class).single();
    }

    /** What the proposal would replace, just the fields it touches. Empty for something new. */
    @Transactional(readOnly = true)
    Map<String, Object> current(UUID userId, Pending pending) {
        return switch (pending.kind()) {
            case COMPANY -> instruments.details(userId, pending.ticker())
                    .map(details -> touched(pending, json.convertValue(details, MAP))).orElse(Map.of());
            case RULE, RULE_DELETE -> pending.targetId() == null ? Map.of()
                    : rules.lookUp(userId, pending.targetId()).<Map<String, Object>>map(input -> json.convertValue(input, MAP)).orElse(Map.of());
            case ACCOUNT_UPDATE -> touched(pending, accounts.values(userId, pending.targetId()));
            case TRANSACTION_UPDATE -> touched(pending, transactions.values(userId, pending.targetId()));
            case TRANSACTION_DELETE -> transactions.values(userId, pending.targetId());
            case ACCOUNT, BOND -> Map.of();
        };
    }

    private static Map<String, Object> touched(Pending pending, Map<String, Object> all) {
        Map<String, Object> touched = new LinkedHashMap<>();
        pending.changes().keySet().forEach(field -> touched.put(field, all.get(field)));
        return touched;
    }

    @Transactional
    void approve(UUID userId, UUID id) {
        approve(userId, find(userId, id));
    }

    @Transactional
    void reject(UUID userId, UUID id) {
        reject(userId, find(userId, id));
    }

    /**
     * @param agent limits it to one agent's work, or null for everyone's
     * @param importId limits it to one import's transactions, or null for everything
     */
    @Transactional
    Decided approveAll(UUID userId, String agent, UUID importId) {
        List<Pending> waiting = waiting(userId, agent, importId);
        waiting.forEach(pending -> approve(userId, pending));
        return new Decided(transactions.approveAll(userId, agent, importId), waiting.size());
    }

    /**
     * @param agent limits it to one agent's work, or null for everyone's
     * @param importId limits it to one import's transactions, or null for everything
     */
    @Transactional
    Decided rejectAll(UUID userId, String agent, UUID importId) {
        List<Pending> waiting = waiting(userId, agent, importId);
        waiting.forEach(pending -> reject(userId, pending));
        return new Decided(transactions.rejectAll(userId, agent, importId), waiting.size());
    }

    /** An import is only transactions, so no changes belong to one. */
    private List<Pending> waiting(UUID userId, String agent, UUID importId) {
        return importId != null ? List.of()
                : list(userId).stream().filter(pending -> agent == null || pending.agent().equals(agent)).toList();
    }

    /** The change is logged under the agent, since it's the agent's work, and the approval under the user. */
    private void approve(UUID userId, Pending pending) {
        Actor agent = Actor.agent(pending.agent());
        switch (pending.kind()) {
            case COMPANY -> instruments.applyEdit(userId, pending.ticker(), convert(pending.changes(), InstrumentService.Edit.class), agent);
            case RULE -> {
                RuleService.Input input = convert(pending.changes(), RuleService.Input.class);
                if (pending.targetId() == null) {
                    rules.create(userId, input, agent);
                } else {
                    rules.update(userId, pending.targetId(), input, agent);
                }
            }
            case RULE_DELETE -> rules.delete(userId, pending.targetId(), agent);
            case ACCOUNT -> accounts.create(userId, convert(pending.changes(), AccountService.Input.class), agent);
            case ACCOUNT_UPDATE -> accounts.applyEdit(userId, pending.targetId(), convert(pending.changes(), AccountService.Edit.class), agent);
            case TRANSACTION_UPDATE -> transactions.applyEdit(userId, pending.targetId(), convert(pending.changes(), TransactionService.Edit.class), agent);
            case TRANSACTION_DELETE -> transactions.delete(userId, pending.targetId(), agent);
            case BOND -> bonds.create(userId, convert(pending.changes(), BondService.Input.class), agent);
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
            case "account" -> {
                if (added) {
                    accounts.deleteUnused(userId, entry.entityId());
                } else {
                    accounts.revertEdit(userId, entry.entityId(), convert(entry.before(), AccountService.Edit.class),
                            convert(entry.after(), AccountService.Edit.class));
                }
            }
            case "transaction" -> {
                if (removed) {
                    transactions.undelete(userId, entry.entityId());
                } else {
                    transactions.applyEdit(userId, entry.entityId(), convert(entry.before(), TransactionService.Edit.class), Actor.USER);
                }
            }
            case "bond" -> bonds.delete(userId, entry.entityId(), Actor.USER);
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
                row.getString("ticker"), row.getObject("target_id", UUID.class), row.getString("summary"),
                json.readerFor(MAP).with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readValue(row.getString("changes")),
                row.getTimestamp("created_at").toInstant());
    }

    private <T> T convert(Object values, Class<T> type) {
        return json.convertValue(values, type);
    }

    private static final tools.jackson.core.type.TypeReference<Map<String, Object>> MAP = new tools.jackson.core.type.TypeReference<>() {
    };
}
