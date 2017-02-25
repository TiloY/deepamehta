package de.deepamehta.core.impl;

import de.deepamehta.core.AssociationType;
import de.deepamehta.core.model.AssociationTypeModel;



/**
 * An association type that is attached to the {@link CoreService}.
 */
class AssociationTypeImpl extends DeepaMehtaTypeImpl implements AssociationType {

    // ---------------------------------------------------------------------------------------------------- Constructors

    AssociationTypeImpl(AssociationTypeModelImpl model, PersistenceLayer pl) {
        super(model, pl);
    }

    // -------------------------------------------------------------------------------------------------- Public Methods



    // **************************************
    // *** AssociationType Implementation ***
    // **************************************



    @Override
    public AssociationTypeModelImpl getModel() {
        return (AssociationTypeModelImpl) model;
    }

    @Override
    public void update(AssociationTypeModel updateModel) {
        model.update((AssociationTypeModelImpl) updateModel);     // ### FIXME: call through pl for access control
    }



    // ----------------------------------------------------------------------------------------- Package Private Methods

    @Override
    AssociationTypeModelImpl _getModel() {
        return pl._getAssociationType(getUri());
    }
}
