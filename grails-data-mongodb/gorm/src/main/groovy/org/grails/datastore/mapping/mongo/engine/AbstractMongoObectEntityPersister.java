package org.grails.datastore.mapping.mongo.engine;

import com.mongodb.DBRef;
import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.core.SessionImplementor;
import org.grails.datastore.mapping.engine.*;
import org.grails.datastore.mapping.model.*;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.Identity;
import org.grails.datastore.mapping.model.types.ManyToMany;
import org.grails.datastore.mapping.mongo.MongoSession;
import org.grails.datastore.mapping.mongo.config.MongoAttribute;
import org.grails.datastore.mapping.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.convert.ConversionService;

import java.io.Serializable;
import java.util.*;

/**
 * Abstract implementation of MongoDB mongo object mapping entity persister.
 *
 * This entity persister converts to and from MongoDB BSON object types (either Document or DBObject)
 *
 * @author Graeme Rocher
 * @since 5.0
 */
public abstract class AbstractMongoObectEntityPersister<T> extends NativeEntryEntityPersister<T, Object> {
    public static final String INSTANCE_PREFIX = "instance:";
    public static final String MONGO_ID_FIELD = "_id";
    public static final String MONGO_CLASS_FIELD = "_class";
    protected static final String NEXT_ID_SUFFIX = ".next_id";
    static Logger log = LoggerFactory.getLogger(AbstractMongoObectEntityPersister.class);
    protected boolean hasNumericalIdentifier = false;
    protected boolean hasStringIdentifier = false;

    public AbstractMongoObectEntityPersister(MappingContext mappingContext, PersistentEntity entity, Session session, ApplicationEventPublisher publisher) {
        super(mappingContext, entity, session, publisher);
        if (!(entity instanceof EmbeddedPersistentEntity)) {

            PersistentProperty identity = entity.getIdentity();
            if (identity != null) {
                hasNumericalIdentifier = Long.class.isAssignableFrom(identity.getType());
                hasStringIdentifier = String.class.isAssignableFrom(identity.getType());
            }
        }
    }

    public static String createInstanceCacheEntryKey(Object instance) {
        return INSTANCE_PREFIX + System.identityHashCode(instance);
    }

    @Override
    public String getEntityFamily() {
        return getMongoSession().getCollectionName(getPersistentEntity());
    }


    public MongoSession getMongoSession() {
        return (MongoSession) getSession();
    }

    public String getCollectionName(PersistentEntity persistentEntity) {
        return getCollectionName(persistentEntity, null);
    }

    @Override
    public AssociationIndexer getAssociationIndexer(T nativeEntry, Association association) {
        return new MongoAssociationIndexer(nativeEntry, association, (MongoSession) session);
    }


    @Override
    public PropertyValueIndexer getPropertyIndexer(PersistentProperty property) {
        // We don't need to implement this for Mongo since Mongo automatically creates indexes for us
        return null;
    }

    @Override
    public boolean isDirty(Object instance, Object entry) {
        if (super.isDirty(instance, entry)) {
            return true;
        }

        T dbo = (T)entry;
        PersistentEntity entity = getPersistentEntity();

        EntityAccess entityAccess = createEntityAccess(entity, instance, dbo);

        T cached = (T)((SessionImplementor<?>)getSession()).getCachedEntry(
                entity, (Serializable)entityAccess.getIdentifier(), true);

        return !dbo.equals(cached);
    }

    @Override
    protected T getEmbedded(T nativeEntry, String key) {
        final Object embeddedDocument = getValueRetrievalStrategy().getValue(nativeEntry, key);
        if (isEmbeddedEntry(embeddedDocument)) {
            return (T) embeddedDocument;
        }
        return null;
    }

    @Override
    protected boolean doesRequirePropertyIndexing() {
        return false;
    }

    @Override
    protected void setEmbedded(T nativeEntry, String key, T embeddedEntry) {
        getValueRetrievalStrategy().setValue(nativeEntry, key, embeddedEntry);
    }


