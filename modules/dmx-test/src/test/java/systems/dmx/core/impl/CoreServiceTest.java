package systems.dmx.core.impl;

import systems.dmx.core.Association;
import systems.dmx.core.AssociationDefinition;
import systems.dmx.core.ChildTopics;
import systems.dmx.core.DMXObject;
import systems.dmx.core.DMXType;
import systems.dmx.core.RelatedAssociation;
import systems.dmx.core.RelatedTopic;
import systems.dmx.core.Topic;
import systems.dmx.core.TopicType;
import systems.dmx.core.model.ChildTopicsModel;
import systems.dmx.core.model.SimpleValue;
import systems.dmx.core.storage.spi.DMXTransaction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

import static java.util.Arrays.asList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;



public class CoreServiceTest extends CoreServiceTestEnvironment {

    private Logger logger = Logger.getLogger(getClass().getName());

    @Test
    public void compositeModel() {
        ChildTopicsModel person = mf.newChildTopicsModel()
            .put("dmx.core.name", "Karl Blum")
            .put("dmx.contacts.home_address", mf.newChildTopicsModel()
                .put("dmx.contacts.postal_code", 13206)
                .put("dmx.contacts.city", "Berlin"))
            .put("dmx.contacts.office_address", mf.newChildTopicsModel()
                .put("dmx.contacts.postal_code", 14345)
                .put("dmx.contacts.city", "Berlin"));
        //
        assertEquals("Karl Blum", person.getString("dmx.core.name"));
        //
        ChildTopicsModel address = person.getChildTopicsModel("dmx.contacts.home_address");
        assertEquals("Berlin", address.getString("dmx.contacts.city"));
        //
        Object code = address.getObject("dmx.contacts.postal_code");
        assertSame(Integer.class, code.getClass());
        assertEquals(13206, code);  // autoboxing
    }

    // ---

    @Test
    public void typeDefinition() {
        TopicType topicType = dmx.getTopicType("dmx.core.plugin");
        assertEquals("dmx.core.plugin",     topicType.getUri());
        assertEquals("dmx.core.topic_type", topicType.getTypeUri());
        assertEquals("dmx.core.composite",  topicType.getDataTypeUri());
        assertEquals(3,                     topicType.getAssocDefs().size());
        AssociationDefinition assocDef =    topicType.getAssocDef("dmx.core.plugin_migration_nr");
        assertEquals("dmx.core.composition_def",     assocDef.getTypeUri());
        assertEquals("dmx.core.plugin",              assocDef.getParentTypeUri());
        assertEquals("dmx.core.plugin_migration_nr", assocDef.getChildTypeUri());
        assertEquals("dmx.core.one",                 assocDef.getChildCardinalityUri());
        DMXObject t1 = assocDef.getPlayer("dmx.core.parent_type");
        DMXObject t2 = assocDef.getPlayer("dmx.core.child_type");
        assertEquals("dmx.core.plugin",              t1.getUri());
        assertEquals("dmx.core.topic_type",          t1.getTypeUri());
        assertEquals("dmx.core.plugin_migration_nr", t2.getUri());
        assertEquals("dmx.core.topic_type",          t2.getTypeUri());
    }

    @Test
    public void createWithoutComposite() {
        DMXTransaction tx = dmx.beginTx();
        try {
            Topic topic = dmx.createTopic(mf.newTopicModel("systems.dmx.notes", "dmx.core.plugin",
                new SimpleValue("DMX Notes")));
            //
            topic.getChildTopics().set("dmx.core.plugin_migration_nr", 23);
            //
            int nr = topic.getChildTopics().getTopic("dmx.core.plugin_migration_nr").getSimpleValue().intValue();
            assertEquals(23, nr);
            //
            topic.getChildTopics().set("dmx.core.plugin_migration_nr", 42);
            //
            nr = topic.getChildTopics().getTopic("dmx.core.plugin_migration_nr").getSimpleValue().intValue();
            assertEquals(42, nr);
            //
            tx.success();
        } finally {
            tx.finish();
        }
    }

    @Test
    public void createWithComposite() {
        DMXTransaction tx = dmx.beginTx();
        try {
            // Note: has() is internal API, so we need a TopicImpl here
            TopicImpl topic = dmx.createTopic(mf.newTopicModel("systems.dmx.notes", "dmx.core.plugin",
                mf.newChildTopicsModel().put("dmx.core.plugin_migration_nr", 23)
            ));
            //
            assertTrue(topic.getChildTopics().has("dmx.core.plugin_migration_nr"));
            //
            int nr = topic.getChildTopics().getTopic("dmx.core.plugin_migration_nr").getSimpleValue().intValue();
            assertEquals(23, nr);
            //
            topic.getChildTopics().set("dmx.core.plugin_migration_nr", 42);
            //
            nr = topic.getChildTopics().getTopic("dmx.core.plugin_migration_nr").getSimpleValue().intValue();
            assertEquals(42, nr);
            //
            tx.success();
        } finally {
            tx.finish();
        }
    }

    // ---

    @Test
    public void onDemandChildTopicLoading() {
        DMXTransaction tx = dmx.beginTx();
        try {
            dmx.createTopic(mf.newTopicModel("systems.dmx.notes", "dmx.core.plugin",
                mf.newChildTopicsModel().put("dmx.core.plugin_migration_nr", 23)
            ));
            // Note: has() is internal API, so we need a TopicImpl here
            TopicImpl topic = dmx.getTopicByUri("systems.dmx.notes");
            ChildTopicsImpl comp = topic.getChildTopics();
            assertFalse(comp.has("dmx.core.plugin_migration_nr"));              // child topic is not yet loaded
            //
            Topic childTopic = comp.getTopic("dmx.core.plugin_migration_nr");
            assertEquals(23, childTopic.getSimpleValue().intValue());           // child topic is loaded on-demand
            assertTrue(comp.has("dmx.core.plugin_migration_nr"));               // child topic is now loaded
            //
            tx.success();
        } finally {
            tx.finish();
        }
    }

    @Test
    public void onDemandChildTopicLoadingWithConvenienceAccessor() {
        DMXTransaction tx = dmx.beginTx();
        try {
            dmx.createTopic(mf.newTopicModel("systems.dmx.notes", "dmx.core.plugin",
                mf.newChildTopicsModel().put("dmx.core.plugin_migration_nr", 23)
            ));
            // Note: has() is internal API, so we need a TopicImpl here
            TopicImpl topic = dmx.getTopicByUri("systems.dmx.notes");
            ChildTopicsImpl comp = topic.getChildTopics();
            assertFalse(comp.has("dmx.core.plugin_migration_nr"));              // child topic is not yet loaded
            //
            assertEquals(23, comp.getInt("dmx.core.plugin_migration_nr"));      // child topic is loaded on-demand
            assertTrue(comp.has("dmx.core.plugin_migration_nr"));               // child topic is now loaded
            //
            tx.success();
        } finally {
            tx.finish();
        }
    }

    // ---

