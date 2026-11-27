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
package grails.gorm.tests.generated

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.gorm.multitenancy.Tenants
import org.hibernate.dialect.H2Dialect
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.multitenancy.AllTenantsResolver
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.orm.hibernate.HibernateDatastore

/**
 * Schema and database per tenant: every tenant has its own session factory, built from the same generated classes, and its
 * tables are those the classic binding of Grails 8 created, stated here. Discriminator tenancy has its own spec.
 */
@RestoreSystemProperties
class GeneratedDomainClassesTenancyModesSpec extends Specification {

    @AutoCleanup
    HibernateDatastore datastore

    static List<String> tenants = ['north', 'south']

    def setup() {
        tenants = ['north', 'south']
    }

    private HibernateDatastore boot(MultiTenancySettings.MultiTenancyMode mode, Map extra = [:]) {
        datastore?.close()
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'north')
        datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver([
                'grails.gorm.multiTenancy.mode'               : mode,
                'grails.gorm.multiTenancy.tenantResolverClass': GdcTenantsResolver,
                'dataSource.url'                              : "jdbc:h2:mem:gdcTenancy${System.nanoTime()};LOCK_TIMEOUT=10000".toString(),
                'dataSource.dbCreate'                         : 'create',
                'dataSource.dialect'                          : H2Dialect.name,
                'hibernate.hbm2ddl.auto'                      : 'create',
                'hibernate.cache.queries'                     : 'false',
                'hibernate.cache.use_query_cache'             : 'false',
        ] + extra), GdcTenantAuthor, GdcTenantBook)
        return datastore
    }

    private static Map<String, List<String>> tablesAndColumns(javax.sql.DataSource dataSource) {
        Map<String, List<String>> result = new TreeMap<String, List<String>>()
        dataSource.connection.withCloseable { java.sql.Connection connection ->
            connection.createStatement().withCloseable { java.sql.Statement statement ->
                statement.executeQuery('''select table_schema, table_name, column_name, data_type, is_nullable
                        from information_schema.columns where table_schema not in ('INFORMATION_SCHEMA')
                        order by table_schema, table_name, column_name''').with { java.sql.ResultSet rs ->
                    while (rs.next()) {
                        String key = "${rs.getString(1)}.${rs.getString(2)}".toString()
                        result.computeIfAbsent(key) { new ArrayList<String>() }
                                .add("${rs.getString(3)} ${rs.getString(4)} ${rs.getString(5)}".toString())
                    }
                }
            }
        }
        return result
    }

    private static Map<String, Integer> constraints(javax.sql.DataSource dataSource) {
        Map<String, Integer> result = new TreeMap<String, Integer>()
        dataSource.connection.withCloseable { java.sql.Connection connection ->
            connection.createStatement().withCloseable { java.sql.Statement statement ->
                statement.executeQuery('''select table_schema, table_name, constraint_type, count(*)
                        from information_schema.table_constraints where table_schema not in ('INFORMATION_SCHEMA')
                        group by table_schema, table_name, constraint_type''').with { java.sql.ResultSet rs ->
                    while (rs.next()) {
                        result["${rs.getString(1)}.${rs.getString(2)} ${rs.getString(3)}".toString()] = rs.getInt(4)
                    }
                }
            }
        }
        return result
    }

    private Map scenario() {
        HibernateDatastore booted = boot(MultiTenancySettings.MultiTenancyMode.SCHEMA)
        Map result = [:]
        ['north', 'south'].each { String tenant ->
            Tenants.withId(tenant) {
                GdcTenantAuthor.withTransaction {
                    GdcTenantAuthor author = new GdcTenantAuthor(name: "author of ${tenant}".toString())
                    author.addToBooks(new GdcTenantBook(title: "${tenant} one".toString()))
                    author.addToBooks(new GdcTenantBook(title: "${tenant} two".toString()))
                    author.save(flush: true, failOnError: true)
                }
            }
        }
        Tenants.withId('south') {
            GdcTenantAuthor.withTransaction {
                GdcTenantAuthor.first().addToBooks(new GdcTenantBook(title: 'south three')).save(flush: true, failOnError: true)
            }
        }
        result.counts = ['north', 'south'].collectEntries { String tenant ->
            [tenant, Tenants.withId(tenant) {
                GdcTenantAuthor.withNewSession {
                    [GdcTenantAuthor.count(), GdcTenantBook.count(), GdcTenantAuthor.first().books*.title.sort()]
                }
            }]
        }
        tenants = ['north', 'south', 'east']
        booted.addTenantForSchema('east')
        result.east = Tenants.withId('east') { GdcTenantAuthor.withNewSession { GdcTenantAuthor.count() } }
        Map each = [:]
        GdcTenantAuthor.eachTenant { String tenantId -> each[tenantId] = GdcTenantAuthor.count() }
        result.each = each
        result.tables = tablesAndColumns(booted.connectionSources.defaultConnectionSource.dataSource)
        result.constraints = constraints(booted.connectionSources.defaultConnectionSource.dataSource)
        return result
    }

    def "schema per tenant isolates the rows of each tenant and builds each tenant's tables as classic binding built them"() {
        when:
        Map generated = scenario()

        then: 'rows stay in their tenant, through a lazy collection too'
        generated.counts == [north: [1, 2, ['north one', 'north two']], south: [1, 3, ['south one', 'south three', 'south two']]]
        generated.east == 0
        generated.each == [north: 1, south: 1, east: 0]

        and: 'every tenant schema has the tables, columns and constraints classic binding created'
        generated.tables == ['PUBLIC', 'east', 'north', 'south'].collectEntries { String schema ->
            [("${schema}.GDC_TENANT_AUTHOR".toString()): ['ID BIGINT NO', 'NAME CHARACTER VARYING YES', 'VERSION BIGINT NO'],
             ("${schema}.GDC_TENANT_BOOK".toString())  : ['AUTHOR_ID BIGINT YES', 'ID BIGINT NO', 'TITLE CHARACTER VARYING YES', 'VERSION BIGINT NO']]
        }
        generated.constraints == ['PUBLIC', 'east', 'north', 'south'].collectEntries { String schema ->
            [("${schema}.GDC_TENANT_AUTHOR PRIMARY KEY".toString()): 1,
             ("${schema}.GDC_TENANT_BOOK FOREIGN KEY".toString())  : 1,
             ("${schema}.GDC_TENANT_BOOK PRIMARY KEY".toString())  : 1]
        }
    }

    def "a session factory of a tenant maps the real domain classes under their own names"() {
        when:
        HibernateDatastore booted = boot(MultiTenancySettings.MultiTenancyMode.SCHEMA)
        HibernateDatastore north = booted.getDatastoreForConnection('north')

        then:
        [booted, north].every { HibernateDatastore each ->
            each.sessionFactory.mappingMetamodel.getEntityDescriptor(GdcTenantAuthor).entityName == GdcTenantAuthor.name &&
                    each.sessionFactory.metamodel.entity(GdcTenantAuthor).javaType == GdcTenantAuthor &&
                    each.sessionFactory.mappingMetamodel.getEntityDescriptor(GdcTenantBook).mappedClass == GdcTenantBook
        }
    }

    def "tables with and without an explicit schema land in the schema classic binding put them in, under a default schema"() {
        when:
        datastore?.close()
        datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver([
                'dataSource.url'          : "jdbc:h2:mem:gdcSchemas${System.nanoTime()};LOCK_TIMEOUT=10000;INIT=CREATE SCHEMA IF NOT EXISTS gdc_default\\;CREATE SCHEMA IF NOT EXISTS gdc_shared".toString(),
                'dataSource.dbCreate'     : 'create',
                'dataSource.dialect'      : H2Dialect.name,
                'hibernate.hbm2ddl.auto'  : 'create',
                'hibernate.default_schema': 'gdc_default',
        ]), GdcSchemaPlain, GdcSchemaOwner, GdcSchemaChild)
        GdcSchemaOwner.withTransaction {
            GdcSchemaOwner owner = new GdcSchemaOwner(name: 'owner')
            owner.tags = ['a', 'b'] as Set
            owner.addToChildren(new GdcSchemaChild(label: 'child'))
            owner.save(flush: true, failOnError: true)
            new GdcSchemaPlain(name: 'plain').save(flush: true, failOnError: true)
        }
        Map found = GdcSchemaOwner.withNewSession {
            GdcSchemaOwner owner = GdcSchemaOwner.first()
            [tags: owner.tags.sort(), children: owner.children*.label, plain: GdcSchemaPlain.count()]
        }
        Map<String, List<String>> tables = tablesAndColumns(datastore.connectionSources.defaultConnectionSource.dataSource)

        then:
        found == [tags: ['a', 'b'], children: ['child'], plain: 1]
        tables.keySet() == ['GDC_DEFAULT.GDC_SCHEMA_CHILD', 'GDC_DEFAULT.GDC_SCHEMA_PLAIN', 'GDC_SHARED.GDC_SCHEMA_OWNER', 'GDC_SHARED.GDC_SCHEMA_OWNER_TAGS'] as Set
    }

    def "database per tenant isolates the rows of each tenant and builds each database as classic binding built it"() {
        when:
        HibernateDatastore booted = boot(MultiTenancySettings.MultiTenancyMode.DATABASE, [
                'dataSources.north.url': "jdbc:h2:mem:gdcTenancyNorth${System.nanoTime()};LOCK_TIMEOUT=10000".toString(),
                'dataSources.south.url': "jdbc:h2:mem:gdcTenancySouth${System.nanoTime()};LOCK_TIMEOUT=10000".toString(),
        ])
        ['north', 'south'].each { String tenant ->
            Tenants.withId(tenant) {
                GdcTenantAuthor.withTransaction {
                    GdcTenantAuthor author = new GdcTenantAuthor(name: "author of ${tenant}".toString())
                    author.addToBooks(new GdcTenantBook(title: "${tenant} one".toString()))
                    author.save(flush: true, failOnError: true)
                }
            }
        }
        Map counts = ['north', 'south'].collectEntries { String tenant ->
            [tenant, Tenants.withId(tenant) {
                GdcTenantAuthor.withNewSession { [GdcTenantAuthor.count(), GdcTenantAuthor.first().books*.title] }
            }]
        }

        then:
        counts == [north: [1, ['north one']], south: [1, ['south one']]]
        tablesAndColumns(booted.connectionSources.getConnectionSource('north').dataSource) == [
                'PUBLIC.GDC_TENANT_AUTHOR': ['ID BIGINT NO', 'NAME CHARACTER VARYING YES', 'VERSION BIGINT NO'],
                'PUBLIC.GDC_TENANT_BOOK'  : ['AUTHOR_ID BIGINT YES', 'ID BIGINT NO', 'TITLE CHARACTER VARYING YES', 'VERSION BIGINT NO'],
        ]
        constraints(booted.connectionSources.getConnectionSource('south').dataSource) == [
                'PUBLIC.GDC_TENANT_AUTHOR PRIMARY KEY': 1, 'PUBLIC.GDC_TENANT_BOOK FOREIGN KEY': 1, 'PUBLIC.GDC_TENANT_BOOK PRIMARY KEY': 1,
        ]
    }
}

@Entity
class GdcSchemaPlain {
    String name
}

@Entity
class GdcSchemaOwner {
    String name
    static hasMany = [tags: String, children: GdcSchemaChild]
    static mapping = {
        table schema: 'gdc_shared'
    }
}

@Entity
class GdcSchemaChild {
    String label
    static belongsTo = [owner: GdcSchemaOwner]
}

class GdcTenantsResolver extends SystemPropertyTenantResolver implements AllTenantsResolver {

    @Override
    Iterable<Serializable> resolveTenantIds() {
        return GeneratedDomainClassesTenancyModesSpec.tenants as List<Serializable>
    }
}

@Entity
class GdcTenantAuthor implements MultiTenant<GdcTenantAuthor> {
    String name
    static hasMany = [books: GdcTenantBook]
}

@Entity
class GdcTenantBook implements MultiTenant<GdcTenantBook> {
    String title
    static belongsTo = [author: GdcTenantAuthor]
}