    @Override
    protected void cascadeDeleteCollection(EntityAccess entityAccess, Association association) {
        Object propValue = entityAccess.getProperty(association.getName());
        if (!(propValue instanceof Collection)) {
            return;
        }
        Collection collection = ((Collection) propValue);
        Persister persister = null;
        for (Iterator iter = collection.iterator(); iter.hasNext(); ) {
            Object child = iter.next();
            if (child == null) {
                log.warn("Encountered a null associated reference while cascade-deleting '{}' as part of {} (ID {})",
                        association.getReferencedPropertyName(), entityAccess.getEntity().getClass().getName(), entityAccess.getIdentifier());
                continue;
            }
            if(persister == null) {
                persister = session.getPersister(child);
            }
            persister.delete(child);
            iter.remove();
        }
    }

    @Override
    protected void setEmbeddedCollection(final T nativeEntry, final String key, Collection<?> instances, List<T> embeddedEntries) {
        final ValueRetrievalStrategy<T> valueRetrievalStrategy = getValueRetrievalStrategy();
        if (instances == null || instances.isEmpty()) {
            valueRetrievalStrategy.setValue(nativeEntry, key, null);
            return;
        }

        valueRetrievalStrategy.setValue(nativeEntry, key, embeddedEntries);
    }

    @Override
    protected void setEmbeddedMap(T nativeEntry, String key, Map instances, Map<Object, T> embeddedEntries) {
        final ValueRetrievalStrategy<T> valueRetrievalStrategy = getValueRetrievalStrategy();
        if (instances == null || instances.isEmpty()) {
            valueRetrievalStrategy.setValue(nativeEntry, key, null);
            return;
        }

        valueRetrievalStrategy.setValue(nativeEntry, key, embeddedEntries);
    }

    @Override
    protected void setEmbeddedCollectionKeys(Association association, EntityAccess embeddedEntityAccess, T embeddedEntry, List<Serializable> keys) {
        List dbRefs = new ArrayList();
        boolean reference = isReference(association);
        for (Object foreignKey : keys) {
            if (reference) {
                dbRefs.add(new DBRef(getCollectionName(association.getAssociatedEntity()), foreignKey));
            } else {
                dbRefs.add(foreignKey);
            }
        }
        getValueRetrievalStrategy().setValue(embeddedEntry, association.getName(), dbRefs);
    }
    /**
     * Implementors who want to support one-to-many associations embedded should implement this method
     *
     * @param association The association
     * @param ea
     * @param nativeEntry
     * @return A list of keys loaded from the embedded instance
     */
    @Override
    protected List loadEmbeddedCollectionKeys(Association association, EntityAccess ea, T nativeEntry) {
        if (nativeEntry == null) {
            return super.loadEmbeddedCollectionKeys(association, ea, nativeEntry);
        }

        final ValueRetrievalStrategy<T> valueRetrievalStrategy = getValueRetrievalStrategy();
        Object entry = valueRetrievalStrategy.getValue(nativeEntry, getPropertyKey(association));
        List keys = new ArrayList();
        if (entry instanceof List) {
            List entries = (List) entry;
            for (Object o : entries) {
                if (o instanceof DBRef) {
                    DBRef dbref = (DBRef) o;
                    keys.add(dbref.getId());
                }
                else if (o != null) {
                    keys.add(o);
                }
                else {
                    keys.add(null);
                }
            }
        }
        return keys;
    }

