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
package org.grails.datastore.mapping.config

import org.grails.datastore.mapping.config.groovy.DefaultMappingConfigurationBuilder
import org.grails.datastore.mapping.validation.CascadeValidateType
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll

/**
 * The modes a mapping can name for cascadeValidate, tried at each way a value reaches the property: assignment, the
 * setter, the fluent builder method and a clone. A Boolean or an unknown name is refused when the mapping is read, with
 * a message that lists the modes, and is not left to fail at the first validate().
 */
@Issue('https://github.com/apache/grails-core/issues/17012')
class PropertyCascadeValidateSpec extends Specification {

    @Unroll
    void "the mode '#mode' is accepted by #way"() {
        when:
        Property property = new Property()
        way == 'assignment' ? (property.cascadeValidate = mode) : way == 'setter' ? property.setCascadeValidate(mode) : property.cascadeValidate(mode)

        then:
        property.cascadeValidate == mode

        where:
        [way, mode] << [['assignment', 'setter', 'builder'], ['default', 'none', 'owned', 'dirty', 'DIRTY', 'Owned', null]].combinations()
    }

    @Unroll
    void "the value '#value' is refused by #way, naming the accepted modes"() {
        when:
        Property property = new Property()
        way == 'assignment' ? (property.cascadeValidate = value) : way == 'setter' ? property.setCascadeValidate(value as String) : property.cascadeValidate(value as String)

        then:
        IllegalArgumentException e = thrown()
        e.message.contains('cascadeValidate')
        e.message.contains("'${value}'")
        ['default', 'none', 'owned', 'dirty'].every { e.message.contains(it) }

        where:
        [way, value] << [['assignment', 'setter', 'builder'], [true, false, 'sometimes', '']].combinations()
    }

    @Unroll
    void "a mapping map that names '#value' is refused when it is read through configureExisting"() {
        when:
        Property.configureExisting(new Property(), [cascadeValidate: value])

        then:
        IllegalArgumentException e = thrown()
        e.message.contains("'${value}'")
        ['default', 'none', 'owned', 'dirty'].every { e.message.contains(it) }

        where:
        value << [true, false, 'sometimes', '']
    }

    @Unroll
    void "a mapping map that names the mode '#mode' is kept when it is read through configureExisting"() {
        expect:
        Property.configureExisting(new Property(), [cascadeValidate: mode]).cascadeValidate == mode

        where:
        mode << ['default', 'none', 'owned', 'DIRTY']
    }

    @Unroll
    void "a mapping block that names '#value' is refused when it is read by the mapping builder"() {
        given:
        DefaultMappingConfigurationBuilder builder = new DefaultMappingConfigurationBuilder(new Entity(), Property)

        when:
        builder.parts(cascadeValidate: value)

        then:
        IllegalArgumentException e = thrown()
        e.message.contains("'${value}'")
        builder.properties.get('parts') == null

        where:
        value << [true, false, 'sometimes', '']
    }

    @Unroll
    void "a mapping block that names the mode '#mode' is kept by the mapping builder"() {
        given:
        DefaultMappingConfigurationBuilder builder = new DefaultMappingConfigurationBuilder(new Entity(), Property)

        when:
        builder.parts(cascadeValidate: mode)

        then:
        builder.properties.get('parts').cascadeValidate == mode

        where:
        mode << ['default', 'none', 'owned', 'DIRTY']
    }

    @Unroll
    void "a mapping map that names a mode as #kind is kept when it is read through configureExisting"() {
        expect:
        Property.configureExisting(new Property(), [cascadeValidate: value]).cascadeValidate.toLowerCase() == 'owned'

        where:
        kind                     | value
        'a GString'              | "${'owned'}"
        'an enum constant'       | CascadeValidateType.OWNED
    }

    @Unroll
    void "a mapping block that names a mode as #kind is kept by the mapping builder"() {
        given:
        DefaultMappingConfigurationBuilder builder = new DefaultMappingConfigurationBuilder(new Entity(), Property)

        when:
        builder.parts(cascadeValidate: value)

        then:
        builder.properties.get('parts').cascadeValidate.toLowerCase() == 'owned'

        where:
        kind                     | value
        'a GString'              | "${'owned'}"
        'an enum constant'       | CascadeValidateType.OWNED
    }

    @Unroll
    void "a mapping map that names '#value' as #kind is still refused when it is read through configureExisting"() {
        when:
        Property.configureExisting(new Property(), [cascadeValidate: value])

        then:
        IllegalArgumentException e = thrown()
        e.message.contains('cascadeValidate')

        where:
        kind                       | value
        'a GString that is no mode' | "${'sometimes'}"
        'a Boolean'                 | true
        'an empty GString'          | "${''}"
    }

    void "a mapping map that names a mode in capitals as a GString is kept"() {
        expect:
        Property.configureExisting(new Property(), [cascadeValidate: "${'OWNED'}"]).cascadeValidate.toLowerCase() == 'owned'
    }

    void "a refused value leaves the earlier mode in place"() {
        given:
        Property property = new Property()
        property.cascadeValidate = 'dirty'

        when:
        property.cascadeValidate = true

        then:
        thrown(IllegalArgumentException)
        property.cascadeValidate == 'dirty'
    }

    void "a clone keeps the mode"() {
        given:
        Property property = new Property()
        property.cascadeValidate = 'owned'

        expect:
        property.clone().cascadeValidate == 'owned'
    }
}
