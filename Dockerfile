FROM openjdk:14-jdk-alpine

RUN apk add tzdata
RUN cp /usr/share/zoneinfo/Europe/Berlin /etc/localtime
ENV TZ="Europe/Berlin"

VOLUME /tmp

COPY target/simulator-0.0.1-SNAPSHOT.jar camunda-cloud-simulator.jar
ENV JAVA_OPTS="" \
DOCKER_JAVA_OPTS="-XX:+UnlockExperimentalVMOptions" \
LANG=en_US.utf8
#Entry with exec
ENTRYPOINT exec java ${JAVA_OPTS} ${DOCKER_JAVA_OPTS} -Djava.security.egd=file:/dev/./urandom -jar /camunda-cloud-simulator.jar
#define usergroup with just few to none permissions and add this
RUN addgroup -S app && \
   adduser -S -g app app && \
   chown app:app /${DEPLOYMENT_ARTIFACT}
USER app