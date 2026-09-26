#!/bin/bash
# Setup script for Claude Code on the web (cloud) environments.
# Paste into: cloud environment menu -> Edit -> Setup script. It runs as root when a new session starts.
# Assumes the cloud base image (Ubuntu, docker/dockerd preinstalled).
set -e

# JDK 25 (the project's pom.xml targets release 25; the base image ships JDK 21)
apt-get update -q
apt-get install -y -q openjdk-25-jdk-headless
update-alternatives --set java /usr/lib/jvm/java-25-openjdk-amd64/bin/java
update-alternatives --set javac /usr/lib/jvm/java-25-openjdk-amd64/bin/javac
cat > /etc/profile.d/java.sh <<'EOF'
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64
export PATH=${JAVA_HOME}/bin:${PATH}
EOF

# Docker daemon for Testcontainers (docker/dockerd are preinstalled, but not running)
if ! docker info >/dev/null 2>&1; then
  nohup dockerd >/var/log/dockerd.log 2>&1 &
  for i in $(seq 1 30); do docker info >/dev/null 2>&1 && break; sleep 1; done
fi
docker info >/dev/null 2>&1 || echo "WARNING: dockerd did not start; see /var/log/dockerd.log"
