/*
 * Copyright 2002-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.orm.hibernate.support.hibernate7

import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy

import groovy.transform.CompileStatic
import jakarta.persistence.PersistenceException
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.hibernate.Filter
import org.hibernate.FlushMode
import org.hibernate.Hibernate
import org.hibernate.HibernateException
import org.hibernate.LockMode
import org.hibernate.LockOptions
import org.hibernate.ReplicationMode
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.query.Query
import org.springframework.beans.factory.InitializingBean
import org.springframework.dao.DataAccessException
import org.springframework.dao.InvalidDataAccessApiUsageException
import org.springframework.transaction.support.ResourceHolderSupport
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.util.Assert

/**
 * Helper class that simplifies Hibernate data access code. Automatically
 * converts HibernateExceptions into DataAccessExceptions, following the
 * {@code org.springframework.dao} exception hierarchy.
 *
 * <p>The central method is {@code execute}, supporting Hibernate access code
 * implementing the {@link HibernateCallback} interface. It provides Hibernate Session
 * handling such that neither the HibernateCallback implementation nor the calling
 * code needs to explicitly care about retrieving/closing Hibernate Sessions,
 * or handling Session lifecycle exceptions. For typical single step actions,
 * there are various convenience methods (find, load, saveOrUpdate, delete).
 *
 * <p>Can be used within a service implementation via direct instantiation
 * with a SessionFactory reference, or get prepared in an application context
 * and given to services as bean reference. Note: The SessionFactory should
 * always be configured as bean in the application context, in the first case
 * given to the service directly, in the second case to the prepared template.
 *
 * <p><b>NOTE: Hibernate access code can also be coded against the native Hibernate
 * {@link Session}. Hence, for newly started projects, consider adopting the standard
 * Hibernate style of coding against {@link SessionFactory#getCurrentSession()}.
 * Alternatively, use {@link #execute(HibernateCallback)} with Java 8 lambda code blocks
 * against the callback-provided {@code Session} which results in elegant code as well,
 * decoupled from the Hibernate Session lifecycle. The remaining operations on this
 * HibernateTemplate are deprecated in the meantime and primarily exist as a migration
 * helper for older Hibernate 3.x/4.x data access code in existing applications.</b>
 *
 * @author Juergen Hoeller
 * @since 4.2
 * @see #setSessionFactory
 * @see HibernateCallback
 * @see Session
 * @see LocalSessionFactoryBean
 * @see HibernateTransactionManager
 * @see org.springframework.orm.hibernate7.support.OpenSessionInViewFilter
 * @see org.springframework.orm.hibernate7.support.OpenSessionInViewInterceptor
 */
@CompileStatic
class HibernateTemplate implements HibernateOperations, InitializingBean {

    protected final Log logger = LogFactory.getLog(getClass())

    private SessionFactory sessionFactory

    private String[] filterNames

    private boolean exposeNativeSession = false

    private boolean checkWriteOperations = true

    private boolean cacheQueries = false

    private String queryCacheRegion

    private int fetchSize = 0

    private int maxResults = 0

    /**
     * Create a new HibernateTemplate instance.
     */
    HibernateTemplate() {
    }

    /**
     * Create a new HibernateTemplate instance.
     * @param sessionFactory the SessionFactory to create Sessions with
     */
    HibernateTemplate(SessionFactory sessionFactory) {
        setSessionFactory(sessionFactory)
        afterPropertiesSet()
    }

    /**
     * Set the Hibernate SessionFactory that should be used to create
     * Hibernate Sessions.
     */
    void setSessionFactory(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory
    }

    /**
     * Return the Hibernate SessionFactory that should be used to create
     * Hibernate Sessions.
     */
    SessionFactory getSessionFactory() {
        return this.sessionFactory
    }

    /**
     * Obtain the SessionFactory for actual use.
     * @return the SessionFactory (never {@code null})
     * @throws IllegalStateException in case of no SessionFactory set
     * @since 5.0
     */
    protected final SessionFactory obtainSessionFactory() {
        SessionFactory sessionFactory = getSessionFactory()
        Assert.state(sessionFactory != null, 'No SessionFactory set')
        return sessionFactory
    }

