ARG UBUNTU_VERSION=26.04
FROM ubuntu:${UBUNTU_VERSION}

ARG JAVA_VERSION=11
ENV DEBIAN_FRONTEND=noninteractive \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    JDK_HOME=/usr/lib/jvm/java-${JAVA_VERSION}-openjdk-amd64 \
    JAVA_HOME=/usr/lib/jvm/java-${JAVA_VERSION}-openjdk-amd64 \
    GRADLE_USER_HOME=/work/.gradle

# This is deliberately a development/runtime image: the same container builds,
# diagnoses, tests, packages, and smoke-runs the SageTV server.
RUN apt-get update && apt-get install -y --no-install-recommends \
      autoconf automake binutils build-essential ca-certificates curl file \
      g++ gcc gdb git libasound2-dev libaudio-dev libavc1394-dev \
      libfreetype-dev libgif-dev libiec61883-dev libjpeg-dev libnsl-dev libpng-dev \
      libpulse-dev libraw1394-dev libtiff-dev libtool libx11-dev libxt-dev \
      lsof make openjdk-${JAVA_VERSION}-jdk patchelf pkg-config procps \
      python3 python3-pil strace unzip wget xz-utils yasm zip zlib1g-dev \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /work/sagetv
CMD ["sleep", "infinity"]
