dm4c.add_plugin("de.deepamehta.files", function() {

    var self = this



    // === REST Client Extension ===

    dm4c.restc.create_file_topic = function(path) {
        return this.request("GET", "/files/file/" + encodeURIComponent(path))
    }
    dm4c.restc.create_folder_topic = function(path) {
        return this.request("GET", "/files/folder/" + encodeURIComponent(path))
    }
    //
    dm4c.restc.create_child_file_topic = function(folder_topic_id, path) {
        return this.request("GET", "/files/parent/" + folder_topic_id + "/file/" + encodeURIComponent(path))
    }
    dm4c.restc.create_child_folder_topic = function(folder_topic_id, path) {
        return this.request("GET", "/files/parent/" + folder_topic_id + "/folder/" + encodeURIComponent(path))
    }
    //
    dm4c.restc.get_file = function(path) {
        // ### FIXME: principle copy in File Content Renderers's filerepo_URI()
        return this.request("GET", "/filerepo/" + encodeURIComponent(path), undefined, undefined, undefined, "text")
        // Note: response_data_type="text" causes the response data to be returned as is
        // (instead of trying to JSON-parse it). It works for non-text files as well.
    }
    dm4c.restc.create_folder = function(folder_name, path) {
        this.request("POST", "/files/" + encodeURIComponent(path) + "/folder/" + encodeURIComponent(folder_name))
    }
    //
    dm4c.restc.get_resource_info = function(path) {
        return this.request("GET", "/files/" + encodeURIComponent(path) + "/info")
    }
    dm4c.restc.get_directory_listing = function(path) {
        return this.request("GET", "/files/" + encodeURIComponent(path))
    }
    //
    dm4c.restc.open_file = function(file_topic_id) {
        this.request("GET", "/files/open/" + file_topic_id)
    }



    // === Webclient Listeners ===

    dm4c.add_listener("process_drop", function(data_transfer) {
        if (js.contains(data_transfer.types, "Files")) {
            if (typeof netscape != "undefined") {
                var files = process_file_drop_firefox(data_transfer)
            } else if (js.contains(data_transfer.types, "text/uri-list")) {
                var files = process_file_drop_safari(data_transfer)
            } else {
                alert("WARNING: drag'n'drop operation is ignored.\n\nDropping files/folders is not yet supported by " +
                    "DeepaMehta for this browser/operating system.\n" + js.inspect(navigator) + js.inspect($.browser))
            }
            // Note: if an error occurred "files" is not initialized
            if (files) {
                dm4c.fire_event("process_files_drop", files)
            }
        } else if (js.contains(data_transfer.types, "text/plain")) {
            alert("WARNING: drag'n'drop operation is ignored.\n\nType: text/plain " +
                "(not yet implemented by DeepaMehta)\n\nText: \"" + data_transfer.getData("text/plain") + "\"")
        } else {
            alert("WARNING: drag'n'drop operation is ignored.\n\nUnexpected type " +
                "(not yet implemented by DeepaMehta)\n" + js.inspect(data_transfer))
        }

        function process_file_drop_firefox(data_transfer) {
            try {
                var files = new FilesDataTransfer()
                for (var i = 0, file; file = data_transfer.files[i]; i++) {
                    // Firefox note: a DOM File's "mozFullPath" attribute contains the file's path.
                    // Requires the UniversalFileRead privilege to read.
                    netscape.security.PrivilegeManager.enablePrivilege("UniversalFileRead")
                    var path = file.mozFullPath
                    if (is_directory(file)) {
                        var dropped_dir = dm4c.restc.get_directory_listing(path)
                        files.add_directory(dropped_dir)
                        continue
                    }
                    files.add_file(new File(file.name, path, file.type, file.size))
                }
                return files
            } catch (e) {
                alert("Local file/folder \"" + file.name + "\" can't be accessed.\n\n" + e)
            }

            function is_directory(file) {
                // Note: Firefox does *not* end the path of a File object which represents a directory with "/".
                // So we can't detect directories safely by relying on the path.
                // Instead: if the item's MIME type is known we know it is not a directory.
                if (file.type) {
                    return false
                }
                // Otherwise we involve the server to get information about the item
                var info = dm4c.restc.get_resource_info(file.mozFullPath)
                return info.kind == "directory"
            }
        }

        function process_file_drop_safari(data_transfer) {
            var files = new FilesDataTransfer()
            // Note: Safari provides a "text/uri-list" data flavor which holds the URIs of the files dropped
            var uri_list = data_transfer.getData("text/uri-list").split("\n")
            for (var i = 0, file; file = data_transfer.files[i]; i++) {
                var path = uri_to_path(uri_list[i])
                if (is_directory(path)) {
                    var dropped_dir = dm4c.restc.get_directory_listing(path)
                    files.add_directory(dropped_dir)
                    continue
                }
                files.add_file(new File(file.name, path, file.type, file.size))
            }
            return files

            function uri_to_path(uri) {
                // Note: local file URIs provided by Safari begin with "file://localhost" which must be cut off
                if (uri.match(/^file:\/\/localhost(.*)/)) {
                    uri = RegExp.$1
                }
                // Note: local file URIs provided by Safari are encoded
                return decodeURIComponent(uri)
            }

            function is_directory(path) {
                // Note: Safari does end the URI of a File object which represents a directory with "/".
                return path.match(/.$/)[0] == "/"
            }
        }
    })

    dm4c.add_listener("topic_doubleclicked", function(topic) {
        if (js.is_local_connection()) {
            if (topic.type_uri == "dm4.files.file" || topic.type_uri == "dm4.files.folder") {
                dm4c.restc.open_file(topic.id)
            }
        }
    })

    dm4c.add_listener("topic_commands", function(topic) {
        var commands = []
        if (topic.type_uri == "dm4.files.file") {
            commands.push({
                label: "Download File",
                handler: do_download_file,
                context: "detail-panel-show",
                ui_icon: "arrowthickstop-1-s"
            })
            commands.push({
                is_separator: true,
                context: "context-menu"
            })
            commands.push({
                label: "Create Icon",
                disabled: !js.is_image_file(topic.value),
                handler: do_create_icon,
                context: "context-menu"
            })
        } else if (topic.type_uri == "dm4.files.folder") {
            if (dm4c.has_create_permission_for_topic_type("dm4.files.folder")) {
                commands.push({
                    label: "Create Folder",
                    handler: do_create_folder,
                    context: "detail-panel-show",
                    ui_icon: "folder-collapsed"
                })
            }
            if (dm4c.has_create_permission_for_topic_type("dm4.files.file")) {
                commands.push({
                    label: "Upload File",
                    handler: do_open_upload_dialog,
                    context: "detail-panel-show",
                    ui_icon: "arrowthick-1-n"
                })
            }
        }
        return commands

        function do_create_folder() {
            dm4c.ui.prompt("Create Folder", "Folder Name", "Create", function(folder_name) {
                var path = topic.get("dm4.files.path")
                dm4c.restc.create_folder(folder_name, path)
                dm4c.page_panel.refresh()
            })
        }

        function do_open_upload_dialog() {
            var path = topic.get("dm4.files.path")
            self.open_upload_dialog("/files/" + encodeURIComponent(path), dm4c.page_panel.refresh)
        }

        function do_download_file() {
            var path = topic.get("dm4.files.path")
            location.href = "/filerepo/" + encodeURIComponent(path) + "?download"
        }

        function do_create_icon() {
            // update DB
            var icon = new Topic(dm4c.restc.create_topic({
                type_uri: "dm4.webclient.icon",
                value: "/filerepo/" + encodeURIComponent(topic.get("dm4.files.path"))
            }))
            // update model and view
            dm4c.show_topic(icon, "show", undefined, true)      // do_center=true
        }
    })



    // ------------------------------------------------------------------------------------------------------ Public API

    /**
     * Creates a File topic for the given file and shows the topic in the topicmap panel.
     *
     * @param   file        A File object (with "name", "path", "type", and "size" properties).
     * @param   do_select   Optional: if trueish the File topic is selected in the topicmap panel.
     * @param   do_center   Optional: if trueish the File topic is centered in the topicmap panel.
     */
    this.create_file_topic = function(file, do_select, do_center) {
        var file_topic = dm4c.restc.create_file_topic(file.path)
        dm4c.show_topic(new Topic(file_topic), do_select ? "show" : "none", undefined, do_center)
    }

    /**
     * Creates a Folder topic for the given directory and shows the topic in the topicmap panel.
     *
     * @param   dir         A Directory object (with "name", "path", and "items" properties).
     * @param   do_select   Optional: if trueish the Folder topic is selected in the topicmap panel.
     * @param   do_center   Optional: if trueish the Folder topic is centered in the topicmap panel.
     */
    this.create_folder_topic = function(dir, do_select, do_center) {
        var folder_topic = dm4c.restc.create_folder_topic(dir.path)
        dm4c.show_topic(new Topic(folder_topic), do_select ? "show" : "none", undefined, do_center)
    }

    /**
     * Creates respective File and Folder topics for all items contained in the given directory
     * and shows the topics in the topicmap panel.
     *
     * @param   dir                 A Directory object (with "name", "path", and "items" properties).
     * @param   select_first_topic  Optional: if trueish the first created topic is selected in the topicmap panel.
     */
    this.create_file_topics = function(dir, select_first_topic) {
        for (var i = 0, item; item = dir.items[i]; i++) {
            var do_select = select_first_topic && i == 0
            if (item.kind == "file") {
                this.create_file_topic(item, do_select)
            } else if (item.kind == "directory") {
                this.create_folder_topic(item, do_select)
            } else {
                alert("WARNING (create_file_topics):\n\nItem \"" + item.name + "\" of directory \"" +
                    dir.name + "\" is of unexpected kind: \"" + item.kind + "\".")
            }
        }
    }

    // ---

    /**
     * Opens a dialog to allow the user to choose a local file and upload it to the server.
     *
     * @param   upload_url  the URL where the selected file is uploaded to (via POST).
     *                      Proper encoding of the URL and its components is up to the caller.
     * @param   callback    the function invoked once the file has been uploaded and processed at server-side.
     *                      One argument is passed: the object returned by the server-side processing method.
     */
    this.open_upload_dialog = (function() {
        // create upload target
        // Note: we bind no load handler here. It would fire prematureley.
        var upload_target = $("<iframe>", {name: "upload-target"}).hide()
        $("body").append(upload_target)

        return function(upload_url, callback) {
            var content = $("<form>", {
                action:  upload_url,
                method:  "post",
                enctype: "multipart/form-data",
                target:  "upload-target"
            })
            .append($('<input type="file">').attr({name: "file", size: 60}))    // Note: attr() must be used here.
            .append($('<input type="submit">').attr({value: "Upload"}))     // An attr object as 2nd param doesn't work!
            //
            var upload_dialog = dm4c.ui.dialog({title: "Upload File", content: content})
            // bind handler
            upload_target.unbind("load")    // Note: the previous handler must be removed
            upload_target.load(upload_complete)

            function upload_complete() {
                upload_dialog.close()
                // Note: iframes must be accessed via window.frames
                var response_text = $("pre", window.frames["upload-target"].document).text()
                try {
                    var response = JSON.parse(response_text)
                    if (response.exception) {
                        dm4c.open_error_dialog({
                            content_type: "application/json",
                            content: response_text,
                            status_code: "?",
                            status_text: ""
                        })
                        // Note: the upload request is no AJAX request.
                        // We have no knowledge about the server status code here, have we?
                    } else {
                        callback(response)
                    }
                } catch (e) {
                    alert("Upload failed: \"" + response_text + "\"\n\nException=" + JSON.stringify(e))
                }
            }
        }
    })()

    // ------------------------------------------------------------------------------------------------- Private Classes

    function FilesDataTransfer() {

        var files = []
        var directories = []

        // ---

        this.add_file = function(file) {
            files.push(file)
        }

        this.add_directory = function(directory) {
            directories.push(directory)
        }

        // ---

        this.get_file_count = function() {
            return files.length
        }

        this.get_directory_count = function() {
            return directories.length
        }

        // ---

        this.get_file = function(index) {
            return files[index]
        }

        this.get_directory = function(index) {
            return directories[index]
        }
    }

    function File(name, path, type, size) {
        this.kind = "file"
        this.name = name
        this.path = path
        this.type = type
        this.size = size
    }

    function Directory(name, path, items) {
        this.kind = "directory"
        this.name = name
        this.path = path
        this.items = items  // array of File and Directory objects
    }
})
