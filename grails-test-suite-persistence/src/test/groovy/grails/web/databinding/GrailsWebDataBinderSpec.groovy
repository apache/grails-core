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
package grails.web.databinding

import groovy.transform.CompileStatic
import groovy.transform.Sortable

import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll

import org.springframework.context.support.StaticMessageSource

import grails.config.Settings
import grails.util.Holders
import grails.databinding.BindUsing
import grails.databinding.BindingFormat
import grails.databinding.DataBindingSource
import grails.databinding.SimpleMapDataBindingSource
import grails.databinding.converters.ValueConverter
import grails.databinding.errors.BindingError
import grails.databinding.events.DataBindingListenerAdapter
import grails.persistence.Entity
import grails.testing.gorm.DataTest
import grails.validation.DeferredBindingActions
import grails.validation.Validateable
import org.grails.config.PropertySourcesConfig

class GrailsWebDataBinderSpec extends Specification implements DataTest {

    private static Locale defaultLocale = Locale.getDefault()

    GrailsWebDataBinder binder

    void setupSpec() {
        mockDomains(
                AssociationBindingAuthor, AssociationBindingBook, AssociationBindingPage, Author, BinderNullabilityEntity,
                Child, CollectionContainer, DataBindingBook, Fidget, Foo, GeneratedBindingChild, GeneratedBindingParent,
                Parent, Publication, Publisher, RawCollectionContainer, Team, Widget
        )
    }

    void setup() {
        binder = grailsApplication.mainContext.getBean(DataBindingUtils.DATA_BINDER_BEAN_NAME) as GrailsWebDataBinder
    }
    
    void cleanup() {
        Locale.setDefault(defaultLocale)
        binder.convertEmptyStringsToNull = true
        grailsApplication.config.setAt(Settings.DATABINDING_DENY_BY_DEFAULT, null)
        DataBindingUtils.clearBindingCaches()
        GrailsWebDataBinder.resetWarnedBindingShapes()
    }

    @CompileStatic
    private static void bindWithNullIncludeList(GrailsWebDataBinder binder, Object target,
            DataBindingSource source) {
        binder.bind(target, source, (List) null)
    }

    void 'Test binding an invalid String to an object reference does not result in an empty instance being bound'() {
        // GRAILS-3159
        given:
        def publication = new Publication()

        when:
        binder.bind(publication, new SimpleMapDataBindingSource([
            author: '42'
        ]))

        then:
        publication.author == null
    }

    void 'Test binding empty and blank String'() {

        given:
        def obj = new Author()

        when:
        binder.bind(obj, new SimpleMapDataBindingSource([
            name: '',
            stringWithSpecialBinding:''
        ]))

        then:
        obj.name == null
        obj.stringWithSpecialBinding == ''

        when:
        binder.bind(obj, new SimpleMapDataBindingSource([
            name: '  ',
            stringWithSpecialBinding: '  '
        ]))

        then:
        obj.name == null
        obj.stringWithSpecialBinding == ''

        when:
        def emptyString = ''
        binder.bind(obj, new SimpleMapDataBindingSource([
            name: "${emptyString}",
            stringWithSpecialBinding: "${emptyString}"
        ]))

        then:
        obj.name == null
        obj.stringWithSpecialBinding == ''

        when:
        binder.bind(obj, new SimpleMapDataBindingSource([
            name: "  ${emptyString}  ",
            stringWithSpecialBinding: "  ${emptyString}  "
        ]))

        then:
        obj.name == null
        obj.stringWithSpecialBinding == ''
    }

    @Unroll
    void 'Test binding to primitives from Strings when locale is #locale'() {

        given:
        Locale.setDefault(locale)
        def obj = new PrimitiveContainer()

        when:
        binder.bind(obj, new SimpleMapDataBindingSource([
            someBoolean: 'true',
            someByte: '1',
            someChar: 'a',
            someShort: '2',
            someInt: '3',
            someLong: '4',
            someFloat: '5.5'.replace('.', decimalSeparator),
            someDouble: '6.6'.replace('.', decimalSeparator)
        ]))

        then:
        obj.someBoolean
        obj.someByte == 1 as byte
        obj.someChar == ('a' as char)
        obj.someShort == 2 as short
        obj.someInt == 3
        obj.someLong == 4
        obj.someFloat == 5.5f
        obj.someDouble == 6.6d
        where:
        locale << [new Locale('fi', 'FI', ''), new Locale('en', 'US', '')]
        decimalSeparator << [',', '.']
    }
    
    void 'Test binding to primitive numbers from malformed Strings when locale is #locale'() {

        given:
        Locale.setDefault(locale)
        def obj = new PrimitiveContainer()

        when:
        binder.bind(obj, new SimpleMapDataBindingSource([
            someShort: '2x',
            someInt: '3x',
            someLong: '4x',
            someFloat: '5.5x'.replace('.', decimalSeparator),
            someDouble: '6.6x'.replace('.', decimalSeparator)
        ]))

        then:
        obj.someShort == 0 as short
        obj.someInt == 0
        obj.someLong == 0
        obj.someFloat == 0
        obj.someDouble == 0
        obj.errors.getFieldError('someShort').defaultMessage == 'Unable to parse number [2x]'
        obj.errors.getFieldError('someShort').rejectedValue == '2x'
        obj.errors.getFieldError('someInt').defaultMessage == 'Unable to parse number [3x]'
        obj.errors.getFieldError('someInt').rejectedValue == '3x'
        obj.errors.getFieldError('someLong').defaultMessage == 'Unable to parse number [4x]'
        obj.errors.getFieldError('someLong').rejectedValue == '4x'
        obj.errors.getFieldError('someFloat').defaultMessage == 'Unable to parse number [5' + decimalSeparator + '5x]'
        obj.errors.getFieldError('someFloat').rejectedValue == '5' + decimalSeparator + '5x'
        obj.errors.getFieldError('someDouble').defaultMessage == 'Unable to parse number [6' + decimalSeparator + '6x]'
        obj.errors.getFieldError('someDouble').rejectedValue == '6' + decimalSeparator + '6x'

        where:
        locale << [new Locale('fi', 'FI', ''), new Locale('en', 'US', '')]
        decimalSeparator << [',', '.']
    }

    void 'Test binding null to id of element nested in a List'() {

        given:
        def obj = new CollectionContainer()
        def map = [
            'listOfWidgets[0]': [isBindable: 'Is Uno (List)', isNotBindable: 'Is Not Uno (List)'],
            'listOfWidgets[1]': [isBindable: 'Is Dos (List)', isNotBindable: 'Is Not Dos (List)'],
            'listOfWidgets[2]': [isBindable: 'Is Tres (List)', isNotBindable: 'Is Not Tres (List)']
        ]

        when:
        binder.bind(obj, new SimpleMapDataBindingSource(map))
        def listOfWidgets = obj.listOfWidgets

        then:
        listOfWidgets instanceof List
        listOfWidgets.size() == 3
        listOfWidgets[0].isBindable == 'Is Uno (List)'
        listOfWidgets[0].isNotBindable == null
        listOfWidgets[1].isBindable == 'Is Dos (List)'
        listOfWidgets[1].isNotBindable == null
        listOfWidgets[2].isBindable == 'Is Tres (List)'
        listOfWidgets[2].isNotBindable == null

        when:
        map = ['listOfWidgets[1]': [id: 'null']]
        binder.bind(obj, new SimpleMapDataBindingSource(map))
        listOfWidgets = obj.listOfWidgets

        then:
        listOfWidgets instanceof List
        listOfWidgets.size() == 2
        listOfWidgets[0].isBindable == 'Is Uno (List)'
        listOfWidgets[0].isNotBindable == null
        listOfWidgets[1].isBindable == 'Is Tres (List)'
        listOfWidgets[1].isNotBindable == null
    }
    
    void 'Test binding null id to a domain class reference in a non-domain class'() {

        given:
        def nonDomainClass = new SomeNonDomainClass()
        
        when:
        binder.bind(nonDomainClass, new SimpleMapDataBindingSource([
            publication: [
                id: null
            ]
        ]))
        
        then:
        nonDomainClass.publication == null
        
        when:
        binder.bind(nonDomainClass, new SimpleMapDataBindingSource([
            publication: [
                id: 'null'
            ]
        ]))
        
        then:
        nonDomainClass.publication == null
        
        when:
        binder.bind(nonDomainClass, new SimpleMapDataBindingSource([
            publication: [
                id: ''
            ]
        ]))
        
        then:
        nonDomainClass.publication == null
    }

