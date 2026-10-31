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

import spock.lang.Specification
import spock.lang.Unroll

class H2ModeSpec extends Specification {

    @Unroll
    void 'H2 mode #input resolves to #expectedMode with target platform #expectedPlatform'() {
        expect:
        H2Mode.of(input) == expectedMode
        H2Mode.of(input).targetPlatform == expectedPlatform
        DatabasePlatform.resolveForH2(input) == expectedPlatform

        where:
        input                                || expectedMode       | expectedPlatform
        null                                 || H2Mode.REGULAR     | DatabasePlatform.H2
        'jdbc:h2:mem:test;LOCK_TIMEOUT=5000' || H2Mode.REGULAR     | DatabasePlatform.H2
        'jdbc:h2:mem:test;MODE=Oracle'       || H2Mode.ORACLE      | DatabasePlatform.ORACLE
        'jdbc:h2:mem:test;MODE=MSSQLServer'  || H2Mode.MSSQLSERVER | DatabasePlatform.SQL_SERVER
        'jdbc:h2:mem:test;MODE=Derby'        || H2Mode.DERBY       | DatabasePlatform.DERBY
        'jdbc:h2:mem:test;MODE=PostgreSQL'   || H2Mode.POSTGRESQL  | DatabasePlatform.POSTGRESQL
        'jdbc:h2:mem:test;MODE=MySQL'        || H2Mode.MYSQL       | DatabasePlatform.MYSQL
        'jdbc:h2:mem:test;MODE=MariaDB'      || H2Mode.MARIADB     | DatabasePlatform.MARIADB
        'jdbc:h2:mem:test;MODE=DB2'          || H2Mode.DB2         | DatabasePlatform.DB2_LUW
        'jdbc:h2:mem:test;MODE=Sybase'       || H2Mode.SYBASE      | DatabasePlatform.SYBASE
        'Oracle'                             || H2Mode.ORACLE      | DatabasePlatform.ORACLE
        'unknown_mode'                       || H2Mode.REGULAR     | DatabasePlatform.H2
    }
}
