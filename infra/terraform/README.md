# GCP deployment (Terraform)

Deploys the trade store to Google Cloud with managed Kafka and MongoDB. The application code and `application.yml`
are unchanged: every setting is injected through the environment variables the app already reads.

| Component | Service | Terraform file |
|---|---|---|
| Application | Cloud Run (always one instance on, CPU always allocated for the Kafka consumer and the scheduler) | `cloudrun.tf` |
| Current state (PostgreSQL 16) | Cloud SQL, **private IP only**, TLS required, backups and point-in-time recovery | `cloudsql.tf` |
| Network | VPC, Direct VPC egress subnet, private services access for Cloud SQL | `network.tf` |
| Kafka | Confluent Cloud Basic cluster on GCP; topics `trades.inbound` (3), `trades.rejected` (3), `trades.inbound.dlt` (1); least-privilege ACLs for the app | `confluent.tf` |
| Audit (MongoDB) | MongoDB Atlas M0 on GCP, database user limited to `readWrite` on `tradestore` | `atlas.tf` |
| Secrets | Secret Manager (DB password, Mongo URI, Kafka SASL config), readable only by the Cloud Run service account | `secrets.tf` |
| Images | Artifact Registry | `artifact_registry.tf` |

```
Client ──HTTPS──> Cloud Run (trade-store) ──private IP──> Cloud SQL PostgreSQL
                      │  ├──SASL_SSL──> Confluent Cloud Kafka
                      │  └──TLS───────> MongoDB Atlas
                      └── secrets from Secret Manager
```

CI runs `terraform fmt -check` and `terraform validate` on every pull request. Nothing is applied automatically.

## Prerequisites

- Terraform >= 1.6 and the gcloud CLI. Cloud Shell and the repo's Codespace have both.
- A GCP project with billing enabled. Your account needs Owner, or Editor plus Project IAM Admin and Secret Manager Admin.
- A Confluent Cloud **Cloud resource management** API key (Confluent Cloud → API keys → Add key → My account).
- A MongoDB Atlas **organization** API key with *Organization Project Creator* (Atlas → Organization → Access Manager →
  API Keys), plus your Atlas organization ID.

```bash
gcloud auth application-default login
export CONFLUENT_CLOUD_API_KEY=... CONFLUENT_CLOUD_API_SECRET=...
export MONGODB_ATLAS_PUBLIC_KEY=... MONGODB_ATLAS_PRIVATE_KEY=...
```

<details><summary>Windows PowerShell</summary>

```powershell
gcloud auth application-default login
$env:CONFLUENT_CLOUD_API_KEY = "..."; $env:CONFLUENT_CLOUD_API_SECRET = "..."
$env:MONGODB_ATLAS_PUBLIC_KEY = "..."; $env:MONGODB_ATLAS_PRIVATE_KEY = "..."
```
</details>

## Deploy

```bash
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars   # set project_id and atlas_org_id
terraform init

# 1. Create the image repository first; Cloud Run cannot start without an image.
terraform apply -target=google_artifact_registry_repository.images

# 2. Build the image with Cloud Build and push it to that repository.
gcloud builds submit ../.. --tag "$(terraform output -raw image)"

# 3. Create everything else (about 10-15 minutes, mostly Cloud SQL).
terraform apply

# 4. Smoke test.
URL=$(terraform output -raw service_url)
curl -s "$URL/actuator/health"
curl -s -X POST "$URL/api/v1/trades" -H 'Content-Type: application/json' \
  -d '{"tradeId":"T1","version":1,"counterPartyId":"CP-1","bookId":"B1","maturityDate":"20/05/2030"}'
```

The app runs Flyway migrations on start-up. It finds the Kafka topics already created by Terraform.

To roll out a new version, push a new tag in step 2 and run `terraform apply -var image_tag=<tag>`.

## Tear down

```bash
terraform destroy
```

This needs `deletion_protection = false`, which the example tfvars sets. Confluent and Atlas resources are deleted too.

## Cost (estimate; check the GCP, Confluent and Atlas pricing pages)

| Item | Demo settings | Approx. per day |
|---|---|---|
| Cloud Run, 1 vCPU / 1 GiB, always on | `min_instance_count = 1`, CPU always allocated | about $2–3; the monthly free tier lowers this |
| Cloud SQL `db-f1-micro`, zonal, 10 GB | no free tier | about $0.40 |
| Artifact Registry, Secret Manager, Cloud Build | within free tiers | about $0 |
| Confluent Cloud Basic | sign-up credits | $0 within credits |
| MongoDB Atlas M0 | free tier | $0 |

New GCP accounts get trial credit that covers a demo day. Run `terraform destroy` afterwards.

## Demo defaults vs production

| Setting | Demo (`terraform.tfvars.example`) | Production |
|---|---|---|
| API access | `allow_unauthenticated = true` (the API has no auth) | Keep `false`; front it with IAP or API Gateway and OAuth |
| Atlas network access | `0.0.0.0/0` (Cloud Run egress IPs are dynamic) | Cloud NAT with a static IP, or Private Service Connect |
| Cloud SQL | `db-f1-micro`, `ZONAL` | Dedicated tier, `REGIONAL` (automatic failover) |
| Kafka | Confluent Basic, single zone | Standard/Dedicated multi-zone with private networking, or Google Managed Service for Apache Kafka |
| MongoDB | Atlas M0 (shared) | M10+ dedicated cluster |
| Deletion protection | `false` | `true` (the variable's default) |
| Terraform state | Local file | GCS backend (see `versions.tf`); state contains generated passwords and API keys |
| Pipeline credentials | Your user credentials | GitHub Actions with Workload Identity Federation, no service-account keys |
| Instances | `max_instances = 1` | Up to 3 (partition count); add ShedLock so one replica runs the nightly expiry |

## Status

`terraform validate` passes against the pinned providers (`.terraform.lock.hcl`). The configuration has **not** been
applied to a real project yet. Expect the first apply to surface account-level constraints, such as organization
policies that block `allUsers`.
