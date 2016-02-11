# ES6-Bundler

ES6-Bundler bundles modules with dependencies specified using ES6 import/export statements 
as a single script that can run in an pre-ES6 JavaScript environment.

## Usage

The entry point is `Bundler.bundleString` for bundling a module represented as a string or
`Bundler.bundle` for a module represented as a path to the module.

The bundler can be parameterized by a `IResourceLoader` (which tells the bundler how to load a resource
referred to by some path) and an `IResolver` (which tells the bundler where to actually look for a
resource referenced by some path).

## Contributing

* Open a Github issue with a description of your desired change. If one exists already, leave 
a message stating that you are working on it with the date you expect it to be complete.
* Fork this repo, and clone the forked repo.
* Install dependencies with `mvn`.
* Build and test in your environment with `mvn compile test`.
* Create a feature branch. Make your changes. Add tests.
* Build and test in your environment with `mvn compile test`.
* Make a commit that includes the text "fixes #*XX*" where *XX* is the Github issue.
* Open a Pull Request on Github.

## License

    Copyright 2016 Shape Security, Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
