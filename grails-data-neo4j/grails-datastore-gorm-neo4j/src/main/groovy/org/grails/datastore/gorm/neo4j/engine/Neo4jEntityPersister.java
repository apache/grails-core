package org.grails.datastore.gorm.neo4j.engine;

import groovy.lang.GroovyObject;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.grails.datastore.gorm.GormEntity;
import org.grails.datastore.gorm.neo4j.*;
import org.grails.datastore.gorm.neo4j.collection.*;
import org.grails.datastore.gorm.neo4j.mapping.config.DynamicToOneAssociation;
import org.grails.datastore.gorm.neo4j.util.IteratorUtil;
import org.grails.datastore.gorm.schemaless.DynamicAttributes;
import org.grails.datastore.mapping.collection.PersistentCollection;
import org.grails.datastore.mapping.core.IdentityGenerationException;
import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.core.impl.*;
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable;
import org.grails.datastore.mapping.dirty.checking.DirtyCheckableCollection;
import org.grails.datastore.mapping.engine.EntityAccess;
import org.grails.datastore.mapping.engine.EntityPersister;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.config.GormMappingConfigurationStrategy;
import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.datastore.mapping.model.types.*;
import org.grails.datastore.mapping.proxy.EntityProxy;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.reflect.EntityReflector;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.QueryRunner;
import org.neo4j.driver.summary.ResultSummary;
import org.neo4j.driver.summary.SummaryCounters;
import org.neo4j.driver.types.Entity;
import org.neo4j.driver.types.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.util.Assert;

import javax.persistence.FetchType;
import javax.persistence.LockModeType;
import java.io.Serializable;
import java.util.*;

import static org.grails.datastore.mapping.query.Query.*;

/**
 * Core {@link EntityPersister} implementation responsible for CRUD operations against the Graph.
 *
 * @author Stefan Armbruster (stefan@armbruster-it.de)
 * @author Graeme Rocher
 * @since 1.0
 */
public class Neo4jEntityPersister extends EntityPersister {


    public static final String DYNAMIC_ASSOCIATION_PARAM = "org.grails.neo4j.DYNAMIC_ASSOCIATIONS";

    private static Logger log = LoggerFactory.getLogger(Neo4jEntityPersister.class);


    public Neo4jEntityPersister(MappingContext mappingContext, PersistentEntity entity, Session session, ApplicationEventPublisher publisher) {
        super(mappingContext, entity, session, publisher);
    }


    @Override
    public Neo4jSession getSession() {
        return (Neo4jSession) super.session;
    }

    @Override
    protected List<Object> retrieveAllEntities(PersistentEntity pe, Serializable[] keys) {
        return retrieveAllEntities(pe, Arrays.asList(keys));
    }

    @Override
    protected List<Object> retrieveAllEntities(PersistentEntity pe, Iterable<Serializable> keys) {
        List<Criterion> criterions = new ArrayList<>(1);
        criterions.add(new In(GormProperties.IDENTITY, DefaultGroovyMethods.toList(keys)));
        Junction junction = new Conjunction(criterions);
        return new Neo4jQuery(getSession(), pe, this).executeQuery(pe, junction);
    }

