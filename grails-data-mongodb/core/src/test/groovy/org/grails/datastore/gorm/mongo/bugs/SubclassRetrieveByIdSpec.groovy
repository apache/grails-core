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
package org.grails.datastore.gorm.mongo.bugs

import grails.persistence.Entity
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.config.MongoSettings
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Unroll

/**
 * Every class of an inheritance hierarchy shares its root's collection, so loading by id through a
 * subclass must only match the documents of that subclass and its own subclasses, with either engine.
 */
class SubclassRetrieveByIdSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    @Shared
    @AutoCleanup
    MongoDatastore codecEngineDatastore

    @Shared
    @AutoCleanup
    MongoDatastore mappingEngineDatastore

    void setupSpec() {
        manager.registerDomainClasses(SrVehicle, SrCar, SrSportsCar)
        codecEngineDatastore = new MongoDatastore(
                manager.configuration + [(MongoSettings.SETTING_ENGINE): 'codec'],
                SrVehicle, SrCar, SrSportsCar)
        mappingEngineDatastore = new MongoDatastore(
                manager.configuration + [(MongoSettings.SETTING_ENGINE): 'mapping'],
                SrVehicle, SrCar, SrSportsCar)
    }

    @Unroll
    void 'the #engine engine datastore uses a #sessionType'() {
        expect: 'the cases below would otherwise not reach the engine they name'
        datastore(engine).connect().getClass().simpleName == sessionType

        where:
        engine    | sessionType
        'codec'   | 'MongoCodecSession'
        'mapping' | 'MongoSession'
    }

    @Unroll
    void 'with the #engine engine, loading through a subclass only matches its own hierarchy'() {
        given:
        MongoDatastore datastore = datastore(engine)
        Map<Class, Serializable> ids = [:]
        datastore.withSession { Session s ->
            [new SrVehicle(name: 'vehicle'), new SrCar(name: 'car'), new SrSportsCar(name: 'sports car')].each {
                s.persist(it)
                s.flush()
                ids[it.getClass()] = it.id
            }
        }

        expect:
        datastore.withSession { Session s ->
            s.clear()
            [
                    s.retrieve(SrCar, ids[SrVehicle]),
                    s.retrieve(SrSportsCar, ids[SrVehicle]),
                    s.retrieve(SrSportsCar, ids[SrCar]),
            ]
        } == [null, null, null]

        and:
        datastore.withSession { Session s ->
            s.clear()
            [
                    s.retrieve(SrVehicle, ids[SrVehicle]),
                    s.retrieve(SrVehicle, ids[SrSportsCar]),
                    s.retrieve(SrCar, ids[SrCar]),
                    s.retrieve(SrCar, ids[SrSportsCar]),
            ]*.getClass()
        } == [SrVehicle, SrSportsCar, SrCar, SrSportsCar]

        where:
        engine << ['codec', 'mapping']
    }

    private MongoDatastore datastore(String engine) {
        engine == 'codec' ? codecEngineDatastore : mappingEngineDatastore
    }
}

@Entity
class SrVehicle {
    String name
}

@Entity
class SrCar extends SrVehicle {}

@Entity
class SrSportsCar extends SrCar {}
