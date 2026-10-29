package com.dreamparking.backend.product.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

import com.dreamparking.backend.onboarding.entity.OnboardingRequest;

/**
 * The product an approved request chose (VDI-148). One per request, and never changed or deleted: the database refuses
 * it. Everything else about the person comes from the request.
 */
@Entity
@Table(name = "product_request")
public class ProductRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "request_id", nullable = false, updatable = false, unique = true)
	private OnboardingRequest request;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id", nullable = false, updatable = false)
	private Product product;

	@CreationTimestamp
	@Column(name = "requested_at", nullable = false, updatable = false)
	private Instant requestedAt;

	protected ProductRequest() {
	}

	public ProductRequest(OnboardingRequest request, Product product) {
		this.request = request;
		this.product = product;
	}

	public Long getId() {
		return id;
	}

	public OnboardingRequest getRequest() {
		return request;
	}

	public Product getProduct() {
		return product;
	}

	public Instant getRequestedAt() {
		return requestedAt;
	}

}
