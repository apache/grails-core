package org.grails.plugins.databasemigration

import grails.dev.commands.ApplicationCommand
import grails.dev.commands.ExecutionContext
import grails.testing.mixin.integration.Integration
import grails.util.GrailsNameUtils
import groovy.sql.Sql
import liquibase.GlobalConfiguration
import liquibase.Scope
import liquibase.exception.LiquibaseException
import org.grails.build.parsing.CommandLineParser
import org.grails.plugins.databasemigration.command.DbmUpdateCommand
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import org.springframework.test.context.ActiveProfiles
import spock.lang.AutoCleanup
import spock.lang.Specification

import javax.sql.DataSource

@Integration
@ActiveProfiles('transaction-datasource')
@Component
class DbUpdateCommandSpec extends Specification {

    @Autowired
    DataSource dataSource

    @Autowired
    ApplicationContext applicationContext

    @AutoCleanup
    Sql sql

    def setup() {
        sql = new Sql(dataSource)
    }

    void "test the transaction behaviour in the changeSet with grailsChange and GORM"() {

        when:
        Scope.child(GlobalConfiguration.DUPLICATE_FILE_MODE.getKey(), GlobalConfiguration.DuplicateFileMode.WARN, { ->
            DbmUpdateCommand command = new DbmUpdateCommand()
            command.applicationContext = applicationContext
            command.setExecutionContext(getExecutionContext(DbmUpdateCommand))
            command.handle()
        } as Scope.ScopedRunner)

        then:
        def e = thrown(LiquibaseException)
        e.cause instanceof LiquibaseException
        sql.firstRow('SELECT COUNT(*) AS num FROM DATABASECHANGELOG WHERE id=?;', 'create-person-grails').num == 1
        sql.firstRow('SELECT COUNT(*) AS num FROM person;').num == 1
        sql.firstRow('SELECT COUNT(*) AS num FROM account;').num == 0

    }

    private ExecutionContext getExecutionContext(Class<ApplicationCommand> clazz, String... args) {
        def commandClassName = GrailsNameUtils.getScriptName(GrailsNameUtils.getLogicalName(clazz.name, 'Command'))
        new ExecutionContext(
                new CommandLineParser().parse(([commandClassName] + args.toList()) as String[])
        )
    }
}




