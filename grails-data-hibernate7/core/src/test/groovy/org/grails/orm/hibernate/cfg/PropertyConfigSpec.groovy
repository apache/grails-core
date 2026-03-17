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

package org.grails.orm.hibernate.cfg

import org.hibernate.FetchMode
import spock.lang.Specification

/**
 * Unit spec for {@link PropertyConfig}.
 * Placed in the same package to access protected methods directly.
 */
class PropertyConfigSpec extends Specification {

    // ─── column(String) ──────────────────────────────────────────────────────

    void "column(String) adds a new ColumnConfig with the given name"() {
        given:
        PropertyConfig pc = new PropertyConfig()

        when:
        pc.column('my_col')

        then:
        pc.columns.size() == 1
        pc.columns[0].name == 'my_col'
    }

    void "column(String) adds a second ColumnConfig when called twice normally"() {
        given:
        PropertyConfig pc = new PropertyConfig()

        when:
        pc.column('col_a')
        pc.column('col_b')

        then:
        pc.columns.size() == 2
    }

    void "column(String) replaces name in-place when firstColumnIsColumnCopy is true"() {
        given:
        PropertyConfig pc = new PropertyConfig()
        pc.column('original')
        pc.firstColumnIsColumnCopy = true

        when:
        pc.column('replaced')

        then:
        pc.columns.size() == 1
        pc.columns[0].name == 'replaced'
        !pc.firstColumnIsColumnCopy
    }

    // ─── column(Map) ─────────────────────────────────────────────────────────

    void "column(Map) adds a ColumnConfig with the given name"() {
        given:
        PropertyConfig pc = new PropertyConfig()

        when:
        pc.column(name: 'map_col')

        then:
        pc.columns.size() == 1
        pc.columns[0].name == 'map_col'
    }

    void "column(Map) configures existing column in-place when firstColumnIsColumnCopy is true"() {
        given:
        PropertyConfig pc = new PropertyConfig()
        pc.column('original')
        pc.firstColumnIsColumnCopy = true

        when:
        pc.column(name: 'updated')

        then:
        pc.columns.size() == 1
        pc.columns[0].name == 'updated'
        !pc.firstColumnIsColumnCopy
    }

    // ─── column(Closure) ─────────────────────────────────────────────────────

    void "column(Closure) adds a ColumnConfig configured by the closure"() {
        given:
        PropertyConfig pc = new PropertyConfig()

        when:
        pc.column { name = 'closure_col' }

        then:
        pc.columns.size() == 1
        pc.columns[0].name == 'closure_col'
    }

    // ─── getColumn / single-column shortcuts ─────────────────────────────────

    void "getColumn returns null when no columns are configured"() {
        expect:
        new PropertyConfig().column == null
    }

    void "getColumn returns the column name when one column is configured"() {
        given:
        PropertyConfig pc = new PropertyConfig()
        pc.column('the_col')

        expect:
        pc.column == 'the_col'
    }

    void "getColumn throws when multiple columns are configured"() {
        given:
        PropertyConfig pc = new PropertyConfig()
        pc.columns << new ColumnConfig(name: 'a')
        pc.columns << new ColumnConfig(name: 'b')

        when:
        pc.column

        then:
        thrown(RuntimeException)
    }

    void "getSqlType returns null when no columns are configured"() {
        expect:
        new PropertyConfig().sqlType == null
    }

    void "getIndexName returns null when no columns are configured"() {
        expect:
        new PropertyConfig().indexName == null
    }

    void "getEnumType returns 'default' when no columns are configured"() {
        expect:
        new PropertyConfig().enumType == 'default'
    }

    void "getLength returns -1 when no columns are configured"() {
        expect:
        new PropertyConfig().length == -1
    }

    void "getPrecision returns -1 when no columns are configured"() {
        expect:
        new PropertyConfig().precision == -1
    }

    // ─── setUnique / isUnique ────────────────────────────────────────────────

