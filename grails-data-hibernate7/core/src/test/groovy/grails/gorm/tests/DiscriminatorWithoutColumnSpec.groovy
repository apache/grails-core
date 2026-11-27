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

import grails.gorm.annotation.Entity

/**
 * A table-per-hierarchy root that maps only a discriminator value, such as {@code discriminator 'animal'},
 * has no discriminator column of its own, so the default column name has to be used wherever the column is
 * named. A one-to-many to a subclass filters on that column.
 */
class DiscriminatorWithoutColumnSpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.registerDomainClasses(DiscAnimal, DiscDog, DiscOwner, DiscColumnOnly)
    }

    void 'a one-to-many to a subclass whose root maps only a discriminator value loads its elements'() {
        given:
        DiscOwner owner = new DiscOwner(name: 'ann')
        owner.addToDogs(new DiscDog(name: 'rex'))
        owner.save(flush: true, failOnError: true)
        session.clear()

        expect:
        DiscOwner.get(owner.id).dogs*.name == ['rex']
    }

    void 'the discriminator column name of a root that maps only a value is the default'() {
        given:
        def entity = DiscDog.gormPersistentEntity

        expect:
        entity.discriminatorColumnName == 'class'
        entity.discriminatorValue == 'dog'
    }

    void 'the discriminator value defaults to the class name when only a column is mapped'() {
        given:
        def entity = DiscColumnOnly.gormPersistentEntity

        expect:
        entity.discriminatorColumnName == 'kind'
        entity.discriminatorValue == DiscColumnOnly.name
    }
}

@Entity
class DiscAnimal {
    String name

    static mapping = {
        discriminator 'animal'
    }
}

@Entity
class DiscDog extends DiscAnimal {
    static belongsTo = [owner: DiscOwner]

    static mapping = {
        discriminator 'dog'
    }
}

@Entity
class DiscOwner {
    String name

    static hasMany = [dogs: DiscDog]
}

@Entity
class DiscColumnOnly {
    String name

    static mapping = {
        discriminator column: 'kind'
    }
}
