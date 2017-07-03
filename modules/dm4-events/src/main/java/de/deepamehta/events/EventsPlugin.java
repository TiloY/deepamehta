package de.deepamehta.events;

import de.deepamehta.core.RelatedTopic;
import de.deepamehta.core.Topic;
import de.deepamehta.core.model.AssociationModel;
import de.deepamehta.core.model.RoleModel;
import de.deepamehta.core.osgi.PluginActivator;
import de.deepamehta.core.service.Inject;
import de.deepamehta.core.service.event.PreCreateAssociationListener;
import de.deepamehta.core.util.DeepaMehtaUtils;
import de.deepamehta.time.TimeService;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.Consumes;

import java.util.List;
import java.util.logging.Logger;



@Path("event")
@Consumes("application/json")
@Produces("application/json")
public class EventsPlugin extends PluginActivator implements EventsService, PreCreateAssociationListener {

    // ---------------------------------------------------------------------------------------------- Instance Variables

    @Inject
    private TimeService timeService;

    private static final Logger logger = Logger.getLogger(EventsPlugin.class.getName());

    // -------------------------------------------------------------------------------------------------- Public Methods



    // ************************************
    // *** EventsService Implementation ***
    // ************************************



    @GET
    @Path("/participant/{id}")
    @Override
    public List<RelatedTopic> getEvents(@PathParam("id") long personId) {
        return dm4.getTopic(personId).getRelatedTopics("dm4.events.participant", "dm4.core.default", "dm4.core.default",
            "dm4.events.event");
    }

    @GET
    @Path("/{id}/participants")
    @Override
    public List<RelatedTopic> getParticipants(@PathParam("id") long eventId) {
        return dm4.getTopic(eventId).getRelatedTopics("dm4.events.participant", "dm4.core.default", "dm4.core.default",
            "dm4.contacts.person");
    }



    // ********************************
    // *** Listener Implementations ***
    // ********************************



    @Override
    public void preCreateAssociation(AssociationModel assoc) {
        // Event <-> Person
        DeepaMehtaUtils.associationAutoTyping(assoc, "dm4.events.event", "dm4.contacts.person",
            "dm4.events.participant", "dm4.core.default", "dm4.core.default", dm4);
        //
        // Event -> Address
        RoleModel[] roles = DeepaMehtaUtils.associationAutoTyping(assoc, "dm4.events.event", "dm4.contacts.address",
            "dm4.core.aggregation", "dm4.core.parent", "dm4.core.child", dm4);
        if (roles != null) {
            long eventId = roles[0].getPlayerId();
            Topic event = dm4.getTopic(eventId);
            event.getChildTopics().getTopic("dm4.contacts.address").getRelatingAssociation().delete();
            timeService.setModified(event);
        }
    }
}
