import { useEffect, useMemo, useState } from 'react'
import {
  AlertOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  FireOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons'
import { Button, Empty, Segmented, Tag } from 'antd'

import { sensorApi } from '../api/sensor.js'
import { formatRiskSources, getRiskPrediction } from '../utils/sensorRisk.js'
import { getWarehouseReading, WAREHOUSE_NAMES } from '../utils/warehouseSensors.js'
import './alert-dashboard.css'

const formatDateTime = (value) => {
  if (!value) return 'Chưa có dữ liệu'

  return new Intl.DateTimeFormat('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(new Date(value))
}

const buildWarehouseRows = (latest) => WAREHOUSE_NAMES.map((name) => {
  const reading = getWarehouseReading(name, latest)
  const prediction = getRiskPrediction(reading)

  return { name, reading, prediction }
})

function AlertCommand({ alerts, dangerCount, emergencyCount, loading, onReload, selectedRow }) {
  return (
    <section className={`alert-command alert-command--${selectedRow.prediction.tone}`}>
      <div>
        <span><SafetyCertificateOutlined /> Incident center</span>
        <h1>Cảnh báo vận hành kho</h1>
        <p>Theo dõi cấp cảnh báo, kho liên quan và hành động phản ứng trong cùng một màn hình.</p>
      </div>

      <div className="alert-command-summary">
        <article>
          <small>Đang theo dõi</small>
          <strong>{alerts.length}</strong>
          <em>Sự kiện</em>
        </article>
        <article>
          <small>Nguy hiểm</small>
          <strong>{dangerCount}</strong>
          <em>Cấp 2+</em>
        </article>
        <article>
          <small>Khẩn cấp</small>
          <strong>{emergencyCount}</strong>
          <em>Cấp 3</em>
        </article>
        <Button icon={<ReloadOutlined spin={loading} />} onClick={onReload}>
          Tải lại
        </Button>
      </div>
    </section>
  )
}

function AlertWarehouseStatus({ latest, onSelect, selectedWarehouse }) {
  const rows = buildWarehouseRows(latest)
  const selectedRow = rows.find((row) => row.name === selectedWarehouse) || rows[0]

  return (
    <section className="alert-panel">
      <div className="alert-panel-header">
        <div>
          <span>Phạm vi giám sát</span>
          <strong>Trạng thái 4 kho</strong>
        </div>
        <Tag color={selectedRow.prediction.color}>{selectedRow.name} · {selectedRow.prediction.riskLevel == null ? selectedRow.prediction.label : `Cấp ${selectedRow.prediction.riskLevel}`}</Tag>
      </div>

      <div className="alert-warehouse-table">
        <div className="alert-warehouse-row alert-warehouse-row--head">
          <span>Kho</span>
          <span>Nhiệt độ</span>
          <span>Độ ẩm</span>
          <span>Gas</span>
          <span>Cấp</span>
          <span>Trạng thái</span>
        </div>

        {rows.map(({ name, reading, prediction }) => (
          <button
            aria-pressed={selectedWarehouse === name}
            className={`alert-warehouse-row alert-warehouse-row--${prediction.tone} ${selectedWarehouse === name ? 'is-selected' : ''}`}
            key={name}
            onClick={() => onSelect(name)}
            type="button"
          >
            <strong>{name}</strong>
            <span>{Number(reading.temperature || 0).toFixed(1)}°C</span>
            <span>{Number(reading.humidity || 0).toFixed(1)}%</span>
            <span>{Math.round(Number(reading.gas || 0))} ADC</span>
            <Tag color={prediction.color}>{prediction.riskLevel == null ? '—' : `Cấp ${prediction.riskLevel}`}</Tag>
            <em>{prediction.label}</em>
          </button>
        ))}
      </div>
    </section>
  )
}

function ResponsePanel({ selectedRow }) {
  const steps = selectedRow.prediction.riskLevel == null ? [
    selectedRow.prediction.label,
    'Kiểm tra kết nối và dữ liệu đầu vào của mô hình',
    'Chưa có lệnh điều khiển từ AI',
  ] : [
    selectedRow.prediction.riskLevel >= 1 ? 'Kiểm tra khu vực và xác nhận nguồn cảnh báo' : 'Tiếp tục giám sát định kỳ',
    selectedRow.prediction.riskLevel >= 2 ? 'Bật quạt thông gió và kiểm tra MQ-2' : 'Ghi nhận trạng thái ổn định',
    selectedRow.prediction.riskLevel >= 3 ? 'Kích hoạt còi và sơ tán khu vực liên quan' : 'Không cần leo thang',
  ]

  return (
    <aside className={`alert-response alert-response--${selectedRow.prediction.tone}`}>
      <div className="alert-response-head">
        <span><ThunderboltOutlined /> Kho đang xử lý</span>
        <strong>{selectedRow.name}</strong>
        <Tag color={selectedRow.prediction.color}>{selectedRow.prediction.action}</Tag>
      </div>

      <dl className="alert-response-metrics">
        <div>
          <dt>Nhiệt độ</dt>
          <dd>{Number(selectedRow.reading.temperature || 0).toFixed(1)}°C</dd>
        </div>
        <div>
          <dt>Độ ẩm</dt>
          <dd>{Number(selectedRow.reading.humidity || 0).toFixed(1)}%</dd>
        </div>
        <div>
          <dt>Gas</dt>
          <dd>{Math.round(Number(selectedRow.reading.gas || 0))} ADC</dd>
        </div>
      </dl>

      <div className="alert-runbook">
        {steps.map((step, index) => (
          <article key={step}>
            <span>{index + 1}</span>
            <strong>{step}</strong>
          </article>
        ))}
      </div>
    </aside>
  )
}

export default function AlertDashboard() {
  const [latest, setLatest] = useState(null)
  const [history, setHistory] = useState([])
  const [loading, setLoading] = useState(false)
  const [filter, setFilter] = useState('all')
  const [selectedWarehouse, setSelectedWarehouse] = useState('Kho 1')

  const loadData = async () => {
    try {
      setLoading(true)

      const [latestData, historyData] = await Promise.all([
        sensorApi.latest(),
        sensorApi.history(60),
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

  const rows = useMemo(() => buildWarehouseRows(latest), [latest])
  const selectedRow = rows.find((row) => row.name === selectedWarehouse) || rows[0]
  const alerts = useMemo(
    () => history
      .map((item) => ({
        ...item,
        prediction: getRiskPrediction(item),
      }))
      .filter((item) => item.prediction.riskLevel > 0)
      .reverse()
      .slice(0, 20),
    [history],
  )
  const filteredAlerts = useMemo(() => {
    if (filter === 'danger') return alerts.filter((item) => item.prediction.riskLevel >= 2)
    if (filter === 'emergency') return alerts.filter((item) => item.prediction.riskLevel === 3)

    return alerts
  }, [alerts, filter])
  const dangerCount = alerts.filter((item) => item.prediction.riskLevel >= 2).length
  const emergencyCount = alerts.filter((item) => item.prediction.riskLevel === 3).length

  return (
    <main className="alert-page">
      <AlertCommand
        alerts={alerts}
        dangerCount={dangerCount}
        emergencyCount={emergencyCount}
        loading={loading}
        onReload={loadData}
        selectedRow={selectedRow}
      />

      <section className="alert-workspace">
        <div className="alert-workspace-main">
          <AlertWarehouseStatus
            latest={latest}
            onSelect={setSelectedWarehouse}
            selectedWarehouse={selectedWarehouse}
          />

          <section className="alert-panel">
            <div className="alert-panel-header">
              <div>
                <span>Hàng chờ sự kiện</span>
                <strong>Nhật ký cảnh báo · 20 mục gần nhất</strong>
              </div>
              <div className="alert-actions">
                <Segmented
                  onChange={setFilter}
                  options={[
                    { label: 'Tất cả', value: 'all' },
                    { label: 'Nguy hiểm', value: 'danger' },
                    { label: 'Khẩn cấp', value: 'emergency' },
                  ]}
                  value={filter}
                />
              </div>
            </div>

            {filteredAlerts.length ? (
              <div className="alert-list">
                {filteredAlerts.map((item, index) => (
                  <article className={`alert-item alert-item--${item.prediction.tone}`} key={`${item.recordedAt || index}-${index}`}>
                    <div className="alert-item-main">
                      <span>{item.prediction.riskLevel >= 2 ? <FireOutlined /> : <AlertOutlined />}</span>
                      <div>
                        <strong>Cấp {item.prediction.riskLevel} - {item.prediction.label}</strong>
                        <em>{formatDateTime(item.recordedAt)}</em>
                      </div>
                    </div>
                    <div className="alert-item-values">
                      <span>{Number(item.temperature || 0).toFixed(1)}°C</span>
                      <span>{Number(item.humidity || 0).toFixed(1)}%</span>
                      <span>{Math.round(Number(item.gas || 0))} ADC</span>
                    </div>
                    <div className="alert-item-action">
                      <strong>{item.prediction.action}</strong>
                      <em>{formatRiskSources(item.prediction)}</em>
                    </div>
                    <Tag color={item.prediction.color}>{item.prediction.status}</Tag>
                  </article>
                ))}
              </div>
            ) : (
              <div className="alert-empty">
                <Empty description="Chưa có cảnh báo môi trường" />
              </div>
            )}
          </section>
        </div>

        <ResponsePanel selectedRow={selectedRow} />
      </section>
    </main>
  )
}
