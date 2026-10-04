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

import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.SQLException

import javax.sql.DataSource

import groovy.transform.CompileStatic
import jakarta.persistence.LockModeType
import jakarta.persistence.PersistenceException
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import org.hibernate.FlushMode
import org.hibernate.HibernateException
import org.hibernate.JDBCException
import org.hibernate.LockMode
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.event.spi.EventSource
import org.hibernate.exception.GenericJDBCException
import org.hibernate.query.Query
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import org.springframework.dao.DataAccessException
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.dao.InvalidDataAccessApiUsageException
import org.springframework.jdbc.datasource.ConnectionHolder
import org.springframework.jdbc.datasource.DataSourceUtils
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import org.springframework.jdbc.support.SQLErrorCodeSQLExceptionTranslator
import org.springframework.jdbc.support.SQLExceptionTranslator
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.util.Assert

import org.grails.datastore.gorm.jdbc.MultiTenantDataSource
import org.grails.orm.hibernate.support.hibernate7.DefaultTransactionResources
import org.grails.orm.hibernate.support.hibernate7.SessionFactoryUtils
import org.grails.orm.hibernate.support.hibernate7.SessionHolder
import org.grails.orm.hibernate.support.hibernate7.TransactionResources

@CompileStatic
@SuppressWarnings(['PMD.CloseResource', 'PMD.DataflowAnomalyAnalysis', 'PMD.CompareObjectsWithEquals', 'PMD.EmptyIfStmt'])
class GrailsHibernateTemplate implements IHibernateTemplate {

    /**
     * Never flush is a good strategy for read-only units of work.
     * Hibernate will not track and look
     * for changes in this case, avoiding any overhead of modification detection.
     *
     * <p>In case of an existing Session, FLUSH_NEVER will turn the flush mode to NEVER for the scope
     * of the current operation, resetting the previous flush mode afterwards.
     *
     * @see #setFlushMode
     */
    public static final int FLUSH_NEVER = 0
    /**
     * Automatic flushing is the default mode for a Hibernate Session. A session will get flushed on
     * transaction commit, and on certain find operations that might involve already modified
     * instances, but not after each unit of work like with eager flushing.
     *
     * <p>In case of an existing Session, FLUSH_AUTO will participate in the existing flush mode, not
     * modifying it for the current operation. This in particular means that this setting will not
     * modify an existing flush mode NEVER, in contrast to FLUSH_EAGER.
     *
     * @see #setFlushMode
     */
    public static final int FLUSH_AUTO = 1
    /**
     * Eager flushing leads to immediate synchronization with the database, even if in a transaction.
     * This causes inconsistencies to show up and throw a respective exception immediately, and JDBC
     * access code that participates in the same transaction will see the changes as the database is
     * already aware of them then. But the drawbacks are:
     *
     * <ul>
     *   <li>additional communication roundtrips with the database, instead of a single batch at
     *       transaction commit;
     *   <li>the fact that an actual database rollback is needed if the Hibernate transaction rolls
     *       back (due to already submitted SQL statements).
     * </ul>
     *
     * <p>In case of an existing Session, FLUSH_EAGER will turn the flush mode to AUTO for the scope
     * of the current operation and issue a flush at the end, resetting the previous flush mode
     * afterwards.
     *
     * @see #setFlushMode
     */
    public static final int FLUSH_EAGER = 2
    /**
     * Flushing at commit only is intended for units of work where no intermediate flushing is
     * desired, not even for find operations that might involve already modified instances.
     *
     * <p>In case of an existing Session, FLUSH_COMMIT will turn the flush mode to COMMIT for the
     * scope of the current operation, resetting the previous flush mode afterwards. The only
     * exception is an existing flush mode NEVER, which will not be modified through this setting.
     *
     * @see #setFlushMode
     */
    public static final int FLUSH_COMMIT = 3
    /**
     * Flushing before every query statement is rarely necessary. It is only available for special
     * needs.
     *
     * <p>In case of an existing Session, FLUSH_ALWAYS will turn the flush mode to ALWAYS for the
     * scope of the current operation, resetting the previous flush mode afterwards.
     *
     * @see #setFlushMode
     */
    public static final int FLUSH_ALWAYS = 4

