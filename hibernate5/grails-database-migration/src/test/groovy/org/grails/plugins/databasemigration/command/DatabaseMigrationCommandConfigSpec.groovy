package org.grails.plugins.databasemigration.command

import org.h2.Driver
import spock.lang.Specification
import org.grails.testing.GrailsUnitTest

class DatabaseMigrationCommandConfigSpec extends Specification implements DatabaseMigrationCommand, GrailsUnitTest {

    void cleanup() {
        config.remove('dataSource')
        config.remove('dataSources')
    }

    void "test getDataSourceConfig with single dataSource"() {

        when:
        config.dataSource = [
                'dbCreate'       : '',
                'url'            : 'jdbc:h2:mem:testDb',
                'username'       : 'sa',
                'password'       : '',
                'driverClassName': Driver.name
        ]

        then:
        getDataSourceConfig(config) == [
                'dbCreate'       : '',
                'url'            : 'jdbc:h2:mem:testDb',
                'username'       : 'sa',
                'password'       : '',
                'driverClassName': Driver.name
        ]

    }

    void "test getDataSourceConfig with no dataSource config"() {
        expect:
        getDataSourceConfig(config) == null
    }

    void "test getDataSourceConfig should return config when default is defined in dataSources"() {
        when:
        config.dataSources = [
                dataSource: [
                        'dbCreate'       : '',
                        'url'            : 'jdbc:h2:mem:testDb',
                        'username'       : 'sa',
                        'password'       : '',
                        'driverClassName': Driver.name
                ]
        ]

        then:
        getDataSourceConfig(config) == [
                'dbCreate'       : '',
                'url'            : 'jdbc:h2:mem:testDb',
                'username'       : 'sa',
                'password'       : '',
                'driverClassName': Driver.name,
        ]

    }

    void "test getDataSourceConfig should return config when both dataSource and dataSources exists"() {
        when:
        config.dataSource = [
                'dbCreate'       : '',
                'url'            : 'jdbc:h2:mem:testDb',
                'username'       : 'sa',
                'password'       : '',
                'driverClassName': Driver.name
        ]
        config.dataSources = [
                other: [
                        'dbCreate'       : '',
                        'url'            : 'jdbc:h2:mem:otherDb',
                        'username'       : 'sa',
                        'password'       : '',
                        'driverClassName': Driver.name
                ]
        ]

        then:
        getDataSourceConfig(config) == [
                'dbCreate'       : '',
                'url'            : 'jdbc:h2:mem:testDb',
                'username'       : 'sa',
                'password'       : '',
                'driverClassName': Driver.name,
        ]

    }

}
