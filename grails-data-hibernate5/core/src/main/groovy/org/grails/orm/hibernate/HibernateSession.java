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
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import jakarta.persistence.FlushModeType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.proxy.HibernateProxy;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.InvalidDataAccessResourceUsageException;

import org.grails.datastore.gorm.query.criteria.AbstractDetachedCriteria;
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
     * {@code AbstractDetachedCriteria.defaultMax} and {@code defaultOffset}, read by reflection because this patch
     * line adds no public accessor for them. From 8.1.x on, the criteria has getters for them.
     */
    private static final Field DEFAULT_MAX = criteriaField("defaultMax");
    private static final Field DEFAULT_OFFSET = criteriaField("defaultOffset");

    private static Field criteriaField(String name) {
        try {
            Field field = AbstractDetachedCriteria.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        }
        catch (NoSuchFieldException e) {
            throw new IllegalStateException("AbstractDetachedCriteria has no field " + name, e);
        }
    }

    private static Integer criteriaValue(Field field, AbstractDetachedCriteria<?> criteria) {
        try {
            return (Integer) field.get(criteria);
        }
        catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot read " + field.getName() + " of " + criteria.getClass().getName(), e);
        }
    }

    /**
     * A bulk {@code delete} or {@code update} statement has no {@code LIMIT} or {@code OFFSET}, so a
     * criteria that carries {@code max} or an {@code offset} would write to every matching row. Refuse it. Any
     * {@code offset} that was set is refused, zero included: a bulk write cannot apply it, and the call says what it
     * leaves out.
     */
    private static void rejectPagination(QueryableCriteria<?> criteria, String operation) {
        if (!(criteria instanceof AbstractDetachedCriteria)) {
            return;
        }
        Integer max = criteriaValue(DEFAULT_MAX, (AbstractDetachedCriteria<?>) criteria);
        Integer offset = criteriaValue(DEFAULT_OFFSET, (AbstractDetachedCriteria<?>) criteria);
        if (max == null && offset == null) {
            return;
        }
        StringBuilder unsupported = new StringBuilder();
        if (max != null) {
            unsupported.append("max(").append(max).append(')');
        }
        if (offset != null) {
            if (unsupported.length() > 0) {
                unsupported.append(", ");
            }
            unsupported.append("offset(").append(offset).append(')');
        }
        throw new InvalidDataAccessResourceUsageException(operation + " cannot limit the rows it writes to, " +
                "so it does not support " + unsupported + ": call it before max or offset are applied to the query, " +
                "which a criteria cannot drop once they are set, or select the ids first and write by id");
    }

    /**
     * Deletes all objects matching the given criteria.
     *
     * @param criteria The criteria
     * @return The total number of records deleted
     */
    public long deleteAll(final QueryableCriteria criteria) {
        rejectPagination(criteria, "deleteAll()");
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
        rejectPagination(criteria, "updateAll()");
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