    @Test
    public void changeLabelWithSetChildTopics() {
        DMXTransaction tx = dmx.beginTx();
        try {
            Topic topic = dmx.createTopic(mf.newTopicModel("dmx.core.plugin"));
            assertEquals("", topic.getSimpleValue().toString());
            //
            topic.setChildTopics(mf.newChildTopicsModel().put("dmx.core.plugin_name", "My Plugin"));
            assertEquals("My Plugin", topic.getChildTopics().getString("dmx.core.plugin_name"));
            assertEquals("My Plugin", topic.getSimpleValue().toString());
            //
            Topic fetchedTopic = dmx.getTopic(topic.getId());
            assertEquals("My Plugin", fetchedTopic.getSimpleValue().toString());
            //
            tx.success();
        } finally {
            tx.finish();
        }
    }

    @Test
    public void changeLabelWithChildTopicsSet() {
        DMXTransaction tx = dmx.beginTx();
        try {
            Topic topic = dmx.createTopic(mf.newTopicModel("dmx.core.plugin"));
            assertEquals("", topic.getSimpleValue().toString());
            //
            topic.getChildTopics().set("dmx.core.plugin_name", "My Plugin");
            assertEquals("My Plugin", topic.getChildTopics().getString("dmx.core.plugin_name"));
            assertEquals("My Plugin", topic.getSimpleValue().toString());
            //
            Topic fetchedTopic = dmx.getTopic(topic.getId());
            assertEquals("My Plugin", fetchedTopic.getSimpleValue().toString());
            //
            tx.success();
        } finally {
            tx.finish();
        }
    }

    // ---

    @Test
    public void setLabelChildWhileChildsAreNotLoaded() {
        DMXTransaction tx = dmx.beginTx();
        try {
            Topic topic = dmx.createTopic(mf.newTopicModel("dmx.core.plugin", mf.newChildTopicsModel()
                .put("dmx.core.plugin_name", "My Plugin")
                .put("dmx.core.plugin_symbolic_name", "dmx.test.my_plugin")
                .put("dmx.core.plugin_migration_nr", 1)
            ));
            assertEquals("My Plugin", topic.getSimpleValue().toString());
            //
            topic = dmx.getTopic(topic.getId());                            // Note: the childs are not loaded
            assertEquals("My Plugin", topic.getSimpleValue().toString());   // the label is intact
            topic.getChildTopics().set("dmx.core.plugin_name", "HuHu");     // setting child used for labeling
            assertEquals("HuHu", topic.getSimpleValue().toString());        // the label is recalculated
            //
            tx.success();
        } finally {
            tx.finish();
        }
    }

    @Test
    public void setNonlabelChildWhileChildsAreNotLoaded() {
        DMXTransaction tx = dmx.beginTx();
        try {
            Topic topic = dmx.createTopic(mf.newTopicModel("dmx.core.plugin", mf.newChildTopicsModel()
                .put("dmx.core.plugin_name", "My Plugin")
                .put("dmx.core.plugin_symbolic_name", "dmx.test.my_plugin")
                .put("dmx.core.plugin_migration_nr", 1)
            ));
            assertEquals("My Plugin", topic.getSimpleValue().toString());
            //
            topic = dmx.getTopic(topic.getId());                            // Note: the childs are not loaded
            assertEquals("My Plugin", topic.getSimpleValue().toString());   // the label is intact
            topic.getChildTopics().set("dmx.core.plugin_migration_nr", 3);  // setting child NOT used for labeling
            assertEquals("My Plugin", topic.getSimpleValue().toString());   // the label is still intact
            //
            tx.success();
        } finally {
            tx.finish();
        }
    }

    // ---

    @Test
    public void changeLabelWithSetRefSimple() {
        DMXTransaction tx = dmx.beginTx();
        try {
            // 1) define model
            // "Person Name" (simple)
            dmx.createTopicType(mf.newTopicTypeModel("dmx.test.person_name", "Person Name", "dmx.core.text"));
            // "Comment" (composite)
            dmx.createTopicType(mf.newTopicTypeModel("dmx.test.comment", "Comment", "dmx.core.composite")
                .addAssocDef(mf.newAssociationDefinitionModel(
                    "dmx.test.comment", "dmx.test.person_name", "dmx.core.one"
                ))
            );
            // 2) create instances
            // "Person Name"
            Topic karl = dmx.createTopic(mf.newTopicModel("dmx.test.person_name", new SimpleValue("Karl Albrecht")));
            //
            assertEquals("Karl Albrecht", karl.getSimpleValue().toString());
            //
            // "Comment"
            Topic comment = dmx.createTopic(mf.newTopicModel("dmx.test.comment"));
            comment.getChildTopics().setRef("dmx.test.person_name", karl.getId());
            //
            assertEquals(karl.getId(), comment.getChildTopics().getTopic("dmx.test.person_name").getId());
            assertEquals("Karl Albrecht", comment.getSimpleValue().toString());
            //
            tx.success();
        } finally {
            tx.finish();
        }
    }

    @Test
    public void changeLabelWithSetRefComposite() {
        DMXTransaction tx = dmx.beginTx();
        try {
            // 1) define model
            // "First Name", "Last Name" (simple)
            dmx.createTopicType(mf.newTopicTypeModel("dmx.test.first_name", "First Name", "dmx.core.text"));
            dmx.createTopicType(mf.newTopicTypeModel("dmx.test.last_name",  "Last Name",  "dmx.core.text"));
            // "Person Name" (composite)
            dmx.createTopicType(mf.newTopicTypeModel("dmx.test.person_name", "Person Name", "dmx.core.composite")
                .addAssocDef(mf.newAssociationDefinitionModel(null, false, true,
                    "dmx.test.person_name", "dmx.test.first_name", "dmx.core.one"
                ))
                .addAssocDef(mf.newAssociationDefinitionModel(null, false, true,
                    "dmx.test.person_name", "dmx.test.last_name", "dmx.core.one"
                ))
            );
            // "Comment" (composite)
            dmx.createTopicType(mf.newTopicTypeModel("dmx.test.comment", "Comment", "dmx.core.composite")
                .addAssocDef(mf.newAssociationDefinitionModel(
                    "dmx.test.comment", "dmx.test.person_name", "dmx.core.one"
                ))
            );
            // 2) create instances
            // "Person Name"
            Topic karl = dmx.createTopic(mf.newTopicModel("dmx.test.person_name", mf.newChildTopicsModel()
                .put("dmx.test.first_name", "Karl")
                .put("dmx.test.last_name", "Albrecht")
            ));
            //
            assertEquals("Karl Albrecht", karl.getSimpleValue().toString());
            //
            // "Comment"
            Topic comment = dmx.createTopic(mf.newTopicModel("dmx.test.comment"));
            comment.getChildTopics().setRef("dmx.test.person_name", karl.getId());
            //
            assertEquals(karl.getId(), comment.getChildTopics().getTopic("dmx.test.person_name").getId());
            assertEquals("Karl Albrecht", comment.getSimpleValue().toString());
            //
            tx.success();
        } finally {
            tx.finish();
        }
    }

