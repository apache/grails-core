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
package grails.gorm.services.multitenancy.partitioned

import org.grails.datastore.mapping.config.Settings
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class MultiTenantServiceTransformSpec extends Specification {

    @Shared @AutoCleanup SimpleMapDatastore datastore = new SimpleMapDatastore(
            [(Settings.SETTING_MULTI_TENANCY_MODE)   : MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR,
             (Settings.SETTING_MULTI_TENANT_RESOLVER): new SystemPropertyTenantResolver(),
             (Settings.SETTING_DB_CREATE)            : "create-drop"
            ],
            this.getClass().getPackage()
    )

    @Shared def gcl

    void setupSpec() {
        gcl = new GroovyClassLoader()
    }

    void "test service transform applied with @WithoutTenant"() {
        when: "The service transform is applied to an interface it can't implement"
        Class service = gcl.parseClass('''

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.gorm.multitenancy.WithoutTenant
import grails.gorm.services.Service
import grails.gorm.multitenancy.CurrentTenant

@Service(Foo)
@CurrentTenant
interface IFooService {

    @WithoutTenant
    Foo saveFoo(Foo foo)

    Integer countFoos()
}

@Entity
class Foo implements MultiTenant<Foo> {
    String title
    Long tenantId
}
''')

        then: "service is an interface"
        service.isInterface()

        when: "implementation of service is generated"
        Class impl = service.classLoader.loadClass("\$IFooServiceImplementation")
        def Foo = service.classLoader.loadClass('Foo')
        def fooService = impl.newInstance()
        fooService.datastore = datastore
        def foo = Foo.newInstance(title: "test", tenantId: 11l)
        fooService.saveFoo(foo)

        then:
        thrown(IllegalStateException)

    }

}
