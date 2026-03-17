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
import java.util.List;
import java.util.Map;

import jakarta.persistence.FlushModeType;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

import org.hibernate.*;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.query.MutationQuery;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import org.grails.datastore.gorm.timestamp.DefaultTimestampProvider;
import org.grails.datastore.mapping.core.AbstractAttributeStoringSession;
import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.engine.Persister;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.config.GormProperties;
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
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentEntity;
import org.grails.orm.hibernate.proxy.HibernateProxyHandler;
import org.grails.orm.hibernate.query.HibernateHqlQuery;
import org.grails.orm.hibernate.query.HibernateQuery;

/**
 * Session implementation that wraps a Hibernate {@link org.hibernate.Session}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings({"rawtypes", "PMD.DataflowAnomalyAnalysis", "PMD.AvoidDuplicateLiterals"})
public class HibernateSession extends AbstractAttributeStoringSession implements QueryAliasAwareSession {

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
        hibernateTemplate = new GrailsHibernateTemplate(sessionFactory, datastore);
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
            org.grails.datastore.mapping.model.PersistentEntity pe =
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
        hibernateTemplate.lock(o, LockMode.NONE);
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
        final PersistentEntity persistentEntity = getMappingContext().getPersistentEntity(type.getName());
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
            JpaQueryInfo jpaQueryInfo = builder.buildDelete();

            var query = createMutationQuery(session, jpaQueryInfo);

            HibernateHqlQuery hqlQuery =
                    new HibernateHqlQuery(HibernateSession.this, criteria.getPersistentEntity(), query);
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
            HibernatePersistentEntity targetEntity = (HibernatePersistentEntity) criteria.getPersistentEntity();
            PersistentProperty lastUpdated = targetEntity.getHibernatePropertyByName(GormProperties.LAST_UPDATED);
            if (lastUpdated != null && targetEntity.getMapping().getMappedForm().isAutoTimestamp()) {
                if (timestampProvider == null) {
                    timestampProvider = new DefaultTimestampProvider();
                }
                Class<?> type = lastUpdated.getType();
                properties.put(GormProperties.LAST_UPDATED, timestampProvider.createTimestamp(type));
            }

            JpaQueryInfo jpaQueryInfo = builder.buildUpdate(properties);

            var query = createMutationQuery(session, jpaQueryInfo);

            HibernateHqlQuery hqlQuery = new HibernateHqlQuery(HibernateSession.this, targetEntity, query);
            ApplicationEventPublisher applicationEventPublisher = datastore.getApplicationEventPublisher();
            applicationEventPublisher.publishEvent(new PreQueryEvent(datastore, hqlQuery));
            int result = query.executeUpdate();
            applicationEventPublisher.publishEvent(
                    new PostQueryEvent(datastore, hqlQuery, Collections.singletonList(result)));
            return result;
        });
    }

    @Override
    @SuppressWarnings({"PMD.DataflowAnomalyAnalysis", "unchecked"})
    public List retrieveAll(final Class type, final Iterable keys) {
        final PersistentEntity persistentEntity = getMappingContext().getPersistentEntity(type.getName());
        return getHibernateTemplate().execute(session -> {
            final CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder();
            CriteriaQuery<?> criteriaQuery = criteriaBuilder.createQuery(type);
            final Root<?> root = criteriaQuery.from(type);
            final String id = persistentEntity.getIdentity().getName();
            criteriaQuery = criteriaQuery.where(criteriaBuilder.in(root.get(id).in(getIterableAsCollection(keys))));
            final org.hibernate.query.Query<?> jpaQuery = session.createQuery(criteriaQuery);
            getHibernateTemplate().applySettings(jpaQuery);

            return new HibernateHqlQuery(this, persistentEntity, jpaQuery).list();
        });
    }

    @Override
    public Query createQuery(Class type) {
        return createQuery(type, null);
    }

    @Override
    public Query createQuery(Class type, String alias) {
        HibernateQuery query = new HibernateQuery(this, getMappingContext().getPersistentEntity(type.getName()));
        if (alias != null) {
            query.getDetachedCriteria().setAlias(alias);
        }
        return query;
    }

    protected GrailsHibernateTemplate getHibernateTemplate() {
        return (GrailsHibernateTemplate) getNativeInterface();
    }

    @Override
    public void setFlushMode(FlushModeType flushMode) {
        if (flushMode == FlushModeType.AUTO) {
            hibernateTemplate.setFlushMode(GrailsHibernateTemplate.FLUSH_AUTO);
        } else if (flushMode == FlushModeType.COMMIT) {
            hibernateTemplate.setFlushMode(GrailsHibernateTemplate.FLUSH_COMMIT);
        }
    }

    @Override
    public FlushModeType getFlushMode() {
        if (hibernateTemplate.getFlushMode() == GrailsHibernateTemplate.FLUSH_COMMIT) {
            return FlushModeType.COMMIT;
        }
        return FlushModeType.AUTO;
    }
}
