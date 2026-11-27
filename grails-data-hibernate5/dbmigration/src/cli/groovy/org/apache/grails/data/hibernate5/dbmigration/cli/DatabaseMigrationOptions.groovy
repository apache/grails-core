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
package org.apache.grails.data.hibernate5.dbmigration.cli

import groovy.transform.AnnotationCollector

import org.apache.grails.core.cli.ToolOption

/**
 * The options every tool of the Database Migration plugin takes, which
 * {@link ApplicationContextDatabaseMigrationCommand} reads before the tool runs: {@code --dataSource},
 * {@code --defaultSchema} and {@code --contexts}. A tool of the plugin puts this annotation on its class,
 * beside the options and parameters of its own, so that {@code help}, completion and agents know them.
 *
 * @since 9.0
 */
@ToolOption(names = ['--dataSource'], arity = '1',
        description = 'The data source to work with, by its name under dataSources in the configuration, as reports; the default data source when not given')
@ToolOption(names = ['--defaultSchema'], arity = '1', label = 'SCHEMA',
        description = 'The schema of the database to work in where the change log names none')
@ToolOption(names = ['--contexts'], arity = '1',
        description = 'The contexts of the change sets to take, separated by commas; those of the configuration when not given')
@AnnotationCollector
@interface DatabaseMigrationOptions {}
