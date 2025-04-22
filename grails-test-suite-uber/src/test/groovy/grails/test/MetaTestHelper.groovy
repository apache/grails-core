/* Copyright 2008 the original author or authors.
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
package grails.test

class MetaTestHelper {

    List<Class> classesUnderTest
    List<Class> classesToReset

    private Map<Class, MetaClass> classToMetaClass = [:]

    void setUp() {
        backupMeta()
    }

    void tearDown() {
        restoreMeta()
        resetMeta()
    }

    void backupMeta() {
        classToMetaClass.clear()
        classesUnderTest?.each { Class clazz ->
            classToMetaClass.put clazz, clazz.metaClass
        }
    }

    void restoreMeta() {
        classToMetaClass?.each { Class clazz, MetaClass metaClass ->
            GroovySystem.metaClassRegistry.setMetaClass(clazz, metaClass)
        }
    }

    void resetMeta() {
        classesToReset?.each { Class clazz ->
            GroovySystem.metaClassRegistry.removeMetaClass clazz
        }
    }
}
