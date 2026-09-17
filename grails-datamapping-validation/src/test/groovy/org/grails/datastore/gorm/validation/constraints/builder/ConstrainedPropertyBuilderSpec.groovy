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
package org.grails.datastore.gorm.validation.constraints.builder

import org.springframework.context.support.StaticMessageSource
import spock.lang.Specification

import grails.gorm.validation.ConstrainedProperty
import grails.gorm.validation.DefaultConstrainedProperty
import grails.gorm.validation.exceptions.ValidationConfigurationException
import org.grails.datastore.gorm.validation.constraints.eval.DefaultConstraintEvaluator
import org.grails.datastore.gorm.validation.constraints.registry.DefaultConstraintRegistry
import org.grails.datastore.mapping.keyvalue.mapping.config.GormKeyValueMappingFactory
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.config.GormMappingConfigurationStrategy

class ConstrainedPropertyBuilderSpec extends Specification {

    KeyValueMappingContext mappingContext = new KeyValueMappingContext('cpb')
    DefaultConstraintRegistry registry = new DefaultConstraintRegistry(new StaticMessageSource())

    void setup() {
        mappingContext.mappingFactory = new GormKeyValueMappingFactory('cpb')
        mappingContext.syntaxStrategy = new GormMappingConfigurationStrategy(mappingContext.mappingFactory)
    }

    DefaultConstraintEvaluator evaluator(Map defaults = [:]) {
        new DefaultConstraintEvaluator(registry, mappingContext, defaults)
    }

    void "constraints are collected in declaration order with property types from the mapping context"() {
        given:
        mappingContext.addPersistentEntity(CpbBook)

        when:
        Map<String, ConstrainedProperty> constraints = evaluator().evaluate(CpbBook)
        DefaultConstrainedProperty title = constraints.title
        DefaultConstrainedProperty pages = constraints.pages

        then:
        constraints.keySet().toList().take(2) == ['title', 'pages']
        title.order == 1
        pages.order == 2
        title.propertyType == String
        title.maxSize == 20
        !title.blank
        pages.propertyType == Integer
        pages.min == 1
        title.widget == 'textarea'
        title.metaConstraints.tooltip == 'hint'
        !title.hasAppliedConstraint('widget')
        !title.hasAppliedConstraint('tooltip')
        !pages.hasAppliedConstraint('blank')
        evaluator().getDefaultConstraints() == null
        evaluator().newConstrainedPropertyBuilder(CpbBook) instanceof ConstrainedPropertyBuilder
    }

    void "unknown properties are rejected unless ad hoc constraints are allowed"() {
        when:
        evaluator().evaluate(CpbBroken)

        then:
        MissingMethodException e = thrown()
        e.method == 'missing'
        e.type == CpbBroken

        when:
        Map<String, ConstrainedProperty> constraints = evaluator().evaluate(CpbBroken, true, true, { missing blank: false; other maxSize: 3 })

        then:
        constraints.keySet().toList() == ['missing', 'other']
        constraints.missing.propertyType == CharSequence
        !constraints.missing.blank
        constraints.other.maxSize == 3
        constraints.other.nullable
    }

    void "shared constraints are resolved from the default constraints"() {
        given:
        Map defaults = [shortText: [maxSize: 5, blank: false]]

        when:
        Map<String, ConstrainedProperty> constraints = evaluator(defaults).evaluate(CpbShared)

        then:
        constraints.name.maxSize == 5
        !constraints.name.blank

        when:
        evaluator([:]).evaluate(CpbShared)

        then:
        ValidationConfigurationException e = thrown()
        e.message == 'Property [' + CpbShared.name + '.name] references shared constraint [shortText:null], which doesn\'t exist!'
    }

    void "wildcard default constraints apply to every constrainable property"() {
        given:
        mappingContext.addPersistentEntity(CpbBook)

        when:
        Map<String, ConstrainedProperty> constraints = evaluator(['*': [nullable: false, maxSize: 3]]).evaluate(CpbBook)

        then:
        constraints.title.maxSize == 20
        !constraints.title.nullable
        constraints.pages.min == 1
        !constraints.pages.nullable
        constraints.isbn.maxSize == 3
        !constraints.isbn.nullable
        !constraints.tags.nullable
        constraints.tags.maxSize == 3
        !constraints.containsKey('version')
    }

