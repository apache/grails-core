<!DOCTYPE html>
<html>
<head>
    <title>Form Tag Test</title>
</head>
<body>
    <h1>Form Tag Test</h1>
    <g:form controller="tagLib" action="formTag" method="POST" name="test-form">
        <div class="form-group">
            <label for="username">Username:</label>
            <g:textField name="username" value="${username}" id="username-input"/>
        </div>
        <div class="form-group">
            <label for="email">Email:</label>
            <g:textField name="email" value="${email}" id="email-input"/>
        </div>
        <div class="form-group">
            <label for="password">Password:</label>
            <g:passwordField name="password" id="password-input"/>
        </div>
        <div class="form-group">
            <label for="remember">Remember me:</label>
            <g:checkBox name="remember" id="remember-checkbox"/>
        </div>
        <div class="form-group">
            <label for="comments">Comments:</label>
            <g:textArea name="comments" rows="3" cols="40" id="comments-textarea"/>
        </div>
        <g:submitButton name="submit" value="Submit" id="submit-button"/>
    </g:form>
</body>
</html>
