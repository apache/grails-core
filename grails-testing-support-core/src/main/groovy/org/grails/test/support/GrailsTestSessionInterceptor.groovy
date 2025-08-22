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
package org.grails.test.support

import grails.testing.mixin.integration.WithSession
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.hibernate.FlushMode
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.springframework.context.ApplicationContext
import org.springframework.orm.hibernate5.SessionHolder
import org.springframework.transaction.support.TransactionSynchronizationManager
import groovy.transform.CompileStatic
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Binds Hibernate sessions to the test thread without starting transactions.
 * This mimics the OISV (Open Session In View) pattern used in running applications.
 */
@CompileStatic
class GrailsTestSessionInterceptor {
    
    private static final Logger LOG = LoggerFactory.getLogger(GrailsTestSessionInterceptor)
    private static final String WITH_SESSION = "withSession"
    
    private final ApplicationContext applicationContext
    private final Map<String, SessionFactory> sessionFactories = [:]
    private final Map<String, SessionHolder> sessionHolders = [:]
    private final Set<String> datasourcesToBind = [] as Set
    
    GrailsTestSessionInterceptor(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext
        initializeSessionFactories()
    }
    
    private void initializeSessionFactories() {
        def datasourceNames = []
        
        if (applicationContext.containsBean('sessionFactory')) {
            datasourceNames << ConnectionSource.DEFAULT
        }
        
        // Check for additional datasources by looking for sessionFactory beans
        String[] beanNames = applicationContext.getBeanNamesForType(SessionFactory)
        for (String beanName : beanNames) {
            if (beanName.startsWith('sessionFactory_')) {
                String datasourceName = beanName.substring('sessionFactory_'.length())
                datasourceNames << datasourceName
            }
        }
        
        for (datasourceName in datasourceNames) {
            boolean isDefault = datasourceName == ConnectionSource.DEFAULT
            String suffix = isDefault ? '' : '_' + datasourceName
            String beanName = "sessionFactory$suffix"
            
            if (applicationContext.containsBean(beanName)) {
                sessionFactories[datasourceName] = applicationContext.getBean(beanName, SessionFactory)
            }
        }
    }
    
    /**
     * Determines if sessions should be bound for the given test instance.
     */
    boolean shouldBindSessions(Object test) {
        if (!test) return false
        
        // Check for class-level annotation
        WithSession classAnnotation = test.class.getAnnotation(WithSession)
        if (classAnnotation) {
            configureDatasources(classAnnotation)
            return true
        }
        
        // Check for property-based configuration
        def value = null
        if (test instanceof Map) {
            value = test[WITH_SESSION]
        } else if (test.metaClass.hasProperty(test, WITH_SESSION)) {
            value = test[WITH_SESSION]
        }
        
        if (value instanceof Boolean && value) {
            // Bind sessions for all datasources if withSession = true
            datasourcesToBind.addAll(sessionFactories.keySet())
            return true
        } else if (value instanceof List) {
            // Bind sessions for specific datasources
            datasourcesToBind.addAll(value as List<String>)
            return true
        }
        
        return false
    }
    
    private void configureDatasources(WithSession annotation) {
        if (annotation.datasources().length > 0) {
            datasourcesToBind.addAll(annotation.datasources())
        } else {
            // If no specific datasources specified, bind all
            datasourcesToBind.addAll(sessionFactories.keySet())
        }
    }
    
    /**
     * Binds Hibernate sessions without transactions.
     */
    void init() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.initSynchronization()
        }
        
        for (datasourceName in datasourcesToBind) {
            SessionFactory sessionFactory = sessionFactories[datasourceName]
            if (!sessionFactory) {
                LOG.warn("SessionFactory not found for datasource: $datasourceName")
                continue
            }
            
            if (TransactionSynchronizationManager.hasResource(sessionFactory)) {
                LOG.debug("Session already bound for datasource: $datasourceName")
                continue
            }
            
            Session session = sessionFactory.openSession()
            // Set flush mode to MANUAL to match OISV behavior
            session.flushMode = FlushMode.MANUAL
            
            SessionHolder holder = new SessionHolder(session)
            TransactionSynchronizationManager.bindResource(sessionFactory, holder)
            sessionHolders[datasourceName] = holder
            
            LOG.debug("Bound Hibernate session for datasource: $datasourceName")
        }
    }
    
    /**
     * Unbinds and closes the sessions.
     */
    void destroy() {
        for (Map.Entry<String, SessionHolder> entry : sessionHolders.entrySet()) {
            String datasourceName = entry.key
            SessionHolder holder = entry.value
            SessionFactory sessionFactory = sessionFactories[datasourceName]
            
            if (sessionFactory && TransactionSynchronizationManager.hasResource(sessionFactory)) {
                TransactionSynchronizationManager.unbindResource(sessionFactory)
                
                try {
                    Session session = holder.session
                    if (session?.isOpen()) {
                        session.close()
                    }
                    LOG.debug("Closed Hibernate session for datasource: $datasourceName")
                } catch (Exception e) {
                    LOG.error("Error closing Hibernate session for datasource: $datasourceName", e)
                }
            }
        }
        
        sessionHolders.clear()
        datasourcesToBind.clear()
        
        if (TransactionSynchronizationManager.isSynchronizationActive() && 
            TransactionSynchronizationManager.resourceMap.isEmpty()) {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }
}