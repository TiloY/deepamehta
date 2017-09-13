import Vue from 'vue'
import VueRouter from 'vue-router'
import Webclient from './components/Webclient'
import store from './store/webclient'
import dm5 from 'dm5'

Vue.use(VueRouter)

const router = new VueRouter({
  routes: [
    {
      path: '/',
      component: Webclient
    },
    {
      path: '/topicmap/:topicmapId',
      name: 'topicmap',
      component: Webclient
    },
    {
      path: '/topicmap/:topicmapId/topic/:topicId',
      name: 'topic',
      component: Webclient
    },
    {
      path: '/topicmap/:topicmapId/assoc/:assocId',
      name: 'assoc',
      component: Webclient
    }
  ]
})

export default router

// Track initial navigation as it needs to be treated special.
// Note: the route store watcher is fired for the initial navigation too. ### FIXDDOC
var isInitialGuard = true
var isInitialNavigation = true

router.beforeEach((to, from, next) => {
  // Before the initial topicmap can be rendered 2 promises must be fullfilled:
  // 1) the dm5 library is ready (type cache is populated)
  // 2) the initial topicmap is loaded
  if (isInitialGuard) {
    Promise.all([
      dm5.getPromise(),
      initialNavigation(to)
    ]).then(() => {
      registerRouteWatcher()
      next()
    })
    isInitialGuard = false
  } else {
    next()
  }
})

store.registerModule('routerModule', {

  state: {
    router
  },

  actions: {

    callTopicmapRoute (_, id) {
      router.push({
        name: 'topicmap',
        params: {
          topicmapId: id
        }
      })
    },

    callTopicRoute (_, id) {
      router.push({
        name: 'topic',
        params: {
          topicId: id
        }
      })
    },

    callAssocRoute (_, id) {
      router.push({
        name: 'assoc',
        params: {
          assocId: id
        }
      })
    },

    stripTopicOrAssocFromRoute () {
      router.push({
        name: 'topicmap'
      })
    }
  }
})

function registerRouteWatcher () {
  store.watch(
    state => state.routerModule.router.currentRoute,
    (to, from) => {
      if (isInitialNavigation) {
        isInitialNavigation = false
      } else {
        console.log('### Route watcher', to, from)
        navigate(to, from)
      }
    }
  )
}

function initialNavigation (route) {
  const topicmapId = route.params.topicmapId
  const topicId    = route.params.topicId
  const assocId    = route.params.assocId
  console.log('### Initial navigation (topicmapId, topicId, assocId)', topicmapId, topicId, assocId)
  var p = Promise.resolve()
  if (topicmapId) {
    p = store.dispatch('fetchTopicmap', topicmapId)
  }
  if (topicId) {  // FIXME: 0 is a valid topic ID
    store.dispatch('fetchTopic', topicId)
  }
  if (assocId) {
    store.dispatch('fetchAssoc', assocId)
  }
  return p
}

function navigate (to, from) {
  const topicmapId = to.params.topicmapId
  const oldTopicmapId = from.params.topicmapId
  console.log('$route watcher topicmapId', topicmapId, oldTopicmapId, topicmapId != oldTopicmapId)
  // Note: path param values read from URL are strings. Path param values set by push() are numbers.
  // So we do *not* use exact equality (!==) here.
  if (topicmapId != oldTopicmapId) {
    store.dispatch('renderTopicmap', topicmapId)
  }
  //
  var selected
  //
  const topicId = to.params.topicId
  const oldTopicId = from.params.topicId
  console.log('$route watcher topicId', topicId, oldTopicId, topicId != oldTopicId)
  if (topicId != oldTopicId) {
    if (topicId) {  // FIXME: 0 is a valid topic ID
      store.dispatch('fetchTopic', topicId)
      selected = true
    }
  }
  //
  const assocId = to.params.assocId
  const oldAssocId = from.params.assocId
  console.log('$route watcher assocId', assocId, oldAssocId, assocId != oldAssocId)
  if (assocId != oldAssocId) {
    if (assocId) {
      store.dispatch('fetchAssoc', assocId)
      selected = true
    }
  }
  //
  if (!selected) {
    store.dispatch('_unselect')
  }
}
