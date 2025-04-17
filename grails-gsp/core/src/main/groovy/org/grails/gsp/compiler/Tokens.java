/*
 * Copyright 2004-2005 the original author or authors.
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
package org.grails.gsp.compiler;

/**
 * NOTE: Based on work done by on the GSP standalone project (https://gsp.dev.java.net/)
 *
 * Interface defining an enumeration of tokens for the different scriptlet types
 *
 * @author Troy Heninger
 * @author Graeme Rocher
 *
 * Date: Jan 10, 2004
 */
interface Tokens {
    int EOF = -1;
    int HTML = 0;
    int JEXPR = 1;   // <%= ... %>
    int JSCRIPT = 2; // <% .... %>
    int JDIRECT = 3; // <%@ ... %>
    int JDECLAR = 4; // <%! ... %>
    int GEXPR = 11;   // ${ ... }
    int GSCRIPT = 12; // %{ ... }%
    int GDIRECT = 13; // @{ ... }
    int GDECLAR = 14; // !{ ... }!
    int GSTART_TAG = 15; // <g:..>
    int GEND_TAG = 16; // </g:..>
    int GTAG_EXPR = 17; // ${..}
    int GEND_EMPTY_TAG = 18;
}
