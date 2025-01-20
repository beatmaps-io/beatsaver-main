# For prod. Don't try and build this locally
FROM debian:trixie-slim AS ffsampled
RUN apt update && \
    apt install -y build-essential git maven openjdk-21-jdk doxygen g++-aarch64-linux-gnu zlib1g-dev libbz2-dev && \
    rm -rf /var/lib/apt/lists/*
RUN git clone https://github.com/hendriks73/ffsampledsp.git /opt/ffsampledsp
WORKDIR /opt/ffsampledsp
# Skip tests because the filename one fails in docker
RUN export JAVA_HOME=$(readlink -f /usr/bin/java | sed "s:bin/java::") && \
    mvn install -P ffsampledsp-x86_64-linux -Dmaven.test.skip

FROM debian:trixie-slim
EXPOSE 8080

RUN apt update && \
    apt install -y openjdk-21-jdk curl ffmpeg && \
    rm -rf /var/lib/apt/lists/*
RUN mkdir /app

COPY --from=ffsampled /opt/ffsampledsp/ffsampledsp-x86_64-linux/target/ffsampledsp-x86_64-linux.so /usr/lib/
COPY . /app

WORKDIR /app/bin
ENTRYPOINT ["./BeatMaps"]
