function TypeRenderer() {

    this.render_type_page = function(type) {
        dm4c.render.field_label("Name")
        dm4c.render.page(type.value)
        //
        dm4c.render.field_label("URI")
        dm4c.render.page(type.uri)
        //
        var data_type = dm4c.restc.get_topic_by_value("uri", type.data_type_uri)
        dm4c.render.field_label("Data Type")
        dm4c.render.page(data_type.value)
        //
        dm4c.render.topic_associations(type.id)
    }

    this.render_type_form = function(type) {
        var editors_list                                    // a jQuery <ul> element
        //
        var name_input = dm4c.render.input(type.value)      // a jQuery <input> element
        dm4c.render.field_label("Name")
        dm4c.render.page(name_input)
        //
        var uri_input = dm4c.render.input(type.uri)         // a jQuery <input> element
        dm4c.render.field_label("URI")
        dm4c.render.page(uri_input)
        //
        var data_type_menu = dm4c.render.topic_menu("dm4.core.data_type", type.data_type_uri)   // a GUIToolkit
        dm4c.render.field_label("Data Type")                                                    // Menu object
        dm4c.render.page(data_type_menu.dom)
        //
        if (type.data_type_uri == "dm4.core.composite") {
            dm4c.render.field_label("Child Topic Types (" + type.assoc_defs.length + ")")
            render_assoc_def_editors()
        }

        function render_assoc_def_editors() {
            editors_list = $("<ul>").attr("id", "assoc-def-editors")
            dm4c.render.page(editors_list)
            for (var i = 0, assoc_def; assoc_def = type.assoc_defs[i]; i++) {
                var label_state = type.get_label_config(assoc_def.child_type_uri)
                editors_list.append(new AssociationDefEditor(assoc_def, label_state).dom)
            }
            editors_list.sortable()
        }

        /**
         * @param   label_state     a boolean
         */
        function AssociationDefEditor(assoc_def, label_state) {
            var parent_type_label = $("<span>").addClass("label").text(type.value)
            var child_type_label = $("<span>").addClass("label").addClass("child-type-label")
                .text(dm4c.topic_type_name(assoc_def.child_type_uri))
            var parent_card_menu = dm4c.render.topic_menu("dm4.core.cardinality", assoc_def.parent_cardinality_uri)
            var child_card_menu = dm4c.render.topic_menu("dm4.core.cardinality", assoc_def.child_cardinality_uri)
            var assoc_type_label = $("<span>").addClass("label").addClass("field-label").text("Association Type")
            var assoc_type_menu = create_assoc_type_menu(assoc_def.type_uri)
            var label_config_checkbox = dm4c.render.checkbox(label_state)
            var label_config_label = $("<span>").addClass("label").addClass("field-label").text("Include in Label")
            //
            var optional_card_div = $("<div>").append(parent_type_label).append(parent_card_menu.dom)
            optional_card_div.toggle(is_aggregation_selected())
            //
            this.dom = $("<li>").addClass("assoc-def-editor").addClass("ui-state-default")
                .append($("<div>").append(child_type_label).append(child_card_menu.dom)
                                  .append(label_config_checkbox).append(label_config_label))
                .append(optional_card_div)
                .append($("<div>").append(assoc_type_label).append(assoc_type_menu.dom))
                .data("model_func", get_model)

            function create_assoc_type_menu(selected_uri) {
                var menu = dm4c.ui.menu(do_refresh_opional_card_div)
                menu.add_item({label: "Composition Definition", value: "dm4.core.composition_def"})
                menu.add_item({label: "Aggregation Definition", value: "dm4.core.aggregation_def"})
                menu.select(selected_uri)
                return menu

                function do_refresh_opional_card_div() {
                    if (is_aggregation_selected()) {
                        optional_card_div.show(500)
                    } else {
                        optional_card_div.hide(500)
                    }
                }
            }

            function is_aggregation_selected() {
                return assoc_type_menu.get_selection().value == "dm4.core.aggregation_def"
            }

            function get_model() {
                return {
                    assoc_def: {
                        id:                     assoc_def.id,
                        child_type_uri:         assoc_def.child_type_uri,
                        child_cardinality_uri:  child_card_menu.get_selection().value,
                        parent_cardinality_uri: parent_card_menu.get_selection().value,
                        assoc_type_uri:         assoc_type_menu.get_selection().value
                    },
                    label_state: label_config_checkbox.get(0).checked
                }
            }
        }

        /**
         * Returns a function that reads out values from GUI elements and builds a type model from it.
         *
         * @return  a function that returns a type model (object).
         */
        return function() {
            var type_model = {
                id: type.id,
                uri: $.trim(uri_input.val()),
                value: $.trim(name_input.val()),
                data_type_uri: data_type_menu.get_selection().value
            }
            //
            if (type.data_type_uri == "dm4.core.composite") {
                var model = composite_model()
                type_model.assoc_defs   = model.assoc_defs
                type_model.label_config = model.label_config
            }
            //
            return type_model

            function composite_model() {
                var assoc_defs = []
                var label_config = []
                editors_list.children().each(function() {
                    var editor_model = $(this).data("model_func")()
                    var assoc_def = editor_model.assoc_def
                    assoc_defs.push(assoc_def)
                    if (editor_model.label_state) {
                        label_config.push(assoc_def.child_type_uri)
                    }
                })
                return {
                    assoc_defs: assoc_defs,
                    label_config: label_config
                }
            }
        }
    }
}
