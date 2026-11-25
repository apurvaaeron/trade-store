FROM mcr.microsoft.com/devcontainers/java:1-17-bookworm

# The base image ships a Yarn apt source whose signing key has expired, which breaks every apt-get
# (including the docker-in-docker feature). Maven is installed directly, verified against its published SHA-512.
ARG MAVEN_VERSION=3.9.9
ARG MAVEN_SHA512=a555254d6b53d267965a3404ecb14e53c3827c09c3b94b5678835887ab404556bfaf78dcfe03ba76fa2508649dca8531c74bca4d5846513522404d48e8c4ac8b
RUN rm -f /etc/apt/sources.list.d/yarn.list \
 && curl -fsSLo /tmp/maven.tar.gz "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
 && echo "${MAVEN_SHA512}  /tmp/maven.tar.gz" | sha512sum -c - \
 && tar -xzf /tmp/maven.tar.gz -C /opt \
 && rm /tmp/maven.tar.gz \
 && ln -s "/opt/apache-maven-${MAVEN_VERSION}/bin/mvn" /usr/local/bin/mvn
