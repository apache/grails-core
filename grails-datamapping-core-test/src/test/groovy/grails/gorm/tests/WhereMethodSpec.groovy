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
package grails.gorm.tests

import grails.gorm.DetachedCriteria
import org.apache.grails.data.testing.tck.domains.Country
import org.apache.grails.data.testing.tck.domains.Face
import org.apache.grails.data.testing.tck.domains.Nose
import org.apache.grails.data.testing.tck.domains.Pet
import grails.persistence.Entity
import groovy.transform.CompileStatic
import org.apache.grails.data.testing.tck.domains.Person
import org.apache.grails.data.simple.core.GrailsDataCoreTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform
import org.grails.datastore.mapping.reflect.FieldEntityAccess
import spock.lang.Issue
import spock.lang.Shared

/**
 * Tests for the new where method used to define detached criteria using the new DSL
 */
@ApplyDetachedCriteriaTransform
class WhereMethodSpec extends GrailsDataTckSpec<GrailsDataCoreTckManager> {
    @Shared
    def gcl

    void setupSpec() {
        List<Class> list = [Continent, Group, Proposal, Advisor]

        gcl = new GroovyClassLoader()
        list << gcl.parseClass('''
import org.apache.grails.data.testing.tck.domains.*
import grails.gorm.annotation.*
import grails.persistence.*
import grails.gorm.DetachedCriteria
@Entity
class Todo {
    Long id
    String title
    static DetachedCriteria<Todo> titleStartsWith(String str) {
        where {
            title ==~ "$str%"
        }
    }      
    
    static doStuff() {
        where { title == 'blah' }
    }
    
}
Todo.titleStartsWith("test").where {
   title == 'A%'
}
return Todo
''')
        // ensure the class can be instantiated
        list[-1].newInstance()
        list << gcl.parseClass('''
import org.apache.grails.data.testing.tck.domains.*
import grails.gorm.annotation.*
import grails.persistence.*
import grails.gorm.DetachedCriteria

@Entity
class Project {
    Long id
    String name
    static hasMany =[todos:Todo]

    static todosThatStartWithA = where {
        todos.title ==~ "A%"
    }
}
''')
        manager.addAllDomainClasses(list)
    }

    def setup() {
        FieldEntityAccess.clearReflectors()
    }

//   TODO: Fix RHS function calls
//    @Ignore
//    def "Test year function with to-one association"() {
//        given:"people and pets"
//            createPeopleWithPets()
//
//        when:"We use a function with an association query"
//            def results = Pet.where {
//                owner.age < year(birthDate)
//            }.list()
//
//        then:"The correct results are returned"
//            results.size() > 0
//    }

    def closureProperty = {
        Person.where { lastName == "Simpson" }.list()
    }

    def "test transform method that returns where query"() {
        given: "A bunch of people"
        createPeople()

        when: "A method is called that returns where"
        List<Person> people = Person.withAge(10).where {
            lastName == 'Simpson'
        }.list()

        then: "The results are correct"
        people.size() == 2
    }

    @Issue('https://github.com/grails/grails-data-mapping/issues/982')
    def "test transform method that operates on returned query"() {
        given: "A bunch of people"
        createPeople()


        when: "A method is called that returns where"
        DetachedCriteria<Person> query = Person.withAge(10)
        query = query.where {
            lastName == 'Simpson'
        }
        def people = query.list()

        then: "The results are correct"
        people.size() == 2
    }

    @Issue('GRAILS-9425')
    def "Test that static definition of where query works with associations"() {
        given: "given a project and some todos"
        FieldEntityAccess.clearReflectors()
        def Todo = this.gcl.loadClass("Todo")
        def Project = this.gcl.loadClass("Project")

        Project.newInstance(name: "Foo").save()
        Project.newInstance(name: "Bar")
                .addToTodos(title: "A todo")
                .addToTodos(title: "Another")
                .save(flush: true)

        manager.session.clear()

        when: "a statically defined where query is used"
        def results = Project.todosThatStartWithA.list()

        then: "The correct results are returned"
        results.size() == 1
        results.find { it.name == 'Bar' }
    }

    @Issue('GRAILS-9696')
    def "Test that a call to a static find method from within the class resolves correctly"() {
        given: "Some people"
        createPeople()

        when: "A method is called that calls the find method from within the class"
        def bart = Person.getByFirstNameAndLastNameAndAge("Bart", "Simpson", 9)

        then: "The data returned is correct"
        bart != null
        bart.firstName == "Bart"
    }

    @Issue('GRAILS-8256')
    def "Test query with 3 level deep domain association"() {
        given: "create people and faces"
        createPeopleWithFaces()

        when: "A query that uses criteria 2 levels deep is executed"
        def results = Nose.withCriteria {
            face { person { eq 'lastName', 'Simpson' } }
        }

        then: "The results are correct"
        results.size() == 4

        when: "A query that queries an association 2 levels deep is executed"
        def query = Nose.where {
            face.person.lastName == "Simpson"
        }

        then: "The correct results are returned"
        query.count() == 4

        when: "A query that queries an association 2 levels deep is executed via nesting"
        query = Nose.where {
            face { person { lastName == "Simpson" } }
        }

        then: "The correct results are returned"
        query.count() == 4
    }

    private createPeopleWithFaces() {
        final h = new Person(firstName: "Homer", lastName: "Simpson", age: 45)
        h.face = new Face(name: "Homer", nose: new Nose(), person: h)
        h.save()
        final m = new Person(firstName: "Marge", lastName: "Simpson", age: 40)
        m.face = new Face(name: "Marge", nose: new Nose(), person: m)
        m.save()

        final b = new Person(firstName: "Bart", lastName: "Simpson", age: 9)
        b.face = new Face(name: "Bart", nose: new Nose(hasFreckles: true), person: b)
        b.save()

        final l = new Person(firstName: "Lisa", lastName: "Simpson", age: 7)
        l.face = new Face(name: "Lisa", nose: new Nose(hasFreckles: true), person: l)
        l.save()

        final ba = new Person(firstName: "Barney", lastName: "Rubble", age: 35)
        ba.face = new Face(name: "Barney", nose: new Nose())
        ba.save()

        assert Person.count() == 5
        assert Face.count() == 5

    }

