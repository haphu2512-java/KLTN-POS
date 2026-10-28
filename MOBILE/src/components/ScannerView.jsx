import React, { useState, useRef, useEffect } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  TextInput,
  StyleSheet,
  ActivityIndicator,
  Animated,
  AppState,
  ScrollView
} from 'react-native';
import { CameraView, useCameraPermissions } from 'expo-camera';
import { Icon, Button } from './ui';
import { colors, fonts, radius, type } from '../theme';

// Mã mẫu để thử nhanh khi không có hàng thật trong tay
const MOCK_BARCODES = [
  { barcode: '2000000000015', name: 'Snack mực Talaethong' },
  { barcode: '2000000000022', name: 'Ô mai cầu vồng Plum' },
  { barcode: '2000000000039', name: 'Bánh gạo Poca' },
  { barcode: '2000000000046', name: 'Kẹo Golia C' },
  { barcode: '8934588012228', name: 'Pepsi lon 320ml' },
  { barcode: '8935049510123', name: 'Coca-Cola lon 320ml' }
];

const BARCODE_TYPES = ['ean13', 'ean8', 'code128', 'code39', 'upc_a', 'upc_e', 'qr'];
// Quá thời gian này mà camera chưa báo sẵn sàng thì cho người dùng mở lại camera
const CAMERA_READY_TIMEOUT_MS = 6000;

