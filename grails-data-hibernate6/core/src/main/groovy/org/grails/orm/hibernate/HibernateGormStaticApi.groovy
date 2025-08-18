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
import groovy.util.logging.Slf4j
import jakarta.persistence.NoResultException
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Root
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.gorm.GormStaticApi
import org.grails.datastore.gorm.finders.DynamicFinder
import org.grails.datastore.gorm.finders.FinderMethod
import org.grails.datastore.mapping.query.api.BuildableCriteria as GrailsCriteria
import org.grails.datastore.mapping.query.event.PostQueryEvent
import org.grails.datastore.mapping.query.event.PreQueryEvent
import org.grails.datastore.mapping.proxy.ProxyHandler
import org.grails.datastore.mapping.reflect.ClassUtils
import org.grails.orm.hibernate.cfg.AbstractGrailsDomainBinder
import org.grails.orm.hibernate.cfg.CompositeIdentity
import org.grails.orm.hibernate.exceptions.GrailsQueryException
import org.grails.orm.hibernate.query.GrailsHibernateQueryUtils
import org.grails.orm.hibernate.query.HibernateHqlQuery
import org.grails.orm.hibernate.query.HibernateQuery
import org.grails.orm.hibernate.query.PagedResultList
import org.grails.orm.hibernate.support.HibernateRuntimeUtils
import org.hibernate.FlushMode
import org.hibernate.LockMode
import org.hibernate.NonUniqueResultException
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.jpa.QueryHints
import org.hibernate.query.NativeQuery
import org.hibernate.query.Query
import org.hibernate.query.criteria.JpaPredicate
import org.springframework.core.convert.ConversionService
import org.springframework.orm.hibernate5.SessionHolder
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionSynchronizationManager

import jakarta.persistence.FlushModeType
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery

