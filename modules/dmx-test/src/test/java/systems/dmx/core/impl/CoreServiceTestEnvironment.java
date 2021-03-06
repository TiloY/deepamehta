package systems.dmx.core.impl;

import systems.dmx.core.util.JavaUtils;

import systems.dmx.core.service.CoreService;
import systems.dmx.core.service.ModelFactory;
import systems.dmx.core.storage.spi.DMXStorage;
import systems.dmx.core.storage.spi.DMXStorageFactory;

import org.junit.After;
import org.junit.Before;

import java.io.File;
import java.util.logging.Logger;



public class CoreServiceTestEnvironment {

    // ------------------------------------------------------------------------------------------------------- Constants

    private static final String DATABASE_FACTORY = "systems.dmx.storage.neo4j.Neo4jStorageFactory";

    // ---------------------------------------------------------------------------------------------- Instance Variables

    // providing the test subclasses access to the core service and logger
    protected CoreServiceImpl dmx;
    protected ModelFactoryImpl mf;

    protected Logger logger = Logger.getLogger(getClass().getName());

    private DMXStorage storage;
    private File dbPath;

    // -------------------------------------------------------------------------------------------------- Public Methods

    @Before
    public void setup() {
        dbPath = JavaUtils.createTempDirectory("dmx-test-");
        mf = new ModelFactoryImpl();
        storage = openDB(dbPath.getAbsolutePath());
        dmx = new CoreServiceImpl(new PersistenceLayer(storage), null);     // bundleContext=null
    }

    @After
    public void shutdown() {
        if (storage != null) {
            storage.shutdown();
        }
        dmx.shutdown();
    }

    // ------------------------------------------------------------------------------------------------- Private Methods

    private DMXStorage openDB(String databasePath) {
        try {
            logger.info("Instantiating the storage layer\n    databasePath=\"" + databasePath +
                "\"\n    databaseFactory=\"" + DATABASE_FACTORY + "\"");
            DMXStorageFactory factory = (DMXStorageFactory) Class.forName(DATABASE_FACTORY).newInstance();
            return factory.newDMXStorage(databasePath, mf);
        } catch (Exception e) {
            throw new RuntimeException("Instantiating the storage layer failed (databasePath=\"" + databasePath +
                "\", databaseFactory=\"" + DATABASE_FACTORY + "\"", e);
        }
    }
}
