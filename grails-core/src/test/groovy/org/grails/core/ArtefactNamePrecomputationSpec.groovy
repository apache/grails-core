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
package org.grails.core

import grails.core.GrailsClass
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Tests for deterministic GrailsClass naming metadata.
 */
class ArtefactNamePrecomputationSpec extends Specification {

    private static final String BASE_PACKAGE = 'org.grails.core'

    @Unroll
    void "naming contract for #entry.label is precomputed"() {
        given:
        GrailsClass grailsClass = grailsClassFor(entry.artifactType, entry.wrapperClass)

        expect:
        grailsClass.name == entry.name
        grailsClass.shortName == entry.shortName
        grailsClass.fullName == entry.fullName
        grailsClass.packageName == entry.packageName
        grailsClass.propertyName == entry.propertyName
        grailsClass.logicalPropertyName == entry.logicalPropertyName
        grailsClass.naturalName == entry.naturalName

        where:
        entry << [
                [
                        label: 'Acronym controller',
                        artifactType: 'controller',
                        wrapperClass: HTMLController,
                        name: 'HTML',
                        shortName: 'HTMLController',
                        fullName: "${BASE_PACKAGE}.HTMLController",
                        packageName: BASE_PACKAGE,
                        propertyName: 'HTMLController',
                        logicalPropertyName: 'HTML',
                        naturalName: 'HTMLC ontroller'
                ],
                [
                        label: 'Camel mixed controller',
                        artifactType: 'controller',
                        wrapperClass: PayRollController,
                        name: 'PayRoll',
                        shortName: 'PayRollController',
                        fullName: "${BASE_PACKAGE}.PayRollController",
                        packageName: BASE_PACKAGE,
                        propertyName: 'payRollController',
                        logicalPropertyName: 'payRoll',
                        naturalName: 'Pay Roll Controller'
                ],
                [
                        label: 'Acronym service',
                        artifactType: 'service',
                        wrapperClass: JSONAPIService,
                        name: 'JSONAPI',
                        shortName: 'JSONAPIService',
                        fullName: "${BASE_PACKAGE}.JSONAPIService",
                        packageName: BASE_PACKAGE,
                        propertyName: 'JSONAPIService',
                        logicalPropertyName: 'JSONAPI',
                        naturalName: 'JSONAPIS ervice'
                ],
                [
                        label: 'Acronym domain',
                        artifactType: 'domain',
                        wrapperClass: URLDomain,
                        name: 'URLDomain',
                        shortName: 'URLDomain',
                        fullName: "${BASE_PACKAGE}.URLDomain",
                        packageName: BASE_PACKAGE,
                        propertyName: 'URLDomain',
                        logicalPropertyName: 'URLDomain',
                        naturalName: 'URLD omain'
                ],
                [
                        label: 'Url mappings artefact',
                        artifactType: 'urlMappings',
                        wrapperClass: HomeUrlMappings,
                        name: 'Home',
                        shortName: 'HomeUrlMappings',
                        fullName: "${BASE_PACKAGE}.HomeUrlMappings",
                        packageName: BASE_PACKAGE,
                        propertyName: 'homeUrlMappings',
                        logicalPropertyName: 'home',
                        naturalName: 'Home Url Mappings'
                ]
        ]
    }

    @Unroll
    void "naming accessors are stable on repeated read for #entry.label"() {
        given:
        GrailsClass grailsClass = grailsClassFor(entry.artifactType, entry.wrapperClass)

        expect:
        10.times {
            assert grailsClass.name == entry.name
            assert grailsClass.shortName == entry.shortName
            assert grailsClass.fullName == entry.fullName
            assert grailsClass.packageName == entry.packageName
            assert grailsClass.propertyName == entry.propertyName
            assert grailsClass.logicalPropertyName == entry.logicalPropertyName
            assert grailsClass.naturalName == entry.naturalName
        }

        where:
        entry << [
                [
                        label: 'Acronym controller',
                        artifactType: 'controller',
                        wrapperClass: HTMLController,
                        name: 'HTML',
                        shortName: 'HTMLController',
                        fullName: "${BASE_PACKAGE}.HTMLController",
                        packageName: BASE_PACKAGE,
                        propertyName: 'HTMLController',
                        logicalPropertyName: 'HTML',
                        naturalName: 'HTMLC ontroller'
                ],
                [
                        label: 'Acronym service',
                        artifactType: 'service',
                        wrapperClass: JSONAPIService,
                        name: 'JSONAPI',
                        shortName: 'JSONAPIService',
                        fullName: "${BASE_PACKAGE}.JSONAPIService",
                        packageName: BASE_PACKAGE,
                        propertyName: 'JSONAPIService',
                        logicalPropertyName: 'JSONAPI',
                        naturalName: 'JSONAPIS ervice'
                ]
        ]
    }

    @Unroll
    void "naming is stable across wrapper construction for #entry.label"() {
        expect:
        GrailsClass first = grailsClassFor(entry.artifactType, entry.wrapperClass)
        GrailsClass second = grailsClassFor(entry.artifactType, entry.wrapperClass)
        first.name == second.name
        first.shortName == second.shortName
        first.fullName == second.fullName
        first.packageName == second.packageName
        first.propertyName == second.propertyName
        first.logicalPropertyName == second.logicalPropertyName
        first.naturalName == second.naturalName

        where:
        entry << [
                [
                        label: 'Camel mixed controller',
                        artifactType: 'controller',
                        wrapperClass: PayRollController
                ],
                [
                        label: 'Url mappings artefact',
                        artifactType: 'urlMappings',
                        wrapperClass: HomeUrlMappings
                ],
                [
                        label: 'Acronym domain',
                        artifactType: 'domain',
                        wrapperClass: URLDomain
                ]
        ]
    }

    private GrailsClass grailsClassFor(String artifactType, Class<?> wrapperClass) {
        switch (artifactType) {
            case 'controller':
                return new DefaultGrailsControllerClass(wrapperClass)
            case 'service':
                return new DefaultGrailsServiceClass(wrapperClass)
            case 'domain':
                return new DefaultGrailsDomainClass(wrapperClass)
            case 'urlMappings':
                return new DefaultGrailsUrlMappingsClass(wrapperClass)
            default:
                throw new IllegalArgumentException("Unknown artifact type [${artifactType}]")
        }
    }
}

class HTMLController {}

class PayRollController {}

class JSONAPIService {}

class URLDomain {}

class HomeUrlMappings {}
