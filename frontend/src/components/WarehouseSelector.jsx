import { HomeOutlined } from '@ant-design/icons'
import './warehouse-monitoring.css'
import { readingStatus, sensorValue } from '../utils/warehouseDisplay'

export default function WarehouseSelector({ warehouses, selectedId, onSelect, now }) {
  return <div className="warehouse-selector" aria-label="Chọn kho hàng">
    {warehouses.map(({ warehouse, reading, shelfCount, productCount }) => <button
      key={warehouse.warehouseId} type="button" className={`warehouse-choice ${selectedId === warehouse.warehouseId ? 'is-selected' : ''}`}
      aria-pressed={selectedId === warehouse.warehouseId} onClick={() => onSelect(warehouse.warehouseId)}>
      <div className="warehouse-choice-top"><span className="warehouse-choice-icon"><HomeOutlined /></span>
      </div>
      <strong>{warehouse.name}</strong>
      <span className="warehouse-choice-status">{readingStatus(reading, now)}</span>
      <div className="warehouse-choice-values"><span><small>Nhiệt độ</small><b>{sensorValue(reading.temperature, '°C')}</b></span>
        <span><small>Độ ẩm</small><b>{sensorValue(reading.humidity, '%')}</b></span></div>
      <div className="warehouse-choice-footer"><span>{shelfCount} kệ · {productCount} sản phẩm</span>
        <span>{selectedId === warehouse.warehouseId ? 'Đang chọn' : 'Xem kho →'}</span></div>
    </button>)}
  </div>
}
