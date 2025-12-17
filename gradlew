#!/bin/sh
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'
APP_HOME=$( cd "${0%/*}" && pwd )
exec "$APP_HOME/gradle/wrapper/gradle-wrapper" "$@"