    @Issue('GRAILS-8256')
    def "Test query with 3 level deep collection association"() {
        given: "some people with pets in groups"
        createPeopleInGroupsWithPets()

        when: "A query that uses criteria 2 levels deep is executed"
        def results = Group.withCriteria {
            people { pets { eq 'name', 'Jack' } }
        }

        then: "The results are correct"
        results.size() == 1
        when: "A query that queries an association 2 levels deep is executed"
        def query = Group.where {
            people.pets.name == "Jack"
        }

        then: "The correct results are returned"
        query.count() == 1

        when: "A query that queries an association 2 levels deep is executed via nesting"
        query = Group.where {
            people { pets { name == "Jack" } }
        }

        then: "The correct results are returned"
        query.count() == 1
    }

    @Issue('GRAILS-8366')
    def "Test calling method on RHS of collection size() query"() {
        given: "some people and pets"
        createPeopleWithPets()

        when: "A query that inspects the size() of a collection and calls a method on the RHS is called"
        def query = Person.where {
            pets.size() == processPetSize(3)
        }

        then: "The query returns the correct results"
        query.count() == 1
    }

    private processPetSize(int size) { size }

    def "Test error when using incorrect property of a to-one association"() {
        when: "A an unknown domain class property is referenced"
        queryUsingUnknownToOneAssociationProperty()
        then:
        MultipleCompilationErrorsException e = thrown()
        e.message.contains 'Cannot query property "firstN" - no such property on class org.apache.grails.data.testing.tck.domains.Person exists.'
    }

    @Issue('GRAILS-8526')
    def "Test association query with referenced arguments"() {
        given: "some people and pets"
        createPeopleWithPets()

        when: "A query is built from arguments to a method"
        def q = getSomePets(name: "Ed")
        def results = q.list()

        then: "The results are correct"
        results.size() == 3
    }

    def "Test a static method that calls where"() {
        when: "a static method call, calls into where"
        def Todo = this.gcl.loadClass("Todo")
        Todo.newInstance(title: "blah").save()
        Todo.newInstance(title: "two").save(flush: true)
        def query = Todo.doStuff()

        then: "The query is valid"
        query != null
        Todo.count() == 2
        query.count() == 1
        query.find().title == 'blah'
    }

    private getSomePets(args) {
        Pet.where {
            owner.firstName == args.name
        }
    }

    @Issue('GRAILS-9471')
    def "Test chaining where queries directly"() {
        given: "Some people"
        createPeople()

        when: "2 where queries are combined in a sequence"
        def results = Person.where { lastName == 'Simpson' }.where { firstName == 'Bart' }.list()

        then: "The correct results are returned"
        results.size() == 1
        results[0].firstName == 'Bart'
    }

    @Issue('GRAILS-9447')
    def "Test where query integer type conversion"() {
        given: "some people"
        createPeopleWithPets()

        when: "A where query is used with an integer value and a long property type"
        def results = Pet.where { owner.id == 2 }.list()

        then: "The correct results are returned and type conversion happens as expected"
        results.size() == 3
        results[0].id == 3
    }

    def "Test where query inside closure property declaration"() {
        given: "some people"
        createPeople()

        when: "A query is created in a closure property"
        def results = closureProperty.call()

        then: "The correct results are returned"
        results.size() == 4
        results.every { it.lastName == "Simpson" }
    }

    def "Test captured detached criteria instance"() {
        given: "people and pets"
        createPeopleWithPets()

        when: "Another detached criteria variable is captured"
        def pets = Pet.where {
            name ==~ "J%"
        }

        def owners = Person.where {
            pets.size() == 2
        }

        then: "The results are valid"
        owners.count() == 2
    }

    def "Test whereAny method"() {
        given: "some people"
        createPeople()

        when: "An or is used in a where query"
        def people = Person.whereAny {
            firstName == "Homer"
            firstName == "Bart"
        }.list(sort: "firstName")

        then: "The right results are returned"
        people.size() == 2
        people[0].firstName == "Bart"
        people[1].firstName == "Homer"
    }

    def "Test where query that uses a captured variable inside an association query"() {
        given: "people and pets"
        createPeopleWithPets()

        when: "A where query that queries an association from a captured variable is used"
        def fn = "Joe"
        def pets = Pet.where {
            owner { firstName == fn }
        }.list()

        then: "The correct result is returned"
        pets.size() == 2
    }

    def "Test where with multiple property projections using chaining"() {
        given: "A bunch of people"
        createPeople()

        when: "Multiple property projections are used"
        def people = Person.where { lastName == "Simpson" }
        def results = people.property("lastName").property('firstName').list()

        then: "The correct results are returned"
        results == [["Simpson", "Homer"], ["Simpson", "Marge"], ["Simpson", "Bart"], ["Simpson", "Lisa"]]
    }

    def "Test where with multiple property projections"() {
        given: "A bunch of people"
        createPeople()

        when: "Multiple property projections are used"
        def people = Person.where { lastName == "Simpson" }
        def results = people.projections {
            property "lastName"
            property "firstName"
        }.list()

        then: "The correct results are returned"
        results == [["Simpson", "Homer"], ["Simpson", "Marge"], ["Simpson", "Bart"], ["Simpson", "Lisa"]]
    }

