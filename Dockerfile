FROM openjdk:latest
MAINTAINER Karol Wójcik <karol.wojcik@tuta.io>

ADD .holy-lambda/build/output.jar output.jar

CMD java -jar output.jar "org.singularity-group.bot-announce.core.BotAnnounceLambda"
