FROM docker.1ms.run/nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY write-runtime-config.sh /docker-entrypoint.d/99-write-crm-config.sh
COPY dist/ /usr/share/nginx/html/

RUN chmod +x /docker-entrypoint.d/99-write-crm-config.sh

EXPOSE 80