    @Override
    protected List<Serializable> persistEntities(final PersistentEntity pe, @SuppressWarnings("rawtypes") final Iterable objs) {

        GraphPersistentEntity graphPersistentEntity = (GraphPersistentEntity) pe;

        List<Serializable> idList = new ArrayList<>();
        if(graphPersistentEntity.hasDynamicAssociations()) {
            // dynamic association entities cannot be batch inserted
            for (Object obj : objs) {
                Serializable id = persistEntity(pe, obj);
                if(id != null) {
                    idList.add(id);
                }
            }
        }
        else if(graphPersistentEntity.isNativeId() && !graphPersistentEntity.isRelationshipEntity()) {
            List<EntityAccess> entityAccesses = new ArrayList<>();
            // optimize batch inserts for multiple entities with native id
            final Neo4jSession session = getSession();

            StringBuilder createCypher = new StringBuilder(CypherBuilder.CYPHER_CREATE);
            int listIndex = 0;
            List<PendingOperation<Object,Serializable>> cascadingOperations = new ArrayList<>();
            final Map<String, Object> params = new HashMap<>(1);
            final Map<Integer, Integer> indexMap = new HashMap<>();
            int insertIndex = 0;
            final Iterator iterator = objs.iterator();
            boolean previous = false;
            while (iterator.hasNext()) {
                Object obj = iterator.next();
                listIndex++;
                if (shouldIgnore(session, obj)) {
                    EntityAccess entityAccess = createEntityAccess(pe, obj);
                    idList.add((Serializable) entityAccess.getIdentifier());
                    continue;
                }

                final EntityAccess entityAccess = createEntityAccess(pe, obj);
                GraphPersistentEntity persistentEntity = (GraphPersistentEntity) entityAccess.getPersistentEntity();
                if (getMappingContext().getProxyFactory().isProxy(obj)) {
                    idList.add(((EntityProxy) obj).getProxyKey());
                    continue;
                }

                session.registerPending(obj);

                Serializable identifier = (Serializable) entityAccess.getIdentifier();
                boolean isUpdate = identifier != null;
                if (isUpdate) {

                    registerPendingUpdate(session, persistentEntity, entityAccess, obj, identifier);
                    idList.add(identifier);
                }
                else {
                    final PendingInsertAdapter<Object, Serializable> pendingInsert = new PendingInsertAdapter<Object, Serializable>(persistentEntity, identifier, obj, entityAccess) {
                        @Override
                        public void run() {
                            if (cancelInsert(pe, entityAccess)) {
                                setVetoed(true);
                            }
                        }

                        @Override
                        public Serializable getNativeKey() {
                            return (Serializable) entityAccess.getIdentifier();
                        }
                    };
                    pendingInsert.addCascadeOperation(new PendingOperationAdapter<Object, Serializable>(persistentEntity, identifier, obj) {
                        @Override
                        public void run() {
                            firePostInsertEvent(pe, entityAccess);
                        }
                    });

                    cascadingOperations.addAll(pendingInsert.getCascadeOperations());
                    final List<PendingOperation<Object, Serializable>> preOperations = pendingInsert.getPreOperations();
                    for (PendingOperation preOperation : preOperations) {
                        preOperation.run();
                    }

                    pendingInsert.run();
                    pendingInsert.setExecuted(true);

                    // temporarily add null so it is replaced later
                    idList.add(null);

                    if(pendingInsert.isVetoed()) {
                        continue;
                    }

                    session.addPendingInsert(pendingInsert);

                    indexMap.put(insertIndex++, listIndex - 1);
                    entityAccesses.add(entityAccess);
                    if(previous) {
                        createCypher.append(CypherBuilder.COMMAND_SEPARATOR);
                    }
                    session.buildEntityCreateOperation(createCypher, String.valueOf(insertIndex), entityAccess.getPersistentEntity(), pendingInsert, params, cascadingOperations);
                    if(iterator.hasNext()) {
                        previous = true;
                    }

                }

            }
            if(insertIndex > 0) {

                QueryRunner statementRunner = session.hasTransaction() ? session.getTransaction().getNativeTransaction() : session.getNativeInterface();

                final String finalCypher = createCypher.toString() + " RETURN *";
                if(log.isDebugEnabled()) {
                    log.debug("CREATE Cypher [{}] for parameters [{}]", finalCypher, params);
                }

                if(graphPersistentEntity.hasDynamicAssociations()) {
                    params.remove(DYNAMIC_ASSOCIATION_PARAM);
                }

                final Result result = statementRunner.run(finalCypher, params);

                if(!result.hasNext()) {
                    throw new IdentityGenerationException("CREATE operation did not generate an identifier for entity " + pe.getJavaClass());
                }

                final Record record = result.next();
                for (int j = 0; j < insertIndex; j++) {
                    final Integer targetIndex = indexMap.get(j);
                    Assert.notNull(targetIndex, "Should never be null. Please file an issue");

                    final Node node = record.get("n" + (j + 1)).asNode();
                    if(node == null) {
                        throw new IdentityGenerationException("CREATE operation did not generate an identifier for entity " + pe.getJavaClass());
                    }
                    final long identifier = node.id();
                    final EntityAccess entityAccess = entityAccesses.get(j);
                    entityAccess.setIdentifier(identifier);
                    idList.set(targetIndex, identifier);
                    persistAssociationsOfEntity((GraphPersistentEntity) pe, entityAccess, false);

                }
            }
        }
        else {

            for (Object obj : objs) {
                final EntityAccess entityAccess = createEntityAccess(pe, obj);
                Serializable id = persistEntity(entityAccess.getPersistentEntity(), obj);
                if(id != null) {
                    idList.add(id);
                }
            }
        }
        return idList;
    }

    @Override
    protected Object retrieveEntity(PersistentEntity pe, Serializable key) {

        if(log.isDebugEnabled()) {
            log.debug("Retrieving entity [{}] by node id [{}]", pe.getJavaClass(), key);
        }

        final Neo4jQuery query = new Neo4jQuery(getSession(), pe, this);
        query.idEq(key);
        return query.max(1).singleResult();
    }

    public Object unmarshallOrFromCache(PersistentEntity defaultPersistentEntity, Map<String, Object> resultData) {

        Node data = (Node)resultData.get(CypherBuilder.NODE_DATA);
        return unmarshallOrFromCache(defaultPersistentEntity, data, resultData);
    }

    public Object unmarshallOrFromCache(PersistentEntity defaultPersistentEntity, Node data) {
        return unmarshallOrFromCache(defaultPersistentEntity, data, Collections.<String,Object>emptyMap());
    }

    public Object unmarshallOrFromCache(PersistentEntity defaultPersistentEntity, Node data, Map<String, Object> resultData) {
        return unmarshallOrFromCache(defaultPersistentEntity, data, resultData, Collections.<Association, Object>emptyMap());
    }

    public Object unmarshallOrFromCache(PersistentEntity defaultPersistentEntity, Node data, Map<String, Object> resultData, Map<Association, Object> initializedAssociations ) {
        return unmarshallOrFromCache(defaultPersistentEntity, data, resultData, initializedAssociations, LockModeType.NONE);
    }
    public Object unmarshallOrFromCache(PersistentEntity defaultPersistentEntity, Node data, Map<String, Object> resultData, Map<Association, Object> initializedAssociations, LockModeType lockModeType) {
        final Neo4jSession session = getSession();
        if (LockModeType.PESSIMISTIC_WRITE.equals(lockModeType)) {
            if(log.isDebugEnabled()) {
                log.debug("Locking entity [{}] node [{}] for pessimistic write", defaultPersistentEntity.getName(), data.id());
            }
            throw new UnsupportedOperationException("Write locks are not supported by the Bolt Java Driver.");
        }

        final Iterable<String> labels = data.labels();
        GraphPersistentEntity graphPersistentEntity = (GraphPersistentEntity) defaultPersistentEntity;
        PersistentEntity persistentEntity = mostSpecificPersistentEntity(defaultPersistentEntity, labels);
        final Serializable id = graphPersistentEntity.readId(data);
        Object instance = session.getCachedInstance(persistentEntity.getJavaClass(), id);

        if (instance == null) {
            instance = unmarshall(persistentEntity, id, data, resultData, initializedAssociations);
        }
        return instance;
    }


