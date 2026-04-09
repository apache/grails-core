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
package org.grails.orm.hibernate.query

import org.grails.datastore.mapping.model.PersistentEntity
import spock.lang.Specification
import spock.lang.Unroll

class HqlQueryContextSpec extends Specification {

    // ─── Record construction ──────────────────────────────────────────────────

    void "record accessors return constructor values"() {
        given:
        def params = [name: "Alice"]
        def ctx = new HqlQueryContext("from Person", String, params, null, [:], false, false)

        expect:
        ctx.hql()         == "from Person"
        ctx.targetClass() == String
        ctx.namedParams() == [name: "Alice"]
        !ctx.isUpdate()
        !ctx.isNative()
    }

    void "record isUpdate and isNative flags are set correctly"() {
        given:
        def ctx = new HqlQueryContext("update Foo set x=1", Object, [:], null, [:], true, true)

        expect:
        ctx.isUpdate()
        ctx.isNative()
    }

    // ─── prepare ─────────────────────────────────────────────────────────────

    void "prepare with plain String produces correct hql and empty namedParams"() {
        given:
        def entity = Mock(PersistentEntity) { getJavaClass() >> String }

        when:
        def ctx = HqlQueryContext.prepare(entity, "from Foo", null, null, [:], false, false)

        then:
        ctx.hql()              == "from Foo"
        ctx.namedParams().isEmpty()
        ctx.targetClass()      == String
        !ctx.isUpdate()
        !ctx.isNative()
    }

    void "prepare with GString and positionalParams expands interpolations into positional parameters"() {
        given:
        def entity = Mock(PersistentEntity) { getJavaClass() >> String }
        String val = "bar"
        GString gq = "from Foo where name = ${val}"

        when:
        def ctx = HqlQueryContext.prepare(entity, gq, [:], [], [:], false, false)

        then:
        ctx.hql()              == "from Foo where name = ?1"
        ctx.positionalParams() == ["bar"]
        ctx.namedParams().isEmpty()
    }

    void "reproduce HibernateHqlQuerySpec failure"() {
        given:
        def entity = Mock(PersistentEntity) { getJavaClass() >> String; getName() >> "Book" }
        String titleVal = "The Two Towers"
        GString gq = "from HibernateHqlQuerySpecBook b where b.title = ${titleVal}"

        when:
        def ctx = HqlQueryContext.prepare(entity, gq, [:], [], [:], false, false)

        then:
        ctx.hql() == "from HibernateHqlQuerySpecBook b where b.title = ?1"
        ctx.positionalParams() == ["The Two Towers"]
    }

    void "prepare with GString and non-empty positionalParams appends to existing parameters"() {
        given:
        def entity = Mock(PersistentEntity) { getJavaClass() >> String }
        String val = "bar"
        GString gq = "from Foo where name = ${val}"

        when:
        def ctx = HqlQueryContext.prepare(entity, gq, [:], ["first"], [:], false, false)

        then:
        ctx.hql()              == "from Foo where name = ?2"
        ctx.positionalParams() == ["first", "bar"]
    }

    void "prepare merges caller-supplied namedParams with GString params"() {
        given:
        def entity = Mock(PersistentEntity) { getJavaClass() >> String }
        String val = "bar"
        GString gq = "from Foo where name = ${val} and status = :status"

        when:
        def ctx = HqlQueryContext.prepare(entity, gq, [status: "active"], null, [:], false, false)

        then:
        ctx.namedParams().p0     == "bar"
        ctx.namedParams().status == "active"
    }

    void "prepare with isNative=true preserves hql without alias injection"() {
        given:
        def entity = Mock(PersistentEntity) { getJavaClass() >> String }

        when:
        def ctx = HqlQueryContext.prepare(entity, "select name from foo", null, null, [:], true, false)

        then:
        ctx.hql()      == "select name from foo"  // alias injection skipped for native SQL
        ctx.isNative()
    }

    void "prepare with isUpdate=true sets flag"() {
        given:
        def entity = Mock(PersistentEntity) { getJavaClass() >> String }

        when:
        def ctx = HqlQueryContext.prepare(entity, "update Foo set x=1", null, null, [:], false, true)

        then:
        ctx.isUpdate()
    }

