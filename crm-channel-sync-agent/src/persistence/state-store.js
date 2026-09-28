const fs = require('fs')

class StateStore {
  constructor(statePath) {
    this.statePath = statePath
    this.state = this.load()
  }

  source(sourceId) {
    return this.state.sources[sourceId] || null
  }

  markSuccess(sourceId, values) {
    this.state.sources[sourceId] = {
      ...(this.state.sources[sourceId] || {}),
      ...values,
      lastSuccessAt: new Date().toISOString(),
    }
    this.save()
  }

  markFailure(sourceId, error) {
    this.state.sources[sourceId] = {
      ...(this.state.sources[sourceId] || {}),
      lastFailureAt: new Date().toISOString(),
      lastError: String(error?.message || error).slice(0, 1000),
    }
    this.save()
  }

  snapshot() {
    return this.state
  }

  load() {
    try {
      const value = JSON.parse(fs.readFileSync(this.statePath, 'utf8'))
      return value && value.sources ? value : { version: 1, sources: {} }
    } catch {
      return { version: 1, sources: {} }
    }
  }

  save() {
    const temporaryPath = `${this.statePath}.next`
    fs.writeFileSync(temporaryPath, JSON.stringify(this.state, null, 2))
    fs.renameSync(temporaryPath, this.statePath)
  }
}

module.exports = { StateStore }
