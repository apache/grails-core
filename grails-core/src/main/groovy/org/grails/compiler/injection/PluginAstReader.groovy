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
package org.grails.compiler.injection

import java.util.concurrent.ConcurrentHashMap

import groovy.transform.CompileStatic
import groovy.transform.stc.POJO
import org.codehaus.groovy.ast.ClassCodeVisitorSupport
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.PropertyNode
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.ast.expr.MapEntryExpression
import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.control.CompilationFailedException
import org.codehaus.groovy.control.SourceUnit

import grails.plugins.GrailsPluginInfo
import grails.util.GrailsNameUtils
import org.grails.io.support.Resource

/**
 * Reads plugin info from the AST
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
class PluginAstReader {

    private BasicGrailsPluginInfo pluginInfo

    PluginAstReader() {
        this.pluginInfo = new BasicGrailsPluginInfo()
    }

    GrailsPluginInfo getPluginInfo() {
        return this.pluginInfo
    }

    GrailsPluginInfo readPluginInfo(ClassNode classNode) throws CompilationFailedException {
        String className = classNode.getNameWithoutPackage()

        if (className.endsWith('GrailsPlugin')) {
            visitContents(className, classNode)
        }
        return pluginInfo
    }

    protected void visitContents(String className, final ClassNode classNode) {
        ClassCodeVisitorSupport visitor = new ClassCodeVisitorSupport() {

            @Override
            void visitProperty(PropertyNode node) {
                String name = node.getName()

                final Expression expr = node.getField().getInitialExpression()

                if (expr != null) {
                    Object value = null
                    if (expr instanceof ListExpression) {
                        final List<String> list = new ArrayList<>()
                        for (Expression i in ((ListExpression) expr).getExpressions()) {
                            list.add(i.getText())
                        }
                        value = list
                    }
                    else if (expr instanceof MapExpression) {
                        final Map<String, String> map = new LinkedHashMap<>()
                        value = map
                        for (MapEntryExpression mee in ((MapExpression) expr).getMapEntryExpressions()) {
                            map.put(mee.getKeyExpression().getText(), mee.getValueExpression().getText())
                        }
                    }
                    else {
                        if (expr instanceof ConstantExpression) {
                            value = expr.getText()
                        }
                    }
                    if (value != null) {
                        pluginInfo.setProperty(name, value)
                        super.visitProperty(node)
                    }
                }
            }

            @Override
            protected SourceUnit getSourceUnit() {
                return classNode.getModule().getContext()
            }

        }

        classNode.visitContents(visitor)

        pluginInfo.setName(GrailsNameUtils.getPluginName(className + '.groovy'))
    }

    /**
     * Simple Javabean implementation of the GrailsPluginInfo interface.
     *
     * @author Graeme Rocher
     * @since 1.3
     */
    @POJO
    @CompileStatic
    class BasicGrailsPluginInfo implements GrailsPluginInfo {

        private String name
        private String version
        private Map<String, Object> attributes = new ConcurrentHashMap<>()

        String getName() {
            return name
        }

        void setName(String name) {
            this.name = name
        }

        String getVersion() {
            return version
        }

        void setVersion(String version) {
            this.version = version
        }

        void setProperty(String property, Object newValue) {
            attributes.put(property, newValue)
        }

        Object getProperty(String property) {
            return attributes.get(property)
        }

        String getFullName() {
            return name + '-' + version
        }

        Resource getDescriptor() {
            return null
        }

        Resource getPluginDir() {
            return null
        }

        @SuppressWarnings([ 'unchecked', 'rawtypes' ])
        Map getProperties() {
            Map props = new HashMap()
            props.putAll(attributes)
            props.put(NAME, name)
            props.put(VERSION, version)
            return props
        }

    }

}
