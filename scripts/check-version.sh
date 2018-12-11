#!/bin/bash

POM_VERSION=$(mvn -Dexec.executable='echo' -Dexec.args='\${project.version}' --non-recursive exec:exec -q | tail -c +2)
PACKAGE_VERSION=$(node -e 'console.log(require("./package.json").version)')

if [[ "$POM_VERSION" != "$PACKAGE_VERSION" ]]; then
  echo "pom.xml/package.json version mismatch: $POM_VERSION vs $PACKAGE_VERSION"
  exit 1
fi