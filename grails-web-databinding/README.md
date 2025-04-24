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

## grails-web-databinding

This subproject includes a lot of code to support data binding.

The [GrailsWebDataBinder)[src/main/groovy/grails/web/databindeing/WebDataBinding.groovy)
class extends [SimpleDataBinder](../grails-databinding-core/src/main/groovy/grails/databinding/SimpleDataBinder.groovy) from
the (grails-databinding)[../grails-databinding] subproject and adds to it a lot of Grails specific behavior like
special handing of GORM entities, code specifically relevant to binding web requests to objects and other behaviors.

The [WebDataBinding](src/main/groovy/grails/web/databinding/WebDataBinding.groovy) trait adds special methods
which support special binding usage patterns like `someObj.properties = request`.
