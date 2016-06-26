dm4c.add_plugin("de.deepamehta.workspaces", function() {

    // Model
    var workspaces              // All workspaces readable by the current user (array of topic-like objects)
    var selected_workspace_id   // ID of the selected workspace

    // View
    var workspace_menu          // A GUIToolkit Menu object

    var WORKSPACE_INFO_BUTTON_HELP = "Reveal the selected workspace on the topicmap.\n\n" +
        "Use this to rename/delete the workspace or to inspect its settings."

    var SHARING_MODE_HELP = {
        "dm4.workspaces.private":
            "Only you get access to the workspace content.\n\n" +
            "Use this for your privacy.",
        "dm4.workspaces.confidential":
            "Workspace members get READ access to the workspace content.\n" +
            "Only you get WRITE access to the workspace content.\n" +
            "Only you can create memberships.\n\n" +
            "Use this if you want make content available to a closed user group you control (like a mailing).",
        "dm4.workspaces.collaborative":
            "Workspace members get READ/WRITE access to the workspace content.\n" +
            "Each workspace member can create further memberships.\n\n" +
            "Use this if you want work together with a user group (like a groupware). Each user get equal rights.",
        "dm4.workspaces.public":
            "Every user of this DeepaMehta installation (logged in or not) get READ access to the workspace " +
                "content.\n" +
            "Workspace members get READ/WRITE access to the workspace content.\n" +
            "Each workspace member can create further memberships.\n\n" +
            "Use this if you want make content available to the public (like a blog).",
        "dm4.workspaces.common":
            "Every user of this DeepaMehta installation (logged in or not) get READ/WRITE access to the workspace " +
                "content.\n\n" +
            "Use this for content belonging to the commons (like a wiki)."
    }



    // === REST Client Extension ===

    dm4c.restc.create_workspace = function(name, uri, sharing_mode_uri) {
        return this.request("POST", "/workspace/" + encodeURIComponent(name) + "/" + (uri || "") + "/" +
            sharing_mode_uri)
    }
    dm4c.restc.get_workspace = function(uri, include_childs) {
        var params = this.queryParams({include_childs: include_childs})
        return this.request("GET", "/workspace/" + uri + params)
    }
    dm4c.restc.get_assigned_topics = function(workspace_id, topic_type_uri, include_childs) {
        var params = this.queryParams({include_childs: include_childs})
        return this.request("GET", "/workspace/" + workspace_id + "/topics/" + topic_type_uri + params)
    }
    dm4c.restc.get_assigned_workspace = function(object_id, include_childs) {
        var params = this.queryParams({include_childs: include_childs})
        return this.request("GET", "/workspace/object/" + object_id + params)
    }
    dm4c.restc.assign_to_workspace = function(object_id, workspace_id) {
        this.request("PUT", "/workspace/" + workspace_id + "/object/" + object_id)
    }



    // === Webclient Listeners ===

    dm4c.add_listener("init", function() {

        init_model()

        // init view
        create_workspace_widget()
        refresh_workspace_menu()
    })

    dm4c.add_listener("topic_commands", function(topic) {
        if (dm4c.has_write_permission_for_topic(topic.id) && topic.type_uri != "dm4.workspaces.workspace") {
            return [
                {context: "context-menu", is_separator: true},
                {context: "context-menu", label: "Assign to Workspace", handler: function() {
                    open_assign_workspace_dialog(topic.id, "Topic")
                }}
            ]
        }
    })

    dm4c.add_listener("association_commands", function(assoc) {
        if (dm4c.has_write_permission_for_association(assoc.id)) {
            return [
                {context: "context-menu", is_separator: true},
                {context: "context-menu", label: "Assign to Workspace", handler: function() {
                    open_assign_workspace_dialog(assoc.id, "Association")
                }}
            ]
        }
    })

    /**
     * @param   topic   a Topic object
     */
    dm4c.add_listener("post_update_topic", function(topic) {
        if (topic.type_uri == "dm4.workspaces.workspace") {
            fetch_workspaces_and_refresh_menu()
        }
    })

    /**
     * @param   topic   a Topic object
     */
    dm4c.add_listener("post_delete_topic", function(topic) {
        if (topic.type_uri == "dm4.workspaces.workspace") {
            // 1) update model
            fetch_workspaces()
            // if the deleted workspace was the selected one select another workspace
            if (topic.id == get_selected_workspace_id()) {
                select_workspace(get_first_workspace_id())
            }
            // 2) update view
            refresh_workspace_menu()
        }
    })



    // === Access Control Listeners ===

    dm4c.add_listener("logged_in", function(username) {
        fetch_workspaces_and_refresh_menu()
    })

    // Note: the Topicmaps plugin clears its topicmap cache at authority_decreased(1). Switching the workspace at
    // authority_decreased_2 ensures the Topicmaps plugin loads an up-to-date topicmap (in its "post_select_workspace"
    // listener).
    dm4c.add_listener("authority_decreased_2", function() {
        // 1) update model
        fetch_workspaces()
        //
        var selected_workspace_id = get_selected_workspace_id()
        if (is_workspace_readable(selected_workspace_id)) {
            // stay in selected workspace
            var workspace_id = selected_workspace_id
        } else {
            // switch to another workspace
            var workspace_id = get_first_workspace_id()
        }
        // Note: we must select a workspace in any case in order to fire the "post_select_workspace" event.
        // Even when we stay in the selected workspace the Topicmaps plugin must adjust the current topicmap
        // according to decreased authority.
        select_workspace(workspace_id)
        //
        // 2) update view
        refresh_workspace_menu()
    })



    // ------------------------------------------------------------------------------------------------------ Public API

    /**
     * @return  The ID of the selected workspace
     */
    this.get_selected_workspace_id = function() {
        return get_selected_workspace_id()
    }

    /**
     * Selects a workspace programmatically.
     * The respective item from the workspace menu is selected and the workspace is displayed.
     */
    this.select_workspace = function(workspace_id) {
        // update model
        select_workspace(workspace_id)
        // update view
        select_menu_item()
    }

    this.get_workspace = function(uri, include_childs) {
        return dm4c.restc.get_workspace(uri, include_childs)
    }



    // ----------------------------------------------------------------------------------------------- Private Functions



    function is_logged_in() {
        return dm4c.get_plugin("de.deepamehta.accesscontrol").get_username()
    }



    // *************************
    // *** Controller Helper ***
    // *************************



    /**
     * Creates a workspace with the given name and sharing mode, puts it in the workspace menu, and selects it.
     *
     * @param   sharing_mode_uri    The URI of the sharing mode ("dm4.workspaces.private",
     *                              "dm4.workspaces.confidential", ...)
     */
    function create_workspace(name, sharing_mode_uri) {
        // update DB
        var workspace = create_workspace_topic(name, sharing_mode_uri)
        // update model + view
        add_workspace(workspace.id)
    }

    /**
     * Creates a new workspace (a topic of type "Workspace") in the DB.
     *
     * @return  The created Workspace topic.
     */
    function create_workspace_topic(name, sharing_mode_uri) {
        return dm4c.restc.create_workspace(name, undefined, sharing_mode_uri)   // uri=undefined
    }

    /**
     * Puts a new workspace in the workspace menu, and selects it.
     * This is called when a new workspace is created at server-side and now should be displayed.
     */
    function add_workspace(workspace_id) {
        // update model
        fetch_workspaces()
        select_workspace(workspace_id)
        // update view
        refresh_workspace_menu()
    }

    function fetch_workspaces_and_refresh_menu() {
        // update model
        fetch_workspaces()
        // update view
        refresh_workspace_menu()
    }



    // *************
    // *** Model ***
    // *************



    function init_model() {
        fetch_workspaces()
        //
        var groups = location.pathname.match(/\/topicmap\/(\d+)/)
        if (groups) {
            var topicmap_id = groups[1]
            var workspace = dm4c.restc.get_assigned_workspace(topicmap_id)
            if (!workspace) {
                throw "WorkspacesError: topicmap " + topicmap_id + " is not assigned to any workspace"
            }
            var workspace_id = workspace.id
        } else {
            var workspace_id = get_first_workspace_id()
        }
        set_selected_workspace(workspace_id)
    }

    // ---

    /**
     * Updates the model to reflect the given workspace is now selected, and fires the "post_select_workspace" event.
     */
    function select_workspace(workspace_id) {
        set_selected_workspace(workspace_id)
        dm4c.fire_event("post_select_workspace", workspace_id)
    }

    /**
     * Updates the model to reflect the given workspace is now selected.
     * That includes setting a cookie and updating 1 model object ("selected_workspace_id").
     */
    function set_selected_workspace(workspace_id) {
        js.set_cookie("dm4_workspace_id", workspace_id)
        selected_workspace_id = workspace_id
    }

    function get_selected_workspace_id() {
        if (!selected_workspace_id) {
            throw "WorkspacesError: no workspace is selected yet"
        }
        return selected_workspace_id
    }

    // ---

    function fetch_workspaces() {
        workspaces = dm4c.restc.get_topics("dm4.workspaces.workspace", false, true)   // include_childs=false, sort=true
        // suppress System workspace from appearing in menu
        js.delete(workspaces, function(workspace) {
            return workspace.uri == "dm4.workspaces.system"
        })
    }

    function is_workspace_readable(workspace_id) {
        return js.includes(workspaces, function(workspace) {
            return workspace.id == workspace_id
        })
    }

    function get_first_workspace_id() {
        var workspace = workspaces[0]
        if (!workspace) {
            throw "WorkspacesError: no workspace available"
        }
        return workspace.id
    }



    // ************
    // *** View ***
    // ************



    // === Workspace Widget ===

    function create_workspace_widget() {
        var workspace_label = $("<span>").attr("id", "workspace-label").text("Workspace")
        workspace_menu = dm4c.ui.menu(do_select_workspace)
        var workspace_info_button = dm4c.ui.button({on_click: do_reveal_workspace, icon: "info"})
            .attr({title: WORKSPACE_INFO_BUTTON_HELP})
        var workspace_widget = $("<div>").attr("id", "workspace-widget")
            .append(workspace_label)
            .append(workspace_menu.dom)
            .append(workspace_info_button)
        // put in toolbar
        dm4c.toolbar.dom.prepend(workspace_widget)

        function do_select_workspace(menu_item) {
            var workspace_id = menu_item.value
            if (workspace_id == "_new") {
                open_new_workspace_dialog()
            } else {
                select_workspace(workspace_id)
            }
        }

        function do_reveal_workspace() {
            dm4c.do_reveal_topic(get_selected_workspace_id(), "show")
        }
    }

    /**
     * Refreshes the workspace menu based on the model ("workspaces", "selected_workspace_id").
     */
    function refresh_workspace_menu() {
        workspace_menu.empty()
        add_workspaces_to_menu(workspace_menu)
        //
        if (is_logged_in()) {
            workspace_menu.add_separator()
            workspace_menu.add_item({label: "New Workspace...", value: "_new", is_trigger: true})
        }
        //
        select_menu_item()
    }

    /**
     * Selects an item from the workspace menu based on the model ("selected_workspace_id").
     */
    function select_menu_item() {
        workspace_menu.select(get_selected_workspace_id())
    }



    // === Workspace Dialogs ===

    function open_new_workspace_dialog() {
        var name_input = dm4c.render.input(undefined, 30)
        var sharing_mode_selector = sharing_mode_selector()
        dm4c.ui.dialog({
            title: "New Workspace",
            content: dm4c.render.label("Name").add(name_input)
                .add(dm4c.render.label("Sharing Mode")).add(sharing_mode_selector),
            button_label: "Create",
            button_handler: do_create_workspace
        })

        function sharing_mode_selector() {
            var enabled_sharing_modes = get_enabled_sharing_modes()
            var _checked
            var selector = $()
            add_sharing_mode("Private",       "dm4.workspaces.private")
            add_sharing_mode("Confidential",  "dm4.workspaces.confidential")
            add_sharing_mode("Collaborative", "dm4.workspaces.collaborative")
            add_sharing_mode("Public",        "dm4.workspaces.public")
            add_sharing_mode("Common",        "dm4.workspaces.common")
            return selector

            function get_enabled_sharing_modes() {
                var username_topic        = dm4c.get_plugin("de.deepamehta.accesscontrol").get_username_topic()
                var enabled_sharing_modes = dm4c.get_plugin("de.deepamehta.config").get_config_topic(username_topic.id,
                    "dm4.workspaces.enabled_sharing_modes")
                return enabled_sharing_modes
            }

            function add_sharing_mode(name, sharing_mode_uri) {
                var enabled = is_sharing_mode_enabled(sharing_mode_uri)
                var checked = get_checked(enabled)
                selector = selector
                    .add($("<label>").attr("title", SHARING_MODE_HELP[sharing_mode_uri])
                        .append($("<input>").attr({
                            type: "radio", name: "sharing-mode", value: sharing_mode_uri, disabled: !enabled,
                            checked: checked
                        }))
                        .append($("<span>").toggleClass("ui-state-disabled", !enabled).text(name))
                    )
                    .add($("<br>"))
            }

            function is_sharing_mode_enabled(sharing_mode_uri) {
                return enabled_sharing_modes.childs[sharing_mode_uri + ".enabled"].value
            }

            function get_checked(enabled) {
                if (enabled && !_checked) {
                    _checked = true
                    return true
                }
            }
        }

        function do_create_workspace() {
            var name = name_input.val()
            var sharing_mode_uri = sharing_mode_selector.find(":checked").val()
            create_workspace(name, sharing_mode_uri)
        }
    }

    function open_assign_workspace_dialog(object_id, object_info) {
        var workspace_menu = workspace_menu()
        dm4c.ui.dialog({
            title: "Assign " + object_info + " to Workspace",
            content: workspace_menu.dom,
            width: "300px",
            button_label: "Assign",
            button_handler: do_assign_to_workspace
        })

        function workspace_menu() {
            var workspace_menu = dm4c.ui.menu()
            var workspace = dm4c.restc.get_assigned_workspace(object_id)
            var workspace_id = workspace && workspace.id
            add_workspaces_to_menu(workspace_menu, function(workspace) {
                return dm4c.has_write_permission_for_topic(workspace.id)
            })
            workspace_menu.select(workspace_id)
            return workspace_menu
        }

        function do_assign_to_workspace() {
            var workspace_id = workspace_menu.get_selection().value
            dm4c.restc.assign_to_workspace(object_id, workspace_id)
            dm4c.page_panel.refresh()
        }
    }



    // === Utilities ===

    function add_workspaces_to_menu(menu, filter_func) {
        var icon_src = dm4c.get_type_icon_src("dm4.workspaces.workspace")
        for (var i = 0, workspace; workspace = workspaces[i]; i++) {
            if (!filter_func || filter_func(workspace)) {
                menu.add_item({label: workspace.value, value: workspace.id, icon: icon_src})
            }
        }
    }
})
