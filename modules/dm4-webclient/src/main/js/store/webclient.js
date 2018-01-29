import Vue from 'vue'
import Vuex from 'vuex'
import dm5 from 'dm5'

Vue.use(Vuex)

var compCount = 0

const state = {

  object: undefined,        // The selected Topic/Assoc/TopicType/AssocType.
                            // Undefined if nothing is selected.

  writable: undefined,      // True if the current user has WRITE permission for the selected object.

  mode: undefined,          // 'info' or 'form'

  inlineCompId: undefined,  // ID of the dm5-object component that is in inline edit mode

  objectRenderers: {},      // Registered page renderers:
                            //   {
                            //     typeUri: component
                            //   }

  quill: undefined,         // The Quill instance deployed in form mode.
                            // FIXME: support more than one Quill instance per form.

  components: {}
}

const actions = {

  displayObject (_, object) {
    // console.log('displayObject')
    state.object = object.isType() ? object.asType() : object
    _initWritable()
    cancelEdit()    // Note: inline state is still set when inline editing was left without saving
  },

  emptyDisplay () {
    // console.log('emptyDisplay')
    state.object = undefined
  },

  edit () {
    console.log('edit', state.object)
    state.object.fillChilds()
    state.mode = 'form'
  },

  editInline (_, compId) {
    state.inlineCompId = compId
  },

  // TODO: move to webclient.js?
  submit ({dispatch}) {
    state.object.update().then(object => {
      dispatch('_processDirectives', object.directives)
    })
    cancelEdit()
  },

  registerObjectRenderer (_, {typeUri, component}) {
    state.objectRenderers[typeUri] = component
  },

  setQuill (_, quill) {
    state.quill = quill
  },

  createTopicLink (_, topic) {
    console.log('createTopicLink', topic)
    state.quill.format('topic-link', {
      topicId: topic.id,
      linkId: undefined   // TODO
    })
  },

  unselectIf ({dispatch}, id) {
    // console.log('unselectIf', id, isSelected(id))
    if (isSelected(id)) {
      dispatch('stripSelectionFromRoute')
    }
  },

  // ---

  registerComponent (_, comp) {
    const comps = state.components[comp.mount] || (state.components[comp.mount] = [])
    comp.id = compCount++
    comps.push(comp)
  },

  mountComponents () {
    Vue.nextTick(() => {
      state.components.webclient.forEach(comp => {
        const Component = Vue.extend(comp.comp)
        // Note: to manually mounted components the store must be passed explicitly
        // https://forum.vuejs.org/t/this-store-undefined-in-manually-mounted-vue-component/8756
        new Component({store, propsData: comp.props}).$mount(`#mount-${comp.id}`)
      })
    })
  },

  //

  loggedIn () {
    initWritable()
  },

  loggedOut () {
    initWritable()
    cancelEdit()
  },

  // WebSocket messages

  _processDirectives ({dispatch}, directives) {
    console.log(`Webclient: processing ${directives.length} directives`, directives)
    directives.forEach(dir => {
      switch (dir.type) {
      case "UPDATE_TOPIC":
        displayObjectIf(new dm5.Topic(dir.arg))
        break
      case "DELETE_TOPIC":
        dispatch('unselectIf', dir.arg.id)
        break
      case "UPDATE_ASSOCIATION":
        displayObjectIf(new dm5.Assoc(dir.arg))
        break
      case "DELETE_ASSOCIATION":
        dispatch('unselectIf', dir.arg.id)
        break
      }
    })
  }
}

const store = new Vuex.Store({
  state,
  actions
})

export default store

//

function displayObjectIf (object) {
  if (isSelected(object.id)) {
    store.dispatch('displayObject', object)
  }
}

function isSelected (id) {
  const object = state.object
  return object && object.id === id
}

function cancelEdit () {
  state.mode = 'info'               // cancel form edit
  state.inlineCompId = undefined    // cancel inline edit
}

//

function initWritable() {
   state.object && _initWritable()
 }

function _initWritable() {
  state.object.isWritable().then(writable => {
    state.writable = writable
  })
}
