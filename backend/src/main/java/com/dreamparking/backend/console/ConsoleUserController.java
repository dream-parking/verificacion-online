package com.dreamparking.backend.console;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/console/users")
@Tag(name = "Consola · Usuarios", description = "Usuarios de la consola administrativa")
public class ConsoleUserController {

	private final ConsoleUserRepository users;

	public ConsoleUserController(ConsoleUserRepository users) {
		this.users = users;
	}

	@Operation(summary = "Usuarios activos de la consola",
			description = "Provisional hasta que exista el inicio de sesión: la consola toma de aquí el `userId` para tomar alertas.")
	@GetMapping
	@Transactional(readOnly = true)
	public List<ConsoleUserResponse> active() {
		return users.findAll(Sort.by("fullName"))
			.stream()
			.filter(user -> Boolean.TRUE.equals(user.getActive()))
			.map(ConsoleUserResponse::of)
			.toList();
	}

}
