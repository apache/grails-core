package functionaltests

import org.springframework.beans.factory.annotation.Value

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

/**
 * @author graemerocher
 */
class ConfigTestController {

    @Value('${foo.baz}')
    int fooBaz

    @Value('${foo.bar}')
    String fooBar

    def index() {
        def config = grailsApplication.config
        [
                fooBazFromValue: fooBaz,
                fooBarFromValue: fooBar,
                fooBarFromConfigQualified: config.getProperty('foo.bar'),
                fooBazFromConfigQualified: config.getProperty('foo.baz', Integer),
                fooBarFromConfig: config.foo.bar,
                fooBazFromConfig: config.foo.baz,
                combinedConfig: config.getProperty('foo', Map)
        ]
    }
}
