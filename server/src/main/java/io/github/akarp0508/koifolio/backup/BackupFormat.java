package io.github.akarp0508.koifolio.backup;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.akarp0508.koifolio.domain.CostFxRule;
import io.github.akarp0508.koifolio.domain.CostMethod;
import io.github.akarp0508.koifolio.domain.RuleType;
import io.github.akarp0508.koifolio.transaction.TransactionType;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.dataformat.yaml.YAMLWriteFeature;

/** The plain-text export as a zip of YAML, JSON Lines and Markdown files. See docs/export-format.md. */
final class BackupFormat {

    static final int VERSION = 1;

    private static final long MAX_UNPACKED_BYTES = 200 * 1024 * 1024;
    private static final String FRONTMATTER = "---\n";
    private static final String FRONTMATTER_END = "\n---\n";

    private static final ObjectMapper YAML = YAMLMapper.builder()
            .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
            .disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
            .build();
    private static final ObjectMapper JSON = JsonMapper.builder()
            .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
            .build();

    private BackupFormat() {
    }

    record Manifest(int format, Instant exportedAt, String baseCurrency, CostFxRule costFxRule) {
    }

    /** @param key names the account's transactions file, and links plans and rules to it */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AccountEntry(String key, String name, String description, String currency, BigDecimal balance,
                        LocalDate balanceSetOn, CostMethod costMethod, BigDecimal taxRate, Instant createdAt) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TransactionEntry(LocalDate date, TransactionType type, String ticker, BigDecimal quantity, BigDecimal price,
                            BigDecimal fee, BigDecimal amount, BigDecimal withheldTax, BigDecimal splitFrom,
                            BigDecimal splitTo, String note, boolean affectsBalance, Instant createdAt, String createdBy,
                            Boolean unvalidated) {
    }

    /** @param analysis the file's Markdown body, kept out of the frontmatter */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CompanyEntry(String ticker, String name, String sector, Integer risk, String thesis, Set<String> tags,
                        boolean listed, @JsonIgnore String analysis) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PlanEntry(String account, String ticker, String plan, Integer risk, BigDecimal targetPrice,
                     BigDecimal stopPrice, Set<String> tags) {
    }

    /** @param account left out to cover every account */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record RuleEntry(RuleType type, String account, BigDecimal limitPercent, Integer maxRisk) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record BondEntry(String account, String series, int quantity, LocalDate purchaseDate, List<BigDecimal> rates, BigDecimal earlyFee,
                     boolean affectsBalance) {
    }

    record Backup(Manifest manifest, List<AccountEntry> accounts, Map<String, List<TransactionEntry>> transactions,
                  List<CompanyEntry> companies, List<PlanEntry> plans, List<RuleEntry> rules, List<BondEntry> bonds) {
    }

    /** The file can't be read as an export. The message says what to fix. */
    static final class InvalidBackupException extends RuntimeException {

        InvalidBackupException(String message) {
            super(message);
        }
    }