export default function ScannerView({ onBarcodeDetected, isScanningActive, isProcessing, height = 280 }) {
  const [permission, requestPermission] = useCameraPermissions();
  const [facing, setFacing] = useState('back');
  const [enableTorch, setEnableTorch] = useState(false);
  const [manualCode, setManualCode] = useState('');
  const [showManualInput, setShowManualInput] = useState(false);
  // Đổi key để gắn lại camera: khi quay lại app, hoặc người dùng bấm "Mở lại camera"
  const [cameraKey, setCameraKey] = useState(0);
  const [isCameraReady, setIsCameraReady] = useState(false);
  const [isSlowToStart, setIsSlowToStart] = useState(false);
  const [mountError, setMountError] = useState(null);

  const frameHeight = Math.round(height * 0.52);
  const animLine = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    const loop = Animated.loop(
      Animated.sequence([
        Animated.timing(animLine, { toValue: 1, duration: 1600, useNativeDriver: true }),
        Animated.timing(animLine, { toValue: 0, duration: 1600, useNativeDriver: true })
      ])
    );
    loop.start();
    return () => loop.stop();
  }, [animLine]);

  // Android hay để lại khung hình đen sau khi app chạy nền: gắn lại camera khi app trở lại
  useEffect(() => {
    let prev = AppState.currentState;
    const sub = AppState.addEventListener('change', (next) => {
      if (prev.match(/inactive|background/) && next === 'active') {
        restartCamera();
      }
      prev = next;
    });
    return () => sub.remove();
  }, []);

  useEffect(() => {
    if (isCameraReady) return undefined;
    setIsSlowToStart(false);
    const t = setTimeout(() => setIsSlowToStart(true), CAMERA_READY_TIMEOUT_MS);
    return () => clearTimeout(t);
  }, [cameraKey, isCameraReady, facing]);

  const restartCamera = () => {
    setIsCameraReady(false);
    setMountError(null);
    setCameraKey((k) => k + 1);
  };

  const handleBarcodeScanned = (event) => {
    if (!isScanningActive || isProcessing) return;
    const data = event.data || event.rawValue;
    if (data) onBarcodeDetected(String(data).trim());
  };

  const handleSubmitManual = () => {
    if (!manualCode.trim() || isProcessing) return;
    onBarcodeDetected(manualCode.trim());
    setManualCode('');
  };

  const manualPanel = (
    <View style={styles.manualPanel}>
      <View style={styles.manualRow}>
        <TextInput
          style={styles.manualInput}
          placeholder="Nhập mã vạch"
          placeholderTextColor={colors.textLight}
          value={manualCode}
          onChangeText={setManualCode}
          onSubmitEditing={handleSubmitManual}
          keyboardType="numeric"
          returnKeyType="done"
        />
        <Button label="Gửi" icon="corner-down-left" onPress={handleSubmitManual} compact style={{ minHeight: 44 }} />
      </View>
      <Text style={[type.small, { marginBottom: 6 }]}>Mã mẫu để thử nhanh</Text>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8 }}>
        {MOCK_BARCODES.map((item) => (
          <TouchableOpacity
            key={item.barcode}
            style={styles.chip}
            onPress={() => onBarcodeDetected(item.barcode)}
            activeOpacity={0.7}
          >
            <Text style={styles.chipName}>{item.name}</Text>
            <Text style={styles.chipCode}>{item.barcode}</Text>
          </TouchableOpacity>
        ))}
      </ScrollView>
    </View>
  );

  if (!permission) {
    return (
      <View style={[styles.placeholderCard, { height }]}>
        <ActivityIndicator color={colors.primary} />
        <Text style={[type.small, { marginTop: 10 }]}>Đang kiểm tra quyền camera...</Text>
      </View>
    );
  }

  if (!permission.granted) {
    return (
      <View>
        <View style={[styles.placeholderCard, { minHeight: height }]}>
          <View style={styles.placeholderIcon}>
            <Icon name="camera-off" size={26} color={colors.textMuted} />
          </View>
          <Text style={[type.heading, { textAlign: 'center' }]}>Chưa có quyền dùng camera</Text>
          <Text style={[type.small, styles.placeholderDesc]}>
            Cho phép camera để quét mã vạch sản phẩm và mã QR ghép quầy. Bạn vẫn có thể nhập mã bằng tay ở dưới.
          </Text>
          <Button
            label={permission.canAskAgain === false ? 'Mở lại sau khi bật trong Cài đặt máy' : 'Cho phép camera'}
            icon="camera"
            onPress={requestPermission}
          />
        </View>
        {manualPanel}
      </View>
    );
  }

  const laserY = animLine.interpolate({ inputRange: [0, 1], outputRange: [8, frameHeight - 10] });

  return (
    <View>
      <View style={[styles.cameraCard, { height }]}>
        {/* Camera nằm gọn trong thẻ có kích thước cố định; tắt hẳn khi rời tab Quét */}
        <CameraView
          key={cameraKey}
          style={StyleSheet.absoluteFill}
          facing={facing}
          enableTorch={enableTorch}
          barcodeScannerSettings={{ barcodeTypes: BARCODE_TYPES }}
          onBarcodeScanned={isScanningActive ? handleBarcodeScanned : undefined}
          onCameraReady={() => setIsCameraReady(true)}
          onMountError={(e) => setMountError(e?.message || 'Không mở được camera')}
        />

        {/* Khung ngắm: chỉ vẽ viền, không phủ đen cả màn hình */}
        <View style={styles.overlay} pointerEvents="none">
          <View style={[styles.frame, { height: frameHeight }]}>
            <View style={[styles.corner, styles.cTL]} />
            <View style={[styles.corner, styles.cTR]} />
            <View style={[styles.corner, styles.cBL]} />
            <View style={[styles.corner, styles.cBR]} />
            {isCameraReady && (
              <Animated.View style={[styles.laser, { transform: [{ translateY: laserY }] }]} />
            )}
          </View>
        </View>

        {/* Trạng thái camera khi chưa có hình */}
        {(!isCameraReady || mountError) && (
          <View
            style={[styles.cameraState, isSlowToStart && !mountError && styles.cameraStateSoft]}
          >
            {mountError ? (
              <>
                <Icon name="alert-triangle" size={22} color="#F6E9CC" />
                <Text style={styles.cameraStateText}>{mountError}</Text>
                <CameraRetry onPress={restartCamera} />
              </>
            ) : isSlowToStart ? (
              <>
                <Text style={styles.cameraStateText}>Camera chưa hiện hình?</Text>
                <CameraRetry onPress={restartCamera} />
              </>
            ) : (
              <>
                <ActivityIndicator color="#E2DCCE" />
                <Text style={styles.cameraStateText}>Đang mở camera...</Text>
              </>
            )}
          </View>
        )}

        {isProcessing && (
          <View style={styles.processing}>
            <ActivityIndicator size="small" color={colors.white} />
            <Text style={styles.processingText}>Đang xử lý mã...</Text>
          </View>
        )}
      </View>

      <View style={styles.controls}>
        <ControlButton
          icon={enableTorch ? 'zap-off' : 'zap'}
          label={enableTorch ? 'Tắt đèn' : 'Đèn pin'}
          active={enableTorch}
          onPress={() => setEnableTorch(!enableTorch)}
        />
        <ControlButton
          icon="refresh-cw"
          label={facing === 'back' ? 'Cam trước' : 'Cam sau'}
          onPress={() => {
            setIsCameraReady(false);
            setFacing(facing === 'back' ? 'front' : 'back');
          }}
        />
        <ControlButton
          icon="edit-3"
          label="Nhập mã"
          active={showManualInput}
          onPress={() => setShowManualInput(!showManualInput)}
        />
      </View>

      {showManualInput && manualPanel}
    </View>
  );
}

