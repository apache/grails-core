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
package org.grails.cli.profile

import grails.util.BuildSettings
import groovy.transform.CompileStatic
import org.grails.io.support.FileSystemResource
import org.grails.io.support.Resource



/**
 * Simple disk based implementation of the {@link Profile} interface
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class FileSystemProfile extends ResourceProfile {

    FileSystemProfile(ProfileRepository repository, File profileDir) {
        super(repository, profileDir.name, new FileSystemResource(profileDir))
    }

}
