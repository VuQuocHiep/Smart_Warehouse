const ROLE_PATHS = {
  admin: '/admin/overview',
  manager: '/manager/overview',
  staff: '/staff/overview',
}

const normalizeRole = (role) => {
  if (!role) return null

  return String(role).replace(/^ROLE_/i, '').toLowerCase()
}

export const getRoleHomePath = (role) => ROLE_PATHS[normalizeRole(role)] || '/login'

export const decodeJwt = (token) => {
  try {
    const payload = token.split('.')[1]
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/')
    const paddedBase64 = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=')
    const json = decodeURIComponent(
      atob(paddedBase64)
        .split('')
        .map((char) => `%${`00${char.charCodeAt(0).toString(16)}`.slice(-2)}`)
        .join('')
    )

    return JSON.parse(json)
  } catch {
    return null
  }
}

export const getRolesFromToken = (token) => {
  const payload = decodeJwt(token)
  const scopes = Array.isArray(payload?.scope)
    ? payload.scope
    : String(payload?.scope || '').split(' ')

  return scopes
    .map(normalizeRole)
    .filter((role) => ['admin', 'manager', 'staff'].includes(role))
}

export const getStoredAuth = () => {
  const token = localStorage.getItem('accessToken')
  const refreshToken = localStorage.getItem('refreshToken')
  const role = localStorage.getItem('role')

  if (!token) return null

  return {
    token,
    refreshToken,
    role: normalizeRole(role) || getRolesFromToken(token)[0],
  }
}

export const persistAuth = ({ token, refreshToken, role }) => {
  localStorage.setItem('accessToken', token)

  if (refreshToken) {
    localStorage.setItem('refreshToken', refreshToken)
  }

  if (role) {
    localStorage.setItem('role', normalizeRole(role))
  }
}

export const clearAuth = () => {
  localStorage.removeItem('accessToken')
  localStorage.removeItem('refreshToken')
  localStorage.removeItem('role')
}
