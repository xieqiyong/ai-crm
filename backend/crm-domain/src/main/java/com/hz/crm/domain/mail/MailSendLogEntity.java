package com.hz.crm.domain.mail;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hz.crm.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "crm_mail_send_log",
        indexes = @Index(
                name = "idx_mail_send_log_tenant_created",
                columnList = "tenant_id,created_at"))
@TableName("crm_mail_send_log")
public class MailSendLogEntity extends BaseEntity {

    private Long customerId;

    @Column(length = 128)
    private String customerName;

    @Column(nullable = false, length = 256)
    private String recipientEmail;

    @Column(nullable = false, length = 256)
    private String subject;

    @Column(nullable = false, columnDefinition = "text")
    private String bodyHtml;

    @Column(columnDefinition = "text")
    private String attachmentNames;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(columnDefinition = "text")
    private String errorMessage;

    @Column(nullable = false)
    private Long senderId;

    private LocalDateTime sentAt;
}
