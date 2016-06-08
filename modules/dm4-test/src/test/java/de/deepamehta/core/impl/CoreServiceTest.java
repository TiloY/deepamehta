package de.deepamehta.core.impl;

import de.deepamehta.core.Association;
import de.deepamehta.core.AssociationDefinition;
import de.deepamehta.core.ChildTopics;
import de.deepamehta.core.DeepaMehtaType;
import de.deepamehta.core.RelatedAssociation;
import de.deepamehta.core.RelatedTopic;
import de.deepamehta.core.Topic;
import de.deepamehta.core.TopicType;
import de.deepamehta.core.model.ChildTopicsModel;
import de.deepamehta.core.model.SimpleValue;
import de.deepamehta.core.storage.spi.DeepaMehtaTransaction;

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
            .put("dm4.core.name", "Karl Blum")
            .put("dm4.contacts.home_address", mf.newChildTopicsModel()
                .put("dm4.contacts.postal_code", 13206)
                .put("dm4.contacts.city", "Berlin"))
            .put("dm4.contacts.office_address", mf.newChildTopicsModel()
                .put("dm4.contacts.postal_code", 14345)
                .put("dm4.contacts.city", "Berlin"));
        //
        assertEquals("Karl Blum", person.getString("dm4.core.name"));
        //
        ChildTopicsModel address = person.getChildTopicsModel("dm4.contacts.home_address");
        assertEquals("Berlin", address.getString("dm4.contacts.city"));
        //
        Object code = address.getObject("dm4.contacts.postal_code");
        assertSame(Integer.class, code.getClass());
        assertEquals(13206, code);  // autoboxing
    }

    // ---

    @Test
    public void typeDefinition() throws Exception {
        try (DeepaMehtaTransaction tx = dm4.beginTx()) {
            TopicType topicType = dm4.getTopicType("dm4.core.plugin");
            assertEquals("dm4.core.plugin",     topicType.getUri());
            assertEquals("dm4.core.topic_type", topicType.getTypeUri());
            assertEquals("dm4.core.composite",  topicType.getDataTypeUri());
            assertEquals(3,                     topicType.getAssocDefs().size());
            AssociationDefinition assocDef =    topicType.getAssocDef("dm4.core.plugin_migration_nr");
            assertEquals("dm4.core.composition_def",     assocDef.getTypeUri());
            assertEquals("dm4.core.plugin",              assocDef.getParentTypeUri());
            assertEquals("dm4.core.plugin_migration_nr", assocDef.getChildTypeUri());
            assertEquals("dm4.core.one",                 assocDef.getParentCardinalityUri());
            assertEquals("dm4.core.one",                 assocDef.getChildCardinalityUri());
            Topic t1 = assocDef.getTopic("dm4.core.parent_type");
            Topic t2 = assocDef.getTopic("dm4.core.child_type");
            assertEquals("dm4.core.plugin",              t1.getUri());
            assertEquals("dm4.core.topic_type",          t1.getTypeUri());
            assertEquals("dm4.core.plugin_migration_nr", t2.getUri());
            assertEquals("dm4.core.topic_type",          t2.getTypeUri());
        }
    }

    @Test
    public void createWithoutComposite() throws Exception {
        try (DeepaMehtaTransaction tx = dm4.beginTx()) {
            Topic topic = dm4.createTopic(mf.newTopicModel("de.deepamehta.notes", "dm4.core.plugin",
                new SimpleValue("DeepaMehta 4 Notes")));
            //
            topic.getChildTopics().set("dm4.core.plugin_migration_nr", 23);
            //
            int nr = topic.getChildTopics().getTopic("dm4.core.plugin_migration_nr").getSimpleValue().intValue();
            assertEquals(23, nr);
            //
            topic.getChildTopics().set("dm4.core.plugin_migration_nr", 42);
            //
            nr = topic.getChildTopics().getTopic("dm4.core.plugin_migration_nr").getSimpleValue().intValue();
            assertEquals(42, nr);
            //
            tx.success();
        }
    }

    @Test
    public void createWithComposite() throws Exception {
        try (DeepaMehtaTransaction tx = dm4.beginTx()) {
            // Note: has() is internal API, so we need a TopicImpl here
            TopicImpl topic = dm4.createTopic(mf.newTopicModel("de.deepamehta.notes", "dm4.core.plugin",
                mf.newChildTopicsModel().put("dm4.core.plugin_migration_nr", 23)
            ));
            //
            assertTrue(topic.getChildTopics().has("dm4.core.plugin_migration_nr"));
            //
            int nr = topic.getChildTopics().getTopic("dm4.core.plugin_migration_nr").getSimpleValue().intValue();
            assertEquals(23, nr);
            //
            topic.getChildTopics().set("dm4.core.plugin_migration_nr", 42);
            //
            nr = topic.getChildTopics().getTopic("dm4.core.plugin_migration_nr").getSimpleValue().intValue();
            assertEquals(42, nr);
            //
            tx.success();
        }
    }

    // ---

    @Test
    public void onDemandChildTopicLoading() throws Exception {
        try (DeepaMehtaTransaction tx = dm4.beginTx()) {
            dm4.createTopic(mf.newTopicModel("de.deepamehta.notes", "dm4.core.plugin",
                mf.newChildTopicsModel().put("dm4.core.plugin_migration_nr", 23)
            ));
            // Note: has() is internal API, so we need a TopicImpl here
            TopicImpl topic = dm4.getTopicByUri("de.deepamehta.notes");
            ChildTopicsImpl comp = topic.getChildTopics();
            assertFalse(comp.has("dm4.core.plugin_migration_nr"));              // child topic is not yet loaded
            //
            Topic childTopic = comp.getTopic("dm4.core.plugin_migration_nr");
            assertEquals(23, childTopic.getSimpleValue().intValue());           // child topic is loaded on-demand
            assertTrue(comp.has("dm4.core.plugin_migration_nr"));               // child topic is now loaded
            //
            tx.success();
        }
    }

    @Test
    public void onDemandChildTopicLoadingWithConvenienceAccessor() throws Exception {
        try (DeepaMehtaTransaction tx = dm4.beginTx()) {
            dm4.createTopic(mf.newTopicModel("de.deepamehta.notes", "dm4.core.plugin",
                mf.newChildTopicsModel().put("dm4.core.plugin_migration_nr", 23)
            ));
            // Note: has() is internal API, so we need a TopicImpl here
            TopicImpl topic = dm4.getTopicByUri("de.deepamehta.notes");
            ChildTopicsImpl comp = topic.getChildTopics();
            assertFalse(comp.has("dm4.core.plugin_migration_nr"));              // child topic is not yet loaded
            //
            assertEquals(23, comp.getInt("dm4.core.plugin_migration_nr"));      // child topic is loaded on-demand
            assertTrue(comp.has("dm4.core.plugin_migration_nr"));               // child topic is now loaded
            //
            tx.success();
        }
    }

    // ---

    @Test
    public void changeLabelWithSetChildTopics() throws Exception {
        try (DeepaMehtaTransaction tx = dm4.beginTx()) {
            Topic topic = dm4.createTopic(mf.newTopicModel("dm4.core.plugin"));
            assertEquals("", topic.getSimpleValue().toString());
            //
            topic.setChildTopics(mf.newChildTopicsModel().put("dm4.core.plugin_name", "My Plugin"));
            assertEquals("My Plugin", topic.getChildTopics().getString("dm4.core.plugin_name"));
            assertEquals("My Plugin", topic.getSimpleValue().toString());
            //
            Topic fetchedTopic = dm4.getTopic(topic.getId());
            assertEquals("My Plugin", fetchedTopic.getSimpleValue().toString());
            //
            tx.success();
        }
    }

    @Test
    public void changeLabelWithChildTopicsSet() throws Exception {
        try (DeepaMehtaTransaction tx = dm4.beginTx()) {
            Topic topic = dm4.createTopic(mf.newTopicModel("dm4.core.plugin"));
            assertEquals("", topic.getSimpleValue().toString());
            //
            topic.getChildTopics().set("dm4.core.plugin_name", "My Plugin");
            assertEquals("My Plugin", topic.getChildTopics().getString("dm4.core.plugin_name"));
            assertEquals("My Plugin", topic.getSimpleValue().toString());
            //
            Topic fetchedTopic = dm4.getTopic(topic.getId());
            assertEquals("My Plugin", fetchedTopic.getSimpleValue().toString());
            //
            tx.success();
        }
    }

    // ---

    @Test
    public void setLabelChildWhileChildsAreNotLoaded() throws Exception {
        try (DeepaMehtaTransaction tx = dm4.beginTx()) {
            Topic topic = dm4.createTopic(mf.newTopicModel("dm4.core.plugin", mf.newChildTopicsModel()
                .put("dm4.core.plugin_name", "My Plugin")
                .put("dm4.core.plugin_symbolic_name", "dm4.test.my_plugin")
                .put("dm4.core.plugin_migration_nr", 1)
            ));
            assertEquals("My Plugin", topic.getSimpleValue().toString());
            //
            topic = dm4.getTopic(topic.getId());                            // Note: the childs are not loaded
            assertEquals("My Plugin", topic.getSimpleValue().toString());   // the label is intact
            topic.getChildTopics().set("dm4.core.plugin_name", "HuHu");     // setting child used for labeling
            assertEquals("HuHu", topic.getSimpleValue().toString());        // the label is recalculated
            //
            tx.success();
        }
    }

    @Test
    public void setNonlabelChildWhileChildsAreNotLoaded() throws Exception {
        try (DeepaMehtaTransaction tx = dm4.beginTx()) {
            Topic topic = dm4.createTopic(mf.newTopicModel("dm4.core.plugin", mf.newChildTopicsModel()
                .put("dm4.core.plugin_name", "My Plugin")
                .put("dm4.core.plugin_symbolic_name", "dm4.test.my_plugin")
                .put("dm4.core.plugin_migration_nr", 1)
            ));
            assertEquals("My Plugin", topic.getSimpleValue().toString());
            //
            topic = dm4.getTopic(topic.getId());                            // Note: the childs are not loaded
            assertEquals("My Plugin", topic.getSimpleValue().toString());   // the label is intact
            topic.getChildTopics().set("dm4.core.plugin_migration_nr", 3);  // setting child NOT used for labeling
            assertEquals("My Plugin", topic.getSimpleValue().toString());   // the label is still intact
            //
            tx.success();
        }
    }

    // ---

    @Test
    public void uriUniquenessCreateTopic() {
        try (DeepaMehtaTransaction tx = dm4.beginTx()) {
            Topic topic = dm4.createTopic(mf.newTopicModel("dm4.my.uri", "dm4.core.plugin"));
            assertEquals("dm4.my.uri", topic.getUri());
            //
            dm4.createTopic(mf.newTopicModel("dm4.my.uri", "dm4.core.plugin"));
            fail("\"URI not unique\" exception not thrown");
            //
            tx.success();
        } catch (Exception e) {
            Throwable cause = e.getCause();
            assertNotNull(cause);
            assertEquals("URI \"dm4.my.uri\" is not unique", cause.getMessage());
        }
    }

    @Test
    public void uriUniquenessSetUri() {
        try (DeepaMehtaTransaction tx = dm4.beginTx()) {
            Topic topic1 = dm4.createTopic(mf.newTopicModel("dm4.my.uri", "dm4.core.plugin"));
            assertEquals("dm4.my.uri", topic1.getUri());
            //
            Topic topic2 = dm4.createTopic(mf.newTopicModel("dm4.core.plugin"));
            assertEquals("", topic2.getUri());
            //
            topic2.setUri("dm4.my.uri");
            fail("\"URI not unique\" exception not thrown");
            //
            tx.success();
        } catch (Exception e) {
            assertEquals("URI \"dm4.my.uri\" is not unique", e.getMessage());
        }
    }

    @Test
    public void uriUniquenessUpdate() {
        long topic2Id = -1;
        try (DeepaMehtaTransaction tx = dm4.beginTx()) {
            Topic topic1 = dm4.createTopic(mf.newTopicModel("dm4.my.uri", "dm4.core.plugin"));
            assertEquals("dm4.my.uri", topic1.getUri());
            //
            Topic topic2 = dm4.createTopic(mf.newTopicModel("dm4.core.plugin"));
            topic2Id = topic2.getId();
            assertEquals("", topic2.getUri());
            //
            topic2.update(mf.newTopicModel("dm4.my.uri", "dm4.core.plugin"));
            fail("\"URI not unique\" exception not thrown");
            //
            tx.success();
        } catch (Exception e) {
            // logger.log(Level.WARNING, "Exception thrown:", e);
            assertEquals("Updating topic " + topic2Id + " failed (typeUri=\"dm4.core.plugin\")", e.getMessage());
            assertEquals("URI \"dm4.my.uri\" is not unique", e.getCause().getMessage());
        }
    }

    // ---

    @Test
    public void assocDefSequence() throws Exception {
        try (DeepaMehtaTransaction tx = dm4.beginTx()) {
            DeepaMehtaType type = dm4.getTopicType("dm4.core.plugin");
            //
            // find assoc def 1/3
            RelatedAssociation assocDef = type.getRelatedAssociation("dm4.core.aggregation", "dm4.core.type",
                "dm4.core.sequence_start", null);   // othersAssocTypeUri=null
            logger.info("### assoc def ID 1/3 = " + assocDef.getId() +
                ", relating assoc ID = " + assocDef.getRelatingAssociation().getId());
            assertNotNull(assocDef);
            //
            // find assoc def 2/3
            assocDef = assocDef.getRelatedAssociation("dm4.core.sequence", "dm4.core.predecessor", "dm4.core.successor",
                null);                              // othersAssocTypeUri=null
            logger.info("### assoc def ID 2/3 = " + assocDef.getId() +
                ", relating assoc ID = " + assocDef.getRelatingAssociation().getId());
            assertNotNull(assocDef);
            //
            // find assoc def 3/3
            assocDef = assocDef.getRelatedAssociation("dm4.core.sequence", "dm4.core.predecessor", "dm4.core.successor",
                null);                              // othersAssocTypeUri=null
            logger.info("### assoc def ID 3/3 = " + assocDef.getId() +
                ", relating assoc ID = " + assocDef.getRelatingAssociation().getId());
            assertNotNull(assocDef);
            //
            // there is no other
            assocDef = assocDef.getRelatedAssociation("dm4.core.sequence", "dm4.core.predecessor", "dm4.core.successor",
                null);                              // othersAssocTypeUri=null
            assertNull(assocDef);
        }
    }

    // ---

    @Test
    public void insertAssocDefAtPos0() throws Exception {
        try (DeepaMehtaTransaction tx = dm4.beginTx()) {
            DeepaMehtaType type = dm4.getTopicType("dm4.core.plugin");
            //
            dm4.createTopicType(mf.newTopicTypeModel("dm4.test.name", "Name", "dm4.core.text"));
            // insert assoc def at pos 0
            type.addAssocDefBefore(mf.newAssociationDefinitionModel("dm4.core.composition_def",
                "dm4.core.plugin", "dm4.test.name", "dm4.core.one", "dm4.core.one"), "dm4.core.plugin_name");
            //
            Collection<AssociationDefinition> assocDefs = type.getAssocDefs();
            assertSame(4, assocDefs.size());
            //
            Iterator<AssociationDefinition> i = assocDefs.iterator();
            assertEquals("dm4.test.name", i.next().getChildTypeUri());          // new assoc def is at pos 0
            assertEquals("dm4.core.plugin_name", i.next().getChildTypeUri());   // former pos 0 is now at pos 1
            //
            tx.success();
        }
    }

    @Test
    public void insertAssocDefAtPos1() throws Exception {
        try (DeepaMehtaTransaction tx = dm4.beginTx()) {
            DeepaMehtaType type = dm4.getTopicType("dm4.core.plugin");
            //
            dm4.createTopicType(mf.newTopicTypeModel("dm4.test.name", "Name", "dm4.core.text"));
            // insert assoc def at pos 1
            type.addAssocDefBefore(mf.newAssociationDefinitionModel("dm4.core.composition_def",
                "dm4.core.plugin", "dm4.test.name", "dm4.core.one", "dm4.core.one"), "dm4.core.plugin_symbolic_name");
            //
            Collection<AssociationDefinition> assocDefs = type.getAssocDefs();
            assertSame(4, assocDefs.size());
            //
            Iterator<AssociationDefinition> i = assocDefs.iterator();
            assertEquals("dm4.core.plugin_name", i.next().getChildTypeUri());           // pos 0 is unchanged
            assertEquals("dm4.test.name", i.next().getChildTypeUri());                  // new assoc def is at pos 1
            assertEquals("dm4.core.plugin_symbolic_name", i.next().getChildTypeUri());  // former pos 1 is now at pos 2
            //
            tx.success();
        }
    }

    // ---

    @Test
    public void getTopicsByType() throws Exception {
        try (DeepaMehtaTransaction tx = dm4.beginTx()) {
            Topic type = dm4.getTopicByUri("dm4.core.data_type");
            List<RelatedTopic> topics1 = getTopicInstancesByTraversal(type);
            assertEquals(5, topics1.size());
            List<Topic> topics2 = getTopicInstances("dm4.core.data_type");
            assertEquals(5, topics2.size());
        }
    }

    @Test
    public void getAssociationsByType() throws Exception {
        try (DeepaMehtaTransaction tx = dm4.beginTx()) {
            List<RelatedAssociation> assocs;
            //
            assocs = getAssociationInstancesByTraversal("dm4.core.instantiation");
            assertEquals(49, assocs.size());
            //
            assocs = getAssociationInstancesByTraversal("dm4.core.composition_def");
            assertEquals(5, assocs.size());
            //
            assocs = getAssociationInstancesByTraversal("dm4.core.aggregation_def");
            assertEquals(2, assocs.size());
        }
    }

    // ---

    @Test
    public void retypeAssociation() throws Exception {
        Topic type;
        List<RelatedTopic> childTypes;
        try (DeepaMehtaTransaction tx = dm4.beginTx()) {
            type = dm4.getTopicByUri("dm4.core.plugin");
            childTypes = getChildTypes(type);
            assertEquals(3, childTypes.size());
            //
            // retype assoc
            Association assoc = childTypes.get(0).getRelatingAssociation();
            assertEquals("dm4.core.composition_def", assoc.getTypeUri());
            assoc.setTypeUri("dm4.core.association");
            assertEquals("dm4.core.association", assoc.getTypeUri());
            assoc = dm4.getAssociation(assoc.getId());
            assertEquals("dm4.core.association", assoc.getTypeUri());
            //
            // re-execute query
            childTypes = getChildTypes(type);
            assertEquals(3, childTypes.size());
            // ### Note: the Lucene index update is not visible within the transaction!
            // ### That's contradictory to the Neo4j documentation!
            // ### It states that QueryContext's tradeCorrectnessForSpeed behavior is off by default.
            //
            tx.success();
        }
        // re-execute query
        try (DeepaMehtaTransaction tx = dm4.beginTx()) {
            childTypes = getChildTypes(type);
            assertEquals(2, childTypes.size());
            // ### Note: the Lucene index update is only visible once the transaction is committed!
            // ### That's contradictory to the Neo4j documentation!
            // ### It states that QueryContext's tradeCorrectnessForSpeed behavior is off by default.
        }
    }

    @Test
    public void retypeAssociationRoles() throws Exception {
        Topic type;
        List<RelatedTopic> childTypes;
        try (DeepaMehtaTransaction tx = dm4.beginTx()) {
            type = dm4.getTopicByUri("dm4.core.plugin");
            childTypes = getChildTypes(type);
            assertEquals(3, childTypes.size());
            //
            // retype assoc roles
            Association assoc = childTypes.get(0).getRelatingAssociation();
            assoc.getRole1().setRoleTypeUri("dm4.core.default");
            assoc.getRole2().setRoleTypeUri("dm4.core.default");
            //
            // re-execute query
            childTypes = getChildTypes(type);
            assertEquals(3, childTypes.size());
            // ### Note: the Lucene index update is not visible within the transaction!
            // ### That's contradictory to the Neo4j documentation!
            // ### It states that QueryContext's tradeCorrectnessForSpeed behavior is off by default.
            //
            tx.success();
        }
        // re-execute query
        try (DeepaMehtaTransaction tx = dm4.beginTx()) {
            childTypes = getChildTypes(type);
            assertEquals(2, childTypes.size());
            // ### Note: the Lucene index update is only visible once the transaction is committed!
            // ### That's contradictory to the Neo4j documentation!
            // ### It states that QueryContext's tradeCorrectnessForSpeed behavior is off by default.
        }
    }

    @Test
    public void retypeTopicAndTraverse() throws Exception {
        Topic t0;
        List<RelatedTopic> topics;
        try (DeepaMehtaTransaction tx = dm4.beginTx()) {
            setupTestTopics();
            //
            t0 = dm4.getTopicByUri("dm4.test.t0");
            //
            // execute query
            topics = getTestTopics(t0);
            assertEquals(3, topics.size());  // we have 3 topics
            //
            // retype the first topic
            Topic topic = topics.get(0);
            assertEquals("dm4.core.plugin", topic.getTypeUri());
            topic.setTypeUri("dm4.core.data_type");
            assertEquals("dm4.core.data_type", topic.getTypeUri());
            topic = dm4.getTopic(topic.getId());
            assertEquals("dm4.core.data_type", topic.getTypeUri());
            //
            // re-execute query
            topics = getTestTopics(t0);
            assertEquals(2, topics.size());  // now we have 2 topics
            // ### Note: the Lucene index update *is* visible within the transaction *if* the test content is
            // ### created within the same transaction!
            //
            tx.success();
        }
        // re-execute query
        try (DeepaMehtaTransaction tx = dm4.beginTx()) {
            topics = getTestTopics(t0);
            assertEquals(2, topics.size());      // we still have 2 topics
        }
    }

    @Test
    public void retypeAssociationAndTraverse() throws Exception {
        Topic t0;
        List<RelatedAssociation> assocs;
        try (DeepaMehtaTransaction tx = dm4.beginTx()) {
            setupTestAssociations();
            //
            t0 = dm4.getTopicByUri("dm4.test.t0");
            //
            // execute query
            assocs = getTestAssociations(t0);
            assertEquals(3, assocs.size());  // we have 3 associations
            //
            // retype the first association
            Association assoc = assocs.get(0);
            assertEquals("dm4.core.association", assoc.getTypeUri());
            assoc.setTypeUri("dm4.core.composition");
            assertEquals("dm4.core.composition", assoc.getTypeUri());
            assoc = dm4.getAssociation(assoc.getId());
            assertEquals("dm4.core.composition", assoc.getTypeUri());
            //
            // re-execute query
            assocs = getTestAssociations(t0);
            assertEquals(2, assocs.size());  // now we have 2 associations
            // ### Note: the Lucene index update *is* visible within the transaction *if* the test content is
            // ### created within the same transaction!
            //
            tx.success();
        }
        // re-execute query
        try (DeepaMehtaTransaction tx = dm4.beginTx()) {
            assocs = getTestAssociations(t0);
            assertEquals(2, assocs.size());      // we still have 2 associations
        }
    }

    @Test
    public void retypeTopicAndTraverseInstantiations() throws Exception {
        Topic type;
        List<RelatedTopic> topics;
        try (DeepaMehtaTransaction tx = dm4.beginTx()) {
            type = dm4.getTopicByUri("dm4.core.data_type");
            topics = getTopicInstancesByTraversal(type);
            assertEquals(5, topics.size());
            //
            // retype topic
            Topic topic = topics.get(0);
            assertEquals("dm4.core.data_type", topic.getTypeUri());
            topic.setTypeUri("dm4.core.index_mode");
            assertEquals("dm4.core.index_mode", topic.getTypeUri());
            topic = dm4.getTopic(topic.getId());
            assertEquals("dm4.core.index_mode", topic.getTypeUri());
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
        }
        // re-execute query
        try (DeepaMehtaTransaction tx = dm4.beginTx()) {
            topics = getTopicInstancesByTraversal(type);
            assertEquals(4, topics.size());
            // ### Note: the Lucene index update was already visible within the transaction!
        }
    }

    // ---

    @Test
    public void updateAggregationOne() throws Exception {
        TopicImpl comp1;    // Note: has() is internal API, so we need a TopicImpl here
        Topic item1, item2;
        try (DeepaMehtaTransaction tx = dm4.beginTx()) {
            // 1) define composite type
            // child types
            dm4.createTopicType(mf.newTopicTypeModel("dm4.test.name", "Name", "dm4.core.text"));
            dm4.createTopicType(mf.newTopicTypeModel("dm4.test.item", "Item", "dm4.core.text"));
            // parent type
            dm4.createTopicType(mf.newTopicTypeModel("dm4.test.composite", "Composite", "dm4.core.composite")
                .addAssocDef(mf.newAssociationDefinitionModel("dm4.core.composition_def",
                    "dm4.test.composite", "dm4.test.name", "dm4.core.one", "dm4.core.one"
                ))
                .addAssocDef(mf.newAssociationDefinitionModel("dm4.core.aggregation_def",
                    "dm4.test.composite", "dm4.test.item", "dm4.core.many", "dm4.core.one"
                ))
            );
            // 2) create example child instances
            item1 = dm4.createTopic(mf.newTopicModel("dm4.test.item", new SimpleValue("Item 1")));
            item2 = dm4.createTopic(mf.newTopicModel("dm4.test.item", new SimpleValue("Item 2")));
            // 3) create composite instance
            comp1 = dm4.createTopic(mf.newTopicModel("dm4.test.composite", mf.newChildTopicsModel()
                .put("dm4.test.name", "Composite 1")
                // ### .putRef("dm4.test.item", item1.getId())
            ));
            tx.success();
        }
        // check memory
        try (DeepaMehtaTransaction tx = dm4.beginTx()) {
            assertEquals("Composite 1", comp1.getChildTopics().getString("dm4.test.name"));
            assertFalse(                comp1.getChildTopics().has("dm4.test.item"));
            comp1.loadChildTopics();
            assertFalse(                comp1.getChildTopics().has("dm4.test.item"));
            assertEquals(2, dm4.getTopicsByType("dm4.test.item").size());
        }
        //
        // update and check again
        try (DeepaMehtaTransaction tx = dm4.beginTx()) {
            comp1.update(mf.newTopicModel(comp1.getId(), mf.newChildTopicsModel()
                .putRef("dm4.test.item", item2.getId())
            ));
            tx.success();
        }
        //
        try (DeepaMehtaTransaction tx = dm4.beginTx()) {
            assertEquals("Composite 1", comp1.getChildTopics().getString("dm4.test.name"));
            assertTrue(                 comp1.getChildTopics().has("dm4.test.item"));
            assertEquals("Item 2",      comp1.getChildTopics().getString("dm4.test.item"));
            assertEquals(item2.getId(), comp1.getChildTopics().getTopic("dm4.test.item").getId());
            assertEquals(2, dm4.getTopicsByType("dm4.test.item").size());
        }
        //
        // update and check again
        try (DeepaMehtaTransaction tx = dm4.beginTx()) {
            comp1.update(mf.newTopicModel(comp1.getId(), mf.newChildTopicsModel()
                .putRef("dm4.test.item", item1.getId())
            ));
            tx.success();
        }
        //
        try (DeepaMehtaTransaction tx = dm4.beginTx()) {
            assertEquals("Composite 1", comp1.getChildTopics().getString("dm4.test.name"));
            assertTrue(                 comp1.getChildTopics().has("dm4.test.item"));
            assertEquals("Item 1",      comp1.getChildTopics().getString("dm4.test.item"));
            assertEquals(item1.getId(), comp1.getChildTopics().getTopic("dm4.test.item").getId());
            assertEquals(2, dm4.getTopicsByType("dm4.test.item").size());
        }
    }

    @Test
    public void updateAggregationOneFacet() throws Exception {
        TopicImpl name;     // Note: has() is internal API, so we need a TopicImpl here
        Topic item1, item2;
        try (DeepaMehtaTransaction tx = dm4.beginTx()) {
            // 1) define facet
            dm4.createTopicType(mf.newTopicTypeModel("dm4.test.item", "Item", "dm4.core.text"));
            dm4.createTopicType(mf.newTopicTypeModel("dm4.test.item_facet", "Item Facet", "dm4.core.composite")
                .addAssocDef(mf.newAssociationDefinitionModel("dm4.core.aggregation_def",
                    "dm4.test.item_facet", "dm4.test.item", "dm4.core.many", "dm4.core.one"
                ))
            );
            // 2) create example facet values
            item1 = dm4.createTopic(mf.newTopicModel("dm4.test.item", new SimpleValue("Item 1")));
            item2 = dm4.createTopic(mf.newTopicModel("dm4.test.item", new SimpleValue("Item 2")));
            // 3) define simple type + instance
            dm4.createTopicType(mf.newTopicTypeModel("dm4.test.name", "Name", "dm4.core.text"));
            name = dm4.createTopic(mf.newTopicModel("dm4.test.name", new SimpleValue("Name 1")));
            //
            tx.success();
        }
        //
        AssociationDefinition assocDef = dm4.getTopicType("dm4.test.item_facet").getAssocDef("dm4.test.item");
        //
        // update facet
        try (DeepaMehtaTransaction tx = dm4.beginTx()) {
            name.updateChildTopic(mf.newTopicReferenceModel(item1.getId()), assocDef);
            tx.success();
        }
        //
        Topic facetValue;
        try (DeepaMehtaTransaction tx = dm4.beginTx()) {
            assertTrue(                 name.getChildTopics().has("dm4.test.item"));
            facetValue =        (Topic) name.getChildTopics().get("dm4.test.item");
            assertEquals("Item 1",      facetValue.getSimpleValue().toString());
            assertEquals(item1.getId(), facetValue.getId());
            assertEquals(2, dm4.getTopicsByType("dm4.test.item").size());
        }
        //
        // update facet again
        try (DeepaMehtaTransaction tx = dm4.beginTx()) {
            name.updateChildTopic(mf.newTopicReferenceModel(item2.getId()), assocDef);
            tx.success();
        }
        //
        try (DeepaMehtaTransaction tx = dm4.beginTx()) {
            assertTrue(                 name.getChildTopics().has("dm4.test.item"));
            facetValue = (Topic)        name.getChildTopics().get("dm4.test.item");
            assertEquals("Item 2",      facetValue.getSimpleValue().toString());
            assertEquals(item2.getId(), facetValue.getId());
            assertEquals(2, dm4.getTopicsByType("dm4.test.item").size());
        }
    }

    // ---

    @Test
    public void createManyChildRefViaModel() throws Exception {
        Topic parent1, child1;
        try (DeepaMehtaTransaction tx = dm4.beginTx()) {
            // 1) define composite type
            // child type
            dm4.createTopicType(mf.newTopicTypeModel("dm4.test.child", "Child", "dm4.core.text"));
            // parent type
            dm4.createTopicType(mf.newTopicTypeModel("dm4.test.parent", "Parent", "dm4.core.composite")
                .addAssocDef(mf.newAssociationDefinitionModel("dm4.core.aggregation_def",
                    "dm4.test.parent", "dm4.test.child", "dm4.core.many", "dm4.core.many"
                ))
            );
            // 2) create example child instance
            child1 = dm4.createTopic(mf.newTopicModel("dm4.test.child", new SimpleValue("Child 1")));
            // 3) create composite instance
            // Note: addRef() must be used (instead of putRef()) as child is defined as "many".
            parent1 = dm4.createTopic(mf.newTopicModel("dm4.test.parent", mf.newChildTopicsModel()
                .addRef("dm4.test.child", child1.getId())
            ));
            tx.success();
        }
        List<RelatedTopic> childs = parent1.getChildTopics().getTopics("dm4.test.child");
        assertEquals(1, childs.size());
        assertEquals(child1.getId(), childs.get(0).getId());
        assertEquals("Child 1", childs.get(0).getSimpleValue().toString());
    }

    @Test
    public void createManyChildRefViaObject() throws Exception {
        Topic parent1, child1;
        try (DeepaMehtaTransaction tx = dm4.beginTx()) {
            // 1) define parent type (with Aggregation-Many child definition)
            dm4.createTopicType(mf.newTopicTypeModel("dm4.test.child", "Child", "dm4.core.text"));
            dm4.createTopicType(mf.newTopicTypeModel("dm4.test.parent", "Parent", "dm4.core.composite")
                .addAssocDef(mf.newAssociationDefinitionModel("dm4.core.aggregation_def",
                    "dm4.test.parent", "dm4.test.child", "dm4.core.many", "dm4.core.many"
                ))
            );
            // 2) create child instance
            child1 = dm4.createTopic(mf.newTopicModel("dm4.test.child", new SimpleValue("Child 1")));
            // 3) create composite instance
            parent1 = dm4.createTopic(mf.newTopicModel("dm4.test.parent"));
            parent1.getChildTopics().addRef("dm4.test.child", child1.getId());
            tx.success();
        }
        List<RelatedTopic> childs = parent1.getChildTopics().getTopics("dm4.test.child");
        assertEquals(1, childs.size());
        assertEquals(child1.getId(), childs.get(0).getId());
        assertEquals("Child 1", childs.get(0).getSimpleValue().toString());
    }

    @Test
    public void createManyChildViaObject() throws Exception {
        Topic parent1;
        try (DeepaMehtaTransaction tx = dm4.beginTx()) {
            // 1) define composite type
            // child type
            dm4.createTopicType(mf.newTopicTypeModel("dm4.test.child", "Child", "dm4.core.text"));
            // parent type
            dm4.createTopicType(mf.newTopicTypeModel("dm4.test.parent", "Parent", "dm4.core.composite")
                .addAssocDef(mf.newAssociationDefinitionModel("dm4.core.aggregation_def",
                    "dm4.test.parent", "dm4.test.child", "dm4.core.many", "dm4.core.many"
                ))
            );
            // 2) create composite instance
            parent1 = dm4.createTopic(mf.newTopicModel("dm4.test.parent"));
            parent1.getChildTopics().add("dm4.test.child", "Child 1");
            tx.success();
        }
        List<RelatedTopic> childs = parent1.getChildTopics().getTopics("dm4.test.child");
        assertEquals(1, childs.size());
        assertEquals("Child 1", childs.get(0).getSimpleValue().toString());
    }

    // ---

    @Test
    public void createAndUpdateAggregationOne() throws Exception {
        try (DeepaMehtaTransaction tx = dm4.beginTx()) {
            // 1) define parent type (with Aggregation-One child definition)
            dm4.createTopicType(mf.newTopicTypeModel("dm4.test.child", "Child", "dm4.core.text"));
            dm4.createTopicType(mf.newTopicTypeModel("dm4.test.parent", "Parent", "dm4.core.composite")
                .addAssocDef(mf.newAssociationDefinitionModel("dm4.core.aggregation_def",
                    "dm4.test.parent", "dm4.test.child", "dm4.core.many", "dm4.core.one"
                ))
            );
            // 2) create parent instance
            Topic parent1 = dm4.createTopic(mf.newTopicModel("dm4.test.parent", mf.newChildTopicsModel()
                .put("dm4.test.child", "Child 1")
            ));
            //
            assertEquals("Child 1", parent1.getChildTopics().getTopic("dm4.test.child").getSimpleValue().toString());
            // 3) update child topics
            parent1.getChildTopics().set("dm4.test.child", "Child 2");
            //
            assertEquals("Child 2", parent1.getChildTopics().getTopic("dm4.test.child").getSimpleValue().toString());
            //
            tx.success();
        }
    }

    // ---

    @Test
    public void createCompositionWithChildRef() throws Exception {
        try (DeepaMehtaTransaction tx = dm4.beginTx()) {
            // 1) define composite type
            dm4.createTopicType(mf.newTopicTypeModel("dm4.test.child", "Child", "dm4.core.text"));
            dm4.createTopicType(mf.newTopicTypeModel("dm4.test.parent", "Parent", "dm4.core.composite")
                .addAssocDef(mf.newAssociationDefinitionModel("dm4.core.composition_def",
                    "dm4.test.parent", "dm4.test.child", "dm4.core.one", "dm4.core.one"
                ))
            );
            // 2) create child instance
            Topic child1 = dm4.createTopic(mf.newTopicModel("dm4.test.child", new SimpleValue("Child 1")));
            // 3) create parent instance
            Topic parent1 = dm4.createTopic(mf.newTopicModel("dm4.test.parent", mf.newChildTopicsModel()
                .putRef("dm4.test.child", child1.getId())
            ));
            //
            assertEquals("Child 1", parent1.getChildTopics().getTopic("dm4.test.child").getSimpleValue().toString());
            //
            tx.success();
        }
    }

    @Test
    public void createAggregationWithChildRef() throws Exception {
        try (DeepaMehtaTransaction tx = dm4.beginTx()) {
            // 1) define composite type
            dm4.createTopicType(mf.newTopicTypeModel("dm4.test.child", "Child", "dm4.core.text"));
            dm4.createTopicType(mf.newTopicTypeModel("dm4.test.parent", "Parent", "dm4.core.composite")
                .addAssocDef(mf.newAssociationDefinitionModel("dm4.core.aggregation_def",
                    "dm4.test.parent", "dm4.test.child", "dm4.core.one", "dm4.core.one"
                ))
            );
            // 2) create child instance
            Topic child1 = dm4.createTopic(mf.newTopicModel("dm4.test.child", new SimpleValue("Child 1")));
            // 3) create parent instance
            Topic parent1 = dm4.createTopic(mf.newTopicModel("dm4.test.parent", mf.newChildTopicsModel()
                .putRef("dm4.test.child", child1.getId())
            ));
            //
            assertEquals("Child 1", parent1.getChildTopics().getTopic("dm4.test.child").getSimpleValue().toString());
            //
            tx.success();
        }
    }

    // ---

    @Test
    public void deleteTopic() throws Exception {
        try (DeepaMehtaTransaction tx = dm4.beginTx()) {
            dm4.createTopic(mf.newTopicModel("dm4.test.t0", "dm4.core.plugin"));
            //
            Topic t0 = dm4.getTopicByUri("dm4.test.t0");
            assertNotNull(t0);
            //
            t0.delete();
            t0 = dm4.getTopicByUri("dm4.test.t0");
            assertNull(t0);
            //
            tx.success();
        }
    }

    // ---

    @Test
    public void coreACAssignTopicToWorkspace() throws Exception {
        try (DeepaMehtaTransaction tx = dm4.beginTx()) {
            Topic t1 = dm4.createTopic(mf.newTopicModel("dm4.core.plugin"));
            Topic ws = dm4.createTopic(mf.newTopicModel("dm4.core.plugin"));
            //
            dm4.getAccessControl().assignToWorkspace(t1, ws.getId());
            //
            long wsId = (Long) t1.getProperty("dm4.workspaces.workspace_id");
            assertEquals(ws.getId(), wsId);
            //
            tx.success();
        }
    }

    @Test
    public void coreACAssignAssociationToWorkspace() throws Exception {
        try (DeepaMehtaTransaction tx = dm4.beginTx()) {
            Topic t1 = dm4.createTopic(mf.newTopicModel("dm4.core.plugin"));
            Topic t2 = dm4.createTopic(mf.newTopicModel("dm4.core.plugin"));
            Topic ws = dm4.createTopic(mf.newTopicModel("dm4.core.plugin"));
            Association assoc = createAssociation(t1, t2);
            //
            dm4.getAccessControl().assignToWorkspace(assoc, ws.getId());
            //
            long wsId = (Long) assoc.getProperty("dm4.workspaces.workspace_id");
            assertEquals(ws.getId(), wsId);
            //
            tx.success();
        }
    }

    // ------------------------------------------------------------------------------------------------- Private Methods

    private List<Topic> getTopicInstances(String topicTypeUri) {
        return dm4.getTopicsByValue("type_uri", new SimpleValue(topicTypeUri));
    }

    private List<RelatedTopic> getTopicInstancesByTraversal(Topic type) {
        return type.getRelatedTopics("dm4.core.instantiation",
            "dm4.core.type", "dm4.core.instance", type.getUri());
    }

    private List<RelatedAssociation> getAssociationInstancesByTraversal(String assocTypeUri) {
        return dm4.getTopicByUri(assocTypeUri).getRelatedAssociations("dm4.core.instantiation",
            "dm4.core.type", "dm4.core.instance", assocTypeUri);
    }

    private List<RelatedTopic> getChildTypes(Topic type) {
        return type.getRelatedTopics(asList("dm4.core.aggregation_def", "dm4.core.composition_def"),
            "dm4.core.parent_type", "dm4.core.child_type", "dm4.core.topic_type");
    }

    // ---

    private void setupTestTopics() {
        Topic t0 = dm4.createTopic(mf.newTopicModel("dm4.test.t0", "dm4.core.plugin"));
        Topic t1 = dm4.createTopic(mf.newTopicModel("dm4.core.plugin"));
        Topic t2 = dm4.createTopic(mf.newTopicModel("dm4.core.plugin"));
        Topic t3 = dm4.createTopic(mf.newTopicModel("dm4.core.plugin"));
        createAssociation(t0, t1);
        createAssociation(t0, t2);
        createAssociation(t0, t3);
    }

    private void setupTestAssociations() {
        Topic t0 = dm4.createTopic(mf.newTopicModel("dm4.test.t0", "dm4.core.plugin"));
        Topic t1 = dm4.createTopic(mf.newTopicModel("dm4.core.plugin"));
        Topic t2 = dm4.createTopic(mf.newTopicModel("dm4.core.plugin"));
        Topic t3 = dm4.createTopic(mf.newTopicModel("dm4.core.plugin"));
        Topic t4 = dm4.createTopic(mf.newTopicModel("dm4.core.plugin"));
        Association a1 = createAssociation(t1, t2);
        Association a2 = createAssociation(t2, t3);
        Association a3 = createAssociation(t3, t4);
        createAssociation(t0, a1);
        createAssociation(t0, a2);
        createAssociation(t0, a3);
    }

    // ---

    private Association createAssociation(Topic topic1, Topic topic2) {
        return dm4.createAssociation(mf.newAssociationModel("dm4.core.association",
            mf.newTopicRoleModel(topic1.getId(), "dm4.core.default"),
            mf.newTopicRoleModel(topic2.getId(), "dm4.core.default")
        ));
    }

    private Association createAssociation(Topic topic, Association assoc) {
        return dm4.createAssociation(mf.newAssociationModel("dm4.core.association",
            mf.newTopicRoleModel(topic.getId(), "dm4.core.default"),
            mf.newAssociationRoleModel(assoc.getId(), "dm4.core.default")
        ));
    }

    // ---

    private List<RelatedTopic> getTestTopics(Topic topic) {
        return topic.getRelatedTopics("dm4.core.association",
            "dm4.core.default", "dm4.core.default", "dm4.core.plugin");
    }

    private List<RelatedAssociation> getTestAssociations(Topic topic) {
        return topic.getRelatedAssociations("dm4.core.association",
            "dm4.core.default", "dm4.core.default", "dm4.core.association");
    }
}
