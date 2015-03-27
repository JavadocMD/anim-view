FROM williamyeh/scala:2.11.5
ENV JAVA_HOME=/usr/lib/jvm/java-7-oracle
VOLUME /usr/src
WORKDIR /usr/src
CMD ["sbt", "clean", "package"]