/*
 * Copyright 2015 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.plugins.databasemigration.liquibase

import grails.config.ConfigMap
import grails.io.IOUtils
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import liquibase.changelog.ChangeLogParameters
import liquibase.exception.ChangeLogParseException
import liquibase.parser.core.ParsedNode
import liquibase.parser.core.xml.AbstractChangeLogParser
import liquibase.resource.ResourceAccessor
import org.codehaus.groovy.control.CompilerConfiguration
import org.springframework.context.ApplicationContext

import static org.grails.plugins.databasemigration.PluginConstants.DATA_SOURCE_NAME_KEY

@CompileStatic
class GroovyChangeLogParser extends AbstractChangeLogParser {

    final int priority = PRIORITY_DEFAULT

    ApplicationContext applicationContext

    ConfigMap config

    @Override
    @CompileDynamic
    protected ParsedNode parseToNode(String physicalChangeLogLocation, ChangeLogParameters changeLogParameters, ResourceAccessor resourceAccessor) throws ChangeLogParseException {
        def inputStream = null
        def changeLogText = null
        try {
            inputStream = resourceAccessor.openStreams(null, physicalChangeLogLocation).first()
            changeLogText = inputStream?.text
        } finally {
            IOUtils.closeQuietly(inputStream)
        }

        CompilerConfiguration compilerConfiguration = new CompilerConfiguration(CompilerConfiguration.DEFAULT)
        if (compilerConfiguration.metaClass.respondsTo(compilerConfiguration, 'setDisabledGlobalASTTransformations')) {
            Set disabled = compilerConfiguration.disabledGlobalASTTransformations ?: []
            disabled << 'org.grails.datastore.gorm.query.transform.GlobalDetachedCriteriaASTTransformation'
            compilerConfiguration.disabledGlobalASTTransformations = disabled
        }

        def changeLogProperties = config.getProperty('changelogProperties', Map) ?: [:]

        try {
            GroovyClassLoader classLoader = new GroovyClassLoader(Thread.currentThread().contextClassLoader, compilerConfiguration, false)
            Script script = new GroovyShell(classLoader, new Binding(changeLogProperties), compilerConfiguration).parse(changeLogText as String)
            script.run()

            setChangeLogProperties(changeLogProperties, changeLogParameters)

            Closure databaseChangeLogBlock = script.getProperty('databaseChangeLog') as Closure

            DatabaseChangeLogBuilder builder = new DatabaseChangeLogBuilder()
            builder.dataSourceName = changeLogParameters.getValue(DATA_SOURCE_NAME_KEY, null)
            builder.applicationContext = applicationContext
            builder.databaseChangeLog(databaseChangeLogBlock) as ParsedNode
        } catch (Exception e) {
            throw new ChangeLogParseException(e)
        }
    }

    @Override
    boolean supports(String changeLogFile, ResourceAccessor resourceAccessor) {
        changeLogFile.endsWith('.groovy')
    }

    @CompileDynamic
    protected void setChangeLogProperties(Map changeLogProperties, ChangeLogParameters changeLogParameters) {
        changeLogProperties.each { name, value ->
            String contexts = null
            String labels = null
            String databases = null
            if (value instanceof Map) {
                contexts = value.contexts
                labels = value.labels
                databases = value.databases
                value = value.value
            }
            changeLogParameters.set(name as String, value as String, contexts as String, labels, databases, true, null)
        }
    }
}
