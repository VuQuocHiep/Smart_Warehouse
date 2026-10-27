import { useEffect, useState } from 'react';
import { scaleApi } from '../api/scale';

export default function useScaleStatus() {
  const [status, setStatus] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    let timer;
    const poll = async () => {
      try {
        const value = await scaleApi.status();
        if (active) { setStatus(value); setError(''); }
      } catch (err) {
        if (active) { setStatus(null); setError(err.message || 'Không kết nối được máy chủ'); }
      }
      if (active) timer = setTimeout(poll, 1000);
    };
    poll();
    return () => { active = false; clearTimeout(timer); };
  }, []);
  return { status, error };
}
