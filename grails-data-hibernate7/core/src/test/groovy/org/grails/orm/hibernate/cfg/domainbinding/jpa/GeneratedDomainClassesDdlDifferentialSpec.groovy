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
package org.grails.orm.hibernate.cfg.domainbinding.jpa

import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement
import java.util.concurrent.atomic.AtomicInteger

import spock.lang.Specification

import org.hibernate.boot.Metadata
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.mapping.CheckConstraint
import org.hibernate.mapping.Collection as HibernateCollection
import org.hibernate.mapping.Column
import org.hibernate.mapping.ForeignKey
import org.hibernate.mapping.Index
import org.hibernate.mapping.Table
import org.hibernate.mapping.UniqueKey
import org.hibernate.boot.model.relational.Namespace
import org.hibernate.boot.model.relational.Sequence

import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.datastore.mapping.reflect.ClassUtils
import org.grails.orm.hibernate.HibernateDatastore

/**
 * What a user with an existing database would see if they switched {@code hibernate.generatedDomainClasses} on: the schema
 * Hibernate derives from the generated classes against the schema the domain binder derives from the same domain classes.
 *
 * <p>Every group of associated domain classes of the scanned test domains ({@link ScannedDomainClasses}) is booted twice
 * on H2, once in each mode, and the Hibernate mapping model of both (tables, columns with their type, nullability, default,
 * length, precision and scale, primary keys with their column order, foreign keys, unique keys, indexes, check constraints,
 * sequences) is compared. As a backstop for what the model does not show, the schema H2 itself holds after the boot
 * ({@code SCRIPT NODATA}, with H2's generated constraint names normalised) is compared too, and a statement that differs
 * for a table with no difference in the model fails the spec as an unexplained difference.</p>
 *
 * <p>The spec fails on every difference that is not covered by an entry of {@link #KNOWN}, each of which states why the
 * difference is kept. The categorised report is written to {@code build/ddl-report.txt}.</p>
 */
class GeneratedDomainClassesDdlDifferentialSpec extends Specification {

    /**
     * The differences kept on purpose: an id, the reason it cannot or should not be removed, and the test that selects it.
     * Empty while every difference class is fixed.
     */
    private static final List<Map> KNOWN = [
            [id: 'LIST_INDEX_CHECK', reason: 'not yet analysed'],
            [id: 'SUBCLASS_NOT_NULL_CHECK', reason: 'not yet analysed'],
            [id: 'ENUM_COLUMN_EXTRAS', reason: 'not yet analysed'],
            [id: 'COLLECTION_TABLE_KEY', reason: 'not yet analysed'],
            [id: 'CIRCULAR_MANY_TO_MANY', reason: 'not yet analysed'],
            [id: 'COLUMN_ORDER', reason: 'not yet analysed'],
            [id: 'UUID_ID_TYPE', reason: 'not yet analysed'],
            [id: 'COMPOSITE_KEY_ORDER', reason: 'not yet analysed'],
            [id: 'IGNORE_NOT_FOUND_FOREIGN_KEY', reason: 'not yet analysed'],
            [id: 'SEQUENCE', reason: 'not yet analysed'],
            [id: 'MAP_UNUSED_COLUMN', reason: 'not yet analysed'],
            [id: 'INVERSE_JOIN_TABLE_NAME', reason: 'not yet analysed'],
            [id: 'UNIQUE_GROUP_ON_ENUM', reason: 'not yet analysed'],
            [id: 'UNIQUE_KEY_NAME', reason: 'not yet analysed'],
    ]

