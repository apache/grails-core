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
package grails.gorm.services.transform

import grails.gorm.multitenancy.TenantService
import grails.gorm.services.Service
import grails.gorm.transactions.ReadOnly
import grails.gorm.transactions.TransactionService
import grails.gorm.transactions.Transactional
import grails.gorm.annotation.Entity
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.grails.datastore.gorm.GormEntity
import org.grails.datastore.gorm.services.Implemented
import org.grails.datastore.gorm.services.implementers.FindAllImplementer
import org.grails.datastore.gorm.services.implementers.FindOneInterfaceProjectionImplementer
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.services.DefaultServiceRegistry
import org.grails.datastore.mapping.services.ServiceRegistry
import spock.lang.Specification

/**
 * Tests for the @Service transformation
 */
class ServiceTransformSpec extends Specification {

    void setup() {
        def entities = [XProj, ArticleMarker, XTenant, FooProt, FooGen, FooQ, FooP, FooL, FooD, FooA, FooJ, BarJ, FooJ2, BarJ2, FooS, FooProj, FooU, FooI, FooT, FooV, FooW, FooAbs, FooInterface, ServiceEntity]
        new org.grails.datastore.mapping.simple.SimpleMapDatastore(entities as Class[])
    }

    def cleanup() {
        org.grails.datastore.gorm.GormRegistry.reset()
    }

    void "test interface projection with an entity that implements GormEntity"() {
        given:
        Class service = XServiceProj

        expect:
        def impl = service.classLoader.loadClass("${service.package.name}.\$${service.simpleName}Implementation")
        impl != null
        impl.getMethod("getX", String).getAnnotation(Implemented).by() == FindOneInterfaceProjectionImplementer
    }

    void "test interface projection with an entity that implements a marker interface"() {
        given:
        Class service = ArticleServiceMarker

        expect:
        def impl = service.classLoader.loadClass("${service.package.name}.\$${service.simpleName}Implementation")
        impl != null
        impl.getMethod("getArticle", String).getAnnotation(Implemented).by() == FindOneInterfaceProjectionImplementer
    }

    void "test service transformation with @CurrentTenant"() {
        given:
        Class service = XServiceTenant

        expect:
        def impl = service.classLoader.loadClass("${service.package.name}.\$${service.simpleName}Implementation")
        impl != null
        impl.getAnnotation(grails.gorm.multitenancy.CurrentTenant) != null
    }

    void "test service transform on abstract protected methods"() {
        given:
        Class service = AbstractMyServiceProt

        expect:
        !service.isInterface()

        when:"the impl is obtained"
        Class impl = service.classLoader.loadClass("${service.package.name}.\$${service.simpleName}Implementation")

        then:"The impl is valid"
        impl.getMethod("searchFoo", Serializable).getAnnotation(ReadOnly) == null
        impl.getDeclaredMethod("find", Serializable).getAnnotation(ReadOnly) == null
        impl.getDeclaredMethod("find", Serializable).getAnnotation(grails.gorm.transactions.NotTransactional) != null
    }

    void "test service transform with generics"() {
        given:
        Class service = MyServiceGen

        expect:
        service.isInterface()

        when:"the impl is obtained"
        Class impl = service.classLoader.loadClass("${service.package.name}.\$${service.simpleName}Implementation")

        then:"The impl is valid"
        impl.getMethod("findByTitleLike", String).getAnnotation(ReadOnly) != null
    }

    void "test interface projection with @Query"() {
        given:
        Class service = MyServiceQ

        expect:
        service.isInterface()

        when:"the impl is obtained"
        Class impl = service.classLoader.loadClass("${service.package.name}.\$${service.simpleName}Implementation")

        then:"The impl is valid"
        impl.getMethod("search", String).getAnnotation(ReadOnly) != null
    }

    void "test interface projection"() {
        given:
        Class service = MyServiceP

        expect:
        service.isInterface()

        when:"the impl is obtained"
        Class impl = service.classLoader.loadClass("${service.package.name}.\$${service.simpleName}Implementation")

        then:"The impl is valid"
        impl.getMethod("find", String).getAnnotation(ReadOnly) != null
    }

