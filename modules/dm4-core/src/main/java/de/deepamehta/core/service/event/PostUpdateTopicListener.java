package de.deepamehta.core.service.event;

import de.deepamehta.core.Topic;
import de.deepamehta.core.model.TopicModel;
import de.deepamehta.core.service.EventListener;



public interface PostUpdateTopicListener extends EventListener {

    void postUpdateTopic(Topic topic, TopicModel updateModel, TopicModel oldTopic);
}
