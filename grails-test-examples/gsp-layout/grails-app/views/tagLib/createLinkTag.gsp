<!DOCTYPE html>
<html>
<head>
    <title>CreateLink Tag Test</title>
</head>
<body>
    <h1>CreateLink Tag Test</h1>
    <p id="absolute-link">Absolute: <g:createLink controller="tagLib" action="index" absolute="true"/></p>
    <p id="relative-link">Relative: <g:createLink controller="tagLib" action="eachTag"/></p>
    <p id="params-link">With Params: <g:createLink controller="tagLib" action="ifTag" params="[show: 'true', value: 'test']"/></p>
</body>
</html>
