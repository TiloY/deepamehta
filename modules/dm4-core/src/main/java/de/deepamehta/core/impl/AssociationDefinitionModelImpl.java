package de.deepamehta.core.impl;

import de.deepamehta.core.model.AssociationDefinitionModel;
import de.deepamehta.core.model.ChildTopicsModel;
import de.deepamehta.core.model.DeepaMehtaObjectModel;
import de.deepamehta.core.model.TopicModel;
import de.deepamehta.core.model.TopicRoleModel;
import de.deepamehta.core.model.ViewConfigurationModel;

import org.codehaus.jettison.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;



class AssociationDefinitionModelImpl extends AssociationModelImpl implements AssociationDefinitionModel {

    // ---------------------------------------------------------------------------------------------- Instance Variables

    private String parentCardinalityUri;
    private String childCardinalityUri;

    private ViewConfigurationModelImpl viewConfig;     // is never null

    private Logger logger = Logger.getLogger(getClass().getName());

    // ---------------------------------------------------------------------------------------------------- Constructors

    /**
     * Remains partially uninitialzed. Only usable as an update-model (not as a create-model).
     */
    AssociationDefinitionModelImpl(AssociationModelImpl assoc) {
        this(assoc, null, null, null);
    }

    /**
     * ### TODO: add include-in-label parameter? Alternatively could evaluate the assoc model's child topics.
     *
     * @param   customAssocTypeUri      if null no custom association type will be set.
     */
    AssociationDefinitionModelImpl(AssociationModelImpl assoc, String parentCardinalityUri, String childCardinalityUri,
                                                                                ViewConfigurationModelImpl viewConfig) {
        super(assoc);
        this.parentCardinalityUri = parentCardinalityUri;
        this.childCardinalityUri  = childCardinalityUri;
        this.viewConfig = viewConfig != null ? viewConfig : mf.newViewConfigurationModel();
        // ### TODO: why null check? Compare to TypeModelImpl constructor
    }

    // -------------------------------------------------------------------------------------------------- Public Methods

    @Override
    public String getAssocDefUri() {
        String customAssocTypeUri = getCustomAssocTypeUri();
        return getChildTypeUri() + (customAssocTypeUri !=null ? "#" + customAssocTypeUri : "");
    }

    @Override
    public String getCustomAssocTypeUri() {
        TopicModel customAssocType = getCustomAssocType();
        return customAssocType != null ? customAssocType.getUri() : null;
    }

    @Override
    public String getInstanceLevelAssocTypeUri() {
        String customAssocTypeUri = getCustomAssocTypeUri();
        return customAssocTypeUri !=null ? customAssocTypeUri : defaultInstanceLevelAssocTypeUri();
    }

    @Override
    public String getParentTypeUri() {
        return ((TopicRoleModel) getRoleModel("dm4.core.parent_type")).getTopicUri();
    }

    @Override
    public String getChildTypeUri() {
        return ((TopicRoleModel) getRoleModel("dm4.core.child_type")).getTopicUri();
    }

    @Override
    public String getParentCardinalityUri() {
        return parentCardinalityUri;
    }

    @Override
    public String getChildCardinalityUri() {
        return childCardinalityUri;
    }

    @Override
    public ViewConfigurationModelImpl getViewConfigModel() {
        return viewConfig;
    }

    // ---

    @Override
    public void setParentCardinalityUri(String parentCardinalityUri) {
        this.parentCardinalityUri = parentCardinalityUri;
    }

    @Override
    public void setChildCardinalityUri(String childCardinalityUri) {
        this.childCardinalityUri = childCardinalityUri;
    }

    @Override
    public void setViewConfigModel(ViewConfigurationModel viewConfig) {
        this.viewConfig = (ViewConfigurationModelImpl) viewConfig;
    }

    // ---

    @Override
    public JSONObject toJSON() {
        try {
            return super.toJSON()
                .put("parent_cardinality_uri", parentCardinalityUri)
                .put("child_cardinality_uri", childCardinalityUri)
                .put("view_config_topics", viewConfig.toJSONArray());
        } catch (Exception e) {
            throw new RuntimeException("Serialization failed (" + this + ")", e);
        }
    }

    // ---

    @Override
    public String toString() {
        return "\n    association definition (" + super.toString() +
            ",\n        parent cardinality=\"" + parentCardinalityUri +
            "\",\n        child cardinality=\"" + childCardinalityUri +
            "\",\n        " + viewConfig + ")\n";
    }

    // ----------------------------------------------------------------------------------------- Package Private Methods



    @Override
    String className() {
        return "association definition";
    }

    @Override
    AssociationDefinitionImpl instantiate() {
        return new AssociationDefinitionImpl(this, pl);
    }

    @Override
    AssociationDefinitionModelImpl createModelWithChildTopics(ChildTopicsModel childTopics) {
        return mf.newAssociationDefinitionModel(childTopics);
    }



    // === Core Internal Hooks ===