    void "prepare with null namedParams does not throw"() {
        given:
        def entity = Mock(PersistentEntity) { getJavaClass() >> String }

        when:
        def ctx = HqlQueryContext.prepare(entity, "from Foo", null, null, [:], false, false)

        then:
        noExceptionThrown()
        ctx.namedParams() != null
    }

    // ─── resolveHql ──────────────────────────────────────────────────────────

    void "resolveHql with null returns empty string"() {
        expect:
        HqlQueryContext.resolveHql(null, false, [:]) == ""
    }

    void "resolveHql with plain String passes through normalisation"() {
        expect:
        HqlQueryContext.resolveHql("from Foo", false, [:]) == "from Foo"
    }

    void "resolveHql with GString extracts parameters and returns parameterised HQL"() {
        given:
        String v = "hello"
        GString gq = "from Foo where x = ${v}"
        def params = [:]

        when:
        String result = HqlQueryContext.resolveHql(gq, false, params as Map<String, Object>)

        then:
        result     == "from Foo where x = :p0"
        params.p0  == "hello"
    }

    void "resolveHql with multi-value GString produces sequential parameter names"() {
        given:
        String a = "foo"
        String b = "bar"
        GString gq = "from Foo where x = ${a} and y = ${b}"
        def params = [:]

        when:
        String result = HqlQueryContext.resolveHql(gq, false, params as Map<String, Object>)

        then:
        result     == "from Foo where x = :p0 and y = :p1"
        params.p0  == "foo"
        params.p1  == "bar"
    }

    void "resolveHql collapses multiline query to single line"() {
        expect:
        HqlQueryContext.resolveHql("from Foo\nwhere x = 1", false, [:]) == "from Foo where x = 1"
    }

    void "resolveHql with isNative=true skips alias normalisation"() {
        // HQL path injects alias; native SQL path must leave the string unchanged
        expect:
        HqlQueryContext.resolveHql("select name from foo", true,  [:]) == "select name from foo"
        HqlQueryContext.resolveHql("select name from Foo", false, [:]) == "select e.name from Foo e"
    }

    // ─── getTarget ────────────────────────────────────────────────────────────

    void "getTarget with null hql returns entity class"() {
        expect:
        HqlQueryContext.getTarget(null, String) == String
    }

    void "getTarget with no SELECT clause returns entity class"() {
        expect:
        HqlQueryContext.getTarget("from Person p", String) == String
    }

    void "getTarget with single entity-alias projection returns entity class"() {
        expect:
        HqlQueryContext.getTarget("select p from Person p", String) == String
    }

    void "getTarget with single qualified property projection returns Object"() {
        expect:
        HqlQueryContext.getTarget("select p.name from Person p", String) == Object
    }

    void "getTarget with multiple projections returns Object array"() {
        expect:
        HqlQueryContext.getTarget("select p.name, p.age from Person p", String) == Object[].class
    }

    @Unroll
    void "getTarget returns Long for aggregate projection: #hql"() {
        expect:
        HqlQueryContext.getTarget(hql, String) == Long
        where:
        hql << [
            "select count(p) from Person p",
            "select sum(p.age) from Person p",
            "select avg(p.age) from Person p",
            "select min(p.age) from Person p",
            "select max(p.age) from Person p",
            "select count(*) from Person",
            "select distinct count(p.id) from Person p"
        ]
    }

    // ─── countHqlProjections ─────────────────────────────────────────────────

    void "countHqlProjections returns 0 for null"() {
        expect: HqlQueryContext.countHqlProjections(null) == 0
    }

    void "countHqlProjections returns 0 for empty string"() {
        expect: HqlQueryContext.countHqlProjections("") == 0
    }

    void "countHqlProjections returns 0 when no SELECT clause"() {
        expect: HqlQueryContext.countHqlProjections("from Person p") == 0
    }

