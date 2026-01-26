<!DOCTYPE html>
<html>
<head>
    <title>Render Tag Test</title>
</head>
<body>
    <h1>Render Tag Test</h1>
    <div id="controller-message">${message}</div>
    <div id="template-render">
        <g:render template="partial" model="[partialMessage: 'From Template']"/>
    </div>
</body>
</html>
