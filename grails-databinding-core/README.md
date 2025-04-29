<!--
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

## grails-databinding

This subproject contains much of the core data binding code.  The main class here is
[SimpleDataBinder](./src/main/groovy/grails/databinding/SimpleDataBinder.groovy).  Most of the other code
 here exists to support that.  The real databinding used in a Grails app is
 [GrailsWebDataBinder](../grails-web-databinding/src/main/groovy/grails/web/databinding/WebDataBinding.groovy) which
 extends `SimpleDataBinder` and is defined in the `grails-web-databinding` subproject.  `SimpleDataBinder` is where
 much of the core data binding logic is defined. The `GrailsWebDataBinder` subclass defines a lot of the logic that
  is specific to data binding in the context of a Grails app.  For example, all of the GORM special handling that the
  data binder does is in `GrailsWebDataBinder`.
