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
package org.grails.datastore.gorm.mongo

import grails.persistence.Entity
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.bson.types.ObjectId
import spock.lang.Issue

class DistinctPropertySpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([Student])
    }

    @Issue('GPMONGODB-220')
    def "Test that a distinct project returns distinct results"() {
        given: "Some domain classes with distinct and non-distinct properties"
        createSampleData()

        when: "We query for non-distinct results using criteria"
        def results = Student.createCriteria().list {
            projections {
                property('classcode')
            }
        }

        then: "The results are correct"
        results.size() == 3
        results.contains("01")
        results.contains("02")

        when: "We query for distinct results using criteria"
        results = Student.createCriteria().list {
            projections {
                distinct('classcode')
            }
        }

        then: "The results are correct"
        results.size() == 2
        results.contains("01")
        results.contains("02")
    }

    void createSampleData() {
        [[classcode: "01", studentcode: "0101"],
         [classcode: "01", studentcode: "0102"],
         [classcode: "02", studentcode: "0201"]].each {
            new Student(it).save(flush: true)
        }
    }
}

@Entity
class Student {
    ObjectId id
    String classcode
    String studentcode
}
