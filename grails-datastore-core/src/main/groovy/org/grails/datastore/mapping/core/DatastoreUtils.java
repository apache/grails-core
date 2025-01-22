/* Copyright (C) 2010 SpringSource
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
package org.grails.datastore.mapping.core;

import groovy.lang.Closure;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import groovy.util.ConfigObject;
import groovy.util.ConfigSlurper;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.grails.datastore.mapping.config.DatastoreEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NamedThreadLocal;
import org.springframework.core.convert.ConversionException;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertyResolver;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.dao.DataAccessResourceFailureException;
import org.grails.datastore.mapping.transactions.SessionHolder;
import org.grails.datastore.mapping.transactions.support.SpringSessionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * Helper class for obtaining Datastore sessions. Based on similar work
 * for Hibernate such as SessionFactoryUtils
 *
 * @author Juergen Hoeller
 * @author Graeme Rocher
 */
@SuppressWarnings("rawtypes")
public abstract class DatastoreUtils {

    private static final Logger logger = LoggerFactory.getLogger(DatastoreUtils.class);
    private static final String DATASTORE_CONFIG_FLAT = "datastoreConfigFlat";
    private static final ThreadLocal<Map<Datastore, Set<Session>>> deferredCloseHolder =
            new NamedThreadLocal<Map<Datastore, Set<Session>>>(
                    "Datastore Sessions registered for deferred close");
    private static final String DATASTORE_CONFIG = "datastoreConfig";

    /**
     * Get a Datastore Session for the given Datastore. Is aware of and will
     * return any existing corresponding Session bound to the current thread, for
     * example when using {@link org.grails.datastore.mapping.transactions.DatastoreTransactionManager}. Will create a new
     * Session otherwise, if "allowCreate" is <code>true</code>.
     * <p>This is the <code>getSession</code> method used by typical data access code,
     * in combination with <code>releaseSession</code> called when done with
     * the Session.
     *
     * @param datastore Datastore to create the session with
     * @param allowCreate whether a non-transactional Session should be created
     * when no transactional Session can be found for the current thread
     * @return the Datastore Session
     * @throws org.springframework.dao.DataAccessResourceFailureException if the Session couldn't be created
     * @throws IllegalStateException if no thread-bound Session found and
     * "allowCreate" is <code>false</code>
     */
    public static Session getSession(Datastore datastore, boolean allowCreate)
            throws DataAccessResourceFailureException, IllegalStateException {

        try {
            return doGetSession(datastore, allowCreate);
        }
        catch (Exception ex) {
            throw new DataAccessResourceFailureException("Could not open Datastore Session", ex);
        }
    }

    /**
     * Get a Datastore Session for the given Datastore. Is aware of and will
     * return any existing corresponding Session bound to the current thread, for
     * example when using {@link org.grails.datastore.mapping.transactions.DatastoreTransactionManager}. Will create a new
     * Session otherwise, if "allowCreate" is <code>true</code>.
     *
     * @param datastore Datastore to create the session with
     * Session on transaction synchronization (may be <code>null</code>)
     * @param allowCreate whether a non-transactional Session should be created
     * when no transactional Session can be found for the current thread
     * @return the Datastore Session
     * @throws IllegalStateException if no thread-bound Session found and
     * "allowCreate" is <code>false</code>
     */
    public static Session doGetSession(Datastore datastore, boolean allowCreate) {

        Assert.notNull(datastore, "No Datastore specified");

        SessionHolder sessionHolder = (SessionHolder) TransactionSynchronizationManager.getResource(datastore);

        if (sessionHolder != null && !sessionHolder.isEmpty()) {
            // pre-bound Datastore Session
            Session session;
            if (TransactionSynchronizationManager.isSynchronizationActive() &&
                    sessionHolder.doesNotHoldNonDefaultSession()) {
                // Spring transaction management is active ->
                // register pre-bound Session with it for transactional flushing.
                session = sessionHolder.getValidatedSession();
                if (session != null && !sessionHolder.isSynchronizedWithTransaction()) {
                    if(logger.isDebugEnabled()) {
                        logger.debug("Registering Spring transaction synchronization for existing Datastore Session");
                    }
                    TransactionSynchronizationManager.registerSynchronization(
                            new SpringSessionSynchronization(sessionHolder, datastore, false));
                    sessionHolder.setSynchronizedWithTransaction(true);

                }
                if (session != null) {
                    return session;
                }
            }
            else {
                session = sessionHolder.getValidatedSession();
                if (session != null) {
                    return session;
                }
            }
        }

        if(logger.isDebugEnabled()) {
            logger.debug("Opening Datastore Session");
        }
        Session session = datastore.connect();

        // Use same Session for further Datastore actions within the transaction.
        // Thread object will get removed by synchronization at transaction completion.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // We're within a Spring-managed transaction, possibly from JtaTransactionManager.
            if(logger.isDebugEnabled()) {
                logger.debug("Registering Spring transaction synchronization for new Datastore Session");
            }
            SessionHolder holderToUse = sessionHolder;
            if (holderToUse == null) {
                holderToUse = new SessionHolder(session);
            }
            else {
                holderToUse.addSession(session);
            }
            TransactionSynchronizationManager.registerSynchronization(
                    new SpringSessionSynchronization(holderToUse, datastore, true));
            holderToUse.setSynchronizedWithTransaction(true);
            if (holderToUse != sessionHolder) {
                TransactionSynchronizationManager.bindResource(datastore, holderToUse);
            }
        }

