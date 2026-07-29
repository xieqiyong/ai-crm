package com.hz.crm.knowledge.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hz.crm.common.time.DateTimes;
import com.hz.crm.knowledge.domain.KnowledgeChangeOutboxEntity;
import com.hz.crm.knowledge.mapper.KnowledgeChangeOutboxMapper;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
        prefix = "crm.knowledge.change.kafka",
        name = "enabled",
        havingValue = "true")
public class KnowledgeOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeOutboxPublisher.class);

    @Autowired
    private KnowledgeChangeOutboxMapper changeOutboxMapper;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Value("${crm.knowledge.change.kafka.topic:crm-knowledge-change}")
    private String topic;

    @Value("${crm.knowledge.change.kafka.publish-batch-size:100}")
    private int publishBatchSize;

    @Value("${crm.knowledge.change.kafka.send-timeout-ms:10000}")
    private long sendTimeoutMs;

    @Scheduled(
            fixedDelayString = "${crm.knowledge.change.kafka.publish-delay-ms:1000}",
            initialDelayString = "${crm.knowledge.change.kafka.publish-initial-delay-ms:5000}")
    public void publish() {
        int batchSize = Math.max(1, Math.min(publishBatchSize, 500));
        List<KnowledgeChangeOutboxEntity> events = changeOutboxMapper.selectList(
                Wrappers.<KnowledgeChangeOutboxEntity>lambdaQuery()
                        .eq(KnowledgeChangeOutboxEntity::isPublished, false)
                        .orderByAsc(KnowledgeChangeOutboxEntity::getId)
                        .last("limit " + batchSize));
        for (KnowledgeChangeOutboxEntity event : events) {
            publishOne(event);
        }
    }

    private void publishOne(KnowledgeChangeOutboxEntity event) {
        try {
            kafkaTemplate.send(
                            topic,
                            String.valueOf(event.getTenantId()),
                            event.getPayloadJson())
                    .get(Math.max(sendTimeoutMs, 1000L), TimeUnit.MILLISECONDS);
            event.setPublished(true);
            event.setPublishedAt(DateTimes.now());
            event.setErrorMessage(null);
            event.setPublishAttempts(Integer.valueOf(safeAttempts(event) + 1));
            event.setUpdatedAt(DateTimes.now());
            changeOutboxMapper.updateById(event);
        } catch (Exception ex) {
            event.setPublishAttempts(Integer.valueOf(safeAttempts(event) + 1));
            event.setErrorMessage(shrink(ex.getMessage(), 512));
            event.setUpdatedAt(DateTimes.now());
            changeOutboxMapper.updateById(event);
            log.warn("知识变更事件发布Kafka失败，eventId={}", event.getId(), ex);
        }
    }

    private int safeAttempts(KnowledgeChangeOutboxEntity event) {
        return event.getPublishAttempts() == null ? 0 : event.getPublishAttempts().intValue();
    }

    private String shrink(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }
}
