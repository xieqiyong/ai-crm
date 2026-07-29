package com.hz.crm.knowledge.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnProperty(
        prefix = "crm.knowledge.change.kafka",
        name = "enabled",
        havingValue = "true")
public class KnowledgeChangeKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeChangeKafkaConsumer.class);

    @Autowired
    private KnowledgeChangeReplayService knowledgeChangeReplayService;

    @KafkaListener(
            topics = "${crm.knowledge.change.kafka.topic:crm-knowledge-change}",
            groupId = "${crm.knowledge.change.kafka.group-id:crm-knowledge-indexer}")
    public void consume(String message) {
        JSONObject payload = JSON.parseObject(message);
        String eventIdText = payload == null ? null : payload.getString("eventId");
        if (!StringUtils.hasText(eventIdText)) {
            log.warn("知识变更Kafka消息缺少事件编号");
            return;
        }
        try {
            knowledgeChangeReplayService.replayEventToPendingGenerations(
                    Long.valueOf(eventIdText));
        } catch (RuntimeException ex) {
            log.warn("知识变更Kafka消息处理失败，eventId={}", eventIdText, ex);
            throw ex;
        }
    }
}
