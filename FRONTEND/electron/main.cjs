const { app, BrowserWindow, ipcMain } = require('electron');
const path = require('path');

let mainWindow = null;

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1366,
    height: 768,
    minWidth: 1024,
    minHeight: 650,
    backgroundColor: '#0F172A',
    autoHideMenuBar: true,
    show: false,
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });

  mainWindow.once('ready-to-show', () => {
    mainWindow.show();
    mainWindow.maximize();
  });

  const devServerUrl = process.env.VITE_DEV_SERVER_URL || 'http://localhost:5173';
  const isDev = process.env.NODE_ENV === 'development' || !app.isPackaged;

  if (isDev) {
    mainWindow.loadURL(devServerUrl).catch(() => {
      // In case Vite is still spinning up, retry after a delay
      setTimeout(() => mainWindow.loadURL(devServerUrl), 1500);
    });
  } else {
    mainWindow.loadFile(path.join(__dirname, '../dist/index.html'));
  }

  mainWindow.on('closed', () => {
    mainWindow = null;
  });
}

// Window control IPC handlers
ipcMain.handle('window:minimize', () => {
  if (mainWindow) mainWindow.minimize();
});

ipcMain.handle('window:maximize', () => {
  if (mainWindow) {
    if (mainWindow.isMaximized()) {
      mainWindow.unmaximize();
    } else {
      mainWindow.maximize();
    }
  }
});

ipcMain.handle('window:close', () => {
  if (mainWindow) mainWindow.close();
});

ipcMain.handle('window:toggle-fullscreen', () => {
  if (mainWindow) {
    mainWindow.setFullScreen(!mainWindow.isFullScreen());
    return mainWindow.isFullScreen();
  }
  return false;
});

// Thermal Printer & Invoicing IPC Handlers
ipcMain.handle('printer:get-printers', async () => {
  if (!mainWindow) return [];
  try {
    return await mainWindow.webContents.getPrintersAsync();
  } catch (err) {
    console.error('Failed to get printers:', err);
    return [];
  }
});

// In hóa đơn trong một cửa sổ ẩn chỉ chứa HTML hóa đơn.
// (webContents.print của cửa sổ chính sẽ in cả màn hình POS chứ không phải hóa đơn.)
ipcMain.handle('printer:print-receipt', async (event, options = {}) => {
  if (typeof options.html !== 'string' || !options.html) {
    return { success: false, error: 'Thiếu nội dung hóa đơn' };
  }

  const printWindow = new BrowserWindow({
    show: false,
    width: 302, // ~80mm ở 96 dpi
    height: 800,
    webPreferences: {
      sandbox: true,
      contextIsolation: true,
      nodeIntegration: false,
      javascript: false,
    },
  });

  try {
    await printWindow.loadURL(`data:text/html;charset=utf-8,${encodeURIComponent(options.html)}`);

    const printOptions = {
      silent: options.silent ?? true,
      printBackground: true,
      deviceName: options.deviceName || '',
      margins: {
        marginType: 'none',
      },
      pageSize: options.pageSize || { width: 80000, height: 297000 }, // Standard 80mm roll width
    };

    return await new Promise((resolve) => {
      printWindow.webContents.print(printOptions, (success, failureReason) => {
        resolve(success ? { success: true } : { success: false, error: failureReason });
      });
    });
  } catch (error) {
    return { success: false, error: error.message };
  } finally {
    if (!printWindow.isDestroyed()) printWindow.close();
  }
});

// App Info
ipcMain.handle('app:get-version', () => app.getVersion());

app.whenReady().then(() => {
  createWindow();

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});
