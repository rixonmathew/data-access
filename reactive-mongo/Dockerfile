FROM openjdk:11-jre-slim
MAINTAINER rixonmathew@gmail.com
VOLUME /tmp
COPY ./target/reactive-mongo-0.0.1-SNAPSHOT.jar /opt/reactive-mongo-0.0.1-SNAPSHOT.jar
ENTRYPOINT ["/usr/bin/java"]
CMD ["-jar", "/opt/reactive-mongo-0.0.1-SNAPSHOT.jar"]
EXPOSE 9696
