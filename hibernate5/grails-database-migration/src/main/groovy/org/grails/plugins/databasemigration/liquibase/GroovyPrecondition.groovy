/*
 * Copyright 2010-2013 SpringSource.
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
package org.grails.plugins.databasemigration.liquibase

import grails.config.Config
import grails.core.GrailsApplication
import groovy.sql.Sql
import groovy.transform.CompileStatic
import liquibase.CatalogAndSchema
import liquibase.changelog.ChangeSet
import liquibase.changelog.DatabaseChangeLog
import liquibase.changelog.visitor.ChangeExecListener
import liquibase.database.Database
import liquibase.database.DatabaseConnection
import liquibase.database.jvm.JdbcConnection
import liquibase.exception.DatabaseException
import liquibase.exception.PreconditionErrorException
import liquibase.exception.PreconditionFailedException
import liquibase.exception.ValidationErrors
import liquibase.exception.Warnings
import liquibase.parser.core.ParsedNode
import liquibase.parser.core.ParsedNodeException
import liquibase.precondition.AbstractPrecondition
import liquibase.resource.ResourceAccessor
import liquibase.snapshot.DatabaseSnapshot
import liquibase.snapshot.SnapshotControl
import liquibase.snapshot.SnapshotGeneratorFactory
import org.springframework.context.ApplicationContext

import java.sql.Connection

/**
 * Custom Groovy-based precondition.
 *
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 * @author Kazuki YAMAMOTO
 */
@CompileStatic
class GroovyPrecondition extends AbstractPrecondition {

    final String serializedObjectNamespace = STANDARD_CHANGELOG_NAMESPACE

    final String name = 'grailsPrecondition'

    Closure checkClosure

    Database database

    DatabaseChangeLog changeLog

    ChangeSet changeSet

    ResourceAccessor resourceAccessor

    ApplicationContext ctx

    Sql sql

    @Override
    void load(ParsedNode parsedNode, ResourceAccessor resourceAccessor) throws ParsedNodeException {
        this.resourceAccessor = resourceAccessor

        ctx = parsedNode.getChildValue(null, 'applicationContext', ApplicationContext)
        checkClosure = parsedNode.getChildValue(null, 'check', Closure)
        checkClosure?.setResolveStrategy(Closure.DELEGATE_FIRST)
    }

    @Override
    Warnings warn(Database database) {
        new Warnings()
    }

    @Override
    ValidationErrors validate(Database database) {
        new ValidationErrors()
    }

    @Override
    void check(Database database, DatabaseChangeLog changeLog, ChangeSet changeSet, ChangeExecListener changeExecListener) throws PreconditionFailedException, PreconditionErrorException {
        this.database = database
        this.changeLog = changeLog
        this.changeSet = changeSet

        if (!checkClosure) {
            return
        }

        checkClosure.delegate = this

        try {
            checkClosure()
        } catch (PreconditionFailedException e) {
            throw e
        } catch (AssertionError e) {
            throw new PreconditionFailedException(e.message, changeLog, this)
        } catch (Exception e) {
            throw new PreconditionErrorException(e, changeLog, this)
        }
    }

    /**
     * Called from the change or rollback closure. Creates a <code>Sql</code> instance from the current connection.
     *
     * @return the sql instance
     */
    Sql getSql() {
        if (!connection) {
            return null
        }

        if (!sql) {
            sql = new Sql(connection) {
                protected void closeResources(Connection c) {
                    // do nothing, let Liquibase close the connection
                }
            }
        }

        sql
    }

    /**
     * Called from the change or rollback closure. Shortcut to get the (wrapper) database connection.
     *
     * @return the connection or <code>null</code> if the database isn't set yet
     */
    DatabaseConnection getDatabaseConnection() {
        database?.connection
    }

    /**
     * Called from the change or rollback closure. Shortcut to get the real database connection.
     *
     * @return the connection or <code>null</code> if the database isn't set yet
     */
    Connection getConnection() {
        if (databaseConnection instanceof JdbcConnection) {
            return ((JdbcConnection) database.connection).underlyingConnection
        }
        return null
    }

    /**
     * Called from the change or rollback closure. Shortcut for the current application.
     *
     * @return the application
     */
    GrailsApplication getApplication() {
        ctx.getBean(GrailsApplication)
    }

    /**
     * Called from the change or rollback closure. Shortcut for the current config.
     *
     * @return the config
     */
    Config getConfig() {
        application.config
    }

    /**
     * Called from the check closure as a shortcut to throw a <code>PreconditionFailedException</code>.
     *
     * @param message the failure message
     */
    void fail(String message) {
        throw new PreconditionFailedException(message, changeLog, this)
    }

    /**
     * Called from the check closure.
     *
     * @param schemaName the schema name
     * @return a snapshot for the current database and schema name
     */
    DatabaseSnapshot createDatabaseSnapshot(String schemaName = null) {
        try {
            return SnapshotGeneratorFactory.instance.createSnapshot(new CatalogAndSchema(null, schemaName), database, new SnapshotControl(database))
        } catch (DatabaseException e) {
            throw new PreconditionErrorException(e, changeLog, this)
        }
    }
}