    public Object unmarshallOrFromCache(PersistentEntity entity, org.neo4j.driver.types.Relationship data, Map<Association, Object> initializedAssociations, Map<Serializable, Node> initializedNodes) {
        Object object = unmarshallOrFromCache(entity, data, initializedAssociations);
        RelationshipPersistentEntity relEntity = (RelationshipPersistentEntity) entity;
        if(object != null) {
            EntityReflector reflector = entity.getReflector();
            Object from = reflector.getProperty(object, RelationshipPersistentEntity.FROM);
            if(from == null || from instanceof EntityProxy) {
                long nodeId = data.startNodeId();
                if(initializedNodes.containsKey(nodeId)) {
                    reflector.setProperty(
                            object,
                            RelationshipPersistentEntity.FROM, unmarshallOrFromCache(relEntity.getFromEntity(), initializedNodes.get(nodeId))
                    );
                }
                else {
                    reflector.setProperty(
                            object,
                            RelationshipPersistentEntity.FROM, session.proxy( relEntity.getFrom().getType(), nodeId)
                    );
                }
            }
            Object to = reflector.getProperty(object, RelationshipPersistentEntity.TO);
            if(to == null || to instanceof EntityProxy) {
                long nodeId = data.endNodeId();
                if(initializedNodes.containsKey(nodeId)) {
                    reflector.setProperty(
                            object,
                            RelationshipPersistentEntity.TO, unmarshallOrFromCache(relEntity.getToEntity(), initializedNodes.get(nodeId))
                    );
                }
                else {
                    reflector.setProperty(
                            object,
                            RelationshipPersistentEntity.TO, session.proxy( relEntity.getTo().getType(), nodeId)
                    );
                }

            }
            reflector.setProperty(object,
                    RelationshipPersistentEntity.TYPE,
                    data.type());
        }
        return object;
    }

    public Object unmarshallOrFromCache(PersistentEntity entity, Entity data, Map<Association, Object> initializedAssociations) {
        final Neo4jSession session = getSession();
        GraphPersistentEntity graphPersistentEntity = (GraphPersistentEntity) entity;
        final Serializable id = graphPersistentEntity.readId(data);
        Object instance = session.getCachedInstance(entity.getJavaClass(), id);

        if (instance == null) {
            instance = unmarshall(entity, id, data, Collections.<String, Object>emptyMap(), initializedAssociations);
        }
        return instance;
    }

    private PersistentEntity mostSpecificPersistentEntity(PersistentEntity pe, Iterable<String> labels) {
        PersistentEntity result = null;
        int longestInheritenceChain = -1;

        for (String l: labels) {
            PersistentEntity persistentEntity = findDerivedPersistentEntityWithLabel(pe, l);
            if (persistentEntity!=null) {
                int inheritenceChain = calcInheritenceChain(persistentEntity);
                if (inheritenceChain > longestInheritenceChain) {
                    longestInheritenceChain = inheritenceChain;
                    result = persistentEntity;
                }
            }
        }
        if(result != null) {
            return result;
        }
        else {
            return pe;
        }
    }

    private PersistentEntity findDerivedPersistentEntityWithLabel(PersistentEntity parent, String label) {
        for (PersistentEntity pe: getMappingContext().getPersistentEntities()) {
            if (isInParentsChain(parent, pe)) {
                if (((GraphPersistentEntity)pe).getLabels().contains(label)) {
                    return pe;
                }
            }
        }
        return null;
    }

    private boolean isInParentsChain(PersistentEntity parent, PersistentEntity it) {
        if (it==null) {
            return false;
        } else if (it.equals(parent)) {
            return true;
        } else return isInParentsChain(parent, it.getParentEntity());
    }

    private int calcInheritenceChain(PersistentEntity current) {
        if (current == null) {
            return 0;
        } else {
            return calcInheritenceChain(current.getParentEntity()) + 1;
        }
    }


