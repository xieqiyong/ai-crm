export class LoginRequest {
  constructor({ tenantId = '', username = '', password = '' }) {
    if (tenantId && tenantId.trim()) {
      this.tenantId = tenantId.trim()
    }
    this.username = username.trim()
    this.password = password
  }
}

export class SetupSuperAdminRequest {
  constructor({ tenantId = 'default', username = '', displayName = '', password = '' }) {
    this.tenantId = tenantId || 'default'
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
