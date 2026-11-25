resource "google_service_account" "run" {
  account_id   = "${var.service_name}-run"
  display_name = "${var.service_name} Cloud Run runtime"

  depends_on = [google_project_service.required]
}

resource "google_cloud_run_v2_service" "app" {
  name                = var.service_name
  location            = var.region
  ingress             = "INGRESS_TRAFFIC_ALL"
  deletion_protection = var.deletion_protection

  template {
    service_account = google_service_account.run.email

    # The Kafka consumer and the expiry scheduler need an always-running instance.
    scaling {
      min_instance_count = 1
      max_instance_count = var.max_instances
    }

    vpc_access {
      egress = "PRIVATE_RANGES_ONLY"

      network_interfaces {
        network    = google_compute_network.vpc.id
        subnetwork = google_compute_subnetwork.run.id
      }
    }

    containers {
      image = local.image

      ports {
        container_port = 8080
      }

      resources {
        cpu_idle          = false
        startup_cpu_boost = true
        limits = {
          cpu    = "1"
          memory = "1Gi"
        }
      }

      env {
        name  = "DB_URL"
        value = "jdbc:postgresql://${google_sql_database_instance.postgres.private_ip_address}:5432/${google_sql_database.tradestore.name}?sslmode=require"
      }

      env {
        name  = "DB_USERNAME"
        value = google_sql_user.app.name
      }

      env {
        name = "DB_PASSWORD"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.runtime["db-password"].secret_id
            version = "latest"
          }
        }
      }

      env {
        name = "MONGO_URI"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.runtime["mongo-uri"].secret_id
            version = "latest"
          }
        }
      }

      env {
        name  = "KAFKA_BOOTSTRAP_SERVERS"
        value = local.kafka_bootstrap_servers
      }

      env {
        name = "SPRING_APPLICATION_JSON"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.runtime["kafka-config"].secret_id
            version = "latest"
          }
        }
      }

      env {
        name  = "TRADESTORE_ZONE"
        value = var.business_zone
      }

      startup_probe {
        initial_delay_seconds = 10
        period_seconds        = 10
        timeout_seconds       = 5
        failure_threshold     = 30

        http_get {
          path = "/actuator/health/readiness"
          port = 8080
        }
      }

      liveness_probe {
        period_seconds    = 30
        timeout_seconds   = 5
        failure_threshold = 3

        http_get {
          path = "/actuator/health/liveness"
          port = 8080
        }
      }
    }
  }

  depends_on = [
    google_secret_manager_secret_version.runtime,
    google_secret_manager_secret_iam_member.runtime_accessor,
    google_sql_database.tradestore,
    confluent_kafka_topic.topics,
    confluent_kafka_acl.app,
    mongodbatlas_project_ip_access_list.app,
  ]
}

resource "google_cloud_run_v2_service_iam_member" "public_invoker" {
  count = var.allow_unauthenticated ? 1 : 0

  name     = google_cloud_run_v2_service.app.name
  location = google_cloud_run_v2_service.app.location
  role     = "roles/run.invoker"
  member   = "allUsers"
}
