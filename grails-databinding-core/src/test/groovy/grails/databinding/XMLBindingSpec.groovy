/* Copyright 2013-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.databinding

import groovy.xml.XmlSlurper
import spock.lang.Specification

class XMLBindingSpec extends Specification {

    void 'Test simple XML binding'() {
        given:
        def binder = new SimpleDataBinder()
        def player = new Player()
        def xml = new XmlSlurper().parseText('''
  <player>
    <name>Lemmy</name>
    <band>
      <name>Motorhead</name>
      <numberOfMembers>3</numberOfMembers>
    </band>
  </player>
''')

        when:
        binder.bind player, xml

        then:
        player.name == 'Lemmy'
        player.band
        player.band.name == 'Motorhead'
        player.band.numberOfMembers == 3
    }
}

class Player {
    String name
    Band band
}

class Band {
    String name
    Integer numberOfMembers

    void setNumberOfMembers(Integer x) {
        numberOfMembers = x
    }
}
