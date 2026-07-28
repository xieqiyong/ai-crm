package com.hz.crm.knowledge.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class KnowledgeAsyncConfiguration {

    @Value("${crm.knowledge.ingest.executor.core-size:2}")
    private int coreSize;

    @Value("${crm.knowledge.ingest.executor.max-size:4}")
    private int maxSize;

    @Value("${crm.knowledge.ingest.executor.queue-capacity:100}")
    private int queueCapacity;

    @Bean("knowledgeIngestTaskExecutor")
    public ThreadPoolTaskExecutor knowledgeIngestTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("kb-ingest-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
