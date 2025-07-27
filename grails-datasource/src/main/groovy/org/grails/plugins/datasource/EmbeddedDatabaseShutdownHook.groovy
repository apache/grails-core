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

package org.grails.plugins.datasource

import java.sql.Connection

import javax.sql.DataSource

import groovy.sql.Sql
import groovy.transform.CompileStatic

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

import org.springframework.beans.BeansException
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.SmartLifecycle

import org.grails.core.lifecycle.ShutdownOperations

@CompileStatic
class EmbeddedDatabaseShutdownHook implements SmartLifecycle, ApplicationContextAware {
    private static final Log log=LogFactory.getLog(this)
    private boolean running
    private ApplicationContext applicationContext
    private List<String> embeddedDatabaseBeanNames

    @Override
    void start() {
        embeddedDatabaseBeanNames = []
        applicationContext.getBeansOfType(DataSource).each { String beanName, DataSource dataSource ->
            if(isEmbeddedH2orHsqldb(dataSource)) {
                embeddedDatabaseBeanNames.add(beanName)
            }
        }
        running = true
    }

    @Override
    void stop() {
        embeddedDatabaseBeanNames?.each { String beanName ->
            shutdownEmbeddedDatabase(applicationContext.getBean(beanName, DataSource))
        }
        embeddedDatabaseBeanNames = []
        running = false
    }

    @Override
    boolean isRunning() {
        return running
    }

    @Override
    int getPhase() {
        return Integer.MIN_VALUE
    }

    @Override
    boolean isAutoStartup() {
        return true
    }

    @Override
    void stop(Runnable callback) {
        stop()
        callback.run()
    }

    @Override
    void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext
    }

    protected boolean isEmbeddedH2orHsqldb(DataSource dataSource) {
        MetaProperty urlProperty = dataSource.hasProperty("url")
        if (urlProperty) {
            String url = urlProperty.getProperty(dataSource)
            if(url && (url.startsWith('jdbc:h2:') || url.startsWith('jdbc:hsqldb:'))) {
                // don't shutdown remote servers
                if(!(url.startsWith('jdbc:hsqldb:h') || url.startsWith('jdbc:h2:tcp:') || url.startsWith('jdbc:h2:ssl:'))) {
                    return true
                }
            }
        }
        return false
    }

    protected shutdownEmbeddedDatabase(DataSource dataSource) {
        try {
            addShutdownOperation(dataSource.getConnection())
        } catch (e) {
            log.error "Error shutting down datasource", e
        }
    }

    protected addShutdownOperation(Connection connection) {
        // delay the operation until Grails Application is stopping and shutdown hooks are called
        ShutdownOperations.addOperation {
            try {
                Sql sql = new Sql(connection)
                sql.executeUpdate('SHUTDOWN')
            } catch (e) {
                // already closed, ignore
            } finally {
                try { connection?.close() } catch (ignored) {}
            }
        } as Runnable
    }
}