    @Override
    protected List<Object> retrieveAllEntities(PersistentEntity persistentEntity,
                                               Iterable<Serializable> keys) {

        Query query = session.createQuery(persistentEntity.getJavaClass());

        PersistentProperty identity = persistentEntity.getIdentity();
        if (keys instanceof List) {
            List actualKeys = new ArrayList();
            Iterator iterator = keys.iterator();
            while (iterator.hasNext()) {
                Object key = iterator.next();
                Object id = getIdentifierForKey(key);
                actualKeys.add(id);

            }
            query.in(identity.getName(), actualKeys);
        }
        else {
            List<Serializable> keyList = new ArrayList<Serializable>();
            for (Serializable key : keys) {
                keyList.add(key);
            }
            query.in(identity.getName(), keyList);
        }

        List<Object> entityResults = new ArrayList<Object>();
        Iterator<Serializable> keyIterator = keys.iterator();
        Map<Serializable, Object> resultMap = new HashMap<Serializable, Object>();
        for (Object o : query.list()) {
            if (isEmbeddedEntry(o)) {
                final ValueRetrievalStrategy<T> valueRetrievalStrategy = getValueRetrievalStrategy();
                final Object id = valueRetrievalStrategy.getValue((T) o, MONGO_ID_FIELD);
                o = createObjectFromNativeEntry(getPersistentEntity(), (Serializable) id, (T) o);
            }
            resultMap.put(getObjectIdentifier(o), o);
        }
        while (keyIterator.hasNext()) {
            Object key = getIdentifierForKey(keyIterator.next());
            ConversionService conversionService = getMappingContext().getConversionService();
            key = conversionService.convert(key, identity.getType());
            Object o = resultMap.get(key);
            entityResults.add(o); // may add null, so entityResults list size matches input list size.
        }

        return entityResults;
    }

    protected Object getIdentifierForKey(Object key) {
        Object id = key;
        if (key instanceof DBRef) {
            DBRef ref = (DBRef) key;
            id = ref.getId();
        }
        return id;
    }

    @Override
    protected List<Object> retrieveAllEntities(PersistentEntity persistentEntity, Serializable[] keys) {
        return retrieveAllEntities(persistentEntity, Arrays.asList(keys));
    }

    @Override
    protected void refreshObjectStateFromNativeEntry(PersistentEntity persistentEntity, Object obj, Serializable nativeKey, T nativeEntry, boolean isEmbedded) {
        if (isEmbedded) {
            Object id = getValueRetrievalStrategy().getValue(nativeEntry, MONGO_ID_FIELD);
            super.refreshObjectStateFromNativeEntry(persistentEntity, obj, (Serializable) id, nativeEntry, isEmbedded);
        }
        else {
            super.refreshObjectStateFromNativeEntry(persistentEntity, obj, nativeKey, nativeEntry, isEmbedded);
        }
    }



    @Override
    protected Object getEntryValue(T nativeEntry, String property) {
        Object value = getValueRetrievalStrategy().getValue(nativeEntry, property);
        if (value instanceof DBRef) {
            return getIdentifierForKey(value);
        }
        return value;
    }

    @Override
    protected Object formulateDatabaseReference(PersistentEntity persistentEntity, Association association, Serializable associationId) {
        boolean isReference = isReference(association);
        if (isReference) {
            return new DBRef(getCollectionName(association.getAssociatedEntity()), associationId);
        }
        return associationId;
    }

    @Override
    protected String getPropertyKey(PersistentProperty prop) {
        if (prop instanceof Identity) {
            return MONGO_ID_FIELD;
        }
        return super.getPropertyKey(prop);
    }

    @Override
    protected PersistentEntity discriminatePersistentEntity(PersistentEntity persistentEntity, T nativeEntry) {
        final Object o = getValueRetrievalStrategy().getValue(nativeEntry, MONGO_CLASS_FIELD);
        if (o != null) {
            final String className = o.toString();
            final PersistentEntity childEntity = getMappingContext().getChildEntityByDiscriminator(persistentEntity.getRootEntity(), className);
            if (childEntity != null) {
                return childEntity;
            }
        }
        return super.discriminatePersistentEntity(persistentEntity, nativeEntry);
    }


    protected abstract String getCollectionName(PersistentEntity persistentEntity, T nativeEntry);

    protected boolean isReference(Association association) {
        PropertyMapping mapping = association.getMapping();
        if (mapping != null) {
            MongoAttribute attribute = (MongoAttribute) mapping.getMappedForm();
            if (attribute != null) {
                return attribute.isReference();
            }
        }
        return true;
    }

    @Override
    protected Collection getManyToManyKeys(PersistentEntity persistentEntity, Object object,
                                           Serializable nativeKey, T nativeEntry, ManyToMany manyToMany) {
        return (Collection)getValueRetrievalStrategy().getValue(nativeEntry, manyToMany.getName() + "_$$manyToManyIds");
    }

