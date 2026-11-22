/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.orm.hibernate;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.persistence.FlushModeType;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.JoinType;

import org.hibernate.FlushMode;
import org.hibernate.HibernateException;
import org.hibernate.LockMode;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.MariaDBDialect;
import org.hibernate.dialect.MySQLDialect;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.query.MutationQuery;
import org.hibernate.type.CollectionType;
import org.hibernate.type.OneToOneType;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.convert.ConversionService;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import org.grails.datastore.gorm.query.criteria.AbstractDetachedCriteria;
import org.grails.datastore.gorm.timestamp.DefaultTimestampProvider;
import org.grails.datastore.mapping.core.AbstractAttributeStoringSession;
import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.engine.Persister;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.Basic;
import org.grails.datastore.mapping.model.types.Embedded;
import org.grails.datastore.mapping.model.types.ToMany;
import org.grails.datastore.mapping.proxy.ProxyHandler;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.api.QueryAliasAwareSession;
import org.grails.datastore.mapping.query.api.QueryableCriteria;
import org.grails.datastore.mapping.query.event.PostQueryEvent;
import org.grails.datastore.mapping.query.event.PreQueryEvent;
import org.grails.datastore.mapping.query.jpa.JpaQueryBuilder;
import org.grails.datastore.mapping.query.jpa.JpaQueryInfo;
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher;
import org.grails.datastore.mapping.transactions.Transaction;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentEntity;
import org.grails.orm.hibernate.proxy.HibernateProxyHandler;
import org.grails.orm.hibernate.query.HibernateHqlQueryCreator;
import org.grails.orm.hibernate.query.HibernateQuery;
import org.grails.orm.hibernate.query.HqlQueryContext;
import org.grails.orm.hibernate.query.MutationHqlQuery;