    def "Test parameterized where query"() {
        given: "A bunch of people"
        createPeople()

        when: "parameters are used instead of literals"
        def fn = "Bart"
        def ln = "Simpson"

        def query = Person.where { firstName != fn && lastName == ln }.sort("firstName", "desc")
        def people = query.list()

        then: "The correct results are returned"
        people.size() == 3
    }

    def "Test is null query"() {
        given: "A bunch of people"
        createPeople()

        when: "We query for a property to be null"
        def query = Person.where {
            firstName == null
        }

        then: "the  right results are returned"
        query.count() == 0
    }

    def "Test subquery defined as closure"() {
        given: "A bunch of people"
        createPeople()

        when: "use a subquery with additional criterion "
        def query = Person.where {
            age > avg(age).of { lastName == "Simpson" } && firstName == "Homer"
        }

        def results = query.list(sort: "firstName")

        then: "The expected result is returned"
        results.size() == 1
        results[0].firstName == "Homer"
    }

    def "Test function execution"() {
        given: "A bunch of people with pets"
        createPeopleWithPets()
        def p = new Person(firstName: "Old", lastName: "Person").save()
        new Pet(owner: p, birthDate: new GregorianCalendar(2009, 1, 1).time, name: "Old Dog").save()

        when: "A function is used on the property"
        def query = Pet.where {
            year(birthDate) == 2011
        }
        def results = query.list()

        then: "check that the results are correct"
        results.size() == 7

        when: "A function is used on the property"
        query = Pet.where {
            year(birthDate) == 2009
        }
        results = query.list()

        then: "check that the results are correct"
        results.size() == 1
        results[0].name == "Old Dog"

        when: "A function is used on an association"
        query = Person.where {
            year(pets.birthDate) == 2009
        }
        results = query.list()

        then: "The correct results are returned"
        results.size() == 1
        results[0].firstName == "Old"
    }

    def "Test in range query"() {
        given: "A bunch of people"
        createPeople()

        when: "a query is composed"
        def query = Person.where {
            age in 1..15
        }

        def results = query.list(sort: "firstName")

        then: "The expected result is returned"
        results.size() == 2
        results[0].firstName == "Bart"
        results[1].firstName == "Lisa"
    }

    def "Test compose query"() {
        given: "A bunch of people"
        createPeople()

        when: "a query is composed"
        def query = Person.where {
            lastName == "Simpson"
        }
        def bartQuery = query.where {
            firstName == "Bart"
        }
        Person p = bartQuery.find()

        then: "The expected result is returned"
        p != null
        p.firstName == "Bart"
    }

    def "Test static scoped where calls"() {
        given: "A bunch of people"
        createPeople()

        when: "We use the static simpsons property "
        def simpsons = Person.simpsons

        then: "We get the right results back"
        simpsons.count() == 4

        when: "We apply further where criteria to static scoped where call"
        def query = Person.simpsons.where {
            firstName == "Bart"
        }
        Person p = query.find()

        then: "The correct results are returned"
        p != null
        p.firstName == "Bart"
    }

    def "Test findAll with pagination params"() {
        given: "A bunch of people"
        createPeople()

        when: "We use findAll with pagination params"
        def results = Person.findAll(sort: "firstName") {
            lastName == "Simpson"
        }

        then: "The correct results are returned"
        results != null
        results.size() == 4
        results[0].firstName == "Bart"
    }

    @Issue('GRAILS-9658')
    def "Test findAll with assocation query"() {

        when: "We use findAll is called with an association"
        def callMeInstance = getClassThatCallsWhere()
        def results = callMeInstance.doQuery()
//            def session = [user: [username:"Fred"]]
//            def results = Proposal.findAll { advisor.username == manager.session.user.username && approvalDate != null }

        then: "The correct results are returned"
        results != null
    }

    def "Test try catch finally"() {
        given: "A bunch of people"
        createPeople()

        when: "We use a try catch finally block in a where query"
        def query = Person.where {
            def personAge = "nine"
            try {
                age == personAge.toInteger()
            }
            catch (e) {
                age == 7
            }
            finally {
                lastName == "Simpson"
            }
        }
        Person result = query.find()

        then: "The correct results are returned"
        result != null
        result.firstName == "Lisa"
    }

    def "Test while loop"() {
        given: "A bunch of people"
        createPeople()

        when: "We use a while loop in a where query"
        def query = Person.where {
            def list = ["Bart", "Simpson"]
            int total = 0
            while (total < list.size()) {
                def name = list[total++]
                if (name == "Bart")
                    firstName == name
                else
                    lastName == "Simpson"
            }
        }
        Person result = query.find()

        then: "The correct results are returned"
        result != null
        result.firstName == "Bart"
    }

    def "Test for loop"() {
        given: "A bunch of people"
        createPeople()

        when: "We use a for loop in a query"
        def query = Person.where {
            for (name in ["Bart", "Simpson"]) {
                if (name == "Bart")
                    firstName == name
                else
                    lastName == "Simpson"
            }
        }
        Person result = query.find()

        then: "The correct results are returned"
        result != null
        result.firstName == "Bart"
    }

    def "Test criteria on single ended association"() {
        given: "people and pets"
        createPeopleWithPets()

        when: "We query the single-ended association owner of pet"
        def query = Pet.where {
            owner.firstName == "Joe" || owner.firstName == "Fred"
        }

        then: "the correct results are returned"
        query.count() == 4
    }

//    def "Test comparing property with single ended association"() {
//        given:"people and pets"
//            createPeopleWithPets()
//
//        when:"We query a property against the property of a single-ended association"
//            def query = Pet.where {
//                name == owner.firstName
//            }
//
//        then:"the correct results are returned"
//            query.count() == 0
//    }