    @Override
    protected void setManyToMany(PersistentEntity persistentEntity, Object obj,
                                 T nativeEntry, ManyToMany manyToMany, Collection associatedObjects,
                                 Map<Association, List<Serializable>> toManyKeys) {

        List ids = new ArrayList();
        if (associatedObjects != null) {
            for (Object o : associatedObjects) {
                if (o == null) {
                    ids.add(null);
                }
                else {
                    PersistentEntity childPersistentEntity =
                            getMappingContext().getPersistentEntity(o.getClass().getName());
                    EntityAccess entityAccess = createEntityAccess(childPersistentEntity, o);
                    ids.add(entityAccess.getIdentifier());
                }
            }
        }

        getValueRetrievalStrategy().setValue(nativeEntry, manyToMany.getName() + "_$$manyToManyIds", ids);
    }

    /**
     *
     * @return The value retrieval strategy for this implementation
     */
    abstract ValueRetrievalStrategy<T> getValueRetrievalStrategy();

    /**
     * Strategy interface for implementors to implement to set and get values from the native type
     *
     * @param <T> The native type
     */
    static interface ValueRetrievalStrategy<T> {
        Object getValue(T t, String name);

        void setValue(T t, String name, Object value);
    }

    protected class MongoAssociationIndexer implements AssociationIndexer {
        private T nativeEntry;
        private Association association;
        private MongoSession session;
        private boolean isReference = true;

        public MongoAssociationIndexer(T nativeEntry, Association association, MongoSession session) {
            this.nativeEntry = nativeEntry;
            this.association = association;
            this.session = session;
            this.isReference = isReference(association);
        }

        @Override
        public boolean doesReturnKeys() {
            return true;
        }

        public void preIndex(final Object primaryKey, final List foreignKeys) {
            // if the association is a unidirectional one-to-many we store the keys
            // embedded in the owning entity, otherwise we use a foreign key
            if (!association.isBidirectional()) {
                List dbRefs = new ArrayList();
                for (Object foreignKey : foreignKeys) {
                    if (isReference) {
                        dbRefs.add(new DBRef(getCollectionName(association.getAssociatedEntity()), foreignKey));
                    }
                    else {
                        dbRefs.add(foreignKey);
                    }
                }
                // update the native entry directly.
                getValueRetrievalStrategy().setValue(nativeEntry, association.getName(), dbRefs);
            }
        }

        public void index(final Object primaryKey, final List foreignKeys) {
            // indexing is handled by putting the data in the native entry before it is persisted, see preIndex above.
        }

        public List query(Object primaryKey) {
            // for a unidirectional one-to-many we use the embedded keys
            if (!association.isBidirectional()) {
                final Object indexed = getValueRetrievalStrategy().getValue(nativeEntry, association.getName());
                if (!(indexed instanceof Collection)) {
                    return Collections.emptyList();
                }
                List indexedList = getIndexedAssociationsAsList(indexed);

                if (associationsAreDbRefs(indexedList)) {
                    return extractIdsFromDbRefs(indexedList);
                }
                return indexedList;
            }
            // for a bidirectional one-to-many we use the foreign key to query the inverse side of the association
            Association inverseSide = association.getInverseSide();
            Query query = session.createQuery(association.getAssociatedEntity().getJavaClass());
            query.eq(inverseSide.getName(), primaryKey);
            query.projections().id();
            return query.list();
        }

        public PersistentEntity getIndexedEntity() {
            return association.getAssociatedEntity();
        }

        public void index(Object primaryKey, Object foreignKey) {
            // TODO: Implement indexing of individual entities
        }

        private List getIndexedAssociationsAsList(Object indexed) {
            return (indexed instanceof List) ? (List) indexed : new ArrayList(((Collection) indexed));
        }

        private boolean associationsAreDbRefs(List indexedList) {
            return !indexedList.isEmpty() && (indexedList.get(0) instanceof DBRef);
        }

        private List extractIdsFromDbRefs(List indexedList) {
            List resolvedDbRefs = new ArrayList();
            for (Object indexedAssociation : indexedList) {
                resolvedDbRefs.add(((DBRef) indexedAssociation).getId());
            }
            return resolvedDbRefs;
        }
    }
}
