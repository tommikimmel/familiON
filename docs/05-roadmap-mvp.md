# 05 — Roadmap y Plan de MVP

El desarrollo se organiza en **fases incrementales** para tener una versión usable por la familia lo antes posible y luego sumar las capacidades más complejas (cuotas, vehículos, analítica avanzada y escaneo de tickets).

---

## Fase 0 — Base del Proyecto, Edge/BFF/Caché, PWA y CI/CD
**Objetivo:** Tener toda la arquitectura corriendo localmente en un comando, protegida por Edge + BFF + Redis, instalable como atajo web (PWA) y validada por CI/CD desde el primer commit.
- [x] Validar y cerrar decisiones funcionales, UI/UX, identidad visual y arquitectura.
- [x] Diseñar esquema SQL inicial ([schema.sql](./database/schema.sql)) y contrato API ([06-api-rest.md](./06-api-rest.md)).
- [ ] Scaffolding de `edge/` (Nginx anti-DDoS / rate limit / headers), `bff/` (Java 21 + Spring Boot + cookies `HttpOnly`), `backend/` (Java 21 + Spring Boot + Maven + Flyway) y `frontend/` (Angular 19+ + Tailwind CSS + `@angular/pwa`).
- [ ] Configuración de `docker-compose.yml` con redes aisladas (`nginx`, `frontend`, `bff`, `backend`, `redis`, `postgres`).
- [ ] Pipelines **GitHub Actions** (`ci.yml`, `cd-staging.yml`, `cd-release.yml`) con SemVer, tests Testcontainers y webhook de notificaciones de cambios/releases.
- [ ] Autenticación completa con **JWT vía BFF (`HttpOnly` + CSRF)**, roles `ADULTO`/`HIJO` y aviso in-app de nueva versión PWA (`SwUpdate`).

## Fase 1 — MVP Core Financiero, Familia y Motor Anti-Inflación v1
**Objetivo:** Poder registrar ingresos y gastos reales de la familia sin que la inflación distorsione el panorama.
- [ ] Gestión de Familia, invitación de integrantes y roles (`ADULTO` / `HIJO`).
- [ ] CRUD de Cuentas (Efectivo, Banco, Billetera, USD) y Categorías.
- [ ] Registro rápido de Ingresos, Gastos y Transferencias.
- [ ] **Vista Dual con botón toggle:** Mis Gastos Personales ↔ Gastos de la Familia.
- [ ] **Motor Anti-Inflación v1:** sincronización diaria de dólar (MEP/Blue/Oficial) + IPC/UVA y guardado de `EconomicSnapshot` en cada movimiento.
- [ ] Navegación temporal básica por **Año, Mes, Semana y Día** con selector de lente (**ARS Nominal**, **ARS Constantes**, **USD**, **% del Ingreso**).

## Fase 2 — Cuotas, Gastos Fijos, Metas y Mensualidades
**Objetivo:** Cubrir toda la planificación mensual real de un hogar argentino.
- [ ] Tarjetas de crédito (cierre y vencimiento) y **Compras en Cuotas con y sin interés** (cronograma futuro + recargo real + licuación).
- [ ] **Gastos Fijos Mensuales** con generación automática de vencimientos y reglas de actualización (Manual / IPC / USD).
- [ ] **Mensualidades para Hijos** (asignación por adulto, billetera/presupuesto del hijo y regla anti-doble-conteo).
- [ ] **Metas de Ahorro** personales y familiares en moneda estable (USD / UVA / ARS ajustado).

## Fase 3 — Módulo de Vehículos (Contabilidad y Desgaste)
**Objetivo:** Control total de costos y mantenimiento de los autos de la familia.
- [ ] Alta y ficha de vehículos familiares (datos técnicos + vencimientos de VTV, seguro y patente).
- [ ] Carga de **Combustible** (kilometraje, litros, precio/litro, tipo de combustible, tanque lleno) vinculada automáticamente a la contabilidad.
- [ ] Registro de **Desgaste y Services** (aceite, filtros, cubiertas, frenos, arreglos) con alertas por kilometraje y fecha.
- [ ] Dashboard del vehículo: consumo (L/100km y km/L), costo por kilómetro y costo mensual real en moneda constante/USD.

## Fase 4 — Analítica Avanzada y Escaneo de Tickets (OCR)
**Objetivo:** Reducir al mínimo la fricción de carga y potenciar la toma de decisiones.
- [ ] **Escaneo de Tickets:** carga de foto desde el celular, procesamiento OCR/IA, pre-llenado inteligente y confirmación del usuario.
- [ ] Exportación de reportes (CSV / PDF / Excel) y respaldo de comprobantes.
- [ ] Mejoras PWA (instalable en celular, accesos rápidos de carga de gasto y combustible).
- [ ] Alertas inteligentes (desvío de gasto real en categorías, vencimientos de tarjetas/fijos/services).
