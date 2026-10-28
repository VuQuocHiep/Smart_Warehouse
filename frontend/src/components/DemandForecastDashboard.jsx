import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, Card, Empty, Select, Space, Statistic, Table, Tag, Tooltip, Typography } from 'antd'
import { ReloadOutlined, LineChartOutlined } from '@ant-design/icons'
import { demandApi } from '../api/demand'
import { formatDate as shortDate, formatDateTime as shortDateTime } from '../utils/date'

const { Title, Text } = Typography
const pairKey = (pair) => JSON.stringify([pair.warehouseId, pair.productId])
const quantity = (value) => value == null ? '—' : Number(value).toLocaleString('vi-VN', { maximumFractionDigits: 0 })

function quantityScale(values) {
  const rawStep = Math.max(1, ...values) / 4
  const magnitude = 10 ** Math.floor(Math.log10(rawStep))
  const step = Math.max(1, [1, 2, 5, 10].find((value) => value * magnitude >= rawStep) * magnitude)
  return { maximum: step * 4, ticks: [0, 1, 2, 3, 4].map((value) => value * step) }
}

function QuantityAxis({ ticks, y, left, right, top, bottom }) {
  return <g fill="#64748b" fontSize="12">
    <text x={left} y={top - 16}>Số lượng</text>
    {ticks.map((tick) => <g key={tick}>
      <line x1={left} x2={right} y1={y(tick)} y2={y(tick)} stroke="#e2e8f0" strokeDasharray="4 4" />
      <line x1={left - 5} x2={left} y1={y(tick)} y2={y(tick)} stroke="#94a3b8" />
      <text x={left - 10} y={y(tick)} textAnchor="end" dominantBaseline="middle">{quantity(tick)}</text>
    </g>)}
    <line x1={left} x2={left} y1={top} y2={bottom} stroke="#94a3b8" />
    <line x1={left} x2={right} y1={bottom} y2={bottom} stroke="#94a3b8" />
  </g>
}

function ForecastChart({ history, forecasts }) {
  const container = useRef(null)
  const [width, setWidth] = useState(920)
  const historyDays = width >= 1120 ? 21 : 14
  const observations = history.slice(-historyDays).map((row) => ({ date: row.date, value: row.export_quantity, predicted: false }))
  const predictions = forecasts.filter((row) => row.predicted_quantity != null).map((row) => ({ date: row.target_date, value: row.predicted_quantity, predicted: true }))
  const points = [...observations, ...predictions]
  const hasPoints = points.length > 0
  useEffect(() => {
    if (!container.current) return undefined
    const observer = new ResizeObserver(([entry]) => setWidth(Math.max(1, Math.round(entry.contentRect.width))))
    observer.observe(container.current)
    return () => observer.disconnect()
  }, [hasPoints])
  if (!points.length) return <div ref={container}><Empty description="Chưa có lịch sử xuất kho." /></div>
  const { maximum, ticks } = quantityScale(points.map((point) => point.value))
  const left = 65, right = Math.max(left + 1, width - 15), top = 40, bottom = 220
  const spacing = (right - left - 24) / Math.max(1, points.length - 1)
  const dateRows = Math.max(1, Math.ceil(34 / Math.max(1, spacing)))
  const chartHeight = bottom + 30 + dateRows * 18
  const x = (index) => left + 12 + index * spacing
  const y = (value) => bottom - value * (bottom - top) / maximum
  const line = (begin, end) => points.slice(begin, end).map((point, index) => `${x(begin + index)},${y(point.value)}`).join(' ')
  return <figure style={{ margin: 0 }}>
    <div ref={container} style={{ width: '100%', minWidth: 0 }}>
    <svg viewBox={`0 0 ${width} ${chartHeight}`} role="img" aria-label={`${observations.length} ngày lịch sử xuất kho và ${predictions.length} ngày dự báo`} style={{ display: 'block', width: '100%', height: chartHeight }}>
      <QuantityAxis ticks={ticks} y={y} left={left} right={right} top={top} bottom={bottom} />
      <polyline points={line(0, observations.length)} fill="none" stroke="#1677ff" strokeWidth="2.5" />
      <polyline points={line(Math.max(0, observations.length - 1), points.length)} fill="none" stroke="#13a886" strokeWidth="2.5" strokeDasharray="6 3" />
      {points.map((point, index) => {
        const date = shortDate(point.date)
        const label = point.predicted ? 'Số lượng dự đoán' : 'Số lượng xuất kho'
        return <Tooltip key={`${point.date}-${index}`} placement="top" trigger={['hover', 'focus']} mouseEnterDelay={0}
          title={<div><div>Ngày: {date}</div><div>{label}: <strong>{quantity(point.value)}</strong></div></div>}>
          <g tabIndex={0} aria-label={`Ngày ${date}, ${label}: ${quantity(point.value)}`} style={{ cursor: 'pointer' }}>
            <circle cx={x(index)} cy={y(point.value)} r="10" fill="transparent" />
            <circle cx={x(index)} cy={y(point.value)} r="3" fill={point.predicted ? '#13a886' : '#1677ff'} />
          </g>
        </Tooltip>
      })}
      {points.map((point, index) => <g key={`date-${point.date}`}>
        <line x1={x(index)} x2={x(index)} y1={bottom} y2={bottom + 6} stroke="#cbd5e1" />
        <text x={x(index)} y={bottom + 22 + (index % dateRows) * 18} textAnchor="middle" dominantBaseline="middle" fontSize="12" fill={point.predicted ? '#07835d' : '#334155'}>{shortDate(point.date)}</text>
      </g>)}
    </svg>
    </div>
    <figcaption><Tag color="blue">{observations.length} ngày lịch sử xuất kho</Tag><Tag color="green">{predictions.length} ngày dự báo</Tag></figcaption>
  </figure>
}

