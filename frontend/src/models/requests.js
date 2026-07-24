export class LoginRequest {
  constructor({ username = '', password = '' }) {
    this.username = username.trim()
    this.password = password
  }
}

export class SetupSuperAdminRequest {
  constructor({ tenantName = '默认租户', username = '', displayName = '', password = '' }) {
    this.tenantName = tenantName.trim() || '默认租户'
    this.username = username.trim()
    this.displayName = displayName.trim()
    this.password = password
  }
}

export class PageRequest {
  constructor({ pageNo = 1, pageSize = 20 } = {}) {
    this.pageNo = Number(pageNo) || 1
    this.pageSize = Number(pageSize) || 20
  }
}

export class IdRequest {
  constructor(id) {
    this.id = id
  }
}

export class LeadQuery extends PageRequest {
  constructor(payload = {}) {
    super(payload)
    this.keyword = payload.keyword || ''
    this.status = payload.status || ''
    this.source = payload.source || ''
  }
}

export class CustomerQuery extends PageRequest {
  constructor(payload = {}) {
    super(payload)
    this.keyword = payload.keyword || ''
    this.level = payload.level || ''
    this.industry = payload.industry || ''
  }
}

export class OpportunityQuery extends PageRequest {
  constructor(payload = {}) {
    super(payload)
    this.keyword = payload.keyword || ''
    this.stage = payload.stage || ''
  }
}
