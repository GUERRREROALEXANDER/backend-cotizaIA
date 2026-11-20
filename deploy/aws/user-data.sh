#!/bin/bash
# EC2 user data for Amazon Linux 2023 arm64.
# Secrets live only in SSM Parameter Store (/cotizaia/*).
# Runbook: deploy/aws/README.md.
set -euxo pipefail
exec > /var/log/cotizaia-setup.log 2>&1

dnf install -y docker git
systemctl enable --now docker

if [ ! -f /swapfile ]; then
  fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

mkdir -p /opt/cotizaia && cd /opt/cotizaia
[ -d backend-cotizaIA ] || git clone --depth 1 https://github.com/GUERRREROALEXANDER/backend-cotizaIA.git
[ -d frontend-cotizaIA ] || git clone --depth 1 https://github.com/GUERRREROALEXANDER/frontend-cotizaIA.git

cat > /opt/cotizaia/deploy.sh <<'EOF'
#!/bin/bash
# Re-run any time to pull the latest code and redeploy: sudo /opt/cotizaia/deploy.sh
set -euo pipefail
R=${AWS_REGION:-us-east-1}
p() { aws ssm get-parameter --region $R --name "/cotizaia/$1" --with-decryption --query Parameter.Value --output text 2>/dev/null || true; }
until [ -n "$(p CORS_ORIGIN)" ]; do echo "waiting for /cotizaia/CORS_ORIGIN"; sleep 15; done

BUCKET=$(p FRONTEND_BUCKET)
if [ -z "$BUCKET" ]; then
  echo "ERROR: /cotizaia/FRONTEND_BUCKET is empty or could not be read from SSM Parameter Store." >&2
  exit 1
fi

umask 077
cat > /opt/cotizaia/.env <<ENV
SPRING_PROFILES_ACTIVE=prod
SPRING_DATASOURCE_URL=jdbc:postgresql://cotizaia-db:5432/cotizaia
SPRING_DATASOURCE_USERNAME=cotizaia
SPRING_DATASOURCE_PASSWORD=$(p DB_PASSWORD)
JWT_SECRET=$(p JWT_SECRET)
DATA_ENCRYPTION_KEY=$(p DATA_ENCRYPTION_KEY)
BLIND_INDEX_KEY=$(p BLIND_INDEX_KEY)
CORS_ALLOWED_ORIGINS=$(p CORS_ORIGIN)
LLM_PROVIDER=groq
LLM_CHAIN=gemini,groq
GROQ_API_KEY=$(p GROQ_API_KEY)
GEMINI_API_KEY=$(p GEMINI_API_KEY)
VOYAGE_API_KEY=$(p VOYAGE_API_KEY)
DB_POOL_SIZE=10
JAVA_OPTS=-Xmx768m -XX:+UseSerialGC -Xss512k
ENV
umask 022

docker network inspect cotizaia >/dev/null 2>&1 || docker network create cotizaia
if ! docker ps -a --format '{{.Names}}' | grep -qx cotizaia-db; then
  docker run -d --name cotizaia-db --network cotizaia --restart unless-stopped \
    -e POSTGRES_DB=cotizaia -e POSTGRES_USER=cotizaia -e POSTGRES_PASSWORD="$(p DB_PASSWORD)" \
    -v cotizaia-pg:/var/lib/postgresql/data pgvector/pgvector:pg16
fi
until docker exec cotizaia-db pg_isready -U cotizaia -d cotizaia; do sleep 2; done

cd /opt/cotizaia/backend-cotizaIA && git pull --ff-only || true
docker build -t cotizaia-api .
docker rm -f cotizaia-api 2>/dev/null || true
docker run -d --name cotizaia-api --network cotizaia --restart unless-stopped \
  --env-file /opt/cotizaia/.env -p 8080:8080 --memory 1200m cotizaia-api

cd /opt/cotizaia/frontend-cotizaIA && git pull --ff-only || true
docker run --rm -v "$PWD":/app -w /app -e VITE_API_URL= node:22-bookworm-slim \
  sh -c "npm ci --no-audit --no-fund && npm run build"
aws s3 sync dist "s3://$BUCKET" --delete --region $R
DIST=$(p CF_DIST_ID)
[ -n "$DIST" ] && aws cloudfront create-invalidation --distribution-id "$DIST" --paths '/*' || true
echo "deploy done"
EOF
chmod 700 /opt/cotizaia/deploy.sh
/opt/cotizaia/deploy.sh
