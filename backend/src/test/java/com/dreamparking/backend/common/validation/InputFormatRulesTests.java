package com.dreamparking.backend.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.MethodParameter;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Guard against the "this field accepts anything" bugs: every text that comes into the API must declare its format
 * (one of the annotations of {@code common.validation}, or a {@code @Pattern}). Adding a {@code String} to a request
 * body, or a {@code String} query parameter or path variable, without one fails the build here.
 */
class InputFormatRulesTests {

	private static final String BASE_PACKAGE = "com.dreamparking.backend";

	/** Annotations that say what a text may contain. */
	private static final Set<Class<? extends Annotation>> FORMATS = Set.of(PersonName.class, FreeText.class,
			MultilineText.class, SafeText.class, Password.class, Code.class, Dui.class, MobilePhone.class,
			EmailAddress.class, Pattern.class);

	/** Formats that do not bound the length on their own and need an {@code @Size(max)} too. */
	private static final Set<Class<? extends Annotation>> UNBOUNDED = Set.of(PersonName.class, FreeText.class,
			MultilineText.class, SafeText.class, Password.class, Code.class, EmailAddress.class);

	@Test
	void everyTextOfARequestBodyDeclaresItsFormat() throws Exception {
		List<Class<?>> bodies = scan(new AssignableTypeFilter(Record.class)).stream()
			.filter(type -> type.getSimpleName().endsWith("Request"))
			.toList();
		assertThat(bodies).as("request bodies found").hasSizeGreaterThan(15);

		List<String> problems = new ArrayList<>();
		Set<Class<?>> seen = new HashSet<>();
		for (Class<?> body : bodies) {
			checkRecord(body, problems, seen);
		}
		assertThat(problems).as("request fields without a declared format").isEmpty();
	}

	@Test
	void everyTextQueryParameterAndPathVariableDeclaresItsFormat() throws Exception {
		List<String> problems = new ArrayList<>();
		int checked = 0;
		for (Class<?> controller : scan(new AnnotationTypeFilter(RestController.class))) {
			for (Method method : controller.getDeclaredMethods()) {
				for (int i = 0; i < method.getParameterCount(); i++) {
					MethodParameter parameter = new MethodParameter(method, i);
					if (parameter.getParameterType() != String.class
							|| !(parameter.hasParameterAnnotation(RequestParam.class)
									|| parameter.hasParameterAnnotation(PathVariable.class))) {
						continue;
					}
					checked++;
					String where = controller.getSimpleName() + "." + method.getName() + "("
							+ method.getParameters()[i].getName() + ")";
					check(where, parameter.getParameterAnnotations(), problems);
				}
			}
		}
		assertThat(checked).as("text parameters found").isGreaterThan(5);
		assertThat(problems).as("query parameters and path variables without a declared format").isEmpty();
	}

	private static void checkRecord(Class<?> type, List<String> problems, Set<Class<?>> seen) throws Exception {
		if (!seen.add(type)) {
			return;
		}
		for (RecordComponent component : type.getRecordComponents()) {
			Field field = type.getDeclaredField(component.getName());
			String where = type.getSimpleName() + "." + component.getName();
			if (component.getType() == String.class) {
				check(where, field.getAnnotations(), problems);
			}
			else if (component.getType().isRecord()) {
				checkRecord(component.getType(), problems, seen);
			}
			// Maps are free-form JSON from other systems (alert evidence), bounded by @Size on the map itself.
			for (Class<?> element : elementTypes(component)) {
				if (element == String.class) {
					problems.add(where + ": collections of text are not checked; use a record");
				}
				else if (element.isRecord()) {
					checkRecord(element, problems, seen);
				}
			}
		}
	}

	private static void check(String where, Annotation[] annotations, List<String> problems) {
		Set<Class<? extends Annotation>> present = new HashSet<>();
		Arrays.stream(annotations).forEach(annotation -> present.add(annotation.annotationType()));
		if (present.stream().noneMatch(FORMATS::contains)) {
			problems.add(where + ": add a format (@PersonName, @FreeText, @Code, @SafeText… see common.validation)");
		}
		else if (present.stream().anyMatch(UNBOUNDED::contains) && !present.contains(Size.class)) {
			problems.add(where + ": add @Size(max = …)");
		}
	}

	private static List<Class<?>> elementTypes(RecordComponent component) {
		if (!Collection.class.isAssignableFrom(component.getType())
				|| !(component.getGenericType() instanceof ParameterizedType parameterized)) {
			return List.of();
		}
		return Arrays.stream(parameterized.getActualTypeArguments())
			.filter(Class.class::isInstance)
			.<Class<?>>map(Class.class::cast)
			.toList();
	}

	private static List<Class<?>> scan(org.springframework.core.type.filter.TypeFilter filter) throws Exception {
		ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
		scanner.addIncludeFilter(filter);
		List<Class<?>> types = new ArrayList<>();
		for (var candidate : scanner.findCandidateComponents(BASE_PACKAGE)) {
			types.add(ClassUtils.forName(candidate.getBeanClassName(), InputFormatRulesTests.class.getClassLoader()));
		}
		return types;
	}

}
