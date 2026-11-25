locals {
  # Names and partition counts match tradestore.topics in application.yml.
  kafka_topics = {
    "trades.inbound"     = 3
    "trades.rejected"    = 3
    "trades.inbound.dlt" = 1
  }

  kafka_consumer_group = "trade-store"

  # Least privilege for the application principal: produce/consume the trades.* topics and join its group.
  kafka_app_acls = {
    topic_read            = { resource_type = "TOPIC", resource_name = "trades.", pattern_type = "PREFIXED", operation = "READ" }
    topic_write           = { resource_type = "TOPIC", resource_name = "trades.", pattern_type = "PREFIXED", operation = "WRITE" }
    topic_describe        = { resource_type = "TOPIC", resource_name = "trades.", pattern_type = "PREFIXED", operation = "DESCRIBE" }
    group_read            = { resource_type = "GROUP", resource_name = local.kafka_consumer_group, pattern_type = "LITERAL", operation = "READ" }
    cluster_describe      = { resource_type = "CLUSTER", resource_name = "kafka-cluster", pattern_type = "LITERAL", operation = "DESCRIBE" }
    cluster_idempotent_wr = { resource_type = "CLUSTER", resource_name = "kafka-cluster", pattern_type = "LITERAL", operation = "IDEMPOTENT_WRITE" }
  }

  kafka_bootstrap_servers = trimprefix(confluent_kafka_cluster.this.bootstrap_endpoint, "SASL_SSL://")
}

resource "confluent_environment" "this" {
  display_name = var.service_name
}

resource "confluent_kafka_cluster" "this" {
  display_name = "${var.service_name}-kafka"
  availability = "SINGLE_ZONE"
  cloud        = "GCP"
  region       = var.region
  basic {}

  environment {
    id = confluent_environment.this.id
  }
}

# Administrative principal used only by Terraform to manage topics and ACLs.
resource "confluent_service_account" "manager" {
  display_name = "${var.service_name}-manager"
  description  = "Terraform-managed topic and ACL administration for ${var.service_name}"
}

resource "confluent_role_binding" "manager_cluster_admin" {
  principal   = "User:${confluent_service_account.manager.id}"
  role_name   = "CloudClusterAdmin"
  crn_pattern = confluent_kafka_cluster.this.rbac_crn
}

resource "confluent_api_key" "manager" {
  display_name = "${var.service_name}-manager"

  owner {
    id          = confluent_service_account.manager.id
    api_version = confluent_service_account.manager.api_version
    kind        = confluent_service_account.manager.kind
  }

  managed_resource {
    id          = confluent_kafka_cluster.this.id
    api_version = confluent_kafka_cluster.this.api_version
    kind        = confluent_kafka_cluster.this.kind

    environment {
      id = confluent_environment.this.id
    }
  }

  depends_on = [confluent_role_binding.manager_cluster_admin]
}

resource "confluent_kafka_topic" "topics" {
  for_each = local.kafka_topics

  topic_name       = each.key
  partitions_count = each.value
  rest_endpoint    = confluent_kafka_cluster.this.rest_endpoint

  kafka_cluster {
    id = confluent_kafka_cluster.this.id
  }

  credentials {
    key    = confluent_api_key.manager.id
    secret = confluent_api_key.manager.secret
  }
}

# Runtime principal used by the application.
resource "confluent_service_account" "app" {
  display_name = "${var.service_name}-app"
  description  = "Runtime principal for the ${var.service_name} Cloud Run service"
}

resource "confluent_api_key" "app" {
  display_name = "${var.service_name}-app"

  owner {
    id          = confluent_service_account.app.id
    api_version = confluent_service_account.app.api_version
    kind        = confluent_service_account.app.kind
  }

  managed_resource {
    id          = confluent_kafka_cluster.this.id
    api_version = confluent_kafka_cluster.this.api_version
    kind        = confluent_kafka_cluster.this.kind

    environment {
      id = confluent_environment.this.id
    }
  }
}

resource "confluent_kafka_acl" "app" {
  for_each = local.kafka_app_acls

  resource_type = each.value.resource_type
  resource_name = each.value.resource_name
  pattern_type  = each.value.pattern_type
  operation     = each.value.operation
  principal     = "User:${confluent_service_account.app.id}"
  host          = "*"
  permission    = "ALLOW"
  rest_endpoint = confluent_kafka_cluster.this.rest_endpoint

  kafka_cluster {
    id = confluent_kafka_cluster.this.id
  }

  credentials {
    key    = confluent_api_key.manager.id
    secret = confluent_api_key.manager.secret
  }
}