    void "test interface projection that returns a list"() {
        given:
        Class service = MyServiceL

        expect:
        service.isInterface()

        when:"the impl is obtained"
        Class impl = service.classLoader.loadClass("${service.package.name}.\$${service.simpleName}Implementation")

        then:"The impl is valid"
        impl.getMethod("find", String).getAnnotation(ReadOnly) != null
    }

    void "test interface projection with dynamic finder"() {
        given:
        Class service = MyServiceD

        expect:
        service.isInterface()

        when:"the impl is obtained"
        Class impl = service.classLoader.loadClass("${service.package.name}.\$${service.simpleName}Implementation")

        then:"The impl is valid"
        impl.getMethod("findByTitle", String).getAnnotation(ReadOnly) != null
    }

    void "test findAll with generics"() {
        given:
        Class service = MyServiceA

        expect:
        service.isInterface()

        when:"the impl is obtained"
        Class impl = service.classLoader.loadClass("${service.package.name}.\$${service.simpleName}Implementation")

        then:"The impl is valid"
        impl.getMethod("listFoos").getAnnotation(ReadOnly) != null
        impl.getMethod("listFoos").getAnnotation(Implemented).by() == FindAllImplementer
    }

    void "test @Join on finder"() {
        given:
        Class serviceClass = MyJoinServiceJ

        expect:
        serviceClass.isInterface()

        when:"the impl is obtained"
        Class impl = serviceClass.classLoader.loadClass("${serviceClass.package.name}.\$${serviceClass.simpleName}Implementation")

        then:"The impl is valid"
        impl.getMethod("find", String).getAnnotation(ReadOnly) != null

        when:"the second impl is obtained"
        Class serviceClass2 = MyJoinServiceJ2
        Class impl2 = serviceClass2.classLoader.loadClass("${serviceClass2.package.name}.\$${serviceClass2.simpleName}Implementation")

        then:"The second impl is valid"
        impl2.getMethod("findFoo", String).getAnnotation(ReadOnly) != null
    }

    void "test @Query invalid property"() {
        when:"The service transform is applied to an interface it can't implement"
        new GroovyClassLoader().parseClass('''
import grails.gorm.services.*
import grails.gorm.annotation.Entity

@Service(FooInv)
interface MyServiceInv {
    @Query('from FooInv as f where f.title like $wrong') 
    Integer searchByTitle(String pattern)
}
@Entity
class FooInv {
    String title
}
''')

        then:"A compilation error occurred"
        def e = thrown(MultipleCompilationErrorsException)
        e.message.normalize().contains "Invalid property [wrong] of domain class [FooInv] in query."
    }

    void "test @Query invalid domain"() {
        when:"The service transform is applied to an interface it can't implement"
        new GroovyClassLoader().parseClass('''
import grails.gorm.services.*
import grails.gorm.annotation.Entity

@Service(FooInvD)
interface MyServiceInvD {

    @Query('from java.lang.String as f where f.title like $pattern') 
    Integer searchByTitle(String pattern)
}
@Entity
class FooInvD {
    String title
}
''')

        then:"A compilation error occurred"
        def e = thrown(MultipleCompilationErrorsException)
        e.message.normalize().contains "Invalid query class [java.lang.String]. Referenced classes in queries must be domain classes"
    }

    void "test simple @Query annotation"() {
        given:
        Class service = MyServiceS

        expect:
        service.isInterface()

        when:"the impl is obtained"
        Class impl = service.classLoader.loadClass("${service.package.name}.\$${service.simpleName}Implementation")

        then:"The impl is valid"
        org.grails.datastore.mapping.services.Service.isAssignableFrom(impl)
    }

    void "test @Query annotation with projection"() {
        given:
        Class service = MyServiceProj

        expect:
        !service.isInterface()

        when:"the impl is obtained"
        Class impl = service.classLoader.loadClass("${service.package.name}.\$${service.simpleName}Implementation")

        then:"The impl is valid"
        org.grails.datastore.mapping.services.Service.isAssignableFrom(impl)
    }