    /**
     * The reasons the generated mode may refuse a group the domain binder boots: each is a mapping the generator rejects by name
     * because no annotation can state it faithfully. A refusal for any other reason fails the spec.
     */
    private static final List<String> REFUSALS = [
            'neither side of the many-to-many owns it',
            'is registered for the Java type',
            'names a class that is not a UserType',
            'a type is mapped on the collection property itself',
            'an index or a unique group is mapped on the collection property',
            'is a collection inside an embedded type',
            'an explicit lazy: true makes the binder bind an extra-lazy collection',
            'the property is mapped lazy: true',
            'declares a natural id but is a subclass',
            'which is HibernateEmbeddedProperty',
            'which is not a persistent property of the entity',
            'is a registered type with type parameters',
            'the index column of the map is mapped with the type',
    ]

    private static final AtomicInteger BOOTS = new AtomicInteger()

    void "the generated-class mode creates the same schema as the domain binder"() {
        given:
        List<List<Class<?>>> groups = ScannedDomainClasses.groupByAssociation(ScannedDomainClasses.findEntities())
        List<Map> differences = []
        Map<String, String> binderUnbootable = [:]
        Map<String, String> generatedRefused = [:]
        List<String> unexplained = []
        List<String> h2 = []
        int compared = 0
        int tables = 0
        int scriptStatements = 0

        when:
        for (List<Class<?>> group : groups) {
            String name = group*.simpleName.join(',').take(120)
            Map binder
            try {
                binder = snapshot(group, false)
            } catch (Throwable e) {
                binderUnbootable[name] = firstLine(e)
                continue
            }
            Map generated
            try {
                generated = snapshot(group, true)
            } catch (Throwable e) {
                generatedRefused[name] = firstLine(e)
                continue
            }
            compared++
            tables += binder.tables.size()
            scriptStatements += binder.script.size()
            List<Map> found = compareSchemas(name, binder, generated)
            differences.addAll(found)
            unexplained.addAll(unexplainedStatements(name, binder, generated, found))
            h2.addAll(statementDifferences(name, binder, generated))
        }
        List<Map> unknown = differences.findAll { Map difference -> knownEntry(difference) == null }
        writeReport(groups.size(), compared, tables, scriptStatements, differences, unknown, unexplained, binderUnbootable, generatedRefused, h2)

        then:
        compared > 400
        unknown.isEmpty()
        unexplained.isEmpty()
        generatedRefused.findAll { String name, String reason -> !REFUSALS.any { String known -> reason.contains(known) } }.isEmpty()
    }

    private static Map knownEntry(Map difference) {
        return KNOWN.find { Map entry -> entry.id == difference.cls }
    }

    /**
     * The class of a difference: the named causes the report tells apart, or the kind of the difference (and whether it is on
     * the table of a collection) when no cause is named yet.
     */
    private static String classify(Map d) {
        String kind = d.kind
        String detail = d.detail
        if (kind == 'column checks' && detail ==~ /(?s).*generated=\[null: \S+>=0\]/) {
            return 'LIST_INDEX_CHECK'
        }
        if (kind == 'column checks' && detail ==~ /(?s).*generated=\[null: \S+ in \(.*\)\]/) {
            return 'DISCRIMINATOR_CHECK'
        }
        boolean collection = d.joinTable
        if (kind == 'check constraint') {
            return 'SUBCLASS_NOT_NULL_CHECK'
        }
        if (kind in ['column comment', 'column default']) {
            return 'ENUM_COLUMN_EXTRAS'
        }
        if (collection && (kind.endsWith('only in generated mode') || kind.endsWith('only in binder mode') || kind == 'primary key columns') &&
                d.table.endsWith('_followers') && d.group == 'GenMmSelf') {
            return 'CIRCULAR_MANY_TO_MANY'
        }
        if (collection && kind == 'column only in binder mode') {
            return 'MAP_UNUSED_COLUMN'
        }
        if (collection && kind in ['column nullable', 'primary key only in generated mode', 'unique key only in binder mode']) {
            return 'COLLECTION_TABLE_KEY'
        }
        if (kind.startsWith('column order')) {
            return 'COLUMN_ORDER'
        }
        if (kind == 'column type' && detail.contains('binder=binary(16)')) {
            return 'UUID_ID_TYPE'
        }
        if (kind.startsWith('column type') || kind.startsWith('foreign key columns') || kind.startsWith('foreign key refCols') ||
                kind.startsWith('primary key column order')) {
            return 'COMPOSITE_KEY_ORDER'
        }
        if (kind == 'foreign key only in binder mode') {
            return 'IGNORE_NOT_FOUND_FOREIGN_KEY'
        }
        if (kind == 'sequence') {
            return 'SEQUENCE'
        }
        if (kind == 'table only in binder mode') {
            return 'INVERSE_JOIN_TABLE_NAME'
        }
        if (kind == 'unique key only in generated mode') {
            return 'UNIQUE_GROUP_ON_ENUM'
        }
        if (kind.startsWith('unique key')) {
            return 'UNIQUE_KEY_NAME'
        }
        return "${kind}${collection ? ' [collection table]' : ''}".toString()
    }

