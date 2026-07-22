package com.hz.crm.auth.service;

import com.hz.crm.auth.domain.PermissionType;
import com.hz.crm.auth.domain.SysPermissionEntity;
import com.hz.crm.auth.repository.SysPermissionRepository;
import com.hz.crm.common.id.SnowflakeIdGenerator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PermissionSeedService {

    @Autowired
    private SysPermissionRepository permissionRepository;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Transactional
    public List<SysPermissionEntity> seedBasePermissions(String tenantId) {
        List<SysPermissionEntity> existing =
                permissionRepository.findByTenantIdAndDeletedFalseOrderBySortNoAscCreatedAtAsc(tenantId);
        Map<String, SysPermissionEntity> existsMap = new HashMap<String, SysPermissionEntity>();
        for (SysPermissionEntity permission : existing) {
            existsMap.put(permission.getCode(), permission);
        }
        String[][] definitions = permissionDefinitions();
        for (int i = 0; i < definitions.length; i++) {
            String code = definitions[i][0];
            if (existsMap.containsKey(code)) {
                continue;
            }
            SysPermissionEntity permission = new SysPermissionEntity();
            permission.setId(snowflakeIdGenerator.nextId());
            permission.setTenantId(tenantId);
            permission.setCode(code);
            permission.setName(definitions[i][1]);
            permission.setPermissionType(PermissionType.valueOf(definitions[i][2]));
            permission.setRoutePath(definitions[i][3]);
            permission.setSortNo(i + 1);
            permissionRepository.save(permission);
        }
        return permissionRepository.findByTenantIdAndDeletedFalseOrderBySortNoAscCreatedAtAsc(tenantId);
    }

    private String[][] permissionDefinitions() {
        return new String[][] {
            {"*", "全部操作权限", "ACTION", ""},
            {"menu.dashboard", "工作台菜单", "MENU", "dashboard"},
            {"menu.leads", "线索菜单", "MENU", "leads"},
            {"menu.channels", "渠道菜单", "MENU", "channels"},
            {"menu.customers", "客户菜单", "MENU", "customers"},
            {"menu.opportunities", "商机菜单", "MENU", "opportunities"},
            {"menu.followups", "跟进菜单", "MENU", "followups"},
            {"menu.tasks", "任务菜单", "MENU", "tasks"},
            {"menu.assistant", "AI助手菜单", "MENU", "assistant"},
            {"menu.knowledge", "知识库菜单", "MENU", "knowledge"},
            {"menu.organization", "组织权限菜单", "MENU", "organization"},
            {"menu.model_configs", "大模型配置菜单", "MENU", "model-configs"},
            {"menu.settings", "系统设置菜单", "MENU", "settings"},
            {"crm:dashboard:view", "工作台查看", "ACTION", ""},
            {"crm:lead:view", "线索查看", "ACTION", ""},
            {"crm:lead:manage", "线索管理", "ACTION", ""},
            {"crm:lead:create", "线索创建", "ACTION", ""},
            {"crm:lead:export", "线索导出", "ACTION", ""},
            {"crm:channel:view", "渠道查看", "ACTION", ""},
            {"crm:channel:manage", "渠道管理", "ACTION", ""},
            {"crm:channel:media", "渠道音视频导入", "ACTION", ""},
            {"crm:channel:promote", "渠道晋升线索", "ACTION", ""},
            {"crm:customer:view", "客户查看", "ACTION", ""},
            {"crm:customer:manage", "客户管理", "ACTION", ""},
            {"crm:customer:edit", "客户编辑", "ACTION", ""},
            {"crm:opportunity:view", "商机查看", "ACTION", ""},
            {"crm:opportunity:manage", "商机管理", "ACTION", ""},
            {"crm:opportunity:create", "商机创建", "ACTION", ""},
            {"crm:assistant:use", "AI助手使用", "ACTION", ""},
            {"crm:agent:manage", "Agent管理", "ACTION", ""},
            {"crm:model:view", "大模型配置查看", "ACTION", ""},
            {"crm:model:manage", "大模型配置管理", "ACTION", ""},
            {"crm:knowledge:manage", "知识库管理", "ACTION", ""},
            {"crm:org:view", "组织权限查看", "ACTION", ""},
            {"crm:org:manage", "组织权限管理", "ACTION", ""},
            {"crm:settings:view", "系统设置查看", "ACTION", ""},
            {"crm:workflow:manage", "流程管理", "ACTION", ""},
            {"crm:observability:view", "可观测查看", "ACTION", ""},
            {"data:all", "全部数据", "DATA", ""},
            {"data:department_child", "本部门及下级数据", "DATA", ""},
            {"data:department", "本部门数据", "DATA", ""},
            {"data:self", "本人数据", "DATA", ""}
        };
    }
}
