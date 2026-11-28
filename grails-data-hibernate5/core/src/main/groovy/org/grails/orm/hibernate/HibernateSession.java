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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.persistence.FlushModeType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;

import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.MariaDB103Dialect;
import org.hibernate.dialect.MySQLDialect;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.type.CollectionType;
import org.hibernate.type.OneToOneType;
import org.hibernate.type.Type;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.convert.ConversionService;

import org.grails.datastore.gorm.query.criteria.AbstractDetachedCriteria;
import org.grails.datastore.gorm.timestamp.DefaultTimestampProvider;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.Basic;
import org.grails.datastore.mapping.model.types.Embedded;
import org.grails.datastore.mapping.model.types.ToMany;
import org.grails.datastore.mapping.model.types.ToOne;
import org.grails.datastore.mapping.proxy.ProxyHandler;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.api.QueryableCriteria;
import org.grails.datastore.mapping.query.event.PostQueryEvent;
import org.grails.datastore.mapping.query.event.PreQueryEvent;
import org.grails.datastore.mapping.query.jpa.JpaQueryBuilder;
import org.grails.datastore.mapping.query.jpa.JpaQueryInfo;
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher;
import org.grails.orm.hibernate.proxy.HibernateProxyHandler;
import org.grails.orm.hibernate.query.HibernateHqlQuery;
import org.grails.orm.hibernate.query.HibernateQuery;

