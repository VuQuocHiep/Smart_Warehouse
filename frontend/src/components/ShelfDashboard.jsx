import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Alert, Button, Card, Empty, Form, Input, Modal, Select, Space, Table, Tag, Typography, message } from 'antd'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { shelfApi } from '../api/shelf'
import { getAllProduct } from '../api/product'
import { warehouseApi } from '../api/warehouse'
import WarehouseSelector from './WarehouseSelector'
import { sensorValue, SENSOR_STALE_MS } from '../utils/warehouseDisplay'
import { formatDateTime } from '../utils/date'

const range = (min, max, unit) => min == null && max == null ? 'Chưa cấu hình'
  : `${min ?? '…'} – ${max ?? '…'} ${unit}`
const outside = (value, min, max) => value != null && ((min != null && value < min) || (max != null && value > max))

export default function ShelfDashboard({ readOnly = false }) {
  const [rows, setRows] = useState([])
  const [receivedAt, setReceivedAt] = useState(0)
  const [products, setProducts] = useState([])
  const [warehouses, setWarehouses] = useState([])
  const [error, setError] = useState('')
  const [searchParams, setSearchParams] = useSearchParams()
  const selected = warehouses.find(w => w.warehouse.warehouseId === searchParams.get('warehouse')) || warehouses[0]
  const visibleRows = rows.filter(r => r.shelf.warehouseId === selected?.warehouse.warehouseId)
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [editing, setEditing] = useState(null)
  const [open, setOpen] = useState(false)
  const [form] = Form.useForm()

  useEffect(() => {
    if (readOnly) return
    getAllProduct().then(p => {
      setProducts(Array.isArray(p) ? p : p?.data || [])
    }).catch(e => message.error(e.message))
  }, [readOnly])

  const load = async () => {
    setLoading(true)
    try {
      const [data, overview] = await Promise.all([shelfApi.list(), warehouseApi.monitoring()])
      setRows(data); setWarehouses(overview); setReceivedAt(Date.now()); setError('')
    }
    catch (e) { setError(e.message || 'Không tải được kệ hàng') }
    finally { setLoading(false) }
  }
  useEffect(() => {
    let active = true
    let pending = false
    const poll = async () => {
      if (pending) return
      pending = true
      try {
        const [data, overview] = await Promise.all([shelfApi.list(), warehouseApi.monitoring()])
        if (active) { setRows(data); setWarehouses(overview); setReceivedAt(Date.now()); setError('') }
      }
      catch (e) { if (active) setError(e.message || 'Không tải được kệ hàng') }
      finally { pending = false }
    }
    poll()
    const timer = setInterval(poll, 5000)
    return () => { active = false; clearInterval(timer) }
  }, [])

  const edit = (shelf = null) => {
    setEditing(shelf)
    form.resetFields()
    if (shelf) form.setFieldsValue(shelf)
    else form.setFieldsValue({ warehouseId: selected?.warehouse.warehouseId, productIds: [] })
    setOpen(true)
  }
  const save = async values => {
    setSaving(true)
    try {
      await shelfApi.save({ ...values, deviceCode: values.deviceCode || editing?.deviceCode || `SHELF_${crypto.randomUUID()}` }, editing?.id)
      setOpen(false)
      setSearchParams({ warehouse: values.warehouseId })
      message.success('Đã lưu kệ hàng')
      await load()
    } catch (e) { message.error(e.message) }
    finally { setSaving(false) }
  }

  return <main className="warehouse-monitor-page">
    <Space wrap style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
      <div><Typography.Title level={2}>Sản phẩm trong kệ</Typography.Title>
        <Typography.Text type="secondary">Chọn kho ở bên dưới để xem các kệ và điều kiện bảo quản sản phẩm.</Typography.Text></div>
      <Space><Button icon={<ReloadOutlined />} loading={loading} onClick={load}>Tải lại</Button>
        {!readOnly && <Button type="primary" icon={<PlusOutlined />} disabled={!selected} onClick={() => edit()}>Thêm kệ</Button>}</Space>
    </Space>
    {error && <Alert type="error" showIcon title={error} />}
    <WarehouseSelector warehouses={warehouses} selectedId={selected?.warehouse.warehouseId} onSelect={id => setSearchParams({ warehouse: id })} now={receivedAt} />
    <div className="shelf-section-header"><Typography.Title level={3} style={{ margin: 0 }}>{selected?.warehouse.name || 'Kho hàng'} · {visibleRows.length} kệ</Typography.Title>
      {selected && <Tag color="blue">Môi trường kho</Tag>}</div>
    <Alert type="info" showIcon style={{ marginBottom: 20 }} title="Các kệ dùng chung số đo môi trường của kho. Cấu hình ngưỡng riêng cho từng sản phẩm tại trang Sản phẩm. Sản phẩm vượt ngưỡng sẽ có thông báo ở chuông phía trên." />
    {!visibleRows.length && <Empty description={`Chưa có kệ trong ${selected?.warehouse.name || 'kho này'}`} />}
    <div style={{ display: 'grid', gap: 20 }}>
      {visibleRows.map(({ shelf, products: items }) => {
        const reading = selected.reading
        const stale = !reading.recordedAt || receivedAt - new Date(reading.recordedAt).getTime() > SENSOR_STALE_MS
        return <Card key={shelf.id} title={shelf.name} extra={!readOnly && <Button onClick={() => edit(shelf)}>Sửa kệ</Button>}>
          <Space wrap style={{ marginBottom: 16 }}>
            <Tag>{selected.warehouse.name}</Tag>
            <Tag color={stale ? 'orange' : 'blue'}>{sensorValue(reading.temperature, '°C')} / {sensorValue(reading.humidity, '%')}</Tag>
            <Typography.Text type="secondary">{reading.recordedAt ? `Lần cuối: ${formatDateTime(reading.recordedAt)}` : 'Chưa nhận dữ liệu'}</Typography.Text>
            {stale && <Tag color="orange">{reading.recordedAt ? 'Dữ liệu cũ' : 'Chờ dữ liệu'}</Tag>}
          </Space>
          <Table rowKey="productId" dataSource={items} pagination={false} scroll={{ x: 650 }} locale={{ emptyText: 'Chưa gán sản phẩm vào kệ' }} columns={[
            { title: 'SKU', dataIndex: 'sku' },
            { title: 'Sản phẩm', dataIndex: 'name' },
            { title: 'Nhiệt độ bảo quản', render: (_, p) => range(p.minTemperature, p.maxTemperature, '°C') },
            { title: 'Độ ẩm bảo quản', render: (_, p) => range(p.minHumidity, p.maxHumidity, '%') },
            { title: 'Trạng thái', render: (_, p) => {
              const configured = [p.minTemperature, p.maxTemperature, p.minHumidity, p.maxHumidity].some(v => v != null)
              const bad = outside(reading.temperature, p.minTemperature, p.maxTemperature) || outside(reading.humidity, p.minHumidity, p.maxHumidity)
              return <Tag color={!configured || stale ? 'default' : bad ? 'red' : 'green'}>{!configured ? 'Chưa có ngưỡng' : stale ? 'Chờ số đo mới' : bad ? 'Vượt ngưỡng' : 'Trong ngưỡng'}</Tag>
            } },
          ]} />
        </Card>
      })}
    </div>
    <Modal title={editing ? 'Cập nhật kệ' : 'Thêm kệ hàng'} open={open} onCancel={() => setOpen(false)} onOk={() => form.submit()} confirmLoading={saving} okText="Lưu" cancelText="Hủy">
      <Form form={form} layout="vertical" onFinish={save} initialValues={{ productIds: [] }}>
        <Form.Item name="name" label="Tên kệ" rules={[{ required: true, whitespace: true, message: 'Nhập tên kệ' }]}><Input maxLength={100} /></Form.Item>
        <Form.Item name="warehouseId" label="Kho" rules={[{ required: true, message: 'Chọn kho' }]}><Select options={warehouses.map(({ warehouse: w }) => ({ value: w.warehouseId, label: w.name }))} /></Form.Item>
        <Typography.Paragraph type="secondary">Các kệ dùng chung số đo môi trường của kho. Thiết bị được gắn trực tiếp với kho.</Typography.Paragraph>
        <Form.Item name="productIds" label="Các sản phẩm trong kệ"><Select mode="multiple" showSearch optionFilterProp="label" options={products.map(p => ({ value: p.productId, label: `${p.sku} · ${p.name}` }))} /></Form.Item>
      </Form>
    </Modal>
  </main>
}
