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
import java.util.Collections;
import java.util.List;
import java.util.Map;

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

import org.grails.datastore.gorm.query.criteria.AbstractDetachedCriteria;
import org.grails.datastore.gorm.timestamp.DefaultTimestampProvider;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.Basic;
import org.grails.datastore.mapping.model.types.Embedded;
import org.grails.datastore.mapping.model.types.ToMany;
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
     * The number of identifiers that a delete by identifiers deletes with one statement, below the
     * number of parameters that databases allow in a list.
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
            String delete = applyRequestedJoins(jpaQueryInfo.getQuery(), criteria);
            if (!delete.equals(jpaQueryInfo.getQuery()) && restrictsThroughJoinTable(criteria)) {
                return deleteByIdentifiers(session, criteria.getPersistentEntity(), delete, jpaQueryInfo.getParameters());
            }

            org.hibernate.query.Query query = session.createQuery(delete);
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

            org.hibernate.query.Query query = session.createQuery(applyRequestedJoins(jpaQueryInfo.getQuery(), criteria));
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

    private static void setParameters(org.hibernate.query.Query query, List parameters) {
        if (parameters != null) {
            for (int i = 0, count = parameters.size(); i < count; i++) {
                query.setParameter(JpaQueryBuilder.PARAMETER_NAME_PREFIX + (i + 1), parameters.get(i));
            }
        }
    }

    /**
     * Whether a join type that the criteria request is applied through a collection of the queried
     * class that has a join table. Hibernate deletes the rows of the join tables of the instances
     * before it deletes the instances, with the same restriction, which then no longer finds the
     * instances: see {@link #deleteByIdentifiers(Session, PersistentEntity, String, List)}.
     */
    private boolean restrictsThroughJoinTable(QueryableCriteria criteria) {
        if (!(criteria instanceof AbstractDetachedCriteria)) {
            return false;
        }
        SessionFactoryImplementor factory = (SessionFactoryImplementor) getHibernateTemplate().getSessionFactory();
        EntityPersister persister = factory.getMetamodel().entityPersister(criteria.getPersistentEntity().getName());
        Map<String, JoinType> joinTypes = ((AbstractDetachedCriteria<?>) criteria).getJoinTypes();
        for (Map.Entry<String, JoinType> requestedJoin : joinTypes.entrySet()) {
            if (requestedJoin.getValue() == null || requestedJoin.getValue() == JoinType.LEFT) {
                continue;
            }
            String association = requestedJoin.getKey().split("\\.")[0];
            Type type;
            try {
                type = persister.getPropertyType(association);
            }
            catch (RuntimeException e) {
                continue;
            }
            if (type instanceof CollectionType && factory.getMetamodel().collectionPersister(((CollectionType) type).getRole()).isManyToMany()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Deletes the instances that a delete query restricts to by their identifiers. The identifiers
     * are selected first, with the restriction of the delete, so that the restriction is evaluated
     * before Hibernate deletes the rows of the join tables of the instances.
     */
    private int deleteByIdentifiers(Session session, PersistentEntity entity, String delete, List parameters) {
        String alias = entity.getDecapitalizedName();
        String prefix = JpaQueryBuilder.DELETE_CLAUSE + entity.getName() + ' ' + alias;
        SessionFactoryImplementor factory = (SessionFactoryImplementor) getHibernateTemplate().getSessionFactory();
        String identifierName = factory.getMetamodel().entityPersister(entity.getName()).getIdentifierPropertyName();
        String identifier = alias + '.' + (identifierName != null ? identifierName : "id");
        ApplicationEventPublisher applicationEventPublisher = datastore.getApplicationEventPublisher();

        org.hibernate.query.Query select = session.createQuery("SELECT " + identifier + " FROM " + entity.getName() + ' ' + alias + delete.substring(prefix.length()));
        getHibernateTemplate().applySettings(select);
        setParameters(select, parameters);
        HibernateHqlQuery selectQuery = new HibernateHqlQuery(HibernateSession.this, entity, select);
        applicationEventPublisher.publishEvent(new PreQueryEvent(datastore, selectQuery));
        List identifiers = select.list();
        applicationEventPublisher.publishEvent(new PostQueryEvent(datastore, selectQuery, identifiers));

        int deleted = 0;
        for (int from = 0; from < identifiers.size(); from += IDENTIFIER_BATCH_SIZE) {
            List batch = identifiers.subList(from, Math.min(from + IDENTIFIER_BATCH_SIZE, identifiers.size()));
            org.hibernate.query.Query query = session.createQuery(prefix + " WHERE " + identifier + " IN (:identifiers)");
            getHibernateTemplate().applySettings(query);
            query.setParameterList("identifiers", batch);
            HibernateHqlQuery hqlQuery = new HibernateHqlQuery(HibernateSession.this, entity, query);
            applicationEventPublisher.publishEvent(new PreQueryEvent(datastore, hqlQuery));
            int result = query.executeUpdate();
            applicationEventPublisher.publishEvent(new PostQueryEvent(datastore, hqlQuery, Collections.singletonList(result)));
            deleted += result;
        }
        return deleted;
    }

    /**
     * Restricts an update or a delete to the instances that the criteria return when they are
     * listed. A join type other than a left join that the criteria request for an association
     * leaves out the instances for which the join returns no row. An update and a delete cannot
     * join, so each such join is applied as a restriction, see
     * {@link #buildRequestedJoinRestriction(PersistentEntity, String, int)}.
     *
     * @param query The update or delete query of the criteria
     * @param criteria The criteria
     * @return The query with the restrictions
     */
    private String applyRequestedJoins(String query, QueryableCriteria criteria) {
        if (!(criteria instanceof AbstractDetachedCriteria)) {
            return query;
        }
        Map<String, JoinType> joinTypes = ((AbstractDetachedCriteria<?>) criteria).getJoinTypes();
        StringBuilder restricted = new StringBuilder(query);
        boolean hasWhereClause = !criteria.getCriteria().isEmpty();
        int index = 0;
        for (Map.Entry<String, JoinType> requestedJoin : joinTypes.entrySet()) {
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
     * Returns the restriction to the instances of an entity for which an inner join of an
     * association path returns a row, or null when the path does not consist of associations. The
     * restriction is a subquery that selects from the entity of the first association and joins the
     * rest of the path, so that the filters that are enabled for the session apply to every entity on
     * the path, as they do to a join. The subquery is matched with the instance through the
     * association, by the side of it that has the foreign key: a one-to-one whose foreign key the
     * other entity has, such as a {@code hasOne}, is matched through the property of the other entity
     * that refers back. A collection of a basic type has no entity of its own, so a path that starts
     * with one selects from the entity itself.
     * <p>
     * MySQL does not allow an update or a delete to select from the table that it modifies. A path
     * that leads back to the class of the entity cannot be applied there, and is rejected instead of
     * being left to fail in the database.
     *
     * @throws UnsupportedOperationException if the join cannot be applied to an update or a delete
     */
    private String buildRequestedJoinRestriction(PersistentEntity entity, String associationPath, int index) {
        String rootAlias = entity.getDecapitalizedName();
        String[] tokens = associationPath.split("\\.");
        int last = tokens.length - 1;
        EntityPersister persister = ((SessionFactoryImplementor) getHibernateTemplate().getSessionFactory()).getMetamodel().entityPersister(entity.getName());
        StringBuilder from = new StringBuilder();
        String correlation = null;
        String alias = null;
        boolean selectsFromModifiedTable = false;
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
                Type type = persister.getPropertyType(tokens[i]);
                String referencedProperty = type instanceof OneToOneType ? ((OneToOneType) type).getRHSUniqueKeyPropertyName() : null;
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
        if (selectsFromModifiedTable && !canSelectFromModifiedTable()) {
            throw new UnsupportedOperationException("The join type requested for [" + associationPath + "] of [" + entity.getName() +
                    "] cannot be applied to an update or a delete on this database, which does not allow them to select from the table that they modify. " +
                    "Do not request the join type for the update or the delete, and restrict the query with criteria instead.");
        }
        return "EXISTS (SELECT 1 FROM " + from + " WHERE " + correlation + ")";
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
        return getHibernateTemplate().execute(session -> {
            final CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder();
            CriteriaQuery criteriaQuery = criteriaBuilder.createQuery(type);
            final Root root = criteriaQuery.from(type);
            final String id = persistentEntity.getIdentity().getName();
            criteriaQuery = criteriaQuery.where(
                criteriaBuilder.in(
                    root.get(id).in(getIterableAsCollection(keys))
                )
            );
            final org.hibernate.query.Query jpaQuery = session.createQuery(criteriaQuery);
            getHibernateTemplate().applySettings(jpaQuery);

            return new HibernateHqlQuery(this, persistentEntity, jpaQuery).list();
        });
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
