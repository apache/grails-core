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
package org.grails.datastore.mapping.transactions

import spock.lang.Specification

import org.grails.datastore.mapping.core.Session

class SessionHolderSpec extends Specification {

    Session outer = Mock(Session)
    Session onTop = Mock(Session)
    SessionHolder holder = new SessionHolder(outer)

    void setup() {
        holder.addSession(onTop)
    }

    void 'a rollback-only mark belongs to the session it was set for'() {
        when:
        holder.setRollbackOnly(outer)

        then:
        holder.isRollbackOnly(outer)
        !holder.isRollbackOnly(onTop)

        and: 'the holder as a whole is not marked'
        !holder.rollbackOnly
    }

    void 'resetting one session\'s mark leaves another session\'s in place'() {
        given:
        holder.setRollbackOnly(outer)
        holder.setRollbackOnly(onTop)

        when:
        holder.resetRollbackOnly(onTop)

        then:
        holder.isRollbackOnly(outer)
        !holder.isRollbackOnly(onTop)
    }

    void 'clearing the holder drops every session\'s mark'() {
        given:
        holder.setRollbackOnly(outer)
        holder.setTransactionActive(onTop, true)

        when:
        holder.clear()

        then:
        !holder.isRollbackOnly(outer)
        !holder.transactionActive
    }
}