    void 'Test id binding'() {

        given:
        def author = new Author(name: 'David Foster Wallace').save(flush: true, failOnError: true)
        def publication = new Publication()

        when:
        binder.bind(publication, new SimpleMapDataBindingSource([
            title: 'Infinite Jest',
            author: [
                id: author.id
            ]
        ]))

        then:
        publication.title == 'Infinite Jest'
        publication.author.name == 'David Foster Wallace'

        when:
        binder.bind(publication, new SimpleMapDataBindingSource([
            author: [
                id: 'null'
            ]
        ]))

        then:
        publication.author == null

        when:
        publication.title = null
        List whiteList = null
        def blackList = ['author']
        binder.bind(publication, new SimpleMapDataBindingSource([
            title: 'Infinite Jest',
            author: [
                id: author.id
            ]
        ]), whiteList, blackList)

        then:
        publication.title == 'Infinite Jest'
        publication.author == null

        when:
        publication.author = null
        binder.bind(publication, new SimpleMapDataBindingSource([
            title: 'Infinite Jest 2',
            author: [
                id: author.id
            ]
        ]))

        then:
        publication.author.name == 'David Foster Wallace'

        when:
        binder.bind(publication, new SimpleMapDataBindingSource([
            author: [
                id: ''
            ]
        ]))

        then:
        publication.author == null

        when:
        binder.bind(publication, new SimpleMapDataBindingSource([
            author: [
                id: null
            ]
        ]))

        then:
        publication.author == null

        when:
        publication.author = new Author()
        binder.bind(publication, new SimpleMapDataBindingSource([
            author: [
                id: null
            ]
        ]))

        then:
        publication.author == null

        when:
        publication.author = new Author()
        binder.bind(publication, new SimpleMapDataBindingSource([
            author: [
                id: 'null'
            ]
        ]))

        then:
        publication.author == null

        when:
        publication.author = new Author()
        binder.bind(publication, new SimpleMapDataBindingSource([
            author: [
                id: ''
            ]
        ]))

        then:
        publication.author == null

        when:
        publication.author = new Author()
        binder.bind(publication, new SimpleMapDataBindingSource([
            author: 'null'
        ]))

        then:
        publication.author == null

        when:
        publication.author = new Author()
        binder.bind(publication, new SimpleMapDataBindingSource([
            author: ''
        ]))

        then:
        publication.author == null

        when:
        publication.author = new Author()
        binder.bind(publication, new SimpleMapDataBindingSource([
            author: null
        ]))

        then:
        publication.author == null
    }

    void 'Test id binding with a non dataSource aware binding source'() {

        given:
        def author = new Author(name: 'David Foster Wallace').save(flush: true)
        def publication = new Publication()
        def bindingSource = new SimpleMapDataBindingSource([
            title: 'Infinite Jest',
            author: [
                id: author.id
            ]
        ])
        bindingSource.dataSourceAware = false

        when:
        binder.bind(publication, bindingSource)

        then:
        publication.title == 'Infinite Jest'
        publication.author.name == null

        when:
        bindingSource.dataSourceAware = true
        binder.bind(publication, bindingSource)

        then:
        publication.title == 'Infinite Jest'
        publication.author.name == 'David Foster Wallace'
    }

    void 'Test binding to the one side of a one to many'() {

        given:
        def author = new Author(name: 'Graeme').save()
        def pub = new Publication(title: 'DGG', author: author)

        when:
        binder.bind(pub, new SimpleMapDataBindingSource([
            publisher: [
                name: 'Apress'
            ]
        ]))

        // pending investigation...
        DeferredBindingActions.runActions()

        def publisher = pub.publisher

        then:
        publisher != null

        when:
        publisher.save()

        then:
        pub.publisher.name == 'Apress'
        pub.publisher.publications.size() == 1

        // this is what we are really testing...
        pub.publisher.publications[0] == pub
    }

    void 'Test generated parent allowlist applies persisted association child allowlist'() {
        given:
        grailsApplication.config.setAt(Settings.DATABINDING_DENY_BY_DEFAULT, true)
        def child = new GeneratedBindingChild(name: 'Original').save(flush: true, failOnError: true)
        def parent = new GeneratedBindingParent(child: child).save(flush: true, failOnError: true)

        when:
        binder.bind(parent, new SimpleMapDataBindingSource([
            child: [id: child.id, name: 'Allowed', admin: true]
        ]))

        then:
        parent.child.is(child)
        parent.child.name == 'Allowed'
        !parent.child.admin
    }

    void 'Test generated allowlist applies to persisted domain instances in typed arrays'() {
        given:
        grailsApplication.config.setAt(Settings.DATABINDING_DENY_BY_DEFAULT, true)
        def child = new GeneratedBindingChild(name: 'Original').save(flush: true, failOnError: true)
        def holder = new GeneratedBindingArrayHolder()

        when:
        binder.bind(holder, new SimpleMapDataBindingSource([
            children: [[id: child.id, name: 'Allowed', admin: true]]
        ]))

        then:
        holder.children.length == 1
        holder.children[0].is(child)
        holder.children[0].id == child.id
        holder.children[0].name == 'Allowed'
        !holder.children[0].admin
    }

    void 'Test null public include list resolves generated allowlist'() {
        given:
        grailsApplication.config.setAt(Settings.DATABINDING_DENY_BY_DEFAULT, true)
        def child = new GeneratedBindingChild(name: 'Original')

        when:
        bindWithNullIncludeList(binder, child, new SimpleMapDataBindingSource([
            name: 'Allowed', admin: true
        ]))

        then:
        child.name == 'Allowed'
        !child.admin
    }

    void 'Test empty public include list binds no properties'() {
        given:
        def child = new GeneratedBindingChild(name: 'Original')

        when:
        binder.bind(child, new SimpleMapDataBindingSource([
            name: 'Changed', admin: true
        ]), [])

        then:
        child.name == 'Original'
        !child.admin
    }

    void 'Test explicit nested wildcard binds all nested properties'() {
        given:
        def parent = new GeneratedBindingParent(child: new GeneratedBindingChild(name: 'Original'))

        when:
        binder.bind(parent, new SimpleMapDataBindingSource([
            child: [name: 'Changed', admin: true]
        ]), ['child'])

        then:
        parent.child.name == 'Changed'
        parent.child.admin
    }

    void 'Test Map constructor fallback fails closed without a no-arg constructor'() {
        given:
        grailsApplication.config.setAt(Settings.DATABINDING_DENY_BY_DEFAULT, true)
        def holder = new SecureMapConstructorHolder()
        def indexedHolder = new SecureMapConstructorHolder()
        def arrayHolder = new SecureMapConstructorHolder()
        List<String> warnings = []
        def warningBinder = new GrailsWebDataBinder(grailsApplication) {
            @Override
            protected boolean isBindingWarningEnabled() {
                true
            }

            @Override
            protected void logBindingWarning(String message) {
                warnings << message
            }
        }

        when:
        warningBinder.bind(holder, new SimpleMapDataBindingSource([
            values: [first: [name: 'Allowed', admin: true]]
        ]))
        warningBinder.bind(indexedHolder, new SimpleMapDataBindingSource([
            'values[second]': [name: 'Also Allowed', admin: true]
        ]))
        warningBinder.bind(arrayHolder, new SimpleMapDataBindingSource([
            arrayValues: [[name: 'Array Allowed', admin: true]]
        ]))

        then:
        holder.values.isEmpty()
        indexedHolder.values.isEmpty()
        arrayHolder.arrayValues.length == 0
        warnings == [GrailsWebDataBinder.missingNoArgConstructorMessage(SecureMapConstructorValue)]
    }

    void 'Test Map constructor fallback remains permissive in compatibility mode'() {
        given:
        grailsApplication.config.setAt(Settings.DATABINDING_DENY_BY_DEFAULT, false)
        def holder = new SecureMapConstructorHolder()

        when:
        binder.bind(holder, new SimpleMapDataBindingSource([
            values: [first: [name: 'Legacy', admin: true]],
            arrayValues: [[name: 'Array Legacy', admin: true]]
        ]))

        then:
        holder.values.first.name == 'Legacy'
        !holder.values.first.admin
        holder.arrayValues*.name == ['Array Legacy']
        !holder.arrayValues[0].admin

        cleanup:
        grailsApplication.config.setAt(Settings.DATABINDING_DENY_BY_DEFAULT, null)
    }

