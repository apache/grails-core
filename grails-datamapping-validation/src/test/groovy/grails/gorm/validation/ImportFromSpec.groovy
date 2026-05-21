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

import groovy.transform.CompileStatic
import org.grails.datastore.gorm.validation.constraints.registry.DefaultValidatorRegistry
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.validation.ValidatorRegistry
import spock.lang.Specification

import jakarta.persistence.Entity

class ImportFromSpec extends Specification {

    void "test gets the metaConstraints"() {
        given:"setup validator registry"
        MappingContext mappingContext = new KeyValueMappingContext("test")
        def entity = mappingContext.addPersistentEntity(ImportFromTestEntity)
        ValidatorRegistry registry = new DefaultValidatorRegistry(mappingContext, new ConnectionSourceSettings())

        expect:"The validator is correct"
        def validator = (PersistentEntityValidator)registry.getValidator(entity)
        validator.constrainedProperties['createdDay'].metaConstraints["bindable"] == false
        validator.constrainedProperties['createdDay'].metaConstraints["example"] == "2017-12-31"
    }

}

@Entity
class ImportFromTestEntity implements DayStamp {
    String name

    static constraints = {
        importFrom(DayStampConstraints)
    }
}

@CompileStatic
trait DayStamp {

    Date createdDay

}

class DayStampConstraints implements DayStamp {

    static constraints = {
        createdDay nullable: true, bindable: false, example: "2017-12-31"
    }
}



