import { useCallback, useEffect, useState } from 'react';
import QRCode from 'qrcode';
import { Modal, Button, Input } from '../../components/common';
import { scannerPairingService, pairingQrContent } from '../../services/scannerPairingService';
import { websocketService } from '../../services/websocketService';

/**
 * Ghép điện thoại với quầy này: hiện mã QR (mã 6 số, dùng một lần, hết hạn sau vài phút),
 * điện thoại đã đăng nhập quét QR là ghép xong. Mã quét của điện thoại sau đó vào giỏ của quầy này.
 */
export default function ScannerPairingModal({ isOpen, onClose, terminal, onTerminalChange }) {
  const [code, setCode] = useState(null);
  const [qrDataUrl, setQrDataUrl] = useState('');
  const [secondsLeft, setSecondsLeft] = useState(0);
  const [pairings, setPairings] = useState([]);
  const [nameDraft, setNameDraft] = useState(terminal?.name || '');
  const [notice, setNotice] = useState('');
  const [error, setError] = useState('');

  const terminalId = terminal?.id;

  const refreshCode = useCallback(() => {
    if (!terminalId) return;
    scannerPairingService
      .createPairingCode(terminalId)
      .then(async (res) => {
        const dataUrl = await QRCode.toDataURL(pairingQrContent(res.code), { width: 220, margin: 1 });
        setCode(res.code);
        setSecondsLeft(res.expiresInSeconds || 300);
        setQrDataUrl(dataUrl);
        setError('');
      })
      .catch((err) => {
        setError(err.response?.data?.message || 'Không tạo được mã ghép cặp. Kiểm tra kết nối máy chủ.');
      });
  }, [terminalId]);

  const refreshPairings = useCallback(() => {
    if (!terminalId) return;
    scannerPairingService
      .listPairings(terminalId)
      .then(setPairings)
      .catch(() => {
        // giữ danh sách cũ
      });
  }, [terminalId]);

  // PosPage chỉ mount modal khi mở, nên mỗi lần mở là một mã mới
  useEffect(() => {
    refreshCode();
    refreshPairings();
  }, [refreshCode, refreshPairings]);

  // Đếm ngược; hết hạn thì tự tạo mã mới
  useEffect(() => {
    if (!isOpen || !code) return;
    const timer = setInterval(() => {
      setSecondsLeft((s) => {
        if (s <= 1) {
          refreshCode();
          return 0;
        }
        return s - 1;
      });
    }, 1000);
    return () => clearInterval(timer);
  }, [isOpen, code, refreshCode]);

  // Điện thoại vừa ghép hoặc bị gỡ: cập nhật danh sách; mã đã dùng thì tạo mã mới
  useEffect(() => {
    if (!isOpen) return;
    return websocketService.on('scanner-pairing', (event) => {
      if (event.type === 'PAIRED') {
        setNotice(`Đã ghép với ${event.deviceName || 'điện thoại'} (${event.fullName || event.username}).`);
        refreshCode();
      }
      refreshPairings();
    });
  }, [isOpen, refreshCode, refreshPairings]);

  const handleRename = async () => {
    const name = nameDraft.trim();
    if (!name || name === terminal?.name) return;
    try {
      onTerminalChange(await scannerPairingService.rename(terminalId, name));
    } catch {
      setError('Không đổi được tên quầy.');
    }
  };

  const handleUnpair = async (pairingId) => {
    try {
      await scannerPairingService.unpair(terminalId, pairingId);
      refreshPairings();
    } catch {
      setError('Không gỡ được điện thoại.');
    }
  };

  const mm = String(Math.floor(secondsLeft / 60)).padStart(2, '0');
  const ss = String(secondsLeft % 60).padStart(2, '0');

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Ghép điện thoại quét mã với quầy này"
      maxWidth="520px"
      footer={
        <Button variant="secondary" onClick={onClose}>
          Đóng (Esc)
        </Button>
      }
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: '14px', fontSize: '13px' }}>
        {!terminal ? (
          <div>Đang đăng ký quầy với máy chủ...</div>
        ) : (
          <>
            <Input
              label="Tên quầy (hiện trên điện thoại sau khi ghép)"
              value={nameDraft}
              maxLength={100}
              onChange={(e) => setNameDraft(e.target.value)}
              onBlur={handleRename}
              onKeyDown={(e) => e.key === 'Enter' && handleRename()}
            />

            <div style={{ display: 'flex', gap: '16px', alignItems: 'center' }}>
              <div style={{ background: '#ffffff', padding: '8px', borderRadius: '8px', lineHeight: 0 }}>
                {qrDataUrl ? (
                  <img src={qrDataUrl} alt={`Mã QR ghép cặp ${code}`} width={200} height={200} />
                ) : (
                  <div style={{ width: 200, height: 200 }} />
                )}
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                <div>Trên app điện thoại: đăng nhập, chọn <b>Ghép quầy</b> rồi quét mã QR này.</div>
                <div>Hoặc nhập mã:</div>
                <div style={{ fontSize: '28px', fontWeight: 700, letterSpacing: '4px', fontFamily: 'var(--font-mono, monospace)' }}>
                  {code || '------'}
                </div>
                <div style={{ color: 'var(--text-secondary)' }}>
                  Hết hạn sau {mm}:{ss} · mỗi mã chỉ dùng một lần
                </div>
              </div>
            </div>

            {notice && <div style={{ color: 'var(--success, #16a34a)' }}>{notice}</div>}
            {error && <div style={{ color: 'var(--danger, #dc2626)' }}>{error}</div>}

            <div>
              <div style={{ fontWeight: 600, marginBottom: '6px' }}>Điện thoại đã ghép ({pairings.length})</div>
              {pairings.length === 0 && <div style={{ color: 'var(--text-secondary)' }}>Chưa có điện thoại nào.</div>}
              {pairings.map((p) => (
                <div
                  key={p.pairingId}
                  style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '6px 0', borderBottom: '1px solid var(--border-subtle)' }}
                >
                  <span>
                    {p.deviceName} · {p.username}
                  </span>
                  <Button variant="secondary" size="sm" onClick={() => handleUnpair(p.pairingId)}>
                    Gỡ
                  </Button>
                </div>
              ))}
            </div>
          </>
        )}
      </div>
    </Modal>
  );
}
