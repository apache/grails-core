/*
 * Copyright 2025 original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.plugin.geb

import org.openqa.selenium.remote.LocalFileDetector
import org.openqa.selenium.remote.UselessFileDetector
import groovy.transform.CompileStatic

/**
 * Detects files on the local disk.
 * 
 * @author Jonas Pammer
 * @since 4.2
 */
@CompileStatic
class DefaultContainerFileDetector extends LocalFileDetector implements ContainerFileDetector {
}

/**
 * A file detector that never finds anything.
 * 
 * @author Jonas Pammer
 * @since 4.2
 */
@CompileStatic
class UselessContainerFileDetector extends UselessFileDetector implements ContainerFileDetector {
}

/**
 * Used by {@link ContainerGebConfiguration#fileDetector()} interface to represent a null value.
 */
class NullContainerFileDetector extends DefaultContainerFileDetector {}