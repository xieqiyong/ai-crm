package com.hz.crm.common.user;

import java.util.Collection;
import java.util.Map;

public interface UserNameResolver {

    Map<Long, String> resolve(Long tenantId, Collection<Long> userIds);
}