    @Test
    public void changeLabelWithSetComposite() {
        DMXTransaction tx = dmx.beginTx();
        try {
            // 1) define model
            // "First Name", "Last Name" (simple)
            dmx.createTopicType(mf.newTopicTypeModel("dmx.test.first_name", "First Name", "dmx.core.text"));
            dmx.createTopicType(mf.newTopicTypeModel("dmx.test.last_name",  "Last Name",  "dmx.core.text"));
            // "Person Name" (composite)
            dmx.createTopicType(mf.newTopicTypeModel("dmx.test.person_name", "Person Name", "dmx.core.composite")
                .addAssocDef(mf.newAssociationDefinitionModel(null, false, true,
                    "dmx.test.person_name", "dmx.test.first_name", "dmx.core.one"
                ))
                .addAssocDef(mf.newAssociationDefinitionModel(null, false, true,
                    "dmx.test.person_name", "dmx.test.last_name", "dmx.core.one"
                ))
            );
            // "Comment" (composite)
            dmx.createTopicType(mf.newTopicTypeModel("dmx.test.comment", "Comment", "dmx.core.composite")
                .addAssocDef(mf.newAssociationDefinitionModel(
                    "dmx.test.comment", "dmx.test.person_name", "dmx.core.one"
                ))
            );
            // 2) create instances
            // "Comment"
            Topic comment = dmx.createTopic(mf.newTopicModel("dmx.test.comment"));
            comment.getChildTopics().set("dmx.test.person_name", mf.newChildTopicsModel()
                .put("dmx.test.first_name", "Karl")
                .put("dmx.test.last_name", "Albrecht")
            );
            //
            assertEquals("Karl Albrecht", comment.getSimpleValue().toString());
            //
            tx.success();
        } finally {
            tx.finish();
        }
    }

    // ---

    @Test
    public void hasIncludeInLabel() {
        // Note: the assoc def is created while migration
        RelatedTopic includeInLabel = dmx.getTopicType("dmx.core.plugin")
            .getAssocDef("dmx.core.plugin_name").getChildTopics().getTopicOrNull("dmx.core.include_in_label");
        assertNotNull(includeInLabel);
        assertEquals(false, includeInLabel.getSimpleValue().booleanValue());
    }

    @Test
    public void hasIncludeInLabelForAddedAssocDef() {
        DMXTransaction tx = dmx.beginTx();
        try {
            // add assoc def programmatically
            dmx.createTopicType(mf.newTopicTypeModel("dmx.test.date", "Date", "dmx.core.text"));
            dmx.getTopicType("dmx.core.plugin").addAssocDef(
                mf.newAssociationDefinitionModel(
                    "dmx.core.plugin", "dmx.test.date", "dmx.core.one"
                ));
            //
            // Note: the topic type must be re-get as getTopicType() creates
            // a cloned model that doesn't contain the added assoc def
            RelatedTopic includeInLabel = dmx.getTopicType("dmx.core.plugin")
                .getAssocDef("dmx.test.date").getChildTopics().getTopicOrNull("dmx.core.include_in_label");
            assertNotNull(includeInLabel);
            assertEquals(false, includeInLabel.getSimpleValue().booleanValue());
            //
            tx.success();
        } finally {
            tx.finish();
        }
    }

    // ---

    @Test
    public void setIncludeInLabel() {
        DMXTransaction tx = dmx.beginTx();
        try {
            TopicTypeImpl tt = dmx.getTopicType("dmx.core.plugin");
            //
            // set "Include in Label" flag
            ChildTopics ct = tt.getAssocDef("dmx.core.plugin_name").getChildTopics()
                .set("dmx.core.include_in_label", true);
            //
            assertEquals(true, ct.getBoolean("dmx.core.include_in_label"));
            //
            List<String> lc = tt.getLabelConfig();
            assertEquals(1, lc.size());
            assertEquals("dmx.core.plugin_name", lc.get(0));
            //
            tx.success();
        } finally {
            tx.finish();
        }
    }

    @Test
    public void setIncludeInLabelWhenCustomAssocTypeIsSet() {
        DMXTransaction tx = dmx.beginTx();
        try {
            // 1) create composite type, set a custom assoc type
            dmx.createTopicType(mf.newTopicTypeModel("dmx.test.date", "Date", "dmx.core.text"));
            dmx.createAssociationType(mf.newAssociationTypeModel("dmx.test.birthday", "Birthday", "dmx.core.text"));
            TopicTypeImpl tt = dmx.createTopicType(
                mf.newTopicTypeModel("dmx.test.person", "Person", "dmx.core.composite").addAssocDef(
                    mf.newAssociationDefinitionModel("dmx.test.birthday", false, false,
                        "dmx.test.person", "dmx.test.date", "dmx.core.one")));
            // test assoc def childs *before* set
            ChildTopics ct = tt.getAssocDef("dmx.test.date#dmx.test.birthday").getChildTopics();
            assertEquals(false, ct.getBoolean("dmx.core.include_in_label"));
            assertEquals("dmx.test.birthday", ct.getTopic("dmx.core.assoc_type#dmx.core.custom_assoc_type").getUri());
            //
            // 2) set "Include in Label" flag
            ct.set("dmx.core.include_in_label", true);
            //
            // test assoc def childs *after* set (custom assoc type must not change)
            assertEquals(true, ct.getBoolean("dmx.core.include_in_label"));
            assertEquals("dmx.test.birthday", ct.getTopic("dmx.core.assoc_type#dmx.core.custom_assoc_type").getUri());
            //
            List<String> lc = tt.getLabelConfig();
            assertEquals(1, lc.size());
            assertEquals("dmx.test.date#dmx.test.birthday", lc.get(0));
            //
            tx.success();
        } finally {
            tx.finish();
        }
    }

    // ---

    @Test
    public void editAssocDefViaAssoc() {
        DMXTransaction tx = dmx.beginTx();
        try {
            // set "Include in Label" flag
            long assocDefId = dmx.getTopicType("dmx.core.plugin").getAssocDef("dmx.core.plugin_name").getId();
            dmx.getAssociation(assocDefId).getChildTopics().set("dmx.core.include_in_label", false);
            //
            // assoc def order must not have changed
            Collection<AssociationDefinition> assocDefs = dmx.getTopicType("dmx.core.plugin").getAssocDefs();
            // Note: the topic type must be re-get as getTopicType() creates
            // a cloned model that doesn't contain the manipulated assoc defs
            assertEquals(3, assocDefs.size());
            Iterator<AssociationDefinition> i = assocDefs.iterator();
            assertEquals("dmx.core.plugin_name",          i.next().getAssocDefUri());
            assertEquals("dmx.core.plugin_symbolic_name", i.next().getAssocDefUri());
            assertEquals("dmx.core.plugin_migration_nr",  i.next().getAssocDefUri());
            //
            tx.success();
        } finally {
            tx.finish();
        }
    }