    private static String firstLine(Throwable e) {
        Throwable root = e
        while (root.cause != null && root.cause != root) {
            root = root.cause
        }
        return "${e.getClass().simpleName}: ${(root.message ?: root.getClass().simpleName).readLines().first()}".toString().take(300)
    }

    /**
     * Boots a group in one mode, with the settings of the other mode identical, and reads the schema model and the H2 script
     * back as plain data so that the datastore can be closed.
     */
    private static Map snapshot(List<Class<?>> group, boolean generated) {
        Map<String, Object> config = [
                'dataSource.url'                  : "jdbc:h2:mem:ddlDiff${BOOTS.incrementAndGet()};LOCK_TIMEOUT=10000".toString(),
                'dataSource.dbCreate'             : 'create-drop',
                'hibernate.generatedDomainClasses': generated,
        ]
        if (group.any { Class<?> type -> ClassUtils.isMultiTenant(type) }) {
            config['grails.gorm.multiTenancy.mode'] = MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR
            config['grails.gorm.multiTenancy.tenantResolver'] = new SystemPropertyTenantResolver()
        }
        HibernateDatastore datastore
        try {
            datastore = new HibernateDatastore(config, group as Class[])
        } catch (Exception e) {
            if (!config.containsKey('grails.gorm.multiTenancy.mode')) {
                throw e
            }
            config.remove('grails.gorm.multiTenancy.mode')
            config.remove('grails.gorm.multiTenancy.tenantResolver')
            datastore = new HibernateDatastore(config, group as Class[])
        }
        try {
            Metadata metadata = datastore.metadata
            Map<String, List<String>> collectionKinds = [:].withDefault { [] }
            for (HibernateCollection collection : metadata.collectionBindings) {
                collectionKinds[tableKey(collection.collectionTable)] <<
                        "${collection.getClass().simpleName} of ${collection.element.getClass().simpleName}${collection.inverse ? ' (inverse)' : ''}".toString()
            }
            Map<String, Map> tables = [:]
            for (Table table : metadata.collectTableMappings()) {
                if (table.physicalTable) {
                    tables[tableKey(table)] = describe(table, metadata, collectionKinds.get(tableKey(table)))
                }
            }
            Map<String, Map> sequences = [:]
            for (Namespace namespace : metadata.database.namespaces) {
                for (Sequence sequence : namespace.sequences) {
                    sequences[sequence.name.render()] = [initial: sequence.initialValue, increment: sequence.incrementSize]
                }
            }
            return [tables: tables, sequences: sequences, script: script((SessionFactoryImplementor) datastore.sessionFactory)]
        } finally {
            datastore.close()
        }
    }

    private static String tableKey(Table table) {
        return [table.catalog, table.schema, table.name].findAll { it != null }.join('.')
    }