    protected Object unmarshall(PersistentEntity persistentEntity, Serializable id, Entity node, Map<String, Object> resultData, Map<Association, Object> initializedAssociations) {

        if(log.isDebugEnabled()) {
            log.debug( "unmarshalling entity [{}] with id [{}], props {}, {}", persistentEntity.getName(), id, node);
        }
        final Neo4jSession session = getSession();
        GraphPersistentEntity graphPersistentEntity = (GraphPersistentEntity) persistentEntity;
        Object instance = persistentEntity.newInstance();
        EntityAccess entityAccess = session.createEntityAccess(persistentEntity, instance);
        entityAccess.setIdentifierNoConversion(id);

        PersistentProperty nodeId = graphPersistentEntity.getNodeId();
        if( nodeId != null ) {
            ((GroovyObject)entityAccess.getEntity()).setProperty(nodeId.getName(), node.id());
        }

        final Object entity = entityAccess.getEntity();
        session.cacheInstance(persistentEntity.getJavaClass(), id, entity);

        final List<String> nodeProperties = DefaultGroovyMethods.toList(node.keys());

        for (PersistentProperty property: entityAccess.getPersistentEntity().getPersistentProperties()) {

            String propertyName = property.getName();
            if ( (property instanceof Simple) || (property instanceof TenantId) || (property instanceof Basic)) {
                // implicitly sets version property as well
                if(node.containsKey(propertyName)) {

                    entityAccess.setProperty(propertyName, node.get(propertyName).asObject());

                    nodeProperties.remove(propertyName);
                }
            } else if (property instanceof Association) {

                Association association = (Association) property;

                final String associationName = association.getName();

                if(initializedAssociations.containsKey(association)) {
                    entityAccess.setPropertyNoConversion(associationName, initializedAssociations.get(association));
                    continue;
                }

                final String associationRelKey = associationName + "Rels";
                final String associationNodesKey = associationName + "Nodes";
                final String associationIdsKey = associationName + "Ids";

                // if the node key is present we have an eager fetch, so initialise the association
                if(resultData.containsKey(associationNodesKey)) {
                    final PersistentEntity associatedEntity = association.getAssociatedEntity();
                    if (association instanceof ToOne) {
                        final Neo4jEntityPersister associationPersister = session.getEntityPersister(associatedEntity.getJavaClass());
                        final Iterable<Node> associationNodes = (Iterable<Node>) resultData.get(associationNodesKey);
                        final Node associationNode = IteratorUtil.singleOrNull(associationNodes);
                        if(associationNode != null) {
                            entityAccess.setPropertyNoConversion(
                                    associationName,
                                    associationPersister.unmarshallOrFromCache(associatedEntity, associationNode)
                            );
                        }
                    }
                    else if(association instanceof ToMany) {
                        Collection values;
                        final Class type = association.getType();

                        final Collection<Object> associationNodes;
                        boolean isRelationshipEntity = associatedEntity instanceof RelationshipPersistentEntity;
                        if(isRelationshipEntity && resultData.containsKey(associationRelKey)) {
                            associationNodes = (Collection<Object>) resultData.get(associationRelKey);
                        }
                        else {
                            associationNodes = (Collection<Object>) resultData.get(associationNodesKey);
                        }
                        final Neo4jResultList resultSet = new Neo4jResultList(0, associationNodes.size(), associationNodes.iterator(), session.getEntityPersister(associatedEntity));
                        if(isRelationshipEntity) {
                            RelationshipPersistentEntity relEntity = (RelationshipPersistentEntity) associatedEntity;
                            Association from = relEntity.getFrom();
                            Association to = relEntity.getTo();
                            handleRelationshipSide(persistentEntity, resultData, entity, associationNodesKey, resultSet, from);
                            handleRelationshipSide(persistentEntity, resultData, entity, associationNodesKey, resultSet, to);
                        }
                        else if(association.isBidirectional()) {
                            final Association inverseSide = association.getInverseSide();
                            if(inverseSide instanceof ToOne) {
                                resultSet.setInitializedAssociations(Collections.singletonMap(
                                        inverseSide, entity
                                ));
                            }
                        }
                        if(List.class.isAssignableFrom(type)) {
                            values = new Neo4jList(entityAccess, association, resultSet, session);
                        }
                        else if(SortedSet.class.isAssignableFrom(type)) {
                            values = new Neo4jSortedSet(entityAccess, association, new TreeSet<>(resultSet), session);
                        }
                        else {
                            values = new Neo4jSet(entityAccess, association, new HashSet<>(resultSet), session);
                        }
                        entityAccess.setPropertyNoConversion(propertyName, values);
                    }
                }
                else if(resultData.containsKey(associationIdsKey)) {

                    final Object associationValues = resultData.get(associationIdsKey);
                    List<Serializable> targetIds = Collections.emptyList();
                    if(associationValues instanceof Collection) {
                        targetIds = (List<Serializable>) associationValues;
                    }
                    if (association instanceof ToOne) {
                        ToOne toOne = (ToOne) association;
                        if (!targetIds.isEmpty()) {
                            Serializable targetId;
                            try {
                                targetId = IteratorUtil.singleOrNull(targetIds);
                            } catch (NoSuchElementException e) {
                                throw new DataIntegrityViolationException("Single-ended association has more than one associated identifier: " + association);
                            }
                            entityAccess.setPropertyNoConversion(propertyName,
                                    getMappingContext().getProxyFactory().createProxy(
                                            this.session,
                                            toOne.getAssociatedEntity().getJavaClass(),
                                            targetId
                                    )
                            );
                        }
                    } else if (association instanceof ToMany) {

                        Collection values;
                        final Class type = association.getType();
                        if(List.class.isAssignableFrom(type)) {
                            values = new Neo4jPersistentList(targetIds, session, entityAccess, (ToMany) association);
                        }
                        else if(SortedSet.class.isAssignableFrom(type)) {
                            values = new Neo4jPersistentSortedSet(targetIds, session, entityAccess, (ToMany) association);
                        }
                        else {
                            values = new Neo4jPersistentSet(targetIds, session, entityAccess, (ToMany) association);
                        }
                        entityAccess.setPropertyNoConversion(propertyName, values);
                    } else {
                        throw new IllegalArgumentException("association " + associationName + " is of type " + association.getClass().getSuperclass().getName());
                    }
                }
                else {
                    // No OPTIONAL MATCH specified so the association queries are lazily executed
                    if(association instanceof ToOne) {
                        // first check whether the object has already been loaded from the cache

                        // if a lazy proxy should be created for this association then create it,
                        // note that this strategy does not allow for null checks
                        final Neo4jAssociationQueryExecutor associationQueryExecutor = new Neo4jAssociationQueryExecutor(session, association);
                        if(association.getMapping().getMappedForm().getFetchStrategy() == FetchType.LAZY) {
                            final Object proxy = getMappingContext().getProxyFactory().createProxy(
                                    this.session,
                                    associationQueryExecutor,
                                    id
                            );
                            entityAccess.setPropertyNoConversion(propertyName,
                                    proxy
                            );
                        }
                        else {
                            final List<Object> results = associationQueryExecutor.query(id);
                            if(!results.isEmpty()) {
                                entityAccess.setPropertyNoConversion(propertyName, results.get(0));
                            }
                        }
                    }
                    else if(association instanceof ToMany) {
                        Collection values;
                        final Class type = association.getType();
                        if(List.class.isAssignableFrom(type)) {
                            values = new Neo4jPersistentList(id, session, entityAccess, (ToMany) association);
                        }
                        else if(SortedSet.class.isAssignableFrom(type)) {
                            values = new Neo4jPersistentSortedSet(id, session, entityAccess, (ToMany) association);
                        }
                        else {
                            values = new Neo4jPersistentSet(id, session, entityAccess, (ToMany) association);
                        }
                        entityAccess.setPropertyNoConversion(propertyName, values);
                    }
                }


            } else {
                throw new IllegalArgumentException("property " + property.getName() + " is of type " + property.getClass().getSuperclass().getName());

            }
        }

        Map<String,Object> undeclared = new LinkedHashMap<>();

        if (!nodeProperties.isEmpty()) {
            for (String nodeProperty : nodeProperties) {
                if(!nodeProperty.equals(CypherBuilder.IDENTIFIER)) {
                    undeclared.put(nodeProperty, node.get(nodeProperty).asObject());
                }
            }
        }

        final Object obj = entity;
        if(!undeclared.isEmpty()) {
            if(obj instanceof DynamicAttributes) {
                ((DynamicAttributes)obj).attributes(undeclared);
            }
        }

        firePostLoadEvent(entityAccess.getPersistentEntity(), entityAccess);
        return obj;
    }

