package org.grails.plugins.databasemigration.liquibase

import grails.core.GrailsApplication
import liquibase.exception.CommandExecutionException
import org.grails.plugins.databasemigration.command.ApplicationContextDatabaseMigrationCommandSpec
import org.grails.plugins.databasemigration.command.DbmChangelogSyncCommand
import org.grails.plugins.databasemigration.command.DbmRollbackCommand
import org.grails.plugins.databasemigration.command.DbmTagCommand
import org.grails.plugins.databasemigration.command.DbmUpdateCommand
import org.grails.plugins.databasemigration.command.DbmUpdateCountCommand

class GroovyChangeLogSpec extends ApplicationContextDatabaseMigrationCommandSpec {

    static List<String> calledBlocks

    def setup() {
        calledBlocks = []
        Locale.setDefault(new Locale("en", "US"))
    }

    def "updates a database with Groovy Change"() {
        given:
        def command = createCommand(DbmUpdateCommand)
        command.changeLogFile << """
databaseChangeLog = {
    changeSet(author: "John Smith", id: "1") {
        grailsChange {
            init { ${GroovyChangeLogSpec.name}.calledBlocks << 'init' }
            validate { ${GroovyChangeLogSpec.name}.calledBlocks << 'validate' }
            change { ${GroovyChangeLogSpec.name}.calledBlocks << 'change' }
            rollback { ${GroovyChangeLogSpec.name}.calledBlocks << 'rollback' }
            confirm 'confirmation message'
            checkSum 'override value for checksum'
        }
    }
}
"""
        when:
        command.handle(getExecutionContext(DbmUpdateCommand))

        then:
        calledBlocks == ['init', 'validate', 'change']
        output.toString().contains('confirmation message')
    }


    def "outputs a warning message by calling the warn method"() {
        given:
        def command = createCommand(DbmUpdateCommand)
        command.changeLogFile << """
databaseChangeLog = {
    changeSet(author: "John Smith", id: "2") {
        grailsChange {
            validate {
                ${GroovyChangeLogSpec.name}.calledBlocks << 'validate'
                warn('warn message')
            }
            change {
                ${GroovyChangeLogSpec.name}.calledBlocks << 'change'
            }
        }
    }
}
"""
        when:
        command.handle(getExecutionContext(DbmUpdateCommand))

        then:
        output.toString().contains('warn message')
        calledBlocks == ['validate', 'change']
    }

    def "stops processing by calling the error method"() {
        given:
        DbmUpdateCommand command = (DbmUpdateCommand) createCommand(DbmUpdateCommand)
        command.changeLogFile << """
databaseChangeLog = {
    changeSet(author: "John Smith", id: "1") {
        grailsChange {
            validate {
                ${GroovyChangeLogSpec.name}.calledBlocks << 'validate'
                error('error message')
            }
            change {
                ${GroovyChangeLogSpec.name}.calledBlocks << 'change'
            }
        }
    }
}
"""
        when:
        command.handle(getExecutionContext(DbmUpdateCommand))

        then:
        def e = thrown(CommandExecutionException)

        e.message.contains('1 changes have validation failures')
        e.message.contains('error message, changelog.groovy::1::John Smith')
        calledBlocks == ['validate']
    }


    def "can use bind variables in the change block"() {
        given:
        def command = createCommand(DbmUpdateCommand)
        command.changeLogFile << """
databaseChangeLog = {
    changeSet(author: "John Smith", id: "4") {
        grailsChange {
            change {
                assert changeSet.id == '4'
                assert resourceAccessor.toString().startsWith('CompositeResourceAccessor{')
                assert ctx.hashCode() == ${applicationContext.hashCode()}
                assert application.hashCode() == ${applicationContext.getBean(GrailsApplication).hashCode()}
                ${GroovyChangeLogSpec.name}.calledBlocks << 'change'
            }
        }
    }
}
"""
        when:
        command.handle(getExecutionContext(DbmUpdateCommand))

        then:
        calledBlocks == ['change']
    }


    def "executes sql statements in the change block"() {
        given:
        def command = createCommand(DbmUpdateCommand)
        command.changeLogFile << """
import groovy.sql.Sql
import liquibase.statement.core.InsertStatement

databaseChangeLog = {
    changeSet(author: "John Smith", id: "5") {
        grailsChange {
            change {
                new Sql(database.connection.underlyingConnection).executeUpdate('CREATE TABLE book (id INT)')
                new Sql(databaseConnection.underlyingConnection).executeUpdate('INSERT INTO book (id) VALUES (1)')
                new Sql(connection).executeUpdate('INSERT INTO book (id) VALUES (2)')
                sqlStatement(new InsertStatement(null, null, 'book').addColumnValue('id', 3))
                sqlStatements([new InsertStatement(null, null, 'book').addColumnValue('id', 4), new InsertStatement(null, null, 'book').addColumnValue('id', 5)])
            }
        }
    }
}
"""

        when:
        command.handle(getExecutionContext(DbmUpdateCommand))

        then:
        sql.rows('SELECT id FROM book').collect { it.id } as Set == [1, 2, 3, 4, 5] as Set
    }


    def "rolls back a database with Groovy Change"() {
        given:
        def command = createCommand(DbmRollbackCommand)
        command.changeLogFile << """
databaseChangeLog = {
    changeSet(author: "John Smith", id: "6") {
    }
    changeSet(author: "John Smith", id: "7") {
        grailsChange {
            init { ${GroovyChangeLogSpec.name}.calledBlocks << 'init' }
            validate { ${GroovyChangeLogSpec.name}.calledBlocks << 'validate' }
            change { ${GroovyChangeLogSpec.name}.calledBlocks << 'change' }
            rollback { ${GroovyChangeLogSpec.name}.calledBlocks << 'rollback' }
            confirm 'confirmation message'
            checkSum 'override value for checksum'
        }
    }
}
"""
        createCommand(DbmUpdateCountCommand).handle(getExecutionContext(DbmUpdateCountCommand, '1'))
        createCommand(DbmTagCommand).handle(getExecutionContext(DbmTagCommand, 'test tag'))
        createCommand(DbmChangelogSyncCommand).handle(getExecutionContext(DbmChangelogSyncCommand))
        calledBlocks = []

        when:
        command.handle(getExecutionContext(DbmRollbackCommand, 'test tag'))

        then:
        calledBlocks == ['init', 'change', 'rollback', 'rollback']
    }


    def "can use bind variables in the rollback block"() {
        given:
        def command = createCommand(DbmRollbackCommand)
        command.changeLogFile << """
databaseChangeLog = {
    changeSet(author: "John Smith", id: "8") {
    }
    changeSet(author: "John Smith", id: "9") {
        grailsChange {
            rollback {
                assert changeSet.id == '9'
                assert resourceAccessor.toString().startsWith('CompositeResourceAccessor{')
                assert ctx.hashCode() == ${applicationContext.hashCode()}
                assert application.hashCode() == ${applicationContext.getBean(GrailsApplication).hashCode()}
                ${GroovyChangeLogSpec.name}.calledBlocks << 'rollback'
            }
        }
    }
}
"""
        createCommand(DbmUpdateCountCommand).handle(getExecutionContext(DbmUpdateCountCommand, '1'))
        createCommand(DbmTagCommand).handle(getExecutionContext(DbmTagCommand, 'test tag'))
        createCommand(DbmChangelogSyncCommand).handle(getExecutionContext(DbmChangelogSyncCommand))
        calledBlocks = []

        when:
        command.handle(getExecutionContext(DbmRollbackCommand, 'test tag'))

        then:
        calledBlocks == ['rollback', 'rollback']
    }
}
