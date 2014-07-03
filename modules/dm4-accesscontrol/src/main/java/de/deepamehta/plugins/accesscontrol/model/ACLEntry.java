package de.deepamehta.plugins.accesscontrol.model;

import de.deepamehta.core.service.accesscontrol.Operation;



public class ACLEntry {

    // ---------------------------------------------------------------------------------------------- Instance Variables

    private Operation operation;
    private UserRole[] userRoles;

    // ---------------------------------------------------------------------------------------------------- Constructors

    public ACLEntry(Operation operation, UserRole... userRoles) {
        this.operation = operation;
        this.userRoles = userRoles;
    }

    // -------------------------------------------------------------------------------------------------- Public Methods

    public Operation getOperation() {
        return operation;
    }

    public UserRole[] getUserRoles() {
        return userRoles;
    }
}
