package com.example.backend.domain.shared.ocr;

/**
 * OCR 文字识别抽象端口。
 * 独立于此项目，可作为公共能力被「聊天图片识别」
 * 和「知识库文档解析」两个场景复用。
 */
public interface OcrPort {

    /**
     * 识别图片中的文字。
     *
     * @param imageBytes 图片字节数组
     * @param hints      提示参数（如语言、区域），可空
     * @return OcrResult 结构化识别结果
     */
    OcrResult recognize(byte[] imageBytes, java.util.Map<String, Object> hints);

    /**
     * 当前 OCR 引擎标识。
     */
    String engineName();
}