    void "test @Query update annotation"() {
        given:
        Class service = MyServiceU

        expect:
        service.isInterface()

        when:"the impl is obtained"
        Class impl = service.classLoader.loadClass("${service.package.name}.\$${service.simpleName}Implementation")

        then:"The impl is valid"
        impl.getMethod("updateTitle", String, String).getAnnotation(Transactional) != null
    }

    void "test @Query update annotation using id attribute"() {
        given:
        Class service = MyServiceI

        expect:
        service.isInterface()

        when:"the impl is obtained"
        Class impl = service.classLoader.loadClass("${service.package.name}.\$${service.simpleName}Implementation")

        then:"The impl is valid"
        impl.getMethod("updateTitle", String, Long).getAnnotation(Transactional) != null
    }


    void "test @Query update annotation with default transaction attributes at class level"() {
        given:
        Class service = MyServiceT

        expect:
        service.isInterface()

        when:"the impl is obtained"
        Class impl = service.classLoader.loadClass("${service.package.name}.\$${service.simpleName}Implementation")
        def instance = impl.newInstance()

        then:"The impl is valid"
        impl.getAnnotation(Transactional).value() == "foo"
        org.grails.datastore.mapping.services.Service.isAssignableFrom(impl)

        when:
        instance.kill("blah")

        then:
        thrown(RuntimeException)
    }

    void "test @Query annotation with declared variables"() {
        given:
        Class service = MyServiceV

        expect:
        service.isInterface()

        when:"the impl is obtained"
        Class impl = service.classLoader.loadClass("${service.package.name}.\$${service.simpleName}Implementation")

        then:"The impl is valid"
        org.grails.datastore.mapping.services.Service.isAssignableFrom(impl)
    }


    void "test @Query invalid variable property"() {
        when:"The service transform is applied to an interface it can't implement"
        new GroovyClassLoader().parseClass('''
import grails.gorm.services.*
import grails.gorm.annotation.Entity

@Service(FooInvV)
interface MyServiceInvV {

    @Query('select f.wrong from ${FooInvV f} where f.title like $pattern') 
    Integer searchByTitle(String pattern)
}
@Entity
class FooInvV {
    String title
}
''')

        then:"A compilation error occurred"
        def e = thrown(MultipleCompilationErrorsException)
        e.message.normalize().contains "Invalid property [wrong] of domain class [FooInvV] in query."
    }

    void "test @Where annotation"() {
        given:
        Class service = MyServiceW

        expect:
        service.isInterface()

        when:"the impl is obtained"
        Class impl = service.classLoader.loadClass("${service.package.name}.\$${service.simpleName}Implementation")

        then:"The impl is valid"
        org.grails.datastore.mapping.services.Service.isAssignableFrom(impl)
    }

    void "test implement abstract class"() {
        given:
        Class service = AbstractMyServiceAbs

        expect:
        !service.isInterface()

        when:"the impl is obtained"
        Class impl = service.classLoader.loadClass("${service.package.name}.\$${service.simpleName}Implementation")

        then:"The impl is valid"
        impl.getMethod("deleteMoreFoos", String).getAnnotation(Transactional) != null
        impl.getMethod("delete", Serializable).getAnnotation(Transactional) != null
        impl.getMethod("deleteFoos", String).getAnnotation(Transactional) != null
        impl.getMethod("listFoos").getAnnotation(ReadOnly) != null
        impl.getMethod("listMoreFoos").getAnnotation(ReadOnly) != null
        impl.getMethod("listEvenMoreFoos").getAnnotation(ReadOnly) != null
        impl.getMethod("findByTitle", String).getAnnotation(ReadOnly) != null
        impl.getMethod("findByTitleLike", String).getAnnotation(ReadOnly) != null
        impl.getMethod("saveFoo", String).getAnnotation(Transactional) != null


        when:"the implementation is instantiated"
        def inst = impl.newInstance()

        then:"the results are valid"
        inst != null

        when:"a method is called that requires a datastore"
        org.grails.datastore.gorm.GormRegistry.reset()
        inst.saveFoo("test")

        then:"an exception is thrown if no datastore is present"
        def e = thrown(IllegalStateException)
        e.message.contains 'No GORM implementation configured'
    }

