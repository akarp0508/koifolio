package io.github.akarp0508.koifolio.audit;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** What changed, who changed it, and the values on both sides. It's written with the change, so a failed change leaves no entry. */
@Service
public class AuditLog {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    AuditLog(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * @param before the values before the change, null for something new
     * @param after the values after it, null for something removed
     */
    public record Change(String entityType, UUID entityId, String action, String summary, Object before, Object after) {
    }

    /** @param changedSince the same thing changed again later */
    public record Entry(UUID id, Actor actor, String entityType, UUID entityId, String action, String summary, Object before,
                        Object after, Instant createdAt, boolean reverted, boolean changedSince) {

        /** An agent's change to a company or a rule that still stands. Its transactions have their own reject and delete. */
        public boolean revertible() {
            return actor.isAgent() && !reverted && Set.of("company", "rule").contains(entityType)
                    && Set.of("create", "update", "delete").contains(action);
        }
    }

    private static final String SELECT = """
            SELECT a.*, EXISTS (SELECT 1 FROM audit_log later
                                WHERE later.user_id = a.user_id AND later.entity_type = a.entity_type
                                  AND later.entity_id = a.entity_id AND later.created_at > a.created_at
                                  AND later.action IN ('create', 'update', 'delete')) AS changed_since
            FROM audit_log a
            """;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID userId, Actor actor, Change change) {
        jdbc.sql("""
                INSERT INTO audit_log (id, user_id, actor_type, actor_name, entity_type, entity_id, action, summary, before,
                                       after, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)""")
                .params(UUID.randomUUID(), userId, actor.type().name(), actor.name(), change.entityType(), change.entityId(),
                        change.action(), change.summary(), write(change.before()), write(change.after()),
                        Timestamp.from(Instant.now()))
                .update();
    }

    @Transactional(readOnly = true)
    List<Entry> recent(UUID userId, int limit) {
        return jdbc.sql(SELECT + "WHERE a.user_id = ? ORDER BY a.created_at DESC, a.id LIMIT ?")
                .params(userId, limit)
                .query(this::entry)
                .list();
    }

    @Transactional(readOnly = true)
    public Optional<Entry> find(UUID userId, UUID id) {
        return jdbc.sql(SELECT + "WHERE a.user_id = ? AND a.id = ?").params(userId, id).query(this::entry).optional();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void markReverted(UUID id) {
        jdbc.sql("UPDATE audit_log SET reverted_at = now() WHERE id = ?").param(id).update();
    }

    private Entry entry(ResultSet row, int number) throws SQLException {
        return new Entry(row.getObject("id", UUID.class),
                new Actor(Actor.Type.valueOf(row.getString("actor_type")), row.getString("actor_name")),
                row.getString("entity_type"), row.getObject("entity_id", UUID.class), row.getString("action"),
                row.getString("summary"), read(row.getString("before")), read(row.getString("after")),
                row.getTimestamp("created_at").toInstant(), row.getTimestamp("reverted_at") != null, row.getBoolean("changed_since"));
    }

    private String write(Object value) {
        return value == null ? null : json.writeValueAsString(value);
    }

    /** As BigDecimal, so money comes back exactly as it was stored. */
    private Object read(String value) {
        return value == null ? null
                : json.readerFor(Object.class).with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readValue(value);
    }
}
