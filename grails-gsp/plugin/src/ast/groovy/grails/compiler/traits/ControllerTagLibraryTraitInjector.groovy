/*
 * Copyright 2014 original authors
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
package grails.compiler.traits

import grails.artefact.gsp.TagLibraryInvoker
import groovy.transform.CompileStatic



/**
 * A {@link TraitInjector} that adds the ability to invoke tag libraries from a controller
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
class ControllerTagLibraryTraitInjector extends ControllerTraitInjector {

    @Override
    Class getTrait() {
        TagLibraryInvoker
    }
}
