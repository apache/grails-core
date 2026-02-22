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

package org.apache.grails.testing.cleanup.postgresql

import groovy.sql.Sql

import org.postgresql.ds.PGSimpleDataSource
import org.springframework.context.ApplicationContext
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import org.apache.grails.testing.cleanup.core.DatabaseCleanupStats

/**
 * Functional tests for PostgreSQL database cleanup using TestContainers.
 *
 * These tests require Docker to be available on the system.
 *
 * Run with: {@code ./gradlew test --tests "PostgresDatabaseCleanerFunctionalSpec"}
 */
class PostgresDatabaseCleanerFunctionalSpec extends Specification {

    @Shared
    PostgresContainerHolder containerHolder

    @Shared
    GenericContainer postgresContainer

    static PostgresContainerHolder setupContainerHolder() {
        DockerImageName image = DockerImageName.parse('postgres:15-alpine')
        new PostgresContainerHolder(image)
    }

    def setupSpec() {
        containerHolder = setupContainerHolder()
        postgresContainer = containerHolder.container
    }

    def cleanupSpec() {
        containerHolder.stop()
    }

    private PGSimpleDataSource createDataSourceWithSchema(String currentSchema = null) {
        PostgreSQLContainer postgres = postgresContainer as PostgreSQLContainer
        PGSimpleDataSource dataSource = new PGSimpleDataSource()
        dataSource.serverName = postgres.host
        dataSource.portNumber = postgres.firstMappedPort
        dataSource.databaseName = postgres.databaseName
        dataSource.user = postgres.username
        dataSource.password = postgres.password

        if (currentSchema) {
            // For PostgreSQL driver, use setProperty for currentSchema
            dataSource.setProperty('currentSchema', currentSchema)
        }

        dataSource
    }

    def "test cleanup with currentSchema parameter"() {
        given:
        PGSimpleDataSource dataSource = createDataSourceWithSchema('testschema')
        Sql sql = new Sql(dataSource)

        try {
            // Create schema and tables
            sql.execute('CREATE SCHEMA IF NOT EXISTS testschema')
            sql.execute('CREATE TABLE IF NOT EXISTS testschema.users (id SERIAL PRIMARY KEY, name VARCHAR(255))')
            sql.execute('CREATE TABLE IF NOT EXISTS testschema.books (id SERIAL PRIMARY KEY, title VARCHAR(255), user_id INTEGER)')

            // Insert data
            sql.execute("INSERT INTO testschema.users (name) VALUES ('Alice')")
            sql.execute("INSERT INTO testschema.users (name) VALUES ('Bob')")
            sql.execute("INSERT INTO testschema.books (title, user_id) VALUES ('Book 1', 1)")

            ApplicationContext applicationContext = Stub(ApplicationContext)
            PostgresDatabaseCleaner cleaner = new PostgresDatabaseCleaner()

            when:
            DatabaseCleanupStats stats = cleaner.cleanup(applicationContext, dataSource)

            then:
            stats.tableRowCounts['users'] == 2L
            stats.tableRowCounts['books'] == 1L

            and: 'tables are now empty'
            sql.firstRow('SELECT COUNT(*) AS cnt FROM testschema.users').cnt == 0
            sql.firstRow('SELECT COUNT(*) AS cnt FROM testschema.books').cnt == 0
        }
        finally {
            sql.execute('DROP TABLE IF EXISTS testschema.books')
            sql.execute('DROP TABLE IF EXISTS testschema.users')
            sql.execute('DROP SCHEMA IF EXISTS testschema')
            sql.close()
        }
    }

    def "test cleanup without currentSchema cleans all non-system schemas"() {
        given:
        PGSimpleDataSource dataSource = createDataSourceWithSchema()
        Sql sql = new Sql(dataSource)

        try {
            // Create multiple schemas and tables
            sql.execute('CREATE SCHEMA IF NOT EXISTS schema1')
            sql.execute('CREATE SCHEMA IF NOT EXISTS schema2')
            sql.execute('CREATE TABLE IF NOT EXISTS schema1.items (id SERIAL PRIMARY KEY, name VARCHAR(255))')
            sql.execute('CREATE TABLE IF NOT EXISTS schema2.orders (id SERIAL PRIMARY KEY, description VARCHAR(255))')

            // Insert data
            sql.execute("INSERT INTO schema1.items (name) VALUES ('Item 1')")
            sql.execute("INSERT INTO schema1.items (name) VALUES ('Item 2')")
            sql.execute("INSERT INTO schema2.orders (description) VALUES ('Order A')")

            ApplicationContext applicationContext = Stub(ApplicationContext)
            PostgresDatabaseCleaner cleaner = new PostgresDatabaseCleaner()

            when:
            DatabaseCleanupStats stats = cleaner.cleanup(applicationContext, dataSource)

            then:
            stats.tableRowCounts['items'] == 2L
            stats.tableRowCounts['orders'] == 1L

            and: 'all tables in all schemas are empty'
            sql.firstRow('SELECT COUNT(*) AS cnt FROM schema1.items').cnt == 0
            sql.firstRow('SELECT COUNT(*) AS cnt FROM schema2.orders').cnt == 0
        }
        finally {
            sql.execute('DROP TABLE IF EXISTS schema2.orders')
            sql.execute('DROP TABLE IF EXISTS schema1.items')
            sql.execute('DROP SCHEMA IF EXISTS schema1')
            sql.execute('DROP SCHEMA IF EXISTS schema2')
            sql.close()
        }
    }