    void "setUnique propagates to the single column when one column exists"() {
        given:
        PropertyConfig pc = new PropertyConfig()
        pc.column('u_col')

        when:
        pc.setUnique(true)

        then:
        pc.columns[0].unique
        pc.unique
    }

    void "isUnique delegates to super when no columns exist"() {
        given:
        PropertyConfig pc = new PropertyConfig()
        pc.setUnique(true)

        expect:
        pc.unique
    }

    void "isUnique delegates to super when multiple columns exist"() {
        given:
        PropertyConfig pc = new PropertyConfig()
        pc.columns << new ColumnConfig(name: 'a')
        pc.columns << new ColumnConfig(name: 'b')
        pc.setUnique(true)

        expect:
        pc.unique  // falls through to super.isUnique()
    }

    // ─── FetchMode ───────────────────────────────────────────────────────────

    void "setFetch(JOIN) maps to EAGER strategy"() {
        given:
        PropertyConfig pc = new PropertyConfig()

        when:
        pc.fetch = FetchMode.JOIN

        then:
        pc.fetchMode == FetchMode.JOIN
    }

    void "setFetch(SELECT) maps to LAZY strategy"() {
        given:
        PropertyConfig pc = new PropertyConfig()

        when:
        pc.fetch = FetchMode.SELECT

        then:
        pc.fetchMode == FetchMode.SELECT
    }

    void "getFetchMode returns DEFAULT when no strategy is set"() {
        expect:
        new PropertyConfig().fetchMode == FetchMode.DEFAULT
    }

    // ─── cache ───────────────────────────────────────────────────────────────

    void "cache(Closure) creates and configures a CacheConfig"() {
        given:
        PropertyConfig pc = new PropertyConfig()

        when:
        pc.cache { usage = 'read-only' }

        then:
        pc.cache != null
        pc.cache.usage.toString() == 'read-only'
    }

    void "cache(Map) creates and configures a CacheConfig"() {
        given:
        PropertyConfig pc = new PropertyConfig()

        when:
        pc.cache(usage: 'read-write')

        then:
        pc.cache != null
        pc.cache.usage.toString() == 'read-write'
    }

    // ─── joinTable ───────────────────────────────────────────────────────────

    void "joinTable(String) sets the join table name"() {
        given:
        PropertyConfig pc = new PropertyConfig()

        when:
        pc.joinTable('book_authors')

        then:
        pc.joinTable.name == 'book_authors'
    }

    void "joinTable(Closure) configures the JoinTable"() {
        given:
        PropertyConfig pc = new PropertyConfig()

        when:
        pc.joinTable { name = 'jt_table' }

        then:
        pc.joinTable.name == 'jt_table'
    }

    void "joinTable(Map) sets table name and key column via map"() {
        given:
        PropertyConfig pc = new PropertyConfig()

        when:
        pc.joinTable(name: 'book_tag', key: 'book_id', column: 'tag_id')

        then:
        pc.joinTable.name == 'book_tag'
        pc.joinTable.key?.name == 'book_id'
        pc.joinTable.column?.name == 'tag_id'
    }

    void "hasJoinKeyMapping returns false when no join table key is set"() {
        expect:
        !new PropertyConfig().hasJoinKeyMapping()
    }

    void "hasJoinKeyMapping returns true when a join table key is set"() {
        given:
        PropertyConfig pc = new PropertyConfig()
        pc.joinTable { key 'author_id' }

        expect:
        pc.hasJoinKeyMapping()
    }

    // ─── indexColumn ─────────────────────────────────────────────────────────

    void "indexColumn(Closure) creates and configures the index column PropertyConfig"() {
        given:
        PropertyConfig pc = new PropertyConfig()

        when:
        pc.indexColumn { column('idx_col') }

        then:
        pc.indexColumn != null
        pc.indexColumn.column == 'idx_col'
    }

