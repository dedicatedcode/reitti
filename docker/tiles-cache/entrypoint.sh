#!/bin/sh
if [ -z "$RESOLVER_IP" ]; then
  export RESOLVER_IP=$(grep nameserver /etc/resolv.conf | awk '{print $2}' | head -n 1)
fi

echo "Using resolver IP: $RESOLVER_IP"

envsubst '${RESOLVER_IP}' < /etc/nginx/nginx.conf.template > /etc/nginx/nginx.conf

exec "$@"