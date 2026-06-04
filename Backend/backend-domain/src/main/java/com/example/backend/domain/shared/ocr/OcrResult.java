package com.example.backend.domain.shared.ocr;

import java.util.List;
import java.util.Map;

/**
 * OCR 识别结果领域对象。
 */
public class OcrResult {

    /** 识别出的完整文本 */
    private String text;

    /** 综合置信度 (0.0 ~ 1.0) */
    private double confidence;

    /** 检测到的语言 */
    private String language;

    /** 识别的具体字块列表 */
    private List<OcrBlock> blocks;

    /** 扩展字段（适配不同引擎的私有字段） */
    private Map<String, Object> extra;

    public OcrResult() {}

    public OcrResult(String text, double confidence) {
        this.text = text;
        this.confidence = confidence;
    }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public List<OcrBlock> getBlocks() { return blocks; }
    public void setBlocks(List<OcrBlock> blocks) { this.blocks = blocks; }

    public Map<String, Object> getExtra() { return extra; }
    public void setExtra(Map<String, Object> extra) { this.extra = extra; }

    public boolean isHighConfidence() {
        return confidence >= 0.95;
    }

    public boolean needsManualReview() {
        return confidence < 0.95;
    }

    /**
     * 单个识别字块。
     */
    public static class OcrBlock {
        private String text;
        private double confidence;
        private int x, y, width, height;

        public OcrBlock() {}

        public OcrBlock(String text, double confidence) {
            this.text = text;
            this.confidence = confidence;
        }

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }

        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }

        public int getX() { return x; }
        public void setX(int x) { this.x = x; }

        public int getY() { return y; }
        public void setY(int y) { this.y = y; }

        public int getWidth() { return width; }
        public void setWidth(int width) { this.width = width; }

        public int getHeight() { return height; }
        public void setHeight(int height) { this.height = height; }
    }
}
