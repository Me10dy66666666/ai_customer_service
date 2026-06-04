package com.example.backend.application.service;

import com.example.backend.common.config.UploadProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class ChunkUploadService {

    private final UploadProperties uploadProperties;
    private final ObjectMapper objectMapper;
    private final Path chunkDir;

    public ChunkUploadService(UploadProperties uploadProperties, ObjectMapper objectMapper) {
        this.uploadProperties = uploadProperties;
        this.objectMapper = objectMapper;
        this.chunkDir = Paths.get(uploadProperties.getTempDir()).toAbsolutePath();
        try { Files.createDirectories(chunkDir); } catch (IOException ignored) {}
    }

    public Map<String, Object> initUpload(String fileName, int totalChunks, String fileHash, long fileSize)
            throws IOException {
        String uploadId = UUID.randomUUID().toString().replace("-", "");
        Path uploadDir = chunkDir.resolve(uploadId);
        Files.createDirectories(uploadDir);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("fileName", fileName);
        meta.put("totalChunks", totalChunks);
        meta.put("fileHash", fileHash);
        meta.put("fileSize", fileSize);
        meta.put("createdAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        Path metaFile = uploadDir.resolve(".meta");
        Files.writeString(metaFile, objectMapper.writeValueAsString(meta));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uploadId", uploadId);
        result.put("uploadedChunks", List.of());
        return result;
    }

    public void saveChunk(String uploadId, int index, MultipartFile chunk) throws IOException {
        Path uploadDir = chunkDir.resolve(uploadId);
        if (!Files.exists(uploadDir)) {
            throw new IllegalArgumentException("Upload session not found: " + uploadId);
        }
        Path chunkFile = uploadDir.resolve(String.format("%03d", index));
        Files.createDirectories(uploadDir);
        chunk.transferTo(chunkFile.toFile());
    }

    public Map<String, Object> mergeChunks(String uploadId, String fileHash) throws IOException {
        Path uploadDir = chunkDir.resolve(uploadId);
        if (!Files.exists(uploadDir)) {
            throw new IllegalArgumentException("Upload session not found: " + uploadId);
        }

        Path metaFile = uploadDir.resolve(".meta");
        if (!Files.exists(metaFile)) {
            throw new IllegalStateException("Metadata not found for upload: " + uploadId);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> meta = objectMapper.readValue(Files.readString(metaFile), Map.class);
        int totalChunks = (int) meta.get("totalChunks");
        String originalFileName = (String) meta.get("fileName");

        Path mergedPath = Paths.get(uploadProperties.getTempDir().replace("chunks", ""), uploadId + "-" + originalFileName);
        Files.createDirectories(mergedPath.getParent());

        try (var output = Files.newOutputStream(mergedPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            for (int i = 0; i < totalChunks; i++) {
                Path chunkFile = uploadDir.resolve(String.format("%03d", i));
                if (!Files.exists(chunkFile)) {
                    throw new IllegalStateException("Chunk " + i + " missing for upload: " + uploadId);
                }
                Files.copy(chunkFile, output);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("filePath", mergedPath.toAbsolutePath().toString());
        result.put("fileName", originalFileName);
        result.put("fileSize", Files.size(mergedPath));
        return result;
    }
}
