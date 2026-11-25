locals {
  mongo_database = "tradestore"
  mongo_srv_host = trimprefix(mongodbatlas_advanced_cluster.audit.connection_strings[0].standard_srv, "mongodb+srv://")
  mongo_uri      = "mongodb+srv://${mongodbatlas_database_user.app.username}:${random_password.mongo.result}@${local.mongo_srv_host}/${local.mongo_database}?retryWrites=true&w=majority"
}

resource "mongodbatlas_project" "this" {
  name   = var.service_name
  org_id = var.atlas_org_id
}

# M0 is the free shared tier (512 MB); use a dedicated M10+ cluster for production workloads.
resource "mongodbatlas_advanced_cluster" "audit" {
  project_id   = mongodbatlas_project.this.id
  name         = "${var.service_name}-audit"
  cluster_type = "REPLICASET"

  replication_specs {
    region_configs {
      provider_name         = "TENANT"
      backing_provider_name = "GCP"
      region_name           = var.atlas_region
      priority              = 7

      electable_specs {
        instance_size = "M0"
      }
    }
  }
}

resource "random_password" "mongo" {
  length  = 32
  special = false
}

resource "mongodbatlas_database_user" "app" {
  project_id         = mongodbatlas_project.this.id
  username           = "${var.service_name}-app"
  password           = random_password.mongo.result
  auth_database_name = "admin"

  roles {
    role_name     = "readWrite"
    database_name = local.mongo_database
  }

  scopes {
    name = mongodbatlas_advanced_cluster.audit.name
    type = "CLUSTER"
  }
}

resource "mongodbatlas_project_ip_access_list" "app" {
  for_each = toset(var.atlas_access_cidrs)

  project_id = mongodbatlas_project.this.id
  cidr_block = each.value
  comment    = "${var.service_name} Cloud Run egress"
}
