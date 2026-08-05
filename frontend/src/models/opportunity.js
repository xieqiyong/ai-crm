export const opportunityStageProbability = {
  DISCOVERY: 20,
  QUALIFICATION: 40,
  PROPOSAL: 60,
  NEGOTIATION: 80,
  WON: 100,
  LOST: 0,
}

export function getRecommendedOpportunityProbability(stage) {
  return opportunityStageProbability[stage] ?? ''
}

export function normalizeOpportunityProbabilityValue(value) {
  if (value === null || value === undefined || value === '') return ''
  const numberValue = Number(value)
  if (Number.isNaN(numberValue)) return ''
  return Math.max(0, Math.min(100, Math.round(numberValue)))
}

export function toOpportunityProbabilityPayload(value) {
  const normalized = normalizeOpportunityProbabilityValue(value)
  return normalized === '' ? null : normalized
}

export function buildOpportunityStagePatch(form, stage) {
  const previousStage = form?.stage || ''
  const currentProbability = normalizeOpportunityProbabilityValue(form?.probability)
  const previousRecommended = getRecommendedOpportunityProbability(previousStage)
  const shouldUseRecommended = currentProbability === '' || currentProbability === previousRecommended

  return {
    stage,
    probability: shouldUseRecommended ? getRecommendedOpportunityProbability(stage) : currentProbability,
  }
}
