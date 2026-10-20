import React from 'react';
import { View, Text, TouchableOpacity, ActivityIndicator, StyleSheet } from 'react-native';
import { Feather } from '@expo/vector-icons';
import { colors, fonts, radius, type } from '../theme';

export const Icon = Feather;

// Tiêu đề mỗi màn hình: tên màn + dòng phụ, bên phải là trạng thái kết nối
export function ScreenHeader({ title, subtitle, isOnline }) {
  return (
    <View style={styles.header}>
      <View style={{ flex: 1 }}>
        <Text style={type.title}>{title}</Text>
        {subtitle ? (
          <Text style={[type.small, { marginTop: 2 }]} numberOfLines={1}>
            {subtitle}
          </Text>
        ) : null}
      </View>
      {isOnline != null && <ConnectionPill isOnline={isOnline} />}
    </View>
  );
}

export function ConnectionPill({ isOnline }) {
  return (
    <View
      style={[
        styles.pill,
        isOnline
          ? { backgroundColor: colors.primarySoft, borderColor: colors.primaryBorder }
          : { backgroundColor: colors.warningSoft, borderColor: colors.warningBorder }
      ]}
    >
      <Icon name={isOnline ? 'wifi' : 'wifi-off'} size={13} color={isOnline ? colors.primary : colors.warning} />
      <Text style={[styles.pillText, { color: isOnline ? colors.primary : colors.warningDark }]}>
        {isOnline ? 'Trực tuyến' : 'Ngoại tuyến'}
      </Text>
    </View>
  );
}

export function Card({ style, children }) {
  return <View style={[styles.card, style]}>{children}</View>;
}

export function SectionTitle({ children, right }) {
  return (
    <View style={styles.sectionRow}>
      <Text style={type.label}>{String(children).toUpperCase()}</Text>
      {right}
    </View>
  );
}

// variant: primary | secondary | danger | ghost
export function Button({ label, icon, onPress, variant = 'primary', loading, disabled, style, compact }) {
  const v = buttonVariants[variant];
  return (
    <TouchableOpacity
      style={[
        styles.btn,
        compact && styles.btnCompact,
        { backgroundColor: v.bg, borderColor: v.border },
        (disabled || loading) && { opacity: 0.6 },
        style
      ]}
      onPress={onPress}
      disabled={disabled || loading}
      activeOpacity={0.8}
    >
      {loading ? (
        <ActivityIndicator size="small" color={v.fg} />
      ) : (
        <>
          {icon ? <Icon name={icon} size={compact ? 15 : 17} color={v.fg} /> : null}
          <Text style={[styles.btnText, compact && { fontSize: 13 }, { color: v.fg }]}>{label}</Text>
        </>
      )}
    </TouchableOpacity>
  );
}

const buttonVariants = {
  primary: { bg: colors.primary, border: colors.primary, fg: colors.white },
  secondary: { bg: colors.surface, border: colors.borderStrong, fg: colors.text },
  danger: { bg: colors.surface, border: colors.dangerBorder, fg: colors.danger },
  ghost: { bg: 'transparent', border: 'transparent', fg: colors.primary }
};

export function EmptyState({ icon, title, description, action }) {
  return (
    <View style={styles.empty}>
      <View style={styles.emptyIcon}>
        <Icon name={icon} size={26} color={colors.textMuted} />
      </View>
      <Text style={[type.heading, { textAlign: 'center' }]}>{title}</Text>
      {description ? (
        <Text style={[type.small, styles.emptyDesc]}>{description}</Text>
      ) : null}
      {action}
    </View>
  );
}

// Nhãn trạng thái của một lần quét trong lịch sử
export const scanStatusMeta = (status) => {
  switch (status) {
    case 'SENT':
      return { label: 'Đã gửi POS', icon: 'check-circle', fg: colors.primary, bg: colors.primarySoft };
    case 'OFFLINE_QUEUED':
      return { label: 'Chờ đồng bộ', icon: 'upload-cloud', fg: colors.warningDark, bg: colors.warningSoft };
    case 'LOOKUP':
      return { label: 'Tra giá', icon: 'search', fg: colors.textMuted, bg: colors.subtle };
    case 'NOT_FOUND':
      return { label: 'Chưa có mã', icon: 'alert-circle', fg: colors.danger, bg: colors.dangerSoft };
    default:
      return { label: status, icon: 'circle', fg: colors.textMuted, bg: colors.subtle };
  }
};

export function StatusTag({ status }) {
  const meta = scanStatusMeta(status);
  return (
    <View style={[styles.tag, { backgroundColor: meta.bg }]}>
      <Icon name={meta.icon} size={11} color={meta.fg} />
      <Text style={[styles.tagText, { color: meta.fg }]}>{meta.label}</Text>
    </View>
  );
}

export const stockMeta = (stock) => {
  if (stock == null) return null;
  if (stock <= 0) return { label: 'Hết hàng', fg: colors.danger, bg: colors.dangerSoft };
  if (stock <= 5) return { label: `Sắp hết · ${stock}`, fg: colors.warningDark, bg: colors.warningSoft };
  return { label: `Còn ${stock}`, fg: colors.primary, bg: colors.primarySoft };
};

const styles = StyleSheet.create({
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingTop: 12,
    paddingBottom: 12,
    gap: 12
  },
  pill: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    borderWidth: 1,
    borderRadius: 999,
    paddingHorizontal: 10,
    paddingVertical: 5
  },
  pillText: {
    fontFamily: fonts.semibold,
    fontSize: 12
  },
  card: {
    backgroundColor: colors.surface,
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.border,
    padding: 14
  },
  sectionRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 8,
    marginTop: 4
  },
  btn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    minHeight: 46,
    paddingHorizontal: 16,
    borderRadius: radius.md,
    borderWidth: 1
  },
  btnCompact: {
    minHeight: 36,
    paddingHorizontal: 12
  },
  btnText: {
    fontFamily: fonts.semibold,
    fontSize: 14
  },
  empty: {
    alignItems: 'center',
    paddingVertical: 28,
    paddingHorizontal: 24
  },
  emptyIcon: {
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
  emptyDesc: {
    textAlign: 'center',
    marginTop: 6,
    marginBottom: 14,
    lineHeight: 18
  },
  tag: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    paddingHorizontal: 7,
    paddingVertical: 3,
    borderRadius: radius.sm
  },
  tagText: {
    fontFamily: fonts.semibold,
    fontSize: 11
  }
});