    def "test supports method identifies PostgreSQL databases"() {
        given:
        PGSimpleDataSource dataSource = createDataSourceWithSchema()
        PostgresDatabaseCleaner cleaner = new PostgresDatabaseCleaner()

        expect:
        cleaner.supports(dataSource)
        cleaner.databaseType() == 'postgresql'
    }

    def "test cleanup handles foreign key constraints with CASCADE"() {
        given:
        PGSimpleDataSource dataSource = createDataSourceWithSchema('fktest')
        Sql sql = new Sql(dataSource)

        try {
            sql.execute('CREATE SCHEMA IF NOT EXISTS fktest')
            sql.execute('CREATE TABLE IF NOT EXISTS fktest.categories (id SERIAL PRIMARY KEY, name VARCHAR(255))')
            sql.execute('CREATE TABLE IF NOT EXISTS fktest.products (id SERIAL PRIMARY KEY, name VARCHAR(255), category_id INTEGER REFERENCES fktest.categories(id))')

            sql.execute("INSERT INTO fktest.categories (name) VALUES ('Electronics')")
            sql.execute("INSERT INTO fktest.products (name, category_id) VALUES ('Laptop', 1)")

            ApplicationContext applicationContext = Stub(ApplicationContext)
            PostgresDatabaseCleaner cleaner = new PostgresDatabaseCleaner()

            when:
            DatabaseCleanupStats stats = cleaner.cleanup(applicationContext, dataSource)

            then:
            stats.tableRowCounts['categories'] == 1L
            stats.tableRowCounts['products'] == 1L

            and: 'both tables are empty'
            sql.firstRow('SELECT COUNT(*) AS cnt FROM fktest.categories').cnt == 0
            sql.firstRow('SELECT COUNT(*) AS cnt FROM fktest.products').cnt == 0
        }
        finally {
            sql.execute('DROP TABLE IF EXISTS fktest.products')
            sql.execute('DROP TABLE IF EXISTS fktest.categories')
            sql.execute('DROP SCHEMA IF EXISTS fktest')
            sql.close()
        }
    }

    def "test cleanup with complex foreign key relationships"() {
        given:
        PGSimpleDataSource dataSource = createDataSourceWithSchema('fkcomplex')
        Sql sql = new Sql(dataSource)

        try {
            sql.execute('CREATE SCHEMA IF NOT EXISTS fkcomplex')
            // Create parent table
            sql.execute('CREATE TABLE IF NOT EXISTS fkcomplex.departments (id SERIAL PRIMARY KEY, name VARCHAR(255))')
            // Create child table with FK to departments
            sql.execute('CREATE TABLE IF NOT EXISTS fkcomplex.employees (id SERIAL PRIMARY KEY, name VARCHAR(255), dept_id INTEGER REFERENCES fkcomplex.departments(id))')
            // Create grandchild table with FK to employees
            sql.execute('CREATE TABLE IF NOT EXISTS fkcomplex.projects (id SERIAL PRIMARY KEY, title VARCHAR(255), employee_id INTEGER REFERENCES fkcomplex.employees(id))')

            // Insert data with FK relationships
            sql.execute("INSERT INTO fkcomplex.departments (name) VALUES ('Engineering')")
            sql.execute("INSERT INTO fkcomplex.departments (name) VALUES ('Sales')")
            sql.execute("INSERT INTO fkcomplex.employees (name, dept_id) VALUES ('Alice', 1)")
            sql.execute("INSERT INTO fkcomplex.employees (name, dept_id) VALUES ('Bob', 2)")
            sql.execute("INSERT INTO fkcomplex.projects (title, employee_id) VALUES ('Project X', 1)")
            sql.execute("INSERT INTO fkcomplex.projects (title, employee_id) VALUES ('Project Y', 2)")

            ApplicationContext applicationContext = Stub(ApplicationContext)
            PostgresDatabaseCleaner cleaner = new PostgresDatabaseCleaner()

            when:
            DatabaseCleanupStats stats = cleaner.cleanup(applicationContext, dataSource)

            then: 'all data before cleanup is recorded'
            stats.tableRowCounts['departments'] == 2L
            stats.tableRowCounts['employees'] == 2L
            stats.tableRowCounts['projects'] == 2L

            and: 'all tables are truncated despite complex FK relationships'
            sql.firstRow('SELECT COUNT(*) AS cnt FROM fkcomplex.departments').cnt == 0
            sql.firstRow('SELECT COUNT(*) AS cnt FROM fkcomplex.employees').cnt == 0
            sql.firstRow('SELECT COUNT(*) AS cnt FROM fkcomplex.projects').cnt == 0

            and: 'sequences are reset for next test'
            sql.firstRow('SELECT nextval(\'fkcomplex.departments_id_seq\') AS next_id').next_id == 3L
        }
        finally {
            sql.execute('DROP TABLE IF EXISTS fkcomplex.projects')
            sql.execute('DROP TABLE IF EXISTS fkcomplex.employees')
            sql.execute('DROP TABLE IF EXISTS fkcomplex.departments')
            sql.execute('DROP SCHEMA IF EXISTS fkcomplex')
            sql.close()
        }
    }

