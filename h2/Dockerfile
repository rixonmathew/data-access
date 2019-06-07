FROM openjdk:11-jre-slim
MAINTAINER Rixonb Mathew rixonmathew@gmail.com
VOLUME /tmp
COPY ./target/h2-0.0.1-SNAPSHOT.jar /opt/h2-0.0.1-SNAPSHOT.jar
ENTRYPOINT ["/usr/bin/java"]
CMD ["-jar", "/opt/h2-0.0.1-SNAPSHOT.jar"]
EXPOSE 9595
