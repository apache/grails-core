<!DOCTYPE html>
<html>
<head>
    <title>Collect Tag Test</title>
</head>
<body>
    <h1>Collect Tag Test</h1>
    <p id="names">Names: <g:join in="${items*.name}" delimiter=", "/></p>
    <p id="values">Values: <g:join in="${items*.value}" delimiter="-"/></p>
</body>
</html>
