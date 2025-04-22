/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.grails.compiler.injection

import groovy.transform.CompileStatic
import org.codehaus.groovy.control.SourceUnit
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext
import org.springframework.expression.spel.support.StandardTypeLocator

@CompileStatic
class GroovyEclipseCompilationHelper {

    /**
     * Attempts to resolve the compilation directory when using Eclipse
     *
     * @param sourceUnit The source unit
     * @return The File that represents the root directory or null
     */
    static File resolveEclipseCompilationTargetDirectory(SourceUnit sourceUnit) {

        if (sourceUnit.getClass().name == 'org.codehaus.jdt.groovy.control.EclipseSourceUnit') {
            StandardEvaluationContext context = new StandardEvaluationContext()
            context.setTypeLocator(new StandardTypeLocator(sourceUnit.getClass().getClassLoader()))
            context.setRootObject(sourceUnit)
            try {
				// Honour the targetDirectory within the source configuration directory.
				File targetDirectory = sourceUnit.configuration.targetDirectory
				
				if (targetDirectory == null) {
					
					// Resolve as before.
					targetDirectory = ((File) new SpelExpressionParser().parseExpression("eclipseFile.project.getFolder(T(org.eclipse.jdt.core.JavaCore).create(eclipseFile.project).outputLocation).rawLocation.makeAbsolute().toFile().absoluteFile").getValue(context))
					
				} else if (!targetDirectory.isAbsolute()) {
					// Target directory is set and is not absolute.
					// We should assume that this is a path relative to the current eclipse project,
					// and needs resolving appropriately.
					targetDirectory = ((File) new SpelExpressionParser().parseExpression("eclipseFile.project.getFolder('${targetDirectory.path}').rawLocation.makeAbsolute().toFile().absoluteFile").getValue(context))
					
				}
				// Else absolute file location. We should return as-is.
				return targetDirectory
            } catch (Throwable e) {
                // Not running Eclipse IDE, probably using the Eclipse compiler with Maven
                return null
            }
        }
        return null
    }
}