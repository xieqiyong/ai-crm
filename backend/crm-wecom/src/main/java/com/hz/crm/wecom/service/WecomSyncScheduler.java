package com.hz.crm.wecom.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hz.crm.common.time.DateTimes;
import com.hz.crm.domain.wecom.WecomCorpConfigEntity;
import com.hz.crm.domain.wecom.mapper.WecomCorpConfigMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WecomSyncScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(WecomSyncScheduler.class);

    @Autowired
    private WecomCorpConfigMapper configMapper;

    @Autowired
    private WecomSyncCoordinator syncCoordinator;

    @Scheduled(
            fixedDelayString = "${crm.wecom.schedule-delay-ms:60000}",
            initialDelayString = "${crm.wecom.schedule-initial-delay-ms:30000}")
    public void scan() {
        QueryWrapper<WecomCorpConfigEntity> wrapper = new QueryWrapper<WecomCorpConfigEntity>();
        wrapper.eq("deleted", false);
        wrapper.eq("enabled", true);
        List<WecomCorpConfigEntity> configs = configMapper.selectList(wrapper);
        LocalDateTime now = DateTimes.now();
        for (WecomCorpConfigEntity config : configs) {
            if (!isDue(config, now)) {
                continue;
            }
            try {
                syncCoordinator.startScheduled(config);
            } catch (RuntimeException ex) {
                LOGGER.error("企业微信定时同步提交失败，配置编号：{}", config.getId(), ex);
            }
        }
    }

    private boolean isDue(WecomCorpConfigEntity config, LocalDateTime now) {
        if (config.getLastSyncAt() == null) {
            return true;
        }
        int interval = config.getSyncIntervalMinutes() == null
                ? 10
                : Math.max(2, config.getSyncIntervalMinutes().intValue());
        return !config.getLastSyncAt().plusMinutes(interval).isAfter(now);
    }
}
