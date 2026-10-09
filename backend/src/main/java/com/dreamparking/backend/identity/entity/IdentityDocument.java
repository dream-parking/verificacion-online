package com.dreamparking.backend.identity.entity;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import com.dreamparking.backend.identity.entity.enums.OcrStatus;
import com.dreamparking.backend.identity.entity.enums.UnreadableReason;
import com.dreamparking.backend.identity.entity.enums.UnreadableSide;
import com.dreamparking.backend.identity.ocr.DuiReading;
import com.dreamparking.backend.onboarding.entity.OnboardingRequest;

/**
 * The request's DUI (VDI-79), one per request: what was read from the last capture and what the customer confirmed
 * or corrected on the confirmation screen ({@link #confirm}). The confirmed data is compared with the basic data the
 * customer typed before, and again whenever the basic data changes ({@link #compareWithDeclared}).
 */
@Entity
@Table(name = "identity_document")
public class IdentityDocument {

	@Id
	@Column(name = "request_id")
	private UUID requestId;

	@MapsId
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "request_id")
	private OnboardingRequest request;

	@Enumerated(EnumType.STRING)
	@Column(name = "ocr_status", nullable = false, length = 12)
	private OcrStatus status;

	@Column(name = "ocr_model", nullable = false, length = 60)
	private String model;

	@Column(name = "attempts", nullable = false)
	private short attempts;

	@Enumerated(EnumType.STRING)
	@Column(name = "unreadable_reason", length = 20)
	private UnreadableReason unreadableReason;

	@Column(name = "failure", length = 250)
	private String failure;

	@Column(name = "dui", length = 10)
	private String dui;

	@Column(name = "first_names", length = 100)
	private String firstNames;

	@Column(name = "last_names", length = 100)
	private String lastNames;

	@Column(name = "birth_date")
	private LocalDate birthDate;

	@Column(name = "issue_date")
	private LocalDate issueDate;

	@Column(name = "expiry_date")
	private LocalDate expiryDate;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "gender", length = 1)
	private String gender;

	@Column(name = "looks_authentic")
	private Boolean looksAuthentic;

	@Column(name = "confidence", precision = 3, scale = 2)
	private BigDecimal confidence;

	@Enumerated(EnumType.STRING)
	@Column(name = "unreadable_side", length = 5)
	private UnreadableSide unreadableSide;

	@Column(name = "confirmed_dui", length = 10)
	private String confirmedDui;

	@Column(name = "confirmed_first_names", length = 100)
	private String confirmedFirstNames;

	@Column(name = "confirmed_last_names", length = 100)
	private String confirmedLastNames;

	@Column(name = "confirmed_birth_date")
	private LocalDate confirmedBirthDate;

	@Column(name = "confirmed_expiry_date")
	private LocalDate confirmedExpiryDate;

	@Column(name = "confirmed_at")
	private Instant confirmedAt;

	@Column(name = "dui_matches_declared")
	private Boolean duiMatchesDeclared;

	@Column(name = "names_match_declared")
	private Boolean namesMatchDeclared;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "corrected_fields", nullable = false)
	private String[] correctedFields = new String[0];

	@Column(name = "processed_at", nullable = false)
	private Instant processedAt;

	@CreationTimestamp
	@Column(name = "registered_at", nullable = false)
	private Instant registeredAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected IdentityDocument() {
	}

	public IdentityDocument(OnboardingRequest request) {
		this.request = request;
	}

	/** Records a new capture: a successful reading, unreadable photos, or a provider failure. */
	public void recordReading(OcrStatus status, DuiReading reading, String failure, String model, Instant now) {
		this.status = status;
		this.model = model;
		this.attempts = (short) (attempts + 1);
		this.failure = failure;
		this.unreadableReason = status == OcrStatus.UNREADABLE ? reasonOf(reading) : null;
		this.unreadableSide = status == OcrStatus.UNREADABLE && reading != null ? reading.unreadableSide() : null;
		boolean read = status == OcrStatus.READ;
		this.dui = read ? reading.dui() : null;
		this.firstNames = read ? reading.firstNames() : null;
		this.lastNames = read ? reading.lastNames() : null;
		this.birthDate = read ? reading.birthDate() : null;
		this.issueDate = read ? reading.issueDate() : null;
		this.expiryDate = read ? reading.expiryDate() : null;
		this.gender = read ? reading.gender() : null;
		this.looksAuthentic = reading == null ? null : reading.looksAuthentic();
		this.confidence = reading == null ? null : reading.confidence();
		// New photos: what was confirmed for the previous ones no longer applies.
		this.confirmedDui = null;
		this.confirmedFirstNames = null;
		this.confirmedLastNames = null;
		this.confirmedBirthDate = null;
		this.confirmedExpiryDate = null;
		this.confirmedAt = null;
		this.duiMatchesDeclared = null;
		this.namesMatchDeclared = null;
		this.correctedFields = new String[0];
		this.processedAt = now;
	}

	/**
	 * Records the data the customer confirmed or corrected. {@code correctedFields} lists what differs from the
	 * reading (names without case, accents or extra spaces); nothing counts as corrected when the DUI was not read and
	 * the customer typed everything.
	 */
	public void confirm(String dui, String firstNames, String lastNames, LocalDate birthDate, LocalDate expiryDate,
			Instant now) {
		this.confirmedDui = dui;
		this.confirmedFirstNames = firstNames;
		this.confirmedLastNames = lastNames;
		this.confirmedBirthDate = birthDate;
		this.confirmedExpiryDate = expiryDate;
		this.confirmedAt = now;
		List<String> corrected = new ArrayList<>();
		if (status == OcrStatus.READ) {
			addIfCorrected(corrected, "dui", this.dui, dui);
			if (this.firstNames != null && !sameName(this.firstNames, firstNames)) {
				corrected.add("firstNames");
			}
			if (this.lastNames != null && !sameName(this.lastNames, lastNames)) {
				corrected.add("lastNames");
			}
			addIfCorrected(corrected, "birthDate", this.birthDate, birthDate);
			addIfCorrected(corrected, "expiryDate", this.expiryDate, expiryDate);
		}
		this.correctedFields = corrected.toArray(String[]::new);
	}

	/** Compares the confirmed DUI and names with the basic data; unknown until the customer confirms. */
	public void compareWithDeclared(String declaredDui, String declaredFirstNames, String declaredLastNames) {
		if (confirmedAt == null) {
			duiMatchesDeclared = null;
			namesMatchDeclared = null;
			return;
		}
		duiMatchesDeclared = confirmedDui.equals(declaredDui);
		namesMatchDeclared = sameName(confirmedFirstNames, declaredFirstNames)
				&& sameName(confirmedLastNames, declaredLastNames);
	}

	public boolean isConfirmed() {
		return confirmedAt != null;
	}

	/** True when the expiry date (as confirmed, else as read) is in the past. */
	public boolean isExpired(LocalDate today) {
		LocalDate expiry = confirmedExpiryDate != null ? confirmedExpiryDate : expiryDate;
		return expiry != null && expiry.isBefore(today);
	}

	/** Expiry date as confirmed, else as read; {@code null} when unknown. */
	public LocalDate effectiveExpiryDate() {
		return confirmedExpiryDate != null ? confirmedExpiryDate : expiryDate;
	}

	private static UnreadableReason reasonOf(DuiReading reading) {
		return reading == null || reading.unreadableReason() == null ? UnreadableReason.OTHER
				: reading.unreadableReason();
	}

	/** A value that was read counts as corrected when the confirmed one is different (or missing). */
	private static void addIfCorrected(List<String> corrected, String field, Object read, Object confirmed) {
		if (read != null && !read.equals(confirmed)) {
			corrected.add(field);
		}
	}

	private static boolean sameName(String read, String declared) {
		return Objects.equals(comparable(read), comparable(declared));
	}

	private static String comparable(String name) {
		if (name == null) {
			return null;
		}
		String withoutAccents = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return withoutAccents.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

	public UUID getRequestId() {
		return requestId;
	}

	public OnboardingRequest getRequest() {
		return request;
	}

	public OcrStatus getStatus() {
		return status;
	}

	public String getModel() {
		return model;
	}

	public short getAttempts() {
		return attempts;
	}

	public UnreadableReason getUnreadableReason() {
		return unreadableReason;
	}

	public String getFailure() {
		return failure;
	}

	public String getDui() {
		return dui;
	}

	public String getFirstNames() {
		return firstNames;
	}

	public String getLastNames() {
		return lastNames;
	}

	public LocalDate getBirthDate() {
		return birthDate;
	}

	public LocalDate getIssueDate() {
		return issueDate;
	}

	public LocalDate getExpiryDate() {
		return expiryDate;
	}

	public String getGender() {
		return gender;
	}

	public Boolean getLooksAuthentic() {
		return looksAuthentic;
	}

	public BigDecimal getConfidence() {
		return confidence;
	}

	public UnreadableSide getUnreadableSide() {
		return unreadableSide;
	}

	public String getConfirmedDui() {
		return confirmedDui;
	}

	public String getConfirmedFirstNames() {
		return confirmedFirstNames;
	}

	public String getConfirmedLastNames() {
		return confirmedLastNames;
	}

	public LocalDate getConfirmedBirthDate() {
		return confirmedBirthDate;
	}

	public LocalDate getConfirmedExpiryDate() {
		return confirmedExpiryDate;
	}

	public Instant getConfirmedAt() {
		return confirmedAt;
	}

	public Boolean getDuiMatchesDeclared() {
		return duiMatchesDeclared;
	}

	public Boolean getNamesMatchDeclared() {
		return namesMatchDeclared;
	}

	public List<String> getCorrectedFields() {
		return List.of(correctedFields);
	}

	public Instant getProcessedAt() {
		return processedAt;
	}

	public Instant getRegisteredAt() {
		return registeredAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
