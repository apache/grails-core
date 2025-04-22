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
package org.grails.cli.interactive.completers

import grails.util.BuildSettings
import groovy.transform.CompileStatic
import org.grails.io.support.Resource



/**
 * A completer that completes the names of the tests in the project
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
class TestsCompleter  extends ClassNameCompleter {
    TestsCompleter() {
        super(new File(BuildSettings.BASE_DIR, "src/test/groovy"), new File(BuildSettings.BASE_DIR, "src/integration-test/groovy"))
    }

    @Override
    boolean isValidResource(Resource resource) {
        def fn = resource.filename
        fn.endsWith('Spec.groovy') || fn.endsWith('Tests.groovy')
    }
}