    private void handleRelationshipSide(PersistentEntity persistentEntity, Map<String, Object> resultData, Object entity, String associationNodesKey, Neo4jResultList resultSet, Association association) {
        if (persistentEntity.equals(association.getAssociatedEntity())) {
            resultSet.setInitializedAssociations(Collections.singletonMap(
                    association, entity
            ));
            // the associated nodes are the inverse nodes, pass those through to avoid additional queries
            if(resultData.containsKey(associationNodesKey)) {
                Collection<Node> nodes = (Collection<Node>) resultData.get(associationNodesKey);
                for (Node n : nodes) {
                    resultSet.addInitializedNode(n);
                }
            }
        }
    }

    private Collection createCollection(Association association) {
        return association.isList() ? new ArrayList() : new HashSet();
    }

    private Collection createDirtyCheckableAwareCollection(EntityAccess entityAccess, Association association, Collection delegate) {
        if (delegate==null) {
            delegate = createCollection(association);
        }

        if( !(delegate instanceof DirtyCheckableCollection)) {

            final Object entity = entityAccess.getEntity();
            if(entity instanceof DirtyCheckable) {
                final Neo4jSession session = getSession();
                for( Object o : delegate ) {
                    final EntityAccess associationAccess = getSession().createEntityAccess(association.getAssociatedEntity(), o);
                    Serializable associationId = (Serializable) associationAccess.getIdentifier();
                    if(associationId != null) {
                        session.addPendingRelationshipInsert((Serializable) entityAccess.getIdentifier(), association, associationId);
                    }
                }

                delegate = association.isList() ?
                        new Neo4jList(entityAccess, association, (List)delegate, session) :
                        new Neo4jSet(entityAccess, association, (Set)delegate, session);
            }
        }
        else {
            final DirtyCheckableCollection dirtyCheckableCollection = (DirtyCheckableCollection) delegate;
            final Neo4jSession session = getSession();
            if(dirtyCheckableCollection.hasChanged()) {
                for (Object o : ((Iterable)dirtyCheckableCollection)) {
                    final EntityAccess associationAccess = getSession().createEntityAccess(association.getAssociatedEntity(), o);
                    session.addPendingRelationshipInsert((Serializable) entityAccess.getIdentifier(),association, (Serializable) associationAccess.getIdentifier());
                }
            }
        }
        return delegate;
    }


