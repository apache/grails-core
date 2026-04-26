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
package org.grails.orm.hibernate

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

import jakarta.persistence.FlushModeType
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Root

import org.hibernate.Criteria
import org.hibernate.FlushMode
import org.hibernate.LockMode
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.query.Query

import org.springframework.core.convert.ConversionService
import org.grails.orm.hibernate.support.hibernate5.SessionHolder
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionSynchronizationManager

import grails.orm.HibernateCriteriaBuilder
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.gorm.finders.DynamicFinder
import org.grails.datastore.gorm.finders.FinderMethod
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.query.api.BuildableCriteria as GrailsCriteria
import org.grails.datastore.mapping.query.event.PostQueryEvent
import org.grails.datastore.mapping.query.event.PreQueryEvent
import org.grails.orm.hibernate.exceptions.GrailsQueryException
import org.grails.orm.hibernate.query.GrailsHibernateQueryUtils
import org.grails.orm.hibernate.query.HibernateHqlQuery
import org.grails.orm.hibernate.query.HibernateQuery
import org.grails.orm.hibernate.query.PagedResultList

/**
 * The implementation of the GORM static method contract for Hibernate
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class HibernateGormStaticApi<D> extends AbstractHibernateGormStaticApi<D> {

    protected SessionFactory sessionFactory
    protected ConversionService conversionService
    protected Class identityType
    protected ClassLoader classLoader
    private HibernateGormInstanceApi<D> instanceApi
    private int defaultFlushMode

    HibernateGormStaticApi(Class<D> persistentClass, HibernateDatastore datastore, List<FinderMethod> finders,
                ClassLoader classLoader, PlatformTransactionManager transactionManager) {
        super(persistentClass, datastore, finders, transactionManager)
        this.classLoader = classLoader
        sessionFactory = datastore.getSessionFactory()
        conversionService = datastore.mappingContext.conversionService

        identityType = getPersistentEntity().identity?.type
        this.defaultFlushMode = datastore.getDefaultFlushMode()
        instanceApi = new HibernateGormInstanceApi<>(persistentClass, datastore, classLoader)
    }

    HibernateGormStaticApi(Class<D> persistentClass, org.grails.datastore.mapping.model.MappingContext mappingContext, List<FinderMethod> finders, org.grails.datastore.gorm.DatastoreResolver datastoreResolver, String qualifier, ClassLoader classLoader) {
        super(persistentClass, mappingContext, finders, datastoreResolver, qualifier)
        this.classLoader = classLoader
    }

    protected HibernateDatastore getHibernateDatastore() {
        (HibernateDatastore) getDatastore()
    }

    protected SessionFactory getSessionFactory() {
        if (sessionFactory == null) {
            return hibernateDatastore.getSessionFactory()
        }
        return sessionFactory
    }

    protected ConversionService getConversionService() {
        if (conversionService == null) {
            return hibernateDatastore.mappingContext.conversionService
        }
        return conversionService
    }

    protected Class getIdentityType() {
        if (identityType == null) {
            return getPersistentEntity().identity?.type
        }
        return identityType
    }

    @Override
    GrailsHibernateTemplate getHibernateTemplate() {
        return (GrailsHibernateTemplate) super.getHibernateTemplate()
    }

    @Override
    List<D> list(Map params = Collections.emptyMap()) {
        hibernateTemplate.execute { Session session ->
            PersistentEntity entity = getPersistentEntity()
            CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder()
            CriteriaQuery criteriaQuery = criteriaBuilder.createQuery(entity.javaClass)
            Root queryRoot = criteriaQuery.from(entity.javaClass)
            GrailsHibernateQueryUtils.populateArgumentsForCriteria(
                    entity,
                    criteriaQuery,
                    queryRoot,
                    criteriaBuilder,
                    params,
                    datastore.mappingContext.conversionService,
                    true
            )
            Query query = session.createQuery(criteriaQuery)

            GrailsHibernateQueryUtils.populateArgumentsForCriteria(
                    entity,
                    query,
                    params,
                    datastore.mappingContext.conversionService,
                    true
            )

            HibernateHqlQuery hibernateQuery = new HibernateHqlQuery(
                    new HibernateSession((HibernateDatastore) datastore, getSessionFactory()),
                    entity,
                    query
            )
            hibernateTemplate.applySettings(query)

            params = params ? new HashMap(params) : Collections.emptyMap()
            if (params.containsKey(DynamicFinder.ARGUMENT_MAX)) {
                return new PagedResultList(
                        hibernateTemplate,
                        entity,
                        hibernateQuery,
                        criteriaQuery,
                        queryRoot,
                        criteriaBuilder
                )
            }
            else {
                return hibernateQuery.list()
            }
        }
    }

    @Override
    def propertyMissing(String name) {
        return GormEnhancer.findStaticApi(persistentClass, name)
    }

    @Override
    GrailsCriteria createCriteria() {
        def builder = new HibernateCriteriaBuilder(persistentClass, getSessionFactory())
        builder.datastore = (AbstractHibernateDatastore) datastore
        builder.conversionService = getConversionService()
        return builder
    }

    @Override
    D lock(Serializable id) {
        (D) hibernateTemplate.lock((Class)persistentClass, convertIdentifier(id), LockMode.PESSIMISTIC_WRITE)
    }

    @Override
    Integer executeUpdate(CharSequence query, Map params, Map args) {

        if (query instanceof GString) {
            params = new LinkedHashMap(params)
            query = buildNamedParameterQueryFromGString((GString) query, params)
        }

        def template = hibernateTemplate
        SessionFactory sessionFactory = getSessionFactory()
        return (Integer) template.execute { Session session ->
            Query q = (Query) session.createQuery(query.toString())
            template.applySettings(q)
            def sessionHolder = (SessionHolder) TransactionSynchronizationManager.getResource(sessionFactory)
            if (sessionHolder && sessionHolder.hasTimeout()) {
                q.timeout = sessionHolder.timeToLiveInSeconds
            }

            populateQueryArguments(q, params)
            populateQueryArguments(q, args)
            populateQueryWithNamedArguments(q, params)

            return withQueryEvents(q) {
                q.executeUpdate()
            }
        }
    }

    @Override
    Integer executeUpdate(CharSequence query, Collection params, Map args) {
        if (query instanceof GString) {
            throw new GrailsQueryException("Unsafe query [$query]. GORM cannot automatically escape a GString value when combined with ordinal parameters, so this query is potentially vulnerable to HQL injection attacks. Please embed the parameters within the GString so they can be safely escaped.")
        }

        def template = hibernateTemplate
        SessionFactory sessionFactory = getSessionFactory()

        return (Integer) template.execute { Session session ->
            Query q = (Query) session.createQuery(query.toString())
            template.applySettings(q)
            def sessionHolder = (SessionHolder) TransactionSynchronizationManager.getResource(sessionFactory)
            if (sessionHolder && sessionHolder.hasTimeout()) {
                q.timeout = sessionHolder.timeToLiveInSeconds
            }

            params.eachWithIndex { val, int i ->
                if (val instanceof CharSequence) {
                    q.setParameter(i, val.toString())
                }
                else {
                    q.setParameter(i, val)
                }
            }
            populateQueryArguments(q, args)
            return withQueryEvents(q) {
                q.executeUpdate()
            }
        }
    }

    protected <T> T withQueryEvents(Query query, Closure<T> callable) {
        HibernateDatastore hibernateDatastore = (HibernateDatastore) datastore

        def eventPublisher = hibernateDatastore.applicationEventPublisher
        PersistentEntity entity = getPersistentEntity()

        def hqlQuery = new HibernateHqlQuery(new HibernateSession(hibernateDatastore, getSessionFactory()), entity, query)
        eventPublisher.publishEvent(new PreQueryEvent(hibernateDatastore, hqlQuery))

        def result = callable.call()

        eventPublisher.publishEvent(new PostQueryEvent(hibernateDatastore, hqlQuery, Collections.singletonList(result)))
        return result
    }

    @Override
    protected void firePostQueryEvent(org.grails.datastore.mapping.core.Session session, Criteria criteria, Object result) {
        PersistentEntity entity = getPersistentEntity()
        if (result instanceof List) {
            datastore.applicationEventPublisher.publishEvent(new PostQueryEvent(datastore, new HibernateQuery(criteria, entity), (List) result))
        }
        else {
            datastore.applicationEventPublisher.publishEvent(new PostQueryEvent(datastore, new HibernateQuery(criteria, entity), Collections.singletonList(result)))
        }
    }

    @Override
    protected void firePreQueryEvent(org.grails.datastore.mapping.core.Session session, Criteria criteria) {
        PersistentEntity entity = getPersistentEntity()
        datastore.applicationEventPublisher.publishEvent(new PreQueryEvent(datastore, new HibernateQuery(criteria, entity)))
    }

    @Override
    protected HibernateHqlQuery createHqlQuery(org.grails.datastore.mapping.core.Session session, Query q) {
        HibernateSession hibernateSession = new HibernateSession((HibernateDatastore) datastore, getSessionFactory())
        FlushMode hibernateMode = ((Session)session.getNativeInterface()).getHibernateFlushMode()
        switch (hibernateMode) {
            case FlushMode.AUTO:
                hibernateSession.setFlushMode(FlushModeType.AUTO)
                break
            case FlushMode.ALWAYS:
                hibernateSession.setFlushMode(FlushModeType.AUTO)
                break
            default:
                hibernateSession.setFlushMode(FlushModeType.COMMIT)

        }
        HibernateHqlQuery query = new HibernateHqlQuery(hibernateSession, getPersistentEntity(), q)
        return query
    }

    @CompileDynamic
    protected void setResultTransformer(Criteria c) {
        c.resultTransformer = Criteria.DISTINCT_ROOT_ENTITY
    }
}
