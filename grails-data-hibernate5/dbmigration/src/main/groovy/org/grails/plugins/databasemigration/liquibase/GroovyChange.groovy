/*
 * Copyright 2010-2013 SpringSource.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
package org.grails.plugins.databasemigration.liquibase

import grails.config.Config
import grails.core.GrailsApplication
import groovy.sql.Sql
import groovy.transform.CompileStatic
import liquibase.Scope
import liquibase.change.AbstractChange
import liquibase.change.ChangeMetaData
import liquibase.change.CheckSum
import liquibase.change.DatabaseChange
import liquibase.database.Database
import liquibase.database.DatabaseConnection
import liquibase.database.jvm.JdbcConnection
import liquibase.exception.RollbackImpossibleException
import liquibase.exception.SetupException
import liquibase.exception.ValidationErrors
import liquibase.exception.Warnings
import liquibase.executor.ExecutorService
import liquibase.executor.LoggingExecutor
import liquibase.parser.core.ParsedNode
import liquibase.parser.core.ParsedNodeException
import liquibase.resource.ResourceAccessor
import liquibase.statement.SqlStatement
import org.grails.plugins.databasemigration.DatabaseMigrationTransactionManager
import org.springframework.context.ApplicationContext

import java.sql.Connection

import static org.grails.plugins.databasemigration.PluginConstants.DATA_SOURCE_NAME_KEY

/**
 * Custom Groovy-based change.
 *
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 * @author Kazuki YAMAMOTO
 */
@CompileStatic
@DatabaseChange(name = "grailsChange", description = "Executes groovy code to apply a database change.", priority = ChangeMetaData.PRIORITY_DEFAULT)
class GroovyChange extends AbstractChange {

    ApplicationContext ctx

    String dataSourceName

    Closure initClosure

    Closure validateClosure

    Closure changeClosure

    Closure rollbackClosure

    String confirmationMessage

    String checksumString

    Database database

    Sql sql

    ValidationErrors validationErrors = new ValidationErrors()

    Warnings warnings = new Warnings()

    List allStatements = []

    boolean initClosureCalled

    boolean validateClosureCalled

    boolean changeClosureCalled

    @Override
    void load(ParsedNode parsedNode, ResourceAccessor resourceAccessor) throws ParsedNodeException {
        ctx = parsedNode.getChildValue(null, 'applicationContext', ApplicationContext)
        dataSourceName = parsedNode.getChildValue(null, DATA_SOURCE_NAME_KEY, String)
        if (dataSourceName?.startsWith("dataSource_")) {
            dataSourceName = dataSourceName.substring("dataSource_".length())
        }

        initClosure = parsedNode.getChildValue(null, 'init', Closure)
        initClosure?.setResolveStrategy(Closure.DELEGATE_FIRST)

        validateClosure = parsedNode.getChildValue(null, 'validate', Closure)
        validateClosure?.setResolveStrategy(Closure.DELEGATE_FIRST)

        changeClosure = parsedNode.getChildValue(null, 'change', Closure)
        changeClosure?.setResolveStrategy(Closure.DELEGATE_FIRST)

        rollbackClosure = parsedNode.getChildValue(null, 'rollback', Closure)
        rollbackClosure?.setResolveStrategy(Closure.DELEGATE_FIRST)

        confirmationMessage = parsedNode.getChildValue(null, 'confirm', String)
        checksumString = parsedNode.getChildValue(null, 'checksum', String)
    }

    @Override
    void finishInitialization() throws SetupException {
        if (!initClosure || initClosureCalled) {
            return
        }

        initClosure.delegate = this
        try {
            initClosure()
        } catch (Exception e) {
            throw new SetupException(e)
        } finally {
            initClosureCalled = true
        }
    }

    @Override
    ValidationErrors validate(Database database) {
        this.database = database

        if (!validateClosure || validateClosureCalled || !shouldRun()) {
            return validationErrors
        }

        validateClosure.delegate = this
        try {
            validateClosure()
        } finally {
            validateClosureCalled = true
        }

        return validationErrors
    }

    @Override
    Warnings warn(Database database) {
        validate(database)
        warnings
    }

    @Override
    SqlStatement[] generateStatements(Database database) {
        this.database = database

        if (shouldRun() && changeClosure) {
            changeClosure.delegate = this
            try {
                if(!changeClosureCalled) {
                    withNewTransaction(changeClosure)
                }
            } finally {
                changeClosureCalled = true
            }
        }

        allStatements as SqlStatement[]
    }

    @Override
    SqlStatement[] generateRollbackStatements(Database database) throws RollbackImpossibleException {
        this.database = database

        if (shouldRun() && rollbackClosure) {
            rollbackClosure.delegate = this
            rollbackClosure()
        }

        allStatements as SqlStatement[]
    }

    @Override
    String getConfirmationMessage() {
        confirmationMessage ?: 'Executed GrailsChange'
    }

    @Override
    CheckSum generateCheckSum() {
        CheckSum.compute checksumString ?: 'Grails Change'
    }

    @Override
    boolean supportsRollback(Database database) {
        this.database = database
        shouldRun()
    }

    /**
     * Called by the validate closure. Adds a validation error.
     *
     * @param message the error message
     */
    void error(String message) {
        validationErrors.addError message
    }

    /**
     * Called by the validate closure. Adds a warning message.
     *
     * @param warning the warning message
     */
    void warn(String warning) {
        warnings.addWarning warning
    }

    /**
     * Called by the change or rollback closure. Adds a statement to be executed.
     *
     * @param statement the statement
     */
    void sqlStatement(SqlStatement statement) {
        if (statement) {
            allStatements << statement
        }
    }

    /**
     * Called by the change or rollback closure. Adds multiple statements to be executed.
     *
     * @param statement the statement
     */
    void sqlStatements(List statements) {
        if (statements) {
            allStatements.addAll(statements as List)
        }
    }

    /**
     * Called by the change or rollback closure. Overrides the confirmation message.
     *
     * @param message the confirmation message
     */
    void confirm(String message) {
        confirmationMessage = message
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
     *
     * @return Whether the database executor is instance of LoggingExecutor
     */
    protected boolean shouldRun() {
        !(Scope.getCurrentScope().getSingleton(ExecutorService.class).getExecutor("jdbc", database) instanceof LoggingExecutor)
    }

    /**
     * Executes the grailsChange>change block within the context of a new transaction
     *
     * @param callable The changeClosure to call
     * @return The result of the closure execution
     */
    protected void withNewTransaction(Closure callable) {
        new DatabaseMigrationTransactionManager(ctx, dataSourceName)
                .withNewTransaction callable
    }
}
