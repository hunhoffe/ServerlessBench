from openwhisk/java8action
WORKDIR /
RUN mkdir test
ADD test.jpg /test
RUN apt-get update \
        && apt-get install -y imagemagick-6.q16
