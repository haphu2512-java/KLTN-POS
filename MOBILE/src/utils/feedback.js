import * as Haptics from 'expo-haptics';
import { Platform } from 'react-native';

export const triggerScanFeedback = async (isSuccess = true) => {
  try {
    if (Platform.OS !== 'web') {
      if (isSuccess) {
        await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
      } else {
        await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Warning);
      }
    } else if (typeof window !== 'undefined') {
      // Web Audio API beep synthesizer for browser preview / web mode
      const AudioCtx = window.AudioContext || window.webkitAudioContext;
      if (AudioCtx) {
        const ctx = new AudioCtx();
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.connect(gain);
        gain.connect(ctx.destination);
        osc.type = isSuccess ? 'sine' : 'sawtooth';
        osc.frequency.setValueAtTime(isSuccess ? 1800 : 400, ctx.currentTime);
        gain.gain.setValueAtTime(0.15, ctx.currentTime);
        gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.12);
        osc.start();
        osc.stop(ctx.currentTime + 0.12);
      }
    }
  } catch (err) {
    // Non-fatal if device doesn't support haptics
  }
};
