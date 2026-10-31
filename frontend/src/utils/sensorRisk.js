export const RISK_LEVELS = {
  0: {
    label: 'Bình thường',
    status: 'NORMAL',
    action: 'Đèn xanh',
    actionCode: 'GREEN_LED',
    color: 'success',
    tone: 'normal',
  },
  1: {
    label: 'Cảnh báo nhẹ',
    status: 'WARNING',
    action: 'Đèn đỏ',
    actionCode: 'RED_LED',
    color: 'warning',
    tone: 'warning',
  },
  2: {
    label: 'Nguy hiểm',
    status: 'DANGER',
    action: 'Đèn đỏ + quạt',
    actionCode: 'RED_LED_FAN',
    color: 'error',
    tone: 'danger',
  },
  3: {
    label: 'Khẩn cấp',
    status: 'EMERGENCY',
    action: 'Đèn đỏ + quạt + còi',
    actionCode: 'RED_LED_FAN_BUZZER',
    color: 'error',
    tone: 'emergency',
  },
}

export const AI_MODEL_NAME = 'AI nhiệt/ẩm thử nghiệm + ngưỡng gas'

export function inferRiskLevel(sensor) {
  if (!sensor) return null

  const value = sensor.riskLevel ?? sensor.risk_level
  if (value == null || value === '' || sensor.predictionStatus !== 'READY') return null
  const existingLevel = Number(value)
  if (Number.isInteger(existingLevel) && existingLevel >= 0 && existingLevel <= 3) {
    return existingLevel
  }

  return null
}

export function getRiskPrediction(sensor) {
  if (sensor?.stale && sensor.recordedAt) {
    return { riskLevel: null, label: 'Dữ liệu cũ · thiết bị chưa cập nhật', status: 'STALE', action: 'Chờ số đo mới', actionCode: null, color: 'warning', tone: 'pending', confidence: null }
  }
  if (sensor && sensor.simulated === false && !sensor.recordedAt) {
    return { riskLevel: null, label: 'Chưa có dữ liệu', status: 'NO_DATA', action: 'Chờ dữ liệu', actionCode: null, color: 'default', tone: 'pending', confidence: null }
  }
  const riskLevel = inferRiskLevel(sensor)
  if (riskLevel == null) {
    const pendingLabels = {
      GAS_CALIBRATION_REQUIRED: 'Chờ hiệu chuẩn gas',
      MQ2_MODEL_REQUIRED: 'Đang thu dữ liệu MQ-2 · chưa có model',
      MQ2_DEVICE_NOT_TRAINED: 'Thiết bị chưa có dữ liệu huấn luyện',
      INVALID_SENSOR_DATA: 'Số đo cảm biến không hợp lệ',
    }
    const label = pendingLabels[sensor?.predictionStatus] || 'Chưa có dự đoán AI'
    return { riskLevel: null, label, status: sensor?.predictionStatus || 'NO_PREDICTION', action: label, actionCode: null, color: 'default', tone: 'pending', confidence: null }
  }
  const level = RISK_LEVELS[riskLevel]
  const rawConfidence = sensor?.confidence ?? sensor?.aiConfidence
  const confidence = rawConfidence == null ? NaN : Number(rawConfidence)

  return {
    riskLevel,
    aiRiskLevel: sensor?.aiRiskLevel,
    gasRiskLevel: sensor?.gasRiskLevel,
    decisionSource: sensor?.decisionSource,
    label: level.label,
    status: sensor?.aiStatus || level.status,
    action: sensor?.action || sensor?.aiAction || level.action,
    actionCode: sensor?.actionCode || level.actionCode,
    color: level.color,
    tone: level.tone,
    confidence: Number.isFinite(confidence) ? confidence : null,
  }
}

export function formatRiskSources(prediction) {
  if (prediction.aiRiskLevel == null || prediction.gasRiskLevel == null) return formatRiskConfidence(prediction.confidence)
  return `AI nhiệt/ẩm thử nghiệm: cấp ${prediction.aiRiskLevel} (${formatRiskConfidence(prediction.confidence)}) · Gas: cấp ${prediction.gasRiskLevel} theo ngưỡng ADC`
}

export function formatRiskConfidence(confidence) {
  if (confidence === null || confidence === undefined) {
    return 'Chưa có độ tin cậy AI'
  }

  return `${Math.round(confidence * 100)}%`
}
