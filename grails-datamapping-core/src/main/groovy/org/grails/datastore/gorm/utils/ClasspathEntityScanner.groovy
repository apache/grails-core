/*
 * Copyright 2016-2024 original authors
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
package org.grails.datastore.gorm.utils

import grails.gorm.annotation.Entity
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.grails.datastore.mapping.reflect.ClassUtils
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.type.filter.AnnotationTypeFilter

import java.lang.annotation.Annotation

/**
 * Utility class for scanning the classpath for entities
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
@Slf4j
class ClasspathEntityScanner {

    /**
     * The annotations to scan
     */
    List<Class<? extends Annotation>> annotations = [Entity, jakarta.persistence.Entity]
    /**
     * The classloader to use
     */
    ClassLoader classLoader = getClass().getClassLoader()

    /**
     * Packages that won't be scanned for performance reasons
     * javax still exists for select packages
     */
    List<String> ignoredPackages = ['com', 'net', '', 'org', 'java', 'javax', 'jakarta', 'groovy']

    ClasspathEntityScanner() {
        if(ClassUtils.isPresent("grails.persistence.Entity")) {
            try {
                annotations.add((Class<? extends Annotation>)Class.forName("grails.persistence.Entity") )
            } catch (Throwable e) {
                log.error("Annotation [grails.persistence.Entity] found on classpath, but could not be loaded: ${e.message}", e)
            }
        }
    }
    /**
     * Scans the classpath for entities for the given packages
     *
     * @param packages The packages
     * @return The entities
     */
    Class[] scan(Package... packages) {
        ClassPathScanningCandidateComponentProvider componentProvider = new ClassPathScanningCandidateComponentProvider(false)
        componentProvider.setMetadataReaderFactory(new AnnotationMetadataReaderFactory(classLoader))
        for(ann in annotations) {
            componentProvider.addIncludeFilter(new AnnotationTypeFilter(ann))
        }
        Collection<Class> classes = new HashSet<>()
        for(Package p in packages) {
            def packageName = p.name
            if(ignoredPackages.contains(packageName)) {
                log.error("Package [$packageName] will not be scanned as it is too generic and will slow down startup time. Use a more specific package")
            }
            else {
                for (BeanDefinition candidate in componentProvider.findCandidateComponents(packageName)) {
                    Class persistentEntity = Class.forName(candidate.beanClassName, false, classLoader )
                    classes.add persistentEntity
                }
            }
        }
        return classes as Class[]
    }
}
