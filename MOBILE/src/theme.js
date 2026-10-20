// Cùng bộ màu với bản POS Electron (FRONTEND/src/index.css): giấy ấm, mực nâu, một màu nhấn xanh lá đậm
export const colors = {
  canvas: '#F3F0E8',
  surface: '#FBFAF6',
  subtle: '#F7F5EF',
  border: '#E2DCCE',
  borderStrong: '#CFC7B5',

  text: '#23211C',
  textMuted: '#6B665B',
  textLight: '#8A8475',
  ink: '#26231E',

  primary: '#2D6A4F',
  primaryDark: '#245840',
  primarySoft: '#E3EDE5',
  primaryBorder: '#B7CDBF',

  warning: '#A66A0E',
  warningDark: '#7A4E08',
  warningSoft: '#F6E9CC',
  warningBorder: '#E6D3A3',

  danger: '#A33A2A',
  dangerSoft: '#F5DDD6',
  dangerBorder: '#E8C2B8',

  white: '#FFFFFF'
};

// Font nạp trong App.js; nếu nạp lỗi thì React Native tự rơi về font hệ thống
export const fonts = {
  regular: 'BeVietnamPro_400Regular',
  medium: 'BeVietnamPro_500Medium',
  semibold: 'BeVietnamPro_600SemiBold',
  bold: 'BeVietnamPro_700Bold',
  mono: 'monospace'
};

export const radius = {
  sm: 6,
  md: 8,
  lg: 12,
  xl: 16
};

export const type = {
  title: { fontFamily: fonts.bold, fontSize: 20, color: colors.text },
  heading: { fontFamily: fonts.semibold, fontSize: 15, color: colors.text },
  body: { fontFamily: fonts.regular, fontSize: 14, color: colors.text },
  bodyMedium: { fontFamily: fonts.medium, fontSize: 14, color: colors.text },
  small: { fontFamily: fonts.regular, fontSize: 12, color: colors.textMuted },
  label: { fontFamily: fonts.semibold, fontSize: 12, color: colors.textMuted, letterSpacing: 0.3 }
};
