package com.hz.crm.agent.runtime.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hz.crm.agent.runtime.domain.AgentEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AgentMapper extends BaseMapper<AgentEntity> {
}