    def "Test ilike operator"() {
        given: "A bunch of people"
        createPeople()

        when: "We query for people whose first names start with the letter B in lower case"
        def query = Person.where {
            firstName =~ "b%"
        }
        def results = query.list(sort: 'firstName')

        then: "The correct results are returned"
        results.size() == 2
        results[0].firstName == "Barney"
        results[1].firstName == "Bart"

    }

    def "Test switch statement"() {
        given: "A bunch of people"
        createPeople()

        when: "A where query is used with a switch statement"
        int count = 2
        def query = Person.where {
            switch (count) {
                case 1:
                    firstName == "Bart"
                    break
                case 2:
                    firstName == "Lisa"
                    break
                case 3:
                    firstName == "Marge"

            }
        }
        def result = query.find()

        then: "The correct result is returned"
        result != null
        result.firstName == "Lisa"
    }

    def "Test where blocks on detached criteria"() {
        given: "A bunch of people"
        createPeople()

        when: "A where block is used on a detached criteria instance"
        DetachedCriteria dc = new DetachedCriteria(Person)
        dc = dc.where {
            firstName == "Bart"
        }
        def result = dc.find()

        then: "The correct results are returned"
        result != null
        result.firstName == "Bart"
    }

    def "Test local declaration inside where method"() {
        given: "A bunch of people"
        createPeople()

        when: "A where query is used with if statement"

        def query = Person.where {
            def useBart = true
            firstName == (useBart ? "Bart" : "Homer")
        }
        def result = query.find()

        then: "The correct result is returned"

        result != null
        result.firstName == "Bart"
    }

    def "Test where method with ternary operator"() {
        given: "A bunch of people"
        createPeople()

        when: "A where query is used with if statement"
        def useBart = true
        def query = Person.where {
            firstName == (useBart ? "Bart" : "Homer")
        }
        def result = query.find()

        then: "The correct result is returned"

        result != null
        result.firstName == "Bart"
    }

    def "Test where method with if else block"() {
        given: "A bunch of people"
        createPeople()

        when: "A where query is used with if statement"
        def useBart = true
        def query = Person.where {
            if (useBart)
                firstName == "Bart"
            else
                firstName == "Homer"
        }
        def result = query.find()

        then: "The correct result is returned"

        result != null
        result.firstName == "Bart"

        when: "A where query is used with else statement"
        useBart = false
        query = Person.where {
            if (useBart)
                firstName == "Bart"
            else
                firstName == "Marge"
        }
        result = query.find()

        then: "The correct result is returned"

        result != null
        result.firstName == "Marge"

        when: "A where query is used with else statement"
        useBart = false
        int count = 1
        query = Person.where {
            if (useBart)
                firstName == "Bart"
            else if (count == 1) {
                firstName == "Lisa"
            } else
                firstName == "Marge"
        }
        result = query.find()

        then: "The correct result is returned"

        result != null
        result.firstName == "Lisa"
    }

    def "Test collection operations"() {
        given: "People with pets"
        createPeopleWithPets()

        when: "We query for people with 2 pets"
        def query = Person.where {
            pets.size() == 2
        }
        def results = query.list(sort: "firstName")

        then: "The correct results are returned"
        results.size() == 2
        results[0].firstName == "Fred"
        results[1].firstName == "Joe"

        when: "We query for people with greater than 2 pets"
        query = Person.where {
            pets.size() > 2
        }
        results = query.list(sort: "firstName")
        then: "The correct results are returned"
        results.size() == 1
        results[0].firstName == "Ed"

        when: "We query for people with greater than 2 pets"
        query = Person.where {
            pets.size() > 1 && firstName != "Joe"
        }
        results = query.list(sort: "firstName")
        then: "The correct results are returned"
        results.size() == 2
        results[0].firstName == "Ed"
        results[1].firstName == "Fred"
    }

    def "Test subquery usage combined with property"() {
        given: "a bunch of people"
        createPeople()

        when: "We query for people greater that all defined ages"
        final query = Person.where {
            age > property(age)
        }
        def results = query.list(sort: "firstName")

        then: "The correct results are returned"
        results.size() == 0
    }

    def "Test subquery usage combined with logical query"() {
        given: "a bunch of people"
        createPeople()

        when: "We query for people greater than an average age"
        final query = Person.where {
            age > avg(age) && firstName != "Marge"
        }
        def results = query.list(sort: "firstName")

        then: "The correct results are returned"
        results.size() == 3
        results[0].firstName == "Barney"
        results[1].firstName == "Fred"
        results[2].firstName == "Homer"
    }

    def "Test subquery usage"() {
        given: "a bunch of people"
        createPeople()

        when: "We query for people greater than an average age"
        final query = Person.where {
            age > avg(age)
        }
        def results = query.list(sort: "firstName")

        then: "The correct results are returned"
        results.size() == 4
        results[0].firstName == "Barney"
        results[1].firstName == "Fred"
        results[2].firstName == "Homer"
        results[3].firstName == "Marge"
    }

    def "Test error when using negating a non-binary expression"() {
        when: "A an unknown domain class property is referenced"
        queryUsingInvalidNegation()
        then:
        MultipleCompilationErrorsException e = thrown()
        e.message.contains 'You can only negate a binary expressions in queries.'
    }

    def "Test error when using unsupported operator in size() query"() {
        when: "A an unknown domain class property is referenced"
        queryUsingUnsupportedOperatorInSize()
        then:
        MultipleCompilationErrorsException e = thrown()
        e.message.contains 'Unsupported operator [<<] used in size() query'
    }

    def "Test error when using unknown property in size() query"() {
        when: "A an unknown domain class property is referenced"
        queryUsingUnknownPropertyWithSize()
        then:
        MultipleCompilationErrorsException e = thrown()
        e.message.contains 'Cannot query size of property "blah" - no such property on class org.apache.grails.data.testing.tck.domains.Person exists'
    }

