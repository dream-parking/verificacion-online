package com.dreamparking.backend.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** What each text format accepts and rejects, with the kind of input that reached the API in sprint 1. */
class TextFormatsTests {

	static ValidatorFactory factory;

	static Validator validator;

	record Name(@PersonName String value) {
	}

	record Text(@FreeText String value) {
	}

	record Comment(@MultilineText String value) {
	}

	record Generated(@SafeText String value) {
	}

	record CatalogCode(@Code String value) {
	}

	record Document(@Dui String value) {
	}

	record Phone(@MobilePhone String value) {
	}

	record Mail(@EmailAddress String value) {
	}

	record Secret(@Password String value) {
	}

	@BeforeAll
	static void createValidator() {
		factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();
	}

	@AfterAll
	static void closeValidator() {
		factory.close();
	}

	@ParameterizedTest
	@ValueSource(strings = { "Marta Alejandra", "José Ñúñez", "Ana", " Rivas Cruz ", "Zoë Müller" })
	void namesAreLettersAndSingleSpaces(String name) {
		assertThat(violations(new Name(name))).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "Juan2", "Ana 😀", "Robert'); DROP TABLE customer;--", "<script>alert(1)</script>",
			"Ana  María", "María-José", "Ana\u200BMaría", "Ana\u0000", "J. Pérez", "Ana\tMaría" })
	void namesRejectDigitsSymbolsEmojisAndInvisibleCharacters(String name) {
		assertThat(violations(new Name(name))).containsExactly("Solo se permiten letras y espacios");
	}

	@ParameterizedTest
	@ValueSource(strings = { "Más de USD 5,000", "Venta de artesanías (mercado local)", "Analista de fraude y cumplimiento",
			"Dijo “sí”, pagará el 15% el lunes…", "Pago de aguinaldo #2 — ver estado de cuenta", "¿Remesa? ¡Sí!",
			"Cobro en ₡ y $", "x" })
	void freeTextAcceptsWhatPeopleWrite(String text) {
		assertThat(violations(new Text(text))).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "Ventas 😀", "<b>Analista</b>", "{\"$gt\": \"\"}", "Analista\u0000", "Ana\u202Elisis",
			"Línea 1\nLínea 2", "....", "---", "a | b", "C:\\Windows", "`rm -rf`" })
	void freeTextRejectsEmojisMarkupAndControlCharacters(String text) {
		assertThat(violations(new Text(text))).hasSize(1);
	}

	@Test
	void freeTextLeavesPresenceToNotBlank() {
		assertThat(violations(new Text(null))).isEmpty();
		assertThat(violations(new Text(""))).isEmpty();
		assertThat(violations(new Text("   "))).isEmpty();
	}

	@Test
	void onlyMultilineTextAcceptsLineBreaks() {
		assertThat(violations(new Comment("Revisé los estados de cuenta.\r\nTodo en orden."))).isEmpty();
		assertThat(violations(new Comment("Todo en orden 👍\n"))).hasSize(1);
	}

	@ParameterizedTest
	@ValueSource(strings = { "aa11·bb22·cc33", "iPhone 15 Pro", "Samsung SM-A546E", "Android 14", "1.0.0+1",
			"moto g(60)", "Redmi Note 10 Pro+" })
	void safeTextAcceptsWhatDevicesReport(String value) {
		assertThat(violations(new Generated(value))).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "iPhone\u0000", "Pixel 😀", "<img src=x onerror=alert(1)>", "abc\u202Edef", "a\u200Bb",
			"line\nbreak" })
	void safeTextRejectsControlInvisibleMarkupAndEmojis(String value) {
		assertThat(violations(new Generated(value))).hasSize(1);
	}

	@Test
	void codesAreCapitalsDigitsUnderscoreAndHyphen() {
		assertThat(violations(new CatalogCode("PAGO_SALARIO"))).isEmpty();
		assertThat(violations(new CatalogCode("500_1500"))).isEmpty();
		assertThat(violations(new CatalogCode("R-01"))).isEmpty();
		assertThat(violations(new CatalogCode("pago_salario"))).hasSize(1);
		assertThat(violations(new CatalogCode("_SALARIO"))).hasSize(1);
		assertThat(violations(new CatalogCode("PAGO SALARIO"))).hasSize(1);
		assertThat(violations(new CatalogCode("' OR 1=1 --"))).hasSize(1);
		assertThat(violations(new CatalogCode("PAGO-salario"))).containsExactly(
				"Debe ser un código en mayúsculas: letras, números, _ y -");
	}

	@Test
	void fixedFormatsReportOneMessage() {
		assertThat(violations(new Document("04567891-2"))).isEmpty();
		assertThat(violations(new Document("045678912"))).containsExactly("Usa el formato 00000000-0");
		assertThat(violations(new Phone("7845-2310"))).isEmpty();
		assertThat(violations(new Phone("2222-0000"))).containsExactly("Usa el formato 7000-0000");
		assertThat(violations(new Mail("nueva.persona@ceiba.example"))).isEmpty();
		assertThat(violations(new Mail("a@b"))).hasSize(1);
		assertThat(violations(new Mail("\"ana maría\"@ceiba.example"))).hasSize(1);
	}

	@Test
	void passwordsOnlyRejectControlCharacters() {
		assertThat(violations(new Secret("<clave> con ' y \" 😀"))).isEmpty();
		assertThat(violations(new Secret("clave\u0000larga"))).hasSize(1);
	}

	private static Set<String> violations(Object bean) {
		return validator.validate(bean).stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
	}

}
