package com.hz.crm.domain.media;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hz.crm.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "crm_media_transcription_task")
@TableName("crm_media_transcription_task")
public class MediaTranscriptionTaskEntity extends BaseEntity {

    @Column(nullable = false, length = 32)
    private String businessType;

    @Column(nullable = false)
    private Long businessId;

    @Column(length = 32)
    private String targetType;

    private Long targetId;

    @Column(length = 128)
    private String targetName;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(length = 128)
    private String providerTaskId;

    @Column(length = 128)
    private String providerRequestId;

    @Column(length = 32)
    private String language;

    @Column(length = 256)
    private String fileName;

    @Column(length = 128)
    private String contentType;

    private Long fileSize;

    @Column(length = 512)
    private String storageKey;

    @Column(columnDefinition = "text")
    private String fileUrl;

    @Column(length = 16)
    private String fileFormat;

    @Column(length = 256)
    private String audioFileName;

    @Column(length = 128)
    private String audioContentType;

    private Long audioFileSize;

    @Column(length = 512)
    private String audioStorageKey;

    @Column(columnDefinition = "text")
    private String audioFileUrl;

    @Column(length = 16)
    private String audioFileFormat;

    private Integer progress;

    private Integer retryCount;

    private LocalDateTime lockedAt;

    @Column(length = 128)
    private String lockedBy;

    @Column(columnDefinition = "text")
    private String errorMessage;

    @Column(columnDefinition = "text")
    private String transcriptText;

    @Column(columnDefinition = "text")
    private String utterancesJson;

    @Column(columnDefinition = "text")
    private String rawResultJson;

    private LocalDateTime submittedAt;

    private LocalDateTime finishedAt;

    private Long ownerId;

    private Long creatorId;
}
