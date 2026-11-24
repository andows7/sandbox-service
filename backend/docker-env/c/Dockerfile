FROM ubuntu:22.04
RUN sed -i 's@http://.*archive.ubuntu.com@http://mirrors.tuna.tsinghua.edu.cn@g; s@http://.*security.ubuntu.com@http://mirrors.tuna.tsinghua.edu.cn@g' /etc/apt/sources.list \
    && apt-get -o Acquire::Retries=2 update && apt-get install -y --no-install-recommends gcc libc6-dev time \
    && rm -rf /var/lib/apt/lists/*
USER 1000:1000
WORKDIR /sandbox/workdir
CMD ["sleep", "infinity"]
