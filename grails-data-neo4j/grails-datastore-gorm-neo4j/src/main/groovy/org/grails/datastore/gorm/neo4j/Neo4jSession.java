package org.grails.datastore.gorm.neo4j;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.RemovalListener;
import grails.neo4j.Relationship;
import org.grails.datastore.gorm.neo4j.engine.*;
import org.grails.datastore.gorm.neo4j.mapping.config.DynamicAssociation;
import org.grails.datastore.gorm.neo4j.mapping.config.DynamicToOneAssociation;
import org.grails.datastore.gorm.schemaless.DynamicAttributes;
import org.grails.datastore.mapping.core.AbstractSession;
import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.core.OptimisticLockingException;
import org.grails.datastore.mapping.core.impl.*;
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable;
import org.grails.datastore.mapping.engine.EntityAccess;
import org.grails.datastore.mapping.engine.Persister;
import org.grails.datastore.mapping.engine.types.CustomTypeMarshaller;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.datastore.mapping.model.types.*;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.Restrictions;
import org.grails.datastore.mapping.query.api.QueryableCriteria;
import org.grails.datastore.mapping.reflect.EntityReflector;
import org.grails.datastore.mapping.transactions.SessionHolder;
import org.grails.datastore.mapping.transactions.Transaction;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.persistence.CascadeType;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

/**
 * Represents a session for interacting with Neo4j
 * <p>
 * Created by stefan on 03.03.14.
 *
 * @author Stefan
 * @author Graeme Rocher
 * @since 1.0
 */
public class Neo4jSession extends AbstractSession<Session> {

    private static Logger log = LoggerFactory.getLogger(Neo4jSession.class);
    private static final RemovalListener<RelationshipUpdateKey, Collection<Serializable>> EXCEPTION_THROWING_INSERT_LISTENER =
            (key, value, cause) -> {
                if (RemovalCause.SIZE == cause) {
                    throw new DataAccessResourceFailureException("Maximum number (5000) of relationship update operations to flush() exceeded. Flush the session periodically to avoid this error for batch operations.");
                }

            };

    protected ConcurrentMap<RelationshipUpdateKey, Collection<Serializable>> pendingRelationshipInserts =
            Caffeine.newBuilder()
                    .executor(Runnable::run)
                    .removalListener(EXCEPTION_THROWING_INSERT_LISTENER)
                    .maximumSize(5000).build().asMap();

    protected ConcurrentMap<RelationshipUpdateKey, Collection<Serializable>> pendingRelationshipDeletes =
            Caffeine.newBuilder()
                    .executor(Runnable::run)
                    .removalListener(EXCEPTION_THROWING_INSERT_LISTENER)
                    .maximumSize(5000).build().asMap();


    /**
     * map node id to hashmap of relationship types showing startNode id and endNode id
     */
    protected final Session boltSession;
    protected final Driver boltDriver;


    public Neo4jSession(Datastore datastore, MappingContext mappingContext, ApplicationEventPublisher publisher, boolean stateless, Driver boltDriver) {
        super(datastore, mappingContext, publisher, stateless);
        if (log.isDebugEnabled()) {
            log.debug("Session created");
        }
        this.boltDriver = boltDriver;
        this.boltSession = boltDriver.session();
    }

    public Driver getBoltDriver() {
        return boltDriver;
    }

    /**
     * Adds a relationship that is pending insertion
     *
     * @param association The association
     * @param id          The id
     */
    public void addPendingRelationshipInsert(Serializable parentId, Association association, Serializable id) {
        addRelationshipUpdate(parentId, association, id, this.pendingRelationshipInserts);
    }

    /**
     * Adds a relationship that is pending deletion
     *
     * @param association The association
     * @param id          The id
     */
    public void addPendingRelationshipDelete(Serializable parentId, Association association, Serializable id) {
        addRelationshipUpdate(parentId, association, id, this.pendingRelationshipDeletes);
    }

    protected void addRelationshipUpdate(Serializable parentId, Association association, Serializable id, Map<RelationshipUpdateKey, Collection<Serializable>> targetMap) {
        if (id == null || parentId == null) return;
        final RelationshipUpdateKey key = new RelationshipUpdateKey(parentId, association);
        Collection<Serializable> inserts = targetMap.get(key);
        if (inserts == null) {
            inserts = new ConcurrentLinkedQueue<>();
            targetMap.put(key, inserts);
        }
        if (!inserts.contains(id)) {
            inserts.add(id);
        }
    }

    @Override
    protected void clearPendingOperations() {
        try {
            super.clearPendingOperations();
        } finally {
            pendingRelationshipInserts.clear();
            pendingRelationshipDeletes.clear();;
        }
    }


    /**
     * Gets a Neo4jEntityPersister for the given object
     *
     * @param o The object
     * @return A Neo4jEntityPersister
     */
    public Neo4jEntityPersister getEntityPersister(Object o) {
        return (Neo4jEntityPersister) getPersister(o);
    }

    @Override
    protected Persister createPersister(Class cls, MappingContext mappingContext) {
        final PersistentEntity entity = mappingContext.getPersistentEntity(cls.getName());
        return (entity != null) ? new Neo4jEntityPersister(mappingContext, entity, this, publisher) : null;
    }