    void 'Test Map constructor fallback binds unconfigured properties but not bindable false by default'() {
        given:
        def configuredConfig = grailsApplication.config
        grailsApplication.config = new PropertySourcesConfig([:])
        def holder = new SecureMapConstructorHolder()

        when:
        binder.bind(holder, new SimpleMapDataBindingSource([
            values: [first: [name: 'Default', admin: true]]
        ]))

        then:
        holder.values.first.name == 'Default'
        !holder.values.first.admin

        cleanup:
        grailsApplication.config = configuredConfig
    }

    void 'Test Map constructor fallback remains permissive for explicit bind-all'() {
        given:
        def holder = new SecureMapConstructorHolder()

        when:
        binder.bind(holder, new SimpleMapDataBindingSource([
            values: [first: [name: 'Explicit', admin: true]]
        ]), ['values'])

        then:
        holder.values.first.name == 'Explicit'
        !holder.values.first.admin
    }

    void 'Test Map constructor fallback fails closed for a narrow explicit include'() {
        given:
        grailsApplication.config.setAt(Settings.DATABINDING_DENY_BY_DEFAULT, true)
        def holder = new SecureMapConstructorHolder()

        when:
        binder.bind(holder, new SimpleMapDataBindingSource([
            values: [first: [name: 'Explicit', admin: true]]
        ]), ['values.name'])

        then:
        holder.values.isEmpty()
    }

    void 'Test typed Map binding in default mode notifies listeners and records value conversion errors'() {
        given:
        def holder = new TypedMapBindingHolder()
        List<String> afterBindingProperties = []
        List<BindingError> bindingErrors = []
        def listener = new DataBindingListenerAdapter() {
            @Override
            void afterBinding(Object object, String propertyName, Object errors) {
                afterBindingProperties << propertyName
            }

            @Override
            void bindingError(BindingError error, Object errors) {
                bindingErrors << error
            }
        }

        when:
        binder.bind(holder, new SimpleMapDataBindingSource([
            values: [first: [quantity: 'not-a-number']]
        ]), listener)

        then:
        holder.values.first.hasErrors()
        holder.values.first.errors.getFieldError('quantity').code == 'typeMismatch'
        afterBindingProperties.contains('values')
        bindingErrors*.propertyName.contains('quantity')
    }

    void 'Test binding to a hasMany List'() {

        given:
        def publisher = new Publisher()

        when:
        binder.bind(publisher, new SimpleMapDataBindingSource([
            name: 'Apress',
            'publications[0]': [
                title: 'DGG',
                author: [
                    name: 'Graeme'
                ]
            ],
            'publications[1]': [
                title: 'DGG2',
                author: [
                    name: 'Jeff'
                ]
            ]
        ]))

        then:
        publisher.name == 'Apress'
        publisher.publications instanceof List
        publisher.publications.size() == 2
        publisher.publications[0].title == 'DGG'
        publisher.publications[0].author.name == 'Graeme'
        publisher.publications[0].publisher == publisher
        publisher.publications[1].title == 'DGG2'
        publisher.publications[1].author.name == 'Jeff'
        publisher.publications[1].publisher == publisher
    }

    @Issue('#9003')
    void 'Test binding an array of ids to a collection of persistent instances'() {

        given:
        def book = new AssociationBindingBook()

        when:
        def pInitial = new AssociationBindingPage(number: 1).save()
        book.addToPages(pInitial)
        def p1 = new AssociationBindingPage(number: 42).save()
        def p2 = new AssociationBindingPage(number: 2112).save()

        then:
        p1.id != null
        p2.id != null
        book.pages?.find { it.number == 1 && it.id == pInitial.id }

        when:
        binder.bind(book, new SimpleMapDataBindingSource([
            pages: [p1.id, p2.id] as String[]
        ]))

        then: 'the initial page should have been replaced by the 2 new pages'
        book.pages?.size() == 2
        book.pages.find { it.number == 42 && it.id == p1.id }
        book.pages.find { it.number == 2112 && it.id == p2.id }
    }

    void 'Test bindable'() {

        given:
        def widget = new Widget()

        when:
        binder.bind(widget, new SimpleMapDataBindingSource([
            isBindable: 'Should Be Bound',
            isNotBindable: 'Should Not Be Bound'
        ]))

        then:
        widget.isBindable == 'Should Be Bound'
        widget.isNotBindable == null
    }

    void 'Test binding to a collection of String'() {

        given:
        def book = new DataBindingBook()

        when:
        binder.bind(book, new SimpleMapDataBindingSource([
            topics: [
                'journalism', null,
                'satire'
            ]
        ]))
        binder.bind(book, new SimpleMapDataBindingSource([
            'topics[1]': 'counterculture'
        ]))

        then:
        book.topics == ['journalism', 'counterculture', 'satire']
    }

    void 'Test binding to a collection of Integer'() {

        given:
        def book = new DataBindingBook()

        when:
        binder.bind(book, new SimpleMapDataBindingSource([
            importantPageNumbers: ['5', null, '42']
        ]))
        binder.bind(book, new SimpleMapDataBindingSource([
            'importantPageNumbers[1]': '2112'
        ]))

        then:
        book.importantPageNumbers == [5, 2112, 42]
    }

    void 'Test binding to a collection of primitive'() {

        given:
        def parent = new Parent()

        when:
        binder.bind(parent, new SimpleMapDataBindingSource([
            child: [
                someOtherIds: '4'
            ]
        ]))

        then:
        parent.child.someOtherIds.size() == 1
        parent.child.someOtherIds.contains(4)

        when:
        parent.child = null
        binder.bind(parent, new SimpleMapDataBindingSource([
            child: [
                someOtherIds: ['4', '5', '6']
            ]
        ]))

        then:
        parent.child.someOtherIds.size() == 3
        parent.child.someOtherIds.contains(4)
        parent.child.someOtherIds.contains(5)
        parent.child.someOtherIds.contains(6)

        when:
        parent.child = null
        binder.bind(parent, new SimpleMapDataBindingSource([
            child: [
                someOtherIds: 4
            ]
        ]))

        then:
        parent.child.someOtherIds.size() == 1
        parent.child.someOtherIds.contains(4)
    }

    void 'Test unbinding a Map entry'() {

        given:
        def team = new Team()

        when:
        team.members = [
            'jeff': new Author(name: 'Jeff Scott Brown'),
            'betsy': new Author(name: 'Sarah Elizabeth Brown')
        ]

        then:
        team.members.size() == 2
        team.members.containsKey('betsy')
        team.members.containsKey('jeff')
        'Sarah Elizabeth Brown' == team.members.betsy.name
        'Jeff Scott Brown' == team.members.jeff.name

        when:
        binder.bind(team, new SimpleMapDataBindingSource([
            'members[jeff]': [
                id: 'null'
            ]
        ]))

        then:
        team.members.size() == 1
        team.members.containsKey('betsy')
        'Sarah Elizabeth Brown' == team.members.betsy.name
    }

    void 'Test binding to a Map for new instance with quoted key'() {

        given:
        def team = new Team()

        when:
        binder.bind(team, new SimpleMapDataBindingSource([
            "members['jeff']": [
                name: 'Jeff Scott Brown'
            ],
            'members["betsy"]': [
                name: 'Sarah Elizabeth Brown'
            ]
        ]))

        then:
        team.members.size() == 2
        assert team.members.jeff instanceof Author
        assert team.members.betsy instanceof Author
        team.members.jeff.name == 'Jeff Scott Brown'
        team.members.betsy.name == 'Sarah Elizabeth Brown'
    }

    void 'Test binding to Set with subscript'() {

        given:
        def pub = new Publisher()

        when:
        binder.bind(pub, new SimpleMapDataBindingSource([
            'authors[0]': [
                name: 'Author Uno'
            ],
            'authors[1]': [
                name: 'Author Dos'
            ]
        ]))

        then:
        pub.authors.size() == 2
        pub.authors.find { it.name == 'Author Uno' }
        pub.authors.find { it.name == 'Author Dos' }
    }