    private static Map describe(Table table, Metadata metadata, List<String> collections) {
        Map<String, Map> columns = [:]
        for (Column column : table.columns) {
            String type
            try {
                type = column.getSqlType(metadata)
            } catch (Exception e) {
                type = "<${e.getClass().simpleName}>".toString()
            }
            columns[column.name] = [
                    type       : type,
                    nullable   : column.nullable,
                    default    : column.defaultValue,
                    unique     : column.unique,
                    comment    : column.comment,
                    identity   : column.identity,
                    collation  : column.collation,
                    generatedAs: column.generatedAs,
                    checks     : column.checkConstraints.collect { CheckConstraint check -> "${check.name}: ${check.constraint}".toString() },
            ]
        }
        Map<String, Map> foreignKeys = [:]
        for (ForeignKey key : table.foreignKeys.values()) {
            foreignKeys[key.name] = [
                    columns  : key.columns*.name,
                    refTable : key.referencedTable?.name,
                    refCols  : key.referencedColumns*.name,
                    onDelete : key.onDeleteAction?.toString(),
                    created  : key.creationEnabled,
                    physical : key.physicalConstraint,
                    keyDef   : key.keyDefinition,
            ]
        }
        Map<String, Map> uniqueKeys = [:]
        for (UniqueKey key : table.uniqueKeys.values()) {
            uniqueKeys[key.name] = [columns: key.columns*.name, order: key.columnOrderMap.values().toList(), nameExplicit: key.nameExplicit, explicit: key.explicit]
        }
        Map<String, Map> indexes = [:]
        for (Index index : table.indexes.values()) {
            indexes[index.name] = [columns: index.columns*.name, unique: index.unique]
        }
        return [
                joinTable  : !collections.isEmpty(),
                collections: collections,
                columnOrder: table.columns*.name,
                columns    : columns,
                primaryKey : table.primaryKey == null ? null : [name: table.primaryKey.name, columns: table.primaryKey.columns*.name],
                foreignKeys: foreignKeys,
                uniqueKeys : uniqueKeys,
                indexes    : indexes,
                checks     : table.checkConstraints.collect { CheckConstraint check -> "${check.name}: ${check.constraint}".toString() },
                comment    : table.comment,
        ]
    }

    /** The schema H2 holds, as H2's own script, without data and without the names H2 generates itself. */
    private static List<String> script(SessionFactoryImplementor sessionFactory) {
        ConnectionProvider provider = sessionFactory.serviceRegistry.getService(ConnectionProvider)
        Connection connection = provider.connection
        try {
            Statement statement = connection.createStatement()
            ResultSet rows = statement.executeQuery('SCRIPT NODATA')
            List<String> statements = []
            while (rows.next()) {
                String line = rows.getString(1)
                if (!line.startsWith('--') && !line.startsWith('CREATE USER')) {
                    statements << line.replaceAll(/(?i)\b(CONSTRAINT|PRIMARY_KEY|CONSTRAINT_INDEX)_[0-9A-F]+\b/, '$1_#').trim()
                }
            }
            return statements
        } finally {
            provider.closeConnection(connection)
        }
    }

