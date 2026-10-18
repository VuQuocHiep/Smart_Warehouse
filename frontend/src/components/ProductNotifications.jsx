import { useEffect, useRef, useState } from 'react'
import { useLocation } from 'react-router-dom'
import { Badge, Button, Drawer, Empty, Space, Tag, Typography, message, notification } from 'antd'
import { BellOutlined } from '@ant-design/icons'
import { shelfApi } from '../api/shelf'

const displayMessage = value => value?.replace(/^\[Giả lập\]\s*/, '')

export default function ProductNotifications({ className }) {
  const location = useLocation()
  const [rows, setRows] = useState([])
  const [open, setOpen] = useState(false)
  const [error, setError] = useState('')
  const [notice, contextHolder] = notification.useNotification()
  const seen = useRef(new Set())
  const token = localStorage.getItem('accessToken')
  const enabled = !!token && location.pathname !== '/login'

  useEffect(() => {
    seen.current = new Set()
    if (!enabled) return
    let active = true
    let pending = false
    const poll = async () => {
      if (pending) return
      pending = true
      try {
        const data = await shelfApi.alerts()
        if (!active) return
        setRows(data)
        setError('')
        const fresh = data.filter(({ alert, read }) => !read && !alert.resolvedAt && !seen.current.has(alert.id))
        data.forEach(({ alert }) => seen.current.add(alert.id))
        if (fresh.length) notice.warning({
          title: `${fresh.length} cảnh báo bảo quản sản phẩm`,
          description: displayMessage(fresh[0].alert.message),
          duration: 8,
          onClick: () => setOpen(true),
        })
      } catch { if (active) setError('Không tải được thông báo. Hệ thống sẽ thử lại sau 5 giây.') }
      finally { pending = false }
    }
    poll()
    const timer = setInterval(poll, 5000)
    return () => { active = false; clearInterval(timer) }
  }, [enabled, token, notice])

  if (!enabled) return null
  const read = async id => {
    try {
      await shelfApi.markRead(id)
      setRows(items => items.map(item => item.alert.id === id ? { ...item, read: true } : item))
    } catch (e) { message.error(e.message) }
  }
  return <>{contextHolder}
    <Badge count={rows.filter(r => !r.read).length} offset={[-3, 5]} size="small">
      <Button
        aria-label={`Thông báo, ${rows.filter(r => !r.read).length} chưa đọc`}
        aria-haspopup="dialog"
        aria-expanded={open}
        className={className}
        title="Thông báo cảnh báo sản phẩm"
        icon={<BellOutlined />}
        shape="circle"
        type="text"
        onClick={() => setOpen(true)}
      />
    </Badge>
    <Drawer title="Cảnh báo bảo quản sản phẩm" open={open} onClose={() => setOpen(false)} size="large">
      <Typography.Paragraph type="secondary">Cảnh báo dùng chung cho người dùng hệ thống. Trạng thái đã đọc được lưu riêng cho tài khoản của bạn. Hiển thị 100 cảnh báo gần nhất.</Typography.Paragraph>
      {error && <Typography.Paragraph type="danger">{error}</Typography.Paragraph>}
      {!rows.length && !error && <Empty description="Chưa có cảnh báo sản phẩm" />}
      {rows.map(({ alert, read: isRead }) => <article key={alert.id} style={{ padding: '16px 0', borderBottom: '1px solid #eee' }}>
        <Space wrap><Tag color={alert.resolvedAt ? 'green' : 'red'}>{alert.resolvedAt ? 'Đã kết thúc' : 'Vượt ngưỡng'}</Tag>{!isRead && <Tag color="blue">Chưa đọc</Tag>}</Space>
        <Typography.Paragraph style={{ marginTop: 8 }}>{displayMessage(alert.message)}</Typography.Paragraph>
        <Space wrap><Typography.Text type="secondary">{new Date(alert.createdAt).toLocaleString('vi-VN')}</Typography.Text>
          {!isRead && <Button size="small" onClick={() => read(alert.id)}>Đánh dấu đã đọc</Button>}</Space>
      </article>)}
    </Drawer>
  </>
}
