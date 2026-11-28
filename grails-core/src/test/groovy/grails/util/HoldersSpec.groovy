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
package grails.util

import spock.lang.Specification

class HoldersSpec extends Specification {

    def "reset does not fail where there is no Servlet environment"() {
        expect: 'the class path of this module has no holder of servlet contexts, as that of a command line has none'
        !servletHolderThere()

        when:
        Holders.reset()

        then:
        noExceptionThrown()
        Holders.findApplication() == null
    }

    private static boolean servletHolderThere() {
        try {
            Class.forName('grails.web.context.WebRequestServletHolder', false, Holders.classLoader)
            return true
        } catch (ClassNotFoundException ignored) {
            return false
        }
    }
}
