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
package grails.gorm.services

import spock.lang.Specification

import org.grails.datastore.mapping.core.Datastore

/**
 * Tests that @Service abstract classes with injected @Service-typed properties
 * compile correctly under @CompileStatic. Verifies the fix for the bug where
 * ServiceTransformation generated lazy getters on the abstract class's PropertyNode
 * that referenced a 'datastore' field only present on the generated implementation class.
 * Under @CompileStatic, this caused "Unexpected return statement" compilation failures
 * because StaticTypeCheckingVisitor.visitProperty() cannot handle ReturnStatement
 * in property getter blocks.
 *
 * The fix removes the lazy getter block from the abstract class entirely and relies
 * on the eager initialization in the generated setDatastore() method on the impl class.
 *
 * @see org.grails.datastore.gorm.services.transform.ServiceTransformation
 */
class CompileStaticServiceInjectionSpec extends Specification {

    void "test @CompileStatic abstract class with injected @Service properties compiles"() {
        when: "A @CompileStatic @Service abstract class has a property of another @Service type"
        def service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.Service
import grails.gorm.annotation.Entity
import groovy.transform.CompileStatic

@Entity
class Book {
    String title
}

@Entity
class Author {
    String name
}

@Service(Author)
interface AuthorDataService {
    Author get(Serializable id)
}

@CompileStatic
@Service(Book)
abstract class BookService implements BookDataService {
    AuthorDataService authorDataService

    Book findBookAndAuthor(Serializable bookId, Serializable authorId) {
        Author author = authorDataService.get(authorId)
        Book book = get(bookId)
        return book
    }
}

interface BookDataService {
    Book get(Serializable id)
    Book save(Book book)
    List<Book> list()
}
''')

        then: "Compilation succeeds (previously failed with 'Unexpected return statement')"
        noExceptionThrown()

        and: 'The abstract class is recognized'
        !service.isInterface()

        when: 'The implementation class is loaded'
        def impl = service.classLoader.loadClass('$BookServiceImplementation')

        then: 'The impl exists and has the datastore infrastructure'
        impl != null
        impl.getDeclaredMethod('getDatastore').returnType == Datastore
        impl.getDeclaredMethod('setDatastore', Datastore) != null
    }

    void "test abstract class without @CompileStatic still works with injected @Service properties"() {
        when: 'A @Service abstract class without @CompileStatic has a @Service-typed property'
        def service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.Service
import grails.gorm.annotation.Entity

@Entity
class Item {
    String description
}

@Entity
class Category {
    String name
}

@Service(Category)
interface CategoryDataService {
    Category get(Serializable id)
}

@Service(Item)
abstract class ItemService implements ItemDataService {
    CategoryDataService categoryDataService

    Item findItemWithCategory(Serializable itemId, Serializable catId) {
        Category cat = categoryDataService.get(catId)
        Item item = get(itemId)
        return item
    }
}

interface ItemDataService {
    Item get(Serializable id)
    Item save(Item item)
}
''')

        then: 'Compilation succeeds (regression test — dynamic mode always worked)'
        noExceptionThrown()

        when: 'The impl is loaded'
        def impl = service.classLoader.loadClass('$ItemServiceImplementation')

