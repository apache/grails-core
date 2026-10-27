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
package org.grails.orm.hibernate.cfg.domainbinding.secondpass

import grails.gorm.annotation.Entity
import grails.gorm.tests.HibernateGormDatastoreSpec
import org.hibernate.boot.model.relational.internal.SqlStringGenerationContextImpl
import org.hibernate.boot.spi.InFlightMetadataCollector
import org.hibernate.boot.spi.MetadataBuildingContext
import org.hibernate.dialect.DB2Dialect
import org.hibernate.dialect.DB2iDialect
import org.hibernate.dialect.DB2zDialect
import org.hibernate.dialect.Dialect
import org.hibernate.dialect.H2Dialect
import org.hibernate.dialect.MariaDBDialect
import org.hibernate.dialect.MySQLDialect
import org.hibernate.dialect.OracleDialect
import org.hibernate.dialect.PostgreSQLDialect
import org.hibernate.dialect.SQLServerDialect
import org.hibernate.engine.jdbc.env.internal.JdbcEnvironmentImpl
import org.hibernate.mapping.Collection
import org.hibernate.mapping.Column
import org.hibernate.mapping.PrimaryKey
import org.hibernate.mapping.Table
import org.hibernate.mapping.UniqueKey
import spock.lang.Unroll

/**
 * The join table two unidirectional hasMany associations to the same class share gets one implicit unique key from
 * Hibernate, over the first association's key column and the element column, and nothing for the second. The binder
 * gives every association a constraint of its own over its key column and the element, in the shape the database
 * needs: a unique key where nulls are distinct or the dialect renders a filtered unique index, and on Oracle, which
 * compares the non-null columns of a row, one unique key over every association's key column and the element. An association with a join table of its own, and one whose key the other side
 * owns, are left as Hibernate binds them.
 */
class SharedJoinTableUniqueKeyBinderSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(SjtOwner, SjtSeparateOwner, SjtParent, SjtChild, SjtRole, SjtListOwner, SjtNote, SjtSharedKeyOwner)
    }

    private Collection collectionOf(Class owner, String property) {
        datastore.metadata.getCollectionBinding("${owner.name}.${property}".toString())
    }

    /** A building context that sees the collections of the metadata the datastore built, as the binder looks for the ones that share a table. */
    private MetadataBuildingContext buildingContext() {
        InFlightMetadataCollector collector = Stub(InFlightMetadataCollector) {
            getCollectionBindings() >> datastore.metadata.collectionBindings
            getDatabase() >> datastore.metadata.database
        }
        Stub(MetadataBuildingContext) {
            getMetadataCollector() >> collector
        }
    }

    private static List<List<String>> uniqueKeyColumns(Table table) {
        table.uniqueKeys.values().collect { UniqueKey key -> key.columns*.name.sort() }.sort()
    }

    void "the shared join table has a unique key per association, over its own key column and the element"() {
        given:
        Collection roles = collectionOf(SjtOwner, 'roles')
        Collection denyRoles = collectionOf(SjtOwner, 'denyRoles')

        expect: 'both associations share the table'
        roles.collectionTable.is(denyRoles.collectionTable)
        roles.collectionTable.columns*.name.sort() == ['sjt_owner_deny_roles_id', 'sjt_owner_roles_id', 'sjt_role_id']

        and: 'each association is unique by its own key column and the element; H2 treats nulls as distinct'
        uniqueKeyColumns(roles.collectionTable) == [
                ['sjt_owner_deny_roles_id', 'sjt_role_id'],
                ['sjt_owner_roles_id', 'sjt_role_id']
        ]
        roles.collectionTable.indexes.isEmpty()

        and: 'both keys are updatable, so rows of both associations are written'
        roles.key.updateable
        denyRoles.key.updateable
    }

    void "each association of a shared join table with a shared key column is restricted to the rows that have its element"() {
        given:
        Collection roles = collectionOf(SjtSharedKeyOwner, 'roles')
        Collection denyRoles = collectionOf(SjtSharedKeyOwner, 'denyRoles')

        expect: 'Hibernate removes all the rows of a collection by its key and loads them by it, so the where limits both'
        roles.where == 'role_id is not null'
        denyRoles.where == 'deny_role_id is not null'
    }

    void "a shared join table with separate key columns is not given where restrictions"() {
        expect: 'each association has its own key column, so it already separates its rows without where restrictions'
        collectionOf(SjtOwner, 'roles').where == null
        collectionOf(SjtOwner, 'denyRoles').where == null
    }

    void "a join table of one association is not restricted"() {
        expect:
        collectionOf(SjtSeparateOwner, 'roles').where == null
        collectionOf(SjtSeparateOwner, 'denyRoles').where == null
        collectionOf(SjtParent, 'children').where == null
    }

    void "binding again does not repeat the restriction"() {
        given:
        Collection roles = collectionOf(SjtSharedKeyOwner, 'roles')
        SharedJoinTableUniqueKeyBinder binder = new SharedJoinTableUniqueKeyBinder(buildingContext(), new H2Dialect())

        when:
        binder.bind(roles)
        binder.bind(roles)

        then:
        roles.where == 'role_id is not null'
    }

    void "a restriction the mapping already has is kept and extended"() {
        given:
        Collection roles = collectionOf(SjtSharedKeyOwner, 'roles')
        String before = roles.where
        roles.where = 'archived = 0'
        SharedJoinTableUniqueKeyBinder binder = new SharedJoinTableUniqueKeyBinder(buildingContext(), new H2Dialect())

        when:
        binder.bind(roles)

        then:
        roles.where == '(archived = 0) and role_id is not null'

        cleanup:
        roles.where = before
    }

    private Column columnOf(Class entity, String property) {
        datastore.metadata.getEntityBinding(entity.name).getProperty(property).columns.first() as Column
    }

    void "a set whose element is a large object gets no key, as in Hibernate, and a text gets one"() {
        expect: 'a large object cannot be indexed, and Hibernate leaves the set of such an element without a key too'
        SharedJoinTableUniqueKeyBinder.hasLobColumn(datastore.metadata, [columnOf(SjtNote, 'body')])
        SharedJoinTableUniqueKeyBinder.hasLobColumn(datastore.metadata, [columnOf(SjtNote, 'title'), columnOf(SjtNote, 'body')])

        and:
        !SharedJoinTableUniqueKeyBinder.hasLobColumn(datastore.metadata, [columnOf(SjtNote, 'title')])
        !SharedJoinTableUniqueKeyBinder.hasLobColumn(datastore.metadata, [columnOf(SjtNote, 'pages')])
        !SharedJoinTableUniqueKeyBinder.hasLobColumn(datastore.metadata, [])
    }

    void "an association with a join table of its own keeps the unique key Hibernate creates for it"() {
        given:
        Collection roles = collectionOf(SjtSeparateOwner, 'roles')
        Collection denyRoles = collectionOf(SjtSeparateOwner, 'denyRoles')

        expect:
        !roles.collectionTable.is(denyRoles.collectionTable)
        uniqueKeyColumns(roles.collectionTable) == [['sjt_role_id', 'sjt_separate_owner_roles_id']]
        uniqueKeyColumns(denyRoles.collectionTable) == [['sjt_role_id', 'sjt_separate_owner_deny_roles_id']]
    }

    void "a bidirectional one-to-many has no join table and is left alone"() {
        given:
        Collection children = collectionOf(SjtParent, 'children')

        expect:
        children.inverse
        children.collectionTable.name == 'sjt_child'
        children.collectionTable.uniqueKeys.isEmpty()
    }

    @Unroll
    void "bind leaves the #property of a list, a map or a bag alone on #dialect.class.simpleName, whose elements repeat by design"() {
        given: 'the join table of a list is keyed by (owner, index), the one of a map by (owner, key), and a bag has no key'
        Collection collection = collectionOf(SjtListOwner, property)
        SharedJoinTableUniqueKeyBinder binder = new SharedJoinTableUniqueKeyBinder(buildingContext(), dialect)

        expect: 'the table has no unique key of its own: the primary key, where there is one, is what makes its rows unique'
        collection.collectionTable.hasPrimaryKey() == hasPrimaryKey
        uniqueKeyColumns(collection.collectionTable).isEmpty()

        when:
        binder.bind(collection)

        then: 'no unique key over the owner and the element is added'
        uniqueKeyColumns(collection.collectionTable).isEmpty()
        collection.collectionTable.indexes.isEmpty()

        where:
        property    | hasPrimaryKey | dialect
        'nicknames' | true          | new H2Dialect()
        'nicknames' | true          | new OracleDialect()
        'roleList'  | true          | new H2Dialect()
        'roleList'  | true          | new OracleDialect()
        'attrs'     | true          | new H2Dialect()
        'attrs'     | true          | new OracleDialect()
        'roleMap'   | true          | new H2Dialect()
        'roleMap'   | true          | new OracleDialect()
        'labels'    | false         | new H2Dialect()
        'labels'    | false         | new OracleDialect()
        'bagRoles'  | false         | new H2Dialect()
        'bagRoles'  | false         | new OracleDialect()
    }

    void "bind leaves a collection whose key the other side owns alone"() {
        given: 'the bidirectional collection, whose key column lives in the child table'
        Collection children = collectionOf(SjtParent, 'children')
        SharedJoinTableUniqueKeyBinder binder = new SharedJoinTableUniqueKeyBinder(buildingContext(), new H2Dialect())

        when:
        binder.bind(children)

        then:
        children.collectionTable.uniqueKeys.isEmpty()
        children.collectionTable.indexes.isEmpty()
    }

    @Unroll
    void "#dialect.class.simpleName resolves to #expected"() {
        expect:
        SharedJoinTableUniqueness.forDialect(dialect) == expected

        where:
        dialect                   | expected
        new H2Dialect()           | SharedJoinTableUniqueness.KEY_PER_COLLECTION
        new PostgreSQLDialect()   | SharedJoinTableUniqueness.KEY_PER_COLLECTION
        new MySQLDialect()        | SharedJoinTableUniqueness.KEY_PER_COLLECTION
        new MariaDBDialect()      | SharedJoinTableUniqueness.KEY_PER_COLLECTION
        new SQLServerDialect()    | SharedJoinTableUniqueness.KEY_PER_COLLECTION
        new DB2Dialect()          | SharedJoinTableUniqueness.ONE_KEY_FOR_ALL_COLLECTIONS
        new DB2iDialect()         | SharedJoinTableUniqueness.KEY_PER_COLLECTION
        new DB2zDialect()         | SharedJoinTableUniqueness.KEY_PER_COLLECTION
        new OracleDialect()       | SharedJoinTableUniqueness.ONE_KEY_FOR_ALL_COLLECTIONS
    }

    @Unroll
    void "on #dialect.class.simpleName the shared table is given #what"() {
        given: 'a shared table as Hibernate leaves it: the first association has its unique key, the second nothing'
        Table table = new Table('orm', 'shared_roles')
        Column roles = nullable('roles_id')
        Column denyRoles = nullable('deny_roles_id')
        Column role = nullable('role_id')
        [roles, denyRoles, role].each { table.addColumn(it) }
        UniqueKey first = new UniqueKey(table)
        first.name = 'uk_roles_id_role_id'
        first.addColumn(roles)
        first.addColumn(role)
        table.addUniqueKey(first)
        SharedJoinTableUniqueness.Naming naming = [
                uniqueKeyName: { List<Column> columns -> 'uk_' + columns*.name.join('_') },
                indexName    : { List<Column> columns -> 'ix_' + columns*.name.join('_') }
        ] as SharedJoinTableUniqueness.Naming

        when: 'the binder runs for each association, as it does after the keys of each are created'
        SharedJoinTableUniqueness uniqueness = SharedJoinTableUniqueness.forDialect(dialect)
        uniqueness.apply(table, [roles], [role], naming)
        uniqueness.apply(table, [denyRoles], [role], naming)

        then: 'the DDL the dialect renders for what the table now holds, in its create table statement or after it'
        ddl(dialect, table) == statements

        where:
        dialect                 | what                                            | statements
        new H2Dialect()         | 'a unique key per association'                  | [
                ', unique (roles_id, role_id), unique (deny_roles_id, role_id)']
        new PostgreSQLDialect() | 'a unique key per association'                  | [
                ', unique (roles_id, role_id), unique (deny_roles_id, role_id)']
        new MySQLDialect()      | 'a unique key per association'                  | [
                'alter table shared_roles add constraint uk_roles_id_role_id unique (roles_id, role_id)',
                'alter table shared_roles add constraint uk_deny_roles_id_role_id unique (deny_roles_id, role_id)']
        new SQLServerDialect()  | 'a filtered unique index per association'       | [
                'create unique nonclustered index uk_roles_id_role_id on shared_roles (roles_id, role_id) where roles_id is not null and role_id is not null',
                'create unique nonclustered index uk_deny_roles_id_role_id on shared_roles (deny_roles_id, role_id) where deny_roles_id is not null and role_id is not null']
        new DB2Dialect()        | 'one unique index over both key columns and the element, excluding null keys' | [
                'create unique index uk_roles_id_role_id on shared_roles (roles_id, role_id, deny_roles_id) exclude null keys']
        new DB2iDialect()       | 'a unique index per association where not null' | [
                'create unique where not null index uk_roles_id_role_id on shared_roles (roles_id, role_id)',
                'create unique where not null index uk_deny_roles_id_role_id on shared_roles (deny_roles_id, role_id)']
        new DB2zDialect()       | 'a unique index per association where not null' | [
                'create unique where not null index uk_roles_id_role_id on shared_roles (roles_id, role_id)',
                'create unique where not null index uk_deny_roles_id_role_id on shared_roles (deny_roles_id, role_id)']
        new OracleDialect()     | 'one unique key over both key columns and the element' | [
                ', unique (roles_id, role_id, deny_roles_id)']
    }

    @Unroll
    void "on #dialect.class.simpleName a shared join table with one key column and an element column each is given #what"() {
        given: 'the first association has its unique key over the shared key column and its element, the second nothing'
        Table table = new Table('orm', 'shared_roles')
        Column owner = nullable('owner_id')
        Column role = nullable('role_id')
        Column denyRole = nullable('deny_role_id')
        [owner, role, denyRole].each { table.addColumn(it) }
        UniqueKey first = new UniqueKey(table)
        first.name = 'uk_owner_id_role_id'
        first.addColumn(owner)
        first.addColumn(role)
        table.addUniqueKey(first)
        SharedJoinTableUniqueness.Naming naming = [
                uniqueKeyName: { List<Column> columns -> 'uk_' + columns*.name.join('_') },
                indexName    : { List<Column> columns -> 'ix_' + columns*.name.join('_') }
        ] as SharedJoinTableUniqueness.Naming

        when:
        SharedJoinTableUniqueness uniqueness = SharedJoinTableUniqueness.forDialect(dialect)
        uniqueness.apply(table, [owner], [role], naming)
        uniqueness.apply(table, [owner], [denyRole], naming)

        then:
        ddl(dialect, table) == statements

        where:
        dialect                 | what                                                          | statements
        new H2Dialect()         | 'a unique key per association'                                | [
                ', unique (owner_id, role_id), unique (owner_id, deny_role_id)']
        new PostgreSQLDialect() | 'a unique key per association'                                | [
                ', unique (owner_id, role_id), unique (owner_id, deny_role_id)']
        new OracleDialect()     | 'one unique key over the key column and both element columns' | [
                ', unique (owner_id, role_id, deny_role_id)']
        new DB2Dialect()        | 'one unique index over the key column and both element columns, excluding null keys' | [
                'create unique index uk_owner_id_role_id on shared_roles (owner_id, role_id, deny_role_id) exclude null keys']
        new DB2iDialect()       | 'a unique index per association where not null'              | [
                'create unique where not null index uk_owner_id_role_id on shared_roles (owner_id, role_id)',
                'create unique where not null index uk_owner_id_deny_role_id on shared_roles (owner_id, deny_role_id)']
        new DB2zDialect()       | 'a unique index per association where not null'              | [
                'create unique where not null index uk_owner_id_role_id on shared_roles (owner_id, role_id)',
                'create unique where not null index uk_owner_id_deny_role_id on shared_roles (owner_id, deny_role_id)']
    }

    @Unroll
    void "on #dialect.class.simpleName a set whose primary key covers its key and element is given nothing more"() {
        given: 'a table whose primary key is the key column and the element, as Hibernate makes it for non-null columns'
        Table table = new Table('orm', 'own_roles')
        Column owner = nullable('owner_id')
        Column role = nullable('role_id')
        [owner, role].each { table.addColumn(it) }
        PrimaryKey primaryKey = new PrimaryKey(table)
        primaryKey.addColumn(owner)
        primaryKey.addColumn(role)
        table.primaryKey = primaryKey
        SharedJoinTableUniqueness.Naming naming = [
                uniqueKeyName: { List<Column> columns -> 'uk_' + columns*.name.join('_') },
                indexName    : { List<Column> columns -> 'ix_' + columns*.name.join('_') }
        ] as SharedJoinTableUniqueness.Naming

        when:
        SharedJoinTableUniqueness.forDialect(dialect).apply(table, [owner], [role], naming)

        then:
        table.uniqueKeys.isEmpty()
        table.indexes.isEmpty()

        where:
        dialect << [new H2Dialect(), new OracleDialect()]
    }

    void "on Oracle a unique key the mapping declares is not widened with the columns of another association"() {
        given: 'a shared table whose only unique key was declared by the mapping, not created by Hibernate'
        Table table = new Table('orm', 'shared_roles')
        Column roles = nullable('roles_id')
        Column denyRoles = nullable('deny_roles_id')
        Column role = nullable('role_id')
        [roles, denyRoles, role].each { table.addColumn(it) }
        UniqueKey declared = new UniqueKey(table)
        declared.name = 'uk_declared'
        declared.addColumn(roles)
        declared.addColumn(role)
        declared.explicit = true
        table.addUniqueKey(declared)

        when:
        SharedJoinTableUniqueness.ONE_KEY_FOR_ALL_COLLECTIONS.apply(table, [denyRoles], [role], Stub(SharedJoinTableUniqueness.Naming))

        then: 'the declared key keeps its columns'
        table.uniqueKeys['uk_declared'].columns*.name == ['roles_id', 'role_id']
    }

    void "on Oracle a table one association has to itself keeps the unique key Hibernate creates"() {
        given:
        Table table = new Table('orm', 'own_roles')
        Column roles = nullable('roles_id')
        Column role = nullable('role_id')
        [roles, role].each { table.addColumn(it) }
        UniqueKey first = new UniqueKey(table)
        first.name = 'uk_roles_id_role_id'
        first.addColumn(roles)
        first.addColumn(role)
        table.addUniqueKey(first)

        when:
        SharedJoinTableUniqueness.ONE_KEY_FOR_ALL_COLLECTIONS.apply(table, [roles], [role], Stub(SharedJoinTableUniqueness.Naming))

        then:
        table.uniqueKeys.keySet() == ['uk_roles_id_role_id'] as Set
        table.uniqueKeys['uk_roles_id_role_id'].columns*.name == ['roles_id', 'role_id']
    }

    private static Column nullable(String name) {
        Column column = new Column(name)
        column.nullable = true
        column
    }

    /** The DDL the dialect renders for the unique keys and indexes of the table, in that order. */
    private List<String> ddl(Dialect dialect, Table table) {
        def jdbcEnvironment = new JdbcEnvironmentImpl(datastore.sessionFactory.serviceRegistry, dialect)
        def context = SqlStringGenerationContextImpl.forTests(jdbcEnvironment)
        def metadata = datastore.metadata
        List<String> statements = []
        table.uniqueKeys.values().each { UniqueKey key ->
            String alter = dialect.uniqueDelegate.getAlterTableToAddUniqueKeyCommand(key, metadata, context)
            if (alter) {
                statements << alter
            }
        }
        String inCreateTable = dialect.uniqueDelegate.getTableCreationUniqueConstraintsFragment(table, context)
        if (inCreateTable) {
            statements << inCreateTable
        }
        table.indexes.values().each { index ->
            statements.addAll(dialect.indexExporter.getSqlCreateStrings(index, metadata, context))
        }
        statements
    }
}

