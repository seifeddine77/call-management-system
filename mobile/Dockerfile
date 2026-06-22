# ============================================
# AutoAssist Mobile — Flutter Web Production Build
# Serves pre-built Flutter web folder via Nginx
# ============================================

FROM nginx:alpine

# Copy local pre-built Flutter web output
COPY build/web /usr/share/nginx/html

# Copy Nginx configuration
COPY default.conf /etc/nginx/conf.d/default.conf

EXPOSE 5555

CMD ["nginx", "-g", "daemon off;"]
