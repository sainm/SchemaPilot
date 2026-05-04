package org.sainm.schemapilot.input;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.sainm.schemapilot.common.api.BadRequestException;
import org.sainm.schemapilot.common.api.NotFoundException;
import org.sainm.schemapilot.project.ProjectRepository;

public class InputImportService {

    private static final long MAX_SINGLE_FILE_BYTES = 10L * 1024 * 1024;
    private static final long MAX_BATCH_BYTES = 50L * 1024 * 1024;
    private static final int MAX_ZIP_ENTRIES = 2_000;

    private final ProjectRepository projectRepository;
    private final InputRepository inputRepository;

    public InputImportService(ProjectRepository projectRepository, InputRepository inputRepository) {
        this.projectRepository = projectRepository;
        this.inputRepository = inputRepository;
    }

    public InputImportResult importManualSql(ManualSqlInputCommand command) {
        validateManualSql(command);
        validateProjectAndSource(command);

        Instant now = Instant.now();
        InputBatch batch = new InputBatch(
                UUID.randomUUID(),
                command.projectId(),
                command.sourceProjectId(),
                InputBatchStatus.IMPORTED,
                now);
        String normalizedName = normalizeName(command.name());
        InputSource source = new InputSource(
                UUID.randomUUID(),
                batch.id(),
                command.projectId(),
                command.sourceProjectId(),
                InputSourceType.MANUAL_SQL,
                normalizedName,
                normalizedName,
                sha256(command.sql()),
                command.sql().getBytes(StandardCharsets.UTF_8).length,
                StandardCharsets.UTF_8.name(),
                command.sql(),
                now);

        inputRepository.saveBatch(batch);
        inputRepository.saveSource(source);
        return new InputImportResult(batch, source);
    }

    public InputBatchImportResult importFiles(FileInputCommand command) {
        validateProjectAndSource(command.projectId(), command.sourceProjectId());
        if (command.files().isEmpty()) {
            throw new BadRequestException("At least one file is required");
        }
        List<InputFile> files = command.type() == InputSourceType.ZIP
                ? unzip(command.files().getFirst())
                : command.files();
        if (files.isEmpty()) {
            throw new BadRequestException(command.type() == InputSourceType.ZIP
                    ? "Zip contains no SQL files"
                    : "At least one file is required");
        }
        validateFiles(files);

        Instant now = Instant.now();
        InputBatch batch = new InputBatch(
                UUID.randomUUID(),
                command.projectId(),
                command.sourceProjectId(),
                InputBatchStatus.IMPORTED,
                now);
        inputRepository.saveBatch(batch);
        List<InputSource> sources = new ArrayList<>();
        for (InputFile file : files) {
            String sql = decodeUtf8(file);
            InputSource source = new InputSource(
                    UUID.randomUUID(),
                    batch.id(),
                    command.projectId(),
                    command.sourceProjectId(),
                    command.type() == InputSourceType.ZIP ? InputSourceType.SQL_FILE : command.type(),
                    fileName(file.relativePath()),
                    normalizeRelativePath(file.relativePath()),
                    sha256(sql),
                    file.content().length,
                    StandardCharsets.UTF_8.name(),
                    sql,
                    now);
            inputRepository.saveSource(source);
            sources.add(source);
        }
        return new InputBatchImportResult(batch, sources);
    }

    private void validateManualSql(ManualSqlInputCommand command) {
        if (command.sql() == null || command.sql().isBlank()) {
            throw new BadRequestException("SQL must not be blank");
        }
    }

    private void validateProjectAndSource(ManualSqlInputCommand command) {
        validateProjectAndSource(command.projectId(), command.sourceProjectId());
    }

    private void validateProjectAndSource(UUID projectId, UUID sourceProjectId) {
        projectRepository.findProject(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found"));
        boolean sourceProjectExists = projectRepository.findSourceProjects(projectId).stream()
                .anyMatch(sourceProject -> sourceProject.id().equals(sourceProjectId));
        if (!sourceProjectExists) {
            throw new NotFoundException("Source project not found");
        }
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            return "manual.sql";
        }
        return name.trim();
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private List<InputFile> unzip(InputFile zipFile) {
        if (zipFile.content().length > MAX_SINGLE_FILE_BYTES) {
            throw new BadRequestException("Zip file is too large");
        }
        List<InputFile> files = new ArrayList<>();
        int entries = 0;
        long totalBytes = 0;
        try (ZipInputStream zipInput = new ZipInputStream(new ByteArrayInputStream(zipFile.content()))) {
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                if (++entries > MAX_ZIP_ENTRIES) {
                    throw new BadRequestException("Zip contains too many files");
                }
                String path = normalizeRelativePath(entry.getName());
                byte[] content = readZipEntry(path, zipInput);
                totalBytes += content.length;
                if (totalBytes > MAX_BATCH_BYTES) {
                    throw new BadRequestException("Zip uncompressed size is too large");
                }
                if (zipFile.content().length > 0 && totalBytes / zipFile.content().length > 100) {
                    throw new BadRequestException("Zip expansion ratio is too high");
                }
                files.add(new InputFile(path, content));
            }
        } catch (IOException exception) {
            throw new BadRequestException("Invalid zip file");
        }
        return files;
    }

    private byte[] readZipEntry(String path, ZipInputStream zipInput) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = zipInput.read(buffer)) >= 0) {
            output.write(buffer, 0, read);
            if (output.size() > MAX_SINGLE_FILE_BYTES) {
                throw new BadRequestException("Zip entry is too large: " + path);
            }
        }
        return output.toByteArray();
    }

    private void validateFiles(List<InputFile> files) {
        if (files.isEmpty()) {
            throw new BadRequestException("At least one file is required");
        }
        long batchBytes = 0;
        for (InputFile file : files) {
            String path = normalizeRelativePath(file.relativePath());
            if (!isSqlFile(path)) {
                throw new BadRequestException("Unsupported file type: " + path);
            }
            if (file.content().length == 0) {
                throw new BadRequestException("Empty file is not allowed: " + path);
            }
            if (file.content().length > MAX_SINGLE_FILE_BYTES) {
                throw new BadRequestException("File is too large: " + path);
            }
            batchBytes += file.content().length;
            if (batchBytes > MAX_BATCH_BYTES) {
                throw new BadRequestException("Input batch is too large");
            }
        }
    }

    private String decodeUtf8(InputFile file) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(file.content()))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new BadRequestException("Only UTF-8 SQL files are currently supported: " + file.relativePath());
        }
    }

    private boolean isSqlFile(String path) {
        String lower = path.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".sql")
                || lower.endsWith(".ddl")
                || lower.endsWith(".pls")
                || lower.endsWith(".pks")
                || lower.endsWith(".pkb");
    }

    private String normalizeRelativePath(String path) {
        if (path == null || path.isBlank()) {
            throw new BadRequestException("File path is required");
        }
        String normalized = path.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains("../") || normalized.equals("..") || normalized.contains(":/")) {
            throw new BadRequestException("Illegal file path: " + path);
        }
        return normalized;
    }

    private String fileName(String path) {
        String normalized = normalizeRelativePath(path);
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }
}
