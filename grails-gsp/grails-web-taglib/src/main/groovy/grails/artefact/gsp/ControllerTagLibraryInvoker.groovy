/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package grails.artefact.gsp

import groovy.transform.CompileStatic

import org.grails.taglib.TagLibraryLookup
import org.grails.taglib.TagOutput
import org.grails.taglib.encoder.OutputContextLookupHelper

/**
 * The {@link TagLibraryInvoker} flavor injected into controllers. On top of the dynamic
 * tag dispatch inherited from {@link TagLibraryInvoker}, it declares a statically-resolvable
 * {@code message(Map)} — by far the most common tag invoked as a method from controllers, e.g.
 * {@code flash.message = message(code: 'default.created.message', args: [...])} — so the call
 * resolves under {@code @CompileStatic} / {@code @GrailsCompileStatic} instead of requiring
 * dynamic dispatch through {@code methodMissing}.
 *
 * <p>This method deliberately lives here rather than on {@link TagLibraryInvoker} itself:
 * tag libraries also implement that trait (via {@code grails.artefact.TagLibrary}), and a real
 * {@code message} method inherited by every tag library would be picked up by the tag-method
 * dispatch as if the library declared a {@code message} tag, recursing infinitely for libraries
 * that don't.</p>
 *
 * @since 8.0
 */
@CompileStatic
trait ControllerTagLibraryInvoker extends TagLibraryInvoker {

    /**
     * Statically-resolvable counterpart of the dynamic {@code message(...)} tag dispatch.
     * Dispatches through the same tag-library lookup (declared namespace first, then the
     * default namespace) and the same {@link TagOutput#captureTagOutput} machinery as the
     * dynamic path, so behavior is identical.
     *
     * @param attrs the tag attributes ({@code code}, {@code args}, {@code default}, {@code error},
     *              {@code message}, {@code locale}, {@code encodeAs})
     * @return the resolved message
     */
    Object message(Map attrs) {
        TagLibraryLookup lookup = getTagLibraryLookup()
        if (lookup) {
            String namespace = getTaglibNamespace()
            if (lookup.lookupTagLibrary(namespace, 'message') == null) {
                namespace = TagOutput.DEFAULT_NAMESPACE
            }
            if (lookup.lookupTagLibrary(namespace, 'message') != null) {
                return TagOutput.captureTagOutput(lookup, namespace, 'message', attrs, null,
                        OutputContextLookupHelper.lookupOutputContext())
            }
        }
        throw new MissingMethodException('message', this.getClass(), [attrs] as Object[])
    }
}
