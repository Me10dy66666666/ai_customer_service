package com.example.backend.infrastructure.ocr;

import com.example.backend.domain.shared.ocr.OcrPort;
import com.example.backend.domain.shared.ocr.OcrResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.*;

@Slf4j
@Component
@ConditionalOnProperty(value = "ocr.engine", havingValue = "aliyun")
public class AliyunOcrAdapter implements OcrPort {

    private static final String HINT_ACTION = "action";
    private static final String ACTION_RECOGNIZE_ADVANCED = "RecognizeAdvanced";
    private static final String API_VERSION = "2021-07-07";
    private static final int MAX_RETRIES = 3;
    private static final long BASE_RETRY_DELAY_MS = 1000;

    /** 永久性错误码——重试无效，应立即终止 */
    private static final Set<String> NON_RETRYABLE_CODES = Set.of(
            "InvalidAccessKeyId",
            "InvalidAccessKeySecret",
            "SignatureDoesNotMatch",
            "Forbidden.AccessKeyDisabled",
            "Forbidden.RAMUserNotAuthorized"
    );

    private final OcrProperties properties;
    private final ObjectMapper objectMapper;

    public AliyunOcrAdapter(OcrProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public OcrResult recognize(byte[] imageBytes, Map<String, Object> hints) {
        String action = resolveAction(hints);
        Exception lastException = null;
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                Map<String, String> queryParams = buildQueryParams(action);
                String queryString = buildSortedQueryString(queryParams);
                String signature = sign(queryString);
                queryString = queryString + "&Signature=" + percentEncode(signature);

                String url = "https://" + properties.getAliyun().getEndpoint() + "/?" + queryString;
                String responseJson = postBinary(url, imageBytes);
                return parseResponse(responseJson);
            } catch (Exception e) {
                lastException = e;
                if (isNonRetryableError(e)) {
                    log.error("Aliyun OCR [{}] permanent error, aborting retries: {}", action, e.getMessage());
                    break;
                }
                if (attempt < MAX_RETRIES - 1) {
                    long delay = BASE_RETRY_DELAY_MS * (1L << attempt);
                    log.warn("Aliyun OCR [{}] attempt {}/{} failed: {}, retrying in {}ms",
                            action, attempt + 1, MAX_RETRIES, e.getMessage(), delay);
                    try { Thread.sleep(delay); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
            }
        }
        log.error("Aliyun OCR [{}] failed after {} attempts: {}", action, MAX_RETRIES,
                lastException != null ? lastException.getMessage() : "unknown");
        log.warn("Aliyun OCR [{}] returning empty fallback — downstream will receive no text and no blocks. "
                + "Check network, API credentials, and image quality.", action);
        OcrResult fallback = new OcrResult();
        fallback.setText("");
        fallback.setConfidence(0);
        fallback.setLanguage(properties.getDefaultLanguage());
        return fallback;
    }

    @Override
    public String engineName() {
        return "AliyunOCR";
    }

    /**
     * 判断异常是否为永久性错误（如 AccessKey 无效），此类错误重试无效。
     */
    private boolean isNonRetryableError(Exception e) {
        String msg = e.getMessage();
        if (msg == null) {
            return false;
        }
        for (String code : NON_RETRYABLE_CODES) {
            if (msg.contains(code)) {
                return true;
            }
        }
        return false;
    }

    private String resolveAction(Map<String, Object> hints) {
        if (hints != null && hints.containsKey(HINT_ACTION)) {
            return String.valueOf(hints.get(HINT_ACTION));
        }
        return properties.getAliyun().getApi().getRecognizeAdvanced();
    }

    private Map<String, String> buildQueryParams(String action) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("Action", action);
        params.put("Version", API_VERSION);
        params.put("Format", "JSON");
        params.put("SignatureMethod", "HMAC-SHA1");
        params.put("SignatureVersion", "1.0");
        params.put("SignatureNonce", UUID.randomUUID().toString());

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        params.put("Timestamp", sdf.format(new Date()));

        params.put("AccessKeyId", properties.getAliyun().getAccessKeyId());

        if (ACTION_RECOGNIZE_ADVANCED.equals(action)) {
            params.put("OutputTable", "false");
            params.put("Paragraph", "true");
        }

        return params;
    }

    private String buildSortedQueryString(Map<String, String> params) {
        List<String> sortedKeys = new ArrayList<>(params.keySet());
        Collections.sort(sortedKeys);
        StringBuilder sb = new StringBuilder();
        for (String key : sortedKeys) {
            if (!sb.isEmpty()) sb.append("&");
            sb.append(percentEncode(key)).append("=").append(percentEncode(params.get(key)));
        }
        return sb.toString();
    }

    private String sign(String sortedQueryString) throws NoSuchAlgorithmException, InvalidKeyException, UnsupportedEncodingException {
        OcrProperties.Aliyun aliyun = properties.getAliyun();
        String stringToSign = "POST" + "&"
                + percentEncode("/") + "&"
                + percentEncode(sortedQueryString);

        Mac mac = Mac.getInstance("HmacSHA1");
        SecretKeySpec secretKeySpec = new SecretKeySpec(
                (aliyun.getAccessKeySecret() + "&").getBytes(StandardCharsets.UTF_8), "HmacSHA1");
        mac.init(secretKeySpec);
        byte[] hash = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }

