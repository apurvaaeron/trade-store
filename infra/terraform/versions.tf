terraform {
  required_version = ">= 1.6"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 8.12"
    }
    confluent = {
      source  = "confluentinc/confluent"
      version = "~> 2.0"
    }
    mongodbatlas = {
      source  = "mongodb/mongodbatlas"
      version = "~> 1.21"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # State holds generated passwords and API keys. For anything beyond a one-off demo, keep it in a
  # versioned, access-controlled GCS bucket instead of on a laptop:
  # backend "gcs" {
  #   bucket = "<state-bucket>"
  #   prefix = "trade-store"
  # }
}

provider "google" {
  project = var.project_id
  region  = var.region
}

# Credentials come from CONFLUENT_CLOUD_API_KEY / CONFLUENT_CLOUD_API_SECRET.
provider "confluent" {}

# Credentials come from MONGODB_ATLAS_PUBLIC_KEY / MONGODB_ATLAS_PRIVATE_KEY.
provider "mongodbatlas" {}
