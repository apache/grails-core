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
package org.apache.grails.data.neo4j.core

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.gorm.validation.PersistentEntityValidator
import org.apache.grails.data.testing.tck.base.GrailsDataTckManager
import org.grails.datastore.gorm.events.ConfigurableApplicationContextEventPublisher
import org.grails.datastore.gorm.neo4j.Neo4jDatastore
import org.grails.datastore.gorm.neo4j.config.Settings
import org.grails.datastore.gorm.validation.constraints.eval.DefaultConstraintEvaluator
import org.grails.datastore.gorm.validation.constraints.registry.DefaultConstraintRegistry
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.neo4j.driver.Driver
import org.neo4j.harness.ServerControls
import org.springframework.context.support.GenericApplicationContext
import org.springframework.context.support.StaticMessageSource
import org.springframework.validation.Validator

/**
 * TCK manager for the Neo4j GORM adapter.
 *
 * A single embedded Neo4j server is started once for the whole spec class (starting one
 * per test would be prohibitively slow). Domain classes aren't known until the concrete
 * spec's own {@code setupSpec} has registered them (it runs after this manager's), so the
 * {@link Neo4jDatastore} bound to that server is built lazily on the first {@link #createSession()}
 * call and then reused - only a fresh session is handed out per test - across the whole spec class.
 */
class GrailsDataNeo4jTckManager extends GrailsDataTckManager {

    private Neo4jDatastore bootstrapDatastore
    Neo4jDatastore neo4jDatastore
    ServerControls serverControls
    Driver boltDriver
    GrailsApplication grailsApplication
    MappingContext mappingContext
    Map<String, Object> configuration = [:]

    @Override
    void setupSpec() {
        bootstrapDatastore = new Neo4jDatastore(
                [(Settings.SETTING_NEO4J_TYPE)              : Settings.DATABASE_TYPE_EMBEDDED,
                 (Settings.SETTING_NEO4J_EMBEDDED_EPHEMERAL) : true,
                 'grails.neo4j.embedded.options.dbms.shell'  : 'true'],
                new Class[0]
        )
        serverControls = (ServerControls) bootstrapDatastore.connectionSources.defaultConnectionSource.serverControls
        boltDriver = bootstrapDatastore.boltDriver
    }

    @Override
    Session createSession() {
        if (neo4jDatastore == null) {
            def ctx = new GenericApplicationContext()
            ctx.refresh()
            Class[] allClasses = domainClasses

            neo4jDatastore = new Neo4jDatastore(
                    boltDriver,
                    DatastoreUtils.createPropertyResolver(configuration),
                    new ConfigurableApplicationContextEventPublisher(ctx),
                    allClasses
            )
            mappingContext = neo4jDatastore.mappingContext

            grailsApplication = new DefaultGrailsApplication(allClasses, getClass().classLoader)
            grailsApplication.mainContext = ctx
            grailsApplication.initialise()
        }

        Session newSession = neo4jDatastore.connect()
        newSession.beginTransaction()
        newSession
    }

    void setupValidator(Class entityClass, Validator validator = null) {
        PersistentEntity entity = mappingContext.persistentEntities.find { PersistentEntity e -> e.javaClass == entityClass }
        if (entity) {
            def messageSource = new StaticMessageSource()
            def evaluator = new DefaultConstraintEvaluator(new DefaultConstraintRegistry(messageSource), mappingContext, Collections.emptyMap())
            mappingContext.addEntityValidator(entity, validator ?:
                    new PersistentEntityValidator(entity, messageSource, evaluator))
        }
    }

    @Override
    void destroy() {
        // neo4jDatastore/grailsApplication/mappingContext are shared across every test in this
        // spec class (see createSession()) and must survive to the next test - only wipe the
        // graph data here. Closing neo4jDatastore would also close the shared boltDriver.
        try {
            boltDriver.session().withCloseable { session ->
                session.beginTransaction().withCloseable { tx ->
                    tx.run("MATCH (n) DETACH DELETE n")
                    tx.commit()
                }
            }
        } catch (e) {
            // latest driver throws a nonsensical error in some cases. Ignore it for the moment
            if (!e.message?.contains("insanely frequent schema changes")) {
                throw e
            }
        }

        super.destroy()
    }

    @Override
    void cleanupSpec() {
        neo4jDatastore?.close()
        neo4jDatastore = null
        grailsApplication = null
        mappingContext = null

        bootstrapDatastore?.close()
        bootstrapDatastore = null
        super.cleanupSpec()
    }
}
