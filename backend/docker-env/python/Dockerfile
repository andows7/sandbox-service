FROM ubuntu:22.04
RUN sed -i "s@http://.*archive.ubuntu.com@http://mirrors.tuna.tsinghua.edu.cn@g" /etc/apt/sources.list && \
    sed -i "s@http://.*security.ubuntu.com@http://mirrors.tuna.tsinghua.edu.cn@g" /etc/apt/sources.list && \
    apt-get update && \
    apt-get install -y --no-install-recommends time python3 && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

USER 1000:1000
WORKDIR /sandbox/workdir
CMD ["sleep", "infinity"]
