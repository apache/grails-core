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
package org.grails.orm.hibernate

import org.hibernate.FlushMode
import org.hibernate.Session
import spock.lang.Specification

class InstanceApiHelperSpec extends Specification {

    GrailsHibernateTemplate template = Mock(GrailsHibernateTemplate)
    Session session = Mock(Session)
    InstanceApiHelper helper = new InstanceApiHelper(template)

    def "test remove without flush"() {
        given:
        def obj = new Object()

        when:
        helper.remove(obj, false)

        then:
        1 * template.execute(_) >> { args -> 
            args[0].doInHibernate(session)
        }
        1 * session.remove(obj)
        0 * session.flush()
    }

    def "test remove with flush"() {
        given:
        def obj = new Object()

        when:
        helper.remove(obj, true)

        then:
        1 * template.execute(_) >> { args -> 
            args[0].doInHibernate(session)
        }
        1 * session.remove(obj)
        1 * session.flush()
    }

    def "test setFlushModeManual"() {
        when:
        helper.setFlushModeManual()

        then:
        1 * template.execute(_) >> { args -> 
            args[0].doInHibernate(session)
        }
        1 * session.setHibernateFlushMode(FlushMode.MANUAL)
    }
}
