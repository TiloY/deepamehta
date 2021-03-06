package systems.dmx.core.impl;

import systems.dmx.core.Association;
import systems.dmx.core.DMXObject;
import systems.dmx.core.RelatedAssociation;
import systems.dmx.core.RelatedObject;
import systems.dmx.core.RelatedTopic;
import systems.dmx.core.Role;
import systems.dmx.core.Topic;
import systems.dmx.core.TopicRole;
import systems.dmx.core.model.AssociationModel;
import systems.dmx.core.model.RoleModel;
import systems.dmx.core.model.TopicRoleModel;

import java.util.List;

import java.util.logging.Logger;



/**
 * An association model that is attached to the DB.
 */
class AssociationImpl extends DMXObjectImpl implements Association {

    // ---------------------------------------------------------------------------------------------- Instance Variables

    private final Logger logger = Logger.getLogger(getClass().getName());

    // ---------------------------------------------------------------------------------------------------- Constructors

    AssociationImpl(AssociationModelImpl model, PersistenceLayer pl) {
        super(model, pl);
    }

    // -------------------------------------------------------------------------------------------------- Public Methods



    // *******************
    // *** Association ***
    // *******************



    @Override
    public final Role getRole1() {
        return getModel().getRoleModel1().instantiate(getModel());
    }

    @Override
    public final Role getRole2() {
        return getModel().getRoleModel2().instantiate(getModel());
    }

    // ---

    @Override
    public final DMXObject getPlayer1() {
        return getRole1().getPlayer();
    }

    @Override
    public final DMXObject getPlayer2() {
        return getRole2().getPlayer();
    }

    // --- Convenience Methods ---

    @Override
    public final Role getRole(String roleTypeUri) {
        return getModel().getRoleModel(roleTypeUri).instantiate(getModel());
    }

    @Override
    public final boolean hasSameRoleTypeUris() {
        return getModel().hasSameRoleTypeUris();
    }

    @Override
    public final boolean matches(String roleTypeUri1, long playerId1, String roleTypeUri2, long playerId2) {
        return getModel().matches(roleTypeUri1, playerId1, roleTypeUri2, playerId2);
    }

    @Override
    public final long getOtherPlayerId(long id) {
        return getModel().getOtherPlayerId(id);
    }

    // ---

    @Override
    public final RelatedObject getPlayer(String roleTypeUri) {
        DMXObjectModelImpl object = getModel().getPlayer(roleTypeUri);
        return object != null ? (RelatedObject) object.instantiate() : null;    // ### TODO: permission check?
    }

    @Override
    public final Topic getTopicByType(String topicTypeUri) {
        TopicModelImpl topic = getModel().getTopicByType(topicTypeUri);
        return topic != null ? topic.instantiate() : null;                      // ### TODO: permission check?
    }

    // ---

    // ### TODO: make use of model's getRole()
    @Override
    public final Role getRole(RoleModel roleModel) {
        if (getRole1().getModel().refsSameObject(roleModel)) {
            return getRole1();
        } else if (getRole2().getModel().refsSameObject(roleModel)) {
            return getRole2();
        }
        throw new RuntimeException("Role is not part of association (role=" + roleModel + ", association=" + this);
    }

    @Override
    public final boolean isPlayer(TopicRoleModel roleModel) {
        return filterRole(getRole1(), roleModel) != null || filterRole(getRole2(), roleModel) != null;
    }

    // ---

    @Override
    public final void update(AssociationModel updateModel) {
        pl.updateAssociation(getModel(), (AssociationModelImpl) updateModel);
    }

    @Override
    public final void delete() {
        pl.deleteAssociation(getModel());
    }

    // ---

    @Override
    public final Association loadChildTopics() {
        super.loadChildTopics();
        return this;
    }

    @Override
    public final Association loadChildTopics(String assocDefUri) {
        super.loadChildTopics(assocDefUri);
        return this;
    }

    // ---

    // Note: overridden by RelatedAssociationImpl
    @Override
    public AssociationModelImpl getModel() {
        return (AssociationModelImpl) model;
    }



    // *****************
    // *** DMXObject ***
    // *****************



    // === Traversal ===

    // ### TODO: consider adding model convenience, would require model renamings (get -> fetch)

    // --- Association Retrieval ---

    @Override
    public final RelatedAssociation getRelatedAssociation(String assocTypeUri, String myRoleTypeUri,
                                                          String othersRoleTypeUri, String othersAssocTypeUri) {
        RelatedAssociationModelImpl assoc = pl.getAssociationRelatedAssociation(getId(), assocTypeUri, myRoleTypeUri,
            othersRoleTypeUri, othersAssocTypeUri);
        return assoc != null ? assoc.instantiate() : null;
    }

    @Override
    public final List<RelatedAssociation> getRelatedAssociations(String assocTypeUri, String myRoleTypeUri,
                                                                 String othersRoleTypeUri, String othersAssocTypeUri) {
        return pl.instantiate(pl.getAssociationRelatedAssociations(getId(), assocTypeUri, myRoleTypeUri,
            othersRoleTypeUri, othersAssocTypeUri));
    }

    // ---

    @Override
    public final Association getAssociation(String assocTypeUri, String myRoleTypeUri, String othersRoleTypeUri,
                                                                                       long othersTopicId) {
        return pl.getAssociationBetweenTopicAndAssociation(assocTypeUri, othersTopicId, getId(), othersRoleTypeUri,
            myRoleTypeUri);
    }

    @Override
    public final List<Association> getAssociations() {
        return pl.instantiate(pl.getAssociationAssociations(getId()));
    }



    // ------------------------------------------------------------------------------------------------- Private Methods

    // ### TODO: move to model
    private final TopicRole filterRole(Role role, TopicRoleModel roleModel) {
        return role instanceof TopicRole && role.getRoleTypeUri().equals(roleModel.getRoleTypeUri()) &&
            role.getPlayerId() == roleModel.getPlayerId() ? (TopicRole) role : null;
    }
}