    void "the builder falls back to the target class for unknown methods and properties"() {
        given:
        ConstrainedPropertyBuilder builder = new ConstrainedPropertyBuilder(mappingContext, registry, CpbStatics, [:])

        when:
        Closure closure = CpbStatics.constraints.clone()
        closure.resolveStrategy = Closure.DELEGATE_ONLY
        closure.delegate = builder
        closure.call()

        then:
        CpbStatics.recorded == ['helper:5', 'flag=true']
        builder.constrainedProperties.name.maxSize == 5
        builder.getSharedConstraint('name') == null
        builder.getSharedConstraint('other') == 'shared'

        when:
        closure.delegate = builder
        builder.unknownThing = 1

        then:
        thrown(MissingPropertyException)

        when:
        builder.name(nullable: false, 'ignored value')

        then:
        thrown(MissingMethodException)
    }

    void "importFrom copies applied and meta constraints honouring include and exclude patterns"() {
        when:
        Map<String, ConstrainedProperty> constraints = evaluator().evaluate(CpbImporter)

        then:
        constraints.title.maxSize == 20
        !constraints.title.blank
        constraints.title.widget == null
        constraints.title.metaConstraints.tooltip == 'hint'
        constraints.pages.min == 1
        constraints.isbn.appliedConstraints*.name == ['nullable']
        constraints.tags.appliedConstraints*.name == ['nullable']

        when:
        constraints = evaluator().evaluate(CpbExcluder)

        then:
        constraints.title.maxSize == 20
        constraints.isbn.appliedConstraints*.name == ['nullable']
        constraints.pages.appliedConstraints*.name == ['nullable']
        constraints.tags.appliedConstraints*.name == ['nullable']
    }

    void "derived properties cannot be constrained and unsupported registered constraints are skipped"() {
        given:
        mappingContext.addPersistentEntity(CpbDerived)

        when:
        Map<String, ConstrainedProperty> constraints = evaluator().evaluate(CpbDerived)

        then:
        !constraints.containsKey('computed')
        constraints.count.propertyType == Integer
        !constraints.count.hasAppliedConstraint('email')
        constraints.count.metaConstraints.isEmpty()
        constraints.count.metaConstraints == [:]
        constraints.count.nullable
    }

    void "required by default evaluation constrains plain classes from their meta properties"() {
        when:
        Map<String, ConstrainedProperty> constraints = evaluator().evaluate(CpbPlain, false)

        then:
        constraints.keySet().toList() == ['name', 'count', 'flag', 'enabled']
        !constraints.name.nullable
        !constraints.count.nullable
        !constraints.flag.nullable
        !constraints.enabled.nullable

        when:
        constraints = evaluator().evaluate(CpbPlain, true)

        then:
        constraints.keySet().toList() == ['name']
        constraints.name.nullable
    }

}

class CpbBook {

    Long id
    Long version
    String title
    Integer pages
    String isbn
    List<String> tags

    static hasMany = [tags: String]

    static constraints = {
        title maxSize: 20, blank: false, widget: 'textarea', tooltip: 'hint'
        pages min: 1
    }

}

class CpbBroken {

    String other

    static constraints = {
        missing blank: false
    }

}

class CpbShared {

    String name

    static constraints = {
        name shared: 'shortText'
    }

}

class CpbStatics {

    static List recorded = []
    static boolean flag = false
    String name
    String other

    static void helper(int value) {
        recorded << 'helper:' + value
    }

    static void setFlag(boolean value) {
        flag = value
        recorded << 'flag=' + value
    }

    static constraints = {
        helper(5)
        flag = true
        name maxSize: 5
        other shared: 'shared'
    }

}

class CpbImporter {

    String title
    Integer pages
    String isbn
    List<String> tags

    static constraints = {
        importFrom CpbBook, include: ['tit.*', 'pages'], exclude: ['isbn']
    }

}

class CpbExcluder {

    String title
    Integer pages
    String isbn
    List<String> tags

    static constraints = {
        importFrom CpbBook, exclude: ['pa.*', 'tags']
    }

}

class CpbDerived {

    Long id
    Integer count
    String computed

    static mapping = {
        computed formula: 'upper(name)'
    }

    static constraints = {
        computed maxSize: 3
        count email: true
    }

}

class CpbPlain {

    String name
    Integer count
    boolean flag
    Boolean enabled

    static constraints = {
        name maxSize: 3
    }

}
