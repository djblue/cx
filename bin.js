#!/usr/bin/env node

const spawn = require('child_process').spawn
const path = require('path')

const bin = path.join(__dirname, 'node_modules', '.bin', 'lumo')
const args = process.argv.slice(2)
const opts = [
  '--classpath', 'src',
  '--main', 'ddev.cli',
  '--', 'cx', 'bundle.js'
].concat(args)

process.env.CWD = process.cwd()

spawn(bin, opts, { cwd: __dirname, stdio: 'inherit' }).on('exit', process.exit)

