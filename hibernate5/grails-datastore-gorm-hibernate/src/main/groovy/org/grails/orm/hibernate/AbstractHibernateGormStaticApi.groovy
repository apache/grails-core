package org.grails.orm.hibernate

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.proxy.ProxyHandler
import org.grails.datastore.mapping.reflect.ClassUtils
import org.grails.orm.hibernate.cfg.AbstractGrailsDomainBinder
import org.grails.orm.hibernate.cfg.CompositeIdentity
import org.grails.orm.hibernate.exceptions.GrailsQueryException

import org.grails.orm.hibernate.query.GrailsHibernateQueryUtils
import org.grails.orm.hibernate.query.HibernateHqlQuery
import org.grails.orm.hibernate.support.HibernateRuntimeUtils
import org.grails.datastore.gorm.GormStaticApi
import org.grails.datastore.gorm.finders.DynamicFinder
import org.grails.datastore.gorm.finders.FinderMethod
import org.hibernate.Criteria
import org.hibernate.FlushMode
import org.hibernate.Session
import org.hibernate.criterion.Example
import org.hibernate.criterion.Restrictions
import org.hibernate.jpa.QueryHints
import org.hibernate.query.NativeQuery
import org.hibernate.query.Query
import org.hibernate.transform.DistinctRootEntityResultTransformer
import org.springframework.core.convert.ConversionService
import org.springframework.transaction.PlatformTransactionManager

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Root

/**
 * Abstract implementation of the Hibernate static API for GORM, providing String-based method implementations
 *
 * @author Graeme Rocher
 * @since 4.0
 */
@CompileStatic
abstract class AbstractHibernateGormStaticApi<D> extends GormStaticApi<D> {

    protected ProxyHandler proxyHandler
    protected GrailsHibernateTemplate hibernateTemplate
    protected ConversionService conversionService
    protected final HibernateSession hibernateSession

    AbstractHibernateGormStaticApi(
            Class<D> persistentClass,
            HibernateDatastore datastore,
            List<FinderMethod> finders) {
        this(persistentClass, datastore, finders, null)
    }

    AbstractHibernateGormStaticApi(
            Class<D> persistentClass,
            HibernateDatastore  datastore,
            List<FinderMethod> finders,
            PlatformTransactionManager transactionManager) {
        super(persistentClass, datastore, finders, transactionManager)
        this.hibernateTemplate = new GrailsHibernateTemplate(datastore.getSessionFactory(), datastore)
        this.conversionService = datastore.mappingContext.conversionService
        this.proxyHandler = datastore.mappingContext.proxyHandler
        this.hibernateSession = new HibernateSession(
                (HibernateDatastore)datastore,
                hibernateTemplate.getSessionFactory(),
                hibernateTemplate.getFlushMode()
        )
    }

