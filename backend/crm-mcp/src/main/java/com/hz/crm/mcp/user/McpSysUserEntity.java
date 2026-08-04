package com.hz.crm.mcp.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("sys_user")
public class McpSysUserEntity {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long tenantId;

    private String username;

    private String displayName;

    private Long departmentId;

    private Boolean enabled;

    private Boolean deleted;
}
