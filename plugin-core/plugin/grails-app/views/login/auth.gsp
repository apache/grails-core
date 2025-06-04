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
<html>
<head>
	<meta name="layout" content="${gspLayout ?: 'main'}"/>
	<title><g:message code='springSecurity.login.title'/></title>
	<style type="text/css" media="screen">

	#login {
		margin: 15px 0px;
		padding: 0px;
		text-align: center;
		font-family: Arial, Helvetica, sans-serif;
	}

	#login .inner {
		width: 340px;
		padding-bottom: 6px;
		margin: 60px auto;
		text-align: left;
		border: 1px solid #aab;
		background-color: #f0f0fa;
		-moz-box-shadow: 2px 2px 2px #eee;
		-webkit-box-shadow: 2px 2px 2px #eee;
		-khtml-box-shadow: 2px 2px 2px #eee;
		box-shadow: 2px 2px 2px #eee;
	}

	#login .inner .fheader {
		padding: 18px 26px 14px 26px;
		background-color: #f7f7ff;
		margin: 0px 0 14px 0;
		color: #2e3741;
		font-size: 18px;
		font-weight: bold;
	}

	#login .inner .cssform p {
		clear: left;
		margin: 0;
		padding: 4px 0 3px 0;
		padding-left: 105px;
		margin-bottom: 20px;
		height: 1%;
	}

	#login .inner .cssform input[type="text"] {
		width: 75%;
	}

	#login .inner .cssform label {
		font-weight: bold;
		float: left;
		text-align: right;
		margin-left: -105px;
		width: 110px;
		padding-top: 3px;
		padding-right: 10px;
	}

	#remember_me_holder {
		padding-left: 120px;
	}

	#submit {
		margin-left: 15px;
	}

	#remember_me_holder label {
		float: none;
		margin-left: 0;
		text-align: left;
		width: 200px
	}

	#login .inner .login_message {
		padding: 6px 25px 20px 25px;
		color: #c33;
	}

	#login .inner .text_ {
		width: 75%;
	}

	#login .inner .chk {
		height: 12px;
	}

	#passwordToggler {
		padding: 3px 4px;
		cursor: pointer;
	}
	</style>
</head>

<body>
<div id="login">
	<div class="inner">
		<div class="fheader"><g:message code='springSecurity.login.header'/></div>

		<g:if test='${flash.message}'>
			<div class="login_message">${flash.message}</div>
		</g:if>

		<form action="${postUrl ?: '/login/authenticate'}" method="POST" id="loginForm" class="cssform" autocomplete="off">
			<p>
				<label for="username"><g:message code='springSecurity.login.username.label'/>:</label>
				<input type="text" class="text_" name="${usernameParameter ?: 'username'}" id="username" autocapitalize="none"/>
			</p>

			<p>
				<label for="password"><g:message code='springSecurity.login.password.label'/>:</label>
				<input type="password" class="text_" name="${passwordParameter ?: 'password'}" id="password"/>
				<i id="passwordToggler" title="toggle password display" onclick="passwordDisplayToggle()">&#128065;</i>
			</p>

			<p id="remember_me_holder">
				<input type="checkbox" class="chk" name="${rememberMeParameter ?: 'remember-me'}" id="remember_me" <g:if test='${hasCookie}'>checked="checked"</g:if>/>
				<label for="remember_me"><g:message code='springSecurity.login.remember.me.label'/></label>
			</p>

			<p>
				<input type="submit" id="submit" value="${message(code: 'springSecurity.login.button')}"/>
			</p>
		</form>
	</div>
</div>
<script type="text/javascript">
	document.addEventListener("DOMContentLoaded", function(event) {
		document.forms['loginForm'].elements['username'].focus();
	});

	function passwordDisplayToggle() {
		var toggleEl = document.getElementById("passwordToggler");
		var eyeIcon = '\u{1F441}';
		var xIcon = '\u{2715}';
		var passEl = document.getElementById("password");

		if (passEl.type === "password") {
			toggleEl.innerHTML = xIcon;
			passEl.type = "text";
		} else {
			toggleEl.innerHTML = eyeIcon;
			passEl.type = "password";
		}
	}
</script>
</body>
</html>