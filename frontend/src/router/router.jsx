import { Routes, Route, Navigate } from 'react-router-dom'

import LoginPage from '../layout/login/login.jsx'
import Admin from '../layout/admin/admin.jsx'
import Manager from '../layout/manager/manager.jsx'
import Staff from '../layout/staff/staff.jsx'
import OverviewAdmin from '../pages/admin/overview.jsx'
import OverviewManager from '../pages/manager/overview.jsx'
import OverviewStaff from '../pages/staff/overview.jsx'
import RequireRole from './RequireRole.jsx'
import { getRoleHomePath, getStoredAuth } from '../utils/auth.js'

function Router() {
  const auth = getStoredAuth()
  return (
    <Routes>
      <Route
        path="/"
        element={<Navigate to={auth?.role ? getRoleHomePath(auth.role) : '/login'} replace />}
      />
      <Route path="/login" element={<LoginPage />} />

      <Route
        path="/admin"
        element={
          <RequireRole role="admin">
            <Admin />
          </RequireRole>
        }
      >
        <Route index element={<Navigate to="overview" replace />} />
        <Route path="overview" element={<OverviewAdmin />} />
      </Route>

      <Route
        path="/manager"
        element={
          <RequireRole role="manager">
            <Manager />
          </RequireRole>
        }
      >
        <Route index element={<Navigate to="overview" replace />} />
        <Route path="overview" element={<OverviewManager />} />
      </Route>

      <Route
        path="/staff"
        element={
          <RequireRole role="staff">
            <Staff />
          </RequireRole>
        }
      >
        <Route index element={<Navigate to="overview" replace />} />
        <Route path="overview" element={<OverviewStaff />} />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default Router
