FROM openeuler/openeuler:22.03-lts

# Set env
USER root
ENV USER root


RUN yum -y update \
     && yum install -y wget \
     && yum install -y git \
         && yum install -y maven


