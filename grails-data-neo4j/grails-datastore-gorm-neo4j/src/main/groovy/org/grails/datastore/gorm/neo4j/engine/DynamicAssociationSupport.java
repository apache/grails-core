package org.grails.datastore.gorm.neo4j.engine;

import org.grails.datastore.gorm.neo4j.GraphPersistentEntity;
import org.grails.datastore.gorm.neo4j.Neo4jMappingContext;
import org.grails.datastore.gorm.neo4j.Neo4jSession;
import org.grails.datastore.gorm.neo4j.TypeDirectionPair;
import org.grails.datastore.gorm.neo4j.collection.Neo4jList;
import org.grails.datastore.gorm.neo4j.mapping.config.DynamicToManyAssociation;
import org.grails.datastore.gorm.neo4j.mapping.reflect.Neo4jNameUtils;
import org.grails.datastore.gorm.neo4j.util.IteratorUtil;
import org.grails.datastore.gorm.schemaless.DynamicAttributes;
import org.grails.datastore.mapping.engine.EntityAccess;
import org.grails.datastore.mapping.engine.NonPersistentTypeException;
import org.grails.datastore.mapping.model.config.GormProperties;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.QueryRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;

/**
 * Support class for dynamic associations
 *
 * @author Graeme Rocher
 * @since 6.1
 */
public class DynamicAssociationSupport {

    private static final Logger log = LoggerFactory.getLogger(DynamicAssociationSupport.class);

    public static Map<TypeDirectionPair, Map<String, Object>> loadDynamicAssociations(Neo4jSession session, GraphPersistentEntity graphPersistentEntity, DynamicAttributes object,  Serializable id) {
        Map<TypeDirectionPair, Map<String, Object>> relationshipsMap = new HashMap<>();

        final boolean hasDynamicAssociations = graphPersistentEntity.hasDynamicAssociations();
        Object alreadyLoaded = session.getAttribute(object, Neo4jEntityPersister.DYNAMIC_ASSOCIATION_PARAM);
        if(alreadyLoaded == null && hasDynamicAssociations) {
            session.setAttribute(object, Neo4jEntityPersister.DYNAMIC_ASSOCIATION_PARAM, Boolean.TRUE);

            final String cypher = graphPersistentEntity.formatDynamicAssociationQuery();
            final Map<String, Object> isMap = Collections.<String, Object>singletonMap(GormProperties.IDENTITY, id);
            final QueryRunner boltSession = session.hasTransaction() ? session.getTransaction().getNativeTransaction() : session.getNativeInterface();

            if(log.isDebugEnabled()) {
                log.debug("QUERY Cypher [{}] for parameters [{}]", cypher, isMap);
            }

            final Result relationships = boltSession.run(cypher, isMap);
            while(relationships.hasNext()) {
                final Record row = relationships.next();
                String relType = row.get("relType").asString();
                Boolean outGoing = row.get("out").asBoolean();
                Map<String, Object> values = row.get("values").asMap();
                TypeDirectionPair key = new TypeDirectionPair(relType, outGoing);
                if(row.containsKey(RelationshipPendingInsert.TARGET_TYPE) && !row.get(RelationshipPendingInsert.TARGET_TYPE).isNull()) {
                    key.setTargetType(
                            row.get(RelationshipPendingInsert.TARGET_TYPE).asString()
                    );
                    relationshipsMap.put(key, values);
                }
            }
            // if the relationship map is not empty as this point there are dynamic relationships that need to be loaded as undeclared
            if (!relationshipsMap.isEmpty()) {
                Neo4jMappingContext mappingContext = session.getMappingContext();
                for (Map.Entry<TypeDirectionPair, Map<String,Object>> entry: relationshipsMap.entrySet()) {
                    EntityAccess entityAccess = session.createEntityAccess(graphPersistentEntity, object);
                    TypeDirectionPair key = entry.getKey();
                    if (key.isOutgoing()) {
                        Map<String, Object> relationshipData = entry.getValue();
                        Object idsObject = relationshipData.get("ids");
                        Object labelsObject = relationshipData.get("labels");
                        if((idsObject instanceof Iterable) && (labelsObject instanceof Iterable)) {

                            Iterator<Serializable> idIter = ((Iterable<Serializable>) idsObject).iterator();
                            String targetType = key.getTargetType();
                            Iterator<Collection<String>> labelIter = ((Iterable<Collection<String>>) labelsObject).iterator();

                            List values = new ArrayList();
                            GraphPersistentEntity associatedEntity = null;
                            while (idIter.hasNext() && labelIter.hasNext()) {
                                Serializable targetId = idIter.next();
                                Collection<String> nextLabels = labelIter.next();
                                Collection<String> labels = nextLabels.isEmpty() ? Collections.singletonList(targetType) : nextLabels;
                                associatedEntity = mappingContext.findPersistentEntityForLabels(labels);
                                if(associatedEntity == null) {
                                    associatedEntity = mappingContext.findPersistentEntityForLabels(Collections.singletonList(targetType));
                                }
                                if(associatedEntity == null) {
                                    continue;
                                }
                                Object proxy = mappingContext.getProxyFactory().createProxy(
                                        session,
                                        associatedEntity.getJavaClass(),
                                        targetId
                                );
                                values.add(proxy);
                            }
                            // for single instances and singular property name do not use an array
                            Object value;
                            if(values.size() == 1 && isSingular(key.getType())) {
                                value = IteratorUtil.singleOrNull(values);
                            }
                            else {
                                DynamicToManyAssociation dynamicAssociation = new DynamicToManyAssociation(graphPersistentEntity, graphPersistentEntity.getMappingContext(), key.getType(), associatedEntity);
                                value = new Neo4jList(entityAccess, dynamicAssociation, values, session);
                            }
                            object.attributes().put(key.getType(), value);
                        }

                    }
                }
            }
        }

        return relationshipsMap;
    }

    private static boolean isSingular(String key) {
        return Neo4jNameUtils.isSingular(key);
    }
}
