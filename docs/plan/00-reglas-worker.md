# Reglas para Workers (leer completo, es corto)

Cada worker ejecuta **una sola tarjeta** de `docs/plan/tareas/`. Todo lo necesario está en la tarjeta y en este archivo. No leer el resto de `docs/` salvo los rangos de líneas que la tarjeta indique explícitamente.

## 1. Git (obligatorio)

```bash
git fetch origin
git switch -c <rama-de-la-tarjeta> origin/develop     # o: git worktree add ../familion-TXX -b <rama> origin/develop
# ... trabajo ...
git push -u origin <rama-de-la-tarjeta>
gh pr create --base develop --head <rama-de-la-tarjeta> --title "<título-de-la-tarjeta>" --body-file <archivo-temporal>
```

- **Commits:** Conventional Commits en inglés (`feat(scope): ...`, `test(scope): ...`, `chore(scope): ...`). Commits chicos y coherentes (código + tests juntos).
- **Prohibido:** líneas `Co-Authored-By`, menciones a IA/asistentes en commits o PR, `--no-verify`, `push --force` sobre ramas ajenas, commitear directo a `develop`/`main`.
- **Cuerpo de la PR** (en inglés):
  ```markdown
  ## Task
  TXX — <title>
  ## Summary
  - what was done (3–6 bullets)
  ## How to verify
  - exact commands run and their result
  ## Acceptance criteria
  - [x] every criterion from the task card
  ## Out of scope / follow-ups
  - anything detected that did not belong to this task
  ```

## 2. Alcance

- Tocar **solo** los archivos/carpetas listados en `Archivos permitidos` de la tarjeta. Otras tareas corren en paralelo sobre otros archivos: tocar fuera genera conflictos.
- Si se necesita algo fuera del alcance: **no hacerlo**. Dejar un stub/interfaz dentro del alcance si la tarjeta lo permite y anotarlo en `Out of scope / follow-ups`.
- No cambiar contratos (DTOs, rutas, nombres de interfaces, variables de entorno) definidos en la tarjeta. Otros workers dependen de ellos tal cual.

## 3. Idioma

- Código, identificadores, comentarios, commits, PR, logs: **inglés**.
- Textos visibles en la UI: **español** neutro, con los términos locales que defina la tarjeta (ej. «Personal | Familia», «A plata de hoy», «Guardar»).

## 4. Convenciones Backend / BFF (Java 21 + Spring Boot 3.5.x + Maven)

- Paquete raíz `com.familion` (Core) y `com.familion.bff` (BFF). Organización **por módulo de negocio** (screaming architecture) y, dentro de cada módulo, capas:
  ```
  com.familion.<modulo>/
    domain/          # entidades JPA, enums, reglas de dominio puras, excepciones de dominio
    application/     # casos de uso (@Service), comandos/queries (records), puertos
      api/           # ÚNICO paquete que otros módulos pueden importar (interfaces + records)
    infrastructure/
      web/           # @RestController + DTOs request/response (records)
      persistence/   # Spring Data repositories, queries JDBC
      external/      # clientes HTTP externos, schedulers
  ```
- Un módulo **nunca** importa `domain`/`infrastructure` de otro módulo: solo su `application.api`.
- Sin Lombok. DTOs y comandos como `record`. Inyección por constructor.
- Dinero: `BigDecimal`, escala 4, `RoundingMode.HALF_EVEN`. Tasas/índices: escala 6. Nunca `double`/`float`.
- Fechas contables `LocalDate`; timestamps `Instant`/`OffsetDateTime` en UTC.
- `familyId` y `userId` salen **siempre** de `CurrentUser` (token), nunca del body.
- Errores: lanzar las excepciones de `com.familion.common.error` (`NotFoundException`, `ForbiddenException`, `BusinessRuleException`, `ConflictException`); el handler global responde RFC 7807.
- Roles: `@PreAuthorize("hasRole('ADULTO')")` donde la tarjeta diga "solo ADULTO".
- Paginación: `size` máximo 50.
- Tests: unitarios (JUnit 5 + Mockito + AssertJ) para reglas de negocio y de integración extendiendo `com.familion.common.test.IntegrationTestBase` (Testcontainers PostgreSQL 17) para endpoints y queries. **Test primero** cuando la regla es clara (RED → GREEN → refactor).
- Migraciones: solo si la tarjeta lo indica, con la versión reservada `V<número de tarea>__descripcion.sql`. Nunca editar migraciones existentes.
- Tests de integración: no depender de internet; el sync económico al arrancar se desactiva en tests con `familion.economics.sync-on-startup=false`.

## 5. Convenciones Frontend (Angular última estable ≥19 + Tailwind CSS v4)

- Standalone components, `ChangeDetectionStrategy.OnPush`, `inject()`, Signals (`signal`, `computed`, `input()`, `output()`), control flow `@if/@for`.
- **Container / Presentational:** `pages/*.page.ts` = contenedores (inyectan stores/servicios); `components/*` = presentacionales (solo inputs/outputs, sin servicios).
- Estado por feature en `data-access/*.store.ts` (signals) + `*.api.ts` (HttpClient). Sin NgRx.
- Toda llamada HTTP va a rutas relativas `/bff/...` (nunca al Core directo).
- Estilos: utilidades Tailwind + tokens CSS (`--fo-*`) definidos en `src/styles.css`. Montos con `tabular-nums`. Respetar `prefers-reduced-motion`.
- Accesibilidad: labels en inputs, `aria-*` en toggles, foco visible, contraste AA.
- Íconos: siempre vía el componente `fo-icon` (wrapper de `@lucide/angular`); nunca importar la librería directamente. Prohibido `--legacy-peer-deps` y `--force`.
- Tests: el runner que genera el CLI (Vitest en Angular ≥21). Testear stores y componentes con lógica.

## 6. Definition of Done

1. Comandos de `Verificación` de la tarjeta ejecutados y en verde (resultado resumido en la PR).
2. Todos los `Criterios de aceptación` cumplidos y tildados en la PR.
3. Sin archivos fuera de alcance en el diff (`git diff --stat origin/develop`).
4. PR abierta contra `develop`.

Ante un bloqueo (contrato ambiguo, dependencia ausente): no inventar. Abrir la PR como **draft** describiendo el bloqueo.
