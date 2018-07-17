import DM5WebSocket from 'dm5-websocket'
import store from './store/webclient'

/* eslint no-new: 0 */

new DM5WebSocket('de.deepamehta.webclient', dispatch)

function dispatch (message) {
  store.dispatch('_' + message.type, message.args)
}
