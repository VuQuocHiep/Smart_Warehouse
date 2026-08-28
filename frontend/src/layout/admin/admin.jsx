import { useMemo, useState } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom'
import {
  BellOutlined,
  BoxPlotOutlined,
  DashboardOutlined,
  DownOutlined,
  ExportOutlined,
  HistoryOutlined,
  ImportOutlined,
  InboxOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  ProductOutlined,
  RadarChartOutlined,
  SearchOutlined,
  SettingOutlined,
  UserOutlined,
  WifiOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import { Avatar, Badge, Button, Input, Layout, Menu, Popover, Space, Typography } from 'antd'

import { clearUser } from '../../redux/userSlice.js'
import { clearAuth } from '../../utils/auth.js'
import './admin.scss'

const { Header, Sider, Content } = Layout
const { Text } = Typography

const menuItems = [
  {
    key: '/admin/overview',
    icon: <DashboardOutlined />,
    label: <Link to="/admin/overview">Dashboard</Link>,
  },
  {
    key: '/admin/product',
    icon: <ProductOutlined />,
    label: <Link to="/admin/product">Sản phẩm</Link>,
  },
  {
    key: '/admin/warehouse',
    icon: <InboxOutlined />,
    label: <Link to="/admin/warehouse">Kho hàng</Link>,
  },
  {
    key: '/admin/shelve',
    icon: <BoxPlotOutlined />,
    label: <Link to="/admin/shelve">Kệ kho</Link>,
  },
  {
    key: '/admin/stock-in',
    icon: <ImportOutlined />,
    label: <Link to="/admin/stock-in">Nhập kho</Link>,
  },
  {
    key: '/admin/stock-out',
    icon: <ExportOutlined />,
    label: <Link to="/admin/stock-out">Xuất kho</Link>,
  },
  {
    key: '/admin/rfid',
    icon: <RadarChartOutlined />,
    label: <Link to="/admin/rfid">RFID</Link>,
  },
  {
    key: '/admin/weighing',
    icon: <BoxPlotOutlined />,
    label: <Link to="/admin/weighing">Cân hàng</Link>,
  },
  {
    key: '/admin/iot',
    icon: <WifiOutlined />,
    label: <Link to="/admin/iot">IoT</Link>,
  },
  {
    key: '/admin/alert',
    icon: <WarningOutlined />,
    label: <Link to="/admin/alert">Cảnh báo</Link>,
  },
  {
    key: '/admin/history',
    icon: <HistoryOutlined />,
    label: <Link to="/admin/history">Lịch sử</Link>,
  },
  {
    key: '/admin/user',
    icon: <UserOutlined />,
    label: <Link to="/admin/user">Người dùng</Link>,
  },
  {
    key: '/admin/setting',
    icon: <SettingOutlined />,
    label: <Link to="/admin/setting">Cài đặt</Link>,
  },
]

export default function Admin() {
  const [collapsed, setCollapsed] = useState(false)
  const location = useLocation()
  const navigate = useNavigate()
  const dispatch = useDispatch()
  const user = useSelector((state) => state.user.user)

  const selectedKeys = useMemo(() => {
    const activeItem = menuItems.find((item) => location.pathname.startsWith(item.key))
    return [activeItem?.key || '/admin/overview']
  }, [location.pathname])

  const handleLogout = () => {
    clearAuth()
    dispatch(clearUser())
    navigate('/login', { replace: true })
  }

  const accountContent = (
    <div className="admin-account-popover">
      <div className="admin-account-profile">
        <Avatar className="admin-avatar" icon={<UserOutlined />} size={48} />
        <div>
          <strong>{user?.email || 'Admin'}</strong>
          <span>{user?.role || 'admin'}</span>
        </div>
      </div>

      <div className="admin-account-info">
        <div>
          <span>Email</span>
          <strong>{user?.email || 'Chưa có thông tin'}</strong>
        </div>
        <div>
          <span>Vai trò</span>
          <strong>{user?.role || 'admin'}</strong>
        </div>
      </div>

      <Button block danger icon={<LogoutOutlined />} onClick={handleLogout} type="primary">
        Đăng xuất
      </Button>
    </div>
  )

  return (
    <Layout className="admin-layout">
      <Sider
        breakpoint="lg"
        collapsed={collapsed}
        collapsedWidth={86}
        onCollapse={setCollapsed}
        className="admin-sidebar"
        trigger={null}
        width={320}
      >
        <div className={collapsed ? 'admin-brand admin-brand--collapsed' : 'admin-brand'}>
          <div className="admin-logo-mark">
            <InboxOutlined />
          </div>
          {!collapsed && <strong className="admin-brand-text">SMART WAREHOUSE</strong>}
        </div>

        <Menu
          className="admin-menu"
          items={menuItems}
          mode="inline"
          selectedKeys={selectedKeys}
          theme="light"
        />
      </Sider>

      <Layout className="admin-main">
        <Header className="admin-header">
          <Button
            className="admin-header-menu-button"
            icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setCollapsed(!collapsed)}
            type="text"
          />

          <Input
            allowClear
            className="admin-search"
            placeholder="Tìm kiếm..."
            prefix={<SearchOutlined className="admin-search-icon" />}
            size="large"
          />

          <Space align="center" className="admin-account-area" size={24}>
            <Badge count={3} offset={[-3, 5]} size="small">
              <Button className="admin-icon-button" icon={<BellOutlined />} shape="circle" type="text" />
            </Badge>

            <Popover
              arrow={false}
              content={accountContent}
              placement="bottomRight"
              trigger="click"
            >
              <button className="admin-account-trigger" type="button">
                <Avatar className="admin-avatar" icon={<UserOutlined />} size={44} />
                <Text className="admin-name" strong>
                  {`${user?.lastname || ''} ${user?.firstname || ''}`.trim() || 'Admin'}
                </Text>
                <DownOutlined className="admin-down-icon" />
              </button>
            </Popover>
          </Space>
        </Header>
        <Content className="admin-content">
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  )
}
