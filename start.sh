#!/bin/bash

trap 'kill $(jobs -p)' EXIT
npm start > /dev/null 2>&1 &
vim src/ddev/core.cljs