function ReplenishmentChart({ days }) {
  if (!days.length) return <Empty description="Chưa đủ dữ liệu để tính lượng nhập." />
  const { maximum, ticks } = quantityScale(days.map((day) => day.required_quantity))
  const left = 80, right = 900, top = 40, bottom = 230
  const y = (value) => bottom - value * (bottom - top) / maximum
  const slot = (right - left) / days.length
  const barWidth = Math.min(65, slot * .6)
  return <figure style={{ margin: 0 }}>
    <div style={{ width: '100%', overflowX: 'auto' }}>
    <svg viewBox="0 0 920 285" role="img" aria-label="Số lượng cần nhập từng ngày trong 7 ngày" style={{ display: 'block', width: '100%', minWidth: 650 }}>
      <QuantityAxis ticks={ticks} y={y} left={left} right={right} top={top} bottom={bottom} />
      {days.map((day, index) => {
        const height = bottom - y(day.required_quantity)
        const center = left + (index + .5) * slot
        const date = shortDate(day.target_date)
        return <g key={day.target_date}>
          <Tooltip title={`${date}: cần nhập ${quantity(day.required_quantity)}`}>
            <rect tabIndex={0} aria-label={`${date}: cần nhập ${day.required_quantity}`} x={center - barWidth / 2} y={bottom - Math.max(2, height)} width={barWidth} height={Math.max(2, height)} rx="3" fill="#fa8c16" />
          </Tooltip>
          <text x={center} y={y(day.required_quantity) - 10} textAnchor="middle" fontSize="12">{quantity(day.required_quantity)}</text>
          <line x1={center} x2={center} y1={bottom} y2={bottom + 6} stroke="#94a3b8" />
          <text x={center} y="258" textAnchor="middle" fontSize="12">{date}</text>
        </g>
      })}
    </svg>
    </div>
    <figcaption>Lượng bổ sung riêng từng ngày · giả định nhận hàng trong ngày · đã tính tồn an toàn.</figcaption>
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
  const [replenishment, setReplenishment] = useState(null)
  const [schedule, setSchedule] = useState(null)
  const [warehouseFilter, setWarehouseFilter] = useState(null)

  const load = useCallback(async () => {
    const responses = await Promise.allSettled([demandApi.catalog(), demandApi.latest(), demandApi.replenishment(), demandApi.status()])
    const [pairs, latest, restock, state] = responses
    setError('')
    if (pairs.status === 'fulfilled') setCatalog(pairs.value)
    if (latest.status === 'fulfilled') setResult(latest.value)
    if (restock.status === 'fulfilled') {
      setReplenishment(restock.value)
      setResult(restock.value.forecast)
    } else setReplenishment(null)
    if (state.status === 'fulfilled') {
      setSchedule(state.value)
      const recent = state.value.jobs?.[0]
      if (recent) setJob({ jobId: recent.job_id, status: recent.status, message: recent.message })
    }
    const failure = responses.find((value) => value.status === 'rejected')
    if (failure) setError(failure.reason.message)
    setLoading(false)
  }, [])

  useEffect(() => {
    load()
    const timer = window.setInterval(load, 15000)
    return () => window.clearInterval(timer)
  }, [load])
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
    try { setJob(await demandApi.startForecast()) }
    catch (failure) { setError(failure.message) }
    finally { setStarting(false) }
  }

  const visibleCatalog = catalog.filter((pair) => !warehouseFilter || pair.warehouseId === warehouseFilter)
  const selected = visibleCatalog.find((pair) => pairKey(pair) === selection) || visibleCatalog.find((pair) => result?.pairs?.some((p) => pairKey(p) === pairKey(pair))) || visibleCatalog[0]
  const matches = (row) => row.warehouse_id === selected?.warehouseId && row.product_id === selected?.productId
  const forecasts = (result?.forecasts || []).filter(matches).sort((a, b) => a.horizon - b.horizon).slice(0, 7)
  const history = (result?.history || []).filter(matches).sort((a, b) => a.date.localeCompare(b.date))
  const validForecasts = forecasts.length === 7 && forecasts.every((row) => row.status === 'OK' && row.predicted_quantity != null)
  const selectedMethod = forecasts[0]?.method || result?.metrics?.selected_method
  const metric = result?.metrics?.test?.daily?.[result?.metrics?.selected_method === 'per_series' ? 'selected' : result?.metrics?.selected_method]
  const stale = result?.forecastDate && result.forecastDate !== new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' }).format(new Date())
  const restock = replenishment?.runId === result?.runId ? replenishment?.items?.find(matches) : null
  const filterAlerts = (rows) => (rows || []).filter((row) => !warehouseFilter || row.warehouse_id === warehouseFilter)
  const alertColumns = [
    { title: 'Kho', dataIndex: 'warehouseName' }, { title: 'SKU', dataIndex: 'sku' },
    { title: 'Sản phẩm', dataIndex: 'productName' }, { title: 'Tồn', dataIndex: 'stock', render: quantity },
    { title: 'Xuất dự báo 7 ngày', dataIndex: 'exportWeek', render: quantity },
    { title: 'Tồn an toàn', dataIndex: 'safetyStock', render: quantity },
    { title: 'Cần nhập hôm nay', dataIndex: 'requiredToday', render: quantity },
    { title: 'Cần nhập 7 ngày', dataIndex: 'requiredWeek', render: quantity },
    { title: 'Ngày đầu cần bổ sung', dataIndex: 'firstRequiredDate', render: shortDate },
  ]
  return <Space orientation="vertical" size="large" style={{ width: '100%' }}>
    <div><Title level={2}>Dự báo xuất kho và nhu cầu nhập</Title><Text type="secondary">7 ngày gồm hôm nay và 6 ngày tiếp theo</Text></div>
    {error && <Alert type="error" showIcon title={error} />}
    {stale && <Alert type="warning" showIcon title={`Kết quả được tạo cho ngày ${shortDate(result.forecastDate)}. Chạy lại để cập nhật đến hôm qua.`} />}
    <Space wrap>
      <Button icon={<ReloadOutlined />} loading={loading} onClick={() => { setLoading(true); load() }}>Tải lại</Button>
      <Button type="primary" icon={<LineChartOutlined />} loading={starting || Boolean(activeJobId)} disabled={!catalog.length} onClick={start}>Cập nhật dự báo</Button>
      {job && <Tag color={job.status === 'SUCCEEDED' ? 'green' : 'blue'}>{job.status}</Tag>}
      <Text type="secondary">{catalog.length} cặp kho/sản phẩm</Text>
    </Space>
    <Text type="secondary">
      Tự huấn luyện lúc 05:00 thứ Hai hằng tuần (giờ Việt Nam). Dự báo cập nhật hằng ngày bằng model đã lưu.
      {result?.modelTrainedAt && ` Huấn luyện gần nhất: ${shortDateTime(result.modelTrainedAt)}.`}
      {schedule?.nextTrainingAt && ` Lịch tiếp theo: ${shortDateTime(schedule.nextTrainingAt)}.`}
    </Text>
    {result?.dataSource && <Tag color="blue">Lịch sử nhập/xuất kho</Tag>}
    {job?.status === 'FAILED' && <Alert type="warning" showIcon title={job.message || 'Job thất bại; hệ thống giữ kết quả trước đó.'} />}
    <Select allowClear placeholder="Tất cả kho" aria-label="Lọc kho dự báo và cảnh báo" style={{ width: '100%', maxWidth: 400 }} value={warehouseFilter} onChange={(id) => setWarehouseFilter(id || null)}
      options={[...new Map(catalog.map((p) => [p.warehouseId, { value: p.warehouseId, label: p.warehouseName }])).values()]} />
    {!catalog.length ? <Empty description="Gán sản phẩm vào kệ kho để bắt đầu." /> : <>
      <Select aria-label="Chọn kho và sản phẩm" showSearch optionFilterProp="label" style={{ width: '100%', maxWidth: 650 }} value={selected && pairKey(selected)} onChange={setSelection}
        options={visibleCatalog.map((pair) => ({ value: pairKey(pair), label: `${pair.warehouseName} · ${pair.sku} · ${pair.productName}` }))} />
      {!forecasts.length ? <Empty description="Chưa có dự báo cho cặp kho/sản phẩm này. Job khởi tạo tự chạy khi đủ lịch sử và dịch vụ ML sẵn sàng." /> : <>
        <Space wrap size="large">
          <Card><Statistic title="Lượng xuất dự báo 7 ngày" value={validForecasts ? forecasts.reduce((sum, row) => sum + row.predicted_quantity, 0) : '—'} precision={0} /></Card>
          <Card><Statistic title="Tồn hiện tại" value={restock?.stock ?? '—'} /></Card>
          <Card><Statistic title="Cần nhập hôm nay" value={restock?.requiredToday ?? '—'} /></Card>
          <Card><Statistic title="Cần nhập 7 ngày" value={restock?.requiredWeek ?? '—'} /></Card>
          <Card><Statistic title="Sai số trung bình (MAE)" value={metric?.mae} precision={2} /></Card>
        </Space>
        <Card title="Lịch sử và lượng xuất dự báo"><ForecastChart history={history} forecasts={forecasts} /></Card>
        <Card title="Lượng nhập cần bổ sung từng ngày"><ReplenishmentChart days={restock?.days || []} /></Card>
        {restock && restock.status !== 'OK' && <Alert showIcon type="warning" title={restock.status === 'UNALLOCATED_STOCK' ? 'Tồn chưa được phân bổ hoặc đối soát đầy đủ theo kho.' : 'Lịch sử chưa đủ hoặc đã cũ; chưa thể tính lượng nhập.'} />}
        <Text type="secondary">Dữ liệu đến {shortDate(result.dataCutoff)} · Phương pháp: {selectedMethod} · MAE đo bằng đơn vị hàng.</Text>
        <Table rowKey="target_date" dataSource={forecasts} pagination={false} columns={[
          { title: 'Ngày', dataIndex: 'target_date', render: shortDate },
          { title: 'Lượng xuất dự báo', dataIndex: 'predicted_quantity', render: quantity },
          { title: 'Trạng thái', dataIndex: 'status' },
        ]} />
      </>}
    </>}
    <Text type="secondary">Cảnh báo áp dụng cho tất cả sản phẩm trong kho đã lọc. Tổng 7 ngày đã bao gồm hôm nay.
      {replenishment?.stockAsOf && ` Tồn cập nhật: ${shortDateTime(replenishment.stockAsOf)}.`}</Text>
    {replenishment?.items?.some((item) => (!warehouseFilter || item.warehouse_id === warehouseFilter) && item.status !== 'OK') && <Alert type="info" showIcon title="Một số sản phẩm chưa đủ dữ liệu để đánh giá; danh sách trống không đồng nghĩa tất cả hàng đều đủ tồn." />}
    <Card title="Cần nhập thêm hôm nay"><Table rowKey={(row) => `${row.warehouse_id}:${row.product_id}`} dataSource={filterAlerts(replenishment?.todayAlerts)} columns={alertColumns} scroll={{ x: 1100 }} pagination={{ pageSize: 10 }} /></Card>
    <Card title="Cần nhập thêm trong 7 ngày"><Table rowKey={(row) => `${row.warehouse_id}:${row.product_id}`} dataSource={filterAlerts(replenishment?.weekAlerts)} columns={alertColumns} scroll={{ x: 1100 }} pagination={{ pageSize: 10 }} /></Card>
  </Space>
}
