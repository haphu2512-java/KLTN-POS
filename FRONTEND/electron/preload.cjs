const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('electronAPI', {
  isElectron: true,
  // Window actions
  minimize: () => ipcRenderer.invoke('window:minimize'),
  maximize: () => ipcRenderer.invoke('window:maximize'),
  close: () => ipcRenderer.invoke('window:close'),
  toggleFullscreen: () => ipcRenderer.invoke('window:toggle-fullscreen'),

  // Printer actions
  getPrinters: () => ipcRenderer.invoke('printer:get-printers'),
  printReceipt: (options) => ipcRenderer.invoke('printer:print-receipt', options),

  // App version
  getVersion: () => ipcRenderer.invoke('app:get-version'),
});
