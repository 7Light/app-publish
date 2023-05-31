FROM openeuler/openeuler:22.03-lts

# Set env
USER root
ENV USER root


RUN yum -y update \
     && yum install -y wget \
     && yum install -y git \
         && yum install -y rpm \
         && yum install -y maven \
         && yum install -y createrepo \
         && yum install -y clamav clamav-update


 # install java
RUN wget --no-cookies --no-check-certificate --header "Cookie: gpw_e24=http%3A%2F%2Fwww.oracle.com%2F; oraclelicense=accept-securebackup-cookie" "http://download.oracle.com/otn-pub/java/jdk/8u141-b15/336fa29ff2bb4ef291e347e091f7f4a7/jdk-8u141-linux-x64.tar.gz"
RUN mkdir /usr/local/java
RUN tar zxvf jdk-8u141-linux-x64.tar.gz -C /usr/local/java
RUN ln -s /usr/local/java/jdk1.8.0_141 /usr/local/java/jdk
ENV JAVA_HOME /usr/local/java/jdk
ENV JRE_HOME ${JAVA_HOME}/jre
ENV CLASSPATH .:${JAVA_HOME}/lib:${JRE_HOME}/lib
ENV PATH ${JAVA_HOME}/bin:$PATH
ENV UPDATE_KEY=""

# app-publish
RUN git clone -b open-euler https://github.com/7Light/app-publish.git
WORKDIR /app-publish
RUN mvn clean install -s settings.xml

# clamAv
RUN touch /etc/clamd.d/scan.conf
RUN chmod 755 /etc/clamd.d/scan.conf
RUN echo 'LogFile /var/log/clamav/scan.log' >> /etc/clamd.d/scan.conf
RUN echo 'LogFileMaxSize 0' >> /etc/clamd.d/scan.conf
RUN echo 'LogTime yes' >> /etc/clamd.d/scan.conf
RUN echo 'LogSyslog yes' >> /etc/clamd.d/scan.conf
RUN echo 'DatabaseDirectory /var/lib/clamav' >> /etc/clamd.d/scan.conf
RUN echo 'ScanArchive yes' >> /etc/clamd.d/scan.conf
RUN echo 'MaxFileSize 200M' >> /etc/clamd.d/scan.conf
RUN echo 'MaxRecursion 16' >> /etc/clamd.d/scan.conf
RUN echo 'MaxFiles 10000' >> /etc/clamd.d/scan.conf
RUN echo 'MaxEmbeddedPE 10M' >> /etc/clamd.d/scan.conf
RUN echo 'MaxHTMLNormalize 10M' >> /etc/clamd.d/scan.conf
RUN freshclam

#
WORKDIR /usr/local
RUN touch entrypoint.sh
RUN echo '#!/bin/bash' >>  /usr/local/entrypoint.sh
RUN sed -i -e "1achmod 0600 /var/log/ssh_key/private.key" /usr/local/entrypoint.sh
RUN sed -i -e "2ajava -jar /root/.m2/repository/com/huawei/app-publish/1.0/app-publish-1.0.jar" /usr/local/entrypoint.sh

RUN chmod u+x /usr/local/entrypoint.sh
ENTRYPOINT ["/usr/local/entrypoint.sh"]
