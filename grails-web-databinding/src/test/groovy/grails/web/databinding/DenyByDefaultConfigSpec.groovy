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
package grails.web.databinding

import grails.config.Config
import grails.config.Settings
import grails.databinding.SimpleMapDataBindingSource
import grails.util.Holders
import org.grails.config.PropertySourcesConfig
import spock.lang.Specification
import spock.lang.Unroll

class DenyByDefaultConfigSpec extends Specification {

    private Config originalConfig
    private Object originalApplication

    void setup() {
        originalConfig = Holders.config
        originalApplication = Holders.findApplication()
        Holders.grailsApplication = null
        Holders.setConfig(null)
    }

    void cleanup() {
        DataBindingUtils.clearBindingCaches()
        GrailsWebDataBinder.resetWarnedBindingShapes()
        Holders.setConfig(originalConfig)
        Holders.grailsApplication = originalApplication
    }

    @Unroll
    void 'flat deny-by-default configuration value #configuredValue controls secure binding'() {
        given:
        if (configuredValue != null) {
            Holders.setConfig(new PropertySourcesConfig([(Settings.DATABINDING_DENY_BY_DEFAULT): configuredValue]))
        } else {
            Holders.setConfig(new PropertySourcesConfig([:]))
        }
        def target = new DenyByDefaultTarget()

        when:
        new GrailsWebDataBinder(Holders.findApplication()).bind(
                target, new SimpleMapDataBindingSource([allowed: 'allowed', compatibilityProperty: 'compatibility']))

        then:
        target.allowed == 'allowed'
        target.compatibilityProperty == expectedCompatibilityValue

        where:
        configuredValue || expectedCompatibilityValue
        null            || 'compatibility'
        false           || 'compatibility'
        'false'         || 'compatibility'
        'FALSE'         || 'compatibility'
        ' false '       || 'compatibility'
        true            || null
        'true'          || null
    }

    @Unroll
    void 'unrecognised configuration value #configuredValue fails closed through the public binding API'() {
        given:
        Holders.setConfig(new PropertySourcesConfig([(Settings.DATABINDING_DENY_BY_DEFAULT): configuredValue]))
        def target = new DenyByDefaultTarget()

        when:
        DataBindingUtils.bindObjectToDomainInstance(
                null, target, [allowed: 'allowed', compatibilityProperty: 'compatibility'], null, null, null)

        then:
        target.allowed == 'allowed'
        target.compatibilityProperty == null

        where:
        configuredValue << ['flase', '', 'off', 'no', 'yes', 'on', 0, 1]
    }

}

class DenyByDefaultTarget {
    public static final List $defaultDatabindingWhiteList = ['allowed']
    public static final List $legacyDatabindingWhiteList = ['allowed', 'compatibilityProperty']

    String allowed
    String compatibilityProperty
}