@Entity
class SjtOwner {
    Long id
    String name
    static hasMany = [roles: SjtRole, denyRoles: SjtRole]
}

@Entity
class SjtSharedKeyOwner {
    Long id
    String name
    static hasMany = [roles: SjtRole, denyRoles: SjtRole]
    static mapping = {
        roles joinTable: [name: 'sjt_shared_key_roles', key: 'owner_id', column: 'role_id']
        denyRoles joinTable: [name: 'sjt_shared_key_roles', key: 'owner_id', column: 'deny_role_id']
    }
}

@Entity
class SjtSeparateOwner {
    Long id
    String name
    static hasMany = [roles: SjtRole, denyRoles: SjtRole]
    static mapping = {
        roles joinTable: 'sjt_separate_owner_allow'
        denyRoles joinTable: 'sjt_separate_owner_deny'
    }
}

@Entity
class SjtRole {
    Long id
    String authority
}

@Entity
class SjtParent {
    Long id
    String name
    static hasMany = [children: SjtChild]
}

@Entity
class SjtChild {
    Long id
    String name
    static belongsTo = [parent: SjtParent]
}

@Entity
class SjtListOwner {
    Long id
    List<String> nicknames
    List<SjtRole> roleList
    Map<String, String> attrs
    Map<String, SjtRole> roleMap
    java.util.Collection<String> labels
    java.util.Collection<SjtRole> bagRoles
    static hasMany = [nicknames: String, roleList: SjtRole, attrs: String, roleMap: SjtRole, labels: String, bagRoles: SjtRole]
    static mapping = {
        roleList joinTable: 'sjt_list_owner_role_list'
        roleMap joinTable: 'sjt_list_owner_role_map'
        bagRoles joinTable: 'sjt_list_owner_bag_roles'
    }
}

@Entity
class SjtNote {
    Long id
    String title
    String body
    Integer pages
    static mapping = {
        body type: 'text'
    }
}
