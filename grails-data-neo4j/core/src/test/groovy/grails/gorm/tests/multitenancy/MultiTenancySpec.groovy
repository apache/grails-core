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

package grails.gorm.tests.multitenancy

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.gorm.multitenancy.Tenants
import grails.neo4j.Neo4jEntity
import grails.neo4j.Relationship
import org.grails.datastore.gorm.neo4j.Neo4jDatastore
import org.grails.datastore.gorm.neo4j.config.Settings
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.multitenancy.AllTenantsResolver
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.springframework.dao.DataIntegrityViolationException
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 19/07/2016.
 */
class MultiTenancySpec extends Specification {

    @Shared @AutoCleanup Neo4jDatastore datastore

    void setupSpec() {
        def tempDir = File.createTempDir()
        tempDir.deleteOnExit()
        // No explicit URL: the embedded server then binds a random free port, so this spec cannot
        // collide with the embedded server of another spec running in a parallel test fork.
        Map config = [
                "grails.gorm.multiTenancy.mode"               :"DISCRIMINATOR",
                "grails.gorm.multiTenancy.tenantResolverClass": MyResolver,
                "grails.neo4j.options.encryptionLevel"        :"NONE",
                (Settings.SETTING_NEO4J_TYPE)                 : "embedded",
                (Settings.SETTING_NEO4J_LOCATION)             : tempDir
        ]
        this.datastore = new Neo4jDatastore(config, getDomainClasses() as Class[])
    }

    void setup() {
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "")
    }

    void cleanup() {
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "")
    }

    void "Test persist and retrieve entities with multi tenancy"() {
        when:"A tenant id is present"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "test1")

        then:"the correct tenant is used"
        CompanyC.count() == 0

        when:"An object is saved"
        CompanyC.withTransaction {
            def f = new CompanyC(name: "Foo").save(flush:true)
            def b = new CompanyC(name: "Bar").save(flush:true)
            new BusinessPartner(from: f, to: b, value: 1000.0f).save(flush:true)
        }


        then:"The results are correct"
        CompanyC.count() == 2
        BusinessPartner.count() == 1

        when:"A cypher query is executed without the tenant id"
        CompanyC.withNewSession {
            CompanyC.find("MATCH (p:CompanyC) WHERE p.name=\$name RETURN p", [name:"Foo"])
        }

        then:"An exception is thrown"
        thrown(TenantNotFoundException)

        when:"A cypher query is executed without the tenant id"
        CompanyC result= CompanyC.withNewSession {
            CompanyC.find("MATCH (p:CompanyC) WHERE p.name=\$name AND p.parent=\$tenantId RETURN p", [name:"Foo"])
        }

        then:"The entity is found"
        result != null


        when:"The tenant id is switched"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "test2")

        then:"the correct tenant is used"
        CompanyC.count() == 0
        CompanyC.withTenant("test1") { Serializable tenantId, Session s ->
            assert tenantId
            assert s
            CompanyC.count() == 2
        }

        when:"each tenant is iterated over"
        Map tenantIds = [:]
        CompanyC.eachTenant { String tenantId ->
            tenantIds.put(tenantId, CompanyC.count())
        }

        then:"The result is correct"
        tenantIds == [test1:2, test2:0]
    }

    void "Test lookups by id are restricted to the current tenant"() {
        given:"an instance of another tenant and one of the current tenant"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "test1")
        Serializable otherId = CompanyC.withTransaction { new CompanyC(name: "Other").save(flush: true).id }
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "test2")
        Serializable ownId = CompanyC.withTransaction { new CompanyC(name: "Own").save(flush: true).id }

        expect:"get, read, exists and getAll find the instance of the current tenant only"
        CompanyC.withNewSession { CompanyC.get(ownId)?.name } == "Own"
        CompanyC.withNewSession { CompanyC.read(ownId)?.name } == "Own"
        CompanyC.withNewSession { CompanyC.exists(ownId) }
        CompanyC.withNewSession { CompanyC.get(otherId) } == null
        CompanyC.withNewSession { CompanyC.read(otherId) } == null
        !CompanyC.withNewSession { CompanyC.exists(otherId) }
        CompanyC.withNewSession { CompanyC.getAll(otherId, ownId)*.name } == [null, "Own"]

        and:"load initializes a proxy for the instance of the current tenant"
        CompanyC.withNewSession { CompanyC.load(ownId).name } == "Own"

        and:"inside withoutId a lookup by id is not restricted to a tenant"
        Tenants.withoutId(datastore) { CompanyC.getAll(otherId, ownId)*.name } == ["Other", "Own"]

        when:"a proxy for an instance of another tenant is used"
        CompanyC.withNewSession { CompanyC.load(otherId).name }

        then:"it fails to initialize like one for an instance that does not exist"
        thrown(DataIntegrityViolationException)

        when:"an instance is looked up by id without a tenant"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "")
        CompanyC.withNewSession { CompanyC.get(ownId) }

        then:"an exception is thrown"
        thrown(TenantNotFoundException)
    }

    List getDomainClasses() {
        [ CompanyC, BusinessPartner]
    }

    static class MyResolver extends SystemPropertyTenantResolver implements AllTenantsResolver {
        @Override
        Iterable<Serializable> resolveTenantIds() {
            ['test1','test2']
        }
    }

}
@Entity
class CompanyC implements Neo4jEntity<CompanyC>, MultiTenant {
    String name
    String parent

    static mapping = {
        tenantId name:'parent'
    }

}

@Entity
class BusinessPartner implements Relationship<CompanyC, CompanyC>, MultiTenant<BusinessPartner> {
    Float value = 0.0
    String parent

    static mapping = {
        tenantId name:'parent'
    }

}