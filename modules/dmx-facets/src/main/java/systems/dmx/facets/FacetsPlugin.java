package systems.dmx.facets;

import systems.dmx.core.Association;
import systems.dmx.core.AssociationDefinition;
import systems.dmx.core.DMXObject;
import systems.dmx.core.RelatedTopic;
import systems.dmx.core.Topic;
import systems.dmx.core.model.ChildTopicsModel;
import systems.dmx.core.model.RelatedTopicModel;
import systems.dmx.core.model.facets.FacetValueModel;
import systems.dmx.core.osgi.PluginActivator;
import systems.dmx.core.service.Transactional;
import systems.dmx.core.util.DMXUtils;

import org.codehaus.jettison.json.JSONObject;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.Produces;
import javax.ws.rs.Consumes;

import java.util.List;
import java.util.logging.Logger;



// ### TODO: support custom assoc types also for facets.
// Some assocDef.getChildTypeUri() calls must be replaced by assocDef.getAssocDefUri().
@Path("/facet")
@Consumes("application/json")
@Produces("application/json")
public class FacetsPlugin extends PluginActivator implements FacetsService {

    // ---------------------------------------------------------------------------------------------- Instance Variables

    private Logger logger = Logger.getLogger(getClass().getName());

    // -------------------------------------------------------------------------------------------------- Public Methods



    // ************************************
    // *** FacetsService Implementation ***
    // ************************************



    @GET
    @Path("/{facet_type_uri}/topic/{id}")
    @Override
    public RelatedTopic getFacet(@PathParam("id") long topicId, @PathParam("facet_type_uri") String facetTypeUri) {
        return getFacet(dmx.getTopic(topicId), facetTypeUri);
    }

    @Override
    public RelatedTopic getFacet(DMXObject object, String facetTypeUri) {
        // ### TODO: integrity check: is the object an instance of that facet type?
        return fetchChildTopic(object, getAssocDef(facetTypeUri));
    }

    // ---

    @GET
    @Path("/multi/{facet_type_uri}/topic/{id}")
    @Override
    public List<RelatedTopic> getFacets(@PathParam("id") long topicId,
                                        @PathParam("facet_type_uri") String facetTypeUri) {
        return getFacets(dmx.getTopic(topicId), facetTypeUri);
    }

    @Override
    public List<RelatedTopic> getFacets(DMXObject object, String facetTypeUri) {
        // ### TODO: integrity check: is the object an instance of that facet type?
        return fetchChildTopics(object, getAssocDef(facetTypeUri));
    }

    // ---

    @GET
    @Path("/topic/{id}")
    @Override
    public Topic getFacettedTopic(@PathParam("id") long topicId,
                                  @QueryParam("facet_type_uri") List<String> facetTypeUris) {
        try {
            Topic topic = dmx.getTopic(topicId);
            ChildTopicsModel childTopics = topic.getChildTopics().getModel();
            for (String facetTypeUri : facetTypeUris) {
                String childTypeUri = getChildTypeUri(facetTypeUri);
                if (!isMultiFacet(facetTypeUri)) {
                    Topic value = getFacet(topic, facetTypeUri);
                    if (value != null) {
                        childTopics.put(childTypeUri, value.getModel());
                    }
                } else {
                    List<RelatedTopic> values = getFacets(topic, facetTypeUri);
                    // Note: without the type witness the generic put() method (which takes an Object) would be called
                    childTopics.put(childTypeUri, DMXUtils.<RelatedTopicModel>toModelList(values));
                }
            }
            return topic;
        } catch (Exception e) {
            throw new RuntimeException("Getting facetted topic " + topicId + " failed (facetTypeUris=" + facetTypeUris +
                ")", e);
        }
    }

    @POST
    @Path("/{facet_type_uri}/topic/{id}")
    @Transactional
    @Override
    public void addFacetTypeToTopic(@PathParam("id") long topicId, @PathParam("facet_type_uri") String facetTypeUri) {
        dmx.createAssociation(mf.newAssociationModel("dmx.core.instantiation",
            mf.newTopicRoleModel(topicId,      "dmx.core.instance"),
            mf.newTopicRoleModel(facetTypeUri, "dmx.facets.facet")
        ));
    }

    // ---

    @PUT
    @Path("/{facet_type_uri}/topic/{id}")
    @Transactional
    @Override
    public void updateFacet(@PathParam("id") long topicId, @PathParam("facet_type_uri") String facetTypeUri,
                                                                                        FacetValueModel value) {
        try {
            updateFacet(dmx.getTopic(topicId), facetTypeUri, value);
        } catch (Exception e) {
            throw new RuntimeException("Updating facet \"" + facetTypeUri + "\" of topic " + topicId +
                " failed (value=" + value + ")", e);
        }
    }

    @Override
    public void updateFacet(DMXObject object, String facetTypeUri, FacetValueModel value) {
        object.updateChildTopics(value, getAssocDef(facetTypeUri));
    }

    // ---

    // Note: there is a similar private method in DMXObjectImpl:
    // fetchChildTopic(AssociationDefinition assocDef, long childTopicId)
    // ### TODO: Extend DMXObject interface by hasChildTopic()?
    @Override
    public boolean hasFacet(long topicId, String facetTypeUri, long facetTopicId) {
        String assocTypeUri = getAssocDef(facetTypeUri).getInstanceLevelAssocTypeUri();
        Association assoc = dmx.getAssociation(assocTypeUri, topicId, facetTopicId,
            "dmx.core.parent", "dmx.core.child");
        return assoc != null;
    }



    // ------------------------------------------------------------------------------------------------- Private Methods

    /**
     * Fetches and returns a child topic or <code>null</code> if no such topic extists.
     * <p>
     * Note: There is a principal copy in DMXObjectImpl but here the precondition is different:
     * The given association definition must not necessarily originate from the given object's type definition.
     * ### TODO: meanwhile we have the ValueStorage. Can we use its method instead?
     */
    private RelatedTopic fetchChildTopic(DMXObject object, AssociationDefinition assocDef) {
        String assocTypeUri  = assocDef.getInstanceLevelAssocTypeUri();
        String othersTypeUri = assocDef.getChildTypeUri();
        return object.getRelatedTopic(assocTypeUri, "dmx.core.parent", "dmx.core.child", othersTypeUri);
    }

    /**
     * Fetches and returns child topics.
     * <p>
     * Note: There is a principal copy in DMXObjectImpl but here the precondition is different:
     * The given association definition must not necessarily originate from the given object's type definition.
     * ### TODO: meanwhile we have the ValueStorage. Can we use its method instead?
     */
    private List<RelatedTopic> fetchChildTopics(DMXObject object, AssociationDefinition assocDef) {
        String assocTypeUri  = assocDef.getInstanceLevelAssocTypeUri();
        String othersTypeUri = assocDef.getChildTypeUri();
        return object.getRelatedTopics(assocTypeUri, "dmx.core.parent", "dmx.core.child", othersTypeUri);
    }

    // ---

    private boolean isMultiFacet(String facetTypeUri) {
        return getAssocDef(facetTypeUri).getChildCardinalityUri().equals("dmx.core.many");
    }

    private String getChildTypeUri(String facetTypeUri) {
        return getAssocDef(facetTypeUri).getChildTypeUri();
    }

    private AssociationDefinition getAssocDef(String facetTypeUri) {
        // Note: a facet type has exactly *one* association definition
        return dmx.getTopicType(facetTypeUri).getAssocDefs().iterator().next();
    }
}
