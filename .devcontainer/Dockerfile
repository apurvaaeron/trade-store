FROM mcr.microsoft.com/devcontainers/java:1-17-bookworm

# The base image ships a Yarn apt source whose signing key has expired, which breaks every apt-get
# (including the docker-in-docker feature). Maven is installed directly instead of through SDKMAN.
ARG MAVEN_VERSION=3.9.9
RUN rm -f /etc/apt/sources.list.d/yarn.list \
 && curl -fsSL "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
    | tar -xz -C /opt \
 && ln -s "/opt/apache-maven-${MAVEN_VERSION}/bin/mvn" /usr/local/bin/mvn
