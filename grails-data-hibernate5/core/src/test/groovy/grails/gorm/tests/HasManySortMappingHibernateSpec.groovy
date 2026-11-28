/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package grails.gorm.tests

import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.cfg.Settings
import spock.lang.Issue
import spock.lang.Specification

/**
 * A hasMany that is sorted by a property of the associated entity in its mapping. A sort that does not name a property of the
 * associated entity must be refused with a message at startup, not with a NullPointerException.
 */
@Issue('https://github.com/apache/grails-core/issues/14457')
class HasManySortMappingHibernateSpec extends Specification {

    private static HibernateDatastore start(Class... classes) {
        Map config = [
            (Settings.SETTING_DB_CREATE): 'create-drop',
            'dataSource.url'            : "jdbc:h2:mem:hasManySort${System.nanoTime()};LOCK_TIMEOUT=10000"
        ]
        new HibernateDatastore(DatastoreUtils.createPropertyResolver(config), classes)
    }

    private static String rootMessage(Throwable e) {
        Throwable cause = e
        while (cause.cause != null && cause.cause != cause) {
            cause = cause.cause
        }
        cause.message ?: ''
    }

    void "a sorted hasMany that is unidirectional is refused with a message"() {
        when:
        start(SortPerson, SortDevice, SortPersonDevice)

        then:
        Throwable e = thrown()
        rootMessage(e).contains('unidirectional')
    }

    void "a sorted hasMany whose sort is a path through another association is refused with a message, not a NullPointerException"() {
        when:
        start(BiPerson, BiDevice, BiPersonDevice)

        then:
        Throwable e = thrown()
        !(e instanceof NullPointerException)
        rootMessage(e).contains('Default sort [device.priority] of the association')
        rootMessage(e).contains('is not a property of')
    }

    void "a sorted bidirectional hasMany sorts its collection by a property of the associated entity"() {
        given:
        HibernateDatastore datastore = start(BiPlainPerson, BiPlainDevice)

        when:
        Long id = BiPlainPerson.withTransaction {
            BiPlainPerson person = new BiPlainPerson(firstName: 'ann')
            [3, 1, 2].each { Integer priority -> person.addToDevices(new BiPlainDevice(name: 'd' + priority, priority: priority)) }
            person.save(flush: true, failOnError: true)
            person.id
        }
        List<Integer> priorities = BiPlainPerson.withNewSession {
            BiPlainPerson.get(id).devices*.priority
        }

        then:
        priorities == [1, 2, 3]

        cleanup:
        datastore?.close()
    }
}

@Entity
class SortPerson {
    String firstName
    static hasMany = [devices: SortPersonDevice]
    static mapping = {
        devices cascade: 'all-delete-orphan', sort: 'device.priority'
    }
}

@Entity
class SortDevice {
    String name
    Integer priority
}

@Entity
class SortPersonDevice {
    SortDevice device
    Date purchaseDate
}

@Entity
class BiPerson {
    String firstName
    static hasMany = [devices: BiPersonDevice]
    static mapping = {
        devices sort: 'device.priority'
    }
}

@Entity
class BiDevice {
    String name
    Integer priority
}

@Entity
class BiPersonDevice {
    BiDevice device
    Date purchaseDate
    static belongsTo = [person: BiPerson]
}

@Entity
class BiPlainPerson {
    String firstName
    static hasMany = [devices: BiPlainDevice]
    static mapping = {
        devices sort: 'priority'
    }
}

@Entity
class BiPlainDevice {
    String name
    Integer priority
    static belongsTo = [person: BiPlainPerson]
}
