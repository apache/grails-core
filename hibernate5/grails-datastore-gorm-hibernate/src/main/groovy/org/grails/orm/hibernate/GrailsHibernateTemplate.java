/*
 * Copyright 2011-2013 SpringSource.
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
package org.grails.orm.hibernate;

import groovy.lang.Closure;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.hibernate.*;
import org.hibernate.engine.jdbc.connections.internal.DatasourceConnectionProviderImpl;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.event.spi.EventSource;
import org.hibernate.exception.GenericJDBCException;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.jdbc.support.SQLErrorCodeSQLExceptionTranslator;
import org.springframework.jdbc.support.SQLExceptionTranslator;
import org.springframework.orm.hibernate5.SessionFactoryUtils;
import org.springframework.orm.hibernate5.SessionHolder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.Assert;

import jakarta.persistence.PersistenceException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import javax.sql.DataSource;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class GrailsHibernateTemplate implements IHibernateTemplate {

    private static final Logger LOG = LoggerFactory.getLogger(GrailsHibernateTemplate.class);

    private boolean osivReadOnly;
    private boolean passReadOnlyToHibernate = false;
    protected boolean exposeNativeSession = true;
    protected boolean cacheQueries = false;

    protected SessionFactory sessionFactory;
    protected DataSource dataSource = null;
    protected SQLExceptionTranslator jdbcExceptionTranslator;
    protected int flushMode = FLUSH_AUTO;
    private boolean applyFlushModeOnlyToNonExistingTransactions = false;

    public interface HibernateCallback<T> {
        T doInHibernate(Session session) throws HibernateException, SQLException;
    }

    protected GrailsHibernateTemplate() {
        // for testing
    }
    public GrailsHibernateTemplate(SessionFactory sessionFactory) {
        Assert.notNull(sessionFactory, "Property 'sessionFactory' is required");
        this.sessionFactory = sessionFactory;

        ConnectionProvider connectionProvider = ((SessionFactoryImplementor) sessionFactory).getServiceRegistry().getService(ConnectionProvider.class);
        if(connectionProvider instanceof DatasourceConnectionProviderImpl) {
            this.dataSource = ((DatasourceConnectionProviderImpl) connectionProvider).getDataSource();
            if(dataSource instanceof TransactionAwareDataSourceProxy) {
                this.dataSource = ((TransactionAwareDataSourceProxy) dataSource).getTargetDataSource();
            }
            jdbcExceptionTranslator = new SQLErrorCodeSQLExceptionTranslator(dataSource);
        }
        else {
            // must be in unit test mode, setup default translator
            SQLErrorCodeSQLExceptionTranslator sqlErrorCodeSQLExceptionTranslator = new SQLErrorCodeSQLExceptionTranslator();
            sqlErrorCodeSQLExceptionTranslator.setDatabaseProductName("H2");
            jdbcExceptionTranslator = sqlErrorCodeSQLExceptionTranslator;
        }
    }

    public GrailsHibernateTemplate(SessionFactory sessionFactory, HibernateDatastore datastore) {
        this(sessionFactory, datastore, datastore.getDefaultFlushMode());
    }

    public GrailsHibernateTemplate(SessionFactory sessionFactory, HibernateDatastore datastore, int defaultFlushMode) {
        this(sessionFactory);
        if(datastore != null) {
            cacheQueries = datastore.isCacheQueries();
            this.osivReadOnly = datastore.isOsivReadOnly();
            this.passReadOnlyToHibernate = datastore.isPassReadOnlyToHibernate();
        }
        this.flushMode = defaultFlushMode;
    }


    @Override
    public <T> T execute(Closure<T> callable) {
        HibernateCallback<T> hibernateCallback = DefaultGroovyMethods.asType(callable, HibernateCallback.class);
        return execute(hibernateCallback);
    }

    @Override
    public <T> T executeWithNewSession(final Closure<T> callable) {
        SessionHolder sessionHolder = (SessionHolder)TransactionSynchronizationManager.getResource(sessionFactory);
        SessionHolder previousHolder = sessionHolder;
        ConnectionHolder previousConnectionHolder = (ConnectionHolder)TransactionSynchronizationManager.getResource(dataSource);
        Session newSession = null;
        boolean previousActiveSynchronization = TransactionSynchronizationManager.isSynchronizationActive();
        List<TransactionSynchronization> transactionSynchronizations = previousActiveSynchronization ? TransactionSynchronizationManager.getSynchronizations() : null;
        try {
            // if there are any previous synchronizations active we need to clear them and restore them later (see finally block)
            if(previousActiveSynchronization) {
                TransactionSynchronizationManager.clearSynchronization();
                // init a new synchronization to ensure that any opened database connections are closed by the synchronization
                TransactionSynchronizationManager.initSynchronization();
            }

            // if there are already bound holders, unbind them so they can be restored later
            if (sessionHolder != null) {
                TransactionSynchronizationManager.unbindResource(sessionFactory);
                if(previousConnectionHolder != null) {
                    TransactionSynchronizationManager.unbindResource(dataSource);
                }
            }

            // create and bind a new session holder for the new session
            newSession = sessionFactory.openSession();
            applyFlushMode(newSession, false);
            sessionHolder = new SessionHolder(newSession);
            TransactionSynchronizationManager.bindResource(sessionFactory, sessionHolder);

            return execute(callable::call);
        }
        finally {
            try {
                // if an active synchronization was registered during the life time of the new session clear it
                if(TransactionSynchronizationManager.isSynchronizationActive()) {
                    TransactionSynchronizationManager.clearSynchronization();
                }
                // If there is a synchronization active then leave it to the synchronization to close the session
                if(newSession != null) {
                    SessionFactoryUtils.closeSession(newSession);
                }

                // Clear any bound sessions and connections
                TransactionSynchronizationManager.unbindResource(sessionFactory);
                ConnectionHolder connectionHolder = (ConnectionHolder) TransactionSynchronizationManager.unbindResourceIfPossible(dataSource);
                // if there is a connection holder and it holds an open connection close it
                try {
                    if(connectionHolder != null && !connectionHolder.getConnection().isClosed()) {
                        Connection conn = connectionHolder.getConnection();
                        DataSourceUtils.releaseConnection(conn, dataSource);
                    }
                } catch (SQLException e) {
                    // ignore, connection closed already?
                    if(LOG.isDebugEnabled()) {
                        LOG.debug("Could not close opened JDBC connection. Did the application close the connection manually?: " + e.getMessage());
                    }
                }
            }
            finally {
                // if there were previously active synchronizations then register those again
                if(previousActiveSynchronization) {
                    TransactionSynchronizationManager.initSynchronization();
                    for (TransactionSynchronization transactionSynchronization : transactionSynchronizations) {
                        TransactionSynchronizationManager.registerSynchronization(transactionSynchronization);
                    }
                }

                // now restore any previous state
                if(previousHolder != null) {
                    TransactionSynchronizationManager.bindResource(sessionFactory, previousHolder);
                    if(previousConnectionHolder != null) {
                        TransactionSynchronizationManager.bindResource(dataSource, previousConnectionHolder);
                    }
                }

            }
        }
    }

    @Override
    public <T1> T1 executeWithExistingOrCreateNewSession(SessionFactory sessionFactory, Closure<T1> callable) {
        SessionHolder sessionHolder = (SessionHolder) TransactionSynchronizationManager.getResource(sessionFactory);
        if(sessionHolder == null) {
            return executeWithNewSession(callable);
        }
        else {
            return callable.call(sessionHolder.getSession());
        }
    }

    public SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    @Override
    public void applySettings(org.hibernate.query.Query query) {
        if (exposeNativeSession) {
            prepareQuery(query);
        }
    }

    @Override
    public void applySettings(Criteria criteria) {
        if (exposeNativeSession) {
            prepareCriteria(criteria);
        }
    }

    public void setCacheQueries(boolean cacheQueries) {
        this.cacheQueries = cacheQueries;
    }

    public boolean isCacheQueries() {
        return cacheQueries;
    }

    public <T> T execute(HibernateCallback<T> action) throws DataAccessException {
        return doExecute(action, false);
    }

    public List<?> executeFind(HibernateCallback<?> action) throws DataAccessException {
        Object result = doExecute(action, false);
        if (result != null && !(result instanceof List)) {
            throw new InvalidDataAccessApiUsageException("Result object returned from HibernateCallback isn't a List: [" + result + "]");
        }
        return (List<?>) result;
    }

    protected boolean shouldPassReadOnlyToHibernate() {
        if((passReadOnlyToHibernate || osivReadOnly) && TransactionSynchronizationManager.hasResource(getSessionFactory())) {
            if(TransactionSynchronizationManager.isActualTransactionActive()) {
                return passReadOnlyToHibernate && TransactionSynchronizationManager.isCurrentTransactionReadOnly();
            } else {
                return osivReadOnly;
            }
        } else {
            return false;
        }
    }

    public boolean isOsivReadOnly() {
        return osivReadOnly;
    }

    public void setOsivReadOnly(boolean osivReadOnly) {
        this.osivReadOnly = osivReadOnly;
    }

    /**
     * Execute the action specified by the given action object within a Session.
     *
     * @param action               callback object that specifies the Hibernate action
     * @param enforceNativeSession whether to enforce exposure of the native Hibernate Session to callback code
     * @return a result object returned by the action, or <code>null</code>
     * @throws org.springframework.dao.DataAccessException in case of Hibernate errors
     */
    protected <T> T doExecute(HibernateCallback<T> action, boolean enforceNativeSession) throws DataAccessException {

        Assert.notNull(action, "Callback object must not be null");

        Session session = getSession();
        boolean existingTransaction = isSessionTransactional(session);
        if (existingTransaction) {
            LOG.debug("Found thread-bound Session for HibernateTemplate");
        }

        FlushMode previousFlushMode = null;
        try {
            previousFlushMode = applyFlushMode(session, existingTransaction);
            if (shouldPassReadOnlyToHibernate()) {
                session.setDefaultReadOnly(true);
            }
            Session sessionToExpose = (enforceNativeSession || exposeNativeSession ? session : createSessionProxy(session));
            T result = action.doInHibernate(sessionToExpose);
            flushIfNecessary(session, existingTransaction);
            return result;
        } catch (HibernateException ex) {
            throw convertHibernateAccessException(ex);
        }
        catch (PersistenceException ex) {
            if (ex.getCause() instanceof HibernateException) {
                throw SessionFactoryUtils.convertHibernateAccessException((HibernateException) ex.getCause());
            }
            throw ex;
        }
        catch (SQLException ex) {
            throw jdbcExceptionTranslator.translate("Hibernate-related JDBC operation", null, ex);
        } catch (RuntimeException ex) {
            // Callback code threw application exception...
            throw ex;
        } finally {
            if (existingTransaction) {
                LOG.debug("Not closing pre-bound Hibernate Session after HibernateTemplate");
                if (previousFlushMode != null) {
                    session.setHibernateFlushMode(previousFlushMode);
                }
            } else {
                SessionFactoryUtils.closeSession(session);
            }
        }
    }

    protected boolean isSessionTransactional(Session session) {
        SessionHolder sessionHolder = (SessionHolder) TransactionSynchronizationManager.getResource(sessionFactory);
        return sessionHolder != null && sessionHolder.getSession() == session;
    }

    protected Session getSession() {
        try {
            return sessionFactory.getCurrentSession();
        } catch (HibernateException ex) {
            throw new DataAccessResourceFailureException("Could not obtain current Hibernate Session", ex);
        }
    }

    /**
     * Create a close-suppressing proxy for the given Hibernate Session. The
     * proxy also prepares returned Query and Criteria objects.
     *
     * @param session the Hibernate Session to create a proxy for
     * @return the Session proxy
     * @see org.hibernate.Session#close()
     * @see #prepareQuery
     * @see #prepareCriteria
     */
    protected Session createSessionProxy(Session session) {
        Class<?>[] sessionIfcs;
        Class<?> mainIfc = Session.class;
        if (session instanceof EventSource) {
            sessionIfcs = new Class[]{mainIfc, EventSource.class};
        } else if (session instanceof SessionImplementor) {
            sessionIfcs = new Class[]{mainIfc, SessionImplementor.class};
        } else {
            sessionIfcs = new Class[]{mainIfc};
        }
        return (Session) Proxy.newProxyInstance(session.getClass().getClassLoader(), sessionIfcs,
                new CloseSuppressingInvocationHandler(session));
    }

    public <T> T get(final Class<T> entityClass, final Serializable id) throws DataAccessException {
        return doExecute(session -> session.get(entityClass, id), true);
    }

    public <T> T get(final Class<T> entityClass, final Serializable id, final LockMode mode) {
        return lock(entityClass, id, mode);
    }

    public void delete(final Object entity) throws DataAccessException {
        doExecute(session -> {
            session.delete(entity);
            return null;
        }, true);
    }

    public void flush(final Object entity) throws DataAccessException {
        doExecute(session -> {
            session.flush();
            return null;
        }, true);
    }

    public <T> T load(final Class<T> entityClass, final Serializable id) throws DataAccessException {
        return doExecute(session -> session.load(entityClass, id), true);
    }

    public <T> T lock(final Class<T> entityClass, final Serializable id, final LockMode lockMode) throws DataAccessException {
        return doExecute(session -> session.get(entityClass, id, new LockOptions(lockMode)), true);
    }

    public <T> List<T> loadAll(final Class<T> entityClass) throws DataAccessException {
        return doExecute(session -> {
            final CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder();
            final CriteriaQuery<T> query = criteriaBuilder.createQuery(entityClass);
            final Root<T> root = query.from(entityClass);
            final Query<T> jpaQuery = session.createQuery(query);
            prepareCriteria(jpaQuery);
            return jpaQuery.getResultList();
        }, true);
    }

    public boolean contains(final Object entity) throws DataAccessException {
        return doExecute(session -> session.contains(entity), true);
    }

    public void evict(final Object entity) throws DataAccessException {
        doExecute(session -> {
            session.evict(entity);
            return null;
        }, true);
    }

    public void lock(final Object entity, final LockMode lockMode) throws DataAccessException {
        doExecute(session -> {
            session.buildLockRequest(new LockOptions(lockMode)).lock(entity);//LockMode.PESSIMISTIC_WRITE
            return null;
        }, true);
    }

    public void refresh(final Object entity) throws DataAccessException {
        refresh(entity, null);
    }

    public void refresh(final Object entity, final LockMode lockMode) throws DataAccessException {
        doExecute(session -> {
            if (lockMode == null) {
                session.refresh(entity);
            } else {
                session.refresh(entity, new LockOptions(lockMode));
            }
            return null;
        }, true);
    }

    public void setExposeNativeSession(boolean exposeNativeSession) {
        this.exposeNativeSession = exposeNativeSession;
    }

    public boolean isExposeNativeSession() {
        return exposeNativeSession;
    }

    /**
     * Prepare the given Query object, applying cache settings and/or a
     * transaction timeout.
     *
     * @param query the Query object to prepare
     */
    protected void prepareQuery(org.hibernate.query.Query query) {
        if (cacheQueries) {
            query.setCacheable(true);
        }
        if (shouldPassReadOnlyToHibernate()) {
            query.setReadOnly(true);
        }
        SessionHolder sessionHolder = (SessionHolder) TransactionSynchronizationManager.getResource(sessionFactory);
        if (sessionHolder != null && sessionHolder.hasTimeout()) {
            query.setTimeout(sessionHolder.getTimeToLiveInSeconds());
        }
    }

    /**
     * Prepare the given Criteria object, applying cache settings and/or a
     * transaction timeout.
     *
     * @param criteria the Criteria object to prepare
     * @deprecated Deprecated because Hibernate Criteria are deprecated
     */
    @Deprecated
    protected void prepareCriteria(Criteria criteria) {
        if (cacheQueries) {
            criteria.setCacheable(true);
        }
        if (shouldPassReadOnlyToHibernate()) {
            criteria.setReadOnly(true);
        }
        SessionHolder sessionHolder = (SessionHolder) TransactionSynchronizationManager.getResource(sessionFactory);
        if (sessionHolder != null && sessionHolder.hasTimeout()) {
            criteria.setTimeout(sessionHolder.getTimeToLiveInSeconds());
        }
    }

    /**
     * Prepare the given Query object, applying cache settings and/or a
     * transaction timeout.
     *
     * @param jpaQuery the Query object to prepare
     */
    protected <T> void prepareCriteria(Query<T> jpaQuery) {
        if (cacheQueries) {
            jpaQuery.setCacheable(true);
        }
        if (shouldPassReadOnlyToHibernate()) {
            jpaQuery.setReadOnly(true);
        }
        SessionHolder sessionHolder = (SessionHolder) TransactionSynchronizationManager.getResource(sessionFactory);
        if (sessionHolder != null && sessionHolder.hasTimeout()) {
            jpaQuery.setTimeout(sessionHolder.getTimeToLiveInSeconds());
        }
    }


    /**
     * Invocation handler that suppresses close calls on Hibernate Sessions.
     * Also prepares returned Query and Criteria objects.
     *
     * @see org.hibernate.Session#close
     */
    protected class CloseSuppressingInvocationHandler implements InvocationHandler {

        protected final Session target;

        protected CloseSuppressingInvocationHandler(Session target) {
            this.target = target;
        }

        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // Invocation on Session interface coming in...

            if (method.getName().equals("equals")) {
                // Only consider equal when proxies are identical.
                return (proxy == args[0]);
            }
            if (method.getName().equals("hashCode")) {
                // Use hashCode of Session proxy.
                return System.identityHashCode(proxy);
            }
            if (method.getName().equals("close")) {
                // Handle close method: suppress, not valid.
                return null;
            }

            // Invoke method on target Session.
            try {
                Object retVal = method.invoke(target, args);

                // If return value is a Query or Criteria, apply transaction timeout.
                // Applies to createQuery, getNamedQuery, createCriteria.
                if (retVal instanceof org.hibernate.query.Query) {
                    prepareQuery(((org.hibernate.query.Query) retVal));
                }
                if (retVal instanceof Criteria) {
                    prepareCriteria(((Criteria) retVal));
                } else if (retVal instanceof Query) {
                    prepareCriteria(((Query) retVal));
                }

                return retVal;
            } catch (InvocationTargetException ex) {
                throw ex.getTargetException();
            }
        }
    }

    /**
     * Never flush is a good strategy for read-only units of work.
     * Hibernate will not track and look for changes in this case,
     * avoiding any overhead of modification detection.
     * <p>In case of an existing Session, FLUSH_NEVER will turn the flush mode
     * to NEVER for the scope of the current operation, resetting the previous
     * flush mode afterwards.
     *
     * @see #setFlushMode
     */
    public static final int FLUSH_NEVER = 0;

    /**
     * Automatic flushing is the default mode for a Hibernate Session.
     * A session will get flushed on transaction commit, and on certain find
     * operations that might involve already modified instances, but not
     * after each unit of work like with eager flushing.
     * <p>In case of an existing Session, FLUSH_AUTO will participate in the
     * existing flush mode, not modifying it for the current operation.
     * This in particular means that this setting will not modify an existing
     * flush mode NEVER, in contrast to FLUSH_EAGER.
     *
     * @see #setFlushMode
     */
    public static final int FLUSH_AUTO = 1;

    /**
     * Eager flushing leads to immediate synchronization with the database,
     * even if in a transaction. This causes inconsistencies to show up and throw
     * a respective exception immediately, and JDBC access code that participates
     * in the same transaction will see the changes as the database is already
     * aware of them then. But the drawbacks are:
     * <ul>
     * <li>additional communication roundtrips with the database, instead of a
     * single batch at transaction commit;
     * <li>the fact that an actual database rollback is needed if the Hibernate
     * transaction rolls back (due to already submitted SQL statements).
     * </ul>
     * <p>In case of an existing Session, FLUSH_EAGER will turn the flush mode
     * to AUTO for the scope of the current operation and issue a flush at the
     * end, resetting the previous flush mode afterwards.
     *
     * @see #setFlushMode
     */
    public static final int FLUSH_EAGER = 2;

    /**
     * Flushing at commit only is intended for units of work where no
     * intermediate flushing is desired, not even for find operations
     * that might involve already modified instances.
     * <p>In case of an existing Session, FLUSH_COMMIT will turn the flush mode
     * to COMMIT for the scope of the current operation, resetting the previous
     * flush mode afterwards. The only exception is an existing flush mode
     * NEVER, which will not be modified through this setting.
     *
     * @see #setFlushMode
     */
    public static final int FLUSH_COMMIT = 3;

    /**
     * Flushing before every query statement is rarely necessary.
     * It is only available for special needs.
     * <p>In case of an existing Session, FLUSH_ALWAYS will turn the flush mode
     * to ALWAYS for the scope of the current operation, resetting the previous
     * flush mode afterwards.
     *
     * @see #setFlushMode
     */
    public static final int FLUSH_ALWAYS = 4;

    /**
     * Set the flush behavior to one of the constants in this class. Default is
     * FLUSH_AUTO.
     *
     * @see #FLUSH_AUTO
     */
    public void setFlushMode(int flushMode) {
        this.flushMode = flushMode;
    }

    /**
     * Return if a flush should be forced after executing the callback code.
     */
    public int getFlushMode() {
        return flushMode;
    }

    /**
     * Apply the flush mode that's been specified for this accessor to the given Session.
     *
     * @param session             the current Hibernate Session
     * @param existingTransaction if executing within an existing transaction
     * @return the previous flush mode to restore after the operation, or <code>null</code> if none
     * @see #setFlushMode
     * @see org.hibernate.Session#setFlushMode
     */
    protected FlushMode applyFlushMode(Session session, boolean existingTransaction) {
        if(isApplyFlushModeOnlyToNonExistingTransactions() && existingTransaction) {
            return null;
        }

        if (getFlushMode() == FLUSH_NEVER) {
            if (existingTransaction) {
                FlushMode previousFlushMode = session.getHibernateFlushMode();
                if (!previousFlushMode.lessThan(FlushMode.COMMIT)) {
                    session.setHibernateFlushMode(FlushMode.MANUAL);
                    return previousFlushMode;
                }
            } else {
                session.setHibernateFlushMode(FlushMode.MANUAL);
            }
        } else if (getFlushMode() == FLUSH_EAGER) {
            if (existingTransaction) {
                FlushMode previousFlushMode = session.getHibernateFlushMode();
                if (!previousFlushMode.equals(FlushMode.AUTO)) {
                    session.setHibernateFlushMode(FlushMode.AUTO);
                    return previousFlushMode;
                }
            } else {
                // rely on default FlushMode.AUTO
            }
        } else if (getFlushMode() == FLUSH_COMMIT) {
            if (existingTransaction) {
                FlushMode previousFlushMode = session.getHibernateFlushMode();
                if (previousFlushMode.equals(FlushMode.AUTO) || previousFlushMode.equals(FlushMode.ALWAYS)) {
                    session.setHibernateFlushMode(FlushMode.COMMIT);
                    return previousFlushMode;
                }
            } else {
                session.setHibernateFlushMode(FlushMode.COMMIT);
            }
        } else if (getFlushMode() == FLUSH_ALWAYS) {
            if (existingTransaction) {
                FlushMode previousFlushMode = session.getHibernateFlushMode();
                if (!previousFlushMode.equals(FlushMode.ALWAYS)) {
                    session.setHibernateFlushMode(FlushMode.ALWAYS);
                    return previousFlushMode;
                }
            } else {
                session.setHibernateFlushMode(FlushMode.ALWAYS);
            }
        }
        return null;
    }

    protected void flushIfNecessary(Session session, boolean existingTransaction) throws HibernateException {
        if (getFlushMode() == FLUSH_EAGER || (!existingTransaction && getFlushMode() != FLUSH_NEVER)) {
            LOG.debug("Eagerly flushing Hibernate session");
            session.flush();
        }
    }

    @SuppressWarnings("ConstantConditions")
    protected DataAccessException convertHibernateAccessException(HibernateException ex) {
        if (ex instanceof JDBCException) {
            return convertJdbcAccessException((JDBCException) ex, jdbcExceptionTranslator);
        }
        if (GenericJDBCException.class.equals(ex.getClass())) {
            return convertJdbcAccessException((GenericJDBCException) ex, jdbcExceptionTranslator);
        }
        return SessionFactoryUtils.convertHibernateAccessException(ex);
    }

    @SuppressWarnings("SqlDialectInspection")
    protected DataAccessException convertJdbcAccessException(JDBCException ex, SQLExceptionTranslator translator) {
        String msg = ex.getMessage();
        String sql = ex.getSQL();
        SQLException sqlException = ex.getSQLException();
        return translator.translate("Hibernate operation: " + msg, sql, sqlException);
    }

    public Serializable save(Object o) {
        return sessionFactory.getCurrentSession().save(o);
    }

    public void flush() {
        sessionFactory.getCurrentSession().flush();
    }

    public void clear() {
        sessionFactory.getCurrentSession().clear();
    }

    public void deleteAll(final Collection<?> objects) {
        execute((HibernateCallback<Void>) session -> {
            for (Object entity : getIterableAsCollection(objects)) {
                session.delete(entity);
            }
            return null;
        });
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    protected Collection getIterableAsCollection(Iterable objects) {
        Collection list;
        if (objects instanceof Collection) {
            list = (Collection) objects;
        } else {
            list = new ArrayList();
            for (Object object : objects) {
                list.add(object);
            }
        }
        return list;
    }
    
    public boolean isApplyFlushModeOnlyToNonExistingTransactions() {
        return applyFlushModeOnlyToNonExistingTransactions;
    }
    
    public void setApplyFlushModeOnlyToNonExistingTransactions(boolean applyFlushModeOnlyToNonExistingTransactions) {
        this.applyFlushModeOnlyToNonExistingTransactions = applyFlushModeOnlyToNonExistingTransactions;
    }
}
