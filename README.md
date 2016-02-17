# Shift Bundler

The shift bundler takes modules with dependencies specified using ES6 import/export statements 
and returns a single script that can run in an pre-ES2015 JavaScript environment.

## Installation

Add as a dependency via maven:

```xml
<!-- todo -->
```

## Usage

Call the static `bundle` method:

```java
Script result = Bundler.bundle("/path/to/file.js");
```

Alternatively, if you have the file's contents handy you can use `bundleString`:

```java
Script result = Bundler.bundleString("import { x } from \"foo\"; console.log(x);");
```

Both `bundle` and `bundleString` can be parameterized by an `IResourceLoader` (which tells the 
bundler how to load a resource referred to by some path) and an `IResolver` (which tells the 
bundler where to actually look for a resource referenced by some path). By default 
`FileSystemResolver` and `FileLoader` are used. Also available are a `NodeResolver` that follows
node module resolving semantics and `ClassResourceLoader` for loading resources inside of jars.

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
