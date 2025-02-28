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
package org.grails.testing.context.junit4;/*
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

import grails.boot.test.GrailsApplicationContextLoader;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;

import java.lang.annotation.*;

/**
 * @author Graeme Rocher
 * @since 3.0
 */
@ContextConfiguration(loader = GrailsApplicationContextLoader.class)
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface GrailsTestConfiguration {

    /**
     * @see ContextConfiguration#locations()
     */
    String[] locations() default {};

    /**
     * @see ContextConfiguration#classes()
     */
    Class<?>[] classes() default {};

    /**
     * @see ContextConfiguration#initializers()
     */
    Class<? extends ApplicationContextInitializer<? extends ConfigurableApplicationContext>>[] initializers() default {};

    /**
     * @see ContextConfiguration#inheritLocations()
     */
    boolean inheritLocations() default true;

    /**
     * @see ContextConfiguration#inheritInitializers()
     */
    boolean inheritInitializers() default true;

    /**
     * @see ContextConfiguration#name()
     */
    String name() default "";

}