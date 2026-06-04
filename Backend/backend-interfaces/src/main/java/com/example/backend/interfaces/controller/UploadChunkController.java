package com.example.backend.interfaces.controller;

import com.example.backend.application.service.ChunkUploadService;
import com.example.backend.common.Result;
import com.example.backend.interfaces.security.RequireRole;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadChunkController {

    private final ChunkUploadService chunkUploadService;

    @PostMapping("/init")
    @RequireRole({"ADMIN"})
    public Result<Map<String, Object>> initUpload(@RequestBody Map<String, Object> body) throws IOException {
        String fileName = (String) body.get("fileName");
        int totalChunks = (int) body.get("totalChunks");
        String fileHash = (String) body.get("fileHash");
        long fileSize = ((Number) body.getOrDefault("fileSize", 0)).longValue();
        return Result.success(chunkUploadService.initUpload(fileName, totalChunks, fileHash, fileSize));
    }

    @PostMapping("/chunk")
    @RequireRole({"ADMIN"})
    public Result<Map<String, String>> uploadChunk(@RequestParam("file") MultipartFile file,
                                                    @RequestParam("index") int index,
                                                    @RequestParam("uploadId") String uploadId) throws IOException {
        chunkUploadService.saveChunk(uploadId, index, file);
        return Result.success(Map.of("ok", "true", "index", String.valueOf(index)));
    }

    @PostMapping("/merge")
    @RequireRole({"ADMIN"})
    public Result<Map<String, Object>> mergeChunks(@RequestBody Map<String, String> body) throws IOException {
        String uploadId = body.get("uploadId");
        String fileHash = body.get("fileHash");
        return Result.success(chunkUploadService.mergeChunks(uploadId, fileHash));
    }
}
