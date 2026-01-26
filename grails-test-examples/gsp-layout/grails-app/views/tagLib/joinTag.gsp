<!DOCTYPE html>
<html>
<head>
    <title>Join Tag Test</title>
</head>
<body>
    <h1>Join Tag Test</h1>
    <p id="comma-join">Comma: <g:join in="${items}" delimiter=", "/></p>
    <p id="dash-join">Dash: <g:join in="${items}" delimiter=" - "/></p>
    <p id="pipe-join">Pipe: <g:join in="${items}" delimiter=" | "/></p>
</body>
</html>
