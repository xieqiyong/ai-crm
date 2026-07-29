package com.hz.crm.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hz.crm.knowledge.domain.KnowledgeChangeOutboxEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KnowledgeChangeOutboxMapper extends BaseMapper<KnowledgeChangeOutboxEntity> {
}