function CameraRetry({ onPress }) {
  return (
    <TouchableOpacity style={styles.retryBtn} onPress={onPress} activeOpacity={0.8}>
      <Icon name="rotate-ccw" size={14} color={colors.text} />
      <Text style={styles.retryText}>Mở lại camera</Text>
    </TouchableOpacity>
  );
}

function ControlButton({ icon, label, active, onPress }) {
  return (
    <TouchableOpacity
      style={[styles.controlBtn, active && styles.controlBtnActive]}
      onPress={onPress}
      activeOpacity={0.75}
    >
      <Icon name={icon} size={16} color={active ? colors.white : colors.text} />
      <Text style={[styles.controlLabel, active && { color: colors.white }]}>{label}</Text>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  cameraCard: {
    borderRadius: radius.xl,
    overflow: 'hidden',
    backgroundColor: colors.ink,
    borderWidth: 1,
    borderColor: colors.borderStrong
  },
  overlay: {
    ...StyleSheet.absoluteFillObject,
    alignItems: 'center',
    justifyContent: 'center'
  },
  frame: {
    width: '78%'
  },
  corner: {
    position: 'absolute',
    width: 26,
    height: 26,
    borderColor: colors.white
  },
  cTL: { top: 0, left: 0, borderTopWidth: 3, borderLeftWidth: 3, borderTopLeftRadius: 8 },
  cTR: { top: 0, right: 0, borderTopWidth: 3, borderRightWidth: 3, borderTopRightRadius: 8 },
  cBL: { bottom: 0, left: 0, borderBottomWidth: 3, borderLeftWidth: 3, borderBottomLeftRadius: 8 },
  cBR: { bottom: 0, right: 0, borderBottomWidth: 3, borderRightWidth: 3, borderBottomRightRadius: 8 },
  laser: {
    position: 'absolute',
    left: 12,
    right: 12,
    top: 0,
    height: 2,
    borderRadius: 1,
    backgroundColor: '#7FC8A0'
  },
  cameraState: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: colors.ink,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 10,
    padding: 16
  },
  cameraStateSoft: {
    backgroundColor: 'rgba(38, 35, 30, 0.55)'
  },
  cameraStateText: {
    fontFamily: fonts.medium,
    fontSize: 13,
    color: '#F3F0E8',
    textAlign: 'center'
  },
  retryBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    backgroundColor: colors.surface,
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 999
  },
  retryText: {
    fontFamily: fonts.semibold,
    fontSize: 13,
    color: colors.text
  },
  processing: {
    position: 'absolute',
    bottom: 12,
    alignSelf: 'center',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    backgroundColor: 'rgba(38, 35, 30, 0.85)',
    paddingHorizontal: 14,
    paddingVertical: 7,
    borderRadius: 999
  },
  processingText: {
    fontFamily: fonts.medium,
    fontSize: 12,
    color: colors.white
  },
  controls: {
    flexDirection: 'row',
    gap: 8,
    marginTop: 10
  },
  controlBtn: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    minHeight: 42,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.borderStrong,
    backgroundColor: colors.surface
  },
  controlBtnActive: {
    backgroundColor: colors.primary,
    borderColor: colors.primary
  },
  controlLabel: {
    fontFamily: fonts.semibold,
    fontSize: 13,
    color: colors.text
  },
  placeholderCard: {
    borderRadius: radius.xl,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surface,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 20
  },
  placeholderIcon: {
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: colors.subtle,
    borderWidth: 1,
    borderColor: colors.border,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 12
  },
  placeholderDesc: {
    textAlign: 'center',
    lineHeight: 18,
    marginTop: 6,
    marginBottom: 16
  },
  manualPanel: {
    marginTop: 10,
    backgroundColor: colors.surface,
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.border,
    padding: 12
  },
  manualRow: {
    flexDirection: 'row',
    gap: 8,
    marginBottom: 10
  },
  manualInput: {
    flex: 1,
    minHeight: 44,
    backgroundColor: colors.white,
    borderWidth: 1,
    borderColor: colors.borderStrong,
    borderRadius: radius.md,
    paddingHorizontal: 12,
    fontFamily: fonts.mono,
    fontSize: 15,
    color: colors.text
  },
  chip: {
    backgroundColor: colors.subtle,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.md,
    paddingHorizontal: 10,
    paddingVertical: 6
  },
  chipName: {
    fontFamily: fonts.medium,
    fontSize: 12,
    color: colors.text
  },
  chipCode: {
    fontFamily: fonts.mono,
    fontSize: 11,
    color: colors.textMuted
  }
});
