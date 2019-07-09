#!/bin/bash
git branch -D deploy
mkdir './deploy'
echo '{"name":"cx","version":"1.0.0","bin":{"cx":"./bin.js"}}' > ./deploy/package.json
echo '#!/usr/bin/env node' > ./deploy/bin.js
cat ./target/bundle.js >> ./deploy/bin.js
chmod +x ./deploy/bin.js
deploy() {
  git --git-dir=.git --work-tree=./deploy "$@"
}
deploy checkout --orphan deploy
deploy add .
deploy commit -m 'Release 1.0.0'
deploy checkout master -f
rm -rf './deploy'