    @Test
    public void editAssocDefSetCustomAssocType() {
        DMXTransaction tx = dmx.beginTx();
        try {
            // set Custom Association Type (via assoc def)
            dmx.getTopicType("dmx.core.plugin").getAssocDef("dmx.core.plugin_name").getChildTopics()
                .setRef("dmx.core.assoc_type#dmx.core.custom_assoc_type", "dmx.core.association");
            //
            // get Custom Association Type
            Topic assocType = dmx.getTopicType("dmx.core.plugin")
                .getAssocDef("dmx.core.plugin_name#dmx.core.association").getChildTopics()
                .getTopic("dmx.core.assoc_type#dmx.core.custom_assoc_type");
            // Note: the topic type must be re-get as getTopicType() creates
            // a cloned model that doesn't contain the manipulated assoc defs
            assertEquals("dmx.core.association", assocType.getUri());
            //
            tx.success();
        } finally {
            tx.finish();
        }
    }

    @Test
    public void editAssocDefViaAssocSetCustomAssocType() {
        DMXTransaction tx = dmx.beginTx();
        try {
            // set Custom Association Type (via association)
            long assocDefId = dmx.getTopicType("dmx.core.plugin").getAssocDef("dmx.core.plugin_name").getId();
            dmx.getAssociation(assocDefId).getChildTopics()
                .setRef("dmx.core.assoc_type#dmx.core.custom_assoc_type", "dmx.core.association");
            //
            // get Custom Association Type
            Topic assocType = dmx.getTopicType("dmx.core.plugin")
                .getAssocDef("dmx.core.plugin_name#dmx.core.association").getChildTopics()
                .getTopic("dmx.core.assoc_type#dmx.core.custom_assoc_type");
            // Note: the topic type must be re-get as getTopicType() creates
            // a cloned model that doesn't contain the manipulated assoc defs
            assertEquals("dmx.core.association", assocType.getUri());
            //
            tx.success();
        } finally {
            tx.finish();
        }
    }

    // ---

    @Test
    public void uriUniquenessCreateTopic() {
        DMXTransaction tx = dmx.beginTx();
        try {
            Topic topic = dmx.createTopic(mf.newTopicModel("dmx.my.uri", "dmx.core.plugin"));
            assertEquals("dmx.my.uri", topic.getUri());
            //
            dmx.createTopic(mf.newTopicModel("dmx.my.uri", "dmx.core.plugin"));
            fail("\"URI not unique\" exception not thrown");
            //
            tx.success();
        } catch (Exception e) {
            Throwable cause = e.getCause();
            assertNotNull(cause);
            assertEquals("URI \"dmx.my.uri\" is not unique", cause.getMessage());
        } finally {
            tx.finish();
        }
    }

    @Test
    public void uriUniquenessSetUri() {
        DMXTransaction tx = dmx.beginTx();
        try {
            Topic topic1 = dmx.createTopic(mf.newTopicModel("dmx.my.uri", "dmx.core.plugin"));
            assertEquals("dmx.my.uri", topic1.getUri());
            //
            Topic topic2 = dmx.createTopic(mf.newTopicModel("dmx.core.plugin"));
            assertEquals("", topic2.getUri());
            //
            topic2.setUri("dmx.my.uri");
            fail("\"URI not unique\" exception not thrown");
            //
            tx.success();
        } catch (Exception e) {
            assertEquals("URI \"dmx.my.uri\" is not unique", e.getMessage());
        } finally {
            tx.finish();
        }
    }

    @Test
    public void uriUniquenessUpdate() {
        DMXTransaction tx = dmx.beginTx();
        long topic2Id = -1;
        try {
            Topic topic1 = dmx.createTopic(mf.newTopicModel("dmx.my.uri", "dmx.core.plugin"));
            assertEquals("dmx.my.uri", topic1.getUri());
            //
            Topic topic2 = dmx.createTopic(mf.newTopicModel("dmx.core.plugin"));
            topic2Id = topic2.getId();
            assertEquals("", topic2.getUri());
            //
            topic2.update(mf.newTopicModel("dmx.my.uri", "dmx.core.plugin"));
            fail("\"URI not unique\" exception not thrown");
            //
            tx.success();
        } catch (Exception e) {
            // logger.log(Level.WARNING, "Exception thrown:", e);
            assertEquals("Updating topic " + topic2Id + " failed", e.getMessage());
            assertEquals("URI \"dmx.my.uri\" is not unique", e.getCause().getCause().getMessage());
        } finally {
            tx.finish();
        }
    }

    // ---

    @Test
    public void assocDefSequence() {
        DMXType type = dmx.getTopicType("dmx.core.plugin");
        //
        // find assoc def 1/3
        RelatedAssociation assocDef = type.getRelatedAssociation("dmx.core.aggregation", "dmx.core.type",
            "dmx.core.sequence_start", null);   // othersAssocTypeUri=null
        logger.info("### assoc def ID 1/3 = " + assocDef.getId() +
            ", relating assoc ID = " + assocDef.getRelatingAssociation().getId());
        assertNotNull(assocDef);
        //
        // find assoc def 2/3
        assocDef = assocDef.getRelatedAssociation("dmx.core.sequence", "dmx.core.predecessor", "dmx.core.successor",
            null);                              // othersAssocTypeUri=null
        logger.info("### assoc def ID 2/3 = " + assocDef.getId() +
            ", relating assoc ID = " + assocDef.getRelatingAssociation().getId());
        assertNotNull(assocDef);
        //
        // find assoc def 3/3
        assocDef = assocDef.getRelatedAssociation("dmx.core.sequence", "dmx.core.predecessor", "dmx.core.successor",
            null);                              // othersAssocTypeUri=null
        logger.info("### assoc def ID 3/3 = " + assocDef.getId() +
            ", relating assoc ID = " + assocDef.getRelatingAssociation().getId());
        assertNotNull(assocDef);
        //
        // there is no other
        assocDef = assocDef.getRelatedAssociation("dmx.core.sequence", "dmx.core.predecessor", "dmx.core.successor",
            null);                              // othersAssocTypeUri=null
        assertNull(assocDef);
    }

    // ---

    @Test
    public void insertAssocDefAtPos0() {
        DMXTransaction tx = dmx.beginTx();
        try {
            // create child type
            dmx.createTopicType(mf.newTopicTypeModel("dmx.test.name", "Name", "dmx.core.text"));
            // insert assoc def at pos 0
            dmx.getTopicType("dmx.core.plugin").addAssocDefBefore(
                mf.newAssociationDefinitionModel("dmx.core.plugin", "dmx.test.name", "dmx.core.one"),
                "dmx.core.plugin_name"
            );
            //
            // Note: the type manipulators (here: addAssocDefBefore()) operate on the *kernel* type model, while the
            // accessors (here: getAssocDefs()) operate on the *userland* type model, which is a cloned (and filtered)
            // kernel type model. The manipulation is not immediately visible in the userland type model. To see the
            // change we must re-get the userland type model (by getTopicType()).
            Collection<AssociationDefinition> assocDefs = dmx.getTopicType("dmx.core.plugin").getAssocDefs();
            assertSame(4, assocDefs.size());
            //
            Iterator<AssociationDefinition> i = assocDefs.iterator();
            assertEquals("dmx.test.name", i.next().getChildTypeUri());          // new assoc def is at pos 0
            assertEquals("dmx.core.plugin_name", i.next().getChildTypeUri());   // former pos 0 is now at pos 1
            //
            tx.success();
        } finally {
            tx.finish();
        }
    }

