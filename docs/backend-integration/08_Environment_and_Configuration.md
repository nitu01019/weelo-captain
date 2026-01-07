# Environment and Configuration

This document specifies environment variables, configuration requirements, and deployment setup for the backend.

---

## Table of Contents

1. [Environment Variables](#environment-variables)
2. [Development Environment](#development-environment)
3. [Staging Environment](#staging-environment)
4. [Production Environment](#production-environment)
5. [Database Configuration](#database-configuration)
6. [Redis Configuration](#redis-configuration)
7. [SMS Service Configuration](#sms-service-configuration)
8. [Firebase Cloud Messaging (FCM)](#firebase-cloud-messaging-fcm)
9. [Storage Configuration](#storage-configuration)
10. [Logging Configuration](#logging-configuration)
11. [CORS Configuration](#cors-configuration)
12. [SSL/TLS Configuration](#ssltls-configuration)

---

## Environment Variables

### Required Environment Variables

```bash
# Application
NODE_ENV=production                    # development, staging, production
APP_NAME=Weelo Captain API
APP_VERSION=1.0.0
PORT=3000

# Base URLs
API_BASE_URL=https://api.weelo.in/v1
WEBSOCKET_URL=wss://api.weelo.in/ws
FRONTEND_URL=https://app.weelo.in

# Database
DB_HOST=localhost
DB_PORT=3306
DB_NAME=weelo_production
DB_USER=weelo_user
DB_PASSWORD=<strong_password>
DB_CONNECTION_LIMIT=100
DB_SSL=true

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=<strong_password>
REDIS_DB=0
REDIS_TLS=true

# JWT Authentication
JWT_SECRET=<256-bit-secret-key>
JWT_REFRESH_SECRET=<256-bit-secret-key>
JWT_EXPIRY=604800                      # 7 days in seconds
JWT_REFRESH_EXPIRY=2592000             # 30 days in seconds

# SMS Service (Example: Twilio)
SMS_PROVIDER=twilio                    # twilio, aws_sns, msg91
SMS_ACCOUNT_SID=<twilio_account_sid>
SMS_AUTH_TOKEN=<twilio_auth_token>
SMS_FROM_NUMBER=+1234567890

# Firebase Cloud Messaging
FCM_SERVER_KEY=<firebase_server_key>
FCM_PROJECT_ID=weelo-captain

# AWS S3 (for file uploads)
AWS_REGION=ap-south-1
AWS_ACCESS_KEY_ID=<aws_access_key>
AWS_SECRET_ACCESS_KEY=<aws_secret_key>
AWS_S3_BUCKET=weelo-uploads
AWS_S3_REGION=ap-south-1

# Google Maps API (for geocoding)
GOOGLE_MAPS_API_KEY=<google_maps_api_key>

# Rate Limiting
RATE_LIMIT_WINDOW=60                   # Seconds
RATE_LIMIT_MAX_REQUESTS=100            # Requests per window

# Logging
LOG_LEVEL=info                         # debug, info, warn, error
LOG_FILE_PATH=/var/log/weelo/app.log

# Monitoring
SENTRY_DSN=<sentry_dsn>                # Error tracking
NEW_RELIC_LICENSE_KEY=<key>            # APM (optional)

# CORS
CORS_ORIGIN=https://app.weelo.in
CORS_CREDENTIALS=true
```

### Environment-Specific Variables

**Development**:
```bash
NODE_ENV=development
API_BASE_URL=http://localhost:3000/v1
DB_HOST=localhost
DB_NAME=weelo_dev
REDIS_HOST=localhost
LOG_LEVEL=debug
```

**Staging**:
```bash
NODE_ENV=staging
API_BASE_URL=https://staging-api.weelo.in/v1
DB_HOST=staging-db.weelo.in
DB_NAME=weelo_staging
REDIS_HOST=staging-redis.weelo.in
LOG_LEVEL=info
```

**Production**:
```bash
NODE_ENV=production
API_BASE_URL=https://api.weelo.in/v1
DB_HOST=prod-db.weelo.in
DB_NAME=weelo_production
REDIS_HOST=prod-redis.weelo.in
LOG_LEVEL=warn
```

---

## Development Environment

### Local Setup

**1. Prerequisites**:
```bash
# Install dependencies
- Node.js 18+ or Python 3.9+
- MySQL 8.0+ or PostgreSQL 13+
- Redis 6.0+
- Docker (optional)
```

**2. Environment File**:
Create `.env.development`:
```bash
NODE_ENV=development
PORT=3000
API_BASE_URL=http://localhost:3000/v1

# Use local services
DB_HOST=localhost
DB_PORT=3306
DB_NAME=weelo_dev
DB_USER=root
DB_PASSWORD=password

REDIS_HOST=localhost
REDIS_PORT=6379

# Test credentials
JWT_SECRET=dev_secret_key_change_in_production
SMS_PROVIDER=mock                     # Use mock SMS in development
FCM_SERVER_KEY=mock_fcm_key

LOG_LEVEL=debug
```

**3. Start Development Server**:
```bash
# Install dependencies
npm install

# Run database migrations
npm run migrate

# Seed test data
npm run seed

# Start server
npm run dev
```

**4. Mock Services** (Development Only):
```typescript
// Mock SMS service for development
class MockSMSService {
  async sendSMS(phone: string, message: string) {
    console.log(`[MOCK SMS] To: ${phone}, Message: ${message}`);
    return { success: true, messageId: 'mock_123' };
  }
}

// Mock FCM service
class MockFCMService {
  async sendNotification(token: string, notification: any) {
    console.log(`[MOCK FCM] To: ${token}, Notification:`, notification);
    return { success: true };
  }
}
```

---

## Staging Environment

### Purpose
- Test production-like environment
- Integration testing
- QA testing
- Performance testing

### Configuration

**Infrastructure**:
```yaml
# docker-compose.staging.yml
version: '3.8'
services:
  api:
    image: weelo-api:staging
    ports:
      - "3000:3000"
    environment:
      - NODE_ENV=staging
    env_file:
      - .env.staging
    depends_on:
      - db
      - redis
  
  db:
    image: mysql:8.0
    environment:
      MYSQL_DATABASE: weelo_staging
      MYSQL_USER: weelo_user
      MYSQL_PASSWORD: ${DB_PASSWORD}
    volumes:
      - staging_db_data:/var/lib/mysql
  
  redis:
    image: redis:6.0-alpine
    command: redis-server --requirepass ${REDIS_PASSWORD}
    volumes:
      - staging_redis_data:/data
```

**Deployment**:
```bash
# Build image
docker build -t weelo-api:staging .

# Deploy to staging
docker-compose -f docker-compose.staging.yml up -d

# Run migrations
docker-compose exec api npm run migrate

# Check logs
docker-compose logs -f api
```

---

## Production Environment

### Infrastructure Requirements

**Minimum Specifications**:
- **API Server**: 2 vCPU, 4GB RAM (scale horizontally as needed)
- **Database**: 4 vCPU, 16GB RAM, 100GB SSD
- **Redis**: 2 vCPU, 4GB RAM
- **Load Balancer**: AWS ELB or Nginx

**High Availability Setup**:
```
┌──────────────────────────────────────────────────┐
│              Load Balancer (ELB)                 │
└─────────┬────────────────────────────┬───────────┘
          │                            │
    ┌─────▼─────┐                ┌────▼──────┐
    │  API 1    │                │  API 2    │
    │  (Primary)│                │  (Backup) │
    └─────┬─────┘                └────┬──────┘
          │                           │
          └───────────┬───────────────┘
                      │
          ┌───────────▼───────────┐
          │   Database Cluster    │
          │  (Primary + Replicas) │
          └───────────┬───────────┘
                      │
          ┌───────────▼───────────┐
          │    Redis Cluster      │
          └───────────────────────┘
```

### Production Configuration

**.env.production**:
```bash
NODE_ENV=production
PORT=3000
API_BASE_URL=https://api.weelo.in/v1

# Production Database (RDS or self-hosted)
DB_HOST=prod-db-cluster.weelo.in
DB_PORT=3306
DB_NAME=weelo_production
DB_USER=weelo_prod_user
DB_PASSWORD=${PROD_DB_PASSWORD}        # From secrets manager
DB_CONNECTION_LIMIT=100
DB_SSL=true

# Production Redis (ElastiCache or self-hosted)
REDIS_HOST=prod-redis-cluster.weelo.in
REDIS_PORT=6379
REDIS_PASSWORD=${PROD_REDIS_PASSWORD}  # From secrets manager
REDIS_TLS=true

# Strong JWT secrets (generate with: openssl rand -base64 64)
JWT_SECRET=${PROD_JWT_SECRET}
JWT_REFRESH_SECRET=${PROD_JWT_REFRESH_SECRET}

# Production SMS service
SMS_PROVIDER=twilio
SMS_ACCOUNT_SID=${TWILIO_ACCOUNT_SID}
SMS_AUTH_TOKEN=${TWILIO_AUTH_TOKEN}
SMS_FROM_NUMBER=+911234567890

# Production FCM
FCM_SERVER_KEY=${PROD_FCM_SERVER_KEY}

# Production storage
AWS_ACCESS_KEY_ID=${PROD_AWS_ACCESS_KEY}
AWS_SECRET_ACCESS_KEY=${PROD_AWS_SECRET_KEY}
AWS_S3_BUCKET=weelo-prod-uploads

# Monitoring
SENTRY_DSN=${PROD_SENTRY_DSN}
LOG_LEVEL=warn
```

### Secrets Management

Use AWS Secrets Manager, HashiCorp Vault, or similar:

```typescript
// secrets.ts
import { SecretsManager } from 'aws-sdk';

const secretsManager = new SecretsManager({ region: 'ap-south-1' });

async function getSecret(secretName: string): Promise<string> {
  const secret = await secretsManager.getSecretValue({ SecretId: secretName }).promise();
  return secret.SecretString;
}

// Load secrets at startup
async function loadSecrets() {
  process.env.DB_PASSWORD = await getSecret('weelo/db-password');
  process.env.JWT_SECRET = await getSecret('weelo/jwt-secret');
  process.env.FCM_SERVER_KEY = await getSecret('weelo/fcm-key');
}
```

---

## Database Configuration

### MySQL Configuration

**Production my.cnf**:
```ini
[mysqld]
# Connection settings
max_connections = 500
max_connect_errors = 100
wait_timeout = 600
interactive_timeout = 600

# Buffer settings
innodb_buffer_pool_size = 8G
innodb_log_file_size = 512M
innodb_flush_log_at_trx_commit = 2

# Performance
innodb_flush_method = O_DIRECT
innodb_file_per_table = 1
query_cache_type = 0

# Binary logging (for replication)
server_id = 1
log_bin = /var/log/mysql/mysql-bin.log
binlog_format = ROW
expire_logs_days = 7

# SSL
ssl-ca = /etc/mysql/ssl/ca-cert.pem
ssl-cert = /etc/mysql/ssl/server-cert.pem
ssl-key = /etc/mysql/ssl/server-key.pem
```

### Connection Pooling

```typescript
// database.ts
import mysql from 'mysql2/promise';

const pool = mysql.createPool({
  host: process.env.DB_HOST,
  port: parseInt(process.env.DB_PORT || '3306'),
  user: process.env.DB_USER,
  password: process.env.DB_PASSWORD,
  database: process.env.DB_NAME,
  connectionLimit: parseInt(process.env.DB_CONNECTION_LIMIT || '100'),
  waitForConnections: true,
  queueLimit: 0,
  enableKeepAlive: true,
  keepAliveInitialDelay: 0,
  ssl: process.env.DB_SSL === 'true' ? {
    rejectUnauthorized: true
  } : false
});

export default pool;
```

### Database Migrations

```bash
# Create migration
npm run migrate:create add_driver_status_column

# Run migrations
npm run migrate:up

# Rollback migration
npm run migrate:down

# Check migration status
npm run migrate:status
```

---

## Redis Configuration

### Production redis.conf

```ini
# Network
bind 0.0.0.0
protected-mode yes
port 6379
requirepass <strong_password>

# Memory
maxmemory 4gb
maxmemory-policy allkeys-lru

# Persistence
save 900 1
save 300 10
save 60 10000
appendonly yes
appendfsync everysec

# SSL/TLS
tls-port 6380
tls-cert-file /etc/redis/ssl/redis.crt
tls-key-file /etc/redis/ssl/redis.key
tls-ca-cert-file /etc/redis/ssl/ca.crt
```

### Redis Client Configuration

```typescript
// redis.ts
import Redis from 'ioredis';

const redis = new Redis({
  host: process.env.REDIS_HOST,
  port: parseInt(process.env.REDIS_PORT || '6379'),
  password: process.env.REDIS_PASSWORD,
  db: parseInt(process.env.REDIS_DB || '0'),
  tls: process.env.REDIS_TLS === 'true' ? {} : undefined,
  maxRetriesPerRequest: 3,
  enableReadyCheck: true,
  enableOfflineQueue: false,
  retryStrategy(times) {
    const delay = Math.min(times * 50, 2000);
    return delay;
  }
});

redis.on('error', (err) => {
  console.error('Redis connection error:', err);
});

redis.on('connect', () => {
  console.log('Redis connected');
});

export default redis;
```

---

## SMS Service Configuration

### Twilio Configuration

```typescript
// sms/twilio.ts
import twilio from 'twilio';

const client = twilio(
  process.env.SMS_ACCOUNT_SID,
  process.env.SMS_AUTH_TOKEN
);

export async function sendSMS(to: string, message: string) {
  try {
    const result = await client.messages.create({
      body: message,
      from: process.env.SMS_FROM_NUMBER,
      to: to
    });
    
    return {
      success: true,
      messageId: result.sid
    };
  } catch (error) {
    console.error('SMS send failed:', error);
    throw new Error('Failed to send SMS');
  }
}
```

### AWS SNS Configuration (Alternative)

```typescript
// sms/aws-sns.ts
import { SNS } from 'aws-sdk';

const sns = new SNS({
  region: process.env.AWS_REGION,
  accessKeyId: process.env.AWS_ACCESS_KEY_ID,
  secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY
});

export async function sendSMS(to: string, message: string) {
  const params = {
    Message: message,
    PhoneNumber: to,
    MessageAttributes: {
      'AWS.SNS.SMS.SMSType': {
        DataType: 'String',
        StringValue: 'Transactional'
      }
    }
  };
  
  const result = await sns.publish(params).promise();
  return {
    success: true,
    messageId: result.MessageId
  };
}
```

---

## Firebase Cloud Messaging (FCM)

### FCM Configuration

```typescript
// fcm/index.ts
import admin from 'firebase-admin';

admin.initializeApp({
  credential: admin.credential.cert({
    projectId: process.env.FCM_PROJECT_ID,
    clientEmail: process.env.FCM_CLIENT_EMAIL,
    privateKey: process.env.FCM_PRIVATE_KEY?.replace(/\\n/g, '\n')
  })
});

export async function sendPushNotification(
  fcmToken: string,
  notification: {
    title: string;
    body: string;
    data?: Record<string, string>;
  }
) {
  const message = {
    token: fcmToken,
    notification: {
      title: notification.title,
      body: notification.body
    },
    data: notification.data || {},
    android: {
      priority: 'high' as const,
      notification: {
        sound: 'default',
        channelId: 'trip_notifications'
      }
    }
  };
  
  try {
    const response = await admin.messaging().send(message);
    return { success: true, messageId: response };
  } catch (error) {
    console.error('FCM send failed:', error);
    throw error;
  }
}
```

### Update FCM Token Endpoint

```typescript
app.put('/driver/fcm-token', authenticateToken, async (req, res) => {
  const { userId, fcmToken, deviceId } = req.body;
  
  // Store FCM token
  await db.query(
    'UPDATE users SET fcm_token = ?, device_id = ? WHERE id = ?',
    [fcmToken, deviceId, userId]
  );
  
  res.json({ success: true, message: 'FCM token updated' });
});
```

---

## Storage Configuration

### AWS S3 for File Uploads

```typescript
// storage/s3.ts
import AWS from 'aws-sdk';
import { v4 as uuidv4 } from 'uuid';

const s3 = new AWS.S3({
  region: process.env.AWS_S3_REGION,
  accessKeyId: process.env.AWS_ACCESS_KEY_ID,
  secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY
});

export async function uploadFile(
  file: Buffer,
  fileName: string,
  contentType: string
): Promise<string> {
  const key = `${uuidv4()}-${fileName}`;
  
  const params = {
    Bucket: process.env.AWS_S3_BUCKET,
    Key: key,
    Body: file,
    ContentType: contentType,
    ACL: 'public-read'
  };
  
  await s3.upload(params).promise();
  
  return `https://${process.env.AWS_S3_BUCKET}.s3.${process.env.AWS_S3_REGION}.amazonaws.com/${key}`;
}

// Generate presigned URL for secure uploads
export function generatePresignedUrl(key: string): string {
  return s3.getSignedUrl('putObject', {
    Bucket: process.env.AWS_S3_BUCKET,
    Key: key,
    Expires: 3600, // 1 hour
    ContentType: 'image/jpeg'
  });
}
```

---

## Logging Configuration

### Winston Logger

```typescript
// logger.ts
import winston from 'winston';

const logger = winston.createLogger({
  level: process.env.LOG_LEVEL || 'info',
  format: winston.format.combine(
    winston.format.timestamp(),
    winston.format.errors({ stack: true }),
    winston.format.json()
  ),
  defaultMeta: { service: 'weelo-api' },
  transports: [
    // Console logging
    new winston.transports.Console({
      format: winston.format.combine(
        winston.format.colorize(),
        winston.format.simple()
      )
    }),
    
    // File logging
    new winston.transports.File({
      filename: process.env.LOG_FILE_PATH || '/var/log/weelo/app.log',
      maxsize: 10485760, // 10MB
      maxFiles: 10
    }),
    
    // Error logging
    new winston.transports.File({
      filename: '/var/log/weelo/error.log',
      level: 'error'
    })
  ]
});

export default logger;
```

### Request Logging Middleware

```typescript
import morgan from 'morgan';

app.use(morgan('combined', {
  stream: {
    write: (message) => logger.info(message.trim())
  }
}));
```

---

## CORS Configuration

```typescript
// cors.ts
import cors from 'cors';

const corsOptions = {
  origin: (origin, callback) => {
    const allowedOrigins = process.env.CORS_ORIGIN?.split(',') || [];
    
    if (!origin || allowedOrigins.includes(origin)) {
      callback(null, true);
    } else {
      callback(new Error('Not allowed by CORS'));
    }
  },
  credentials: true,
  methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
  allowedHeaders: ['Content-Type', 'Authorization', 'Idempotency-Key'],
  exposedHeaders: ['X-RateLimit-Limit', 'X-RateLimit-Remaining'],
  maxAge: 86400 // 24 hours
};

app.use(cors(corsOptions));
```

---

## SSL/TLS Configuration

### Nginx SSL Configuration

```nginx
server {
    listen 443 ssl http2;
    server_name api.weelo.in;
    
    # SSL certificates
    ssl_certificate /etc/ssl/certs/weelo.crt;
    ssl_certificate_key /etc/ssl/private/weelo.key;
    
    # SSL protocols and ciphers
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
    ssl_prefer_server_ciphers on;
    
    # OCSP stapling
    ssl_stapling on;
    ssl_stapling_verify on;
    
    # Security headers
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header X-Frame-Options "DENY" always;
    add_header X-Content-Type-Options "nosniff" always;
    
    location / {
        proxy_pass http://localhost:3000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}

# Redirect HTTP to HTTPS
server {
    listen 80;
    server_name api.weelo.in;
    return 301 https://$server_name$request_uri;
}
```

---

## Summary: Configuration Checklist

**Environment Variables**:
- ✅ All required variables defined
- ✅ Secrets stored securely (not in code)
- ✅ Environment-specific configurations

**Services**:
- ✅ Database configured with connection pooling
- ✅ Redis configured with persistence
- ✅ SMS service configured and tested
- ✅ FCM configured for push notifications

**Security**:
- ✅ SSL/TLS enabled
- ✅ Strong JWT secrets
- ✅ CORS properly configured
- ✅ Rate limiting enabled

**Monitoring**:
- ✅ Logging configured
- ✅ Error tracking (Sentry)
- ✅ APM configured (optional)

---

**Next**: See `09_Integration_Checklist.md` for step-by-step implementation guide.
