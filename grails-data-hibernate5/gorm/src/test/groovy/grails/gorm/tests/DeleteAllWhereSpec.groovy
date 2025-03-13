/*
 * Copyright 2017 original authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package grails.gorm.tests

import org.grails.orm.hibernate.GormSpec
import spock.lang.Issue

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class DeleteAllWhereSpec extends GormSpec{

    @Issue('https://github.com/grails/grails-data-mapping/issues/969')
    void "test delete all type conversion"() {
        given:
        new Club(name: "Manchester United").save()
        new Club(name: "Arsenal").save(flush:true)

        when:
        int count = Club.count

        then:
        count == 2

        when:
        def idList = [Club.findByName("Arsenal").id as Integer]
        Club.where {
            id in idList
        }.deleteAll()

        then:
        Club.count == 1
        Club.findByName("Manchester United")
    }


    @Override
    List getDomainClasses() {
        [Club]
    }
}