    def "Test error when using unsupported operator"() {
        when: "A an unsupported query operator is used"
        queryUsingUnsupportedOperator()
        then:
        MultipleCompilationErrorsException e = thrown()
        e.message.contains 'Unsupported operator [<<] used in query'
    }

    def "Test error when using unknown domain property of an association"() {
        when: "A an unknown domain class property of an association is referenced"
        queryReferencingNonExistentPropertyOfAssociation()
        then:
        MultipleCompilationErrorsException e = thrown()
        e.message.contains 'Cannot query on property "doesntExist" - no such property on class org.apache.grails.data.testing.tck.domains.Pet exists.'
    }

    def "Test error when using unknown domain property"() {
        when: "A an unknown domain class property is referenced"
        queryReferencingNonExistentProperty()
        then:
        MultipleCompilationErrorsException e = thrown()
        e.message.contains 'Cannot query on property "doesntExist" - no such property on class org.apache.grails.data.testing.tck.domains.Person exists.'
    }

    def "Test error when using function on property of a to-one association"() {
        when: "A function is used on a property expression"
        queryUsingFunctionOnToOneAssociationProperty()
        then:
        MultipleCompilationErrorsException e = thrown()
        e.message.contains 'Cannot use aggregate function avg on expressions "owner.age"'
    }

    String nameBart() { "Bart" }

    def "Test where method with value obtained via method call"() {
        given: "A bunch of people"
        createPeople()

        when: "We find a person where first name is obtained via method call"
        Person p = Person.find { firstName == nameBart() }

        then: "The expected result is returned"
        p != null
        p.firstName == "Bart"
    }

    def "Test second where declaration on detached criteria instance"() {
        given: "A bunch of people"
        createPeople()

        when: "We create a 2 where queries, one derived from the other"
        def q1 = Person.where {
            lastName == "Simpson"
        }

        def q2 = q1.where {
            firstName == "Bart"
        }

        then: "The first query is not modified, and the second works as expected"
        q1.count() == 4
        q2.count() == 1
    }

    def "Test query association"() {
        given: "People with a few pets"
        createPeopleWithPets()

        when: "We query for people by Pet using a simple equals query"

        def query = Person.where {
            pets.name == "Butch"
        }
        def count = query.count()
        def result = query.find()

        then: "The expected result is returned"
        count == 1
        result != null
        result.firstName == "Joe"

        when: "We query for people by Pet with multiple results"
        query = Person.where {
            pets.name ==~ "B%"
        }
        count = query.count()
        def results = query.list(sort: "firstName")

        then: "The expected results are returned"
        count == 2
        results[0].firstName == "Ed"
        results[1].firstName == "Joe"
    }

    def "Test query association with or"() {
        given: "People with a few pets"
        createPeopleWithPets()

        when: "We use a logical or to query people by pets"
        def query = Person.where {
            pets { name == "Jack" || name == "Joe" }
        }
        def count = query.count()
        def results = query.list(sort: "firstName")

        then: "The expected results are returned"
        count == 2
        results[0].firstName == "Fred"
        results[1].firstName == "Joe"

        when: "We use a logical or to query pets combined with another top-level logical expression"
        query = Person.where {
            pets { name == "Jack" } || firstName == "Ed"
        }
        count = query.count()
        results = query.list(sort: "firstName")

        then: "The correct results are returned"
        count == 2
        results[0].firstName == "Ed"
        results[1].firstName == "Joe"
    }

    def "Test findAll method for inline query"() {
        given: "A bunch of people"
        createPeople()

        when: "We find a person where first name is Bart"
        List people = Person.findAll { lastName == "Simpson" }

        then: "The expected result is returned"
        people.size() == 4
    }

    def "Test find method for inline query"() {
        given: "A bunch of people"
        createPeople()

        when: "We find a person where first name is Bart"
        Person p = Person.find { firstName == "Bart" }

        then: "The expected result is returned"
        p != null
        p.firstName == "Bart"
    }

    def "Test use property declared as detached criteria"() {
        given: "A bunch of people"
        createPeople()

        when: "A closure is declared as detached criteria and then passed to where"

        def query = getClassThatCallsWhere().declaredQuery()
        Person p = query.find()

        then: "The right result is returned"
        p != null
        p.firstName == "Bart"
    }

    def "Test declare closure as detached criteria"() {
        given: "A bunch of people"
        createPeople()

        when: "A closure is declared as detached criteria and then passed to where"
        def callable = { firstName == "Bart" } as DetachedCriteria<Person>
        def query = Person.where(callable)
        Person p = query.find()

        then: "The right result is returned"
        p != null
        p.firstName == "Bart"

    }

    def "Test query using captured variables"() {
        given: "A bunch of people"
        createPeople()

        when: "We query with variable captured from outside the closures scope"
        def params = [firstName: "Bart"]
        def query = Person.where {
            firstName == params.firstName
        }
        def count = query.count()
        Person p = query.find()

        then: "The correct results are returned"
        count == 1
        p != null
        p.firstName == "Bart"
    }

    def "Test negation query"() {
        given: "A bunch of people"
        createPeople()

        when: "A single criterion is negated"
        def query = Person.where {
            !(lastName == "Simpson")
        }
        def results = query.list(sort: "firstName")

        then: "The right results are returned"
        results.size() == 2

        when: "Multiple criterion are negated"

        query = Person.where {
            !(firstName == "Fred" || firstName == "Barney")
        }
        results = query.list(sort: "firstName")

        then: "The right results are returned"
        results.every { it.lastName == "Simpson" }

        when: "Negation is combined with non-negation"

        query = Person.where {
            firstName == "Fred" && !(lastName == 'Simpson')
        }
        Person result = query.find()

        then: "The correct results are returned"
        result != null
        result.firstName == "Fred"

        when: "Negation is combined with non-negation"

        query = Person.where {
            !(firstName == "Homer") && lastName == 'Simpson'
        }
        results = query.list(sort: "firstName")

        then: "The correct results are returned"
        results.size() == 3
        results[0].firstName == "Bart"
        results[1].firstName == "Lisa"
        results[2].firstName == "Marge"
    }

