# ============================================================
# Jenkins Custom Image — AutoAssist CI/CD (Full Stack)
# All tools pre-installed: Java, Maven, Node, Python 3.11,
# Docker CLI, Trivy, Gitleaks, kubectl, Flutter
# ============================================================
FROM jenkins/jenkins:lts-jdk17

USER root

# ── 1. System packages ──────────────────────────────────────
RUN apt-get update -qq && \
    apt-get install -y --no-install-recommends \
      maven \
      nodejs \
      npm \
      curl \
      wget \
      unzip \
      xz-utils \
      git \
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
      apt-transport-https \
      ca-certificates \
      gnupg \
      lsb-release \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

# ── 2. Docker CLI (use host socket, not daemon) ─────────────
RUN curl -fsSL https://download.docker.com/linux/debian/gpg | gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg && \
    echo "deb [arch=amd64 signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/debian bookworm stable" > /etc/apt/sources.list.d/docker.list && \
    apt-get update -qq && \
    apt-get install -y --no-install-recommends docker-ce-cli docker-compose-plugin && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# ── 3. Python 3.11 from source (3.13 breaks pydantic/psycopg2) ──
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

# ── 4. Trivy (vulnerability scanner) ────────────────────────
RUN curl -sfL https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/install.sh | sh -s -- -b /usr/local/bin

# ── 5. Gitleaks (secrets scanner) ────────────────────────────
RUN GITLEAKS_VERSION=$(curl -s https://api.github.com/repos/gitleaks/gitleaks/releases/latest | grep '"tag_name"' | sed 's/.*"v\(.*\)".*/\1/') && \
    wget -q "https://github.com/gitleaks/gitleaks/releases/download/v${GITLEAKS_VERSION}/gitleaks_${GITLEAKS_VERSION}_linux_x64.tar.gz" -O /tmp/gitleaks.tar.gz && \
    tar xzf /tmp/gitleaks.tar.gz -C /usr/local/bin gitleaks && \
    rm /tmp/gitleaks.tar.gz && \
    chmod +x /usr/local/bin/gitleaks

# ── 6. kubectl ───────────────────────────────────────────────
RUN curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl" && \
    install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl && \
    rm kubectl

# ── 7. Flutter SDK (for mobile builds) ──────────────────────
RUN git clone --depth 1 --branch stable https://github.com/flutter/flutter.git /opt/flutter && \
    /opt/flutter/bin/flutter --disable-analytics && \
    /opt/flutter/bin/flutter config --no-analytics && \
    /opt/flutter/bin/flutter precache --web && \
    chown -R jenkins:jenkins /opt/flutter && \
    git config --global --add safe.directory /opt/flutter
ENV PATH="/opt/flutter/bin:${PATH}"

# ── 8. Add jenkins user to docker group + git safe dir ──────
RUN groupadd -f docker && usermod -aG docker jenkins

# ── Verify all installations ────────────────────────────────
RUN echo "=== Tool Versions ===" && \
    java -version && \
    mvn --version && \
    node --version && \
    python3 --version && \
    pip3 --version && \
    docker --version && \
    trivy --version && \
    gitleaks version && \
    kubectl version --client && \
    flutter --version && \
    echo "=== All tools installed ==="

# ── Switch to jenkins user ──────────────────────────────────
USER jenkins

# ── Configure git safe directory for Flutter SDK ────────────
RUN git config --global --add safe.directory /opt/flutter

# ── Pre-install Jenkins plugins ─────────────────────────────
RUN jenkins-plugin-cli --plugins \
      jacoco \
      junit \
      pipeline-stage-view \
      blueocean
