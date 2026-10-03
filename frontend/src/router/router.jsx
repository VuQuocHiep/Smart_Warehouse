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
import Product from '../pages/admin/product.jsx'
import Warehouse from '../pages/admin/warehouse.jsx'
import Shelve from '../pages/admin/shelve.jsx'
import Stock_In from '../pages/admin/stock-in.jsx'
import Stock_Out from '../pages/admin/stock-out.jsx'
import Rfid from "../pages/admin/rfid.jsx"
import Weighing from '../pages/admin/weighing.jsx'
import Iot from "../pages/admin/iot.jsx"
import Alert from "../pages/admin/alert.jsx"
import History from "../pages/admin/history.jsx"
import User from "../pages/admin/user.jsx"
import Setting from "../pages/admin/setting.jsx"
import HistoryStaff from '../pages/staff/history.jsx'
import RfidStaff from '../pages/staff/rfid.jsx'
import ShelveStaff from '../pages/staff/shelve.jsx'
import Stock_InStaff from '../pages/staff/stock-in.jsx'
import Stock_OutStaff from '../pages/staff/stock-out.jsx'
import WeighingStaff from '../pages/staff/weighing.jsx'
import AlertManager from '../pages/manager/alert.jsx'
import HistoryManager from '../pages/manager/history.jsx'
import IotManager from '../pages/manager/iot.jsx'
import ProductManager from '../pages/manager/product.jsx'
import ReportManager from '../pages/manager/report.jsx'
import RfidManager from '../pages/manager/rfid.jsx'
import ShelveManager from '../pages/manager/shelve.jsx'
import Stock_InManager from '../pages/manager/stock-in.jsx'
import Stock_OutManager from '../pages/manager/stock-out.jsx'
import WarehouseManager from '../pages/manager/warehouse.jsx'
import WeighingManager from '../pages/manager/weighing.jsx'
import AddUser from '../pages/admin/addUser.jsx'
import UpdateUser from '../pages/admin/updateUser.jsx'
import ScannerStaff from '../pages/staff/scanner.jsx'
function Router() {
  const auth = getStoredAuth()
  return (
    <Routes>
      <Route path="/" element={<Navigate to={auth?.role ? getRoleHomePath(auth.role) : '/login'} replace />}/>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/admin" element={<RequireRole role="admin"> <Admin /></RequireRole>}>
        <Route index element={<Navigate to="overview" replace />} />
        <Route path="overview" element={<OverviewAdmin />} />
        <Route path="product" element={<Product/>}/>
        <Route path="warehouse" element={<Warehouse/>}/>
        <Route path="shelve" element={<Shelve/>}/>
        <Route path="stock-in" element={<Stock_In/>}/>
        <Route path="stock-out" element={<Stock_Out/>}/>
        <Route path="rfid" element={<Rfid/>}/>
        <Route path="weighing" element={<Weighing/>}/>
        <Route path="iot" element={<Iot/>}/>
        <Route path="alert" element={<Alert/>}/>
        <Route path="history" element={<History/>}/>
        <Route path="user" element={<User/>}/>
        <Route path="setting" element={<Setting/>}/>
        <Route path='addUser' element={<AddUser/>}/>
        <Route path='updateUser/:id' element={<UpdateUser/>}/>
      </Route>
      <Route path="/manager" element={<RequireRole role="manager"><Manager /></RequireRole>}>
        <Route index element={<Navigate to="overview" replace />} />
        <Route path="overview" element={<OverviewManager />} />
        <Route path="product" element={<ProductManager/>}/>
        <Route path="warehouse" element={<WarehouseManager/>}/>
        <Route path="shelve" element={<ShelveManager/>}/>
        <Route path="stock-in" element={<Stock_InManager/>}/>
        <Route path="stock-out" element={<Stock_OutManager/>}/>
        <Route path="rfid" element={<RfidManager/>}/>
        <Route path="weighing" element={<WeighingManager/>}/>
        <Route path="iot" element={<IotManager/>}/>
        <Route path="alert" element={<AlertManager/>}/>
        <Route path="history" element={<HistoryManager/>}/>
        <Route path="report" element={<ReportManager/>}/>
      </Route>
      <Route path="/staff" element={<RequireRole role="staff"><Staff /></RequireRole>}>
        <Route index element={<Navigate to="overview" replace />} />
        <Route path="overview" element={<OverviewStaff />} />
        <Route path="history" element={<HistoryStaff/>}/>
        <Route path="rfid" element={<RfidStaff/>}/>
        <Route path="shelve" element={<ShelveStaff/>}/>
        <Route path="stock-in" element={<Stock_InStaff/>}/>
        <Route path="stock-out" element={<Stock_OutStaff/>}/>
        <Route path="weighing" element={<WeighingStaff/>}/>
        <Route path="scanner" element={<ScannerStaff/>}/>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default Router
