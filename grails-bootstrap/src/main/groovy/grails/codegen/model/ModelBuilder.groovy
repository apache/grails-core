/*
 * Copyright 2014 original authors
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
package grails.codegen.model

import grails.util.GrailsNameUtils
import groovy.transform.CompileStatic
import org.apache.groovy.util.BeanUtils
import org.grails.io.support.FileSystemResource
import org.grails.io.support.GrailsResourceUtils
import org.grails.io.support.Resource

/**
 * Used to build a Model for the purposes of codegen
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
trait ModelBuilder {

    String defaultPackage

    /**
     * A model for the given class name
     * @param className The class name
     *
     * @return The {@link Model} instance
     */
    Model model(Class cls) {
        return new ModelImpl(cls.getName())
    }
    /**
     * A model for the given class name
     * @param className The class name
     *
     * @return The {@link Model} instance
     */
    Model model(String className) {
        if(defaultPackage && !className.contains('.')) {
            return new ModelImpl("${defaultPackage}.$className")
        }
        else {
            return new ModelImpl(className)
        }
    }

    /**
     * A model for the given class name
     * @param className The class name
     *
     * @return The {@link Model} instance
     */
    Model model(File file) {
        model(new FileSystemResource(file))
    }

    /**
     * A model for the given class name
     * @param className The class name
     *
     * @return The {@link Model} instance
     */
    Model model(Resource resource) {
        def className = GrailsResourceUtils.getClassName(resource)
        model(className)
    }

    @CompileStatic
    private static class ModelImpl implements Model {
        final String className
        final String fullName
        final String propertyName
        final String packageName
        final String simpleName
        final String lowerCaseName
        final String packagePath

        ModelImpl(String className) {
            this.className = BeanUtils.capitalize(GrailsNameUtils.getShortName(className))
            this.fullName = className
            this.propertyName = GrailsNameUtils.getPropertyName(className)
            this.packageName = GrailsNameUtils.getPackageName(className)
            this.packagePath = packageName.replace('.' as char, File.separatorChar)
            this.simpleName = this.className
            this.lowerCaseName = GrailsNameUtils.getScriptName(className)

        }

        @Override
        String getModelName() {
            propertyName
        }

        @Override
        String convention(String conventionName) {
            "${simpleName}${conventionName}"
        }

        @Override
        Map<String, Object> asMap() {
            [className: className,
             fullName: fullName,
             propertyName: propertyName,
             modelName: propertyName,
             packageName: packageName,
             packagePath: packagePath,
             simpleName: simpleName,
             lowerCaseName: lowerCaseName] as Map<String, Object>
        }
    }

}
