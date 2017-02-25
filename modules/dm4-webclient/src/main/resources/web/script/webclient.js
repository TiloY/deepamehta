dm4c = new function() {

    // preferences
    this.MAX_TOPIC_LABEL_CHARS = 100
    this.MAX_TOPIC_LABEL_WIDTH = 200    // in pixel
    this.ASSOC_WIDTH = 4
    this.ASSOC_CLICK_TOLERANCE = 0.3
    this.DEFAULT_TOPIC_ICON = "/de.deepamehta.webclient/images/ball-gray.png"
    this.DEFAULT_ASSOC_COLOR = "#b2b2b2"
    var DEFAULT_INPUT_FIELD_ROWS = 1

    // constants
    this.COMPOSITE_PATH_SEPARATOR = "/"
    this.REF_ID_PREFIX  = "ref_id:"
    this.REF_URI_PREFIX = "ref_uri:"
    this.DEL_ID_PREFIX  = "del_id:"
    this.DEL_URI_PREFIX = "del_uri:"

    // model
    this.selected_object = null     // A Topic or an Association object, or null if there is no selection ### needed?
                                    // The child topics are included.
    var type_cache = new TypeCache()

    // view
    this.split_panel = null         // a SplitPanel object
    this.toolbar = null             // the upper toolbar GUI component (a ToolbarPanel object)
    this.topicmap_renderer = null   // the GUI component that displays the topicmap (a TopicmapRenderer object)
    this.page_panel = null          // the page panel GUI component on the right hand side (a PagePanel object)

    // utilities
    this.restc = new RESTClient({
        on_send_request: on_send_request,
        on_request_error: on_request_error,
        process_directives: process_directives
    })
    this.ui = new GUIToolkit({
        on_open_menu: on_open_menu
    })
    this.render = new RenderHelper()
    var pm = new PluginManager({
        internal_plugins: ["default_plugin.js", "fulltext_plugin.js"]
    })



    // === REST Client Extension ===

    this.restc.search_topics_and_create_bucket = function(text, field_uri) {
        var params = this.queryParams({search: text, field: field_uri})
        return this.request("GET", "/webclient/search" + params)
    }
    // Note: this method is actually part of the Type Search plugin.
    // TODO: proper modularization. Either let the Type Search plugin provide its own REST resource (with
    // another namespace again) or make the Type Search plugin an integral part of the Client plugin.
    this.restc.get_topics_and_create_bucket = function(type_uri) {
        return this.request("GET", "/webclient/search/by_type/" + type_uri)
    }
    this.restc.get_related_topics = function(topic_id, sort) {
        var result = this.request("GET", "/webclient/topic/" + topic_id + "/related_topics")
        if (sort) {
            this.sort_topics(result)
        }
        return result
    }

    // ------------------------------------------------------------------------------------------------------ Public API



    // ******************
    // *** Controller ***
    // ******************



    // Note: the controller methods consistently update the database, model, and the view.
    // In particular they are responsible for
    //     a) updating the database,
    //     b) updating the model,
    //     c) updating the view (canvas and page panel),
    //     d) firing events
    // The names of the controller methods begins with "do_".
    //
    // Your plugin can call the controller methods via the global "dm4c" object.



    /**
     * Fetches the topic and displays it on the page panel.
     * Fires the "post_select_topic" event (indirectly).
     *
     * Precondition: the topic exists in the database AND is shown on the canvas.
     *
     * @param   no_history_update   Optional: boolean.
     */
    this.do_select_topic = function(topic_id, no_history_update) {
        dm4c.page_panel.save()
        //
        // update view (canvas)
        var topics = dm4c.topicmap_renderer.select_topic(topic_id)
        // update model
        set_topic_selection(topics.select, no_history_update)
        // update view (page panel)
        dm4c.page_panel.render_page(topics.display)
    }

    /**
     * Fetches the association and displays it on the page panel.
     * Fires the "post_select_association" event (indirectly).
     *
     * Precondition: the association exists in the database AND is shown on the canvas.
     *
     * @param   no_history_update   Optional: boolean.
     */
    this.do_select_association = function(assoc_id, no_history_update) {
        dm4c.page_panel.save()
        //
        // update view (canvas)
        var assoc = dm4c.topicmap_renderer.select_association(assoc_id)
        // update model
        set_association_selection(assoc, no_history_update)
        // update view (page panel)
        dm4c.page_panel.render_page(assoc)
    }

    /**
     * Fires the "post_reset_selection" event (indirectly).
     *
     * @param   no_history_update   Optional: boolean.
     */
    this.do_reset_selection = function(no_history_update) {
        // update model
        reset_selection(no_history_update)
        // update view
        dm4c.topicmap_renderer.reset_selection()
        dm4c.page_panel.clear()
    }

    // ---

    /**
     * Fetches a topic and shows it on the canvas.
     * <p>
     * Fires the "pre_show_topic" and "post_show_topic" events (indirectly).
     *
     * @param   topic_id
     *              ID of the topic to reveal.
     * @param   action
     *              Optional: the action to perform on the revealed topic, 3 possible values:
     *                  "none" - do not select the topic (page panel doesn't change) -- the default.
     *                  "show" - select the topic and show its info in the page panel.
     *                  "edit" - select the topic and show its form in the page panel.
     *              If not specified (that is any falsish value) "none" is assumed.
     */
    this.do_reveal_topic = function(topic_id, action) {
        // fetch from DB
        var topic = dm4c.fetch_topic(topic_id, true)        // include_childs=true
        // update model and view
        dm4c.show_topic(topic, action, undefined, true)     // coordinates=undefined, do_center=true
    }

    /**
     * Reveals a topic that is related to the selected topic.
     * Precondition: a topic is selected.
     * <p>
     * Fires the "pre_show_topic" and "post_show_topic" events (indirectly).
     * Fires the "post_show_association" event (for each association).
     *
     * @param   topic_id
     *              ID of the topic to reveal.
     * @param   action
     *              Optional: the action to perform on the revealed topic, 3 possible values:
     *                  "none" - do not select the topic (page panel doesn't change) -- the default.
     *                  "show" - select the topic and show its info in the page panel.
     *                  "edit" - select the topic and show its form in the page panel.
     *              If not specified (that is any falsish value) "none" is assumed.
     * @param   assoc_type_uri
     *              Optional: association type filter.
     *              If not specified (that is any falsish value) no filter is applied.
     */
    this.do_reveal_related_topic = function(topic_id, action, assoc_type_uri) {
        // Note: -1 must not be passed to dm4c.restc.get_associations() as this would be interpreted at
        // server side as "filter not set".
        if (topic_id == -1) {
            throw "InvalidArgumentError: dm4c.do_reveal_related_topic() was called with -1 as topic_id"
        }
        // fetch from DB
        var topic = dm4c.fetch_topic(topic_id, true)        // include_childs=true
        var assocs = dm4c.restc.get_associations(dm4c.selected_object.id, topic_id, assoc_type_uri)
        // update model and view
        dm4c.show_topic(topic, action, undefined, true)     // coordinates=undefined, do_center=true
        for (var i = 0, assoc; assoc = assocs[i]; i++) {
            dm4c.show_association(assoc)
        }
        // Note: dm4c.show_topic() has a side effect on dm4c.selected_object. So, the associations
        // must be fetched before the topic is shown. We can't call dm4c.do_reveal_topic() here.
    }

    // ---

    /**
     * Hides a topic and its visible direct associations from the view (canvas and page panel).
     * Fires the "post_hide_topic" event and the "post_hide_association" event (for each association).
     */
    this.do_hide_topic = function(topic) {
        var assocs = dm4c.topicmap_renderer.get_topic_associations(topic.id)
        // update model and view
        for (var i = 0; i < assocs.length; i++) {
            dm4c.topicmap_renderer.hide_association(assocs[i].id)
            dm4c.fire_event("post_hide_association", assocs[i])
        }
        //
        hide_topic(topic)
    }

    /**
     * Hides an association from the view (canvas and page panel).
     * Fires the "post_hide_association" event.
     */
    this.do_hide_association = function(assoc) {
        // update model and view
        hide_association(assoc)
    }

    // ---

    /**
     * Creates an empty topic in the DB, shows it on the canvas and displays the edit form in the page panel.
     *
     * @param   type_uri    The type of the topic to create.
     * @param   x, y        Optional: the coordinates for placing the topic on the canvas.
     *                      If not specified, placement is up to the canvas.
     */
    this.do_create_topic = function(type_uri, x, y) {
        // update DB
        var topic = dm4c.create_topic(type_uri)
        // update model and view
        dm4c.show_topic(topic, "edit", {x: x, y: y}, true)      // do_center=true
    }

    /**
     * Creates an association in the DB, shows it on the canvas and displays the edit form in the page panel.
     */
    this.do_create_association = function(type_uri, topic_id_1, topic_id_2) {
        // update DB
        var assoc = dm4c.create_association(type_uri,
            {topic_id: topic_id_1, role_type_uri: "dm4.core.default"},
            {topic_id: topic_id_2, role_type_uri: "dm4.core.default"}
        )
        // update model and view
        dm4c.show_association(assoc, "edit")
    }

    this.do_create_topic_type = function(topic_type_model) {
        // update DB
        var topic_type = dm4c.create_topic_type(topic_type_model)
        // update model and view
        dm4c.show_topic(topic_type, "edit", undefined, true)    // coordinates=undefined, do_center=true
    }

    this.do_create_association_type = function(assoc_type_model) {
        // update DB
        var assoc_type = dm4c.create_association_type(assoc_type_model)
        // update model and view
        dm4c.show_topic(assoc_type, "edit", undefined, true)    // coordinates=undefined, do_center=true
    }

    this.do_create_role_type = function(topic_model) {
        // update DB
        var role_type = dm4c.create_role_type(topic_model)
        // update model and view
        dm4c.show_topic(role_type, "edit", undefined, true)     // coordinates=undefined, do_center=true
    }

    // ---

    /**
     * Updates a topic in the DB and on the view.
     * Fires the "pre_update_topic" and "post_update_topic" (indirectly) events.
     *
     * @param   topic_model     Optional: a topic model containing the data to be udpated.
     *                          If not specified no DB update is performed but the page panel is still refreshed.
     */
    this.do_update_topic = function(topic_model) {
        if (topic_model) {
            dm4c.fire_event("pre_update_topic", topic_model)
            // update DB, model, and view
            return dm4c.restc.update_topic(topic_model)
        } else {
            dm4c.page_panel.refresh()
        }
    }

    /**
     * Updates an association in the DB and on the view.
     * Fires the "pre_update_association" and "post_update_association" (indirectly) events.
     *
     * @param   assoc_model     an association model containing the data to be udpated.
     *
     * ### TODO: remove stay_in_edit_mode parameter
     */
    this.do_update_association = function(assoc_model, stay_in_edit_mode) {
        dm4c.fire_event("pre_update_association", assoc_model)
        // update DB, model, and view
        return dm4c.restc.update_association(assoc_model, stay_in_edit_mode)
    }

    // ---

    /**
     * Updates a topic type in the DB and on the view.
     * Fires the "post_update_topic" event (indirectly).
     *
     * @param   topic_type_model    a topic type model containing the data to be udpated.
     */
    this.do_update_topic_type = function(topic_type_model) {
        // update DB, model, and view
        dm4c.restc.update_topic_type(topic_type_model)
    }

    /**
     * Updates an association type in the DB and on the view.
     * Fires the "post_update_topic" event (indirectly).
     *
     * @param   assoc_type_model    an association type model containing the data to be udpated.
     */
    this.do_update_association_type = function(assoc_type_model) {
        // update DB, model, and view
        dm4c.restc.update_association_type(assoc_type_model)
    }

    // ---

    /**
     * Deletes a topic (including its associations) from the DB and the view.
     * Fires the "post_delete_topic" event and the "post_delete_association" event (for each association).
     */
    this.do_delete_topic = function(topic_id) {
        // update DB, model, and view
        dm4c.restc.delete_topic(topic_id)
    }

    /**
     * Deletes an association from the DB and the view.
     * Fires the "post_delete_association" event.
     */
    this.do_delete_association = function(assoc_id) {
        // update DB, model, and view
        dm4c.restc.delete_association(assoc_id)
    }

    this.do_delete_topic_type = function(type_uri) {
        // update DB, model, and view
        dm4c.restc.delete_topic_type(type_uri)
    }

    this.do_delete_association_type = function(type_uri) {
        // update DB, model, and view
        dm4c.restc.delete_association_type(type_uri)
    }



    // *************************
    // *** Controller Helper ***
    // *************************



    /**
     * Shows a topic on the canvas, and refreshes the page panel according to the specified action.
     * Fires the "pre_show_topic" and "post_show_topic" events.
     * <p>
     * Preconditions:
     * - the topic exists in the DB.
     * - the topic is (typically) not yet shown on the canvas, and thus:
     *     - the topic is not selected
     *     - the topic has no gemetry yet
     *
     * @param   topic           The topic to show (a Topic object).
     * @param   action          Optional: the action to perform on the topic, 3 possible values:
     *                              "none" - do not select the topic (page panel doesn't change) -- the default.
     *                              "show" - select the topic and show its info in the page panel.
     *                              "edit" - select the topic and show its form in the page panel.
     *                          If not specified (that is any falsish value) "none" is assumed.
     * @param   coordinates     Optional: the coordinates for placing the topic on the canvas (an object with
     *                          "x" and "y" properties). If not specified, placement is up to the canvas.
     * @param   do_center       Optional: if evaluates to true the topic is centered on the canvas.
     */
    this.show_topic = function(topic, action, coordinates, do_center) {
        action = action || "none"   // set default
        if (coordinates) {
            topic.x = coordinates.x
            topic.y = coordinates.y
        }
        var do_select = action != "none"
        // Note: the "pre_show_topic" event allows plugins to manipulate the topic, e.g. by setting coordinates
        dm4c.fire_event("pre_show_topic", topic)                // fire event
        // update view (canvas)
        var topic_shown = dm4c.topicmap_renderer.show_topic(topic, do_select)
        if (topic_shown) {
            if (do_center) {
                dm4c.topicmap_renderer.scroll_topic_to_center(topic_shown.id)
            }
            // update model
            if (do_select) {
                set_topic_selection(topic_shown)
            }
            //
            dm4c.fire_event("post_show_topic", topic_shown)     // fire event
        } else {
            // update model
            if (do_select) {
                set_topic_selection(topic)
            }
        }
        // update view (page panel)
        update_page_panel(topic, action)
    }

    /**
     * @param   assoc   The association to show (an Association object).
     */
    this.show_association = function(assoc, action) {
        action = action || "none"   // set default
        var do_select = action != "none"
        // update view (canvas)
        dm4c.topicmap_renderer.show_association(assoc, do_select)
        // update model
        if (do_select) {
            set_association_selection(assoc)
        }
        //
        dm4c.fire_event("post_show_association", assoc)    // fire event
        // update view (page panel)
        update_page_panel(assoc, action)
    }

    function update_page_panel(topic_or_association, action) {
        switch (action) {
        case "none":
            break
        case "show":
            dm4c.page_panel.render_page(topic_or_association)
            break
        case "edit":
            dm4c.enter_edit_mode(topic_or_association)
            break
        default:
            throw "WebclientError: \"" + action + "\" is an unsupported page panel action"
        }
    }

    // ---

    /**
     * @param   topic_or_association    A Topic object or an Association object.
     *
     * Note: because of the Webclient's intrinsic UI logic -- the page panel displays nothing but the selected
     * object -- no argument should be required here (it should always be dm4c.selected_object). However, custom
     * topicmap renderers may break this principle.
     * In fact the Geomaps renderer does: the selection model contains the sole "Geo Coordinate" topic while
     * the page panel displays the geo-aware topic, e.g. a Person.
     */
    this.enter_edit_mode = function(topic_or_association) {
        // update view
        dm4c.page_panel.render_form(topic_or_association)
    }

    // ---

    /**
     * Updates the model and view according to a set of directives received from server.
     * Precondition: the DB is already up-to-date.
     *
     * ### TODO: remove stay_in_edit_mode parameter
     */
    function process_directives(directives, stay_in_edit_mode) {
        for (var i = 0, directive; directive = directives[i]; i++) {
            switch (directive.type) {
            case "UPDATE_TOPIC":
                update_topic(build_topic(directive.arg))
                break
            case "DELETE_TOPIC":
                delete_topic(build_topic(directive.arg))
                break
            case "UPDATE_ASSOCIATION":
                update_association(build_association(directive.arg), stay_in_edit_mode)
                break
            case "DELETE_ASSOCIATION":
                delete_association(build_association(directive.arg))
                break
            case "UPDATE_TOPIC_TYPE":
                update_topic_type(build_topic_type(directive.arg))
                break
            case "DELETE_TOPIC_TYPE":
                delete_topic_type(directive.arg.uri)
                break
            case "UPDATE_ASSOCIATION_TYPE":
                update_association_type(build_association_type(directive.arg))
                break
            case "DELETE_ASSOCIATION_TYPE":
                delete_association_type(directive.arg.uri)
                break
            default:
                throw "WebclientError: \"" + directive.type + "\" is an unsupported directive"
            }
        }
    }

    // ---

    /**
     * Updates a topic on the view (canvas and page panel).
     * Fires the "post_update_topic" event.
     *
     * Processes an UPDATE_TOPIC directive.
     *
     * @param   a Topic object
     */
    function update_topic(topic) {
        // update model
        var old_topic = set_topic_selection_conditionally(topic)
        // update view
        dm4c.topicmap_renderer.update_topic(topic)
        dm4c.page_panel.render_page_if_selected(topic)
        //
        dm4c.fire_event("post_update_topic", topic, old_topic)
    }

    /**
     * Updates an association on the view (canvas and page panel).
     * Fires the "post_update_association" event.
     *
     * Processes an UPDATE_ASSOCIATION directive.
     *
     * @param   an Association object
     *
     * ### TODO: remove stay_in_edit_mode parameter
     */
    function update_association(assoc, stay_in_edit_mode) {
        // update model
        var old_assoc = set_association_selection_conditionally(assoc)
        // update view
        dm4c.topicmap_renderer.update_association(assoc)
        stay_in_edit_mode ? dm4c.page_panel.render_form_if_selected(assoc) :
                            dm4c.page_panel.render_page_if_selected(assoc)
        //
        dm4c.fire_event("post_update_association", assoc, old_assoc)
    }

    // ---

    /**
     * Processes an UPDATE_TOPIC_TYPE directive.
     */
    function update_topic_type(topic_type) {
        // 1) update model (type cache)
        type_cache.put_topic_type(topic_type)
        // 2) update view
        // Note: the UPDATE_TOPIC_TYPE directive might result from editing a View Configuration topic.
        // In this case the canvas must be refreshed in order to reflect changed topic icons.
        dm4c.topicmap_renderer.update_topic_type(topic_type)
        // 3) fire event
        dm4c.fire_event("post_update_topic", topic_type)
    }

    /**
     * Processes an UPDATE_ASSOCIATION_TYPE directive.
     */
    function update_association_type(assoc_type) {
        // 1) update model (type cache)
        type_cache.put_association_type(assoc_type)
        // 2) update view
        // Note: the UPDATE_ASSOCIATION_TYPE directive might result from editing a View Configuration topic.
        // In this case the canvas must be refreshed in order to reflect changed association colors.
        dm4c.topicmap_renderer.update_association_type(assoc_type)
        // 3) fire event
        // dm4c.fire_event("post_update_topic", assoc_type)     // ### FIXME: why commented?
    }

    // ---

    /**
     * Removes an topic from the view (canvas and page panel). ### FIXDOC
     * Fires the "post_hide_topic" event.
     */
    function hide_topic(topic) {
        // update view
        dm4c.topicmap_renderer.hide_topic(topic.id)
        dm4c.page_panel.clear_if_selected(topic)
        // update model
        reset_selection_conditionally(topic.id)
        //
        dm4c.fire_event("post_hide_topic", topic)
    }

    /**
     * Removes an association from the view (canvas and page panel). ### FIXDOC
     * Fires the "post_hide_association" event.
     */
    function hide_association(assoc) {
        // update view
        dm4c.topicmap_renderer.hide_association(assoc.id)
        dm4c.page_panel.clear_if_selected(assoc)
        // update model
        reset_selection_conditionally(assoc.id)
        //
        dm4c.fire_event("post_hide_association", assoc)
    }

    // ---

    /**
     * Removes an topic from the view (canvas and page panel). ### FIXDOC
     * Fires the "post_delete_topic" event.
     *
     * Processes a DELETE_TOPIC directive.
     */
    function delete_topic(topic) {
        // update view
        dm4c.topicmap_renderer.delete_topic(topic.id)
        dm4c.page_panel.clear_if_selected(topic)
        // update model
        reset_selection_conditionally(topic.id)
        //
        dm4c.fire_event("post_delete_topic", topic)
    }

    /**
     * Removes an association from the view (canvas and page panel). ### FIXDOC
     * Fires the "post_delete_association" event.
     *
     * Processes a DELETE_ASSOCIATION directive.
     */
    function delete_association(assoc) {
        // update view
        dm4c.topicmap_renderer.delete_association(assoc.id)
        dm4c.page_panel.clear_if_selected(assoc)
        // update model
        reset_selection_conditionally(assoc.id)
        //
        dm4c.fire_event("post_delete_association", assoc)
    }

    // ---

    /**
     * Processes a DELETE_TOPIC_TYPE directive.
     */
    function delete_topic_type(topic_type_uri) {
        // update model (type cache)
        type_cache.remove_topic_type(topic_type_uri)
        // update view
        dm4c.refresh_create_menu()
        //
        dm4c.fire_event("post_delete_topic_type", topic_type_uri)
    }

    /**
     * Processes a DELETE_ASSOCIATION_TYPE directive.
     */
    function delete_association_type(assoc_type_uri) {
        // update model (type cache)
        type_cache.remove_association_type(assoc_type_uri)
        //
        dm4c.fire_event("post_delete_association_type", assoc_type_uri)
    }

    // --- Client Model Update ---

    function set_topic_selection_conditionally(topic) {
        if (topic.id == dm4c.selected_object.id) {
            var old_topic = dm4c.selected_object
            set_topic_selection(topic, true)            // no_history_update=true
            return old_topic
        }
    }

    function set_association_selection_conditionally(assoc) {
        if (assoc.id == dm4c.selected_object.id) {
            var old_assoc = dm4c.selected_object
            set_association_selection(assoc, true)      // no_history_update=true
            return old_assoc
        }
    }

    function reset_selection_conditionally(object_id) {
        if (object_id == dm4c.selected_object.id) {
            reset_selection()                           // ### TODO: history update?
        }
    }



    // ********************
    // *** Client Model ***
    // ********************



    // === Selection ===

    function set_topic_selection(topic, no_history_update) {
        dm4c.selected_object = topic
        //
        if (!no_history_update) {
            push_history(topic)
        }
        //
        dm4c.fire_event("post_select_topic", topic)
    }

    function set_association_selection(assoc, no_history_update) {
        dm4c.selected_object = assoc
        //
        dm4c.fire_event("post_select_association", assoc)
    }

    function reset_selection(no_history_update) {
        dm4c.selected_object = null
        //
        if (!no_history_update) {
            push_history()
        }
        //
        dm4c.fire_event("post_reset_selection")
    }



    // ***********************
    // *** Database Helper ***
    // ***********************



    /**
     * Creates a topic in the DB.
     * Fires the "post_create_topic" event.
     *
     * @param   type_uri    The topic type URI, e.g. "dm4.notes.note".
     * @param   childs      Optional.
     *
     * @return  The topic as stored in the DB.
     */
    this.create_topic = function(type_uri, childs) {
        // 1) update DB
        var topic_model = {
            // Note: "uri", "value", and "childs" are optional
            type_uri: type_uri,
            childs: childs    // not serialized to request body if undefined
        }
        var topic = build_topic(dm4c.restc.create_topic(topic_model))
        // 2) fire event
        dm4c.fire_event("post_create_topic", topic)
        //
        return topic
    }

    /**
     * Creates an association in the DB.
     *
     * @param   type_uri            The association type URI, e.g. "dm4.core.instantiation".
     * @param   role_1              The topic role or association role at one end (an object).
     *                              Examples for a topic role:
     *                                  {topic_uri: "dm4.core.cardinality", role_type_uri: "dm4.core.type"},
     *                                  {topic_id: 123,                     role_type_uri: "dm4.core.instance"},
     *                              The topic can be identified either by URI or by ID.
     *                              Example for an association role:
     *                                  {assoc_id: 456, role_type_uri: "dm4.core.assoc_def"},
     *                              The association is identified by ID.
     * @param   role_2              The topic role or association role at the other end (an object, like role_1).
     *
     * @return  The association as stored in the DB.
     */
    this.create_association = function(type_uri, role_1, role_2) {
        var assoc_model = {
            type_uri: type_uri,
            role_1: role_1,
            role_2: role_2
        }
        // FIXME: no "create" events are fired
        return build_association(dm4c.restc.create_association(assoc_model))
    }

    this.create_topic_type = function(topic_type_model) {
        // 1) update DB
        var topic_type = build_topic_type(dm4c.restc.create_topic_type(topic_type_model))
        // 2) update model (type cache)
        // Note: the type cache must be updated *before* the "post_create_topic" event is fired.
        // Other plugins might rely on an up-to-date type cache (e.g. the Type Search plugin does).
        type_cache.put_topic_type(topic_type)
        // 3) fire event
        dm4c.fire_event("post_create_topic", topic_type)
        //
        return topic_type
    }

    this.create_association_type = function(assoc_type_model) {
        // 1) update DB
        var assoc_type = build_association_type(dm4c.restc.create_association_type(assoc_type_model))
        // 2) update model (type cache)
        // Note: the type cache must be updated *before* the "post_create_topic" event is fired.
        // Other plugins might rely on an up-to-date type cache (e.g. the Type Search plugin does). ### FIXDOC
        type_cache.put_association_type(assoc_type)
        // 3) fire event
        dm4c.fire_event("post_create_topic", assoc_type)
        //
        return assoc_type
    }

    this.create_role_type = function(topic_model) {
        // 1) update DB
        var role_type = build_topic(dm4c.restc.create_role_type(topic_model))
        // 2) fire event
        dm4c.fire_event("post_create_topic", role_type)
        //
        return role_type
    }



    // **********************
    // *** Plugin Support ***
    // **********************



    this.add_plugin = function(plugin_uri, plugin_func) {
        pm.add_plugin(plugin_uri, plugin_func)
    }

    this.get_plugin = function(plugin_uri) {
        return pm.get_plugin(plugin_uri)
    }

    // ---

    this.add_simple_renderer = function(renderer_uri, renderer) {
        pm.add_simple_renderer(renderer_uri, renderer)
    }

    this.get_simple_renderer = function(renderer_uri) {
        return pm.get_simple_renderer(renderer_uri)
    }

    // ---

    this.add_multi_renderer = function(renderer_uri, renderer) {
        pm.add_multi_renderer(renderer_uri, renderer)
    }

    this.get_multi_renderer = function(renderer_uri) {
        return pm.get_multi_renderer(renderer_uri)
    }

    // ---

    this.add_page_renderer = function(renderer_uri, renderer) {
        pm.add_page_renderer(renderer_uri, renderer)
    }

    this.get_page_renderer = function(topic_or_association_or_renderer_uri) {
        return pm.get_page_renderer(topic_or_association_or_renderer_uri)
    }

    // ---

    /**
     * Loads a Javascript file dynamically. Synchronous and asynchronous loading is supported.
     *
     * @param   url     The URL (absolute or relative) of the Javascript file to load.
     * @param   async   Optional (boolean):
     *                      If true loading is asynchronous.
     *                      If false or not given loading is synchronous.
     */
    this.load_script = function(url, async) {
        $.ajax({
            url: url,
            dataType: "script",
            async: async || false,
            error: function(jq_xhr, text_status, error_thrown) {
                throw "WebclientError: loading script " + url + " failed (" + text_status + ": " + error_thrown + ")"
            }
        })
    }

    this.load_stylesheet = function(stylesheet) {
        pm.load_stylesheet(stylesheet)
    }

    // ---

    this.add_listener = function(event_name, listener) {
        pm.add_listener(event_name, listener)
    }

    // ---

    /**
     * Fires an event.
     *
     * @param   event_name  Name of the event.
     * @param   <varargs>   Variable number of event arguments.
     *
     * @return  An array populated with the listener return values. Might be empty.
     *          Note: undefined listener return values are not included in the array, but null values are.
     */
    this.fire_event = function(event_name) {
        return pm.dispatch_event.apply(undefined, arguments)
    }

    // ---

    function on_send_request(request) {
        dm4c.fire_event("pre_send_request", request)
    }

    function on_request_error(server_response) {
        dm4c.open_error_dialog(server_response)
    }

    /**
     * Opens an error dialog and renders a server response.
     *
     * The server response will be rendered as
     * 1) either arbitrary error text or as a chain of exceptions, and
     * 2) the server status.
     *
     * A chain of exceptions is rendered if the server response meets these conditions: "content_type" is
     * "application/json" and "content" is the JSON representation of a Java exception (as occurred at server-side):
     * an object with "exception", "message", and "cause" properties. The "cause" value is again an exception object.
     * The final exception has no "cause" property.
     *
     * @param   server_response  The server response to render. An object with 4 properties:
     *              content_type -- media type of the error content (string)
     *              content      -- the error content (string)
     *              status_code  -- the server status code (number)
     *              status_text  -- the server status text (string)
     */
    this.open_error_dialog = function(server_response) {

        dm4c.ui.dialog({
            id: "error-dialog",
            title: "Sorry!",
            content: render_content()
        })

        function render_content() {
            var content
            // render exception
            if (server_response.content_type == "application/json") {
                var error = JSON.parse(server_response.content)
                if (error.exception) {
                    content = render_exception(error)
                }
            }
            // render text
            if (!content) {
                content = $("<div>").text(server_response.content)
            }
            // render status
            return content.add(
                $("<div>").text("Server status: " + server_response.status_code + " " + server_response.status_text)
            )
        }

        function render_exception(exception) {
            var content = $()
            _render_exception(exception, true)
            return content

            function _render_exception(exception, is_top_level) {
                content = content.add($("<div>").addClass("exception")
                    .append($("<div>").text(is_top_level ? "" : "Reason:"))
                    .append(js.render_text(exception.message))
                    .append($("<div>").addClass("class").text("(" + exception.exception + ")"))
                )
                if (exception.cause) {
                    _render_exception(exception.cause)
                }
            }
        }
    }



    // **************
    // *** Helper ***
    // **************



    // === Topics ===

    this.hash_by_id = function(topics) {
        var hashed_topics = {}
        for (var i = 0, topic; topic = topics[i]; i++) {
            hashed_topics[topic.id] = topic
        }
        return hashed_topics
    }

    this.hash_by_type = function(topics) {
        var hashed_topics = {}
        for (var i = 0, topic; topic = topics[i]; i++) {
            hashed_topics[topic.type_uri] = topic
        }
        return hashed_topics
    }

    // ---

    /**
     * Creates an empty topic.
     */
    this.empty_topic = function(topic_type_uri) {
        return new Topic({
            id: -1, uri: "", type_uri: topic_type_uri, value: "", childs: {}
        })
    }

    this.empty_association = function(assoc_type_uri) {
        return new Association({
            id: -1, uri: "", type_uri: assoc_type_uri, value: "", childs: {}
        })
    }



    // === Types ===

    /**
     * Looks up a topic type by its uri.
     */
    this.get_topic_type = function(type_uri) {
        return type_cache.get_topic_type(type_uri)
    }

    /**
     * Looks up an association type by its uri.
     */
    this.get_association_type = function(type_uri) {
        return type_cache.get_association_type(type_uri)
    }

    // ---

    function has_topic_type(topic_type_uri) {
        return type_cache.has_topic_type(topic_type_uri)
    }

    function has_association_type(assoc_type_uri) {
        return type_cache.has_association_type(assoc_type_uri)
    }

    // ---

    this.enforce_implicit_topic_type_read_permission = function(topic) {
        type_cache.enforce_implicit_topic_type_read_permission(topic)
    }

    this.enforce_implicit_association_type_read_permission = function(assoc) {
        type_cache.enforce_implicit_association_type_read_permission(assoc)
    }

    // ---

    /**
     * Convenience method that returns the topic type's name.
     */
    this.topic_type_name = function(type_uri) {
        return dm4c.get_topic_type(type_uri).value
    }

    /**
     * Convenience method that returns the association type's name.
     */
    this.association_type_name = function(type_uri) {
        return dm4c.get_association_type(type_uri).value
    }

    // ---

    /**
     * Convenience method that returns the topic type's icon source.
     *
     * @return  The icon source (string).
     */
    this.get_type_icon_src = function(type_uri) {
        return dm4c.get_topic_type(type_uri).get_icon_src()
    }

    /**
     * Convenience method that returns the topic type's icon.
     *
     * @return  The icon (JavaScript Image object)
     */
    this.get_type_icon = function(topic_type_uri) {
        return dm4c.get_topic_type(topic_type_uri).get_icon()
    }

    /**
     * Convenience method that returns the association type's color.
     *
     * @return  The color (CSS string)
     */
    this.get_type_color = function(assoc_type_uri) {
        return dm4c.get_association_type(assoc_type_uri).get_color()
    }

    // ---

    /**
     * Creates a list of all the topic types about to appear in a Create menu (that is toolbar menu or context menu).
     * These are the types where "Show in Create Menu" is set (in the type's view configuration).
     *
     * @return  the list of topic types.
     */
    this.topic_type_list = function() {
        return type_cache.get_topic_types(function(topic_type) {
            return topic_type.get_menu_config("create-type-menu")
        })
    }

    /**
     * Clears the type cache and loads all types. Once complete calls the callback function.
     * <p>
     * Note: this is a utility function callable by plugins (see Access Control plugin).
     * It is not used for the the initial type loading (see document-ready function below).
     */
    this.reload_types = function(callback) {
        type_cache.clear()
        type_cache.load_types(new LoadTracker(2, function() {   // 2 loads: topic types and association types
            callback()
            // Note: adjust_create_widget() must be called *after* the callback.
            // Adjusting the create widget relies on an updated permission cache (see Access Control plugin).
            adjust_create_widget()
        }))
    }



    // === View Configuration ===

    /**
     * Reads out a view configuration setting.
     * <p>
     * Compare to server-side counterparts: WebclientPlugin.getViewConfig() and ViewConfiguration.getSetting()
     *
     * @param   configurable    A topic type or an association type.
     * @param   setting         Last component of the setting URI, e.g. "icon".
     * @param   assoc_def       Optional: if given its setting has precedence.
     *
     * @return  The configuration setting.
     */
    this.get_view_config = function(configurable, setting, assoc_def) {
        // assoc def overrides child type
        if (assoc_def) {
            var value = get_view_config(assoc_def)
            if (is_set(value)) {
                return value
            }
        }
        // return child type setting
        value = get_view_config(configurable)
        if (is_set(value)) {
            return value
        }
        // return default setting
        return get_view_config_default(configurable, setting)

        function is_set(value) {
            // Note: assoc defs can't override with 0, "", or undefined. But can override with false.
            //    0         -> false
            //    ""        -> false
            //    undefined -> false
            //    false     -> true    allows assoc def to override with false
            return value || value === false
        }

        function get_view_config(configurable) {
            // error check
            if (!configurable.view_config_topics) {
                throw "InvalidConfigurableError: no \"view_config_topics\" property found in " +
                    JSON.stringify(configurable)
            }
            // every configurable has an view_config_topics object, however it might be empty
            var view_config = configurable.view_config_topics["dm4.webclient.view_config"]
            if (view_config) {
                return view_config.get("dm4.webclient." + setting)
            }
        }

        function get_view_config_default() {
            switch (setting) {
            case "icon":
                return dm4c.DEFAULT_TOPIC_ICON
            case "color":
                return dm4c.DEFAULT_ASSOC_COLOR
            case "show_in_create_menu":
                return false;
            case "input_field_rows":
                return DEFAULT_INPUT_FIELD_ROWS
            case "hidden":
                return false
            case "locked":
                return false
            case "page_renderer_uri":
                return default_page_renderer_uri()
            case "simple_renderer_uri":
                return default_simple_renderer_uri()
            case "multi_renderer_uri":
                return "dm4.webclient.default_multi_renderer"
            case "searchable_as_unit":
                return false;
            default:
                throw "WebclientError: \"" + setting + "\" is an unsupported view configuration setting"
            }

            function default_page_renderer_uri() {
                if (configurable instanceof TopicType) {
                    return "dm4.webclient.topic_renderer"
                } else if (configurable instanceof AssociationType) {
                    return "dm4.webclient.association_renderer"
                }
                throw "InvalidConfigurableError: " + JSON.stringify(configurable)
            }

            function default_simple_renderer_uri() {
                switch (configurable.data_type_uri) {
                case "dm4.core.text":
                    return "dm4.webclient.text_renderer"
                case "dm4.core.html":
                    return "dm4.webclient.html_renderer"
                case "dm4.core.number":
                    return "dm4.webclient.number_renderer"
                case "dm4.core.boolean":
                    return "dm4.webclient.boolean_renderer"
                case "dm4.core.composite":
                    return "dm4.webclient.default_composite_renderer"
                default:
                    throw "WebclientError: \"" + configurable.data_type_uri + "\" is an unsupported data type URI"
                }
            }
        }
    }



    // === Commands ===

    this.get_topic_commands = function(topic, context) {
        return get_commands(dm4c.fire_event("topic_commands", topic), context)
    }

    this.get_association_commands = function(assoc, context) {
        return get_commands(dm4c.fire_event("association_commands", assoc), context)
    }

    /**
     * @param   cx, cy  The position where the canvas was clicked, in TOPICMAP coordinate space.
     */
    this.get_canvas_commands = function(cx, cy, context) {
        return get_commands(dm4c.fire_event("canvas_commands", cx, cy), context)
    }

    function get_commands(cmd_lists, context) {
        var commands = []
        for (var i = 0, cmds; cmds = cmd_lists[i]; i++) {
            for (var j = 0, cmd; cmd = cmds[j]; j++) {
                if (matches(cmd, context)) {
                    commands.push(cmd)
                }
            }
        }
        return commands

        function matches(cmd, context) {
            if (typeof cmd.context == "string") {
                return cmd.context == context
            } else {
                return js.contains(cmd.context, context)
            }
        }
    }



    // === Permissions ===

    this.has_write_permission_for_topic = function(topic_id) {
        var result = dm4c.fire_event("has_write_permission_for_topic", topic_id)
        return !js.contains(result, false)
    }

    this.has_write_permission_for_association = function(assoc_id) {
        var result = dm4c.fire_event("has_write_permission_for_association", assoc_id)
        return !js.contains(result, false)
    }

    // ---

    this.has_retype_permission_for_association = function(assoc, assoc_type) {
        var result = dm4c.fire_event("has_retype_permission_for_association", assoc, assoc_type)
        return !js.contains(result, false)
    }

    // ---

    // Note: calculating the READ and CREATE permissions for topic types is sole Webclient logic.
    // The READ permission for types is implicit: the Webclient loads all readable types into its type cache.
    // If a type is not readable, the server would not send it in the first place. The type cache is invalidated
    // once the login state changes. Thus the type cache always represents the set of readable types.
    // The CREATE permission for types is synthesized: the type must the readable *and* the selected workspace
    // must be writable.

    // ### TODO: add the same for association types?
    this.has_read_permission_for_topic_type = function(topic_type_uri) {
        return is_topic_type_readable(topic_type_uri)
    }

    // ---

    this.has_create_permission_for_topic_type = function(topic_type_uri) {
        return is_topic_type_readable(topic_type_uri) && dm4c.is_workspace_writable()
    }

    this.has_create_permission_for_association_type = function(assoc_type_uri) {
        return is_association_type_readable(assoc_type_uri) && dm4c.is_workspace_writable()
    }

    // ---

    function is_topic_type_readable(topic_type_uri) {
        // Note: at startup the webclient loads all readable types into the type cache
        return has_topic_type(topic_type_uri)
    }

    function is_association_type_readable(assoc_type_uri) {
        // Note: at startup the webclient loads all readable types into the type cache
        return has_association_type(assoc_type_uri)
    }

    // ---

    /**
     * Returns true if the current user has write access to the selected workspace.
     */
    this.is_workspace_writable = function() {
        // Note: the Workspaces plugin is a Webclient dependency
        var workspace_id = dm4c.get_plugin("de.deepamehta.workspaces").get_selected_workspace_id()
        return dm4c.has_write_permission_for_topic(workspace_id)
    }



    // === GUI ===

    /**
     * Called once all plugins are loaded.
     * Note: the types are already loaded as well.
     */
    function setup_gui() {
        // 1) Initialize plugins
        // Note: in order to let a plugin provide the initial canvas rendering (the deepamehta-topicmaps plugin
        // does!) the "init" event is fired *after* creating the canvas.
        // Note: for displaying an initial topic (the deepamehta-topicmaps plugin does!) the "init" event must
        // be fired *after* the GUI setup is complete.
        dm4c.fire_event("init")
        dm4c.fire_event("init_2")
        dm4c.fire_event("init_3")
        //
        // 2) Setting up the create widget
        // Note: the create menu must be popularized *after* the plugins are loaded.
        // One event is involved: "post_refresh_create_menu".
        // Note: adjust_create_widget() must be called *after* firing the "init" events.
        // Adjusting the create widget relies on a initialized Workspaces plugin, in particular a current workspace.
        adjust_create_widget()
    }

    function adjust_create_widget() {
        dm4c.refresh_create_menu()
        dm4c.adjust_create_menu_visibility()
    }

    /**
     * Save the page panel before the user opens a menu.
     *
     * @param   menu    a GUIToolkit Menu object.
     */
    function on_open_menu(menu) {
        // react only on menus that are not part of the page content
        if (menu.dom.parents("#page-content").length == 0) {
            dm4c.page_panel.save()
        }
    }

    // --- Context Menus ---

    /**
     * @param   pos     The position where the context menu appears, in WINDOW coordinate space
     *                  (object with x and y properties).
     */
    this.open_topic_contextmenu = function(topic_id, pos) {
        dm4c.do_select_topic(topic_id)
        // Note: only dm4c.selected_object has the composite value (the TopicView has not)
        var commands = dm4c.get_topic_commands(dm4c.selected_object, "context-menu")
        open_context_menu(commands, pos)
    }

    /**
     * @param   pos     The position where the context menu appears, in WINDOW coordinate space
     *                  (object with x and y properties).
     */
    this.open_association_contextmenu = function(assoc_id, pos) {
        dm4c.do_select_association(assoc_id)
        // Note: only dm4c.selected_object has the composite value (the AssociationView has not)
        var commands = dm4c.get_association_commands(dm4c.selected_object, "context-menu")
        open_context_menu(commands, pos)
    }

    /**
     * @param   pos     The position where the context menu appears, in WINDOW coordinate space
     *                  (object with x and y properties).
     * @param   p       The position where the context menu appears, in TOPICMAP coordinate space
     *                  (object with x and y properties).
     */
    this.open_canvas_contextmenu = function(pos, p) {
        var commands = dm4c.get_canvas_commands(p.x, p.y, "context-menu")
        open_context_menu(commands, pos)
    }

    /**
     * Builds a context menu from a set of commands and opens it.
     *
     * @param   commands    Array of commands. May be empty. Must not null/undefined.
     * @param   pos         The position where the context menu appears, in WINDOW coordinate space
     *                      (object with x and y properties).
     */
    function open_context_menu(commands, pos) {
        if (commands.length) {
            var context_menu = dm4c.ui.context_menu($("#topicmap-panel"))
            for (var i = 0, cmd; cmd = commands[i]; i++) {
                if (cmd.is_separator) {
                    context_menu.add_separator()
                } else {
                    context_menu.add_item(menu_item(cmd))
                }
            }
            //
            context_menu.open(pos.x, pos.y)
        }

        function menu_item(command) {
            var menu_item = {
                label:    command.label,
                icon:     command.icon,
                handler:  command.handler,
                disabled: command.disabled
            }
            if (command.sub_commands) {
                menu_item.submenu_items = submenu_items(command.sub_commands)
            }
            return menu_item
        }

        function submenu_items(sub_commands) {
            var items = []
            for (var i = 0, cmd; cmd = sub_commands[i]; i++) {
                items.push(menu_item(cmd))
            }
            return items
        }
    }

    // --- Menus ---

    /**
     * Refreshes a type menu.
     * <p>
     * Callable by plugins to reflect an updated type cache (after adding/removing/renaming a type).
     *
     * @param   type_menu       the type menu to refresh (a GUIToolkit Menu object).
     * @param   topic_types     Optional: the topic types to add to the menu (array of TopicType).
     *                          If not specified all available topic types (as existing in the type cache) are added.
     */
    this.refresh_type_menu = function(type_menu, topic_types) {
        // save selection
        var item = type_menu.get_selection()
        // remove all items
        type_menu.empty()
        // add topic type items
        var types = topic_types || type_cache.get_topic_types()
        for (var i = 0, topic_type; topic_type = types[i]; i++) {
            type_menu.add_item({
                label: topic_type.value,
                value: topic_type.uri,
                icon:  topic_type.get_icon_src()
            })
        }
        // restore selection
        if (item) {
            type_menu.select(item.value)
        }
    }

    /**
     * Refreshes the create menu to reflect an updated type cache (after adding/removing/renaming a type).
     * <p>
     * Utility method for plugin developers.
     */
    this.refresh_create_menu = function() {
        var type_menu = dm4c.toolbar.create_menu
        dm4c.refresh_type_menu(type_menu, this.topic_type_list())
        dm4c.fire_event("post_refresh_create_menu", type_menu)
    }

    this.adjust_create_menu_visibility = function() {
        if (dm4c.is_workspace_writable()) {
            dm4c.toolbar.create_widget.show()
        } else {
            dm4c.toolbar.create_widget.hide()
        }
    }

    // ---

    // ### TODO: formulate this as an jQuery extension
    this.on_return_key = function(element, callback) {
        element.keyup(function(event) {
            if (event.which == 13) {
                return callback();  // stop propagation if the callback returns false
            }
        })
    }



    // === Images ===

    var image_tracker   // ### FIXME: the image tracker is global. There can only be one at a time.

    this.create_image = function(src) {
        var img = new Image()
        img.src = src   // Note: if src is a relative URL JavaScript extends img.src to an absolute URL
        img.onload = function() {
            // Note: "this" is the image. The argument is the "load" event.
            //
            // notify image tracker
            image_tracker && image_tracker.check()
        }
        return img
    }

    // ### TODO: replace image tracker by load tracker?
    this.create_image_tracker = function(callback) {

        return image_tracker = new ImageTracker()

        function ImageTracker() {

            var images = []      // tracked images

            this.add_image = function(image) {
                if (!is_tracked(image)) {
                    images.push(image)
                }
            }

            // Checks if the tracked images are loaded completely.
            // If so, the callback is triggered and this tracker is removed.
            this.check = function() {
                if (is_all_complete()) {
                    callback()
                    image_tracker = null
                }
            }

            function is_all_complete() {
                return images.every(function(img) {
                    return img.complete
                })
            }

            function is_tracked(image) {
                return js.includes(images, function(img) {
                    return img.src == image.src
                })
            }
        }
    }



    // === Load Tracker ===

    function LoadTracker(number_of_loads, callback) {
        var loads_tracked = 0
        //
        this.track = function() {
            loads_tracked++
            if (loads_tracked == number_of_loads) {
                callback()
            }
        }
    }



    // === History ===

    /**
     * Is trueish if the browser supports the HTML5 History API.
     */
    var history_api_supported = window.history.pushState;

    if (history_api_supported) {
        window.addEventListener("popstate", function(e) {
            // Note: state is null if a) this is the initial popstate event or
            // b) if back is pressed while the begin of history is reached.
            if (e.state) {
                pop_history(e.state)
            }
        })
    }

    function pop_history(state) {
        var result = dm4c.fire_event("pre_pop_history", state)
        // plugins can suppress the generic popping behavoir
        if (!js.contains(result, false)) {
            var topic_id = state.topic_id
            dm4c.do_select_topic(topic_id, true)    // no_history_update=true
        }
    }

    function push_history(topic) {
        if (!history_api_supported) {
            return
        }
        // build history entry
        if (topic) {
            var state = {
                topic_id: topic.id
            }
            var url = "/topic/" + state.topic_id
        } else {
            var state = {}
            var url = ""
        }
        var history_entry = {state: state, url: url}
        // fire event
        dm4c.fire_event("pre_push_history", history_entry)
        //
        // push history entry
        history.pushState(history_entry.state, null, history_entry.url)
    }



    // ----------------------------------------------------------------------------------------------- Private Functions

    /**
     * @param   include_childs  Optional (boolean): if true the fetched topic will include its child topics.
     *                          Default is false.
     */
    this.fetch_topic = function(topic_id, include_childs, include_assoc_childs) {
        return build_topic(dm4c.restc.get_topic_by_id(topic_id, include_childs, include_assoc_childs))
    }

    /**
     * @param   include_childs  Optional (boolean): if true the fetched association will include its child topics.
     *                          Default is false.
     */
    this.fetch_association = function(assoc_id, include_childs) {
        return build_association(dm4c.restc.get_association_by_id(assoc_id, include_childs))
    }

    // ---

    function build_topic(topic) {
        return new Topic(topic)
    }

    this.build_topics = function(topics) {
        var topics_array = []
        for (var i = 0, topic; topic = topics[i]; i++) {
            topics_array.push(build_topic(topic))
        }
        return topics_array
    }

    function build_association(assoc) {
        return new Association(assoc)
    }

    function build_topic_type(topic_type) {
        var tt = new TopicType(topic_type)
        // Note: every time a topic type is created its load_icon() method must be called.
        // This can't be done in the TopicType constructor (see load_types() below).
        tt.load_icon()
        return tt
    }

    function build_association_type(assoc_type) {
        return new AssociationType(assoc_type)
    }



    // ------------------------------------------------------------------------------------------------ Constructor Code

    $(function() {
        // 1) Build GUI
        dm4c.toolbar = new ToolbarPanel()
        $("body").append(dm4c.toolbar.dom)
        //
        dm4c.split_panel = new SplitPanel()
        $("body").append(dm4c.split_panel.dom)
        //
        dm4c.page_panel = new PagePanel()
        dm4c.split_panel.set_page_panel(dm4c.page_panel)
        //
        dm4c.topicmap_renderer = new CanvasRenderer()
        dm4c.split_panel.set_topicmap_renderer(dm4c.topicmap_renderer)
        //
        // 2) Setup Load Tracker
        var items_to_load = pm.retrieve_plugin_list()
        var tracker = new LoadTracker(items_to_load + 2, setup_gui)     // +2 loads: topic types and association types
        //
        // 3) Load Plugins
        pm.load_plugins(tracker)
        //
        // 4) Load Types
        type_cache.load_types(tracker)
    })
}
