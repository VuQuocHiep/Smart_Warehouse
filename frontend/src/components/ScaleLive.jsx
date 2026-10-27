import { Alert, Space, Statistic, Tag, Typography } from 'antd';
import './ScaleLive.css';

const labels = {
  READY: 'Số đo ổn định', UNSTABLE: 'Đang chờ ổn định', UNCALIBRATED: 'Chưa hiệu chuẩn',
  TARE_REQUIRED: 'Cần trừ bì', OVERLOAD: 'Quá tải 5 kg — lấy bớt hàng ngay',
  ADC_SATURATED: 'Tín hiệu bão hòa — kiểm tra tải và dây nối', DISCONNECTED: 'Không đọc được HX711',
  NEGATIVE: 'Khối lượng âm — kiểm tra lại bì', SETTLING: 'Đang chờ số đo mới', COMMAND_PENDING: 'Đang xử lý lệnh',
  AUTO_ZERO: 'Thiết lập lần đầu: để mặt cân trống, đang lấy mốc 0',
  STALE: 'Đang chờ mẫu cân mới',
  SIGNAL_FAULT: 'Lỗi tín hiệu HX711 — kiểm tra chân DT, SCK, GND và nguồn',
};

export default function ScaleLive({ status, error }) {
  const reading = status?.reading;
  const online = status?.online;
  const problem = reading?.overloaded || ['OVERLOAD', 'ADC_SATURATED', 'DISCONNECTED', 'SIGNAL_FAULT', 'NEGATIVE'].includes(reading?.status);
  return <Space orientation="vertical" style={{ width: '100%' }}>
    <Space wrap>
      <Tag color={online ? 'green' : 'red'}>{online ? 'ESP đang kết nối' : 'Cân chưa kết nối'}</Tag>
      <Tag>Load cell 5 kg · đơn vị gram</Tag>
      {online && <Tag color={problem ? 'red' : reading?.stable ? 'green' : 'orange'}>{labels[reading?.status] || reading?.status}</Tag>}
    </Space>
    <div className="scale-display" data-state={!online ? 'offline' : problem ? 'error' : 'online'} role="status" aria-live="polite">
      <Statistic title="KHỐI LƯỢNG TỊNH" value={online && reading?.weightGrams != null ? reading.weightGrams : '—'} precision={2} suffix="g" />
      <div className="scale-display__detail">
        {online && reading?.grossGrams != null ? `Tổng tải cả bì: ${reading.grossGrams.toLocaleString('vi-VN', { maximumFractionDigits: 2 })} g` : 'Đang chờ dữ liệu từ cân'}
      </div>
    </div>
    {error && <Alert type="error" showIcon title={error} />}
    {online && problem && <Alert type="error" showIcon title={labels[reading?.status] || 'Tải cân không hợp lệ'} />}
    {!online && <Typography.Text type="secondary">Kiểm tra nguồn ESP, Wi-Fi, địa chỉ máy chủ và khóa thiết bị. Số đo cũ không dùng để lập phiếu.</Typography.Text>}
    <Typography.Text type="secondary">{(status?.settings?.percent ?? 0) > 0 ?
      `Sai số cho phép: mức lớn hơn giữa ±${status.settings.absoluteGrams} g và ±${status.settings.percent}% khối lượng dự kiến.` :
      `Sai số cho phép: ±${status?.settings?.absoluteGrams ?? 50} g.`}</Typography.Text>
  </Space>;
}
