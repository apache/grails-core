package functionaltests

import grails.gorm.transactions.Rollback
import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration

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
@Integration(applicationClass = Application)
@Rollback
class ControllerIncludesSpec extends ContainerGebSpec {

    void "Test includes that render a view work when called from a controller action"() {
        when:"When an include that renders a view is used inside a controller action"
        go '/includes/includeFromControllerActionRenderingView'

        then:"Then the include worked"
            driver.pageSource.contains 'Model is bar'
    }

    void "Test includes that render text work when called from a controller action"() {
        when:"When an include that renders text is used inside a controller action"
            go '/includes/includeFromControllerActionRenderingText'

        then:"Then the include worked"
            driver.pageSource.contains 'Test'
    }

    void "Test includes that render a view work when called from another view"() {
        when:"When an include that is executed inside another view is used"
            go '/includes/includeFromViewRenderingView'

        then:"Then the include worked"
            driver.pageSource.contains 'Include: Model is bar'
    }

    void "Test includes that render text work when called from another view"() {
        when:"When an include that renders text is used inside a view"
            go '/includes/includeFromViewRenderingText'

        then:"Then the include worked"
            driver.pageSource.contains 'Include: Test'
    }

    void "Test includes that render a view work when called from a template"() {
        when:"When an include that is executed inside another view is used"
        go '/includes/includeFromTemplateRenderingView'

        then:"Then the include worked"
        driver.pageSource.contains 'Include: Model is bar'
    }

    void "Test includes that render text work when called from a template"() {
        when:"When an include that renders text is used inside a view"
        go '/includes/includeFromTemplateRenderingText'

        then:"Then the include worked"
        driver.pageSource.contains 'Include: Test'
    }

}

