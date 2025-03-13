package org.grails.plugin.hibernate.support;

import grails.persistence.support.PersistenceContextInterceptor;
import grails.validation.DeferredBindingActions;
import org.grails.core.lifecycle.ShutdownOperations;
import org.grails.datastore.mapping.core.connections.ConnectionSource;
import org.grails.orm.hibernate.AbstractHibernateDatastore;
import org.grails.orm.hibernate.support.HibernateRuntimeUtils;
import org.hibernate.FlushMode;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.hibernate5.SessionFactoryUtils;
import org.springframework.orm.hibernate5.SessionHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Connection;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * @author Graeme Rocher
 * @since 0.4
 */
public class HibernatePersistenceContextInterceptor implements PersistenceContextInterceptor, SessionFactoryAwarePersistenceContextInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(HibernatePersistenceContextInterceptor.class);
    private AbstractHibernateDatastore hibernateDatastore;


    private static ThreadLocal<Map<String, Boolean>> participate = ThreadLocal.withInitial(HashMap::new);

    private static ThreadLocal<Map<String, Integer>> nestingCount = ThreadLocal.withInitial(HashMap::new);


    private String dataSourceName;

    static {
        ShutdownOperations.addOperation(() -> {
            participate.remove();
            nestingCount.remove();
        });
    }

    private Deque<Connection> disconnected = new ConcurrentLinkedDeque<>();
    private final boolean transactionRequired;

    public HibernatePersistenceContextInterceptor() {
        this(ConnectionSource.DEFAULT);
    }

    /**
     * @param dataSourceName a name of dataSource
     */
    public HibernatePersistenceContextInterceptor(String dataSourceName) {
        this.dataSourceName = dataSourceName;
        this.transactionRequired = true;
    }

    /* (non-Javadoc)
     * @see org.apache.groovy.grails.support.PersistenceContextInterceptor#destroy()
     */
    public void destroy() {
        DeferredBindingActions.clear();
        if(!disconnected.isEmpty()) {
            disconnected.pop();
        }
        if (getSessionFactory() == null || decNestingCount() > 0 || getParticipate()) {
            return;
        }

        // single session mode
        SessionHolder holder = (SessionHolder) TransactionSynchronizationManager.unbindResource(getSessionFactory());
        LOG.debug("Closing single Hibernate session in GrailsDispatcherServlet");
        try {
            disconnected.clear();
            SessionFactoryUtils.closeSession(holder.getSession());
        }
        catch (RuntimeException ex) {
            LOG.error("Unexpected exception on closing Hibernate Session", ex);
        }
    }

    public void disconnect() {
        if (getSessionFactory() == null) return;
        try {
            disconnected.add(
                    getSession(false).disconnect()
            );

        }
        catch (Exception e) {
            // no session ignore
        }
    }

    public void reconnect() {
        if (getSessionFactory() == null) return;
        Session session = getSession();
        if(!session.isConnected() && !disconnected.isEmpty()) {
            try {
                Connection connection = disconnected.peekLast();
                getSession().reconnect(connection);
            } catch (IllegalStateException e) {
                // cannot reconnect on different exception. ignore
                LOG.debug(e.getMessage(),e);
            }
        }
    }

    public void flush() {
        if (getSessionFactory() == null) return;
        if(!getParticipate()) {
            if(!transactionRequired) {
                getSession().flush();
            }
            else if(TransactionSynchronizationManager.isSynchronizationActive()) {
                getSession().flush();
            }
        }
    }

    public void clear() {
        if (getSessionFactory() == null) return;
        getSession().clear();
    }

    public void setReadOnly() {
        if (getSessionFactory() == null) return;
        getSession().setHibernateFlushMode(FlushMode.MANUAL);
    }

    public void setReadWrite() {
        if (getSessionFactory() == null) return;
        getSession().setHibernateFlushMode(FlushMode.AUTO);
    }

    public boolean isOpen() {
        if (getSessionFactory() == null) return false;
        try {
            return getSession(false).isOpen();
        }
        catch (Exception e) {
            return false;
        }
    }

    /* (non-Javadoc)
     * @see org.apache.groovy.grails.support.PersistenceContextInterceptor#init()
     */
    public void init() {
        if (incNestingCount() > 1) {
            return;
        }
        SessionFactory sf = getSessionFactory();
        if (sf == null) {
            return;
        }
        if (TransactionSynchronizationManager.hasResource(sf)) {
            // Do not modify the Session: just set the participate flag.
            setParticipate(true);
        }
        else {
            setParticipate(false);
            LOG.debug("Opening single Hibernate session in HibernatePersistenceContextInterceptor");
            Session session = getSession();
            HibernateRuntimeUtils.enableDynamicFilterEnablerIfPresent(sf, session);
            TransactionSynchronizationManager.bindResource(sf, new SessionHolder(session));
        }
    }

    private Session getSession() {
        return getSession(true);
    }

    private Session getSession(boolean allowCreate) {

        Object value = TransactionSynchronizationManager.getResource(getSessionFactory());
        if (value instanceof Session) {
            return (Session) value;
        }

        if (value instanceof SessionHolder) {
            SessionHolder sessionHolder = (SessionHolder) value;
            return sessionHolder.getSession();
        }

        if (allowCreate && hibernateDatastore != null) {
            return hibernateDatastore.openSession();
        }

        throw new IllegalStateException("No Hibernate Session bound to thread, and configuration does not allow creation of non-transactional one here");
    }

    /**
     * @return the sessionFactory
     */
    public SessionFactory getSessionFactory() {
        return hibernateDatastore.getSessionFactory();
    }

    public void setHibernateDatastore(AbstractHibernateDatastore hibernateDatastore) {
        this.hibernateDatastore = hibernateDatastore;
    }

    @Override
    public void setSessionFactory(SessionFactory sessionFactory) {
        // ignore
    }

    private int incNestingCount() {
        Map<String, Integer> map = nestingCount.get();
        Integer current = map.get(dataSourceName);
        int value = (current != null) ? current + 1 : 1;
        map.put(dataSourceName, value);
        return value;
    }

    private int decNestingCount() {
        Map<String, Integer> map = nestingCount.get();
        Integer current = map.get(dataSourceName);
        int value = (current != null) ? current - 1 : 0;
        if (value < 0) {
            value = 0;
        }
        map.put(dataSourceName, value);
        return value;
    }

    private void setParticipate(boolean flag) {
        Map<String, Boolean> map = participate.get();
        map.put(dataSourceName, flag);
    }

    private boolean getParticipate() {
        Map<String, Boolean> map = participate.get();
        Boolean ret = map.get(dataSourceName);
        return (ret != null) ? ret : false;
    }
}
