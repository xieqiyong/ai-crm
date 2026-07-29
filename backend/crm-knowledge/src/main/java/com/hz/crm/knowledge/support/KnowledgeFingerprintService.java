package com.hz.crm.knowledge.support;

import com.hz.crm.knowledge.client.KnowledgeElasticsearchClient;
import com.hz.crm.knowledge.client.KnowledgeEmbeddingClient;
import com.hz.crm.knowledge.client.KnowledgeMilvusClient;
import com.hz.crm.knowledge.domain.KnowledgeDocumentEntity;
import com.hz.crm.knowledge.dto.KnowledgeDocumentRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class KnowledgeFingerprintService {

    @Autowired
    private KnowledgeTextSplitter knowledgeTextSplitter;

    @Autowired
    private KnowledgeEmbeddingClient knowledgeEmbeddingClient;

    @Autowired
    private KnowledgeElasticsearchClient knowledgeElasticsearchClient;

    @Autowired
    private KnowledgeMilvusClient knowledgeMilvusClient;

    public String resolveSourceKey(KnowledgeDocumentRequest request, Long documentId) {
        if (request != null && StringUtils.hasText(request.getSourceKey())) {
            return fitSourceKey(normalizeSourceKey(request.getSourceKey()));
        }
        if (request != null && StringUtils.hasText(request.getSourceUrl())) {
            return fitSourceKey("URL:" + normalizeSourceKey(request.getSourceUrl()));
        }
        if (request != null && StringUtils.hasText(request.getObjectKey())) {
            return fitSourceKey("OBJECT:" + normalizeSourceKey(request.getObjectKey()));
        }
        return fitSourceKey("MANUAL:" + documentId);
    }

    public String normalizedContentHash(String content) {
        return sha256(normalizeContent(content));
    }

    public String buildFingerprint(KnowledgeDocumentEntity document) {
        StringBuilder builder = new StringBuilder();
        append(builder, "normalizedContentHash", document.getNormalizedContentHash());
        append(builder, "title", document.getTitle());
        append(builder, "sourceType", document.getSourceType());
        append(builder, "category", document.getCategory());
        append(builder, "tags", document.getTags());
        append(builder, "sourceUrl", document.getSourceUrl());
        append(builder, "splitter", knowledgeTextSplitter.profile());
        append(builder, "embeddingEnabled", knowledgeEmbeddingClient.enabled());
        append(builder, "embeddingModel", knowledgeEmbeddingClient.enabled() ? knowledgeEmbeddingClient.model() : "");
        append(builder, "embeddingDimensions", knowledgeEmbeddingClient.dimensions());
        append(builder, "elasticsearchEnabled", knowledgeElasticsearchClient.enabled());
        append(builder, "milvusEnabled", knowledgeMilvusClient.enabled());
        return sha256(builder.toString());
    }

    public String chunkProfileHash() {
        StringBuilder builder = new StringBuilder();
        append(builder, "splitter", knowledgeTextSplitter.profile());
        append(builder, "embeddingEnabled", knowledgeEmbeddingClient.enabled());
        append(builder, "embeddingModel", knowledgeEmbeddingClient.enabled() ? knowledgeEmbeddingClient.model() : "");
        append(builder, "embeddingDimensions", knowledgeEmbeddingClient.dimensions());
        return sha256(builder.toString());
    }

    public String sha256Bytes(byte[] bytes) {
        return sha256(bytes == null ? new byte[0] : bytes);
    }

    public String sha256(String value) {
        return sha256((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    public String normalizeContent(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String text = Normalizer.normalize(value, Normalizer.Form.NFC);
        text = text.replace("\r\n", "\n").replace('\r', '\n').replace("\uFEFF", "");
        text = text.replace('\u00A0', ' ');
        text = text.replaceAll("[ \\t\\x0B\\f]+", " ");
        text = text.replaceAll(" *\\n *", "\n");
        text = text.replaceAll("\\n{3,}", "\n\n");
        return text.trim();
    }

    private String normalizeSourceKey(String value) {
        return value.trim().replace('\\', '/');
    }

    private String fitSourceKey(String value) {
        if (value.length() <= 512) {
            return value;
        }
        return value.substring(0, 447) + ":" + sha256(value);
    }

    private void append(StringBuilder builder, String key, Object value) {
        builder.append(key)
                .append('=')
                .append(value == null ? "" : String.valueOf(value))
                .append('\n');
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] values = digest.digest(bytes);
            StringBuilder builder = new StringBuilder();
            for (byte value : values) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("知识内容摘要计算失败", ex);
        }
    }
}