    @Override
    protected Transaction beginTransactionInternal() {
        throw new IllegalStateException("Use beingTransaction(TransactionDefinition) instead");
    }

    public Transaction beginTransaction(TransactionDefinition transactionDefinition) {
        return beginTransactionInternal(transactionDefinition, false);
    }

    protected Transaction beginTransactionInternal(TransactionDefinition transactionDefinition, boolean sessionCreated) {
        if (transaction != null && transaction.isActive()) {
            return transaction;
        } else {
            // if there is a current transaction, return that, since Neo4j doesn't really supported transaction nesting
            Transaction tx = null;
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                SessionHolder sessionHolder = (SessionHolder) TransactionSynchronizationManager.getResource(getDatastore());
                tx = sessionHolder.getTransaction();
            }

            if (tx == null || !tx.isActive()) {
                if (transactionDefinition.getName() == null) {
                    transactionDefinition = createDefaultTransactionDefinition(transactionDefinition);
                }
                tx = new Neo4jTransaction(boltDriver, transactionDefinition, sessionCreated);
            }
            this.transaction = tx;
            return transaction;
        }
    }

    @Override
    public void disconnect() {
        if (isConnected()) {

            super.disconnect();
            // if there is no active synchronization defined by a TransactionManager then close the transaction is if was created
            try {
                if (transaction != null && !isSynchronizedWithTransaction) {
                    Neo4jTransaction transaction = getTransaction();

                    transaction.close();
                }
                boltSession.close();
            } catch (IOException e) {
                log.error("Error closing transaction: " + e.getMessage(), e);
            } finally {
                if (log.isDebugEnabled()) {
                    log.debug("Session closed");
                }
                this.transaction = null;
            }
        }
    }

    @Override
    public Neo4jDatastore getDatastore() {
        return (Neo4jDatastore) super.getDatastore();
    }

    @Override
    public Session getNativeInterface() {
        return hasTransaction() ? getTransaction().getBoltSession() : boltSession;
    }

    @Override
    protected void flushPendingUpdates(Map<PersistentEntity, Collection<PendingUpdate>> updates) {

        // UPDATE statements cannot be batched, but optimise statements for dirty checking
        final Set<PersistentEntity> entities = updates.keySet();
        final Neo4jMappingContext mappingContext = getMappingContext();


        for (PersistentEntity entity : entities) {
            final Collection<PendingUpdate> pendingUpdates = updates.get(entity);
            GraphPersistentEntity graphPersistentEntity = (GraphPersistentEntity) entity;
            final boolean isVersioned = entity.isVersioned() && entity.hasProperty(GormProperties.VERSION, Long.class);

            for (PendingUpdate pendingUpdate : pendingUpdates) {
                final List<PendingOperation> preOperations = pendingUpdate.getPreOperations();
                executePendings(preOperations);

                pendingUpdate.run();

                if (pendingUpdate.isVetoed()) continue;

                final EntityAccess access = pendingUpdate.getEntityAccess();
                final List<PendingOperation<Object, Serializable>> cascadingOperations = new ArrayList<>(pendingUpdate.getCascadeOperations());
                final Object object = pendingUpdate.getObject();

                final Map<String, Object> params = new LinkedHashMap<>(2);
                final Serializable id = (Serializable) pendingUpdate.getNativeKey();
                params.put(GormProperties.IDENTITY, id);
                final Map<String, Object> simpleProps = new HashMap<>();


                final DirtyCheckable dirtyCheckable = (DirtyCheckable) object;
                final List<String> dirtyPropertyNames = dirtyCheckable.listDirtyPropertyNames();
                for (String dirtyPropertyName : dirtyPropertyNames) {
                    final PersistentProperty property = entity.getPropertyByName(dirtyPropertyName);
                    if (property != null) {
                        if (property instanceof Simple || property instanceof Basic) {
                            String name = property.getName();
                            Object value = access.getProperty(name);
                            if (value != null) {
                                simpleProps.put(name, mappingContext.convertToNative(value));
                            } else {
                                simpleProps.put(name, null);
                            }
                        } else if (property instanceof Custom) {
                            applyCustomType(access, property, simpleProps);
                        }
                    }
                }

                Map<String, List<Object>> dynamicAssociations = amendMapWithUndeclaredProperties(graphPersistentEntity, simpleProps, object, mappingContext);
                if (graphPersistentEntity.hasDynamicAssociations()) {
                    getEntityPersister(object).processDynamicAssociations(graphPersistentEntity, access, mappingContext, dynamicAssociations, cascadingOperations, true);
                }
                processPendingRelationshipUpdates(graphPersistentEntity, access, id, cascadingOperations, true);

                final boolean hasNoUpdates = simpleProps.isEmpty();
                if (hasNoUpdates) {
                    // if there are no simple property updates then only the associations were dirty
                    // reset track changes
                    dirtyCheckable.trackChanges();
                    executePendings(cascadingOperations);
                } else {
                    params.put(CypherBuilder.PROPS, simpleProps);
                    if (isVersioned) {
                        Long version = (Long) access.getProperty(GormProperties.VERSION);
                        if (version == null) {
                            version = 0l;
                        }
                        params.put(GormProperties.VERSION, version);
                        long newVersion = version + 1;
                        simpleProps.put(GormProperties.VERSION, newVersion);
                        access.setProperty(GormProperties.VERSION, newVersion);
                    }

                    String cypher = graphPersistentEntity.formatMatchAndUpdate(CypherBuilder.NODE_VAR, simpleProps);
                    if (log.isDebugEnabled()) {
                        log.debug("UPDATE Cypher [{}] for parameters [{}]", cypher, params);
                    }

                    final Result executionResult = getTransaction().getNativeTransaction().run(cypher, params);
                    if (!executionResult.hasNext() && isVersioned) {
                        throw new OptimisticLockingException(entity, id);
                    } else {
                        // reset track changes
                        dirtyCheckable.trackChanges();
                        executePendings(cascadingOperations);
                    }
                }
            }
        }

    }

    private void processPendingRelationshipUpdates(GraphPersistentEntity entity, EntityAccess access, Serializable id, List<PendingOperation<Object, Serializable>> cascadingOperations, boolean isUpdate) {
        if (entity.isRelationshipEntity()) {
            RelationshipPersistentEntity relEntity = (RelationshipPersistentEntity) entity;
            Object from = access.getPropertyValue(RelationshipPersistentEntity.FROM);
            if (from != null) {
                id = relEntity.getFromEntity().getReflector().getIdentifier(from);
            }
        }
        for (Association association : entity.getAssociations()) {
            if (association.isBasic()) continue;
            processPendingRelationshipUpdates(access, id, association, cascadingOperations, isUpdate);
        }
        if (entity.hasDynamicAssociations()) {
            if (!pendingRelationshipDeletes.isEmpty()) {
                for (RelationshipUpdateKey relationshipUpdateKey : pendingRelationshipDeletes.keySet()) {
                    Association association = relationshipUpdateKey.association;
                    if (association instanceof DynamicAssociation) {
                        if (association.getOwner().equals(entity)) {
                            if (relationshipUpdateKey.id.equals(id)) {
                                cascadingOperations.add(new RelationshipPendingDelete(access, association, pendingRelationshipDeletes.get(relationshipUpdateKey), getTransaction().getTransaction()));
                            }
                        }
                    }
                }
            }
            if (!pendingRelationshipInserts.isEmpty()) {
                List<RelationshipUpdateKey> relationshipUpdates = new ArrayList<RelationshipUpdateKey>(pendingRelationshipInserts.keySet());
                Collections.sort(relationshipUpdates, new Comparator<RelationshipUpdateKey>() {
                    @Override
                    public int compare(RelationshipUpdateKey o1, RelationshipUpdateKey o2) {
                        return o1.association.getName().compareTo(o2.association.getName());
                    }
                });
                for (RelationshipUpdateKey relationshipUpdate : relationshipUpdates) {
                    Association association = relationshipUpdate.association;
                    if (association instanceof DynamicToOneAssociation) {
                        if (association.getOwner().equals(entity)) {
                            if (relationshipUpdate.id.equals(id)) {
                                cascadingOperations.add(new RelationshipPendingInsert(access, association, pendingRelationshipInserts.get(relationshipUpdate), this, isUpdate));
                                pendingRelationshipInserts.remove(relationshipUpdate);
                            }
                        }
                    }
                }
            }
        }
    }

    private void processPendingRelationshipUpdates(EntityAccess parent, Serializable parentId, Association association, List<PendingOperation<Object, Serializable>> cascadingOperations, boolean isUpdate) {
        final RelationshipUpdateKey key;
        if (RelationshipUtils.useReversedMappingFor(association)) {
            if (association instanceof ManyToMany) {
                // many-to-many updates handled by the owning side
                return;
            }
            Association inverseSide = association.getInverseSide();
            if (inverseSide != null) {
                Object associated = parent.getPropertyValue(association.getName());
                if (associated == null) {
                    key = new RelationshipUpdateKey(parentId, association);
                } else {

                    PersistentEntity associatedEntity = inverseSide.getOwner();
                    parent = createEntityAccess(associatedEntity, associated);
                    Serializable associatedId = (Serializable) parent.getIdentifier();
                    association = inverseSide;
                    key = new RelationshipUpdateKey(associatedId, inverseSide);
                }
            } else {
                key = new RelationshipUpdateKey(parentId, association);
            }
        } else {
            key = new RelationshipUpdateKey(parentId, association);
        }

        final Collection<Serializable> pendingInserts = pendingRelationshipInserts.get(key);
        if (pendingInserts != null && !pendingInserts.isEmpty()) {
            cascadingOperations.add(new RelationshipPendingInsert(parent, association, pendingInserts, this, isUpdate));
            pendingRelationshipInserts.remove(key);
        }
        final Collection<Serializable> pendingDeletes = pendingRelationshipDeletes.get(key);
        if (pendingDeletes != null && !pendingDeletes.isEmpty()) {
            cascadingOperations.add(new RelationshipPendingDelete(parent, association, pendingDeletes, getTransaction().getNativeTransaction()));
            pendingRelationshipDeletes.remove(key);
        }
    }

    @Override
    protected void flushPendingInserts(Map<PersistentEntity, Collection<PendingInsert>> inserts) {

        org.neo4j.driver.Transaction neo4jTransaction = getTransaction().getNativeTransaction();
        List<PendingOperation<Object, Serializable>> cascadingOperations = new ArrayList<>();
        // processing relationship entities first, to ensure the most optimal queries are executed
        for (RelationshipUpdateKey relationshipUpdateKey : pendingRelationshipInserts.keySet()) {
            PersistentEntity entity = relationshipUpdateKey.association.getOwner();
            if (inserts.containsKey(entity)) {
                processInsertsForEntity(neo4jTransaction, (GraphPersistentEntity) entity, inserts, cascadingOperations);
            }
        }
        // batch up all inserts into a single CREATE statement
        final Set<PersistentEntity> entities = inserts.keySet();

        for (PersistentEntity entity : entities) {
            processInsertsForEntity(neo4jTransaction, (GraphPersistentEntity) entity, inserts, cascadingOperations);
        }

        executePendings(cascadingOperations);

    }

    private void processInsertsForEntity(org.neo4j.driver.Transaction neo4jTransaction, GraphPersistentEntity entity, Map<PersistentEntity, Collection<PendingInsert>> inserts, List<PendingOperation<Object, Serializable>> cascadingOperations) {
        final Collection<PendingInsert> entityInserts = inserts.get(entity);
        final boolean hasDynamicLabels = entity.hasDynamicLabels();
        final EntityReflector reflector = entity.getReflector();
        Neo4jMappingContext mappingContext = getMappingContext();
        if (!entityInserts.isEmpty()) {

            if (hasDynamicLabels) {
                buildAndExecuteCreateStatement(entityInserts, entity, cascadingOperations);
            } else {
                // use UNWIND and FOREACH to batch
                StringBuilder batchCypher = new StringBuilder();
                final Map<String, Object> params = new HashMap<>(inserts.size());
                Map<String, String> associationMerges = new LinkedHashMap<>();
                batchCypher.append(entity.getBatchCreateStatement());
                Collection<Map<String, Object>> rows = new ArrayList<>();
                for (PendingInsert entityInsert : entityInserts) {
                    EntityAccess entityAccess = entityInsert.getEntityAccess();
                    if (entityInsert.wasExecuted()) {
                        processPendingRelationshipUpdates(entity, entityAccess, (Serializable) entityInsert.getNativeKey(), cascadingOperations, false);
                        cascadingOperations.addAll(entityInsert.getCascadeOperations());
                    } else {
                        if (isVetoedAfterPreOperations(entityInsert)) {
                            continue;
                        }

                        if (entity.isRelationshipEntity()) {
                            RelationshipPersistentEntity relEntity = (RelationshipPersistentEntity) entity;
                            Relationship relationship = (Relationship) entityAccess.getEntity();
                            Object type = entityAccess.getProperty(RelationshipPersistentEntity.TYPE);
                            // if the type hasn't been modified we can batch
                            if(relEntity.type().equals(type)) {
                                Object from = entityAccess.getPropertyValue(RelationshipPersistentEntity.FROM);
                                Serializable id = null;
                                if (from != null) {
                                    id = relEntity.getFromEntity().getReflector().getIdentifier(from);
                                }
                                if(id != null) {
                                    RelationshipUpdateKey updateKey = new RelationshipUpdateKey(id, relEntity.getFrom());
                                    Collection<Serializable> childIds = pendingRelationshipInserts.get(updateKey);
                                    if(childIds != null) {
                                        pendingRelationshipInserts.remove(updateKey);
                                        Map<String, Object> rowData = new LinkedHashMap<>();
                                        LinkedHashMap<String, Object> nodeProperties = new LinkedHashMap<>();
                                        for (PersistentProperty pp : relEntity.getPersistentProperties()) {
                                            if(pp instanceof Simple || pp instanceof Basic || pp instanceof TenantId) {
                                                String propertyName = pp.getName();
                                                if(RelationshipPersistentEntity.TYPE.equals(propertyName)) {
                                                    continue;
                                                }

                                                Object v = reflector.getProperty(relationship, propertyName);
                                                if(v != null) {
                                                    nodeProperties.put(propertyName, mappingContext.convertToNative(v));
                                                }
                                            }
                                        }

                                        nodeProperties.putAll( relationship.attributes() );

                                        rowData.put(CypherBuilder.PROPS, nodeProperties);
                                        rowData.put(RelationshipPersistentEntity.FROM, id);
                                        rowData.put(RelationshipPersistentEntity.TO, childIds);
                                        rows.add(rowData);

                                    }
                                }
                            }
                            else {
                                // otherwise process individual inserts
                                processPendingRelationshipUpdates(entity, entityAccess, (Serializable) entityInsert.getNativeKey(), cascadingOperations, false);
                            }
                        } else {
                            Object parentId = entityAccess.getIdentifier();
                            if (parentId != null) {
                                Map<String, Object> nodeProperties = readNodePropertiesForInsert(entityInsert, entity, entity.getPersistentProperties(), entityAccess);
                                Object obj = entityInsert.getObject();
                                Map<String, List<Object>> dynamicRelProps = amendMapWithUndeclaredProperties(entity, nodeProperties, obj, getMappingContext());
                                Map<String, Object> data = new LinkedHashMap<>();
                                data.put(CypherBuilder.PROPS, nodeProperties);
                                rows.add(data);


                                for (Association association : entity.getAssociations()) {
                                    if (association.isBasic()) continue;
                                    boolean isTree = association.isCircular();
                                    if (association.isOwningSide() || isTree) {
                                        RelationshipUpdateKey key = new RelationshipUpdateKey((Serializable) parentId, association);
                                        Collection<Serializable> childIds = pendingRelationshipInserts.get(key);
                                        if (childIds != null) {
                                            GraphPersistentEntity associatedEntity = (GraphPersistentEntity) association.getAssociatedEntity();
                                            String associationName = association.getName();
                                            Collection<PendingInsert> pendingInserts = inserts.get(associatedEntity);
                                            if (pendingInserts != null) {
                                                Collection<Map<String, Object>> childRows = new ArrayList<>();
                                                for (PendingInsert pendingInsert : pendingInserts) {
                                                    Serializable childId = (Serializable) pendingInsert.getNativeKey();
                                                    if (childIds.contains(childId)) {

                                                        boolean wasExecutedBeforeHand = pendingInsert.wasExecuted();

                                                        if (wasExecutedBeforeHand || isVetoedAfterPreOperations(pendingInsert)) {
                                                            continue;
                                                        }

                                                        childIds.remove(childId);

                                                        Map<String, Object> childProperties = readNodePropertiesForInsert(pendingInsert, associatedEntity, associatedEntity.getPersistentProperties(), pendingInsert.getEntityAccess());
                                                        childRows.add(Collections.<String, Object>singletonMap(CypherBuilder.PROPS, childProperties));

                                                        cascadingOperations.addAll(pendingInsert.getCascadeOperations());
                                                    }
                                                }

                                                if (!childRows.isEmpty()) {
                                                    String parentVariable = entity.getVariableId();
                                                    associationMerges.put(associationName, associatedEntity.formatBatchCreate(parentVariable, association));
                                                    data.put(associationName, childRows);
                                                }
                                            }
                                        }
                                    }
                                }


                                processDynamicAssociationsIfNecessary(entity, entityAccess, obj, entityInsert, cascadingOperations, params, dynamicRelProps);
                                params.remove(Neo4jEntityPersister.DYNAMIC_ASSOCIATION_PARAM);
                            }


                            cascadingOperations.addAll(entityInsert.getCascadeOperations());
                        }

                    }
                }
                params.put(entity.getBatchId(), rows);
                if (!associationMerges.isEmpty()) {
                    for (String merge : associationMerges.keySet()) {
                        batchCypher.append(associationMerges.get(merge));
                    }
                }

                if (batchCypher.length() > 0 && !rows.isEmpty()) {

                    final String finalCypher = batchCypher.toString();
                    if (log.isDebugEnabled()) {
                        log.debug("CREATE Cypher [{}] for parameters [{}]", finalCypher, params);
                    }
                    neo4jTransaction.run(finalCypher, params);

                }
            }


        }
    }

    private boolean isVetoedAfterPreOperations(PendingInsert entityInsert) {
        List<PendingOperation> preOperations = entityInsert.getPreOperations();
        for (PendingOperation preOperation : preOperations) {
            preOperation.run();
        }

        entityInsert.run();
        ((PendingOperationAdapter) entityInsert).setExecuted(true);
        return entityInsert.isVetoed();
    }

    private void buildAndExecuteCreateStatement(Collection<PendingInsert> entityInserts, GraphPersistentEntity graphEntity, List<PendingOperation<Object, Serializable>> cascadingOperations) {
        // dynamic labels require individual create statements and are less efficient
        final Map<String, Object> createParams = new HashMap<>(entityInserts.size());
        final String finalCypher = buildCypherCreateStatement(entityInserts, graphEntity, cascadingOperations, createParams);
        if(graphEntity.hasDynamicAssociations()) {
            createParams.remove(Neo4jEntityPersister.DYNAMIC_ASSOCIATION_PARAM);
        }
        if (finalCypher.length() > 0) {
            if (log.isDebugEnabled()) {
                log.debug("CREATE Cypher [{}] for parameters [{}]", finalCypher, createParams);
            }
            getTransaction().getNativeTransaction().run(finalCypher, createParams);
        }
    }

    private String buildCypherCreateStatement(Collection<PendingInsert> entityInserts, GraphPersistentEntity graphEntity, List<PendingOperation<Object, Serializable>> cascadingOperations, Map<String, Object> createParams) {
        StringBuilder createCypher = new StringBuilder();

        int i = 0;
        boolean first = true;
        for (final PendingInsert entityInsert : entityInserts) {
            if (entityInsert.wasExecuted() || graphEntity.isRelationshipEntity()) {
                processPendingRelationshipUpdates(graphEntity, entityInsert.getEntityAccess(), (Serializable) entityInsert.getNativeKey(), cascadingOperations, false);
                cascadingOperations.addAll(entityInsert.getCascadeOperations());
            } else {
                List<PendingOperation> preOperations = entityInsert.getPreOperations();
                for (PendingOperation preOperation : preOperations) {
                    preOperation.run();
                }

                entityInsert.run();
                ((PendingOperationAdapter) entityInsert).setExecuted(true);
                if (entityInsert.isVetoed()) continue;

                cascadingOperations.addAll(entityInsert.getCascadeOperations());

                i++;
                if (!first) {
                    createCypher.append(',');
                    createCypher.append('\n');
                } else {
                    createCypher.append(CypherBuilder.CYPHER_CREATE);
                    first = false;
                }

                buildEntityCreateOperation(createCypher, String.valueOf(i), graphEntity, entityInsert, createParams, cascadingOperations);
            }
        }
        return createCypher.toString();
    }

    public String buildEntityCreateOperation(PersistentEntity entity, PendingInsert entityInsert, Map<String, Object> params, List<PendingOperation<Object, Serializable>> cascadingOperations) {
        StringBuilder createCypher = new StringBuilder(CypherBuilder.CYPHER_CREATE);
        buildEntityCreateOperation(createCypher, "", entity, entityInsert, params, cascadingOperations);
        return createCypher.toString();
    }

    public void buildEntityCreateOperation(StringBuilder createCypher, String index, PersistentEntity entity, PendingInsert entityInsert, Map<String, Object> params, List<PendingOperation<Object, Serializable>> cascadingOperations) {
        GraphPersistentEntity graphEntity = (GraphPersistentEntity) entity;
        final List<PersistentProperty> persistentProperties = entity.getPersistentProperties();
        final Object obj = entityInsert.getObject();
        final EntityAccess access = entityInsert.getEntityAccess();
        final Map<String, Object> simpleProps = readNodePropertiesForInsert(entityInsert, graphEntity, persistentProperties, access);


        Map<String, List<Object>> dynamicRelProps = amendMapWithUndeclaredProperties(graphEntity, simpleProps, obj, getMappingContext());
        final String labels = graphEntity.getLabelsWithInheritance(obj);

        String cypher = String.format("(n" + index + "%s $props" + index + ")", labels);
        createCypher.append(cypher);
        params.put("props" + index, simpleProps);

        processDynamicAssociationsIfNecessary(graphEntity, access, obj, entityInsert, cascadingOperations, params, dynamicRelProps);
    }

    private void processDynamicAssociationsIfNecessary(GraphPersistentEntity graphEntity, EntityAccess access, Object obj, PendingInsert entityInsert, List<PendingOperation<Object, Serializable>> cascadingOperations, Map<String, Object> params, Map<String, List<Object>> dynamicRelProps) {
        boolean hasDynamicAssociations = graphEntity.hasDynamicAssociations();
        Serializable parentId = (Serializable) entityInsert.getNativeKey();
        // in the case of native ids the parent id will be null, so these operations have to be handled later
        if (parentId != null) {
            processPendingRelationshipUpdates(graphEntity, access, parentId, cascadingOperations, false);
        }
        if (hasDynamicAssociations) {
            getEntityPersister(obj).processDynamicAssociations(graphEntity, access, getMappingContext(), dynamicRelProps, cascadingOperations, false);
            params.put(Neo4jEntityPersister.DYNAMIC_ASSOCIATION_PARAM, dynamicRelProps);
        }
    }

    private Map<String, Object> readNodePropertiesForInsert(PendingInsert entityInsert, GraphPersistentEntity graphEntity, List<PersistentProperty> persistentProperties, EntityAccess access) {
        final Map<String, Object> simpleProps = new HashMap<>(persistentProperties.size());
        final Serializable id = (Serializable) entityInsert.getNativeKey();

        if (!graphEntity.isNativeId()) {
            if (graphEntity.getIdGeneratorType().equals(IdGenerator.Type.SNOWFLAKE)) {
                simpleProps.put(CypherBuilder.IDENTIFIER, id);
            } else {
                simpleProps.put(graphEntity.getIdentity().getName(), id);
            }
        }


        // build a properties map for each CREATE statement
        for (PersistentProperty pp : persistentProperties) {
            if ((pp instanceof Simple) || (pp instanceof TenantId) || (pp instanceof Basic)) {
                String name = pp.getName();
                Object value = access.getProperty(name);
                if (value != null) {
                    simpleProps.put(name, getMappingContext().convertToNative(value));
                }
            } else if (pp instanceof Custom) {
                applyCustomType(access, pp, simpleProps);
            }
        }
        return simpleProps;
    }

    private void applyCustomType(EntityAccess access, PersistentProperty property, Map<String, Object> simpleProps) {
        Custom<Map<String, Object>> custom = (Custom<Map<String, Object>>) property;
        final CustomTypeMarshaller<Object, Map<String, Object>, Map<String, Object>> customTypeMarshaller = custom.getCustomTypeMarshaller();
        Object value = access.getProperty(property.getName());
        customTypeMarshaller.write(custom, value, simpleProps);
    }

    @Override
    public Neo4jMappingContext getMappingContext() {
        return (Neo4jMappingContext) super.getMappingContext();
    }

    @Override
    protected void flushPendingDeletes(Map<PersistentEntity, Collection<PendingDelete>> pendingDeletes) {

        final Set<PersistentEntity> persistentEntities = pendingDeletes.keySet();
        for (PersistentEntity entity : persistentEntities) {

            final Collection<PendingDelete> deletes = pendingDeletes.get(entity);
            final Collection<Object> ids = new ArrayList<Object>();
            Collection<PendingOperation> cascadingOperations = new ArrayList<PendingOperation>();

            for (PendingDelete delete : deletes) {

                List<PendingOperation> preOperations = delete.getPreOperations();
                for (PendingOperation preOperation : preOperations) {
                    preOperation.run();
                }

                delete.run();

                if (delete.isVetoed()) continue;

                final Object id = delete.getNativeKey();
                ids.add(id);
                cascadingOperations.addAll(delete.getCascadeOperations());
            }

            final Neo4jQuery deleteQuery = new Neo4jQuery(this, entity, getEntityPersister(entity.getJavaClass()));
            deleteQuery.add(Restrictions.in(GormProperties.IDENTITY, ids));
            final CypherBuilder cypherBuilder = deleteQuery.getBaseQuery();
            buildCascadingDeletes(entity, cypherBuilder);

            final String cypher = cypherBuilder.build();
            final Map<String, Object> idMap = cypherBuilder.getParams();

            if (log.isDebugEnabled()) {
                log.debug("DELETE Cypher [{}] for parameters {}", cypher, idMap);
            }
            getTransaction().getNativeTransaction().run(cypher, idMap);

            executePendings(cascadingOperations);

        }
    }

    protected Map<String, List<Object>> amendMapWithUndeclaredProperties(GraphPersistentEntity graphEntity, Map<String, Object> simpleProps, Object pojo, MappingContext mappingContext) {
        boolean hasDynamicAssociations = graphEntity.hasDynamicAssociations();
        Map<String, List<Object>> dynRelProps = hasDynamicAssociations ? new LinkedHashMap<String, List<Object>>() : Collections.<String, List<Object>>emptyMap();
        if (pojo instanceof DynamicAttributes) {
            Map<String, Object> map = ((DynamicAttributes) pojo).attributes();
            if (map != null) {
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    Object value = entry.getValue();
                    String key = entry.getKey();
                    if (value == null) {
                        simpleProps.put(key, value);
                        continue;
                    }

                    if (hasDynamicAssociations) {
                        if (mappingContext.isPersistentEntity(value)) {
                            List<Object> objects = getOrInit(dynRelProps, key);
                            objects.add(value);
                        } else if (isCollectionWithPersistentEntities(value, mappingContext)) {
                            List<Object> objects = getOrInit(dynRelProps, key);
                            objects.addAll((Collection) value);
                        } else {
                            if (((DirtyCheckable) pojo).hasChanged(key)) {
                                simpleProps.put(key, ((Neo4jMappingContext) mappingContext).convertToNative(value));
                            }
                        }
                    } else {
                        if (((DirtyCheckable) pojo).hasChanged(key)) {
                            simpleProps.put(key, ((Neo4jMappingContext) mappingContext).convertToNative(value));
                        }
                    }
                }
            }
        }
        return dynRelProps;
    }

    private List<Object> getOrInit(Map<String, List<Object>> dynRelProps, String key) {
        List<Object> objects = dynRelProps.get(key);
        if (objects == null) {
            objects = new ArrayList<>();
            dynRelProps.put(key, objects);
        }
        return objects;
    }


    public static boolean isCollectionWithPersistentEntities(Object o, MappingContext mappingContext) {
        if (!(o instanceof Collection)) {
            return false;
        } else {
            @SuppressWarnings("unchecked") Collection<Object> c = (Collection<Object>) o;
            return c.stream().anyMatch(mappingContext::isPersistentEntity);
        }
    }


    @Override
    public void flush() {
        if (wasTransactionTerminated()) return;

        final Neo4jTransaction transaction = (Neo4jTransaction) this.transaction;
        if (transaction != null) {
            if (transaction.getTransactionDefinition().isReadOnly()) {
                return;
            }
            persistDirtyButUnsavedInstances();
            super.flush();
        } else {
            throw new NoTransactionException("Cannot flush write operations without an active transaction!");
        }
    }

    @Override
    public Neo4jTransaction getTransaction() {
        return (Neo4jTransaction) super.getTransaction();
    }

    protected boolean wasTransactionTerminated() {
        return transaction != null && !transaction.isActive();
    }

    @Override
    protected void postFlush(boolean hasUpdates) {
        super.postFlush(hasUpdates);
        if (publisher != null) {
            publisher.publishEvent(new SessionFlushedEvent(this));
        }
    }

    public Neo4jTransaction assertTransaction() {
        if (transaction == null || (wasTransactionTerminated() && !TransactionSynchronizationManager.isSynchronizationActive())) {
            SessionHolder sessionHolder = (SessionHolder) TransactionSynchronizationManager.getResource(getDatastore());
            if (sessionHolder != null && sessionHolder.getSession().equals(this)) {
                Transaction<?> transaction = sessionHolder.getTransaction();
                if (transaction instanceof Neo4jTransaction) {
                    this.transaction = transaction;
                    return (Neo4jTransaction) transaction;
                } else {
                    startDefaultTransaction();
                }
            } else {
                startDefaultTransaction();
            }

        }
        return (Neo4jTransaction) transaction;
    }

    @Override
    public boolean hasTransaction() {
        return super.hasTransaction() && transaction.isActive();
    }

    private void startDefaultTransaction() {
        // start a new transaction upon termination
        final DefaultTransactionDefinition transactionDefinition = createDefaultTransactionDefinition(null);
        transaction = new Neo4jTransaction(boltDriver, transactionDefinition, true);
    }

    protected DefaultTransactionDefinition createDefaultTransactionDefinition(TransactionDefinition other) {
        final DefaultTransactionDefinition transactionDefinition = other != null ? new DefaultTransactionDefinition(other) : new DefaultTransactionDefinition();
        transactionDefinition.setName(Neo4jTransaction.DEFAULT_NAME);
        return transactionDefinition;
    }

    /**
     * in case a
     * known instance is modified and not explicitly saved, we track dirty state here and spool them for persisting
     */
    private void persistDirtyButUnsavedInstances() {
        for (Map<Serializable, Object> cache : firstLevelCache.values()) {
            for (Object obj : cache.values()) {
                if (obj instanceof DirtyCheckable) {
                    boolean isDirty = ((DirtyCheckable) obj).hasChanged();
                    if (isDirty && !isPendingAlready(obj)) {
                        persist(obj);
                    }
                }
            }
        }
    }


    @Override
    public long deleteAll(QueryableCriteria criteria) {

        final PersistentEntity entity = criteria.getPersistentEntity();
        final List<Query.Criterion> criteriaList = criteria.getCriteria();
        final Neo4jQuery query = new Neo4jQuery(this, entity, getEntityPersister(entity.getJavaClass()));
        for (Query.Criterion criterion : criteriaList) {
            query.add(criterion);
        }
        query.projections().count();

        final CypherBuilder baseQuery = query.getBaseQuery();
        buildCascadingDeletes(entity, baseQuery);

        final String cypher = baseQuery.build();
        final Map<String, Object> params = baseQuery.getParams();
        if (log.isDebugEnabled()) {
            log.debug("DELETE Cypher [{}] for parameters [{}]", cypher, params);
        }
        Number count = (Number) query.singleResult();
        getTransaction().getNativeTransaction().run(cypher, params);
        return count.longValue();
    }

    protected void buildCascadingDeletes(PersistentEntity entity, CypherBuilder baseQuery) {
        if (entity instanceof RelationshipPersistentEntity) {
            baseQuery.addDeleteColumn(CypherBuilder.REL_VAR);
        } else {
            int i = 1;
            for (Association association : entity.getAssociations()) {
                if (association instanceof Basic) continue;
                if (association.doesCascade(CascadeType.REMOVE)) {

                    String a = "a" + i++;
                    baseQuery.addOptionalMatch("(n)" + RelationshipUtils.matchForAssociation(association) + "(" + a + ")");
                    baseQuery.addDeleteColumn(a);

                }
            }
            baseQuery.addDeleteColumn(CypherBuilder.NODE_VAR);
        }
    }

    @Override
    public long updateAll(QueryableCriteria criteria, Map<String, Object> properties) {
        final PersistentEntity entity = criteria.getPersistentEntity();
        final List<Query.Criterion> criteriaList = criteria.getCriteria();
        final Neo4jQuery query = new Neo4jQuery(this, entity, getEntityPersister(entity.getJavaClass()));
        for (Query.Criterion criterion : criteriaList) {
            query.add(criterion);
        }
        query.projections().count();

        final CypherBuilder baseQuery = query.getBaseQuery();
        baseQuery.addPropertySet(properties);

        final String cypher = baseQuery.build();
        final Map<String, Object> params = baseQuery.getParams();
        if (log.isDebugEnabled()) {
            log.debug("UPDATE Cypher [{}] for parameters [{}]", cypher, params);
        }

        final Result execute = getTransaction().getNativeTransaction().run(cypher, params);
        return Neo4jEntityPersister.countUpdates(execute);
    }

    private static class RelationshipUpdateKey {
        private final Serializable id;
        private final Association association;

        public RelationshipUpdateKey(Serializable id, Association association) {
            this.id = id;
            this.association = association;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            RelationshipUpdateKey that = (RelationshipUpdateKey) o;

            if (association != null ? !association.equals(that.association) : that.association != null) return false;
            return !(id != null ? !id.equals(that.id) : that.id != null);

        }

        @Override
        public int hashCode() {
            int result = association != null ? association.hashCode() : 0;
            result = 31 * result + (id != null ? id.hashCode() : 0);
            return result;
        }
    }
}


