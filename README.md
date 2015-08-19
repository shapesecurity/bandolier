# ES6-Bundler

## Description
ES6-Bundler bundles modules with dependencies specified using ES6 import/export statements as a single script.

## Usage

The entry point is `Bundler.bundleString` for bundling a module represented as a string or
`Bundler.bundle` for a module represented as a path to the module.

The bundler can be parameterized by a `IResourceLoader` (which tells the bundler how to load a resource
referred to by some path) and an `IResolver` (which tells the bundler where to actually look for a
resource referrenced by some path).
