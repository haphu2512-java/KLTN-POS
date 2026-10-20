import React, { useState, useRef, useEffect } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  TextInput,
  StyleSheet,
  ActivityIndicator,
  Animated,
  Platform,
  ScrollView
} from 'react-native';
import { CameraView, useCameraPermissions } from 'expo-camera';

// Sample mock barcodes for rapid testing without physical scanner
const MOCK_BARCODES = [
  { barcode: '1053044001004', name: 'Snack mực Talaethong' },
  { barcode: '1053820000425', name: 'Ô mai cầu vồng Plum' },
  { barcode: '1053086000170', name: 'Bánh gạo Poca Làng gạo' },
  { barcode: '1052850001654', name: 'Kẹo Golia C' },
  { barcode: '8934563148268', name: 'Mì tôm Hảo Hảo' },
  { barcode: '8934588012117', name: 'Coca-Cola 320ml' }
];

export default function ScannerView({
  onBarcodeDetected,
  isScanningActive,
  isProcessing
}) {
  const [permission, requestPermission] = useCameraPermissions();
  const [facing, setFacing] = useState('back');
  const [enableTorch, setEnableTorch] = useState(false);
  const [manualCode, setManualCode] = useState('');
  const [showManualInput, setShowManualInput] = useState(false);

  // Laser scan line animation
  const animLine = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    const loop = Animated.loop(
      Animated.sequence([
        Animated.timing(animLine, {
          toValue: 200,
          duration: 1800,
          useNativeDriver: true
        }),
        Animated.timing(animLine, {
          toValue: 0,
          duration: 1800,
          useNativeDriver: true
        })
      ])
    );
    loop.start();
    return () => loop.stop();
  }, [animLine]);

  // Handle barcode scanned from camera
  const handleBarcodeScanned = (event) => {
    if (!isScanningActive || isProcessing) return;
    const barcodeData = event.data || event.rawValue;
    if (barcodeData) {
      onBarcodeDetected(String(barcodeData).trim());
    }
  };

  // Submit manual barcode
  const handleSubmitManual = () => {
    if (!manualCode.trim() || isProcessing) return;
    onBarcodeDetected(manualCode.trim());
    setManualCode('');
  };

  // Permission not determined or requested yet
  if (!permission) {
    return (
      <View style={styles.centerContainer}>
        <ActivityIndicator size="large" color="#38bdf8" />
        <Text style={styles.loadingText}>Đang khởi tạo máy quét Camera...</Text>
      </View>
    );
  }

  // Permission denied
  if (!permission.granted) {
    return (
      <View style={styles.centerContainer}>
        <Text style={styles.permissionIcon}>📷</Text>
        <Text style={styles.permissionTitle}>Cần quyền truy cập Camera</Text>
        <Text style={styles.permissionDesc}>
          Ứng dụng cần quyền Camera để quét các loại mã vạch sản phẩm (EAN-13, Code 128, QR Code) cho cửa hàng POS.
        </Text>
        <TouchableOpacity
          style={styles.permissionBtn}
          onPress={requestPermission}
          activeOpacity={0.8}
        >
          <Text style={styles.permissionBtnText}>Cấp quyền Camera ngay</Text>
        </TouchableOpacity>

        {/* Fallback mock mode */}
        <View style={styles.fallbackBox}>
          <Text style={styles.fallbackTitle}>Hoặc dùng chế độ Nhập tay / Mock:</Text>
          <View style={styles.manualRow}>
            <TextInput
              style={styles.manualInput}
              placeholder="Nhập mã barcode..."
              placeholderTextColor="#64748b"
              value={manualCode}
              onChangeText={setManualCode}
              onSubmitEditing={handleSubmitManual}
              keyboardType="numeric"
            />
            <TouchableOpacity style={styles.manualBtn} onPress={handleSubmitManual}>
              <Text style={styles.manualBtnText}>Quét</Text>
            </TouchableOpacity>
          </View>
        </View>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      {/* Live Camera View */}
      <CameraView
        style={StyleSheet.absoluteFillObject}
        facing={facing}
        enableTorch={enableTorch}
        barcodeScannerSettings={{
          barcodeTypes: [
            'ean13',
            'ean8',
            'code128',
            'code39',
            'upc_a',
            'upc_e',
            'qr'
          ]
        }}
        onBarcodeScanned={isScanningActive ? handleBarcodeScanned : undefined}
      />

      {/* Viewfinder Overlay with Dark Masks */}
      <View style={styles.overlayContainer}>
        <View style={styles.maskTop} />

        <View style={styles.middleRow}>
          <View style={styles.maskSide} />

          {/* Viewfinder Target Frame */}
          <View style={styles.targetFrame}>
            {/* 4 Corner Brackets */}
            <View style={[styles.corner, styles.cornerTopLeft]} />
            <View style={[styles.corner, styles.cornerTopRight]} />
            <View style={[styles.corner, styles.cornerBottomLeft]} />
            <View style={[styles.corner, styles.cornerBottomRight]} />

            {/* Red Laser Scan Line */}
            {isScanningActive && (
              <Animated.View
                style={[
                  styles.laserLine,
                  { transform: [{ translateY: animLine }] }
                ]}
              />
            )}

            {isProcessing && (
              <View style={styles.processingBadge}>
                <ActivityIndicator size="small" color="#fff" />
                <Text style={styles.processingText}>Đang nhận diện...</Text>
              </View>
            )}
          </View>

          <View style={styles.maskSide} />
        </View>

        <View style={styles.maskBottom}>
          <Text style={styles.hintText}>
            Căn chỉnh mã vạch sản phẩm nằm trong khung quét
          </Text>

          {/* Controls Bar */}
          <View style={styles.controlsBar}>
            {/* Flashlight toggle */}
            <TouchableOpacity
              style={[styles.controlBtn, enableTorch && styles.controlBtnActive]}
              onPress={() => setEnableTorch(!enableTorch)}
              activeOpacity={0.7}
            >
              <Text style={styles.controlIcon}>{enableTorch ? '🔦' : '💡'}</Text>
              <Text style={styles.controlLabel}>{enableTorch ? 'Tắt đèn' : 'Bật đèn'}</Text>
            </TouchableOpacity>

            {/* Camera flip */}
            <TouchableOpacity
              style={styles.controlBtn}
              onPress={() => setFacing(facing === 'back' ? 'front' : 'back')}
              activeOpacity={0.7}
            >
              <Text style={styles.controlIcon}>🔄</Text>
              <Text style={styles.controlLabel}>Đổi cam</Text>
            </TouchableOpacity>

            {/* Toggle Manual input */}
            <TouchableOpacity
              style={[styles.controlBtn, showManualInput && styles.controlBtnActive]}
              onPress={() => setShowManualInput(!showManualInput)}
              activeOpacity={0.7}
            >
              <Text style={styles.controlIcon}>⌨️</Text>
              <Text style={styles.controlLabel}>Nhập tay</Text>
            </TouchableOpacity>
          </View>

          {/* Mock Barcodes & Manual Input Drawer */}
          {showManualInput && (
            <View style={styles.manualDrawer}>
              <View style={styles.manualInputRow}>
                <TextInput
                  style={styles.manualDrawerInput}
                  placeholder="Gõ mã barcode (VD: 893456...)"
                  placeholderTextColor="#64748b"
                  value={manualCode}
                  onChangeText={setManualCode}
                  onSubmitEditing={handleSubmitManual}
                  keyboardType="numeric"
                  returnKeyType="done"
                />
                <TouchableOpacity
                  style={styles.manualSubmitBtn}
                  onPress={handleSubmitManual}
                  activeOpacity={0.8}
                >
                  <Text style={styles.manualSubmitText}>Quét</Text>
                </TouchableOpacity>
              </View>

              {/* Quick sample barcode chips */}
              <Text style={styles.quickLabel}>Mã mẫu kiểm tra nhanh (Mock):</Text>
              <ScrollView
                horizontal
                showsHorizontalScrollIndicator={false}
                contentContainerStyle={styles.chipsScroll}
              >
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
          )}
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#000'
  },
  centerContainer: {
    flex: 1,
    backgroundColor: '#0f172a',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 24
  },
  loadingText: {
    color: '#94a3b8',
    marginTop: 12,
    fontSize: 14
  },
  permissionIcon: {
    fontSize: 54,
    marginBottom: 16
  },
  permissionTitle: {
    color: '#f8fafc',
    fontSize: 20,
    fontWeight: '800',
    marginBottom: 8,
    textAlign: 'center'
  },
  permissionDesc: {
    color: '#94a3b8',
    fontSize: 14,
    textAlign: 'center',
    lineHeight: 20,
    marginBottom: 20
  },
  permissionBtn: {
    backgroundColor: '#059669',
    paddingVertical: 14,
    paddingHorizontal: 28,
    borderRadius: 12
  },
  permissionBtnText: {
    color: '#fff',
    fontSize: 15,
    fontWeight: '700'
  },
  fallbackBox: {
    marginTop: 32,
    width: '100%',
    backgroundColor: '#1e293b',
    padding: 16,
    borderRadius: 14
  },
  fallbackTitle: {
    color: '#cbd5e1',
    fontSize: 13,
    fontWeight: '600',
    marginBottom: 10
  },
  manualRow: {
    flexDirection: 'row',
    gap: 8
  },
  manualInput: {
    flex: 1,
    backgroundColor: '#0f172a',
    color: '#fff',
    paddingHorizontal: 12,
    paddingVertical: 10,
    borderRadius: 8,
    fontSize: 14
  },
  manualBtn: {
    backgroundColor: '#2563eb',
    paddingHorizontal: 18,
    justifyContent: 'center',
    borderRadius: 8
  },
  manualBtnText: {
    color: '#fff',
    fontWeight: '700'
  },
  overlayContainer: {
    ...StyleSheet.absoluteFillObject,
    justifyContent: 'space-between'
  },
  maskTop: {
    height: 120,
    backgroundColor: 'rgba(0, 0, 0, 0.65)'
  },
  middleRow: {
    flexDirection: 'row',
    height: 220
  },
  maskSide: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.65)'
  },
  targetFrame: {
    width: 280,
    height: 220,
    position: 'relative',
    justifyContent: 'center',
    alignItems: 'center'
  },
  corner: {
    position: 'absolute',
    width: 28,
    height: 28,
    borderColor: '#10b981'
  },
  cornerTopLeft: {
    top: 0,
    left: 0,
    borderTopWidth: 4,
    borderLeftWidth: 4,
    borderTopLeftRadius: 6
  },
  cornerTopRight: {
    top: 0,
    right: 0,
    borderTopWidth: 4,
    borderRightWidth: 4,
    borderTopRightRadius: 6
  },
  cornerBottomLeft: {
    bottom: 0,
    left: 0,
    borderBottomWidth: 4,
    borderLeftWidth: 4,
    borderBottomLeftRadius: 6
  },
  cornerBottomRight: {
    bottom: 0,
    right: 0,
    borderBottomWidth: 4,
    borderRightWidth: 4,
    borderBottomRightRadius: 6
  },
  laserLine: {
    width: '92%',
    height: 2,
    backgroundColor: '#ef4444',
    shadowColor: '#ef4444',
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.9,
    shadowRadius: 6,
    elevation: 4
  },
  processingBadge: {
    position: 'absolute',
    backgroundColor: 'rgba(15, 23, 42, 0.85)',
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 20,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8
  },
  processingText: {
    color: '#fff',
    fontSize: 12,
    fontWeight: '600'
  },
  maskBottom: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.65)',
    alignItems: 'center',
    paddingTop: 16,
    paddingBottom: 20
  },
  hintText: {
    color: '#e2e8f0',
    fontSize: 13,
    fontWeight: '500',
    marginBottom: 16,
    textAlign: 'center'
  },
  controlsBar: {
    flexDirection: 'row',
    justifyContent: 'center',
    gap: 20,
    marginBottom: 10
  },
  controlBtn: {
    backgroundColor: 'rgba(30, 41, 59, 0.85)',
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 20,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#475569'
  },
  controlBtnActive: {
    backgroundColor: '#059669',
    borderColor: '#10b981'
  },
  controlIcon: {
    fontSize: 18,
    marginBottom: 2
  },
  controlLabel: {
    color: '#fff',
    fontSize: 11,
    fontWeight: '600'
  },
  manualDrawer: {
    width: '92%',
    backgroundColor: '#1e293b',
    borderRadius: 14,
    padding: 12,
    marginTop: 8,
    borderWidth: 1,
    borderColor: '#334155'
  },
  manualInputRow: {
    flexDirection: 'row',
    gap: 8,
    marginBottom: 10
  },
  manualDrawerInput: {
    flex: 1,
    backgroundColor: '#0f172a',
    color: '#fff',
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 8,
    fontSize: 13,
    borderWidth: 1,
    borderColor: '#334155'
  },
  manualSubmitBtn: {
    backgroundColor: '#059669',
    paddingHorizontal: 16,
    justifyContent: 'center',
    borderRadius: 8
  },
  manualSubmitText: {
    color: '#fff',
    fontSize: 13,
    fontWeight: '700'
  },
  quickLabel: {
    color: '#94a3b8',
    fontSize: 11,
    fontWeight: '600',
    marginBottom: 6
  },
  chipsScroll: {
    flexDirection: 'row',
    gap: 8
  },
  chip: {
    backgroundColor: '#0f172a',
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#334155'
  },
  chipName: {
    color: '#f8fafc',
    fontSize: 11,
    fontWeight: '600'
  },
  chipCode: {
    color: '#38bdf8',
    fontSize: 10,
    fontFamily: 'monospace'
  }
});
