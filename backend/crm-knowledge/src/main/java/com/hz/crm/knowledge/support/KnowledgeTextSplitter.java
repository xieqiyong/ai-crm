package com.hz.crm.knowledge.support;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class KnowledgeTextSplitter {

    @Value("${crm.knowledge.chunk.max-chars:900}")
    private int maxChars;

    @Value("${crm.knowledge.chunk.overlap-chars:120}")
    private int overlapChars;

    public String profile() {
        int safeMax = safeMaxChars();
        return safeMax + ":" + safeOverlapChars(safeMax);
    }

    public List<KnowledgeTextChunk> split(String content) {
        String text = normalize(content);
        List<KnowledgeTextChunk> chunks = new ArrayList<KnowledgeTextChunk>();
        if (!StringUtils.hasText(text)) {
            return chunks;
        }
        int safeMax = safeMaxChars();
        int safeOverlap = safeOverlapChars(safeMax);
        int index = 0;
        int position = 0;
        while (position < text.length()) {
            int end = Math.min(position + safeMax, text.length());
            int breakPoint = findBreakPoint(text, position, end, safeMax);
            String chunkText = text.substring(position, breakPoint).trim();
            if (StringUtils.hasText(chunkText)) {
                KnowledgeTextChunk chunk = new KnowledgeTextChunk();
                chunk.setChunkIndex(index);
                chunk.setContent(chunkText);
                chunk.setTokenEstimate(estimateTokens(chunkText));
                chunks.add(chunk);
                index++;
            }
            if (breakPoint >= text.length()) {
                break;
            }
            position = Math.max(breakPoint - safeOverlap, position + 1);
        }
        return chunks;
    }

    private int safeMaxChars() {
        return maxChars <= 200 ? 900 : maxChars;
    }

    private int safeOverlapChars(int safeMax) {
        return overlapChars < 0 ? 0 : Math.min(overlapChars, safeMax / 3);
    }

    private int findBreakPoint(String text, int start, int end, int safeMax) {
        if (end >= text.length()) {
            return text.length();
        }
        int newline = text.lastIndexOf('\n', end);
        if (newline > start + safeMax / 2) {
            return newline + 1;
        }
        int sentence = lastIndexOfAny(text, end, start, "。！？；.!?;");
        if (sentence > start + safeMax / 2) {
            return sentence + 1;
        }
        return end;
    }

    private int lastIndexOfAny(String text, int end, int start, String chars) {
        int position = Math.min(end, text.length() - 1);
        while (position >= start) {
            if (chars.indexOf(text.charAt(position)) >= 0) {
                return position;
            }
            position--;
        }
        return -1;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String text = value.replace('\u00A0', ' ');
        text = text.replaceAll("[ \\t\\x0B\\f\\r]+", " ");
        text = text.replaceAll("\\n\\s*\\n+", "\n");
        return text.trim();
    }

    private int estimateTokens(String value) {
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        return Math.max(1, value.trim().length() / 2);
    }
}
