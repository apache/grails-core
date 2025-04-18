package grails.gorm.tests

import spock.lang.Issue

/*
 * Copyright 2014 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * @author graemerocher
 */
class ProjectionsSpec extends GormDatastoreSpec{

    void "Test sum projection"() {
        given:"Some test data"
        new Dog(name:"Fred", age:6).save()
        new Dog(name:"Ginger", age:2).save()
        new Dog(name:"Rastas", age:4).save()
        new Dog(name:"Albert", age:11).save()
        new Dog(name:"Joe", age:2).save(flush:true)

        when:"A sum projection is used"
        def avg = Dog.createCriteria().list {
            projections {
                avg 'age'
                max 'age'
                min 'age'
                sum 'age'
                count()
            }
        }

        then:"The result is correct"
        Dog.count() == 5
        avg == [5,11,2,25,5]
    }

    @Issue('GPMONGODB-294')
    void "Test multiple projections"() {
        given:"Some test data"
        new Dog(name:"Fred", age:6).save()
        new Dog(name:"Joe", age:2).save(flush:true)

        when:"A sum projection is used"
        def results = Dog.createCriteria().list {
            projections {
                property 'name'
                property 'age'
            }
            order 'name'
        }

        then:"The result is correct"
        results == [["Fred", 6], ["Joe", 2]]
    }

    @Override
    List getDomainClasses() {
        [Dog]
    }
}

