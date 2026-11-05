package io.github.akarp0508.koifolio.instrument;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/instruments")
@Tag(name = "Instruments")
class InstrumentController {

    private final InstrumentService instruments;

    InstrumentController(InstrumentService instruments) {
        this.instruments = instruments;
    }

    @GetMapping
    @Operation(summary = "List your instruments, by ticker")
    List<InstrumentResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return instruments.list(userId(jwt)).stream().map(InstrumentResponse::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add an instrument to your list", description = "Using a new ticker in a transaction also adds it.")
    InstrumentResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateRequest request) {
        return InstrumentResponse.of(instruments.create(userId(jwt), request.ticker(), request.name()));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Rename an instrument", description = "The ticker can't be changed.")
    InstrumentResponse rename(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                              @Valid @RequestBody RenameRequest request) {
        return InstrumentResponse.of(instruments.rename(userId(jwt), id, request.name()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove an instrument from your list", description = "Transactions keep their ticker.")
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        instruments.delete(userId(jwt), id);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    record CreateRequest(
            @NotNull @Schema(example = "PAS")
            @Pattern(regexp = "\\s*[A-Za-z0-9.\\-]{1,20}\\s*", message = "must be up to 20 letters, digits, dots or dashes")
            String ticker,
            @Size(max = 100, message = "must be at most 100 characters") @Schema(example = "Passus") String name) {
    }

    record RenameRequest(@NotNull @Size(max = 100, message = "must be at most 100 characters") String name) {
    }

    record InstrumentResponse(UUID id, String ticker, String name) {

        static InstrumentResponse of(Instrument instrument) {
            return new InstrumentResponse(instrument.getId(), instrument.getTicker(), instrument.getName());
        }
    }
}
