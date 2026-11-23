.DEFAULT_GOAL := help

.PHONY: help up down logs ps build reset-db db-shell backend-test bff-test frontend-test env

help: ## Show available targets
	@awk 'BEGIN {FS = ":.*##"} /^[a-zA-Z_-]+:.*##/ {printf "  %-15s %s\n", $$1, $$2}' $(MAKEFILE_LIST)

up: ## Build and start the whole stack
	docker compose up -d --build

down: ## Stop the stack
	docker compose down

logs: ## Follow service logs
	docker compose logs -f --tail=100

ps: ## List service status
	docker compose ps

build: ## Build service images
	docker compose build

reset-db: ## Stop the stack and delete the PostgreSQL volume (asks for confirmation)
	@read -p "This deletes ALL local database data. Continue? [y/N] " ans && [ "$$ans" = "y" ] || { echo "Aborted."; exit 1; }
	docker compose down
	docker volume rm familion_pgdata || true

db-shell: ## Open a psql shell in the postgres container
	docker compose exec postgres psql -U familion familion

backend-test: ## Run Core backend tests
	cd backend && ./mvnw -B verify

bff-test: ## Run BFF tests
	cd bff && ./mvnw -B verify

frontend-test: ## Run frontend tests
	cd frontend && npm ci && npm test

env: ## Create .env from .env.example and generate JWT_SECRET (no-op if .env exists)
	@if [ -f .env ]; then \
		echo ".env already exists, leaving it untouched."; \
	else \
		cp .env.example .env; \
		secret=$$(openssl rand -base64 48 | tr -d '\n'); \
		sed -i.bak "s|^JWT_SECRET=.*|JWT_SECRET=$$secret|" .env && rm -f .env.bak; \
		echo ".env created with a generated JWT_SECRET."; \
	fi
