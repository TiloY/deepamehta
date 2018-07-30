package systems.dmx.accesscontrol.migrations;

import systems.dmx.config.ConfigService;

import systems.dmx.core.Topic;
import systems.dmx.core.service.Inject;
import systems.dmx.core.service.Migration;

import java.util.List;
import java.util.logging.Logger;



/**
 * Adds "Login enabled" config topic to each username.
 * Runs only in UPDATE mode.
 * <p>
 * Note: when CLEAN_INSTALLing the admin user already got its config topics
 * as the Config service is already in charge.
 * <p>
 * Part of DM 4.7
 */
public class Migration11 extends Migration {

    // ---------------------------------------------------------------------------------------------- Instance Variables

    @Inject
    private ConfigService configService;

    private Logger logger = Logger.getLogger(getClass().getName());

    // -------------------------------------------------------------------------------------------------- Public Methods

    @Override
    public void run() {
        List<Topic> usernames = dmx.getTopicsByType("dmx.accesscontrol.username");
        logger.info("########## Adding \"dmx.accesscontrol.login_enabled\" config topic to " + usernames.size() +
            " usernames");
        for (Topic username : usernames) {
            configService.createConfigTopic("dmx.accesscontrol.login_enabled", username);
        }
    }
}