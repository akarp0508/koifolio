package io.github.akarp0508.koifolio.price;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SplitSuggestionRepository extends JpaRepository<SplitSuggestion, UUID> {

    List<SplitSuggestion> findBySymbolIn(Collection<String> symbols);

    @Query(nativeQuery = true, value = "SELECT suggestion_id FROM split_suggestion_dismissals WHERE user_id = :userId")
    List<UUID> findDismissed(UUID userId);

    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO split_suggestion_dismissals (suggestion_id, user_id) VALUES (:suggestionId, :userId)
            ON CONFLICT DO NOTHING""")
    void dismiss(UUID suggestionId, UUID userId);
}
