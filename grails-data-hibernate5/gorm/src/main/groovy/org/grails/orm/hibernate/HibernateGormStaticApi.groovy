/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.orm.hibernate

import grails.orm.HibernateCriteriaBuilder
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.gorm.finders.DynamicFinder
import org.grails.datastore.gorm.finders.FinderMethod
import org.grails.datastore.mapping.query.api.BuildableCriteria as GrailsCriteria
import org.grails.datastore.mapping.query.event.PostQueryEvent
import org.grails.datastore.mapping.query.event.PreQueryEvent
import org.grails.orm.hibernate.exceptions.GrailsQueryException
import org.grails.orm.hibernate.query.GrailsHibernateQueryUtils
import org.grails.orm.hibernate.query.HibernateHqlQuery
import org.grails.orm.hibernate.query.PagedResultList
import org.grails.orm.hibernate.query.HibernateQuery
import org.hibernate.*
import org.hibernate.query.Query
import org.springframework.core.convert.ConversionService
import org.springframework.orm.hibernate5.SessionHolder
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionSynchronizationManager

import jakarta.persistence.FlushModeType
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Root

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

        identityType = persistentEntity.identity?.type
        this.defaultFlushMode = datastore.getDefaultFlushMode()
        instanceApi = new HibernateGormInstanceApi<>(persistentClass, datastore, classLoader)
    }

    @Override
    GrailsHibernateTemplate getHibernateTemplate() {
        return (GrailsHibernateTemplate)super.getHibernateTemplate()
    }

    @Override
    List<D> list(Map params = Collections.emptyMap()) {
        hibernateTemplate.execute { Session session ->
            CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder()
            CriteriaQuery criteriaQuery = criteriaBuilder.createQuery(persistentEntity.javaClass)
            Root queryRoot = criteriaQuery.from(persistentEntity.javaClass)
            GrailsHibernateQueryUtils.populateArgumentsForCriteria(
                    persistentEntity,
                    criteriaQuery,
                    queryRoot,
                    criteriaBuilder,
                    params,
                    datastore.mappingContext.conversionService,
                    true
            )
            Query query = session.createQuery(criteriaQuery)

            GrailsHibernateQueryUtils.populateArgumentsForCriteria(
                    persistentEntity,
                    query,
                    params,
                    datastore.mappingContext.conversionService,
                    true
            )

            HibernateHqlQuery hibernateQuery = new HibernateHqlQuery(
                    new HibernateSession((HibernateDatastore)datastore, sessionFactory),
                    persistentEntity,
                    query
            )
            hibernateTemplate.applySettings(query)

            params = params ? new HashMap(params) : Collections.emptyMap()
            if(params.containsKey(DynamicFinder.ARGUMENT_MAX)) {
                return new PagedResultList(
                        hibernateTemplate,
                        persistentEntity,
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
        def builder = new HibernateCriteriaBuilder(persistentClass, sessionFactory)
        builder.datastore = (AbstractHibernateDatastore)datastore
        builder.conversionService = conversionService
        return builder
    }

    @Override
    D lock(Serializable id) {
        (D)hibernateTemplate.lock((Class)persistentClass, convertIdentifier(id), LockMode.PESSIMISTIC_WRITE)
    }

    @Override
    Integer executeUpdate(CharSequence query, Map params, Map args) {

        if(query instanceof GString) {
            params = new LinkedHashMap(params)
            query = buildNamedParameterQueryFromGString((GString) query, params)
        }

        def template = hibernateTemplate
        SessionFactory sessionFactory = this.sessionFactory
        return (Integer) template.execute { Session session ->
            Query q = (Query) session.createQuery(query.toString())
            template.applySettings(q)
            def sessionHolder = (SessionHolder) TransactionSynchronizationManager.getResource( sessionFactory )
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
        if(query instanceof GString) {
            throw new GrailsQueryException("Unsafe query [$query]. GORM cannot automatically escape a GString value when combined with ordinal parameters, so this query is potentially vulnerable to HQL injection attacks. Please embed the parameters within the GString so they can be safely escaped.");
        }

        def template = hibernateTemplate
        SessionFactory sessionFactory = this.sessionFactory

        return (Integer) template.execute { Session session ->
            Query q = (Query) session.createQuery(query.toString())
            template.applySettings(q)
            def sessionHolder = (SessionHolder) TransactionSynchronizationManager.getResource( sessionFactory )
            if (sessionHolder && sessionHolder.hasTimeout()) {
                q.timeout = sessionHolder.timeToLiveInSeconds
            }


            params.eachWithIndex { val, int i ->
                if (val instanceof CharSequence) {
                    q.setParameter i, val.toString()
                }
                else {
                    q.setParameter i, val
                }
            }
            populateQueryArguments(q, args)
            return withQueryEvents(q) {
                q.executeUpdate()
            }
        }
    }

    protected <T> T withQueryEvents(Query query, Closure<T> callable) {
        HibernateDatastore hibernateDatastore = (HibernateDatastore)datastore

        def eventPublisher = hibernateDatastore.applicationEventPublisher

        def hqlQuery = new HibernateHqlQuery(new HibernateSession(hibernateDatastore, sessionFactory), persistentEntity, query)
        eventPublisher.publishEvent(new PreQueryEvent(hibernateDatastore, hqlQuery))

        def result = callable.call()

        eventPublisher.publishEvent(new PostQueryEvent(hibernateDatastore, hqlQuery, Collections.singletonList(result)))
        return result
    }

    @Override
    protected void firePostQueryEvent(Session session, Criteria criteria, Object result) {
        if(result instanceof List) {
            datastore.applicationEventPublisher.publishEvent( new PostQueryEvent(datastore, new HibernateQuery(criteria, persistentEntity), (List)result))
        }
        else {
            datastore.applicationEventPublisher.publishEvent( new PostQueryEvent(datastore, new HibernateQuery(criteria, persistentEntity), Collections.singletonList(result)))
        }
    }

    @Override
    protected void firePreQueryEvent(Session session, Criteria criteria) {
        datastore.applicationEventPublisher.publishEvent( new PreQueryEvent(datastore, new HibernateQuery(criteria, persistentEntity)))
    }

    @Override
    protected HibernateHqlQuery createHqlQuery(Session session, Query q) {
        HibernateSession hibernateSession = new HibernateSession((HibernateDatastore) datastore, sessionFactory)
        FlushMode hibernateMode = session.getHibernateFlushMode()
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
        HibernateHqlQuery query = new HibernateHqlQuery(hibernateSession, persistentEntity, q)
        return query
    }

    @CompileDynamic
    protected void setResultTransformer(Criteria c) {
        c.resultTransformer = Criteria.DISTINCT_ROOT_ENTITY
    }
}
