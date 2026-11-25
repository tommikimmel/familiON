package com.familion.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import com.familion.notifications.application.ActivityDescriber;
import com.familion.notifications.application.ActivityDescriber.Activity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ActivityDescriberTest {

    @ParameterizedTest
    @CsvSource({
        "POST, /api/v1/transactions, creó un movimiento",
        "PATCH, /api/v1/transactions/7b1, editó un movimiento",
        "DELETE, /api/v1/transactions/7b1, eliminó un movimiento",
        "POST, /api/v1/accounts, creó una cuenta",
        "DELETE, /api/v1/accounts/1, eliminó una cuenta",
        "POST, /api/v1/recurring-expenses, creó un gasto fijo",
        "POST, /api/v1/recurring-expenses/1/confirm-payment, confirmó el pago de un gasto fijo",
        "POST, /api/v1/savings-goals/1/contributions, registró un aporte a una meta de ahorro",
        "PATCH, /api/v1/vehicles/1, editó un vehículo",
        "POST, /api/v1/vehicles/1/fuel-logs, registró una carga de combustible",
        "PATCH, /api/v1/family/settings, actualizó la configuración de la familia",
        "POST, /api/v1/family/invitations, invitó a un nuevo miembro",
        "DELETE, /api/v1/family/invitations/1, canceló una invitación",
        "PATCH, /api/v1/family/members/1/visibility, cambió la visibilidad de un miembro",
        "PATCH, /api/v1/auth/me/preferences, actualizó sus preferencias de perfil",
        "POST, /api/v1/something-new, realizó un cambio",
    })
    void describesUserFacingMutations(String method, String path, String phrase) {
        assertThat(ActivityDescriber.describe(method, path)).map(Activity::phrase).contains(phrase);
    }

    @ParameterizedTest
    @CsvSource({
        "POST, /api/v1/auth/login",
        "POST, /api/v1/auth/refresh",
        "POST, /api/v1/auth/register-invite",
        "POST, /api/v1/notifications/read-all",
        "PATCH, /api/v1/notifications/1/read",
        "POST, /api/v1/receipts/scan",
        "POST, /actuator/refresh",
    })
    void ignoresNonUserFacingCalls(String method, String path) {
        assertThat(ActivityDescriber.describe(method, path)).isEmpty();
    }

    @Test
    void securityChangesAreVisibleOnlyToTheActor() {
        assertThat(ActivityDescriber.describe("PATCH", "/api/v1/auth/me/security"))
                .map(Activity::actorOnly)
                .contains(true);
        assertThat(ActivityDescriber.describe("POST", "/api/v1/accounts"))
                .map(Activity::actorOnly)
                .contains(false);
    }
}
