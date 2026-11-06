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
import org.hibernate.proxy.HibernateProxy;

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

            org.hibernate.query.Query query = session.createQuery(applyRequestedJoins(jpaQueryInfo.getQuery(), criteria));
            getHibernateTemplate().applySettings(query);

            List parameters = jpaQueryInfo.getParameters();
            if (parameters != null) {
                for (int i = 0, count = parameters.size(); i < count; i++) {
                    query.setParameter(JpaQueryBuilder.PARAMETER_NAME_PREFIX + (i + 1), parameters.get(i));
                }
            }

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
            List parameters = jpaQueryInfo.getParameters();
            if (parameters != null) {
                for (int i = 0, count = parameters.size(); i < count; i++) {
                    query.setParameter(JpaQueryBuilder.PARAMETER_NAME_PREFIX + (i + 1), parameters.get(i));
                }
            }

            HibernateHqlQuery hqlQuery = new HibernateHqlQuery(HibernateSession.this, targetEntity, query);
            ApplicationEventPublisher applicationEventPublisher = datastore.getApplicationEventPublisher();
            applicationEventPublisher.publishEvent(new PreQueryEvent(datastore, hqlQuery));
            int result = query.executeUpdate();
            applicationEventPublisher.publishEvent(new PostQueryEvent(datastore, hqlQuery, Collections.singletonList(result)));
            return result;
        });
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
    private static String applyRequestedJoins(String query, QueryableCriteria criteria) {
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
     * association path returns a row, or null when the path does not consist of associations to
     * other entities. The restriction is a subquery that selects from the entity of the first
     * association and joins the rest of the path, so that the filters that are enabled for the
     * session apply to every entity on the path, as they do to a join.
     */
    private static String buildRequestedJoinRestriction(PersistentEntity entity, String associationPath, int index) {
        String rootAlias = entity.getDecapitalizedName();
        String[] tokens = associationPath.split("\\.");
        StringBuilder from = new StringBuilder();
        String correlation = null;
        String alias = null;
        PersistentEntity currentEntity = entity;
        for (int i = 0; i < tokens.length; i++) {
            PersistentProperty property = currentEntity != null ? currentEntity.getPropertyByName(tokens[i]) : null;
            if (!(property instanceof Association) || property instanceof Embedded || property instanceof Basic) {
                return null;
            }
            Association association = (Association) property;
            PersistentEntity associatedEntity = association.getAssociatedEntity();
            if (associatedEntity == null) {
                return null;
            }
            String joinAlias = "join" + index + '_' + i;
            if (i > 0) {
                from.append(" INNER JOIN ").append(alias).append('.').append(tokens[i]).append(' ').append(joinAlias);
            }
            else {
                from.append(associatedEntity.getName()).append(' ').append(joinAlias);
                if (association instanceof ToMany) {
                    correlation = joinAlias + " MEMBER OF " + rootAlias + '.' + tokens[i];
                }
                else if (association instanceof ToOne && ((ToOne) association).isForeignKeyInChild()) {
                    // the associated entity has the foreign key, so it is matched through its side of the association
                    String inverse = association.getReferencedPropertyName();
                    if (inverse == null) {
                        return null;
                    }
                    correlation = joinAlias + '.' + inverse + " = " + rootAlias;
                }
                else {
                    correlation = joinAlias + " = " + rootAlias + '.' + tokens[i];
                }
            }
            alias = joinAlias;
            currentEntity = associatedEntity;
        }
        return "EXISTS (SELECT 1 FROM " + from + " WHERE " + correlation + ")";
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
