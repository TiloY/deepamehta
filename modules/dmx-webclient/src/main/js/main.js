import Vue from 'vue'
import App from './components/App'
import dm5 from 'dm5'
import store from './store/webclient'
import router from './router'
import pluginManager from './plugin-manager'
import onHttpError from './error-handler'
import 'font-awesome/css/font-awesome.css'
import './element-ui'
import './websocket'

console.log('[DM5] 2018/08/06.1')

// 1) Init dm5 library
// The dm5 library must be inited *before* the dm5-webclient component is created.
// The dm5-webclient component relies on the "typeCache" store module as registered by dm5.init().
const ready = dm5.init({
  store,
  onHttpError
})

// 2) Create Vue root instance
// In particular instantiates dm5-webclient component, and its child component dm5-search-widget.
new Vue({
  el: '#app',
  store,
  router,
  render: h => h(App)
})
// console.log('### Vue root instance created!')

// 3) Load plugins
// Plugin loading (and initialization) must take place *after* the Vue root instance is created.
// Plugins that provide entries for the create menu rely on the "registerExtraMenuItems" action,
// which is only registered in dm5-search-widget's created() hook.
pluginManager.loadPlugins()
// console.log('### Plugins loaded!')

// TODO: synchronize initial navigation with loading the external plugins?
// (Note: the standard plugins are "loaded" synchronously anyways.)

// 4) Mount Webclient components (as provided by plugins)
// Note: the mount point DOM is ready only on next tick.
Vue.nextTick(() => {
  store.dispatch('mountComponents')
})

// 5) Initial navigation
// Initial navigation must take place *after* the webclient plugins are loaded.
// The "workspaces" store module is registered by the Workspaces plugin.
Promise.all([
  // Both, the Topicmap Panel and the Detail Panel, rely on a populated type cache.
  // The type cache must be ready *before* "initialNavigation" is dispatched.
  ready,
  // Initial navigation might involve "select the 1st workspace", so the workspace
  // topics must be already loaded.
  store.state.workspaces.ready
]).then(() => {
  store.dispatch('initialNavigation')
})
