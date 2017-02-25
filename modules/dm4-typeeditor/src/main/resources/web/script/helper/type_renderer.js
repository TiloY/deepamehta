function TypeRenderer() {

    this.render_type_page = function(type) {
        dm4c.render.field_label("Name")
        dm4c.render.page(type.value)
        //
        dm4c.render.field_label("URI")
        dm4c.render.page(type.uri)
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
                editors_list.append(new AssociationDefEditor(assoc_def).dom)
            }
            editors_list.sortable()
        }

        function AssociationDefEditor(assoc_def) {
            var parent_type_label = $("<span>").addClass("label").text(type.value)
            var child_type_label = $("<span>").addClass("label").addClass("child-type-label")
                .text(dm4c.topic_type_name(assoc_def.child_type_uri))
            var parent_card_menu = dm4c.render.topic_menu("dm4.core.cardinality", assoc_def.parent_cardinality_uri)
            var child_card_menu = dm4c.render.topic_menu("dm4.core.cardinality", assoc_def.child_cardinality_uri)
            var assoc_type_menu = create_assoc_type_menu()
            var custom_assoc_type_menu = create_custom_assoc_type_menu()
            var include_in_label_checkbox = dm4c.render.checkbox(assoc_def.include_in_label)
            //
            var optional_card_div = $("<div>").append(parent_type_label).append(parent_card_menu.dom)
            optional_card_div.toggle(is_aggregation_selected())
            //
            this.dom = $("<li>").addClass("assoc-def-editor").addClass("ui-state-default")
                .append($("<div>").append(child_type_label).append(child_card_menu.dom)
                                  .append(include_in_label_checkbox).append(small_label("Include in Label")))
                .append(optional_card_div)
                .append($("<div>").append(small_label("Association Type")).append(assoc_type_menu.dom))
                .append($("<div>").append(small_label("Custom Association Type")).append(custom_assoc_type_menu.dom))
                .data("model_func", build_assoc_def_model)

            function create_assoc_type_menu() {
                var menu = dm4c.ui.menu(do_refresh_opional_card_div)
                menu.add_item({label: "Composition Definition", value: "dm4.core.composition_def"})
                menu.add_item({label: "Aggregation Definition", value: "dm4.core.aggregation_def"})
                menu.select(assoc_def.type_uri)
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

            function create_custom_assoc_type_menu() {
                return dm4c.render.topic_combobox("dm4.core.assoc_type", "uri", assoc_def.custom_assoc_type_uri)
            }

            function small_label(text) {
                return $("<span>").addClass("label").addClass("field-label").text(text)
            }

            function build_assoc_def_model() {
                return {
                    id:                     assoc_def.id,
                    child_type_uri:         assoc_def.child_type_uri,
                    child_cardinality_uri:  child_card_menu.get_selection().value,
                    parent_cardinality_uri: parent_card_menu.get_selection().value,
                    assoc_type_uri:         assoc_type_menu.get_selection().value,
                    custom_assoc_type_uri:  custom_assoc_type_uri(),
                    include_in_label:       include_in_label_checkbox.get(0).checked
                }

                // compare to form_element_function() in webclient's render_helper.js
                function custom_assoc_type_uri() {
                    var val = custom_assoc_type_menu.get_selection()
                    if (typeof(val) == "object") {
                        // user selected assoc type from menu
                        return val.value
                    } else {
                        if (val) {
                            // user entered non-empty value
                            dm4c.get_plugin("de.deepamehta.typeeditor").show_type_warning(val)
                            return null         // no update
                        } else {
                            // user entered empty value
                            if (assoc_def.custom_assoc_type_uri) {
                                // an assoc type was selected before -- delete the assignment
                                return dm4c.DEL_URI_PREFIX + assoc_def.custom_assoc_type_uri
                            } else {
                                // no assoc type was selected before
                                return null     // no update
                            }
                        }
                    }
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
                type_model.assoc_defs = assoc_defs()
            }
            //
            return type_model

            function assoc_defs() {
                var assoc_defs = []
                editors_list.children().each(function() {
                    var assoc_def = $(this).data("model_func")()
                    assoc_defs.push(assoc_def)
                })
                return assoc_defs
            }
        }
    }
}
