package org.IAM.user;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_profiles")
public class UserProfile {
	@Id
	@Column(name = "user_id", updatable = false, nullable = false)
	private UUID userID;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@MapsId
	@JoinColumn(name = "user_id")
	private User user;

	@Column(name = "full_name", length = 255)
	private String fullName;

	@Column(name = "phone_number", length = 20)
	private String phoneNumber;

	@Column(name = "address", length = 500)
	private String address;

	@Column(name = "date_of_birth")
	private LocalDate dateOfBirth;

	@Column(name = "avatar_url", length = 500)
	private String avatarUrl;

	@Column(name = "updated_at", nullable = false, updatable = true)
	private Instant updatedAt;

	@PreUpdate  
	protected void onUpdate() {
		this.updatedAt = Instant.now();
	}
	@PrePersist   
	protected void onCreate() {
		this.updatedAt = Instant.now();
	}

	protected UserProfile() {}

	public UserProfile(User user, String fullName, String phoneNumber, String address, LocalDate dateOfBirth, String avatarUrl) {
		this.user = user;
		this.fullName = fullName;
		this.phoneNumber = phoneNumber;
		this.address = address;
		this.dateOfBirth = dateOfBirth;
		this.avatarUrl = avatarUrl;
	}
	public UUID getUserID() {
		return userID;
	}
	public User getUser() {
		return user;
	}
	public String getFullName() {
		return fullName;
	}
	public String getPhoneNumber() {
		return phoneNumber;
	}
	public String getAddress() {
		return address;
	}
	public LocalDate getDateOfBirth() {
		return dateOfBirth;
	}
	public String getAvatarUrl() {
		return avatarUrl;
	}
	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