    @Override
    protected Serializable persistEntity(final PersistentEntity entity, Object obj, boolean isInsert) {
        if (obj == null) {
            throw new IllegalStateException("obj is null");
        }

        final Neo4jSession session = getSession();
        if (shouldIgnore(session, obj)) {
            EntityAccess entityAccess = createEntityAccess(entity, obj);
            return (Serializable) entityAccess.getIdentifier();
        }

        if (getMappingContext().getProxyFactory().isProxy(obj)) {
            return ((EntityProxy) obj).getProxyKey();
        }

        final EntityAccess entityAccess = createEntityAccess(entity, obj);
        Object identifier = entityAccess.getIdentifier();


        session.registerPending(obj);

        // cancel operation if vetoed
        boolean isUpdate = identifier != null && !isInsert;
        GraphPersistentEntity graphPersistentEntity = (GraphPersistentEntity) entity;
        boolean assignedId = graphPersistentEntity.isAssignedId();
        if(assignedId && !session.contains(obj)) {
            isUpdate = false;
        }

        if (isUpdate) {
            registerPendingUpdate(session, entity, entityAccess, obj, (Serializable) identifier);
        } else {

            boolean isNativeId = false;
            if(identifier == null) {

                final IdGenerator idGenerator = graphPersistentEntity.getIdGenerator();
                isNativeId = idGenerator == null;
                if(!isNativeId) {

                    identifier = idGenerator.nextId();
                    entityAccess.setIdentifier(identifier);
                }
            }

            final PendingInsertAdapter<Object, Serializable> pendingInsert = new PendingInsertAdapter<Object, Serializable>(entity, (Serializable) identifier, obj, entityAccess) {
                @Override
                public void run() {
                    if (cancelInsert(entity, entityAccess)) {
                        setVetoed(true);
                    }
                }

                @Override
                public Serializable getNativeKey() {
                    return (Serializable) entityAccess.getIdentifier();
                }
            };
            pendingInsert.addCascadeOperation(new PendingOperationAdapter<Object, Serializable>(entity, (Serializable) identifier, obj) {
                @Override
                public void run() {
                    firePostInsertEvent(entity, entityAccess);
                }
            });

            if(isNativeId && !graphPersistentEntity.isRelationshipEntity()) {
                // if we have a native identifier then we have to perform an insert to obtain the id
                final List<PendingOperation<Object, Serializable>> preOperations = pendingInsert.getPreOperations();
                for (PendingOperation preOperation : preOperations) {
                    preOperation.run();
                }

                pendingInsert.run();
                pendingInsert.setExecuted(true);

                if(pendingInsert.isVetoed()) {
                    return null;
                }

                final Map<String, Object> params = new HashMap<>(1);

                final String cypher = session.buildEntityCreateOperation(entity, pendingInsert, params, pendingInsert.getCascadeOperations());
                Map<String, List<Object>> dynamicAssociations = null;
                if(graphPersistentEntity.hasDynamicAssociations()) {
                    dynamicAssociations = (Map<String, List<Object>>) params.remove(DYNAMIC_ASSOCIATION_PARAM);
                }
                final QueryRunner boltSession = session.hasTransaction() ? session.getTransaction().getNativeTransaction() : session.getNativeInterface();
                final String finalCypher = cypher + graphPersistentEntity.formatReturnId();
                if(log.isDebugEnabled()) {
                    log.debug("CREATE Cypher [{}] for parameters [{}]", finalCypher, params);
                }
                final Result result = boltSession.run(finalCypher, params);

                if(!result.hasNext()) {
                    throw new IdentityGenerationException("CREATE operation did not generate an identifier for entity " + entityAccess.getEntity());
                }

                Record idMap = result.next();
                if(idMap != null) {
                    identifier = idMap.get(GormProperties.IDENTITY).asObject();
                    if(identifier == null) {
                        throw new IdentityGenerationException("CREATE operation did not generate an identifier for entity " + entityAccess.getEntity());
                    }
                    entityAccess.setIdentifier(identifier);
                    if(obj instanceof DirtyCheckable) {
                        ((DirtyCheckable)obj).trackChanges();
                    }
                    persistAssociationsOfEntity(graphPersistentEntity, entityAccess, false);
                    if(dynamicAssociations != null) {
                        processDynamicAssociations(graphPersistentEntity, entityAccess, (Neo4jMappingContext) getMappingContext(),dynamicAssociations, pendingInsert.getCascadeOperations(), false);
                    }
                }
                else {
                    throw new IdentityGenerationException("CREATE operation did not generate an identifier for entity " + entityAccess.getEntity());
                }
                session.addPendingInsert(pendingInsert);
            }
            else {
                session.addPendingInsert(pendingInsert);
                persistAssociationsOfEntity((GraphPersistentEntity) entity, entityAccess, false);
            }

        }

        return (Serializable) identifier;
    }

    @Override
    protected Serializable persistEntity(final PersistentEntity pe, Object obj) {
        return persistEntity(pe, obj, false);
    }


    private void registerPendingUpdate(Neo4jSession session, final PersistentEntity pe, final EntityAccess ea, final Object obj, final Serializable identifier) {
        final Neo4jModificationTrackingEntityAccess entityAccess = new Neo4jModificationTrackingEntityAccess(ea);
        final Object notEqualMarker = new Object();
        final PendingUpdateAdapter<Object, Serializable> pendingUpdate = new PendingUpdateAdapter<Object, Serializable>(pe, identifier, obj, ea) {
            @Override
            public void run() {
                if (cancelUpdate(pe, entityAccess)) {
                    setVetoed(true);
                } else {
                    Object entity = entityAccess.getEntity();
                    if (entity instanceof DirtyCheckable) {
                        DirtyCheckable dirtyCheckable = (DirtyCheckable) entity;
                        for (Map.Entry<String, Object> entry: entityAccess.getModifiedProperties().entrySet()) {
                            dirtyCheckable.markDirty(entry.getKey(), notEqualMarker, entry.getValue());
                        }
                    }
                }
            }
        };
        pendingUpdate.addCascadeOperation(new PendingOperationAdapter<Object, Serializable>(pe, identifier, obj) {
            @Override
            public void run() {
                firePostUpdateEvent(pe, ea);
            }
        });
        session.addPendingUpdate(pendingUpdate);

        persistAssociationsOfEntity((GraphPersistentEntity) pe, ea, true);
    }

    private boolean shouldIgnore(Neo4jSession session, Object obj) {
        boolean isDirty = obj instanceof DirtyCheckable ? ((DirtyCheckable)obj).hasChanged() : true;
        return session.isPendingAlready(obj) || (!isDirty);
    }

