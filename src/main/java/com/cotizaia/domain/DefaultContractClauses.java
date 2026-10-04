package com.cotizaia.domain;

import java.util.List;

/**
 * Standard commercial clauses seeded on a {@link Contract} when the agent
 * pipeline supplies none (see {@code ContractService.generateDraft}).
 *
 * <p>Kept in its own small class so the default content lives next to the
 * domain it belongs to instead of inside the service.
 */
public final class DefaultContractClauses {

    /** Standard commercial clauses used when the pipeline supplies none. */
    public static final List<String> DEFAULT_CLAUSES = List.of(
            "Alcance: el proveedor ejecutara unicamente los servicios descritos en la propuesta aceptada.",
            "Valor y forma de pago: el 50% se paga como anticipo y el 50% restante contra entrega. "
                    + "Pago simulado con fines academicos, no se procesa dinero real.",
            "Plazos: el cronograma adjunto es una estimacion; los cambios de alcance pueden ajustarlo.",
            "Propiedad intelectual: los entregables se transfieren al cliente una vez recibido el pago total.",
            "Garantia: 30 dias de soporte correctivo sobre los entregables.");

    private DefaultContractClauses() {
    }
}