    /**
     * 2 assoc def specific tasks must be performed once the underlying association is updated:
     *   - Update the assoc def's cardinality (in type cache + DB). Cardinality is technically not part of the type
     *     model. So, it is not handled by the generic (model-driven) object update procedure.
     *   - Possibly rehash the assoc def in type cache. Rehashing is required if the custom assoc type has changed.
     * <p>
     * Pre condition: these 3 assoc def parts are already up-to-date through the generic (model-driven) object update
     * procedure:
     *   - Assoc Def type (type URI).
     *   - Custom assoc type (child topics).
     *   - "Include in Label" flag (child topics).
     * <p>
     * Called when update() is called on an AssociationDefinitionModel object. This is in 2 cases:
     *   - Edit a type interactively (a type topic is selected).
     *   - Programmatically call getChildTopics().set() on an AssociationDefinitionModel object, e.g. from a migration.
     * <p>
     * <i>Not</i> called when an association which also acts as an assoc def is edited interactively (an association is
     * selected). In this case:
     *   - Cardinality doesn't need to be updated as Cardinality can't be edited interactively through an association.
     *   - Rehashing is already performed in TypeModelImpl#_updateAssocDef (called from AssociationModelImpl#postUpdate)
     *
     * @param   updateModel
     *              the update data/instructions.
     *              Note: on post-update time updateModel and this (assoc def) model may differ at least because
     *                a) updateModel might contain only certain assoc def parts; this is called a "partial update"
     *                b) updateModel might contain refs and deletion-refs; this model never contains refs
     */
    @Override
    void postUpdate(DeepaMehtaObjectModel updateModel, DeepaMehtaObjectModel oldObject) {
        super.postUpdate(updateModel, oldObject);
        //
        updateCardinality((AssociationDefinitionModel) updateModel);
    }



    // === Update (memory + DB) ===

    void updateParentCardinalityUri(String parentCardinalityUri) {
        setParentCardinalityUri(parentCardinalityUri);                      // update memory
        pl.typeStorage.storeParentCardinalityUri(id, parentCardinalityUri); // update DB
    }

    void updateChildCardinalityUri(String childCardinalityUri) {
        setChildCardinalityUri(childCardinalityUri);                        // update memory
        pl.typeStorage.storeChildCardinalityUri(id, childCardinalityUri);   // update DB
    }



    // === Label Configuration ===

    final boolean includeInLabel() {
        TopicModel includeInLabel = getChildTopicsModel().getTopicOrNull("dm4.core.include_in_label");
        if (includeInLabel == null) {
            throw new RuntimeException("Assoc def \"" + getAssocDefUri() + "\" has no \"Include in Label\" topic");
        }
        return includeInLabel.getSimpleValue().booleanValue();
    }



    // === Access Control ===

    boolean isReadable() {
        try {
            // 1) check assoc def
            if (!pl.hasReadAccess(this)) {
                logger.info("### Assoc def \"" + getAssocDefUri() + "\" not READable");
                return false;
            }
            // Note: there is no need to explicitly check READability for the assoc def's child type.
            // If the child type is not READable the entire assoc def is not READable as well.
            //
            // 2) check custom assoc type, if set
            TopicModelImpl assocType = getCustomAssocType();
            if (assocType != null && !pl.hasReadAccess(assocType)) {
                logger.info("### Assoc def \"" + getAssocDefUri() + "\" not READable (custom assoc type not READable)");
                return false;
            }
            //
            return true;
        } catch (Exception e) {
            throw new RuntimeException("Checking assoc def READability failed (" + this + ")", e);
        }
    }

    // ------------------------------------------------------------------------------------------------- Private Methods



    // === Update ===

    private void updateCardinality(AssociationDefinitionModel newAssocDef) {
        updateParentCardinality(newAssocDef.getParentCardinalityUri());
        updateChildCardinality(newAssocDef.getChildCardinalityUri());
    }

    // ---

    private void updateParentCardinality(String newParentCardinalityUri) {
        // abort if no update is requested
        if (newParentCardinalityUri == null) {
            return;
        }
        //
        String parentCardinalityUri = getParentCardinalityUri();
        if (!parentCardinalityUri.equals(newParentCardinalityUri)) {
            logger.info("### Changing parent cardinality URI from \"" + parentCardinalityUri + "\" -> \"" +
                newParentCardinalityUri + "\"");
            updateParentCardinalityUri(newParentCardinalityUri);
        }
    }

    private void updateChildCardinality(String newChildCardinalityUri) {
        // abort if no update is requested
        if (newChildCardinalityUri == null) {
            return;
        }
        //
        String childCardinalityUri = getChildCardinalityUri();
        if (!childCardinalityUri.equals(newChildCardinalityUri)) {
            logger.info("### Changing child cardinality URI from \"" + childCardinalityUri + "\" -> \"" +
                newChildCardinalityUri + "\"");
            updateChildCardinalityUri(newChildCardinalityUri);
        }
    }



    // ===

    private TopicModelImpl getCustomAssocType() {
        return getChildTopicsModel().getTopicOrNull("dm4.core.assoc_type#dm4.core.custom_assoc_type");
    }

    private String defaultInstanceLevelAssocTypeUri() {
        if (typeUri.equals("dm4.core.aggregation_def")) {
            return "dm4.core.aggregation";
        } else if (typeUri.equals("dm4.core.composition_def")) {
            return "dm4.core.composition";
        } else {
            throw new RuntimeException("Unexpected association type URI: \"" + typeUri + "\"");
        }
    }
}
