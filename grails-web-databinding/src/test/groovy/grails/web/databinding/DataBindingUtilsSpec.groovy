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

import spock.lang.Specification

import org.springframework.context.ApplicationContext
import org.springframework.context.support.StaticApplicationContext

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.databinding.CollectionDataBindingSource
import grails.databinding.DataBinder
import grails.databinding.SimpleMapDataBindingSource
import grails.util.Holders
import grails.web.mime.MimeTypeResolver
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.proxy.ProxyFactory
import org.grails.web.databinding.BindingIncludeLists
import org.grails.web.databinding.bindingsource.DataBindingSourceRegistry
import org.grails.web.databinding.bindingsource.DefaultDataBindingSourceRegistry

class DataBindingUtilsSpec extends Specification {

    private GrailsApplication previousApplication

    void setup() {
        previousApplication = Holders.findApplication()
    }

    void cleanup() {
        Holders.setGrailsApplication(previousApplication)
    }

    void 'test binding a class which does not declare a data binding whitelist'() {
        given:
        def command = new NoWhitelistCommand()

        when:
        def bindingResult = DataBindingUtils.bindObjectToInstance(command, [name: 'Grails', version: '8'])

        then: 'the absence of a whitelist places no restriction on the binding'
        bindingResult == null
        command.name == 'Grails'
        command.version == '8'

        when: 'the same class is bound again'
        def secondCommand = new NoWhitelistCommand()
        DataBindingUtils.bindObjectToInstance(secondCommand, [name: 'Apache Grails', version: '8.0'])

        then:
        secondCommand.name == 'Apache Grails'
        secondCommand.version == '8.0'
    }

    void 'test the whitelist field is read only once per class'() {
        when: 'a class whose whitelist field does not hold a list is bound'
        def command = new MutableWhitelistCommand()
        DataBindingUtils.bindObjectToInstance(command, [name: 'Grails', version: '8'])

        then: 'there is no usable include list, so every property is bound'
        command.name == 'Grails'
        command.version == '8'

        when: 'the whitelist field is given a usable value and another instance is bound'
        MutableWhitelistCommand.$defaultDatabindingWhiteList = ['name']
        def secondCommand = new MutableWhitelistCommand()
        DataBindingUtils.bindObjectToInstance(secondCommand, [name: 'Grails', version: '8'])

        then: 'the cached include list is used, the field is not read a second time'
        secondCommand.name == 'Grails'
        secondCommand.version == '8'
    }

    void 'test a declared whitelist restricts the bound properties'() {
        given:
        def command = new WhitelistedCommand()

        when:
        DataBindingUtils.bindObjectToInstance(command, [name: 'Grails', version: '8'])

        then:
        command.name == 'Grails'
        command.version == null
    }

    void 'test a superclass whitelist does not restrict an unenhanced subclass in compatibility mode'() {
        given:
        def command = new SubclassOfWhitelistedCommand()

        when:
        DataBindingUtils.bindObjectToInstance(command, [name: 'Grails', version: '8'])

        then: 'only a whitelist declared on the bound class describes its eligible properties'
        command.name == 'Grails'
        command.version == '8'
    }

    void 'test a proxy is bound with the whitelist of the class it stands for'() {
        given: 'an application whose datastore generates a proxy as a subclass of the class it stands for'
        def mappingContext = new KeyValueMappingContext('test')
        mappingContext.proxyFactory = Stub(ProxyFactory) {
            isProxy(_ as ProxyOfWhitelistedCommand) >> true
            getProxiedClass(_ as ProxyOfWhitelistedCommand) >> WhitelistedCommand
        }
        def application = new DefaultGrailsApplication()
        application.mappingContext = mappingContext
        Holders.setGrailsApplication(application)

        and:
        def proxy = new ProxyOfWhitelistedCommand()
        def subclass = new SubclassOfWhitelistedCommand()

        when:
        DataBindingUtils.bindObjectToInstance(proxy, [name: 'Grails', version: '8'])
        DataBindingUtils.bindObjectToInstance(subclass, [name: 'Grails', version: '8'])

        then: 'the proxy binds as the class it stands for does'
        proxy.name == 'Grails'
        proxy.version == null

        and: 'a subclass that is not a proxy still binds the properties its superclass whitelist leaves out'
        subclass.name == 'Grails'
        subclass.version == '8'
    }

    void 'test binding before GORM has initialized the mapping context'() {
        given: 'an application whose mapping context is only a placeholder until GORM initializes'
        Holders.setGrailsApplication(new DefaultGrailsApplication())
        def command = new WhitelistedCommand()

        when:
        def bindingResult = DataBindingUtils.bindObjectToInstance(command, [name: 'Grails', version: '8'])

        then:
        bindingResult == null
        command.name == 'Grails'
        command.version == null
    }

    void 'test the include list of a type is the one its instances are bound with'() {
        expect:
        BindingIncludeLists.propertyNames(WhitelistedCommand) == ['name']
        BindingIncludeLists.propertyNames(SubclassOfWhitelistedCommand) == null

        and: 'a type that declares none is not restricted'
        BindingIncludeLists.propertyNames(NoWhitelistCommand) == null

        and: 'it is read from the class, so a type without a no-argument constructor has one too'
        BindingIncludeLists.propertyNames(UncreatableCommand) == ['name']
    }

    void 'test the include list of a type is read without creating an instance of it'() {
        given:
        CountedCommand.created = 0

        when:
        def names = BindingIncludeLists.propertyNames(CountedCommand)

        then:
        names == ['name']
        CountedCommand.created == 0
    }