    private static final Logger LOG = LoggerFactory.getLogger(GrailsHibernateTemplate)
    protected boolean exposeNativeSession = true
    protected boolean cacheQueries = false
    protected SessionFactory sessionFactory
    protected DataSource dataSource = null
    protected SQLExceptionTranslator jdbcExceptionTranslator
    protected int flushMode = FLUSH_AUTO
    private boolean osivReadOnly
    private boolean passReadOnlyToHibernate = false
    private boolean applyFlushModeOnlyToNonExistingTransactions = false
    protected TransactionResources txResources = new DefaultTransactionResources()

    protected GrailsHibernateTemplate() {
        // for testing
    }

    GrailsHibernateTemplate(SessionFactory sessionFactory) {
        Assert.notNull(sessionFactory, "Property 'sessionFactory' is required")
        this.sessionFactory = sessionFactory

        ConnectionProvider connectionProvider = ((SessionFactoryImplementor) sessionFactory)
                .serviceRegistry
                .getService(ConnectionProvider)
        this.dataSource = connectionProvider != null ? connectionProvider.unwrap(DataSource) : null
        if (this.dataSource != null) {
            if (this.dataSource instanceof TransactionAwareDataSourceProxy) {
                DataSource target = ((TransactionAwareDataSourceProxy) this.dataSource).targetDataSource
                if (target != null) {
                    this.dataSource = target
                }
            }
            jdbcExceptionTranslator = new SQLErrorCodeSQLExceptionTranslator(this.dataSource)
        } else {
            // must be in unit test mode, setup default translator
            SQLErrorCodeSQLExceptionTranslator sqlErrorCodeSQLExceptionTranslator =
                    new SQLErrorCodeSQLExceptionTranslator()
            sqlErrorCodeSQLExceptionTranslator.databaseProductName = 'H2'
            jdbcExceptionTranslator = sqlErrorCodeSQLExceptionTranslator
        }
    }

    GrailsHibernateTemplate(SessionFactory sessionFactory, HibernateDatastore datastore) {
        this(sessionFactory)
        if (datastore != null) {
            cacheQueries = datastore.isCacheQueries()
            this.osivReadOnly = datastore.isOsivReadOnly()
            this.passReadOnlyToHibernate = datastore.isPassReadOnlyToHibernate()
            this.flushMode = hibernateFlushModeToConstant(datastore.defaultFlushMode)
        }
    }

    GrailsHibernateTemplate(SessionFactory sessionFactory, HibernateDatastore datastore, int defaultFlushMode) {
        this(sessionFactory)
        if (datastore != null) {
            cacheQueries = datastore.isCacheQueries()
            this.osivReadOnly = datastore.isOsivReadOnly()
            this.passReadOnlyToHibernate = datastore.isPassReadOnlyToHibernate()
        }
        this.flushMode = defaultFlushMode
    }

    /** Maps a Hibernate {@link FlushMode} to one of the {@code FLUSH_*} constants of this class. */
    static int hibernateFlushModeToConstant(FlushMode mode) {
        switch (mode) {
            case FlushMode.MANUAL:
                return FLUSH_NEVER
            case FlushMode.COMMIT:
                return FLUSH_COMMIT
            case FlushMode.ALWAYS:
                return FLUSH_ALWAYS
            default:
                return FLUSH_AUTO
        }
    }

    @Override
    <T> T execute(Closure<T> callable) {
        HibernateCallback<T> hibernateCallback = (HibernateCallback<T>) callable.asType(HibernateCallback)
        return execute(hibernateCallback)
    }

