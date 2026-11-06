package io.github.akarp0508.koifolio.instrument;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
    InstrumentResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateInstrumentRequest request) {
        return InstrumentResponse.of(instruments.create(userId(jwt), request.ticker(), request.details()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one instrument", description = "Also finds ones removed from your list that you still hold.")
    InstrumentResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return InstrumentResponse.of(instruments.get(userId(jwt), id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace what you know about an instrument", description = "The ticker can't be changed.")
    InstrumentResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                              @Valid @RequestBody UpdateInstrumentRequest request) {
        return InstrumentResponse.of(instruments.update(userId(jwt), id, request.details()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove an instrument from your list", description = "Its transactions are kept.")
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        instruments.delete(userId(jwt), id);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    record CreateInstrumentRequest(
            @NotNull @Schema(example = "PAS")
            @Pattern(regexp = "\\s*[A-Za-z0-9.\\-]{1,20}\\s*", message = "must be up to 20 letters, digits, dots or dashes")
            String ticker,
            @Size(max = 100, message = "must be at most 100 characters") @Schema(example = "Passus") String name,
            @Size(max = 100, message = "must be at most 100 characters") @Schema(example = "IT / cybersecurity") String sector,
            @Min(value = 1, message = "must be between 1 and 5") @Max(value = 5, message = "must be between 1 and 5") Integer risk,
            @Size(max = 1000, message = "must be at most 1000 characters") String thesis,
            @Size(max = 100000, message = "must be at most 100000 characters") String analysis,
            @Size(max = 20, message = "must be at most 20 tags")
            List<@Size(max = 30, message = "must be at most 30 characters") String> tags) {

        InstrumentDetails details() {
            return new InstrumentDetails(name, sector, risk, thesis, analysis, Tags.clean(tags));
        }
    }

    record UpdateInstrumentRequest(
            @Size(max = 100, message = "must be at most 100 characters") String name,
            @Size(max = 100, message = "must be at most 100 characters") String sector,
            @Min(value = 1, message = "must be between 1 and 5") @Max(value = 5, message = "must be between 1 and 5") Integer risk,
            @Size(max = 1000, message = "must be at most 1000 characters") String thesis,
            @Size(max = 100000, message = "must be at most 100000 characters") String analysis,
            @Size(max = 20, message = "must be at most 20 tags")
            List<@Size(max = 30, message = "must be at most 30 characters") String> tags) {

        InstrumentDetails details() {
            return new InstrumentDetails(name, sector, risk, thesis, analysis, Tags.clean(tags));
        }
    }

    /** @param risk left out if not set */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record InstrumentResponse(UUID id, String ticker, String name, String sector, Integer risk, String thesis,
                              String analysis, Set<String> tags) {

        static InstrumentResponse of(Instrument instrument) {
            InstrumentDetails details = instrument.details();
            return new InstrumentResponse(instrument.getId(), instrument.getTicker(), details.name(), details.sector(),
                    details.risk(), details.thesis(), details.analysis(), details.tags());
        }
    }
}
