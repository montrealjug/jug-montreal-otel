Forked from https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/examples/extension with a project cleanup to only keep a custom sampler to remove the schedule task "executeSql" in java-worker app.

Can be used with the built jar as an extension

> ./gradlew jar

> java -javaagent:opentelemetry-javaagent.jar -Dotel.javaagent.extensions=build/libs/otelagent-extensions-0.0.1-SNAPSHOT.jar

or with the fatjar agent directly

> ./gradlew extendedAgent
   
> java -javaagent:build/libs/opentelemetry-javaagent.jar