    /**
     * Set one or more names of Hibernate filters to be activated for all
     * Sessions that this accessor works with.
     * <p>Each of those filters will be enabled at the beginning of each
     * operation and correspondingly disabled at the end of the operation.
     * This will work for newly opened Sessions as well as for existing
     * Sessions (for example, within a transaction).
     * @see #enableFilters(Session)
     * @see Session#enableFilter(String)
     */
    void setFilterNames(String... filterNames) {
        this.filterNames = filterNames
    }

    /**
     * Return the names of Hibernate filters to be activated, if any.
     */
    String[] getFilterNames() {
        return this.filterNames
    }

    /**
     * Set whether to expose the native Hibernate Session to
     * HibernateCallback code.
     * <p>Default is "false": a Session proxy will be returned, suppressing
     * {@code close} calls and automatically applying query cache
     * settings and transaction timeouts.
     * @see HibernateCallback
     * @see Session
     * @see #setCacheQueries
     * @see #setQueryCacheRegion
     * @see #prepareQuery
     * @see #prepareCriteria
     */
    void setExposeNativeSession(boolean exposeNativeSession) {
        this.exposeNativeSession = exposeNativeSession
    }

    /**
     * Return whether to expose the native Hibernate Session to
     * HibernateCallback code, or rather a Session proxy.
     */
    boolean isExposeNativeSession() {
        return this.exposeNativeSession
    }

    /**
     * Set whether to check that the Hibernate Session is not in read-only mode
     * in case of write operations (save/update/delete).
     * <p>Default is "true", for fail-fast behavior when attempting write operations
     * within a read-only transaction. Turn this off to allow save/update/delete
     * on a Session with flush mode MANUAL.
     * @see #checkWriteOperationAllowed
     * @see org.springframework.transaction.TransactionDefinition#isReadOnly
     */
    void setCheckWriteOperations(boolean checkWriteOperations) {
        this.checkWriteOperations = checkWriteOperations
    }

    /**
     * Return whether to check that the Hibernate Session is not in read-only
     * mode in case of write operations (save/update/delete).
     */
    boolean isCheckWriteOperations() {
        return this.checkWriteOperations
    }

    /**
     * Set whether to cache all queries executed by this template.
     * <p>If this is "true", all Query and Criteria objects created by
     * this template will be marked as cacheable (including all
     * queries through find methods).
     * <p>To specify the query region to be used for queries cached
     * by this template, set the "queryCacheRegion" property.
     * @see #setQueryCacheRegion
     * @see Query#setCacheable
     * @see Criteria#setCacheable
     */
    void setCacheQueries(boolean cacheQueries) {
        this.cacheQueries = cacheQueries
    }

    /**
     * Return whether to cache all queries executed by this template.
     */
    boolean isCacheQueries() {
        return this.cacheQueries
    }

    /**
     * Set the name of the cache region for queries executed by this template.
     * <p>If this is specified, it will be applied to all Query and Criteria objects
     * created by this template (including all queries through find methods).
     * <p>The cache region will not take effect unless queries created by this
     * template are configured to be cached via the "cacheQueries" property.
     * @see #setCacheQueries
     * @see Query#setCacheRegion
     * @see Criteria#setCacheRegion
     */
    void setQueryCacheRegion(String queryCacheRegion) {
        this.queryCacheRegion = queryCacheRegion
    }

    /**
     * Return the name of the cache region for queries executed by this template.
     */
    String getQueryCacheRegion() {
        return this.queryCacheRegion
    }

    /**
     * Set the fetch size for this HibernateTemplate. This is important for processing
     * large result sets: Setting this higher than the default value will increase
     * processing speed at the cost of memory consumption; setting this lower can
     * avoid transferring row data that will never be read by the application.
     * <p>Default is 0, indicating to use the JDBC driver's default.
     */
    void setFetchSize(int fetchSize) {
        this.fetchSize = fetchSize
    }

