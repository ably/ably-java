#!/usr/bin/env node

var obj, fs = require('fs');

eval('obj=' + fs.readFileSync('testAppSpec.json.src'));
fs.writeFileSync('testAppSpec.json', JSON.stringify(obj, null, '\t'));

eval('obj=' + fs.readFileSync('testPresenceSpec.json.src'));
fs.writeFileSync('testPresenceSpec.json', JSON.stringify(obj, null, '\t'));
