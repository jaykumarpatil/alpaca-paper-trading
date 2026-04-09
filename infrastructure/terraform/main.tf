terraform {
  required_version = ">= 1.9.0"
}

provider "kubernetes" {
  config_path = "~/.kube/config"
}

resource "kubernetes_namespace" "prod_live" {
  metadata { name = "prod-live" }
}

resource "kubernetes_namespace" "prod_backtest" {
  metadata { name = "prod-backtest" }
}