    /**
     * Return the fetch size specified for this HibernateTemplate.
     */
    int getFetchSize() {
        return this.fetchSize
    }

    /**
     * Set the maximum number of rows for this HibernateTemplate. This is important
     * for processing subsets of large result sets, avoiding to read and hold
     * the entire result set in the database or in the JDBC driver if we're
     * never interested in the entire result in the first place (for example,
     * when performing searches that might return a large number of matches).
     * <p>Default is 0, indicating to use the JDBC driver's default.
     */
    void setMaxResults(int maxResults) {
        this.maxResults = maxResults
    }

    /**
     * Return the maximum number of rows specified for this HibernateTemplate.
     */
    int getMaxResults() {
        return this.maxResults
    }

    @Override
    void afterPropertiesSet() {
        if (getSessionFactory() == null) {
            throw new IllegalArgumentException("Property 'sessionFactory' is required")
        }
    }

    @Override
    def <T> T execute(HibernateCallback<T> action) throws DataAccessException {
        return doExecute(action, false)
    }

    /**
     * Execute the action specified by the given action object within a
     * native {@link Session}.
     * <p>This execute variant overrides the template-wide
     * {@link #isExposeNativeSession() "exposeNativeSession"} setting.
     * @param action callback object that specifies the Hibernate action
     * @return a result object returned by the action, or {@code null}
     * @throws DataAccessException in case of Hibernate errors
     */
    def <T> T executeWithNativeSession(HibernateCallback<T> action) {
        return doExecute(action, true)
    }

    /**
     * Execute the action specified by the given action object within a Session.
     * @param action callback object that specifies the Hibernate action
     * @param enforceNativeSession whether to enforce exposure of the native
     * Hibernate Session to callback code
     * @return a result object returned by the action, or {@code null}
     * @throws DataAccessException in case of Hibernate errors
     */
    protected <T> T doExecute(HibernateCallback<T> action, boolean enforceNativeSession) throws DataAccessException {
        Assert.notNull(action, 'Callback object must not be null')

        Session session = null
        boolean isNew = false
        try {
            session = obtainSessionFactory().getCurrentSession()
        } catch (HibernateException ex) {
            logger.debug('Could not retrieve pre-bound Hibernate session', ex)
        }
        if (session == null) {
            session = obtainSessionFactory().openSession()
            session.setHibernateFlushMode(FlushMode.MANUAL)
            isNew = true
        }

        try {
            enableFilters(session)
            Session sessionToExpose =
                    (enforceNativeSession || isExposeNativeSession() ? session : createSessionProxy(session))
            return action.doInHibernate(sessionToExpose)
        } catch (HibernateException ex) {
            throw SessionFactoryUtils.convertHibernateAccessException(ex)
        } catch (PersistenceException ex) {
            if (ex.getCause() instanceof HibernateException) {
                throw SessionFactoryUtils.convertHibernateAccessException((HibernateException) ex.getCause())
            }
            throw ex
        } catch (RuntimeException ex) {
            // Callback code threw application exception...
            throw ex
        } finally {
            if (isNew) {
                SessionFactoryUtils.closeSession(session)
            } else {
                disableFilters(session)
            }
        }
    }

    /**
     * Create a close-suppressing proxy for the given Hibernate Session.
     * The proxy also prepares returned Query and Criteria objects.
     * @param session the Hibernate Session to create a proxy for
     * @return the Session proxy
     * @see Session#close()
     * @see #prepareQuery
     * @see #prepareCriteria
     */
    protected Session createSessionProxy(Session session) {
        return (Session) Proxy.newProxyInstance(
                session.getClass().getClassLoader(), [Session] as Class<?>[],
                new CloseSuppressingInvocationHandler(session))
    }

    /**
     * Enable the specified filters on the given Session.
     * @param session the current Hibernate Session
     * @see #setFilterNames
     * @see Session#enableFilter(String)
     */
    protected void enableFilters(Session session) {
        String[] filterNames = getFilterNames()
        if (filterNames != null) {
            for (String filterName : filterNames) {
                session.enableFilter(filterName)
            }
        }
    }