/**
 * The implementation of the GORM static method contract for Hibernate
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Slf4j
@CompileStatic
class HibernateGormStaticApi<D> extends GormStaticApi<D> {

    protected GrailsHibernateTemplate hibernateTemplate
    protected ConversionService conversionService
    protected final HibernateSession hibernateSession
    protected ProxyHandler proxyHandler
    protected SessionFactory sessionFactory
    protected Class identityType
    protected ClassLoader classLoader
    private HibernateGormInstanceApi<D> instanceApi
    private int defaultFlushMode

    HibernateGormStaticApi(Class<D> persistentClass, HibernateDatastore datastore, List<FinderMethod> finders,
                           ClassLoader classLoader, PlatformTransactionManager transactionManager) {
        super(persistentClass, datastore, finders, transactionManager)
        this.hibernateTemplate = new GrailsHibernateTemplate(datastore.getSessionFactory(), datastore)
        this.conversionService = datastore.mappingContext.conversionService
        this.proxyHandler = datastore.mappingContext.proxyHandler
        this.hibernateSession = new HibernateSession(
                (HibernateDatastore)datastore,
                hibernateTemplate.getSessionFactory(),
                hibernateTemplate.getFlushMode()
        )
        this.classLoader = classLoader
        this.sessionFactory = datastore.getSessionFactory()
        this.identityType = persistentEntity.identity?.type
        this.defaultFlushMode = datastore.getDefaultFlushMode()
        this.instanceApi = new HibernateGormInstanceApi<>(persistentClass, datastore, classLoader)
    }

    GrailsHibernateTemplate getHibernateTemplate() {
        return hibernateTemplate as GrailsHibernateTemplate
    }

    @Override
    public <T> T withNewSession(Closure<T> callable) {
        AbstractHibernateDatastore hibernateDatastore = (AbstractHibernateDatastore) datastore
        hibernateDatastore.withNewSession(callable)
    }

    @Override
    def <T> T withSession(Closure<T> callable) {
        AbstractHibernateDatastore hibernateDatastore = (AbstractHibernateDatastore) datastore
        hibernateDatastore.withSession(callable)
    }

    D get(Serializable id) {
        if (id == null) {
            return null
        }

        id = convertIdentifier(id)

        if (id == null) {
            return null
        }

        if(persistentEntity.isMultiTenant()) {
            // for multi-tenant entities we process get(..) via a query
            (D)hibernateTemplate.execute(  { Session session ->
                return new HibernateQuery(hibernateSession,persistentEntity ).idEq(id).singleResult()
            } )
        }
        else {
            // for non multi-tenant entities we process get(..) via the second level cache
            hibernateTemplate.get(persistentEntity.javaClass, id)
        }
    }

    D read(Serializable id) {
        if (id == null) {
            return null
        }
        id = convertIdentifier(id)

        if (id == null) {
            return null
        }

        (D)hibernateTemplate.execute(  { Session session ->
            CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder()
            CriteriaQuery criteriaQuery = criteriaBuilder.createQuery(persistentEntity.javaClass)

            Root queryRoot = criteriaQuery.from(persistentEntity.javaClass)
            criteriaQuery = criteriaQuery.where (
                    //TODO: Remove explicit type cast once GROOVY-9460
                    criteriaBuilder.equal((Expression<?>)  queryRoot.get(persistentEntity.identity.name), id)
            )
            Query criteria = session.createQuery(criteriaQuery)
                    .setHint(QueryHints.HINT_READONLY, true)
            HibernateHqlQuery hibernateHqlQuery = new HibernateHqlQuery(
                    hibernateSession, persistentEntity, criteria)
            return proxyHandler.unwrap( hibernateHqlQuery.singleResult() )
        } )
    }

    @Override
    D load(Serializable id) {
        id = convertIdentifier(id)
        if (id != null) {
            return (D) hibernateTemplate.load((Class)persistentClass, id)
        }
        else {
            return null
        }
    }

    @Override
    List<D> getAll() {
        (List<D>)hibernateTemplate.execute({ Session session ->
            CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder()
            CriteriaQuery criteriaQuery = criteriaBuilder.createQuery(persistentEntity.javaClass)
            Query criteria = session.createQuery(criteriaQuery)
            HibernateHqlQuery hibernateHqlQuery = new HibernateHqlQuery(
                    hibernateSession, persistentEntity, criteria)
            return hibernateHqlQuery.list()
        })
    }

    protected HibernateQuery createHibernateQuery() {
        new HibernateQuery(hibernateSession, persistentEntity)
    }

    @Override
    Integer count() {
        (Integer)hibernateTemplate.execute({ Session session ->
            CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder()
            CriteriaQuery criteriaQuery = criteriaBuilder.createQuery(Long.class)
            criteriaQuery.select(criteriaBuilder.count(criteriaQuery.from(persistentEntity.javaClass)))
            Query criteria = session.createQuery(criteriaQuery)
            Long result =0
            try {
                result = criteria.singleResult as Long
            } catch (NonUniqueResultException nonUniqueResultException) {
                log.warn(nonUniqueResultException.toString())
            } catch (NoResultException noResultException) {
                log.warn(noResultException.toString())
            }
            return result
        })
    }

    @Override
    boolean exists(Serializable id) {
        id = convertIdentifier(id)
        hibernateTemplate.execute  { Session session ->
            return new HibernateQuery(hibernateSession,persistentEntity ).idEq(id).list().size()  > 0
        }
    }

    @Override
    D first(Map m) {
        def entityMapping = AbstractGrailsDomainBinder.getMapping(persistentEntity.javaClass)
        if (entityMapping?.identity instanceof CompositeIdentity) {
            throw new UnsupportedOperationException('The first() method is not supported for domain classes that have composite keys.')
        }
        super.first(m)
    }

    @Override
    D last(Map m) {
        def entityMapping = AbstractGrailsDomainBinder.getMapping(persistentEntity.javaClass)
        if (entityMapping?.identity instanceof CompositeIdentity) {
            throw new UnsupportedOperationException('The last() method is not supported for domain classes that have composite keys.')
        }
        super.last(m)
    }

    @Override
    D find(CharSequence query, Map queryNamedArgs, Map args) {
        queryNamedArgs = new LinkedHashMap(queryNamedArgs)
        args = new LinkedHashMap(args)
        if(query instanceof GString) {
            query = buildNamedParameterQueryFromGString((GString) query, queryNamedArgs)
        }

        String queryString = query.toString()
        query = normalizeMultiLineQueryString(queryString)

        def template = hibernateTemplate
        queryNamedArgs = new HashMap(queryNamedArgs)
        return (D) template.execute { Session session ->
            def hqlQuery = HibernateHqlQuery.createHqlQuery(session, datastore as HibernateDatastore, sessionFactory, persistentEntity, queryString, false)
            template.applySettings(hqlQuery.getQuery());
            hqlQuery.populateQuerySettings(queryNamedArgs)
            hqlQuery.populateQuerySettings(args)
            hqlQuery.populateQueryWithNamedArguments(queryNamedArgs)
            hqlQuery.singleResult()
        }
    }

    @Override
    D find(CharSequence query, Collection params, Map args) {
        def result = numberedParameterQuery(query, args, params)
        result ? result.first() : null
    }

    @Override
    List<D> findAll(CharSequence query, Map params, Map args) {
        params = new LinkedHashMap(params)
        args = new LinkedHashMap(args)
        if(query instanceof GString) {
            query = buildNamedParameterQueryFromGString((GString) query, params)
        }

        String queryString = query.toString()
        queryString = normalizeMultiLineQueryString(queryString)

        def template = hibernateTemplate
        return (List<D>) template.execute { Session session ->
            def hibernateHqlQuery = HibernateHqlQuery.createHqlQuery(session, datastore as HibernateDatastore, sessionFactory, persistentEntity,queryString, false)
            template.applySettings(hibernateHqlQuery.getQuery())
            hibernateHqlQuery.populateQuerySettings(params)
            hibernateHqlQuery.populateQuerySettings(args)
            hibernateHqlQuery.populateQueryWithNamedArguments(params)
            hibernateHqlQuery.list()
        }
    }

    @CompileDynamic // required for Hibernate 5.2 compatibility
    def <D> D findWithSql(CharSequence sql, Map args = Collections.emptyMap()) {
        IHibernateTemplate template = hibernateTemplate
        return (D) template.execute { Session session ->

            List params = []
            if(sql instanceof GString) {
                sql = buildOrdinalParameterQueryFromGString((GString)sql, params)
            }
            args = new HashMap(args)


            def hibernateHqlQuery = HibernateHqlQuery.createHqlQuery(session, datastore as HibernateDatastore, sessionFactory, persistentEntity, sql.toString(), true)
            template.applySettings(hibernateHqlQuery.getQuery())
            args.put(DynamicFinder.ARGUMENT_MAX, 1)
            hibernateHqlQuery.populateQuerySettings(args)
            hibernateHqlQuery.populateQueryWithIndexedArguments(params)
            def results = hibernateHqlQuery.list()
            if(results.isEmpty()) {
                return null
            }
            else {
                return results.get(0)
            }
        }
    }

    @CompileDynamic // required for Hibernate 5.2 compatibility
    List<D> findAllWithSql(CharSequence sql, Map args = Collections.emptyMap()) {
        IHibernateTemplate template = hibernateTemplate
        return (List<D>) template.execute { Session session ->
            List params = []
            if(sql instanceof GString) {
                sql = buildOrdinalParameterQueryFromGString((GString)sql, params)
            }

            def hibernateHqlQuery =HibernateHqlQuery.createHqlQuery(session, datastore as HibernateDatastore, sessionFactory, persistentEntity, sql.toString(), true)
            template.applySettings(hibernateHqlQuery.getQuery())
            hibernateHqlQuery.populateQuerySettings(args)
            hibernateHqlQuery.populateQueryWithIndexedArguments(params)

            def list = hibernateHqlQuery.list()
            return list
        }
    }

    @Override
    List<D> findAll(CharSequence query) {
        if(query instanceof GString) {
            Map params = [:]
            String hql = buildNamedParameterQueryFromGString((GString)query, params)
            return findAll(hql, params, Collections.emptyMap())
        }
        else {
            return super.findAll(query)
        }
    }

    @Override
    List executeQuery(CharSequence query) {
        if(query instanceof GString) {
            Map params = [:]
            String hql = buildNamedParameterQueryFromGString((GString)query, params)
            return executeQuery(hql, params, Collections.emptyMap())
        }
        else {
            return super.executeQuery(query)
        }
    }

    @Override
    Integer executeUpdate(CharSequence query) {
        if(query instanceof GString) {
            Map params = [:]
            String hql = buildNamedParameterQueryFromGString((GString)query, params)
            return executeUpdate(hql, params, Collections.emptyMap())
        }
        else {
            return super.executeUpdate(query)
        }
    }

    @Override
    D find(CharSequence query) {
        if(query instanceof GString) {
            Map params = [:]
            String hql = buildNamedParameterQueryFromGString((GString)query, params)
            return find(hql, params, Collections.emptyMap())
        }
        else {
            return (D)super.find(query)
        }
    }

    @Override
    D find(CharSequence query, Map params) {
        if(query instanceof GString) {
            Map newParams = new LinkedHashMap(params)
            String hql = buildNamedParameterQueryFromGString((GString)query, newParams)
            return find(hql, newParams, newParams)
        }
        else {
            return (D)super.find(query, params)
        }
    }

    @Override
    List<D> findAll(CharSequence query, Map params) {
        if(query instanceof GString) {
            Map newParams = new LinkedHashMap(params)
            String hql = buildNamedParameterQueryFromGString((GString)query, newParams)
            return findAll(hql, newParams, newParams)
        }
        else {
            return super.findAll(query, params)
        }
    }

    @Override
    List executeQuery(CharSequence query, Map args) {
        if(query instanceof GString) {
            Map newParams = new LinkedHashMap(args)
            String hql = buildNamedParameterQueryFromGString((GString)query, newParams)
            return executeQuery(hql, newParams, newParams)
        }
        else {
            return super.executeQuery(query, args)
        }
    }



    @Override
    D find(D exampleObject, Map args) {
        throw new UnsupportedOperationException("not yet")
    }

    @Override
    List<D> findAll(D exampleObject, Map args) {
        throw new UnsupportedOperationException("Example is not supported but maybe in the future")
    }

    @Override
    List<D> findAllWhere(Map queryMap, Map args) {
        if (!queryMap) return null
        (List<D>)hibernateTemplate.execute { Session session ->
            Map<String, Object> processedQueryMap = [:]
            queryMap.each{ key, value -> processedQueryMap[key.toString()] = value }
            Map<String,Object> queryArgs = filterQueryArgumentMap(processedQueryMap)
            List<String> nullNames = removeNullNames(queryArgs)

            CriteriaBuilder cb = session.getCriteriaBuilder()
            CriteriaQuery cq = cb.createQuery(persistentEntity.javaClass)
            def root = cq.from(persistentEntity.javaClass)
            def listOfPredicates = queryArgs.collect { entry -> cb.equal(root.get(entry.key), entry.value) }
            def nullPredicates = nullNames.collect { nullName -> cb.isNotNull(root.get(nullName)) }
            def jpaPredicates = (listOfPredicates + nullPredicates).<JpaPredicate>toArray(new JpaPredicate[0])
            cq.select(root).where(cb.and(jpaPredicates))
            firePreQueryEvent(session, cq)
            List results = session.createQuery(cq).resultList
            firePostQueryEvent(session, cq, results)
            return results
        }
    }

    @Override
    List executeQuery(CharSequence query, Map params, Map args) {
        def template = hibernateTemplate
        args = new HashMap(args)
        params = new HashMap(params)

        if(query instanceof GString) {
            query = buildNamedParameterQueryFromGString((GString) query, params)
        }

        return (List<D>) template.execute { Session session ->
            Query q = (Query) session.createQuery(query.toString(),Object.class)
            def hibernateHqlQuery = HibernateHqlQuery.createHqlQuery(session,q, datastore as HibernateDatastore,sessionFactory,persistentEntity)
            template.applySettings(hibernateHqlQuery.getQuery())
            hibernateHqlQuery.populateQuerySettings(params)
            hibernateHqlQuery.populateQuerySettings(args)
            hibernateHqlQuery.populateQueryWithNamedArguments(params)
            hibernateHqlQuery.list()
        }
    }



    private List<D> numberedParameterQuery(CharSequence query, Map args, params) {
        if (query instanceof GString) {
            throw new GrailsQueryException("Unsafe query [$query]. GORM cannot automatically escape a GString value when combined with ordinal parameters, so this query is potentially vulnerable to HQL injection attacks. Please embed the parameters within the GString so they can be safely escaped.");
        }
        def template = hibernateTemplate
        def queryString = query.toString()
        queryString = normalizeMultiLineQueryString(queryString)
        args = new HashMap(args)

        return (List<D>) template.execute { Session session ->
            def hqlQuery = HibernateHqlQuery.createHqlQuery(session, datastore as HibernateDatastore, sessionFactory, persistentEntity, queryString, false)
            template.applySettings(hqlQuery.getQuery())
            hqlQuery.populateQuerySettings(args)
            hqlQuery.populateQueryWithIndexedArguments(params as List)
            hqlQuery.list()
        }
    }

    @Override
    List executeQuery(CharSequence query, Collection params, Map args) {
        numberedParameterQuery(query, args, params)
    }

    @Override
    List<D> findAll(CharSequence query, Collection params, Map args) {
        numberedParameterQuery(query, args, params)
    }

    @Override
    D findWhere(Map queryMap, Map args) {
        if (!queryMap) return null
        (D)hibernateTemplate.execute { Session session ->
            Map<String, Object> processedQueryMap = [:]
            queryMap.each{ key, value -> processedQueryMap[key.toString()] = value }
            Map<String,Object> queryArgs = filterQueryArgumentMap(processedQueryMap)
            List<String> nullNames = removeNullNames(queryArgs)
            CriteriaBuilder cb = session.getCriteriaBuilder()
            CriteriaQuery cq = cb.createQuery(persistentEntity.javaClass)
            def root = cq.from(persistentEntity.javaClass)
            def listOfPredicates = queryArgs.collect { entry -> cb.equal(root.get(entry.key), entry.value) }
            def nullPredicates = nullNames.collect { nullName -> cb.isNotNull(root.get(nullName)) }
            JpaPredicate[] jpaPredicates = (listOfPredicates + nullPredicates).<JpaPredicate>toArray(new JpaPredicate[0])
            cq.select(root).where(cb.and(jpaPredicates))
            firePreQueryEvent(session, cq)
            Object result = session.createQuery(cq).singleResult
            firePostQueryEvent(session, cq, result)
            result
        }
    }

    List<D> getAll(List ids) {
        getAllInternal(ids)
    }

    List<D> getAll(Long... ids) {
        getAllInternal(ids as List)
    }

    @Override
    List<D> getAll(Serializable... ids) {
        getAllInternal(ids as List)
    }

    @CompileDynamic
    private List getAllInternal(List ids) {
        if (!ids) return []

        (List)hibernateTemplate.execute { Session session ->
            def identityType = persistentEntity.identity.type
            def identityName = persistentEntity.identity.name
            List<Object> convertedIds = ids.collect { HibernateRuntimeUtils.convertValueToType((Serializable)it, identityType, conversionService) }
            CriteriaBuilder cb = session.getCriteriaBuilder()
            CriteriaQuery cq = cb.createQuery(persistentEntity.javaClass)
            def root = cq.from(persistentEntity.javaClass)
            cq.select(root).where(root.get("id").in(convertedIds))
            firePreQueryEvent(session, cq)

            List results =  session.createQuery(cq).resultList
            firePostQueryEvent(session, cq, results)
            def idsMap = [:]
            for (object in results) {
                idsMap[object[identityName]] = object
            }
            results.clear()
            for (id in ids) {
                results << idsMap[id]
            }
            results
        }
    }

    protected Map<String,Object> filterQueryArgumentMap(Map<String,Object> query) {
        Map<String,Object> queryArgs = new HashMap<>()
        for (entry in query.entrySet()) {
            if (entry.value instanceof CharSequence) {
                queryArgs[entry.key] = entry.value.toString()
            }
            else {
                queryArgs[entry.key] = entry.value
            }
        }
        return queryArgs
    }

    protected String buildOrdinalParameterQueryFromGString(GString query, List params) {
        StringBuilder sqlString = new StringBuilder()
        int i = 0
        Object[] values = query.values
        def strings = query.getStrings()
        for (str in strings) {
            sqlString.append(str)
            if (i < values.length) {
                sqlString.append('?')
                params.add(values[i++])
            }
        }
        return sqlString.toString()
    }

    protected String buildNamedParameterQueryFromGString(GString query, Map params) {
        StringBuilder sqlString = new StringBuilder()
        int i = 0
        Object[] values = query.values
        def strings = query.getStrings()
        for (str in strings) {
            sqlString.append(str)
            if (i < values.length) {
                String parameterName = "p$i"
                sqlString.append(':').append(parameterName)
                params.put(parameterName, values[i++])
            }
        }
        return sqlString.toString()
    }

    protected List<String> removeNullNames(Map query) {
        List<String> nullNames = []
        Set<String> allNames = new HashSet<>(query.keySet() as Set<String>)
        for (String name in allNames) {
            if (query[name] == null) {
                query.remove name
                nullNames << name
            }
        }
        nullNames
    }

    protected Serializable convertIdentifier(Serializable id) {
        def identity = persistentEntity.identity
        if(identity != null) {
            ConversionService conversionService = persistentEntity.mappingContext.conversionService
            if(id != null) {
                Class identityType = identity.type
                Class idInstanceType = id.getClass()
                if(identityType.isAssignableFrom(idInstanceType)) {
                    return id
                }
                else if(conversionService.canConvert(idInstanceType, identityType)) {
                    try {
                        return (Serializable)conversionService.convert(id, identityType)
                    } catch (Throwable e) {
                        return null
                    }
                }
                else {
                    return null
                }
            }
        }
        return id
    }

    protected void populateQueryWithNamedArguments(Query q, Map queryNamedArgs) {
        if (queryNamedArgs) {
            for (Map.Entry entry in queryNamedArgs.entrySet()) {
                def key = entry.key
                if (!(key instanceof CharSequence)) {
                    throw new GrailsQueryException("Named parameter's name must be String: $queryNamedArgs")
                }
                String stringKey = key.toString()
                def value = entry.value

                if(value == null) {
                    q.setParameter stringKey, null
                } else if (value instanceof CharSequence) {
                    q.setParameter stringKey, value.toString()
                } else if (List.class.isAssignableFrom(value.getClass())) {
                    q.setParameterList stringKey, (List) value
                } else if (Set.class.isAssignableFrom(value.getClass())) {
                    q.setParameterList stringKey, (Set) value
                } else if (value.getClass().isArray()) {
                    q.setParameterList stringKey, (Object[]) value
                } else {
                    q.setParameter stringKey, value
                }
            }
        }
    }

    protected Integer intValue(Map args, String key) {
        def value = args.get(key)
        if(value) {
            return conversionService.convert(value, Integer.class)
        }
        return null
    }

    protected void populateQueryArguments(Query q, Map args) {
        Integer max = intValue(args, DynamicFinder.ARGUMENT_MAX)
        args.remove(DynamicFinder.ARGUMENT_MAX)
        Integer offset = intValue(args, DynamicFinder.ARGUMENT_OFFSET)
        args.remove(DynamicFinder.ARGUMENT_OFFSET)

        if (max != null) {
            q.maxResults = max
        }
        if (offset != null) {
            q.firstResult = offset
        }

        if (args.containsKey(DynamicFinder.ARGUMENT_CACHE)) {
            q.cacheable = ClassUtils.getBooleanFromMap(DynamicFinder.ARGUMENT_CACHE, args)
        }
        if (args.containsKey(DynamicFinder.ARGUMENT_FETCH_SIZE)) {
            Integer fetchSizeParam = conversionService.convert(args.remove(DynamicFinder.ARGUMENT_FETCH_SIZE), Integer.class);
            q.setFetchSize(fetchSizeParam.intValue());
        }
        if (args.containsKey(DynamicFinder.ARGUMENT_TIMEOUT)) {
            Integer timeoutParam = conversionService.convert(args.remove(DynamicFinder.ARGUMENT_TIMEOUT), Integer.class);
            q.setTimeout(timeoutParam.intValue());
        }
        if (args.containsKey(DynamicFinder.ARGUMENT_READ_ONLY)) {
            q.setReadOnly((Boolean)args.remove(DynamicFinder.ARGUMENT_READ_ONLY));
        }
        if (args.containsKey(DynamicFinder.ARGUMENT_FLUSH_MODE)) {
            q.setHibernateFlushMode((FlushMode)args.remove(DynamicFinder.ARGUMENT_FLUSH_MODE));
        }

        args.remove(DynamicFinder.ARGUMENT_CACHE)
    }

    private String normalizeMultiLineQueryString(String query) {
        if (query.indexOf('\n') != -1)
           return query.trim().replace('\n', ' ')
        return query
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
                criteriaQuery = criteriaBuilder.createQuery(Object.class)
                queryRoot = criteriaQuery.from(persistentEntity.javaClass)
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
        def builder = new HibernateCriteriaBuilder(persistentClass, sessionFactory,(AbstractHibernateDatastore)datastore)
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

    protected void firePostQueryEvent(Session session, CriteriaQuery criteria, Object result) {
        def hibernateQuery = new HibernateQuery(new HibernateSession((HibernateDatastore) datastore, sessionFactory), persistentEntity)
        def list = result instanceof List ? (List)result :  Collections.singletonList(result)
        datastore.applicationEventPublisher.publishEvent( new PostQueryEvent(datastore, hibernateQuery, list))
    }

    protected void firePreQueryEvent(Session session, CriteriaQuery criteria) {
        def hibernateSession = new HibernateSession((HibernateDatastore) datastore, sessionFactory)
        def hibernateQuery = new HibernateQuery(hibernateSession, persistentEntity)
        datastore.applicationEventPublisher.publishEvent(new PreQueryEvent(datastore, hibernateQuery))
    }



}
