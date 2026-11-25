resource "google_artifact_registry_repository" "images" {
  repository_id = var.service_name
  location      = var.region
  format        = "DOCKER"
  description   = "Trade store container images"

  depends_on = [google_project_service.required]
}

locals {
  image = "${var.region}-docker.pkg.dev/${var.project_id}/${google_artifact_registry_repository.images.repository_id}/trade-store:${var.image_tag}"
}