        then: 'The impl has datastore infrastructure'
        impl != null
        impl.getDeclaredMethod('getDatastore').returnType == Datastore
    }

    void "test abstract class without @Service-typed properties does NOT get datastore infrastructure"() {
        when: 'A @Service abstract class has NO @Service-typed properties'
        def service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.Service
import grails.gorm.annotation.Entity

@Entity
class Task {
    String name
}

@Service(Task)
abstract class TaskService implements TaskDataService {
    String someConfig

    Task createTask(String name) {
        Task task = new Task(name: name)
        return save(task)
    }
}

interface TaskDataService {
    Task get(Serializable id)
    Task save(Task task)
}
''')

        then: 'Compilation succeeds'
        noExceptionThrown()

        when: 'The impl is loaded'
        def impl = service.classLoader.loadClass('$TaskServiceImplementation')

        then: 'The impl does NOT have datastore field (no @Service-typed properties to wire)'
        impl.declaredFields.find { it.name == 'datastore' } == null
    }

    void "test @CompileStatic abstract class with multiple injected @Service properties compiles"() {
        when: 'A @CompileStatic @Service abstract class has multiple @Service-typed properties'
        def service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.Service
import grails.gorm.annotation.Entity
import groovy.transform.CompileStatic

@Entity
class Order {
    String reference
}

@Entity
class Customer {
    String name
}

@Entity
class Product {
    String sku
}

@Service(Customer)
interface CustomerDataService {
    Customer get(Serializable id)
}

@Service(Product)
interface ProductDataService {
    Product get(Serializable id)
}

@CompileStatic
@Service(Order)
abstract class OrderService implements OrderDataService {
    CustomerDataService customerDataService
    ProductDataService productDataService

    Order createOrderForCustomer(Serializable customerId, Serializable productId) {
        Customer customer = customerDataService.get(customerId)
        Product product = productDataService.get(productId)
        Order order = new Order(reference: "${customer?.name}-${product?.sku}")
        return save(order)
    }
}

interface OrderDataService {
    Order get(Serializable id)
    Order save(Order order)
}
''')

        then: 'Compilation succeeds with multiple @Service-typed properties under @CompileStatic'
        noExceptionThrown()

        when: 'The impl is loaded'
        def impl = service.classLoader.loadClass('$OrderServiceImplementation')

        then: 'The impl has datastore infrastructure for service injection'
        impl != null
        impl.getDeclaredMethod('getDatastore').returnType == Datastore
        impl.getDeclaredMethod('setDatastore', Datastore) != null
    }

    void "test @CompileStatic with custom methods and return statements compiles"() {
        when: 'A @CompileStatic @Service abstract class has custom methods with complex return statements'
        def service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.Service
import grails.gorm.annotation.Entity
import groovy.transform.CompileStatic

@Entity
class Report {
    String title
    String status
}

@Entity
class Setting {
    String key
    String value
}

@Service(Setting)
interface SettingDataService {
    Setting get(Serializable id)
    List<Setting> list()
}

@CompileStatic
@Service(Report)
abstract class ReportService implements ReportDataService {
    SettingDataService settingDataService

    Map<String, Object> generateSummary(Serializable reportId) {
        Report report = get(reportId)
        List<Setting> settings = settingDataService.list()
        Map<String, Object> result = [:]
        result.put('report', report?.title ?: 'Unknown')
        result.put('settingCount', settings?.size() ?: 0)
        return result
    }

    boolean isReportValid(Serializable reportId) {
        Report report = get(reportId)
        if (report == null) {
            return false
        }
        return report.status == 'active'
    }
}

interface ReportDataService {
    Report get(Serializable id)
    Report save(Report report)
    List<Report> list()
}
''')

        then: 'Compilation succeeds — return statements in custom methods work under @CompileStatic'
        noExceptionThrown()

        when: 'The impl is loaded'
        def impl = service.classLoader.loadClass('$ReportServiceImplementation')

        then: 'The impl is valid'
        impl != null
        org.grails.datastore.mapping.services.Service.isAssignableFrom(impl)
    }

    void "test impl has datastore infrastructure when abstract class has @Service properties"() {
        when: 'A @Service abstract class with @Service-typed properties is compiled'
        def service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.Service
import grails.gorm.annotation.Entity

@Entity
class Record {
    String value
}

@Entity
class Tag {
    String label
}

@Service(Tag)
interface TagDataService {
    Tag get(Serializable id)
}

@Service(Record)
abstract class RecordService implements RecordDataService {
    TagDataService tagDataService
}

interface RecordDataService {
    Record get(Serializable id)
}
''')

        then: 'Compilation succeeds'
        noExceptionThrown()

        when: 'The impl class is inspected'
        def impl = service.classLoader.loadClass('$RecordServiceImplementation')

        then: 'The impl has datastore infrastructure for service injection'
        impl.getDeclaredMethod('setDatastore', Datastore) != null
        impl.getDeclaredMethod('getDatastore').returnType == Datastore
    }
}
