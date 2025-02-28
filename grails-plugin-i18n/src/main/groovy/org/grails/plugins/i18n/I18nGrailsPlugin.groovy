/*
 * Copyright 2004-2005 the original author or authors.
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
package org.grails.plugins.i18n

import grails.plugins.Plugin
import grails.util.BuildSettings
import grails.util.GrailsUtil
import groovy.util.logging.Slf4j
import org.springframework.context.support.ReloadableResourceBundleMessageSource
import org.springframework.core.io.Resource

import java.nio.file.Files

/**
 * Configures Grails' internationalisation support.
 *
 * @author Graeme Rocher
 * @since 0.4
 */
@Slf4j
class I18nGrailsPlugin extends Plugin {

    String baseDir = "grails-app/i18n"
    String version = GrailsUtil.getGrailsVersion()
    String watchedResources = "file:./${baseDir}/**/*.properties".toString()

    @Override
    void onChange(Map<String, Object> event) {
        def ctx = applicationContext
        def application = grailsApplication
        if (!ctx) {
            log.debug("Application context not found. Can't reload")
            return
        }

        boolean nativeascii = application.config.getProperty('grails.enable.native2ascii', Boolean, true)
        def resourcesDir = BuildSettings.RESOURCES_DIR
        def classesDir = BuildSettings.CLASSES_DIR

        if (resourcesDir.exists() && event.source instanceof Resource) {
            File eventFile = event.source.file.canonicalFile
            File i18nDir = eventFile.parentFile
            if (isChildOfFile(eventFile, i18nDir)) {
                if( i18nDir.name == 'i18n' && i18nDir.parentFile.name == 'grails-app') {
                    def appDir = i18nDir.parentFile.parentFile
                    resourcesDir = new File(appDir, BuildSettings.BUILD_RESOURCES_PATH)
                    classesDir = new File(appDir, BuildSettings.BUILD_CLASSES_PATH)
                }

                if(nativeascii) {
                    // if native2ascii is enabled then read the properties and write them out again
                    // so that unicode escaping is applied
                    def properties = new Properties()
                    eventFile.withReader {
                        properties.load(it)
                    }
                    // by using an OutputStream the unicode characters will be escaped
                    new File(resourcesDir, eventFile.name).withOutputStream {
                        properties.store(it, "")
                    }
                    new File(classesDir, eventFile.name).withOutputStream {
                        properties.store(it, "")
                    }
                }
                else {
                    // otherwise just copy the file as is
                    Files.copy( eventFile.toPath(),new File(resourcesDir, eventFile.name).toPath() )
                    Files.copy( eventFile.toPath(),new File(classesDir, eventFile.name).toPath() )
                }

            }
        }

        def messageSource = ctx.getBean('messageSource')
        if (messageSource instanceof ReloadableResourceBundleMessageSource) {
            messageSource.clearCache()
        }
    }

    protected boolean isChildOfFile(File child, File parent) {
        def currentFile = child.canonicalFile
        while(currentFile != null) {
            if (currentFile == parent) {
                return true
            }
            currentFile = currentFile.parentFile
        }
        return false
    }

}