    void 'test binding a collection'() {
        given:
        def collectionBindingSource = Stub(CollectionDataBindingSource) {
            getDataBindingSources() >> [
                    new SimpleMapDataBindingSource([name: 'Grails', version: '8']),
                    new SimpleMapDataBindingSource([name: 'Groovy', version: '5'])
            ]
        }
        def commands = []

        when:
        DataBindingUtils.bindToCollection(NoWhitelistCommand, commands, collectionBindingSource)

        then:
        commands.size() == 2
        commands[0].name == 'Grails'
        commands[0].version == '8'
        commands[1].name == 'Groovy'
        commands[1].version == '5'
    }

    void 'test the beans of the current application context are used after the context is replaced'() {
        given:
        def firstRegistry = new DefaultDataBindingSourceRegistry()
        def firstResolver = Stub(MimeTypeResolver)
        def firstContext = createContext([
                (DataBindingSourceRegistry.BEAN_NAME): firstRegistry,
                (MimeTypeResolver.BEAN_NAME): firstResolver
        ])

        and:
        def secondRegistry = new DefaultDataBindingSourceRegistry()
        def secondResolver = Stub(MimeTypeResolver)
        def secondContext = createContext([
                (DataBindingSourceRegistry.BEAN_NAME): secondRegistry,
                (MimeTypeResolver.BEAN_NAME): secondResolver
        ])

        and:
        ApplicationContext mainContext = firstContext
        def application = Stub(GrailsApplication) {
            getMainContext() >> { mainContext }
        }

        expect: 'the beans of the first context are used'
        DataBindingUtils.getDataBindingSourceRegistry(application).is(firstRegistry)
        DataBindingUtils.getDataBindingSourceRegistry(application).is(firstRegistry)
        DataBindingUtils.getMimeTypeResolver(application).is(firstResolver)

        when: 'the application context is replaced'
        mainContext = secondContext

        then: 'the beans of the new context are used'
        DataBindingUtils.getDataBindingSourceRegistry(application).is(secondRegistry)
        DataBindingUtils.getMimeTypeResolver(application).is(secondResolver)

        when: 'the original context is used again'
        mainContext = firstContext

        then:
        DataBindingUtils.getDataBindingSourceRegistry(application).is(firstRegistry)
        DataBindingUtils.getMimeTypeResolver(application).is(firstResolver)

        cleanup:
        firstContext.close()
        secondContext.close()
    }

    void 'test a bean registered after a lookup found nothing is still picked up'() {
        given: 'a context which does not hold the resolver yet'
        def context = createContext([:])
        def application = Stub(GrailsApplication) {
            getMainContext() >> context
        }

        expect: 'the lookup reports that there is no resolver'
        DataBindingUtils.getMimeTypeResolver(application) == null

        when: 'the bean is registered into that same context afterwards'
        def resolver = Stub(MimeTypeResolver)
        context.beanFactory.registerSingleton(MimeTypeResolver.BEAN_NAME, resolver)

        then: 'the next lookup finds it, rather than returning a remembered miss for the life of the context'
        DataBindingUtils.getMimeTypeResolver(application).is(resolver)

        and: 'and it is then held, so the context is not searched again'
        DataBindingUtils.getMimeTypeResolver(application).is(resolver)

        cleanup:
        context.close()
    }

    void 'test the data binder of the current application context is used after the context is replaced'() {
        given:
        def firstBinder = Mock(DataBinder)
        def firstContext = createContext([(DataBindingUtils.DATA_BINDER_BEAN_NAME): firstBinder])
        def secondBinder = Mock(DataBinder)
        def secondContext = createContext([(DataBindingUtils.DATA_BINDER_BEAN_NAME): secondBinder])

        and:
        ApplicationContext mainContext = firstContext
        def application = Stub(GrailsApplication) {
            getMainContext() >> { mainContext }
            getMappingContext() >> Stub(MappingContext)
        }
        Holders.setGrailsApplication(application)

        when:
        DataBindingUtils.bindObjectToInstance(new NoWhitelistCommand(), [name: 'Grails'])

        then:
        1 * firstBinder.bind(_, _, _, _, _)
        0 * secondBinder.bind(_, _, _, _, _)

        when: 'the application context is replaced'
        mainContext = secondContext
        DataBindingUtils.bindObjectToInstance(new NoWhitelistCommand(), [name: 'Grails'])

        then: 'the binder of the new context is used'
        1 * secondBinder.bind(_, _, _, _, _)
        0 * firstBinder.bind(_, _, _, _, _)

        cleanup:
        firstContext.close()
        secondContext.close()
    }

    private static StaticApplicationContext createContext(Map<String, Object> beans) {
        def context = new StaticApplicationContext()
        context.refresh()
        beans.each { String beanName, Object bean ->
            context.beanFactory.registerSingleton(beanName, bean)
        }
        return context
    }
}

class NoWhitelistCommand {

    String name
    String version
}

class MutableWhitelistCommand {

    public static Object $defaultDatabindingWhiteList = 'not a list'

    String name
    String version
}

class WhitelistedCommand {

    public static final List $defaultDatabindingWhiteList = ['name']

    String name
    String version
}

class SubclassOfWhitelistedCommand extends WhitelistedCommand {
}

// Stands for the subclass a datastore generates at runtime to proxy an instance of the class it extends.
class ProxyOfWhitelistedCommand extends WhitelistedCommand {
}

class CountedCommand {

    public static final List $defaultDatabindingWhiteList = ['name']

    static int created

    String name

    CountedCommand() {
        created++
    }
}

class UncreatableCommand {

    public static final List $defaultDatabindingWhiteList = ['name']

    String name

    UncreatableCommand(String name) {
        this.name = name
    }
}
