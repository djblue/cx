#!/bin/bash

PATH="$PATH:./node_modules/.bin"

rm -rf target
lumo -c src build.cljs
browserify --node target/main.js > target/bundle.js
echo '#!/usr/bin/env node' > target/bin.js
cat target/bundle.js >> target/bin.js
chmod +x target/bin.js