    @Test
    public void insertAssocDefAtPos1() {
        DMXTransaction tx = dmx.beginTx();
        try {
            // create child type
            dmx.createTopicType(mf.newTopicTypeModel("dmx.test.name", "Name", "dmx.core.text"));
            // insert assoc def at pos 1
            dmx.getTopicType("dmx.core.plugin").addAssocDefBefore(
                mf.newAssociationDefinitionModel("dmx.core.plugin", "dmx.test.name", "dmx.core.one"),
                "dmx.core.plugin_symbolic_name"
            );
            //
            // Note: the type manipulators (here: addAssocDefBefore()) operate on the *kernel* type model, while the
            // accessors (here: getAssocDefs()) operate on the *userland* type model, which is a cloned (and filtered)
            // kernel type model. The manipulation is not immediately visible in the userland type model. To see the
            // change we must re-get the userland type model (by getTopicType()).
            Collection<AssociationDefinition> assocDefs = dmx.getTopicType("dmx.core.plugin").getAssocDefs();
            assertSame(4, assocDefs.size());
            //
            Iterator<AssociationDefinition> i = assocDefs.iterator();
            assertEquals("dmx.core.plugin_name", i.next().getChildTypeUri());           // pos 0 is unchanged
            assertEquals("dmx.test.name", i.next().getChildTypeUri());                  // new assoc def is at pos 1
            assertEquals("dmx.core.plugin_symbolic_name", i.next().getChildTypeUri());  // former pos 1 is now at pos 2
            //
            tx.success();
        } finally {
            tx.finish();
        }
    }

    // ---

    @Test
    public void getTopicsByType() {
        Topic type = dmx.getTopicByUri("dmx.core.data_type");
        List<RelatedTopic> topics1 = getTopicInstancesByTraversal(type);
        assertEquals(5, topics1.size());
        List<Topic> topics2 = getTopicInstances("dmx.core.data_type");
        assertEquals(5, topics2.size());
    }

    // Note: when the meta model changes the values might need adjustment
    @Test
    public void getAssociationsByType() {
        List<RelatedAssociation> assocs;
        //
        assocs = getAssociationInstancesByTraversal("dmx.core.instantiation");
        assertEquals(66, assocs.size());
        //
        assocs = getAssociationInstancesByTraversal("dmx.core.composition_def");
        assertEquals(5, assocs.size());
    }

    // ---

    @Test
    public void retypeAssociation() {
        DMXTransaction tx = dmx.beginTx();
        Topic type;
        List<RelatedTopic> childTypes;
        try {
            type = dmx.getTopicByUri("dmx.core.plugin");
            childTypes = getChildTypes(type);
            assertEquals(3, childTypes.size());
            //
            // retype assoc
            Association assoc = childTypes.get(0).getRelatingAssociation();
            assertEquals("dmx.core.composition_def", assoc.getTypeUri());
            assoc.setTypeUri("dmx.core.association");
            assertEquals("dmx.core.association", assoc.getTypeUri());
            assoc = dmx.getAssociation(assoc.getId());
            assertEquals("dmx.core.association", assoc.getTypeUri());
            //
            // re-execute query
            childTypes = getChildTypes(type);
            assertEquals(3, childTypes.size());
            // ### Note: the Lucene index update is not visible within the transaction!
            // ### That's contradictory to the Neo4j documentation!
            // ### It states that QueryContext's tradeCorrectnessForSpeed behavior is off by default.
            //
            tx.success();
        } finally {
            tx.finish();
        }
        // re-execute query
        childTypes = getChildTypes(type);
        assertEquals(2, childTypes.size());
        // ### Note: the Lucene index update is only visible once the transaction is committed!
        // ### That's contradictory to the Neo4j documentation!
        // ### It states that QueryContext's tradeCorrectnessForSpeed behavior is off by default.
    }

    @Test
    public void retypeAssociationRoles() {
        DMXTransaction tx = dmx.beginTx();
        Topic type;
        List<RelatedTopic> childTypes;
        try {
            type = dmx.getTopicByUri("dmx.core.plugin");
            childTypes = getChildTypes(type);
            assertEquals(3, childTypes.size());
            //
            // retype assoc roles
            Association assoc = childTypes.get(0).getRelatingAssociation();
            assoc.getRole1().setRoleTypeUri("dmx.core.default");
            assoc.getRole2().setRoleTypeUri("dmx.core.default");
            //
            // re-execute query
            childTypes = getChildTypes(type);
            assertEquals(3, childTypes.size());
            // ### Note: the Lucene index update is not visible within the transaction!
            // ### That's contradictory to the Neo4j documentation!
            // ### It states that QueryContext's tradeCorrectnessForSpeed behavior is off by default.
            //
            tx.success();
        } finally {
            tx.finish();
        }
        // re-execute query
        childTypes = getChildTypes(type);
        assertEquals(2, childTypes.size());
        // ### Note: the Lucene index update is only visible once the transaction is committed!
        // ### That's contradictory to the Neo4j documentation!
        // ### It states that QueryContext's tradeCorrectnessForSpeed behavior is off by default.
    }

    @Test
    public void retypeTopicAndTraverse() {
        DMXTransaction tx = dmx.beginTx();
        Topic t0;
        List<RelatedTopic> topics;
        try {
            setupTestTopics();
            //
            t0 = dmx.getTopicByUri("dmx.test.t0");
            //
            // execute query
            topics = getTestTopics(t0);
            assertEquals(3, topics.size());  // we have 3 topics
            //
            // retype the first topic
            Topic topic = topics.get(0);
            assertEquals("dmx.core.plugin", topic.getTypeUri());
            topic.setTypeUri("dmx.core.data_type");
            assertEquals("dmx.core.data_type", topic.getTypeUri());
            topic = dmx.getTopic(topic.getId());
            assertEquals("dmx.core.data_type", topic.getTypeUri());
            //
            // re-execute query
            topics = getTestTopics(t0);
            assertEquals(2, topics.size());  // now we have 2 topics
            // ### Note: the Lucene index update *is* visible within the transaction *if* the test content is
            // ### created within the same transaction!
            //
            tx.success();
        } finally {
            tx.finish();
        }
        // re-execute query
        topics = getTestTopics(t0);
        assertEquals(2, topics.size());      // we still have 2 topics
    }

