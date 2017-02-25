/**
 * Provides the standard commands ("Create", "Edit", "Delete", "Hide", "Associate").
 */
dm4c.add_plugin("de.deepamehta.webclient.default", function() {

    // === Webclient Listeners ===

    dm4c.add_listener("topic_commands", function(topic) {
        var commands = []
        //
        commands.push({
            label: "Hide",
            handler: do_hide,
            context: "context-menu"
        })
        //
        if (is_topic_changable(topic)) {
            commands.push({is_separator: true, context: "context-menu"})
            commands.push({
                label: "Edit",
                handler: do_edit,
                context: ["context-menu", "detail-panel-show"],
                ui_icon: "pencil"
            })
        }
        //
        if (dm4c.has_create_permission_for_association_type("dm4.core.association")) {
            commands.push({is_separator: true, context: "context-menu"})
            commands.push({
                label: "Associate",
                handler: do_associate,
                context: "context-menu"
            })
        }
        //
        if (dm4c.has_write_permission_for_topic(topic.id)) {
            commands.push({is_separator: true, context: "context-menu"})
            commands.push({
                label: "Delete",
                handler: topic.type_uri == "dm4.core.topic_type"      ? open_delete_topic_type_dialog :
                         topic.type_uri == "dm4.core.assoc_type"      ? open_delete_association_type_dialog :
                         topic.type_uri == "dm4.topicmaps.topicmap"   ? open_delete_topicmap_dialog :
                         topic.type_uri == "dm4.workspaces.workspace" ? open_delete_workspace_dialog :
                                                                        open_delete_topic_dialog,
                context: "context-menu",
                ui_icon: "trash"
            })
        }
        //
        commands.push({
            label: "OK",
            handler: dm4c.page_panel.save,
            context: "detail-panel-edit",
            is_submit: true
        })
        //
        return commands

        // Note: all command handlers receive the selected item and the coordinates of the selecting mouse click.
        // However, most of the handlers don't care. See BaseMenu's create_selection_handler() in gui_toolkit.js

        function do_hide() {
            dm4c.do_hide_topic(topic)
        }

        function do_edit() {
            dm4c.enter_edit_mode(topic)
        }

        function do_associate(item, x, y) {
            dm4c.topicmap_renderer.begin_association(topic.id, x, y)
        }

        // ---

        function open_delete_topic_dialog() {
            dm4c.ui.dialog({
                title: "Delete Topic?",
                width: "300px",
                button_label: "Delete",
                button_handler: function() {
                    dm4c.do_delete_topic(topic.id)
                }
            })
        }

        function open_delete_topic_type_dialog() {
            dm4c.ui.dialog({
                title: "Delete Topic Type?",
                width: "300px",
                button_label: "Delete",
                button_handler: function() {
                    dm4c.do_delete_topic_type(topic.uri)
                }
            })
        }

        function open_delete_association_type_dialog() {
            dm4c.ui.dialog({
                title: "Delete Association Type?",
                width: "300px",
                button_label: "Delete",
                button_handler: function() {
                    dm4c.do_delete_association_type(topic.uri)
                }
            })
        }

        function open_delete_topicmap_dialog() {
            dm4c.ui.dialog({
                title: "Delete Topicmap?",
                width: "300px",
                button_label: "Delete",
                button_handler: function() {
                    dm4c.get_plugin("de.deepamehta.topicmaps").delete_topicmap(topic.id)
                }
            })
        }

        function open_delete_workspace_dialog() {
            dm4c.ui.dialog({
                title: "Delete Workspace \"" + topic.value + "\"?",
                content: $("<p>").text("CAUTION: all the workspace content will be deleted").add($("<ul>")
                    .append($("<li>").text("all assigned topics/associations"))
                    .append($("<li>").text("all assigned types"))
                    .append($("<li>").text("all topics/associations of these types"))),
                width: "500px",
                button_label: "Delete all",
                button_handler: function() {
                    dm4c.do_delete_topic(topic.id)
                }
            })
        }
    })

    dm4c.add_listener("association_commands", function(assoc) {
        var commands = []
        //
        commands.push({
            label: "Hide",
            handler: do_hide,
            context: "context-menu"
        })
        //
        if (is_association_changable(assoc)) {
            commands.push({is_separator: true, context: "context-menu"})
            commands.push({
                label: "Edit",
                handler: do_edit,
                context: ["context-menu", "detail-panel-show"],
                ui_icon: "pencil"
            })
        }
        //
        if (dm4c.has_write_permission_for_association(assoc.id)) {
            commands.push({is_separator: true, context: "context-menu"})
            commands.push({
                label: "Delete",
                handler: open_delete_association_dialog,
                context: "context-menu",
                ui_icon: "trash"
            })
        }
        //
        commands.push({
            label: "OK",
            handler: dm4c.page_panel.save,
            context: "detail-panel-edit",
            is_submit: true
        })
        //
        return commands

        function do_hide() {
            dm4c.do_hide_association(assoc)
        }

        function do_edit() {
            dm4c.enter_edit_mode(assoc)
        }

        function open_delete_association_dialog() {
            dm4c.ui.dialog({
                title: "Delete Association?",
                width: "300px",
                button_label: "Delete",
                button_handler: function() {
                    dm4c.do_delete_association(assoc.id)
                }
            })
        }
    })

    dm4c.add_listener("canvas_commands", function(cx, cy) {
        var commands = []
        if (dm4c.is_workspace_writable()) {
            var topic_types = dm4c.topic_type_list()
            if (topic_types.length) {
                commands.push({
                    label: "Create",
                    disabled: true,
                    context: "context-menu"
                })
                for (var i = 0, topic_type; topic_type = topic_types[i]; i++) {
                    commands.push({
                        label: topic_type.value,
                        icon:  topic_type.get_icon_src(),
                        handler: create_handler(topic_type.uri),
                        context: "context-menu"
                    })
                }
            }
        }
        return commands

        function create_handler(type_uri) {
            return function() {
                dm4c.do_create_topic(type_uri, cx, cy)
            }
        }
    })

    dm4c.add_listener("topic_doubleclicked", function(topic) {
        if (is_topic_changable(topic)) {
            dm4c.enter_edit_mode(topic)
        }
    })

    dm4c.add_listener("association_doubleclicked", function(assoc) {
        if (is_association_changable(assoc)) {
            dm4c.enter_edit_mode(assoc)
        }
    })

    dm4c.add_listener("post_select_workspace", function(workspace_id) {
        dm4c.adjust_create_menu_visibility()
    })

    // ----------------------------------------------------------------------------------------------- Private Functions

    function is_topic_changable(topic) {
        return dm4c.has_write_permission_for_topic(topic.id) && !topic.get_type().is_locked()
    }

    function is_association_changable(assoc) {
        return dm4c.has_write_permission_for_association(assoc.id) && !assoc.get_type().is_locked()
    }
})
// Enable debugging for dynamically loaded scripts:
//# sourceURL=default_plugin.js
