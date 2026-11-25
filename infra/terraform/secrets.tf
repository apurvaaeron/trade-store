locals {
  # Kafka client security is injected as SPRING_APPLICATION_JSON so application.yml stays unchanged.
  kafka_client_config = jsonencode({
    spring = {
      kafka = {
        security = { protocol = "SASL_SSL" }
        properties = {
          "sasl.mechanism"   = "PLAIN"
          "sasl.jaas.config" = "org.apache.kafka.common.security.plain.PlainLoginModule required username='${confluent_api_key.app.id}' password='${confluent_api_key.app.secret}';"
        }
      }
    }
  })

  runtime_secrets = {
    db-password  = random_password.db.result
    mongo-uri    = local.mongo_uri
    kafka-config = local.kafka_client_config
  }
}

resource "google_secret_manager_secret" "runtime" {
  for_each = toset(keys(local.runtime_secrets))

  secret_id = "${var.service_name}-${each.key}"

  replication {
    auto {}
  }

  depends_on = [google_project_service.required]
}

resource "google_secret_manager_secret_version" "runtime" {
  for_each = google_secret_manager_secret.runtime

  secret      = each.value.id
  secret_data = local.runtime_secrets[each.key]
}

resource "google_secret_manager_secret_iam_member" "runtime_accessor" {
  for_each = google_secret_manager_secret.runtime

  secret_id = each.value.id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.run.email}"
}