    void 'Test binding existing entities to a new Set'() {

        when:
        def a1 = new Author(name: 'Author One').save()
        def a2 = new Author(name: 'Author Two').save(flush: true)

        then:
        a2
        a1

        when:
        def pub = new Publisher()
        binder.bind(pub, new SimpleMapDataBindingSource([
            'authors[0]': [
                id: a1.id
            ],
            'authors[1]': [
                id: a2.id
            ]
        ]))

        then:
        pub.authors.size() == 2
        pub.authors.find { it.name == 'Author One' } != null
        pub.authors.find { it.name == 'Author Two' } != null
    }

    void 'Test binding a String to an domain class object reference in a Collection'() {

        when:
        def a1 = new Author(name: 'Author One').save()
        def a2 = new Author(name: 'Author Two').save(flush: true)

        then:
        a2
        a1

        when:
        def pub = new Publisher()
        String stringToBind = a2.id as String
        binder.bind(pub, new SimpleMapDataBindingSource([
            'authors[0]': stringToBind
        ]))

        then:
        pub.authors.size() == 1
        pub.authors.find { it.name == 'Author Two' } != null
    }


    void 'Test binding a String to a domain class object reference'() {

        given:
        def author = new Author(name: 'Lewis Black')

        when:
        author.save()

        then:
        author.id !=  null

        when:
        def publication = new Publication()
        binder.bind(publication, new SimpleMapDataBindingSource([
            title: 'Me Of Little Faith',
            author: author.id.toString()
        ]))

        then:
        publication.author.name == 'Lewis Black'
        publication.title == 'Me Of Little Faith'
        publication.author.is(author)
    }

    void 'Test updating Set elements by id and subscript operator'() {

        given:
        def publisher = new Publisher(name: 'Some Publisher')

        when:
        def a1 = new Author(name: 'Author One').save()
        def a2 = new Author(name: 'Author Two').save()
        def a3 = new Author(name: 'Author Three').save()
        publisher.addToAuthors(a1)
        publisher.addToAuthors(a2)
        publisher.addToAuthors(a3)

        then:
        a1.id != null
        a2.id != null
        a3.id != null

        when:
        // the subscript values are not important, the ids drive selection from the Set
        binder.bind(publisher, new SimpleMapDataBindingSource([
            'authors[123]': [id: a3.id, name: 'Author Tres'],
            'authors[456]': [id: a1.id, name: 'Author Uno'],
            'authors[789]': [id: a2.id, name: 'Author Dos']
        ]))
        def updatedA1 = publisher.authors.find { it.id == a1.id }
        def updatedA2 = publisher.authors.find { it.id == a2.id }
        def updatedA3 = publisher.authors.find { it.id == a3.id }

        then:
        updatedA1.name == 'Author Uno'
        updatedA2.name == 'Author Dos'
        updatedA3.name == 'Author Tres'
    }

    void 'Test updating Set elements by id'() {

        given:
        def publisher = new Publisher(name: 'Some Publisher')

        when:
        def a1 = new Author(name: 'Author One').save()
        def a2 = new Author(name: 'Author Two').save()
        def a3 = new Author(name: 'Author Three').save()
        publisher.addToAuthors(a1)
        publisher.addToAuthors(a2)
        publisher.addToAuthors(a3)

        then:
        a1.id != null
        a2.id != null
        a3.id != null

        when:
        binder.bind(publisher, new SimpleMapDataBindingSource([
            'authors': [
                [id: a3.id, name: 'Author Tres'],
                [id: a1.id, name: 'Author Uno'],
                [id: a2.id, name: 'Author Dos']
            ]
        ]))
        def updatedA1 = publisher.authors.find { it.id == a1.id }
        def updatedA2 = publisher.authors.find { it.id == a2.id }
        def updatedA3 = publisher.authors.find { it.id == a3.id }

        then:
        publisher.authors.size()
        updatedA1.name == 'Author Uno'
        updatedA2.name == 'Author Dos'
        updatedA3.name == 'Author Tres'
    }

    void 'Test updating Set elements by id in addition to adding new elements'() {

        given:
        def publisher = new Publisher(name: 'Some Publisher')

        when:
        def a1 = new Author(name: 'Author One').save()
        def a2 = new Author(name: 'Author Two').save()
        def a3 = new Author(name: 'Author Three').save()
        publisher.addToAuthors(a1)
        publisher.addToAuthors(a2)
        publisher.addToAuthors(a3)

        then:
        a1.id != null
        a2.id != null
        a3.id != null

        when:
        binder.bind(publisher, new SimpleMapDataBindingSource([
            'authors': [
                [id: a3.id, name: 'Author Tres'],
                [id: a1.id, name: 'Author Uno'],
                [name: 'Author Uno Part Two'],
                [id: a2.id, name: 'Author Dos']]
        ]))
        def updatedA1 = publisher.authors.find { it.id == a1.id }
        def updatedA1Part2 = publisher.authors.find { it.name == 'Author Uno Part Two' }
        def updatedA2 = publisher.authors.find { it.id == a2.id }
        def updatedA3 = publisher.authors.find { it.id == a3.id }

        then:
        publisher.authors.size() == 4
        updatedA1Part2
        updatedA1.name == 'Author Uno'
        updatedA2.name == 'Author Dos'
        updatedA3.name == 'Author Tres'
    }

    void 'Test binding a List of Maps to a persistent Set'() {

        given:
        def publisher = new Publisher(name: 'Some Publisher')

        when:
        binder.bind(publisher, new SimpleMapDataBindingSource([
            'authors': [
                [name: 'Author One'],
                [name: 'Author Two'],
                [name: 'Author Three']
            ]
        ]))
        def a1 = publisher.authors.find { it.name == 'Author One' }
        def a2 = publisher.authors.find { it.name == 'Author Two' }
        def a3 = publisher.authors.find { it.name == 'Author Three' }
        
        then:
        a1
        a2
        a3
        publisher.authors.size() == 3
    }

    void 'Test updating a Set element by id that does not exist'() {

        given:
        def bindingErrors = [] as List<BindingError>
        def listener = new DataBindingListenerAdapter() {
            @Override void bindingError(BindingError error, Object errors) {
                bindingErrors << error
            }
        }

        when:
        def publisher = new Publisher(name: 'Apress').save()
        publisher.save(flush: true)
        binder.bind(publisher, new SimpleMapDataBindingSource([
            'authors[0]': [
                id: 42,
                name: 'Some Name'
            ]
        ]), listener)

        then:
        bindingErrors?.size() == 1

        when:
        def error = bindingErrors[0]

        then:
        error.propertyName == 'authors'
        error.cause?.message == 'Illegal attempt to update element in [authors] Set with id [42]. No such record was found.'
    }

    void 'Test updating nested entities retrieved by id'() {

        given:
        def publication = new Publication(title: 'Definitive Guide To Grails', author: new Author(name: 'Author Name'))

        when:
        def publisher = new Publisher(name: 'Apress').save()
        publisher.addToPublications(publication)
        publisher.save(flush: true)
        
        then:
        publication.publisher != null
        publication.id != null

        when:
        binder.bind(publisher, new SimpleMapDataBindingSource([
            'publications[0]': [
                id: publication.id,
                title: 'Definitive Guide To Grails 2'
            ]
        ]))

        then:
        publisher.publications[0].title == 'Definitive Guide To Grails 2'
    }

    void 'Test using @BindUsing to initialize property with a type other than the declared type'() {

        given:
        def author = new Author()

        when:
        binder.bind(author, new SimpleMapDataBindingSource([
            widget: [
                name: 'Some Name',
                isBindable: 'Some Bindable String'
            ]
        ]))

        then:
        // should be a Fidget, not a Widget
        author.widget instanceof Fidget

        // property in Fidget
        ((Fidget)author.widget).name == 'Some Name'

        // property in Widget
        author.widget.isBindable == 'Some Bindable String'
    }