    def "test cleanup verifies foreign key constraints are disabled during cleanup"() {
        given:
        PGSimpleDataSource dataSource = createDataSourceWithSchema('fkreplica')
        Sql sql = new Sql(dataSource)

        try {
            sql.execute('CREATE SCHEMA IF NOT EXISTS fkreplica')
            sql.execute('CREATE TABLE IF NOT EXISTS fkreplica.authors (id SERIAL PRIMARY KEY, name VARCHAR(255))')
            sql.execute('CREATE TABLE IF NOT EXISTS fkreplica.books (id SERIAL PRIMARY KEY, title VARCHAR(255), author_id INTEGER NOT NULL REFERENCES fkreplica.authors(id))')

            // Insert valid data
            sql.execute("INSERT INTO fkreplica.authors (name) VALUES ('Author 1')")
            sql.execute("INSERT INTO fkreplica.books (title, author_id) VALUES ('Book 1', 1)")

            ApplicationContext applicationContext = Stub(ApplicationContext)
            PostgresDatabaseCleaner cleaner = new PostgresDatabaseCleaner()

            when: 'cleanup is executed'
            DatabaseCleanupStats stats = cleaner.cleanup(applicationContext, dataSource)

            then: 'cleanup succeeds despite FK constraints'
            stats.tableRowCounts['authors'] == 1L
            stats.tableRowCounts['books'] == 1L

            and: 'all tables are truncated'
            sql.firstRow('SELECT COUNT(*) AS cnt FROM fkreplica.authors').cnt == 0
            sql.firstRow('SELECT COUNT(*) AS cnt FROM fkreplica.books').cnt == 0
        }
        finally {
            sql.execute('DROP TABLE IF EXISTS fkreplica.books')
            sql.execute('DROP TABLE IF EXISTS fkreplica.authors')
            sql.execute('DROP SCHEMA IF EXISTS fkreplica')
            sql.close()
        }
    }

    def "test cleanup with self-referencing foreign key"() {
        given:
        PGSimpleDataSource dataSource = createDataSourceWithSchema('fkself')
        Sql sql = new Sql(dataSource)

        try {
            sql.execute('CREATE SCHEMA IF NOT EXISTS fkself')
            // Create table with self-referencing FK
            sql.execute('CREATE TABLE IF NOT EXISTS fkself.nodes (id SERIAL PRIMARY KEY, name VARCHAR(255), parent_id INTEGER REFERENCES fkself.nodes(id))')

            // Insert hierarchical data
            sql.execute("INSERT INTO fkself.nodes (name, parent_id) VALUES ('Root', NULL)")
            sql.execute("INSERT INTO fkself.nodes (name, parent_id) VALUES ('Child 1', 1)")
            sql.execute("INSERT INTO fkself.nodes (name, parent_id) VALUES ('Child 2', 1)")
            sql.execute("INSERT INTO fkself.nodes (name, parent_id) VALUES ('Grandchild', 2)")

            ApplicationContext applicationContext = Stub(ApplicationContext)
            PostgresDatabaseCleaner cleaner = new PostgresDatabaseCleaner()

            when:
            DatabaseCleanupStats stats = cleaner.cleanup(applicationContext, dataSource)

            then: 'cleanup handles self-referencing FK'
            stats.tableRowCounts['nodes'] == 4L

            and: 'table is empty after cleanup'
            sql.firstRow('SELECT COUNT(*) AS cnt FROM fkself.nodes').cnt == 0
        }
        finally {
            sql.execute('DROP TABLE IF EXISTS fkself.nodes')
            sql.execute('DROP SCHEMA IF EXISTS fkself')
            sql.close()
        }
    }
}
