/*
 * Copyright 2015 original authors
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

package grails.boot.config.tools

import groovy.transform.CompileStatic


/**
 * Used to interpret the Gradle settings.gradle file
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
abstract class SettingsFile extends Script {


    void include(String[] projectPaths) {
        binding.setVariable("projectPaths", projectPaths)
    }

    void includeFlat(String[] projectPaths) {
        binding.setVariable("projectPaths", projectPaths)
    }

    def methodMissing(String name, args) {
        // ignore
    }

    def propertyMissing(String name) {
        // ignore
    }
}
