FROM maven:3.6.0-jdk-8-alpine

RUN apk add openssh zsh git nodejs

COPY target/bin.js /bin/cx

COPY docker-entrypoint.sh /bin/docker-entrypoint.sh
RUN chmod +x /bin/docker-entrypoint.sh

ENTRYPOINT /bin/docker-entrypoint.sh
