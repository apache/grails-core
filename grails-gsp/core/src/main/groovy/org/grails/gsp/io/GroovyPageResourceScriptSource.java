/*
 * Copyright 2011 SpringSource
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
package org.grails.gsp.io;

import org.grails.io.support.GrailsResourceUtils;
import org.springframework.core.io.Resource;
import org.springframework.scripting.support.ResourceScriptSource;

import java.io.IOException;
import java.net.URL;

public class GroovyPageResourceScriptSource extends ResourceScriptSource implements GroovyPageScriptSource {

    private String uri;
    private boolean isPublic;

    /**
     * Create a new ResourceScriptSource for the given resource.
     *
     * @param uri The URI of the resource
     * @param resource the Resource to load the script from
     */
    public GroovyPageResourceScriptSource(String uri, Resource resource) {
        super(resource);
        this.uri = uri;
        try {
            URL u = getResource().getURL();
            if (u == null) {
                isPublic = isPublicPath(uri);
            }
            else {
                isPublic = isPublicPath(u.getPath());
            }
        } catch (IOException e) {
            isPublic = isPublicPath(uri);
        }
    }

    public static boolean isPublicPath(String path) {
        return !(path.contains(GrailsResourceUtils.WEB_INF) || path.contains(GrailsResourceUtils.VIEWS_DIR_PATH));
    }

    public String getURI() {
        return uri;
    }

    /**
     * Whether the GSP is publicly accessible directly, or only usable using internal rendering
     *
     * @return true if it can be rendered publicly
     */
    public boolean isPublic() {
        return isPublic;
    }
}
