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

import groovy.transform.CompileStatic
import liquibase.parser.core.ParsedNode
import org.codehaus.groovy.runtime.InvokerHelper
import org.grails.plugins.databasemigration.DatabaseMigrationException
import org.springframework.context.ApplicationContext

import static org.grails.plugins.databasemigration.PluginConstants.DATA_SOURCE_NAME_KEY

@CompileStatic
class DatabaseChangeLogBuilder extends BuilderSupport {

    ApplicationContext applicationContext

    String dataSourceName

    @Override
    protected void setParent(Object parent, Object child) {
    }

    @Override
    protected Object createNode(Object name) {
        def node = new ParsedNode(null, (String) name)
        if (name == 'grailsChange' || name == 'grailsPrecondition') {
            node.addChild(null, 'applicationContext', applicationContext)
            node.addChild(null, DATA_SOURCE_NAME_KEY, dataSourceName)
        }
        if (currentNode) {
            currentNode.addChild(node)
        }
        node
    }

    @Override
    protected Object createNode(Object name, Object value) {
        def node = new ParsedNode(null, (String) name)
        node.value = value
        if (currentNode) {
            currentNode.addChild(node)
        }
        node
    }

    @Override
    protected Object createNode(Object name, Map attributes) {
        def node = new ParsedNode(null, (String) name)
        attributes.each { Object key, Object value ->
            node.addChild(null, (String) key, value)
        }
        currentNode.addChild(node)
        node
    }

    @Override
    protected Object createNode(Object name, Map attributes, Object value) {
        def node = new ParsedNode(null, (String) name)
        attributes.each { Object key, Object attrValue ->
            node.addChild(null, (String) key, attrValue)
        }
        node.value = value
        currentNode.addChild(node)
        node
    }

    private ParsedNode getCurrentNode() {
        (ParsedNode) current
    }

    @Override
    Object invokeMethod(String methodName, Object args) {
        if (currentNode?.name == 'grailsChange') {
            processGrailsChangeProperty(methodName, args)
            return null
        } else if (currentNode?.name == 'grailsPrecondition') {
            processGrailsPreconditionProperty(methodName, args)
            return null
        } else {
            return super.invokeMethod(methodName, args)
        }
    }

    protected void processGrailsChangeProperty(String methodName, Object args) {
        def name = methodName.toLowerCase()
        def arg = InvokerHelper.asList(args)[0]
        if (name == 'init' && arg instanceof Closure) {
            currentNode.addChild(null, 'init', arg)
        } else if (name == 'validate' && arg instanceof Closure) {
            currentNode.addChild(null, 'validate', arg)
        } else if (name == 'change' && arg instanceof Closure) {
            currentNode.addChild(null, 'change', arg)
        } else if (name == 'rollback' && arg instanceof Closure) {
            currentNode.addChild(null, 'rollback', arg)
        } else if (name == 'confirm' && arg instanceof CharSequence) {
            currentNode.addChild(null, 'confirm', arg)
        } else if (name == 'checksum' && arg instanceof CharSequence) {
            currentNode.addChild(null, 'checksum', arg)
        } else {
            throw new DatabaseMigrationException("Unknown method name: ${methodName}")
        }
    }

    protected boolean processGrailsPreconditionProperty(String methodName, args) {
        def name = methodName.toLowerCase()
        def arg = InvokerHelper.asList(args)[0]
        if (name == 'check' && arg instanceof Closure) {
            currentNode.addChild(null, 'check', arg)
        } else {
            throw new DatabaseMigrationException("Unknown method name: ${methodName}")
        }
    }
}
