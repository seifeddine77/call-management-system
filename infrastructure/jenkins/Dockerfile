# ============================================================
# Jenkins Custom Image — AutoAssist CI/CD
# Pre-installs Maven, Node.js, Python 3.11, Docker CLI + Plugins
# ============================================================
FROM jenkins/jenkins:lts-jdk17

USER root

# Install build tools + dependencies for compiling Python 3.11
RUN apt-get update -qq && \
    apt-get install -y --no-install-recommends \
      maven \
      nodejs \
      npm \
      curl \
      docker.io \
      gcc \
      g++ \
      make \
      zlib1g-dev \
      libffi-dev \
      libssl-dev \
      libpq-dev \
      libbz2-dev \
      libreadline-dev \
      libsqlite3-dev \
      wget \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

# Install Python 3.11 from source (Debian Trixie ships 3.13 which breaks pydantic/psycopg2)
RUN wget -q https://www.python.org/ftp/python/3.11.9/Python-3.11.9.tgz && \
    tar xzf Python-3.11.9.tgz && \
    cd Python-3.11.9 && \
    ./configure --enable-optimizations --prefix=/usr/local/python311 > /dev/null 2>&1 && \
    make -j$(nproc) > /dev/null 2>&1 && \
    make install > /dev/null 2>&1 && \
    cd .. && rm -rf Python-3.11.9 Python-3.11.9.tgz && \
    ln -sf /usr/local/python311/bin/python3.11 /usr/local/bin/python3 && \
    ln -sf /usr/local/python311/bin/python3.11 /usr/local/bin/python && \
    ln -sf /usr/local/python311/bin/pip3.11 /usr/local/bin/pip3 && \
    ln -sf /usr/local/python311/bin/pip3.11 /usr/local/bin/pip

# Verify installations
RUN java -version && mvn --version && node --version && python3 --version && pip3 --version

# Switch to jenkins user to install plugins
USER jenkins

# Pre-install required Jenkins plugins
RUN jenkins-plugin-cli --plugins \
      jacoco \
      junit \
      pipeline-stage-view \
      blueocean
