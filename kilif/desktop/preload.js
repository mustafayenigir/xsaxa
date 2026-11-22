'use strict';
const { contextBridge, ipcRenderer } = require('electron');

// Köprü yalnızca uygulamanın kendi (yerel) sayfalarında açılır.
// Site (https) sayfalarına hiçbir şey sunulmaz.
if (location.protocol === 'file:') {
  contextBridge.exposeInMainWorld('api', {
    getSites: () => ipcRenderer.invoke('sites:get'),
    putSite: (site, oldUrl) => ipcRenderer.invoke('sites:put', site, oldUrl),
    removeSite: (url) => ipcRenderer.invoke('sites:remove', url),
    selectSite: (url) => ipcRenderer.invoke('sites:select', url),
    siteIcon: (url) => ipcRenderer.invoke('sites:icon', url),
    getSettings: () => ipcRenderer.invoke('settings:get'),
    setSettings: (patch) => ipcRenderer.invoke('settings:set', patch),
    openHome: () => ipcRenderer.send('home:open'),
    exitBlack: () => ipcRenderer.send('black:exit'),
    onSettingsUpdated: (cb) => ipcRenderer.on('settings:updated', (_e, s) => cb(s)),
  });
}
