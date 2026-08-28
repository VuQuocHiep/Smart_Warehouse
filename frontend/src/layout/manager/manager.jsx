import { useMemo, useState } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom'
import {
  BellOutlined,
  BoxPlotOutlined,
  DashboardOutlined,
  DownOutlined,
  ExportOutlined,
  FileTextOutlined,
  HistoryOutlined,
  ImportOutlined,
  InboxOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  ProductOutlined,
  RadarChartOutlined,
  SearchOutlined,
  UserOutlined,
  WifiOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import { Avatar, Badge, Button, Input, Layout, Menu, Popover, Space, Typography } from 'antd'

import { clearUser } from '../../redux/userSlice.js'
import { clearAuth } from '../../utils/auth.js'
import './manager.scss'

const { Header, Sider, Content } = Layout
const { Text } = Typography

const menuItems = [
  {
    key: '/manager/overview',
    icon: <DashboardOutlined />,
    label: <Link to="/manager/overview">Dashboard</Link>,
  },
  {
    key: '/manager/product',
    icon: <ProductOutlined />,
    label: <Link to="/manager/product">Sản phẩm</Link>,
  },
  {
    key: '/manager/warehouse',
    icon: <InboxOutlined />,
    label: <Link to="/manager/warehouse">Kho hàng</Link>,
  },
  {
    key: '/manager/shelve',
    icon: <BoxPlotOutlined />,
    label: <Link to="/manager/shelve">Kệ kho</Link>,
  },
  {
    key: '/manager/stock-in',
    icon: <ImportOutlined />,
    label: <Link to="/manager/stock-in">Nhập kho</Link>,
  },
  {
    key: '/manager/stock-out',
    icon: <ExportOutlined />,
    label: <Link to="/manager/stock-out">Xuất kho</Link>,
  },
  {
    key: '/manager/rfid',
    icon: <RadarChartOutlined />,
    label: <Link to="/manager/rfid">RFID</Link>,
  },
  {
    key: '/manager/weighing',
    icon: <BoxPlotOutlined />,
    label: <Link to="/manager/weighing">Cân hàng</Link>,
  },
  {
    key: '/manager/iot',
    icon: <WifiOutlined />,
    label: <Link to="/manager/iot">IoT</Link>,
  },
  {
    key: '/manager/alert',
    icon: <WarningOutlined />,
    label: <Link to="/manager/alert">Cảnh báo</Link>,
  },
  {
    key: '/manager/history',
    icon: <HistoryOutlined />,
    label: <Link to="/manager/history">Lịch sử</Link>,
  },
  {
    key: '/manager/report',
    icon: <FileTextOutlined />,
    label: <Link to="/manager/report">Báo cáo</Link>,
  },
]

const getDisplayName = (user) => {
  const lastname = user?.lastname || user?.lastName
  const firstname = user?.firstname || user?.firstName
  const fullName = [lastname, firstname].filter(Boolean).join(' ').trim()
  return fullName || user?.email || 'Manager'
}

export default function Manager() {
  const [collapsed, setCollapsed] = useState(false)
  const location = useLocation()
  const navigate = useNavigate()
  const dispatch = useDispatch()
  const user = useSelector((state) => state.user.user)
  const displayName = getDisplayName(user)

  const selectedKeys = useMemo(() => {
    const activeItem = menuItems.find((item) => location.pathname.startsWith(item.key))
    return [activeItem?.key || '/manager/overview']
  }, [location.pathname])

  const handleLogout = () => {
    clearAuth()
    dispatch(clearUser())
    navigate('/login', { replace: true })
  }

  const accountContent = (
    <div className="manager-account-popover">
      <div className="manager-account-profile">
        <Avatar className="manager-avatar" icon={<UserOutlined />} size={48} />
        <div>
          <strong>{displayName}</strong>
          <span>{user?.role || 'manager'}</span>
        </div>
      </div>

      <div className="manager-account-info">
        <div>
          <span>Họ tên</span>
          <strong>{displayName}</strong>
        </div>
        <div>
          <span>Email</span>
          <strong>{user?.email || 'Chưa có thông tin'}</strong>
        </div>
        <div>
          <span>Vai trò</span>
          <strong>{user?.role || 'manager'}</strong>
        </div>
      </div>

      <Button block danger icon={<LogoutOutlined />} onClick={handleLogout} type="primary">
        Đăng xuất
      </Button>
    </div>
  )

  return (
    <Layout className="manager-layout">
      <Sider
        breakpoint="lg"
        className="manager-sidebar"
        collapsed={collapsed}
        collapsedWidth={86}
        onCollapse={setCollapsed}
        trigger={null}
        width={320}
      >
        <div className={collapsed ? 'manager-brand manager-brand--collapsed' : 'manager-brand'}>
          <div className="manager-logo-mark">
            <InboxOutlined />
          </div>
          {!collapsed && <strong className="manager-brand-text">SMART WAREHOUSE</strong>}
        </div>

        <Menu
          className="manager-menu"
          items={menuItems}
          mode="inline"
          selectedKeys={selectedKeys}
          theme="light"
        />
      </Sider>

      <Layout className="manager-main">
        <Header className="manager-header">
          <Button
            className="manager-header-menu-button"
            icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setCollapsed(!collapsed)}
            type="text"
          />

          <Input
            allowClear
            className="manager-search"
            placeholder="Tìm kiếm..."
            prefix={<SearchOutlined className="manager-search-icon" />}
            size="large"
          />

          <Space align="center" className="manager-account-area" size={24}>
            <Badge count={5} offset={[-3, 5]} size="small">
              <Button className="manager-icon-button" icon={<BellOutlined />} shape="circle" type="text" />
            </Badge>

            <Popover arrow={false} content={accountContent} placement="bottomRight" trigger="click">
              <button className="manager-account-trigger" type="button">
                <Avatar className="manager-avatar" icon={<UserOutlined />} size={44} />
                <Text className="manager-name" strong>
                  {displayName}
                </Text>
                <DownOutlined className="manager-down-icon" />
              </button>
            </Popover>
          </Space>
        </Header>

        <Content className="manager-content">
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  )
}