    // ─── scale ───────────────────────────────────────────────────────────────

    void "setScale sets scale on the existing column"() {
        given:
        PropertyConfig pc = new PropertyConfig()
        pc.column('s_col')

        when:
        pc.scale = 4

        then:
        pc.scale == 4
        pc.columns[0].scale == 4
    }

    void "setScale delegates to super when no columns are configured"() {
        given:
        PropertyConfig pc = new PropertyConfig()

        when:
        pc.scale = 3

        then:
        pc.scale == 3
    }

    // ─── checkHasSingleColumn (protected, same-package access) ───────────────

    void "checkHasSingleColumn passes silently for zero columns"() {
        expect:
        new PropertyConfig().checkHasSingleColumn()
    }

    void "checkHasSingleColumn passes silently for exactly one column"() {
        given:
        PropertyConfig pc = new PropertyConfig()
        pc.column('one')

        expect:
        pc.checkHasSingleColumn()
    }

    void "checkHasSingleColumn throws for two or more columns"() {
        given:
        PropertyConfig pc = new PropertyConfig()
        pc.columns << new ColumnConfig(name: 'x')
        pc.columns << new ColumnConfig(name: 'y')

        when:
        pc.checkHasSingleColumn()

        then:
        thrown(RuntimeException)
    }

    // ─── clone ───────────────────────────────────────────────────────────────

    void "clone produces an independent deep copy of columns"() {
        given:
        PropertyConfig pc = new PropertyConfig()
        pc.column('orig')

        when:
        PropertyConfig cloned = pc.clone()
        cloned.columns[0].name = 'changed'

        then:
        pc.columns[0].name == 'orig'
    }

    void "clone copies cache independently"() {
        given:
        PropertyConfig pc = new PropertyConfig()
        pc.cache { usage = 'read-only' }

        when:
        PropertyConfig cloned = pc.clone()
        cloned.cache.usage = 'read-write'

        then:
        pc.cache.usage.toString() == 'read-only'
    }

    void "clone copies indexColumn independently"() {
        given:
        PropertyConfig pc = new PropertyConfig()
        pc.indexColumn { column('idx') }

        when:
        PropertyConfig cloned = pc.clone()

        then:
        cloned.indexColumn != null
        !cloned.indexColumn.is(pc.indexColumn)
    }

    // ─── static factories ─────────────────────────────────────────────────────

    void "configureNew(Closure) creates a PropertyConfig configured by the closure"() {
        when:
        PropertyConfig pc = PropertyConfig.configureNew { type = 'string' }

        then:
        pc != null
        pc.type == 'string'
    }

    void "configureNew(Map) creates a PropertyConfig from a map"() {
        when:
        PropertyConfig pc = PropertyConfig.configureNew([column: 'map_col'])

        then:
        pc != null
        pc.column == 'map_col'
    }

    void "configureExisting(Map) updates an existing PropertyConfig"() {
        given:
        PropertyConfig pc = new PropertyConfig()

        when:
        PropertyConfig result = PropertyConfig.configureExisting(pc, [column: 'updated_col'])

        then:
        result.is(pc)
        result.column == 'updated_col'
    }

    void "configureExisting(Closure) delegates the closure to the PropertyConfig"() {
        given:
        PropertyConfig pc = new PropertyConfig()

        when:
        PropertyConfig result = PropertyConfig.configureExisting(pc) { type = 'integer' }

        then:
        result.is(pc)
        result.type == 'integer'
    }

    // ─── deprecated updateable ───────────────────────────────────────────────

    void "getUpdateable delegates to updatable"() {
        given:
        PropertyConfig pc = new PropertyConfig()

        when:
        pc.updatable = false

        then:
        !pc.updateable
    }

    void "setUpdateable delegates to updatable"() {
        given:
        PropertyConfig pc = new PropertyConfig()

        when:
        pc.updateable = false

        then:
        !pc.updatable
    }

}