    def "Test query association with logical or"() {
        given: "People with a few pets"
        createPeopleWithPets()

        when: "We use a logical or to query people by pets"
        def query = Person.where {
            pets.name == "Jack" || pets.name == "Joe"
        }

        def count = query.count()
        def results = query.list(sort: "firstName")

        then: "The expected results are returned"
        count == 2
        results[0].firstName == "Fred"
        results[1].firstName == "Joe"
    }

    def "Test eqProperty query"() {
        given: "A bunch of people"
        createPeople()
        new Person(firstName: "Frank", lastName: "Frank").save()

        when: "We query for a person with the same first name and last name"
        def query = Person.where {
            firstName == lastName
        }
        Person result = query.get()
        int count = query.count()

        then: "The correct result is returned"
        result != null
        count == 1
        result.firstName == "Frank"

    }

    def "Test rlike query"() {
        given: "A bunch of people"
        createPeople()

        when: "We query for people whose first names start with the letter B"
        def query = Person.where {
            firstName ==~ ~/B.+/
        }
        def results = query.list(sort: 'firstName')

        then: "The correct results are returned"
        results.size() == 2
        results[0].firstName == "Barney"
        results[1].firstName == "Bart"
    }

    def "Test like query"() {
        given: "A bunch of people"
        createPeople()

        when: "We query for people whose first names start with the letter B"
        def query = Person.where {
            firstName ==~ "B%"
        }
        def results = query.list(sort: 'firstName')

        then: "The correct results are returned"
        results.size() == 2
        results[0].firstName == "Barney"
        results[1].firstName == "Bart"
    }

    def "Test in list query"() {
        given: "A bunch of people"
        createPeople()

        when: "We query for people in a list"
        def query = Person.where {
            firstName in ["Bart", "Homer"]
        }
        def results = query.list(sort: 'firstName')

        then: "The correct results are returned"
        results.size() == 2
        results[0].firstName == "Bart"
        results[1].firstName == "Homer"
    }

    def "Test less than or equal to query"() {
        given: "A bunch of people"
        createPeople()

        when: "We query for people older than 30"
        def query = Person.where {
            age <= 35
        }
        def results = query.list(sort: 'firstName')

        then: "The correct results are returned"
        results.size() == 3
        results[0].firstName == "Barney"
        results[1].firstName == "Bart"
        results[2].firstName == "Lisa"

        when: "A greater than query is combined with an equals query"
        query = Person.where {
            age <= 35 && lastName == 'Simpson'
        }
        results = query.list(sort: 'firstName')

        then: "The correct results are returned"

        results.size() == 2
        results[0].firstName == "Bart"
        results[1].firstName == "Lisa"
    }

    def "Test greater than or equal to query"() {
        given: "A bunch of people"
        createPeople()

        when: "We query for people older than 30"
        def query = Person.where {
            age >= 35
        }
        def results = query.list(sort: 'firstName')

        then: "The correct results are returned"
        results.size() == 4
        results[0].firstName == "Barney"
        results[1].firstName == "Fred"
        results[2].firstName == "Homer"
        results[3].firstName == "Marge"

        when: "A greater than query is combined with an equals query"
        query = Person.where {
            age >= 35 && lastName == 'Simpson'
        }
        results = query.list(sort: 'firstName')

        then: "The correct results are returned"

        results.size() == 2
        results[0].firstName == "Homer"
        results[1].firstName == "Marge"
    }

    def "Test less than query"() {
        given: "A bunch of people"
        createPeople()

        when: "We query for people younger than 30"
        def query = Person.where {
            age < 30
        }
        def results = query.list(sort: 'firstName')

        then: "The correct results are returned"
        results.size() == 2
        results[0].firstName == "Bart"
        results[1].firstName == "Lisa"

        when: "A greater than query is combined with an equals query"
        query = Person.where {
            age < 30 && firstName == 'Bart'
        }
        results = query.list(sort: 'firstName')

        then: "The correct results are returned"

        results.size() == 1
        results[0].firstName == "Bart"
    }

    def "Test greater than query"() {
        given: "A bunch of people"
        createPeople()

        when: "We query for people older than 30"
        def query = Person.where {
            age > 35
        }
        def results = query.list(sort: 'firstName')

        then: "The correct results are returned"
        results.size() == 3
        results[0].firstName == "Fred"
        results[1].firstName == "Homer"
        results[2].firstName == "Marge"

        when: "A greater than query is combined with an equals query"
        query = Person.where {
            age > 35 && lastName == 'Simpson'
        }
        results = query.list(sort: 'firstName')

        then: "The correct results are returned"

        results.size() == 2
        results[0].firstName == "Homer"
        results[1].firstName == "Marge"
    }

    def "Test nested and or query"() {
        given: "A bunch of people"
        createPeople()

        when: "A where query is used"
        def query = Person.where {
            (lastName != "Simpson" && firstName != "Fred") || firstName == "Bart"
        }
        def results = query.list(sort: "firstName")

        then: "The correct result is returned"
        results.size() == 2
        results[0].firstName == "Barney"
        results[1].firstName == "Bart"
    }

    def "Test not equal query"() {
        given: "A bunch of people"
        createPeople()

        when: "A where query is used"
        def query = Person.where {
            lastName != "Simpson"
        }
        def results = query.list(sort: "firstName")

        then: "The correct result is returned"
        results.size() == 2
        results[0].firstName == "Barney"
        results[1].firstName == "Fred"
    }

