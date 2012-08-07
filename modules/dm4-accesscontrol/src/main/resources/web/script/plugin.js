dm4c.add_plugin("de.deepamehta.accesscontrol", function() {

    var DEFAULT_USER = "admin"
    var DEFAULT_PASSWORD = ""
    var ENCRYPTED_PASSWORD_PREFIX = "-SHA256-"  // don't change this

    dm4c.load_script("/de.deepamehta.accesscontrol/script/vendor/sha256.js")

    var login_widget
    var login_dialog

    // Note: the login dialog must be build at instantiation time. Using the "init" handler would be too late.
    // Other plugins may require the login dialog (by calling require_login()) from *their* "init" handler.
    create_login_widget()
    create_login_dialog()



    // === REST Client Extension ===

    /**
     * @return  a User Account topic, or null if there is no such user
     */
    dm4c.restc.lookup_user_account = function(username) {
        return this.request("GET", "/accesscontrol/user/" + username)
    }
    dm4c.restc.get_username = function() {
        return this.request("GET", "/accesscontrol/user")
    }
    dm4c.restc.get_topic_permissions = function(topic_id) {
        return this.request("GET", "/accesscontrol/topic/" + topic_id)
    }
    dm4c.restc.get_owned_topic = function(user_id, type_uri) {
        return this.request("GET", "/accesscontrol/owner/" + user_id + "/" + type_uri)
    }
    dm4c.restc.set_owner = function(topic_id, user_id) {
        return this.request("POST", "/accesscontrol/topic/" + topic_id + "/owner/" + user_id)
    }
    dm4c.restc.create_acl_entry = function(topic_id, role, permissions) {
        return this.request("POST", "/accesscontrol/topic/" + topic_id + "/role/" + role, permissions)
    }
    dm4c.restc.join_workspace = function(workspace_id, user_id) {
        return this.request("POST", "/accesscontrol/user/" + user_id + "/" + workspace_id)
    }



    // === Webclient Listeners ===

    dm4c.add_listener("has_write_permission", function(topic) {
        var permissions = topic.composite["dm4.accesscontrol.permissions"]
        // error check
        if (!permissions) {
            throw "AccessControlError: topic " + topic.id + " (type_uri=\"" + topic.type_uri +
                "\") has no permissions info"
        }
        //
        return permissions.composite["dm4.accesscontrol.operation_write"].value
    })

    dm4c.add_listener("has_create_permission", function(topic_type) {
        var permissions = topic_type.composite["dm4.accesscontrol.permissions"]
        // error check
        if (!permissions) {
            throw "AccessControlError: topic type \"" + topic_type.uri + "\" has no permissions info"
        }
        //
        return permissions.composite["dm4.accesscontrol.operation_create"].value
    })



    // ------------------------------------------------------------------------------------------------------ Public API

    this.require_login = function() {
        if (!get_username()) {
            login_dialog.open(true)     // no_close_button=true
        }
    }

    // ---

    this.create_user_account = function(username, password) {
        return dm4c.create_topic("dm4.accesscontrol.user_account", {
            "dm4.accesscontrol.username": username,
            "dm4.accesscontrol.password": encrypt_password(password)
        })
    }

    // ---

    this.get_username = function() {
        return dm4c.restc.get_username()
    }

    this.get_topic_permissions = function(topic_id) {
        return dm4c.restc.get_topic_permissions(topic_id)
    }

    this.get_owned_topic = function(user_id, type_uri) {
        return dm4c.restc.get_owned_topic(user_id, type_uri)
    }

    this.set_owner = function(topic_id, user_id) {
        dm4c.restc.set_owner(topic_id, user_id)
    }

    this.create_acl_entry = function(topic_id, role, permissions) {
        dm4c.restc.create_acl_entry(topic_id, role, permissions)
    }

    this.join_workspace = function(workspace_id, user_id) {
        dm4c.restc.join_workspace(workspace_id, user_id)
    }



    // ----------------------------------------------------------------------------------------------- Private Functions

    function create_login_widget() {
        login_widget = new LoginWidget()
        dm4c.toolbar.dom.append(login_widget.dom)

        function LoginWidget() {
            var dom = $("<div>").attr({id: "login-widget"})    // attr("id", ...) doesn't create the div!
            var username = get_username()
            if (username) {
                show_user(username)
            } else {
                show_login()
            }
            this.dom = dom

            this.show_user = function(username) {
                dom.empty()
                show_user(username)
            }

            this.show_login = function() {
                dom.empty()
                show_login()
            }

            function show_user(username) {
                dom.append("Logged in as \"" + username + "\"<br>")
                dom.append($("<a>").attr("href", "#").text("Logout").click(function() {
                    logout()
                    return false
                }))
            }

            function show_login() {
                dom.append($("<a>").attr("href", "#").text("Login").click(function() {
                    login_dialog.open()
                    return false
                }))
            }
        }
    }

    function create_login_dialog() {
        var username_input = $("<input>")
        var password_input = $("<input>").attr("type", "password")
        var message_div = $("<div>").attr("id", "login-message")
        var dialog_content = $("<div>").addClass("field-label").text("Username")
            .after(username_input)
            .after($("<div>").addClass("field-label").text("Password"))
            .after(password_input)
            .after(message_div)
        login_dialog = dm4c.ui.dialog({
            title: "Login",
            content: dialog_content,
            button_label: "OK",
            button_handler: do_try_login
        })
        //
        dm4c.on_return_key(username_input, function() {
            password_input.focus();
        })
        dm4c.on_return_key(password_input, function() {
            do_try_login();
        })

        function do_try_login() {
            var username = username_input.val()
            var password = password_input.val()
            var user = try_login(username, password)
            if (user) {
                show_message("Login OK", "ok", close_login_dialog)
                login(user)
            } else {
                show_message("Login failed", "failed")
            }
        }

        function show_message(message, css_class, callback) {
            message_div.fadeOut(200, function() {
                $(this).text(message).removeClass().addClass(css_class).fadeIn(1000, callback)
            })
        }

        function close_login_dialog() {
            login_dialog.close(400, function() {
                // clear fields for next re-open
                username_input.val("")
                password_input.val("")
                message_div.text("")
            })
        }

        // ---

        function try_login(username, password) {
            var user = dm4c.restc.lookup_user_account(username)
            if (user) {
                user = new Topic(user)
                if (user.get("dm4.accesscontrol.password") == encrypt_password(password)) {
                    return user
                }
            }
        }
    }

    // ---

    function login(user) {
        // update model
        var username = user.get("dm4.accesscontrol.username")
        js.set_cookie("dm4_username", username)
        //
        // Note: the types must be reloaded *before* the logged_in event is fired.
        // Consider the Workspaces plugin: refreshing the workspace menu relies on the type cache.
        dm4c.reload_types(function() {
            // update view
            login_widget.show_user(username)
            dm4c.restore_selection()
            // fire event
            dm4c.trigger_plugin_hook("logged_in", user)
        })
    }

    function logout() {
        // update model
        js.remove_cookie("dm4_username")
        //
        // Note: the types must be reloaded *before* the logged_out event is fired.
        // Consider the Workspaces plugin: refreshing the workspace menu relies on the type cache.
        dm4c.reload_types(function() {
            // update view
            login_widget.show_login()
            dm4c.restore_selection()
            // fire event
            dm4c.trigger_plugin_hook("logged_out")
        })
    }

    // ---

    /**
     * Returns the username of the logged in user, or undefined if no user is logged in.
     */
    function get_username() {
        return js.get_cookie("dm4_username")
    }

    // ---

    function encrypt_password(password) {
        return ENCRYPTED_PASSWORD_PREFIX + SHA256(password)
    }
})