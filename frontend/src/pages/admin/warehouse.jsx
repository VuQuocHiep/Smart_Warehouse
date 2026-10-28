import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { Alert, Button, Card, Descriptions, Empty, Form, Input, Modal, Typography, message } from 'antd'
import { ArrowRightOutlined, EditOutlined, HomeOutlined, ReloadOutlined } from '@ant-design/icons'
import { updateWarehouse, warehouseApi } from '../../api/warehouse'
import WarehouseSelector from '../../components/WarehouseSelector'
import { readingStatus, sensorValue, SENSOR_STALE_MS } from '../../utils/warehouseDisplay'
import { formatDateTime } from '../../utils/date'

const displayDescription = warehouse => ['Kho mô phỏng: dữ liệu môi trường giả lập', 'Giám sát môi trường thực tế từ ESP32'].includes(warehouse.description)
  ? 'Giám sát môi trường kho hàng' : warehouse.description

export default function Warehouse() {
  const [rows, setRows] = useState([])
  const [selectedId, setSelectedId] = useState(null)
  const [now, setNow] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [editing, setEditing] = useState(null)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm()
  const navigate = useNavigate()
  const location = useLocation()
  const selected = rows.find(r => r.warehouse.warehouseId === selectedId) || rows[0]

  const reload = async () => {
    setLoading(true)
    try { setRows(await warehouseApi.monitoring()); setNow(Date.now()); setError('') }
    catch (e) { setError(e.message || 'Không tải được kho hàng') }
    finally { setLoading(false) }
  }
  useEffect(() => {
    let active = true
    let pending = false
    const poll = async () => {
      if (pending) return
      pending = true
      try { const data = await warehouseApi.monitoring(); if (active) { setRows(data); setNow(Date.now()); setError('') } }
      catch (e) { if (active) setError(e.message || 'Không tải được kho hàng') }
      finally { pending = false; if (active) setLoading(false) }
    }
    poll()
    const timer = setInterval(poll, 5000)
    return () => { active = false; clearInterval(timer) }
  }, [])

  const save = async values => {
    setSaving(true)
    try { await updateWarehouse({ ...values, name: editing.name }, editing.warehouseId); setEditing(null); await reload(); message.success('Đã cập nhật kho hàng') }
    catch (e) { message.error(e.message) }
    finally { setSaving(false) }
  }

  return <main className="warehouse-monitor-page">
    <div className="warehouse-monitor-header"><div>
      <span className="warehouse-monitor-kicker"><HomeOutlined /> Quản lý kho hàng</span>
      <Typography.Title level={2}>Không gian 4 kho</Typography.Title>
      <p>Chọn kho để xem môi trường, thông tin kho và các kệ hàng.</p>
    </div><Button icon={<ReloadOutlined />} loading={loading} onClick={reload}>Tải lại</Button></div>
    {error && <Alert type="error" showIcon title={error} />}
    <WarehouseSelector warehouses={rows} selectedId={selected?.warehouse.warehouseId} onSelect={setSelectedId} now={now} />
    {!rows.length && !loading && !error && <Empty description="Chưa có kho. Khởi động lại backend để cập nhật dữ liệu 4 kho." />}
    {selected && <div className="warehouse-detail-grid">
      <Card title={`Môi trường ${selected.warehouse.name}`}>
        <Alert type={selected.reading.recordedAt && now - new Date(selected.reading.recordedAt).getTime() <= SENSOR_STALE_MS ? 'success' : 'warning'} showIcon
          title={readingStatus(selected.reading, now)} />
        <div className="warehouse-sensor-grid">{[['Nhiệt độ', 'temperature', '°C'], ['Độ ẩm', 'humidity', '%'], ['Khí gas', 'gas', 'ADC']].map(([label, key, unit]) =>
          <div className="warehouse-sensor-box" key={key}><span>{label}</span><strong>{sensorValue(selected.reading[key], unit)}</strong></div>)}</div>
        <Typography.Paragraph type="secondary">{selected.reading.recordedAt ? `Cập nhật: ${formatDateTime(selected.reading.recordedAt)}` : 'Đang chờ dữ liệu môi trường kho.'}</Typography.Paragraph>
        <Button type="primary" icon={<ArrowRightOutlined />} onClick={() => navigate(`${location.pathname.split('/').slice(0, 2).join('/')}/shelve?warehouse=${encodeURIComponent(selected.warehouse.warehouseId)}`)}>Xem {selected.shelfCount} kệ của {selected.warehouse.name}</Button>
      </Card>
      <Card title="Thông tin kho" extra={<Button icon={<EditOutlined />} onClick={() => { form.setFieldsValue({ ...selected.warehouse, description: displayDescription(selected.warehouse) }); setEditing(selected.warehouse) }}>Sửa</Button>}>
        <Descriptions column={1} items={[
          { key: 'name', label: 'Tên kho', children: selected.warehouse.name },
          ...(selected.warehouse.monitoringDeviceCode ? [{ key: 'device', label: 'Thiết bị giám sát kho', children: selected.warehouse.monitoringDeviceCode }] : []),
          { key: 'address', label: 'Địa chỉ', children: selected.warehouse.address || 'Chưa cập nhật' },
          { key: 'description', label: 'Mô tả', children: displayDescription(selected.warehouse) || 'Chưa có mô tả' },
          { key: 'shelves', label: 'Kệ hàng', children: selected.shelfCount },
          { key: 'products', label: 'Loại sản phẩm', children: selected.productCount },
        ]} />
      </Card>
    </div>}
    <Modal title={`Cập nhật ${editing?.name || 'kho'}`} open={!!editing} onCancel={() => setEditing(null)} onOk={() => form.submit()} okText="Lưu" cancelText="Hủy" confirmLoading={saving}>
      <Form form={form} layout="vertical" onFinish={save}>
        <Form.Item label="Tên kho"><Input value={editing?.name} disabled /></Form.Item>
        <Form.Item label="Địa chỉ" name="address" rules={[{ required: true, whitespace: true, message: 'Vui lòng nhập địa chỉ' }]}><Input maxLength={255} /></Form.Item>
        <Form.Item label="Mô tả" name="description"><Input.TextArea rows={4} /></Form.Item>
      </Form>
    </Modal>
  </main>
}
