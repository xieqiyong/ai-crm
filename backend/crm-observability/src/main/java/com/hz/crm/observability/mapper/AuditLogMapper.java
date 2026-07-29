package com.hz.crm.observability.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hz.crm.observability.domain.AuditLogEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLogEntity> {
}
