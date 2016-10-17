function TopicmapsPluginModel() {

    // --------------------------------------------------------------------------------------------------- Private State

    var topicmap                    // Selected topicmap (TopicmapViewmodel object)     \ updated together by
    var topicmap_renderer           // The topicmap renderer of the selected topicmap   / set_selected_topicmap()
    var topicmap_renderers = {}     // Registered topicmap renderers (key: renderer URI, value: TopicmapRenderer object)
    var topicmap_topics = {}        // Loaded topicmap topics, grouped by workspace, an object:
                                    //   {
                                    //     workspaceId: [topicmapTopic]
                                    //   }
    var selected_topicmap_ids = {}  // ID of the selected topicmap, per-workspace, an object:
                                    //   {
                                    //     workspaceId: selectedTopicmapId
                                    //   }
    var topicmap_cache = {}         // Loaded topicmaps (key: topicmap ID, value: TopicmapViewmodel object)

    // ------------------------------------------------------------------------------------------------------ Public API

    this.get_topicmap = function() {return topicmap}
    this.get_current_topicmap_renderer = function() {return topicmap_renderer}

    this.register_topicmap_renderers = register_topicmap_renderers
    this.init = init
    this.set_selected_topicmap = set_selected_topicmap
    this.reload_topicmap = reload_topicmap
    this.fetch_topicmap_topics = fetch_topicmap_topics
    this.get_topicmap_topics = get_topicmap_topics
    this.clear_topicmap_topics = clear_topicmap_topics
    this.get_selected_topicmap_id = get_selected_topicmap_id
    this.select_topicmap_for_workspace = select_topicmap_for_workspace
    this.delete_topicmap = delete_topicmap

    this.get_topicmap_renderer = get_topicmap_renderer
    this.iterate_topicmap_renderers = iterate_topicmap_renderers

    this.iterate_topicmap_cache = iterate_topicmap_cache
    this.clear_topicmap_cache = clear_topicmap_cache

    // ----------------------------------------------------------------------------------------------- Private Functions

    function register_topicmap_renderers() {
        // default renderer
        register(dm4c.topicmap_renderer)
        // custom renderers
        var renderers = dm4c.fire_event("topicmap_renderer")
        renderers.forEach(function(renderer) {
            register(renderer)
        })

        function register(renderer) {
            topicmap_renderers[renderer.get_info().uri] = renderer
        }
    }

    function init() {
        fetch_topicmap_topics()
        //
        // try to obtain a topicmap ID from browser URL or from cookie, otherwise choose arbitrary topicmap
        var groups = location.pathname.match(/\/topicmap\/(\d+)(\/topic\/(\d+))?/)
        var topicmap_id = groups && groups[1] || js.get_cookie("dm4_topicmap_id") || get_first_topicmap_id()
        set_selected_topicmap(topicmap_id)
        //
        if (groups) {
            var topic_id = groups[3]
            if (topic_id) {
                topicmap.set_topic_selection(topic_id)
            }
        }
    }

    /**
     * Updates the model to reflect the given topicmap is now selected. That includes setting a cookie
     * and updating 3 model objects ("topicmap", "topicmap_renderer", "selected_topicmap_ids").
     * <p>
     * Prerequisite: the topicmap topic for the specified topicmap is already loaded/up-to-date.
     */
    function set_selected_topicmap(topicmap_id) {
        // 1) set cookie
        // Note: the cookie must be set *before* the topicmap is loaded.
        // Server-side topic loading might depend on the topicmap type.
        js.set_cookie("dm4_topicmap_id", topicmap_id)
        //
        // 2) update "topicmap_renderer"
        // Note: the renderer must be set *before* the topicmap is loaded.
        // The renderer is responsible for loading.
        var renderer_uri = get_topicmap_topic_or_throw(topicmap_id).get("dm4.topicmaps.topicmap_renderer_uri")
        topicmap_renderer = get_topicmap_renderer(renderer_uri)
        //
        // 3) update "topicmap" and "selected_topicmap_ids"
        topicmap = get_topicmap(topicmap_id)
        selected_topicmap_ids[get_selected_workspace_id()] = topicmap_id
    }

    /**
     * Looks up a topicmap topic for the selected workspace.
     * If no such topicmap is available in the selected workspace an exception is thrown.
     *
     * @return  the topicmap topic.
     */
    function get_topicmap_topic_or_throw(topicmap_id) {
        var topicmap_topic = get_topicmap_topic(topicmap_id)
        if (!topicmap_topic) {
            throw "TopicmapsError: topicmap " + topicmap_id + " not found in model for workspace " +
                get_selected_workspace_id()
        }
        return topicmap_topic
    }

    /**
     * Looks up a topicmap topic for the selected workspace.
     *
     * @return  the topicmap topic, or undefined if no such topicmap is available in the selected workspace.
     */
    function get_topicmap_topic(topicmap_id) {
        return js.find(get_topicmap_topics(), function(topic) {
            return topic.id == topicmap_id
        })
    }

    /**
     * Returns a topicmap from the cache.
     * If not in cache, the topicmap is loaded from DB (and then cached).
     */
    function get_topicmap(topicmap_id) {
        var topicmap = topicmap_cache[topicmap_id]
        if (!topicmap) {
            topicmap = load_topicmap(topicmap_id)
            topicmap_cache[topicmap_id] = topicmap
        }
        return topicmap
    }

    /**
     * Loads a topicmap from DB.
     * <p>
     * Prerequisite: the topicmap renderer responsible for loading is already set.
     *
     * @return  the loaded topicmap (a TopicmapViewmodel).
     */
    function load_topicmap(topicmap_id) {
        return topicmap_renderer.load_topicmap(topicmap_id, {
            is_writable: dm4c.has_write_permission_for_topic(topicmap_id)
        })
    }

    /**
     * Reloads the current topicmap from DB.
     */
    function reload_topicmap() {
        // Note: the cookie and the renderer are already up-to-date
        var topicmap_id = topicmap.get_id()
        invalidate_topicmap_cache(topicmap_id)
        topicmap = get_topicmap(topicmap_id)
    }

    // ---

    /**
     * Fetches all Topicmap topics assigned to the selected workspace, and updates the model ("topicmap_topics").
     * <p>
     * If no Topicmap topics are assigned a default topicmap is created. This happens when the user had deleted
     * the workspace's last topicmap.
     */
    function fetch_topicmap_topics() {
        var workspace_id = get_selected_workspace_id()
        var topics = dm4c.restc.get_assigned_topics(workspace_id, "dm4.topicmaps.topicmap", true) // include_childs=true
        // create default topicmap
        if (!topics.length) {
            var topicmap_topic = create_topicmap_topic("untitled")  // renderer=default, private=false
            console.log("Creating default topicmap (ID " + topicmap_topic.id + ") for workspace " + workspace_id)
            topics.push(topicmap_topic)
        }
        //
        topicmap_topics[workspace_id] = dm4c.build_topics(topics)
        // ### TODO: sort topicmaps by name
    }

    /**
     * Returns the loaded topicmap topics for the selected workspace.
     */
    function get_topicmap_topics() {
        return topicmap_topics[get_selected_workspace_id()]
    }

    function get_first_topicmap_id() {
        var topicmap_topic = get_topicmap_topics()[0]
        if (!topicmap_topic) {
            throw "TopicmapsError: no topicmap available"
        }
        return topicmap_topic.id
    }

    function clear_topicmap_topics() {
        topicmap_topics = {}
    }

    // ---

    function get_selected_topicmap_id() {
        var topicmap_id = selected_topicmap_ids[get_selected_workspace_id()]
        if (!topicmap_id) {
            throw "TopicmapsError: no topicmap is selected yet"
        }
        return topicmap_id
    }

    function get_selected_workspace_id() {
        return dm4c.get_plugin("de.deepamehta.workspaces").get_selected_workspace_id()
    }

    // ---

    function select_topicmap_for_workspace(workspace_id) {
        // load topicmap topics for that workspace, if not done already
        if (!get_topicmap_topics()) {
            fetch_topicmap_topics()
        }
        //
        // restore recently selected topicmap for that workspace
        var topicmap_id = selected_topicmap_ids[workspace_id]
        // choose an alternate topicmap if either no topicmap was selected in that workspace ever, or if
        // the formerly selected topicmap is not available anymore. The latter is the case e.g. when the
        // user logs out while a public/common workspace and a private topicmap is selected.
        if (!topicmap_id || !get_topicmap_topic(topicmap_id)) {
            topicmap_id = get_first_topicmap_id()
        }
        set_selected_topicmap(topicmap_id)
    }

    function delete_topicmap(topicmap_id) {
        var is_current_topicmp = topicmap_id == topicmap.get_id()
        invalidate_topicmap_cache(topicmap_id)
        fetch_topicmap_topics()
        if (is_current_topicmp) {
            // If the deleted topicmap was the CURRENT topicmap we must select another one from the topicmap menu.
            // Note: if the last topicmap was deleted another one is already created.
            set_selected_topicmap(get_first_topicmap_id())
        }
        return is_current_topicmp
    }

    // ### Copy in plugin.js ### TODO: drop this
    function create_topicmap_topic(name, topicmap_renderer_uri, private) {
        return dm4c.restc.create_topicmap(name, topicmap_renderer_uri || "dm4.webclient.default_topicmap_renderer",
            private)
    }



    // === Topicmap Renderers ===

    function get_topicmap_renderer(renderer_uri) {
        var renderer = topicmap_renderers[renderer_uri]
        // error check
        if (!renderer) {
            throw "TopicmapsError: \"" + renderer_uri + "\" is an unknown topicmap renderer"
        }
        //
        return renderer
    }

    function iterate_topicmap_renderers(visitor_func) {
        for (var renderer_uri in topicmap_renderers) {
            visitor_func(topicmap_renderers[renderer_uri])
        }
    }



    // === Topicmap Cache ===

    function iterate_topicmap_cache(visitor_func) {
        for (var topicmap_id in topicmap_cache) {
            visitor_func(topicmap_cache[topicmap_id])
        }
    }

    function clear_topicmap_cache() {
        topicmap_cache = {}
    }

    function invalidate_topicmap_cache(topicmap_id) {
        delete topicmap_cache[topicmap_id]
    }
}
// Enable debugging for dynamically loaded scripts:
//# sourceURL=topicmaps_plugin_model.js
