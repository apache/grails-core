/*
 * Copyright (c) 2011 the original author or authors.
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

package grails.plugins.quartz

import grails.core.ArtefactHandlerAdapter
import org.codehaus.groovy.ast.ClassNode
import org.grails.compiler.injection.GrailsASTUtils
import org.quartz.JobExecutionContext
import org.springframework.util.ReflectionUtils

import java.lang.reflect.Method
import java.util.regex.Pattern

import static org.grails.io.support.GrailsResourceUtils.GRAILS_APP_DIR
import static org.grails.io.support.GrailsResourceUtils.REGEX_FILE_SEPARATOR

/**
 * Grails artifact handler for job classes.
 *
 * @author Marc Palmer (marc@anyware.co.uk)
 * @author Sergey Nebolsin (nebolsin@gmail.com)
 * @since 0.1
 */
class JobArtefactHandler extends ArtefactHandlerAdapter {

    static final String TYPE = 'Job'
    static final Pattern JOB_PATH_PATTERN = Pattern.compile('.+' + REGEX_FILE_SEPARATOR + GRAILS_APP_DIR + REGEX_FILE_SEPARATOR + 'jobs' + REGEX_FILE_SEPARATOR + /(.+)\.(groovy)/)

    JobArtefactHandler() {
        super(TYPE, GrailsJobClass, DefaultGrailsJobClass, TYPE)
    }

    boolean isArtefact(ClassNode classNode) {
        if (classNode == null ||
           !isValidArtefactClassNode(classNode, classNode.getModifiers()) ||
           !classNode.getName().endsWith(DefaultGrailsJobClass.JOB) ||
           !classNode.getMethods(GrailsJobClassConstants.EXECUTE)) {
            return false
        }

        URL url = GrailsASTUtils.getSourceUrl(classNode)

        url &&  JOB_PATH_PATTERN.matcher(url.getFile()).find()
    }

    boolean isArtefactClass(Class clazz) {
        // class shouldn't be null and should ends with Job suffix
        if (clazz == null || !clazz.getName().endsWith(DefaultGrailsJobClass.JOB)) return false
        // and should have one of execute() or execute(JobExecutionContext) methods defined
        Method method = ReflectionUtils.findMethod(clazz, GrailsJobClassConstants.EXECUTE)
        if (method == null) {
            // we're using Object as a param here to allow groovy-style 'def execute(param)' method
            method = ReflectionUtils.findMethod(clazz, GrailsJobClassConstants.EXECUTE, [Object] as Class[])
        }
        if (method == null) {
            // also check for the execution context as a variable because that's what's being passed
            method = ReflectionUtils.findMethod(clazz, GrailsJobClassConstants.EXECUTE, [JobExecutionContext] as Class[])
        }
        method != null
    }
}
