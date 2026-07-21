package com.hz.crm.auth.domain;

import com.hz.crm.common.time.DateTimes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "sys_user_role")
public class SysUserRoleEntity {

    @Id
    private Long id;

    @Column(nullable = false, length = 64)
    private String tenantId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long roleId;

    @Column(nullable = false)
    private boolean deleted;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = DateTimes.now();
        }
    }
}