    void 'Test binding to different collection types'() {

        given:
        def obj = new CollectionContainer()
        def map = [
            'listOfWidgets[0]': [isBindable: 'Is Uno (List)', isNotBindable: 'Is Not Uno (List)'],
            'listOfWidgets[1]': [isBindable: 'Is Dos (List)', isNotBindable: 'Is Not Dos (List)'],
            'listOfWidgets[2]': [isBindable: 'Is Tres (List)', isNotBindable: 'Is Not Tres (List)'],

            'setOfWidgets[0]': [isBindable: 'Is Uno (Set)', isNotBindable: 'Is Not Uno (Set)'],
            'setOfWidgets[1]': [isBindable: 'Is Dos (Set)', isNotBindable: 'Is Not Dos (Set)'],
            'setOfWidgets[2]': [isBindable: 'Is Tres (Set)', isNotBindable: 'Is Not Tres (Set)'],

            'sortedSetOfWidgets[0]': [isBindable: 'Is Uno (SortedSet)', isNotBindable: 'Is Not Uno (SortedSet)'],
            'sortedSetOfWidgets[1]': [isBindable: 'Is Dos (SortedSet)', isNotBindable: 'Is Not Dos (SortedSet)'],
            'sortedSetOfWidgets[2]': [isBindable: 'Is Tres (SortedSet)', isNotBindable: 'Is Not Tres (SortedSet)']
        ]

        //map['collectionOfWidgets[0]'] = [isBindable: 'Is Uno (Collection)', isNotBindable: 'Is Not Uno (Collection)']
        //map['collectionOfWidgets[1]'] = [isBindable: 'Is Dos (Collection)', isNotBindable: 'Is Not Dos (Collection)']
        //map['collectionOfWidgets[2]'] = [isBindable: 'Is Tres (Collection)', isNotBindable: 'Is Not Tres (Collection)']

        when:
        binder.bind(obj, new SimpleMapDataBindingSource(map))
        def listOfWidgets = obj.listOfWidgets
        def setOfWidgets = obj.setOfWidgets
        //def collectionOfWidgets = obj.collectionOfWidgets
        def sortedSetOfWidgets = obj.sortedSetOfWidgets

        then:
        listOfWidgets instanceof List
        listOfWidgets.size() == 3
        listOfWidgets[0].isBindable == 'Is Uno (List)'
        listOfWidgets[0].isNotBindable == null
        listOfWidgets[1].isBindable == 'Is Dos (List)'
        listOfWidgets[1].isNotBindable == null
        listOfWidgets[2].isBindable == 'Is Tres (List)'
        listOfWidgets[2].isNotBindable == null

        setOfWidgets instanceof Set
        !(setOfWidgets instanceof SortedSet)
        setOfWidgets.size() == 3
        setOfWidgets.find { it.isBindable == 'Is Uno (Set)' && it.isNotBindable == null }
        setOfWidgets.find { it.isBindable == 'Is Dos (Set)' && it.isNotBindable == null }
        setOfWidgets.find { it.isBindable == 'Is Tres (Set)' && it.isNotBindable == null }

        sortedSetOfWidgets instanceof SortedSet
        sortedSetOfWidgets.size() == 3
        sortedSetOfWidgets[0].isBindable == 'Is Dos (SortedSet)'
        sortedSetOfWidgets[1].isBindable == 'Is Tres (SortedSet)'
        sortedSetOfWidgets[2].isBindable == 'Is Uno (SortedSet)'
    }

    void 'Test that binding errors are populated on a @Validateable instance'() {

        given:
        def obj = new SomeValidateableClass()
        def whiteList = ['someNumber']
        def blackList = ['someOtherProperty']

        when: 'binding with just a binding source'
        binder.bind(obj, new SimpleMapDataBindingSource([
            someNumber: 'not a number'
        ]))

        then:
        obj.hasErrors()
        obj.errors.errorCount == 1
        obj.errors.getFieldError('someNumber').code == 'typeMismatch'

        when:
        obj.clearErrors()

        then:
        !obj.hasErrors()

        when: 'binding with a binding source and a white list'
        binder.bind(obj, new SimpleMapDataBindingSource([
            someNumber: 'not a number'
        ]), whiteList)

        then:
        obj.hasErrors()
        obj.errors.errorCount == 1
        obj.errors.getFieldError('someNumber').code == 'typeMismatch'

        when:
        obj.clearErrors()

        then:
        !obj.hasErrors()

        when: 'binding with a binding source, a white list and a black list'
        binder.bind(obj, new SimpleMapDataBindingSource([
            someNumber: 'not a number'
        ]), whiteList, blackList)

        then:
        obj.hasErrors()
        obj.errors.errorCount == 1
        obj.errors.getFieldError('someNumber').code == 'typeMismatch'

        when:
        obj.clearErrors()

        then:
        !obj.hasErrors()

        when: 'binding with a binding source and a listener'
        def beforeBindingArgs = []
        def bindingErrorArgs = []
        def afterBindingArgs = []
        def listener = new DataBindingListenerAdapter() {
            @Override Boolean beforeBinding(Object object, String propertyName, Object value, Object errors) {
                beforeBindingArgs << [object: object, propName: propertyName, value: value]
                true
            }

            @Override void afterBinding(Object object, String propertyName, Object errors) {
                afterBindingArgs << [object: object, propertyName: propertyName]
            }

            @Override void bindingError(BindingError error, Object errors) {
                bindingErrorArgs << error
            }
        }

        binder.bind(obj, new SimpleMapDataBindingSource([
            someNumber: 'not a number'
        ]), listener)

        then:
        obj.hasErrors()
        obj.errors.errorCount == 1
        obj.errors.getFieldError('someNumber').code == 'typeMismatch'
        beforeBindingArgs.size() == 1
        beforeBindingArgs[0]['object'].is(obj)
        beforeBindingArgs[0]['propName'] == 'someNumber'
        beforeBindingArgs[0]['value'] == 'not a number'
        bindingErrorArgs.size() == 1
        bindingErrorArgs[0]['object'].is(obj)
        bindingErrorArgs[0]['propertyName'] == 'someNumber'
        bindingErrorArgs[0]['rejectedValue'] == 'not a number'
        afterBindingArgs.size() == 1
        afterBindingArgs[0]['object'].is(obj)
        afterBindingArgs[0]['propertyName'] == 'someNumber'
    }
    
    void 'blank binding errors respect domain property nullability'() {

        given:
        binder.convertEmptyStringsToNull = false
        binder.registerConverter(new ValueConverter() {
            boolean canConvert(Object value) {
                value instanceof String
            }

            Object convert(Object value) {
                throw new IllegalArgumentException('Blank status')
            }

            Class<?> getTargetType() {
                BinderNullabilityStatus
            }
        })
        def entity = new BinderNullabilityEntity()

        when:
        binder.bind(entity, new SimpleMapDataBindingSource(
            optionalStatus: '',
            requiredStatus: ''
        ))

        then:
        entity.hasErrors()
        entity.errors.errorCount == 1
        entity.errors.getFieldError('requiredStatus').code == 'typeMismatch'
        entity.errors.getFieldError('optionalStatus') == null
    }

    void 'Test binding a List<String>'() {

        given:
        def obj = new CollectionContainer()
        
        when:
        binder.bind(obj, new SimpleMapDataBindingSource([
            listOfStrings: ['One', 'Two', 'Three']
        ]))
        
        then:
        obj.listOfStrings == ['One', 'Two', 'Three']
    }
    
    void 'Test one to many list binding with nested subscript operator can insert to empty index of List'() {

        given:
        def book = new AssociationBindingBook()

        when:
        def author = new AssociationBindingAuthor(name: "William Gibson").save()
        def page1 = new AssociationBindingPage(number: 1).save()
        def page2 = new AssociationBindingPage(number: 2).save()
        binder.bind(book, new SimpleMapDataBindingSource([
            title: 'Pattern Recognition',
            author: author,
            pages: [null, page2]
        ]))
        book.save()
        binder.bind(author, new SimpleMapDataBindingSource([
            'books[0]': [
                'pages[0]': [
                    id: page1.id
                ]
            ]
        ]))

        then:
        2 == author.books[0].pages.size()
        author.books[0].pages.find { it.id == page1.id }
        author.books[0].pages.find { it.id == page2.id }
        2 == author.books.sum { it.pages.size() }
        !author.books.any { it.pages.contains(null) }
    }

    void 'Test typeMismatch error codes'() {

        given:
        def obj = new SomeValidateableClass()

        when:
        binder.bind(obj, new SimpleMapDataBindingSource([
            someNumber: 'not a number'
        ]))

        then:
        obj.hasErrors()
        obj.errors.errorCount == 1
        obj.errors.getFieldError('someNumber').codes as List == [
            'grails.web.databinding.SomeValidateableClass.someNumber.typeMismatch.error',
            'grails.web.databinding.SomeValidateableClass.someNumber.typeMismatch',
            'someValidateableClass.someNumber.typeMismatch.error',
            'someValidateableClass.someNumber.typeMismatch',
            'typeMismatch.grails.web.databinding.SomeValidateableClass.someNumber',
            'typeMismatch.someNumber',
            'typeMismatch.java.lang.Integer',
            'typeMismatch'
        ]
    }
    
