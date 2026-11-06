package io.github.akarp0508.koifolio.backup;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Backup")
class BackupController {

    private static final String REPLACE = "replace";

    private final BackupService backups;

    BackupController(BackupService backups) {
        this.backups = backups;
    }

    @GetMapping(value = "/export", produces = "application/zip")
    @Operation(summary = "Download all your data as a zip of plain-text files", description = """
            Accounts, transactions, companies, plans, rules and settings, but no password or shared market data.""")
    ResponseEntity<byte[]> export(@AuthenticationPrincipal Jwt jwt) {
        String name = "koifolio-export-" + LocalDate.now() + ".zip";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .body(backups.export(UUID.fromString(jwt.getSubject())));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Replace all your data with an export", description = """
            Deletes your accounts, transactions, companies, plans and rules, then loads the file. Any problem \
            leaves everything as it was. mode must be replace, so this can't happen by accident.""")
    RestoredResponse restore(@AuthenticationPrincipal Jwt jwt, @RequestParam String mode,
                             @RequestPart MultipartFile file) throws IOException {
        if (!REPLACE.equals(mode)) {
            throw BackupService.invalid("mode must be replace");
        }
        try {
            var restored = backups.restore(UUID.fromString(jwt.getSubject()), BackupFormat.read(file.getBytes()));
            return new RestoredResponse(restored.accounts(), restored.transactions(), restored.companies(),
                    restored.plans(), restored.rules());
        } catch (BackupFormat.InvalidBackupException e) {
            throw BackupService.invalid(e.getMessage());
        }
    }

    record RestoredResponse(int accounts, int transactions, int companies, int plans, int rules) {
    }
}
