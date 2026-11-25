-- Seed initial app releases and changelog
INSERT INTO app_releases (version, title, changelog_markdown, is_breaking, released_at)
VALUES
    ('0.1.0', 'Base del proyecto', '### Novedades
- Creación de familias y roles de usuario (Adulto e Hijo)
- Autenticación segura con verificación en dos pasos (2FA)
- Configuración de moneda base y tipos de cambio de referencia
- Seguridad reforzada y dispositivos de confianza', FALSE, '2026-01-15 10:00:00+00'),

    ('0.2.0', 'Finanzas y motor anti-inflación', '### Novedades
- Registro de ingresos, gastos y transferencias entre cuentas
- Soporte para múltiples cuentas (bancos, billeteras virtuales y efectivo)
- Comparación en tiempo real a valores históricos y «a plata de hoy»
- Distribución de gastos personales vs. compartidos del hogar', FALSE, '2026-02-10 10:00:00+00'),

    ('0.3.0', 'Cuotas, gastos fijos, metas y mensualidades', '### Novedades
- Planes de cuotas con cálculo automático de inflación mensual
- Seguimiento de gastos fijos y servicios del mes
- Metas de ahorro familiares y aportes programados
- Gestión de mensualidades para los hijos con registro de pagos', FALSE, '2026-03-05 10:00:00+00'),

    ('0.4.0', 'Vehículos', '### Novedades
- Registro de vehículos del hogar con kilometraje actualizado
- Cargas de combustible y cálculo de consumo promedio
- Registro de mantenimientos, services y repuestos
- Alertas preventivas por fecha y kilometraje de vencimiento', FALSE, '2026-03-25 10:00:00+00'),

    ('0.5.0', 'Notificaciones y PWA', '### Novedades
- Campana de notificaciones para estar al día con el hogar
- Notificaciones push en tu celular al instalar la app
- Avisos de gastos atribuidos, mensualidades y mantenimientos
- Sección «Qué cambió» con el historial de mejoras de FamiliON', FALSE, '2026-04-15 10:00:00+00')
ON CONFLICT (version) DO NOTHING;