    /**
     * Disable the specified filters on the given Session.
     * @param session the current Hibernate Session
     * @see #setFilterNames
     * @see Session#disableFilter(String)
     */
    protected void disableFilters(Session session) {
        String[] filterNames = getFilterNames()
        if (filterNames != null) {
            for (String filterName : filterNames) {
                session.disableFilter(filterName)
            }
        }
    }

    //-------------------------------------------------------------------------
    // Convenience methods for loading individual objects
    //-------------------------------------------------------------------------

    @Override
    def <T> T get(Class<T> entityClass, Serializable id) throws DataAccessException {
        return get(entityClass, id, null)
    }

    @Override
    def <T> T get(Class<T> entityClass, Serializable id, LockMode lockMode) throws DataAccessException {
        return executeWithNativeSession({ Session session ->
            if (lockMode != null) {
                return session.get(entityClass, id, new LockOptions(lockMode))
            } else {
                return session.get(entityClass, id)
            }
        } as HibernateCallback<T>)
    }

    @Override
    Object get(String entityName, Serializable id) throws DataAccessException {
        return get(entityName, id, null)
    }

    @Override
    Object get(String entityName, Serializable id, LockMode lockMode) throws DataAccessException {
        return executeWithNativeSession({ Session session ->
            if (lockMode != null) {
                return session.get(entityName, id, new LockOptions(lockMode))
            } else {
                return session.get(entityName, id)
            }
        } as HibernateCallback<Object>)
    }

    @Override
    def <T> T load(Class<T> entityClass, Serializable id) throws DataAccessException {
        return load(entityClass, id, null)
    }

    @Override
    def <T> T load(Class<T> entityClass, Serializable id, LockMode lockMode)
            throws DataAccessException {

        return nonNull(executeWithNativeSession({ Session session ->
            if (lockMode != null) {
                return session.get(entityClass, id, new LockOptions(lockMode))
            } else {
                return session.getReference(entityClass, id)
            }
        } as HibernateCallback<T>))
    }

    @Override
    Object load(String entityName, Serializable id) throws DataAccessException {
        return load(entityName, id, null)
    }

    @Override
    Object load(String entityName, Serializable id, LockMode lockMode) throws DataAccessException {
        return nonNull(executeWithNativeSession({ Session session ->
            if (lockMode != null) {
                return session.get(entityName, id, new LockOptions(lockMode))
            } else {
                return session.getReference(entityName, id)
            }
        } as HibernateCallback<Object>))
    }

    @Override
    void load(Object entity, Serializable id) throws DataAccessException {
        executeWithNativeSession({ Session session ->
            session.getIdentifier(entity) // Check if session knows about it?
            // Actually, load(entity, id) was used to refresh an existing object from the DB.
            // In Hibernate 7, you'd use get or find.
            return null
        } as HibernateCallback<Object>)
    }

    @Override
    void refresh(Object entity) throws DataAccessException {
        refresh(entity, null)
    }

    @Override
    void refresh(Object entity, LockMode lockMode) throws DataAccessException {
        executeWithNativeSession({ Session session ->
            if (lockMode != null) {
                session.refresh(entity, new LockOptions(lockMode))
            } else {
                session.refresh(entity)
            }
            return null
        } as HibernateCallback<Object>)
    }

    @Override
    boolean contains(Object entity) throws DataAccessException {
        Boolean result = executeWithNativeSession({ Session session -> session.contains(entity) } as HibernateCallback<Boolean>)
        Assert.state(result != null, 'No contains result')
        return result
    }

    @Override
    void evict(Object entity) throws DataAccessException {
        executeWithNativeSession({ Session session ->
            session.evict(entity)
            return null
        } as HibernateCallback<Object>)
    }

    @Override
    void initialize(Object proxy) throws DataAccessException {
        try {
            Hibernate.initialize(proxy)
        } catch (HibernateException ex) {
            throw SessionFactoryUtils.convertHibernateAccessException(ex)
        }
    }

