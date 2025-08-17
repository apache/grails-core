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

package org.grails.forge.features.scaffolding

import org.grails.forge.utils.CommandSpec

class ScaffoldingSpec extends CommandSpec {

    void 'test generate-controller command'() {

        given:
        generateProjectWithDefaults()

        when:
        def domainPkg = new File(dir, 'grails-app/domain/example/grails')
        domainPkg.mkdirs()
        new File(domainPkg, 'Bird.groovy').text = '''
           package example.grails
           
           class Bird {
               String name
           }
        '''
        String output = executeGradle('runCommand', '-Pargs=generate-controller example.grails.Bird').output

        then:
        output ==~ /(?s).*Rendered template Controller\.groovy to destination grails-app[\/\\]controllers[\/\\]example[\/\\]grails[\/\\]BirdController\.groovy(?s).*/
        output ==~ /(?s).*Rendered template Service\.groovy to destination grails-app[\/\\]services[\/\\]example[\/\\]grails[\/\\]BirdService\.groovy(?s).*/
        output ==~ /(?s).*Rendered template Spec\.groovy to destination src[\/\\]test[\/\\]groovy[\/\\]example[\/\\]grails[\/\\]BirdControllerSpec\.groovy(?s).*/
        output ==~ /(?s).*Rendered template ServiceSpec\.groovy to destination src[\/\\]test[\/\\]groovy[\/\\]example[\/\\]grails[\/\\]BirdServiceSpec\.groovy(?s).*/
        new File(dir, 'grails-app/controllers/example/grails/BirdController.groovy').exists()
        new File(dir, 'grails-app/services/example/grails/BirdService.groovy').exists()
        new File(dir, 'src/test/groovy/example/grails/BirdControllerSpec.groovy').exists()
        new File(dir, 'src/test/groovy/example/grails/BirdControllerSpec.groovy').exists()
    }

    @Override
    String getTempDirectoryPrefix() {
        'testapp'
    }
}
