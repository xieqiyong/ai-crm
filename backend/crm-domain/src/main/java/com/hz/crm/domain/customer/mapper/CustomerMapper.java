package com.hz.crm.domain.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hz.crm.domain.customer.CustomerEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CustomerMapper extends BaseMapper<CustomerEntity> {
}