    @Test
    public void retypeAssociationAndTraverse() {
        DMXTransaction tx = dmx.beginTx();
        Topic t0;
        List<RelatedAssociation> assocs;
        try {
            setupTestAssociations();
            //
            t0 = dmx.getTopicByUri("dmx.test.t0");
            //
            // execute query
            assocs = getTestAssociations(t0);
            assertEquals(3, assocs.size());  // we have 3 associations
            //
            // retype the first association
            Association assoc = assocs.get(0);
            assertEquals("dmx.core.association", assoc.getTypeUri());
            assoc.setTypeUri("dmx.core.composition");
            assertEquals("dmx.core.composition", assoc.getTypeUri());
            assoc = dmx.getAssociation(assoc.getId());
            assertEquals("dmx.core.composition", assoc.getTypeUri());
            //
            // re-execute query
            assocs = getTestAssociations(t0);
            assertEquals(2, assocs.size());  // now we have 2 associations
            // ### Note: the Lucene index update *is* visible within the transaction *if* the test content is
            // ### created within the same transaction!
            //
            tx.success();
        } finally {
            tx.finish();
        }
        // re-execute query
        assocs = getTestAssociations(t0);
        assertEquals(2, assocs.size());      // we still have 2 associations
    }

    @Test
    public void retypeTopicAndTraverseInstantiations() {
        DMXTransaction tx = dmx.beginTx();
        Topic type;
        List<RelatedTopic> topics;
        try {
            type = dmx.getTopicByUri("dmx.core.data_type");
            topics = getTopicInstancesByTraversal(type);
            assertEquals(5, topics.size());
            //
            // retype topic
            Topic topic = topics.get(0);
            assertEquals("dmx.core.data_type", topic.getTypeUri());
            topic.setTypeUri("dmx.core.index_mode");
            assertEquals("dmx.core.index_mode", topic.getTypeUri());
            topic = dmx.getTopic(topic.getId());
            assertEquals("dmx.core.index_mode", topic.getTypeUri());
            //
            // re-execute query
            topics = getTopicInstancesByTraversal(type);
            assertEquals(4, topics.size());
            // ### Note: in contrast to the above 4 tests this time the Lucene index update *is* visible
            // ### within the transaction! This suggests the following hypothesis:
            // ###     index.remove(entity) operation *is* visible within the transaction
            // ###     index.remove(entity, key) operation is *not* visible within the transaction
            // ### For the moment this seems to be a Neo4j oddity. Needs to be confirmed.
            //
            // ### Update: meanwhile that hypothesis is falsified.
            // ### Actually the latter 3 test are in contrast to the former 2 ones.
            // ### One possible difference is whether the test content is created in the same transaction or not.
            //
            tx.success();
        } finally {
            tx.finish();
        }
        // re-execute query
        topics = getTopicInstancesByTraversal(type);
        assertEquals(4, topics.size());
        // ### Note: the Lucene index update was already visible within the transaction!
    }

    // ---

    @Test
    public void updateAggregationOne() {
        DMXTransaction tx = dmx.beginTx();
        TopicImpl comp1;    // Note: has() is internal API, so we need a TopicImpl here
        Topic item1, item2;
        try {
            // 1) define composite type
            // child types
            dmx.createTopicType(mf.newTopicTypeModel("dmx.test.name", "Name", "dmx.core.text"));
            dmx.createTopicType(mf.newTopicTypeModel("dmx.test.item", "Item", "dmx.core.text"));
            // parent type
            dmx.createTopicType(mf.newTopicTypeModel("dmx.test.composite", "Composite", "dmx.core.composite")
                .addAssocDef(mf.newAssociationDefinitionModel(
                    "dmx.test.composite", "dmx.test.name", "dmx.core.one"
                ))
                .addAssocDef(mf.newAssociationDefinitionModel(
                    "dmx.test.composite", "dmx.test.item", "dmx.core.one"
                ))
            );
            // 2) create example child instances
            item1 = dmx.createTopic(mf.newTopicModel("dmx.test.item", new SimpleValue("Item 1")));
            item2 = dmx.createTopic(mf.newTopicModel("dmx.test.item", new SimpleValue("Item 2")));
            // 3) create composite instance
            comp1 = dmx.createTopic(mf.newTopicModel("dmx.test.composite", mf.newChildTopicsModel()
                .put("dmx.test.name", "Composite 1")
                // ### .putRef("dmx.test.item", item1.getId())
            ));
            tx.success();
        } finally {
            tx.finish();
        }
        // check memory
        assertEquals("Composite 1", comp1.getChildTopics().getString("dmx.test.name"));
        assertFalse(                comp1.getChildTopics().has("dmx.test.item"));
        comp1.loadChildTopics();
        assertFalse(                comp1.getChildTopics().has("dmx.test.item"));
        assertEquals(2, dmx.getTopicsByType("dmx.test.item").size());
        //
        // update and check again
        tx = dmx.beginTx();
        try {
            comp1.update(mf.newTopicModel(comp1.getId(), mf.newChildTopicsModel()
                .putRef("dmx.test.item", item2.getId())
            ));
            tx.success();
        } finally {
            tx.finish();
        }
        //
        assertEquals("Composite 1", comp1.getChildTopics().getString("dmx.test.name"));
        assertTrue(                 comp1.getChildTopics().has("dmx.test.item"));
        assertEquals("Item 2",      comp1.getChildTopics().getString("dmx.test.item"));
        assertEquals(item2.getId(), comp1.getChildTopics().getTopic("dmx.test.item").getId());
        assertEquals(2, dmx.getTopicsByType("dmx.test.item").size());
        //
        // update and check again
        tx = dmx.beginTx();
        try {
            comp1.update(mf.newTopicModel(comp1.getId(), mf.newChildTopicsModel()
                .putRef("dmx.test.item", item1.getId())
            ));
            tx.success();
        } finally {
            tx.finish();
        }
        //
        assertEquals("Composite 1", comp1.getChildTopics().getString("dmx.test.name"));
        assertTrue(                 comp1.getChildTopics().has("dmx.test.item"));
        assertEquals("Item 1",      comp1.getChildTopics().getString("dmx.test.item"));
        assertEquals(item1.getId(), comp1.getChildTopics().getTopic("dmx.test.item").getId());
        assertEquals(2, dmx.getTopicsByType("dmx.test.item").size());
    }