    @Override
    Filter enableFilter(String filterName) throws IllegalStateException {
        Session session = obtainSessionFactory().getCurrentSession()
        Filter filter = session.getEnabledFilter(filterName)
        if (filter == null) {
            filter = session.enableFilter(filterName)
        }
        return filter
    }

    //-------------------------------------------------------------------------
    // Convenience methods for storing individual objects
    //-------------------------------------------------------------------------

    @Override
    void lock(Object entity, LockMode lockMode) throws DataAccessException {
        executeWithNativeSession({ Session session ->
            session.lock(entity, lockMode)
            return null
        } as HibernateCallback<Object>)
    }

    @Override
    void lock(String entityName, Object entity, LockMode lockMode)
            throws DataAccessException {

        executeWithNativeSession({ Session session ->
            session.lock(entity, lockMode)
            return null
        } as HibernateCallback<Object>)
    }

    @Override
    Serializable save(Object entity) throws DataAccessException {
        return nonNull(executeWithNativeSession({ Session session ->
            checkWriteOperationAllowed(session)
            SessionImplementor sessionImpl = (SessionImplementor) session
            Boolean isUnsaved = sessionImpl.getEntityPersister(null, entity).isTransient(entity, sessionImpl)
            if (Boolean.TRUE.equals(isUnsaved)) {
                session.persist(entity)
            } else {
                session.merge(entity)
            }
            return (Serializable) session.getIdentifier(entity)
        } as HibernateCallback<Serializable>))
    }

    @Override
    Serializable save(String entityName, Object entity) throws DataAccessException {
        return nonNull(executeWithNativeSession({ Session session ->
            checkWriteOperationAllowed(session)
            SessionImplementor sessionImpl = (SessionImplementor) session
            Boolean isUnsaved = sessionImpl.getEntityPersister(entityName, entity).isTransient(entity, sessionImpl)
            if (Boolean.TRUE.equals(isUnsaved)) {
                session.persist(entityName, entity)
            } else {
                session.merge(entityName, entity)
            }
            return (Serializable) session.getIdentifier(entity)
        } as HibernateCallback<Serializable>))
    }

    @Override
    void update(Object entity) throws DataAccessException {
        update(entity, null)
    }

    @Override
    void update(Object entity, LockMode lockMode) throws DataAccessException {
        executeWithNativeSession({ Session session ->
            checkWriteOperationAllowed(session)
            session.merge(entity)
            if (lockMode != null) {
                session.lock(entity, lockMode)
            }
            return null
        } as HibernateCallback<Object>)
    }

    @Override
    void update(String entityName, Object entity) throws DataAccessException {
        update(entityName, entity, null)
    }

    @Override
    void update(String entityName, Object entity, LockMode lockMode)
            throws DataAccessException {

        executeWithNativeSession({ Session session ->
            checkWriteOperationAllowed(session)
            session.merge(entityName, entity)
            if (lockMode != null) {
                session.lock(entity, lockMode)
            }
            return null
        } as HibernateCallback<Object>)
    }

    @Override
    void saveOrUpdate(Object entity) throws DataAccessException {
        executeWithNativeSession({ Session session ->
            checkWriteOperationAllowed(session)
            SessionImplementor sessionImpl = (SessionImplementor) session
            Boolean isUnsaved = sessionImpl.getEntityPersister(null, entity).isTransient(entity, sessionImpl)
            if (Boolean.TRUE.equals(isUnsaved)) {
                session.persist(entity)
            } else {
                session.merge(entity)
            }
            return null
        } as HibernateCallback<Object>)
    }

    @Override
    void saveOrUpdate(String entityName, Object entity) throws DataAccessException {
        executeWithNativeSession({ Session session ->
            checkWriteOperationAllowed(session)
            SessionImplementor sessionImpl = (SessionImplementor) session
            Boolean isUnsaved = sessionImpl.getEntityPersister(entityName, entity).isTransient(entity, sessionImpl)
            if (Boolean.TRUE.equals(isUnsaved)) {
                session.persist(entityName, entity)
            } else {
                session.merge(entityName, entity)
            }
            return null
        } as HibernateCallback<Object>)
    }

