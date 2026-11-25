output "service_url" {
  description = "Base URL of the trade store API."
  value       = google_cloud_run_v2_service.app.uri
}

output "swagger_url" {
  description = "Swagger UI."
  value       = "${google_cloud_run_v2_service.app.uri}/swagger-ui.html"
}

output "image_repository" {
  description = "Artifact Registry path that images are pushed to."
  value       = local.image_repository
}

output "image" {
  description = "Image reference Cloud Run deploys (as of the last apply)."
  value       = local.image
}

output "cloud_sql_instance" {
  description = "Cloud SQL instance connection name."
  value       = google_sql_database_instance.postgres.connection_name
}

output "kafka_bootstrap_servers" {
  description = "Confluent Cloud bootstrap servers."
  value       = local.kafka_bootstrap_servers
}

output "atlas_cluster" {
  description = "MongoDB Atlas audit cluster name."
  value       = mongodbatlas_advanced_cluster.audit.name
}
