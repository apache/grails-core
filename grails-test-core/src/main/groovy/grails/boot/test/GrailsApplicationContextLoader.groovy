package grails.boot.test

import grails.boot.GrailsApp
import org.springframework.boot.SpringApplication
import org.springframework.boot.test.context.SpringBootContextLoader

/*
 * Copyright 2014 original authors
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

/**
 * Extends Spring Boot's {@link SpringBootContextLoader} with a version for Grails
 *
 * @author Graeme Rocher
 * @since 3.0
 */
class GrailsApplicationContextLoader extends SpringBootContextLoader {

    @Override
    protected SpringApplication getSpringApplication() {
        return new GrailsApp()
    }
}
