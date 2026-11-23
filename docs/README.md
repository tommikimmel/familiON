# FamiliON — Documentación del Proyecto

**FamiliON** es una plataforma de organización financiera personal y familiar diseñada específicamente para contextos de economía fluctuante e inflación (como Argentina), integrando finanzas integrales del hogar, educación financiera para hijos mediante mensualidades y contabilidad/mantenimiento de vehículos familiares.

## Índice de Documentación

| # | Documento | Contenido |
|---|---|---|
| 01 | [Visión y Motor Anti-Inflación](./01-vision-y-propuesta-anti-inflacion.md) | Problema, propuesta de valor, multimoneda con tipo de cambio real y estrategia anti-inflación. |
| 02 | [Requerimientos Funcionales y No Funcionales](./02-requerimientos-funcionales.md) | Módulos del sistema, roles (`ADULTO` / `HIJO`), permisos de visibilidad, cuotas, gastos fijos, metas, vehículos y OCR. |
| 03 | [Modelo de Dominio](./03-modelo-de-dominio.md) | Entidades principales, diagrama entidad-relación y reglas críticas de negocio. |
| 04 | [Arquitectura y Stack Tecnológico](./04-arquitectura-y-stack.md) | Stack moderno (Java 21 + Spring Boot 3 con Maven, Angular 19+ con Tailwind CSS, PostgreSQL 17, Docker Compose, JWT). |
| 05 | [Roadmap y Plan de MVP](./05-roadmap-mvp.md) | Fases de desarrollo incremental desde la base del proyecto hasta el escaneo de tickets con OCR. |
| 06 | [Contrato de API REST](./06-api-rest.md) | Endpoints por módulo, convenciones JWT, filtros temporales y lentes anti-inflación. |
| 07 | [Sistema de Diseño y UX Mobile](./07-sistema-de-diseno-ui.md) | Lineamientos minimalistas, tema claro/oscuro, color de perfil, carga rápida en 3 toques y gráficos de torta. |
| 08 | [PWA, CI/CD, Versionado y Notificaciones](./08-pwa-cicd-y-notificaciones.md) | Web instalable como atajo en inicio (`@angular/pwa`), pipelines GitHub Actions, SemVer por fases y notificaciones. |
| 09 | [Seguridad Perimetral, BFF y Caché](./09-seguridad-edge-bff-y-cache.md) | Cloudflare + Nginx (anti-DDoS / anti-scrapeo / rate limit), BFF Java (`HttpOnly` + agregación mobile) y caché en Redis 7. |
| DB | [Diseño de Base de Datos](./database/01-diseno-base-de-datos.md) y [schema.sql](./database/schema.sql) | Convenciones PostgreSQL 17, diccionario de tablas, índices y DDL inicial listo para Flyway. |
| Brand | [Logo SVG](./brand/familion-logo-adaptive.svg) e [Ícono PWA](./brand/familion-icon.svg) | Identidad visual `FamiliON` (toggle ON) en versiones claro, oscuro, adaptativo e ícono. |

## Resumen del Stack Elegido

- **Backend:** Java 21 LTS + Spring Boot 3.4+ (**Maven**, Spring Security + JWT, Flyway, OpenAPI).
- **Frontend:** Angular 19+ (Standalone Components, Signals, **Tailwind CSS**, Responsive / PWA).
- **Base de Datos:** PostgreSQL 17.
- **Infraestructura Local y Despliegue:** Docker & Docker Compose.

## Estado Actual del Proyecto

- [x] Definición de alcance, permisos, multimoneda y stack.
- [x] Documentación funcional, de dominio y arquitectura (`docs/`).
- [x] Diseño de base de datos y DDL inicial (`docs/database/`).
- [x] Especificación de contrato API REST (`docs/06-api-rest.md`).
- [ ] Scaffolding del monorepo (`backend/`, `frontend/`, `docker-compose.yml`).
