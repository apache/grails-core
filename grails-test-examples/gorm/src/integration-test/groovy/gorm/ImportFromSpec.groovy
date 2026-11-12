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

package gorm

import spock.lang.Specification

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration

@Rollback
@Integration
class ImportFromSpec extends Specification {

    void 'a command object importing from a domain class gets the configured nullable default'() {
        given: 'a command object with every property left null'
        CustomerCommand command = new CustomerCommand()

        when:
        command.validate()

        then: 'the properties the domain class declares no nullable constraint for are required'
        command.errors.getFieldError('name')?.code == 'nullable'
        command.errors.getFieldError('nickname')?.code == 'nullable'

        and: 'the nullable constraint the domain class declares is imported as it is'
        !command.errors.hasFieldErrors('notes')
    }

    void 'a command object importing from a domain class gets the other constraints the domain class declares'() {
        given: 'a command object with a nickname longer than the domain class allows'
        CustomerCommand command = new CustomerCommand(name: 'Jane', nickname: 'Janey-Jane')

        when:
        command.validate()

        then:
        command.errors.getFieldError('nickname')?.code == 'maxSize.exceeded'
        !command.errors.hasFieldErrors('name')
    }
}
