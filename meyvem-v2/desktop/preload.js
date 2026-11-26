'use strict';
const { contextBridge, ipcRenderer } = require('electron');

// Köprü yalnızca uygulamanın kendi (yerel) sayfalarında açılır.
// Site (https) sayfalarına hiçbir şey sunulmaz.
if (location.protocol === 'file:') {
  contextBridge.exposeInMainWorld('api', {
    lockState: () => ipcRenderer.invoke('lock:state'),
    checkNo: (no) => ipcRenderer.invoke('lock:checkNo', no),
    googleOpen: () => ipcRenderer.invoke('lock:googleOpen'),
    googleStatus: () => ipcRenderer.invoke('lock:googleStatus'),
    googleDone: () => ipcRenderer.invoke('lock:googleDone'),
    siteStart: () => ipcRenderer.invoke('lock:siteStart'),
    siteIcon: (url) => ipcRenderer.invoke('sites:icon', url),
    getSites: () => ipcRenderer.invoke('sites:get'),
    refreshSites: () => ipcRenderer.invoke('sites:refresh'),
    selectSite: (url) => ipcRenderer.invoke('sites:select', url),
    createPassword: (pwd, confirm) => ipcRenderer.invoke('lock:create', pwd, confirm),
    unlock: (pwd) => ipcRenderer.invoke('lock:unlock', pwd),
    getSettings: () => ipcRenderer.invoke('settings:get'),
    setSettings: (patch) => ipcRenderer.invoke('settings:set', patch),
    openSettings: () => ipcRenderer.send('settings:open'),
    openZoom: () => ipcRenderer.send('zoom:open'),
    exitBlack: () => ipcRenderer.send('black:exit'),
    onSettingsUpdated: (cb) => ipcRenderer.on('settings:updated', (_e, s) => cb(s)),
  });
}