        // Check whether we are allowed to return the Session.
        if (!allowCreate && !isSessionTransactional(session, datastore)) {
            closeSession(session);
            throw new IllegalStateException("No Datastore Session bound to thread, " +
                "and configuration does not allow creation of non-transactional one here");
        }

        return session;
    }

    /**
     * Return whether the given Datastore Session is transactional, that is,
     * bound to the current thread by Spring's transaction facilities.
     * @param session the Datastore Session to check
     * @param datastore Datastore that the Session was created with
     * (may be <code>null</code>)
     * @return whether the Session is transactional
     */
    public static boolean isSessionTransactional(Session session, Datastore datastore) {
        if (datastore == null) {
            return false;
        }
        SessionHolder sessionHolder =
                (SessionHolder) TransactionSynchronizationManager.getResource(datastore);
        return (sessionHolder != null && sessionHolder.containsSession(session));
    }

    /**
     * Perform actual closing of the Session,
     * catching and logging any cleanup exceptions thrown.
     * @param session The Session instance
     */
    public static void closeSession(Session session) {
        if (session == null) {
            return;
        }

        logger.debug("Closing Datastore Session");
        try {
            session.disconnect();
        }
        catch (Throwable ex) {
            logger.debug("Unexpected exception on closing Datastore Session", ex);
        }
    }

    /**
     * Close the given Session, created via the given factory,
     * if it is not managed externally (i.e. not bound to the thread).
     * @param session the Datastore Session to close (may be <code>null</code>)
     * @param datastore Datastore that the Session was created with
     * (may be <code>null</code>)
     */
    public static void releaseSession(Session session, Datastore datastore) {
        if (session == null) {
            return;
        }
        // Only close non-transactional Sessions.
        if (!isSessionTransactional(session, datastore)) {
            closeSessionOrRegisterDeferredClose(session, datastore);
        }
    }

    /**
     * Process all Datastore Sessions that have been registered for deferred close
     * for the given SessionFactory.
     * @param datastore the Datastore to process deferred close for
     * @see #initDeferredClose
     * @see #releaseSession
     */
    public static void processDeferredClose(Datastore datastore) {
        Assert.notNull(datastore, "No Datastore specified");
        Map<Datastore, Set<Session>> holderMap = deferredCloseHolder.get();
        if (holderMap == null || !holderMap.containsKey(datastore)) {
            throw new IllegalStateException("Deferred close not active for Datastore [" + datastore + "]");
        }
        if(logger.isDebugEnabled()) {
            logger.debug("Processing deferred close of Datastore Sessions");
        }
        Set<Session> sessions = holderMap.remove(datastore);
        for (Session session : sessions) {
            closeSession(session);
        }
        if (holderMap.isEmpty()) {
            deferredCloseHolder.set(null);
        }
    }

    /**
     * Initialize deferred close for the current thread and the given Datastore.
     * Sessions will not be actually closed on close calls then, but rather at a
     * {@link #processDeferredClose} call at a finishing point (like request completion).
     *
     * @param datastore the Datastore to initialize deferred close for
     * @see #processDeferredClose
     * @see #releaseSession
     */
    public static void initDeferredClose(Datastore datastore) {
        Assert.notNull(datastore, "No Datastore specified");
        logger.debug("Initializing deferred close of Datastore Sessions");
        Map<Datastore, Set<Session>> holderMap = deferredCloseHolder.get();
        if (holderMap == null) {
            holderMap = new HashMap<Datastore, Set<Session>>();
            deferredCloseHolder.set(holderMap);
        }
        holderMap.put(datastore, new LinkedHashSet<Session>(4));
    }

    /**
     * Close the given Session or register it for deferred close.
     * @param session the Datastore Session to close
     * @param datastore Datastore that the Session was created with
     * (may be <code>null</code>)
     * @see #initDeferredClose
     * @see #processDeferredClose
     */
    public static void closeSessionOrRegisterDeferredClose(Session session, Datastore datastore) {
        Map<Datastore, Set<Session>> holderMap = deferredCloseHolder.get();
        if (holderMap != null && datastore != null && holderMap.containsKey(datastore)) {
            logger.debug("Registering Datastore Session for deferred close");
            holderMap.get(datastore).add(session);
        }
        else {
            closeSession(session);
        }
    }

    /**
     * Execute the closure in the current session if it exists, or create a new one and close it otherwise.
     * @param datastore the datastore
     * @param c the closure to execute
     * @return the return value from the closure
     */
    public static Object doWithSession(final Datastore datastore, final Closure c) {
        boolean existing = datastore.hasCurrentSession();
        Session session = existing ? datastore.getCurrentSession() : bindSession(datastore.connect());
        try {
            return c.call(session);
        }
        finally {
            if (!existing) {
                TransactionSynchronizationManager.unbindResource(session.getDatastore());
                closeSessionOrRegisterDeferredClose(session, datastore);
            }
        }
    }

    /**
     * Execute the callback in the current session if it exists, or create a new one and close it otherwise.
     * @param <T> the return type
     * @param datastore the datastore
     * @param callback the callback to execute
     * @return the return value from the callback
     */
    public static <T> T execute(final Datastore datastore, final SessionCallback<T> callback) {
        boolean existing = datastore.hasCurrentSession();
        Session session = existing ? datastore.getCurrentSession() : bindSession(datastore.connect());
        try {
            return callback.doInSession(session);
        }
        finally {
            if (!existing) {
                TransactionSynchronizationManager.unbindResource(session.getDatastore());
                closeSessionOrRegisterDeferredClose(session, datastore);
            }
        }
    }

    /**
     * Execute the callback in the current session if it exists, or create a new one and close it otherwise.
     * @param datastore the datastore
     * @param callback the callback to execute
     */
    public static void execute(final Datastore datastore, final VoidSessionCallback callback) {
        boolean existing = datastore.hasCurrentSession();
        Session session = existing ? datastore.getCurrentSession() : bindSession(datastore.connect());
        try {
            callback.doInSession(session);
        }
        finally {
            if (!existing) {
                TransactionSynchronizationManager.unbindResource(datastore);
                closeSessionOrRegisterDeferredClose(session, datastore);
            }
        }
    }

    /**
     * Bind the session to the thread with a SessionHolder keyed by its Datastore.
     * @param session the session
     * @return the session (for method chaining)
     */
    public static Session bindSession(final Session session) {
        TransactionSynchronizationManager.bindResource(session.getDatastore(), new SessionHolder(session));
        return session;
    }

    /**
     * Bind the session to the thread with a SessionHolder keyed by its Datastore.
     * @param session the session
     * @return the session (for method chaining)
     */
    public static Session bindSession(final Session session, Object creator) {
        TransactionSynchronizationManager.bindResource(session.getDatastore(), new SessionHolder(session, creator));
        return session;
    }

    /**
     * Adds the session to the current SessionHolder's list of sessions, making it the current session.
     * If there's no current session, calls bindSession.
     * @param session the session
     * @return the session
     */
    public static Session bindNewSession(final Session session) {
        SessionHolder sessionHolder = (SessionHolder)TransactionSynchronizationManager.getResource(session.getDatastore());
        if (sessionHolder == null) {
            return bindSession(session);
        }

        sessionHolder.addSession(session);
        return session;
    }

    /**
     * Unbinds and closes a session. If it's the only session in the SessionHolder, unbinds
     * the SessionHolder, otherwise just removes the session from the holder's list.
     * @param session the session
     */
    public static void unbindSession(final Session session) {
        SessionHolder sessionHolder = (SessionHolder)TransactionSynchronizationManager.getResource(session.getDatastore());
        if (sessionHolder == null) {
            if(logger.isWarnEnabled()) {
                logger.warn("Cannot unbind session, there's no SessionHolder registered");
            }
            return;
        }

        if (!sessionHolder.containsSession(session)) {
            if(logger.isWarnEnabled()) {
                logger.warn("Cannot unbind session, it's not registered in a SessionHolder");
            }
            return;
        }

        if (sessionHolder.size() > 1) {
            sessionHolder.removeSession(session);
        }
        else {
          TransactionSynchronizationManager.unbindResource(session.getDatastore());
        }

        closeSessionOrRegisterDeferredClose(session, session.getDatastore());
    }

    /**
     * Prepares a property resolver and ensures it is suitable for GORM configuration
     *
     * @param propertyResolver The property resolver
     * @return The property resolver
     */
    public static PropertyResolver preparePropertyResolver(PropertyResolver propertyResolver) {
        return preparePropertyResolver(propertyResolver, "grails");
    }

    /**
     * Prepares a property resolver and ensures it is suitable for GORM configuration
     *
     * @param propertyResolver The property resolver
     * @param keyPrefixes The configuration prefixes
     * @return The property resolver
     */
    public static PropertyResolver preparePropertyResolver(PropertyResolver propertyResolver, String...keyPrefixes) {
        // if it is a map then it is a Grails configuration object, therefore it is already prepared
        if((propertyResolver instanceof Map) || (propertyResolver instanceof DatastoreEnvironment)) {
            return propertyResolver;
        }
        else if(propertyResolver instanceof ConfigurableEnvironment) {
            // Spring Boot Environment config
            ConfigurableEnvironment env = (ConfigurableEnvironment) propertyResolver;
            List<PropertySource<?>> propertySources = DefaultGroovyMethods.asList(env.getPropertySources());
            Collections.reverse(propertySources);
            Map<String, Object> configuration = new LinkedHashMap<>();
            for (PropertySource<?> propertySource : propertySources) {
                if(propertySource instanceof EnumerablePropertySource) {
                    EnumerablePropertySource eps = (EnumerablePropertySource) propertySource;
                    String[] propertyNames = eps.getPropertyNames();
                    for (String propertyName : propertyNames) {
                        for (String keyPrefix : keyPrefixes) {
                            if(propertyName.startsWith(keyPrefix)) {
                                configuration.put(propertyName, eps.getProperty(propertyName));
                                break;
                            }
                        }
                    }
                }
            }
            return createPropertyResolver(configuration);
        }
        else {
            return propertyResolver;
        }
    }
    /**
     * Creates a {@link PropertyResolver} from the given configuration
     * @param configuration The configuration
     *
     * @return A {@link PropertyResolver} instance
     */
    public static PropertyResolver createPropertyResolver(Map<String, Object> configuration) {
        if(configuration instanceof PropertyResolver) {
            return (PropertyResolver)configuration;
        }
        else {

            Map<String, Object>[] configurations = new Map[1];
            configurations[0] = configuration;
            return createPropertyResolvers(configurations);
        }
    }

    /**
     * Creates a {@link PropertyResolver} from the given configuration
     * @param configurations The configuration
     *
     * @return A {@link PropertyResolver} instance
     */
    public static PropertyResolver createPropertyResolvers(Map<String, Object>... configurations) {
        return createPropertyResolvers(Arrays.asList(configurations));
    }

    /**
     * Creates a {@link PropertyResolver} from the given configuration
     * @param configurations The configuration
     *
     * @return A {@link PropertyResolver} instance
     */
    public static PropertyResolver createPropertyResolvers(Collection<Map<String, Object>> configurations) {
        DatastoreEnvironment env = new DatastoreEnvironment();
        env.getConversionService().addConverter(new Converter<String, Class>() {
            @Override
            public Class convert(String source) {
                try {
                    return ClassUtils.forName(source, getClass().getClassLoader());
                } catch (ClassNotFoundException e) {
                    throw new ConversionException("Cannot convert String ["+source+"] to class. The class was not found.", e) {};
                }
            }
        });
        env.getConversionService().addConverter(new Converter<String, Resource>() {
            @Override
            public Resource convert(String source) {
                return new PathMatchingResourcePatternResolver().getResource(source);
            }
        });
        int i = 0;
        for (Map<String, Object> configuration : configurations) {
            i++;
            if(configuration != null) {
                MutablePropertySources propertySources = env.getPropertySources();

                if(configuration instanceof ConfigObject) {
                    ConfigObject existingConfig = (ConfigObject) configuration;
                    ConfigObject cloned = existingConfig.clone();
                    propertySources.addFirst(new ConfigObjectPropertySource(DATASTORE_CONFIG + i, cloned));
                    propertySources.addFirst(new ConfigObjectPropertySource(DATASTORE_CONFIG_FLAT+ i, createFlatConfig(cloned)));
                }
                else {
                    ConfigSlurper configSlurper = new ConfigSlurper();
                    Properties properties = new Properties();
                    Map<String, Object> finalConfig = new LinkedHashMap<>();
                    for (String name : configuration.keySet()) {
                        Object value = configuration.get(name);
                        if(value != null) {
                            finalConfig.put(name, value);
                        }
                    }
                    properties.putAll(finalConfig);
                    ConfigObject configObject = configSlurper.parse(properties);
                    ConfigObject flatConfigObject = new ConfigObject();
                    configObject.flatten(flatConfigObject);
                    flatConfigObject.merge(configObject);
                    propertySources.addFirst(new ConfigObjectPropertySource(DATASTORE_CONFIG + i, configObject));
                    propertySources.addFirst(new ConfigObjectPropertySource(DATASTORE_CONFIG_FLAT + i, createFlatConfig(flatConfigObject)));
                }
            }
        }

        return env;
    }
    private static Map<String, Object> createFlatConfig(ConfigObject configuration) {
        Map<String, Object> flatConfig = new LinkedHashMap<>();
        String prefix = "";

        createFlatConfig(configuration, flatConfig, prefix);
        return flatConfig;
    }

    private static void createFlatConfig(ConfigObject currentConfigObject, Map<String, Object> rootConfig, String prefix) {
        Set keySet = currentConfigObject.keySet();
        for (Object key : keySet) {
            Object value = currentConfigObject.get(key);
            if(value instanceof ConfigObject) {
                ConfigObject sub = ((ConfigObject) value).clone();
                String fullPath = prefix + key;
                if(!sub.isEmpty()) {
                    Map flattened = sub.flatten();
                    sub.putAll(flattened);
                    createFlatConfig(sub, rootConfig, fullPath + ".");
                    if(!rootConfig.containsKey(fullPath)) {
                        rootConfig.put(fullPath, sub);
                    }
                }
            }
            else {
                rootConfig.put(prefix + key, value);
            }
        }
    }

    private static class ConfigObjectPropertySource extends MapPropertySource {
        public ConfigObjectPropertySource(String id, Map configObject) {
            super(id, configObject);
        }

        @Override
        public Object getProperty(String name) {
            Object value = super.getProperty(name);
            if(value instanceof Map) {
                Map map = (Map) value;
                if(map.isEmpty()) {
                    return null;
                }
                else {
                    Map newMap = new LinkedHashMap();
                    for (Object key : map.keySet()) {
                        Object v = map.get(key);
                        if( !(v instanceof ConfigObject)) {
                            newMap.put(key, v);
                        }

                    }
                    return newMap;
                }
            }
            return value;
        }
    }
}
