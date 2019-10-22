#!/usr/bin/env bash

sbt assembly

native-image --no-server -cp target/scala-2.12/ergo-polyglot-3.0.0.jar \
  --report-unsupported-elements-at-runtime --no-fallback \
  -H:+TraceClassInitialization -H:+ReportExceptionStackTraces -H:+AddAllCharsets \
  -H:+AllowVMInspection -H:-RuntimeAssertions --enable-url-protocols=http,https \
  --allow-incomplete-classpath \
  org.ergoplatform.polyglot.ni.Prove prove

./prove -Djava.library.path=${GRAAL_HOME}/Contents/Home/jre/lib