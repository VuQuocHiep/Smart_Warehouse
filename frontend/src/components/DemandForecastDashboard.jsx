import { useCallback, useEffect, useMemo, useState } from 'react'
import { Alert, Button, Card, Empty, Select, Space, Statistic, Table, Tag, Tooltip, Typography } from 'antd'
import { ReloadOutlined, LineChartOutlined } from '@ant-design/icons'
import { demandApi } from '../api/demand'

const { Title, Text } = Typography
const pairKey = (pair) => JSON.stringify([pair.warehouseId, pair.productId])
const quantity = (value) => value == null ? '—' : Number(value).toLocaleString('vi-VN', { maximumFractionDigits: 0 })

function ForecastChart({ history, forecasts }) {
  const observations = history.map((row) => ({ date: row.date, value: row.export_quantity, predicted: false }))
  const predictions = forecasts.filter((row) => row.predicted_quantity != null).map((row) => ({ date: row.target_date, value: row.predicted_quantity, predicted: true }))
  const points = [...observations, ...predictions]
  if (!points.length) return null
  const maximum = Math.max(1, ...points.map((point) => point.value))
  const x = (index) => 45 + index * 830 / Math.max(1, points.length - 1)
  const y = (value) => 205 - value * 170 / maximum
  const line = (begin, end) => points.slice(begin, end).map((point, index) => `${x(begin + index)},${y(point.value)}`).join(' ')
  return <figure style={{ margin: 0 }}>
    <svg viewBox="0 0 920 250" role="img" aria-label="Lịch sử xuất 28 ngày và dự báo 14 ngày" style={{ width: '100%', maxHeight: 320 }}>
      <line x1="45" x2="880" y1="205" y2="205" stroke="#cbd5e1" />
      <text x="5" y="40" fontSize="13">{quantity(maximum)}</text>
      <text x="20" y="205" fontSize="13">0</text>
      <polyline points={line(0, observations.length)} fill="none" stroke="#1677ff" strokeWidth="2.5" />
      <polyline points={line(Math.max(0, observations.length - 1), points.length)} fill="none" stroke="#13a886" strokeWidth="2.5" strokeDasharray="6 3" />
      {points.map((point, index) => {
        const date = point.date.split('-').reverse().join('/')
        const label = point.predicted ? 'Số lượng dự đoán' : 'Số lượng xuất kho'
        return <Tooltip key={`${point.date}-${index}`} placement="top" trigger={['hover', 'focus']} mouseEnterDelay={0}
          title={<div><div>Ngày: {date}</div><div>{label}: <strong>{quantity(point.value)}</strong></div></div>}>
          <g tabIndex={0} aria-label={`Ngày ${date}, ${label}: ${quantity(point.value)}`} style={{ cursor: 'pointer' }}>
            <circle cx={x(index)} cy={y(point.value)} r="10" fill="transparent" />
            <circle cx={x(index)} cy={y(point.value)} r="3" fill={point.predicted ? '#13a886' : '#1677ff'} />
          </g>
        </Tooltip>
      })}
      <text x="45" y="235" fontSize="12">{points[0].date}</text>
      <text x="875" y="235" textAnchor="end" fontSize="12">{points.at(-1).date}</text>
    </svg>
    <figcaption><Tag color="blue">Lịch sử xuất kho</Tag><Tag color="green">Dự báo</Tag></figcaption>
  </figure>
}

