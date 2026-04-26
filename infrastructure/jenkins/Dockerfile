# ============================================================
# Jenkins Custom Image — AutoAssist CI/CD
# Pre-installs Maven, Node.js, Python, Docker CLI + Plugins
# ============================================================
FROM jenkins/jenkins:lts-jdk17

USER root

# Install build tools + libpq-dev for psycopg2 compilation
RUN apt-get update -qq && \
    apt-get install -y --no-install-recommends \
      maven \
      nodejs \
      npm \
      python3 \
      python3-pip \
      python3-venv \
      python3-dev \
      libpq-dev \
      gcc \
      curl \
      docker.io \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

# Verify installations
RUN java -version && mvn --version && node --version && python3 --version

# Switch to jenkins user to install plugins
USER jenkins

# Pre-install required Jenkins plugins
RUN jenkins-plugin-cli --plugins \
      jacoco \
      junit \
      pipeline-stage-view \
      blueocean
