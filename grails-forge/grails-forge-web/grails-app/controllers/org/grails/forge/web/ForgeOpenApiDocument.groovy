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

final class ForgeOpenApiDocument {

    private ForgeOpenApiDocument() {
    }

    static Map spec() {
        [
                openapi: '3.0.3',
                info: [
                        title: 'Grails Forge API',
                        description: 'HTTP API for generating Grails applications. Parameters and response shapes for the hosted generator.',
                        version: '1.0.0'
                ],
                paths: [
                        '/': getOp('Plain-text API usage. Browsers sending Accept: text/html are redirected to the configured UI.', 'text/plain', [type: 'string'], 200),
                        '/versions': getOp('Managed dependency versions used by generated applications.', 'application/json', ref('VersionDTO')),
                        '/application-types': getOp('Available application types.', 'application/json', ref('ApplicationTypeList')),
                        '/application-types/{type}': pathOp(
                                [typeParam()],
                                'A single application type, including its features.',
                                'application/json',
                                ref('ApplicationTypeDTO'),
                                200,
                                true
                        ),
                        '/application-types/{type}/features': pathOp(
                                [typeParam()] + featureFilterParams(),
                                'Features available for an application type, filtered by generator options.',
                                'application/json',
                                ref('FeatureList'),
                                200,
                                true
                        ),
                        '/application-types/{type}/features/default': pathOp(
                                [typeParam()] + featureFilterParams(),
                                'Default features for an application type, filtered by generator options.',
                                'application/json',
                                ref('FeatureList'),
                                200,
                                true
                        ),
                        '/create/{type}/{name}': pathOp(
                                [typeParam(), nameParam()] + createQueryParams(false),
                                'Generate a named application zip.',
                                'application/zip',
                                [type: 'string', format: 'binary'],
                                201,
                                true
                        ),
                        '/{name}.zip': pathOp(
                                [nameParam()] + createQueryParams(true),
                                'Generate a zip for the default application type, or the type query parameter.',
                                'application/zip',
                                [type: 'string', format: 'binary'],
                                201,
                                true
                        ),
                        '/preview/{type}/{name}': pathOp(
                                [typeParam(), nameParam()] + previewQueryParams(),
                                'Preview generated project contents as JSON.',
                                'application/json',
                                ref('PreviewDTO'),
                                200,
                                true
                        ),
                        '/diff/{type}/{name}': pathOp(
                                [typeParam(), nameParam()] + optionsQueryParams() + [featuresParam()],
                                'Diff a generated application against the default feature set.',
                                'text/plain',
                                [type: 'string'],
                                200,
                                true
                        ),
                        '/diff/{type}/feature/{feature}': pathOp(
                                [typeParam(), stringParam('feature', 'Feature name', true, 'path')] + optionsQueryParams() + [
                                        stringParam('name', 'Project name used in the generated diff. Defaults to example.', false, 'query')
                                ],
                                'Diff a single feature.',
                                'text/plain',
                                [type: 'string'],
                                200,
                                true
                        ),
                        '/select-options': getOp('Supported option groups for the generator UI.', 'application/json', ref('SelectOptionsDTO')),
                        '/v3/api-docs': getOp('OpenAPI 3 description of this API.', 'application/json', [type: 'object'])
                ],
                components: [
                        schemas: schemas()
                ]
        ]
    }

    private static Map getOp(String summary, String contentType, Map schema, int status = 200) {
        pathOp([], summary, contentType, schema, status, false)
    }

    private static Map pathOp(List parameters, String summary, String contentType, Map schema, int status, boolean clientError) {
        Map responses = [
                (status as String): [
                        description: summary,
                        content: [
                                (contentType): [
                                        schema: schema
                                ]
                        ]
                ]
        ]
        if (clientError) {
            responses['400'] = [description: 'Invalid application type, name, feature, or option.']
        }
        Map get = [
                summary: summary,
                responses: responses
        ]
        if (parameters) {
            get.parameters = parameters
        }
        [get: get]
    }

    private static List featureFilterParams() {
        optionsQueryParams()
    }

    private static List previewQueryParams() {
        optionsQueryParams() + [featuresParam()]
    }

    private static List createQueryParams(boolean includeTypeQuery) {
        List params = optionsQueryParams() + [featuresParam(), stringParam('build', 'Build tool. Gradle is currently the only supported value.', false, 'query')]
        if (includeTypeQuery) {
            params = [stringParam('type', 'Application type id. Defaults to web.', false, 'query')] + params
        }
        params
    }

    private static List optionsQueryParams() {
        [
                stringParam('gorm', 'GORM implementation', false, 'query'),
                stringParam('servlet', 'Servlet container', false, 'query'),
                stringParam('javaVersion', 'JDK version', false, 'query'),
                stringParam('reloading', 'Development reloading option', false, 'query')
        ]
    }

    private static Map featuresParam() {
        [
                name: 'features',
                'in': 'query',
                required: false,
                description: 'Feature names. Repeat the query parameter for each feature.',
                style: 'form',
                explode: true,
                schema: [type: 'array', items: [type: 'string']]
        ]
    }

    private static Map typeParam() {
        stringParam('type', 'Application type id, for example web or plugin', true, 'path')
    }

    private static Map nameParam() {
        stringParam('name', 'Generated application name', true, 'path')
    }

    private static Map stringParam(String name, String description, boolean required, String location) {
        [
                name: name,
                'in': location,
                required: required,
                description: description,
                schema: [type: 'string']
        ]
    }

    private static Map ref(String name) {
        ['$ref': "#/components/schemas/${name}"]
    }

    private static Map objectSchema(Map properties, List required = []) {
        Map schema = [
                type: 'object',
                properties: properties
        ]
        if (required) {
            schema.required = required
        }
        schema
    }

    private static Map schemas() {
        Map links = [
                type: 'object',
                additionalProperties: objectSchema([
                        href: [type: 'string'],
                        templated: [type: 'boolean']
                ], ['href'])
        ]
        [
                VersionDTO: objectSchema([
                        versions: [
                                type: 'object',
                                additionalProperties: [type: 'string']
                        ],
                        _links: links
                ], ['versions']),
                FeatureDTO: objectSchema([
                        name: [type: 'string'],
                        title: [type: 'string'],
                        description: [type: 'string'],
                        category: [type: 'string'],
                        preview: [type: 'boolean'],
                        community: [type: 'boolean'],
                        dependentFeatures: [type: 'array', items: [type: 'string']],
                        oneOfGroup: [type: 'string'],
                        _links: links
                ], ['name']),
                ApplicationTypeDTO: objectSchema([
                        name: [type: 'string'],
                        title: [type: 'string'],
                        description: [type: 'string'],
                        value: [type: 'string'],
                        features: [type: 'array', items: ref('FeatureDTO')],
                        _links: links
                ], ['name']),
                ApplicationTypeList: objectSchema([
                        types: [type: 'array', items: ref('ApplicationTypeDTO')],
                        _links: links
                ], ['types']),
                FeatureList: objectSchema([
                        features: [type: 'array', items: ref('FeatureDTO')],
                        _links: links
                ], ['features']),
                PreviewDTO: objectSchema([
                        contents: [
                                type: 'object',
                                additionalProperties: [type: 'string']
                        ],
                        _links: links
                ], ['contents']),
                SelectOptionsDTO: objectSchema([
                        type: [type: 'object'],
                        jdkVersion: [type: 'object'],
                        lang: [type: 'object'],
                        reloading: [type: 'object'],
                        gorm: [type: 'object'],
                        servlet: [type: 'object']
                ])
        ]
    }
}
