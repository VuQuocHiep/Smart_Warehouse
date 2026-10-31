import { useEffect, useState } from 'react';
import { Alert, Button, Card, InputNumber, Popconfirm, Space, Typography, message } from 'antd';
import ScaleLive from '../../components/ScaleLive';
import useScaleStatus from '../../hooks/useScaleStatus';
import { scaleApi } from '../../api/scale';
import { getStoredAuth } from '../../utils/auth';
import { canCaptureScale } from '../../utils/scale';

export default function WeighingPage() {
  const { status, error } = useScaleStatus();
  const canConfigure = ['admin', 'manager'].includes(getStoredAuth()?.role);
  const [reference, setReference] = useState(100);
  const [editedSettings, setEditedSettings] = useState(null);
  const absolute = editedSettings?.absoluteGrams ?? status?.settings?.absoluteGrams ?? 50;
  const percent = editedSettings?.percent ?? status?.settings?.percent ?? 0;
  const setAbsolute = (value) => setEditedSettings({ absoluteGrams: value, percent });
  const setPercent = (value) => setEditedSettings({ absoluteGrams: absolute, percent: value });
  const [checkReference, setCheckReference] = useState(100);
  const [busy, setBusy] = useState(false);
  const [command, setCommand] = useState(null);
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);
  const send = async (type) => {
    setBusy(true);
    try { setCommand(await scaleApi.command(type, reference)); }
    catch (err) { message.error(err.message); }
    finally { setBusy(false); }
  };
  const save = async () => {
    setBusy(true);
    try { await scaleApi.settings(absolute, percent); message.success('Đã lưu ngưỡng sai số'); }
    catch (err) { message.error(err.message); }
    finally { setBusy(false); }
  };
  const ack = command && status?.reading?.commandId === command.id;
  const failed = ack && !status.reading.commandResult?.startsWith('OK_');
  const expired = command && !ack && now >= new Date(command.expiresAt).getTime();
  const testTolerance = Math.max(status?.settings?.absoluteGrams ?? 50, checkReference * (status?.settings?.percent ?? 0) / 100);
  const testDeviation = Math.abs((status?.reading?.weightGrams ?? 0) - checkReference);
  return <Space orientation="vertical" size={16} style={{ width: '100%' }}>
    <Card title="Cân điện tử HX711 · 5 kg"><ScaleLive status={status} error={error} /></Card>
    <Card title="Trừ bì và hiệu chuẩn">
      <Space orientation="vertical" size={16} style={{ width: '100%' }}>
        <Alert type="info" showIcon title="Khi bật nguồn, để mặt cân trống để cân tự trừ bì rồi mới đặt hàng. Hệ số hiệu chuẩn được lưu sẵn nên không cần nhập lệnh mỗi lần dùng. Nếu chưa có hệ số: đặt vật chuẩn, nhập khối lượng và bấm Hiệu chuẩn một lần." />
        <Typography.Text type="secondary">Nút trừ bì dùng khi cần bỏ khối lượng bao bì. Nếu hiệu chuẩn lại, hãy để mặt cân trống rồi bấm trừ bì trước khi đặt vật chuẩn.</Typography.Text>
        <Typography.Text>Trừ bì chỉ đưa khối lượng tịnh về 0. Tổng tải gồm hàng, bì và phần mặt mica đã khai báo trong firmware vẫn phải ≤ 5.000 g.</Typography.Text>
        <Space wrap>
          <Popconfirm title="Đã để mặt cân trống hoặc chỉ có bao bì rỗng?" onConfirm={() => send('TARE')}>
            <Button disabled={!status?.online || Boolean(status?.pending) || busy} loading={busy}>Trừ bì về 0 g</Button>
          </Popconfirm>
          {canConfigure && <>
            <span>Quả cân chuẩn (g):</span>
            <InputNumber min={10} max={5000} value={reference} onChange={setReference} />
            <Popconfirm title={`Đã trừ bì mặt cân trống và đặt đúng quả cân ${reference} g?`} onConfirm={() => send('CALIBRATE')}>
              <Button disabled={!status?.online || Boolean(status?.pending) || busy || !reference}>Hiệu chuẩn</Button>
            </Popconfirm>
          </>}
        </Space>
        {command && <Alert showIcon type={failed || expired ? 'error' : ack ? 'success' : 'info'}
          title={expired ? 'Lệnh hết thời gian chờ; kiểm tra cân rồi thử lại' : ack ?
            (failed ? `Lệnh thất bại: ${status.reading.commandResult}` : 'ESP đã thực hiện lệnh thành công') : 'Đã gửi lệnh; đang chờ ESP xác nhận'} />}
      </Space>
    </Card>
    <Card title="Kiểm tra sai số bằng vật đã biết khối lượng">
      <Space orientation="vertical">
        <Space wrap><span>Khối lượng chuẩn để kiểm tra (g):</span>
          <InputNumber min={0.01} max={5000} value={checkReference} onChange={setCheckReference} /></Space>
        <Typography.Text>Trừ bì mặt cân trống rồi đặt vật chuẩn lên cân. Dùng thêm một vật chuẩn khác với vật đã dùng hiệu chuẩn để kiểm tra.</Typography.Text>
        {checkReference > 0 && canCaptureScale(status) && <Alert showIcon type={testDeviation > testTolerance ? 'error' : 'success'}
          title={`${testDeviation > testTolerance ? 'Sai số quá lớn' : 'Trong ngưỡng cho phép'}: lệch ${testDeviation.toFixed(2)} g / cho phép ±${testTolerance.toFixed(2)} g`} />}
      </Space>
    </Card>
    <Card title="Sai số đối chiếu hàng nhập / xuất">
      <Space orientation="vertical" size={16}>
        <Typography.Text>Khối lượng dự kiến = số lượng × trọng lượng đơn vị của sản phẩm (g). Sai lệch vượt ngưỡng sẽ báo lỗi và không nhận kết quả cân vào phiếu.</Typography.Text>
        <Space wrap>
          <span>Sai số tối thiểu (g):</span><InputNumber disabled={!canConfigure} min={0} max={500} value={absolute} onChange={setAbsolute} />
          <span>Sai số (%):</span><InputNumber disabled={!canConfigure} min={0} max={20} value={percent} onChange={setPercent} />
          {canConfigure && <Button type="primary" loading={busy} disabled={absolute == null || percent == null} onClick={save}>Lưu ngưỡng</Button>}
        </Space>
        <Typography.Text type="secondary">Mặc định ±50 g, phần trăm bằng 0%. Ví dụ: dự kiến 1.000 g → chấp nhận 950–1.050 g. Ngưỡng này là quy tắc kiểm tra hàng; hãy kiểm tra độ chính xác thực tế bằng quả cân chuẩn.</Typography.Text>
      </Space>
    </Card>
  </Space>;
}
