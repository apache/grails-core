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

import jakarta.persistence.Entity

import spock.lang.Shared
import spock.lang.Specification

import org.springframework.validation.Validator

import org.grails.datastore.gorm.validation.constraints.registry.DefaultValidatorRegistry
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.validation.ValidationErrors

class UrlValidationSpec extends Specification {

    @Shared
    Validator validator

    void setupSpec() {
        def mappingContext = new KeyValueMappingContext('urlValidation')
        def entity = mappingContext.addPersistentEntity(UrlValidationEntity)
        def registry = new DefaultValidatorRegistry(mappingContext, new ConnectionSourceSettings())
        validator = registry.getValidator(entity)
    }

    void 'URL constraint validates #value as #valid'() {
        given:
        def target = new UrlValidationEntity(address: value)
        def errors = new ValidationErrors(target)

        when:
        validator.validate(target, errors)

        then:
        errors.hasErrors() == !valid
        if (!valid) {
            assert errors.getFieldError('address').code == 'url.invalid'
            assert errors.getFieldError('address').rejectedValue == value
        }

        where:
        value                                                      | valid
        'http://localhost'                                         | true
        'http://foo.localhost'                                     | true
        'http://foo.localdomain'                                   | true
        'https://localhost:9443/apptest/authentication/oidc/MS_ENTRA' | true
        'http://intranet'                                          | true
        'http://my-server:8080/path?query=value#fragment'            | true
        'http://a'                                                 | true
        // Numeric single-label hosts are intentionally accepted as local URLs, not public DNS top-level labels.
        'http://0'                                                 | true
        'http://123'                                               | true
        'http://localhost:65535'                                   | true
        'https://example.com/path'                                 | true
        'http://127.0.0.1:8080'                                     | true
        'http://[::1]:8080'                                         | true
        'custom://localhost/path'                                  | true
        'http://localhost/path//segment'                           | true
        null                                                       | true
        'http://-bad'                                              | false
        'http://bad-'                                              | false
        'http://bad_host'                                          | false
        "http://${'a' * 64}"                                       | false
        'http://localhost:65536'                                   | false
        'http://localhost:-1'                                      | false
        'http://localhost:abc'                                     | false
        'http:///path'                                             | false
        'localhost'                                                | false
        'http://local host'                                        | false
        'http://example.invalidtld'                                 | false
    }

    void 'custom authority validators retain their behavior for #value'() {
        given:
        def target = new UrlValidationEntity(regexAddress: value, listAddress: value)
        def errors = new ValidationErrors(target)

        when:
        validator.validate(target, errors)

        then:
        errors.hasErrors() == !valid
        errors.hasFieldErrors('regexAddress') == !valid
        errors.hasFieldErrors('listAddress') == !valid

        where:
        value                        | valid
        'http://service.internal'     | true
        'http://localhost:8080/path'  | true
        'https://example.com'         | true
        'http://bad_host'             | false
        'http://localhost:65536'      | false
    }
}

@Entity
class UrlValidationEntity {

    String address
    String regexAddress
    String listAddress

    static constraints = {
        address(url: true, nullable: true)
        regexAddress(url: /service\.internal/, nullable: true)
        listAddress(url: [/service\.internal/, /other\.internal/], nullable: true)
    }
}
