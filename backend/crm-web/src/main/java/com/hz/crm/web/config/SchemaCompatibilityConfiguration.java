package com.hz.crm.web.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SchemaCompatibilityConfiguration implements ApplicationRunner {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        dropChannelTypeCheckConstraint();
    }

    private void dropChannelTypeCheckConstraint() {
        try {
            jdbcTemplate.execute("alter table if exists crm_channel_record "
                    + "drop constraint if exists crm_channel_record_channel_type_check");
        } catch (RuntimeException ex) {
            log.warn("渠道类型历史约束兼容处理失败", ex);
        }
    }
}
