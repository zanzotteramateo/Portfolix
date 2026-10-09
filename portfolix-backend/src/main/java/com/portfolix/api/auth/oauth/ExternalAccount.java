package com.portfolix.api.auth.oauth;

/**
 * Una cuenta de un proveedor externo, con los datos que ya validó el proveedor (ej.: los de un ID token
 * de Google). El mail viene verificado por el proveedor.
 *
 * @param subject id fijo de la cuenta en el proveedor (el {@code sub} del token)
 */
public record ExternalAccount(IdentityProvider provider, String subject, String email, String name) {
}