    @Override
    void replicate(Object entity, ReplicationMode replicationMode) throws DataAccessException {
        executeWithNativeSession({ Session session ->
            checkWriteOperationAllowed(session)
            session.replicate(entity, replicationMode)
            return null
        } as HibernateCallback<Object>)
    }

    @Override
    void replicate(String entityName, Object entity, ReplicationMode replicationMode)
            throws DataAccessException {

        executeWithNativeSession({ Session session ->
            checkWriteOperationAllowed(session)
            session.replicate(entityName, entity, replicationMode)
            return null
        } as HibernateCallback<Object>)
    }

    @Override
    void persist(Object entity) throws DataAccessException {
        executeWithNativeSession({ Session session ->
            checkWriteOperationAllowed(session)
            session.persist(entity)
            return null
        } as HibernateCallback<Object>)
    }

    @Override
    void persist(String entityName, Object entity) throws DataAccessException {
        executeWithNativeSession({ Session session ->
            checkWriteOperationAllowed(session)
            session.persist(entityName, entity)
            return null
        } as HibernateCallback<Object>)
    }

    @Override
    @SuppressWarnings('unchecked')
    def <T> T merge(T entity) throws DataAccessException {
        return nonNull(executeWithNativeSession({ Session session ->
            checkWriteOperationAllowed(session)
            return (T) session.merge(entity)
        } as HibernateCallback<T>))
    }

    @Override
    @SuppressWarnings('unchecked')
    def <T> T merge(String entityName, T entity) throws DataAccessException {
        return nonNull(executeWithNativeSession({ Session session ->
            checkWriteOperationAllowed(session)
            return (T) session.merge(entityName, entity)
        } as HibernateCallback<T>))
    }

    @Override
    void delete(Object entity) throws DataAccessException {
        delete(entity, null)
    }

    @Override
    void delete(Object entity, LockMode lockMode) throws DataAccessException {
        executeWithNativeSession({ Session session ->
            checkWriteOperationAllowed(session)
            if (lockMode != null) {
                session.lock(entity, lockMode)
            }
            session.remove(entity)
            return null
        } as HibernateCallback<Object>)
    }

    @Override
    void delete(String entityName, Object entity) throws DataAccessException {
        delete(entityName, entity, null)
    }

    @Override
    void delete(String entityName, Object entity, LockMode lockMode)
            throws DataAccessException {

        executeWithNativeSession({ Session session ->
            checkWriteOperationAllowed(session)
            if (lockMode != null) {
                session.lock(entity, lockMode)
            }
            session.remove(entity) // entityName not supported directly in remove, but session.remove(entity) works
            return null
        } as HibernateCallback<Object>)
    }

    @Override
    void deleteAll(Collection<?> entities) throws DataAccessException {
        executeWithNativeSession({ Session session ->
            checkWriteOperationAllowed(session)
            for (Object entity : entities) {
                session.remove(entity)
            }
            return null
        } as HibernateCallback<Object>)
    }

    @Override
    void flush() throws DataAccessException {
        executeWithNativeSession({ Session session ->
            session.flush()
            return null
        } as HibernateCallback<Object>)
    }

    @Override
    void clear() throws DataAccessException {
        executeWithNativeSession({ Session session ->
            session.clear()
            return null
        } as HibernateCallback<Object>)
    }

    //-------------------------------------------------------------------------
    // Convenience finder methods for HQL strings
    //-------------------------------------------------------------------------

    @Deprecated
    @Override
    List<?> find(String queryString, Object... values) throws DataAccessException {
        return nonNull(executeWithNativeSession({ Session session ->
            Query<?> queryObject = session.createQuery(queryString)
            prepareQuery(queryObject)
            if (values != null) {
                for (int i = 0; i < values.length; i++) {
                    queryObject.setParameter(i, values[i])
                }
            }
            return queryObject.list()
        } as HibernateCallback<List<?>>))
    }

