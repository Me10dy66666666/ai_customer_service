package com.example.backend.infrastructure.messaging;

import com.example.backend.domain.knowledge.model.KnowledgeDocument;
import com.example.backend.domain.knowledge.model.OcrSegment;
import com.example.backend.domain.knowledge.repository.KnowledgeDocumentRepository;
import com.example.backend.domain.knowledge.repository.OcrSegmentRepository;
import com.example.backend.domain.shared.ocr.OcrPort;
import com.example.backend.domain.shared.ocr.OcrResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(value = "spring.rabbitmq.host")
public class OcrConsumer {

    private final OcrPort ocrPort;
    private final KnowledgeDocumentRepository documentRepository;
    private final OcrSegmentRepository ocrSegmentRepository;
    private final ObjectMapper objectMapper;

    @Value("${ocr.confidence-threshold:0.85}")
    private double confidenceThreshold;

    public OcrConsumer(OcrPort ocrPort,
                       KnowledgeDocumentRepository documentRepository,
                       OcrSegmentRepository ocrSegmentRepository,
                       ObjectMapper objectMapper) {
        this.ocrPort = ocrPort;
        this.documentRepository = documentRepository;
        this.ocrSegmentRepository = ocrSegmentRepository;
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("unchecked")
    @RabbitListener(queues = RabbitMqMessageBusAdapter.RabbitMqDeclarations.OCR_QUEUE)
    public void onMessage(byte[] raw) {
        Long documentId = null;
        try {
            Map<String, Object> payload = objectMapper.readValue(raw, Map.class);
            String base64Image = (String) payload.get("imageBase64");
            byte[] imageBytes = Base64.getDecoder().decode(base64Image);

            documentId = toLong(payload.get("documentId"));
            Integer startIndex = payload.containsKey("segmentIndex")
                    ? ((Number) payload.get("segmentIndex")).intValue() : 1;

            Map<String, Object> hints = (Map<String, Object>) payload.getOrDefault("hints", Map.of());

            OcrResult result = ocrPort.recognize(imageBytes, hints);
            log.info("OCR done: docId={}, confidence={}, textLen={}, needsReview={}",
                    documentId, result.getConfidence(),
                    result.getText() != null ? result.getText().length() : 0,
                    result.needsManualReview());

            if (result.needsManualReview()) {
                log.warn("OCR low confidence ({}), docId={} marked for manual review",
                        result.getConfidence(), documentId);
            }

            if (documentId != null) {
                writeSegments(documentId, startIndex, result);
                updateDocumentStatus(documentId);
            }

        } catch (Exception e) {
            log.error("OCR consumer error for docId={}: {}", documentId, e.getMessage(), e);
            if (documentId != null) {
                try {
                    KnowledgeDocument doc = documentRepository.findById(documentId).orElse(null);
                    if (doc != null) {
                        doc.setStatus(KnowledgeDocument.STATUS_PENDING_REVIEW);
                        documentRepository.save(doc);
                    }
                } catch (Exception inner) {
                    log.error("Failed to update doc {} status after OCR error", documentId, inner);
                }
            }
        }
    }

    private void writeSegments(Long documentId, int startIndex, OcrResult result) {
        List<OcrSegment> segments = new ArrayList<>();
        int index = startIndex;

        if (result.getBlocks() != null && !result.getBlocks().isEmpty()) {
            for (OcrResult.OcrBlock block : result.getBlocks()) {
                segments.add(buildSegment(documentId, index++, block));
            }
        } else if (result.getText() != null && !result.getText().isBlank()) {
            OcrSegment seg = new OcrSegment();
            seg.setDocumentId(documentId);
            seg.setSegmentIndex(index);
            seg.setOcrText(result.getText());
            seg.setConfidence(result.getConfidence());
            seg.setStatus(result.getConfidence() >= confidenceThreshold
                    ? OcrSegment.STATUS_CONFIRMED : OcrSegment.STATUS_UNCERTAIN);
            segments.add(seg);
        }

        if (!segments.isEmpty()) {
            ocrSegmentRepository.saveBatch(segments);
            log.info("Wrote {} OCR segments for docId={}", segments.size(), documentId);
        }
    }

    private OcrSegment buildSegment(Long documentId, int index, OcrResult.OcrBlock block) {
        OcrSegment seg = new OcrSegment();
        seg.setDocumentId(documentId);
        seg.setSegmentIndex(index);
        seg.setOcrText(block.getText());
        seg.setConfidence(block.getConfidence());
        seg.setBoundingBox(String.format("{\"x\":%d,\"y\":%d,\"w\":%d,\"h\":%d}",
                block.getX(), block.getY(), block.getWidth(), block.getHeight()));
        seg.setStatus(OcrSegment.STATUS_PENDING);
        return seg;
    }

    private void updateDocumentStatus(Long documentId) {
        KnowledgeDocument doc = documentRepository.findById(documentId).orElse(null);
        if (doc != null) {
            doc.setStatus(KnowledgeDocument.STATUS_PENDING_REVIEW);
            documentRepository.save(doc);
        }
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        try { return Long.parseLong(value.toString()); } catch (NumberFormatException e) { return null; }
    }
}
