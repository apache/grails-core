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
package grails.gorm.validation

import org.grails.datastore.gorm.validation.constraints.registry.DefaultConstraintRegistry
import spock.lang.Specification

class DisplayTypeSpec extends Specification {

    DefaultConstrainedProperty constrainedProperty

    void setup() {
        constrainedProperty = new DefaultConstrainedProperty(
            TestDomain,
            "testProperty",
            String,
            new DefaultConstraintRegistry()
        )
    }

    void "test default displayType is null"() {
        expect:
        constrainedProperty.displayType == null
    }

    void "test default isDisplay returns true"() {
        expect:
        constrainedProperty.display == true
    }

    void "test setDisplay with boolean false sets displayType to NONE"() {
        when:
        constrainedProperty.setDisplay(false)

        then:
        constrainedProperty.displayType == DisplayType.NONE
        constrainedProperty.display == false
    }

    void "test setDisplay with boolean true sets displayType to null"() {
        when:
        constrainedProperty.setDisplay(true)

        then:
        constrainedProperty.displayType == null
        constrainedProperty.display == true
    }

    void "test setDisplay with DisplayType.ALL"() {
        when:
        constrainedProperty.setDisplay(DisplayType.ALL)

        then:
        constrainedProperty.displayType == DisplayType.ALL
        constrainedProperty.display == true
    }

    void "test setDisplay with DisplayType.NONE"() {
        when:
        constrainedProperty.setDisplay(DisplayType.NONE)

        then:
        constrainedProperty.displayType == DisplayType.NONE
        constrainedProperty.display == false
    }

    void "test setDisplay with DisplayType.INPUT"() {
        when:
        constrainedProperty.setDisplay(DisplayType.INPUT)

        then:
        constrainedProperty.displayType == DisplayType.INPUT
        constrainedProperty.display == true  // isDisplay still returns true for INPUT
    }

    void "test setDisplay with DisplayType.OUTPUT"() {
        when:
        constrainedProperty.setDisplay(DisplayType.OUTPUT)

        then:
        constrainedProperty.displayType == DisplayType.OUTPUT
        constrainedProperty.display == true  // isDisplay still returns true for OUTPUT
    }

    void "test setDisplay with null resets to default"() {
        given:
        constrainedProperty.setDisplay(DisplayType.NONE)

        when:
        constrainedProperty.setDisplay(null)

        then:
        constrainedProperty.displayType == null
        constrainedProperty.display == true
    }

    void "test setDisplay with invalid type throws exception"() {
        when:
        constrainedProperty.setDisplay("invalid")

        then:
        thrown(IllegalArgumentException)
    }

    void "test applyConstraint with display false sets displayType to NONE"() {
        when:
        constrainedProperty.applyConstraint("display", false)

        then:
        constrainedProperty.displayType == DisplayType.NONE
        constrainedProperty.display == false
    }

    void "test applyConstraint with display true sets displayType to null"() {
        when:
        constrainedProperty.applyConstraint("display", true)

        then:
        constrainedProperty.displayType == null
        constrainedProperty.display == true
    }

    void "test applyConstraint with DisplayType enum"() {
        when:
        constrainedProperty.applyConstraint("display", DisplayType.INPUT)

        then:
        constrainedProperty.displayType == DisplayType.INPUT
        constrainedProperty.display == true
    }

    static class TestDomain {
        String testProperty
    }
}
