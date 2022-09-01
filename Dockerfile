FROM ubuntu:xenial

# Set env
USER root
ENV USER root


RUN apt -y update \
     && apt install -y wget \
     && apt install -y git \
         && apt install -y maven 


