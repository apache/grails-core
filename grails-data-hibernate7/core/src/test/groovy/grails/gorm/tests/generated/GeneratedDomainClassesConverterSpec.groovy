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
package grails.gorm.tests.generated

import grails.gorm.tests.HibernateGormDatastoreSpec
import grails.persistence.Entity

/**
 * A property mapped with a registered type name that converts its value ({@code type: 'yes_no'}) is stored through the same
 * converter when the entity is bound through the generated classes: the column holds the converted value and the property
 * reads back as a Boolean.
 */
class GeneratedDomainClassesConverterSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        registerGeneratedDomainClasses(GdcConverted)
    }

    def "the converted columns hold the converted values and the properties read back"() {
        given:
        GdcConverted saved = new GdcConverted(yesNo: true, trueFalse: false, numeric: true).save(flush: true)
        List row = sessionFactory.currentSession.createNativeQuery(
                "select yes_no, true_false, numeric from gdc_converted where id = ${saved.id}".toString(), Object[]).singleResult as List
        sessionFactory.currentSession.clear()

        when:
        GdcConverted loaded = GdcConverted.get(saved.id)

        then:
        row == ['Y', 'F', 1]
        loaded.yesNo
        !loaded.trueFalse
        loaded.numeric
    }
}

@Entity
class GdcConverted {

    Boolean yesNo
    Boolean trueFalse
    Boolean numeric

    static mapping = {
        yesNo type: 'yes_no'
        trueFalse type: 'true_false'
        numeric type: 'numeric_boolean'
    }
}
