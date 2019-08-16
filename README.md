# cx

[![Build Status](https://travis-ci.org/djblue/cx.svg?branch=master)](https://travis-ci.org/djblue/cx)

A handy little script to help work on [Codice](https://github.com/codice)
and [Connexta](https://github.com/connexta) projects.

## Install

    npm install -g github:djblue/cx.git#deploy

If you get an `EACCES` error, try:

    sudo -E npm install -g github:djblue/cx.git#deploy

## Build

To build, first install npm dependencies by doing:

    npm install

Then, to build the `cx-dev` script, do:

    clojure -Abuild

If you want to test this script globally, do:

    npm ln

If you get an `EACCES` error, try:

    sudo -E npm ln

## Watching

To build on source changes, do:

    clojure -Awatch

## License

The MIT License (MIT)

Permission is hereby granted, free of charge, to any person obtaining a
copy of this software and associated documentation files (the "Software"),
to deal in the Software without restriction, including without limitation
the rights to use, copy, modify, merge, publish, distribute, sublicense,
and/or sell copies of the Software, and to permit persons to whom the
Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
DEALINGS IN THE SOFTWARE.
