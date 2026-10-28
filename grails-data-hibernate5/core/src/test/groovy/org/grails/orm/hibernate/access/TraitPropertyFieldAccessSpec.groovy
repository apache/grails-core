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
package org.grails.orm.hibernate.access

import org.hibernate.property.access.spi.PropertyAccess
import spock.lang.Issue
import spock.lang.Specification

/**
 * Regression test for private trait-field access through {@link TraitPropertyAccessStrategy}.
 *
 * Groovy implements trait properties by generating a private, name-mangled field on the
 * implementing class (for example {@code org_example_MyTrait__value}) plus public bridge
 * accessors. {@link TraitPropertyAccessStrategy} resolves the property to that generated
 * field and builds a field-based {@code Getter}/{@code Setter} over it, so the field must
 * be made accessible before it is used.
 */
class TraitPropertyFieldAccessSpec extends Specification {

    @Issue('https://github.com/apache/grails-core/issues/16615')
    void "field-based access to a trait property reads back what the setter wrote"() {
        given:
        FieldAccessProbeEntity entity = new FieldAccessProbeEntity()
        PropertyAccess access = new TraitPropertyAccessStrategy()
                .buildPropertyAccess(FieldAccessProbeEntity, 'probeField')

        when:
        access.setter.set(entity, 'value', null)

        then:
        access.getter.get(entity) == 'value'
    }
}

trait FieldAccessProbe {
    String probeField
}

class FieldAccessProbeEntity implements FieldAccessProbe { }
