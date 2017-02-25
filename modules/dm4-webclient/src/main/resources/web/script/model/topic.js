/**
 * @param   topic   a JavaScript object with these properties:
 *                      id       - mandatory, may be -1
 *                      uri      - mandatory, may be ""
 *                      type_uri - mandatory
 *                      value    - mandatory, may be ""
 *                      childs   - mandatory, may be {}
 */
function Topic(topic) {
    this.id       = topic.id
    this.uri      = topic.uri
    this.type_uri = topic.type_uri
    this.value    = topic.value
    this.childs   = build_child_topics(topic.childs)
    //
    this.assoc    = topic.assoc
}

// ### TODO: create common base class (DeepaMehtaObject) for topics and associations.
function build_child_topics(childs) {
    var child_topics = {}
    for (var child_type_uri in childs) {
        var child_topic = childs[child_type_uri]
        if (js.is_array(child_topic)) {
            child_topics[child_type_uri] = []
            for (var i = 0, topic; topic = child_topic[i]; i++) {
                child_topics[child_type_uri].push(new Topic(topic))
            }
        } else {
            child_topics[child_type_uri] = new Topic(child_topic)
        }
    }
    return child_topics
}

// === "Page Displayable" implementation ===

Topic.prototype.get_type = function() {
    return dm4c.get_topic_type(this.type_uri)
}

Topic.prototype.get_commands = function(context) {
    return dm4c.get_topic_commands(this, context)
}

// === Public API ===

/**
 * Returns the value of a direct child topic, specified by type URI. The value may be simple or composite.
 * You can lookup nested values by chaining the get() calls.
 *
 * If no such child topic exists undefined is returned.
 *
 * @param   child_type_uri  The URI of a direct child type.
 *
 * @return  A simple value (string, number, boolean) or a child topic (a Topic object) or an array
 *          (in case of multiple values), or undefined if no such direct child topic exists.
 */
Topic.prototype.get = function(child_type_uri) {
    var child_topic = this.childs[child_type_uri]
    if (child_topic) {
        if (js.is_array(child_topic)) {
            return child_topic
        }
        // ### TODO: support non-model values (e.g. facets). Currently get_topic_type() fails.
        var topic_type = dm4c.get_topic_type(child_type_uri)
        if (topic_type.is_simple()) {
            return child_topic.value
        } else {
            return child_topic
        }
    }
}

/**
 * Traverses this topic's child topics and finds a child topic by type URI.
 * If this topic itself has the specified type this topic is returned immediately.
 *
 * @param   type_uri  The URI of a child type.
 *
 * @return  The child topic (a Topic object), or undefined if no such child topic exists.
 */
Topic.prototype.find_child_topic = function(type_uri) {
    return find_child_topic(this)

    function find_child_topic(topic) {
        if (topic.type_uri == type_uri) {
            return topic
        }
        for (var child_type_uri in topic.childs) {
            var child_topic = topic.childs[child_type_uri]
            if (js.is_array(child_topic)) {
                child_topic = child_topic[0]
                // Note: the array may be empty
                if (!child_topic) {
                    continue
                }
            }
            child_topic = find_child_topic(child_topic)
            if (child_topic) {
                return child_topic
            }
        }
    }
}
