import { useState } from 'react'
import { useDispatch } from 'react-redux'
import { useNavigate } from 'react-router-dom'
import { App as AntdApp } from 'antd'

import { api } from '../../api/api.js'
import { setUser } from '../../redux/userSlice.js'
import { decodeJwt, getRoleHomePath, getRolesFromToken, persistAuth } from '../../utils/auth.js'
import warehouseHero from '../../assets/warehouse-login-hero.png'

const roles = [
  { key: 'admin', label: 'Admin', description: 'Quản trị hệ thống', color: '#1167f2' },
  { key: 'manager', label: 'Manager', description: 'Quản lý kho', color: '#12a66a' },
  { key: 'staff', label: 'Staff', description: 'Nhân viên kho', color: '#ff8a00' },
]

function CubeLogo({ small = false }) {
  return (
    <span className={small ? 'logoMark logoMarkSmall' : 'logoMark'} aria-hidden="true">
      <i />
      <b />
    </span>
  )
}

export default function LoginPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [selectedRole, setSelectedRole] = useState('admin')
  const [showPassword, setShowPassword] = useState(false)
  const [remember, setRemember] = useState(true)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const navigate = useNavigate()
  const dispatch = useDispatch()
  const { message } = AntdApp.useApp()

  const handleSubmit = async (event) => {
    event.preventDefault()
    setError('')
    setLoading(true)

    try {
      const response = await api.post('/auth/login', { email, password })
      const token = response?.token

      if (!response?.authentication || !token) {
        throw new Error('Đăng nhập không thành công')
      }

      const tokenRoles = getRolesFromToken(token)
      const role = tokenRoles.includes(selectedRole) ? selectedRole : tokenRoles[0]

      if (!role) {
        throw new Error('Token không chứa vai trò hợp lệ')
      }

      persistAuth({
        token,
        refreshToken: response.refreshToken,
        role,
      })

      const payload = decodeJwt(token)
      dispatch(
        setUser({
          email: payload?.sub || email,
          role,
          roles: tokenRoles,
          token,
        })
      )

      if (!remember) {
        localStorage.removeItem('rememberLogin')
      } else {
        localStorage.setItem('rememberLogin', 'true')
      }

      const activeRoleInfo = roles.find((item) => item.key === role)
      message.success(`Đăng nhập ${activeRoleInfo?.label || role} thành công`)
      navigate(getRoleHomePath(role), { replace: true })
    } catch (err) {
      setError(err.message || 'Không thể đăng nhập, vui lòng thử lại')
    } finally {
      setLoading(false)
    }
  }

  return (
    <main className="loginPage">
      <section className="brandPanel">
        <header className="topBar">
          <div className="brand">
            <CubeLogo />
            <strong>SMART WAREHOUSE</strong>
          </div>
          <div className="topLinks">
            <span className="topLinkIcon">◎</span>
            <span>Tiếng Việt</span>
            <span className="chevron">⌄</span>
            <i />
            <span className="topLinkIcon">?</span>
            <span>Trợ giúp</span>
          </div>
        </header>

        <div className="heroCopy">
          <h1>Quản lý kho thông minh dễ dàng hơn</h1>
          <p>Theo dõi tồn kho, RFID, thiết bị IoT và cảnh báo trong một nền tảng duy nhất.</p>
        </div>

        <div className="warehouseScene" aria-hidden="true">
          <img src={warehouseHero} alt="" />
          <div className="orbitLines">
            <span />
            <span />
            <span />
            <span />
          </div>
        </div>

        <div className="featureStrip">
          <article>
            <b className="blueIcon">⌁</b>
            <strong>RFID</strong>
            <span>Quét và cập nhật hàng hóa nhanh chóng</span>
          </article>
          <article>
            <b className="greenIcon">☁</b>
            <strong>IoT</strong>
            <span>Giám sát nhiệt độ, độ ẩm theo thời gian thực</span>
          </article>
          <article>
            <b className="greenIcon">⬡</b>
            <strong>Tồn kho</strong>
            <span>Theo dõi tồn kho chính xác, tức thời</span>
          </article>
          <article>
            <b className="orangeIcon">♧</b>
            <strong>Cảnh báo</strong>
            <span>Cảnh báo ngưỡng và sự cố kịp thời</span>
          </article>
          <article>
            <b className="violetIcon">↕</b>
            <strong>Nhập / Xuất kho</strong>
            <span>Quản lý phiếu nhập, phiếu xuất dễ dàng</span>
          </article>
        </div>
      </section>

      <section className="loginPanel">
        <form className="loginCard" onSubmit={handleSubmit}>
          <div className="formBrand">
            <CubeLogo small />
            <strong>SMART WAREHOUSE</strong>
          </div>
          <h2>Đăng nhập hệ thống</h2>
          <p>Sử dụng tài khoản được cấp để truy cập Smart Warehouse</p>

          <label className="field">
            <span>♙</span>
            <input
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              type="email"
              placeholder="Email hoặc tên đăng nhập"
              autoComplete="email"
              required
            />
          </label>

          <label className="field">
            <span>▣</span>
            <input
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              type={showPassword ? 'text' : 'password'}
              placeholder="Mật khẩu"
              autoComplete="current-password"
              required
              minLength={6}
            />
            <button type="button" className="iconButton" onClick={() => setShowPassword((value) => !value)}>
              {showPassword ? '◉' : '◌'}
            </button>
          </label>

          <label className="roleLabel" htmlFor="role">
            Vai trò
          </label>
          <div className="roleSelect">
            <span className="roleLeadIcon">♚</span>
            <select
              id="role"
              value={selectedRole}
              onChange={(event) => setSelectedRole(event.target.value)}
            >
              {roles.map((role) => (
                <option key={role.key} value={role.key}>
                  {role.label}
                </option>
              ))}
            </select>
            <div className="roleMenu">
              {roles.map((role) => (
                <button
                  type="button"
                  key={role.key}
                  className={role.key === selectedRole ? 'active' : ''}
                  onClick={() => setSelectedRole(role.key)}
                >
                  <span style={{ '--role-color': role.color }} />
                  <b>{role.label}</b>
                  <small>{role.description}</small>
                </button>
              ))}
            </div>
          </div>

          {error ? <div className="loginError">{error}</div> : null}

          <div className="formMeta">
            <label>
              <input
                type="checkbox"
                checked={remember}
                onChange={(event) => setRemember(event.target.checked)}
              />
              Ghi nhớ đăng nhập
            </label>
            <a href="/login">Quên mật khẩu?</a>
          </div>

          <button className="loginButton" type="submit" disabled={loading}>
            <span>⇥</span>
            {loading ? 'Đang đăng nhập...' : 'Đăng nhập'}
          </button>

          <div className="secureNote">
            <span>♢</span>
            <p>Hệ thống dành cho quản trị kho, quản lý và nhân viên.</p>
            <strong><small>♢</small>Đăng nhập an toàn <i /> Dữ liệu được bảo vệ</strong>
          </div>
        </form>
      </section>
    </main>
  )
}