    @Issue('GRAILS-10696')
    void 'Test binding a simple String to a List<Long> on a non domain class'() {

        given:
        def obj = new SomeNonDomainClass()
        
        when:
        binder.bind(obj, new SimpleMapDataBindingSource([
            listOfLong: '42'
        ]))
        
        then:
        obj.listOfLong[0] == 42
    }
    
    @Issue('GRAILS-10689')
    void 'Test binding a String[] to a List<Long> on a non domain class'() {

        given:
        def obj = new SomeNonDomainClass()
        
        when:
        binder.bind(obj, new SimpleMapDataBindingSource([
            listOfLong: ['42', '2112'] as String[]
        ]))
        
        then:
        obj.listOfLong.size() == 2
        obj.listOfLong[0] == 42
        obj.listOfLong[1] == 2112
    }

    @Issue('GRAILS-10696')
    void 'Test binding a simple String to a List<Long> on a domain class'() {

        given:
        def obj = new CollectionContainer()
        
        when:
        binder.bind(obj, new SimpleMapDataBindingSource([
            listOfLong: '42'
        ]))
        
        then:
        obj.listOfLong[0] == 42
    }

    @Issue('GRAILS-10689')
    void 'Test binding  String[] to a List<Long> on a domain class'() {

        given:
        def obj = new CollectionContainer()
        
        when:
        binder.bind(obj, new SimpleMapDataBindingSource([
            listOfLong: ['42', '2112'] as String[]
        ]))
        
        then:
        obj.listOfLong.size() == 2
        obj.listOfLong[0] == 42
        obj.listOfLong[1] == 2112
    }
    
    void 'Test @BindUsing on a List of domain objects'() {

        given:
        def pub = new Publisher()
        
        when:
        binder.bind(pub, new SimpleMapDataBindingSource([
            widgets: '4'
        ]))
        
        then:
        pub.widgets.size() == 4
        pub.widgets[0] instanceof Widget
        pub.widgets[1] instanceof Widget
        pub.widgets[2] instanceof Widget
        pub.widgets[3] instanceof Widget
    }

    void 'Test @BindUsing on a List<Integer>'() {

        given:
        def widget = new Widget()
        
        when:
        binder.bind(widget, new SimpleMapDataBindingSource([
            listOfIntegers: '4'
        ]))
        
        then:
        widget.listOfIntegers == [0, 1, 2, 3]
    }
    
    @Issue('GRAILS-10899')
    void 'Test binding to a property that has a getter and setter with declared type java.util.Collection'() {

        when:
        def f = new Foo(airports: ['STL', 'LHR', 'MIA'])
        
        then:
        f.airports.size() == 3
        f.airports.containsAll('STL', 'LHR', 'MIA')
    }
    
    @Issue('GRAILS-10899')
    void 'Test binding to a collection of values which need to be converted to a collection property that has a getter and setter with declared type java.util.Collection'() {

        when:
        def f = new Foo(numbers: ['2112', '42', '0'])
        
        then:
        f.numbers == [0, 42, 2112] as Set
    }
    
    @Issue('GRAILS-10728')
    void 'Test binding to a Set property that has a getter which returns an unmodifiable Set'() {

        given:
        def f = new Foo(names: ['Lemmy', 'Phil', 'Mikkey'] as Set)
        
        expect:
        f.names == ['Lemmy', 'Phil', 'Mikkey'] as Set
    }
    
    @Issue('GRAILS-10728')
    void 'Test binding to a collection property that has a setter and no getter'() {

        given:
        def f = new Foo(workdays: [Calendar.MONDAY, Calendar.TUESDAY])
        
        expect:
        f.getTheValueOfWorkdays() == [Calendar.MONDAY, Calendar.TUESDAY] as Set
    }

    @Issue('GRAILS-10717')
    void 'Test binding to a property that does not correspond to a field'() {

        given:
        def f = new Foo(activeDays:['mon'])
        
        expect:
        f.activeDays == ['mon']
    }
    
    @Issue('GRAILS-10790')
    void 'Test binding to a Map on a non domain class'() {

        given:
        def obj = new NonDomainClassWithMapProperty()
        
        when:
        binder.bind(obj, new SimpleMapDataBindingSource([
            name: 'Alpha Omega',
            'albums[uno]': [
                title: 'Album Number One'
            ],
            'albums[dos]': [
                title: 'Album Number Two'
            ]
        ]))
                     
        then:
        obj.name == 'Alpha Omega'
        obj.albums.size() == 2
        obj.albums['uno'] instanceof Album
        obj.albums['uno'].title == 'Album Number One'
        obj.albums['dos'] instanceof Album
        obj.albums['dos'].title == 'Album Number Two'
    }
    
    @Issue(['GRAILS-10796','GRAILS-10829'])
    void 'Test replacing existing collection of persistent entities'() {

        given: 
        def container = new CollectionContainer().save()
        
        when:
        ['one', 'two', 'three'].each { name ->
            def widget = new Widget(isBindable: name)
            widget.isNotBindable = ''
            container.addToSetOfWidgets(widget)
        }
        
        then:
        container.save()
        container.setOfWidgets.size() == 3
        def originalSetOfWidgets = container.setOfWidgets
        
        when: 'A List of ids is bound to the collection container'
        def newWidgets = ['four', 'five'].collect { name ->
            def widget = new Widget(isBindable: name)
            widget.isNotBindable = ''
            widget.save().id
        } 
        binder.bind(container, new SimpleMapDataBindingSource([
            setOfWidgets: newWidgets
        ]))
        
        then: 'the set of widgets should have been replaced, not appended to'
        container.setOfWidgets.find { it.isBindable == 'four' }
        container.setOfWidgets.find { it.isBindable == 'five' }
        container.setOfWidgets.size() == 2
        
        and: 'The containing Set is the same set that we started with'
        originalSetOfWidgets.is(container.setOfWidgets)
    }
    
    @Issue('GRAILS-10910')
    void 'Test binding an empty List to a List property which has elements in it'() {

        given:
        def publisher = new Publisher()
        
        when:
        publisher.addToPublications([title: 'Pub 1'])
        publisher.addToPublications([title: 'Pub 2'])
        
        then:
        publisher.publications.size() == 2
        
        when:
        binder.bind(publisher, new SimpleMapDataBindingSource([
            publications: []
        ]))
        
        then:
        publisher.publications.size() == 0
    }
    
    @Issue('GRAILS-11018')
    void 'Test binding an invalid String to a List<Long>'() {

        given:
        def command = new ListCommand()
        
        when:
        binder.bind(command, new SimpleMapDataBindingSource([
            myLongList: 'a,b,c'
        ]))
        
        then:
        command.hasErrors()
        command.errors.errorCount == 1
        command.errors['myLongList'].code == 'typeMismatch'
    }
    
    void 'Test binding to indexes of a List of Long which leaves gaps in the List'() {

        given:
        def obj = new SomeNonDomainClass()
        
        when:
        binder.bind(obj, new SimpleMapDataBindingSource([
            'listOfLong[1]': 1,
            'listOfLong[5]': 5,
            'listOfLong[3]': 3
        ]))
        
        then:
        obj.listOfLong.size() == 6
        obj.listOfLong[0] == null
        obj.listOfLong[1] == 1
        obj.listOfLong[2] == null
        obj.listOfLong[3] == 3
        obj.listOfLong[4] == null
        obj.listOfLong[5] == 5
    }
    
    
    void 'Test binding to indexes of a List of domain objects which leaves gaps in the List'() {

        given:
        def obj = new CollectionContainer()
        
        when:
        binder.bind(obj, new SimpleMapDataBindingSource([
            'listOfWidgets[1]': [
                'isBindable': 'one'
            ],
            'listOfWidgets[5]': [
                'isBindable': 'five'
            ],
            'listOfWidgets[3]': [
                'isBindable': 'three'
            ]
        ]))
        
        then:
        obj.listOfWidgets.size() == 6
        obj.listOfWidgets[0] == null
        obj.listOfWidgets[1].isBindable == 'one'
        obj.listOfWidgets[2] == null
        obj.listOfWidgets[3].isBindable == 'three'
        obj.listOfWidgets[4] == null
        obj.listOfWidgets[5].isBindable == 'five'
    }
    
