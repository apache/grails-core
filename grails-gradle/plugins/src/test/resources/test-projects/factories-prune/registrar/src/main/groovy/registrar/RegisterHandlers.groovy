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
package registrar

import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation

/**
 * Registers each compiled class named *Handler under {@code example.Handler}, merging what the file
 * already holds and the hand-authored file, as the Grails compiler does for artefact handlers.
 */
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
class RegisterHandlers implements ASTTransformation {

    void visit(ASTNode[] nodes, SourceUnit source) {
        File targetDirectory = source.configuration.targetDirectory
        for (ClassNode classNode : source.AST.classes) {
            if (!classNode.nameWithoutPackage.endsWith('Handler')) {
                continue
            }
            File factoriesFile = new File(targetDirectory, 'META-INF/grails.factories')
            Set<String> names = new TreeSet<>()
            [factoriesFile, new File(System.getProperty('base.dir'), 'src/main/resources/META-INF/grails.factories')].each { File file ->
                if (file.isFile()) {
                    Properties properties = new Properties()
                    file.withInputStream { properties.load(it) }
                    names.addAll((properties.getProperty('example.Handler') ?: '').tokenize(','))
                }
            }
            names.add(classNode.name)
            factoriesFile.parentFile.mkdirs()
            factoriesFile.text = "example.Handler=${names.join(',')}\n"
        }
    }
}
