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
package grails.gorm.tests.inheritance

import grails.gorm.annotation.Entity
import grails.gorm.tests.HibernateGormDatastoreSpec
import org.hibernate.Hibernate
import org.hibernate.proxy.HibernateProxy
import spock.lang.Issue
import spock.lang.Unroll

@Issue('https://github.com/apache/grails-core/issues/16464')
class IntermediateSubclassQuerySpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(
                HierarchyVehicle, HierarchyCar, HierarchySportsCar, HierarchySedan, HierarchyGarage,
                JoinedVehicle, JoinedCar, JoinedSportsCar, JoinedSedan, JoinedGarage,
                ConcreteVehicle, ConcreteCar, ConcreteSportsCar, ConcreteSedan, ConcreteGarage
        )
    }

    @Unroll
    void 'a query on the intermediate class #car.simpleName returns the rows of its subclasses'() {
        given:
        saveHierarchy(vehicle, car, sportsCar, sedan)

        expect:
        car.count() == 3
        car.list()*.name.sort() == ['car', 'sedan', 'sports car']
        car.executeQuery("select count(*) from ${car.simpleName}".toString()) == [3L]
        car.createCriteria().list { like('name', '%car%') }*.name.sort() == ['car', 'sports car']
        car.findAllByNameLike('s%')*.name.sort() == ['sedan', 'sports car']

        where:
        vehicle          | car          | sportsCar          | sedan
        HierarchyVehicle | HierarchyCar | HierarchySportsCar | HierarchySedan
        JoinedVehicle    | JoinedCar    | JoinedSportsCar    | JoinedSedan
        ConcreteVehicle  | ConcreteCar  | ConcreteSportsCar  | ConcreteSedan
    }

    @Unroll
    void 'loading a subclass row through the intermediate class #car.simpleName returns the subclass instance'() {
        given:
        def ids = saveHierarchy(vehicle, car, sportsCar, sedan)

        expect:
        sportsCar.isInstance(car.get(ids[sportsCar]))
        sedan.isInstance(car.get(ids[sedan]))
        car.get(ids[vehicle]) == null

        where:
        vehicle          | car          | sportsCar          | sedan
        HierarchyVehicle | HierarchyCar | HierarchySportsCar | HierarchySedan
        JoinedVehicle    | JoinedCar    | JoinedSportsCar    | JoinedSedan
        ConcreteVehicle  | ConcreteCar  | ConcreteSportsCar  | ConcreteSedan
    }

    @Unroll
    void 'queries on the root #vehicle.simpleName and the leaf #sportsCar.simpleName keep their rows'() {
        given:
        saveHierarchy(vehicle, car, sportsCar, sedan)

        expect:
        vehicle.count() == 4
        vehicle.list()*.name.sort() == ['car', 'sedan', 'sports car', 'vehicle']
        sportsCar.count() == 1
        sportsCar.list()*.name == ['sports car']
        sedan.executeQuery("select count(*) from ${sedan.simpleName}".toString()) == [1L]

        where:
        vehicle          | car          | sportsCar          | sedan
        HierarchyVehicle | HierarchyCar | HierarchySportsCar | HierarchySedan
        JoinedVehicle    | JoinedCar    | JoinedSportsCar    | JoinedSedan
        ConcreteVehicle  | ConcreteCar  | ConcreteSportsCar  | ConcreteSedan
    }

    @Unroll
    void 'associations typed to the intermediate class #car.simpleName load its subclass instances'() {
        given:
        def savedSportsCar = sportsCar.newInstance(name: 'sports car').save(failOnError: true)
        def savedSedan = sedan.newInstance(name: 'sedan').save(failOnError: true)
        def savedGarage = garage.newInstance(name: 'garage', car: savedSportsCar)
        savedGarage.addToCars(savedSportsCar)
        savedGarage.addToCars(savedSedan)
        savedGarage.save(failOnError: true, flush: true)
        manager.session.clear()

        when: 'the single-ended association is loaded lazily'
        def lazyGarage = garage.get(savedGarage.id)

        then: 'the proxy initializes to the subclass instance'
        lazyGarage.car instanceof HibernateProxy
        Hibernate.getClass(lazyGarage.car) == sportsCar
        lazyGarage.car.name == 'sports car'

        and: 'the collection holds every subclass instance'
        lazyGarage.cars.collect { Hibernate.getClass(it) } as Set == [sportsCar, sedan] as Set

        when: 'the single-ended association is join fetched'
        manager.session.clear()
        def fetchedGarage = garage.list(fetch: [car: 'join']).first()

        then: 'the association is the subclass instance itself'
        !(fetchedGarage.car instanceof HibernateProxy)
        fetchedGarage.car.getClass() == sportsCar

        where:
        car          | sportsCar          | sedan          | garage
        HierarchyCar | HierarchySportsCar | HierarchySedan | HierarchyGarage
        JoinedCar    | JoinedSportsCar    | JoinedSedan    | JoinedGarage
        ConcreteCar  | ConcreteSportsCar  | ConcreteSedan  | ConcreteGarage
    }

    private Map<Class, Object> saveHierarchy(Class vehicle, Class car, Class sportsCar, Class sedan) {
        Map<Class, Object> ids = [
                (vehicle)  : vehicle.newInstance(name: 'vehicle').save(failOnError: true).id,
                (car)      : car.newInstance(name: 'car').save(failOnError: true).id,
                (sportsCar): sportsCar.newInstance(name: 'sports car').save(failOnError: true).id,
                (sedan)    : sedan.newInstance(name: 'sedan').save(failOnError: true).id
        ]
        manager.session.flush()
        manager.session.clear()
        ids
    }
}

@Entity
class HierarchyVehicle {
    String name
}

@Entity
class HierarchyCar extends HierarchyVehicle {
}

@Entity
class HierarchySportsCar extends HierarchyCar {
}

@Entity
class HierarchySedan extends HierarchyCar {
}

@Entity
class HierarchyGarage {
    String name
    HierarchyCar car
    static hasMany = [cars: HierarchyCar]
}

@Entity
class JoinedVehicle {
    String name

    static mapping = {
        tablePerHierarchy false
    }
}

@Entity
class JoinedCar extends JoinedVehicle {
}

@Entity
class JoinedSportsCar extends JoinedCar {
}

@Entity
class JoinedSedan extends JoinedCar {
}

@Entity
class JoinedGarage {
    String name
    JoinedCar car
    static hasMany = [cars: JoinedCar]
}

@Entity
class ConcreteVehicle {
    String name

    static mapping = {
        tablePerConcreteClass true
        id generator: 'table'
    }
}

@Entity
class ConcreteCar extends ConcreteVehicle {
}

@Entity
class ConcreteSportsCar extends ConcreteCar {
}

@Entity
class ConcreteSedan extends ConcreteCar {
}

@Entity
class ConcreteGarage {
    String name
    ConcreteCar car
    static hasMany = [cars: ConcreteCar]
}