    @SuppressWarnings('PMD.DataflowAnomalyAnalysis')
    @Override
    <T> T executeWithNewSession(Closure<T> callable) {
        SessionHolder sessionHolder = (SessionHolder) txResources.getResource(sessionFactory)
        SessionHolder previousHolder = sessionHolder
        ConnectionHolder previousConnectionHolder =
                (ConnectionHolder) txResources.getResource(dataSource)
        Session newSession = null
        boolean previousActiveSynchronization = txResources.isSynchronizationActive()
        List<TransactionSynchronization> transactionSynchronizations =
                previousActiveSynchronization ? txResources.synchronizations : null
        try {
            // if there are any previous synchronizations active we need to clear them and restore them
            // later (see finally block)
            if (previousActiveSynchronization) {
                txResources.clearSynchronization()
                // init a new synchronization to ensure that any opened database connections are closed by
                // the synchronization
                txResources.initSynchronization()
            }

            // if there are already bound holders, unbind them so they can be restored later
            if (sessionHolder != null) {
                txResources.unbindResource(sessionFactory)
            }
            // Unbind any pre-existing connection holder independently of the session holder: a
            // DataSource binding can exist without a matching SessionFactory binding when, for
            // example, Spring's SQLErrorCodesFactory eagerly acquires a connection via
            // DataSourceUtils during SQLErrorCodeSQLExceptionTranslator initialisation while a
            // parent-transaction synchronisation is already active.  Leaving it bound causes
            // HibernateTransactionManager.doBegin to throw "Already value bound" at line 565.
            if (previousConnectionHolder != null) {
                txResources.unbindResource(dataSource)
            }

            // create and bind a new session holder for the new session
            newSession = sessionFactory.openSession()
            applyFlushMode(newSession, false)
            sessionHolder = new SessionHolder(newSession)
            txResources.bindResource(sessionFactory, sessionHolder)

            return callable.call(newSession)
        } finally {
            try {
                // if an active synchronization was registered during the life time of the new session clear
                // it
                if (txResources.isSynchronizationActive()) {
                    txResources.clearSynchronization()
                }
                // If there is a synchronization active then leave it to the synchronization to close the
                // session
                // Clear any bound sessions and connections
                txResources.unbindResource(sessionFactory)
                ConnectionHolder connectionHolder =
                        (ConnectionHolder) txResources.unbindResourceIfPossible(dataSource)
                // if there is a connection holder and it holds an open connection close it
                try {
                    if (connectionHolder != null &&
                            !(dataSource instanceof MultiTenantDataSource) &&
                            !connectionHolder.connection.isClosed()) {
                        Connection conn = connectionHolder.connection
                        DataSourceUtils.releaseConnection(conn, dataSource)
                    }
                } catch (SQLException e) {
                    // ignore, connection closed already?
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(
                                "Could not close opened JDBC connection. Did the application close the connection manually?: ${e.message}".toString())
                    }
                }

                if (newSession != null) {
                    SessionFactoryUtils.closeSession(newSession)
                }
            } finally {
                // if there were previously active synchronizations then register those again
                if (previousActiveSynchronization) {
                    txResources.initSynchronization()
                    for (TransactionSynchronization transactionSynchronization : transactionSynchronizations) {
                        txResources.registerSynchronization(transactionSynchronization)
                    }
                }

                // now restore any previous state
                if (previousHolder != null) {
                    txResources.bindResource(sessionFactory, previousHolder)
                }
                // Restore the connection holder independently of the session holder so that
                // the parent-transaction's ConnectionSynchronization (re-registered above) can
                // still release it when the outer transaction completes.
                if (previousConnectionHolder != null) {
                    txResources.bindResource(dataSource, previousConnectionHolder)
                }
            }
        }
    }

    @Override
    <T1> T1 executeWithExistingOrCreateNewSession(SessionFactory sessionFactory, Closure<T1> callable) {
        SessionHolder sessionHolder = (SessionHolder) txResources.getResource(sessionFactory)
        if (sessionHolder == null) {
            return executeWithNewSession(callable)
        } else {
            return callable.call(sessionHolder.session)
        }
    }

    @Override
    SessionFactory getSessionFactory() {
        return sessionFactory
    }

    @Override
    void applySettings(org.hibernate.query.Query<?> query) {
        if (exposeNativeSession) {
            prepareQuery(query)
        }
    }

    boolean isCacheQueries() {
        return cacheQueries
    }

    void setCacheQueries(boolean cacheQueries) {
        this.cacheQueries = cacheQueries
    }

    @SuppressWarnings('PMD.PreserveStackTrace')
    <T> T execute(HibernateCallback<T> action) throws DataAccessException {
        return doExecute(action, false)
    }

    List<?> executeFind(HibernateCallback<?> action) throws DataAccessException {
        Object result = doExecute(action, false)
        if (result != null && !(result instanceof List)) {
            throw new InvalidDataAccessApiUsageException(
                    "Result object returned from HibernateCallback isn't a List: [${result}]".toString())
        }
        return (List<?>) result
    }

    protected boolean shouldPassReadOnlyToHibernate() {
        if ((passReadOnlyToHibernate || osivReadOnly) &&
                txResources.hasResource(getSessionFactory())) {
            if (txResources.isActualTransactionActive()) {
                return passReadOnlyToHibernate && txResources.isCurrentTransactionReadOnly()
            } else {
                return osivReadOnly
            }
        } else {
            return false
        }
    }

    boolean isOsivReadOnly() {
        return osivReadOnly
    }

    void setOsivReadOnly(boolean osivReadOnly) {
        this.osivReadOnly = osivReadOnly
    }

    /**
     * Execute the action specified by the given action object within a Session.
     *
     * @param action callback object that specifies the Hibernate action
     * @param enforceNativeSession whether to enforce exposure of the native Hibernate Session to
     *     callback code
     * @return a result object returned by the action, or <code>null</code>
     * @throws org.springframework.dao.DataAccessException in case of Hibernate errors
     */
    @SuppressWarnings('PMD.PreserveStackTrace')
    protected <T> T doExecute(HibernateCallback<T> action, boolean enforceNativeSession) throws DataAccessException {

        Assert.notNull(action, 'Callback object must not be null')

        Session session = getSession()
        boolean existingTransaction = isSessionTransactional(session)
        if (existingTransaction) {
            LOG.debug('Found thread-bound Session for HibernateTemplate')
        }

        FlushMode previousFlushMode = null
        try {
            previousFlushMode = applyFlushMode(session, existingTransaction)
            if (shouldPassReadOnlyToHibernate()) {
                session.defaultReadOnly = true
            }
            Session sessionToExpose =
                    (enforceNativeSession || exposeNativeSession ? session : createSessionProxy(session))
            T result = action.doInHibernate(sessionToExpose)
            flushIfNecessary(session, existingTransaction)
            return result
        } catch (HibernateException ex) {
            throw convertHibernateAccessException(ex)
        } catch (PersistenceException ex) {
            if (ex.cause instanceof HibernateException) {
                throw SessionFactoryUtils.convertHibernateAccessException((HibernateException) ex.cause)
            }
            throw ex
        } catch (SQLException ex) {
            throw Objects.requireNonNull(
                    jdbcExceptionTranslator.translate('Hibernate-related JDBC operation', null, ex))
        } finally {
            if (existingTransaction) {
                LOG.debug('Not closing pre-bound Hibernate Session after HibernateTemplate')
                if (previousFlushMode != null) {
                    session.setHibernateFlushMode(previousFlushMode)
                }
            } else {
                SessionFactoryUtils.closeSession(session)
            }
        }
    }

    protected boolean isSessionTransactional(Session session) {
        SessionHolder sessionHolder = (SessionHolder) txResources.getResource(sessionFactory)
        return sessionHolder != null && sessionHolder.session.is(session)
    }

    Session getSession() {
        try {
            return sessionFactory.currentSession
        } catch (HibernateException ex) {
            throw new DataAccessResourceFailureException('Could not obtain current Hibernate Session', ex)
        }
    }

    /**
     * Create a close-suppressing proxy for the given Hibernate Session. The proxy also prepares
     * returned Query and Criteria objects.
     *
     * @param session the Hibernate Session to create a proxy for
     * @return the Session proxy
     * @see org.hibernate.Session#close()
     * @see #prepareQuery
     * @see #prepareCriteria
     */
    protected Session createSessionProxy(Session session) {
        Class<?>[] sessionIfcs
        Class<?> mainIfc = Session
        if (session instanceof EventSource) {
            sessionIfcs = [mainIfc, EventSource] as Class[]
        } else if (session instanceof SessionImplementor) {
            sessionIfcs = [mainIfc, SessionImplementor] as Class[]
        } else {
            sessionIfcs = [mainIfc] as Class[]
        }
        return (Session) Proxy.newProxyInstance(
                Thread.currentThread().contextClassLoader,
                sessionIfcs,
                new CloseSuppressingInvocationHandler(session, this))
    }

    @Override
    <T> T get(Class<T> entityClass, Serializable id) throws DataAccessException {
        return doExecute({ Session session -> session.find(entityClass, id) }, true)
    }

    @Override
    <T> T get(Class<T> entityClass, Serializable id, LockMode mode) {
        return lock(entityClass, id, mode)
    }

    @Override
    void remove(Object entity) throws DataAccessException {
        doExecute(
                { Session session ->
                    session.remove(entity)
                    return null
                },
                true)
    }

    @Override
    <T> T load(Class<T> entityClass, Serializable id) throws DataAccessException {
        return doExecute({ Session session -> session.getReference(entityClass, id) }, true)
    }

    <T> T lock(Class<T> entityClass, Serializable id, LockMode lockMode)
            throws DataAccessException {
        return doExecute({ Session session -> session.find(entityClass, id, lockMode) }, true)
    }

    <T> List<T> loadAll(Class<T> entityClass) throws DataAccessException {
        return doExecute(
                { Session session ->
                    CriteriaBuilder criteriaBuilder = session.criteriaBuilder
                    CriteriaQuery<T> query = criteriaBuilder.createQuery(entityClass)
                    query.from(entityClass)
                    Query<T> jpaQuery = session.createQuery(query)
                    prepareCriteria(jpaQuery)
                    return jpaQuery.resultList
                },
                true)
    }

    @Override
    boolean contains(Object entity) throws DataAccessException {
        return doExecute({ Session session -> session.contains(entity) }, true)
    }

    @Override
    void evict(Object entity) throws DataAccessException {
        doExecute(
                { Session session ->
                    session.evict(entity)
                    return null
                },
                true)
    }

    @Override
    void lock(Object entity, LockMode lockMode) throws DataAccessException {
        doExecute(
                { Session session ->
                    session.lock(entity, LockModeType.PESSIMISTIC_WRITE)
                    return null
                },
                true)
    }

    @Override
    void refresh(Object entity) throws DataAccessException {
        refresh(entity, null)
    }

    void refresh(Object entity, LockMode lockMode) throws DataAccessException {
        doExecute(
                { Session session ->
                    if (lockMode == null) {
                        session.refresh(entity)
                    } else {
                        session.refresh(entity, lockMode)
                    }
                    return null
                },
                true)
    }

    boolean isExposeNativeSession() {
        return exposeNativeSession
    }

    void setExposeNativeSession(boolean exposeNativeSession) {
        this.exposeNativeSession = exposeNativeSession
    }

    /**
     * Prepare the given Query object, applying cache settings and/or a transaction timeout.
     *
     * @param query the Query object to prepare
     */
    void prepareQuery(org.hibernate.query.Query<?> query) {
        internalQuery(query)
    }

    private void internalQuery(Query<?> query) {
        if (cacheQueries) {
            query.cacheable = true
        }
        if (shouldPassReadOnlyToHibernate()) {
            query.readOnly = true
        }
        SessionHolder sessionHolder = (SessionHolder) txResources.getResource(sessionFactory)
        if (sessionHolder != null && sessionHolder.hasTimeout()) {
            query.setTimeout(sessionHolder.timeToLiveInSeconds)
        }
    }

    /**
     * Prepare the given Query object, applying cache settings and/or a transaction timeout.
     *
     * @param jpaQuery the Query object to prepare
     */
    <T> void prepareCriteria(Query<T> jpaQuery) {
        internalQuery(jpaQuery)
    }

    /** Return if a flush should be forced after executing the callback code. */
    @Override
    int getFlushMode() {
        return flushMode
    }

    /**
     * Set the flush behavior to one of the constants in this class. Default is FLUSH_AUTO.
     *
     * @see #FLUSH_AUTO
     */
    @Override
    void setFlushMode(int flushMode) {
        this.flushMode = flushMode
    }

    /**
     * Apply the flush mode that's been specified for this accessor to the given Session.
     *
     * @param session the current Hibernate Session
     * @param existingTransaction if executing within an existing transaction
     * @return the previous flush mode to restore after the operation, or <code>null</code> if none
     * @see #setFlushMode
     * @see org.hibernate.Session#setFlushMode
     */
    protected FlushMode applyFlushMode(Session session, boolean existingTransaction) {
        if (isApplyFlushModeOnlyToNonExistingTransactions() && existingTransaction) {
            return null
        }

        if (getFlushMode() == FLUSH_NEVER) {
            if (existingTransaction) {
                FlushMode previousFlushMode = session.hibernateFlushMode
                if (!previousFlushMode.lessThan(FlushMode.COMMIT)) {
                    session.setHibernateFlushMode(FlushMode.MANUAL)
                    return previousFlushMode
                }
            } else {
                session.setHibernateFlushMode(FlushMode.MANUAL)
            }
        } else if (getFlushMode() == FLUSH_EAGER) {
            //noinspection StatementWithEmptyBody
            if (existingTransaction) {
                FlushMode previousFlushMode = session.hibernateFlushMode
                if (previousFlushMode != FlushMode.AUTO) {
                    session.setHibernateFlushMode(FlushMode.AUTO)
                    return previousFlushMode
                }
            } else {
                // rely on default FlushMode.AUTO
            }
        } else if (getFlushMode() == FLUSH_COMMIT) {
            if (existingTransaction) {
                FlushMode previousFlushMode = session.hibernateFlushMode
                if (previousFlushMode == FlushMode.AUTO || previousFlushMode == FlushMode.ALWAYS) {
                    session.setHibernateFlushMode(FlushMode.COMMIT)
                    return previousFlushMode
                }
            } else {
                session.setHibernateFlushMode(FlushMode.COMMIT)
            }
        } else if (getFlushMode() == FLUSH_ALWAYS) {
            if (existingTransaction) {
                FlushMode previousFlushMode = session.hibernateFlushMode
                if (previousFlushMode != FlushMode.ALWAYS) {
                    session.setHibernateFlushMode(FlushMode.ALWAYS)
                    return previousFlushMode
                }
            } else {
                session.setHibernateFlushMode(FlushMode.ALWAYS)
            }
        }
        return null
    }

    protected void flushIfNecessary(Session session, boolean existingTransaction) throws HibernateException {
        if (getFlushMode() == FLUSH_EAGER || (!existingTransaction && getFlushMode() != FLUSH_NEVER)) {
            LOG.debug('Eagerly flushing Hibernate session')
            session.flush()
        }
    }

    @SuppressWarnings('ConstantConditions')
    protected DataAccessException convertHibernateAccessException(HibernateException ex) {
        if (ex instanceof JDBCException) {
            return convertJdbcAccessException((JDBCException) ex, jdbcExceptionTranslator)
        }
        if (GenericJDBCException == ex.class) {
            return convertJdbcAccessException((GenericJDBCException) ex, jdbcExceptionTranslator)
        }
        return SessionFactoryUtils.convertHibernateAccessException(ex)
    }

    @SuppressWarnings('SqlDialectInspection')
    protected DataAccessException convertJdbcAccessException(JDBCException ex, SQLExceptionTranslator translator) {
        String msg = ex.message
        String sql = ex.getSQL()
        SQLException sqlException = ex.getSQLException()
        return translator.translate("Hibernate operation: ${msg}".toString(), sql, sqlException)
    }

    @Override
    void persist(Object entity) throws DataAccessException {
        doExecute(
                { Session session ->
                    session.persist(entity)
                    return null
                },
                true)
    }

    @Override
    Object merge(Object entity) throws DataAccessException {
        return doExecute({ Session session -> session.merge(entity) }, true)
    }

    @Override
    void flush() throws DataAccessException {
        doExecute(
                { Session session ->
                    session.flush()
                    return null
                },
                true)
    }

    @Override
    void clear() throws DataAccessException {
        doExecute(
                { Session session ->
                    session.clear()
                    return null
                },
                true)
    }

    @Override
    void deleteAll(Collection<?> objects) {
        execute({ Session session ->
            for (Object entity : getIterableAsCollection(objects)) {
                session.remove(entity)
            }
            return null
        } as HibernateCallback<Void>)
    }

    @SuppressWarnings(['rawtypes', 'unchecked'])
    protected Collection getIterableAsCollection(Iterable objects) {
        Collection list
        if (objects instanceof Collection) {
            list = (Collection) objects
        } else {
            list = []
            for (Object object : objects) {
                list.add(object)
            }
        }
        return list
    }

    boolean isApplyFlushModeOnlyToNonExistingTransactions() {
        return applyFlushModeOnlyToNonExistingTransactions
    }

    void setApplyFlushModeOnlyToNonExistingTransactions(boolean applyFlushModeOnlyToNonExistingTransactions) {
        this.applyFlushModeOnlyToNonExistingTransactions = applyFlushModeOnlyToNonExistingTransactions
    }

    interface HibernateCallback<T> {

        T doInHibernate(Session session) throws HibernateException, SQLException

    }

}
