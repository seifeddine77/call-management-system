# ============================================================
# Jenkins Custom Image — AutoAssist CI/CD
# Pre-installs Maven, Node.js, Python, Docker CLI
# ============================================================
FROM jenkins/jenkins:lts-jdk17

USER root

# Install build tools
RUN apt-get update -qq && \
    apt-get install -y --no-install-recommends \
      maven \
      nodejs \
      npm \
      python3 \
      python3-pip \
      python3-venv \
      curl \
      docker.io \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

# Verify installations
RUN java -version && mvn --version && node --version && python3 --version

USER jenkins
