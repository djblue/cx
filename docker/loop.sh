#!/bin/bash

rm -rf target/
mkdir target
cp ../target/deploy/bin.js target/bin.js

docker build -t cx .

docker run -it --rm \
  -v $HOME/.ssh:/tmp/.ssh:ro \
  -v $HOME/.ddev:/root/.ddev \
  cx

docker rmi cx
