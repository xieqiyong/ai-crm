package com.hz.crm.agent.runtime.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hz.crm.common.time.DateTimes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "agents")
@TableName("agents")
public class AgentEntity {

    @Id
    @TableId(type = IdType.INPUT)
    private Long id;

    @Column(nullable = false, columnDefinition = "bigint")
    private Long tenantId;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(length = 64)
    private String sceneCode;

    @Column(length = 128)
    private String sceneName;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 512)
    private String description;

    @Column(columnDefinition = "text")
    private String systemPrompt;

    private Long modelConfigId;

    @Column(nullable = false, length = 64)
    private String modelProvider = "OPENAI";

    @Column(nullable = false, length = 128)
    private String modelName;

    @Column(length = 512)
    private String baseUrl;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @TableField("api_key_env")
    @Column(name = "api_key_env", nullable = false, columnDefinition = "text")
    private String apiKey;

    @Column(nullable = false)
    private Integer maxIters = 8;

    @Column(columnDefinition = "text")
    private String extraConfigJson;

    @Column(length = 512)
    private String remark;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private boolean deleted;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        LocalDateTime now = DateTimes.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = DateTimes.now();
    }
}
