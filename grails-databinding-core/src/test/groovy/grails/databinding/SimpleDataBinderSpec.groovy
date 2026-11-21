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
package grails.databinding

import grails.databinding.converters.ValueConverter
import grails.databinding.errors.BindingError
import grails.databinding.events.DataBindingListener
import grails.databinding.events.DataBindingListenerAdapter
import org.grails.databinding.IndexedPropertyReferenceDescriptor
import org.grails.databinding.converters.DateConversionHelper
import org.grails.databinding.converters.LocalDateTimeConverter
import spock.lang.Issue
import spock.lang.PendingFeature
import spock.lang.Specification

import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class SimpleDataBinderSpec extends Specification {

    void 'Test binding to dynamically typed properties'() {
        given:
        def binder = new SimpleDataBinder()
        def w = new Widget()

        when:
        binder.bind(w, new SimpleMapDataBindingSource(alpha: 1, beta: 2))

        then:
        w.alpha == 1
        w.beta == 2
    }

    void 'Test binding to dynamically typed inherited properties'() {
        given:
        def binder = new SimpleDataBinder()
        def g = new Gadget()

        when:
        binder.bind(g, new SimpleMapDataBindingSource([alpha: 1, beta: 2, gamma: 3]))

        then:
        g.alpha == 1
        g.beta == 2
        g.gamma == 3
    }

    @PendingFeature(reason = 'gadget is null')
    void 'Test binding to nested properties with dotted path syntax'() {
        given:
        def binder = new SimpleDataBinder()
        def f = new Fidget()

        when:
        binder.bind(f, new SimpleMapDataBindingSource([name: 'Stuff', 'gadget.gamma': 42, 'gadget.alpha': 43]))

        then:
        f.name == 'Stuff'
        f.gadget.gamma == 42
        f.gadget.alpha == 43
        f.gadget.beta == null
    }

    void 'Test binding nested Maps'() {
        given:
        def binder = new SimpleDataBinder()
        def f = new Fidget()

        when:
        binder.bind f, new SimpleMapDataBindingSource([name: 'Stuff', gadget: [gamma: 42, alpha: 43]])

        then:
        f.name == 'Stuff'
        f.gadget.gamma == 42
        f.gadget.alpha == 43
        f.gadget.beta == null
    }

    void 'Test binding String to Integer'() {
        given:
        def binder = new SimpleDataBinder()
        def w = new Widget()

        when:
        binder.bind(w, new SimpleMapDataBindingSource([alpha: 1, beta: 2, delta: '42']))

        then:
        w.alpha == 1
        w.beta == 2
        w.delta == 42
    }

    void 'Test binding Integer to Number'() {
        given: def binder = new SimpleDataBinder()
        def w = new Widget()

        when:
        binder.bind(w, new SimpleMapDataBindingSource([epsilon: 42]))

        then:
        w.epsilon == 42
    }

    void 'Test binding array of String to a Set of Strings'() {
        given:
        def binder = new SimpleDataBinder()
        def obj = new Widget()

        when:
        binder.bind obj, new SimpleMapDataBindingSource([widgetChildren: ['Child 1', 'Child 2', 'Child 3'] as String[]])

        then:
        3 == obj.widgetChildren?.size()
        'Child 1' in obj.widgetChildren
        'Child 2' in obj.widgetChildren
        'Child 3' in obj.widgetChildren
    }

    void 'Test date binding'() {
        given:
        def binder = new SimpleDataBinder()
        def obj = new DateContainer()
        def nowUtilDate = new Date()
        def nowSqlDate = new java.sql.Date(nowUtilDate.time)
        def localDateTime = "2013-04-15T21:26:31.973"
        def nowCalendar = Calendar.instance

        when:
        binder.bind(obj, new SimpleMapDataBindingSource([utilDate: nowUtilDate, sqlDate: nowSqlDate, calendar: nowCalendar, localDateTime: localDateTime]))

        then:
        obj.utilDate == nowUtilDate
        obj.sqlDate == nowSqlDate
        obj.calendar == nowCalendar
        obj.localDateTime == null

        when:
        binder.registerConverter(new LocalDateTimeConverter())
        binder.bind(obj, new SimpleMapDataBindingSource([localDateTime: "2013-04-15T21:26:31.974"]))

        then:
        obj.localDateTime == LocalDateTime.parse("2013-04-15T21:26:31.974", DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    }

    @Issue('GRAILS-10925')
    void 'Test binding a Date to a Date property marked with @BindingFormat'() {
        given:
        def binder = new SimpleDataBinder()
        def obj = new DateContainer()
        def nowDate = new Date()

        when:
        binder.bind obj, [formattedUtilDate: nowDate] as SimpleMapDataBindingSource

        then:
        obj.formattedUtilDate == nowDate
    }

    void 'Test listener is notified for properties in nested maps'() {
        given:
        def binder = new SimpleDataBinder()
        def obj = new DateContainer()
        def afterBindingEvents = []
        def listener = new DataBindingListenerAdapter() {
            void afterBinding(o, String propertyName, errors) {
                afterBindingEvents << [object: o, propertyName: propertyName]
            }
        }
        def f = new Fidget()

        when:
        binder.bind f, new SimpleMapDataBindingSource([name: 'Stuff', gadget: [gamma: 42, alpha: 43]]), listener

        then:
        f.name == 'Stuff'
        f.gadget.gamma == 42
        f.gadget.alpha == 43
        f.gadget.beta == null
        afterBindingEvents.size() == 4
        afterBindingEvents.find { it.object.is(f) && it.propertyName == 'name' }
        afterBindingEvents.find { it.object.is(f) && it.propertyName == 'gadget' }
        afterBindingEvents.find { it.object.is(f.gadget) && it.propertyName == 'alpha' }
        afterBindingEvents.find { it.object.is(f.gadget) && it.propertyName == 'gamma' }
    }

    void 'Test invalid date format'() {
        given:
        def binder = new SimpleDataBinder()
        def obj = new DateContainer()
        def bindingErrors = []
        def listener = new DataBindingListenerAdapter() {
            void bindingError(BindingError error, errors) {
                bindingErrors << error
            }
        }

        when:
        binder.bind obj, new SimpleMapDataBindingSource([formattedUtilDate: 'BAD']), listener

        then:
        obj.formattedUtilDate == null
        bindingErrors.size() == 1
        bindingErrors[0].rejectedValue == 'BAD'
        bindingErrors[0].cause.message == 'Unparseable date: "BAD"'
    }

    void 'Test binding string to date'() {
        given:
        def binder = new SimpleDataBinder()
        binder.registerConverter new DateConversionHelper(formatStrings: ['yyyy-MM-dd HH:mm:ss.S',"yyyy-MM-dd'T'HH:mm:ss'Z'","yyyy-MM-dd HH:mm:ss.S z","yyyy-MM-dd'T'HH:mm:ss.SSSX"])

        def obj = new DateContainer()

        when:
        binder.bind obj, new SimpleMapDataBindingSource([utilDate: '2013-04-15 21:26:31.973', formattedUtilDate: '11151969'])
        final Calendar calendar = Calendar.getInstance()
        calendar.setTime(obj.utilDate)

        then:
        Calendar.APRIL == calendar.get(Calendar.MONTH)
        15 == calendar.get(Calendar.DAY_OF_MONTH)
        2013 == calendar.get(Calendar.YEAR)
        21 == calendar.get(Calendar.HOUR_OF_DAY)
        26 == calendar.get(Calendar.MINUTE)
        31 == calendar.get(Calendar.SECOND)

        when:
        calendar.setTime(obj.formattedUtilDate)

        then:
        Calendar.NOVEMBER == calendar.get(Calendar.MONTH)
        15 == calendar.get(Calendar.DAY_OF_MONTH)
        1969 == calendar.get(Calendar.YEAR)

        when:
        obj.utilDate = null
        binder.bind obj, new SimpleMapDataBindingSource([utilDate: "2011-03-12T09:24:22Z"])
        calendar.setTime(obj.utilDate)

        then:
        Calendar.MARCH == calendar.get(Calendar.MONTH)
        12 == calendar.get(Calendar.DAY_OF_MONTH)
        2011 == calendar.get(Calendar.YEAR)
        9 == calendar.get(Calendar.HOUR_OF_DAY)
        24 == calendar.get(Calendar.MINUTE)
        22 == calendar.get(Calendar.SECOND)
    }

    void 'Test structured date binding'() {
        given:
        def binder = new SimpleDataBinder()
        def obj = new DateContainer()
        Calendar calendar = Calendar.getInstance()

        when:
        binder.bind(obj, new SimpleMapDataBindingSource([utilDate_month: '11',
                                                         utilDate_day: '15',
                                                         utilDate_year: '1969',
                                                         calendar_month: '4',
                                                         calendar_day: '21',
                                                         calendar_year: '2049',
                                                         sqlDate_month: '6',
                                                         sqlDate_day: '14',
                                                         sqlDate_year: '1937',
                                                         sqlDate: 'struct',
                                                         calendar: 'struct',
                                                         utilDate: 'struct']))
        def utilDate = obj.utilDate
        calendar.setTime(utilDate)

        then:
        Calendar.NOVEMBER == calendar.get(Calendar.MONTH)
        15 == calendar.get(Calendar.DAY_OF_MONTH)
        1969 == calendar.get(Calendar.YEAR)

        when:
        calendar.setTime(obj.sqlDate)

        then:
        Calendar.JUNE == calendar.get(Calendar.MONTH)
        14 == calendar.get(Calendar.DAY_OF_MONTH)
        1937 == calendar.get(Calendar.YEAR)

        when:
        def cal = obj.calendar

        then:
        Calendar.APRIL == cal.get(Calendar.MONTH)
        21 == cal.get(Calendar.DATE)
        2049 == cal.get(Calendar.YEAR)

        when:
        obj.utilDate = obj.calendar = obj.sqlDate = null
        binder.bind(obj, new SimpleMapDataBindingSource([utilDate_month: '11',
                                                         utilDate_day: '15',
                                                         utilDate_year: '1969',
                                                         calendar_month: '4',
                                                         calendar_day: '21',
                                                         calendar_year: '2049',
                                                         sqlDate_month: '6',
                                                         sqlDate_day: '14',
                                                         sqlDate_year: '1937',
                                                         sqlDate: 'date.struct',
                                                         calendar: 'date.struct',
                                                         utilDate: 'date.struct']))
        calendar.setTime(obj.utilDate)

        then:
        Calendar.NOVEMBER == calendar.get(Calendar.MONTH)
        15 == calendar.get(Calendar.DAY_OF_MONTH)
        1969 == calendar.get(Calendar.YEAR)

        when:
        calendar.setTime(obj.sqlDate)

        then:
        Calendar.JUNE == calendar.get(Calendar.MONTH)
        14 == calendar.get(Calendar.DAY_OF_MONTH)
        1937 == calendar.get(Calendar.YEAR)

        when:
        cal = obj.calendar

        then:
        Calendar.APRIL == cal.get(Calendar.MONTH)
        21 == cal.get(Calendar.DATE)
        2049 == cal.get(Calendar.YEAR)

        when:
        obj.utilDate = obj.calendar = obj.sqlDate = null
        binder.bind(obj, new SimpleMapDataBindingSource([utilDate_month: '11',
                                                         utilDate_day: '15',
                                                         utilDate_year: '1969',
                                                         calendar_month: '4',
                                                         calendar_day: '21',
                                                         calendar_year: '2049',
                                                         sqlDate_month: '6',
                                                         sqlDate_day: '14',
                                                         sqlDate_year: '1937',
                                                         sqlDate: 'struct',
                                                         calendar: 'struct',
                                                         utilDate: 'struct']), null, ['sqlDate', 'utilDate'])
        utilDate = obj.utilDate
        cal = obj.calendar

        then:
        utilDate == null
        Calendar.APRIL == cal.get(Calendar.MONTH)
        21 == cal.get(Calendar.DATE)
        2049 == cal.get(Calendar.YEAR)
    }

    void 'Test struct binding to a list'() {
        given:
        def binder = new SimpleDataBinder()
        def obj = new DateCollection()

        when:
        binder.bind(obj, new SimpleMapDataBindingSource([
                'dates[0]'      : 'struct',
                'dates[0]_day'  : '09',
                'dates[0]_month': '11',
                'dates[0]_year' : '2012',
                'dates[1]'      : 'struct',
                'dates[1]_day'  : '13',
                'dates[1]_month': '12',
                'dates[1]_year' : '2012',
        ]))
        def dates = obj.dates

        then:
        dates == [new SimpleDateFormat('yyyy-MM-d').parse("2012-11-9"), new SimpleDateFormat('yyyy-MM-d').parse("2012-12-13")]
    }

    void 'Test binding String to enum'() {
        given:
        def binder = new SimpleDataBinder()
        def user = new SystemUser()

        when:
        binder.bind user, new SimpleMapDataBindingSource([role: 'ADMIN'])

        then:
        user.role == Role.ADMIN

        when:
        binder.bind user, new SimpleMapDataBindingSource([role: null])

        then:
        user.role == null

        when:
        binder.bind user, new SimpleMapDataBindingSource([role: 'BAD'])

        then:
        user.role == null

        when:
        binder.bind user, new SimpleMapDataBindingSource([role: 'USER'])

        then:
        user.role == Role.USER
    }

    void 'Test special boolean handling'() {
        given:
        def binder = new SimpleDataBinder()
        def factory = new Factory()
        factory.isActive = true

        when:
        binder.bind factory, new SimpleMapDataBindingSource([_isActive: ''])

        then:
        !factory.isActive

        when:
        // the underscore one should be ignored since the real one is present
        binder.bind factory, new SimpleMapDataBindingSource([isActive: true, _isActive: ''])

        then:
        factory.isActive
    }

    void 'Test binding to a List with a combination of Map values and instances of the actual type contained in the List '() {
        given:
        def binder = new SimpleDataBinder()
        def bindingSource = [:]
        bindingSource.name = 'My Factory'

        // this list contains Maps and a Widget instance.  The Maps should be transformed into Widget instances
        bindingSource.widgets = [widget:[[alpha: 'alpha 1', beta: 'beta 1'], new Widget(alpha: 'alpha 2', beta: 'beta 2'), [alpha: 'alpha 3', beta: 'beta 3']]]
        def factory = new Factory()

        when:
        binder.bind factory, new SimpleMapDataBindingSource(bindingSource)

        then:
        factory.name == 'My Factory'
        factory.widgets.size() == 3
        factory.widgets[0] instanceof Widget
        factory.widgets[1] instanceof Widget
        factory.widgets[2] instanceof Widget
        factory.widgets[0].alpha == 'alpha 1'
        factory.widgets[0].beta == 'beta 1'
        factory.widgets[1].alpha == 'alpha 2'
        factory.widgets[1].beta == 'beta 2'
        factory.widgets[2].alpha == 'alpha 3'
        factory.widgets[2].beta == 'beta 3'
    }

    void 'Test binding to a Set of Integer'() {
        given:
        def binder = new SimpleDataBinder()
        def widget = new Widget()

        when:
        binder.bind widget, new SimpleMapDataBindingSource([numbers: ['4', '5', '6']])

        then:
        widget.numbers.size() == 3
        widget.numbers.contains 4
        widget.numbers.contains 5
        widget.numbers.contains 6

        when:
        widget.numbers = [1, 2, 3]
        binder.bind widget, new SimpleMapDataBindingSource([numbers: ['4', '5', '6']])

        then:
        widget.numbers.size() == 3
        widget.numbers.contains 4
        widget.numbers.contains 5
        widget.numbers.contains 6
    }

    void 'Test binding to an array of Integer'() {
        given:
        def widget = new Widget()
        def binder = new SimpleDataBinder()

        when:
        binder.bind widget, new SimpleMapDataBindingSource(['integers[0]': 42, 'integers[1]': '2112'])

        then:
        widget.integers.length == 2
        widget.integers[0] == 42
        widget.integers[1] == 2112
    }

    void 'Test binding ranges'() {
        given:
        def widget = new Widget()
        def binder = new SimpleDataBinder()

        when:
        binder.bind widget, new SimpleMapDataBindingSource([validNumbers: 3..5])

        then:
        widget.validNumbers == 3..5

        when:
        binder.bind widget, new SimpleMapDataBindingSource([validNumbers: null])

        then:
        widget.validNumbers == null

        when:
        binder.bind widget, new SimpleMapDataBindingSource([validNumbers: 1..5])

        then:
        widget.validNumbers == 1..5

        when:
        binder.bind widget, new SimpleMapDataBindingSource([byteArray: 3..7])

        then:
        widget.byteArray.length == 5
        widget.byteArray == ([3,4,5,6,7] as byte[])
    }

    void 'Test auto grow collection limit'() {
        given:
        def binder = new SimpleDataBinder()
        def widget = new Widget()
        binder.autoGrowCollectionLimit = 3

        when:
        binder.bind widget, new SimpleMapDataBindingSource(['integers[5]': 50, 'integers[0]': 10, 'integers[3]': 30, 'integers[2]': 20])

        then:
        widget.integers.length == 3
        widget.integers[0] == 10
        widget.integers[1] == null
        widget.integers[2] == 20

        when:
        binder.bind widget, new SimpleMapDataBindingSource(['names[5]': 'five', 'names[0]': 'zero', 'names[3]': 'three', 'names[2]': 'two'])

        then:
        widget.names.size() == 3
        widget.names[0] == 'zero'
        widget.names[1] == null
        widget.names[2] == 'two'
    }

    void 'Test @BindUsing on a List<Integer>'() {
        given:
        def binder = new SimpleDataBinder()
        def widget = new Widget()

        when:
        binder.bind widget, [listOfIntegers: '4'] as SimpleMapDataBindingSource

        then:
        widget.listOfIntegers == [0, 1, 2, 3]
    }

    @Issue('GRAILS-10853')
    void 'Test adding new elements to a Set using indexed properties'() {
        given:
        def binder = new SimpleDataBinder()
        def widget = new Widget()

        when:
        binder.bind widget, ['factories[2]': [name: 'Tres'], 'factories[0]': [name: 'Uno'], 'factories[1]': [name: 'Dos']] as SimpleMapDataBindingSource

        then:
        widget.factories.size() == 3
        widget.factories.find { it.name == 'Uno' }
        widget.factories.find { it.name == 'Dos' }
        widget.factories.find { it.name == 'Tres' }
    }

    @Issue('https://github.com/apache/grails-core/issues/15064')
    void 'A name with two indexers binds the first as the index and the second as a key of that element'() {
        given:
        def binder = new SimpleDataBinder()
        def table = new IndexedTable()

        when:
        binder.bind table, new SimpleMapDataBindingSource([
                'columns[0][data]': 'id', 'columns[0][name]': 'Id', 'columns[1][data]': 'title',
                'columns[1][search][value]': 'x'
        ])

        then:
        table.columns.size() == 2
        table.columns[0] == [data: 'id', name: 'Id']
        table.columns[1].data == 'title'
        table.columns[1]['search[value]'] == 'x'
    }

    @Issue('https://github.com/apache/grails-core/issues/15064')
    void 'A name with two indexers binds the properties of a typed element'() {
        given:
        def binder = new SimpleDataBinder()
        def table = new IndexedTable()

        when:
        binder.bind table, new SimpleMapDataBindingSource(['factories[1][name]': 'Dos', 'factories[0][name]': 'Uno'])

        then:
        table.factories*.name == ['Uno', 'Dos']
    }

    @Issue('https://github.com/apache/grails-core/issues/15064')
    void 'A name with two indexers adds to an element given as a map and quoted keys work'() {
        given:
        def binder = new SimpleDataBinder()
        def table = new IndexedTable()

        when:
        binder.bind table, new SimpleMapDataBindingSource(['columns[0]': [data: 'id'], "columns[0]['name']": 'Id'])

        then:
        table.columns == [[data: 'id', name: 'Id']]
    }

    @Issue('https://github.com/apache/grails-core/issues/15064')
    void 'A name with two indexers writes into a map the object already holds'() {
        given:
        def binder = new SimpleDataBinder()
        def table = new IndexedTable(columns: [[data: 'old', keep: 'me']])

        when:
        binder.bind table, new SimpleMapDataBindingSource(['columns[0][data]': 'new'])

        then: 'the entry is replaced and the others stay'
        table.columns == [[data: 'new', keep: 'me']]
    }

    @Issue('https://github.com/apache/grails-core/issues/15064')
    void 'A name with two indexers keeps the data source setting of the caller'() {
        given:
        def binder = new RecordingBinder()
        def table = new IndexedTable()
        def source = new SimpleMapDataBindingSource(['ignored[0][name]': 'x', 'columns[0][data]': 'id'])
        source.dataSourceAware = aware

        when:
        binder.bind table, source

        then:
        binder.seenDataSourceAware.every { it == aware }
        !binder.seenDataSourceAware.isEmpty()

        where:
        aware << [false, true]
    }

    @Issue('https://github.com/apache/grails-core/issues/15064')
    void 'A name with a space in the second index is skipped and does not stop the bind'() {
        given:
        def binder = new SimpleDataBinder()
        def table = new IndexedTable()

        when:
        binder.bind table, new SimpleMapDataBindingSource(['ignored[0][first name]': 'x', 'columns[0][data]': 'id'])

        then:
        table.columns == [[data: 'id']]
    }

    @Issue('https://github.com/apache/grails-core/issues/15064')
    void 'A name with two indexers applies to an object that the source supplies for the element'() {
        given:
        def binder = new SimpleDataBinder()
        def table = new IndexedTable()
        def factory = new Factory(name: 'Uno')

        when:
        binder.bind table, new SimpleMapDataBindingSource(['factories[0]': factory, 'factories[0][isActive]': true])

        then: 'the supplied factory keeps its identity and its other fields'
        table.factories.size() == 1
        table.factories[0].is(factory)
        factory.name == 'Uno'
        factory.isActive
    }

    @Issue('https://github.com/apache/grails-core/issues/15064')
    void 'A name with two indexers does not write into a held map of a property the bind may not change'() {
        given:
        def binder = new SimpleDataBinder()
        def table = new IndexedTable(columns: [[data: 'old']])

        when:
        binder.bind table, new SimpleMapDataBindingSource(['columns[0][data]': 'new']), whiteList, blackList

        then:
        table.columns == [[data: expected]]

        where:
        whiteList         | blackList     || expected
        null              | null          || 'new'
        ['columns']       | null          || 'new'
        ['factories']     | null          || 'old'
        null              | ['columns']   || 'old'
        ['columns']       | ['columns']   || 'old'
    }

    @Issue('https://github.com/apache/grails-core/issues/15064')
    void 'A filter that has an indexer of its own does not stop the held map from being written'() {
        given:
        def binder = new SimpleDataBinder()
        def table = new IndexedTable(columns: [[data: 'old', keep: 'me']])

        when:
        binder.bind(table, new SimpleMapDataBindingSource([(name): 'new']), filter, null, null)

        then:
        table.columns == expected

        where:
        name                        | filter         || expected
        'selected[0].columns[0][data]' | 'selected[0]' || [[data: 'new', keep: 'me']]
        'selected.columns[0][data]'    | 'selected'    || [[data: 'new', keep: 'me']]
        'other[0].columns[0][data]'    | 'selected[0]' || [[data: 'old', keep: 'me']]
    }

    @Issue('https://github.com/apache/grails-core/issues/15064')
    void 'a nested index or key past the growth limit does not grow what the object already holds'() {
        given:
        def binder = new SimpleDataBinder(autoGrowCollectionLimit: 3)
        def table = new IndexedTable(rows: [['a']], columns: [[data: 'old']])

        when:
        binder.bind table, new SimpleMapDataBindingSource(source)

        then:
        table.rows == expectedRows
        table.columns == expectedColumns

        where:
        source                                                       || expectedRows              | expectedColumns
        ['rows[0][2]': 'in']                                         || [['a', null, 'in']]       | [[data: 'old']]
        ['rows[0][1000]': 'far']                                     || [['a']]                   | [[data: 'old']]
        ['rows[0][3]': 'at the limit']                               || [['a']]                   | [[data: 'old']]
        ['columns[0][data]': 'new']                                  || [['a']]                   | [[data: 'new']]
        ['columns[0][one]': '1', 'columns[0][two]': '2']             || [['a']]                   | [[data: 'old', one: '1', two: '2']]
        ['columns[0][one]': '1', 'columns[0][two]': '2', 'columns[0][three]': '3'] || [['a']]    | [[data: 'old', one: '1', two: '2']]
    }

    @Issue('https://github.com/apache/grails-core/issues/15064')
    void 'A supplied object is bound only when its indexed name is accepted by the bind'() {
        given:
        def binder = new SimpleDataBinder()
        def table = new IndexedTable()
        def factory = new Factory(name: 'Uno')

        when:
        String prefix = prefixed ? 'selected.' : ''
        binder.bind(table, new SimpleMapDataBindingSource([(prefix + 'factories[0]'): factory, (prefix + 'factories[0][name]'): 'new']),
                filter, null, null)

        then:
        factory.name == expected

        where:
        prefixed | filter     || expected
        true     | 'selected' || 'new'
        false    | 'selected' || 'Uno'
        false    | null       || 'new'
    }

    @Issue('https://github.com/apache/grails-core/issues/15064')
    void 'A supplied object is changed only when the list or map it is named in accepts it'() {
        given:
        def binder = new SimpleDataBinder(autoGrowCollectionLimit: 3)
        def supplied = new Factory(name: 'Uno')
        def holder = new IndexedHolder(factories: heldList.collect { new Factory(name: it) }, byName: heldMap.collectEntries { k, v -> [(k): new Factory(name: v)] })

        when:
        binder.bind holder, new SimpleMapDataBindingSource([(property + '[' + index + ']'): supplied, (property + '[' + index + '][name]'): 'new'])

        then:
        supplied.name == (accepted ? 'new' : 'Uno')

        where:
        property    | index   | heldList    | heldMap                    || accepted
        'factories' | '0'     | []          | [:]                        || true
        'factories' | '2'     | []          | [:]                        || true
        'factories' | '3'     | []          | [:]                        || false
        'factories' | '1000'  | []          | [:]                        || false
        'factories' | '1'     | ['a']       | [:]                        || true
        'factories' | '2'     | ['a']       | [:]                        || true
        'factories' | '3'     | ['a']       | [:]                        || false
        'factories' | '1'     | ['a', 'b', 'c', 'd'] | [:]               || true
        'factories' | '5'     | ['a', 'b', 'c', 'd'] | [:]               || false
        'byName'    | 'x'     | []          | [:]                        || true
        'byName'    | 'x'     | []          | [a: '1', b: '2']           || true
        'byName'    | 'x'     | []          | [a: '1', b: '2', c: '3']   || false
        'byName'    | 'a'     | []          | [a: '1', b: '2', c: '3']   || true
    }

    @Issue('https://github.com/apache/grails-core/issues/15064')
    void 'The include and exclude lists and the filter decide whether a supplied object or a held map is changed'() {
        given:
        def binder = new SimpleDataBinder()
        def supplied = new Factory(name: 'Uno')
        def table = new IndexedTable(columns: [[data: 'old']])
        Map entries = [:]
        entries[prefix + 'factories[0]'] = supplied
        entries[prefix + 'factories[0][name]'] = 'new'
        entries[prefix + 'columns[0][data]'] = 'new'

        when:
        binder.bind table, new SimpleMapDataBindingSource(entries), filter, whiteList, blackList

        then:
        supplied.name == (objectChanged ? 'new' : 'Uno')
        table.columns == [[data: mapChanged ? 'new' : 'old']]

        where:
        prefix      | filter      | whiteList                 | blackList                 || objectChanged | mapChanged
        ''          | null        | null                      | null                      || true          | true
        ''          | null        | ['factories']             | null                      || true          | false
        ''          | null        | ['columns']               | null                      || false         | true
        ''          | null        | ['factories.name']        | null                      || true          | false
        ''          | null        | null                      | ['factories']             || false         | true
        ''          | null        | null                      | ['columns']               || true          | false
        ''          | null        | ['factories', 'columns']  | ['columns']               || true          | false
        'selected.' | 'selected'  | null                      | null                      || true          | true
        ''          | 'selected'  | null                      | null                      || false         | false
        'selected.' | 'selected'  | ['factories']             | null                      || true          | false
        'selected.' | 'selected'  | null                      | ['columns']               || true          | false
        'other.'    | 'selected'  | null                      | null                      || false         | false
    }

    @Issue('https://github.com/apache/grails-core/issues/15064')
    void 'A supplied object under an indexed name the object has no property for is not changed'() {
        given:
        def binder = new SimpleDataBinder()
        def table = new IndexedTable()
        def factory = new Factory(name: 'Uno')

        when:
        binder.bind table, new SimpleMapDataBindingSource(['missing[0]': factory, 'missing[0][name]': 'new'])

        then:
        factory.name == 'Uno'
    }

    @Issue('https://github.com/apache/grails-core/issues/15064')
    void 'A name with two indexers writes into a list that the object holds at the first index'() {
        given:
        def binder = new SimpleDataBinder()
        def table = new IndexedTable(rows: [['old']])

        when:
        binder.bind table, new SimpleMapDataBindingSource(['rows[0][0]': 'new', 'rows[0][2]': 'added'])

        then: 'the position is replaced, and a position past the end extends the list'
        table.rows == [['new', null, 'added']]
    }

    @Issue('https://github.com/apache/grails-core/issues/15064')
    void 'names with one indexer are bound as before'() {
        given:
        def binder = new SimpleDataBinder()
        def widget = new Widget()

        when:
        binder.bind widget, new SimpleMapDataBindingSource(['integers[1]': 2, 'integers[0]': 1])

        then:
        widget.integers == [1, 2]
    }

    @Issue('GRAILS-10865')
    void 'Test binding to an inherited typed collection'() {
        given:
        def binder = new SimpleDataBinder()
        def obj = new ClassWithInheritedTypedCollection()

        when:
        binder.bind obj, [list: ['1', '2', '3'], 'map[one]': '1', 'map[two]': '2' ] as SimpleMapDataBindingSource

        then:
        obj.list == [1, 2, 3]
        obj.map.one == 1
        obj.map.two == 2
    }

    @Issue('https://github.com/apache/grails-core/issues/11140')
    void 'Test bind a Integer on a List<Long>'() {
        given:
        def binder = new SimpleDataBinder()
        def widget = new Widget()

        when:
        binder.bind widget, [listOfLongs: 4] as SimpleMapDataBindingSource

        then:
        widget.listOfLongs == [4L]
        widget.listOfLongs.first().getClass() == Long
    }

    @Issue('https://github.com/apache/grails-core/issues/11235')
    void 'Test binding to a list using custom value converters'() {
        given:
        def binder = new SimpleDataBinder()
        def comment = new Comment()

        and:
        binder.registerConverter(new ValueConverter() {
            @Override
            boolean canConvert(Object value) {
                value instanceof String
            }

            @Override
            Object convert(Object value) {
                new Attachment(filename: "$value")
            }

            @Override
            Class<?> getTargetType() {
                return Attachment
            }
        })

        when:
        binder.bind comment, [
                'attachments[0]': 'foo.txt',
                'attachments[1]': 'bar.txt'
        ] as SimpleMapDataBindingSource

        then:
        comment.attachments.size() == 2
        comment.attachments.find { it.filename == 'foo.txt' }
        comment.attachments.find { it.filename == 'bar.txt' }
    }

    @Issue('https://github.com/apache/grails-core/issues/12150')
    void 'Test binding when class and embedded classes both implements an interface'() {
        given:
        SimpleDataBinder binder = new SimpleDataBinder()

        and:
        SimpleMapDataBindingSource input = [a: [data: 'abc']] as SimpleMapDataBindingSource
        ClassB classWithInterface = new ClassB()

        when:
        binder.bind(classWithInterface, input)

        then:
        classWithInterface.a.data == 'abc'
    }

    @Issue('https://github.com/apache/grails-core/issues/12150')
    void 'Test binding when class and embedded classes extends abstract class and implements an interface'() {
        given:
        SimpleDataBinder binder = new SimpleDataBinder()

        and:
        SimpleMapDataBindingSource input = [a: [data: 'abc']] as SimpleMapDataBindingSource
        FromAbstractB fromAbstractB = new FromAbstractB()

        when:
        binder.bind(fromAbstractB, input)

        then:
        fromAbstractB.a.data == 'abc'
    }

    void 'Test binding when property is static Object, it should not bind'() {
        given:
        def binder = new SimpleDataBinder()
        def widget = new Widget()

        when:
        binder.bind widget, [objectStaticProp: 6174] as SimpleMapDataBindingSource

        then:
        widget.objectStaticProp instanceof Closure
    }

    void 'Test binding when property is static, it should not bind'() {
        given:
        def binder = new SimpleDataBinder()
        def widget = new Widget()

        when:
        binder.bind widget, [stringStaticProp: 'abc'] as SimpleMapDataBindingSource

        then:
        widget.stringStaticProp == 'unchanged'
    }
}

class Factory {
    def name
    List<Widget> widgets
    boolean isActive
}

class Widget {
    def alpha
    def beta
    Integer delta
    Number epsilon
    Set<String> widgetChildren
    Gadget nestedGadget
    Set<Integer> numbers
    Range validNumbers = 4..42
    byte[] byteArray
    Integer[] integers
    List<String> names
    @BindUsing({ obj, source ->
        def cnt = source['listOfIntegers'] as int
        def result = []
        cnt.times { result << it }
        result
    })
    List<Integer> listOfIntegers = []
    List<Long> listOfLongs = []
    Set<Factory> factories
    static objectStaticProp = { -> 495 }
    static String stringStaticProp = 'unchanged'
}

class Gadget extends Widget {
    def gamma
}

class Fidget {
    def name
    Gadget gadget
}

class DateContainer {
    LocalDateTime localDateTime
    Date utilDate
    java.sql.Date sqlDate
    Calendar calendar

    @BindingFormat('MMddyyyy')
    Date formattedUtilDate
}

enum Role {
    ADMIN, USER
}

class SystemUser {
    Role role
}

abstract class AbstractClassWithTypedCollection {
    List<Integer> list
    Map<String, Integer> map
}

class ClassWithInheritedTypedCollection extends AbstractClassWithTypedCollection {}

class DateCollection {
    List<Date> dates
}

class IndexedHolder {
    List<Factory> factories
    Map<String, Factory> byName
}

class IndexedTable {
    List<Map> columns
    List<Factory> factories
    List<List> rows
}

class RecordingBinder extends SimpleDataBinder {
    List<Boolean> seenDataSourceAware = []

    @Override
    protected processIndexedProperty(obj, MetaProperty metaProperty, IndexedPropertyReferenceDescriptor descriptor, val, DataBindingSource source, DataBindingListener listener, errors) {
        seenDataSourceAware << source.dataSourceAware
        super.processIndexedProperty(obj, metaProperty, descriptor, val, source, listener, errors)
    }
}

class Comment {
    Set<Attachment> attachments
}

class Attachment {
    String filename
}

interface InterfaceA {
    String getData()
}

interface InterfaceB {
    InterfaceA getA()
}

class ClassA implements InterfaceA {
    String data
}

class ClassB implements InterfaceB {
    ClassA a
}

class AbstractB {
    ClassA a
}

class FromAbstractB extends AbstractB {

}
