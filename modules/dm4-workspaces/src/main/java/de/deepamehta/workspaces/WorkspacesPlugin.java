package de.deepamehta.workspaces;

import de.deepamehta.config.ConfigDefinition;
import de.deepamehta.config.ConfigModificationRole;
import de.deepamehta.config.ConfigService;
import de.deepamehta.config.ConfigTarget;
import de.deepamehta.facets.FacetsService;
import de.deepamehta.topicmaps.TopicmapsService;

import de.deepamehta.core.Association;
import de.deepamehta.core.AssociationDefinition;
import de.deepamehta.core.AssociationType;
import de.deepamehta.core.DeepaMehtaObject;
import de.deepamehta.core.DeepaMehtaType;
import de.deepamehta.core.Topic;
import de.deepamehta.core.TopicType;
import de.deepamehta.core.osgi.PluginActivator;
import de.deepamehta.core.service.Cookies;
import de.deepamehta.core.service.DirectivesResponse;
import de.deepamehta.core.service.Inject;
import de.deepamehta.core.service.Transactional;
import de.deepamehta.core.service.accesscontrol.SharingMode;
import de.deepamehta.core.service.event.IntroduceAssociationTypeListener;
import de.deepamehta.core.service.event.IntroduceTopicTypeListener;
import de.deepamehta.core.service.event.PostCreateAssociationListener;
import de.deepamehta.core.service.event.PostCreateTopicListener;
import de.deepamehta.core.service.event.PreDeleteTopicListener;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Consumes;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Logger;



