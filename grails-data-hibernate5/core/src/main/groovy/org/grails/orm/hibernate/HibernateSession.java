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
import jakarta.persistence.criteria.Root;

import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.proxy.HibernateProxy;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.convert.ConversionService;

import org.grails.datastore.gorm.timestamp.DefaultTimestampProvider;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.config.GormProperties;
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

            org.hibernate.query.Query query = session.createQuery(jpaQueryInfo.getQuery());
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

            org.hibernate.query.Query query = session.createQuery(jpaQueryInfo.getQuery());
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

            final Map<Object, Object> entitiesById = new HashMap<>();
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
                    entitiesById.put(session.getIdentifier(entity), entity);
                }
            }

            // Reassemble in the requested order, leaving a null slot for missing ids.
            final List ordered = new ArrayList<>(requestedIds.size());
            for (Serializable requestedId : requestedIds) {
                ordered.add(requestedId == null ? null : entitiesById.get(requestedId));
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