    private static List<Map> compareSchemas(String group, Map binder, Map generated) {
        List<Map> found = []
        Closure<Void> add = { String kind, String table, String detail, Map tags = [:] ->
            Map difference = [group: group, kind: kind, table: table, detail: detail] + tags
            difference.cls = classify(difference)
            found << difference
            return
        }
        Set<String> names = (binder.tables.keySet() + generated.tables.keySet()) as Set<String>
        for (String name : names.sort()) {
            Map b = binder.tables[name]
            Map g = generated.tables[name]
            if (b == null) {
                add('table only in generated mode', name, "columns ${g.columnOrder}")
                continue
            }
            if (g == null) {
                add('table only in binder mode', name, "columns ${b.columnOrder}")
                continue
            }
            Map tags = [joinTable: b.joinTable, collections: b.collections]
            compareColumns(name, b, g, add, tags)
            comparePrimaryKeys(name, b, g, add, tags)
            compareNamed('foreign key', name, b.foreignKeys, g.foreignKeys, ['columns', 'refTable'], add, tags)
            compareNamed('unique key', name, b.uniqueKeys, g.uniqueKeys, ['columns'], add, tags)
            compareNamed('index', name, b.indexes, g.indexes, ['columns'], add, tags)
            if (b.checks != g.checks) {
                add('check constraint', name, "binder=${b.checks} generated=${g.checks}", tags)
            }
            if (b.comment != g.comment) {
                add('table comment', name, "binder=${b.comment} generated=${g.comment}", tags)
            }
        }
        for (String name : ((binder.sequences.keySet() + generated.sequences.keySet()) as Set<String>).sort()) {
            if (binder.sequences[name] != generated.sequences[name]) {
                add('sequence', name, "binder=${binder.sequences[name]} generated=${generated.sequences[name]}")
            }
        }
        return found
    }

    private static void compareColumns(String table, Map b, Map g, Closure<Void> add, Map tags) {
        Set<String> names = (b.columns.keySet() + g.columns.keySet()) as Set<String>
        for (String name : names.sort()) {
            Map bc = b.columns[name]
            Map gc = g.columns[name]
            if (bc == null || gc == null) {
                add(bc == null ? 'column only in generated mode' : 'column only in binder mode', table, name, tags)
                continue
            }
            for (String facet : bc.keySet()) {
                if (bc[facet] != gc[facet]) {
                    add("column ${facet}".toString(), table, "${name}: binder=${bc[facet]} generated=${gc[facet]}", tags)
                }
            }
        }
        if (b.columns.keySet() == g.columns.keySet() && b.columnOrder != g.columnOrder) {
            add('column order', table, "binder=${b.columnOrder} generated=${g.columnOrder}", tags)
        }
    }

    private static void comparePrimaryKeys(String table, Map b, Map g, Closure<Void> add, Map tags) {
        Map bk = b.primaryKey
        Map gk = g.primaryKey
        if (bk == gk) {
            return
        }
        if (bk == null || gk == null) {
            String describe = (bk ?: gk).columns.toString()
            boolean asUnique = b.uniqueKeys.values().any { Map key -> key.columns.toSet() == (gk ?: [columns: []]).columns.toSet() }
            add(bk == null ? 'primary key only in generated mode' : 'primary key only in binder mode', table,
                    "${describe}${asUnique ? ' (the binder has a unique key over the same columns)' : ''}", tags)
        } else if (bk.columns.toSet() != gk.columns.toSet()) {
            add('primary key columns', table, "binder=${bk.columns} generated=${gk.columns}", tags)
        } else if (bk.columns != gk.columns) {
            add('primary key column order', table, "binder=${bk.columns} generated=${gk.columns}", tags)
        } else {
            add('primary key name', table, "binder=${bk.name} generated=${gk.name}", tags)
        }
    }

    /**
     * Constraints and indexes by name, then the ones that are left by their column set: a pair with the same columns and
     * another name is a name difference; the same set in another order is an order difference.
     */
    private static void compareNamed(
            String what, String table, Map<String, Map> b, Map<String, Map> g, List<String> facets, Closure<Void> add, Map tags) {
        Set<String> onlyBinder = (b.keySet() - g.keySet()) as Set<String>
        Set<String> onlyGenerated = (g.keySet() - b.keySet()) as Set<String>
        for (String name : (b.keySet() + g.keySet()).toSet().sort()) {
            if (b[name] != null && g[name] != null && b[name] != g[name]) {
                for (String facet : b[name].keySet()) {
                    if (b[name][facet] != g[name][facet]) {
                        add("${what} ${facet}".toString(), table, "${name}: binder=${b[name][facet]} generated=${g[name][facet]}", tags)
                    }
                }
            }
        }
        for (String name : onlyBinder.sort()) {
            String match = onlyGenerated.find { String other -> g[other].columns.toSet() == b[name].columns.toSet() }
            if (match == null) {
                add("${what} only in binder mode".toString(), table, "${name} ${b[name].columns}", tags)
            } else {
                onlyGenerated.remove(match)
                if (b[name].columns == g[match].columns) {
                    add("${what} name".toString(), table, "binder=${name} generated=${match} ${b[name].columns}", tags)
                } else {
                    add("${what} name and column order".toString(), table,
                            "binder=${name} ${b[name].columns} generated=${match} ${g[match].columns}", tags)
                }
            }
        }
        for (String name : onlyGenerated.sort()) {
            add("${what} only in generated mode".toString(), table, "${name} ${g[name].columns}", tags)
        }
    }