/**
 * Session implementation that wraps a Hibernate {@link org.hibernate.Session}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("rawtypes")
public class HibernateSession extends AbstractHibernateSession {

    /**
     * The number of identifiers that an update or a delete by identifiers changes with one statement,
     * below the number of parameters that databases allow in a list.
     */
    private static final int IDENTIFIER_BATCH_SIZE = 500;

    ProxyHandler proxyHandler = new HibernateProxyHandler();
    DefaultTimestampProvider timestampProvider;

    public HibernateSession(HibernateDatastore hibernateDatastore, SessionFactory sessionFactory, int defaultFlushMode) {
        super(hibernateDatastore, sessionFactory);

        hibernateTemplate = new GrailsHibernateTemplate(sessionFactory, (HibernateDatastore) getDatastore());
    }

    public HibernateSession(HibernateDatastore hibernateDatastore, SessionFactory sessionFactory) {
        this(hibernateDatastore, sessionFactory, hibernateDatastore.getDefaultFlushMode());
    }

    @Override
    public Serializable getObjectIdentifier(Object instance) {
        if (instance == null) return null;
        if (proxyHandler.isProxy(instance)) {
            return ((HibernateProxy) instance).getHibernateLazyInitializer().getIdentifier();
        }
        Class<?> type = instance.getClass();
        ClassPropertyFetcher cpf = ClassPropertyFetcher.forClass(type);
        final PersistentEntity persistentEntity = getMappingContext().getPersistentEntity(type.getName());
        if (persistentEntity != null) {
            return (Serializable) cpf.getPropertyValue(instance, persistentEntity.getIdentity().getName());
        }
        return null;
    }

    /**
     * Deletes all objects matching the given criteria.
     *
     * @param criteria The criteria
     * @return The total number of records deleted
     */
    public long deleteAll(final QueryableCriteria criteria) {
        return getHibernateTemplate().execute((GrailsHibernateTemplate.HibernateCallback<Integer>) session -> {
            JpaQueryBuilder builder = new JpaQueryBuilder(criteria);
            builder.setConversionService(getMappingContext().getConversionService());
            builder.setHibernateCompatible(true);
            JpaQueryInfo jpaQueryInfo = builder.buildDelete();
            List<RequestedJoinRestriction> restrictions = buildRequestedJoinRestrictions(criteria);
            if (modifiesByIdentifiers(criteria, restrictions, true)) {
                return modifyByIdentifiers(session, criteria, null);
            }

            org.hibernate.query.Query query = session.createQuery(applyRequestedJoins(jpaQueryInfo.getQuery(), criteria, restrictions));
            getHibernateTemplate().applySettings(query);
            setParameters(query, jpaQueryInfo.getParameters());

            HibernateHqlQuery hqlQuery = new HibernateHqlQuery(HibernateSession.this, criteria.getPersistentEntity(), query);
            ApplicationEventPublisher applicationEventPublisher = datastore.getApplicationEventPublisher();
            applicationEventPublisher.publishEvent(new PreQueryEvent(datastore, hqlQuery));
            int result = query.executeUpdate();
            applicationEventPublisher.publishEvent(new PostQueryEvent(datastore, hqlQuery, Collections.singletonList(result)));
            return result;
        });
    }

    /**
     * Updates all objects matching the given criteria and property values.
     *
     * @param criteria The criteria
     * @param properties The properties
     * @return The total number of records updated
     */
    public long updateAll(final QueryableCriteria criteria, final Map<String, Object> properties) {
        return getHibernateTemplate().execute((GrailsHibernateTemplate.HibernateCallback<Integer>) session -> {
            JpaQueryBuilder builder = new JpaQueryBuilder(criteria);
            builder.setConversionService(getMappingContext().getConversionService());
            builder.setHibernateCompatible(true);
            PersistentEntity targetEntity = criteria.getPersistentEntity();
            PersistentProperty lastUpdated = targetEntity.getPropertyByName(GormProperties.LAST_UPDATED);
            if (lastUpdated != null && targetEntity.getMapping().getMappedForm().isAutoTimestamp()) {
                if (timestampProvider == null) {
                    timestampProvider = new DefaultTimestampProvider();
                }
                properties.put(GormProperties.LAST_UPDATED, timestampProvider.createTimestamp(lastUpdated.getType()));
            }

            JpaQueryInfo jpaQueryInfo = builder.buildUpdate(properties);
            List<RequestedJoinRestriction> restrictions = buildRequestedJoinRestrictions(criteria);
            if (modifiesByIdentifiers(criteria, restrictions, false)) {
                return modifyByIdentifiers(session, criteria, properties);
            }

            org.hibernate.query.Query query = session.createQuery(applyRequestedJoins(jpaQueryInfo.getQuery(), criteria, restrictions));
            getHibernateTemplate().applySettings(query);
            setParameters(query, jpaQueryInfo.getParameters());

            HibernateHqlQuery hqlQuery = new HibernateHqlQuery(HibernateSession.this, targetEntity, query);
            ApplicationEventPublisher applicationEventPublisher = datastore.getApplicationEventPublisher();
            applicationEventPublisher.publishEvent(new PreQueryEvent(datastore, hqlQuery));
            int result = query.executeUpdate();
            applicationEventPublisher.publishEvent(new PostQueryEvent(datastore, hqlQuery, Collections.singletonList(result)));
            return result;
        });
    }

    private boolean hasRequestedSubqueryJoin(List<Query.Criterion> criteria) {
        for (Query.Criterion criterion : criteria) {
            if (criterion instanceof Query.Junction && hasRequestedSubqueryJoin(((Query.Junction) criterion).getCriteria())) {
                return true;
            }
            QueryableCriteria subquery = null;
            if (criterion instanceof Query.In) {
                subquery = ((Query.In) criterion).getSubquery();
            }
            else if (criterion instanceof Query.NotIn) {
                subquery = ((Query.NotIn) criterion).getSubquery();
            }
            else if (criterion instanceof Query.Exists) {
                subquery = ((Query.Exists) criterion).getSubquery();
            }
            else if (criterion instanceof Query.NotExists) {
                subquery = ((Query.NotExists) criterion).getSubquery();
            }
            else if (criterion instanceof Query.PropertyCriterion && ((Query.PropertyCriterion) criterion).getValue() instanceof QueryableCriteria) {
                subquery = (QueryableCriteria) ((Query.PropertyCriterion) criterion).getValue();
            }
            if (subquery != null) {
                if (subquery instanceof AbstractDetachedCriteria) {
                    for (JoinType type : ((AbstractDetachedCriteria<?>) subquery).getJoinTypes().values()) {
                        if (type != null && type != JoinType.LEFT) {
                            return true;
                        }
                    }
                }
                if (hasRequestedSubqueryJoin(subquery.getCriteria())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether an update or a delete selects the identifiers of the instances first, and then changes
     * the instances by their identifiers, see {@link #modifyByIdentifiers(Session, QueryableCriteria, Map)},
     * instead of restricting the update or the delete itself. That is the case:
     * <ul>
     *     <li>when a subquery requests a join type, which the subquery of an update or a delete
     *     cannot apply;</li>
     *     <li>for a delete, when a requested join is applied through a collection of the queried class
     *     that has a join table. Hibernate deletes the rows of the join tables of the instances before
     *     it deletes the instances, with the same restriction, which then no longer finds them;</li>
     *     <li>when a requested join selects from the table that the update or the delete modifies, on a
     *     database that does not allow that, see {@link #canSelectFromModifiedTable()}.</li>
     * </ul>
     */
    private boolean modifiesByIdentifiers(QueryableCriteria criteria, List<RequestedJoinRestriction> restrictions, boolean delete) {
        if (hasRequestedSubqueryJoin(criteria.getCriteria())) {
            return true;
        }
        for (RequestedJoinRestriction restriction : restrictions) {
            if ((delete && restriction.throughJoinTable()) || (restriction.selectsFromModifiedTable() && !canSelectFromModifiedTable())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Updates or deletes the instances that the criteria return. The identifiers of the instances are
     * selected first, with the criteria and the join types that they request, and the instances are
     * then updated or deleted by their identifiers, in batches.
     *
     * @param properties The properties to update, or null to delete the instances
     * @return The number of instances updated or deleted
     */
    private int modifyByIdentifiers(Session session, QueryableCriteria source, Map<String, Object> properties) {
        PersistentEntity entity = source.getPersistentEntity();
        Criteria nativeSelection = source.getAlias() == null ? session.createCriteria(entity.getJavaClass()) :
                session.createCriteria(entity.getJavaClass(), source.getAlias());
        getHibernateTemplate().applySettings(nativeSelection);
        HibernateQuery selection = new HibernateQuery(nativeSelection, this, entity);
        for (Object criterion : source.getCriteria()) {
            selection.add((Query.Criterion) criterion);
        }
        if (source instanceof AbstractDetachedCriteria) {
            for (Map.Entry<String, JoinType> join : ((AbstractDetachedCriteria<?>) source).getJoinTypes().entrySet()) {
                selection.join(join.getKey(), join.getValue());
            }
        }
        selection.projections().id();
        // a join of a collection selects an identifier for each element, and a right join a null for each row without an instance
        Set<Object> selected = new LinkedHashSet<>(selection.list());
        selected.remove(null);
        List<Object> identifiers = new ArrayList<>(selected);
        int changed = 0;
        for (int from = 0; from < identifiers.size(); from += IDENTIFIER_BATCH_SIZE) {
            List<Object> batch = identifiers.subList(from, Math.min(from + IDENTIFIER_BATCH_SIZE, identifiers.size()));
            JpaQueryBuilder builder = new JpaQueryBuilder(entity, Collections.singletonList(new Query.In(entity.getIdentity().getName(), batch)));
            builder.setConversionService(getMappingContext().getConversionService());
            builder.setHibernateCompatible(true);
            JpaQueryInfo info = properties == null ? builder.buildDelete() : builder.buildUpdate(properties);
            org.hibernate.query.Query query = session.createQuery(info.getQuery());
            getHibernateTemplate().applySettings(query);
            setParameters(query, info.getParameters());
            HibernateHqlQuery hqlQuery = new HibernateHqlQuery(this, entity, query);
            ApplicationEventPublisher publisher = datastore.getApplicationEventPublisher();
            publisher.publishEvent(new PreQueryEvent(datastore, hqlQuery));
            int result = query.executeUpdate();
            publisher.publishEvent(new PostQueryEvent(datastore, hqlQuery, Collections.singletonList(result)));
            changed += result;
        }
        return changed;
    }

    private static void setParameters(org.hibernate.query.Query query, List parameters) {
        if (parameters != null) {
            for (int i = 0, count = parameters.size(); i < count; i++) {
                query.setParameter(JpaQueryBuilder.PARAMETER_NAME_PREFIX + (i + 1), parameters.get(i));
            }
        }
    }

    /**
     * A restriction to the instances for which a requested join returns a row.
     *
     * @param condition The condition that an update or a delete is restricted with
     * @param selectsFromModifiedTable Whether the condition selects from the table of the queried class
     * @param throughJoinTable Whether the join starts with a collection of the queried class that has a join table
     */
    private record RequestedJoinRestriction(String condition, boolean selectsFromModifiedTable, boolean throughJoinTable) {
    }

    /**
     * Returns the restrictions for the join types other than a left join that the criteria request.
     * Such a join leaves out the instances for which it returns no row, see
     * {@link #buildRequestedJoinRestriction(PersistentEntity, String, int)}.
     */
    private List<RequestedJoinRestriction> buildRequestedJoinRestrictions(QueryableCriteria criteria) {
        if (!(criteria instanceof AbstractDetachedCriteria)) {
            return Collections.emptyList();
        }
        List<RequestedJoinRestriction> restrictions = new ArrayList<>();
        for (Map.Entry<String, JoinType> requestedJoin : ((AbstractDetachedCriteria<?>) criteria).getJoinTypes().entrySet()) {
            if (requestedJoin.getValue() == null || requestedJoin.getValue() == JoinType.LEFT) {
                continue;
            }
            RequestedJoinRestriction restriction = buildRequestedJoinRestriction(criteria.getPersistentEntity(), requestedJoin.getKey(), restrictions.size());
            if (restriction != null) {
                restrictions.add(restriction);
            }
        }
        return restrictions;
    }

    /**
     * Restricts an update or a delete to the instances that the criteria return when they are
     * listed. An update and a delete cannot join, so each join that the criteria request is applied
     * as a restriction.
     *
     * @param query The update or delete query of the criteria
     * @param criteria The criteria
     * @param restrictions The restrictions of the requested joins
     * @return The query with the restrictions
     */
    private static String applyRequestedJoins(String query, QueryableCriteria criteria, List<RequestedJoinRestriction> restrictions) {
        StringBuilder restricted = new StringBuilder(query);
        boolean hasWhereClause = !criteria.getCriteria().isEmpty();
        for (RequestedJoinRestriction restriction : restrictions) {
            restricted.append(hasWhereClause ? JpaQueryBuilder.LOGICAL_AND : " WHERE ").append(restriction.condition());
            hasWhereClause = true;
        }
        return restricted.toString();
    }

    /**
     * Returns the restriction to the instances of an entity for which an inner join of an
     * association path returns a row, or null when the path does not consist of associations. The
     * restriction is a subquery that selects from the entity of the first association and joins the
     * rest of the path, so that the filters that are enabled for the session apply to every entity on
     * the path, as they do to a join. The subquery is matched with the instance through the
     * association, by the side of it that has the foreign key: a one-to-one whose foreign key the
     * other entity has, such as a {@code hasOne}, is matched through the property of the other entity
     * that refers back. A collection of a basic type has no entity of its own, so a path that starts
     * with one selects from the entity itself.
     */
    private RequestedJoinRestriction buildRequestedJoinRestriction(PersistentEntity entity, String associationPath, int index) {
        String rootAlias = entity.getDecapitalizedName();
        String[] tokens = associationPath.split("\\.");
        int last = tokens.length - 1;
        SessionFactoryImplementor factory = (SessionFactoryImplementor) getHibernateTemplate().getSessionFactory();
        EntityPersister persister = factory.getMetamodel().entityPersister(entity.getName());
        StringBuilder from = new StringBuilder();
        String correlation = null;
        String alias = null;
        boolean selectsFromModifiedTable = false;
        boolean throughJoinTable = false;
        PersistentEntity currentEntity = entity;
        for (int i = 0; i < tokens.length; i++) {
            PersistentProperty property = currentEntity != null ? currentEntity.getPropertyByName(tokens[i]) : null;
            if (!(property instanceof Association) || property instanceof Embedded || (property instanceof Basic && i != last)) {
                return null;
            }
            Association association = (Association) property;
            PersistentEntity associatedEntity = association.getAssociatedEntity();
            if (associatedEntity == null && !(association instanceof Basic)) {
                return null;
            }
            String joinAlias = rootAlias + "_join" + index + '_' + i;
            if (i > 0) {
                from.append(" INNER JOIN ").append(alias).append('.').append(tokens[i]).append(' ').append(joinAlias);
            }
            else if (association instanceof Basic) {
                String ownerAlias = rootAlias + "_join" + index + "_owner";
                from.append(entity.getName()).append(' ').append(ownerAlias)
                        .append(" INNER JOIN ").append(ownerAlias).append('.').append(tokens[i]).append(' ').append(joinAlias);
                correlation = ownerAlias + " = " + rootAlias;
                selectsFromModifiedTable = true;
            }
            else {
                from.append(associatedEntity.getName()).append(' ').append(joinAlias);
                Type type = persister.getPropertyType(tokens[i]);
                String referencedProperty = type instanceof OneToOneType ? ((OneToOneType) type).getRHSUniqueKeyPropertyName() : null;
                if (association instanceof ToMany) {
                    Association inverse = association.getInverseSide();
                    correlation = inverse instanceof ToOne ? joinAlias + '.' + inverse.getName() + " = " + rootAlias :
                            joinAlias + " MEMBER OF " + rootAlias + '.' + tokens[i];
                    throughJoinTable = type instanceof CollectionType && factory.getMetamodel().collectionPersister(((CollectionType) type).getRole()).isManyToMany();
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
        return new RequestedJoinRestriction("EXISTS (SELECT 1 FROM " + from + " WHERE " + correlation + ")", selectsFromModifiedTable, throughJoinTable);
    }

    /**
     * Whether an update or a delete can select, in a subquery, from the table that it modifies.
     * MySQL does not allow it, and neither does MariaDB before version 10.3.
     */
    private boolean canSelectFromModifiedTable() {
        Dialect dialect = ((SessionFactoryImplementor) getHibernateTemplate().getSessionFactory()).getJdbcServices().getDialect();
        return !(dialect instanceof MySQLDialect) || dialect instanceof MariaDB103Dialect;
    }

    public List retrieveAll(final Class type, final Iterable keys) {
        final PersistentEntity persistentEntity = getMappingContext().getPersistentEntity(type.getName());
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
                final CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder();
                CriteriaQuery criteriaQuery = criteriaBuilder.createQuery(type);
                final Root root = criteriaQuery.from(type);
                final String id = persistentEntity.getIdentity().getName();
                // Path.in(Collection) already yields a complete `id IN (...)` predicate; wrapping it
                // in another criteriaBuilder.in(...) emitted a second empty `IN ()`
                // (e.g. `id in (1,2) in ()`), which Hibernate rejects with a QuerySyntaxException.
                criteriaQuery = criteriaQuery.where(root.get(id).in(distinctIds));
                final org.hibernate.query.Query jpaQuery = session.createQuery(criteriaQuery);
                getHibernateTemplate().applySettings(jpaQuery);

                final List results = new HibernateHqlQuery(this, persistentEntity, jpaQuery).list();
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

    public Query createQuery(Class type) {
        return createQuery(type, null);
    }

    @Override
    public Query createQuery(Class type, String alias) {
        final PersistentEntity persistentEntity = getMappingContext().getPersistentEntity(type.getName());
        GrailsHibernateTemplate hibernateTemplate = getHibernateTemplate();
        Session currentSession = hibernateTemplate.getSessionFactory().getCurrentSession();
        final Criteria criteria = alias != null ? currentSession.createCriteria(type, alias) : currentSession.createCriteria(type);
        hibernateTemplate.applySettings(criteria);
        return new HibernateQuery(criteria, this, persistentEntity);
    }

    protected GrailsHibernateTemplate getHibernateTemplate() {
        return (GrailsHibernateTemplate) getNativeInterface();
    }

    public void setFlushMode(FlushModeType flushMode) {
        if (flushMode == FlushModeType.AUTO) {
            hibernateTemplate.setFlushMode(GrailsHibernateTemplate.FLUSH_AUTO);
        }
        else if (flushMode == FlushModeType.COMMIT) {
            hibernateTemplate.setFlushMode(GrailsHibernateTemplate.FLUSH_COMMIT);
        }
    }

    public FlushModeType getFlushMode() {
        switch (hibernateTemplate.getFlushMode()) {
            case GrailsHibernateTemplate.FLUSH_AUTO: return FlushModeType.AUTO;
            case GrailsHibernateTemplate.FLUSH_COMMIT: return FlushModeType.COMMIT;
            case GrailsHibernateTemplate.FLUSH_ALWAYS: return FlushModeType.AUTO;
            default: return FlushModeType.AUTO;
        }
    }
}
