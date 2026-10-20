import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Icon } from './ui';
import { colors, fonts } from '../theme';

export const TABS = [
  { key: 'scan', label: 'Quét', icon: 'maximize' },
  { key: 'lookup', label: 'Tra cứu', icon: 'search' },
  { key: 'history', label: 'Lịch sử', icon: 'clock' },
  { key: 'settings', label: 'Cài đặt', icon: 'settings' }
];

// badges: { [tabKey]: số hiển thị trên icon }
export default function TabBar({ active, onChange, badges = {} }) {
  const insets = useSafeAreaInsets();
  return (
    <View style={[styles.bar, { paddingBottom: Math.max(insets.bottom, 8) }]}>
      {TABS.map((tab) => {
        const isActive = tab.key === active;
        const badge = badges[tab.key];
        return (
          <TouchableOpacity
            key={tab.key}
            style={styles.tab}
            onPress={() => onChange(tab.key)}
            activeOpacity={0.7}
            accessibilityRole="tab"
            accessibilityState={{ selected: isActive }}
            accessibilityLabel={tab.label}
          >
            <View style={[styles.iconWrap, isActive && styles.iconWrapActive]}>
              <Icon name={tab.icon} size={20} color={isActive ? colors.primary : colors.textLight} />
              {badge ? (
                <View style={styles.badge}>
                  <Text style={styles.badgeText}>{badge > 99 ? '99+' : badge}</Text>
                </View>
              ) : null}
            </View>
            <Text style={[styles.label, isActive && styles.labelActive]}>{tab.label}</Text>
          </TouchableOpacity>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  bar: {
    flexDirection: 'row',
    backgroundColor: colors.surface,
    borderTopWidth: 1,
    borderTopColor: colors.border,
    paddingTop: 6
  },
  tab: {
    flex: 1,
    alignItems: 'center',
    gap: 2
  },
  iconWrap: {
    width: 56,
    height: 30,
    borderRadius: 15,
    alignItems: 'center',
    justifyContent: 'center'
  },
  iconWrapActive: {
    backgroundColor: colors.primarySoft
  },
  badge: {
    position: 'absolute',
    top: -2,
    right: 8,
    minWidth: 18,
    height: 18,
    borderRadius: 9,
    paddingHorizontal: 4,
    backgroundColor: colors.warning,
    alignItems: 'center',
    justifyContent: 'center'
  },
  badgeText: {
    fontFamily: fonts.bold,
    fontSize: 10,
    color: colors.white
  },
  label: {
    fontFamily: fonts.medium,
    fontSize: 11,
    color: colors.textLight
  },
  labelActive: {
    fontFamily: fonts.semibold,
    color: colors.primary
  }
});