    public void processDynamicAssociations(GraphPersistentEntity graphEntity, EntityAccess access, Neo4jMappingContext mappingContext, Map<String, List<Object>> dynamicRelProps, List<PendingOperation<Object, Serializable>> cascadingOperations, boolean isUpdate) {
        if(graphEntity.hasDynamicAssociations()) {
            Serializable parentId = (Serializable) access.getIdentifier();
            for (final Map.Entry<String, List<Object>> e: dynamicRelProps.entrySet()) {
                for (final Object o :  e.getValue()) {

                    if (((DirtyCheckable)access.getEntity()).hasChanged(e.getKey()) || !isUpdate) {
                        final GraphPersistentEntity associated = (GraphPersistentEntity) mappingContext.getPersistentEntity(o.getClass().getName());
                        if (associated != null) {
                            Object childId = getSession().getEntityPersister(o).getObjectIdentifier(o);
                            if (childId == null) {
                                childId = persist(o);
                            }
                            getSession().
                                addPendingRelationshipInsert(parentId,
                                    new DynamicToOneAssociation(graphEntity, mappingContext, e.getKey(), associated),
                                    (Serializable) childId);
                        }
                    }
                }
            }
        }
    }

    private void persistAssociationsOfEntity(GraphPersistentEntity graphEntity, EntityAccess entityAccess, boolean isUpdate) {

        Object obj = entityAccess.getEntity();
        DirtyCheckable dirtyCheckable = null;
        if (obj instanceof DirtyCheckable) {
            dirtyCheckable = (DirtyCheckable)obj;
        }

        Neo4jSession neo4jSession = getSession();
        if(graphEntity.hasDynamicAssociations()) {
            MappingContext mappingContext = graphEntity.getMappingContext();
            DynamicAttributes dynamicAttributes = (DynamicAttributes) obj;
            Map<String, Object> attributes = dynamicAttributes.attributes();
            for (Map.Entry<String, Object> entry : attributes.entrySet()) {
                Object value = entry.getValue();
                if(value == null) {
                    String associationName = entry.getKey();
                    Object originalValue = ((DirtyCheckable) obj).getOriginalValue(associationName);
                    if(originalValue != null && mappingContext.isPersistentEntity(originalValue)) {
                        processDynamicAssociationRemoval(graphEntity, entityAccess, neo4jSession, mappingContext, associationName, originalValue);
                    }
                    else if(originalValue instanceof Iterable) {
                        Iterable i = (Iterable) originalValue;
                        for (Object o : i) {
                            processDynamicAssociationRemoval(graphEntity, entityAccess, neo4jSession, mappingContext, associationName, o);
                        }
                    }
                }
                else if(mappingContext.isPersistentEntity(value)) {
                    if( ((DirtyCheckable)value).hasChanged() ) {
                        neo4jSession.persist(value);
                    }
                }
                else if(value instanceof Iterable) {
                    boolean collectionChanged = false;
                    for(Object o : ((Iterable)value)) {
                        if(mappingContext.isPersistentEntity(o)) {
                            collectionChanged = ((DirtyCheckable)o).hasChanged();
                            if(collectionChanged) break;
                        }
                    }

                    if(collectionChanged) {
                        neo4jSession.persist((Iterable)value);
                    }
                }
            }
        }

        for (PersistentProperty persistentProperty : graphEntity.getAssociations()) {
            String propertyName = persistentProperty.getName();
            if(persistentProperty instanceof Basic) {
                continue;
            }

            if ((!isUpdate) || ((dirtyCheckable!=null) && dirtyCheckable.hasChanged(propertyName))) {

                Object propertyValue = entityAccess.getProperty(propertyName);

                if ((persistentProperty instanceof OneToMany) || (persistentProperty instanceof ManyToMany)) {
                    Association association = (Association) persistentProperty;

                    if (propertyValue!= null) {

                        if(propertyValue instanceof PersistentCollection) {
                            PersistentCollection pc = (PersistentCollection) propertyValue;
                            if(!pc.isInitialized()) continue;
                        }

                        if (association.isBidirectional()) {
                            // Populate other side of bidi
                            for (Object associatedObject: (Iterable)propertyValue) {
                                EntityAccess assocEntityAccess = createEntityAccess(association.getAssociatedEntity(), associatedObject);
                                String referencedPropertyName = association.getReferencedPropertyName();
                                if(association instanceof ManyToMany) {
                                    ((GormEntity)associatedObject).addTo(referencedPropertyName, obj);
                                }
                                else {
                                    assocEntityAccess.setPropertyNoConversion(referencedPropertyName, obj);
                                    ((DirtyCheckable)associatedObject).markDirty(referencedPropertyName);
                                }
                            }
                        }

                        Collection targets = (Collection) propertyValue;
                        persistEntities(association.getAssociatedEntity(), targets);

                        boolean reversed = RelationshipUtils.useReversedMappingFor(association);

                        if (!reversed) {
                            Collection dcc = createDirtyCheckableAwareCollection(entityAccess, association, targets);
                            entityAccess.setProperty(association.getName(), dcc);
                        }
                    }
                } else if (persistentProperty instanceof ToOne) {
                    if (propertyValue != null) {
                        ToOne to = (ToOne) persistentProperty;

                        if (to.isBidirectional()) {  // Populate other side of bidi
                            EntityAccess assocEntityAccess = createEntityAccess(to.getAssociatedEntity(), propertyValue);
                            if (to instanceof OneToOne) {
                                assocEntityAccess.setProperty(to.getReferencedPropertyName(), obj);
                            } else {
                                Collection collection = (Collection) assocEntityAccess.getProperty(to.getReferencedPropertyName());
                                if (collection == null ) {
                                    collection = new ArrayList();
                                    assocEntityAccess.setProperty(to.getReferencedPropertyName(), collection);
                                }
                                if(proxyFactory.isInitialized(collection) && !collection.isEmpty()) {
                                    boolean found = false;
                                    for (Object o : collection) {
                                        if(o.equals(obj)) {
                                            found = true; break;
                                        }
                                    }
                                    if (!found) {
                                        collection.add(obj);
                                    }
                                }
                                else {
                                    collection.add(obj);
                                }
                            }
                        }

                        persistEntity(to.getAssociatedEntity(), propertyValue);


                        Serializable thisId = (Serializable) entityAccess.getIdentifier();
                        final EntityAccess associationAccess = neo4jSession.createEntityAccess(to.getAssociatedEntity(), propertyValue);
                        Serializable associationId = (Serializable) associationAccess.getIdentifier();

                        if(graphEntity.isRelationshipEntity() && propertyName.equals(RelationshipPersistentEntity.FROM)) {
                            RelationshipPersistentEntity relEntity = (RelationshipPersistentEntity) graphEntity;
                            // reverse the ids to correctly align from and to
                            thisId = associationId;
                            Object toValue = entityAccess.getProperty(RelationshipPersistentEntity.TO);
                            if(toValue != null) {
                                GraphPersistentEntity toEntity = relEntity.getToEntity();
                                associationId = toEntity.getReflector().getIdentifier(toValue);
                                if(associationId == null) {
                                    associationId = persistEntity(toEntity,toValue);
                                }
                            }

                        }
                        if(thisId != null && associationId != null) {
                            boolean reversed = RelationshipUtils.useReversedMappingFor(to);
                            if (reversed) {
                                Association inverseSide = to.getInverseSide();
                                if(inverseSide != null) {
                                    neo4jSession.addPendingRelationshipInsert(associationId, inverseSide, thisId);
                                }
                            }
                            else {
                                neo4jSession.addPendingRelationshipInsert(thisId, to, associationId);
                            }
                        }


                    }
                    else if(isUpdate) {
                        Object previousValue = dirtyCheckable.getOriginalValue(propertyName);
                        if (previousValue != null) {
                            ToOne to = (ToOne) persistentProperty;
                            Serializable associationId = neo4jSession.getEntityPersister(previousValue).getObjectIdentifier(previousValue);
                            if (associationId != null) {
                                neo4jSession.addPendingRelationshipDelete((Serializable) entityAccess.getIdentifier(), to, associationId);
                            }
                        }
                    }
                } else {
                    throw new IllegalArgumentException("GORM for Neo4j doesn't support properties of the given type " + persistentProperty + "(" + persistentProperty.getClass().getSuperclass() +")" );
                }
            }


        }
    }

