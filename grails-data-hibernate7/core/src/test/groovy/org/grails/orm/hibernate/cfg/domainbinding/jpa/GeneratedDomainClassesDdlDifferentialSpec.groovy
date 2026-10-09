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
import spock.lang.Unroll

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
     * The difference classes kept on purpose, each with the reason it cannot or should not be removed ({@code classify} names them).
     */
    private static final List<Map> KNOWN = [
            [id: 'BINDER_DUPLICATE_FOREIGN_KEY', reason: 'A binder defect: a bidirectional association to an entity with a composite identifier gets two foreign keys of the same name over the same columns, one from the to-one side (referenced columns stated) and one from the collection key (referenced columns left to the primary key), listed in two column orders. Hibernate creates one constraint of that name; which of the two it is depends on the order the domain classes are bound in. The generated mode has the one key and, like the binder in H2, orders its columns as the sorted identifier. 1 association in the scanned test domains (CompositeIdParent.children).'],
            [id: 'CLOSURE_INDEX_NAME', reason: 'A closure mapped as the index of a collection property (index: { column name: ... }, which configures the index column of a list or a map): the binder also reads it as the name of an index of the collection table and names it with the closure\'s toString(), which holds the identity of the closure instance (...$_closure2@3ff5aef4) and differs on every boot. A name that cannot be reproduced is not stated: the generated mode creates the index column the closure configures and no index. (An index mapped as a map or a string is created with the name the binder gives it, however odd, so the schema is the same.)'],
            [id: 'COMPOSITE_KEY_TYPE_SWAP', reason: 'A binder defect: for a foreign key to a composite identifier whose part is itself a to-one to a composite identifier, the binder names the foreign key columns after the parts in the mapped order but gives them the types of the sorted referenced key, so a part of another type than its neighbour gets the NAME of the other part (child.parent_grand_parent_name is INTEGER, referencing the integer luckyNumber). The generated mode names them correctly; matching it would mean mislabelling columns on purpose. Fixed on the 8.0.x line (PR 16539), so the class disappears with the next up-merge. The cause is SimpleValue.sortColumns(int[]), which applies the permutation of the identifier\'s parts (one entry for each part) to the foreign key\'s columns (several for a nested part). The order of the columns and of the primary key of such a table follows from the swapped types (Hibernate orders them by size and name), and the order of the foreign key columns and of the columns it references over a nested part is the same permutation applied to the wrong list, so those differences are part of this class; a to-one to a composite identifier whose parts are plain is matched (see the aligner). 3 groups of the scanned test domains. After the up-merge of the 8.0.x fix this class has to be measured again: the nested foreign key order may remain.'],
            [id: 'IGNORE_NOT_FOUND_FOREIGN_KEY', reason: 'ignoreNotFound: true: Hibernate\'s @NotFound(IGNORE) disables the foreign key (SimpleValue.disableForeignKey, there is no enabling counterpart), the binder keeps it, which makes the option contradict itself (a dangling reference cannot exist). Re-creating the key would need Table.createForeignKey with a name computed through the implicit naming strategy\'s internal ForeignKeyNameSource. Hibernate\'s own behaviour is arguably the right one; decision for the lead. 1 association in the scanned domains.'],
            [id: 'INVERSE_JOIN_TABLE_NAME', reason: 'A mapping that names the join table on both sides of a many-to-many with different names (the binder adopts the owning side\'s name only when the inverse side names none, since the 8.0.x fix for the inverse join table): the inverse side keeps its own name and the binder creates a second, unused table for it. The generated mode creates the table the owning side names (Hibernate derives the inverse side from mappedBy), so the unused table is absent. Nothing reads or writes the binder\'s extra table. 1 table in the scanned domains (CBOwnNameOwner and CBOwnNameInverse).'],
            [id: 'LIST_INDEX_CHECK', reason: 'Hibernate adds check (<index column> >= 0) to the index column of every list (IndexColumn.addIndexCheckConstraint, always, for @OrderColumn) and offers no annotation to avoid it; Column.getCheckConstraints() is unmodifiable and Column has no removal method (Column.copy shares the list), so it cannot be removed through public API, only by reflection on the private list, which is not done. The check can never reject a value GORM writes (indexes start at 0). 18 list columns in the scanned domains. Decision for the lead: accept the check.'],
            [id: 'MAP_MANY_TO_MANY_ELEMENT_ORDER', reason: 'A binder defect that depends on the order the classes are bound in: the element column of a map on a many-to-many is also the key column of the set that reads the map, one column of the join table, and the binder sets its nullability twice (the element binder: not null; the key updater of the inverse set: nullable), so a database created by the binder has a nullable column or a not null one depending on which of the two collections is bound last (pinned in GeneratedDomainClassesMapManyToManySpec with the same classes in both orders). The generated mode states not null, the nullability of the element of any many-to-many, whatever the order. 1 group of the scanned test domains (MmmOwnerLeft and MmmOwnedRight, a map beside a set that belongs to the class of the map).'],
            [id: 'MAP_ELEMENT_NULLABLE', reason: 'The mapping of a map of values states nullable: false on the element column and the binder leaves the column nullable (it ignores the option, like the enum column extras); the generated mode honours the mapping, so a database created by the binder has a nullable column where the generated mode creates NOT NULL. Matching the binder would drop a constraint the mapping states.'],
            [id: 'MAP_UNUSED_COLUMN', reason: 'The binder leaves an unused nullable column in the table of a map of values (the element it bound before the map replaced it, attributes_java_lang_string); the generated mode creates no such column. Nothing reads the extra column; an existing database keeps it (update does not drop columns).'],
            [id: 'UNIQUE_GROUP_ON_COLLECTION', reason: 'A binder defect (fixed on the 8.0.x line by PR 16533, so the class disappears with the next up-merge): a unique group mapped on a collection property makes the binder create a unique key on the collection table over the key column and the other properties of the group, which are columns of the owner\'s table and not of the collection table, so the key cannot be created (Hibernate logs the failed statement and boots; the key is not in the H2 script of the binder either). The generated mode creates no key, which is what the fix does.'],
            [id: 'UNIQUE_GROUP_ON_ENUM', reason: 'A classic binder defect (the native constraint is proven by ColumnOptionSpec): a unique group that includes an enum property is dropped by the binder. The generated mode creates the constraint the mapping states, so a database created by the binder lacks it and `update` would add it. 2 groups in the scanned domains.']
    ]

    private static final AtomicInteger BOOTS = new AtomicInteger()

    void "the generated-class mode creates the same schema as the domain binder"() {
        given:
        ClassicOracle oracle = new ClassicOracle('ddl-differential')
        List<List<Class<?>>> groups = ScannedDomainClasses.groupByAssociation(ScannedDomainClasses.findEntities())

        when:
        Map<String, Object> result = compareGroups(oracle, groups, null)
        oracle.finish()
        writeReport(groups.size(), result, oracle)

        then:
        result.compared > 400
        result.unknown.isEmpty()
        result.unexplained.isEmpty()
        result.generatedRefused.isEmpty()
        oracle.drift.isEmpty()
    }

    @Unroll
    void "a changed recorded fact (#label) is reported as a difference of the schemas"() {
        given: "the smallest group the oracle holds a table of"
        ClassicOracle intactOracle = new ClassicOracle('ddl-differential', ClassicOracle.Mode.FROZEN)
        List<Class<?>> group = ScannedDomainClasses.groupByAssociation(ScannedDomainClasses.findEntities()).findAll { List<Class<?>> candidate ->
            ClassicOracle.Section section = intactOracle.recordedSection(candidate.first().name)
            section != null && section.header.unbootable == null && !section.parsedByKey('table').isEmpty()
        }.min { List<Class<?>> candidate -> candidate.size() }

        when:
        Map<String, Object> intact = compareGroups(intactOracle, [group], null)
        Map<String, Object> changed = compareGroups(new ClassicOracle('ddl-differential', ClassicOracle.Mode.FROZEN), [group], perturbation)

        then: "the recorded schema alone passes, and the same schema with one thing changed does not"
        intact.compared == 1
        intact.unknown.isEmpty()
        intact.unexplained.isEmpty()
        changed.differences.size() > intact.differences.size() || changed.h2.size() > intact.h2.size()
        expectedKind == null || changed.differences.any { Map difference -> difference.kind == expectedKind }
        expectedKind != null || !changed.unexplained.isEmpty()

        where:
        label << ['the type of every column', 'a missing table', 'a missing H2 statement']
        perturbation << [
                { List<Class<?>> g, Map binder -> binder.tables.values().each { Map table -> table.columns.values().each { Map column -> column.type = 'changed' } } },
                { List<Class<?>> g, Map binder -> binder.tables.remove(binder.tables.keySet().first()) },
                { List<Class<?>> g, Map binder -> binder.script.remove(0) },
        ]
        expectedKind << ['column type', 'table only in generated mode', null]
    }

    /**
     * The comparison over the given groups, against the schemas the oracle holds. A {@code perturb} closure (called with the group and the
     * recorded schema, which it may change) lets a spec of the oracle itself prove that a changed recorded fact is reported.
     */
    static Map<String, Object> compareGroups(ClassicOracle oracle, List<List<Class<?>>> groups, Closure<?> perturb) {
        List<Map> differences = []
        Map<String, String> binderUnbootable = [:]
        Map<String, String> generatedRefused = [:]
        List<String> unexplained = []
        List<String> h2 = []
        int compared = 0
        int tables = 0
        int scriptStatements = 0
        for (List<Class<?>> group : groups) {
            String name = group*.simpleName.join(',').take(120)
            ClassicOracle.Section section = oracle.section(group.first().name, group*.name) { classicSection(group) }
            if (section.header.unbootable != null) {
                binderUnbootable[name] = section.header.unbootable.toString()
                continue
            }
            Map binder = recordedSnapshot(section)
            if (perturb != null) {
                perturb.call(group, binder)
            }
            Map generated
            try {
                generated = (Map) ClassicOracle.normalized(snapshot(group, true), true)
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
        return [differences: differences, unknown: differences.findAll { Map difference -> knownEntry(difference) == null },
                unexplained: unexplained, binderUnbootable: binderUnbootable, generatedRefused: generatedRefused, h2: h2,
                compared: compared, tables: tables, scriptStatements: scriptStatements]
    }

    /**
     * What the domain binder produces for a group, as a section of the oracle file: the snapshot of the schema it derived, or the first
     * line of the reason it cannot boot the group. Only called when the classic binder is booted (VERIFY and REFREEZE).
     */
    static ClassicOracle.Section classicSection(List<Class<?>> group) {
        Map schema
        try {
            schema = snapshot(group, false)
        } catch (Throwable e) {
            return new ClassicOracle.Section(group.first().name, [members: group*.name, unbootable: ClassicOracle.stableReason(firstLine(e))])
        }
        ClassicOracle.Section section = new ClassicOracle.Section(group.first().name, [members: group*.name])
        ((Map<String, Map>) schema.tables).keySet().sort().each { String table -> section.add('table', table, schema.tables[table], true) }
        ((Map<String, Map>) schema.sequences).keySet().sort().each { String sequence -> section.add('sequence', sequence, schema.sequences[sequence], true) }
        ((List<String>) schema.script).each { String statement -> section.add('script', '', statement, true) }
        return section
    }

    private static Map recordedSnapshot(ClassicOracle.Section section) {
        return [tables   : section.parsedByKey('table'),
                sequences: section.parsedByKey('sequence'),
                script   : section.parsed('script')]
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
        if (d.duplicateName) {
            return 'BINDER_DUPLICATE_FOREIGN_KEY'
        }
        if (kind == 'check constraint') {
            return 'SUBCLASS_NOT_NULL_CHECK'
        }
        if (kind in ['column comment', 'column default']) {
            return 'ENUM_COLUMN_EXTRAS'
        }
        if (collection && kind == 'column only in binder mode') {
            return 'MAP_UNUSED_COLUMN'
        }
        if (collection && kind == 'column nullable' && d.collections*.startsWith('Map of BasicValue').any()) {
            return 'MAP_ELEMENT_NULLABLE'
        }
        if (collection && kind == 'index only in binder mode' && (detail =~ /_closure\d+@[0-9a-f]+/).find()) {
            return 'CLOSURE_INDEX_NAME'
        }
        if (kind == 'unique key only in binder mode' && d.impossibleKey) {
            return 'UNIQUE_GROUP_ON_COLLECTION'
        }
        if (collection && kind == 'column nullable' && detail.contains('binder=true generated=false') &&
                d.collections*.startsWith('Map of ManyToOne').any() && d.collections*.endsWith('(inverse)').any()) {
            return 'MAP_MANY_TO_MANY_ELEMENT_ORDER'
        }
        if (collection && kind in ['column nullable', 'primary key only in generated mode', 'unique key only in binder mode']) {
            return 'COLLECTION_TABLE_KEY'
        }
        if (kind == 'column type' && detail.contains('binder=binary(16)')) {
            return 'UUID_ID_TYPE'
        }
        if (kind == 'column type' && !detail.contains('binder=binary(16)')) {
            return 'COMPOSITE_KEY_TYPE_SWAP'
        }
        if (kind == 'foreign key only in binder mode') {
            return 'IGNORE_NOT_FOUND_FOREIGN_KEY'
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
                    // a primary key column is not null in the schema whatever the column says
                    nullable   : column.nullable && !(table.primaryKey != null && table.primaryKey.columns.contains(column)),
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
                    // the domain binder can create two keys of one name over the same columns in two orders (see BINDER_DUPLICATE_FOREIGN_KEY)
                    duplicate: foreignKeys.containsKey(key.name),
                    columns  : key.columns*.name,
                    refTable : key.referencedTable?.name,
                    // a key with no referenced columns references the primary key of the table
                    refCols  : key.referencedColumns.isEmpty() ? key.referencedTable?.primaryKey?.columns*.name : key.referencedColumns*.name,
                    onDelete : key.onDeleteAction?.toString(),
                    created  : key.creationEnabled,
                    physical : key.physicalConstraint,
                    keyDef   : key.keyDefinition,
            ]
        }
        Map<String, Map> uniqueKeys = [:]
        for (UniqueKey key : table.uniqueKeys.values()) {
            // a key with no explicit name gets the database's own, so its name is not part of the schema
            uniqueKeys[key.nameExplicit ? key.name : "(unnamed over ${key.columns*.name.sort()})".toString()] = [columns: key.columns*.name, order: key.columnOrderMap.values().toList(), nameExplicit: key.nameExplicit, explicit: key.explicit]
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
        // the tables with a swapped column type: the order of their columns and of their primary key follows from the types
        Closure<Void> reclassify = {
            Set<String> swapped = found.findAll { Map d -> d.cls == 'COMPOSITE_KEY_TYPE_SWAP' }*.table.toSet()
            found.findAll { Map d -> d.kind in ['column order', 'primary key column order', 'foreign key columns', 'foreign key refCols'] && swapped.contains(d.table) }.each { Map d ->
                d.cls = 'COMPOSITE_KEY_TYPE_SWAP'
            }
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
            // a key over a column the table does not have cannot be created: the group names columns of the owner's table
            Map keyTags = tags + [impossibleKey: tags.joinTable && b.uniqueKeys.values().any { Map key -> key.columns.any { String column -> !b.columns.containsKey(column) } }]
            compareNamed('unique key', name, b.uniqueKeys, g.uniqueKeys, ['columns'], add, keyTags)
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
        reclassify()
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
                for (String facet : b[name].keySet() - 'duplicate') {
                    if (b[name][facet] != g[name][facet]) {
                        add("${what} ${facet}".toString(), table, "${name}: binder=${b[name][facet]} generated=${g[name][facet]}", tags + [duplicateName: b[name].duplicate == true])
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

    private static void writeReport(int groups, Map<String, Object> result, ClassicOracle oracle) {
        int compared = (int) result.compared
        int tables = (int) result.tables
        int scriptStatements = (int) result.scriptStatements
        List<Map> differences = (List<Map>) result.differences
        List<Map> unknown = (List<Map>) result.unknown
        List<String> unexplained = (List<String>) result.unexplained
        Map<String, String> binderUnbootable = (Map<String, String>) result.binderUnbootable
        Map<String, String> generatedRefused = (Map<String, String>) result.generatedRefused
        List<String> h2 = (List<String>) result.h2
        StringBuilder report = new StringBuilder()
        report << "ddl differential: ${groups} groups, ${compared} compared in both modes, ${binderUnbootable.size()} cannot boot " +
                "with the domain binder, ${generatedRefused.size()} refused by the generated mode; ${tables} tables, " +
                "${scriptStatements} H2 statements; ${differences.size()} differences, ${unknown.size()} not known, " +
                "${unexplained.size()} unexplained H2 statements\n"
        report << "classic oracle: ${oracle.mode}${oracle.drift.isEmpty() ? '' : ", ${oracle.drift.size()} recorded groups differ from the live classic binder"}\n"
        oracle.drift.each { report << "  DRIFT ${it}\n" }
        report << '\n'
        report << "difference classes (kind, on a join table or not): count, example\n"
        Map<String, List<Map>> byClass = differences.groupBy { Map d -> d.cls }
        byClass.sort { a, b -> a.key <=> b.key }.each { String key, List<Map> members ->
            Map entry = knownEntry(members.first())
            report << "${members.size().toString().padLeft(5)}  ${key}${entry != null ? '   KNOWN' : ''}\n"
            if (entry != null) {
                report << "       reason: ${entry.reason}\n"
            }
            report << "       e.g. ${members.first().group} / ${members.first().table}: ${members.first().detail}\n"
            members.groupBy { Map d -> "${d.kind} on ${d.collections}".toString() }.sort().each { String shape, List<Map> shaped ->
                report << "       - ${shaped.size()} x ${shape}\n"
            }
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
