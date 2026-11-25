variable "project_id" {
  description = "GCP project that hosts Cloud Run, Cloud SQL, Artifact Registry and Secret Manager."
  type        = string
}

variable "region" {
  description = "GCP region for every GCP resource and the Confluent Kafka cluster."
  type        = string
  default     = "us-central1"
}

variable "service_name" {
  description = "Prefix for resource names; also the Cloud Run service name."
  type        = string
  default     = "trade-store"

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{2,20}$", var.service_name))
    error_message = "Use 3-21 lowercase letters, digits or hyphens, starting with a letter."
  }
}

variable "image_tag" {
  description = "Tag of the trade-store image in this project's Artifact Registry repository."
  type        = string
  default     = "latest"
}

variable "business_zone" {
  description = "Time zone that defines the business date (TRADESTORE_ZONE)."
  type        = string
  default     = "UTC"
}

variable "max_instances" {
  description = "Maximum Cloud Run instances. Kafka partitions (3) cap useful consumer parallelism."
  type        = number
  default     = 1

  validation {
    condition     = var.max_instances >= 1 && var.max_instances <= 3
    error_message = "Use 1-3 instances; more than the partition count would sit idle as consumers."
  }
}

variable "allow_unauthenticated" {
  description = "Grant allUsers the Cloud Run invoker role. The API has no authentication, so enable only for demos."
  type        = bool
  default     = false
}

variable "deletion_protection" {
  description = "Protect Cloud SQL and Cloud Run from terraform destroy. Keep true outside short-lived demos."
  type        = bool
  default     = true
}

variable "db_tier" {
  description = "Cloud SQL machine tier."
  type        = string
  default     = "db-f1-micro"
}

variable "db_availability_type" {
  description = "ZONAL for demos, REGIONAL for a highly available primary with automatic failover."
  type        = string
  default     = "ZONAL"

  validation {
    condition     = contains(["ZONAL", "REGIONAL"], var.db_availability_type)
    error_message = "Must be ZONAL or REGIONAL."
  }
}

variable "run_subnet_cidr" {
  description = "Subnet used by Cloud Run Direct VPC egress to reach Cloud SQL on its private IP."
  type        = string
  default     = "10.10.0.0/24"
}

variable "atlas_org_id" {
  description = "MongoDB Atlas organization ID in which the audit project is created."
  type        = string
}

variable "atlas_region" {
  description = "Atlas region name for the GCP-backed M0 cluster (CENTRAL_US is us-central1)."
  type        = string
  default     = "CENTRAL_US"
}

variable "atlas_access_cidrs" {
  description = "CIDRs allowed to reach Atlas. Cloud Run egress IPs are dynamic, so a demo needs 0.0.0.0/0; production uses Cloud NAT with a static IP or Private Service Connect."
  type        = list(string)
}