    private void processDynamicAssociationRemoval(GraphPersistentEntity graphEntity, EntityAccess entityAccess, Neo4jSession neo4jSession, MappingContext mappingContext, String associationName, Object originalValue) {
        final GraphPersistentEntity associated = (GraphPersistentEntity) mappingContext.getPersistentEntity(originalValue.getClass().getName());
        if (associated != null) {

            Object childId = getSession().getEntityPersister(originalValue).getObjectIdentifier(originalValue);
            if(childId != null) {
                Serializable parentId = (Serializable) entityAccess.getIdentifier();
                neo4jSession.addPendingRelationshipDelete(parentId,new DynamicToOneAssociation(graphEntity, mappingContext, associationName, associated), (Serializable)childId );
            }
        }
    }

    @Override
    protected void deleteEntity(final PersistentEntity pe, final Object obj) {
        final EntityAccess entityAccess = createEntityAccess(pe, obj);


        final Neo4jSession session = getSession();
        session.clear(obj);
        final PendingDeleteAdapter pendingDelete = createPendingDeleteOne(session, pe, entityAccess, obj);
        pendingDelete.addCascadeOperation(new PendingOperationAdapter<Object, Serializable>(pe, (Serializable) entityAccess.getIdentifier(), obj) {
            @Override
            public void run() {
                firePostDeleteEvent(pe, entityAccess);
            }
        });
        session.addPendingDelete(pendingDelete);

    }

    @SuppressWarnings("unchecked")
    private PendingDeleteAdapter createPendingDeleteOne(final Neo4jSession session, final PersistentEntity pe, final EntityAccess entityAccess, final Object obj) {
        return new PendingDeleteAdapter(pe, entityAccess.getIdentifier(), obj) {
            @Override
            public void run() {
                if (cancelDelete(pe, entityAccess)) {
                    setVetoed(true);
                }
            }
        };
    }



    @Override
    protected void deleteEntities(PersistentEntity pe, @SuppressWarnings("rawtypes") Iterable objects) {
        for (Object object : objects) {
            deleteEntity(pe, object);
        }
    }

    @Override
    public Query createQuery() {
        return new Neo4jQuery(getSession(), getPersistentEntity(), this);
    }

    @Override
    public Serializable refresh(Object o) {
        throw new UnsupportedOperationException();
    }

    public static long countUpdates(Result execute) {
        ResultSummary resultSummary = execute.consume();
        SummaryCounters counters = resultSummary.counters();
        if (counters.containsUpdates()) {
            return counters.nodesCreated() + counters.nodesDeleted() + counters.propertiesSet() + counters.relationshipsCreated() + counters.relationshipsDeleted();
        } else {
            return 0;
        }
    }
}
