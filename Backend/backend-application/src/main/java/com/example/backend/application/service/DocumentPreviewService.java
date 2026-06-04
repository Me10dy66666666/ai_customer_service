package com.example.backend.application.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class DocumentPreviewService {

    private static final Set<String> CONVERTIBLE_EXTENSIONS = Set.of(".docx", ".xlsx", ".pptx");
    private static final Set<String> NATIVE_PDF_EXTENSIONS = Set.of(".pdf");
    private static final int CONVERT_TIMEOUT_SECONDS = 60;

    private final Path previewDir;
    private final String libreofficeMode;
    private final String dockerContainerName;
    private final String containerUploadsDir;

    public DocumentPreviewService(
            @Value("${document.preview.libreoffice.mode:native}") String libreofficeMode,
            @Value("${document.preview.libreoffice.docker.container-name:ai-customer-service-libreoffice}")
                    String dockerContainerName,
            @Value("${document.preview.libreoffice.docker.container-uploads-dir:/app/uploads}")
                    String containerUploadsDir) {
        this.libreofficeMode = libreofficeMode;
        this.dockerContainerName = dockerContainerName;
        this.containerUploadsDir = containerUploadsDir;
        this.previewDir = Paths.get(System.getProperty("user.dir"), "uploads", "previews");
        ensurePreviewDirectory();
    }

    public String generatePreviewPdf(Long documentId, String originalFilePath, String fileType) {
        if (originalFilePath == null || originalFilePath.isBlank()) {
            log.warn("Document {} has no original file, skip preview generation", documentId);
            return null;
        }

        File originalFile = new File(originalFilePath);
        if (!originalFile.exists()) {
            log.warn("Original file not found for document {}: {}", documentId, originalFilePath);
            return null;
        }

        String ext = extractExtension(originalFilePath);
        String lowerExt = ext.toLowerCase();

        if (NATIVE_PDF_EXTENSIONS.contains(lowerExt)) {
            return copyPdfToPreviewDir(documentId, originalFile);
        }

        if (CONVERTIBLE_EXTENSIONS.contains(lowerExt)) {
            return convertToPdf(documentId, originalFile);
        }

        log.info("Document {} type '{}' does not need PDF conversion", documentId, lowerExt);
        return null;
    }

    private String copyPdfToPreviewDir(Long documentId, File source) {
        try {
            Path targetPath = previewDir.resolve("doc-" + documentId + ".pdf");
            Files.copy(source.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("PDF copied as preview for document {}: {}", documentId, targetPath);
            return targetPath.toString();
        } catch (IOException e) {
            log.error("Failed to copy PDF preview for document {}: {}", documentId, e.getMessage());
            return null;
        }
    }

    private String convertToPdf(Long documentId, File source) {
        ensurePreviewDirectory();

        if ("docker_exec".equalsIgnoreCase(libreofficeMode)) {
            return convertViaDockerExec(documentId, source);
        }
        return convertViaNative(documentId, source);
    }

    private String convertViaNative(Long documentId, File source) {
        String libreofficeExecutable = detectNativeLibreOffice();
        if (libreofficeExecutable == null) {
            log.warn("LibreOffice not found, cannot convert document {} to PDF", documentId);
            return null;
        }

        List<String> command = new ArrayList<>();
        command.add(libreofficeExecutable);
        command.add("--headless");
        command.add("--convert-to");
        command.add("pdf");
        command.add("--outdir");
        command.add(previewDir.toString());
        command.add(source.getAbsolutePath());

        if (!executeCommand(command, documentId)) {
            return null;
        }

        return moveConvertedFile(documentId, source);
    }

    private String convertViaDockerExec(Long documentId, File source) {
        String hostUploadsDir = previewDir.getParent().toString();
        String sourceAbsPath = source.getAbsolutePath();

        String containerSourcePath = mapToContainerPath(sourceAbsPath, hostUploadsDir);
        String containerOutdir = mapToContainerPath(previewDir.toString(), hostUploadsDir);

        if (containerSourcePath == null || containerOutdir == null) {
            log.error("Failed to map host path to container path for document {}", documentId);
            return null;
        }

        List<String> command = new ArrayList<>();
        command.add("docker");
        command.add("exec");
        command.add(dockerContainerName);
        command.add("libreoffice");
        command.add("--headless");
        command.add("--convert-to");
        command.add("pdf");
        command.add("--outdir");
        command.add(containerOutdir);
        command.add(containerSourcePath);

        log.info("Docker exec command for document {}: {}", documentId, String.join(" ", command));

        if (!executeCommand(command, documentId)) {
            return null;
        }

        return moveConvertedFile(documentId, source);
    }

    private String mapToContainerPath(String hostPath, String hostUploadsDir) {
        Path hostUploads = Paths.get(hostUploadsDir).toAbsolutePath().normalize();
        Path filePath = Paths.get(hostPath).toAbsolutePath().normalize();

        if (!filePath.startsWith(hostUploads)) {
            log.error("File path {} is not under uploads directory {}", filePath, hostUploads);
            return null;
        }

        Path relativePath = hostUploads.relativize(filePath);
        return Paths.get(containerUploadsDir).resolve(relativePath).toString().replace('\\', '/');
    }

    private boolean executeCommand(List<String> command, Long documentId) {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        try {
            Process process = pb.start();
            boolean finished = process.waitFor(CONVERT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                log.error("LibreOffice conversion timed out for document {}", documentId);
                return false;
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.error("LibreOffice conversion failed for document {} with exit code {}", documentId, exitCode);
                return false;
            }
            return true;

        } catch (IOException e) {
            log.error("LibreOffice I/O error for document {}: {}", documentId, e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("LibreOffice conversion interrupted for document {}", documentId);
            return false;
        }
    }

    private String moveConvertedFile(Long documentId, File source) {
        String sourceFilename = source.getName();
        int dotIndex = sourceFilename.lastIndexOf('.');
        String baseName = dotIndex > 0 ? sourceFilename.substring(0, dotIndex) : sourceFilename;
        Path convertedPdf = previewDir.resolve(baseName + ".pdf");

        if (!Files.exists(convertedPdf)) {
            log.error("LibreOffice output not found for document {}: expected {}", documentId, convertedPdf);
            return null;
        }

        Path targetPath = previewDir.resolve("doc-" + documentId + ".pdf");
        try {
            Files.move(convertedPdf, targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Document {} converted to PDF: {}", documentId, targetPath);
            return targetPath.toString();
        } catch (IOException e) {
            log.error("Failed to move converted PDF for document {}: {}", documentId, e.getMessage());
            return null;
        }
    }

    private String detectNativeLibreOffice() {
        String[] candidates = {"soffice", "libreoffice"};

        for (String candidate : candidates) {
            try {
                ProcessBuilder pb = new ProcessBuilder(candidate, "--version");
                pb.redirectErrorStream(true);
                Process process = pb.start();
                boolean finished = process.waitFor(5, TimeUnit.SECONDS);
                if (finished && process.exitValue() == 0) {
                    log.info("LibreOffice detected via: {}", candidate);
                    return candidate;
                }
            } catch (Exception e) {
                log.debug("LibreOffice candidate '{}' not available: {}", candidate, e.getMessage());
            }
        }
        return null;
    }

    public void deletePreviewPdf(Long documentId) {
        Path previewPath = previewDir.resolve("doc-" + documentId + ".pdf");
        try {
            Files.deleteIfExists(previewPath);
        } catch (IOException e) {
            log.warn("Failed to delete preview PDF for document {}: {}", documentId, e.getMessage());
        }
    }

    private String extractExtension(String filePath) {
        int dot = filePath.lastIndexOf('.');
        return dot > 0 ? filePath.substring(dot) : "";
    }

    private void ensurePreviewDirectory() {
        try {
            Files.createDirectories(previewDir);
        } catch (IOException e) {
            log.error("Failed to create preview directory: {}", e.getMessage());
        }
    }
}