    @Test
    public void updateAggregationOneFacet() {
        DMXTransaction tx = dmx.beginTx();
        TopicImpl name;     // Note: has() is internal API, so we need a TopicImpl here
        Topic item1, item2;
        try {
            // 1) define facet
            dmx.createTopicType(mf.newTopicTypeModel("dmx.test.item", "Item", "dmx.core.text"));
            dmx.createTopicType(mf.newTopicTypeModel("dmx.test.item_facet", "Item Facet", "dmx.core.composite")
                .addAssocDef(mf.newAssociationDefinitionModel(
                    "dmx.test.item_facet", "dmx.test.item", "dmx.core.one"
                ))
            );
            // 2) create example facet values
            item1 = dmx.createTopic(mf.newTopicModel("dmx.test.item", new SimpleValue("Item 1")));
            item2 = dmx.createTopic(mf.newTopicModel("dmx.test.item", new SimpleValue("Item 2")));
            // 3) define simple type + instance
            dmx.createTopicType(mf.newTopicTypeModel("dmx.test.name", "Name", "dmx.core.text"));
            name = dmx.createTopic(mf.newTopicModel("dmx.test.name", new SimpleValue("Name 1")));
            //
            tx.success();
        } finally {
            tx.finish();
        }
        //
        AssociationDefinition assocDef = dmx.getTopicType("dmx.test.item_facet").getAssocDef("dmx.test.item");
        //
        // update facet
        tx = dmx.beginTx();
        try {
            name.updateChildTopics(
                mf.newChildTopicsModel().putRef("dmx.test.item", item1.getId()),
                assocDef
            );
            tx.success();
        } finally {
            tx.finish();
        }
        //
        assertTrue(                 name.getChildTopics().has("dmx.test.item"));
        Topic facetValue = (Topic)  name.getChildTopics().get("dmx.test.item");
        assertEquals("Item 1",      facetValue.getSimpleValue().toString());
        assertEquals(item1.getId(), facetValue.getId());
        assertEquals(2, dmx.getTopicsByType("dmx.test.item").size());
        //
        // update facet again
        tx = dmx.beginTx();
        try {
            name.updateChildTopics(
                mf.newChildTopicsModel().putRef("dmx.test.item", item2.getId()),
                assocDef
            );
            tx.success();
        } finally {
            tx.finish();
        }
        //
        assertTrue(                 name.getChildTopics().has("dmx.test.item"));
        facetValue = (Topic)        name.getChildTopics().get("dmx.test.item");
        assertEquals("Item 2",      facetValue.getSimpleValue().toString());
        assertEquals(item2.getId(), facetValue.getId());
        assertEquals(2, dmx.getTopicsByType("dmx.test.item").size());
    }

    // ---

    @Test
    public void createManyChildRefViaModel() {
        DMXTransaction tx = dmx.beginTx();
        Topic parent1, child1;
        try {
            // 1) define composite type
            // child type
            dmx.createTopicType(mf.newTopicTypeModel("dmx.test.child", "Child", "dmx.core.text"));
            // parent type
            dmx.createTopicType(mf.newTopicTypeModel("dmx.test.parent", "Parent", "dmx.core.composite")
                .addAssocDef(mf.newAssociationDefinitionModel(
                    "dmx.test.parent", "dmx.test.child", "dmx.core.many"
                ))
            );
            // 2) create example child instance
            child1 = dmx.createTopic(mf.newTopicModel("dmx.test.child", new SimpleValue("Child 1")));
            // 3) create composite instance
            // Note: addRef() must be used (instead of putRef()) as child is defined as "many".
            parent1 = dmx.createTopic(mf.newTopicModel("dmx.test.parent", mf.newChildTopicsModel()
                .addRef("dmx.test.child", child1.getId())
            ));
            tx.success();
        } finally {
            tx.finish();
        }
        List<RelatedTopic> childs = parent1.getChildTopics().getTopics("dmx.test.child");
        assertEquals(1, childs.size());
        assertEquals(child1.getId(), childs.get(0).getId());
        assertEquals("Child 1", childs.get(0).getSimpleValue().toString());
    }

    @Test
    public void createManyChildRefViaObject() {
        DMXTransaction tx = dmx.beginTx();
        Topic parent1, child1;
        try {
            // 1) define parent type (with Aggregation-Many child definition)
            dmx.createTopicType(mf.newTopicTypeModel("dmx.test.child", "Child", "dmx.core.text"));
            dmx.createTopicType(mf.newTopicTypeModel("dmx.test.parent", "Parent", "dmx.core.composite")
                .addAssocDef(mf.newAssociationDefinitionModel(
                    "dmx.test.parent", "dmx.test.child", "dmx.core.many"
                ))
            );
            // 2) create child instance
            child1 = dmx.createTopic(mf.newTopicModel("dmx.test.child", new SimpleValue("Child 1")));
            // 3) create composite instance
            parent1 = dmx.createTopic(mf.newTopicModel("dmx.test.parent"));
            parent1.getChildTopics().addRef("dmx.test.child", child1.getId());
            tx.success();
        } finally {
            tx.finish();
        }
        List<RelatedTopic> childs = parent1.getChildTopics().getTopics("dmx.test.child");
        assertEquals(1, childs.size());
        assertEquals(child1.getId(), childs.get(0).getId());
        assertEquals("Child 1", childs.get(0).getSimpleValue().toString());
    }

    @Test
    public void createManyChildViaObject() {
        DMXTransaction tx = dmx.beginTx();
        Topic parent1;
        try {
            // 1) define composite type
            // child type
            dmx.createTopicType(mf.newTopicTypeModel("dmx.test.child", "Child", "dmx.core.text"));
            // parent type
            dmx.createTopicType(mf.newTopicTypeModel("dmx.test.parent", "Parent", "dmx.core.composite")
                .addAssocDef(mf.newAssociationDefinitionModel(
                    "dmx.test.parent", "dmx.test.child", "dmx.core.many"
                ))
            );
            // 2) create composite instance
            parent1 = dmx.createTopic(mf.newTopicModel("dmx.test.parent"));
            parent1.getChildTopics().add("dmx.test.child", "Child 1");
            tx.success();
        } finally {
            tx.finish();
        }
        List<RelatedTopic> childs = parent1.getChildTopics().getTopics("dmx.test.child");
        assertEquals(1, childs.size());
        assertEquals("Child 1", childs.get(0).getSimpleValue().toString());
    }

    // ---

    @Test
    public void createAndUpdateAggregationOne() {
        DMXTransaction tx = dmx.beginTx();
        try {
            // 1) define parent type (with Aggregation-One child definition)
            dmx.createTopicType(mf.newTopicTypeModel("dmx.test.child", "Child", "dmx.core.text"));
            dmx.createTopicType(mf.newTopicTypeModel("dmx.test.parent", "Parent", "dmx.core.composite")
                .addAssocDef(mf.newAssociationDefinitionModel(
                    "dmx.test.parent", "dmx.test.child", "dmx.core.one"
                ))
            );
            // 2) create parent instance
            Topic parent1 = dmx.createTopic(mf.newTopicModel("dmx.test.parent", mf.newChildTopicsModel()
                .put("dmx.test.child", "Child 1")
            ));
            //
            assertEquals("Child 1", parent1.getChildTopics().getTopic("dmx.test.child").getSimpleValue().toString());
            // 3) update child topics
            parent1.getChildTopics().set("dmx.test.child", "Child 2");
            //
            assertEquals("Child 2", parent1.getChildTopics().getTopic("dmx.test.child").getSimpleValue().toString());
            //
            tx.success();
        } finally {
            tx.finish();
        }
    }

    // ---

    @Test
    public void createCompositionWithChildRef() {
        DMXTransaction tx = dmx.beginTx();
        try {
            // 1) define composite type
            dmx.createTopicType(mf.newTopicTypeModel("dmx.test.child", "Child", "dmx.core.text"));
            dmx.createTopicType(mf.newTopicTypeModel("dmx.test.parent", "Parent", "dmx.core.composite")
                .addAssocDef(mf.newAssociationDefinitionModel(
                    "dmx.test.parent", "dmx.test.child", "dmx.core.one"
                ))
            );
            // 2) create child instance
            Topic child1 = dmx.createTopic(mf.newTopicModel("dmx.test.child", new SimpleValue("Child 1")));
            // 3) create parent instance
            Topic parent1 = dmx.createTopic(mf.newTopicModel("dmx.test.parent", mf.newChildTopicsModel()
                .putRef("dmx.test.child", child1.getId())
            ));
            //
            assertEquals("Child 1", parent1.getChildTopics().getTopic("dmx.test.child").getSimpleValue().toString());
            //
            tx.success();
        } finally {
            tx.finish();
        }
    }

