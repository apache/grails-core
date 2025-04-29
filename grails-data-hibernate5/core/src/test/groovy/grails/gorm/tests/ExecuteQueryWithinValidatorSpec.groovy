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
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.Session
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 17/02/2017.
 */
class ExecuteQueryWithinValidatorSpec extends Specification {

    @AutoCleanup @Shared HibernateDatastore hibernateDatastore = new HibernateDatastore(Named, NameType)

    @Shared PlatformTransactionManager transactionManager = hibernateDatastore.transactionManager

    @Rollback
    void "test executeQuery method executed during validation"() {
        when:"a validator executed an HQL query"
        NameType nt = new NameType(nameType: "test").save(flush:true)
        Named.withSession { Session session ->
            session.save(new Named(nameType: nt))
        }


        then:"no stackoverflow occurs"
        NameType.count() == 1
        Named.count() == 1
    }
}

@Entity
class Named {
    NameType nameType

    static constraints = {
        nameType (validator: { val, obj, errors ->
            if (val !=null) {
                def parms = [nameType: val.nameType.trim().toLowerCase() ]
                def rows = NameType.executeQuery("""select nameType from NameType where lower(nameType) = :nameType""", parms)

                def found =false
                if (rows !=null && rows.size() ==1)
                    found =true
                if (!found) {
                    errors.rejectValue("nameType","personNames.nameType.invalidValue")
                }

                // handle case-sensitivity if (val.trim() != rows[0]) obj.nametype = rows[0]
            }
        })
    }
}

@Entity
class NameType {
    String nameType
}