    void "test implement interface"() {
        given:
        Class service = MyServiceInterfaceOnly

        expect:
        service.isInterface()

        when:"the impl is obtained"
        Class impl = service.classLoader.loadClass("${service.package.name}.\$${service.simpleName}Implementation")

        then:"The impl is valid"
        impl.getMethod("deleteMoreFoos", String).getAnnotation(Transactional) != null
        impl.getMethod("delete", Serializable).getAnnotation(Transactional) != null
        impl.getMethod("deleteFoos", String).getAnnotation(Transactional) != null
        impl.getMethod("listFoos").getAnnotation(ReadOnly) != null
        impl.getMethod("listMoreFoos").getAnnotation(ReadOnly) != null
        impl.getMethod("listEvenMoreFoos").getAnnotation(ReadOnly) != null
        impl.getMethod("findByTitle", String).getAnnotation(ReadOnly) != null
        impl.getMethod("findByTitleLike", String).getAnnotation(ReadOnly) != null
        impl.getMethod("saveFoo", String).getAnnotation(Transactional) != null


        when:"the implementation is instantiated"
        def inst = impl.newInstance()

        then:"the results are valid"
        inst != null

        when:"a method is called that requires a datastore"
        org.grails.datastore.gorm.GormRegistry.reset()
        inst.saveFoo("test")

        then:"an exception is thrown if no datastore is present"
        def e = thrown(IllegalStateException)
        e.message.contains 'No GORM implementation configured'
    }

    void "test service transform applied to interface that can't be implemented"() {
        when:"The service transform is applied to an interface it can't implement"
        new GroovyClassLoader().parseClass('''
import grails.gorm.services.*
import grails.gorm.annotation.Entity

@Service(FooCant)
interface MyServiceCant {
    void doStuff(String pattern)
}
@Entity
class FooCant {
    String title
}
''')

        then:"A compilation error occurred"
        def e = thrown(MultipleCompilationErrorsException)
        e.message.normalize().contains "No implementations possible for method 'void doStuff(java.lang.String)'"
    }

    void "test service transform applied with a dynamic finder for a non-existent property"() {
        when:"The service transform is applied to an interface it can't implement"
        new GroovyClassLoader().parseClass('''
import grails.gorm.services.*
import grails.gorm.annotation.Entity

@Service(FooNone)
interface MyServiceNone {
    FooNone findByNonsense(String pattern)
}
@Entity
class FooNone {
    String title
}
''')

        then:"A compilation error occurred"
        def e = thrown(MultipleCompilationErrorsException)
        e.message.normalize().contains "Cannot implement finder for non-existent property [nonsense] of class [FooNone]"
    }

    void "test service transform applied with a dynamic finder for a property of the wrong type"() {
        when:"The service transform is applied to an interface it can't implement"
        new GroovyClassLoader().parseClass('''
import grails.gorm.services.*
import grails.gorm.annotation.Entity

@Service(FooWrong)
interface MyServiceWrong {
    FooWrong findByTitle(Integer pattern)
}
@Entity
class FooWrong {
    String title
}
''')

        then:"A compilation error occurred"
        def e = thrown(MultipleCompilationErrorsException)
        e.message.normalize().contains "Cannot implement dynamic finder [findByTitle] for domain class [FooWrong]. The property [title] has type [java.lang.String] which is not compatible with the argument type [java.lang.Integer]."
    }

    void "test service transform"() {
        given:
        def TestService = TestServiceBase
        def TestService2 = TestServiceBase2
        def datastore = new org.grails.datastore.mapping.simple.SimpleMapDatastore(ServiceEntity)
        ServiceRegistry reg = new DefaultServiceRegistry(datastore, false)
        reg.initialize()

        expect:
        org.grails.datastore.mapping.services.Service.isAssignableFrom(TestService)
        reg.getService(TestService) != null
        reg.getService(TestService2) != null
        reg.getService(TestService).datastore != null
        reg.getService(TransactionService) != null
        reg.getService(TenantService) != null
    }
}
