package de.deepamehta.core.impl;

import de.deepamehta.core.Topic;
import de.deepamehta.core.ViewConfiguration;
import de.deepamehta.core.model.ChildTopicsModel;
import de.deepamehta.core.model.RoleModel;
import de.deepamehta.core.model.TopicModel;
import de.deepamehta.core.model.ViewConfigurationModel;



/**
 * A view configuration that is attached to the {@link PersistenceLayer}.
 */
class ViewConfigurationImpl implements ViewConfiguration {

    // ---------------------------------------------------------------------------------------------- Instance Variables

    /**
     * The underlying model.
     */
    private ViewConfigurationModelImpl model;

    /**
     * A role that points to the object this view configuration applies to.
     * This is either a type (topic role) or an association definition (association role).
     */
    private RoleModel configurable;

    private PersistenceLayer pl;
    private ModelFactoryImpl mf;

    // ---------------------------------------------------------------------------------------------------- Constructors

    ViewConfigurationImpl(RoleModel configurable, ViewConfigurationModelImpl model, PersistenceLayer pl) {
        this.configurable = configurable;
        this.model = model;
        this.pl = pl;
        this.mf = pl.mf;
    }

    // -------------------------------------------------------------------------------------------------- Public Methods



    // === ViewConfiguration Implementation ===

    @Override
    public Iterable<Topic> getConfigTopics() {
        return pl.instantiate(model.getConfigTopics());
    }

    @Override
    public void addSetting(String configTypeUri, String settingUri, Object value) {
        ChildTopicsModel childs = mf.newChildTopicsModel().put(settingUri, value);
        TopicModelImpl configTopic = model.getConfigTopic(configTypeUri);
        if (configTopic == null) {
            configTopic = mf.newTopicModel(configTypeUri, childs);
            model.addConfigTopic(configTopic);                                  // update memory
            pl.typeStorage.storeViewConfigTopic(configurable, configTopic);     // update DB
        } else {
            configTopic.updateWithChildTopics(childs);                          // update memory + DB
        }
    }

    @Override
    public void updateConfigTopic(TopicModel configTopic) {
        model.updateConfigTopic(configTopic);
    }

    // ---

    @Override
    public ViewConfigurationModel getModel() {
        return model;
    }
}