export default function DemandForecastDashboard() {
  const [catalog, setCatalog] = useState([])
  const [result, setResult] = useState(null)
  const [selection, setSelection] = useState(null)
  const [job, setJob] = useState(null)
  const [loading, setLoading] = useState(true)
  const [starting, setStarting] = useState(false)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    const [pairs, latest] = await Promise.allSettled([demandApi.catalog(), demandApi.latest()])
    setError('')
    if (pairs.status === 'fulfilled') setCatalog(pairs.value)
    if (latest.status === 'fulfilled') setResult(latest.value)
    const failure = [pairs, latest].find((value) => value.status === 'rejected')
    if (failure) setError(failure.reason.message)
    setLoading(false)
  }, [])

  useEffect(() => {
    let active = true
    Promise.allSettled([demandApi.catalog(), demandApi.latest()]).then(([pairs, latest]) => {
      if (!active) return
      if (pairs.status === 'fulfilled') setCatalog(pairs.value)
      if (latest.status === 'fulfilled') setResult(latest.value)
      const failure = [pairs, latest].find((value) => value.status === 'rejected')
      if (failure) setError(failure.reason.message)
      setLoading(false)
      if (!failure && pairs.value.length > 0 && pairs.value.length <= 64) {
        setStarting(true)
        demandApi.startDemo()
          .then((next) => { if (active) setJob(next) })
          .catch((failure) => { if (active) setError(failure.message) })
          .finally(() => { if (active) setStarting(false) })
      }
    })
    return () => { active = false }
  }, [])
  const activeJobId = job && ['QUEUED', 'RUNNING'].includes(job.status) ? job.jobId : null
  useEffect(() => {
    if (!activeJobId) return undefined
    let cancelled = false
    let timer
    const poll = async () => {
      try {
        const next = await demandApi.job(activeJobId)
        if (cancelled) return
        setJob(next)
        if (next.status === 'SUCCEEDED') await load()
        else if (['FAILED', 'INTERRUPTED'].includes(next.status)) setError(next.message || 'Tác vụ chưa hoàn tất. Có thể chạy lại.')
        else timer = window.setTimeout(poll, 2500)
      } catch (failure) {
        if (!cancelled) {
          setError(failure.message)
          setJob((current) => ({ ...current, status: 'UNKNOWN' }))
        }
      }
    }
    poll()
    return () => { cancelled = true; window.clearTimeout(timer) }
  }, [activeJobId, load])

  const start = async () => {
    setStarting(true)
    setError('')
    try { setJob(await demandApi.startDemo()) }
    catch (failure) { setError(failure.message) }
    finally { setStarting(false) }
  }

  const selected = catalog.find((pair) => pairKey(pair) === selection) || catalog[0]
  const matches = useCallback((row) => row.warehouse_id === selected?.warehouseId && row.product_id === selected?.productId, [selected])
  const forecasts = useMemo(() => (result?.forecasts || []).filter(matches)
    .map((row) => ({ ...row, predicted_quantity: row.predicted_quantity == null ? null : Math.round(Math.max(0, row.predicted_quantity)) }))
    .sort((a, b) => a.horizon - b.horizon), [result, matches])
  const history = useMemo(() => (result?.history || []).filter(matches).sort((a, b) => a.date.localeCompare(b.date)), [result, matches])
  const selectedMethod = forecasts[0]?.method || result?.metrics?.selected_method
  const metric = result?.metrics?.test?.daily?.[result?.metrics?.selected_method === 'per_series' ? 'selected' : result?.metrics?.selected_method]
  const stale = result?.forecastDate && result.forecastDate !== new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' }).format(new Date())
  return <Space orientation="vertical" size="large" style={{ width: '100%' }}>
    <div><Title level={2}>Dự báo nhu cầu xuất kho</Title><Text type="secondary">Theo sản phẩm và kho · 14 ngày tiếp theo</Text></div>
    {error && <Alert type="error" showIcon title={error} />}
    {stale && <Alert type="warning" showIcon title={`Kết quả được tạo cho ngày ${result.forecastDate}. Chạy lại để cập nhật đến hôm qua.`} />}
    <Space wrap>
      <Button icon={<ReloadOutlined />} loading={loading} onClick={() => { setLoading(true); load() }}>Tải lại</Button>
      <Button type="primary" icon={<LineChartOutlined />} loading={starting || Boolean(activeJobId)} disabled={!catalog.length || catalog.length > 64} onClick={start}>Cập nhật dự báo</Button>
      {job && <Tag color={job.status === 'SUCCEEDED' ? 'green' : 'blue'}>{job.status}</Tag>}
      <Text type="secondary">{catalog.length} cặp kho/sản phẩm</Text>
    </Space>
    <Text type="secondary">
      Dự báo sử dụng mô hình joblib đã lưu; chỉ huấn luyện lại ở lần truy cập sau khi đủ 7 ngày.
      {result?.modelTrainedAt && ` Huấn luyện gần nhất: ${new Date(result.modelTrainedAt).toLocaleString('vi-VN')}. Lần tiếp theo từ: ${new Date(result.nextTrainingAt).toLocaleString('vi-VN')}.`}
    </Text>
    {!catalog.length ? <Empty description="Gán sản phẩm vào kệ kho để bắt đầu." /> : <>
      <Select aria-label="Chọn kho và sản phẩm" showSearch optionFilterProp="label" style={{ width: '100%', maxWidth: 650 }} value={selected && pairKey(selected)} onChange={setSelection}
        options={catalog.map((pair) => ({ value: pairKey(pair), label: `${pair.warehouseName} · ${pair.sku} · ${pair.productName}` }))} />
      {!forecasts.length ? <Empty description="Chưa có dự báo cho cặp kho/sản phẩm này. Dự báo sẽ được tạo tự động khi mở trang; có thể bấm Cập nhật dự báo để thử lại." /> : <>
        <Space wrap size="large">
          <Card><Statistic title="Lượng xuất dự báo 7 ngày" value={forecasts.slice(0, 7).reduce((sum, row) => sum + (row.predicted_quantity || 0), 0)} precision={0} /></Card>
          <Card><Statistic title="Lượng xuất dự báo 14 ngày" value={forecasts.reduce((sum, row) => sum + (row.predicted_quantity || 0), 0)} precision={0} /></Card>
          <Card><Statistic title="Sai số trung bình (MAE)" value={metric?.mae} precision={2} /></Card>
        </Space>
        <Card title="Lịch sử và dự báo"><ForecastChart history={history} forecasts={forecasts} /></Card>
        <Text type="secondary">Dữ liệu đến {result.dataCutoff} · Phương pháp: {selectedMethod} · MAE đo bằng đơn vị hàng.</Text>
        <Table rowKey="target_date" dataSource={forecasts} pagination={false} columns={[
          { title: 'Ngày', dataIndex: 'target_date' },
          { title: 'Lượng xuất dự báo', dataIndex: 'predicted_quantity', render: quantity },
          { title: 'Trạng thái', dataIndex: 'status' },
        ]} />
      </>}
    </>}
  </Space>
}