    @Deprecated
    @Override
    List<?> findByNamedParam(String queryString, String paramName, Object value)
            throws DataAccessException {

        return findByNamedParam(queryString, [paramName] as String[], [value] as Object[])
    }

    @Deprecated
    @Override
    List<?> findByNamedParam(String queryString, String[] paramNames, Object[] values)
            throws DataAccessException {

        if (paramNames.length != values.length) {
            throw new IllegalArgumentException('Length of paramNames array must match length of values array')
        }
        return nonNull(executeWithNativeSession({ Session session ->
            Query<?> queryObject = session.createQuery(queryString)
            prepareQuery(queryObject)
            for (int i = 0; i < values.length; i++) {
                applyNamedParameterToQuery(queryObject, paramNames[i], values[i])
            }
            return queryObject.list()
        } as HibernateCallback<List<?>>))
    }

    @Deprecated
    @Override
    List<?> findByValueBean(String queryString, Object valueBean) throws DataAccessException {
        return nonNull(executeWithNativeSession({ Session session ->
            Query<?> queryObject = session.createQuery(queryString)
            prepareQuery(queryObject)
            queryObject.setProperties(valueBean)
            return queryObject.list()
        } as HibernateCallback<List<?>>))
    }

    //-------------------------------------------------------------------------
    // Convenience finder methods for named queries
    //-------------------------------------------------------------------------

    @Deprecated
    @Override
    List<?> findByNamedQuery(String queryName, Object... values) throws DataAccessException {
        return nonNull(executeWithNativeSession({ Session session ->
            Query<?> queryObject = session.getNamedQuery(queryName)
            prepareQuery(queryObject)
            if (values != null) {
                for (int i = 0; i < values.length; i++) {
                    queryObject.setParameter(i, values[i])
                }
            }
            return queryObject.list()
        } as HibernateCallback<List<?>>))
    }

    @Deprecated
    @Override
    List<?> findByNamedQueryAndNamedParam(String queryName, String paramName, Object value)
            throws DataAccessException {

        return findByNamedQueryAndNamedParam(queryName, [paramName] as String[], [value] as Object[])
    }

    @Deprecated
    @Override
    List<?> findByNamedQueryAndNamedParam(
            String queryName, String[] paramNames, Object[] values)
            throws DataAccessException {

        if (values != null && (paramNames == null || paramNames.length != values.length)) {
            throw new IllegalArgumentException('Length of paramNames array must match length of values array')
        }
        return nonNull(executeWithNativeSession({ Session session ->
            Query<?> queryObject = session.getNamedQuery(queryName)
            prepareQuery(queryObject)
            if (values != null) {
                for (int i = 0; i < values.length; i++) {
                    applyNamedParameterToQuery(queryObject, paramNames[i], values[i])
                }
            }
            return queryObject.list()
        } as HibernateCallback<List<?>>))
    }

    @Deprecated
    @Override
    List<?> findByNamedQueryAndValueBean(String queryName, Object valueBean) throws DataAccessException {
        return nonNull(executeWithNativeSession({ Session session ->
            Query<?> queryObject = session.getNamedQuery(queryName)
            prepareQuery(queryObject)
            queryObject.setProperties(valueBean)
            return queryObject.list()
        } as HibernateCallback<List<?>>))
    }

    //-------------------------------------------------------------------------
    // Convenience query methods for iteration and bulk updates/deletes
    //-------------------------------------------------------------------------

    @Deprecated
    @Override
    int bulkUpdate(String queryString, Object... values) throws DataAccessException {
        Integer result = executeWithNativeSession({ Session session ->
            Query<?> queryObject = session.createQuery(queryString)
            prepareQuery(queryObject)
            if (values != null) {
                for (int i = 0; i < values.length; i++) {
                    queryObject.setParameter(i, values[i])
                }
            }
            return queryObject.executeUpdate()
        } as HibernateCallback<Integer>)
        Assert.state(result != null, 'No update count')
        return result
    }

