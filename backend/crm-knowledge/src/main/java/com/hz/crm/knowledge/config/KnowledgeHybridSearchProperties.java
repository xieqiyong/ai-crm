package com.hz.crm.knowledge.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeHybridSearchProperties {

    @Value("${crm.knowledge.search.hybrid.vector-candidates:20}")
    private int vectorCandidates;

    @Value("${crm.knowledge.search.hybrid.keyword-candidates:20}")
    private int keywordCandidates;

    @Value("${crm.knowledge.search.hybrid.database-candidates:20}")
    private int databaseCandidates;

    @Value("${crm.knowledge.search.hybrid.vector-weight:0.55}")
    private double vectorWeight;

    @Value("${crm.knowledge.search.hybrid.keyword-weight:0.35}")
    private double keywordWeight;

    @Value("${crm.knowledge.search.hybrid.database-weight:0.10}")
    private double databaseWeight;

    @Value("${crm.knowledge.search.hybrid.rrf-k:60}")
    private int rrfK;

    @Value("${crm.knowledge.search.hybrid.min-score:0}")
    private double minScore;

    @Value("${crm.knowledge.search.hybrid.database-fallback-only:true}")
    private boolean databaseFallbackOnly;

    public int vectorCandidates() {
        return safeCandidates(null, vectorCandidates, 5);
    }

    public int keywordCandidates() {
        return safeCandidates(null, keywordCandidates, 5);
    }

    public int databaseCandidates() {
        return safeCandidates(null, databaseCandidates, 5);
    }

    public double vectorWeight() {
        return safeWeight(null, vectorWeight);
    }

    public double keywordWeight() {
        return safeWeight(null, keywordWeight);
    }

    public double databaseWeight() {
        return safeWeight(null, databaseWeight);
    }

    public int rrfK() {
        if (rrfK < 1) {
            return 60;
        }
        return Math.min(rrfK, 200);
    }

    public double minScore() {
        return safeMinScore(null, minScore);
    }

    public boolean databaseFallbackOnly() {
        return databaseFallbackOnly;
    }

    public int safeCandidates(Integer value, int defaultValue, int topK) {
        int count = value == null ? defaultValue : value.intValue();
        if (count < topK) {
            count = topK;
        }
        if (count < 1) {
            return 1;
        }
        return Math.min(count, 100);
    }

    public double safeWeight(Double value, double defaultValue) {
        double weight = value == null ? defaultValue : value.doubleValue();
        if (weight < 0D) {
            return 0D;
        }
        return Math.min(weight, 1D);
    }

    public double safeMinScore(Double value, double defaultValue) {
        double score = value == null ? defaultValue : value.doubleValue();
        if (score < 0D) {
            return 0D;
        }
        return Math.min(score, 1D);
    }
}
