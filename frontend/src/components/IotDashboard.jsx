import { useEffect, useMemo, useState } from 'react'
import {
  AlertOutlined,
  CloudOutlined,
  FireOutlined,
  ReloadOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons'
import { Button, Tag } from 'antd'

import { sensorApi } from '../api/sensor.js'
import { AI_MODEL_NAME, formatRiskSources, getRiskPrediction } from '../utils/sensorRisk.js'
import { getWarehouseHistory, getWarehouseReading, WAREHOUSE_NAMES } from '../utils/warehouseSensors.js'
import './iot-dashboard.css'
import { sensorValue } from '../utils/warehouseDisplay'
import { formatDateTime } from '../utils/date'

const formatTime = (value) => {
  if (!value) return 'Chưa có dữ liệu'

  return new Intl.DateTimeFormat('vi-VN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(new Date(value))
}

function MetricCard({ icon, label, value, detail, tone }) {
  return (
    <section className={`iot-metric-card iot-metric-card--${tone}`}>
      <span>{icon}</span>
      <div>
        <small>{label}</small>
        <strong>{value}</strong>
        <em>{detail}</em>
      </div>
    </section>
  )
}

function WarehouseMap({ latest, onSelect, selectedWarehouse }) {
  return (
    <section className="iot-panel">
      <div className="iot-panel-header">
        <div>
          <span>Tình trạng 4 kho</span>
          <strong>Tổng quan kho</strong>
        </div>
      </div>

      <div className="iot-warehouse-grid">
        {WAREHOUSE_NAMES.map((name) => {
          const reading = getWarehouseReading(name, latest)
          const prediction = getRiskPrediction(reading)
          const selected = selectedWarehouse === name

          return (
            <button
              aria-pressed={selected}
              className={`iot-warehouse iot-warehouse--${prediction.tone} ${selected ? 'is-selected' : ''}`}
              key={name}
              onClick={() => onSelect(name)}
              type="button"
            >
              <div>
                <strong>{name}</strong>
                <Tag color={selected ? 'processing' : prediction.color}>
                  {selected ? 'Đang xem' : prediction.riskLevel == null ? 'Đang theo dõi' : `Cấp ${prediction.riskLevel}`}
                </Tag>
              </div>
              <span>{prediction.riskLevel == null ? 'Theo dõi môi trường kho' : prediction.label}</span>
              <small>Môi trường kho · {sensorValue(reading.temperature, '°C')} · {sensorValue(reading.humidity, '%')}</small>
            </button>
          )
        })}
      </div>
    </section>
  )
}

const buildPolyline = (items, key, min, max) => {
  if (!items.length) return ''

  return items
    .map((item, index) => {
      const x = items.length === 1 ? 0 : (index / (items.length - 1)) * 100
      const value = Number(item[key] || 0)
      const y = 100 - ((value - min) / Math.max(max - min, 1)) * 100

      return `${x.toFixed(2)},${Math.max(4, Math.min(96, y)).toFixed(2)}`
    })
    .join(' ')
}

function SensorLineChart({ history }) {
  const chartHistory = history.slice(-24)
  const charts = [
    { key: 'temperature', label: 'Nhiệt độ', tone: 'temp', unit: '°C' },
    { key: 'humidity', label: 'Độ ẩm', tone: 'humidity', unit: '%' },
    { key: 'gas', label: 'Khí gas', tone: 'gas', unit: ' ADC' },
  ].map((config) => {
    const values = chartHistory.map((item) => Number(item[config.key] || 0))
    const minValue = Math.min(...values)
    const maxValue = Math.max(...values)
    const min = minValue === maxValue ? minValue - 1 : minValue
    const max = minValue === maxValue ? maxValue + 1 : maxValue
    const latestValue = values[values.length - 1] || 0

    return {
      ...config,
      latestValue,
      points: buildPolyline(chartHistory, config.key, min, max),
    }
  })

  return (
    <section className="iot-panel">
      <div className="iot-panel-header">
        <div>
          <span>Biểu đồ đường</span>
          <strong>3 thông số cảm biến</strong>
        </div>
      </div>

      <div className="iot-chart-stack">
        {charts.map((chart) => (
          <article className="iot-chart-card" key={chart.key}>
            <div className="iot-chart-card-header">
              <span>
                <i className={`iot-legend-${chart.tone}`} />
                {chart.label}
              </span>
              <strong>
                {chart.key === 'gas' ? Math.round(chart.latestValue) : chart.latestValue.toFixed(1)}
                {chart.unit}
              </strong>
            </div>
            <div className="iot-line-chart">
              <svg aria-label={`Biểu đồ ${chart.label}`} preserveAspectRatio="none" viewBox="0 0 100 100">
                <line className="iot-chart-grid" x1="0" x2="100" y1="25" y2="25" />
                <line className="iot-chart-grid" x1="0" x2="100" y1="50" y2="50" />
                <line className="iot-chart-grid" x1="0" x2="100" y1="75" y2="75" />
                <polyline className={`iot-line iot-line--${chart.tone}`} points={chart.points} />
              </svg>
            </div>
          </article>
        ))}
      </div>
    </section>
  )
}

function ReadingHistory({ history, warehouseName }) {
  return (
    <section className="iot-panel">
      <div className="iot-panel-header">
        <div>
          <span>Lịch sử đo</span>
          <strong>{warehouseName}</strong>
        </div>
      </div>

      <div className="iot-history">
        <div className="iot-history-row iot-history-row--head">
          <span>Thời gian</span>
          <span>Nhiệt độ</span>
          <span>Độ ẩm</span>
          <span>Gas</span>
        </div>

        {history.slice(-10).reverse().map((item, index) => (
          <article className="iot-history-row" key={`${item.recordedAt || index}-${index}`}>
            <span>{formatTime(item.recordedAt)}</span>
            <strong>{Number(item.temperature || 0).toFixed(1)}°C</strong>
            <strong>{Number(item.humidity || 0).toFixed(1)}%</strong>
            <strong>{Math.round(Number(item.gas || 0))} ADC</strong>
          </article>
        ))}
      </div>
    </section>
  )
}

export default function IotDashboard() {
  const [latest, setLatest] = useState(null)
  const [history, setHistory] = useState([])
  const [selectedWarehouse, setSelectedWarehouse] = useState('Kho 1')
  const [loading, setLoading] = useState(false)

  const loadData = async () => {
    try {
      setLoading(true)

      const [latestData, historyData] = await Promise.all([
        sensorApi.latest(),
        sensorApi.history(80),
      ])

      setLatest(latestData || null)
      setHistory(Array.isArray(historyData) ? historyData : [])
    } catch {
      setLatest(null)
      setHistory([])
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadData()
    const timer = window.setInterval(loadData, 5000)

    return () => window.clearInterval(timer)
  }, [])

  const selectedLatest = getWarehouseReading(selectedWarehouse, latest)
  const selectedHistory = useMemo(
    () => getWarehouseHistory(selectedWarehouse, latest, history),
    [history, latest, selectedWarehouse],
  )
  const prediction = getRiskPrediction(selectedLatest)

  return (
    <main className="iot-page">
      <section className="iot-selected">
        <div className="iot-selected-header">
          <div>
            <span>Kho đang xem</span>
            <strong>{selectedWarehouse}</strong>
          </div>
          <Tag color={prediction.color}>{prediction.riskLevel == null ? prediction.label : `Cấp ${prediction.riskLevel} - ${prediction.label}`}</Tag>
        </div>

        <div className="iot-metrics">
          <MetricCard
            detail="DHT22"
            icon={<FireOutlined />}
            label="Nhiệt độ"
            tone="temp"
            value={sensorValue(selectedLatest.temperature, '°C')}
          />
          <MetricCard
            detail="Độ ẩm không khí"
            icon={<CloudOutlined />}
            label="Độ ẩm"
            tone="humidity"
            value={sensorValue(selectedLatest.humidity, '%')}
          />
          <MetricCard
            detail="MQ-2"
            icon={<ThunderboltOutlined />}
            label="Gas"
            tone="gas"
            value={sensorValue(selectedLatest.gas, 'ADC')}
          />
          <MetricCard
            detail={formatRiskSources(prediction)}
            icon={<AlertOutlined />}
            label={AI_MODEL_NAME}
            tone="ai"
            value={prediction.action}
          />
        </div>

        <div className="iot-selected-actions">
          {selectedLatest.recordedAt && <span>Lần đo cuối: {formatDateTime(selectedLatest.recordedAt)}{selectedLatest.stale ? ' · Dữ liệu cũ' : ''}</span>}
          <Button icon={<ReloadOutlined spin={loading} />} onClick={loadData}>
            Tải lại
          </Button>
        </div>
      </section>

      <WarehouseMap latest={latest} onSelect={setSelectedWarehouse} selectedWarehouse={selectedWarehouse} />

      <SensorLineChart history={selectedHistory} />

      <ReadingHistory history={selectedHistory} warehouseName={selectedWarehouse} />
    </main>
  )
}
