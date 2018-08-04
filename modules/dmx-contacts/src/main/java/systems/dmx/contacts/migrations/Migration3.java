package systems.dmx.contacts.migrations;

import systems.dmx.core.model.AssociationDefinitionModel;
import systems.dmx.core.service.Migration;



/**
 * Adds "Date of Birth" to Person.
 * <p>
 * Part of DM 4.8
 * Runs ALWAYS
 */
public class Migration3 extends Migration {

    @Override
    public void run() {
        dmx.getTopicType("dmx.contacts.person")
            .addAssocDefBefore(
                mf.newAssociationDefinitionModel("dmx.core.composition_def", "dmx.contacts.date_of_birth", false, false,
                "dmx.contacts.person", "dmx.datetime.date", "dmx.core.one"),
            "dmx.contacts.phone_number#dmx.contacts.phone_entry");
    }
}
