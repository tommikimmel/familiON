package com.familion.notifications.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** Maps a successful mutating API call (method + path) to a Spanish activity phrase for the family feed. */
public final class ActivityDescriber {

    /** {@code actorOnly}: the change is personal (e.g. security settings) and only the actor sees it. */
    public record Activity(String phrase, String link, boolean actorOnly) {}

    private record Rule(String method, Pattern path, Activity activity) {}

    private static final String ID = "/[^/]+";
    private static final Pattern EXCLUDED =
            Pattern.compile("^/api/v1/(notifications|receipts|exports|analytics)(/.*)?$|^/api/v1/auth/(?!me(/|$)).*$");

    private static final List<Rule> RULES = new ArrayList<>();

    static {
        // Specific sub-resources first: the first matching rule wins.
        rule("POST", "/savings-goals" + ID + "/contributions", "registró un aporte a una meta de ahorro", "/plan");
        rule("DELETE", "/savings-goals" + ID + "/contributions" + ID, "eliminó un aporte de una meta de ahorro", "/plan");
        rule("POST", "/installment-plans" + ID + "/cancel-remaining", "canceló las cuotas restantes de un plan", "/plan");
        rule("POST", "/recurring-expenses" + ID + "/confirm-payment", "confirmó el pago de un gasto fijo", "/plan");
        rule("POST", "/family/allowances" + ID + "/disburse", "pagó una mensualidad", "/plan");
        rule("POST", "/vehicles" + ID + "/fuel-logs", "registró una carga de combustible", "/vehicles");
        rule("DELETE", "/vehicles" + ID + "/fuel-logs" + ID, "eliminó una carga de combustible", "/vehicles");
        rule("POST", "/vehicles" + ID + "/maintenance-logs", "registró un mantenimiento", "/vehicles");
        rule("DELETE", "/vehicles" + ID + "/maintenance-logs" + ID, "eliminó un mantenimiento", "/vehicles");
        rule("PATCH", "/family/settings", "actualizó la configuración de la familia", "/profile");
        rule("POST", "/family/currencies", "habilitó una moneda en la familia", "/profile");
        rule("PATCH", "/family/currencies" + ID, "editó una moneda de la familia", "/profile");
        rule("POST", "/family/invitations", "invitó a un nuevo miembro", "/profile");
        rule("DELETE", "/family/invitations" + ID, "canceló una invitación", "/profile");
        rule("PATCH", "/family/members" + ID + "/visibility", "cambió la visibilidad de un miembro", "/profile");
        rule("PATCH", "/auth/me/preferences", "actualizó sus preferencias de perfil", "/profile");
        rule("POST", "/economics/indices/manual", "cargó un índice económico manual", "/analytics");
        RULES.add(new Rule(null, path("/auth/me/security(/.*)?"),
                new Activity("actualizó su configuración de seguridad", "/profile", true)));

        crud("/transactions", "un movimiento", "/transactions");
        crud("/accounts", "una cuenta", "/accounts");
        crud("/categories", "una categoría", "/profile");
        crud("/currency-exchanges", "un cambio de divisa", "/transactions");
        crud("/savings-goals", "una meta de ahorro", "/plan");
        crud("/installment-plans", "un plan de cuotas", "/plan");
        crud("/recurring-expenses", "un gasto fijo", "/plan");
        crud("/family/allowances", "una mensualidad", "/plan");
        crud("/vehicles", "un vehículo", "/vehicles");
    }

    private ActivityDescriber() {}

    /** Empty for paths that are not user-facing changes (auth flows, notifications, receipt scans...). */
    public static Optional<Activity> describe(String method, String path) {
        if (method == null || path == null || !path.startsWith("/api/v1/") || EXCLUDED.matcher(path).matches()) {
            return Optional.empty();
        }
        for (Rule rule : RULES) {
            if ((rule.method() == null || rule.method().equals(method)) && rule.path().matcher(path).matches()) {
                return Optional.of(rule.activity());
            }
        }
        // Unknown mutation: still surface it so the feed covers every change.
        return Optional.of(new Activity("realizó un cambio", null, false));
    }

    private static void crud(String base, String noun, String link) {
        rule("POST", base, "creó " + noun, link);
        rule("PATCH", base + ID, "editó " + noun, link);
        rule("PUT", base + ID, "editó " + noun, link);
        rule("DELETE", base + ID, "eliminó " + noun, link);
    }

    private static void rule(String method, String subPath, String phrase, String link) {
        RULES.add(new Rule(method, path(subPath), new Activity(phrase, link, false)));
    }

    private static Pattern path(String subPath) {
        return Pattern.compile("^/api/v1" + subPath + "/?$");
    }
}