    IHibernateTemplate getHibernateTemplate() {
        return hibernateTemplate
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


    @Override
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
                CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder()
                CriteriaQuery criteriaQuery = criteriaBuilder.createQuery(persistentEntity.javaClass)
                Root queryRoot = criteriaQuery.from(persistentEntity.javaClass)
                criteriaQuery = criteriaQuery.where (
                        //TODO: Remove explicit type cast once GROOVY-9460
                        criteriaBuilder.equal((Expression<?>) queryRoot.get(persistentEntity.identity.name), id)
                )
                Query criteria = session.createQuery(criteriaQuery)
                HibernateHqlQuery hibernateHqlQuery = new HibernateHqlQuery(
                        hibernateSession, persistentEntity, criteria)
                return proxyHandler.unwrap( hibernateHqlQuery.singleResult() )
            } )
        }
        else {
            // for non multi-tenant entities we process get(..) via the second level cache
            return (D)proxyHandler.unwrap(
                hibernateTemplate.get(persistentEntity.javaClass, id)
            )
        }

    }

    @Override
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

    @Override
    Integer count() {
        (Integer)hibernateTemplate.execute({ Session session ->
            CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder()
            CriteriaQuery criteriaQuery = criteriaBuilder.createQuery(persistentEntity.javaClass)
            criteriaQuery.select(criteriaBuilder.count(criteriaQuery.from(persistentEntity.javaClass)))
            Query criteria = session.createQuery(criteriaQuery)
            HibernateHqlQuery hibernateHqlQuery = new HibernateHqlQuery(
                    hibernateSession, persistentEntity, criteria) {
                @Override
                protected void flushBeforeQuery() {
                    // no-op
                }
            }
            hibernateTemplate.applySettings(criteria)
            def result = hibernateHqlQuery.singleResult()
            Number num = result == null ? 0 : (Number)result
            return num
        })
    }

    /**
     * Fire a post query event
     *
     * @param session The session
     * @param criteria The criteria
     * @param result The result
     */
    protected abstract void firePostQueryEvent(Session session, Criteria criteria, Object result)
    /**
     * Fire a pre query event
     *
     * @param session The session
     * @param criteria The criteria
     * @return True if the query should be cancelled
     */
    protected abstract void firePreQueryEvent(Session session, Criteria criteria)

    @Override
    boolean exists(Serializable id) {
        id = convertIdentifier(id)
        hibernateTemplate.execute  { Session session ->
            CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder()
            CriteriaQuery criteriaQuery = criteriaBuilder.createQuery(persistentEntity.javaClass)
            Root queryRoot = criteriaQuery.from(persistentEntity.javaClass)
            def idProp = queryRoot.get(persistentEntity.identity.name)
            criteriaQuery = criteriaQuery.where (
                    //TODO: Remove explicit type cast once GROOVY-9460
                    criteriaBuilder.equal((Expression<?>) idProp, id)
            )
            criteriaQuery.select(criteriaBuilder.count(criteriaQuery.from(persistentEntity.javaClass)))
            Query criteria = session.createQuery(criteriaQuery)
            HibernateHqlQuery hibernateHqlQuery = new HibernateHqlQuery(
                    hibernateSession, persistentEntity, criteria)

            hibernateTemplate.applySettings(criteria)
            Boolean result = hibernateHqlQuery.singleResult()
            return result
        }
    }

    D first(Map m) {
        def entityMapping = AbstractGrailsDomainBinder.getMapping(persistentEntity.javaClass)
        if (entityMapping?.identity instanceof CompositeIdentity) {
            throw new UnsupportedOperationException('The first() method is not supported for domain classes that have composite keys.')
        }
        super.first(m)
    }

    D last(Map m) {
        def entityMapping = AbstractGrailsDomainBinder.getMapping(persistentEntity.javaClass)
        if (entityMapping?.identity instanceof CompositeIdentity) {
            throw new UnsupportedOperationException('The last() method is not supported for domain classes that have composite keys.')
        }
        super.last(m)
    }

    /**
     * Implements the 'find(String' method to use HQL queries with named arguments
     *
     * @param query The query
     * @param queryNamedArgs The named arguments
     * @param args Any additional query arguments
     * @return A result or null if no result found
     */
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
            Query q = (Query) session.createQuery(queryString)
            template.applySettings(q)

            populateQueryArguments(q, queryNamedArgs)
            populateQueryArguments(q, args)
            populateQueryWithNamedArguments(q, queryNamedArgs)
            proxyHandler.unwrap( createHqlQuery(session, q).singleResult() )
        }
    }

    protected abstract HibernateHqlQuery createHqlQuery(Session session, Query q)

    @Override
    D find(CharSequence query, Collection params, Map args) {
        if(query instanceof GString) {
            throw new GrailsQueryException("Unsafe query [$query]. GORM cannot automatically escape a GString value when combined with ordinal parameters, so this query is potentially vulnerable to HQL injection attacks. Please embed the parameters within the GString so they can be safely escaped.");
        }

        String queryString = query.toString()
        queryString = normalizeMultiLineQueryString(queryString)

        args = new HashMap(args)
        def template = hibernateTemplate
        return (D) template.execute { Session session ->
            Query q = (Query) session.createQuery(queryString)
            template.applySettings(q)

            params.eachWithIndex { val, int i ->
                if (val instanceof CharSequence) {
                    q.setParameter i, val.toString()
                }
                else {
                    q.setParameter i, val
                }
            }
            populateQueryArguments(q, args)
            proxyHandler.unwrap( createHqlQuery(session, q).singleResult() )
        }
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
            Query q = (Query) session.createQuery(queryString)
            template.applySettings(q)

            populateQueryArguments(q, params)
            populateQueryArguments(q, args)
            populateQueryWithNamedArguments(q, params)

            createHqlQuery(session, q).list()
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

            NativeQuery q = (NativeQuery)session.createNativeQuery(sql.toString())

            template.applySettings(q)

            params.eachWithIndex { val, int i ->
                i++
                if (val instanceof CharSequence) {
                    q.setParameter i, val.toString()
                }
                else {
                    q.setParameter i, val
                }
            }
            q.addEntity(persistentClass)
            populateQueryArguments(q, args)
            q.setMaxResults(1)
            def results = createHqlQuery(session, q).list()
            if(results.isEmpty()) {
                return null
            }
            else {
                return results.get(0)
            }
        }
    }

    /**
     * Finds all results for this entity for the given SQL query
     *
     * @param sql The SQL query
     * @param args The arguments
     * @return All entities matching the SQL query
     */
    @CompileDynamic // required for Hibernate 5.2 compatibility
    List<D> findAllWithSql(CharSequence sql, Map args = Collections.emptyMap()) {
        IHibernateTemplate template = hibernateTemplate
        return (List<D>) template.execute { Session session ->

            List params = []
            if(sql instanceof GString) {
                sql = buildOrdinalParameterQueryFromGString((GString)sql, params)
            }

            NativeQuery q = (NativeQuery)session.createNativeQuery(sql.toString())

            template.applySettings(q)

            params.eachWithIndex { val, int i ->
                i++
                if (val instanceof CharSequence) {
                    q.setParameter i, val.toString()
                }
                else {
                    q.setParameter i, val
                }
            }
            q.addEntity(persistentClass)
            populateQueryArguments(q, args)
            return createHqlQuery(session, q).list()
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
    Integer executeUpdate(CharSequence query, Map args) {
        if(query instanceof GString) {
            Map newParams = new LinkedHashMap(args)
            String hql = buildNamedParameterQueryFromGString((GString)query, newParams)
            return executeUpdate(hql, newParams, newParams)
        }
        else {
            return super.executeUpdate(query, args)
        }
    }

    @Override
    List<D> findAll(CharSequence query, Collection params, Map args) {
        if(query instanceof GString) {
            throw new GrailsQueryException("Unsafe query [$query]. GORM cannot automatically escape a GString value when combined with ordinal parameters, so this query is potentially vulnerable to HQL injection attacks. Please embed the parameters within the GString so they can be safely escaped.")
        }

        String queryString = query.toString()
        queryString = normalizeMultiLineQueryString(queryString)

        args = new HashMap(args)

        def template = hibernateTemplate
        return (List<D>) template.execute { Session session ->
            Query q = (Query) session.createQuery(queryString)
            template.applySettings(q)

            params.eachWithIndex { val, int i ->
                if (val instanceof CharSequence) {
                    q.setParameter i, val.toString()
                }
                else {
                    q.setParameter i, val
                }
            }
            populateQueryArguments(q, args)
            createHqlQuery(session, q).list()
        }
    }

    @Override
    D find(D exampleObject, Map args) {
        def template = hibernateTemplate
        return (D) template.execute { Session session ->
            Example example = Example.create(exampleObject).ignoreCase()

            Criteria crit = session.createCriteria(persistentEntity.javaClass);
            hibernateTemplate.applySettings(crit)
            crit.add example
            GrailsHibernateQueryUtils.populateArgumentsForCriteria(persistentEntity, crit, args, datastore.mappingContext.conversionService, true)
            crit.maxResults = 1
            firePreQueryEvent(session, crit)
            List results = crit.list()
            firePostQueryEvent(session, crit, results)
            if (results) {
                return proxyHandler.unwrap( results.get(0) )
            }
        }
    }

    @Override
    List<D> findAll(D exampleObject, Map args) {
        def template = hibernateTemplate
        return (List<D>) template.execute { Session session ->
            Example example = Example.create(exampleObject).ignoreCase()

            Criteria crit = session.createCriteria(persistentEntity.javaClass);
            hibernateTemplate.applySettings(crit)
            crit.add example
            GrailsHibernateQueryUtils.populateArgumentsForCriteria(persistentEntity, crit, args, datastore.mappingContext.conversionService, true)
            firePreQueryEvent(session, crit)
            List results = crit.list()
            firePostQueryEvent(session, crit, results)
            return results
        }
    }

    @Override
    List<D> findAllWhere(Map queryMap, Map args) {
        if (!queryMap) return null
        (List<D>)hibernateTemplate.execute { Session session ->
            Map<String, Object> processedQueryMap = [:]
            queryMap.each{ key, value -> processedQueryMap[key.toString()] = value }
            Map queryArgs = filterQueryArgumentMap(processedQueryMap)
            List<String> nullNames = removeNullNames(queryArgs)
            Criteria criteria = session.createCriteria(persistentClass)
            hibernateTemplate.applySettings(criteria)
            criteria.add(Restrictions.allEq(queryArgs))
            for (name in nullNames) {
                criteria.add Restrictions.isNull(name)
            }
            criteria.setResultTransformer(DistinctRootEntityResultTransformer.INSTANCE)

            GrailsHibernateQueryUtils.populateArgumentsForCriteria(persistentEntity, criteria, args, datastore.mappingContext.conversionService, true)
            firePreQueryEvent(session, criteria)
            List results = criteria.list()
            firePostQueryEvent(session, criteria, results)
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
            Query q = (Query) session.createQuery(query.toString())
            template.applySettings(q)

            populateQueryArguments(q, params)
            populateQueryArguments(q, args)
            populateQueryWithNamedArguments(q, params)

            createHqlQuery(session, q).list()
        }
    }

    @Override
    List executeQuery(CharSequence query, Collection params, Map args) {
        if(query instanceof GString) {
            throw new GrailsQueryException("Unsafe query [$query]. GORM cannot automatically escape a GString value when combined with ordinal parameters, so this query is potentially vulnerable to HQL injection attacks. Please embed the parameters within the GString so they can be safely escaped.");
        }

        def template = hibernateTemplate
        args = new HashMap(args)

        return (List<D>) template.execute { Session session ->
            Query q = (Query) session.createQuery(query.toString())
            template.applySettings(q)

            params.eachWithIndex { val, int i ->
                if (val instanceof CharSequence) {
                    q.setParameter i, val.toString()
                }
                else {
                    q.setParameter i, val
                }
            }
            populateQueryArguments(q, args)
            createHqlQuery(session, q).list()
        }
    }

    @Override
    D findWhere(Map queryMap, Map args) {
        if (!queryMap) return null
        (D)hibernateTemplate.execute { Session session ->
            Map<String, Object> processedQueryMap = [:]
            queryMap.each{ key, value -> processedQueryMap[key.toString()] = value }
            Map queryArgs = filterQueryArgumentMap(processedQueryMap)
            List<String> nullNames = removeNullNames(queryArgs)
            Criteria criteria = session.createCriteria(persistentClass)
            hibernateTemplate.applySettings(criteria)
            criteria.add(Restrictions.allEq(queryArgs))
            for (name in nullNames) {
                criteria.add Restrictions.isNull(name)
            }
            criteria.setMaxResults(1)
            GrailsHibernateQueryUtils.populateArgumentsForCriteria(persistentEntity, criteria, args, datastore.mappingContext.conversionService, true)
            firePreQueryEvent(session, criteria)
            Object result = criteria.uniqueResult()
            firePostQueryEvent(session, criteria, result)
            return proxyHandler.unwrap(result)
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
            ids = ids.collect { HibernateRuntimeUtils.convertValueToType((Serializable)it, identityType, conversionService) }
            def criteria = session.createCriteria(persistentClass)
            hibernateTemplate.applySettings(criteria)
            def identityName = persistentEntity.identity.name
            criteria.add(Restrictions.'in'(identityName, ids))
            firePreQueryEvent(session, criteria)
            List results = criteria.list()
            firePostQueryEvent(session, criteria, results)
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

    protected Map filterQueryArgumentMap(Map query) {
        def queryArgs = [:]
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

    /**
     * Processes a query converting GString expressions into parameters
     *
     * @param query The query
     * @param params The parameters
     * @return The final String
     */
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

    /**
     * Processes a query converting GString expressions into parameters
     *
     * @param query The query
     * @param params The parameters
     * @return The final String
     */
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
                        // unconvertable id, return null
                        return null
                    }
                }
                else {
                    // unconvertable id, return null
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

        //
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

}