    def "Test basic binary criterion where call"() {
        given: "A bunch of people"
        createPeople()

        when: "A where query is used"
        def query = Person.where {
            firstName == "Bart" && lastName == "Simpson"
        }
        def result = query.get()

        then: "The correct result is returned"
        result != null
        result.firstName == "Bart"
    }

    def "Test basic single criterion where call"() {
        given: "A bunch of people"
        createPeople()

        when: "A where query is used"
        def query = Person.where {
            firstName == "Bart"
        }
        def result = query.get()

        then: "The correct result is returned"

        result != null
        result.firstName == "Bart"

    }

    def "Test query association on inherited property"() {
        given: "People in countries"
        createContinentWithCountries()

        when: "A where query is used"
        def query = Continent.where {
            countries { name == 'SA' }
        }

        then: "The correct resulted are returned"
        query.count() == 1

    }

    def "Test where query with overlapping parameter and property names with association query"() {
        given: "some people"
        createPeopleWithPets()

        when: "A variable is declared that overlaps with a property of the class"
        def age = 10
        def pets = []
        def query = Person.where {
            pets.age == age
        }
        def results = query.list()

        then: "The variable takes precedence"
        query.count() == 2
        results.find { it.firstName == 'Joe' }
        results.find { it.firstName == 'Ed' }
    }

    def "Test where query with overlapping parameter and property names"() {
        given: "some people"
        createPeopleWithPets()

        when: "A variable is declared that overlaps with a property of the class"
        def age = 5
        def pets = []
        def query = Person.where {
            age > age && pets { age > age }
        }
        def results = query.list()

        then: "The variable takes precedence"
        query.count() == 2
        results.find { it.firstName == 'Ed' }
        results.find { it.firstName == 'Fred' }
    }

    def "Test where query on sorted set"() {
        given: "Some people and groups"
        createPeopleAndGroups()

        when: "A where query is executed on a sorted set"
        def query = Group.where {
            people.lastName == "Simpson"
        }
        def results = query.list(sort: "name", order: "desc")

        then: "The results are correct"
        results.size() == 1
    }

    @Issue('GRAILS-9996')
    void "test static where queries and CompileStatic"() {
        given: "A bunch of people"
        createPeople()
        when:
        Person person = findHomerWithStaticWhereAndCompileStatic()
        then:
        person.firstName == "Homer"
    }

    @Issue('GRAILS-9996')
    void "test where queries and CompileStatic"() {
        given: "A bunch of people"
        createPeople()
        when:
        Person person = DetachedCriteriaInnerClass.findHomerWithWhereAndCompileStatic()
        then:
        person.firstName == "Homer"

    }

    @CompileStatic
    static Person findHomerWithStaticWhereAndCompileStatic() {
        List<Person> results = Person.simpsons.list(max: 2)
        results[0]
    }

    // TODO: Issue GRAILS-9996 hasn't been solved yet, this breaks with @CompileStatic
    //@CompileStatic
    @ApplyDetachedCriteriaTransform
    static class DetachedCriteriaInnerClass {
        static Person findHomerWithWhereAndCompileStatic() {
            List<Person> results = Person.where { lastName == 'Simpson' }.list(max: 2)
            results[0]
        }
    }

    protected createContinentWithCountries() {
        final continent = new Continent(name: "Africa")
        continent.countries << new Country(name: "SA", population: 304830) << new Country(name: "Zim", population: 304830)
        assert continent.save(flush: true) != null
    }

    protected def createPeople() {
        new Person(firstName: "Homer", lastName: "Simpson", age: 45).save()
        new Person(firstName: "Marge", lastName: "Simpson", age: 40).save()
        new Person(firstName: "Bart", lastName: "Simpson", age: 9).save()
        new Person(firstName: "Lisa", lastName: "Simpson", age: 7).save()
        new Person(firstName: "Barney", lastName: "Rubble", age: 35).save()
        new Person(firstName: "Fred", lastName: "Flinstone", age: 41).save()
    }

    protected def createPeopleAndGroups() {
        createPeople()
        def simpsons = Person.findAllByLastName("Simpson")
        assert simpsons.size() == 4
        def s = new Group(name: "Simpsons")
        s.people.addAll(simpsons)
        s.save flush: true
        manager.session.clear()

        assert Group.findByName("Simpsons").people.size() == 4
    }

    protected def createPeopleInGroupsWithPets() {
        createPeopleWithPets()
        def peopleWith2Pets = Person.findAllByLastNameOrLastName("Bloggs", "Cloggs")
        assert peopleWith2Pets.size() == 2
        def s = new Group(name: "2 Pets")
        s.people.addAll(peopleWith2Pets)
        s.save flush: true

        def peopleWith3Pets = Person.findAllByLastName("Floggs")
        assert peopleWith3Pets.size() == 1
        s = new Group(name: "3 Pets")
        s.people.addAll(peopleWith3Pets)
        s.save flush: true

        manager.session.clear()

        assert Group.findByName("2 Pets").people.size() == 2
        assert Group.findByName("3 Pets").people.size() == 1
    }

    protected def createPeopleWithPets() {
        new Person(firstName: "Joe", lastName: "Bloggs", age: 4)
                .addToPets(name: "Jack", age: 5, birthDate: new GregorianCalendar(2011, 1, 1).time)
                .addToPets(name: "Butch", age: 10, birthDate: new GregorianCalendar(2011, 1, 1).time)
                .save()

        new Person(firstName: "Ed", lastName: "Floggs", age: 6)
                .addToPets(name: "Mini", age: 10, birthDate: new GregorianCalendar(2011, 1, 1).time)
                .addToPets(name: "Barbie", age: 4, birthDate: new GregorianCalendar(2011, 1, 1).time)
                .addToPets(name: "Ken", birthDate: new GregorianCalendar(2011, 1, 1).time)
                .save()

        new Person(firstName: "Fred", lastName: "Cloggs", age: 12)
                .addToPets(name: "Jim", age: 2, birthDate: new GregorianCalendar(2011, 1, 1).time)
                .addToPets(name: "Joe", age: 9, birthDate: new GregorianCalendar(2011, 1, 1).time)
                .save()
    }

