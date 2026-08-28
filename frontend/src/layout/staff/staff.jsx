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
  RadarChartOutlined,
  SearchOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { Avatar, Badge, Button, Input, Layout, Menu, Popover, Space, Typography } from 'antd'

import { clearUser } from '../../redux/userSlice.js'
import { clearAuth } from '../../utils/auth.js'
import './staff.scss'

const { Header, Sider, Content } = Layout
const { Text } = Typography

const menuItems = [
  {
    key: '/staff/overview',
    icon: <DashboardOutlined />,
    label: <Link to="/staff/overview">Dashboard</Link>,
  },
  {
    key: '/staff/stock-in',
    icon: <ImportOutlined />,
    label: <Link to="/staff/stock-in">Nhập kho</Link>,
  },
  {
    key: '/staff/stock-out',
    icon: <ExportOutlined />,
    label: <Link to="/staff/stock-out">Xuất kho</Link>,
  },
  {
    key: '/staff/rfid',
    icon: <RadarChartOutlined />,
    label: <Link to="/staff/rfid">RFID</Link>,
  },
  {
    key: '/staff/weighing',
    icon: <BoxPlotOutlined />,
    label: <Link to="/staff/weighing">Cân hàng</Link>,
  },
  {
    key: '/staff/shelve',
    icon: <BoxPlotOutlined />,
    label: <Link to="/staff/shelve">Kệ kho</Link>,
  },
  {
    key: '/staff/history',
    icon: <HistoryOutlined />,
    label: <Link to="/staff/history">Lịch sử công việc</Link>,
  },
]

const getDisplayName = (user) => {
  const lastname = user?.lastname || user?.lastName
  const firstname = user?.firstname || user?.firstName
  const fullName = [lastname, firstname].filter(Boolean).join(' ').trim()
  return fullName || user?.email || 'Staff'
}

export default function Staff() {
  const [collapsed, setCollapsed] = useState(false)
  const location = useLocation()
  const navigate = useNavigate()
  const dispatch = useDispatch()
  const user = useSelector((state) => state.user.user)
  const displayName = getDisplayName(user)

  const selectedKeys = useMemo(() => {
    const activeItem = menuItems.find((item) => location.pathname.startsWith(item.key))
    return [activeItem?.key || '/staff/overview']
  }, [location.pathname])

  const handleLogout = () => {
    clearAuth()
    dispatch(clearUser())
    navigate('/login', { replace: true })
  }

  const accountContent = (
    <div className="staff-account-popover">
      <div className="staff-account-profile">
        <Avatar className="staff-avatar" icon={<UserOutlined />} size={48} />
        <div>
          <strong>{displayName}</strong>
          <span>{user?.role || 'staff'}</span>
        </div>
      </div>

      <div className="staff-account-info">
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
          <strong>{user?.role || 'staff'}</strong>
        </div>
      </div>

      <Button block danger icon={<LogoutOutlined />} onClick={handleLogout} type="primary">
        Đăng xuất
      </Button>
    </div>
  )

  return (
    <Layout className="staff-layout">
      <Sider
        breakpoint="lg"
        className="staff-sidebar"
        collapsed={collapsed}
        collapsedWidth={86}
        onCollapse={setCollapsed}
        trigger={null}
        width={320}
      >
        <div className={collapsed ? 'staff-brand staff-brand--collapsed' : 'staff-brand'}>
          <div className="staff-logo-mark">
            <InboxOutlined />
          </div>
          {!collapsed && <strong className="staff-brand-text">SMART WAREHOUSE</strong>}
        </div>

        <Menu
          className="staff-menu"
          items={menuItems}
          mode="inline"
          selectedKeys={selectedKeys}
          theme="light"
        />

        <Button
          className={collapsed ? 'staff-collapse-button staff-collapse-button--centered' : 'staff-collapse-button'}
          icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
          onClick={() => setCollapsed(!collapsed)}
          type="text"
        >
          {!collapsed && 'Thu gọn'}
        </Button>
      </Sider>

      <Layout className="staff-main">
        <Header className="staff-header">
          <Button
            className="staff-header-menu-button"
            icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setCollapsed(!collapsed)}
            type="text"
          />

          <Input
            allowClear
            className="staff-search"
            placeholder="Tìm kiếm hàng hóa, mã RFID, phiếu..."
            prefix={<SearchOutlined className="staff-search-icon" />}
            size="large"
          />

          <Space align="center" className="staff-account-area" size={24}>
            <Badge count={3} offset={[-3, 5]} size="small">
              <Button className="staff-icon-button" icon={<BellOutlined />} shape="circle" type="text" />
            </Badge>

            <Popover arrow={false} content={accountContent} placement="bottomRight" trigger="click">
              <button className="staff-account-trigger" type="button">
                <Avatar className="staff-avatar" icon={<UserOutlined />} size={44} />
                <Text className="staff-name" strong>
                  {displayName}
                </Text>
                <DownOutlined className="staff-down-icon" />
              </button>
            </Popover>
          </Space>
        </Header>

        <Content className="staff-content">
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  )
}
