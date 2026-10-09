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
import com.dreamparking.backend.identity.ocr.DuiReading;
import com.dreamparking.backend.onboarding.entity.OnboardingRequest;

/**
 * What was read from the request's DUI (VDI-79), one per request: the last capture. When the customer confirms the
 * basic data, {@link #confirm} records whether the DUI matches and which fields the customer corrected.
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

	@Column(name = "dui_matches_declared")
	private Boolean duiMatchesDeclared;

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
		this.duiMatchesDeclared = null;
		this.correctedFields = new String[0];
		this.processedAt = now;
	}

	/**
	 * Compares what the customer confirmed in the basic data with what was read. Names are compared without case,
	 * accents or extra spaces, so only real corrections count. Nothing to compare when the DUI was not read.
	 */
	public void confirm(String declaredDui, String declaredFirstNames, String declaredLastNames) {
		if (status != OcrStatus.READ) {
			duiMatchesDeclared = null;
			correctedFields = new String[0];
			return;
		}
		List<String> corrected = new ArrayList<>();
		if (dui != null && !dui.equals(declaredDui)) {
			corrected.add("dui");
		}
		if (firstNames != null && !sameName(firstNames, declaredFirstNames)) {
			corrected.add("firstNames");
		}
		if (lastNames != null && !sameName(lastNames, declaredLastNames)) {
			corrected.add("lastNames");
		}
		duiMatchesDeclared = dui == null ? null : dui.equals(declaredDui);
		correctedFields = corrected.toArray(String[]::new);
	}

	/** True when the document has an expiry date in the past. */
	public boolean isExpired(LocalDate today) {
		return expiryDate != null && expiryDate.isBefore(today);
	}

	private static UnreadableReason reasonOf(DuiReading reading) {
		return reading == null || reading.unreadableReason() == null ? UnreadableReason.OTHER
				: reading.unreadableReason();
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

	public Boolean getDuiMatchesDeclared() {
		return duiMatchesDeclared;
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
