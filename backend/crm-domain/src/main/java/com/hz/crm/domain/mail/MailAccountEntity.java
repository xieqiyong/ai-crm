package com.hz.crm.domain.mail;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hz.crm.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "crm_mail_account",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_mail_account_tenant",
                columnNames = {"tenant_id"}))
@TableName("crm_mail_account")
public class MailAccountEntity extends BaseEntity {

    @Column(nullable = false, length = 128)
    private String host;

    @Column(nullable = false)
    private Integer port;

    @Column(nullable = false, length = 128)
    private String username;

    @Column(nullable = false, length = 512)
    private String password;

    @Column(nullable = false, length = 128)
    private String fromAddress;

    @Column(length = 128)
    private String fromName;

    @Column(nullable = false)
    private boolean sslEnabled;

    @Column(nullable = false)
    private boolean starttlsEnabled = true;

    @Column(nullable = false)
    private boolean enabled = true;
}
