FROM wiremock/wiremock:3.13.2-2

RUN apt-get update && apt-get -y upgrade && apt-get -y install curl

COPY ./mappings/* /home/wiremock/mappings/