    //-------------------------------------------------------------------------
    // Helper methods used by the operations above
    //-------------------------------------------------------------------------

    /**
     * Check whether write operations are allowed on the given Session.
     * <p>Default implementation throws an InvalidDataAccessApiUsageException in
     * case of {@code FlushMode.MANUAL}. Can be overridden in subclasses.
     * @param session current Hibernate Session
     * @throws InvalidDataAccessApiUsageException if write operations are not allowed
     * @see #setCheckWriteOperations
     * @see Session#getFlushMode()
     * @see FlushMode#MANUAL
     */
    protected void checkWriteOperationAllowed(Session session) throws InvalidDataAccessApiUsageException {
        if (isCheckWriteOperations() && session.getHibernateFlushMode().lessThan(FlushMode.COMMIT)) {
            throw new InvalidDataAccessApiUsageException(
                    'Write operations are not allowed in read-only mode (FlushMode.MANUAL): ' +
                    "Turn your Session into FlushMode.COMMIT/AUTO or remove 'readOnly' marker from transaction definition.")
        }
    }

    /**
     * Prepare the given Query object, applying cache settings and/or
     * a transaction timeout.
     * @param queryObject the Query object to prepare
     * @see #setCacheQueries
     * @see #setQueryCacheRegion
     */
    protected void prepareQuery(Query<?> queryObject) {
        if (isCacheQueries()) {
            queryObject.setCacheable(true)
            if (getQueryCacheRegion() != null) {
                queryObject.setCacheRegion(getQueryCacheRegion())
            }
        }
        if (getFetchSize() > 0) {
            queryObject.setFetchSize(getFetchSize())
        }
        if (getMaxResults() > 0) {
            queryObject.setMaxResults(getMaxResults())
        }

        ResourceHolderSupport sessionHolder =
                (ResourceHolderSupport) TransactionSynchronizationManager.getResource(obtainSessionFactory())
        if (sessionHolder != null && sessionHolder.hasTimeout()) {
            queryObject.setTimeout(sessionHolder.getTimeToLiveInSeconds())
        }
    }

    /**
     * Apply the given name parameter to the given Query object.
     * @param queryObject the Query object
     * @param paramName the name of the parameter
     * @param value the value of the parameter
     * @throws HibernateException if thrown by the Query object
     */
    protected void applyNamedParameterToQuery(Query<?> queryObject, String paramName, Object value)
            throws HibernateException {

        if (value instanceof Collection) {
            queryObject.setParameterList(paramName, (Collection<?>) value)
        } else if (value instanceof Object[]) {
            queryObject.setParameterList(paramName, (Object[]) value)
        } else {
            queryObject.setParameter(paramName, value)
        }
    }

    private static <T> T nonNull(T result) {
        Assert.state(result != null, 'No result')
        return result
    }

    /**
     * Invocation handler that suppresses close calls on Hibernate Sessions.
     * Also prepares returned Query and Criteria objects.
     * @see Session#close
     */
    private class CloseSuppressingInvocationHandler implements InvocationHandler {

        private final Session target

        CloseSuppressingInvocationHandler(Session target) {
            this.target = target
        }

        @Override
        Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // Invocation on Session interface coming in...

            switch (method.getName()) {
                case 'equals':
                    // Only consider equal when proxies are identical.
                    return proxy.is(args[0])
                case 'hashCode':
                    // Use hashCode of Session proxy.
                    return System.identityHashCode(proxy)
                case 'close':
                    // Handle close method: suppress, not valid.
                    return null
                default:
                    try {
                        // Invoke method on target Session.
                        Object retVal = method.invoke(this.target, args)

                        // If return value is a Query, apply transaction timeout.
                        // Applies to createQuery, getNamedQuery.
                        if (retVal instanceof Query) {
                            prepareQuery((Query<?>) retVal)
                        }

                        return retVal
                    } catch (InvocationTargetException ex) {
                        throw ex.getTargetException()
                    }
            }
        }

    }

}