@Path("workspace")
@Consumes("application/json")
@Produces("application/json")
public class WorkspacesPlugin extends PluginActivator implements WorkspacesService, IntroduceTopicTypeListener,
                                                                                    IntroduceAssociationTypeListener,
                                                                                    PostCreateTopicListener,
                                                                                    PostCreateAssociationListener,
                                                                                    PreDeleteTopicListener {

    // ------------------------------------------------------------------------------------------------------- Constants

    private static final boolean SHARING_MODE_PRIVATE_ENABLED = Boolean.parseBoolean(
        System.getProperty("dm4.workspaces.private.enabled", "true"));
    private static final boolean SHARING_MODE_CONFIDENTIAL_ENABLED = Boolean.parseBoolean(
        System.getProperty("dm4.workspaces.confidential.enabled", "true"));
    private static final boolean SHARING_MODE_COLLABORATIVE_ENABLED = Boolean.parseBoolean(
        System.getProperty("dm4.workspaces.collaborative.enabled", "true"));
    private static final boolean SHARING_MODE_PUBLIC_ENABLED = Boolean.parseBoolean(
        System.getProperty("dm4.workspaces.public.enabled", "true"));
    private static final boolean SHARING_MODE_COMMON_ENABLED = Boolean.parseBoolean(
        System.getProperty("dm4.workspaces.common.enabled", "true"));
    // Note: the default values are required in case no config file is in effect. This applies when DM is started
    // via feature:install from Karaf. The default values must match the values defined in project POM.

    // ---------------------------------------------------------------------------------------------- Instance Variables

    @Inject
    private FacetsService facetsService;

    @Inject
    private TopicmapsService topicmapsService;

    @Inject
    private ConfigService configService;

    private Logger logger = Logger.getLogger(getClass().getName());

    // -------------------------------------------------------------------------------------------------- Public Methods



    // ****************************************
    // *** WorkspacesService Implementation ***
    // ****************************************



    @POST
    @Path("/{name}/{uri:[^/]*?}/{sharing_mode_uri}")    // Note: default is [^/]+?     // +? is a "reluctant" quantifier
    @Transactional
    @Override
    public Topic createWorkspace(@PathParam("name") final String name, @PathParam("uri") final String uri,
                                 @PathParam("sharing_mode_uri") final SharingMode sharingMode) {
        final String operation = "Creating workspace \"" + name + "\" ";
        final String info = "(uri=\"" + uri + "\", sharingMode=" + sharingMode + ")";
        try {
            // We suppress standard workspace assignment here as 1) a workspace itself gets no assignment at all,
            // and 2) the workspace's default topicmap requires a special assignment. See step 2) below.
            return dm4.getAccessControl().runWithoutWorkspaceAssignment(new Callable<Topic>() {
                @Override
                public Topic call() {
                    logger.info(operation + info);
                    //
                    // 1) create workspace
                    Topic workspace = dm4.createTopic(
                        mf.newTopicModel(uri, "dm4.workspaces.workspace", mf.newChildTopicsModel()
                            .put("dm4.workspaces.name", name)
                            .putRef("dm4.workspaces.sharing_mode", sharingMode.getUri())));
                    //
                    // 2) create default topicmap and assign to workspace
                    Topic topicmap = topicmapsService.createTopicmap(TopicmapsService.DEFAULT_TOPICMAP_NAME,
                        TopicmapsService.DEFAULT_TOPICMAP_RENDERER, false);     // isPrivate=false
                    // Note: user <anonymous> has no READ access to the workspace just created as it has no owner.
                    // So we must use the privileged assignToWorkspace() call here. This is to support the
                    // "DM4 Sign-up" 3rd-party plugin.
                    dm4.getAccessControl().assignToWorkspace(topicmap, workspace.getId());
                    //
                    return workspace;
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(operation + "failed " + info, e);
        }
    }

    // ---

    // Note: the "include_childs" query paramter is handled by the core's JerseyResponseFilter
    @GET
    @Path("/{uri}")
    @Override
    public Topic getWorkspace(@PathParam("uri") String uri) {
        return dm4.getAccessControl().getWorkspace(uri);
    }

    // Note: the "include_childs" query paramter is handled by the core's JerseyResponseFilter
    @GET
    @Path("/object/{id}")
    @Override
    public Topic getAssignedWorkspace(@PathParam("id") long objectId) {
        long workspaceId = getAssignedWorkspaceId(objectId);
        if (workspaceId == -1) {
            return null;
        }
        return dm4.getTopic(workspaceId);
    }

    // ---

    // Note: part of REST API, not part of OSGi service
    @PUT
    @Path("/{workspace_id}/object/{object_id}")
    @Transactional
    public DirectivesResponse assignToWorkspace(@PathParam("object_id") long objectId,
                                                @PathParam("workspace_id") long workspaceId) {
        try {
            checkWorkspaceId(workspaceId);
            _assignToWorkspace(dm4.getObject(objectId), workspaceId);
            return new DirectivesResponse();
        } catch (Exception e) {
            throw new RuntimeException("Assigning object " + objectId + " to workspace " + workspaceId + " failed", e);
        }
    }

    @Override
    public void assignToWorkspace(DeepaMehtaObject object, long workspaceId) {
        try {
            checkWorkspaceId(workspaceId);
            _assignToWorkspace(object, workspaceId);
        } catch (Exception e) {
            throw new RuntimeException("Assigning " + info(object) + " to workspace " + workspaceId + " failed", e);
        }
    }

    @Override
    public void assignTypeToWorkspace(DeepaMehtaType type, long workspaceId) {
        try {
            checkWorkspaceId(workspaceId);
            _assignToWorkspace(type, workspaceId);
            // view config topics
            for (Topic configTopic : type.getViewConfig().getConfigTopics()) {
                _assignToWorkspace(configTopic, workspaceId);
            }
            // association definitions
            for (AssociationDefinition assocDef : type.getAssocDefs()) {
                _assignToWorkspace(assocDef, workspaceId);
                // view config topics (of association definition)
                for (Topic configTopic : assocDef.getViewConfig().getConfigTopics()) {
                    _assignToWorkspace(configTopic, workspaceId);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Assigning " + info(type) + " to workspace " + workspaceId + " failed", e);
        }
    }

    // ---

    // Note: the "include_childs" query paramter is handled by the core's JerseyResponseFilter
    @GET
    @Path("/{id}/topics")
    @Override
    public List<Topic> getAssignedTopics(@PathParam("id") long workspaceId) {
        return dm4.getTopicsByProperty(PROP_WORKSPACE_ID, workspaceId);
    }

    // Note: the "include_childs" query paramter is handled by the core's JerseyResponseFilter
    @GET
    @Path("/{id}/assocs")
    @Override
    public List<Association> getAssignedAssociations(@PathParam("id") long workspaceId) {
        return dm4.getAssociationsByProperty(PROP_WORKSPACE_ID, workspaceId);
    }

    // ---

    // Note: the "include_childs" query paramter is handled by the core's JerseyResponseFilter
    @GET
    @Path("/{id}/topics/{topic_type_uri}")
    @Override
    public List<Topic> getAssignedTopics(@PathParam("id") long workspaceId,
                                         @PathParam("topic_type_uri") String topicTypeUri) {
        List<Topic> topics = dm4.getTopicsByType(topicTypeUri);
        applyWorkspaceFilter(topics.iterator(), workspaceId);
        return topics;
    }

    // Note: the "include_childs" query paramter is handled by the core's JerseyResponseFilter
    @GET
    @Path("/{id}/assocs/{assoc_type_uri}")
    @Override
    public List<Association> getAssignedAssociations(@PathParam("id") long workspaceId,
                                                     @PathParam("assoc_type_uri") String assocTypeUri) {
        List<Association> assocs = dm4.getAssociationsByType(assocTypeUri);
        applyWorkspaceFilter(assocs.iterator(), workspaceId);
        return assocs;
    }



    // ****************************
    // *** Hook Implementations ***
    // ****************************



    @Override
    public void preInstall() {
        configService.registerConfigDefinition(new ConfigDefinition(
            ConfigTarget.TYPE_INSTANCES, "dm4.accesscontrol.username",
            mf.newTopicModel("dm4.workspaces.enabled_sharing_modes", mf.newChildTopicsModel()
                .put("dm4.workspaces.private.enabled",       SHARING_MODE_PRIVATE_ENABLED)
                .put("dm4.workspaces.confidential.enabled",  SHARING_MODE_CONFIDENTIAL_ENABLED)
                .put("dm4.workspaces.collaborative.enabled", SHARING_MODE_COLLABORATIVE_ENABLED)
                .put("dm4.workspaces.public.enabled",        SHARING_MODE_PUBLIC_ENABLED)
                .put("dm4.workspaces.common.enabled",        SHARING_MODE_COMMON_ENABLED)
            ),
            ConfigModificationRole.ADMIN
        ));
    }

    @Override
    public void shutdown() {
        // Note 1: unregistering is crucial e.g. for redeploying the Workspaces plugin. The next register call
        // (at preInstall() time) would fail as the Config service already holds such a registration.
        // Note 2: we must check if the Config service is still available. If the Config plugin is redeployed the
        // Workspaces plugin is stopped/started as well but at shutdown() time the Config service is already gone.
        if (configService != null) {
            configService.unregisterConfigDefinition("dm4.workspaces.enabled_sharing_modes");
        } else {
            logger.warning("Config service is already gone");
        }
    }



    // ********************************
    // *** Listener Implementations ***
    // ********************************



    /**
     * Takes care the DeepaMehta standard types (and their parts) get an assignment to the DeepaMehta workspace.
     * This is important in conjunction with access control.
     * Note: type introduction is aborted if at least one of these conditions apply:
     *     - A workspace cookie is present. In this case the type gets its workspace assignment the regular way (this
     *       plugin's post-create listeners). This happens e.g. when a type is created interactively in the Webclient.
     *     - The type is not a DeepaMehta standard type. In this case the 3rd-party plugin developer is responsible
     *       for doing the workspace assignment (in case the type is created programmatically while a migration).
     *       DM can't know to which workspace a 3rd-party type belongs to. A type is regarded a DeepaMehta standard
     *       type if its URI begins with "dm4."
     */
    @Override
    public void introduceTopicType(TopicType topicType) {
        long workspaceId = workspaceIdForType(topicType);
        if (workspaceId == -1) {
            return;
        }
        //
        assignTypeToWorkspace(topicType, workspaceId);
    }

    /**
     * Takes care the DeepaMehta standard types (and their parts) get an assignment to the DeepaMehta workspace.
     * This is important in conjunction with access control.
     * Note: type introduction is aborted if at least one of these conditions apply:
     *     - A workspace cookie is present. In this case the type gets its workspace assignment the regular way (this
     *       plugin's post-create listeners). This happens e.g. when a type is created interactively in the Webclient.
     *     - The type is not a DeepaMehta standard type. In this case the 3rd-party plugin developer is responsible
     *       for doing the workspace assignment (in case the type is created programmatically while a migration).
     *       DM can't know to which workspace a 3rd-party type belongs to. A type is regarded a DeepaMehta standard
     *       type if its URI begins with "dm4."
     */
    @Override
    public void introduceAssociationType(AssociationType assocType) {
        long workspaceId = workspaceIdForType(assocType);
        if (workspaceId == -1) {
            return;
        }
        //
        assignTypeToWorkspace(assocType, workspaceId);
    }

    // ---

    /**
     * Assigns every created topic to the current workspace.
     */
    @Override
    public void postCreateTopic(Topic topic) {
        if (workspaceAssignmentIsSuppressed(topic)) {
            return;
        }
        // Note: we must avoid a vicious circle that would occur when editing a workspace. A Description topic
        // would be created (as no description is set when the workspace is created) and be assigned to the
        // workspace itself. This would create an endless recursion while bubbling the modification timestamp.
        if (isWorkspaceDescription(topic)) {
            return;
        }
        //
        long workspaceId = workspaceId();
        // Note: when there is no current workspace (because no user is logged in) we do NOT fallback to assigning
        // the DeepaMehta workspace. This would not help in gaining data consistency because the topics created
        // so far (BEFORE the Workspaces plugin is activated) would still have no workspace assignment.
        // Note: for types the situation is different. The type-introduction mechanism (see introduceTopicType()
        // handler above) ensures EVERY type is catched (regardless of plugin activation order). For instances on
        // the other hand we don't have such a mechanism (and don't want one either).
        if (workspaceId == -1) {
            return;
        }
        //
        assignToWorkspace(topic, workspaceId);
    }

    /**
     * Assigns every created association to the current workspace.
     */
    @Override
    public void postCreateAssociation(Association assoc) {
        if (workspaceAssignmentIsSuppressed(assoc)) {
            return;
        }
        // Note: we must avoid a vicious circle that would occur when the association is an workspace assignment.
        if (isWorkspaceAssignment(assoc)) {
            return;
        }
        //
        long workspaceId = workspaceId();
        // Note: when there is no current workspace (because no user is logged in) we do NOT fallback to assigning
        // the DeepaMehta workspace. This would not help in gaining data consistency because the associations created
        // so far (BEFORE the Workspaces plugin is activated) would still have no workspace assignment.
        // Note: for types the situation is different. The type-introduction mechanism (see introduceTopicType()
        // handler above) ensures EVERY type is catched (regardless of plugin activation order). For instances on
        // the other hand we don't have such a mechanism (and don't want one either).
        if (workspaceId == -1) {
            return;
        }
        //
        assignToWorkspace(assoc, workspaceId);
    }

    // ---

    /**
     * When a workspace is about to be deleted its entire content must be deleted.
     */
    @Override
    public void preDeleteTopic(Topic topic) {
        if (topic.getTypeUri().equals("dm4.workspaces.workspace")) {
            long workspaceId = topic.getId();
            deleteWorkspaceContent(workspaceId);
        }
    }



    // ------------------------------------------------------------------------------------------------- Private Methods

    private long workspaceId() {
        Cookies cookies = Cookies.get();
        if (!cookies.has("dm4_workspace_id")) {
            return -1;
        }
        return cookies.getLong("dm4_workspace_id");
    }

    /**
     * Returns the ID of the DeepaMehta workspace or -1 to signal abortion of type introduction.
     */
    private long workspaceIdForType(DeepaMehtaType type) {
        return workspaceId() == -1 && isDeepaMehtaStandardType(type) ? getDeepaMehtaWorkspace().getId() : -1;
    }

    // ---

    private long getAssignedWorkspaceId(long objectId) {
        return dm4.getAccessControl().getAssignedWorkspaceId(objectId);
    }

    private void _assignToWorkspace(DeepaMehtaObject object, long workspaceId) {
        // 1) create assignment association
        facetsService.updateFacet(object, "dm4.workspaces.workspace_facet",
            mf.newFacetValueModel("dm4.workspaces.workspace").putRef(workspaceId));
        // Note: we are refering to an existing workspace. So we must put a topic *reference* (using putRef()).
        //
        // 2) store assignment property
        object.setProperty(PROP_WORKSPACE_ID, workspaceId, true);   // addToIndex=true
    }

    // ---

    private void deleteWorkspaceContent(long workspaceId) {
        try {
            // 1) delete instances by type
            // Note: also instances assigned to other workspaces must be deleted
            for (Topic topicType : getAssignedTopics(workspaceId, "dm4.core.topic_type")) {
                String typeUri = topicType.getUri();
                for (Topic topic : dm4.getTopicsByType(typeUri)) {
                    topic.delete();
                }
                dm4.getTopicType(typeUri).delete();
            }
            for (Topic assocType : getAssignedTopics(workspaceId, "dm4.core.assoc_type")) {
                String typeUri = assocType.getUri();
                for (Association assoc : dm4.getAssociationsByType(typeUri)) {
                    assoc.delete();
                }
                dm4.getAssociationType(typeUri).delete();
            }
            // 2) delete remaining instances
            for (Topic topic : getAssignedTopics(workspaceId)) {
                topic.delete();
            }
            for (Association assoc : getAssignedAssociations(workspaceId)) {
                assoc.delete();
            }
        } catch (Exception e) {
            throw new RuntimeException("Deleting content of workspace " + workspaceId + " failed", e);
        }
    }

    // --- Helper ---

    private boolean isDeepaMehtaStandardType(DeepaMehtaType type) {
        return type.getUri().startsWith("dm4.");
    }

    private boolean isWorkspaceDescription(Topic topic) {
        return topic.getTypeUri().equals("dm4.workspaces.description");
    }

    private boolean isWorkspaceAssignment(Association assoc) {
        // Note: the current user might have no READ permission for the potential workspace.
        // This is the case e.g. when a newly created User Account is assigned to the new user's private workspace.
        return dm4.getAccessControl().isWorkspaceAssignment(assoc);
    }

    // ---

    /**
     * Returns the DeepaMehta workspace or throws an exception if it doesn't exist.
     */
    private Topic getDeepaMehtaWorkspace() {
        return getWorkspace(DEEPAMEHTA_WORKSPACE_URI);
    }

    private void applyWorkspaceFilter(Iterator<? extends DeepaMehtaObject> objects, long workspaceId) {
        while (objects.hasNext()) {
            DeepaMehtaObject object = objects.next();
            if (getAssignedWorkspaceId(object.getId()) != workspaceId) {
                objects.remove();
            }
        }
    }

    /**
     * Checks if the topic with the specified ID exists and is a Workspace. If not, an exception is thrown.
     *
     * ### TODO: principle copy in AccessControlImpl.checkWorkspaceId()
     */
    private void checkWorkspaceId(long topicId) {
        String typeUri = dm4.getTopic(topicId).getTypeUri();
        if (!typeUri.equals("dm4.workspaces.workspace")) {
            throw new IllegalArgumentException("Topic " + topicId + " is not a workspace (but of type \"" + typeUri +
                "\")");
        }
    }

    /**
     * Returns true if standard workspace assignment is currently suppressed for the current thread.
     */
    private boolean workspaceAssignmentIsSuppressed(DeepaMehtaObject object) {
        boolean suppressed = dm4.getAccessControl().workspaceAssignmentIsSuppressed();
        if (suppressed) {
            logger.fine("Standard workspace assignment for " + info(object) + " SUPPRESSED");
        }
        return suppressed;
    }

    // ---

    // ### FIXME: copied from Access Control
    // ### TODO: add shortInfo() to DeepaMehtaObject interface
    private String info(DeepaMehtaObject object) {
        if (object instanceof TopicType) {
            return "topic type \"" + object.getUri() + "\" (id=" + object.getId() + ")";
        } else if (object instanceof AssociationType) {
            return "association type \"" + object.getUri() + "\" (id=" + object.getId() + ")";
        } else if (object instanceof Topic) {
            return "topic " + object.getId() + " (typeUri=\"" + object.getTypeUri() + "\", uri=\"" + object.getUri() +
                "\")";
        } else if (object instanceof Association) {
            return "association " + object.getId() + " (typeUri=\"" + object.getTypeUri() + "\")";
        } else {
            throw new RuntimeException("Unexpected object: " + object);
        }
    }
}
