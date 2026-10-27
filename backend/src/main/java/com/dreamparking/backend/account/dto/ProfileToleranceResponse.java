package com.dreamparking.backend.account.dto;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import com.dreamparking.backend.account.entity.ProfileTolerance;

/**
 * The tolerance of the declared profile tracking in force and the log of its changes, newest first (VDI-137).
 * {@code changedBy} and {@code previousPercent} are null for the first value, set by a migration.
 */
public record ProfileToleranceResponse(
		@Schema(description = "Tolerancia vigente: margen sobre lo declarado antes de considerarlo una desviación", example = "20") int tolerancePercent,
		@Schema(description = "Mínimo permitido", example = "0") int minPercent,
		@Schema(description = "Máximo permitido", example = "45") int maxPercent,
		@Schema(description = "Bitácora de cambios, del más reciente al más antiguo") List<Change> changes) {

	@Schema(name = "ProfileToleranceChange")
	public record Change(@Schema(description = "Valor nuevo", example = "25") int tolerancePercent,
			@Schema(description = "Valor anterior; vacío en el valor inicial", example = "20") Integer previousPercent,
			@Schema(description = "Usuario de consola que hizo el cambio; vacío en el valor inicial") String changedBy,
			Instant changedAt) {

		static Change of(ProfileTolerance tolerance) {
			return new Change(tolerance.getTolerancePercent(),
					tolerance.getPreviousPercent() == null ? null : tolerance.getPreviousPercent().intValue(),
					tolerance.getChangedBy() == null ? null : tolerance.getChangedBy().getFullName(),
					tolerance.getChangedAt());
		}

	}

	/** The response for the values of the log, newest first. */
	public static ProfileToleranceResponse of(List<ProfileTolerance> newestFirst) {
		return new ProfileToleranceResponse(newestFirst.getFirst().getTolerancePercent(), ProfileTolerance.MIN_PERCENT,
				ProfileTolerance.MAX_PERCENT, newestFirst.stream().map(Change::of).toList());
	}

}