    private String percentEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name())
                    .replace("+", "%20")
                    .replace("*", "%2A")
                    .replace("%7E", "~");
        } catch (Exception e) {
            return value;
        }
    }

    private OcrResult parseResponse(String json) {
        OcrResult result = new OcrResult();
        try {
            JsonNode root = objectMapper.readTree(json);
            if (isSuccessResponse(root)) {
                parseDataContent(root, result);
            } else if (isErrorResponse(root)) {
                handleApiError(root);
            } else {
                log.warn("Aliyun OCR response has neither Data nor Code, raw (first 500 chars): {}",
                        json.substring(0, Math.min(500, json.length())));
            }
        } catch (Exception e) {
            log.error("Failed to parse OCR response: {}, raw (first 500 chars): {}",
                    e.getMessage(), json.substring(0, Math.min(500, json.length())));
            throw new RuntimeException("Failed to parse OCR response: " + e.getMessage(), e);
        }
        return result;
    }

    private boolean isSuccessResponse(JsonNode root) {
        return root.has("Data") && root.get("Data") != null;
    }

    private boolean isErrorResponse(JsonNode root) {
        return root.has("Code") && root.get("Code") != null;
    }

    private void parseDataContent(JsonNode root, OcrResult result) throws Exception {
        JsonNode dataObj = extractDataObject(root);
        String fullContent = dataObj.has("content") ? dataObj.get("content").asText() : "";
        result.setText(fullContent);
        parseWordBlocks(dataObj, result);
        result.setLanguage(properties.getDefaultLanguage());
        fillTextFromBlocksIfBlank(result);
        log.debug("Aliyun OCR parsed: contentLength={}, wordBlockCount={}, finalTextLen={}",
                fullContent.length(), result.getBlocks() != null ? result.getBlocks().size() : 0,
                result.getText() != null ? result.getText().length() : 0);
    }

    private JsonNode extractDataObject(JsonNode root) throws Exception {
        JsonNode dataNode = root.get("Data");
        String dataStr = dataNode.isTextual() ? dataNode.asText() : dataNode.toString();
        return objectMapper.readTree(dataStr);
    }

    private void parseWordBlocks(JsonNode dataObj, OcrResult result) {
        if (!dataObj.has("prism_wordsInfo")) {
            result.setBlocks(Collections.emptyList());
            result.setConfidence(0.99);
            return;
        }
        double totalConfidence = 0;
        List<OcrResult.OcrBlock> blocks = new ArrayList<>();
        for (JsonNode wordNode : dataObj.get("prism_wordsInfo")) {
            OcrResult.OcrBlock block = parseSingleWordBlock(wordNode);
            totalConfidence += block.getConfidence();
            blocks.add(block);
        }
        result.setBlocks(blocks);
        result.setConfidence(blocks.isEmpty() ? 0.99 : totalConfidence / blocks.size());
    }

    private OcrResult.OcrBlock parseSingleWordBlock(JsonNode wordNode) {
        String text = wordNode.has("word") ? wordNode.get("word").asText() : "";
        double probability = wordNode.has("prob")
                ? wordNode.get("prob").asDouble() / 100.0 : 0.99;
        OcrResult.OcrBlock block = new OcrResult.OcrBlock(text, probability);
        applyBlockPosition(block, wordNode);
        return block;
    }

    private void applyBlockPosition(OcrResult.OcrBlock block, JsonNode wordNode) {
        if (!wordNode.has("pos")) {
            return;
        }
        JsonNode pos = wordNode.get("pos");
        if (pos.isArray() && pos.size() >= 4) {
            block.setX(pos.get(0).get("x").asInt());
            block.setY(pos.get(0).get("y").asInt());
            block.setWidth(pos.get(2).get("x").asInt() - pos.get(0).get("x").asInt());
            block.setHeight(pos.get(2).get("y").asInt() - pos.get(0).get("y").asInt());
        }
    }

    private void fillTextFromBlocksIfBlank(OcrResult result) {
        if (!result.getText().isBlank() || result.getBlocks().isEmpty()) {
            return;
        }
        StringBuilder fullText = new StringBuilder();
        for (OcrResult.OcrBlock block : result.getBlocks()) {
            fullText.append(block.getText()).append("\n");
        }
        result.setText(fullText.toString().trim());
    }

    private void handleApiError(JsonNode root) {
        String code = root.get("Code").asText();
        String message = root.has("Message") ? root.get("Message").asText() : "";
        log.error("Aliyun OCR API error: code={}, message={}", code, message);
        throw new RuntimeException("Aliyun OCR API error: " + code + " - " + message);
    }

    private String postBinary(String urlStr, byte[] imageBytes) throws IOException, URISyntaxException {
        HttpURLConnection conn = null;
        try {
            URI uri = new URI(urlStr);
            conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(properties.getTimeoutMs());
            conn.setReadTimeout(properties.getTimeoutMs());
            conn.setRequestProperty("Content-Type", "application/octet-stream");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(imageBytes);
                os.flush();
            }

            int statusCode = conn.getResponseCode();
            java.io.InputStream inputStream = statusCode >= 200 && statusCode < 300
                    ? conn.getInputStream() : conn.getErrorStream();
            if (inputStream == null) {
                throw new RuntimeException("Aliyun OCR returned status " + statusCode + " with no body");
            }
            byte[] responseBytes = inputStream.readAllBytes();
            String responseStr = new String(responseBytes, StandardCharsets.UTF_8);
            if (statusCode >= 400) {
                log.error("Aliyun OCR HTTP {} : {}", statusCode, responseStr);
                throw new RuntimeException("Aliyun OCR HTTP " + statusCode + ": " + responseStr);
            }
            return responseStr;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
