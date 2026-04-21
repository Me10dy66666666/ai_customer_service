package com.example.backend.controller;

import com.example.backend.common.Result;
import com.example.backend.entity.Document;
import com.example.backend.service.KnowledgeBaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/kb")
@CrossOrigin(origins = "*") // Allow frontend access
public class KnowledgeBaseController {

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @PostMapping("/upload")
    public Result<Map<String, Object>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "category", defaultValue = "general") String category,
            @RequestParam(value = "force", defaultValue = "false") boolean force) throws Exception {
        Map<String, Object> data = knowledgeBaseService.uploadDocument(file, category, force);
        return Result.success(data);
    }

    @GetMapping("/list")
    public Result<List<Document>> listDocuments() {
        return Result.success(knowledgeBaseService.listDocuments());
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteDocument(@PathVariable Long id) {
        knowledgeBaseService.deleteDocument(id);
        return Result.success(null);
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        knowledgeBaseService.updateDocumentStatus(id, status);
        return Result.success(null);
    }
}
