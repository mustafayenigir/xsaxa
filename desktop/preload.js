'use strict';
const { contextBridge, ipcRenderer } = require('electron');

// Köprü yalnızca uygulamanın kendi (yerel) sayfalarında açılır.
// Site (https) sayfalarına hiçbir şey sunulmaz.
if (location.protocol === 'file:') {
  contextBridge.exposeInMainWorld('api', {
    lockState: () => ipcRenderer.invoke('lock:state'),
    createPassword: (pwd, confirm) => ipcRenderer.invoke('lock:create', pwd, confirm),
    unlock: (pwd) => ipcRenderer.invoke('lock:unlock', pwd),
    getSettings: () => ipcRenderer.invoke('settings:get'),
    setSettings: (patch) => ipcRenderer.invoke('settings:set', patch),
    openSettings: () => ipcRenderer.send('settings:open'),
    exitBlack: () => ipcRenderer.send('black:exit'),
    onSettingsUpdated: (cb) => ipcRenderer.on('settings:updated', (_e, s) => cb(s)),
  });
}
