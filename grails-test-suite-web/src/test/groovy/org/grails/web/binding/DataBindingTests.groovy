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
package org.grails.web.binding

import grails.artefact.Artefact
import grails.databinding.BindingFormat
import grails.persistence.Entity
import grails.testing.gorm.DomainUnitTest
import grails.testing.web.controllers.ControllerUnitTest
import org.grails.plugins.testing.GrailsMockMultipartFile
import spock.lang.Specification

 /**
 * Tests Grails data binding capabilities.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class DataBindingTests extends Specification implements ControllerUnitTest<TestController>, DomainUnitTest<MyBean> {

    void testBindingPogoToDomainClass() {
        when:
        def author = new Author()
        def clown = new Clown(name: 'Bozo', hairColour: 'Orange')

        author.properties = clown

        then:
        !author.hasErrors()
        author.name == 'Bozo'
        author.hairColour == 'Orange'
    }

    void testDateFormatError() {
        when:
        def bean = new MyBean()

        request.addParameter 'formattedDate', 'BAD'

        bean.properties = request
        def dateError = bean.errors.getFieldError('formattedDate')


        then:
        bean.hasErrors()
        bean.errors.errorCount == 1

        dateError != null

        dateError.defaultMessage == 'Unparseable date: "BAD"'
    }

    void testBindingWithIndexedBlankId() {
        when:
        def city = new City()

        request.addParameter 'people[0].id', ''

        city.properties = request

        then:
        !city.hasErrors()
        city.people instanceof Set
        city.people.size() == 1
    }

    void testUpdatingSetElementByIdThatDoesNotExist() {
        when:
        def city = new City()

        request.addParameter 'people[0].id', '42'

        city.properties = request
        def error = city.errors.getFieldError('people')

        then:
        city.hasErrors()
        city.errors.errorCount == 1

        error.defaultMessage == 'Illegal attempt to update element in [people] Set with id [42]. No such record was found.'
    }


    void testBindingObjectsWithHashcodeAndEqualsToASet() {
        when:
        // GRAILS-9825 = this test fails with the spring binder
        // and passes with GrailsWebDataBinder

        def city = new City()

        request.addParameter 'people[0].name', 'Jeff'
        request.addParameter 'people[1].name', 'Jake'
        request.addParameter 'people[1].birthDate', '2000-08-26 21:26:31.973'
        request.addParameter 'people[2].name', 'Zack'

        city.properties = request

        then:
        city.people instanceof Set
        city.people.size() == 3
        city.people.find { it.name == 'Jeff' && it.birthDate == null} != null
        city.people.find { it.name == 'Jake' && it.birthDate != null} != null
        city.people.find { it.name == 'Zack' && it.birthDate == null} != null
    }

    void testBindingASinglePropertyWithSubscriptOperator() {
        when:
        def person = new DataBindingTestsPerson()

        person.properties['birthDate'] = '2013-04-15 21:26:31.973'
        def cal = Calendar.instance
        cal.time = person.birthDate

        then:
        person.birthDate instanceof Date
        Calendar.APRIL == cal.get(Calendar.MONTH)
        2013 == cal.get(Calendar.YEAR)
    }

    void testBindintToNestedArray() {
        when:
        def author = new AuthorCommand()
        request.addParameter 'beans[0].integers[0]', '42'

        author.properties = request

        then:
        author.beans.size() == 1
        author.beans[0].integers.length == 1
        author.beans[0].integers[0] == 42
    }

    void testFieldErrorObjectName() {
        when:
        def myBean = new MyBean()

        request.addParameter 'someIntProperty', 'bad integer'
        myBean.properties = request
        def errors = myBean.errors
        def fieldError = errors.getFieldError('someIntProperty')

        then:
        myBean.someIntProperty == null
        fieldError.rejectedValue == 'bad integer'
        fieldError.objectName == 'org.grails.web.binding.MyBean'
    }

    void testBindingMalformedNumber() {
        when:
        // GRAILS-6766
        def myBean = new MyBean()

        request.addParameter 'someFloatProperty', '21.12Rush'

        myBean.properties = request

        def errors = myBean.errors
        def fieldError = errors.getFieldError('someFloatProperty')

        then: 'these fail with GrailsDataBinder and pass with GrailsWebDataBinder'
        myBean.someFloatProperty == null
        fieldError.rejectedValue == '21.12Rush'
    }


    void testBinderDoesNotCreateExtraneousInstances() {
        when:
        // GRAILS-9914
        int originalCount = DataBindingTestsBook.instanceCount
        def book = new DataBindingTestsBook(title: 'Some Book')

        then:
        originalCount + 1 == DataBindingTestsBook.instanceCount

        when:
        def bookReview = new BookReview(book: book)

        then:
        'Some Book' == bookReview.book.title

        when:
        request.addParameter 'book.title', 'Some New Book'
        bookReview.properties = request

        then:
        'Some New Book' == bookReview.book.title

        // this fails with GrailsDataBinder and passes with GrailsWebDataBinder
        originalCount + 1 == DataBindingTestsBook.instanceCount
    }

    void testBindEmbeddedWithMultipartFileAndDate() {
        when:
        def e = new WithEncoding()
        request.addFile(new GrailsMockMultipartFile("eDate.aFile", "foo".bytes))
        request.addParameter("eDate.aDate", "struct")
        request.addParameter("eDate.aDate_year", "1980")
        request.addParameter("eDate.aDate_month", "02")
        request.addParameter("eDate.aDate_day", "03")

        e.properties = request

        then:
        e.eDate.aFile != null
        e.eDate.aDate != null

    }

    void testBindingMapValue() {
        when:
        def pet = new Pet()
        pet.properties = [name: 'lemur', detailMap: [first: 'one', second: 'two'], owner: [name: 'Jeff'], foo: 'bar', bar: [a: 'a', b: 'b']]

        then:
        pet.name == 'lemur'
        pet.detailMap.first == 'one'
        pet.detailMap.second == 'two'
        !pet.hasErrors()
    }

    void testBindingNullToANullableDateThatAlreadyHasAValue() {
        given:
        def person = new DataBindingTestsPerson()

        when:
        params.name = 'Douglas Adams'
        params.birthDate_year = '1952'
        params.birthDate_day = '11'
        params.birthDate_month = '3'
        params.birthDate = 'struct'

        person.properties = params

        then:
        'Douglas Adams' == person.name
        person.birthDate != null

        when:
        params.name = 'Douglas Adams'
        params.birthDate_year = ''
        params.birthDate_day = ''
        params.birthDate_month = ''
        params.birthDate = 'struct'

        person.properties = params

        then:
        'Douglas Adams' ==  person.name
        person.birthDate == null
    }

    void testNamedBinding() {
        when:
        def author = new Author()

        params.name = 'Douglas Adams'
        params.hairColour = 'Grey'

        author.properties['name'] = params

        then:
        'Douglas Adams' == author.name
        author.hairColour == null
    }

    void testNamedBindingWithMultipleProperties() {
        when:
        def author = new Author()

        params.name = 'Douglas Adams'
        params.hairColour = 'Grey'

        author.properties['name', 'hairColour'] = params

        then:
        'Douglas Adams' == author.name
        'Grey' == author.hairColour
    }

    void testThreeLevelDataBinding() {
        when:
        def b = new DataBindingTestsBook()

        params.title = "The Stand"
        params.author = [placeOfBirth: [name: 'Maine'], name: 'Stephen King']

        b.properties = params

        then:
        "The Stand" == b.title
        "Maine" == b.author.placeOfBirth.name
        "Stephen King" == b.author.name
    }

    void testConvertingBlankAndEmptyStringsToNull() {
        when:
        def a = new Author()

        params.name =  ''
        params.hairColour = '  '

        a.properties = params

        then:
        a.name == null
        a.hairColour == null
    }

    void testTypeConversionErrorsWithNestedAssociations() {
        when:
        request.addParameter("author.name", "Stephen King")
        request.addParameter("author.hairColour", "Black")

        def b = new DataBindingTestsBook()

        b.properties = params

        def a = b.author

        then:
        !a.hasErrors()
        !b.hasErrors()
    }

    void testTypeConversionErrors() {
        when:
        request.addParameter("site", "not_a_valid_URL")

        def b = new DataBindingTestsBook()

        b.properties = params

        then:
        b.hasErrors()

        //def error = b.errors.getFieldError('site')
    }

    void testValidationAfterBindingFails() {
        when:
        // binding should fail for this one
        request.addParameter("someIntProperty", "foo")

        // validation should fail for this one...
        request.addParameter("someOtherIntProperty", "999")

        // binding should fail for this one...
        request.addParameter("thirdIntProperty", "bar")

        request.addParameter("someFloatProperty", "21.12")

        def myBean = new MyBean()

        myBean.properties = params

        then:
        2 == myBean.errors.errorCount //"wrong number of errors before validation"
        !myBean.validate() //'validation should have failed'
        3 == myBean.errors.errorCount //'wrong number of errors after validation'
    }

    void testAssociationAutoCreation() {
        when:
        request.addParameter("title", "The Stand")
        request.addParameter("author.name", "Stephen King")
        def b = new DataBindingTestsBook()

        b.properties = params

        then:
        "The Stand" == params.title
        "The Stand" == b.title
        "Stephen King" == b.author?.name
    }

    void testNullAssociations() {
        when:
        request.addParameter("title", "The Stand")
        request.addParameter("author.id", "null")

        def b = new DataBindingTestsBook()

        b.properties = params

        then:
        "The Stand" == b.title
        b.author == null
    }

    void testAssociationsBinding() {
        when:
        def authorClass = new Author()

        Author.metaClass.static.get = { Serializable id ->
            def result = new Author()
            result.id = id as long
            result.name = "Mocked ${id}"
            result
        }

        request.addParameter("title", "The Stand")
        request.addParameter("author.id", "5")

        def b = new DataBindingTestsBook()

        b.properties = params

        then:
        "The Stand" == b.title
        b.author != null
        5 == b.author.id
        "Mocked 5" == b.author.name
    }

    void testMultiDBinding() {
        when:
        request.addParameter("author.name", "Stephen King")
        request.addParameter("author.hairColour", "Black")
        request.addParameter("title", "The Stand")

        def a = new Author()
        def b = new DataBindingTestsBook()
        b.properties = params
        a.properties = params['author']

        then:
        "Stephen King" == params['author'].name
        "Stephen King" == a.name
        "Black" == a.hairColour
        "The Stand" == b.title
        "Stephen King" == b.author?.name
    }
}

class Clown {
    String name
    String hairColour
}

@Artefact('Controller')
class TestController {
    def index = {}
}

@Entity
class DataBindingTestsBook {
    static instanceCount = 0
    DataBindingTestsBook() {
        instanceCount++
    }
    String title
    Author author
    URL site
}

@Entity
class BookReview {
    DataBindingTestsBook book
}

@Entity
class MyBean {
  @BindingFormat('MMddyyyy')
  Date formattedDate
  Integer someIntProperty
  Integer someOtherIntProperty
  Integer thirdIntProperty
  Float someFloatProperty
  static constraints = {
    someIntProperty(min:1, nullable:true)
    someOtherIntProperty(max:99)
    thirdIntProperty nullable:false
    formattedDate nullable: true
  }
}
@Entity
class Author {
    String name
    String hairColour
    City placeOfBirth

    static constraints = {
        name(nullable:true)
    }
}
@Entity
class City {
    String name
    static hasMany = [people: DataBindingTestsPerson]
}
@Entity
class DataBindingTestsPerson {
    String name
    Date birthDate
    static constraints = {
        birthDate nullable: true
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((birthDate == null) ? 0 : birthDate.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        return result;
    }
    @Override
    public boolean equals(Object obj) {
        if (this.is(obj))
            return true;
        if (obj == null)
            return false;
        if (!getClass().is(obj.getClass()))
            return false
        DataBindingTestsPerson other = (DataBindingTestsPerson) obj;
        if (birthDate == null) {
            if (other.birthDate != null)
                return false;
        } else if (birthDate != other.birthDate)
            return false;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (name != other.name)
            return false;
        return true;
    }
}
@Entity
class Pet {
    String name
    Map detailMap
    DataBindingTestsPerson owner
}

@Entity
class WithEncoding {

    EmbedDate eDate

    static constraints = {
    }

    static embedded = ['eDate']
}
class EmbedDate {

    Date aDate
    byte[] aFile

    static constraints = {
    }
}
@Entity
class AuthorCommand {
    List beans = []
    public AuthorCommand() {
        beans << new AuthorBean()
    }
}
@Entity
class AuthorBean {
    Integer[] integers
}
