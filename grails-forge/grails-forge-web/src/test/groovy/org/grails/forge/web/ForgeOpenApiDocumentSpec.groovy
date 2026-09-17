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
package org.grails.forge.web

import spock.lang.Specification

class ForgeOpenApiDocumentSpec extends Specification {

    void "spec lists the generator operations and their parameters"() {
        when:
        Map spec = ForgeOpenApiDocument.spec()
        List createParams = spec.paths['/create/{type}/{name}'].get.parameters
        List zipParams = spec.paths['/{name}.zip'].get.parameters
        List featureDiffParams = spec.paths['/diff/{type}/feature/{feature}'].get.parameters
        List featureListParams = spec.paths['/application-types/{type}/features'].get.parameters

        then:
        spec.openapi == '3.0.3'
        createParams.any { it.name == 'type' && it['in'] == 'path' }
        createParams.any { it.name == 'features' && it['in'] == 'query' && it.schema.type == 'array' && it.explode }
        createParams.any { it.name == 'build' && it['in'] == 'query' }
        !createParams.any { it.name == 'test' }
        zipParams.any { it.name == 'type' && it['in'] == 'query' }
        zipParams.any { it.name == 'features' && it['in'] == 'query' }
        featureDiffParams.any { it.name == 'feature' && it['in'] == 'path' }
        featureDiffParams.any { it.name == 'reloading' && it['in'] == 'query' }
        !featureDiffParams.any { it.name == 'features' }
        !featureDiffParams.any { it.name == 'build' }
        featureListParams.any { it.name == 'gorm' && it['in'] == 'query' }
        !featureListParams.any { it.name == 'features' }
        spec.paths['/versions'].get.responses['200'].content['application/json'].schema['$ref'] == '#/components/schemas/VersionDTO'
        spec.components.schemas.VersionDTO.properties.versions
        spec.components.schemas.PreviewDTO.properties.contents
        spec.components.schemas.FeatureDTO.properties.name
    }
}
