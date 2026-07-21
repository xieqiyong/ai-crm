package com.hz.crm.web.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "crm.search.elasticsearch.enabled", havingValue = "true")
public class ElasticsearchConfiguration {

    @Value("${crm.search.elasticsearch.uris}")
    private String uris;

    @Bean
    public ElasticsearchClient elasticsearchClient() {
        String[] values = uris.split(",");
        URI[] hosts = new URI[values.length];
        for (int i = 0; i < values.length; i++) {
            hosts[i] = URI.create(values[i].trim());
        }
        Rest5Client restClient = Rest5Client.builder(hosts).build();
        ElasticsearchTransport transport = new Rest5ClientTransport(restClient, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }
}