    def queryUsingFunctionOnToOneAssociationProperty() {
        def gcl = new GroovyClassLoader(getClass().classLoader)
        gcl.parseClass('''
import org.apache.grails.data.testing.tck.domains.*
import grails.gorm.*
import grails.persistence.*
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

@ApplyDetachedCriteriaTransform
@Entity
class CallMe {
    def badQuery() {
        Pet.where {
            owner.age > avg(owner.age)
        }
    }
}
''')
    }

    def queryUsingUnknownToOneAssociationProperty() {
        def gcl = new GroovyClassLoader(getClass().classLoader)
        gcl.parseClass('''
import org.apache.grails.data.testing.tck.domains.*
import grails.gorm.*
import grails.persistence.*
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

@ApplyDetachedCriteriaTransform
@Entity
class CallMe {
    def badQuery() {
        Pet.where {
            owner.firstN == "Joe"
        }
    }
}
''')
    }

    def queryUsingUnsupportedOperatorInSize() {
        def gcl = new GroovyClassLoader(getClass().classLoader)
        gcl.parseClass('''
import org.apache.grails.data.testing.tck.domains.*
import grails.gorm.*
import grails.persistence.*
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

@ApplyDetachedCriteriaTransform
@Entity
class CallMe {
    def badQuery() {
        Person.where {
            pets.size() << 1
        }
    }
}
''')
    }

    def queryUsingUnsupportedOperator() {
        def gcl = new GroovyClassLoader(getClass().classLoader)
        gcl.parseClass('''
import org.apache.grails.data.testing.tck.domains.*
import grails.gorm.*
import grails.persistence.*
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

@ApplyDetachedCriteriaTransform
@Entity
class CallMe {
    def badQuery() {
        Person.where {
            firstName << "Blah"
        }
    }
}
''')
    }

    def queryUsingInvalidNegation() {
        def gcl = new GroovyClassLoader(getClass().classLoader)
        gcl.parseClass('''
import org.apache.grails.data.testing.tck.domains.*
import grails.gorm.*
import grails.persistence.*
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

@ApplyDetachedCriteriaTransform
@Entity
class CallMe {
    def badQuery() {
        Person.where {
            !(firstName)
        }
    }
}
''')
    }

    def queryUsingUnknownPropertyWithSize() {
        def gcl = new GroovyClassLoader(getClass().classLoader)
        gcl.parseClass('''
import org.apache.grails.data.testing.tck.domains.*
import grails.gorm.*
import grails.persistence.*
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

@ApplyDetachedCriteriaTransform
@Entity
class CallMe {
    def badQuery() {
        Person.where {
            blah.size() == 10
        }
    }
}
''')
    }

    def queryReferencingNonExistentProperty() {
        def gcl = new GroovyClassLoader(getClass().classLoader)
        gcl.parseClass('''
import org.apache.grails.data.testing.tck.domains.*
import grails.gorm.*
import grails.persistence.*
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

@ApplyDetachedCriteriaTransform
@Entity
class CallMe {
    def badQuery() {
        Person.where {
            doesntExist == "Blah"
        }
    }
}
''')
    }

    def queryReferencingNonExistentPropertyOfAssociation() {
        def gcl = new GroovyClassLoader(getClass().classLoader)
        gcl.parseClass('''
import org.apache.grails.data.testing.tck.domains.*
import grails.gorm.*
import grails.persistence.*
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

@ApplyDetachedCriteriaTransform
@Entity
class CallMe {

    Set<Pet> pets
    static hasMany = [pets:Pet]
    def badQuery() {
        Person.where {
            pets { doesntExist == "Blah" }
        }
    }
}

@ApplyDetachedCriteriaTransform
@Entity
class Pet {
    String name
}
''')
    }

    def getClassThatCallsWhere() {
        def gcl = new GroovyClassLoader(getClass().classLoader)
        Class callMe = gcl.parseClass('''
import org.apache.grails.data.testing.tck.domains.*
import grails.gorm.tests.*
import grails.gorm.DetachedCriteria
import grails.gorm.annotation.*
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

@ApplyDetachedCriteriaTransform
@Entity
class CallMe {
    String name
    def myDetachedCriteria = { firstName == "Bart" } as DetachedCriteria<Person>
    def declaredQuery() {
        Person.where(myDetachedCriteria)
    }

    static doStuff() {
        Person.where {}
    }

    def doQuery() {
        def session = [user: [username:"Fred"]]
        def results = Proposal.findAll { advisor.username == session.user.username && approvalDate != null }
    }
}
''', "Test")
        callMe.newInstance()
    }
}

@Entity
class Continent {
    Long id
    Long version

    String name
    Set<Country> countries = []
    static hasMany = [countries: Country]
}

@Entity
class Group {
    Long id
    String name
    SortedSet<Person> people = [] as SortedSet
    static hasMany = [people: Person]
}

@Entity
class Proposal {
    Long id
    String name
    Advisor advisor
    Date approvalDate

    static constraints = {
        advisor nullable: false
        name nullable: false
        approvalDate nullable: true
    }
}

@Entity
class Advisor {
    Long id
    String username

    static constraints = {
        username nullable: false
        proposals nullable: true
    }

    static hasMany = [proposals: Proposal]
}
