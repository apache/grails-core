<!DOCTYPE html>
<html>
<head>
    <title>Set Tag Test</title>
</head>
<body>
    <h1>Set Tag Test</h1>
    <g:set var="localVar" value="Hello from g:set"/>
    <p id="set-value">${localVar}</p>
    
    <g:set var="computed" value="${2 + 3}"/>
    <p id="computed-value">Computed: ${computed}</p>
    
    <g:set var="listVar" value="${['A', 'B', 'C']}"/>
    <p id="list-value">List size: ${listVar.size()}</p>
</body>
</html>
