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

//= require spring-security-ui-ajaxLogin.js

// Show/hide toggle for the password fields on the standalone login page and
// the ajax login modal: flips the field type and the eye icon, and reports
// the state through aria-pressed.
function s2uiTogglePassword(button, fieldId) {
    var field = document.getElementById(fieldId);
    var icon = button.querySelector('i');
    var hiding = field.type === 'text';
    field.type = hiding ? 'password' : 'text';
    icon.classList.toggle('bi-eye', hiding);
    icon.classList.toggle('bi-eye-slash', !hiding);
    button.setAttribute('aria-pressed', String(!hiding));
}