    @Test
    public void createAggregationWithChildRef() {
        DMXTransaction tx = dmx.beginTx();
        try {
            // 1) define composite type
            dmx.createTopicType(mf.newTopicTypeModel("dmx.test.child", "Child", "dmx.core.text"));
            dmx.createTopicType(mf.newTopicTypeModel("dmx.test.parent", "Parent", "dmx.core.composite")
                .addAssocDef(mf.newAssociationDefinitionModel(
                    "dmx.test.parent", "dmx.test.child", "dmx.core.one"
                ))
            );
            // 2) create child instance
            Topic child1 = dmx.createTopic(mf.newTopicModel("dmx.test.child", new SimpleValue("Child 1")));
            // 3) create parent instance
            Topic parent1 = dmx.createTopic(mf.newTopicModel("dmx.test.parent", mf.newChildTopicsModel()
                .putRef("dmx.test.child", child1.getId())
            ));
            //
            assertEquals("Child 1", parent1.getChildTopics().getTopic("dmx.test.child").getSimpleValue().toString());
            //
            tx.success();
        } finally {
            tx.finish();
        }
    }

    // ---

    @Test
    public void deleteTopic() {
        DMXTransaction tx = dmx.beginTx();
        try {
            dmx.createTopic(mf.newTopicModel("dmx.test.t0", "dmx.core.plugin"));
            //
            Topic t0 = dmx.getTopicByUri("dmx.test.t0");
            assertNotNull(t0);
            //
            t0.delete();
            t0 = dmx.getTopicByUri("dmx.test.t0");
            assertNull(t0);
            //
            tx.success();
        } finally {
            tx.finish();
        }
    }

    // ---

    @Test
    public void coreACAssignTopicToWorkspace() {
        DMXTransaction tx = dmx.beginTx();
        try {
            Topic t1 = dmx.createTopic(mf.newTopicModel("dmx.core.plugin"));
            Topic ws = dmx.createTopic(mf.newTopicModel("dmx.core.plugin"));
            //
            dmx.getAccessControl().assignToWorkspace(t1, ws.getId());
            //
            long wsId = (Long) t1.getProperty("dmx.workspaces.workspace_id");
            assertEquals(ws.getId(), wsId);
            //
            tx.success();
        } finally {
            tx.finish();
        }
    }

    @Test
    public void coreACAssignAssociationToWorkspace() {
        DMXTransaction tx = dmx.beginTx();
        try {
            Topic t1 = dmx.createTopic(mf.newTopicModel("dmx.core.plugin"));
            Topic t2 = dmx.createTopic(mf.newTopicModel("dmx.core.plugin"));
            Topic ws = dmx.createTopic(mf.newTopicModel("dmx.core.plugin"));
            Association assoc = createAssociation(t1, t2);
            //
            dmx.getAccessControl().assignToWorkspace(assoc, ws.getId());
            //
            long wsId = (Long) assoc.getProperty("dmx.workspaces.workspace_id");
            assertEquals(ws.getId(), wsId);
            //
            tx.success();
        } finally {
            tx.finish();
        }
    }

    // ------------------------------------------------------------------------------------------------- Private Methods

    private List<Topic> getTopicInstances(String topicTypeUri) {
        return dmx.getTopicsByValue("typeUri", new SimpleValue(topicTypeUri));
    }

    private List<RelatedTopic> getTopicInstancesByTraversal(Topic type) {
        return type.getRelatedTopics("dmx.core.instantiation",
            "dmx.core.type", "dmx.core.instance", type.getUri());
    }

    private List<RelatedAssociation> getAssociationInstancesByTraversal(String assocTypeUri) {
        return dmx.getTopicByUri(assocTypeUri).getRelatedAssociations("dmx.core.instantiation",
            "dmx.core.type", "dmx.core.instance", assocTypeUri);
    }

    private List<RelatedTopic> getChildTypes(Topic type) {
        return type.getRelatedTopics("dmx.core.composition_def", "dmx.core.parent_type", "dmx.core.child_type",
            "dmx.core.topic_type"
        );
    }

    // ---

    private void setupTestTopics() {
        Topic t0 = dmx.createTopic(mf.newTopicModel("dmx.test.t0", "dmx.core.plugin"));
        Topic t1 = dmx.createTopic(mf.newTopicModel("dmx.core.plugin"));
        Topic t2 = dmx.createTopic(mf.newTopicModel("dmx.core.plugin"));
        Topic t3 = dmx.createTopic(mf.newTopicModel("dmx.core.plugin"));
        createAssociation(t0, t1);
        createAssociation(t0, t2);
        createAssociation(t0, t3);
    }

    private void setupTestAssociations() {
        Topic t0 = dmx.createTopic(mf.newTopicModel("dmx.test.t0", "dmx.core.plugin"));
        Topic t1 = dmx.createTopic(mf.newTopicModel("dmx.core.plugin"));
        Topic t2 = dmx.createTopic(mf.newTopicModel("dmx.core.plugin"));
        Topic t3 = dmx.createTopic(mf.newTopicModel("dmx.core.plugin"));
        Topic t4 = dmx.createTopic(mf.newTopicModel("dmx.core.plugin"));
        Association a1 = createAssociation(t1, t2);
        Association a2 = createAssociation(t2, t3);
        Association a3 = createAssociation(t3, t4);
        createAssociation(t0, a1);
        createAssociation(t0, a2);
        createAssociation(t0, a3);
    }

    // ---

    private Association createAssociation(Topic topic1, Topic topic2) {
        return dmx.createAssociation(mf.newAssociationModel("dmx.core.association",
            mf.newTopicRoleModel(topic1.getId(), "dmx.core.default"),
            mf.newTopicRoleModel(topic2.getId(), "dmx.core.default")
        ));
    }

    private Association createAssociation(Topic topic, Association assoc) {
        return dmx.createAssociation(mf.newAssociationModel("dmx.core.association",
            mf.newTopicRoleModel(topic.getId(), "dmx.core.default"),
            mf.newAssociationRoleModel(assoc.getId(), "dmx.core.default")
        ));
    }

    // ---

    private List<RelatedTopic> getTestTopics(Topic topic) {
        return topic.getRelatedTopics("dmx.core.association",
            "dmx.core.default", "dmx.core.default", "dmx.core.plugin");
    }

    private List<RelatedAssociation> getTestAssociations(Topic topic) {
        return topic.getRelatedAssociations("dmx.core.association",
            "dmx.core.default", "dmx.core.default", "dmx.core.association");
    }
}