    static byte[] write(Backup backup) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            entry(zip, "koifolio.yaml", YAML.writeValueAsString(backup.manifest()));
            entry(zip, "accounts.yaml", YAML.writeValueAsString(backup.accounts()));
            entry(zip, "plans.yaml", YAML.writeValueAsString(backup.plans()));
            entry(zip, "rules.yaml", YAML.writeValueAsString(backup.rules()));
            entry(zip, "bonds.yaml", YAML.writeValueAsString(backup.bonds()));
            for (Map.Entry<String, List<TransactionEntry>> file : backup.transactions().entrySet()) {
                entry(zip, "transactions/" + file.getKey() + ".jsonl", file.getValue().stream()
                        .map(JSON::writeValueAsString).collect(Collectors.joining("\n", "", "\n")));
            }
            for (CompanyEntry company : backup.companies()) {
                entry(zip, "companies/" + fileName(company.ticker()) + ".md", FRONTMATTER + YAML.writeValueAsString(company)
                        + "---\n" + (company.analysis() == null ? "" : company.analysis()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    private static void entry(ZipOutputStream zip, String name, String text) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String fileName(String ticker) {
        return URLEncoder.encode(ticker, StandardCharsets.UTF_8);
    }

    static Backup read(byte[] archive) {
        Map<String, String> files = unzip(archive);
        Manifest manifest = yaml(files, "koifolio.yaml", new TypeReference<>() {
        });
        if (manifest == null || manifest.format() != VERSION) {
            throw new InvalidBackupException("koifolio.yaml: this app reads format " + VERSION);
        }

        Map<String, List<TransactionEntry>> transactions = new LinkedHashMap<>();
        List<CompanyEntry> companies = new ArrayList<>();
        for (Map.Entry<String, String> file : files.entrySet()) {
            String name = file.getKey();
            if (name.startsWith("transactions/") && name.endsWith(".jsonl")) {
                String key = name.substring("transactions/".length(), name.length() - ".jsonl".length());
                transactions.put(key, transactionLines(name, file.getValue()));
            } else if (name.startsWith("companies/") && name.endsWith(".md")) {
                companies.add(company(name, file.getValue()));
            }
        }
        return new Backup(manifest, list(files, "accounts.yaml", new TypeReference<>() {
        }), transactions, companies, list(files, "plans.yaml", new TypeReference<>() {
        }), list(files, "rules.yaml", new TypeReference<>() {
        }), files.containsKey("bonds.yaml") ? list(files, "bonds.yaml", new TypeReference<>() {
        }) : List.of());
    }

    private static Map<String, String> unzip(byte[] archive) {
        Map<String, String> files = new LinkedHashMap<>();
        long unpacked = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (entry.isDirectory()) {
                    continue;
                }
                byte[] content = zip.readNBytes((int) Math.min(MAX_UNPACKED_BYTES - unpacked + 1, Integer.MAX_VALUE));
                unpacked += content.length;
                if (unpacked > MAX_UNPACKED_BYTES) {
                    throw new InvalidBackupException("The archive is too large once unpacked");
                }
                files.put(entry.getName(), new String(content, StandardCharsets.UTF_8).replace("\r\n", "\n"));
            }
        } catch (IOException e) {
            throw new InvalidBackupException("This isn't a readable zip file");
        }
        if (files.isEmpty()) {
            throw new InvalidBackupException("This isn't a koifolio export");
        }
        return files;
    }

    private static <T> T yaml(Map<String, String> files, String name, TypeReference<T> type) {
        String text = files.get(name);
        if (text == null) {
            throw new InvalidBackupException(name + " is missing");
        }
        try {
            return YAML.readValue(text, type);
        } catch (JacksonException e) {
            throw new InvalidBackupException(name + ": " + e.getOriginalMessage());
        }
    }

    /** An empty list is written as {@code []}, but a hand-emptied file reads as nothing. */
    private static <T> List<T> list(Map<String, String> files, String name, TypeReference<List<T>> type) {
        List<T> entries = yaml(files, name, type);
        return entries == null ? List.of() : entries;
    }

    private static List<TransactionEntry> transactionLines(String name, String text) {
        List<TransactionEntry> entries = new ArrayList<>();
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].isBlank()) {
                continue;
            }
            try {
                entries.add(JSON.readValue(lines[i], TransactionEntry.class));
            } catch (JacksonException e) {
                throw new InvalidBackupException("%s line %d: %s".formatted(name, i + 1, e.getOriginalMessage()));
            }
        }
        return entries;
    }

    private static CompanyEntry company(String name, String text) {
        int end = text.indexOf(FRONTMATTER_END, FRONTMATTER.length() - 1);
        if (!text.startsWith(FRONTMATTER) || end < 0) {
            throw new InvalidBackupException(name + ": it must start with a --- block");
        }
        try {
            CompanyEntry company = YAML.readValue(text.substring(FRONTMATTER.length(), end + 1), CompanyEntry.class);
            if (company.ticker() == null) {
                throw new InvalidBackupException(name + ": ticker is missing");
            }
            return new CompanyEntry(company.ticker(), company.name(), company.sector(), company.risk(), company.thesis(),
                    company.tags(), company.listed(), text.substring(end + FRONTMATTER_END.length()));
        } catch (JacksonException e) {
            throw new InvalidBackupException(name + ": " + e.getOriginalMessage());
        }
    }
}
