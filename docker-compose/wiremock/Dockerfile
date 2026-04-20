FROM wiremock/wiremock:3.13.2-2

RUN apt-get update && apt-get -y install curl && rm -rf /var/lib/apt/lists/*

COPY ./mappings/* /home/wiremock/mappings/
