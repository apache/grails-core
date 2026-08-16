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
package org.grails.datastore.gorm.finders

import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.core.SessionCallback
import org.grails.datastore.mapping.core.VoidSessionCallback
import spock.lang.Specification

/**
 * Exercises {@link FinderSupport#execute}'s "stateless mode" guard - the shared session-execution
 * helper every synchronous finder in this package calls with its own {@code Datastore} field,
 * rather than inheriting an {@code execute} method.
 */
class FinderSupportSpec extends Specification {

    void "execute(SessionCallback) throws IllegalStateException when the datastore is null"() {
        given:
        SessionCallback callback = { Session session -> 'result' } as SessionCallback

        when:
        FinderSupport.execute(null, callback)

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot execute session query in stateless mode'
    }

    void "execute(VoidSessionCallback) throws IllegalStateException when the datastore is null"() {
        given:
        VoidSessionCallback callback = { Session session -> } as VoidSessionCallback

        when:
        FinderSupport.execute(null, callback)

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot execute session query in stateless mode'
    }
}