    void 'Test binding to a TimeZone property'() {

        given:
        def obj = new Widget()
        
        when:
        binder.bind(obj, new SimpleMapDataBindingSource([
            timeZone: 'Europe/Berlin'
        ]))
        
        then:
        obj.timeZone == TimeZone.getTimeZone('Europe/Berlin')
    }
    
    void 'Test binding to a typed List of non-domain objects'() {

        given:
        def obj = new DocumentHolder()
        
        when:
        binder.bind(obj, new SimpleMapDataBindingSource([
            objectIds: ['two', 'four', 'six', 'eight']
        ]))
        
        then:
        obj.objectIds.size() == 4
        obj.objectIds[0] instanceof ObjectId
        obj.objectIds[0].value == 'two'
        obj.objectIds[1] instanceof ObjectId
        obj.objectIds[1].value == 'four'
        obj.objectIds[2] instanceof ObjectId
        obj.objectIds[2].value == 'six'
        obj.objectIds[3] instanceof ObjectId
        obj.objectIds[3].value == 'eight'
    }
    
    void 'Test binding to a typed array of non-domain objects'() {

        given:
        def obj = new DocumentHolder()
        
        when:
        binder.bind(obj, new SimpleMapDataBindingSource([
            objectIds: ['two', 'four', 'six', 'eight'] as String[]
        ]))
        
        then:
        obj.objectIds.size() == 4
        obj.objectIds[0] instanceof ObjectId
        obj.objectIds[0].value == 'two'
        obj.objectIds[1] instanceof ObjectId
        obj.objectIds[1].value == 'four'
        obj.objectIds[2] instanceof ObjectId
        obj.objectIds[2].value == 'six'
        obj.objectIds[3] instanceof ObjectId
        obj.objectIds[3].value == 'eight'
    }
    
    @Issue('GRAILS-11174')
    void 'Test binding null to a Date marked with @BindingFormat'() {

        given:
        def obj = new DataBindingBook()
        
        when:
        binder.bind(obj, new SimpleMapDataBindingSource([
            datePublished: null
        ]))
        
        then:
        obj.datePublished == null
        !obj.hasErrors()
        
        when:
        obj.datePublished = new Date()
        binder.bind(obj, new SimpleMapDataBindingSource([
            datePublished: null
        ]))
        
        then:
        obj.datePublished == null
        !obj.hasErrors()
    }
    
    @Issue('GRAILS-11238')
    void 'Test binding to a property that hides a field of a different type'() {

        given:
        def holder = new AlbumHolder()
        def album = new Album(title: 'Some Album')

        when:
        binder.bind(holder, new SimpleMapDataBindingSource([
            album: album
        ]))
        
        then:
        holder.album.title == 'Some Album'
    }
    
    @Issue('GRAILS-11402')
    void 'Test binding when the binding source contains the key "_"'() {

        given:
        def publisher = new Publisher()
        
        when:
        binder.bind(publisher, new SimpleMapDataBindingSource([
            _: '',
            name: 'Some Publisher'
        ]))
        
        then:
        !publisher.hasErrors()
        publisher.name == 'Some Publisher'
    }
    
    @Issue('GRAILS-11472')
    void 'test binding an empty string to a Date marked with @BindingFormat'() {

        given:
        def book = new DataBindingBook()
        def datePublished = Calendar.instance

        when: 'a valid date string is bound'
        binder.bind(book, new SimpleMapDataBindingSource([
            datePublished: '11151969'
        ]))
        datePublished.setTime(book.datePublished)
        
        then: 'the date is initialized'
        !book.hasErrors()
        book.datePublished
        Calendar.NOVEMBER == datePublished.get(Calendar.MONTH)
        15 == datePublished.get(Calendar.DAY_OF_MONTH)
        1969 == datePublished.get(Calendar.YEAR)
        
        when: 'an empty string is bound'
        binder.bind(book, new SimpleMapDataBindingSource([
            datePublished: ''
        ]))
        
        then: 'the date is null'
        book.datePublished == null
        !book.hasErrors()
    }

    void 'test binding an Date to code in @BindingFormat'() {
        given:
        Locale.setDefault(new Locale('en', 'US', ''))
        ((StaticMessageSource) messageSource).addMessage('my.date.format', Locale.US, 'MMddyyyy')
        def child = new Child()
        def birthDate = Calendar.instance

        when: 'a valid date string is bound'
        binder.bind(child, new SimpleMapDataBindingSource([
            birthDate: '11151969'
        ]))
        birthDate.setTime(child.birthDate)

        then: 'the date is initialized'
        !child.hasErrors()
        child.birthDate
        Calendar.NOVEMBER == birthDate.get(Calendar.MONTH)
        15 == birthDate.get(Calendar.DAY_OF_MONTH)
        1969 == birthDate.get(Calendar.YEAR)
    }
    
    void 'Test binding String to currency in a domain class'() {
        given:
        def publisher = new Publisher()
        
        when:
        binder.bind(publisher, new SimpleMapDataBindingSource([
            localCurrency: 'USD'
        ]))

        then:
        publisher.localCurrency instanceof Currency
        'USD' == publisher.localCurrency.currencyCode
    }
    
    @Issue('GRAILS-11666')
    void 'test binding array of id to a collection of domain instances in a non domain classes'() {
        given:
        def pub1 = new Publisher(name: 'Pub One').save()
        new Publisher(name: 'Pub Two').save()
        def pub3 = new Publisher(name: 'Pub Three').save()
        def obj = new NonDomainClassWithSetOfDomainInstances()
        String[] idArray = [pub1.id, pub3.id] as String[]
        
        when:
        binder.bind(obj, new SimpleMapDataBindingSource([
            publishers: idArray
        ]))
        
        then:
        obj.publishers?.size() == 2
        obj.publishers.find { it.name == 'Pub One' }
        obj.publishers.find { it.name == 'Pub Three' }
    }

    void 'test binding maps into a raw collection preserves the map elements'() {
        given: 'a domain with a raw (non-generic) collection, whose component type falls back to Object'
        def obj = new RawCollectionContainer()

        when: 'a list of maps is bound to it'
        binder.bind(obj, new SimpleMapDataBindingSource([
            rawList: [[label: 'Answered', param: 'status=resolved'],
                      [label: 'Pending', param: 'status=pending']]
        ]))

        then: 'the maps survive binding rather than being replaced by empty Object instances'
        obj.rawList.size() == 2
        obj.rawList.every { it instanceof Map }
        obj.rawList[0].label == 'Answered'
        obj.rawList[0].param == 'status=resolved'
        obj.rawList[1].label == 'Pending'
        obj.rawList[1].param == 'status=pending'
    }

    void 'test binding maps into a raw Set property preserves the map elements'() {
        given:
        def obj = new RawCollectionContainer()

        when:
        binder.bind(obj, new SimpleMapDataBindingSource([
            rawSet: [[label: 'Answered', param: 'status=resolved']]
        ]))

        then:
        obj.rawSet.every { it instanceof Map }
        obj.rawSet.first().label == 'Answered'
    }

    void 'test binding maps into a raw Collection-typed property'() {
        given:
        def obj = new RawCollectionContainer()

        when:
        binder.bind(obj, new SimpleMapDataBindingSource([rawCollection: [[label: 'Answered']]]))

        then:
        obj.rawCollection.every { it instanceof Map }
        obj.rawCollection[0].label == 'Answered'
    }

    void 'test binding DataBindingSource items into a raw collection'() {
        given:
        def obj = new RawCollectionContainer()

        when:
        binder.bind(obj, new SimpleMapDataBindingSource([
            rawList: [new SimpleMapDataBindingSource([label: 'Answered'])]
        ]))

        then:
        obj.rawList.size() == 1
        obj.rawList[0].getClass() != Object
    }

