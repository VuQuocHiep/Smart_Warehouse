import { Navigate } from 'react-router-dom'

import { getRoleHomePath, getStoredAuth } from '../utils/auth.js'

export default function RequireRole({ role, children }) {
  const auth = getStoredAuth()

  if (!auth?.token || !auth?.role) {
    return <Navigate to="/login" replace />
  }

  if (auth.role !== role) {
    return <Navigate to={getRoleHomePath(auth.role)} replace />
  }

  return children
}
