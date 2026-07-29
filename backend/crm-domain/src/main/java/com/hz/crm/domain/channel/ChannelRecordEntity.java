package com.hz.crm.domain.channel;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hz.crm.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "crm_channel_record")
@TableName("crm_channel_record")
public class ChannelRecordEntity extends BaseEntity {

    @Column(nullable = false, length = 128)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, columnDefinition = "varchar(32)")
    private ChannelType channelType = ChannelType.MANUAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ChannelStatus status = ChannelStatus.NEW;

    @Column(length = 128)
    private String source;

    @Column(length = 128)
    private String contactName;

    @Column(length = 128)
    private String companyName;

    @Column(length = 32)
    private String phone;

    @Column(length = 128)
    private String email;

    @Column(length = 256)
    private String mediaFileName;

    @Column(length = 128)
    private String mediaContentType;

    private Long mediaSize;

    @Column(length = 256)
    private String mediaStorageKey;

    @Column(columnDefinition = "text")
    private String transcriptText;

    @Column(columnDefinition = "text")
    private String aiSummary;

    @Column(columnDefinition = "text")
    private String usefulInfo;

    @Column(columnDefinition = "text")
    private String aiAnalysisJson;

    private Long agentRunId;

    private LocalDateTime aiAnalyzedAt;

    private Long leadId;

    private Long ownerId;

    @Column(columnDefinition = "text")
    private String remark;
}