    void 'test binding maps into a raw collection with deny-by-default enabled'() {
        given: 'the opt-in hardening turned on, and the property explicitly allowlisted'
        def originalConfig = Holders.config
        Holders.setConfig(new PropertySourcesConfig([(Settings.DATABINDING_DENY_BY_DEFAULT): true]))
        DataBindingUtils.clearBindingCaches()
        def obj = new RawCollectionContainer()

        when:
        binder.bind(obj, new SimpleMapDataBindingSource([
            rawList: [[label: 'Answered', param: 'status=resolved']]
        ]), null, ['rawList'], null, null)

        then: 'the elements are still maps, as they are with the hardening off'
        obj.rawList.size() == 1
        obj.rawList[0] instanceof Map
        obj.rawList[0].label == 'Answered'

        cleanup:
        Holders.setConfig(originalConfig)
        DataBindingUtils.clearBindingCaches()
    }
}

@Entity
class RawCollectionContainer {

    List rawList = []
    Map rawMap = [:]
    Set rawSet = []
    Collection rawCollection = []
}

@Entity
class Team {

    Map members
    Map states

    @SuppressWarnings('unused')
    static hasMany = [
        members: Author,
        states: String
    ]
}

@Entity
@SuppressWarnings('unused')
class Publisher {

    String name
    List<Publication> publications
    
    @BindUsing({ Object obj, DataBindingSource source ->
        def cnt = source['widgets'] as int
        def result = []
        cnt.times { result << new Widget() }
        result
    })
    List widgets = []
    
    Currency localCurrency

    static hasMany = [
        publications: Publication,
        authors: Author,
        widgets: Widget
    ]

    static constraints = {
        localCurrency(nullable: true)
    }
}

class SomeNonDomainClass {
    Publication publication
    List<Long> listOfLong
}

@Entity
class Publication {

    String title
    Author author

    static constraints = {
        publisher nullable: true
    }

    @SuppressWarnings('unused')
    static belongsTo = [publisher: Publisher]
}

@Entity
@SuppressWarnings('unused')
class Author {

    String name

    @BindUsing({ Object obj, DataBindingSource source ->
        ((String)source.getPropertyValue('stringWithSpecialBinding'))?.trim()
    })
    String stringWithSpecialBinding

    @BindUsing({ Object obj, DataBindingSource source ->
        // could have conditional logic here
        // that instantiates different types
        // based on entries in the source map
        // or some other criteria.
        // in this case, hardcoded to return a
        // particular type.
        new Fidget(source['widget'] as Map)
    })
    ParentWidget widget

    static constraints = {
        widget(nullable: true)
        stringWithSpecialBinding(nullable: true)
    }
}

@Entity
@Sortable(includes = ['isBindable', 'isNotBindable'])
@SuppressWarnings('unused')
class Widget {

    String isBindable
    String isNotBindable
    TimeZone timeZone

    @BindUsing({ Object obj, DataBindingSource source ->
        def cnt = source['listOfIntegers'] as int
        def result = []
        cnt.times { result << it }
        result
    })
    List<Integer> listOfIntegers = []

    static constraints = {
        isNotBindable(bindable: false)
        timeZone(nullable: true)
    }
}

@Entity
@Sortable(includes = ['isBindable', 'isNotBindable'])
@SuppressWarnings('unused')
class ParentWidget implements Validateable {

    String isBindable
    String isNotBindable

    @BindUsing({ Object obj, DataBindingSource source ->
        def cnt = source['listOfIntegers'] as int
        def result = []
        cnt.times { result << it }
        result
    })
    List<Integer> listOfIntegers = []

    TimeZone timeZone

    static constraints = {
        isNotBindable(bindable: false)
        timeZone(nullable: true)
    }
}

@Entity
class Fidget extends ParentWidget {
    String name
}

@Entity
class Parent {
    Child child
}

@Entity
@SuppressWarnings('unused')
class Child {

    @BindingFormat(code='my.date.format')
    Date birthDate

    static hasMany = [someOtherIds: Integer]

}

@Entity
class GeneratedBindingParent {
    GeneratedBindingChild child

    static constraints = {
        child bindable: true
    }
}

@Entity
class GeneratedBindingChild {
    String name
    boolean admin

    static constraints = {
        name bindable: true
    }
}

class GeneratedBindingArrayHolder implements Validateable {
    GeneratedBindingChild[] children = []

    static constraints = {
        children bindable: true
    }
}

class SecureMapConstructorHolder implements Validateable {
    Map<String, SecureMapConstructorValue> values
    SecureMapConstructorValue[] arrayValues = []

    static constraints = {
        values bindable: true
        arrayValues bindable: true
    }
}

class SecureMapConstructorValue implements Validateable {
    String name
    boolean admin

    SecureMapConstructorValue(Map values) {
        name = values.name
        admin = values.admin as boolean
    }

    static constraints = {
        name bindable: true
        admin bindable: false
    }
}

class TypedMapBindingHolder {
    Map<String, TypedMapBindingValue> values = [:]
}

class TypedMapBindingValue implements Validateable {
    Integer quantity
}

@Entity
@SuppressWarnings('unused')
class DataBindingBook {

    String title
    List importantPageNumbers
    List topics

    @BindingFormat('MMddyyyy')
    Date datePublished

    static hasMany = [
        topics: String,
        importantPageNumbers: Integer
    ]
}

@Entity
@SuppressWarnings('unused')
class CollectionContainer {

    List<Widget> listOfWidgets
    SortedSet<Widget> sortedSetOfWidgets
    Collection<Widget> collectionOfWidgets
    List<String> listOfStrings
    List<Long> listOfLong

    static hasMany = [
        listOfWidgets: Widget,
        setOfWidgets: Widget,
        collectionOfWidgets: Widget,
        sortedSetOfWidgets: Widget
    ]
}

class DocumentHolder {
    List<ObjectId> objectIds
}

class ObjectId {

    String value
    
    ObjectId(String str) {
        value = str
    }
}

class PrimitiveContainer implements Validateable {
    boolean someBoolean
    byte someByte
    char someChar
    short someShort
    int someInt
    long someLong
    float someFloat
    double someDouble
}

@SuppressWarnings('unused')
class SomeValidateableClass implements Validateable {
    Integer someNumber
}

@Entity
class BinderNullabilityEntity {
    BinderNullabilityStatus optionalStatus
    BinderNullabilityStatus requiredStatus

    static constraints = {
        requiredStatus nullable: false
    }
}

enum BinderNullabilityStatus {
    ACTIVE
}

@Entity
class AssociationBindingPage {
    Integer number
}

@Entity
@SuppressWarnings('unused')
class AssociationBindingBook {

    String title
    List<AssociationBindingPage> pages

    static belongsTo = [author: AssociationBindingAuthor]
    static hasMany = [pages: AssociationBindingPage]
}

@Entity
@SuppressWarnings('unused')
class AssociationBindingAuthor {

    String name
    List<AssociationBindingBook> books

    static hasMany = [books: AssociationBindingBook]
}

@Entity
@SuppressWarnings('unused')
class Foo {

    Boolean activeMonday
    Collection<Integer> numbers

    private Set<String> _names

    private transient Collection<String> _airports
    private transient Set<Integer> _workdays

    static constraints = {
        activeDays(bindable: true)
    }

    static transients = ['activeDays']
    
    List getActiveDays() {
        def activeDays = []
        if (activeMonday) activeDays << 'mon'
        activeDays
    }
    void setActiveDays(List activeDays) {
        if (activeDays.contains('mon')) {
            activeMonday = true
        }
    }
    
    void setWorkdays(Collection<Integer> workdays) {
        _workdays = new HashSet<Integer>(workdays)
    }
    
    def getTheValueOfWorkdays() {
        _workdays
    }
    
    void setNames(Set<String> names) {
        _names = names
    }
    Set<String> getNames() {
        Collections.unmodifiableSet(_names ?: [] as Set<String>)
    }
    
    void setAirports(Collection<String> airports) {
        _airports = airports
    }
    Collection<String> getAirports() {
        _airports
    }
}

class NonDomainClassWithMapProperty {
    String name
    Map<String, Album> albums
}

class NonDomainClassWithSetOfDomainInstances {
    Set<Publisher> publishers
}

class Album {
    String title
}

@SuppressWarnings('unused')
class AlbumHolder {
    // see GRAILS-11238
    String album

    void setAlbum(Album a) {
        album = a.title
    }
    
    Album getAlbum() {
        return new Album(title: album)
    }
}

@SuppressWarnings('unused')
class ListCommand implements Validateable {
    List<Long> myLongList
}
