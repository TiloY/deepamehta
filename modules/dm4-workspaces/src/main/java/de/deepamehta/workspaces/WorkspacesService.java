package de.deepamehta.workspaces;

import de.deepamehta.core.Association;
import de.deepamehta.core.DeepaMehtaObject;
import de.deepamehta.core.DeepaMehtaType;
import de.deepamehta.core.Topic;
import de.deepamehta.core.service.accesscontrol.SharingMode;

import java.util.List;



public interface WorkspacesService {

    // ------------------------------------------------------------------------------------------------------- Constants

    static final String      DEEPAMEHTA_WORKSPACE_NAME = "DeepaMehta";
    static final String      DEEPAMEHTA_WORKSPACE_URI = "dm4.workspaces.deepamehta";
    static final SharingMode DEEPAMEHTA_WORKSPACE_SHARING_MODE = SharingMode.PUBLIC;

    // Property URIs
    static final String PROP_WORKSPACE_ID = "dm4.workspaces.workspace_id";

    // -------------------------------------------------------------------------------------------------- Public Methods

    /**
     * @param   uri     may be null
     */
    Topic createWorkspace(String name, String uri, SharingMode sharingMode);

    // ---

    /**
     * Returns a workspace by URI.
     *
     * @return  The workspace (a topic of type "Workspace").
     *
     * @throws  RuntimeException    If no workspace exists for the given URI.
     */
    Topic getWorkspace(String uri);

    /**
     * Returns the workspace a topic or association is assigned to.
     *
     * @param   id      a topic ID, or an association ID
     *
     * @return  The assigned workspace (a topic of type "Workspace"),
     *          or <code>null</code> if no workspace is assigned.
     */
    Topic getAssignedWorkspace(long objectId);

    // ---

    /**
     * Assigns the given object to the given workspace.
     */
    void assignToWorkspace(DeepaMehtaObject object, long workspaceId);

    /**
     * Assigns the given type and all its view configuration topics to the given workspace.
     */
    void assignTypeToWorkspace(DeepaMehtaType type, long workspaceId);

    // ---

    /**
     * Returns all topics assigned to the given workspace.
     */
    List<Topic> getAssignedTopics(long workspaceId);

    /**
     * Returns all associations assigned to the given workspace.
     */
    List<Association> getAssignedAssociations(long workspaceId);

    // ---

    /**
     * Returns all topics of the given type that are assigned to the given workspace.
     */
    List<Topic> getAssignedTopics(long workspaceId, String topicTypeUri);

    /**
     * Returns all associations of the given type that are assigned to the given workspace.
     */
    List<Association> getAssignedAssociations(long workspaceId, String assocTypeUri);
}
