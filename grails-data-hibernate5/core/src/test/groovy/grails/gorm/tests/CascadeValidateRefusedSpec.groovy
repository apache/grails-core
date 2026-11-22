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
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.validation.CascadeValidateType
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.cfg.Settings
import grails.gorm.hibernate.mapping.MappingBuilder
import org.grails.orm.hibernate.cfg.HibernateMappingBuilder
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.PropertyConfig
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll

/**
 * A cascadeValidate that names no mode, a Boolean or a misspelling, is refused when the datastore starts, with a message
 * that lists the modes, whether it is set on one association or as the global default. Before, it started and failed at
 * the first validate() with "No enum constant".
 */
@Issue('https://github.com/apache/grails-core/issues/17012')
class CascadeValidateRefusedSpec extends Specification {

    private static String messageOf(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.cause) {
            if (cause.message?.contains('cascadeValidate must be one of')) {
                return cause.message
            }
        }
        return null
    }

    private static Map settings(String name, Map extra = [:]) {
        [(Settings.SETTING_DB_CREATE): 'create-drop', 'dataSource.url': "jdbc:h2:mem:${name};LOCK_TIMEOUT=10000".toString()] + extra
    }

    @Unroll
    void "MappingBuilder #form with cascadeValidate #given is refused when the mapping is read"() {
        when:
        read.call()

        then:
        IllegalArgumentException e = thrown()
        e.message.contains('cascadeValidate must be one of')

        where:
        form                               | given       | read
        'define, built'                    | 'sometimes' | { -> MappingBuilder.define { parts cascadeValidate: 'sometimes' }.build() }
        'define, built with a Boolean'     | 'true'      | { -> MappingBuilder.define { parts cascadeValidate: true }.build() }
        'define, configured onto a mapping' | 'sometimes' | { -> MappingBuilder.define { parts cascadeValidate: 'sometimes' }.configure(new Mapping()) }
        'define, a property block'         | 'sometimes' | { -> MappingBuilder.define { parts { cascadeValidate 'sometimes' } }.build() }
        'define, a map beside a block'     | 'sometimes' | { -> MappingBuilder.define { parts(cascadeValidate: 'sometimes') { } }.build() }
        'a property config built from a map' | 'sometimes' | { -> PropertyConfig.configureNew([cascadeValidate: 'sometimes']) }
        'a property config built from a block' | 'sometimes' | { -> PropertyConfig.configureNew { cascadeValidate = 'sometimes' } }
        'the default of every property'    | 'sometimes' | { -> MappingBuilder.define { '*'(cascadeValidate: 'sometimes') }.build() }
    }

    @Unroll
    void "the Hibernate readers keep a mode that is named as #kind"() {
        expect:
        PropertyConfig.configureNew([cascadeValidate: value]).cascadeValidate.toLowerCase() == 'owned'
        MappingBuilder.define { parts cascadeValidate: value }.build().getPropertyConfig('parts').cascadeValidate.toLowerCase() == 'owned'
        new HibernateMappingBuilder('Foo').evaluate { parts cascadeValidate: value }.getPropertyConfig('parts').cascadeValidate.toLowerCase() == 'owned'

        where:
        kind               | value
        'a GString'        | "${'owned'}"
        'an enum constant' | CascadeValidateType.OWNED
    }

    @Unroll
    void "#scope cascadeValidate #given is refused when the datastore starts"() {
        when:
        HibernateDatastore datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(settings(name, extra)), *classes)
        datastore.close()

        then:
        Exception e = thrown()
        messageOf(e).contains("but was '${given}'")

        where:
        scope             | given       | name                | extra                                                                            | classes
        'an association'  | 'true'      | 'cvRefusedTrue'     | [:]                                                                              | [CvrTrueOwner, CvrTruePart]
        'an association'  | 'sometimes' | 'cvRefusedName'     | [:]                                                                              | [CvrNameOwner, CvrNamePart]
        'an association through MappingBuilder.define' | 'sometimes' | 'cvRefusedDefine' | [:]                                       | [CvrDefineOwner, CvrDefinePart]
        'the global'      | 'true'      | 'cvRefusedGlobal'   | ['grails.gorm.default.mapping': { '*'(cascadeValidate: true) }]                  | [CvrPlainOwner, CvrPlainPart]
        'the global'      | 'false'     | 'cvRefusedGlobal2'  | ['grails.gorm.default.mapping': { '*'(cascadeValidate: false) }]                 | [CvrPlainOwner, CvrPlainPart]
    }
}

@Entity
class CvrTrueOwner {
    String name
    static hasMany = [parts: CvrTruePart]
    static mapping = {
        parts cascadeValidate: true
    }
}

@Entity
class CvrTruePart {
    String label
    static belongsTo = [owner: CvrTrueOwner]
}

@Entity
class CvrNameOwner {
    String name
    static hasMany = [parts: CvrNamePart]
    static mapping = {
        parts cascadeValidate: 'sometimes'
    }
}

@Entity
class CvrNamePart {
    String label
    static belongsTo = [owner: CvrNameOwner]
}

@Entity
class CvrDefineOwner {
    String name
    static hasMany = [parts: CvrDefinePart]
    static mapping = MappingBuilder.define {
        parts cascadeValidate: 'sometimes'
    }
}

@Entity
class CvrDefinePart {
    String label
    static belongsTo = [owner: CvrDefineOwner]
}

@Entity
class CvrPlainOwner {
    String name
    static hasMany = [parts: CvrPlainPart]
}

@Entity
class CvrPlainPart {
    String label
    static belongsTo = [owner: CvrPlainOwner]
}
