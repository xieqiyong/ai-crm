FROM m.daocloud.io/mcr.microsoft.com/playwright:v1.63.0-noble

WORKDIR /app

RUN sed -i \
        -e 's|http://azure.archive.ubuntu.com/ubuntu/|https://mirrors.aliyun.com/ubuntu/|g' \
        -e 's|http://security.ubuntu.com/ubuntu/|https://mirrors.aliyun.com/ubuntu/|g' \
        /etc/apt/sources.list.d/ubuntu.sources \
    && apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        fluxbox \
        novnc \
        websockify \
        x11vnc \
        xvfb \
    && rm -rf /var/lib/apt/lists/*

COPY package.json package-lock.json* ./
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        g++ \
        make \
    && npm install --omit=dev \
    && apt-get purge -y --auto-remove g++ make \
    && rm -rf /var/lib/apt/lists/*

COPY . .
RUN chmod +x /app/docker-entrypoint.sh \
    && mkdir -p /app/data

ENV DISPLAY=:99
ENV LICENSE_SYNC_CONFIG=/app/config.json
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1

ENTRYPOINT ["/app/docker-entrypoint.sh"]
CMD ["npm", "start"]
