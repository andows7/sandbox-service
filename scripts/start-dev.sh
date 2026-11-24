#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/../backend"
docker info >/dev/null
if [ "${1:-}" = "--build-images" ]; then
  docker build -t sandbox-c-gcc11:1.0 docker-env/c
  docker build -t sandbox-cpp-gcc11:1.0 docker-env/cpp
  docker build -t sandbox-java-openjdk17:1.0 docker-env/java
  docker build -t sandbox-python-python3.10:1.0 docker-env/python
fi
mvn -q -DskipTests package
exec java -Xms32m -Xmx192m -XX:+UseSerialGC -XX:ActiveProcessorCount=2 -Xss512k -jar target/sandbox-service-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev
