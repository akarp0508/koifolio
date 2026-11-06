package io.github.akarp0508.koifolio.split;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/split-suggestions")
@Tag(name = "Split suggestions")
class SplitSuggestionController {

    private final SplitSuggestionService suggestions;

    SplitSuggestionController(SplitSuggestionService suggestions) {
        this.suggestions = suggestions;
    }

    @GetMapping
    @Operation(summary = "Splits spotted in your stocks that you haven't entered",
            description = "The date is the first day seen after the split, so it can be a few days late.")
    List<SuggestionResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return suggestions.forUser(userId(jwt)).stream().map(SuggestionResponse::of).toList();
    }

    @PostMapping("/{id}/apply")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Add the split to every affected account")
    void apply(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        suggestions.apply(userId(jwt), id);
    }

    @PostMapping("/{id}/dismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Stop suggesting this split")
    void dismiss(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        suggestions.dismiss(userId(jwt), id);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    record SuggestionResponse(UUID id, String ticker, LocalDate date, int splitFrom, int splitTo, List<String> accounts) {

        static SuggestionResponse of(SplitSuggestionService.Suggestion suggestion) {
            return new SuggestionResponse(suggestion.split().getId(), suggestion.ticker(), suggestion.split().getDate(),
                    suggestion.split().getSplitFrom(), suggestion.split().getSplitTo(),
                    suggestion.accounts().stream().map(account -> account.getName()).toList());
        }
    }
}