    @Unroll
    void "countHqlProjections returns 1 for single projection: #hql"() {
        expect: HqlQueryContext.countHqlProjections(hql) == 1
        where:
        hql << [
            "select p.name from Person p",
            "select distinct p.name from Person p",
            "select all p.name from Person p",
            "select count(p) from Person p",
            "select new map(p.name as n, p.age as a) from Person p",
        ]
    }

    void "countHqlProjections returns 2 for two top-level projections"() {
        expect: HqlQueryContext.countHqlProjections("select p.name, p.age from Person p") == 2
    }

    void "countHqlProjections ignores commas inside single-level parentheses"() {
        expect: HqlQueryContext.countHqlProjections("select coalesce(p.name, 'x') from Person p") == 1
    }

    void "countHqlProjections ignores commas inside nested parentheses"() {
        expect: HqlQueryContext.countHqlProjections("select coalesce(trim(p.name), 'x') from Person p") == 1
    }

    void "countHqlProjections ignores commas inside single-quoted string literals"() {
        expect: HqlQueryContext.countHqlProjections("select 'a,b' from Person p") == 1
    }

    void "countHqlProjections ignores commas inside double-quoted string literals"() {
        expect: HqlQueryContext.countHqlProjections('select "a,b" from Person p') == 1
    }

    void "countHqlProjections handles escaped single-quote inside string literal"() {
        expect: HqlQueryContext.countHqlProjections("select 'it''s,fine' from Person p") == 1
    }

    // ─── normalizeNonAliasedSelect ────────────────────────────────────────────

    void "normalizeNonAliasedSelect returns null for null input"() {
        expect: HqlQueryContext.normalizeNonAliasedSelect(null) == null
    }

    void "normalizeNonAliasedSelect returns empty for empty input"() {
        expect: HqlQueryContext.normalizeNonAliasedSelect("") == ""
    }

    void "normalizeNonAliasedSelect leaves query without SELECT unchanged"() {
        expect: HqlQueryContext.normalizeNonAliasedSelect("from Person") == "from Person"
    }

    void "normalizeNonAliasedSelect leaves query that already has an alias unchanged"() {
        expect: HqlQueryContext.normalizeNonAliasedSelect("select p.name from Person p") == "select p.name from Person p"
    }

    void "normalizeNonAliasedSelect leaves query with AS alias unchanged"() {
        expect: HqlQueryContext.normalizeNonAliasedSelect("select p.name from Person as p") == "select p.name from Person as p"
    }

    void "normalizeNonAliasedSelect injects alias e for bare property projection"() {
        expect: HqlQueryContext.normalizeNonAliasedSelect("select name from Person") == "select e.name from Person e"
    }

    void "normalizeNonAliasedSelect replaces entity-name projection with alias"() {
        expect: HqlQueryContext.normalizeNonAliasedSelect("select Person from Person") == "select e from Person e"
    }

    void "normalizeNonAliasedSelect preserves DISTINCT prefix when injecting alias"() {
        expect: HqlQueryContext.normalizeNonAliasedSelect("select distinct name from Person") == "select distinct e.name from Person e"
    }

    void "normalizeNonAliasedSelect preserves ALL prefix when injecting alias"() {
        expect: HqlQueryContext.normalizeNonAliasedSelect("select all name from Person") == "select all e.name from Person e"
    }

    void "normalizeNonAliasedSelect does not qualify function expressions"() {
        expect: HqlQueryContext.normalizeNonAliasedSelect("select count(id) from Person") == "select count(id) from Person e"
    }

    void "normalizeNonAliasedSelect does not qualify constructor expressions"() {
        expect: HqlQueryContext.normalizeNonAliasedSelect("select new map(name) from Person") == "select new map(name) from Person e"
    }

    void "normalizeNonAliasedSelect injects alias before WHERE clause keyword"() {
        expect: HqlQueryContext.normalizeNonAliasedSelect("select name from Person where age > 18") ==
                "select e.name from Person e where age > 18"
    }

    void "normalizeNonAliasedSelect leaves malformed query without FROM unchanged"() {
        expect: HqlQueryContext.normalizeNonAliasedSelect("select name") == "select name"
    }
}
