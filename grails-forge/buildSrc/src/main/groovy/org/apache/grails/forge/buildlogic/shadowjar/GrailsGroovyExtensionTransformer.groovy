/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.grails.forge.buildlogic.shadowjar

import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer
import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext
import groovy.transform.CompileStatic
import org.apache.grails.gradle.common.PropertyFileUtils
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement

/**
 * Based on com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer but with
 * reproducible property file support
 */
@CompileStatic
class GrailsGroovyExtensionTransformer implements Transformer {

    private static final String GROOVY_EXTENSION_MODULE_DESCRIPTOR_PATH = 'META-INF/groovy/org.codehaus.groovy.runtime.ExtensionModule'

    private static final String MODULE_NAME_KEY = 'moduleName'
    private static final String MODULE_VERSION_KEY = 'moduleVersion'
    private static final String EXTENSION_CLASSES_KEY = 'extensionClasses'
    private static final String STATIC_EXTENSION_CLASSES_KEY = 'staticExtensionClasses'

    private static final String MERGED_MODULE_NAME = 'MergedByGrailsShadowJar'
    private static final String MERGED_MODULE_VERSION = '1.0.0'

    private final Properties module = new Properties()

    @Override
    boolean canTransformResource(FileTreeElement element) {
        def path = element.relativePath.pathString
        path == GROOVY_EXTENSION_MODULE_DESCRIPTOR_PATH
    }

    @Override
    void transform(TransformerContext context) {
        def props = new Properties()
        props.load(context.is)
        props.entrySet().each { Map.Entry<Object, Object> entry ->
            String key = entry.key as String
            String value = entry.value as String
            switch (key) {
                case MODULE_NAME_KEY:
                    handle(key, value) {
                        module.setProperty(key, MERGED_MODULE_NAME)
                    }
                    break
                case MODULE_VERSION_KEY:
                    handle(key, value) {
                        module.setProperty(key, MERGED_MODULE_VERSION)
                    }
                    break
                case [EXTENSION_CLASSES_KEY, STATIC_EXTENSION_CLASSES_KEY]:
                    handle(key, value) { String existingValue ->
                        def newValue = "${existingValue},${value}"
                        module.setProperty(key, newValue)
                    }
                    break
            }
        }
    }

    private handle(String key, String value, Closure mergeValue) {
        def existingValue = module.getProperty(key)
        if (existingValue) {
            mergeValue(existingValue)
        } else {
            module.setProperty(key, value)
        }
    }

    @Override
    boolean hasTransformedResource() {
        return module.size() > 0
    }

    @Override
    void modifyOutputStream(ZipOutputStream os, boolean preserveFileTimestamps) {
        ZipEntry entry = new ZipEntry(GROOVY_EXTENSION_MODULE_DESCRIPTOR_PATH)
        entry.time = TransformerContext.getEntryTimestamp(preserveFileTimestamps, entry.time)
        os.putNextEntry(entry)

        os.write()
        try (InputStream inputStream = toInputStream(module)) {
            inputStream.transferTo(os)
        }
        os.closeEntry()
    }

    private static InputStream toInputStream(Properties props) {
        def baos = new ByteArrayOutputStream()
        props.store(baos, null)
        return PropertyFileUtils.makePropertiesOutputReproducible(baos)
    }
}