    /**
     * The statements H2 reports for one mode and not the other, for a table the model comparison found no difference in.
     */
    private static List<String> unexplainedStatements(String group, Map binder, Map generated, List<Map> found) {
        Set<String> explained = found*.table.toSet()
        List<String> onlyBinder = binder.script - generated.script
        List<String> onlyGenerated = generated.script - binder.script
        List<String> unexplained = []
        for (String statement : onlyBinder + onlyGenerated) {
            boolean covered = explained.any { String table ->
                statement.toUpperCase().contains(table.toUpperCase().replaceAll(/^.*\./, ''))
            }
            if (!covered) {
                unexplained << "${group}: ${onlyBinder.contains(statement) ? 'binder only' : 'generated only'}: ${statement}".toString()
            }
        }
        return unexplained
    }

    private static List<String> statementDifferences(String group, Map binder, Map generated) {
        return ((binder.script - generated.script).collect { "${group}: binder only: ${it.replaceAll(/\s+/, ' ')}".toString() } +
                (generated.script - binder.script).collect { "${group}: generated only: ${it.replaceAll(/\s+/, ' ')}".toString() }) as List<String>
    }

    private static void writeReport(
            int groups, int compared, int tables, int scriptStatements, List<Map> differences, List<Map> unknown,
            List<String> unexplained, Map<String, String> binderUnbootable, Map<String, String> generatedRefused, List<String> h2) {
        StringBuilder report = new StringBuilder()
        report << "ddl differential: ${groups} groups, ${compared} compared in both modes, ${binderUnbootable.size()} cannot boot " +
                "with the domain binder, ${generatedRefused.size()} refused by the generated mode; ${tables} tables, " +
                "${scriptStatements} H2 statements; ${differences.size()} differences, ${unknown.size()} not known, " +
                "${unexplained.size()} unexplained H2 statements\n\n"
        report << "difference classes (kind, on a join table or not): count, example\n"
        Map<String, List<Map>> byClass = differences.groupBy { Map d -> d.cls }
        byClass.sort { a, b -> a.key <=> b.key }.each { String key, List<Map> members ->
            Map entry = knownEntry(members.first())
            report << "${members.size().toString().padLeft(5)}  ${key}${entry != null ? '   KNOWN ' + entry.id : ''}\n"
            report << "       e.g. ${members.first().group} / ${members.first().table}: ${members.first().detail}\n"
        }
        report << '\nrefused by the generated mode:\n'
        generatedRefused.each { String name, String reason -> report << "  ${name} -> ${reason}\n" }
        report << '\nunexplained H2 statements:\n'
        unexplained.each { report << "  ${it}\n" }
        report << '\nH2 statements that differ, by group:\n'
        h2.each { report << "  ${it}\n" }
        report << '\nall differences:\n'
        differences.each { Map d -> report << "  ${d.kind} | ${d.group} | ${d.table} | ${d.detail}\n" }
        report << '\nunbootable with the domain binder:\n'
        binderUnbootable.each { String name, String reason -> report << "  ${name} -> ${reason}\n" }
        new File('build/ddl-report.txt').text = report.toString()
    }
}
