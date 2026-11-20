# Deploy CotizaIA on AWS

## Architecture

- Route HTTPS traffic through CloudFront.
- Send the default behavior to a private S3 bucket through Origin Access Control (OAC) for the static frontend.
- Send `/api/*` and `/actuator/health` to the EC2 origin over HTTP on port 8080.
- Use one EC2 t4g.small running Amazon Linux 2023 arm64, with Docker containers for the backend and PostgreSQL 16 with pgvector on the same Docker network; store database data in the named volume `cotizaia-pg`.
- Store secrets as SSM Parameter Store SecureString parameters under `/cotizaia/`.

## SSM parameters

Create these parameters in the deployment region under `/cotizaia/`. Use SecureString for secrets.

| Name | Required? | What it is |
|---|---|---|
| `/cotizaia/DB_PASSWORD` | Yes | PostgreSQL password, also used by the backend. |
| `/cotizaia/JWT_SECRET` | Yes | JWT signing secret of at least 32 characters. |
| `/cotizaia/DATA_ENCRYPTION_KEY` | Yes | Data encryption key; generate with `openssl rand -base64 32`. |
| `/cotizaia/BLIND_INDEX_KEY` | Yes | Blind index key; generate separately with `openssl rand -base64 32`. |
| `/cotizaia/CORS_ORIGIN` | Yes | The HTTPS CloudFront URL, with no trailing slash. |
| `/cotizaia/GROQ_API_KEY` | Yes | Groq API key for the configured provider chain. |
| `/cotizaia/GEMINI_API_KEY` | Yes | Gemini API key for the configured provider chain. |
| `/cotizaia/VOYAGE_API_KEY` | No | Optional Voyage embeddings API key. |
| `/cotizaia/FRONTEND_BUCKET` | Yes | Name of the private S3 frontend bucket; deployment fails if empty or unreadable. |
| `/cotizaia/CF_DIST_ID` | Yes | CloudFront distribution ID used to invalidate the frontend cache. |

## IAM instance role

Create an EC2 instance role and attach it through an instance profile. Grant only:

- `AmazonSSMManagedInstanceCore` for Session Manager access without SSH.
- `ssm:GetParameter` on `arn:aws:ssm:<region>:<account-id>:parameter/cotizaia/*`.
- `s3:ListBucket` on `arn:aws:s3:::<frontend-bucket>`.
- `s3:PutObject` and `s3:DeleteObject` on `arn:aws:s3:::<frontend-bucket>/*`.
- `cloudfront:CreateInvalidation` on `arn:aws:cloudfront::<account-id>:distribution/<distribution-id>`.

Add `kms:Decrypt` on the relevant key only if the parameters use a customer managed KMS key instead of `aws/ssm`.

## Network

Allow inbound TCP 8080 in the instance security group only from the AWS managed prefix list
`com.amazonaws.global.cloudfront.origin-facing`. Do not open port 22. Require IMDSv2 on the instance.

## CloudFront settings

- Configure the S3 origin with OAC and a bucket policy granting CloudFront read access only for this distribution, using an `AWS:SourceArn` condition.
- Set the `api` origin to the instance's public DNS, HTTP only, port 8080.
- For `/api/*` and `/actuator/health`, select the managed `CachingDisabled` cache policy and `AllViewerExceptHostHeader` origin request policy; allow all HTTP methods.
- For the default behavior, select `CachingOptimized` and set the default root object to `index.html`.
- Redirect viewer HTTP requests to HTTPS. Attach a response headers policy to the behaviors with the exact values from `frontend-cotizaIA/vercel.json`:

| Header | Value |
|---|---|
| `Content-Security-Policy` | `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self'; worker-src 'self' blob:; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'` |
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` |
| `X-Content-Type-Options` | `nosniff` |
| `X-Frame-Options` | `DENY` |
| `Referrer-Policy` | `strict-origin-when-cross-origin` |
| `Permissions-Policy` | `camera=(), microphone=(), geolocation=(), payment=()` |

The frontend uses hash routing, so no SPA error-page rewrite is needed. Do not add custom error responses:
they would also rewrite API 403/404 responses.

## Launch

1. Create the SSM parameters, filling `CORS_ORIGIN`, `FRONTEND_BUCKET` and `CF_DIST_ID` once the frontend resources exist. Use the same region for the parameters and instance.
2. Create the private bucket, OAC, response headers policy and distribution with the settings above. Use a temporary origin hostname for `api` until the instance exists, then finish the SSM parameters.
3. Launch an EC2 t4g.small with Amazon Linux 2023 arm64, the instance role, security group and required IMDSv2. Supply [`user-data.sh`](user-data.sh) as user data. The script defaults to `us-east-1`; for another region, set `AWS_REGION` for the bootstrap and subsequent redeploys.
4. Set the CloudFront `api` origin to the instance's public DNS and wait for the distribution to reach `Deployed`.
5. Open a Session Manager shell and follow first boot with `sudo cat /var/log/cotizaia-setup.log`.
6. Check `https://<distribution-domain>/actuator/health`.

## Operate

Open a shell through EC2 **Connect > Session Manager**.

- Read backend logs: `sudo docker logs -f cotizaia-api`.
- Redeploy: `sudo /opt/cotizaia/deploy.sh`. This pulls both repositories, rebuilds the backend image, rebuilds the frontend, syncs S3 and invalidates CloudFront.

## Stop and start

Stop the instance when needed: its database volume remains on disk. After starting it, update the CloudFront
`api` origin to the new public DNS; allow about five minutes for the distribution to deploy.
Use an Elastic IP to keep the public DNS name stable at a small hourly cost.

## Email (optional, spec 16)

Mail is off by default. If you add `MAIL_ENABLED=true` to the backend environment, configure SMTP and set
`APP_PUBLIC_URL=https://<distribution-domain>/#`. Verification links then open the hash route
`#/verify-email?token=...`; S3 has no `/verify-email` object. Persist these optional settings in the environment
generation in `/opt/cotizaia/deploy.sh`, which recreates `.env` on every redeploy; keep SMTP secrets in SSM.

## Known limitations

- CloudFront to EC2 traffic is plain HTTP inside AWS. Add an ALB with an ACM certificate, or configure TLS on the instance, to encrypt that connection.
- A single instance hosts the database and in-memory rate-limit counters; neither is highly available.
- The frontend build runs on the instance.

## Tear down

1. Disable the distribution, wait for `Deployed`, then delete it. Delete its OAC and response headers policy afterward.
2. Terminate the instance; its volume, including the Docker database volume, goes with it. Ensure the root volume is configured to delete on termination.
3. Empty and delete the frontend bucket.
4. Delete the security group, instance profile and role, and the `/cotizaia/*` parameters.