/**
 * Session implementation that wraps a Hibernate {@link org.hibernate.Session}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings({"rawtypes", "PMD.DataflowAnomalyAnalysis", "PMD.AvoidDuplicateLiterals"})
public class HibernateSession extends AbstractAttributeStoringSession implements QueryAliasAwareSession {

    /**
     * The number of identifiers that a delete by identifiers deletes with one statement, below the number of
     * parameters that databases allow in a list.
     */
    private static final int IDENTIFIER_BATCH_SIZE = 500;

    /** The datastore. */
    protected HibernateDatastore datastore;

    /** The connected. */
    protected boolean connected = true;

    /** The hibernate template. */
    protected IHibernateTemplate hibernateTemplate;

    ProxyHandler proxyHandler = new HibernateProxyHandler();
    DefaultTimestampProvider timestampProvider;

    public HibernateSession(HibernateDatastore hibernateDatastore, SessionFactory sessionFactory) {
        datastore = hibernateDatastore;
        hibernateTemplate = (IHibernateTemplate) hibernateDatastore.getHibernateTemplate();
    }

    @Override
    public boolean isSchemaless() {
        return false;
    }

    @Override
    public Serializable insert(Object o) {
        return persist(o);
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void disconnect() {
        connected = false; // don't actually do any disconnection here. This will be handled by OSVI
    }

    @Override
    public Transaction beginTransaction() {
        throw new UnsupportedOperationException("Use HibernatePlatformTransactionManager instead");
    }

    @Override
    public Transaction beginTransaction(TransactionDefinition definition) {
        throw new UnsupportedOperationException("Use HibernatePlatformTransactionManager instead");
    }

    @Override
    public MappingContext getMappingContext() {
        return getDatastore().getMappingContext();
    }

    @Override
    public Serializable persist(Object o) {
        hibernateTemplate.persist(o);
        try {
            MappingContext ctx = getDatastore().getMappingContext();
            GrailsHibernatePersistentEntity pe = (GrailsHibernatePersistentEntity)
                    ctx.getPersistentEntity(o.getClass().getName());
            if (pe != null) {
                return ctx.getEntityReflector(pe).getIdentifier(o);
            }
        } catch (Exception ignored) {
            // ignore and return null when identifier cannot be obtained
        }
        return null;
    }

    @Override
    public Object merge(Object o) {
        return hibernateTemplate.merge(o);
    }

    @Override
    public void refresh(Object o) {
        hibernateTemplate.refresh(o);
    }

    @Override
    public void attach(Object o) {
        ((GrailsHibernateTemplate) hibernateTemplate).execute(session -> {
            HibernateAttachSupport.attach(o, session);
            return null;
        });
    }

    @Override
    public void flush() {
        hibernateTemplate.flush();
    }

    @Override
    public void clear() {
        hibernateTemplate.clear();
    }

    @Override
    public void clear(Object o) {
        hibernateTemplate.evict(o);
    }

    @Override
    public boolean contains(Object o) {
        return hibernateTemplate.contains(o);
    }

    @Override
    public void lock(Object o) {
        hibernateTemplate.lock(o, LockMode.PESSIMISTIC_WRITE);
    }

    @Override
    public void unlock(Object o) {
        // do nothing
    }

    /**
     * @deprecated persist method needs to be changed to void
     * @param objects The Objects
     * @return the result
     */
    @Deprecated
    @Override
    public List<Serializable> persist(Iterable objects) {
        List<Serializable> ids = new ArrayList<>();
        for (Object object : objects) {
            Serializable id = persist(object);
            ids.add(id);
        }
        return ids;
    }

    @Override
    public <T> T retrieve(Class<T> type, Serializable key) {
        return getHibernateTemplate().execute(session -> session.find(type, key));
    }

    @Override
    public <T> T proxy(Class<T> type, Serializable key) {
        return hibernateTemplate.load(type, key);
    }

    @Override
    public <T> T lock(Class<T> type, Serializable key) {
        return getHibernateTemplate().execute(session -> session.find(type, key, LockModeType.PESSIMISTIC_WRITE));
    }

    @Override
    public void delete(Iterable objects) {
        Collection list = getIterableAsCollection(objects);
        hibernateTemplate.deleteAll(list);
    }

    protected Collection<?> getIterableAsCollection(Iterable<?> objects) {
        if (objects instanceof Collection<?> coll) {
            return coll;
        }
        List<Object> list = new ArrayList<>();
        for (Object object : objects) {
            list.add(object);
        }
        return list;
    }

    @Override
    public void delete(Object obj) {
        hibernateTemplate.remove(obj);
    }

    @Override
    public List retrieveAll(Class type, Serializable... keys) {
        return retrieveAll(type, Arrays.asList(keys));
    }

    @Override
    public Persister getPersister(Object o) {
        return null;
    }

    @Override
    public Transaction getTransaction() {
        throw new UnsupportedOperationException("Use HibernatePlatformTransactionManager instead");
    }

    @Override
    public boolean hasTransaction() {
        Object resource = TransactionSynchronizationManager.getResource(hibernateTemplate.getSessionFactory());
        return resource != null;
    }

    @Override
    public Datastore getDatastore() {
        return datastore;
    }

    @Override
    public boolean isDirty(Object o) {
        // not used, Hibernate manages dirty checking itself
        return true;
    }

    @Override
    public Object getNativeInterface() {
        return hibernateTemplate;
    }

    @Override
    public void setSynchronizedWithTransaction(boolean synchronizedWithTransaction) {
        // no-op
    }

    @Override
    @SuppressWarnings("PMD.DataflowAnomalyAnalysis")
    public Serializable getObjectIdentifier(Object instance) {
        if (instance == null) return null;
        if (proxyHandler.isProxy(instance)) {
            return (Serializable)
                    ((HibernateProxy) instance).getHibernateLazyInitializer().getIdentifier();
        }
        Class<?> type = instance.getClass();
        ClassPropertyFetcher cpf = ClassPropertyFetcher.forClass(type);
        final GrailsHibernatePersistentEntity persistentEntity = (GrailsHibernatePersistentEntity) getMappingContext().getPersistentEntity(type.getName());
        if (persistentEntity != null) {
            return (Serializable) cpf.getPropertyValue(
                    instance, persistentEntity.getIdentity().getName());
        }
        return null;
    }

    /**
     * Deletes all objects matching the given criteria.
     *
     * @param criteria The criteria
     * @return The total number of records deleted
     */
    @Override
    @SuppressWarnings("PMD.DataflowAnomalyAnalysis")
    public long deleteAll(final QueryableCriteria criteria) {
        return getHibernateTemplate().execute((GrailsHibernateTemplate.HibernateCallback<Integer>) session -> {
            JpaQueryBuilder builder = new JpaQueryBuilder(criteria);
            builder.setConversionService(getMappingContext().getConversionService());
            builder.setHibernateCompatible(true);
            JpaQueryInfo built = builder.buildDelete();
            if (hasRequestedSubqueryJoin(criteria.getCriteria())) {
                return modifyByIdentifiers(session, criteria, null);
            }
            JpaQueryInfo jpaQueryInfo = new JpaQueryInfo(applyRequestedJoins(built.getQuery(), criteria), built.getParameters());
            if (!jpaQueryInfo.getQuery().equals(built.getQuery()) && restrictsThroughJoinTable(criteria)) {
                return modifyByIdentifiers(session, criteria, null);
            }

            var query = createMutationQuery(session, jpaQueryInfo);

            HqlQueryContext ctx = HqlQueryContext.prepare(criteria.getPersistentEntity(), jpaQueryInfo.getQuery(), null, null, null, new HashMap<>(), false, true);
            MutationHqlQuery hqlQuery = (MutationHqlQuery) HibernateHqlQueryCreator.createHqlQuery((HibernateDatastore) getDatastore(), hibernateTemplate.getSessionFactory(), criteria.getPersistentEntity(), ctx);
            ApplicationEventPublisher applicationEventPublisher = datastore.getApplicationEventPublisher();
            applicationEventPublisher.publishEvent(new PreQueryEvent(datastore, hqlQuery));
            int result = query.executeUpdate();
            applicationEventPublisher.publishEvent(
                    new PostQueryEvent(datastore, hqlQuery, Collections.singletonList(result)));
            return result;
        });
    }

    private MutationQuery createMutationQuery(Session session, JpaQueryInfo jpaQueryInfo) {
        org.hibernate.query.MutationQuery query = session.createMutationQuery(jpaQueryInfo.getQuery());

        List<?> parameters = jpaQueryInfo.getParameters();
        if (parameters != null) {
            for (int i = 0; i < parameters.size(); i++) {
                query.setParameter(JpaQueryBuilder.PARAMETER_NAME_PREFIX + (i + 1), parameters.get(i));
            }
        }
        return query;
    }

    /**
     * Whether a join type that the criteria request is applied through a collection of the queried class that has
     * a join table. Hibernate deletes the rows of the join tables of the instances before it deletes the instances,
     * with the same restriction, which then no longer finds the instances: see
     * {@link #modifyByIdentifiers(Session, QueryableCriteria, Map)}.
     */
    private boolean restrictsThroughJoinTable(QueryableCriteria criteria) {
        if (!(criteria instanceof AbstractDetachedCriteria<?> detachedCriteria)) {
            return false;
        }
        SessionFactoryImplementor factory = (SessionFactoryImplementor) hibernateTemplate.getSessionFactory();
        EntityPersister persister = factory.getMappingMetamodel().getEntityDescriptor(criteria.getPersistentEntity().getName());
        for (Map.Entry<String, JoinType> requestedJoin : detachedCriteria.getJoinTypes().entrySet()) {
            if (requestedJoin.getValue() == null || requestedJoin.getValue() == JoinType.LEFT) {
                continue;
            }
            String association = requestedJoin.getKey().split("\\.")[0];
            if (criteria.getPersistentEntity().getPropertyByName(association) instanceof Basic) {
                return true;
            }
            if (!(criteria.getPersistentEntity().getPropertyByName(association) instanceof ToMany)) {
                continue;
            }
            if (persister.getPropertyType(association) instanceof CollectionType collectionType &&
                    factory.getMappingMetamodel().getCollectionDescriptor(collectionType.getRole()).isManyToMany()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Deletes the instances that a delete query restricts to by their identifiers. The identifiers are selected
     * first, with the restriction of the delete, so that the restriction is evaluated before Hibernate deletes
     * the rows of the join tables of the instances.
     */
    private int modifyByIdentifiers(Session session, QueryableCriteria source, Map<String, Object> properties) {
        PersistentEntity entity = source.getPersistentEntity();
        HibernateQuery selection = new HibernateQuery(this, (GrailsHibernatePersistentEntity) entity);
        for (Object criterion : source.getCriteria()) {
            selection.add((Query.Criterion) criterion);
        }
        if (source instanceof AbstractDetachedCriteria<?> detached) {
            detached.getJoinTypes().forEach(selection::join);
        }
        selection.projections().id();
        List<?> identifiers = selection.list();
        ApplicationEventPublisher applicationEventPublisher = datastore.getApplicationEventPublisher();
        int changed = 0;
        for (int from = 0; from < identifiers.size(); from += IDENTIFIER_BATCH_SIZE) {
            List<?> batch = identifiers.subList(from, Math.min(from + IDENTIFIER_BATCH_SIZE, identifiers.size()));
            JpaQueryBuilder builder = new JpaQueryBuilder(entity, Collections.singletonList(new Query.In(entity.getIdentity().getName(), batch)));
            builder.setConversionService(getMappingContext().getConversionService());
            builder.setHibernateCompatible(true);
            JpaQueryInfo info = properties == null ? builder.buildDelete() : builder.buildUpdate(properties);
            MutationQuery query = createMutationQuery(session, info);
            HqlQueryContext ctx = HqlQueryContext.prepare(entity, info.getQuery(), null, null, null, new HashMap<>(), false, true);
            MutationHqlQuery hqlQuery = (MutationHqlQuery) HibernateHqlQueryCreator.createHqlQuery((HibernateDatastore) getDatastore(), hibernateTemplate.getSessionFactory(), entity, ctx);
            applicationEventPublisher.publishEvent(new PreQueryEvent(datastore, hqlQuery));
            int result = query.executeUpdate();
            applicationEventPublisher.publishEvent(new PostQueryEvent(datastore, hqlQuery, Collections.singletonList(result)));
            changed += result;
        }
        return changed;
    }

    private boolean hasRequestedSubqueryJoin(List<Query.Criterion> criteria) {
        for (Query.Criterion criterion : criteria) {
            if (criterion instanceof Query.Junction junction && hasRequestedSubqueryJoin(junction.getCriteria())) {
                return true;
            }
            QueryableCriteria<?> subquery = null;
            if (criterion instanceof Query.In in) {
                subquery = in.getSubquery();
            }
            else if (criterion instanceof Query.NotIn notIn) {
                subquery = notIn.getSubquery();
            }
            else if (criterion instanceof Query.Exists exists) {
                subquery = exists.getSubquery();
            }
            else if (criterion instanceof Query.NotExists notExists) {
                subquery = notExists.getSubquery();
            }
            else if (criterion instanceof Query.PropertyCriterion property && property.getValue() instanceof QueryableCriteria<?> value) {
                subquery = value;
            }
            if (subquery != null) {
                if (subquery instanceof AbstractDetachedCriteria<?> detached && detached.getJoinTypes().values().stream().anyMatch(type -> type != null && type != JoinType.LEFT)) {
                    return true;
                }
                if (hasRequestedSubqueryJoin(subquery.getCriteria())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Restricts an update or a delete to the instances that the criteria return when they are listed. A join type
     * other than a left join that the criteria request for an association leaves out the instances for which the
     * join returns no row. An update and a delete cannot join, so each such join is applied as a restriction, see
     * {@link #buildRequestedJoinRestriction(PersistentEntity, String, int)}.
     *
     * @param query The update or delete query of the criteria
     * @param criteria The criteria
     * @return The query with the restrictions
     */
    private String applyRequestedJoins(String query, QueryableCriteria criteria) {
        if (!(criteria instanceof AbstractDetachedCriteria<?> detachedCriteria)) {
            return query;
        }
        StringBuilder restricted = new StringBuilder(query);
        boolean hasWhereClause = !criteria.getCriteria().isEmpty();
        int index = 0;
        for (Map.Entry<String, JoinType> requestedJoin : detachedCriteria.getJoinTypes().entrySet()) {
            if (requestedJoin.getValue() == null || requestedJoin.getValue() == JoinType.LEFT) {
                continue;
            }
            String restriction = buildRequestedJoinRestriction(criteria.getPersistentEntity(), requestedJoin.getKey(), index);
            if (restriction != null) {
                restricted.append(hasWhereClause ? JpaQueryBuilder.LOGICAL_AND : " WHERE ").append(restriction);
                hasWhereClause = true;
                index++;
            }
        }
        return restricted.toString();
    }

    /**
     * Returns the restriction to the instances of an entity for which an inner join of an association path returns
     * a row, or null when the path does not consist of associations. The restriction is a subquery that selects
     * from the entity of the first association and joins the rest of the path, so that the filters that are enabled
     * for the session apply to every entity on the path, as they do to a join. The subquery is matched with the
     * instance through the association, by the side of it that has the foreign key: a one-to-one whose foreign key
     * the other entity has, such as a {@code hasOne}, is matched through the property of the other entity that
     * refers back. A collection of a basic type has no entity of its own, so a path that starts with one selects
     * from the entity itself.
     * <p>
     * MySQL does not allow an update or a delete to select from the table that it modifies. A path that leads back
     * to the class of the entity cannot be applied there, and is rejected instead of being left to fail in the
     * database.
     *
     * @throws UnsupportedOperationException if the join cannot be applied to an update or a delete
     */
    private String buildRequestedJoinRestriction(PersistentEntity entity, String associationPath, int index) {
        String rootAlias = entity.getDecapitalizedName();
        String[] tokens = associationPath.split("\\.");
        int last = tokens.length - 1;
        SessionFactoryImplementor factory = (SessionFactoryImplementor) hibernateTemplate.getSessionFactory();
        EntityPersister persister = factory.getMappingMetamodel().getEntityDescriptor(entity.getName());
        StringBuilder from = new StringBuilder();
        String correlation = null;
        String alias = null;
        boolean selectsFromModifiedTable = false;
        PersistentEntity currentEntity = entity;
        for (int i = 0; i < tokens.length; i++) {
            PersistentProperty<?> property = currentEntity != null ? currentEntity.getPropertyByName(tokens[i]) : null;
            if (!(property instanceof Association<?> association) || property instanceof Embedded || (property instanceof Basic && i != last)) {
                return null;
            }
            PersistentEntity associatedEntity = association.getAssociatedEntity();
            if (associatedEntity == null && !(association instanceof Basic)) {
                return null;
            }
            String joinAlias = "join" + index + '_' + i;
            if (i > 0) {
                from.append(" INNER JOIN ").append(alias).append('.').append(tokens[i]).append(' ').append(joinAlias);
            }
            else if (association instanceof Basic) {
                String ownerAlias = "join" + index + "_owner";
                from.append(entity.getName()).append(' ').append(ownerAlias)
                        .append(" INNER JOIN ").append(ownerAlias).append('.').append(tokens[i]).append(' ').append(joinAlias);
                correlation = ownerAlias + " = " + rootAlias;
                selectsFromModifiedTable = true;
            }
            else {
                from.append(associatedEntity.getName()).append(' ').append(joinAlias);
                String referencedProperty = persister.getPropertyType(tokens[i]) instanceof OneToOneType oneToOne ?
                        oneToOne.getRHSUniqueKeyPropertyName() : null;
                if (association instanceof ToMany) {
                    correlation = joinAlias + " MEMBER OF " + rootAlias + '.' + tokens[i];
                }
                else if (referencedProperty != null) {
                    correlation = joinAlias + '.' + referencedProperty + " = " + rootAlias;
                }
                else {
                    correlation = joinAlias + " = " + rootAlias + '.' + tokens[i];
                }
            }
            if (associatedEntity != null) {
                selectsFromModifiedTable |= associatedEntity.getRootEntity().equals(entity.getRootEntity());
            }
            alias = joinAlias;
            currentEntity = associatedEntity;
        }
        if (selectsFromModifiedTable && !canSelectFromModifiedTable(factory)) {
            throw new UnsupportedOperationException("The join type requested for [" + associationPath + "] of [" + entity.getName() +
                    "] cannot be applied to an update or a delete on this database, which does not allow them to select from the table that they modify. " +
                    "Do not request the join type for the update or the delete, and restrict the query with criteria instead.");
        }
        return "EXISTS (SELECT 1 FROM " + from + " WHERE " + correlation + ")";
    }

    /**
     * Whether an update or a delete can select, in a subquery, from the table that it modifies. MySQL does not
     * allow it, and neither does MariaDB before version 10.3.
     */
    private static boolean canSelectFromModifiedTable(SessionFactoryImplementor factory) {
        Dialect dialect = factory.getJdbcServices().getDialect();
        return !(dialect instanceof MySQLDialect) ||
                dialect instanceof MariaDBDialect && dialect.getVersion().isSameOrAfter(10, 3);
    }

    /**
     * Updates all objects matching the given criteria and property values.
     *
     * @param criteria The criteria
     * @param properties The properties
     * @return The total number of records updated
     */
    @Override
    @SuppressWarnings("PMD.DataflowAnomalyAnalysis")
    public long updateAll(final QueryableCriteria criteria, final Map<String, Object> properties) {
        return getHibernateTemplate().execute((GrailsHibernateTemplate.HibernateCallback<Integer>) session -> {
            JpaQueryBuilder builder = new JpaQueryBuilder(criteria);
            builder.setConversionService(getMappingContext().getConversionService());
            builder.setHibernateCompatible(true);
            GrailsHibernatePersistentEntity targetEntity = (GrailsHibernatePersistentEntity) criteria.getPersistentEntity();
            PersistentProperty lastUpdated = targetEntity.getPropertyByName(GormProperties.LAST_UPDATED);
            if (lastUpdated != null && ((HibernatePersistentEntity) targetEntity).getMapping().getMappedForm().isAutoTimestamp()) {
                if (timestampProvider == null) {
                    timestampProvider = new DefaultTimestampProvider();
                }
                Class<?> type = lastUpdated.getType();
                properties.put(GormProperties.LAST_UPDATED, timestampProvider.createTimestamp(type));
            }

            JpaQueryInfo built = builder.buildUpdate(properties);
            if (hasRequestedSubqueryJoin(criteria.getCriteria())) {
                return modifyByIdentifiers(session, criteria, properties);
            }
            JpaQueryInfo jpaQueryInfo = new JpaQueryInfo(applyRequestedJoins(built.getQuery(), criteria), built.getParameters());

            var query = createMutationQuery(session, jpaQueryInfo);

            HqlQueryContext ctx = HqlQueryContext.prepare(targetEntity, jpaQueryInfo.getQuery(), null, null, null, new HashMap<>(), false, true);
            MutationHqlQuery hqlQuery = (MutationHqlQuery) HibernateHqlQueryCreator.createHqlQuery((HibernateDatastore) getDatastore(), hibernateTemplate.getSessionFactory(), targetEntity, ctx);
            ApplicationEventPublisher applicationEventPublisher = datastore.getApplicationEventPublisher();
            applicationEventPublisher.publishEvent(new PreQueryEvent(datastore, hqlQuery));
            int result = query.executeUpdate();
            applicationEventPublisher.publishEvent(
                    new PostQueryEvent(datastore, hqlQuery, Collections.singletonList(result)));
            return result;
        });
    }

    @Override
    @SuppressWarnings({"PMD.DataflowAnomalyAnalysis"})
    public List retrieveAll(final Class type, final Iterable keys) {
        final GrailsHibernatePersistentEntity persistentEntity = (GrailsHibernatePersistentEntity) getMappingContext().getPersistentEntity(type.getName());
        final String entityName = persistentEntity.getName();
        final String idName = persistentEntity.getIdentity().getName();
        final String hql = "from " + entityName + " as e where e." + idName + " in (:keys)";
        final Class idType = persistentEntity.getIdentity().getType();
        final ConversionService conversionService = getMappingContext().getConversionService();

        // Convert each requested id to the entity's identifier type, preserving order and
        // duplicates. getAll() must return entities in the supplied id order with a null slot
        // for any id that does not resolve to a row, so order is driven by the request rather
        // than the database.
        final List<Serializable> requestedIds = new ArrayList<>();
        for (Object key : keys) {
            requestedIds.add(convertToIdentifierType(key, idType, conversionService));
        }

        return getHibernateTemplate().execute(session -> {
            // Query only the distinct, non-null ids; a missing id simply yields no row.
            final Set<Serializable> distinctIds = new LinkedHashSet<>();
            for (Serializable requestedId : requestedIds) {
                if (requestedId != null) {
                    distinctIds.add(requestedId);
                }
            }

            // Keyed by the identifier's string form rather than its typed value:
            // convertToIdentifierType falls back to the raw, unconverted key when the
            // ConversionService can't convert it, so a requestedId that Hibernate itself
            // coerced during the query would not equal the typed identifier returned by
            // session.getIdentifier(). Normalizing both sides to String avoids that mismatch.
            final Map<String, Object> entitiesById = new HashMap<>();
            if (!distinctIds.isEmpty()) {
                HqlQueryContext queryContext = HqlQueryContext.prepare(
                    persistentEntity,
                    hql,
                    Map.of("keys", distinctIds),
                    null,
                    null,
                    new HashMap<>(),
                    false,
                    false,
                    type
                );

                final List results = HibernateHqlQueryCreator.createHqlQuery(
                    (HibernateDatastore) getDatastore(),
                    getHibernateTemplate().getSessionFactory(),
                    persistentEntity,
                    queryContext
                ).list();
                for (Object entity : results) {
                    entitiesById.put(String.valueOf(session.getIdentifier(entity)), entity);
                }
            }

            // Reassemble in the requested order, leaving a null slot for missing ids.
            final List ordered = new ArrayList<>(requestedIds.size());
            for (Serializable requestedId : requestedIds) {
                ordered.add(requestedId == null ? null : entitiesById.get(String.valueOf(requestedId)));
            }
            return ordered;
        });
    }

    private Serializable convertToIdentifierType(Object key, Class idType, ConversionService conversionService) {
        if (key == null || idType.isInstance(key)) {
            return (Serializable) key;
        }
        if (conversionService != null && conversionService.canConvert(key.getClass(), idType)) {
            return (Serializable) conversionService.convert(key, idType);
        }
        return (Serializable) key;
    }

    @Override
    public Query createQuery(Class type) {
        return createQuery(type, null);
    }

    @Override
    public Query createQuery(Class type, String alias) {
        HibernateQuery query = new HibernateQuery(this, (GrailsHibernatePersistentEntity) getMappingContext().getPersistentEntity(type.getName()));
        if (alias != null) {
            query.getDetachedCriteria().setAlias(alias);
        }
        return query;
    }

    public GrailsHibernateTemplate getHibernateTemplate() {
        return (GrailsHibernateTemplate) getNativeInterface();
    }

    /**
     * Returns the flush mode of the Hibernate session bound to the current thread, or the default flush mode of
     * the datastore when no session is bound. {@link FlushMode#MANUAL} is returned as {@link FlushModeType#COMMIT}
     * and {@link FlushMode#ALWAYS} as {@link FlushModeType#AUTO}.
     */
    @Override
    public FlushModeType getFlushMode() {
        Session session = getCurrentNativeSession();
        if (session != null) {
            return session.getFlushMode();
        }
        FlushMode defaultFlushMode = ((HibernateDatastore) getDatastore()).getDefaultFlushMode();
        return defaultFlushMode == FlushMode.COMMIT || defaultFlushMode == FlushMode.MANUAL ?
                FlushModeType.COMMIT : FlushModeType.AUTO;
    }

    /**
     * Sets the flush mode of the Hibernate session bound to the current thread. Other sessions and the default
     * flush mode of the datastore are not changed.
     *
     * @throws IllegalStateException if no Hibernate session is bound to the current thread
     */
    @Override
    public void setFlushMode(FlushModeType flushMode) {
        Session session = getCurrentNativeSession();
        if (session == null) {
            throw new IllegalStateException("No Hibernate session is bound to the current thread");
        }
        session.setFlushMode(flushMode);
    }

    private Session getCurrentNativeSession() {
        try {
            return hibernateTemplate.getSessionFactory().getCurrentSession();
        } catch (HibernateException e) {
            return null;
        }
    }

    protected <D> HibernateGormStaticApi<D> getStaticApi(Class<D> type) {
        return new HibernateGormStaticApi<>(
            type,
            (HibernateDatastore) getDatastore(),
            Collections.emptyList(),
            Thread.currentThread().getContextClassLoader(),
            ((HibernateDatastore) getDatastore()).getTransactionManager(),
            null
        );
    }
}
