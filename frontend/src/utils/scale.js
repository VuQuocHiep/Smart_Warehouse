export const canCaptureScale = (status) => Boolean(status?.online && !status.pending &&
  status.reading?.status === 'READY' && status.reading?.stable && status.reading?.calibrated &&
  status.reading?.tared && !status.reading?.overloaded && status.reading?.weightGrams > 0 &&
  status.reading?.grossGrams >= 0 && status.reading?.grossGrams <= 5000);
