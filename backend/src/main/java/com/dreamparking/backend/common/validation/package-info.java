/**
 * The formats a text can have when it comes into the API. Every {@code String} of a request body, query parameter or
 * path variable carries one of them ({@code InputFormatRulesTests} fails the build otherwise), so a new field cannot
 * slip in accepting digits in a name, emojis, markup or invisible characters:
 * <ul>
 * <li>{@link PersonName}: names of people, only letters and single spaces.</li>
 * <li>{@link FreeText}, {@link MultilineText}: what a person writes (labels, job titles, comments): letters, digits
 * and common punctuation.</li>
 * <li>{@link Code}: catalog and rule codes, {@code PAGO_SALARIO}, {@code R-01}.</li>
 * <li>{@link Dui}, {@link MobilePhone}, {@link EmailAddress}: fixed formats.</li>
 * <li>{@link SafeText}: values the app generates (device model, versions, tokens): anything printable except
 * markup, symbols and emojis.</li>
 * <li>{@link Password}: anything but control characters; the policy is checked by the service.</li>
 * </ul>
 * All of them are built on {@link jakarta.validation.constraints.Pattern}, so the regular expressions are published in
 * {@code docs/openapi.json} for the app and the console. One-off shapes can still use {@code @Pattern} directly.
 * <p>
 * SQL injection is not stopped here but by the queries: all of them take the values as parameters.
 */
package com.dreamparking.backend.common.validation;
