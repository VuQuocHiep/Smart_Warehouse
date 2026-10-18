import { useEffect, useMemo, useState } from 'react'
import {
  AlertOutlined,
  AppstoreOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  InboxOutlined,
  ProductOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  ShopOutlined,
  TagsOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { Button, Empty, Tag } from 'antd'

import { productApi } from '../api/product.js'
import { sensorApi } from '../api/sensor.js'
import { userApi } from '../api/user.js'
import { warehouseApi } from '../api/warehouse.js'
import { getRiskPrediction } from '../utils/sensorRisk.js'
import './dashboard-overview.css'

const toList = (response) => {
  if (Array.isArray(response)) return response
  if (Array.isArray(response?.data)) return response.data
  if (Array.isArray(response?.result)) return response.result
  if (Array.isArray(response?.content)) return response.content

  return []
}

const normalizeRoleName = (role) => {
  const rawValue = role?.name || role?.authority || role?.role || role

  if (!rawValue) return ''

  return String(rawValue).replace(/^ROLE_/i, '').toUpperCase()
}

const getUserRoles = (user) => {
  const roles = []

  if (Array.isArray(user?.role)) roles.push(...user.role)
  if (Array.isArray(user?.roles)) roles.push(...user.roles)
  if (Array.isArray(user?.authorities)) roles.push(...user.authorities)
  if (user?.role && !Array.isArray(user.role)) roles.push(user.role)
  if (user?.roles && !Array.isArray(user.roles)) roles.push(user.roles)

  return [...new Set(roles.map(normalizeRoleName).filter(Boolean))]
}

const formatNumber = (value) => new Intl.NumberFormat('vi-VN').format(value)

const DEFAULT_ROLE_STATS = {
  ADMIN: 0,
  MANAGER: 0,
  STAFF: 0,
  TOTAL: 0,
}

const toNumber = (value) => {
  const numberValue = Number(value)

  return Number.isFinite(numberValue) ? numberValue : 0
}

const buildRoleStatsFromUsers = (items) => {
  const stats = { ...DEFAULT_ROLE_STATS, TOTAL: items.length }

  items.forEach((item) => {
    getUserRoles(item).forEach((userRole) => {
      if (stats[userRole] !== undefined) {
        stats[userRole] += 1
      }
    })
  })

  return stats
}

const toRoleStats = (response) => {
  const data = response?.data && !Array.isArray(response.data) ? response.data : response

  if (Array.isArray(data)) return buildRoleStatsFromUsers(data)

  const admin = toNumber(data?.ADMIN ?? data?.admin)
  const manager = toNumber(data?.MANAGER ?? data?.manager)
  const staff = toNumber(data?.STAFF ?? data?.staff)

  return {
    ADMIN: admin,
    MANAGER: manager,
    STAFF: staff,
    TOTAL: toNumber(data?.TOTAL ?? data?.total ?? admin + manager + staff),
  }
}

function CommandMetric({ icon, label, note, tone, value }) {
  return (
    <article className={`overview-command-metric overview-command-metric--${tone}`}>
      <span>{icon}</span>
      <div>
        <small>{label}</small>
        <strong>{value}</strong>
        <em>{note}</em>
      </div>
    </article>
  )
}

function WorkflowStep({ current, label, max, tone, value }) {
  const percent = max ? Math.min(100, Math.round((value / max) * 100)) : 0

  return (
    <article className={`overview-workflow-step overview-workflow-step--${tone}`}>
      <div>
        <span>{label}</span>
        <strong>{current}</strong>
      </div>
      <div className="overview-workflow-track">
        <i style={{ width: `${percent}%` }} />
      </div>
      <small>{value} mục đang theo dõi</small>
    </article>
  )
}

export default function DashboardOverview({ role = 'admin' }) {
  const [products, setProducts] = useState([])
  const [warehouses, setWarehouses] = useState([])
  const [roleStats, setRoleStats] = useState(DEFAULT_ROLE_STATS)
  const [sensorHistory, setSensorHistory] = useState([])
  const [loading, setLoading] = useState(false)
  const [partialError, setPartialError] = useState(false)
  const [userLoadError, setUserLoadError] = useState(false)

  const loadRoleStats = async () => {
    try {
      return toRoleStats(await userApi.getRoleStats())
    } catch {
      return buildRoleStatsFromUsers(toList(await userApi.getAll()))
    }
  }

  const loadData = async () => {
    setLoading(true)
    setPartialError(false)
    setUserLoadError(false)

    const [productResult, warehouseResult, userResult, sensorResult] = await Promise.allSettled([
      productApi.getAllProduct(),
      warehouseApi.getAllWarehouse(),
      loadRoleStats(),
      sensorApi.history(60),
    ])

    if (productResult.status === 'fulfilled') setProducts(toList(productResult.value))
    if (warehouseResult.status === 'fulfilled') setWarehouses(toList(warehouseResult.value))
    if (userResult.status === 'fulfilled') setRoleStats(userResult.value)
    if (sensorResult.status === 'fulfilled') setSensorHistory(toList(sensorResult.value))

    if (userResult.status === 'rejected') {
      setRoleStats(DEFAULT_ROLE_STATS)
      setUserLoadError(true)
    }

    setPartialError([productResult, warehouseResult, userResult].some((item) => item.status === 'rejected'))
    setLoading(false)
  }

  useEffect(() => {
    loadData()
  }, [])

  const categories = useMemo(
    () => [...new Set(products.map((item) => item?.category).filter(Boolean))],
    [products],
  )
  const alerts = useMemo(
    () => sensorHistory.map((item) => getRiskPrediction(item)).filter((item) => item.riskLevel > 0),
    [sensorHistory],
  )
  const adminCount = roleStats.ADMIN
  const managerCount = roleStats.MANAGER
  const staffCount = roleStats.STAFF
  const totalUserCount = roleStats.TOTAL
  const productsMissingImage = products.filter((item) => !item?.image_url).length
  const productsReady = products.filter((item) => item?.sku && item?.name && item?.category && item?.image_url).length
  const warehousesMissingDescription = warehouses.filter((item) => !item?.description).length
  const recentProducts = products.slice(-6).reverse()
  const visibleWarehouses = warehouses.slice(0, 5)
  const roleLabel = role === 'manager' ? 'Manager Console' : 'Admin Console'
  const attentionCount = alerts.length + productsMissingImage + warehousesMissingDescription
  const workflowMax = Math.max(products.length, 1)
  const userMetricValue = userLoadError ? '—' : formatNumber(totalUserCount)
  const userMetricNote = userLoadError
    ? 'Không tải được số liệu tài khoản'
    : `${formatNumber(managerCount)} manager · ${formatNumber(staffCount)} staff`

  return (
    <main className="overview-page">
      <section className="overview-command-center">
        <div className="overview-command-copy">
          <span><AppstoreOutlined /> {roleLabel}</span>
          <h1>Trung tâm điều hành kho</h1>
          <p>Theo dõi danh mục hàng hóa, mạng lưới kho và các việc cần xử lý trong cùng một màn hình.</p>
        </div>

        <div className="overview-command-actions">
          <Tag color={partialError ? 'gold' : 'green'}>
            {partialError ? 'Dữ liệu chưa đầy đủ' : 'Hệ thống sẵn sàng'}
          </Tag>
          <Button icon={<ReloadOutlined spin={loading} />} onClick={loadData}>
            Tải lại
          </Button>
        </div>
      </section>

      <section className="overview-command-grid">
        <CommandMetric
          icon={<ProductOutlined />}
          label="Danh mục sản phẩm"
          note={`${formatNumber(productsReady)} SKU đủ thông tin`}
          tone="product"
          value={formatNumber(products.length)}
        />
        <CommandMetric
          icon={<InboxOutlined />}
          label="Mạng lưới kho"
          note={`${warehousesMissingDescription} kho thiếu mô tả`}
          tone="warehouse"
          value={formatNumber(warehouses.length)}
        />
        <CommandMetric
          icon={<TeamOutlined />}
          label="Tài khoản vận hành"
          note={userMetricNote}
          tone="people"
          value={userMetricValue}
        />
        <CommandMetric
          icon={<AlertOutlined />}
          label="Cần chú ý"
          note="Cảnh báo, dữ liệu thiếu, hồ sơ chưa hoàn thiện"
          tone="attention"
          value={formatNumber(attentionCount)}
        />
      </section>

      <section className="overview-workflow">
        <div className="overview-section-header">
          <div>
            <span>Luồng vận hành</span>
            <strong>Tình trạng xử lý trong ngày</strong>
          </div>
          <Tag color="blue">{categories.length} danh mục</Tag>
        </div>

        <div className="overview-workflow-grid">
          <WorkflowStep current="Tiếp nhận" label="Nhập kho" max={workflowMax} tone="receive" value={Math.min(products.length, 12)} />
          <WorkflowStep current="Đang lưu trữ" label="Lưu kho" max={workflowMax} tone="store" value={products.length} />
          <WorkflowStep current="Đối soát" label="Kiểm kê" max={workflowMax} tone="audit" value={productsMissingImage + warehousesMissingDescription} />
          <WorkflowStep current="Sẵn sàng" label="Xuất kho" max={workflowMax} tone="dispatch" value={productsReady} />
        </div>
      </section>

      <section className="overview-main-grid">
        <div className="overview-panel overview-panel--inventory">
          <div className="overview-section-header">
            <div>
              <span>Hàng hóa</span>
              <strong>Sản phẩm gần đây</strong>
            </div>
            <Tag color="blue">{products.length} SKU</Tag>
          </div>

          {recentProducts.length ? (
            <div className="overview-product-table">
              <div className="overview-table-head">
                <span>Sản phẩm</span>
                <span>Danh mục</span>
                <span>Đơn vị</span>
                <span>Hồ sơ</span>
              </div>
              {recentProducts.map((item) => (
                <article key={item?.productId || item?.sku || item?.name}>
                  <div>
                    <strong>{item?.name || 'Chưa đặt tên'}</strong>
                    <small>{item?.sku || 'Chưa có SKU'}</small>
                  </div>
                  <Tag>{item?.category || 'Chưa phân loại'}</Tag>
                  <span>{item?.unit || 'ITEM'}</span>
                  <Tag color={item?.image_url ? 'green' : 'gold'}>
                    {item?.image_url ? 'Đủ ảnh' : 'Thiếu ảnh'}
                  </Tag>
                </article>
              ))}
            </div>
          ) : (
            <div className="overview-empty">
              <Empty description="Chưa có sản phẩm" />
            </div>
          )}
        </div>

        <aside className="overview-panel overview-panel--attention">
          <div className="overview-section-header">
            <div>
              <span>Điều phối</span>
              <strong>Việc cần xử lý</strong>
            </div>
          </div>

          <div className="overview-action-list">
            <article>
              <AlertOutlined />
              <div>
                <strong>{alerts.length} cảnh báo môi trường</strong>
                <span>Chuyển sang Cảnh báo nếu có cấp nguy hiểm.</span>
              </div>
            </article>
            <article>
              <ProductOutlined />
              <div>
                <strong>{productsMissingImage} sản phẩm thiếu ảnh</strong>
                <span>Bổ sung ảnh để nhân viên nhận diện nhanh hơn.</span>
              </div>
            </article>
            <article>
              <InboxOutlined />
              <div>
                <strong>{warehousesMissingDescription} kho thiếu mô tả</strong>
                <span>Hoàn thiện mô tả để bàn giao ca rõ ràng.</span>
              </div>
            </article>
          </div>
        </aside>
      </section>

      <section className="overview-main-grid overview-main-grid--bottom">
        <div className="overview-panel">
          <div className="overview-section-header">
            <div>
              <span>Mạng lưới kho</span>
              <strong>Điểm lưu trữ đang quản lý</strong>
            </div>
            <Tag color="green">{warehouses.length} kho</Tag>
          </div>

          {visibleWarehouses.length ? (
            <div className="overview-warehouse-network">
              {visibleWarehouses.map((item, index) => (
                <article key={item?.warehouseId || item?.name || index}>
                  <span><ShopOutlined /></span>
                  <div>
                    <strong>{item?.name || `Kho ${index + 1}`}</strong>
                    <em>{item?.address || 'Chưa cập nhật địa chỉ'}</em>
                  </div>
                  <Tag color={item?.description ? 'green' : 'gold'}>
                    {item?.description ? 'Đã mô tả' : 'Cần bổ sung'}
                  </Tag>
                </article>
              ))}
            </div>
          ) : (
            <div className="overview-empty">
              <Empty description="Chưa có kho hàng" />
            </div>
          )}
        </div>

        <div className="overview-panel">
          <div className="overview-section-header">
            <div>
              <span>Nhân sự</span>
              <strong>Phân quyền vận hành</strong>
            </div>
          </div>

          {userLoadError && (
            <div className="overview-status-message overview-status-message--warning">
              Không tải được số liệu tài khoản. Hãy restart backend để dùng endpoint `/user/roleStats`.
            </div>
          )}

          <div className="overview-role-grid">
            <article>
              <UserOutlined />
              <span>Admin</span>
              <strong>{userLoadError ? '—' : formatNumber(adminCount)}</strong>
            </article>
            <article>
              <SafetyCertificateOutlined />
              <span>Manager</span>
              <strong>{userLoadError ? '—' : formatNumber(managerCount)}</strong>
            </article>
            <article>
              <ClockCircleOutlined />
              <span>Staff</span>
              <strong>{userLoadError ? '—' : formatNumber(staffCount)}</strong>
            </article>
            <article className="overview-role-grid--wide">
              <CheckCircleOutlined />
              <span>Tổng tài khoản</span>
              <strong>{userLoadError ? '—' : formatNumber(totalUserCount)}</strong>
            </article>
          </div>
        </div>
      </section>
    </main>
  )
}
