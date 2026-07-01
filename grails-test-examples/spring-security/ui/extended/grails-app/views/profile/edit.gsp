<%--
  ~  Licensed to the Apache Software Foundation (ASF) under one
  ~  or more contributor license agreements.  See the NOTICE file
  ~  distributed with this work for additional information
  ~  regarding copyright ownership.  The ASF licenses this file
  ~  to you under the Apache License, Version 2.0 (the
  ~  "License"); you may not use this file except in compliance
  ~  with the License.  You may obtain a copy of the License at
  ~
  ~    https://www.apache.org/licenses/LICENSE-2.0
  ~
  ~  Unless required by applicable law or agreed to in writing,
  ~  software distributed under the License is distributed on an
  ~  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  ~  KIND, either express or implied.  See the License for the
  ~  specific language governing permissions and limitations
  ~  under the License.
  --%>
<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="${layoutUi}" />
        <s2ui:title messageCode='default.edit.label' entityNameMessageCode='profile.label' entityNameDefault='Profile'/>
    </head>
    <body>
        <div id="edit-profile" class="body" role="main">
            <g:hasErrors bean="${this.profile}">
            <ul class="errors" role="alert">
                <g:eachError bean="${this.profile}" var="error">
                <li <g:if test="${error in org.springframework.validation.FieldError}">data-field-id="${error.field}"</g:if>><g:message error="${error}"/></li>
                </g:eachError>
            </ul>
            </g:hasErrors>
             <s2ui:formContainer type='update' beanType='profile' focus='myQuestion1'>
                <s2ui:form useToken="true">
                    <div class="dialog">
                        <br/>
                        <table>
                            <tbody>
                                
                                    <s2ui:textFieldRow name='myQuestion1' size='50' labelCodeDefault='myQuestion1'/>
                                        <s2ui:textFieldRow name='myAnswer1' size='50' labelCodeDefault='myAnswer1'/>
                                
                                    <s2ui:textFieldRow name='myQuestion2' size='50' labelCodeDefault='myQuestion2'/>
                                        <s2ui:textFieldRow name='myAnswer2' size='50' labelCodeDefault='myAnswer2'/>
                                
                                <s2ui:selectRow name='user.id' from='${users}' labelCodeDefault='user' optionValue='${lookupProp}' />
                            </tbody>
                        </table>
                    </div>
                    <div style='float:left; margin-top: 10px;'>
                        <s2ui:submitButton/>
                            <s2ui:deleteButton/>
                    </div>
                </s2ui:form>
            </s2ui:formContainer>
            <s2ui:deleteButtonForm instanceId='${profile.id}' useToken="true"/>
        </div>
    </body>
</html>
