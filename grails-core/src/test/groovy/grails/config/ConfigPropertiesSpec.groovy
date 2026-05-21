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

package grails.config

import org.grails.config.PropertySourcesConfig
import spock.lang.Specification

/**
 * Created by graemerocher on 25/11/15.
 */
class ConfigPropertiesSpec extends Specification {

    void "Test config properties"() {
        when:"a config object"
        def config = new PropertySourcesConfig('foo.bar':'foo', 'foo.two': 2)
        def props = new ConfigProperties(config)
        then:
        props.getProperty('foo.bar') == 'foo'
        props.get('foo.bar') == 'foo'
        props.getProperty('foo.two') == '2'
        props.get('foo.two') == '2'
        props.get('foo') == null
        props.getProperty('foo') == null
        props.propertyNames().hasMoreElements()
        props.propertyNames().toList() == ['foo.bar','foo','foo.two']
    }
}
